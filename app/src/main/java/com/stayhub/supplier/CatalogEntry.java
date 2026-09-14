package com.stayhub.supplier;

import java.util.List;

/**
 * 숙소 목록 API 응답을 정규화
 */
public record CatalogEntry(String hotelCode, String hotelName, List<Room> rooms) {

	public record Room(String roomCode, String roomName, int maxOccupancy) {
	}
}
