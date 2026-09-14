package com.stayhub.supplier.b;

import java.util.List;

/**
 * Supplier B 숙소 목록 API
 * B는 실패해도 HTTP 200이고 resultCode로만 알림
 */
record BPropertiesResponse(String resultCode, String resultMessage, Data data) {

	record Data(List<Property> items) {
	}

	record Property(String propertyId, String propertyName, List<Room> rooms) {
	}

	record Room(String roomId, String roomName, int maxOccupancy) {
	}
}
