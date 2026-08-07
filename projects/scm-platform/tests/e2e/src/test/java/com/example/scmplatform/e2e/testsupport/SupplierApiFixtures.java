package com.example.scmplatform.e2e.testsupport;

import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.scmplatform.e2e.testsupport.E2ETestFixtures.uniqueIdempotencyKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Supplier setup for the e2e suite, through the real HTTP surface.
 *
 * <p>This replaces {@code ProcurementDbFixtures.insertActiveSupplier}, which
 * wrote the row with direct JDBC because v1 had no registration endpoint at
 * all (TASK-SCM-BE-059 / ADR-SCM-001 option A). That was the suite's only
 * production-API bypass; with the endpoint in place, keeping both paths alive
 * would let the fixture and the product drift apart silently — so the JDBC one
 * is gone rather than merely deprecated.
 *
 * <p>Registration needs an <b>OPERATOR</b> token; a BUYER token is refused with
 * 403 {@code PERMISSION_DENIED}. Scenarios that otherwise act as a buyer still
 * mint an operator token just for this setup step, which mirrors how a real
 * demo works: an operator maintains the master, a buyer orders against it.
 */
public final class SupplierApiFixtures {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SupplierApiFixtures() {}

    /** Gateway path fronting {@code POST/GET /api/procurement/suppliers}. */
    public static String pathProcurementSuppliers() {
        return "/api/v1/procurement/suppliers";
    }

    /**
     * Unique per-scenario supplier code. Codes are the tenant-scoped natural
     * key, so a shared literal would make two scenarios converge onto one row
     * (the TASK-MONO-023d race-avoidance rule applied to the new key).
     */
    public static String uniqueSupplierCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Registers an ACTIVE supplier through the gateway and returns its id.
     *
     * @param operatorToken an OPERATOR-role token — a BUYER token yields 403
     */
    public static String registerActiveSupplier(HttpClient http,
                                                URI gatewayBaseUri,
                                                String operatorToken,
                                                String codePrefix,
                                                String name) throws Exception {
        String body = MAPPER.writeValueAsString(java.util.Map.of(
                "code", uniqueSupplierCode(codePrefix),
                "name", name));

        HttpResponse<String> response = sendString(http, authedJson(
                gatewayBaseUri.resolve(pathProcurementSuppliers()), operatorToken)
                .header("Idempotency-Key", uniqueIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());

        if (response.statusCode() != 201 && response.statusCode() != 200) {
            throw new IllegalStateException(
                    "supplier registration failed: HTTP " + response.statusCode()
                            + " body=" + response.body());
        }
        JsonNode data = MAPPER.readTree(response.body()).get("data");
        if (data == null || data.get("id") == null) {
            throw new IllegalStateException(
                    "supplier registration returned no id: " + response.body());
        }
        return data.get("id").asText();
    }
}
