package com.stayhub.mocksupplier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청 파라미터 중 checkIn, checkOut만 날짜 생성에 쓰고 나머지는 무시
 */
@RestController
public class MockSupplierController {

	private static final long NO_RESPONSE_SLEEP_MS = 600_000;

	private final FailureModeController failureModes;
	private final long delayMs;

	public MockSupplierController(FailureModeController failureModes,
			@Value("${mock.delay-seconds:3}") long delaySeconds) {
		this.failureModes = failureModes;
		this.delayMs = delaySeconds * 1000;
	}

	// ---- Supplier A ----

	@GetMapping(value = "/a/v1/hotels", produces = "application/json")
	public ResponseEntity<Object> hotelsA() {
		ResponseEntity<Object> failure = failureResponse("a", "catalog");
		if (failure != null) {
			return failure;
		}
		return ResponseEntity.ok(hotelsBodyA());
	}

	private Map<String, Object> hotelsBodyA() {
		Map<String, Map<String, Object>> hotels = new LinkedHashMap<>();
		for (MockData.RoomA r : MockData.SUPPLIER_A) {
			Map<String, Object> hotel = hotels.get(r.hotelCode());
			if (hotel == null) {
				hotel = new LinkedHashMap<>();
				hotel.put("hotelCode", r.hotelCode());
				hotel.put("hotelName", r.hotelName());
				hotel.put("roomTypes", new ArrayList<Map<String, Object>>());
				hotels.put(r.hotelCode(), hotel);
			}
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> roomTypes = (List<Map<String, Object>>) hotel.get("roomTypes");
			roomTypes.add(Map.of(
					"roomTypeCode", r.roomTypeCode(),
					"roomTypeName", r.roomTypeName(),
					"maxOccupancy", r.maxOccupancy()));
		}
		return Map.of("items", new ArrayList<>(hotels.values()));
	}

	@GetMapping(value = "/a/v1/availability", produces = "application/json")
	public ResponseEntity<Object> availabilityA(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
		ResponseEntity<Object> failure = failureResponse("a", "availability");
		if (failure != null) {
			return failure;
		}
		return ResponseEntity.ok(availabilityBodyA(nights(checkIn, checkOut)));
	}

