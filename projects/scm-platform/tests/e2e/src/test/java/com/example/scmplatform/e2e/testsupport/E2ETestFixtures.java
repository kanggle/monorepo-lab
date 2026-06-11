package com.example.scmplatform.e2e.testsupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Common request builders and fixture data generators for the scm-platform
 * v1 cross-service e2e suite (TASK-SCM-INT-001).
 *
 * <p>Unique-suffix helpers ({@link #uniqueIdempotencyKey()},
 * {@link #uniqueSku(String)}, {@link #uniqueSupplierAckRef(String)}) follow
 * the {@code TASK-MONO-023d} race-avoidance pattern: every scenario
 * differentiates its fixtures so a Kafka topic shared across scenarios
 * cannot accidentally satisfy another scenario's Awaitility assertion.
 */
public final class E2ETestFixtures {

    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private E2ETestFixtures() {}

    // ------------------------------------------------------------------
    // Path helpers — gateway-prefixed (`/api/v1/...`) paths used by tests
    // ------------------------------------------------------------------

    /** Gateway path that fronts {@code POST /api/procurement/po} on procurement-service. */
    public static String pathProcurementPo() {
        return "/api/v1/procurement/po";
    }

    /** Gateway path that fronts {@code GET/POST /api/procurement/po/{id}} on procurement-service. */
    public static String pathProcurementPoById(String poId) {
        return "/api/v1/procurement/po/" + poId;
    }

    /** Gateway path that fronts {@code POST /api/procurement/po/{id}/submit} on procurement-service. */
    public static String pathProcurementPoSubmit(String poId) {
        return "/api/v1/procurement/po/" + poId + "/submit";
    }

    /** Gateway path that fronts {@code POST /api/procurement/po/{id}/confirm} on procurement-service. */
    public static String pathProcurementPoConfirm(String poId) {
        return "/api/v1/procurement/po/" + poId + "/confirm";
    }

    /** Direct procurement-service path — supplier ack webhook (gateway not in path). */
    public static String pathSupplierAckWebhook() {
        return "/api/procurement/webhooks/supplier-ack";
    }

    /** Direct procurement-service path — ASN webhook (gateway not in path). */
    public static String pathAsnWebhook() {
        return "/api/procurement/webhooks/asn";
    }

    /** Gateway path for inventory-visibility cross-node snapshot. */
    public static String pathInventoryVisibilitySnapshot() {
        return "/api/v1/inventory-visibility/snapshot";
    }

    /** Gateway path for inventory-visibility per-SKU breakdown. */
    public static String pathInventoryVisibilitySkuBreakdown(String sku) {
        return "/api/v1/inventory-visibility/sku/" + sku;
    }

    // ------------------------------------------------------------------
    // demand-planning (ADR-MONO-027) — gateway-fronted operator paths
    // ------------------------------------------------------------------

    /** Gateway path: seed/inspect the SKU→supplier mapping. */
    public static String pathDemandPlanningSkuSupplierMap(String skuCode) {
        return "/api/v1/demand-planning/sku-supplier-map/" + skuCode;
    }

    /** Gateway path: seed/inspect the reorder policy. */
    public static String pathDemandPlanningPolicy(String skuCode) {
        return "/api/v1/demand-planning/policies/" + skuCode;
    }

    /** Gateway path: list reorder suggestions (optionally filtered by skuCode). */
    public static String pathDemandPlanningSuggestions(String skuCode) {
        return "/api/v1/demand-planning/suggestions?skuCode=" + skuCode;
    }

    /** Gateway path: approve a suggestion → procurement DRAFT PO (D5). */
    public static String pathDemandPlanningApprove(String suggestionId) {
        return "/api/v1/demand-planning/suggestions/" + suggestionId + "/approve";
    }

    // ------------------------------------------------------------------
    // Fixture data generators — unique per call to avoid cross-scenario races
    // ------------------------------------------------------------------

    public static String uniqueIdempotencyKey() {
        return "idem-" + UUID.randomUUID().toString().substring(0, 12);
    }

    public static String uniqueSku(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static String uniqueSupplierAckRef(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    public static String uniqueSupplierAsnRef(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    public static String randomAccountId() {
        return UUID.randomUUID().toString();
    }

    public static String randomLocationId() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    public static HttpRequest.Builder authedJson(URI uri, String bearerToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Forwarded-For", "10.0.0." + (1 + (int) (Math.random() * 250)));
    }

    public static HttpRequest.Builder authedGet(URI uri, String bearerToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .header("X-Forwarded-For", "10.0.0." + (1 + (int) (Math.random() * 250)));
    }

    public static HttpRequest.Builder webhookJson(URI uri, String supplierSecret) {
        return HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Supplier-Signature", supplierSecret);
    }

    public static HttpResponse<String> sendString(HttpClient http, HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
