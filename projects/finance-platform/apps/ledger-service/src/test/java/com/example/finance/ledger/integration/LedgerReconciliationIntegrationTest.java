package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reconciliation end-to-end integration (4th increment, TASK-FIN-BE-010 — the
 * authoritative round-trip gate, AC-1..AC-6). Testcontainers MySQL + real Kafka +
 * MockWebServer JWKS.
 *
 * <p>Flow (one ordered test):
 * <ol>
 *   <li>publish two TOPUP completed events (150000 + 99000) → two DR CASH_CLEARING
 *       internal lines (a TOPUP posts DR CASH_CLEARING);</li>
 *   <li>ingest an external statement on CASH_CLEARING with TWO DEBIT lines: one
 *       matching 150000 (→ a match) + one non-matching 70000 (→ UNMATCHED_EXTERNAL);
 *       the 99000 internal line is unmatched (→ UNMATCHED_INTERNAL);</li>
 *   <li>assert matchedCount=1, discrepancyCount=2, both discrepancies OPEN (NOT
 *       auto-closed, F8);</li>
 *   <li>consume {@code finance.ledger.reconciliation.completed.v1} (matchedCount=1,
 *       discrepancyCount=2) + {@code .discrepancy.detected.v1} (≥1, assert type +
 *       amounts);</li>
 *   <li>GET the discrepancy queue (?status=OPEN) → 2; resolve one (WRITTEN_OFF) →
 *       RESOLVED; re-resolve → 409; ingest on a non-clearing account → 422.</li>
 * </ol>
 */
class LedgerReconciliationIntegrationTest extends AbstractLedgerIntegrationTest {

