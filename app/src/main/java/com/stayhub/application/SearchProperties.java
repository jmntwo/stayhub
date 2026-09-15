package com.stayhub.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 검색 설정
 * deadline: 공급사 하나의 조회에 허용하는 전체 시간. 어댑터 응답 타임아웃(3초)보다 크게
 * chunkSize: 재고·요금 API가 한 번에 받는 숙소 코드 상한
 * concurrencyPerSupplier: 한 공급사에 동시에 보내는 청크 수
 */
@ConfigurationProperties(prefix = "stayhub.search")
public record SearchProperties(Duration deadline, int chunkSize, int concurrencyPerSupplier) {

	public SearchProperties {
		if (deadline == null) {
			deadline = Duration.ofSeconds(5);
		}
		if (chunkSize <= 0) {
			chunkSize = 50;
		}
		if (concurrencyPerSupplier <= 0) {
			concurrencyPerSupplier = 4;
		}
	}
}
