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
 * Realized FX gain/loss on settlement end-to-end integration (10th increment,
 * TASK-FIN-BE-016 — the authoritative round-trip gate, AC-7). Testcontainers MySQL +
 * real Kafka + MockWebServer JWKS. <b>No migration</b> — the existing columns carry the
 * settlement entry (reuses the 8th-increment multi-currency line + the 9th-increment FX
 * accounts).
 *
 * <p>The proceeds account is the already-seeded {@code SETTLEMENT_SUSPENSE} (no new GL
 * account / no lazy mint — the operator settles into an existing account).
 *
 * <ol>
 *   <li>establish a USD position on {@code CASH_CLEARING} via a multi-currency manual
 *       entry (DR USD 10000 / CR KRW wallet @ rate 13.0 → carrying 130000 KRW);</li>
 *   <li>{@code POST /settlements {CASH_CLEARING, USD, 13.7, SETTLEMENT_SUSPENSE}} → 201
 *       settled:true FX_GAIN realized 7000 / proceeds 137000; the 3-line entry persists
 *       (DR SETTLEMENT_SUSPENSE 137000 / CR CASH_CLEARING 10000 USD@130000 base / CR
 *       FX_GAIN 7000); the USD position on CASH_CLEARING is removed (foreign 0 + base 0);
 *       proceeds in SETTLEMENT_SUSPENSE; trial balance base-balanced; consume
 *       {@code entry.posted.v1} with {@code sourceType=SETTLEMENT} (AC-4);</li>
 *   <li>a below-carrying settlement → FX_LOSS;</li>
 *   <li>a revalue-then-settle realizes the incremental delta (AC-3 — no double-count);</li>
 *   <li>a replay of the first key → 200 same entryId (AC-5);</li>
 *   <li>{@code settlementRate:0} → 422 SETTLEMENT_RATE_INVALID (AC-5);</li>
 *   <li>an unknown proceeds account → 404 LEDGER_ACCOUNT_NOT_FOUND (AC-5);</li>
 *   <li>a no-position currency (EUR) → 200 settled:false (AC-5);</li>
 *   <li>a back-dated settlement into a CLOSED window → 422 LEDGER_PERIOD_CLOSED (AC-4);</li>
 *   <li>cross-tenant JWT → 403 (AC-7).</li>
 * </ol>
 *
 * Ordering matters — the closed-window scenario is last, so this is one ordered test.
 */
class LedgerFxSettlementIntegrationTest extends AbstractLedgerIntegrationTest {

    private static final String PROCEEDS = "SETTLEMENT_SUSPENSE";

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

