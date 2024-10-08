package io.descoped.lds.graphql.schemas.visitors;

import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.TypeTraverser;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class ReverseLinkBuildingVisitorTest {

    @Test
    public void testReverseLink() {
        TypeDefinitionRegistry definitionRegistry = new SchemaParser().parse(
                "" +
                        "directive @link(reverseName: String) on FIELD_DEFINITION\n" +
                        "" +
                        "type Source {" +
                        "   foo: String" +
                        "   link: [Target] @link(reverseName : \"sources\")" +
                        "}" +
                        "type Target {" +
                        "   bar: String" +
                        "}" +
                        "type Query {" +
                        "   Source: Source" +
                        "}"
        );
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(definitionRegistry, runtimeWiring);

        Map<String, GraphQLType> typeMap = new HashMap<>(schema.getTypeMap());
        new TypeTraverser().depthFirst(
                new ReverseLinkBuildingVisitor(typeMap),
                typeMap.values()
        );

        String target = new SchemaPrinter().print(typeMap.get("Target"));

        Assertions.assertThat(target).isEqualToIgnoringWhitespace("" +
                "type Target {" +
                "  bar: String" +
                "  sources: [Source!]! @reverseLink(mappedBy : \"$.Source.link[]\", pagination : true)" +
                "}"
        );


    }
}