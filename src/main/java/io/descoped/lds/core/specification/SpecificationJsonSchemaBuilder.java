package io.descoped.lds.core.specification;

import io.descoped.lds.api.specification.SpecificationElement;
import io.descoped.lds.api.specification.SpecificationElementType;
import io.descoped.lds.core.schema.JsonSchema;
import io.descoped.lds.core.schema.JsonSchemaDefinitionElement;

import java.util.LinkedHashSet;

public class SpecificationJsonSchemaBuilder {

    public static SpecificationJsonSchemaBuilder createBuilder(JsonSchema jsonSchema) {
        return new SpecificationJsonSchemaBuilder(jsonSchema, null, null, null);
    }

    private static LinkedHashSet<String> objectOnlyJsonTypes = new LinkedHashSet<>();

    static {
        objectOnlyJsonTypes.add("object");
    }

    final JsonSchema jsonSchema;
    final SpecificationElementType parentSpecificationElementType;
    final JsonSchemaDefinitionElement element;
    final SpecificationElement specificationElement;

    SpecificationJsonSchemaBuilder(
            JsonSchema jsonSchema,
            SpecificationElementType parentSpecificationElementType,
            JsonSchemaDefinitionElement element,
            SpecificationElement specificationElement) {
        this.jsonSchema = jsonSchema;
        this.parentSpecificationElementType = parentSpecificationElementType;
        this.element = element;
        this.specificationElement = specificationElement;
    }

    public JsonSchemaBasedSpecification build() {
        SpecificationElement root = new SpecificationElementBuilder(element)
                .name("root")
                .parent(null)
                .specificationElementType(SpecificationElementType.ROOT)
                .jsonTypes(objectOnlyJsonTypes)
                .jsonSchema(jsonSchema)
                .build();
        return new JsonSchemaBasedSpecification(jsonSchema, root);
    }

}
