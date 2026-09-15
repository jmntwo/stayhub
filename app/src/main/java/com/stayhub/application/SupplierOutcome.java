package com.stayhub.application;

import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;

public record SupplierOutcome(Supplier supplier, Status status, FailureReason reason) {

	public enum Status {
		OK,
		PARTIAL,
		FAILED
	}

	static SupplierOutcome ok(Supplier supplier) {
		return new SupplierOutcome(supplier, Status.OK, null);
	}

	static SupplierOutcome failed(Supplier supplier, FailureReason reason) {
		return new SupplierOutcome(supplier, Status.FAILED, reason);
	}

	static SupplierOutcome partial(Supplier supplier, FailureReason reason) {
		return new SupplierOutcome(supplier, Status.PARTIAL, reason);
	}
}
