package io.descoped.lds.graphql.schemas.visitors;

import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnionType;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import io.descoped.lds.api.json.JsonNavigationPath;
import io.descoped.lds.api.persistence.DocumentKey;
import io.descoped.lds.api.persistence.reactivex.RxJsonPersistence;
import io.descoped.lds.api.search.SearchIndex;
import io.descoped.lds.graphql.directives.LinkDirective;
import io.descoped.lds.graphql.directives.ReverseLinkDirective;
import io.descoped.lds.graphql.fetcher.PersistenceFetcher;
import io.descoped.lds.graphql.fetcher.PersistenceLinkFetcher;
import io.descoped.lds.graphql.fetcher.PersistenceLinksConnectionFetcher;
import io.descoped.lds.graphql.fetcher.PersistenceLinksFetcher;
import io.descoped.lds.graphql.fetcher.PersistenceReverseLinksConnectionFetcher;
import io.descoped.lds.graphql.fetcher.PersistenceRootConnectionFetcher;
import io.descoped.lds.graphql.fetcher.QueryConnectionFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import static graphql.schema.GraphQLTypeUtil.isList;
import static graphql.schema.GraphQLTypeUtil.isNotWrapped;
import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static graphql.schema.GraphQLTypeUtil.unwrapAll;
import static graphql.schema.GraphQLTypeUtil.unwrapType;
import static io.descoped.lds.graphql.schemas.visitors.ReverseLinkBuildingVisitor.computePath;

public class RegistrySetupVisitor extends GraphQLTypeVisitorStub {

    private static final Logger log = LoggerFactory.getLogger(RegistrySetupVisitor.class);
    private final GraphQLCodeRegistry.Builder registry;
    private final RxJsonPersistence persistence;
    private final SearchIndex searchIndex;
    private final String namespace;

    public RegistrySetupVisitor(RxJsonPersistence persistence, String namespace, SearchIndex searchIndex) {
        this(GraphQLCodeRegistry.newCodeRegistry(), persistence, namespace, searchIndex);
    }

    public RegistrySetupVisitor(GraphQLCodeRegistry registry, RxJsonPersistence persistence, String namespace, SearchIndex searchIndex) {
        this(GraphQLCodeRegistry.newCodeRegistry(registry), persistence, namespace, searchIndex);
    }

    public RegistrySetupVisitor(GraphQLCodeRegistry.Builder registry, RxJsonPersistence persistence, String namespace, SearchIndex searchIndex) {
        this.registry = registry;
        this.persistence = persistence;
        this.namespace = namespace;
        this.searchIndex = searchIndex;
    }

    private static Boolean isMany(GraphQLOutputType type) {
        Stack<GraphQLType> types = unwrapType(type);
        for (GraphQLType currentType : types) {
            if (isList(currentType)) {
                return true;
            }
        }
        return false;
    }

    private static Boolean isConnection(GraphQLOutputType type) {
        Stack<GraphQLType> types = unwrapType(type);
        for (GraphQLType currentType : types) {
            if (isNotWrapped(currentType) && currentType.getName().endsWith("Connection")) {
                return true;
            }
        }
        return false;
    }

    private static Boolean hasReverseLinkDirective(GraphQLDirectiveContainer container) {
        for (GraphQLDirective directive : container.getDirectives()) {
            if (directive.getName().equals(ReverseLinkDirective.NAME)) {
                return true;
            }
        }
        return false;
    }

    private static Boolean hasLinkDirective(GraphQLDirectiveContainer container) {
        for (GraphQLDirective directive : container.getDirectives()) {
            if (directive.getName().equals(LinkDirective.NAME)) {
                return true;
            }
        }
        return false;
    }

