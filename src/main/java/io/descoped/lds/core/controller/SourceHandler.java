package io.descoped.lds.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.lds.api.persistence.json.JsonTools;
import io.descoped.lds.core.txlog.TxLogTools;
import io.descoped.lds.core.txlog.TxlogRawdataPool;
import io.descoped.rawdata.api.RawdataMessage;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.nio.charset.StandardCharsets;

import static java.util.Optional.ofNullable;

public class SourceHandler implements HttpHandler {

    private final TxlogRawdataPool txLogPool;

    public SourceHandler(TxlogRawdataPool txLogPool) {
        this.txLogPool = txLogPool;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestMethod().equalToString("get")) {
            new GetHandler().handleRequest(exchange);
            return;
        }

        exchange.setStatusCode(400);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send("Unsupported managed resource method: " + exchange.getRequestMethod());
    }

    private class GetHandler implements HttpHandler {
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.isInIoThread()) {
                exchange.dispatch(this);
                return;
            }

            String source = exchange.getRequestPath().substring("/source/".length());
            RawdataMessage lastMessage = txLogPool.getLastMessage(source);
            if (lastMessage == null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ObjectNode result = JsonTools.mapper.createObjectNode();
                result.putNull("lastSourceId");
                exchange.getResponseSender().send(JsonTools.toJson(result), StandardCharsets.UTF_8);
                return;
            }

            JsonNode meta = TxLogTools.toJson(lastMessage.get("meta"));
            JsonNode sourceIdTextNode = ofNullable(meta.get("sourceId")).orElse(NullNode.getInstance());
            ObjectNode result = JsonTools.mapper.createObjectNode();
            result.set("lastSourceId", sourceIdTextNode);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(JsonTools.toJson(result), StandardCharsets.UTF_8);
        }
    }
}
