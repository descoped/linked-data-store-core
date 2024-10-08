package io.descoped.lds.core.search;

import io.descoped.lds.api.persistence.DocumentKey;
import io.descoped.lds.api.persistence.flattened.FlattenedDocument;
import io.descoped.lds.api.persistence.json.JsonDocument;
import io.descoped.lds.api.persistence.json.JsonToFlattenedDocument;
import io.descoped.lds.api.search.SearchIndex;
import io.descoped.lds.api.search.SearchResponse;
import io.descoped.lds.api.search.SearchResult;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleSearchIndex implements SearchIndex {

    private Map<String, FlattenedDocument> index = Collections.synchronizedMap(new HashMap<>());

    @Override
    public Completable createOrOverwrite(JsonDocument document) {
        return createOrOverwrite(Arrays.asList(document));
    }

    @Override
    public Completable createOrOverwrite(Collection<JsonDocument> collection) {
        return Completable.fromAction(() -> {
            for (JsonDocument document : collection) {
                DocumentKey key = document.key();
                FlattenedDocument converted = new JsonToFlattenedDocument(key.namespace(), key.entity(), key.id(),
                        null, document.jackson(), 8192).toDocument();
                index.put(converted.key().id(), converted);
            }
        });
    }

    @Override
    public Completable delete(JsonDocument document) {
        return Completable.fromAction(() -> index.put(document.key().id(), null));
    }

    @Override
    public Completable deleteAll() {
        return Completable.fromAction(() -> index.clear());
    }

    @Override
    public Single<SearchResponse> search(String query, Set<String> filter, long from, long size) {
        return Flowable.fromIterable(index.entrySet().stream()
                        .filter(entry -> entry.getValue() != null)
                        .filter(entry -> entry.getValue().leafNodesByPath().values().stream()
                                .anyMatch(value -> value.value() instanceof String
                                        && ((String) value.value()).toLowerCase().contains(query.toLowerCase())))
                        .map(entry -> new SearchResult(entry.getValue().key()))
                        .collect(Collectors.toList())).toList()
                .map(results -> new SearchResponse(results.size(), results, from, size));
    }
}
