package com.stayhub.supplier.a;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;
import com.stayhub.supplier.SupplierException;
import com.stayhub.supplier.SupplierOffer;

import reactor.netty.http.client.HttpClient;

/** WireMock으로 A를 흉내내어 HTTP 실패가 SupplierException으로 바뀌는지 확인 */
class SupplierAAdapterTest {

	static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);

	WireMockServer server;
	SupplierAAdapter adapter;
	SearchQuery query = new SearchQuery(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2, 0);

	@BeforeEach
	void setUp() {
		server = new WireMockServer(options().dynamicPort());
		server.start();
		WebClient client = WebClient.builder()
				.baseUrl(server.baseUrl())
				.defaultHeader("X-Api-Key", "test-key")
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(RESPONSE_TIMEOUT)))
				.build();
		adapter = new SupplierAAdapter(client);
	}

	@AfterEach
	void tearDown() {
		server.stop();
	}

	@Test
	void 정상_응답을_표준_형식으로_변환하고_요청_파라미터를_스펙대로_보냄() {
		server.stubFor(get(urlPathEqualTo("/a/v1/availability"))
				.withQueryParam("hotelCodes", equalTo("A-2001,A-2002"))
				.withQueryParam("checkIn", equalTo("2026-10-01"))
				.withQueryParam("checkOut", equalTo("2026-10-03"))
				.withQueryParam("adults", equalTo("2"))
				.withQueryParam("children", equalTo("0"))
				.withHeader("X-Api-Key", equalTo("test-key"))
				.willReturn(okJson("""
						{"items":[{"hotelCode":"A-2001","hotelName":"Harbor View Hotel",
						  "roomTypeCode":"OCN-TWN","roomTypeName":"Ocean Twin","maxOccupancy":2,
						  "breakfastIncluded":false,"currency":"KRW",
						  "dailyRates":[
						    {"date":"2026-10-01","remainingRooms":2,"nightlyRate":130000,"taxAmount":13000},
						    {"date":"2026-10-02","remainingRooms":2,"nightlyRate":130000,"taxAmount":13000}]}]}
						""")));

		List<SupplierOffer> offers = adapter.fetchAvailability(List.of("A-2001", "A-2002"), query).block();

		assertThat(offers).hasSize(1);
		assertThat(offers.get(0).supplier()).isEqualTo(Supplier.A);
		assertThat(offers.get(0).hotelCode()).isEqualTo("A-2001");
		assertThat(offers.get(0).price().total()).isEqualTo(286_000);
		assertThat(offers.get(0).availableRooms()).isEqualTo(2);
	}

	@Test
	void 숙소_목록을_가져옴() {
		server.stubFor(get(urlPathEqualTo("/a/v1/hotels")).willReturn(okJson("""
				{"items":[{"hotelCode":"A-2001","hotelName":"Harbor View Hotel",
				  "roomTypes":[{"roomTypeCode":"OCN-TWN","roomTypeName":"Ocean Twin","maxOccupancy":2}]}]}
				""")));

		var catalog = adapter.fetchCatalog().block();

		assertThat(catalog).hasSize(1);
		assertThat(catalog.get(0).rooms()).hasSize(1);
	}

	@Test
	void HTTP_5xx는_UNAVAILABLE() {
		server.stubFor(get(urlPathEqualTo("/a/v1/availability")).willReturn(aResponse().withStatus(503)
				.withBody("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"temporarily unavailable\"}")));

		assertFailure(FailureReason.UNAVAILABLE);
	}

	@Test
	void HTTP_4xx는_REJECTED() {
		server.stubFor(get(urlPathEqualTo("/a/v1/availability")).willReturn(aResponse().withStatus(400)
				.withBody("{\"error\":\"INVALID_DATE_RANGE\"}")));

		assertFailure(FailureReason.REJECTED);
	}

	@Test
	void 응답_지연이_타임아웃을_넘으면_TIMEOUT() {
		server.stubFor(get(urlPathEqualTo("/a/v1/availability"))
				.willReturn(okJson("{\"items\":[]}").withFixedDelay((int) RESPONSE_TIMEOUT.toMillis() * 4)));

		assertFailure(FailureReason.TIMEOUT);
	}

	@Test
	void 연결이_끊기면_UNAVAILABLE() {
		server.stubFor(get(urlPathEqualTo("/a/v1/availability"))
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertFailure(FailureReason.UNAVAILABLE);
	}

	@Test
	void 깨진_JSON은_UNAVAILABLE() {
		server.stubFor(get(urlPathEqualTo("/a/v1/availability")).willReturn(okJson("{not json")));

		assertFailure(FailureReason.UNAVAILABLE);
	}

	@Test
	void 숙소_코드_50개_초과는_호출_전에_거절() {
		List<String> tooMany = Collections.nCopies(51, "A-1");

		assertThatThrownBy(() -> adapter.fetchAvailability(tooMany, query))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private void assertFailure(FailureReason expected) {
		assertThatThrownBy(() -> adapter.fetchAvailability(List.of("A-2001"), query).block())
				.isInstanceOfSatisfying(SupplierException.class, e -> {
					assertThat(e.supplier()).isEqualTo(Supplier.A);
					assertThat(e.reason()).isEqualTo(expected);
				});
	}
}
