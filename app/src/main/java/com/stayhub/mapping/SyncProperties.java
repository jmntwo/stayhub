package com.stayhub.mapping;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매핑 동기화 설정
 * interval: 정규 갱신 주기
 * retryInitial → retryMax: 실패 후 재시도 간격(2배씩 증가, 상한)
 * retryMaxAttempts: 매핑이 이미 있을 때 재시도 횟수 상한. 매핑이 없으면 무제한
 */
@ConfigurationProperties(prefix = "stayhub.sync")
public record SyncProperties(Duration interval, Duration retryInitial, Duration retryMax, int retryMaxAttempts) {

	public SyncProperties {
		if (interval == null) {
			interval = Duration.ofHours(6);
		}
		if (retryInitial == null) {
			retryInitial = Duration.ofSeconds(30);
		}
		if (retryMax == null) {
			retryMax = Duration.ofMinutes(5);
		}
		if (retryMaxAttempts <= 0) {
			retryMaxAttempts = 5;
		}
	}
}
