package com.stayhub.application;

import java.util.List;

import com.stayhub.domain.SearchQuery;
import com.stayhub.domain.StayOffer;

public record SearchResult(SearchQuery query, List<StayOffer> items, List<SupplierOutcome> suppliers) {
}
