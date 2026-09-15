package com.stayhub.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.stayhub.domain.Price;
import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.StayOffer;
import com.stayhub.domain.Supplier;
import com.stayhub.mapping.Property;
import com.stayhub.mapping.PropertyRepository;
import com.stayhub.mapping.RoomType;
import com.stayhub.mapping.RoomTypeRepository;
import com.stayhub.mapping.SyncProperties;
import com.stayhub.mapping.SyncStatus;
import com.stayhub.supplier.CatalogEntry;
import com.stayhub.supplier.FailureReason;
import com.stayhub.supplier.SupplierAdapter;
import com.stayhub.supplier.SupplierException;
import com.stayhub.supplier.SupplierOffer;

import reactor.core.publisher.Mono;

@DataJpaTest
class StaySearchServiceTest {

	@Autowired
	PropertyRepository properties;

	@Autowired
	RoomTypeRepository roomTypes;

	SyncStatus syncStatus;
	FakeAdapter a;
	FakeAdapter b;
	StaySearchService service;

	SearchQuery query = new SearchQuery(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2, 0);
	Instant now = Instant.parse("2026-09-15T00:00:00Z");

	@BeforeEach
	void setUp() {
		syncStatus = new SyncStatus(new SyncProperties(null, null, null, 0));
		syncStatus.recordSuccess(Supplier.A, now);
		syncStatus.recordSuccess(Supplier.B, now);
		a = new FakeAdapter(Supplier.A);
		b = new FakeAdapter(Supplier.B);
		service = new StaySearchService(List.of(a, b), properties, roomTypes, syncStatus,
				new SearchProperties(Duration.ofMillis(500), 50, 4));
	}

	@Test
	void 두_공급사_결과를_내부_식별자로_결합해_병합() {
		Property harborA = property(Supplier.A, "A-2001", "Harbor View Hotel", "OCN-TWN", "STD-DBL");
		Property harborB = property(Supplier.B, "P-3101", "Harbor View Hotel", "R-11");
		a.respond(codes -> List.of(offer(Supplier.A, "A-2001", "OCN-TWN", 286_000, 2, false)));
		b.respond(codes -> List.of(offer(Supplier.B, "P-3101", "R-11", 310_000, 1, true)));

		SearchResult result = service.search(query);

		assertThat(result.suppliers()).extracting(SupplierOutcome::status)
				.containsOnly(SupplierOutcome.Status.OK);
		assertThat(result.items()).hasSize(2);
		StayOffer first = result.items().get(0);
		assertThat(first.propertyId()).isEqualTo(harborA.getId());
		assertThat(first.propertyName()).isEqualTo("Harbor View Hotel");
		assertThat(first.roomTypeName()).isEqualTo("OCN-TWN name");
		assertThat(first.maxOccupancy()).isEqualTo(2);
		assertThat(first.price().total()).isEqualTo(286_000);
		assertThat(result.items().get(1).propertyId()).isEqualTo(harborB.getId());
		assertThat(result.items().get(1).breakfastIncluded()).isTrue();
	}

	@Test
	void 한_공급사가_실패하면_나머지로_응답하고_실패_사실을_표기() {
		property(Supplier.A, "A-2001", "Harbor", "OCN-TWN");
		property(Supplier.B, "P-3101", "Harbor", "R-11");
		a.respond(codes -> List.of(offer(Supplier.A, "A-2001", "OCN-TWN", 286_000, 2, false)));
		b.fail(FailureReason.UNAVAILABLE);

		SearchResult result = service.search(query);

		assertThat(result.items()).hasSize(1);
		assertThat(outcome(result, Supplier.A).status()).isEqualTo(SupplierOutcome.Status.OK);
		assertThat(outcome(result, Supplier.B).status()).isEqualTo(SupplierOutcome.Status.FAILED);
		assertThat(outcome(result, Supplier.B).reason()).isEqualTo(FailureReason.UNAVAILABLE);
	}

	@Test
	void 전부_실패하면_빈_결과와_공급사별_사유() {
		property(Supplier.A, "A-2001", "Harbor", "OCN-TWN");
		property(Supplier.B, "P-3101", "Harbor", "R-11");
		a.fail(FailureReason.TIMEOUT);
		b.fail(FailureReason.REJECTED);

		SearchResult result = service.search(query);

		assertThat(result.items()).isEmpty();
		assertThat(outcome(result, Supplier.A).reason()).isEqualTo(FailureReason.TIMEOUT);
		assertThat(outcome(result, Supplier.B).reason()).isEqualTo(FailureReason.REJECTED);
	}

	@Test
	void 매핑이_없는_공급사는_호출하지_않고_NO_MAPPING() {
		property(Supplier.A, "A-2001", "Harbor", "OCN-TWN");
		a.respond(codes -> List.of(offer(Supplier.A, "A-2001", "OCN-TWN", 286_000, 2, false)));
		syncStatus = new SyncStatus(new SyncProperties(null, null, null, 0));
		syncStatus.recordSuccess(Supplier.A, now);
		service = new StaySearchService(List.of(a, b), properties, roomTypes, syncStatus,
				new SearchProperties(Duration.ofMillis(500), 50, 4));

		SearchResult result = service.search(query);

		assertThat(outcome(result, Supplier.B).status()).isEqualTo(SupplierOutcome.Status.FAILED);
		assertThat(outcome(result, Supplier.B).reason()).isEqualTo(FailureReason.NO_MAPPING);
		assertThat(b.calls).isEmpty();
		assertThat(result.items()).hasSize(1);
	}

