package com.stayhub.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stayhub.application.SearchResult;
import com.stayhub.application.StaySearchService;
import com.stayhub.domain.SearchQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 통합 검색 API. 검색 조건은 날짜와 인원만, 대상은 보유 숙소 전체
 */
@RestController
@RequestMapping("/api/v1/stays")
@RequiredArgsConstructor
@Tag(name = "Stays", description = "통합 숙박 상품 검색")
public class StaySearchController {

	private final StaySearchService searchService;

	@Operation(
			summary = "통합 검색",
			description = "여러 공급사의 재고·요금을 병렬 조회해 표준 상품 목록으로 반환. "
					+ "일부 공급사가 실패해도 200이며 suppliers[]에 상태와 사유가 표기됨")
	@GetMapping("/search")
	public SearchResponse search(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
			@RequestParam int adults,
			@RequestParam int children) {
		SearchQuery query = new SearchQuery(checkIn, checkOut, adults, children);
		SearchResult result = searchService.search(query);
		return new SearchResponse(
				query.checkIn(), query.checkOut(), query.nights(), query.adults(), query.children(),
				result.suppliers().stream().map(SupplierStatusResponse::from).toList(),
				result.items());
	}
}
