package com.stayhub.mapping;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.stayhub.domain.Supplier;
import com.stayhub.supplier.FailureReason;
import com.stayhub.supplier.SupplierAdapter;
import com.stayhub.supplier.SupplierException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매핑 동기화 실행 시점 결정
 * - 기동 완료 시: 기록이 없는 공급사는 모두 대상이라 전부 동기화
 * - 이후 retry-initial 간격의 틱마다 SyncStatus.isDue인 공급사만 동기화
 * - 실패는 상태에 기록만 하고 예외를 밖으로 내지 않음 (동기화 실패가 기동 실패가 되지 않도록)
 * - 어댑터가 빈으로 등록되면 목록에 자동 포함 (신규 공급사 추가 시 이 클래스는 수정 없음)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MappingSyncScheduler {

	private final List<SupplierAdapter> adapters;
	private final MappingSyncService syncService;
	private final SyncStatus status;
	private final Set<Supplier> running = ConcurrentHashMap.newKeySet();

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		syncDue();
	}

	@Scheduled(fixedDelayString = "${stayhub.sync.retry-initial:30s}", initialDelayString = "${stayhub.sync.retry-initial:30s}")
	public void tick() {
		syncDue();
	}

	void syncDue() {
		Instant now = Instant.now();
		for (SupplierAdapter adapter : adapters) {
			if (status.isDue(adapter.supplier(), now)) {
				syncOne(adapter, now);
			}
		}
	}

	/** 같은 공급사의 동기화가 겹치지 않게 실행 중 표시. 실패 종류에 따라 상태 기록 */
	void syncOne(SupplierAdapter adapter, Instant now) {
		Supplier supplier = adapter.supplier();
		if (!running.add(supplier)) {
			return;
		}
		try {
			SyncResult result = syncService.sync(adapter, now);
			status.recordSuccess(supplier, now);
			log.info("mapping sync ok supplier={} properties={} roomTypes={} deactivated={}/{}",
					supplier, result.properties(), result.roomTypes(),
					result.deactivatedProperties(), result.deactivatedRoomTypes());
		} catch (SupplierException e) {
			status.recordFailure(supplier, e.reason(), now);
			log.warn("mapping sync failed supplier={} reason={} nextAttemptAt={} detail={}",
					supplier, e.reason(), status.of(supplier).map(SyncStatus.State::nextAttemptAt).orElse(null),
					e.getMessage());
		} catch (RuntimeException e) {
			status.recordFailure(supplier, FailureReason.UNAVAILABLE, now);
			log.error("mapping sync error supplier={}", supplier, e);
		} finally {
			running.remove(supplier);
		}
	}
}
