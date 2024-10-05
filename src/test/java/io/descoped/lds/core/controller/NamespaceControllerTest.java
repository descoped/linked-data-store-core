package io.descoped.lds.core.controller;

import io.descoped.lds.test.client.TestClient;
import io.descoped.lds.test.server.TestServerListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;

import static org.testng.Assert.assertEquals;

@Listeners(TestServerListener.class)
public class NamespaceControllerTest {

    @Inject
    private TestClient client;

    @Test
    public void thatGETWithoutBadNamespaceFailsWith400() {
        String response = client.get("/bad").expect400BadRequest().body();
        assertEquals(response, "Unsupported namespace: \"bad\"");
    }

    @Test(enabled = false)
    public void thatPUTOnLegalNamespaceAndDocumentAcceptsWith200() {
        client.put("/data/contact/1", "{\"name\":\"John\",\"email\":\"john@company.com\"}").expect200Ok();
    }

    @Test
    public void thatGETWithoutNamespaceFailtWith404() {
        client.get("/").expect404NotFound();
    }
}
