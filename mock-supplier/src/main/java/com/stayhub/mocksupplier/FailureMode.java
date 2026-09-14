package com.stayhub.mocksupplier;


enum FailureMode {
	NORMAL("normal"),
	ERROR("error"),
	NO_RESPONSE("no-response"),
	DELAY("delay");

	private final String value;

	FailureMode(String value) {
		this.value = value;
	}

	String value() {
		return value;
	}

	static FailureMode from(String value) {
		for (FailureMode m : values()) {
			if (m.value.equals(value)) {
				return m;
			}
		}
		throw new IllegalArgumentException("unknown mode: " + value);
	}
}
