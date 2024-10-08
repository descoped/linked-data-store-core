package io.descoped.lds.core.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.lds.api.persistence.DocumentKey;
import io.descoped.lds.api.persistence.Transaction;
import io.descoped.lds.api.persistence.json.JsonDocument;
import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.descoped.lds.api.specification.Specification;
import no.cantara.saga.api.SagaNode;
import no.cantara.saga.execution.adapter.AbortSagaException;
import no.cantara.saga.execution.adapter.Adapter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class PersistenceCreateOrOverwriteSagaAdapter extends Adapter<JsonNode> {

    public static final String NAME = "Persistence-Create-or-Overwrite";

    private final RxJsonPersistence persistence;
    private final Specification specification;

    public PersistenceCreateOrOverwriteSagaAdapter(RxJsonPersistence persistence, Specification specification) {
        super(JsonNode.class, NAME);
        this.persistence = persistence;
        this.specification = specification;
    }

    @Override
    public JsonNode executeAction(SagaNode sagaNode, Object sagaInput, Map<SagaNode, Object> dependeesOutput) {
        JsonNode input = (JsonNode) sagaInput;
        String versionStr = input.get("version").textValue();
        ZonedDateTime version = ZonedDateTime.parse(versionStr, DateTimeFormatter.ISO_ZONED_DATE_TIME);
        try (Transaction tx = persistence.createTransaction(false)) {
            persistence.createOrOverwrite(tx, new JsonDocument(new DocumentKey(input.get("namespace").textValue(), input.get("entity").textValue(), input.get("id").textValue(), version), input.get("data")), specification).blockingAwait();
        } catch (Throwable t) {
            throw new AbortSagaException("Unable to write data using persistence.", t);
        }
        return null;
    }
}
