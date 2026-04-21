package com.example.order.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class ContractTestHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ContractTestHelper() {}

    public static Set<String> extractFieldNames(String json) throws Exception {
        return extractFieldNames(objectMapper.readTree(json));
    }

    public static Set<String> extractFieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    public static void assertFieldsMatch(String json, Set<String> expectedFields, String specRef) throws Exception {
        Set<String> actualFields = extractFieldNames(json);
        assertEquals(expectedFields, actualFields, "Fields must match " + specRef);
    }

    public static void assertFieldsMatch(JsonNode node, Set<String> expectedFields, String specRef) {
        Set<String> actualFields = extractFieldNames(node);
        assertEquals(expectedFields, actualFields, "Fields must match " + specRef);
    }
}
