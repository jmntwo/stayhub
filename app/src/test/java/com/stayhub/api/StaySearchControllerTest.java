package com.stayhub.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stayhub.application.SearchResult;
import com.stayhub.application.StaySearchService;
import com.stayhub.application.SupplierOutcome;
import com.stayhub.domain.Price;
import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.StayOffer;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;

/** 컨트롤러 계층 테스트만 수행
 * 서비스는 가짜로 두고 파라미터 검증(400)과 응답 JSON 구조를 확인 */
@WebMvcTest(StaySearchController.class)
class StaySearchControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	StaySearchService searchService;

	static final String OK_URL = "/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0";

	@Test
	void 정상_응답_구조() throws Exception {
		SearchQuery query = new SearchQuery(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2, 0);
		StayOffer offer = new StayOffer(1, "Harbor View Hotel", 1, "Ocean Twin", Supplier.A, 2, 2,
				new Price(286_000, "KRW", null), false);
		when(searchService.search(any())).thenReturn(new SearchResult(query, List.of(offer), List.of(
				new SupplierOutcome(Supplier.A, SupplierOutcome.Status.OK, null),
				new SupplierOutcome(Supplier.B, SupplierOutcome.Status.FAILED, FailureReason.TIMEOUT))));

		mvc.perform(get(OK_URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nights").value(2))
				.andExpect(jsonPath("$.suppliers[0].supplier").value("A"))
				.andExpect(jsonPath("$.suppliers[0].status").value("OK"))
				.andExpect(jsonPath("$.suppliers[0].reason").doesNotExist())
				.andExpect(jsonPath("$.suppliers[1].status").value("FAILED"))
				.andExpect(jsonPath("$.suppliers[1].reason").value("TIMEOUT"))
				.andExpect(jsonPath("$.items[0].propertyId").value(1))
				.andExpect(jsonPath("$.items[0].supplier").value("A"))
				.andExpect(jsonPath("$.items[0].price.total").value(286_000))
				.andExpect(jsonPath("$.items[0].price.nightly").value((Object) null));
	}

	@Test
	void 체크아웃이_체크인보다_앞이면_400() throws Exception {
		mvc.perform(get("/api/v1/stays/search?checkIn=2026-10-03&checkOut=2026-10-01&adults=2&children=0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("checkOut must be after checkIn"));
	}

	@Test
	void 성인이_0이면_400() throws Exception {
		mvc.perform(get("/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=0&children=1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
	}

	@Test
	void 파라미터가_빠지면_400() throws Exception {
		mvc.perform(get("/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("children is required"));
	}

	@Test
	void 날짜_형식이_틀리면_400() throws Exception {
		mvc.perform(get("/api/v1/stays/search?checkIn=2026-10-1&checkOut=2026-10-03&adults=2&children=0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("checkIn has invalid format"));
	}
}
