package com.stayhub.supplier.a;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.stayhub.domain.SearchQuery;
import com.stayhub.supplier.SupplierOffer;

class SupplierANormalizerTest {

	SearchQuery twoNights = new SearchQuery(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), 2, 0);

	@Test
	void 총액은_날짜별_단가와_세금의_합이고_가용_객실은_최솟값() {
		var item = item("A-2001", "OCN-TWN", false, List.of(
				rate("2026-10-01", 2, 130_000, 13_000),
				rate("2026-10-02", 3, 130_000, 13_000)));

		SupplierOffer offer = SupplierANormalizer.toOffers(new AAvailabilityResponse(List.of(item)), twoNights).get(0);

		assertThat(offer.price().total()).isEqualTo(286_000);
		assertThat(offer.price().currency()).isEqualTo("KRW");
		assertThat(offer.availableRooms()).isEqualTo(2);
		assertThat(offer.price().nightly()).hasSize(2);
		assertThat(offer.price().nightly().get(0).tax()).isEqualTo(13_000);
	}

	@Test
	void 하루라도_재고가_0이면_가용_객실은_0() {
		var item = item("A-2002", "STD-DBL", false, List.of(
				rate("2026-10-01", 1, 90_000, 9_000),
				rate("2026-10-02", 0, 82_000, 9_000)));

		SupplierOffer offer = SupplierANormalizer.toOffers(new AAvailabilityResponse(List.of(item)), twoNights).get(0);

		assertThat(offer.availableRooms()).isZero();
		assertThat(offer.price().total()).isEqualTo(190_000);
	}

	@Test
	void 요청_날짜가_응답에_빠지면_가용_객실은_0() {
		var item = item("A-2001", "OCN-TWN", false, List.of(
				rate("2026-10-01", 2, 130_000, 13_000)));

		SupplierOffer offer = SupplierANormalizer.toOffers(new AAvailabilityResponse(List.of(item)), twoNights).get(0);

		assertThat(offer.availableRooms()).isZero();
	}

	@Test
	void 요청_기간_밖의_날짜는_무시() {
		var item = item("A-2001", "OCN-TWN", true, List.of(
				rate("2026-09-30", 0, 999_999, 0),
				rate("2026-10-01", 2, 130_000, 13_000),
				rate("2026-10-02", 2, 130_000, 13_000),
				rate("2026-10-03", 0, 999_999, 0)));

		SupplierOffer offer = SupplierANormalizer.toOffers(new AAvailabilityResponse(List.of(item)), twoNights).get(0);

		assertThat(offer.price().total()).isEqualTo(286_000);
		assertThat(offer.availableRooms()).isEqualTo(2);
		assertThat(offer.breakfastIncluded()).isTrue();
	}

	@Test
	void 목록은_숙소_아래_객실_타입_구조를_유지() {
		var hotel = new AHotelsResponse.Hotel("A-2001", "Harbor View Hotel", List.of(
				new AHotelsResponse.RoomType("OCN-TWN", "Ocean Twin", 2),
				new AHotelsResponse.RoomType("STD-DBL", "Standard Double", 2)));

		var entries = SupplierANormalizer.toCatalog(new AHotelsResponse(List.of(hotel)));

		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).hotelCode()).isEqualTo("A-2001");
		assertThat(entries.get(0).rooms()).extracting("roomCode").containsExactly("OCN-TWN", "STD-DBL");
	}

	private static AAvailabilityResponse.Item item(String hotel, String room, boolean breakfast,
			List<AAvailabilityResponse.DailyRate> rates) {
		return new AAvailabilityResponse.Item(hotel, "name", room, "room", 2, breakfast, "KRW", rates);
	}

	private static AAvailabilityResponse.DailyRate rate(String date, int remaining, long rate, long tax) {
		return new AAvailabilityResponse.DailyRate(LocalDate.parse(date), remaining, rate, tax);
	}
}
