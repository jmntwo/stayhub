package com.stayhub.supplier.b;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stayhub.domain.SearchQuery;
import com.stayhub.supplier.SupplierOffer;

class SupplierBNormalizerTest {

	SearchQuery twoNights = new SearchQuery(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2, 0);

	@Test
	void 총액은_그대로_쓰고_날짜별_내역은_없음() {
		var item = item("P-3101", "R-11", true, 310_000, List.of(inv("2026-10-01", 1), inv("2026-10-02", 3)));

		SupplierOffer offer = SupplierBNormalizer.toOffers(search(item), twoNights).get(0);

		assertThat(offer.price().total()).isEqualTo(310_000);
		assertThat(offer.price().nightly()).isNull();
		assertThat(offer.availableRooms()).isEqualTo(1);
		assertThat(offer.breakfastIncluded()).isTrue();
	}

	@Test
	void 하루라도_재고가_0이면_가용_객실은_0() {
		var item = item("P-3101", "R-11", false, 310_000, List.of(inv("2026-10-01", 2), inv("2026-10-02", 0)));

		assertThat(SupplierBNormalizer.toOffers(search(item), twoNights).get(0).availableRooms())
				.isZero();
	}

	@Test
	void 요청_날짜가_응답에_빠지면_가용_객실은_0() {
		var item = item("P-3101", "R-11", false, 310_000, List.of(inv("2026-10-01", 2)));

		assertThat(SupplierBNormalizer.toOffers(search(item), twoNights).get(0).availableRooms())
				.isZero();
	}

	@Test
	void 목록은_숙소_아래_객실_구조를_유지() {
		var property = new BPropertiesResponse.Property("P-3101", "Harbor View Hotel",
				List.of(new BPropertiesResponse.Room("R-11", "Ocean Twin Room", 2)));

		var entries = SupplierBNormalizer.toCatalog(new BPropertiesResponse("0000", "SUCCESS", new BPropertiesResponse.Data(List.of(property))));

		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).hotelCode()).isEqualTo("P-3101");
		assertThat(entries.get(0).rooms().get(0).roomCode()).isEqualTo("R-11");
	}

	private static BSearchResponse search(BSearchResponse.Item item) {
		return new BSearchResponse("0000", "SUCCESS", new BSearchResponse.Data(List.of(item)));
	}

	private static BSearchResponse.Item item(String property, String room, boolean breakfast, long total,
			List<BSearchResponse.Inventory> inventory) {
		return new BSearchResponse.Item(property, "name", room, "room", 2, breakfast, "KRW", total, true, inventory);
	}

	private static BSearchResponse.Inventory inv(String date, int remaining) {
		return new BSearchResponse.Inventory(LocalDate.parse(date), remaining);
	}
}
