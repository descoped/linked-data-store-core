package io.descoped.lds.core.controller;

import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.descoped.lds.api.specification.Specification;
import io.descoped.lds.core.saga.SagaExecutionCoordinator;
import io.descoped.lds.core.saga.SagaRepository;
import io.descoped.lds.core.schema.SchemaRepository;
import io.descoped.lds.core.txlog.TxlogRawdataPool;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class NamespaceController implements HttpHandler {

    private final String defaultNamespace;

    private final Specification specification;
    private final SchemaRepository schemaRepository;
    private final RxJsonPersistence persistence;
    private final SagaExecutionCoordinator sec;
    private final SagaRepository sagaRepository;
    private final TxlogRawdataPool txLogPool;

    public NamespaceController(String namespaceDefault, Specification specification, SchemaRepository schemaRepository,
                               RxJsonPersistence persistence, SagaExecutionCoordinator sec,
                               SagaRepository sagaRepository, TxlogRawdataPool txLogPool) {
        this.specification = specification;
        this.schemaRepository = schemaRepository;
        this.persistence = persistence;
        this.sagaRepository = sagaRepository;
        this.txLogPool = txLogPool;
        if (!namespaceDefault.startsWith("/")) {
            namespaceDefault = "/" + namespaceDefault;
        }
        this.defaultNamespace = namespaceDefault;
        this.sec = sec;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String requestPath = exchange.getRelativePath();

        // TODO: This should be handled by a path handler.
        if (requestPath.trim().length() <= 1) {
            exchange.setStatusCode(404);
            return;
        }

        if (requestPath.equals(defaultNamespace) && exchange.getQueryParameters().containsKey("schema") && exchange.getQueryParameters().get("schema").getFirst().isBlank()) {
            List<String> managedDomains = specification.getManagedDomains().stream().sorted().map(md -> String.format("\"%s/%s?schema\"", defaultNamespace, md)).collect(Collectors.toList());
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("[" + managedDomains.stream().collect(Collectors.joining(",")) + "]", StandardCharsets.UTF_8);
            return;
        }

        if (requestPath.equals(defaultNamespace) && exchange.getQueryParameters().containsKey("schema") && exchange.getQueryParameters().get("schema").contains("embed")) {
            List<String> managedDomains = specification.getManagedDomains().stream().sorted().map(md -> schemaRepository.getJsonSchema().getSchemaJson(md)).collect(Collectors.toList());
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("[" + managedDomains.stream().collect(Collectors.joining(",")) + "]", StandardCharsets.UTF_8);
            return;
        }

        if (requestPath.startsWith(defaultNamespace)) {
            new DataController(specification, schemaRepository, persistence, sec, sagaRepository).handleRequest(exchange);
            return;
        }

        if (requestPath.startsWith("/source/")) {
            new SourceHandler(txLogPool).handleRequest(exchange);
            return;
        }

        exchange.setStatusCode(400);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        String namespace = requestPath.substring(1, Math.max(requestPath.substring(1).indexOf("/") + 1, requestPath.length()));
        exchange.getResponseSender().send("Unsupported namespace: \"" + namespace + "\"");
    }
}
