package com.stayhub.mapping;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;

/**
 * 앱을 통째로 띄워 기동 시 동기화를 확인
 * A 목록 API는 정상, B 목록 API는 장애 상태로 두고 앱이 뜨는지, A만 매핑되는지, B는 실패로 기록되는지
 */
@SpringBootTest
class MappingSyncSchedulerTest {

	static WireMockServer server = new WireMockServer(options().dynamicPort());

	@BeforeAll
	static void startServer() {
		server.start();
		server.stubFor(get(urlPathEqualTo("/a/v1/hotels")).willReturn(okJson("""
				{"items":[{"hotelCode":"A-2001","hotelName":"Harbor View Hotel",
				  "roomTypes":[{"roomTypeCode":"OCN-TWN","roomTypeName":"Ocean Twin","maxOccupancy":2},
				               {"roomTypeCode":"STD-DBL","roomTypeName":"Standard Double","maxOccupancy":2}]}]}
				""")));
		server.stubFor(get(urlPathEqualTo("/b/api/properties")).willReturn(aResponse().withStatus(503)));
	}

	@AfterAll
	static void stopServer() {
		server.stop();
	}

	@DynamicPropertySource
	static void supplierUrls(DynamicPropertyRegistry registry) {
		// 같은 JVM의 다른 컨텍스트와 인메모리 DB를 공유하지 않도록 컨텍스트마다 다른 DB 이름
		registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + java.util.UUID.randomUUID());
		registry.add("stayhub.suppliers.a.base-url", server::baseUrl);
		registry.add("stayhub.suppliers.b.base-url", server::baseUrl);
	}

	@Autowired
	SyncStatus status;

	@Autowired
	PropertyRepository properties;

	@Autowired
	RoomTypeRepository roomTypes;

	@Test
	void 기동_시_동기화가_돌아_정상인_공급사만_매핑되고_장애인_공급사는_실패로_기록() {
		assertThat(status.hasMapping(Supplier.A)).isTrue();
		assertThat(properties.findBySupplierAndSupplierCode(Supplier.A, "A-2001")).isPresent();
		assertThat(roomTypes.count()).isEqualTo(2);

		assertThat(status.hasMapping(Supplier.B)).isFalse();
		SyncStatus.State b = status.of(Supplier.B).orElseThrow();
		assertThat(b.lastFailureReason()).isEqualTo(FailureReason.UNAVAILABLE);
		assertThat(b.consecutiveFailures()).isEqualTo(1);
		assertThat(b.nextAttemptAt()).isAfter(b.lastFailureAt());
	}
}