	private Map<String, Object> availabilityBodyA(List<LocalDate> nights) {
		List<Map<String, Object>> items = new ArrayList<>();
		for (MockData.RoomA r : MockData.SUPPLIER_A) {
			List<Map<String, Object>> dailyRates = new ArrayList<>();
			for (int i = 0; i < nights.size(); i++) {
				Map<String, Object> d = new LinkedHashMap<>();
				d.put("date", nights.get(i).toString());
				d.put("remainingRooms", MockData.valueAt(r.remainingRooms(), i));
				d.put("nightlyRate", MockData.valueAt(r.nightlyRate(), i));
				d.put("taxAmount", MockData.valueAt(r.taxAmount(), i));
				dailyRates.add(d);
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("hotelCode", r.hotelCode());
			item.put("hotelName", r.hotelName());
			item.put("roomTypeCode", r.roomTypeCode());
			item.put("roomTypeName", r.roomTypeName());
			item.put("maxOccupancy", r.maxOccupancy());
			item.put("breakfastIncluded", r.breakfastIncluded());
			item.put("currency", MockData.CURRENCY);
			item.put("dailyRates", dailyRates);
			items.add(item);
		}
		return Map.of("items", items);
	}

	// ---- Supplier B ----

	@GetMapping(value = "/b/api/properties", produces = "application/json")
	public ResponseEntity<Object> propertiesB() {
		ResponseEntity<Object> failure = failureResponse("b", "catalog");
		if (failure != null) {
			return failure;
		}
		return ResponseEntity.ok(propertiesBodyB());
	}

	private Map<String, Object> propertiesBodyB() {
		Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
		for (MockData.RoomB r : MockData.SUPPLIER_B) {
			Map<String, Object> property = properties.get(r.propertyId());
			if (property == null) {
				property = new LinkedHashMap<>();
				property.put("propertyId", r.propertyId());
				property.put("propertyName", r.propertyName());
				property.put("rooms", new ArrayList<Map<String, Object>>());
				properties.put(r.propertyId(), property);
			}
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> rooms = (List<Map<String, Object>>) property.get("rooms");
			rooms.add(Map.of(
					"roomId", r.roomId(),
					"roomName", r.roomName(),
					"maxOccupancy", r.maxOccupancy()));
		}
		return successB(Map.of("items", new ArrayList<>(properties.values())));
	}

	@GetMapping(value = "/b/api/search", produces = "application/json")
	public ResponseEntity<Object> searchB(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
		ResponseEntity<Object> failure = failureResponse("b", "availability");
		if (failure != null) {
			return failure;
		}
		return ResponseEntity.ok(searchBodyB(nights(checkIn, checkOut)));
	}

	private Map<String, Object> searchBodyB(List<LocalDate> nights) {
		List<Map<String, Object>> items = new ArrayList<>();
		for (MockData.RoomB r : MockData.SUPPLIER_B) {
			List<Map<String, Object>> inventory = new ArrayList<>();
			for (int i = 0; i < nights.size(); i++) {
				Map<String, Object> d = new LinkedHashMap<>();
				d.put("date", nights.get(i).toString());
				d.put("remainingRooms", MockData.valueAt(r.remainingRooms(), i));
				inventory.add(d);
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("propertyId", r.propertyId());
			item.put("propertyName", r.propertyName());
			item.put("roomId", r.roomId());
			item.put("roomName", r.roomName());
			item.put("maxOccupancy", r.maxOccupancy());
			item.put("breakfastIncluded", r.breakfastIncluded());
			item.put("currency", MockData.CURRENCY);
			item.put("totalPrice", (long) r.grossPerNight() * nights.size());
			item.put("taxIncluded", true);
			item.put("inventory", inventory);
			items.add(item);
		}
		return successB(Map.of("items", items));
	}

	// ---- failure modes ----

	/**
	 * ERROR: A는 HTTP 503, B는 HTTP 200 + resultCode E503
	 * NO_RESPONSE: 연결은 유지하고 응답하지 않음 (600초 대기 후 빈 응답)
	 * DELAY: 설정 시간 대기 후 null (정상 응답으로 이어짐)
	 */
	private ResponseEntity<Object> failureResponse(String supplier, String api) {
		FailureMode mode = failureModes.modeOf(supplier, api);
		if (mode == FailureMode.ERROR) {
			if ("a".equals(supplier)) {
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBodyA());
			}
			return ResponseEntity.ok(errorBodyB());
		}
		if (mode == FailureMode.NO_RESPONSE) {
			sleep(NO_RESPONSE_SLEEP_MS);
			return ResponseEntity.ok(Map.of());
		}
		if (mode == FailureMode.DELAY) {
			sleep(delayMs);
		}
		return null;
	}

	private static Map<String, Object> errorBodyA() {
		return Map.of("error", "SERVICE_UNAVAILABLE", "message", "temporarily unavailable");
	}

	private static Map<String, Object> errorBodyB() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("resultCode", "E503");
		body.put("resultMessage", "TEMPORARILY_UNAVAILABLE");
		body.put("data", null);
		return body;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ---- helpers ----

	private static List<LocalDate> nights(LocalDate checkIn, LocalDate checkOut) {
		LocalDate in = checkIn != null ? checkIn : LocalDate.now();
		LocalDate out = checkOut != null && checkOut.isAfter(in) ? checkOut : in.plusDays(2);
		return in.datesUntil(out).toList();
	}

	private static Map<String, Object> successB(Map<String, Object> data) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("resultCode", "0000");
		body.put("resultMessage", "SUCCESS");
		body.put("data", data);
		return body;
	}
}
