package io.descoped.lds.core.txlog;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.lds.core.saga.SagaInput;
import io.descoped.rawdata.api.RawdataProducer;
import no.cantara.saga.api.SagaNode;
import no.cantara.saga.execution.adapter.Adapter;

import java.util.Map;

public class AppendTxLogAdapter extends Adapter<JsonNode> {

    public static final String NAME = "TxLog-put-entry";

    final TxlogRawdataPool pool;

    public AppendTxLogAdapter(TxlogRawdataPool pool) {
        super(JsonNode.class, NAME);
        this.pool = pool;
    }

    @Override
    public JsonNode executeAction(SagaNode sagaNode, Object input, Map<SagaNode, Object> dependeesOutput) {
        SagaInput sagaInput = new SagaInput((JsonNode) input);
        RawdataProducer producer = pool.producer(sagaInput.source());
//        producer.publishBuilders(TxLogTools.sagaInputToTxEntry(producer.builder(), sagaInput)); // TODO fix rawdata client update of dep from 0.23 to 2.0.0
        producer.publish(TxLogTools.sagaInputToTxEntry(sagaInput));
        return null;
    }

}
