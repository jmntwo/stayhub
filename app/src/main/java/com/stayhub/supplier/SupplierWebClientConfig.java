package com.stayhub.supplier;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.stayhub.domain.Supplier;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * base URL, X-Api-Key 헤더, 연결/응답 타임아웃을 설정에서 받아 하나씩
 * app 밖으로 나가는 HTTP 호출은 전부 여기서 만든 WebClient를 사용
 */
@Configuration
@EnableConfigurationProperties(SupplierProperties.class)
public class SupplierWebClientConfig {

	@Bean
	public Map<Supplier, WebClient> supplierWebClients(SupplierProperties properties, WebClient.Builder builder) {
		Map<Supplier, WebClient> clients = new EnumMap<>(Supplier.class);
		for (Supplier supplier : Supplier.values()) {
			clients.put(supplier, build(properties.of(supplier), builder.clone()));
		}
		return clients;
	}

	private static WebClient build(SupplierProperties.Endpoint endpoint, WebClient.Builder builder) {
		HttpClient httpClient = HttpClient.create()
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) endpoint.connectTimeout().toMillis())
				.responseTimeout(endpoint.responseTimeout());
		return builder
				.baseUrl(endpoint.baseUrl())
				.defaultHeader("X-Api-Key", endpoint.apiKey())
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.build();
	}
}
