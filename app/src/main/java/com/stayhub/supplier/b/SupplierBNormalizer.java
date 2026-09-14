package com.stayhub.supplier.b;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stayhub.domain.Price;
import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.CatalogEntry;
import com.stayhub.supplier.SupplierOffer;

/**
 * 요금: totalPrice가 이미 기간 총액 gross라 그대로. 날짜별 내역은 null
 */
final class SupplierBNormalizer {

	private SupplierBNormalizer() {
	}

	static List<CatalogEntry> toCatalog(BPropertiesResponse response) {
		List<CatalogEntry> entries = new ArrayList<>();
		for (BPropertiesResponse.Property property : nonNull(response.data().items())) {
			List<CatalogEntry.Room> rooms = new ArrayList<>();
			for (BPropertiesResponse.Room room : nonNull(property.rooms())) {
				rooms.add(new CatalogEntry.Room(room.roomId(), room.roomName(), room.maxOccupancy()));
			}
			entries.add(new CatalogEntry(property.propertyId(), property.propertyName(), rooms));
		}
		return entries;
	}

	static List<SupplierOffer> toOffers(BSearchResponse response, SearchQuery query) {
		List<LocalDate> nights = query.checkIn().datesUntil(query.checkOut()).toList();
		List<SupplierOffer> offers = new ArrayList<>();
		for (BSearchResponse.Item item : nonNull(response.data().items())) {
			offers.add(toOffer(item, nights));
		}
		return offers;
	}

	private static SupplierOffer toOffer(BSearchResponse.Item item, List<LocalDate> nights) {
		Map<LocalDate, Integer> remainingByDate = new HashMap<>();
		for (BSearchResponse.Inventory inv : nonNull(item.inventory())) {
			remainingByDate.put(inv.date(), inv.remainingRooms());
		}

		int minRemaining = Integer.MAX_VALUE;
		boolean complete = true;
		for (LocalDate night : nights) {
			Integer remaining = remainingByDate.get(night);
			if (remaining == null) {
				complete = false;
				continue;
			}
			minRemaining = Math.min(minRemaining, remaining);
		}
		int availableRooms = complete && !nights.isEmpty() ? minRemaining : 0;

		return new SupplierOffer(
				Supplier.B,
				item.propertyId(),
				item.roomId(),
				new Price(item.totalPrice(), item.currency(), null),
				availableRooms,
				item.breakfastIncluded());
	}

	private static <T> List<T> nonNull(List<T> list) {
		return list != null ? list : List.of();
	}
}
