package no.ssb.lds.graphql;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import no.ssb.lds.api.persistence.Transaction;
import no.ssb.lds.api.persistence.buffered.BufferedPersistence;
import no.ssb.lds.api.persistence.buffered.Document;
import no.ssb.lds.api.persistence.buffered.DocumentIterator;
import no.ssb.lds.core.buffered.DocumentToJson;
import org.json.JSONObject;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersistenceLinksFetcher implements DataFetcher<List<Map<String, Object>>> {

    private final String field;
    private final String target;
    private final BufferedPersistence persistence;
    private final Pattern pattern;
    private final String namespace;

    public PersistenceLinksFetcher(BufferedPersistence persistence, String namespace, String field, String target) {
        this.field = field;
        this.target = target;
        this.persistence = persistence;
        this.pattern = Pattern.compile("/" + target + "/(?<id>.*)");
        this.namespace = namespace;
    }

    @Override
    public List<Map<String, Object>> get(DataFetchingEnvironment environment) throws Exception {
        Map<String, Object> source = environment.getSource();
        List<String> links = (List<String>) source.get(field);
        List<Map<String, Object>> results = new ArrayList<>();
        if (links == null) {
            return null;
        }
        for (String link : links) {
            Matcher matcher = pattern.matcher(link);
            if (matcher.matches()) {
                String id = matcher.group("id");
                // TODO get snapshot timestamp from client through data-fetching-environment
                ZonedDateTime snapshot = ZonedDateTime.now(ZoneId.of("Etc/UTC"));
                JSONObject entity = readDocument(id, snapshot);
                results.add(entity != null ? entity.toMap() : null);
            } else {
                // TODO: Handle.
            }
        }
        return results;
    }

    private JSONObject readDocument(String id, ZonedDateTime snapshot) {
        Document document;
        try (Transaction tx = persistence.createTransaction(true)) {
            CompletableFuture<DocumentIterator> future = persistence.read(tx, snapshot, namespace, target, id);
            DocumentIterator iterator = future.join();
            if (!iterator.hasNext()) {
                return null;
            }
            document = iterator.next();
        }
        return new DocumentToJson(document).toJSONObject();
    }
}
