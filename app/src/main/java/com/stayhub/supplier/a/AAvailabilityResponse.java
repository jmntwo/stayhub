package com.stayhub.supplier.a;

import java.time.LocalDate;
import java.util.List;

/** Supplier A 재고·요금 API 응답. 날짜별 1박 단가와 세금이 별도 */
record AAvailabilityResponse(List<Item> items) {

	record Item(
			String hotelCode,
			String hotelName,
			String roomTypeCode,
			String roomTypeName,
			int maxOccupancy,
			boolean breakfastIncluded,
			String currency,
			List<DailyRate> dailyRates) {
	}

	record DailyRate(LocalDate date, int remainingRooms, long nightlyRate, long taxAmount) {
	}
}
