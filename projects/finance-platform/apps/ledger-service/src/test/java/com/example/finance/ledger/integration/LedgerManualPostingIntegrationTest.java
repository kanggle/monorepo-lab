package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Manual journal-posting end-to-end integration (5th increment, TASK-FIN-BE-011 —
 * the authoritative round-trip gate, AC-6). Testcontainers MySQL + real Kafka +
 * MockWebServer JWKS.
 *
 * <ol>
 *   <li>drive a TOPUP auto-journal so the {@code CUSTOMER_WALLET:acc-X} liability
 *       account exists (the manual path does NOT lazily mint — AC-3);</li>
 *   <li>{@code POST /api/finance/ledger/entries} balanced (DR CASH_CLEARING / CR
 *       CUSTOMER_WALLET) → 201; assert entry + lines persist, trial balance == 0,
 *       and consume {@code finance.ledger.entry.posted.v1} with
 *       {@code source.sourceType == "MANUAL"} (AC-1/AC-6);</li>
 *   <li>replay the SAME key → 200, same entryId, exactly ONE entry for the key
 *       (AC-4);</li>
 *   <li>unbalanced body → 422 LEDGER_ENTRY_UNBALANCED (AC-2);</li>
 *   <li>back-dated postedAt into a CLOSED window → 422 LEDGER_PERIOD_CLOSED (AC-5);</li>
 *   <li>cross-tenant JWT → 403 (AC-5).</li>
 * </ol>
 *
 * Ordering matters — the closed-window scenario is last (a window covering a chosen
 * instant blocks postings into it), so this is one ordered test.
 */
class LedgerManualPostingIntegrationTest extends AbstractLedgerIntegrationTest {

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

    private String balancedBody(Instant postedAt, String wallet, long amount) {
        return "{"
                + (postedAt != null ? "\"postedAt\":\"" + postedAt + "\"," : "")
                + "\"reference\":\"ADJ-CORR-1\","
                + "\"memo\":\"manual correction\","
                + "\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"" + amount + "\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + wallet + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"" + amount + "\",\"currency\":\"KRW\"}}"
                + "]}";
    }

