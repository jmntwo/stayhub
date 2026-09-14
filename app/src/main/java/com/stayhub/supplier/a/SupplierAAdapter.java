package com.stayhub.supplier.a;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.CatalogEntry;
import com.stayhub.supplier.FailureReason;
import com.stayhub.supplier.SupplierAdapter;
import com.stayhub.supplier.SupplierException;
import com.stayhub.supplier.SupplierOffer;
import com.stayhub.supplier.SupplierWebClients;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import reactor.core.publisher.Mono;

/**
 * Supplier A 연동
 * A 공급사는 HTTP 상태 코드로 실패를 알림
 *
 * 실패 판정:
 * 4xx → REJECTED
 * 5xx → UNAVAILABLE
 * 타임아웃 → TIMEOUT
 * 연결 실패·파싱 실패 → UNAVAILABLE
 */
@Component
public class SupplierAAdapter implements SupplierAdapter {

	static final int MAX_HOTEL_CODES = 50;

	private final WebClient client;

	@Autowired
	public SupplierAAdapter(SupplierWebClients clients) {
		this(clients.of(Supplier.A));
	}

	SupplierAAdapter(WebClient client) {
		this.client = client;
	}

	@Override
	public Supplier supplier() {
		return Supplier.A;
	}

	@Override
	public Mono<List<CatalogEntry>> fetchCatalog() {
		return client.get()
				.uri("/a/v1/hotels")
				.retrieve()
				.bodyToMono(AHotelsResponse.class)
				.map(SupplierANormalizer::toCatalog)
				.onErrorMap(this::toSupplierException);
	}

	@Override
	public Mono<List<SupplierOffer>> fetchAvailability(List<String> hotelCodes, SearchQuery query) {
		if (hotelCodes.size() > MAX_HOTEL_CODES) {
			throw new IllegalArgumentException("hotelCodes must be at most " + MAX_HOTEL_CODES);
		}
		if (hotelCodes.isEmpty()) {
			return Mono.just(List.of());
		}
		return client.get()
				.uri(b -> b.path("/a/v1/availability")
						.queryParam("hotelCodes", String.join(",", hotelCodes))
						.queryParam("checkIn", query.checkIn())
						.queryParam("checkOut", query.checkOut())
						.queryParam("adults", query.adults())
						.queryParam("children", query.children())
						.build())
				.retrieve()
				.bodyToMono(AAvailabilityResponse.class)
				.map(response -> SupplierANormalizer.toOffers(response, query))
				.onErrorMap(this::toSupplierException);
	}

	private SupplierException toSupplierException(Throwable e) {
		if (e instanceof SupplierException se) {
			return se;
		}
		if (e instanceof WebClientResponseException re) {
			FailureReason reason = re.getStatusCode().is4xxClientError()
					? FailureReason.REJECTED
					: FailureReason.UNAVAILABLE;
			return new SupplierException(Supplier.A, reason, "HTTP " + re.getStatusCode().value(), e);
		}
		if (isTimeout(e)) {
			return new SupplierException(Supplier.A, FailureReason.TIMEOUT, e.getMessage(), e);
		}
		return new SupplierException(Supplier.A, FailureReason.UNAVAILABLE, e.getClass().getSimpleName(), e);
	}

	private static boolean isTimeout(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof TimeoutException || t instanceof ReadTimeoutException || t instanceof ConnectTimeoutException) {
				return true;
			}
		}
		return false;
	}
}
