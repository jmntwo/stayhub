package com.stayhub.mapping;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

import com.stayhub.domain.Supplier;
import com.stayhub.supplier.CatalogEntry;
import com.stayhub.supplier.SupplierAdapter;

/**
 * 공급사 하나의 숙소 목록을 받아 매핑 테이블에 반영
 * 절차: 목록 API 호출 → upsert → 이번에 등장하지 않은 행 비활성화
 * 목록 API 실패는 SupplierException으로 전파되며 DB는 건드리지 않음
 */
@Service
@RequiredArgsConstructor
public class MappingSyncService {

	private final PropertyRepository properties;
	private final RoomTypeRepository roomTypes;
	private final TransactionTemplate transaction;

	/** 목록 API 호출 후 반영. 동기화는 백그라운드 진입점이라 여기서 block */
	public SyncResult sync(SupplierAdapter adapter, Instant seenAt) {
		List<CatalogEntry> catalog = adapter.fetchCatalog().block();
		return transaction.execute(status -> apply(adapter.supplier(), catalog, seenAt));
	}

	private SyncResult apply(Supplier supplier, List<CatalogEntry> catalog, Instant seenAt) {
		int propertyCount = 0;
		int roomTypeCount = 0;
		for (CatalogEntry entry : catalog) {
			Property property = upsertProperty(supplier, entry, seenAt);
			propertyCount++;
			for (CatalogEntry.Room room : entry.rooms()) {
				upsertRoomType(property, room, seenAt);
				roomTypeCount++;
			}
		}

		int deactivatedRoomTypes = roomTypes.deactivateNotSeen(supplier, seenAt);
		int deactivatedProperties = properties.deactivateNotSeen(supplier, seenAt);
		return new SyncResult(supplier, propertyCount, roomTypeCount, deactivatedProperties, deactivatedRoomTypes);
	}

	private Property upsertProperty(Supplier supplier, CatalogEntry entry, Instant seenAt) {
		return properties.findBySupplierAndSupplierCode(supplier, entry.hotelCode())
				.map(existing -> {
					existing.seen(entry.hotelName(), seenAt);
					return existing;
				})
				.orElseGet(() -> properties.save(new Property(supplier, entry.hotelCode(), entry.hotelName(), seenAt)));
	}

	private void upsertRoomType(Property property, CatalogEntry.Room room, Instant seenAt) {
		roomTypes.findByPropertyIdAndSupplierRoomCode(property.getId(), room.roomCode())
				.ifPresentOrElse(
						existing -> existing.seen(room.roomName(), room.maxOccupancy(), seenAt),
						() -> roomTypes.save(new RoomType(property, room.roomCode(), room.roomName(),
								room.maxOccupancy(), seenAt)));
	}
}
