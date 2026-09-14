package com.stayhub.supplier;

import com.stayhub.domain.Price;
import com.stayhub.domain.Supplier;

/**
 * 재고·요금 API 응답을 정규화
 */
public record SupplierOffer(
		Supplier supplier,
		String hotelCode,
		String roomCode,
		Price price,
		int availableRooms,
		boolean breakfastIncluded) {
}
