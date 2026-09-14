package com.stayhub.supplier;

import com.stayhub.domain.Supplier;

public class SupplierException extends RuntimeException {

	private final Supplier supplier;
	private final FailureReason reason;

	public SupplierException(Supplier supplier, FailureReason reason, String detail) {
		super(supplier + " " + reason + (detail != null ? ": " + detail : ""));
		this.supplier = supplier;
		this.reason = reason;
	}

	public SupplierException(Supplier supplier, FailureReason reason, String detail, Throwable cause) {
		this(supplier, reason, detail);
		initCause(cause);
	}

	public Supplier supplier() {
		return supplier;
	}

	public FailureReason reason() {
		return reason;
	}
}
