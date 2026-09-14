package com.stayhub.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record SearchQuery(LocalDate checkIn, LocalDate checkOut, int adults, int children) {

	public SearchQuery {
		if (checkIn == null || checkOut == null) {
			throw new IllegalArgumentException("checkIn and checkOut are required");
		}
		if (!checkOut.isAfter(checkIn)) {
			throw new IllegalArgumentException("checkOut must be after checkIn");
		}
		if (adults < 1) {
			throw new IllegalArgumentException("adults must be at least 1");
		}
		if (children < 0) {
			throw new IllegalArgumentException("children must not be negative");
		}
	}

	public int nights() {
		return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
	}
}
