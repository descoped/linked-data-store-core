package io.descoped.lds.core.controller;

import io.descoped.lds.api.persistence.json.JsonTools;
import io.descoped.lds.test.client.ResponseHelper;
import io.descoped.lds.test.client.TestClient;
import io.descoped.lds.test.server.TestServerListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;

import static org.testng.Assert.assertTrue;

@Listeners(TestServerListener.class)
public class NamespaceSchemaTest {

    @Inject
    private TestClient client;

    @Test
    public void thatNamespaceSchemaRespondsWithSetOfManagedDomains() {
        ResponseHelper<String> response = client.get("/data?schema");
        assertTrue(JsonTools.toJsonNode(response.expect200Ok().body()).size() > 1);
    }

    @Test
    public void thatNamespaceSchemaRespondsWithEmbeddedManagedDomains() {
        ResponseHelper<String> response = client.get("/data?schema=embed");
        assertTrue(JsonTools.toJsonNode(response.expect200Ok().body()).size() > 1);
    }
}
