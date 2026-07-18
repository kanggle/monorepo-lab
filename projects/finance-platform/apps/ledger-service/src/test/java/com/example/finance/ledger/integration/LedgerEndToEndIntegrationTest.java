package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration (Testcontainers MySQL + real Kafka + MockWebServer
 * JWKS). Covers the architecture.md Testing Strategy scenarios: a TOPUP completed
 * event posts a balanced entry + trial balance is in balance; re-delivery of the
 * same eventId is idempotent (one entry); a TRANSFER posts two wallet lines; a
 * reversed.v1 posts a reversal and the trial balance stays balanced; a cross-tenant
 * read JWT is 403; a HOLD completed event posts no entry.
 */
class LedgerEndToEndIntegrationTest extends AbstractLedgerIntegrationTest {

    private JsonNode trialBalance(String token) throws Exception {
        HttpResponse<String> resp = get("/api/finance/ledger/trial-balance", token);
        assertThat(resp.statusCode()).isEqualTo(200);
        return objectMapper.readTree(resp.body());
    }

    @Test
    void topupPostsBalancedEntryAndTrialBalanceInBalance() throws Exception {
        String accountId = "acc-" + newId();
        String txnId = newId();
        publish(TOPIC_COMPLETED, accountId,
                completedEnvelope(newId(), txnId, accountId, "TOPUP", 150_000L, "KRW", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(txnId, "finance"))
                        .isPresent());

        var entry = journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(txnId, "finance")
                .orElseThrow();
        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.lines()).hasSize(2);

        String token = financeWriteToken();
        JsonNode tb = trialBalance(token);
        assertThat(tb.at("/data/inBalance").asBoolean()).isTrue();
        assertThat(tb.at("/data/grandDebitTotal/amount").asText())
                .isEqualTo(tb.at("/data/grandCreditTotal/amount").asText());
    }

    @Test
    void duplicateEventIdPostsOnlyOneEntry() throws Exception {
        String accountId = "acc-" + newId();
        String txnId = newId();
        String eventId = newId();
        String env = completedEnvelope(eventId, txnId, accountId, "TOPUP", 100_000L, "KRW", null);

        publish(TOPIC_COMPLETED, accountId, env);
        await().atMost(Duration.ofSeconds(30))
                .until(() -> processedEventJpa.existsById(eventId));

        // Re-deliver the SAME eventId. Dedupe must skip it → still exactly one entry.
        publish(TOPIC_COMPLETED, accountId, env);
        Thread.sleep(3000);

        long entries = journalEntryJpa.findAll().stream()
                .filter(e -> e.source().getSourceTransactionId().equals(txnId))
                .count();
        assertThat(entries).isEqualTo(1L);
    }

    @Test
    void transferPostsTwoWalletLines() throws Exception {
        String from = "acc-" + newId();
        String to = "acc-" + newId();
        String txnId = newId();
        publish(TOPIC_COMPLETED, from,
                completedEnvelope(newId(), txnId, from, "TRANSFER", 70_000L, "KRW", to));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(txnId, "finance"))
                        .isPresent());

        var entry = journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(txnId, "finance")
                .orElseThrow();
        assertThat(entry.lines()).hasSize(2);
        assertThat(entry.lines()).allSatisfy(l ->
                assertThat(l.ledgerAccountCode()).startsWith("CUSTOMER_WALLET:"));
        assertThat(entry.isBalanced()).isTrue();
    }

    @Test
    void reversedEventPostsReversalAndTrialBalanceStaysBalanced() throws Exception {
        String accountId = "acc-" + newId();
        String origTxn = newId();
        // First a CAPTURE so there is an original entry to reverse.
        publish(TOPIC_COMPLETED, accountId,
                completedEnvelope(newId(), origTxn, accountId, "CAPTURE", 40_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(origTxn, "finance"))
                        .isPresent());

        String reversalTxn = newId();
        publish(TOPIC_REVERSED, accountId,
                reversedEnvelope(newId(), reversalTxn, origTxn, accountId, 40_000L, "KRW"));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(reversalTxn, "finance"))
                        .isPresent());

        var reversal = journalEntryJpa
                .findBySourceSourceTransactionIdAndTenantId(reversalTxn, "finance").orElseThrow();
        assertThat(reversal.reversalOfEntryId()).isNotNull();
        assertThat(reversal.isBalanced()).isTrue();

        JsonNode tb = trialBalance(financeWriteToken());
        assertThat(tb.at("/data/inBalance").asBoolean()).isTrue();
    }

    @Test
    void crossTenantReadIsForbidden() throws Exception {
        HttpResponse<String> resp = get("/api/finance/ledger/trial-balance", crossTenantToken());
        assertThat(resp.statusCode()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(resp.body());
        assertThat(body.at("/code").asText()).isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    void holdCompletedEventPostsNoEntry() throws Exception {
        String accountId = "acc-" + newId();
        String txnId = newId();
        String eventId = newId();
        publish(TOPIC_COMPLETED, accountId,
                completedEnvelope(eventId, txnId, accountId, "HOLD", 20_000L, "KRW", null));

        // The event is processed (deduped) but posts no entry.
        await().atMost(Duration.ofSeconds(30))
                .until(() -> processedEventJpa.existsById(eventId));

        assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(txnId, "finance"))
                .isEmpty();
    }
}
