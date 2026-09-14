package com.stayhub.supplier.b;

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

/** WireMock으로 B를 흉내내어 HTTP 200 + resultCode 실패가 SupplierException으로 바뀌는지 확인 */
class SupplierBAdapterTest {

	static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);

	WireMockServer server;
	SupplierBAdapter adapter;
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
		adapter = new SupplierBAdapter(client);
	}

	@AfterEach
	void tearDown() {
		server.stop();
	}

	@Test
	void 정상_응답을_표준_형식으로_변환하고_요청_파라미터를_스펙대로_보냄() {
		server.stubFor(get(urlPathEqualTo("/b/api/search"))
				.withQueryParam("propertyIds", equalTo("P-3101,P-3102"))
				.withQueryParam("checkIn", equalTo("2026-10-01"))
				.withQueryParam("checkOut", equalTo("2026-10-03"))
				.withQueryParam("adults", equalTo("2"))
				.withQueryParam("children", equalTo("0"))
				.withHeader("X-Api-Key", equalTo("test-key"))
				.willReturn(okJson("""
						{"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[
						  {"propertyId":"P-3101","propertyName":"Harbor View Hotel","roomId":"R-11","roomName":"Ocean Twin Room",
						   "maxOccupancy":2,"breakfastIncluded":true,"currency":"KRW","totalPrice":310000,"taxIncluded":true,
						   "inventory":[{"date":"2026-10-01","remainingRooms":1},{"date":"2026-10-02","remainingRooms":3}]}]}}
						""")));

		List<SupplierOffer> offers = adapter.fetchAvailability(List.of("P-3101", "P-3102"), query).block();

		assertThat(offers).hasSize(1);
		assertThat(offers.get(0).supplier()).isEqualTo(Supplier.B);
		assertThat(offers.get(0).hotelCode()).isEqualTo("P-3101");
		assertThat(offers.get(0).price().total()).isEqualTo(310_000);
		assertThat(offers.get(0).price().nightly()).isNull();
		assertThat(offers.get(0).availableRooms()).isEqualTo(1);
	}

	@Test
	void 숙소_목록을_가져옴() {
		server.stubFor(get(urlPathEqualTo("/b/api/properties")).willReturn(okJson("""
				{"resultCode":"0000","resultMessage":"SUCCESS","data":{"items":[
				  {"propertyId":"P-3101","propertyName":"Harbor View Hotel",
				   "rooms":[{"roomId":"R-11","roomName":"Ocean Twin Room","maxOccupancy":2}]}]}}
				""")));

		var catalog = adapter.fetchCatalog().block();

		assertThat(catalog).hasSize(1);
		assertThat(catalog.get(0).rooms()).hasSize(1);
	}

	@Test
	void HTTP_200이지만_resultCode_E5xx면_UNAVAILABLE() {
		server.stubFor(get(urlPathEqualTo("/b/api/search")).willReturn(okJson(
				"{\"resultCode\":\"E503\",\"resultMessage\":\"TEMPORARILY_UNAVAILABLE\",\"data\":null}")));

		assertFailure(FailureReason.UNAVAILABLE);
	}

	@Test
	void HTTP_200이지만_resultCode_E4xx면_REJECTED() {
		server.stubFor(get(urlPathEqualTo("/b/api/search")).willReturn(okJson(
				"{\"resultCode\":\"E400\",\"resultMessage\":\"BAD_REQUEST\",\"data\":null}")));

		assertFailure(FailureReason.REJECTED);
	}

	@Test
	void HTTP_5xx도_UNAVAILABLE() {
		server.stubFor(get(urlPathEqualTo("/b/api/search")).willReturn(aResponse().withStatus(502)));

		assertFailure(FailureReason.UNAVAILABLE);
	}

	@Test
	void 응답_지연이_타임아웃을_넘으면_TIMEOUT() {
		server.stubFor(get(urlPathEqualTo("/b/api/search"))
				.willReturn(okJson("{\"resultCode\":\"0000\",\"data\":{\"items\":[]}}")
						.withFixedDelay((int) RESPONSE_TIMEOUT.toMillis() * 4)));

		assertFailure(FailureReason.TIMEOUT);
	}

	@Test
	void 연결이_끊기면_UNAVAILABLE() {
		server.stubFor(get(urlPathEqualTo("/b/api/search"))
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		assertFailure(FailureReason.UNAVAILABLE);
	}

	private void assertFailure(FailureReason expected) {
		assertThatThrownBy(() -> adapter.fetchAvailability(List.of("P-3101"), query).block())
				.isInstanceOfSatisfying(SupplierException.class, e -> {
					assertThat(e.supplier()).isEqualTo(Supplier.B);
					assertThat(e.reason()).isEqualTo(expected);
				});
	}
}
