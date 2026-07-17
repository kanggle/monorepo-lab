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
 * Per-account FX cost-flow method override end-to-end integration (21st increment,
 * TASK-FIN-BE-029 — the authoritative config-lifecycle gate, AC-4; mirrors
 * {@link LedgerFxCostFlowConfigIntegrationTest}). Testcontainers MySQL (V11 runs) + real Kafka +
 * MockWebServer JWKS.
 *
 * <p><b>Shadow / no settlement.</b> This IT does NOT perform any settlement — the config surface
 * is config-only here ({@code SettleForeignPositionUseCase} is exercised by
 * {@link LedgerPerAccountFifoSettlementIntegrationTest}, AC-3). No Kafka predicate conflict. A
 * UNIQUE {@code ledgerAccountCode} so the override never collides with a sibling class.
 *
 * <p>Scenarios (one ordered test, AC-4):
 * <ol>
 *   <li><b>GET empty</b>: no overrides → {@code []};</li>
 *   <li><b>PUT FIFO override</b>: → 200 with the account + method + audit; GET reflects it; DB
 *       audit columns + {@code audit_log} {@code FX_COST_FLOW_ACCOUNT_METHOD_SET};</li>
 *   <li><b>DELETE</b>: → 200 {@code cleared=true}; the row is gone; {@code audit_log}
 *       {@code FX_COST_FLOW_ACCOUNT_METHOD_CLEARED};</li>
 *   <li><b>PUT invalid method</b>: {@code "LIFO"} → 400 VALIDATION_ERROR, nothing persisted.</li>
 * </ol>
 */
class LedgerFxCostFlowAccountConfigIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String ACCOUNTS_PATH =
            "/api/finance/ledger/settlements/cost-flow-config/accounts";
    /** Unique to this class — avoids any cross-test override collision. */
    private static final String ACCOUNT = "FX_ACCT_CFG_USD_WALLET";

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

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .DELETE().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void costFlowAccountConfigLifecycle() throws Exception {
        String token = financeWriteToken();

        // AC-4a: GET empty — no overrides → [].
        HttpResponse<String> getEmpty = get(ACCOUNTS_PATH, token);
        assertThat(getEmpty.statusCode()).isEqualTo(200);
        JsonNode emptyData = objectMapper.readTree(getEmpty.body()).get("data");
        assertThat(emptyData.isArray()).isTrue();
        assertThat(emptyData).isEmpty();

        // AC-4b: PUT FIFO override → 200 with account + method + audit.
        HttpResponse<String> putResp = put(ACCOUNTS_PATH + "/" + ACCOUNT, token,
                "{\"method\":\"FIFO\"}");
        assertThat(putResp.statusCode()).isEqualTo(200);
        JsonNode putData = objectMapper.readTree(putResp.body()).get("data");
        assertThat(putData.get("ledgerAccountCode").asText()).isEqualTo(ACCOUNT);
        assertThat(putData.get("method").asText()).isEqualTo("FIFO");
        assertThat(putData.get("updatedBy").asText()).isEqualTo("user-1"); // JWT subject

        // AC-4c: GET reflects the override.
        HttpResponse<String> getAfter = get(ACCOUNTS_PATH, token);
        assertThat(getAfter.statusCode()).isEqualTo(200);
        JsonNode afterData = objectMapper.readTree(getAfter.body()).get("data");
        assertThat(afterData).hasSize(1);
        assertThat(afterData.get(0).get("ledgerAccountCode").asText()).isEqualTo(ACCOUNT);
        assertThat(afterData.get(0).get("method").asText()).isEqualTo("FIFO");
        assertThat(afterData.get(0).get("updatedBy").asText()).isEqualTo("user-1");
        assertThat(afterData.get(0).get("updatedAt").asText()).isNotBlank();

        // AC-4d: DB-level audit columns populated.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT updated_by, updated_at FROM fx_cost_flow_account_config "
                        + "WHERE tenant_id = 'finance' AND ledger_account_code = '" + ACCOUNT + "'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("updated_by")).isEqualTo("user-1");
        assertThat(rows.get(0).get("updated_at")).isNotNull();

        // AC-4e: audit_log row FX_COST_FLOW_ACCOUNT_METHOD_SET written in the same Tx.
        Integer setAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = 'finance' "
                        + "AND action = 'FX_COST_FLOW_ACCOUNT_METHOD_SET' AND actor = 'user-1' "
                        + "AND aggregate_id = 'finance:" + ACCOUNT + "'",
                Integer.class);
        assertThat(setAuditCount).isEqualTo(1);

        // AC-4f: DELETE → 200 cleared=true; the override row is gone.
        HttpResponse<String> del = delete(ACCOUNTS_PATH + "/" + ACCOUNT, token);
        assertThat(del.statusCode()).isEqualTo(200);
        JsonNode delData = objectMapper.readTree(del.body()).get("data");
        assertThat(delData.get("ledgerAccountCode").asText()).isEqualTo(ACCOUNT);
        assertThat(delData.get("cleared").asBoolean()).isTrue();

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_cost_flow_account_config WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = '" + ACCOUNT + "'",
                Integer.class);
        assertThat(remaining).isZero();

        // AC-4g: audit_log row FX_COST_FLOW_ACCOUNT_METHOD_CLEARED written.
        Integer clearAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE tenant_id = 'finance' "
                        + "AND action = 'FX_COST_FLOW_ACCOUNT_METHOD_CLEARED' AND actor = 'user-1' "
                        + "AND aggregate_id = 'finance:" + ACCOUNT + "'",
                Integer.class);
        assertThat(clearAuditCount).isEqualTo(1);

        // AC-4h: GET empty again after the delete.
        JsonNode afterDelete = objectMapper.readTree(get(ACCOUNTS_PATH, token).body()).get("data");
        assertThat(afterDelete).isEmpty();

        // AC-4i: PUT invalid method (LIFO) → 400 VALIDATION_ERROR; nothing persisted.
        HttpResponse<String> invalid = put(ACCOUNTS_PATH + "/" + ACCOUNT, token,
                "{\"method\":\"LIFO\"}");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(invalid.body()).get("code").asText())
                .isEqualTo("VALIDATION_ERROR");
        Integer afterInvalid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_cost_flow_account_config WHERE tenant_id = 'finance' "
                        + "AND ledger_account_code = '" + ACCOUNT + "'",
                Integer.class);
        assertThat(afterInvalid).isZero();
    }
}
