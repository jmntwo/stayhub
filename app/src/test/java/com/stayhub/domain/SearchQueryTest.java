package com.stayhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

	@Test
	void 체크아웃일은_숙박일에_포함되지_않는다() {
		SearchQuery q = new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

		assertThat(q.nights()).isEqualTo(3);
	}

	@Test
	void 체크아웃이_체크인보다_뒤가_아니면_거절한다() {
		assertThatThrownBy(() -> new SearchQuery(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 4), 2, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 성인은_한_명_이상이어야_한다() {
		assertThatThrownBy(() -> new SearchQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 0, 1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
