package io.descoped.lds.core.domain.resource;

import com.fasterxml.jackson.databind.JsonNode;
import io.descoped.lds.api.persistence.DocumentKey;
import io.descoped.lds.api.persistence.Transaction;
import io.descoped.lds.api.persistence.json.JsonDocument;
import io.descoped.lds.api.persistence.json.JsonTools;
import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.descoped.lds.test.client.TestClient;
import io.descoped.lds.test.server.TestServer;
import io.descoped.lds.test.server.TestServerListener;
import org.testng.annotations.Ignore;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.testng.Assert.assertEquals;

@Listeners(TestServerListener.class)
public class ResourceContextIntegrationTest {

    @Inject
    private TestServer server;

    @Inject
    private TestClient client;

    private void createTestResource(String entity, String id, String json) {
        ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("Etc/UTC"));
        JsonNode jsonObject = JsonTools.toJsonNode(json);
        RxJsonPersistence persistence = server.getApplication().getPersistence();
        try (Transaction tx = persistence.createTransaction(false)) {
            persistence.createOrOverwrite(tx, new JsonDocument(new DocumentKey("data", entity, id, timestamp), jsonObject), server.getApplication().getSpecification()).blockingAwait();
        }
    }

    static String urlEncode(String decoded) {
        return URLEncoder.encode(decoded, StandardCharsets.UTF_8);
    }

    @Ignore
    @Test
    public void thatFreakyResourceURLsAreDecodedProperly() {
        createTestResource("provisionagreement", "rc1", "{\"id\":\"rc1\",\"str/ange=Prop#Name\":\"some-value\"}");
        String response = client.get("/data/provisionagreement/rc1/" + urlEncode("str/ange=Prop#Name")).expect200Ok().body();
        assertEquals(response, "[\"some-value\"]");
    }
}
