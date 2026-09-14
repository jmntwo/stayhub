package com.stayhub.domain;

import java.time.LocalDate;

/** 날짜별 요금. 세금 별도 금액을 주는 공급사만*/
public record NightlyRate(LocalDate date, long rate, long tax) {
}
