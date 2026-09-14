package com.stayhub.supplier.a;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stayhub.domain.NightlyRate;
import com.stayhub.domain.Price;
import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.CatalogEntry;
import com.stayhub.supplier.SupplierOffer;

/**
 * A 응답 → 표준 형식 변환
 *
 * 요금: 요청 숙박일의 (nightlyRate + taxAmount) 합산 → 총액 gross. 날짜별 내역은 nightly에 유지
 * 재고: 요청 숙박일 remainingRooms의 최솟값. 응답에 요청 날짜가 하나라도 빠지면 0
 * 요청 기간 밖의 날짜는 무시
 */
final class SupplierANormalizer {

	private SupplierANormalizer() {
	}

	static List<CatalogEntry> toCatalog(AHotelsResponse response) {
		List<CatalogEntry> entries = new ArrayList<>();
		for (AHotelsResponse.Hotel hotel : nonNull(response.items())) {
			List<CatalogEntry.Room> rooms = new ArrayList<>();
			for (AHotelsResponse.RoomType rt : nonNull(hotel.roomTypes())) {
				rooms.add(new CatalogEntry.Room(rt.roomTypeCode(), rt.roomTypeName(), rt.maxOccupancy()));
			}
			entries.add(new CatalogEntry(hotel.hotelCode(), hotel.hotelName(), rooms));
		}
		return entries;
	}

	static List<SupplierOffer> toOffers(AAvailabilityResponse response, SearchQuery query) {
		List<LocalDate> nights = query.checkIn().datesUntil(query.checkOut()).toList();
		List<SupplierOffer> offers = new ArrayList<>();
		for (AAvailabilityResponse.Item item : nonNull(response.items())) {
			offers.add(toOffer(item, nights));
		}
		return offers;
	}

	private static SupplierOffer toOffer(AAvailabilityResponse.Item item, List<LocalDate> nights) {
		Map<LocalDate, AAvailabilityResponse.DailyRate> byDate = new HashMap<>();
		for (AAvailabilityResponse.DailyRate d : nonNull(item.dailyRates())) {
			byDate.put(d.date(), d);
		}

		long total = 0;
		int minRemaining = Integer.MAX_VALUE;
		boolean complete = true;
		List<NightlyRate> nightly = new ArrayList<>();
		for (LocalDate night : nights) {
			AAvailabilityResponse.DailyRate d = byDate.get(night);
			if (d == null) {
				complete = false;
				continue;
			}
			total += d.nightlyRate() + d.taxAmount();
			minRemaining = Math.min(minRemaining, d.remainingRooms());
			nightly.add(new NightlyRate(night, d.nightlyRate(), d.taxAmount()));
		}
		int availableRooms = complete && !nights.isEmpty() ? minRemaining : 0;

		return new SupplierOffer(
				Supplier.A,
				item.hotelCode(),
				item.roomTypeCode(),
				new Price(total, item.currency(), nightly),
				availableRooms,
				item.breakfastIncluded());
	}

	private static <T> List<T> nonNull(List<T> list) {
		return list != null ? list : List.of();
	}
}
