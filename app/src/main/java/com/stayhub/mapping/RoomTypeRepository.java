package com.stayhub.mapping;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.stayhub.domain.Supplier;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

	Optional<RoomType> findByPropertyIdAndSupplierRoomCode(Long propertyId, String supplierRoomCode);

	List<RoomType> findByPropertyIdInAndActiveTrue(Collection<Long> propertyIds);

	/** 이번 동기화(seenAt)에 등장하지 않은 공급사의 객실 타입을 비활성화 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RoomType r set r.active = false, r.updatedAt = :seenAt "
			+ "where r.property.supplier = :supplier and r.active = true and r.lastSeenAt < :seenAt")
	int deactivateNotSeen(Supplier supplier, Instant seenAt);
}
