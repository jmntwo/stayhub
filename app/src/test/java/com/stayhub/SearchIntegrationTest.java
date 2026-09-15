package com.stayhub;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * 검색 API를 처음부터 끝까지 실제로 돌려보는 테스트
 * - 들어오는 쪽: MockMvc가 손님처럼 GET /api/v1/stays/search를 보냄
 * - 나가는 쪽: WireMock이 공급사 A·B 서버 역할
 *
 * 앱이 뜰 때의 매핑 동기화도 WireMock의 목록 API를 받아 진행
 * 한 공급사가 실패해도 나머지로 200 응답하고 suppliers[]에 사유가 표기되는지가 핵심
 *
 * B의 응답 타임아웃을 500ms로 줄여 무응답 케이스가 몇 초씩 기다리지 않게 함
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchIntegrationTest {

	static final WireMockServer server = new WireMockServer(options().dynamicPort());
	static final String SEARCH = "/api/v1/stays/search?checkIn=2026-10-01&checkOut=2026-10-03&adults=2&children=0";

	@BeforeAll
	static void startServer() {
		server.start();
		stubCatalogs();
	}

	@AfterAll
	static void stopServer() {
		server.stop();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		// 같은 JVM의 다른 컨텍스트와 인메모리 DB를 공유하지 않도록 컨텍스트마다 다른 DB 이름
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + java.util.UUID.randomUUID());
		registry.add("stayhub.suppliers.a.base-url", server::baseUrl);
		registry.add("stayhub.suppliers.b.base-url", server::baseUrl);
		registry.add("stayhub.suppliers.b.response-timeout", () -> "500ms");
		registry.add("stayhub.search.deadline", () -> "2s");
	}

	@Autowired
	MockMvc mvc;

	@BeforeEach
	void resetAvailability() {
		server.resetMappings();
		stubCatalogs();
		stubAvailabilityA();
	}

	@Test
	void 두_공급사_정상이면_모두_OK이고_상품이_합쳐짐() throws Exception {
		stubSearchB(okJson(B_SEARCH_OK));

		mvc.perform(get(SEARCH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='A')].status").value("OK"))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='B')].status").value("OK"))
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[?(@.supplier=='A')].price.total").value(286_000))
				.andExpect(jsonPath("$.items[?(@.supplier=='B')].price.total").value(310_000))
				.andExpect(jsonPath("$.items[?(@.supplier=='B')].breakfastIncluded").value(true));
	}

	@Test
	void B가_HTTP_503이면_A_결과만으로_200_응답하고_B는_UNAVAILABLE() throws Exception {
		stubSearchB(aResponse().withStatus(503));

		expectPartialFailure("UNAVAILABLE");
	}

	@Test
	void B가_HTTP_200에_resultCode_E503이면_A_결과만으로_응답하고_B는_UNAVAILABLE() throws Exception {
		stubSearchB(okJson("{\"resultCode\":\"E503\",\"resultMessage\":\"TEMPORARILY_UNAVAILABLE\",\"data\":null}"));

		expectPartialFailure("UNAVAILABLE");
	}

	@Test
	void B가_무응답이면_타임아웃_안에_A_결과만으로_응답하고_B는_TIMEOUT() throws Exception {
		stubSearchB(okJson(B_SEARCH_OK).withFixedDelay(3_000));

		long started = System.currentTimeMillis();
		expectPartialFailure("TIMEOUT");
		long elapsed = System.currentTimeMillis() - started;

		assertThat(elapsed).isLessThan(2_000);
	}

	@Test
	void 둘_다_실패하면_빈_결과와_공급사별_사유로_200() throws Exception {
		server.stubFor(WireMock.get(urlPathEqualTo("/a/v1/availability")).willReturn(aResponse().withStatus(500)));
		stubSearchB(okJson("{\"resultCode\":\"E500\",\"resultMessage\":\"INTERNAL\",\"data\":null}"));

		mvc.perform(get(SEARCH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items", hasSize(0)))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='A')].status").value("FAILED"))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='A')].reason").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='B')].status").value("FAILED"))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='B')].reason").value("UNAVAILABLE"));
	}

	private void expectPartialFailure(String reasonB) throws Exception {
		mvc.perform(get(SEARCH))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='A')].status").value("OK"))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='B')].status").value("FAILED"))
				.andExpect(jsonPath("$.suppliers[?(@.supplier=='B')].reason").value(reasonB))
				.andExpect(jsonPath("$.items", hasSize(1)))
				.andExpect(jsonPath("$.items[0].supplier").value("A"));
	}

	// ---- WireMock 스텁 ----

	private static void stubCatalogs() {
		server.stubFor(WireMock.get(urlPathEqualTo("/a/v1/hotels")).willReturn(okJson("""
				{"items":[{"hotelCode":"A-2001","hotelName":"Harbor View Hotel",
				  "roomTypes":[{"roomTypeCode":"OCN-TWN","roomTypeName":"Ocean Twin","maxOccupancy":2}]}]}
				""")));
		server.stubFor(WireMock.get(urlPathEqualTo("/b/api/properties")).willReturn(okJson("""
				{"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[
				  {"propertyId":"P-3101","propertyName":"Harbor View Hotel",
				   "rooms":[{"roomId":"R-11","roomName":"Ocean Twin Room","maxOccupancy":2}]}]}}
				""")));
	}

	private static void stubAvailabilityA() {
		server.stubFor(WireMock.get(urlPathEqualTo("/a/v1/availability")).willReturn(okJson("""
				{"items":[{"hotelCode":"A-2001","hotelName":"Harbor View Hotel",
				  "roomTypeCode":"OCN-TWN","roomTypeName":"Ocean Twin","maxOccupancy":2,
				  "breakfastIncluded":false,"currency":"KRW",
				  "dailyRates":[
				    {"date":"2026-10-01","remainingRooms":2,"nightlyRate":130000,"taxAmount":13000},
				    {"date":"2026-10-02","remainingRooms":2,"nightlyRate":130000,"taxAmount":13000}]}]}
				""")));
	}

	private static void stubSearchB(com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder response) {
		server.stubFor(WireMock.get(urlPathEqualTo("/b/api/search")).willReturn(response));
	}

	static final String B_SEARCH_OK = """
			{"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[
			  {"propertyId":"P-3101","propertyName":"Harbor View Hotel","roomId":"R-11","roomName":"Ocean Twin Room",
			   "maxOccupancy":2,"breakfastIncluded":true,"currency":"KRW","totalPrice":310000,"taxIncluded":true,
			   "inventory":[{"date":"2026-10-01","remainingRooms":1},{"date":"2026-10-02","remainingRooms":3}]}]}}
			""";
}
