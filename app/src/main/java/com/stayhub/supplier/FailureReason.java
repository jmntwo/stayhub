package com.stayhub.supplier;

/**
 * 공급사 연동 실패 사유. 어댑터가 던지는 예외와 검색 응답의 suppliers[].reason이 같은 값을 쓸거임
 */
public enum FailureReason {
	TIMEOUT(true),
	UNAVAILABLE(true),
	REJECTED(false),
	NO_MAPPING(false);

	private final boolean retryable;

	FailureReason(boolean retryable) {
		this.retryable = retryable;
	}

	public boolean retryable() {
		return retryable;
	}
}
