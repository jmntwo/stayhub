package com.stayhub.supplier.b;

import java.time.LocalDate;
import java.util.List;

/**
 * Supplier B 재고·요금 API
 */
record BSearchResponse(String resultCode, String resultMessage, Data data) {

	record Data(List<Item> items) {
	}

	record Item(
			String propertyId,
			String propertyName,
			String roomId,
			String roomName,
			int maxOccupancy,
			boolean breakfastIncluded,
			String currency,
			long totalPrice,
			boolean taxIncluded,
			List<Inventory> inventory) {
	}

	record Inventory(LocalDate date, int remainingRooms) {
	}
}
