package com.stayhub.supplier;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.stayhub.domain.Supplier;

@ConfigurationProperties(prefix = "stayhub")
public record SupplierProperties(Map<Supplier, Endpoint> suppliers) {

	public record Endpoint(String baseUrl, String apiKey, Duration connectTimeout, Duration responseTimeout) {

		public Endpoint {
			if (connectTimeout == null) {
				connectTimeout = Duration.ofSeconds(1);
			}
			if (responseTimeout == null) {
				responseTimeout = Duration.ofSeconds(3);
			}
		}
	}

	public Endpoint of(Supplier supplier) {
		Endpoint endpoint = suppliers.get(supplier);
		if (endpoint == null) {
			throw new IllegalStateException("missing config: stayhub.suppliers." + supplier.name().toLowerCase());
		}
		return endpoint;
	}
}
