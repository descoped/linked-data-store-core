package io.descoped.lds.core.domain.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.descoped.lds.api.persistence.json.JsonTools;
import io.descoped.lds.api.specification.Specification;
import io.descoped.lds.core.domain.resource.ResourceContext;
import org.testng.annotations.Test;

import static io.descoped.lds.api.persistence.json.JsonTools.mapper;
import static io.descoped.lds.core.domain.resource.ResourceContext.createResourceContext;
import static io.descoped.lds.core.domain.resource.ResourceContextTest.specification;
import static java.time.ZoneId.of;
import static java.time.ZonedDateTime.now;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ReferenceJsonHelperTest {

    @Test
    public void thatCreateLinkOnNestedReferenceResourceWorks() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/ref/OtherEntity/456", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        JsonNode documentRoot = mapper.createObjectNode();
        boolean referenceJson = helper.createReferenceJson(context, documentRoot);
        assertTrue(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{\"ref\":\"/OtherEntity/456\"}}");
    }

    @Test
    public void thatChangeLinkOnExistingNestedReferenceResourceWorks() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/ref/OtherEntity/456", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        ObjectNode documentRoot = mapper.createObjectNode();
        documentRoot.putObject("object1").put("ref", "/OtherEntity/123");
        boolean referenceJson = helper.createReferenceJson(context, documentRoot);
        assertTrue(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{\"ref\":\"/OtherEntity/456\"}}");
    }

    @Test
    public void thatDeleteLinkOnExistingNestedReferenceResourceWorks() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/ref/OtherEntity/123", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        ObjectNode documentRoot = mapper.createObjectNode();
        documentRoot.putObject("object1").put("ref", "/OtherEntity/123");
        boolean referenceJson = helper.deleteReferenceJson(context, documentRoot);
        assertTrue(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{}}");
    }

    @Test
    public void thatExistingNestedReferenceResourceWorksIsNotTouchedWhenRefIsAlreadyCorrect() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/ref/OtherEntity/456", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        ObjectNode documentRoot = mapper.createObjectNode();
        documentRoot.putObject("object1").put("ref", "/OtherEntity/456");
        boolean referenceJson = helper.createReferenceJson(context, documentRoot);
        assertFalse(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{\"ref\":\"/OtherEntity/456\"}}");
    }

    @Test
    public void thatCreateLinkOnNestedArrayReferenceResourceWorks() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/refs/OtherEntity/456", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        JsonNode documentRoot = mapper.createObjectNode();
        boolean referenceJson = helper.createReferenceJson(context, documentRoot);
        assertTrue(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{\"refs\":[\"/OtherEntity/456\"]}}");
    }

    @Test
    public void thatDeleteLinkOnNestedArrayReferenceResourceWorks() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/refs/OtherEntity/456", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        ObjectNode documentRoot = mapper.createObjectNode();
        documentRoot.putObject("object1").putArray("refs").add("/OtherEntity/123").add("/OtherEntity/456").add("/OtherEntity/789");
        boolean referenceJson = helper.deleteReferenceJson(context, documentRoot);
        assertTrue(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{\"refs\":[\"/OtherEntity/123\",\"/OtherEntity/789\"]}}");
    }

    @Test
    public void thatCreateLinkOnExistingNestedArrayReferenceResourceWorks() {
        Specification specification = specification();
        ResourceContext context = createResourceContext(specification, "/ns/SomeEntity/123/object1/refs/OtherEntity/456", now(of("Etc/UTC")));
        ReferenceJsonHelper helper = new ReferenceJsonHelper(specification, context.getFirstElement());
        ObjectNode documentRoot = mapper.createObjectNode();
        documentRoot.putObject("object1").putArray("refs").add("/OtherEntity/456");
        boolean referenceJson = helper.createReferenceJson(context, documentRoot);
        assertFalse(referenceJson);
        assertEquals(JsonTools.toJson(documentRoot), "{\"object1\":{\"refs\":[\"/OtherEntity/456\"]}}");
    }

}
