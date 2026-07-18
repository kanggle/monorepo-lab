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
 * Per-account FIFO override settlement end-to-end integration (21st increment,
 * TASK-FIN-BE-029 — the authoritative AC-3 gate; ADR-001 D1 follow-up). Mirrors
 * {@link LedgerFifoSettlementIntegrationTest}'s lot-building setup, but leaves the per-TENANT
 * cost-flow config UNSET (so the tenant default is WEIGHTED_AVERAGE) and instead PUTs a per-ACCOUNT
 * override = FIFO for the settled account. Settling a multi-lot position on that account must then
 * consume the open lots oldest-first (FIFO carrying basis) — proving the settlement path reads the
 * per-account override (precedence account override > tenant default > WEIGHTED_AVERAGE).
 *
 * <p>A UNIQUE asset account ({@code FX_ACCT_FIFO_USD_WALLET}) distinct from the sibling FIFO ITs'
 * accounts so its positions / lots never collide on the shared-Kafka MySQL container. No
 * {@code awaitEnvelope} cross-matching is used (DB-level + response assertions only), so there is
 * no Kafka predicate isolation concern.
 */
class LedgerPerAccountFifoSettlementIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String FX_ACCOUNT = "FX_ACCT_FIFO_USD_WALLET";
    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";
    private static final String ACCOUNTS_PATH =
            "/api/finance/ledger/settlements/cost-flow-config/accounts";

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> postEntry(String token, String idempotencyKey, String body)
            throws Exception {
        return post("/api/finance/ledger/entries", token, idempotencyKey, body);
    }

    private HttpResponse<String> postSettlement(String token, String idempotencyKey, String body)
            throws Exception {
        return post("/api/finance/ledger/settlements", token, idempotencyKey, body);
    }

    private HttpResponse<String> post(String path, String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** PUT the per-account FIFO override via the REST surface (proves the wired read path). */
    private void putAccountOverride(String token, String account, String method) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + ACCOUNTS_PATH + "/" + account))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"method\":\"" + method + "\"}"))
                .build();
        assertThat(http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    /** Seed the unique ASSET account so the manual path (no lazy mint) accepts it. */
    private void seedAssetAccount() {
        seedAssetAccount(FX_ACCOUNT);
    }

    /** Post a single USD acquisition (DR FX_ACCOUNT foreign@base / CR SETTLEMENT_SUSPENSE base). */
    private void acquire(String token, long foreignMinor, long baseMinor) throws Exception {
        String key = "ACCT-FIFO-ACQ-" + newId().substring(0, 8);
        String body = "{"
                + "\"reference\":\"ACCT-FIFO-ACQ\",\"memo\":\"acquire USD\",\"lines\":["
                + "{\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + foreignMinor + "\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + PROCEEDS + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + baseMinor + "\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, key, body).statusCode()).isEqualTo(201);
    }

    private String settlementBody(String rate, String settleForeignAmount) {
        return "{"
                + "\"ledgerAccountCode\":\"" + FX_ACCOUNT + "\",\"currency\":\"USD\","
                + "\"settlementRate\":\"" + rate + "\",\"proceedsAccountCode\":\"" + PROCEEDS + "\","
                + (settleForeignAmount != null
                        ? "\"settleForeignAmount\":\"" + settleForeignAmount + "\"," : "")
                + "\"reference\":\"ACCT-FIFO-SETTLE\",\"memo\":\"FIFO liquidation\"}";
    }

    /** The open lots (remaining > 0) of the FX_ACCOUNT USD position, FIFO-ordered. */
    private List<Map<String, Object>> openLots() {
        return openLots(FX_ACCOUNT);
    }

    private static long asLong(Object v) {
        return ((Number) v).longValue();
    }

    @Test
    void accountOverrideElevatesToFifoAndConsumesOldestLotsFirst() throws Exception {
        String token = financeWriteToken();
        seedAssetAccount();

        // Tenant config is UNSET (default WEIGHTED_AVERAGE) — confirm no per-tenant row.
        Integer tenantConfigRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fx_cost_flow_config WHERE tenant_id = 'finance'", Integer.class);
        assertThat(tenantConfigRows).isZero();

        // PUT the per-account FIFO override for the settled account ONLY.
        putAccountOverride(token, FX_ACCOUNT, "FIFO");

        // Build a 2-lot position: 1000 USD @ 1,300,000 then 1000 USD @ 1,400,000.
        acquire(token, 1_000L, 1_300_000L);   // lot1 — oldest @ 1300/USD
        acquire(token, 1_000L, 1_400_000L);   // lot2 — @ 1400/USD
        assertThat(openLots()).hasSize(2);

        // Settle 1500 USD @ spot 1500 → proceeds 2,250,000.
        // FIFO C_settle = lot1 1,300,000 (full) + round(1,400,000×500/1000)=700,000 = 2,000,000.
        // realized = 2,250,000 − 2,000,000 = 250,000 (FIFO — the weighted-average pool avg would
        // give 225,000). The non-225,000 result proves the per-account override drove the FIFO walk
        // even though the tenant default is unset (WEIGHTED_AVERAGE).
        HttpResponse<String> r1 = postSettlement(token, "ACCT-FIFO-1-" + newId().substring(0, 8),
                settlementBody("1500", "1500"));
        assertThat(r1.statusCode()).isEqualTo(201);
        JsonNode b1 = objectMapper.readTree(r1.body());
        assertThat(b1.at("/data/settled").asBoolean()).isTrue();
        assertThat(b1.at("/data/proceedsBaseMinor").asText()).isEqualTo("2250000");
        assertThat(b1.at("/data/realizedBaseMinor").asText()).isEqualTo("250000");   // FIFO, not 225000
        assertThat(b1.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        // residual is lot-exact: (2000 − 1500, 2,700,000 − 2,000,000) = (500, 700,000) = lot2's remainder.
        assertThat(b1.at("/data/residualForeignMinor").asText()).isEqualTo("500");
        assertThat(b1.at("/data/residualCarryingBaseMinor").asText()).isEqualTo("700000");

        // Oldest lot consumed first: lot1 fully gone, lot2 has 500 / 700,000 remaining.
        List<Map<String, Object>> lots = openLots();
        assertThat(lots).hasSize(1);
        assertThat(asLong(lots.get(0).get("remaining_foreign_minor"))).isEqualTo(500L);
        assertThat(asLong(lots.get(0).get("carrying_base_minor"))).isEqualTo(700_000L);
    }
}
