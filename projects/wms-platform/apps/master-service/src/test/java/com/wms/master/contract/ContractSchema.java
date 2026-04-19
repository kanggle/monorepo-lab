package com.wms.master.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thin wrapper over {@code networknt/json-schema-validator} that loads a
 * schema from the test classpath under {@code /contracts/} and validates JSON
 * strings / nodes against it. Throws a clear {@link AssertionError} listing
 * every violation on failure.
 *
 * <p>Keeping the helper minimal avoids coupling tests to the underlying
 * library's error types.
 */
public final class ContractSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final JsonSchema schema;
    private final String resource;

    private ContractSchema(JsonSchema schema, String resource) {
        this.schema = schema;
        this.resource = resource;
    }

    /**
     * Loads a schema from the classpath root. Example: {@code
     * ContractSchema.load("/contracts/http/warehouse-response.schema.json")}.
     */
    public static ContractSchema load(String classpathResource) {
        try (InputStream in = ContractSchema.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Schema not found on classpath: " + classpathResource);
            }
            JsonSchema schema = FACTORY.getSchema(in);
            return new ContractSchema(schema, classpathResource);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schema: " + classpathResource, e);
        }
    }

    public void assertValid(String json) {
        try {
            assertValid(MAPPER.readTree(json));
        } catch (IOException e) {
            throw new AssertionError("Document is not valid JSON: " + e.getMessage(), e);
        }
    }

    public void assertValid(JsonNode node) {
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            String summary = errors.stream()
                    .map(m -> m.getInstanceLocation() + ": " + m.getMessage())
                    .collect(Collectors.joining("\n  "));
            throw new AssertionError("Schema " + resource + " violated:\n  " + summary);
        }
    }
}
