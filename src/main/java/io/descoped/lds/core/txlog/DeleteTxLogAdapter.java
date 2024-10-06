package io.descoped.lds.core.txlog;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.lds.core.saga.SagaInput;
import io.descoped.rawdata.api.RawdataProducer;
import no.cantara.saga.api.SagaNode;
import no.cantara.saga.execution.adapter.Adapter;

import java.util.Map;

public class DeleteTxLogAdapter extends Adapter<JsonNode> {

    public static final String NAME = "TxLog-delete-entry";

    final TxlogRawdataPool pool;

    public DeleteTxLogAdapter(TxlogRawdataPool pool) {
        super(JsonNode.class, NAME);
        this.pool = pool;
    }

    @Override
    public JsonNode executeAction(SagaNode sagaNode, Object input, Map<SagaNode, Object> dependeesOutput) {
        SagaInput sagaInput = new SagaInput((JsonNode) input);
        RawdataProducer producer = pool.producer(sagaInput.source());
        producer.publish(TxLogTools.sagaInputToTxEntry(sagaInput));
        return null;
    }
}
