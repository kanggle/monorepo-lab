package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FX cost-flow method config end-to-end integration (15th increment, TASK-FIN-BE-023 —
 * the authoritative wiring gate; Docker-free {@code :check} would not catch
 * persisted-config / audit-column population). Testcontainers MySQL (V9 runs) + real
 * Kafka + MockWebServer JWKS.
 *
 * <p><b>Shadow / net-zero.</b> This IT does NOT perform any settlement. The config
 * surface is config-only; {@code SettleForeignPositionUseCase} / {@code FxSettlementPolicy}
 * are NOT invoked. No Kafka predicate conflict from sibling classes.
 *
 * <p>Scenarios (one ordered test, AC-1 through AC-6):
 * <ol>
 *   <li><b>GET default (AC-1)</b>: unset → {@code { method: "WEIGHTED_AVERAGE" }} with
 *       no audit fields;</li>
 *   <li><b>PUT FIFO + GET round-trip (AC-2)</b>: set FIFO → 200; GET reflects FIFO +
 *       the {@code updatedBy} (actor) / {@code updatedAt} audit columns populated; audit
 *       row {@code FX_COST_FLOW_METHOD_SET} written;</li>
 *   <li><b>PUT invalid method (AC-3)</b>: {@code "LIFO"} → 400 VALIDATION_ERROR, nothing
 *       persisted.</li>
 * </ol>
 */
class LedgerFxCostFlowConfigIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String COST_FLOW_PATH =
            "/api/finance/ledger/settlements/cost-flow-config";

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void costFlowConfigLifecycle() throws Exception {
        String token = financeWriteToken();

        // AC-1: GET default — unset → WEIGHTED_AVERAGE, no audit fields.
        HttpResponse<String> getDefault = get(COST_FLOW_PATH, token);
        assertThat(getDefault.statusCode()).isEqualTo(200);
        JsonNode defData = objectMapper.readTree(getDefault.body()).get("data");
        assertThat(defData.get("method").asText()).isEqualTo("WEIGHTED_AVERAGE");
        assertThat(defData.has("updatedBy")).isFalse();
        assertThat(defData.has("updatedAt")).isFalse();

        // AC-2a: PUT FIFO → 200 with method + audit.
        HttpResponse<String> putResp = put(COST_FLOW_PATH, token, "{\"method\":\"FIFO\"}");
        assertThat(putResp.statusCode()).isEqualTo(200);
        JsonNode putData = objectMapper.readTree(putResp.body()).get("data");
        assertThat(putData.get("method").asText()).isEqualTo("FIFO");
        assertThat(putData.get("updatedBy").asText()).isEqualTo("user-1"); // JWT subject

        // AC-2b: GET reflects FIFO (last-write-wins).
        HttpResponse<String> getAfter = get(COST_FLOW_PATH, token);
        assertThat(getAfter.statusCode()).isEqualTo(200);
        JsonNode afterData = objectMapper.readTree(getAfter.body()).get("data");
        assertThat(afterData.get("method").asText()).isEqualTo("FIFO");
        assertThat(afterData.get("updatedBy").asText()).isEqualTo("user-1");
        assertThat(afterData.get("updatedAt").asText()).isNotBlank();

        // AC-2c: DB-level audit columns populated.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT updated_by, updated_at FROM fx_cost_flow_config "
                        + "WHERE tenant_id = 'finance'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("updated_by")).isEqualTo("user-1");
        assertThat(rows.get(0).get("updated_at")).isNotNull();

        // AC-2d: audit_log row FX_COST_FLOW_METHOD_SET written in the same Tx.
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = 'finance' "
                        + "AND action = 'FX_COST_FLOW_METHOD_SET' AND actor = 'user-1'",
                Integer.class);
        assertThat(auditCount).isEqualTo(1);

        // AC-3: PUT invalid method (LIFO) → 400 VALIDATION_ERROR; nothing extra persisted.
        HttpResponse<String> invalid = put(COST_FLOW_PATH, token, "{\"method\":\"LIFO\"}");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(invalid.body()).get("code").asText())
                .isEqualTo("VALIDATION_ERROR");

        // Confirm the config row still reads FIFO (no partial persist on the invalid attempt).
        Integer configCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_cost_flow_config WHERE tenant_id = 'finance' "
                        + "AND method = 'FIFO'",
                Integer.class);
        assertThat(configCount).isEqualTo(1);

        // AC-4 / net-zero confirmation: SettleForeignPositionUseCase was NOT invoked — this
        // test performs no settlement. The journal_entry table remains empty; the config does
        // not influence settlement in this increment (shadow).
        Long entryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entry WHERE tenant_id = 'finance'", Long.class);
        assertThat(entryCount).isZero();
    }
}
