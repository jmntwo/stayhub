package com.stayhub.supplier.b;

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
 * Supplier B 연동
 * 실패해도 HTTP 200이고 본문 resultCode로만 알리는 공급사
 *
 * 실패 판정:
 * resultCode E4xx → REJECTED
 * E5xx·그 외 코드 → UNAVAILABLE
 * HTTP 4xx/5xx도 같은 규칙
 * 타임아웃 → TIMEOUT
 * 연결 실패·파싱 실패 → UNAVAILABLE
 */
@Component
public class SupplierBAdapter implements SupplierAdapter {

	static final int MAX_PROPERTY_IDS = 50;

	private static final String SUCCESS_CODE = "0000";

	private final WebClient client;

	@Autowired
	public SupplierBAdapter(SupplierWebClients clients) {
		this(clients.of(Supplier.B));
	}

	SupplierBAdapter(WebClient client) {
		this.client = client;
	}

	@Override
	public Supplier supplier() {
		return Supplier.B;
	}

	@Override
	public Mono<List<CatalogEntry>> fetchCatalog() {
		return client.get()
				.uri("/b/api/properties")
				.retrieve()
				.bodyToMono(BPropertiesResponse.class)
				.doOnNext(r -> checkResult(r.resultCode(), r.resultMessage(), r.data()))
				.map(SupplierBNormalizer::toCatalog)
				.onErrorMap(this::toSupplierException);
	}

	@Override
	public Mono<List<SupplierOffer>> fetchAvailability(List<String> propertyIds, SearchQuery query) {
		if (propertyIds.size() > MAX_PROPERTY_IDS) {
			throw new IllegalArgumentException("propertyIds must be at most " + MAX_PROPERTY_IDS);
		}
		if (propertyIds.isEmpty()) {
			return Mono.just(List.of());
		}
		return client.get()
				.uri(b -> b.path("/b/api/search")
						.queryParam("propertyIds", String.join(",", propertyIds))
						.queryParam("checkIn", query.checkIn())
						.queryParam("checkOut", query.checkOut())
						.queryParam("adults", query.adults())
						.queryParam("children", query.children())
						.build())
				.retrieve()
				.bodyToMono(BSearchResponse.class)
				.doOnNext(r -> checkResult(r.resultCode(), r.resultMessage(), r.data()))
				.map(response -> SupplierBNormalizer.toOffers(response, query))
				.onErrorMap(this::toSupplierException);
	}

	/** 응답 래퍼의 resultCode 검사. 성공이 아니거나 data가 없으면 SupplierException */
	private static void checkResult(String resultCode, String resultMessage, Object data) {
		if (!SUCCESS_CODE.equals(resultCode)) {
			throw new SupplierException(Supplier.B, reasonOf(resultCode), resultCode + " " + resultMessage);
		}
		if (data == null) {
			throw new SupplierException(Supplier.B, FailureReason.UNAVAILABLE, "success without data");
		}
	}

	/** E4xx는 우리 요청 문제, 그 외는 공급사 쪽 문제로 판정 */
	private static FailureReason reasonOf(String resultCode) {
		return resultCode != null && resultCode.startsWith("E4")
				? FailureReason.REJECTED
				: FailureReason.UNAVAILABLE;
	}

	private SupplierException toSupplierException(Throwable e) {
		if (e instanceof SupplierException se) {
			return se;
		}
		if (e instanceof WebClientResponseException re) {
			FailureReason reason = re.getStatusCode().is4xxClientError()
					? FailureReason.REJECTED
					: FailureReason.UNAVAILABLE;
			return new SupplierException(Supplier.B, reason, "HTTP " + re.getStatusCode().value(), e);
		}
		if (isTimeout(e)) {
			return new SupplierException(Supplier.B, FailureReason.TIMEOUT, e.getMessage(), e);
		}
		return new SupplierException(Supplier.B, FailureReason.UNAVAILABLE, e.getClass().getSimpleName(), e);
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
