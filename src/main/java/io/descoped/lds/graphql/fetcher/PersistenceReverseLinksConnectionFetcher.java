package io.descoped.lds.graphql.fetcher;

import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import graphql.relay.PageInfo;
import graphql.schema.DataFetchingEnvironment;
import io.descoped.lds.api.json.JsonNavigationPath;
import io.descoped.lds.api.persistence.DocumentKey;
import io.descoped.lds.api.persistence.Transaction;
import io.descoped.lds.api.persistence.json.JsonDocument;
import io.descoped.lds.api.persistence.reactivex.Range;
import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.reactivex.Flowable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reverse link fetcher.
 */
public class PersistenceReverseLinksConnectionFetcher extends ConnectionFetcher<Map<String, Object>> {

    // Field name containing the ids of the target.
    private final JsonNavigationPath relationPath;

    private final String sourceEntityName;

    // Target entity name.
    private final String targetEntityName;

    // Name space
    private final String nameSpace;

    private final RxJsonPersistence persistence;

    public PersistenceReverseLinksConnectionFetcher(RxJsonPersistence persistence, String nameSpace, String sourceEntityName, JsonNavigationPath relationPath, String targetEntityName) {
        this.relationPath = Objects.requireNonNull(relationPath);
        this.sourceEntityName = Objects.requireNonNull(sourceEntityName);
        this.targetEntityName = Objects.requireNonNull(targetEntityName);
        this.nameSpace = Objects.requireNonNull(nameSpace);
        this.persistence = Objects.requireNonNull(persistence);
    }

    /**
     * Extracts the id from the source object in the environment.
     */
    private static String getIdFromSource(DataFetchingEnvironment environment) {
        Map<String, Object> source = environment.getSource();
        DocumentKey key = (DocumentKey) source.get("__graphql_internal_document_key");
        return key.id();
    }

    @Override
    Connection<Map<String, Object>> getConnection(DataFetchingEnvironment environment, ConnectionParameters parameters) {
        try (Transaction tx = persistence.createTransaction(true)) {

            String targetId = getIdFromSource(environment);

            Flowable<JsonDocument> documents = persistence.readSourceDocuments(tx, parameters.getSnapshot(), nameSpace,
                    targetEntityName, targetId, relationPath, sourceEntityName, parameters.getRange());

            List<Edge<Map<String, Object>>> edges = documents.map(document -> toEdge(document)).toList()
                    .blockingGet();

            if (edges.isEmpty()) {
                PageInfo pageInfo = new DefaultPageInfo(null, null, false,
                        false);
                return new DefaultConnection<>(Collections.emptyList(), pageInfo);
            }

            Edge<Map<String, Object>> firstEdge = edges.get(0);
            Edge<Map<String, Object>> lastEdge = edges.get(edges.size() - 1);

            boolean hasPrevious = true;
            if (environment.getSelectionSet().contains("pageInfo/hasPreviousPage")) {
                hasPrevious = persistence.readSourceDocuments(tx, parameters.getSnapshot(), nameSpace, targetEntityName,
                        targetId, relationPath, sourceEntityName, Range.lastBefore(1, firstEdge.getCursor().getValue())
                ).isEmpty().map(wasEmpty -> !wasEmpty).blockingGet();
            }

            boolean hasNext = true;
            if (environment.getSelectionSet().contains("pageInfo/hasNextPage")) {
                hasNext = persistence.readSourceDocuments(tx, parameters.getSnapshot(), nameSpace, targetEntityName,
                        targetId, relationPath, sourceEntityName, Range.firstAfter(1, lastEdge.getCursor().getValue())
                ).isEmpty().map(wasEmpty -> !wasEmpty).blockingGet();
            }

            PageInfo pageInfo = new DefaultPageInfo(
                    firstEdge.getCursor(),
                    lastEdge.getCursor(),
                    hasPrevious,
                    hasNext
            );

            return new DefaultConnection<>(
                    edges,
                    pageInfo
            );
        }
    }
}
