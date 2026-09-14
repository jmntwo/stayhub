package com.stayhub.supplier;

import java.util.List;

import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.Supplier;

import reactor.core.publisher.Mono;

/**
 * 두 메서드 모두 실패 시 SupplierException으로 끝나는 Mono를 돌려줌
 * 신규 공급사 추가 = 이 인터페이스 구현체 하나 + 설정
 */
public interface SupplierAdapter {

	Supplier supplier();

	/** 숙소 목록 API */
	Mono<List<CatalogEntry>> fetchCatalog();

	/** 재고·요금 API */
	Mono<List<SupplierOffer>> fetchAvailability(List<String> hotelCodes, SearchQuery query);
}
