package com.stayhub.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.stayhub.domain.Supplier;

@DataJpaTest
class MappingRepositoryTest {

	@Autowired
	PropertyRepository properties;

	@Autowired
	RoomTypeRepository roomTypes;

	Instant t0 = Instant.parse("2026-09-14T00:00:00Z");

	@Test
	void 같은_공급사_코드를_다시_저장하면_유니크_제약에_걸린다() {
		properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Harbor View Hotel", t0));

		assertThatThrownBy(() ->
				properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Harbor View Hotel", t0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 공급사가_다르면_같은_코드도_다른_숙소다() {
		Property a = properties.saveAndFlush(new Property(Supplier.A, "X-1", "Hotel", t0));
		Property b = properties.saveAndFlush(new Property(Supplier.B, "X-1", "Hotel", t0));

		assertThat(a.getId()).isNotEqualTo(b.getId());
	}

	@Test
	void 다시_발견된_숙소는_내부_식별자를_유지한다() {
		Property saved = properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Old Name", t0));
		Long id = saved.getId();

		Property found = properties.findById(id).orElseThrow();
		found.seen("New Name", t0.plusSeconds(60));
		properties.saveAndFlush(found);

		Property after = properties.findById(id).orElseThrow();
		assertThat(after.getId()).isEqualTo(id);
		assertThat(after.getName()).isEqualTo("New Name");
		assertThat(after.getLastSeenAt()).isEqualTo(t0.plusSeconds(60));
	}

	@Test
	void 객실_코드는_숙소_안에서만_유일하다() {
		Property harbor = properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Harbor View Hotel", t0));
		Property pine = properties.saveAndFlush(new Property(Supplier.A, "A-2002", "Pine Hill Stay", t0));

		roomTypes.saveAndFlush(new RoomType(harbor, "STD-DBL", "Standard Double", 2, t0));
		roomTypes.saveAndFlush(new RoomType(pine, "STD-DBL", "Standard Double", 2, t0));

		assertThatThrownBy(() ->
				roomTypes.saveAndFlush(new RoomType(harbor, "STD-DBL", "Standard Double", 2, t0)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 자연_키로_숙소와_객실_타입을_찾는다() {
		Property harbor = properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Harbor View Hotel", t0));
		roomTypes.saveAndFlush(new RoomType(harbor, "OCN-TWN", "Ocean Twin", 2, t0));

		assertThat(properties.findBySupplierAndSupplierCode(Supplier.A, "A-2001")).map(Property::getId)
				.contains(harbor.getId());
		assertThat(properties.findBySupplierAndSupplierCode(Supplier.B, "A-2001")).isEmpty();
		assertThat(roomTypes.findByPropertyIdAndSupplierRoomCode(harbor.getId(), "OCN-TWN")).isPresent();
		assertThat(roomTypes.findByPropertyIdAndSupplierRoomCode(harbor.getId(), "STD-DBL")).isEmpty();
	}

	@Test
	void 이번_동기화에_등장하지_않은_행만_비활성화된다() {
		Instant sync1 = t0;
		Instant sync2 = t0.plusSeconds(3600);
		Property seenAgain = properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Harbor", sync1));
		Property gone = properties.saveAndFlush(new Property(Supplier.A, "A-2002", "Pine", sync1));
		Property otherSupplier = properties.saveAndFlush(new Property(Supplier.B, "P-1", "Lake", sync1));
		roomTypes.saveAndFlush(new RoomType(seenAgain, "OCN-TWN", "Ocean Twin", 2, sync2));
		roomTypes.saveAndFlush(new RoomType(seenAgain, "OLD-RM", "Old Room", 2, sync1));

		seenAgain.seen("Harbor", sync2);
		properties.saveAndFlush(seenAgain);

		int deactivatedProperties = properties.deactivateNotSeen(Supplier.A, sync2);
		int deactivatedRooms = roomTypes.deactivateNotSeen(Supplier.A, sync2);
		properties.flush();

		assertThat(deactivatedProperties).isEqualTo(1);
		assertThat(deactivatedRooms).isEqualTo(1);
		assertThat(properties.findById(gone.getId()).orElseThrow().isActive()).isFalse();
		assertThat(properties.findById(seenAgain.getId()).orElseThrow().isActive()).isTrue();
		assertThat(properties.findById(otherSupplier.getId()).orElseThrow().isActive()).isTrue();
	}

	@Test
	void 비활성화해도_행과_식별자는_남는다() {
		Property saved = properties.saveAndFlush(new Property(Supplier.A, "A-2001", "Harbor View Hotel", t0));
		saved.deactivate();
		properties.saveAndFlush(saved);

		Property after = properties.findById(saved.getId()).orElseThrow();
		assertThat(after.isActive()).isFalse();
		assertThat(after.getSupplierCode()).isEqualTo("A-2001");
	}
}
