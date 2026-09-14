package com.stayhub.mapping;

import java.time.Instant;

import com.stayhub.domain.Supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 숙소 매핑
 * 같은 공급사 코드는 항상 같은 id를 유지
 * 목록에서 사라지면 삭제하지 않고 active=false
 */
@Entity
@Table(name = "property", uniqueConstraints = @UniqueConstraint(columnNames = {"supplier", "supplier_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Property {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Supplier supplier;

	@Column(name = "supplier_code", nullable = false, length = 64)
	private String supplierCode;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** 마지막으로 목록 API에 등장한 동기화 시각 (비활성화 판정 기준) */
	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	public Property(Supplier supplier, String supplierCode, String name, Instant seenAt) {
		this.supplier = supplier;
		this.supplierCode = supplierCode;
		this.name = name;
		this.lastSeenAt = seenAt;
	}

	/** 동기화에서 다시 발견된 경우 나머지 갱신 + id는 그대로 */
	public void seen(String name, Instant seenAt) {
		this.name = name;
		this.active = true;
		this.lastSeenAt = seenAt;
	}

	public void deactivate() {
		this.active = false;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