    private static Boolean hasSearchDirective(GraphQLDirectiveContainer container) {
        for (GraphQLDirective directive : container.getDirectives()) {
            if (directive.getName().equals("search")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOneToMany(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        GraphQLOutputType targetType = field.getType();
        return isMany(targetType);
    }

    private static boolean isOneToOne(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        return !isOneToMany(field, context);
    }

    public GraphQLCodeRegistry getRegistry() {
        return registry.build();
    }

    private TraversalControl visitLinkField(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        if (hasSearchDirective(field)) {
            return visitSearchLink(field, context);
        } else if (isOneToMany(field, context) && !isConnection(field, context)) {
            return visitOneToManyLink(field, context);
        } else if (!isOneToMany(field, context) && isConnection(field, context)) {
            return visitConnectionLink(field, context);
        } else if (isOneToOne(field, context)) {
            return visitOneToOneLink(field, context);
        } else {
            return TraversalControl.CONTINUE;
        }
    }

    private TraversalControl visitSearchLink(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        registry.dataFetcher(FieldCoordinates.coordinates((GraphQLFieldsContainer) context.getParentNode(), field),
                new QueryConnectionFetcher(searchIndex, persistence, namespace, field.getType().getName())
        );
        return TraversalControl.CONTINUE;
    }

    private TraversalControl visitConnectionLink(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        GraphQLFieldsContainer sourceObject = (GraphQLFieldsContainer) context.getParentNode();
        GraphQLOutputType targetType = field.getType();

        // Unwrap the connection.
        // [Source][Target]Connection -edges-> [Source][Target]Egde -node-> Target
        GraphQLObjectType connectionType = (GraphQLObjectType) unwrapAll(targetType);
        GraphQLObjectType edgeType = (GraphQLObjectType) unwrapAll(
                connectionType.getFieldDefinition("edges").getType());
        GraphQLUnmodifiedType nodeType = unwrapAll(
                edgeType.getFieldDefinition("node").getType());

        if (sourceObject.getName().equals("Query")) {
            log.trace("RootConnection: {} -> {} -> {} ",
                    simplePrint(sourceObject),
                    field.getName(),
                    simplePrint(nodeType)
            );
            registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field),
                    new PersistenceRootConnectionFetcher(persistence, namespace, nodeType.getName()));
        } else {

            if (hasReverseLinkDirective(field)) {
                log.trace("ReverseConnection: {} -> {} -> {} ",
                        simplePrint(sourceObject),
                        field.getName(),
                        simplePrint(nodeType)
                );
                registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field),
                        new PersistenceReverseLinksConnectionFetcher(persistence, namespace, nodeType.getName(),
                                getReverseJsonNavigationPath(field, context), sourceObject.getName()));
            } else {
                log.trace("Connection: {} -> {} -> {} ",
                        simplePrint(sourceObject),
                        field.getName(),
                        simplePrint(nodeType)
                );
                registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field),
                        new PersistenceLinksConnectionFetcher(persistence, namespace, sourceObject.getName(),
                                getJsonNavigationPath(field, context), nodeType.getName()));
            }
        }
        return TraversalControl.CONTINUE;
    }

    private boolean isConnection(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        GraphQLOutputType targetType = field.getType();
        return isConnection(targetType);
    }

    private JsonNavigationPath getReverseJsonNavigationPath(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        String mappedBy = (String) field.getDirective(ReverseLinkDirective.NAME)
                .getArgument(ReverseLinkDirective.MAPPED_BY_NAME).getValue();
        return JsonNavigationPath.from(mappedBy);
    }

    private JsonNavigationPath getJsonNavigationPath(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        Collection<String> parts = computePath(field, context);
        return JsonNavigationPath.from(parts);
    }


    private TraversalControl visitOneToManyLink(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        GraphQLFieldsContainer sourceObject = (GraphQLFieldsContainer) context.getParentNode();
        GraphQLOutputType targetType = field.getType();
        if (sourceObject.getName().equals("Query")) {
            log.trace("RootOneToMany: {} -> {} -> {} ",
                    simplePrint(sourceObject),
                    field.getName(),
                    simplePrint(unwrapAll(targetType))
            );
            registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field),
                    new PersistenceLinkFetcher(persistence, namespace, field.getName(), unwrapAll(targetType).getName()));
        } else {
            if (hasReverseLinkDirective(field)) {
                log.trace("ManyToOne: {} -> {} -> {}",
                        simplePrint(sourceObject),
                        field.getName(),
                        simplePrint(unwrapAll(targetType))
                );
                log.warn("ManyToOne: is not supported for reverse links");
            } else {
                log.trace("OneToMany: {} -> {} -> {} ",
                        simplePrint(sourceObject),
                        field.getName(),
                        simplePrint(unwrapAll(targetType))
                );
                registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field), new PersistenceLinksFetcher(
                        persistence, namespace, field.getName(), unwrapAll(targetType).getName()));
            }
        }
        return TraversalControl.CONTINUE;
    }

    private TraversalControl visitOneToOneLink(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        GraphQLFieldsContainer sourceObject = (GraphQLFieldsContainer) context.getParentNode();
        GraphQLUnmodifiedType targetType = unwrapAll(field.getType());

        // TODO: Factorize the logic in PersistenceLinksConnectionFetcher.
        String targetTypeName;
        if (targetType instanceof GraphQLUnionType) {
            targetTypeName = ((GraphQLUnionType) targetType).getTypes().stream()
                    .map(GraphQLType::getName)
                    .collect(Collectors.joining("|", "(", ")"));
        } else {
            targetTypeName = targetType.getName();
        }
        if (sourceObject.getName().equals("Query")) {
            log.trace("RootOneToOne: {} -> {} -> {} ",
                    simplePrint(sourceObject),
                    field.getName(),
                    simplePrint(unwrapAll(targetType))
            );
            registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field), new PersistenceFetcher(persistence,
                    namespace, targetTypeName));
        } else {
            log.trace("OneToOne: {} -> {} -> {} ",
                    simplePrint(sourceObject),
                    field.getName(),
                    simplePrint(unwrapAll(targetType))
            );
            registry.dataFetcher(FieldCoordinates.coordinates(sourceObject, field), new PersistenceLinkFetcher(
                    persistence, namespace, field.getName(), targetTypeName));
        }
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLType> context) {
        registry.typeResolver(node, env -> {
            Map<String, Object> object = env.getObject();
            return (GraphQLObjectType) env.getSchema().getType(((DocumentKey) object.get("__graphql_internal_document_key")).entity());
        });
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {
        registry.typeResolver(node, env -> {
            Map<String, Object> object = env.getObject();
            return (GraphQLObjectType) env.getSchema().getType(((DocumentKey) object.get("__graphql_internal_document_key")).entity());
        });
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition field, TraverserContext<GraphQLType> context) {
        if (hasReverseLinkDirective(field) || hasLinkDirective(field) | hasSearchDirective(field)) {
            return visitLinkField(field, context);
        } else {
            return TraversalControl.CONTINUE;
        }
    }
}