    protected HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ingestMatchesDiscrepanciesOpenEmitsEventsAndResolves() throws Exception {
        String token = financeWriteToken();

        // (1) Two TOPUPs → two DR CASH_CLEARING internal lines (150000 + 99000).
        String acct1 = "acc-" + newId();
        String topupTxn1 = newId();
        publish(TOPIC_COMPLETED, acct1,
                completedEnvelope(newId(), topupTxn1, acct1, "TOPUP", 150_000L, "KRW", null));
        String acct2 = "acc-" + newId();
        String topupTxn2 = newId();
        publish(TOPIC_COMPLETED, acct2,
                completedEnvelope(newId(), topupTxn2, acct2, "TOPUP", 99_000L, "KRW", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(topupTxn1, "finance"))
                    .isPresent();
            assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(topupTxn2, "finance"))
                    .isPresent();
        });
        String matchedEntryId = journalEntryJpa
                .findBySourceSourceTransactionIdAndTenantId(topupTxn1, "finance")
                .orElseThrow().entryId();

        // (2) Ingest a statement on CASH_CLEARING: a 150000 DEBIT line matches the
        //     first TOPUP's DR; a 70000 DEBIT line matches nothing; the 99000
        //     internal line is left unmatched.
        String ingestBody = """
                { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "BANKTXN-001",
                      "money": { "amount": "150000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-15", "description": "deposit" },
                    { "externalRef": "BANKTXN-002",
                      "money": { "amount": "70000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-16" } ] }
                """;
        HttpResponse<String> ingestResp =
                post("/api/finance/ledger/reconciliation/statements", token, ingestBody);
        assertThat(ingestResp.statusCode()).isEqualTo(201);
        JsonNode ingestData = objectMapper.readTree(ingestResp.body()).get("data");

        String statementId = ingestData.get("statementId").asText();
        assertThat(ingestData.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(ingestData.get("discrepancyCount").asInt()).isEqualTo(2);
        assertThat(ingestData.get("matches").get(0).get("journalEntryId").asText())
                .isEqualTo(matchedEntryId);
        assertThat(ingestData.get("matches").get(0).get("money").get("amount").asText())
                .isEqualTo("150000");

        // (3) AC-2 / F8 — both discrepancies are OPEN (assert NOT auto-closed).
        for (JsonNode d : ingestData.get("discrepancies")) {
            assertThat(d.get("status").asText()).isEqualTo("OPEN");
            assertThat(d.has("resolution")).isFalse();
        }
        // The two discrepancy types: one external (70000 unmatched), one internal (99000 unmatched).
        assertThat(ingestData.get("discrepancies")).hasSize(2);

        // (4) Consume the emitted reconciliation feed (AC-4).
        JsonNode completedEnv = awaitEnvelope(TOPIC_RECONCILIATION_COMPLETED,
                env -> env.path("payload").path("statementId").asText().equals(statementId),
                Duration.ofSeconds(30));
        assertThat(completedEnv.get("eventType").asText())
                .isEqualTo("finance.ledger.reconciliation.completed");
        assertThat(completedEnv.get("aggregateType").asText()).isEqualTo("ReconciliationStatement");
        assertThat(completedEnv.get("source").asText()).isEqualTo("finance-platform-ledger-service");
        JsonNode completedPayload = completedEnv.get("payload");
        assertThat(completedPayload.get("ledgerAccountCode").asText()).isEqualTo("CASH_CLEARING");
        assertThat(completedPayload.get("source").asText()).isEqualTo("BANK");
        assertThat(completedPayload.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(completedPayload.get("discrepancyCount").asInt()).isEqualTo(2);

        JsonNode detectedEnv = awaitEnvelope(TOPIC_DISCREPANCY_DETECTED,
                env -> env.path("payload").path("ledgerAccountCode").asText().equals("CASH_CLEARING")
                        && (env.path("payload").path("type").asText().equals("UNMATCHED_EXTERNAL")
                        || env.path("payload").path("type").asText().equals("UNMATCHED_INTERNAL")),
                Duration.ofSeconds(30));
        assertThat(detectedEnv.get("aggregateType").asText()).isEqualTo("ReconciliationDiscrepancy");
        JsonNode detectedPayload = detectedEnv.get("payload");
        // F5 — money minor amounts are STRINGs, never floats.
        assertThat(detectedPayload.get("expectedMinor").isTextual()).isTrue();
        assertThat(detectedPayload.get("actualMinor").isTextual()).isTrue();
        assertThat(detectedPayload.get("currency").asText()).isEqualTo("KRW");

        // (5) AC-5 — the OPEN review queue returns the 2 discrepancies.
        HttpResponse<String> queueResp =
                get("/api/finance/ledger/reconciliation/discrepancies?status=OPEN", token);
        assertThat(queueResp.statusCode()).isEqualTo(200);
        JsonNode queue = objectMapper.readTree(queueResp.body());
        assertThat(queue.get("data")).hasSize(2);
        assertThat(queue.at("/meta/totalElements").asInt()).isEqualTo(2);

        // (AC-3) Resolve one discrepancy (WRITTEN_OFF) → RESOLVED.
        String discrepancyId = queue.get("data").get(0).get("discrepancyId").asText();
        HttpResponse<String> resolveResp = post(
                "/api/finance/ledger/reconciliation/discrepancies/" + discrepancyId + "/resolve",
                token, "{\"resolutionType\":\"WRITTEN_OFF\",\"note\":\"bank fee, below threshold\"}");
        assertThat(resolveResp.statusCode()).isEqualTo(200);
        JsonNode resolved = objectMapper.readTree(resolveResp.body()).get("data");
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolution").get("resolutionType").asText()).isEqualTo("WRITTEN_OFF");
        assertThat(resolved.get("resolution").get("resolvedBy").asText()).isEqualTo("user-1");

        // (AC-3) Re-resolve the same discrepancy → 409 RECONCILIATION_ALREADY_RESOLVED.
        HttpResponse<String> reResolveResp = post(
                "/api/finance/ledger/reconciliation/discrepancies/" + discrepancyId + "/resolve",
                token, "{\"resolutionType\":\"ACCEPTED\"}");
        assertThat(reResolveResp.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(reResolveResp.body()).get("code").asText())
                .isEqualTo("RECONCILIATION_ALREADY_RESOLVED");

        // After resolving one, the OPEN queue has 1 left (the other stays OPEN — no auto-close).
        JsonNode queueAfter = objectMapper.readTree(
                get("/api/finance/ledger/reconciliation/discrepancies?status=OPEN", token).body());
        assertThat(queueAfter.get("data")).hasSize(1);

        // (AC-5) The statement detail read returns the match + both discrepancies.
        JsonNode detail = objectMapper.readTree(
                get("/api/finance/ledger/reconciliation/statements/" + statementId, token).body())
                .get("data");
        assertThat(detail.get("matchedCount").asInt()).isEqualTo(1);
        assertThat(detail.get("discrepancies")).hasSize(2);

        // (AC-5) Ingest on a non-clearing account → 422 RECONCILIATION_ACCOUNT_INVALID.
        String walletBody = """
                { "ledgerAccountCode": "CUSTOMER_WALLET:acc-x", "source": "BANK",
                  "statementDate": "2026-01-31",
                  "lines": [
                    { "externalRef": "X1", "money": { "amount": "1000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "2026-01-15" } ] }
                """;
        HttpResponse<String> walletResp =
                post("/api/finance/ledger/reconciliation/statements", token, walletBody);
        assertThat(walletResp.statusCode()).isEqualTo(422);
        assertThat(objectMapper.readTree(walletResp.body()).get("code").asText())
                .isEqualTo("RECONCILIATION_ACCOUNT_INVALID");
    }
}
