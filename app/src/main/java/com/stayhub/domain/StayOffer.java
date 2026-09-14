package com.stayhub.domain;

/**
 * 검색 결과 항목. 상품 단위는 (공급사, 숙소, 객실 타입)
 * availableRooms가 0이면 예약 불가지만 결과에서 제외하지 않음
 */
public record StayOffer(
		long propertyId,
		String propertyName,
		long roomTypeId,
		String roomTypeName,
		Supplier supplier,
		int maxOccupancy,
		int availableRooms,
		Price price,
		boolean breakfastIncluded) {
}