    @Test
    void manualPostingFunnelsThroughTheGuardedWritePathAndIsIdempotent() throws Exception {
        String token = financeWriteToken();

        // (1) Drive a TOPUP auto-journal so the wallet liability account exists (the
        //     manual path rejects an unknown account — no lazy mint).
        String walletAcct = "acc-" + newId();
        String wallet = "CUSTOMER_WALLET:" + walletAcct;
        String topupTxn = newId();
        publish(TOPIC_COMPLETED, walletAcct,
                completedEnvelope(newId(), topupTxn, walletAcct, "TOPUP", 150_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerAccountJpa.findByCodeAndTenantId(wallet, "finance")).isPresent());

        long entriesBeforeManual = journalEntryJpa.count();

        // (2) POST a balanced manual entry → 201, MANUAL provenance, persisted, TB == 0.
        String key = "MANUAL-KEY-" + newId().substring(0, 8);
        HttpResponse<String> created = postEntry(token, key, balancedBody(null, wallet, 50_000L));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode createdBody = objectMapper.readTree(created.body());
        String entryId = createdBody.at("/data/entryId").asText();
        assertThat(entryId).isNotBlank();
        assertThat(createdBody.at("/data/source/sourceType").asText()).isEqualTo("MANUAL");
        assertThat(createdBody.at("/data/source/sourceEventId").asText()).isEqualTo("manual:" + key);
        assertThat(createdBody.at("/data/balanced").asBoolean()).isTrue();

        assertThat(journalEntryJpa.findByEntryIdAndTenantId(entryId, "finance")).isPresent();
        assertThat(journalEntryJpa.count()).isEqualTo(entriesBeforeManual + 1);

        // Trial balance stays balanced (== 0).
        JsonNode tb = objectMapper.readTree(get("/api/finance/ledger/trial-balance", token).body());
        assertThat(tb.at("/data/inBalance").asBoolean()).isTrue();
        assertThat(tb.at("/data/grandDebitTotal/amount").asText())
                .isEqualTo(tb.at("/data/grandCreditTotal/amount").asText());

        // Consume the GL feed and assert MANUAL provenance.
        JsonNode entryEnv = awaitEnvelope(TOPIC_ENTRY_POSTED,
                env -> env.path("payload").path("entryId").asText().equals(entryId),
                Duration.ofSeconds(30));
        assertThat(entryEnv.get("source").asText()).isEqualTo("finance-platform-ledger-service");
        JsonNode payload = entryEnv.get("payload");
        assertThat(payload.get("source").get("sourceType").asText()).isEqualTo("MANUAL");
        assertThat(payload.get("source").get("sourceEventId").asText()).isEqualTo("manual:" + key);
        assertThat(payload.get("lines")).hasSize(2);

        // (3) Replay the SAME key → 200, same entryId, exactly ONE entry for the key.
        HttpResponse<String> replay = postEntry(token, key, balancedBody(null, wallet, 50_000L));
        assertThat(replay.statusCode()).isEqualTo(200);
        JsonNode replayBody = objectMapper.readTree(replay.body());
        assertThat(replayBody.at("/data/entryId").asText()).isEqualTo(entryId);
        assertThat(journalEntryJpa.findBySourceSourceEventIdAndTenantId("manual:" + key, "finance"))
                .isPresent();
        assertThat(journalEntryJpa.count()).isEqualTo(entriesBeforeManual + 1); // no second entry

        // (4) Unbalanced body → 422 LEDGER_ENTRY_UNBALANCED, nothing persisted.
        String unbalanced = "{\"lines\":["
                + "{\"ledgerAccountCode\":\"CASH_CLEARING\",\"direction\":\"DEBIT\","
                + "\"money\":{\"amount\":\"50000\",\"currency\":\"KRW\"}},"
                + "{\"ledgerAccountCode\":\"" + wallet + "\",\"direction\":\"CREDIT\","
                + "\"money\":{\"amount\":\"40000\",\"currency\":\"KRW\"}}"
                + "]}";
        HttpResponse<String> unbalancedResp = postEntry(token, "UNBAL-" + newId().substring(0, 8), unbalanced);
        assertThat(unbalancedResp.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(unbalancedResp.body()).at("/code").asText())
                .isEqualTo("LEDGER_ENTRY_UNBALANCED");

        // Missing Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED.
        HttpResponse<String> noKey = postEntry(token, null, balancedBody(null, wallet, 10_000L));
        assertThat(noKey.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(noKey.body()).at("/code").asText())
                .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        // (5) Back-dated postedAt into a CLOSED window → 422 LEDGER_PERIOD_CLOSED.
        // MICROS truncation: MySQL DATETIME(6) round-trip (a known finance-IT trap).
        Instant backDated = Instant.now().minus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS);
        Instant windowFrom = backDated.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        Instant windowTo = backDated.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        HttpResponse<String> openResp = postPeriod(token,
                "{\"from\":\"" + windowFrom + "\",\"to\":\"" + windowTo + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = objectMapper.readTree(openResp.body()).at("/data/periodId").asText();
        assertThat(closePeriod(token, periodId).statusCode()).isEqualTo(200);

        HttpResponse<String> intoClosed =
                postEntry(token, "CLOSED-" + newId().substring(0, 8),
                        balancedBody(backDated, wallet, 20_000L));
        assertThat(intoClosed.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(intoClosed.body()).at("/code").asText())
                .isEqualTo("LEDGER_PERIOD_CLOSED");

        // (6) Cross-tenant JWT → 403.
        HttpResponse<String> crossTenant =
                postEntry(crossTenantToken(), "X-" + newId().substring(0, 8),
                        balancedBody(null, wallet, 10_000L));
        assertThat(crossTenant.statusCode()).isEqualTo(403);
    }
}
