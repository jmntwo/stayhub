package com.stayhub.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;

class SyncStatusTest {

	SyncProperties props = new SyncProperties(Duration.ofHours(6), Duration.ofSeconds(30), Duration.ofMinutes(5), 5);
	SyncStatus status = new SyncStatus(props);
	Instant t0 = Instant.parse("2026-09-15T00:00:00Z");

	@Test
	void 기록이_없으면_바로_동기화_대상() {
		assertThat(status.isDue(Supplier.A, t0)).isTrue();
		assertThat(status.hasMapping(Supplier.A)).isFalse();
	}

	@Test
	void 성공하면_다음_시도는_정규_주기_뒤() {
		status.recordSuccess(Supplier.A, t0);

		assertThat(status.hasMapping(Supplier.A)).isTrue();
		assertThat(status.isDue(Supplier.A, t0.plus(Duration.ofHours(5)))).isFalse();
		assertThat(status.isDue(Supplier.A, t0.plus(Duration.ofHours(6)))).isTrue();
	}

	@Test
	void 매핑이_없을_때_실패는_백오프로_무제한_재시도() {
		Instant now = t0;
		Duration[] expected = {
				Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120),
				Duration.ofSeconds(240), Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5)};

		for (Duration delay : expected) {
			status.recordFailure(Supplier.A, FailureReason.UNAVAILABLE, now);
			Instant next = status.of(Supplier.A).orElseThrow().nextAttemptAt();
			assertThat(Duration.between(now, next)).isEqualTo(delay);
			now = next;
		}
		assertThat(status.hasMapping(Supplier.A)).isFalse();
		assertThat(status.isDue(Supplier.A, now)).isTrue();
	}

	@Test
	void 매핑이_있을_때_실패는_횟수_상한을_넘으면_정규_주기까지_대기() {
		status.recordSuccess(Supplier.A, t0);
		Instant now = t0.plus(Duration.ofHours(6));

		for (int i = 1; i <= 5; i++) {
			status.recordFailure(Supplier.A, FailureReason.TIMEOUT, now);
			Instant next = status.of(Supplier.A).orElseThrow().nextAttemptAt();
			assertThat(Duration.between(now, next)).isLessThanOrEqualTo(Duration.ofMinutes(5));
			now = next;
		}

		status.recordFailure(Supplier.A, FailureReason.TIMEOUT, now);
		SyncStatus.State state = status.of(Supplier.A).orElseThrow();

		assertThat(state.consecutiveFailures()).isEqualTo(6);
		assertThat(Duration.between(now, state.nextAttemptAt())).isEqualTo(Duration.ofHours(6));
		assertThat(state.hasMapping()).isTrue();
		assertThat(state.lastFailureReason()).isEqualTo(FailureReason.TIMEOUT);
	}

	@Test
	void 성공하면_연속_실패_횟수가_초기화() {
		status.recordFailure(Supplier.B, FailureReason.UNAVAILABLE, t0);
		status.recordFailure(Supplier.B, FailureReason.UNAVAILABLE, t0.plusSeconds(30));
		status.recordSuccess(Supplier.B, t0.plusSeconds(90));

		SyncStatus.State state = status.of(Supplier.B).orElseThrow();
		assertThat(state.consecutiveFailures()).isZero();
		assertThat(state.lastFailureReason()).isNull();
	}

	@Test
	void 공급사별로_독립() {
		status.recordFailure(Supplier.A, FailureReason.UNAVAILABLE, t0);
		status.recordSuccess(Supplier.B, t0);

		assertThat(status.hasMapping(Supplier.A)).isFalse();
		assertThat(status.hasMapping(Supplier.B)).isTrue();
	}
}
