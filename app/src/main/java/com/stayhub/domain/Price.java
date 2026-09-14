package com.stayhub.domain;

import java.util.List;

/**
 * total은 요청 기간 전체 총액, 세금 포함, 통화 최소 단위 정수
 * nightly는 날짜별 요금을 제공하는 공급사만 채우고 없으면 null
 */
public record Price(long total, String currency, List<NightlyRate> nightly) {
}
