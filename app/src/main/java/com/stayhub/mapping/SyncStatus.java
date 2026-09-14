package com.stayhub.mapping;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;

import lombok.RequiredArgsConstructor;

/**
 * 공급사별 동기화 상태
 *
 * 재시도 규칙
 * - 성공: 다음 시도는 정규 주기 뒤
 * - 실패, 매핑 없음: retryInitial부터 2배씩, retryMax 상한으로 무제한 재시도
 * - 실패, 매핑 있음: 같은 백오프로 retryMaxAttempts회까지, 그 뒤는 정규 주기까지 대기
 */
@Component
@RequiredArgsConstructor
public class SyncStatus {

	/** 공급사 하나의 상태. lastSuccessAt이 null이면 아직 매핑 없음 */
	public record State(
			Instant lastSuccessAt,
			Instant lastFailureAt,
			FailureReason lastFailureReason,
			int consecutiveFailures,
			Instant nextAttemptAt) {

		public boolean hasMapping() {
			return lastSuccessAt != null;
		}
	}

	private final SyncProperties properties;
	private final Map<Supplier, State> states = new ConcurrentHashMap<>();

	public void recordSuccess(Supplier supplier, Instant now) {
		states.put(supplier, new State(now, null, null, 0, now.plus(properties.interval())));
	}

	public void recordFailure(Supplier supplier, FailureReason reason, Instant now) {
		State previous = states.get(supplier);
		int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
		Instant lastSuccessAt = previous == null ? null : previous.lastSuccessAt();
		boolean hasMapping = lastSuccessAt != null;

		Instant next;
		if (hasMapping && failures > properties.retryMaxAttempts()) {
			next = now.plus(properties.interval());
		} else {
			next = now.plus(backoff(failures));
		}
		states.put(supplier, new State(lastSuccessAt, now, reason, failures, next));
	}

	/** 아직 기록이 없거나 다음 시도 시각이 지났으면 true */
	public boolean isDue(Supplier supplier, Instant now) {
		State state = states.get(supplier);
		return state == null || !now.isBefore(state.nextAttemptAt());
	}

	public boolean hasMapping(Supplier supplier) {
		State state = states.get(supplier);
		return state != null && state.hasMapping();
	}

	public Optional<State> of(Supplier supplier) {
		return Optional.ofNullable(states.get(supplier));
	}

	/** n번째 연속 실패의 대기 시간. initial × 2^(n-1), 상한 retryMax */
	private Duration backoff(int failures) {
		Duration delay = properties.retryInitial();
		for (int i = 1; i < failures && delay.compareTo(properties.retryMax()) < 0; i++) {
			delay = delay.multipliedBy(2);
		}
		return delay.compareTo(properties.retryMax()) > 0 ? properties.retryMax() : delay;
	}
}
