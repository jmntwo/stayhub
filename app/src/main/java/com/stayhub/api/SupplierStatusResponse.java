package com.stayhub.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stayhub.application.SupplierOutcome;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupplierStatusResponse(String supplier, String status, String reason) {

	static SupplierStatusResponse from(SupplierOutcome outcome) {
		return new SupplierStatusResponse(
				outcome.supplier().name(),
				outcome.status().name(),
				outcome.reason() != null ? outcome.reason().name() : null);
	}
}
