package com.stayhub.mocksupplier;

import java.util.List;

/**
 * Mock 응답 데이터. 값은 고정이고 날짜만 요청에 맞춰 생성
 * 날짜별 값은 배열로 두고, 숙박일 인덱스가 배열 길이를 넘으면 단순 마지막 값을 반복
 * 검증 케이스: A와 B 동일 숙소(조식 조건 다름), A 전용 숙소, 둘째 날 재고 0, 숙소 하나에 객실 2종, 다른 숙소에 같은 객실 코드
 */
final class MockData {

	private MockData() {
	}

	record RoomA(String hotelCode, String hotelName, String roomTypeCode, String roomTypeName,
			int maxOccupancy, boolean breakfastIncluded,
			int[] nightlyRate, int[] taxAmount, int[] remainingRooms) {
	}

	record RoomB(String propertyId, String propertyName, String roomId, String roomName,
			int maxOccupancy, boolean breakfastIncluded,
			int grossPerNight, int[] remainingRooms) {
	}

	static final String CURRENCY = "KRW";

	static final List<RoomA> SUPPLIER_A = List.of(
			new RoomA("A-2001", "Harbor View Hotel", "OCN-TWN", "Ocean Twin", 2, false,
					new int[] {130_000}, new int[] {13_000}, new int[] {2}),
			new RoomA("A-2001", "Harbor View Hotel", "STD-DBL", "Standard Double", 2, false,
					new int[] {100_000}, new int[] {10_000}, new int[] {3}),
			// 둘째 날 재고 0, 다른 숙소(A-2001)와 같은 객실 코드 STD-DBL
			new RoomA("A-2002", "Pine Hill Stay", "STD-DBL", "Standard Double", 2, false,
					new int[] {90_000, 82_000, 90_000}, new int[] {9_000}, new int[] {1, 0, 1})
	);

	static final List<RoomB> SUPPLIER_B = List.of(
			// A-2001과 같은 숙소, 같은 객실, 조식 포함
			new RoomB("P-3101", "Harbor View Hotel", "R-11", "Ocean Twin Room", 2, true,
					155_000, new int[] {1, 3}),
			// 다른 숙소(P-3101)와 같은 객실 코드 R-11
			new RoomB("P-3102", "Lakeside Inn", "R-11", "Lake Room", 2, false,
					120_000, new int[] {2})
	);

	/** 숙박일 인덱스의 값. 배열 길이를 넘으면 마지막 값. */
	static int valueAt(int[] values, int nightIndex) {
		return nightIndex < values.length ? values[nightIndex] : values[values.length - 1];
	}
}