	@Test
	void 숙소가_50개를_넘으면_청크로_나눠_호출() {
		for (int i = 0; i < 120; i++) {
			property(Supplier.A, "A-" + i, "Hotel " + i, "R");
		}
		a.respond(codes -> List.of());
		syncStatus.recordSuccess(Supplier.B, now);
		b.respond(codes -> List.of());

		service.search(query);

		assertThat(a.calls).hasSize(3);
		assertThat(a.calls).extracting(List::size).containsExactlyInAnyOrder(50, 50, 20);
	}

	@Test
	void 청크_일부가_실패하면_PARTIAL이고_성공한_청크의_결과는_남음() {
		for (int i = 0; i < 60; i++) {
			property(Supplier.A, "A-" + i, "Hotel " + i, "R");
		}
		a.respond(codes -> {
			if (codes.contains("A-0")) {
				throw new SupplierException(Supplier.A, FailureReason.UNAVAILABLE, "chunk down");
			}
			return List.of(offer(Supplier.A, codes.get(0), "R", 100_000, 1, false));
		});
		b.respond(codes -> List.of());

		SearchResult result = service.search(query);

		assertThat(outcome(result, Supplier.A).status()).isEqualTo(SupplierOutcome.Status.PARTIAL);
		assertThat(outcome(result, Supplier.A).reason()).isEqualTo(FailureReason.UNAVAILABLE);
		assertThat(result.items()).hasSize(1);
	}

	@Test
	void 매핑에_없는_코드가_응답에_섞여_오면_버림() {
		property(Supplier.A, "A-2001", "Harbor", "OCN-TWN");
		a.respond(codes -> List.of(
				offer(Supplier.A, "A-2001", "OCN-TWN", 286_000, 2, false),
				offer(Supplier.A, "A-9999", "OCN-TWN", 1, 1, false),
				offer(Supplier.A, "A-2001", "GHOST", 1, 1, false)));
		b.respond(codes -> List.of());

		SearchResult result = service.search(query);

		assertThat(result.items()).hasSize(1);
		assertThat(outcome(result, Supplier.A).status()).isEqualTo(SupplierOutcome.Status.OK);
	}

	@Test
	void 데드라인을_넘기는_공급사는_TIMEOUT으로_끊고_나머지로_응답() {
		property(Supplier.A, "A-2001", "Harbor", "OCN-TWN");
		property(Supplier.B, "P-3101", "Harbor", "R-11");
		a.respond(codes -> List.of(offer(Supplier.A, "A-2001", "OCN-TWN", 286_000, 2, false)));
		b.hang();

		long started = System.currentTimeMillis();
		SearchResult result = service.search(query);
		long elapsed = System.currentTimeMillis() - started;

		assertThat(result.items()).hasSize(1);
		assertThat(outcome(result, Supplier.B).reason()).isEqualTo(FailureReason.TIMEOUT);
		assertThat(elapsed).isLessThan(1500);
	}

	// ---- helpers ----

	private Property property(Supplier supplier, String code, String name, String... roomCodes) {
		Property p = properties.saveAndFlush(new Property(supplier, code, name, now));
		for (String rc : roomCodes) {
			roomTypes.saveAndFlush(new RoomType(p, rc, rc + " name", 2, now));
		}
		return p;
	}

	private static SupplierOffer offer(Supplier s, String hotel, String room, long total, int available, boolean breakfast) {
		return new SupplierOffer(s, hotel, room, new Price(total, "KRW", null), available, breakfast);
	}

	private static SupplierOutcome outcome(SearchResult result, Supplier supplier) {
		return result.suppliers().stream().filter(o -> o.supplier() == supplier).findFirst().orElseThrow();
	}

	/** 청크 목록을 기록하고, 지정한 방식으로 응답하는 가짜 어댑터 */
	static final class FakeAdapter implements SupplierAdapter {

		private final Supplier supplier;
		final List<List<String>> calls = new ArrayList<>();
		private Function<List<String>, List<SupplierOffer>> handler = codes -> List.of();
		private boolean hang;

		FakeAdapter(Supplier supplier) {
			this.supplier = supplier;
		}

		void respond(Function<List<String>, List<SupplierOffer>> handler) {
			this.handler = handler;
		}

		void fail(FailureReason reason) {
			this.handler = codes -> {
				throw new SupplierException(supplier, reason, "fake");
			};
		}

		void hang() {
			this.hang = true;
		}

		@Override
		public Supplier supplier() {
			return supplier;
		}

		@Override
		public Mono<List<CatalogEntry>> fetchCatalog() {
			return Mono.just(List.of());
		}

		@Override
		public Mono<List<SupplierOffer>> fetchAvailability(List<String> hotelCodes, SearchQuery query) {
			calls.add(hotelCodes);
			if (hang) {
				return Mono.never();
			}
			return Mono.fromCallable(() -> handler.apply(hotelCodes));
		}
	}
}
