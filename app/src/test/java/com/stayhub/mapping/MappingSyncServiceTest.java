package com.stayhub.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.Supplier;
import com.stayhub.supplier.CatalogEntry;
import com.stayhub.supplier.FailureReason;
import com.stayhub.supplier.SupplierAdapter;
import com.stayhub.supplier.SupplierException;
import com.stayhub.supplier.SupplierOffer;

import reactor.core.publisher.Mono;

@DataJpaTest
class MappingSyncServiceTest {

	@Autowired
	PropertyRepository properties;

	@Autowired
	RoomTypeRepository roomTypes;

	@Autowired
	PlatformTransactionManager txManager;

	MappingSyncService service;

	Instant t1 = Instant.parse("2026-09-14T09:00:00Z");
	Instant t2 = t1.plusSeconds(3600);
	Instant t3 = t2.plusSeconds(3600);

	@BeforeEach
	void setUp() {
		service = new MappingSyncService(properties, roomTypes, new TransactionTemplate(txManager));
	}

	@Test
	void 첫_동기화는_숙소와_객실_타입을_모두_생성() {
		SyncResult result = service.sync(catalog(
				hotel("A-2001", "Harbor View Hotel", room("OCN-TWN"), room("STD-DBL")),
				hotel("A-2002", "Pine Hill Stay", room("STD-DBL"))), t1);

		assertThat(result.properties()).isEqualTo(2);
		assertThat(result.roomTypes()).isEqualTo(3);
		assertThat(result.deactivatedProperties()).isZero();
		assertThat(properties.count()).isEqualTo(2);
		assertThat(roomTypes.count()).isEqualTo(3);
	}

	@Test
	void 재동기화는_같은_코드에_같은_식별자를_유지하고_이름만_갱신() {
		service.sync(catalog(hotel("A-2001", "Old Name", room("OCN-TWN"))), t1);
		Long propertyId = properties.findBySupplierAndSupplierCode(Supplier.A, "A-2001").orElseThrow().getId();
		Long roomTypeId = roomTypes.findByPropertyIdAndSupplierRoomCode(propertyId, "OCN-TWN").orElseThrow().getId();

		service.sync(catalog(hotel("A-2001", "New Name", room("OCN-TWN"))), t2);

		Property after = properties.findBySupplierAndSupplierCode(Supplier.A, "A-2001").orElseThrow();
		assertThat(after.getId()).isEqualTo(propertyId);
		assertThat(after.getName()).isEqualTo("New Name");
		assertThat(after.getLastSeenAt()).isEqualTo(t2);
		assertThat(roomTypes.findByPropertyIdAndSupplierRoomCode(propertyId, "OCN-TWN").orElseThrow().getId())
				.isEqualTo(roomTypeId);
		assertThat(properties.count()).isEqualTo(1);
	}

	@Test
	void 목록에서_사라진_객실은_비활성화되고_다시_나타나면_같은_식별자로_활성화() {
		service.sync(catalog(hotel("A-2001", "Harbor", room("OCN-TWN"), room("STD-DBL"))), t1);
		Long propertyId = properties.findBySupplierAndSupplierCode(Supplier.A, "A-2001").orElseThrow().getId();
		Long stdId = roomTypes.findByPropertyIdAndSupplierRoomCode(propertyId, "STD-DBL").orElseThrow().getId();

		SyncResult second = service.sync(catalog(hotel("A-2001", "Harbor", room("OCN-TWN"))), t2);
		RoomType gone = roomTypes.findById(stdId).orElseThrow();

		assertThat(second.deactivatedRoomTypes()).isEqualTo(1);
		assertThat(gone.isActive()).isFalse();

		service.sync(catalog(hotel("A-2001", "Harbor", room("OCN-TWN"), room("STD-DBL"))), t3);
		RoomType back = roomTypes.findByPropertyIdAndSupplierRoomCode(propertyId, "STD-DBL").orElseThrow();

		assertThat(back.getId()).isEqualTo(stdId);
		assertThat(back.isActive()).isTrue();
	}

	@Test
	void 목록_API가_실패하면_예외가_전파되고_기존_매핑은_그대로() {
		service.sync(catalog(hotel("A-2001", "Harbor", room("OCN-TWN"))), t1);

		assertThatThrownBy(() -> service.sync(failing(), t2)).isInstanceOf(SupplierException.class);

		Property untouched = properties.findBySupplierAndSupplierCode(Supplier.A, "A-2001").orElseThrow();
		assertThat(untouched.isActive()).isTrue();
		assertThat(untouched.getLastSeenAt()).isEqualTo(t1);
	}

	@Test
	void 다른_숙소의_같은_객실_코드는_별개_행() {
		service.sync(catalog(
				hotel("A-2001", "Harbor", room("STD-DBL")),
				hotel("A-2002", "Pine", room("STD-DBL"))), t1);

		assertThat(roomTypes.count()).isEqualTo(2);
	}

	@Test
	void 다른_공급사의_매핑은_건드리지_않음() {
		properties.saveAndFlush(new Property(Supplier.B, "P-3101", "Harbor", t1.minusSeconds(999_999)));

		service.sync(catalog(hotel("A-2001", "Harbor", room("OCN-TWN"))), t1);

		assertThat(properties.findBySupplierAndSupplierCode(Supplier.B, "P-3101").orElseThrow().isActive()).isTrue();
	}

	// ---- 가짜 어댑터 ----

	private static SupplierAdapter catalog(CatalogEntry... entries) {
		return new FakeAdapter(Mono.just(List.of(entries)));
	}

	private static SupplierAdapter failing() {
		return new FakeAdapter(Mono.error(new SupplierException(Supplier.A, FailureReason.UNAVAILABLE, "down")));
	}

	private static CatalogEntry hotel(String code, String name, CatalogEntry.Room... rooms) {
		return new CatalogEntry(code, name, List.of(rooms));
	}

	private static CatalogEntry.Room room(String code) {
		return new CatalogEntry.Room(code, code + " name", 2);
	}

	private record FakeAdapter(Mono<List<CatalogEntry>> catalog) implements SupplierAdapter {

		@Override
		public Supplier supplier() {
			return Supplier.A;
		}

		@Override
		public Mono<List<CatalogEntry>> fetchCatalog() {
			return catalog;
		}

		@Override
		public Mono<List<SupplierOffer>> fetchAvailability(List<String> hotelCodes, SearchQuery query) {
			return Mono.just(List.of());
		}
	}
}