    private HttpResponse<String> postSettlement(String token, String idempotencyKey, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/finance/ledger/settlements"))
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

    private String settlementBody(String currency, String rate, String proceedsAccount,
                                  Instant postedAt) {
        return "{"
                + "\"ledgerAccountCode\":\"CASH_CLEARING\","
                + "\"currency\":\"" + currency + "\","
                + "\"settlementRate\":\"" + rate + "\","
                + "\"proceedsAccountCode\":\"" + proceedsAccount + "\""
                + (postedAt != null ? ",\"postedAt\":\"" + postedAt + "\"" : "")
                + ",\"reference\":\"FX-SETTLE-1\",\"memo\":\"liquidate USD\"}";
    }

    private String revaluationBody(String currency, String rate) {
        return "{"
                + "\"ledgerAccountCode\":\"CASH_CLEARING\","
                + "\"currency\":\"" + currency + "\","
                + "\"closingRate\":\"" + rate + "\","
                + "\"reference\":\"FX-REVAL-1\",\"memo\":\"month-end revaluation\"}";
    }

    /** The CASH_CLEARING USD position's foreign balance + base carrying from the DB. */
    private long[] usdPosition() {
        return positionFor("CASH_CLEARING", "USD");
    }

    /** A ledger account's (currency) foreign balance + base carrying from the DB. */
    private long[] positionFor(String account, String currency) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount_minor ELSE 0 END),0) AS f, "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN base_amount_minor ELSE 0 END),0) "
                        + "- COALESCE(SUM(CASE WHEN direction='CREDIT' THEN base_amount_minor ELSE 0 END),0) AS b "
                        + "FROM journal_line "
                        + "WHERE tenant_id='finance' AND ledger_account_code='" + account
                        + "' AND currency='" + currency + "'");
        Map<String, Object> r = rows.get(0);
        return new long[]{((Number) r.get("f")).longValue(), ((Number) r.get("b")).longValue()};
    }

    /** Establish a USD position on CASH_CLEARING: DR USD 10000 (base 130000 @ 13.0) / CR KRW wallet. */
    private void establishUsdPosition(String token, String wallet) throws Exception {
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
    }

    private String ensureWallet(String token) throws Exception {
        String walletAcct = "acc-" + newId();
        String wallet = "CUSTOMER_WALLET:" + walletAcct;
        publish(TOPIC_COMPLETED, walletAcct,
                completedEnvelope(newId(), newId(), walletAcct, "TOPUP", 150_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerAccountJpa.findByCodeAndTenantId(wallet, "finance")).isPresent());
        return wallet;
    }

    @Test
    void fxSettlementRemovesThePositionAndRealizesGainLossThroughTheGuardedWritePath() throws Exception {
        String token = financeReadToken();

        // (0) Drive a TOPUP auto-journal so a KRW wallet liability account exists.
        String wallet = ensureWallet(token);

        // (1) Establish a USD position on CASH_CLEARING (carrying 130000 @ 13.0).
        establishUsdPosition(token, wallet);
        long[] before = usdPosition();
        assertThat(before[0]).isEqualTo(10_000L);   // USD foreign balance
        assertThat(before[1]).isEqualTo(130_000L);  // KRW carrying @ 13.0

        // (2) Settle @ 13.7 → 201 FX_GAIN realized 7000, proceeds 137000.
        String key1 = "SETTLE1-" + newId().substring(0, 8);
        HttpResponse<String> r1 = postSettlement(token, key1,
                settlementBody("USD", "13.7", PROCEEDS, null));
        assertThat(r1.statusCode()).isEqualTo(201);
        JsonNode b1 = objectMapper.readTree(r1.body());
        assertThat(b1.at("/data/settled").asBoolean()).isTrue();
        assertThat(b1.at("/data/realizedBaseMinor").asText()).isEqualTo("7000");
        assertThat(b1.at("/data/proceedsBaseMinor").asText()).isEqualTo("137000");
        assertThat(b1.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        String entryId1 = b1.at("/data/entry/entryId").asText();
        assertThat(b1.at("/data/entry/source/sourceType").asText()).isEqualTo("SETTLEMENT");
        assertThat(b1.at("/data/entry/lines")).hasSize(3);

        // The USD position on CASH_CLEARING is removed (foreign 0 + base 0).
        long[] afterSettle = usdPosition();
        assertThat(afterSettle[0]).isZero();
        assertThat(afterSettle[1]).isZero();

        // The proceeds (137000 KRW) sit in SETTLEMENT_SUSPENSE.
        long[] proceedsPos = positionFor(PROCEEDS, "KRW");
        assertThat(proceedsPos[0]).isEqualTo(137_000L);
        assertThat(proceedsPos[1]).isEqualTo(137_000L);

        // Trial balance stays base-balanced.
        JsonNode tb = objectMapper.readTree(get("/api/finance/ledger/trial-balance", token).body());
        assertThat(tb.at("/data/inBalance").asBoolean()).isTrue();
        assertThat(tb.at("/data/grandBaseDebitTotal/amount").asText())
                .isEqualTo(tb.at("/data/grandBaseCreditTotal/amount").asText());

        // Consume the GL feed and assert SETTLEMENT provenance.
        JsonNode entryEnv = awaitEnvelope(TOPIC_ENTRY_POSTED,
                env -> env.path("payload").path("entryId").asText().equals(entryId1),
                Duration.ofSeconds(30));
        JsonNode payload = entryEnv.get("payload");
        assertThat(payload.get("source").get("sourceType").asText()).isEqualTo("SETTLEMENT");
        assertThat(payload.get("source").get("sourceEventId").asText()).isEqualTo("settle:" + key1);
        assertThat(payload.get("lines")).hasSize(3);

        // (3) A below-carrying settlement → FX_LOSS. Establish a fresh position, settle @ 12.5.
        String wallet2 = ensureWallet(token);
        establishUsdPosition(token, wallet2);
        String keyLoss = "SETTLELOSS-" + newId().substring(0, 8);
        HttpResponse<String> rLoss = postSettlement(token, keyLoss,
                settlementBody("USD", "12.5", PROCEEDS, null));
        assertThat(rLoss.statusCode()).isEqualTo(201);
        JsonNode bLoss = objectMapper.readTree(rLoss.body());
        assertThat(bLoss.at("/data/realizedBaseMinor").asText()).isEqualTo("-5000");
        assertThat(bLoss.at("/data/proceedsBaseMinor").asText()).isEqualTo("125000");
        assertThat(bLoss.at("/data/outcome").asText()).isEqualTo("FX_LOSS");
        assertThat(usdPosition()[0]).isZero();   // position removed

        // (4) Revalue-then-settle realizes only the incremental delta (no double-count).
        //     Establish 130000 carrying, revalue @ 13.5 (carrying → 135000, unrealized +5000),
        //     then settle @ 13.7 → proceeds 137000, realized = 137000 - 135000 = 2000 (incremental).
        String wallet3 = ensureWallet(token);
        establishUsdPosition(token, wallet3);
        String revalKey = "REVAL-" + newId().substring(0, 8);
        HttpResponse<String> reval = postRevaluation(token, revalKey, revaluationBody("USD", "13.5"));
        assertThat(reval.statusCode()).isEqualTo(201);
        assertThat(usdPosition()[1]).isEqualTo(135_000L);  // carrying revalued to spot
        String settleKey2 = "SETTLE2-" + newId().substring(0, 8);
        HttpResponse<String> r2 = postSettlement(token, settleKey2,
                settlementBody("USD", "13.7", PROCEEDS, null));
        assertThat(r2.statusCode()).isEqualTo(201);
        JsonNode b2 = objectMapper.readTree(r2.body());
        assertThat(b2.at("/data/realizedBaseMinor").asText()).isEqualTo("2000"); // incremental, not 7000
        assertThat(b2.at("/data/outcome").asText()).isEqualTo("FX_GAIN");
        assertThat(usdPosition()[0]).isZero();   // position removed
        assertThat(usdPosition()[1]).isZero();

        // (5) Replay the FIRST key → 200, same entryId (no second post).
        HttpResponse<String> replay = postSettlement(token, key1,
                settlementBody("USD", "13.7", PROCEEDS, null));
        assertThat(replay.statusCode()).isEqualTo(200);
        JsonNode replayBody = objectMapper.readTree(replay.body());
        assertThat(replayBody.at("/data/settled").asBoolean()).isFalse();
        assertThat(replayBody.at("/data/reason").asText()).isEqualTo("REPLAY");
        assertThat(replayBody.at("/data/entry/entryId").asText()).isEqualTo(entryId1);

        // (6) settlementRate 0 → 422 SETTLEMENT_RATE_INVALID (re-establish a position first).
        String wallet4 = ensureWallet(token);
        establishUsdPosition(token, wallet4);
        HttpResponse<String> zeroRate = postSettlement(token, "ZERO-" + newId().substring(0, 8),
                settlementBody("USD", "0", PROCEEDS, null));
        assertThat(zeroRate.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(zeroRate.body()).at("/code").asText())
                .isEqualTo("SETTLEMENT_RATE_INVALID");

        // (7) Unknown proceeds account → 404 LEDGER_ACCOUNT_NOT_FOUND.
        HttpResponse<String> unknownProceeds = postSettlement(token,
                "UNK-" + newId().substring(0, 8),
                settlementBody("USD", "13.7", "NO_SUCH_ACCOUNT", null));
        assertThat(unknownProceeds.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(unknownProceeds.body()).at("/code").asText())
                .isEqualTo("LEDGER_ACCOUNT_NOT_FOUND");

        // (8) A currency with no position (EUR) → 200 settled:false (key not consumed).
        HttpResponse<String> noPos = postSettlement(token, "EUR-" + newId().substring(0, 8),
                settlementBody("EUR", "14.0", PROCEEDS, null));
        assertThat(noPos.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(noPos.body()).at("/data/settled").asBoolean()).isFalse();
        assertThat(objectMapper.readTree(noPos.body()).at("/data/reason").asText())
                .isEqualTo("NO_POSITION");

        // (9) Back-dated settlement into a CLOSED window → 422 LEDGER_PERIOD_CLOSED.
        //     MICROS truncation: MySQL DATETIME(6) round-trip (a known finance-IT trap).
        //     (the position from step 6 is still open — settleable.)
        Instant backDated = Instant.now().minus(Duration.ofDays(60)).truncatedTo(ChronoUnit.MICROS);
        Instant windowFrom = backDated.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        Instant windowTo = backDated.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        HttpResponse<String> openResp = postPeriod(token,
                "{\"from\":\"" + windowFrom + "\",\"to\":\"" + windowTo + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = objectMapper.readTree(openResp.body()).at("/data/periodId").asText();
        assertThat(closePeriod(token, periodId).statusCode()).isEqualTo(200);

        HttpResponse<String> intoClosed = postSettlement(token,
                "CLOSED-" + newId().substring(0, 8),
                settlementBody("USD", "13.7", PROCEEDS, backDated));
        assertThat(intoClosed.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(intoClosed.body()).at("/code").asText())
                .isEqualTo("LEDGER_PERIOD_CLOSED");

        // (10) Cross-tenant JWT → 403.
        HttpResponse<String> crossTenant = postSettlement(crossTenantToken(),
                "X-" + newId().substring(0, 8),
                settlementBody("USD", "13.7", PROCEEDS, null));
        assertThat(crossTenant.statusCode()).isEqualTo(403);
    }
}
