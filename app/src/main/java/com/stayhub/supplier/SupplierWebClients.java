package com.stayhub.supplier;

import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import com.stayhub.domain.Supplier;

/** 공급사별 WebClient 보관. 어댑터가 자기 공급사의 클라이언트를 꺼내 쓰는 용도 */
public class SupplierWebClients {

	private final Map<Supplier, WebClient> clients;

	SupplierWebClients(Map<Supplier, WebClient> clients) {
		this.clients = clients;
	}

	public WebClient of(Supplier supplier) {
		WebClient client = clients.get(supplier);
		if (client == null) {
			throw new IllegalStateException("no WebClient for " + supplier);
		}
		return client;
	}
}
