package io.descoped.lds.core.search;

import io.descoped.lds.api.search.SearchIndex;
import io.descoped.lds.api.search.SearchIndexProvider;

import java.util.Map;
import java.util.Set;

public class TestSearchIndex implements SearchIndexProvider {

    @Override
    public String getProviderId() {
        return "testSearchIndex";
    }

    @Override
    public Set<String> configurationKeys() {
        return Set.of();
    }

    @Override
    public SearchIndex getSearchIndex(Map<String, String> map) {
        return new SimpleSearchIndex();
    }

}
