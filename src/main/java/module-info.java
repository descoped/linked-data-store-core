module io.descoped.lds.core {
    requires io.descoped.lds.persistence.api;
    requires io.descoped.lds.search.api;
    requires io.descoped.dynamic.config;
    requires no.cantara.concurrent.futureselector;
    requires no.cantara.saga.api;
    requires no.cantara.saga.execution;
    requires no.cantara.sagalog;
    requires io.descoped.rawdata.api;
    requires de.huxhorn.sulky.ulid;
    requires jdk.unsupported;
    requires java.base;
    requires java.net.http;
    requires org.slf4j;
    requires undertow.core;
    requires xnio.api;
    requires org.json;
    requires org.everit.json.schema;
    requires java.xml; // TODO this should be in test-scope only!
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires jackson.dataformat.msgpack;
    requires org.apache.tika.core;

    requires graphql.java;
    requires io.reactivex.rxjava2;
    requires org.reactivestreams;
    requires com.github.akarnokd.rxjava2jdk9interop;
    requires graphql.java.extended.scalars;

    opens io.descoped.lds.graphql.graphiql;

    provides io.descoped.lds.api.search.SearchIndexProvider with io.descoped.lds.core.search.TestSearchIndex;

    uses io.descoped.lds.api.persistence.PersistenceInitializer;
    uses io.descoped.rawdata.api.RawdataClientInitializer;
    uses io.descoped.lds.api.search.SearchIndexProvider;
    uses no.cantara.sagalog.SagaLogInitializer;

    exports io.descoped.lds.core;
    exports io.descoped.lds.test.server; // Needed to run tests in IntelliJ
}
