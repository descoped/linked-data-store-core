package io.descoped.lds.core.domain.embedded;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.lds.api.persistence.DocumentKey;
import io.descoped.lds.api.persistence.Transaction;
import io.descoped.lds.api.persistence.json.JsonDocument;
import io.descoped.lds.api.persistence.json.JsonTools;
import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.descoped.lds.test.client.TestClient;
import io.descoped.lds.test.server.TestServer;
import io.descoped.lds.test.server.TestServerListener;
import org.skyscreamer.jsonassert.JSONAssert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;

@Listeners(TestServerListener.class)
public class EmbeddedResourceHandlerTest {

    @Inject
    private TestClient client;

    @Inject
    private TestServer server;

    private void createTestResource(String entity, String id, String json) {
        ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("Etc/UTC"));
        JsonNode jsonObject = JsonTools.toJsonNode(json);
        RxJsonPersistence persistence = server.getApplication().getPersistence();
        try (Transaction tx = persistence.createTransaction(false)) {
            persistence.createOrOverwrite(tx, new JsonDocument(new DocumentKey("data", entity, id, timestamp), jsonObject), server.getApplication().getSpecification()).blockingAwait();
        }
    }

    @Test
    public void thatGetEmbeddedResourceNavigatesDocumentToArray() {
        createTestResource("provisionagreement", "e1", "{\"id\":\"e1\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"]}");
        String response = client.get("/data/provisionagreement/e1/contacts").expect200Ok().body();
        assertEquals(response, "[\"/contact/c1\",\"/contact/c2\"]");
    }

    @Test
    public void thatGETEmbeddedResourceReturnsEmptyArray() {
        createTestResource("provisionagreement", "e2", "{\"id\":\"e2\"}");
        String response = client.get("/data/provisionagreement/e2/contacts").expect200Ok().body();
        assertEquals(response, "[null]");
    }

    @Test
    public void thatPutEmbeddedObjectResourceUpdatesSubDocument() {
        createTestResource("provisionagreement", "e3", "{\"id\":\"e3\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"],\"address\":{\"street\":\"Der julenissen bor\",\"country\":\"Nordpolen\"}}");
        client.put("/data/provisionagreement/e3/address?sync=true", "{\"street\":\"Akersveien 26\",\"country\":\"Norway\"}").expect200Ok();
        String response = client.get("/data/provisionagreement/e3").expect200Ok().body();
        JSONAssert.assertEquals("{\"id\":\"e3\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"],\"address\":{\"street\":\"Akersveien 26\",\"country\":\"Norway\"}}", response, true);
    }

    @Test
    public void thatPutEmbeddedArrayResourceUpdatesSubDocument() {
        createTestResource("provisionagreement", "e4", "{\"id\":\"e4\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"]}");
        client.put("/data/provisionagreement/e4/contacts?sync=true", "[\"/contact/truls\",\"/contact/hans\"]").expect200Ok();
        String response = client.get("/data/provisionagreement/e4").expect200Ok().body();
        JSONAssert.assertEquals("{\"id\":\"e4\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/truls\",\"/contact/hans\"]}", response, true);
    }

    @Test
    public void thatDeleteEmbeddedObjectResourceUpdatesSubDocument() {
        createTestResource("provisionagreement", "e5", "{\"id\":\"e5\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"],\"address\":{\"street\":\"Der julenissen bor\",\"country\":\"Nordpolen\"}}");
        client.delete("/data/provisionagreement/e5/address?sync=true").expect200Ok();
        String response = client.get("/data/provisionagreement/e5").expect200Ok().body();
        JSONAssert.assertEquals("{\"id\":\"e5\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"]}", response, true);
    }

    @Test
    public void thatDeleteEmbeddedArrayResourceUpdatesSubDocument() {
        createTestResource("provisionagreement", "e6", "{\"id\":\"e6\",\"name\":\"pa-test-name\",\"contacts\":[\"/contact/c1\",\"/contact/c2\"]}");
        client.delete("/data/provisionagreement/e6/contacts?sync=true").expect200Ok();
        String response = client.get("/data/provisionagreement/e6").expect200Ok().body();
        JSONAssert.assertEquals("{\"id\":\"e6\",\"name\":\"pa-test-name\"}", response, true);
    }

    @Test
    public void thatDELETELongResourcePathWorks() {
        createTestResource("provisionagreement", "e7", "{\"id\":\"e7\",\"name\":\"pa-test-name\",\"support\":{\"technicalSupport\":[\"/contact/s1\",\"/contact/s2\"],\"businessSupport\":[\"/contact/b1\"]}}");
        client.delete("/data/provisionagreement/e7/support/technicalSupport?sync=true").expect200Ok();
        String response = client.get("/data/provisionagreement/e7/support").expect200Ok().body();
        JSONAssert.assertEquals("{\"businessSupport\":[\"/contact/b1\"]}", response, true);
    }

    @Test
    public void thatPUTLongResourcePathWorks() {
        createTestResource("provisionagreement", "e8", "{\"id\":\"e8\",\"name\":\"pa-test-name\",\"support\":{\"technicalSupport\":[\"/contact/s1\",\"/contact/s2\"],\"businessSupport\":[\"/contact/b1\"]}}");
        client.put("/data/provisionagreement/e8/support/technicalSupport?sync=true", "[]").expect200Ok();
        String response = client.get("/data/provisionagreement/e8/support").expect200Ok().body();
        JSONAssert.assertEquals("{\"technicalSupport\":[],\"businessSupport\":[\"/contact/b1\"]}", response, true);
    }
}
