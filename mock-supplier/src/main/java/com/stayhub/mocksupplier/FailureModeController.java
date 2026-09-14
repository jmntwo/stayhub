package com.stayhub.mocksupplier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * (공급사, API)별로 독립
 * POST /control/{a|b}/{catalog|availability}/mode?value={normal|error|no-response|delay}
 */
@RestController
public class FailureModeController {

	static final Set<String> SUPPLIERS = Set.of("a", "b");
	static final Set<String> APIS = Set.of("catalog", "availability");

	private final Map<String, FailureMode> modes = new ConcurrentHashMap<>();

	FailureMode modeOf(String supplier, String api) {
		return modes.getOrDefault(key(supplier, api), FailureMode.NORMAL);
	}

	@PostMapping("/control/{supplier}/{api}/mode")
	public ResponseEntity<Map<String, String>> setMode(
			@PathVariable String supplier, @PathVariable String api, @RequestParam String value) {
		if (!SUPPLIERS.contains(supplier) || !APIS.contains(api)) {
			return ResponseEntity.badRequest().body(Map.of("error", "unknown supplier or api"));
		}
		FailureMode mode;
		try {
			mode = FailureMode.from(value);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
		modes.put(key(supplier, api), mode);
		return ResponseEntity.ok(Map.of(key(supplier, api), mode.value()));
	}

	private static String key(String supplier, String api) {
		return supplier + "/" + api;
	}
}
