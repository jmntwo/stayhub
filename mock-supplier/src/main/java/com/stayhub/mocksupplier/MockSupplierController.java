package com.stayhub.mocksupplier;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요청 파라미터 중 checkIn, checkOut만 날짜 생성에 쓰고 나머지는 무시
 */
@RestController
public class MockSupplierController {

	// ---- Supplier A ----

	@GetMapping(value = "/a/v1/hotels", produces = "application/json")
	public Map<String, Object> hotelsA() {
		Map<String, Map<String, Object>> hotels = new LinkedHashMap<>();
		for (MockData.RoomA r : MockData.SUPPLIER_A) {
			Map<String, Object> hotel = hotels.computeIfAbsent(r.hotelCode(), code -> {
				Map<String, Object> h = new LinkedHashMap<>();
				h.put("hotelCode", code);
				h.put("hotelName", r.hotelName());
				h.put("roomTypes", new ArrayList<Map<String, Object>>());
				return h;
			});
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
	public Map<String, Object> availabilityA(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
		List<LocalDate> nights = nights(checkIn, checkOut);
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
	public Map<String, Object> propertiesB() {
		Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
		for (MockData.RoomB r : MockData.SUPPLIER_B) {
			Map<String, Object> property = properties.computeIfAbsent(r.propertyId(), id -> {
				Map<String, Object> p = new LinkedHashMap<>();
				p.put("propertyId", id);
				p.put("propertyName", r.propertyName());
				p.put("rooms", new ArrayList<Map<String, Object>>());
				return p;
			});
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
	public Map<String, Object> searchB(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
		List<LocalDate> nights = nights(checkIn, checkOut);
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
