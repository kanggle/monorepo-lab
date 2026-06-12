package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * FX gain/loss revaluation end-to-end integration (9th increment, TASK-FIN-BE-015 —
 * the authoritative round-trip gate, AC-7). Testcontainers MySQL + real Kafka +
 * MockWebServer JWKS. <b>No migration</b> — the existing columns carry the
 * base-adjustment line.
 *
 * <ol>
 *   <li>establish a USD position on {@code CASH_CLEARING} via a multi-currency manual
 *       entry (DR USD 10000 / CR KRW wallet @ rate 13.0 → carrying 130000 KRW);</li>
 *   <li>{@code POST /revaluations {CASH_CLEARING, USD, 13.5}} → 201 revalued:true
 *       FX_GAIN delta 5000; the 2-line entry persists (DR CASH_CLEARING USD amount 0 /
 *       base +5000, CR FX_GAIN 5000); trial balance stays base-balanced; the USD
 *       foreign balance is unchanged; the base carrying == foreignBalance × 13.5;
 *       consume {@code entry.posted.v1} with {@code sourceType=REVALUATION} (AC-4);</li>
 *   <li>a SECOND revaluation @ 14.0 books only the incremental delta (AC-3 — no
 *       double-booking: USD balance still unchanged, carrying == × 14.0);</li>
 *   <li>a lower-rate 13.0 revaluation books FX_LOSS;</li>
 *   <li>a replay of the first key → 200 same entryId (AC-5);</li>
 *   <li>{@code closingRate:0} → 422 REVALUATION_RATE_INVALID (AC-5);</li>
 *   <li>a no-position currency (EUR) → 200 revalued:false (AC-5);</li>
 *   <li>a back-dated revaluation into a CLOSED window → 422 LEDGER_PERIOD_CLOSED (AC-4);</li>
 *   <li>cross-tenant JWT → 403 (AC-7).</li>
 * </ol>
 *
 * Ordering matters — the closed-window scenario is last, so this is one ordered test.
 */
