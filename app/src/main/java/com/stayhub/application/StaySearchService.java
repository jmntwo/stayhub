package com.stayhub.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;

import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.StayOffer;
import com.stayhub.domain.Supplier;
import com.stayhub.mapping.Property;
import com.stayhub.mapping.PropertyRepository;
import com.stayhub.mapping.RoomType;
import com.stayhub.mapping.RoomTypeRepository;
import com.stayhub.mapping.SyncStatus;
import com.stayhub.supplier.FailureReason;
import com.stayhub.supplier.SupplierAdapter;
import com.stayhub.supplier.SupplierException;
import com.stayhub.supplier.SupplierOffer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 통합 검색 핵심 흐름
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaySearchService {

	private final List<SupplierAdapter> adapters;
	private final PropertyRepository properties;
	private final RoomTypeRepository roomTypes;
	private final SyncStatus syncStatus;
	private final SearchProperties config;

	public SearchResult search(SearchQuery query) {

		MappingIndex index = MappingIndex.load(properties, roomTypes);


		List<Mono<SupplierCall>> calls = new ArrayList<>();
		for (SupplierAdapter adapter : adapters) {
			calls.add(callSupplier(adapter, index.codesOf(adapter.supplier()), query));
		}

		List<SupplierCall> results = Flux.merge(calls)
				.collectList()
				.block(config.deadline().plusSeconds(1));


		List<StayOffer> items = new ArrayList<>();
		List<SupplierOutcome> outcomes = new ArrayList<>();
		for (SupplierCall call : results) {
			outcomes.add(call.outcome());
			items.addAll(index.join(call.offers()));
		}

		items.sort(Comparator.comparingLong(StayOffer::propertyId).thenComparingLong(StayOffer::roomTypeId));
		outcomes.sort(Comparator.comparing(SupplierOutcome::supplier));
		return new SearchResult(query, items, outcomes);
	}

	/** 청크별 병렬 호출 후 집계 */
	private Mono<SupplierCall> callSupplier(SupplierAdapter adapter, List<String> codes, SearchQuery query) {
		Supplier supplier = adapter.supplier();
		// 동기화가 한 번도 성공하지 않은 공급사 → 호출 x
		if (!syncStatus.hasMapping(supplier)) {
			return Mono.just(SupplierCall.failed(supplier, FailureReason.NO_MAPPING));
		}
		// 매핑은 있는데 활성 숙소가 0개 → 정상이지만 결과 없음
		if (codes.isEmpty()) {
			return Mono.just(SupplierCall.empty(supplier));
		}

		// 청크 리스트를 Flux로 만듦
		return Flux.fromIterable(chunks(codes))
				.flatMap(chunk -> adapter.fetchAvailability(chunk, query)
						.map(ChunkResult::ok)                                      // 성공 -> ok
						.onErrorResume(SupplierException.class, e -> Mono.just(ChunkResult.failed(e.reason())))  // 어댑터 실패 ->  사유 보존
						.onErrorResume(e -> Mono.just(ChunkResult.failed(FailureReason.UNAVAILABLE))),          // 예상 밖 -> UNAVAILABLE
						config.concurrencyPerSupplier())
				.collectList()
				.map(chunkResults -> SupplierCall.aggregate(supplier, chunkResults))   // OK or PARTIAL or FAILED
				.timeout(config.deadline())                           // 공급사 전체가 deadline 안에 못 끝나면 에러로
				.onErrorResume(TimeoutException.class, e -> Mono.just(SupplierCall.failed(supplier, FailureReason.TIMEOUT)));
	}

	private List<List<String>> chunks(List<String> codes) {
		List<List<String>> chunks = new ArrayList<>();
		for (int from = 0; from < codes.size(); from += config.chunkSize()) {
			chunks.add(codes.subList(from, Math.min(from + config.chunkSize(), codes.size())));
		}
		return chunks;
	}

	// ---- 내부 타입 ----

	/** 청크 하나의 결과. 성공이면 offers, 실패면 reason */
	private record ChunkResult(List<SupplierOffer> offers, FailureReason reason) {

		static ChunkResult ok(List<SupplierOffer> offers) {
			return new ChunkResult(offers, null);
		}

		static ChunkResult failed(FailureReason reason) {
			return new ChunkResult(List.of(), reason);
		}

		boolean isFailed() {
			return reason != null;
		}
	}

	/** 공급사 하나의 집계 결과 */
	private record SupplierCall(List<SupplierOffer> offers, SupplierOutcome outcome) {

		static SupplierCall empty(Supplier supplier) {
			return new SupplierCall(List.of(), SupplierOutcome.ok(supplier));
		}

		static SupplierCall failed(Supplier supplier, FailureReason reason) {
			return new SupplierCall(List.of(), SupplierOutcome.failed(supplier, reason));
		}

		static SupplierCall aggregate(Supplier supplier, List<ChunkResult> chunks) {
			// 청크 결과를 하나로. 실패 수를 세고 첫 실패 사유를 대표로
			List<SupplierOffer> offers = new ArrayList<>();
			int failed = 0;
			FailureReason firstReason = null;
			for (ChunkResult chunk : chunks) {
				if (chunk.isFailed()) {
					failed++;
					if (firstReason == null) {
						firstReason = chunk.reason();
					}
				} else {
					offers.addAll(chunk.offers());
				}
			}
			if (failed == 0) {                       // 전부 성공
				return new SupplierCall(offers, SupplierOutcome.ok(supplier));
			}
			if (failed == chunks.size()) {           // 전부 실패
				return new SupplierCall(List.of(), SupplierOutcome.failed(supplier, firstReason));
			}
			return new SupplierCall(offers, SupplierOutcome.partial(supplier, firstReason));  // 섞임
		}
	}

	/**
	 * 매핑 테이블을 검색 시작 시 한 번 읽어 Map으로 만든 것 (상품마다 DB를 다시 묻지 않기 위함)
	 *
	 * 검색 한 번의 흐름에서 두 번 쓰임
	 * - 공급사 API를 부르기 전: 어느 숙소 코드를 물어볼지 목록을 꺼냄 (codesOf)
	 * - 공급사 응답을 받은 뒤: 응답의 공급사 코드로 우리 숙소/객실 행을 찾아 id, 이름, 최대 인원을 붙임 (join)
	 */
	private static final class MappingIndex {


		// 숙소 조회 키
		private record PropertyKey(Supplier supplier, String code) {
		}

		// 객실 조회 키
		private record RoomKey(long propertyId, String roomCode) {
		}

		// 공급사 -> 숙소 코드 목록
		private final Map<Supplier, List<String>> codesBySupplier = new HashMap<>();

		// (공급사, 숙소 코드) -> 숙소 행
		private final Map<PropertyKey, Property> propertyByKey = new HashMap<>();

		// (숙소 id, 객실 코드) -> 객실 행
		private final Map<RoomKey, RoomType> roomByKey = new HashMap<>();

		static MappingIndex load(PropertyRepository properties, RoomTypeRepository roomTypes) {
			MappingIndex index = new MappingIndex();
			List<Property> active = properties.findByActiveTrue();   // 활성 숙소 전체 (지역 필터 없음)
			List<Long> ids = new ArrayList<>();
			for (Property p : active) {
				index.codesBySupplier.computeIfAbsent(p.getSupplier(), s -> new ArrayList<>()).add(p.getSupplierCode());
				index.propertyByKey.put(new PropertyKey(p.getSupplier(), p.getSupplierCode()), p);
				ids.add(p.getId());
			}
			if (!ids.isEmpty()) {
				for (RoomType r : roomTypes.findByPropertyIdInAndActiveTrue(ids)) {
					index.roomByKey.put(new RoomKey(r.getProperty().getId(), r.getSupplierRoomCode()), r);
				}
			}
			return index;
		}

		List<String> codesOf(Supplier supplier) {
			return codesBySupplier.getOrDefault(supplier, List.of());
		}

		/** 공급사 코드를 내부 식별자로 */
		List<StayOffer> join(List<SupplierOffer> offers) {
			List<StayOffer> joined = new ArrayList<>();
			for (SupplierOffer offer : offers) {
				// 응답의 공급사 코드로 매핑을 찾음
				Property property = propertyByKey.get(new PropertyKey(offer.supplier(), offer.hotelCode()));
				if (property == null) {
					log.debug("unmapped hotel dropped supplier={} code={}", offer.supplier(), offer.hotelCode());
					continue;
				}
				RoomType room = roomByKey.get(new RoomKey(property.getId(), offer.roomCode()));
				if (room == null) {
					log.debug("unmapped room dropped supplier={} hotel={} room={}", offer.supplier(), offer.hotelCode(), offer.roomCode());
					continue;
				}


				joined.add(new StayOffer(
						property.getId(), property.getName(),
						room.getId(), room.getName(),
						offer.supplier(), room.getMaxOccupancy(),
						offer.availableRooms(), offer.price(), offer.breakfastIncluded()));
			}
			return joined;
		}
	}
}
