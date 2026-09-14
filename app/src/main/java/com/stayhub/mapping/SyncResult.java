package com.stayhub.mapping;

import com.stayhub.domain.Supplier;

public record SyncResult(
		Supplier supplier,
		int properties,
		int roomTypes,
		int deactivatedProperties,
		int deactivatedRoomTypes) {
}
