package com.stayhub.api;

import java.time.LocalDate;
import java.util.List;

import com.stayhub.domain.StayOffer;

/**
 * 통합 검색 응답 (검색 조건 + 공급사별 조회 상태 + 표준 상품 목록)
 * items는 도메인의 StayOffer를 그대로 직렬화
 */
public record SearchResponse(
		LocalDate checkIn,
		LocalDate checkOut,
		int nights,
		int adults,
		int children,
		List<SupplierStatusResponse> suppliers,
		List<StayOffer> items) {
}