class LedgerFxRevaluationIntegrationTest extends AbstractLedgerIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postEntry(String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/entries"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postRevaluation(String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/revaluations"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postPeriod(String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/periods"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> closePeriod(String token, String periodId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/finance/ledger/periods/" + periodId + "/close"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String revaluationBody(String currency, String rate, Instant postedAt) {
        return "{"
                + "\"ledgerAccountCode\":\"CASH_CLEARING\","
                + "\"currency\":\"" + currency + "\","
                + "\"closingRate\":\"" + rate + "\""
                + (postedAt != null ? ",\"postedAt\":\"" + postedAt + "\"" : "")
                + ",\"reference\":\"FX-REVAL-1\",\"memo\":\"month-end revaluation\"}";
    }

    /** The CASH_CLEARING USD position's foreign balance + base carrying from the DB. */
    private long[] usdPosition() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount_minor ELSE 0 END),0) AS f, "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN base_amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN base_amount_minor ELSE 0 END),0) AS b "
                        + "FROM journal_line "
                        + "WHERE tenant_id='finance' AND ledger_account_code='CASH_CLEARING' AND currency='USD'");
        Map<String, Object> r = rows.get(0);
        return new long[]{((Number) r.get("f")).longValue(), ((Number) r.get("b")).longValue()};
    }

    @Test
    void fxRevaluationFunnelsThroughTheGuardedWritePathWithoutDoubleBooking() throws Exception {
        String token = financeReadToken();

        // (0) Drive a TOPUP auto-journal so a KRW wallet liability account exists (the
        //     manual path rejects an unknown account — no lazy mint).
        String walletAcct = "acc-" + newId();
        String wallet = "CUSTOMER_WALLET:" + walletAcct;
        publish(TOPIC_COMPLETED, walletAcct,
                completedEnvelope(newId(), newId(), walletAcct, "TOPUP", 150_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerAccountJpa.findByCodeAndTenantId(wallet, "finance")).isPresent());

        // (1) Establish a USD position on CASH_CLEARING — DR USD 10000 (base 130000 KRW
        //     @ rate 13.0) / CR KRW wallet 130000.
        String posKey = "POS-" + newId().substring(0, 8);
        String posBody = "{"
                + "\"reference\":\"USD-POS-1\",\"memo\":\"establish USD position\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"10000\",\"currency\":\"USD\"},"
                + "\"baseAmount\":{\"amount\":\"130000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + wallet + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"130000\",\"currency\":\"KRW\"}}"
                + "]}";
        assertThat(postEntry(token, posKey, posBody).statusCode()).isEqualTo(201);

        long[] before = usdPosition();
        assertThat(before[0]).isEqualTo(10_000L);   // USD foreign balance
        assertThat(before[1]).isEqualTo(130_000L);  // KRW carrying @ 13.0

        // (2) Revalue @ 13.5 → 201 FX_GAIN delta 5000.
        String key1 = "REVAL1-" + newId().substring(0, 8);
        HttpResponse<String> r1 = postRevaluation(token, key1, revaluationBody("USD", "13.5", null));
        assertThat(r1.statusCode()).isEqualTo(201);
        JsonNode b1 = objectMapper.readTree(r1.body());
        assertThat(b1.at("/data/revalued").asBoolean()).isTrue();
        assertThat(b1.at("/data/deltaBaseMinor").asText()).isEqualTo("5000");
        assertThat(b1.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        String entryId1 = b1.at("/data/entry/entryId").asText();
        assertThat(b1.at("/data/entry/source/sourceType").asText()).isEqualTo("REVALUATION");

        // The 2-line entry — DR CASH_CLEARING USD amount 0 / base 5000, CR FX_GAIN 5000.
        JsonNode lines1 = b1.at("/data/entry/lines");
        assertThat(lines1).hasSize(2);
        JsonNode adj = lines1.get(0).get("ledgerAccountCode").asText().equals("CASH_CLEARING")
                ? lines1.get(0) : lines1.get(1);
        JsonNode gain = adj == lines1.get(0) ? lines1.get(1) : lines1.get(0);
        assertThat(adj.get("direction").asText()).isEqualTo("DEBIT");
        assertThat(adj.get("money").get("amount").asText()).isEqualTo("0");
        assertThat(adj.get("money").get("currency").asText()).isEqualTo("USD");
        assertThat(adj.get("baseAmount").get("amount").asText()).isEqualTo("5000");
        assertThat(gain.get("ledgerAccountCode").asText()).isEqualTo("FX_GAIN");
        assertThat(gain.get("direction").asText()).isEqualTo("CREDIT");
        assertThat(gain.get("money").get("amount").asText()).isEqualTo("5000");

        // Trial balance stays base-balanced; USD foreign balance unchanged; carrying == 10000 × 13.5.
        JsonNode tb = objectMapper.readTree(get("/api/finance/ledger/trial-balance", token).body());
        assertThat(tb.at("/data/inBalance").asBoolean()).isTrue();
        assertThat(tb.at("/data/grandBaseDebitTotal/amount").asText())
                .isEqualTo(tb.at("/data/grandBaseCreditTotal/amount").asText());
        long[] afterReval1 = usdPosition();
        assertThat(afterReval1[0]).isEqualTo(10_000L);   // foreign unchanged
        assertThat(afterReval1[1]).isEqualTo(135_000L);  // carrying == 10000 × 13.5

        // Consume the GL feed and assert REVALUATION provenance.
        JsonNode entryEnv = awaitEnvelope(TOPIC_ENTRY_POSTED,
                env -> env.path("payload").path("entryId").asText().equals(entryId1),
                Duration.ofSeconds(30));
        JsonNode payload = entryEnv.get("payload");
        assertThat(payload.get("source").get("sourceType").asText()).isEqualTo("REVALUATION");
        assertThat(payload.get("source").get("sourceEventId").asText()).isEqualTo("reval:" + key1);
        assertThat(payload.get("lines")).hasSize(2);

        // (3) Second revaluation @ 14.0 — books only the incremental delta (no double-booking).
        //     carrying 135000 → 140000 → delta +5000.
        String key2 = "REVAL2-" + newId().substring(0, 8);
        HttpResponse<String> r2 = postRevaluation(token, key2, revaluationBody("USD", "14.0", null));
        assertThat(r2.statusCode()).isEqualTo(201);
        JsonNode b2 = objectMapper.readTree(r2.body());
        assertThat(b2.at("/data/deltaBaseMinor").asText()).isEqualTo("5000"); // incremental, not 10000
        assertThat(b2.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        long[] afterReval2 = usdPosition();
        assertThat(afterReval2[0]).isEqualTo(10_000L);   // foreign STILL unchanged
        assertThat(afterReval2[1]).isEqualTo(140_000L);  // carrying == 10000 × 14.0

        // (4) Lower-rate revaluation @ 13.0 → FX_LOSS. carrying 140000 → 130000 → delta -10000.
        String key3 = "REVAL3-" + newId().substring(0, 8);
        HttpResponse<String> r3 = postRevaluation(token, key3, revaluationBody("USD", "13.0", null));
        assertThat(r3.statusCode()).isEqualTo(201);
        JsonNode b3 = objectMapper.readTree(r3.body());
        assertThat(b3.at("/data/deltaBaseMinor").asText()).isEqualTo("-10000");
        assertThat(b3.at("/data/outcome").asText()).isEqualTo("FX_LOSS");
        long[] afterReval3 = usdPosition();
        assertThat(afterReval3[0]).isEqualTo(10_000L);
        assertThat(afterReval3[1]).isEqualTo(130_000L);  // carrying == 10000 × 13.0

        // (5) Replay the FIRST key → 200, same entryId (no second post).
        HttpResponse<String> replay = postRevaluation(token, key1, revaluationBody("USD", "13.5", null));
        assertThat(replay.statusCode()).isEqualTo(200);
        JsonNode replayBody = objectMapper.readTree(replay.body());
        assertThat(replayBody.at("/data/revalued").asBoolean()).isFalse();
        assertThat(replayBody.at("/data/reason").asText()).isEqualTo("REPLAY");
        assertThat(replayBody.at("/data/entry/entryId").asText()).isEqualTo(entryId1);

        // (6) closingRate 0 → 422 REVALUATION_RATE_INVALID.
        HttpResponse<String> zeroRate =
                postRevaluation(token, "ZERO-" + newId().substring(0, 8),
                        revaluationBody("USD", "0", null));
        assertThat(zeroRate.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(zeroRate.body()).at("/code").asText())
                .isEqualTo("REVALUATION_RATE_INVALID");

        // (7) A currency with no position (EUR) → 200 revalued:false (key not consumed).
        HttpResponse<String> noPos =
                postRevaluation(token, "EUR-" + newId().substring(0, 8),
                        revaluationBody("EUR", "14.0", null));
        assertThat(noPos.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(noPos.body()).at("/data/revalued").asBoolean()).isFalse();
        assertThat(objectMapper.readTree(noPos.body()).at("/data/reason").asText())
                .isEqualTo("NO_POSITION");

        // (8) Back-dated revaluation into a CLOSED window → 422 LEDGER_PERIOD_CLOSED.
        //     MICROS truncation: MySQL DATETIME(6) round-trip (a known finance-IT trap).
        Instant backDated = Instant.now().minus(Duration.ofDays(60)).truncatedTo(ChronoUnit.MICROS);
        Instant windowFrom = backDated.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        Instant windowTo = backDated.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        HttpResponse<String> openResp = postPeriod(token,
                "{\"from\":\"" + windowFrom + "\",\"to\":\"" + windowTo + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = objectMapper.readTree(openResp.body()).at("/data/periodId").asText();
        assertThat(closePeriod(token, periodId).statusCode()).isEqualTo(200);

        HttpResponse<String> intoClosed =
                postRevaluation(token, "CLOSED-" + newId().substring(0, 8),
                        revaluationBody("USD", "15.0", backDated));
        assertThat(intoClosed.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(intoClosed.body()).at("/code").asText())
                .isEqualTo("LEDGER_PERIOD_CLOSED");

        // (9) Cross-tenant JWT → 403.
        HttpResponse<String> crossTenant =
                postRevaluation(crossTenantToken(), "X-" + newId().substring(0, 8),
                        revaluationBody("USD", "13.5", null));
        assertThat(crossTenant.statusCode()).isEqualTo(403);
    }
}
