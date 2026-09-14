package com.stayhub.mapping;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 객실 타입 매핑
 */
@Entity
@Table(name = "room_type", uniqueConstraints = @UniqueConstraint(columnNames = {"property_id", "supplier_room_code"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id", nullable = false)
	private Property property;

	@Column(name = "supplier_room_code", nullable = false, length = 64)
	private String supplierRoomCode;

	@Column(nullable = false)
	private String name;

	@Column(name = "max_occupancy", nullable = false)
	private int maxOccupancy;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	public RoomType(Property property, String supplierRoomCode, String name, int maxOccupancy, Instant seenAt) {
		this.property = property;
		this.supplierRoomCode = supplierRoomCode;
		this.name = name;
		this.maxOccupancy = maxOccupancy;
		this.lastSeenAt = seenAt;
	}

	public void seen(String name, int maxOccupancy, Instant seenAt) {
		this.name = name;
		this.maxOccupancy = maxOccupancy;
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
