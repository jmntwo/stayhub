package com.stayhub.mapping;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.stayhub.domain.Supplier;

public interface PropertyRepository extends JpaRepository<Property, Long> {

	Optional<Property> findBySupplierAndSupplierCode(Supplier supplier, String supplierCode);

	List<Property> findByActiveTrue();

	/** 이번 동기화(seenAt)에 등장하지 않은 활성 숙소를 비활성화. 동기화 성공 후에만 호출 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Property p set p.active = false, p.updatedAt = :seenAt "
			+ "where p.supplier = :supplier and p.active = true and p.lastSeenAt < :seenAt")
	int deactivateNotSeen(Supplier supplier, Instant seenAt);
}
