package io.descoped.lds.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.descoped.lds.api.persistence.json.JsonTools;
import io.descoped.lds.test.ConfigurationOverride;
import io.descoped.lds.test.client.TestClient;
import io.descoped.lds.test.server.TestServerListener;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.io.IOException;

import static io.descoped.lds.core.utils.FileAndClasspathReaderUtils.readFileOrClasspathResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;


@Listeners(TestServerListener.class)
public class GraphQLIntegrationTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    TestClient client;

    @Test
    @ConfigurationOverride({
            "graphql.enabled", "true",
            "graphql.search.enabled", "false",
            "specification.schema", "src/test/resources/spec/abstract/jsonschemas",
            "graphql.schema", "src/test/resources/spec/abstract/graphqlschemas/schema.graphql"
    })
    public void testAbstractRelations() throws IOException {
        putResource("/data/Cat/cat1", "spec/abstract/cat1.json");
        putResource("/data/Cat/cat2", "spec/abstract/cat2.json");
        putResource("/data/Dog/dog1", "spec/abstract/dog1.json");
        putResource("/data/Dog/dog2", "spec/abstract/dog2.json");
        putResource("/data/Owner/owner1", "spec/abstract/owner1.json");
        putResource("/data/Owner/owner2", "spec/abstract/owner2.json");

        JsonNode result = executeGraphQLQuery("spec/abstract/query.json");

        JsonNode expectedResult = MAPPER.readTree(readFileOrClasspathResource("spec/abstract/query-response.json"));

        assertThat(result)
                .isEqualTo(expectedResult);

    }

    @Test
    @ConfigurationOverride({
            "graphql.enabled", "true",
            "specification.schema", "spec/demo/contact.json,spec/demo/provisionagreement.json"
    })
    public void thatGraphQLEndpointSupportsCors() {

        client.options("/graphql",
                "Access-Control-Request-Method", "POST"
        ).response().headers();
    }

    @Test
    @ConfigurationOverride({
            "graphql.enabled", "true",
            "specification.schema", "spec/demo/contact.json,spec/demo/provisionagreement.json"
    })
    public void thatGetContactOnlyWorks() {
        // setup demo data
        putResource("/data/contact/821aa", "demo/4-donald.json");
        assertEquals(client.get("/data/contact/821aa").expect200Ok().body(), "{\"email\":\"donald@duck.no\",\"name\":\"Donald Duck\"}");

        assertNoErrors(executeGraphQLQuery("spec/demo/graphql/contact_only.json"));
        assertNoErrors(executeGraphQLQuery("spec/demo/graphql/contact_by_id.json", "821aa"));
    }

    @Test
    @ConfigurationOverride({
            "graphql.enabled", "true",
            "specification.schema", "spec/demo/contact.json"
    })
    public void thatSeachByContactNameWorks() {
        // setup demo data
        putResource("/data/contact/821aa", "demo/4-donald.json");
        JsonNode result = executeGraphQLQuery("spec/demo/graphql/search_contact.json", "Duck");
        assertNoErrors(result);
        Assert.assertEquals(result.get("data").get("Search").get("edges").size(), 1);
        Assert.assertEquals(result.get("data").get("Search").get("edges").get(0).get("node").get("name").textValue(), "Donald Duck");
        // Check that the entity is deleted from the index
        client.delete("/data/contact/821aa?sync=true");
        result = executeGraphQLQuery("spec/demo/graphql/search_contact.json", "Duck");
        Assert.assertEquals(result.get("data").get("Search").get("edges").size(), 0);
    }

    @Test
    @ConfigurationOverride({
            "graphql.enabled", "true",
            "specification.schema", "spec/demo/contact.json,spec/demo/provisionagreement.json"
    })
    public void thatNestedSeachWorks() {
        // setup demo data
        putResource("/data/provisionagreement/2a41c", "demo/1-sirius.json");
        putResource("/data/provisionagreement/2a41c/address", "demo/2-sirius-address.json");

        JsonNode result = executeGraphQLQuery("spec/demo/graphql/search_address.json", "Andeby");
        assertNoErrors(result);
        Assert.assertEquals(result.get("data").get("Search").get("edges").size(), 1);
        Assert.assertEquals(result.get("data").get("Search").get("edges").get(0).get("node").get("name").textValue(), "Sirius");
        // Check that the entity index is updated
        client.put("/data/provisionagreement/2a41c", readFileOrClasspathResource(
                "demo/1-sirius.json").replace("Sirius", "Jupiter"));
        result = executeGraphQLQuery("spec/demo/graphql/search_address.json", "Jupiter");
        assertNoErrors(result);
        Assert.assertEquals(result.get("data").get("Search").get("edges").size(), 1);
    }

    @Test
    @ConfigurationOverride({
            "graphql.enabled", "true",
            "specification.schema", "spec/demo/contact.json,spec/demo/provisionagreement.json"
    })
    public void thatLinkingQueryWorks() {
        // setup demo data
        putResource("/data/provisionagreement/2a41c", "demo/1-sirius.json");
        putResource("/data/provisionagreement/2a41c/address", "demo/2-sirius-address.json");
        putResource("/data/contact/4b2ef", "demo/3-skrue.json");
        putResource("/data/contact/821aa", "demo/4-donald.json");
        client.put("/data/provisionagreement/2a41c/contacts/contact/4b2ef");
        client.put("/data/provisionagreement/2a41c/contacts/contact/821aa");

        assertNoErrors(executeGraphQLQuery("spec/demo/graphql/basic_query.json"));
    }

    private void putResource(String path, String resourceFilePath) {
        client.put(path + "?sync=true", readFileOrClasspathResource(resourceFilePath));
    }

    private void assertNoErrors(JsonNode responseRootNode) {
        System.out.println(responseRootNode);
        if (responseRootNode.has("errors")) {
            // there should not be any errors!
            JsonNode errors = responseRootNode.get("errors");
            String errorMessages = JsonTools.toJson(errors);
            Assert.fail(errorMessages);
        }
    }

    private JsonNode executeGraphQLQuery(String path, Object... params) {
        String query = String.format(readFileOrClasspathResource(path), params);
        return JsonTools.toJsonNode(client.postJson("/graphql", query)
                .expect200Ok()
                .body());
    }
}
