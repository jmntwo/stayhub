package com.stayhub.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import com.stayhub.domain.Supplier;

@SpringBootTest
class SupplierPropertiesTest {

	@Autowired
	SupplierProperties properties;

	@Autowired
	Map<Supplier, WebClient> supplierWebClients;

	@Test
	void 공급사별_설정이_바인딩된다() {
		SupplierProperties.Endpoint a = properties.of(Supplier.A);

		assertThat(a.baseUrl()).isEqualTo("http://localhost:9090");
		assertThat(a.apiKey()).isEqualTo("mock-key-a");
		assertThat(a.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
		assertThat(a.responseTimeout()).isEqualTo(Duration.ofSeconds(3));
	}

	@Test
	void 공급사마다_WebClient가_하나씩_만들어진다() {
		assertThat(supplierWebClients).containsKeys(Supplier.A, Supplier.B);
	}
}
