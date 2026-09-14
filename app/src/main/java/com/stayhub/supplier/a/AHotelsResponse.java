package com.stayhub.supplier.a;

import java.util.List;

/** Supplier A 숙소 목록 API 응답 */
record AHotelsResponse(List<Hotel> items) {

	record Hotel(String hotelCode, String hotelName, List<RoomType> roomTypes) {
	}

	record RoomType(String roomTypeCode, String roomTypeName, int maxOccupancy) {
	}
}
