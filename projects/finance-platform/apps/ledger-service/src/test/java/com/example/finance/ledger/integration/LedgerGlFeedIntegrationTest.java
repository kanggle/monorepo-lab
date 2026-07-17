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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * GL/AP-feed end-to-end integration (3rd increment, TASK-FIN-BE-009 — the
 * authoritative round-trip gate, AC-1/AC-2/AC-3/AC-5/AC-6). Testcontainers MySQL
 * + real Kafka + MockWebServer JWKS.
 *
 * <ol>
 *   <li>publish completed events (TOPUP + TRANSFER) → entries post → a
 *       {@code ledger_outbox} row appears → the relay publishes →
 *       <b>consume {@code finance.ledger.entry.posted.v1}</b> and assert the
 *       canonical envelope (eventType, tenantId, source, aggregateType) + payload
 *       (entryId, balanced lines, money minor-string) (AC-1/AC-5);</li>
 *   <li>open a window covering now + close it → <b>consume
 *       {@code finance.ledger.period.closed.v1}</b> and assert
 *       {@code {periodId, from, to, closedAt, entryCount}} (AC-2);</li>
 *   <li>a completed event whose entry would post into the CLOSED window → assert
 *       NO entry posts (and no {@code entry.posted} for it) — atomic rollback
 *       (AC-3).</li>
 * </ol>
 *
 * Ordering matters — all happy-path postings happen BEFORE the close (a window
 * covering now blocks every later posting once CLOSED), so this is one ordered test.
 */
class LedgerGlFeedIntegrationTest extends AbstractLedgerIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            b.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.POST(HttpRequest.BodyPublishers.noBody());
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void glFeedRoundTripForEntryPostedAndPeriodClosed() throws Exception {
        String token = financeWriteToken();

        // (1) A TOPUP completed event → balanced entry posts → outbox row → relay
        //     publishes finance.ledger.entry.posted.v1.
        String topupAcct = "acc-" + newId();
        String topupTxn = newId();
        publish(TOPIC_COMPLETED, topupAcct,
                completedEnvelope(newId(), topupTxn, topupAcct, "TOPUP", 150_000L, "KRW", null));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(topupTxn, "finance"))
                        .isPresent());

        String topupEntryId = journalEntryJpa
                .findBySourceSourceTransactionIdAndTenantId(topupTxn, "finance")
                .orElseThrow().entryId();

        // An outbox row for the posted entry exists (AC-1 — appended in the post Tx).
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(ledgerOutboxJpa.findAll().stream()
                        .anyMatch(r -> "finance.ledger.entry.posted".equals(r.getEventType())
                                && topupEntryId.equals(r.getAggregateId()))).isTrue());

        // Consume the published GL feed and assert the envelope + payload.
        JsonNode entryEnv = awaitEnvelope(TOPIC_ENTRY_POSTED,
                env -> env.path("payload").path("entryId").asText().equals(topupEntryId),
                Duration.ofSeconds(30));
        assertThat(entryEnv.get("eventType").asText()).isEqualTo("finance.ledger.entry.posted");
        assertThat(entryEnv.get("tenantId").asText()).isEqualTo("finance");
        assertThat(entryEnv.get("source").asText()).isEqualTo("finance-platform-ledger-service");
        assertThat(entryEnv.get("aggregateType").asText()).isEqualTo("JournalEntry");
        assertThat(entryEnv.get("aggregateId").asText()).isEqualTo(topupEntryId);
        assertThat(entryEnv.get("eventId").asText()).isNotBlank();

        JsonNode topupPayload = entryEnv.get("payload");
        assertThat(topupPayload.get("entryId").asText()).isEqualTo(topupEntryId);
        assertThat(topupPayload.get("postedAt").asText()).isNotBlank();
        JsonNode lines = topupPayload.get("lines");
        assertThat(lines).hasSize(2);
        // Balanced TOPUP: DR CASH_CLEARING / CR CUSTOMER_WALLET, both 150000 KRW (F5 string).
        long debitSum = 0;
        long creditSum = 0;
        for (JsonNode line : lines) {
            assertThat(line.get("money").get("amount").isTextual()).isTrue();
            assertThat(line.get("money").get("currency").asText()).isEqualTo("KRW");
            long amt = Long.parseLong(line.get("money").get("amount").asText());
            if ("DEBIT".equals(line.get("direction").asText())) {
                debitSum += amt;
            } else {
                creditSum += amt;
            }
        }
        assertThat(debitSum).isEqualTo(creditSum).isEqualTo(150_000L);
        assertThat(topupPayload.get("source").get("sourceTransactionId").asText()).isEqualTo(topupTxn);

        // A TRANSFER also emits an entry.posted (two wallet lines).
        String from = "acc-" + newId();
        String to = "acc-" + newId();
        String transferTxn = newId();
        publish(TOPIC_COMPLETED, from,
                completedEnvelope(newId(), transferTxn, from, "TRANSFER", 70_000L, "KRW", to));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(transferTxn, "finance"))
                        .isPresent());
        String transferEntryId = journalEntryJpa
                .findBySourceSourceTransactionIdAndTenantId(transferTxn, "finance")
                .orElseThrow().entryId();
        JsonNode transferEnv = awaitEnvelope(TOPIC_ENTRY_POSTED,
                env -> env.path("payload").path("entryId").asText().equals(transferEntryId),
                Duration.ofSeconds(30));
        assertThat(transferEnv.get("payload").get("lines")).hasSize(2);

        long entriesBeforeClose = journalEntryJpa.count();

        // (2) Open a window covering now, close it → consume period.closed.v1.
        // Truncate to MICROS so the POST body, MySQL DATETIME(6) round-trip, and the
        // emitted payload all agree (MySQL is microsecond-precision — without this the
        // nanosecond `Instant.now()` differs from the persisted/emitted value).
        Instant windowFrom = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        Instant windowTo = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        HttpResponse<String> openResp = post("/api/finance/ledger/periods", token,
                "{\"from\":\"" + windowFrom + "\",\"to\":\"" + windowTo + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = objectMapper.readTree(openResp.body()).at("/data/periodId").asText();

        HttpResponse<String> closeResp =
                post("/api/finance/ledger/periods/" + periodId + "/close", token, null);
        assertThat(closeResp.statusCode()).isEqualTo(200);

        JsonNode periodEnv = awaitEnvelope(TOPIC_PERIOD_CLOSED,
                env -> env.path("payload").path("periodId").asText().equals(periodId),
                Duration.ofSeconds(30));
        assertThat(periodEnv.get("eventType").asText()).isEqualTo("finance.ledger.period.closed");
        assertThat(periodEnv.get("aggregateType").asText()).isEqualTo("AccountingPeriod");
        assertThat(periodEnv.get("source").asText()).isEqualTo("finance-platform-ledger-service");
        JsonNode periodPayload = periodEnv.get("payload");
        assertThat(periodPayload.get("periodId").asText()).isEqualTo(periodId);
        assertThat(periodPayload.get("from").asText()).isEqualTo(windowFrom.toString());
        assertThat(periodPayload.get("to").asText()).isEqualTo(windowTo.toString());
        assertThat(periodPayload.get("closedAt").asText()).isNotBlank();
        assertThat(periodPayload.get("entryCount").asLong()).isEqualTo(entriesBeforeClose);

        // (3) A late completed event whose entry would post into the CLOSED window →
        //     rejected (LEDGER_PERIOD_CLOSED) → DLT → NO entry AND no entry.posted row.
        long outboxRowsBeforeLate = ledgerOutboxJpa.count();
        String lateAcct = "acc-" + newId();
        String lateTxn = newId();
        publish(TOPIC_COMPLETED, lateAcct,
                completedEnvelope(newId(), lateTxn, lateAcct, "TOPUP", 99_000L, "KRW", null));
        // Give the consumer + retry/DLT machinery + relay time; nothing must appear.
        Thread.sleep(6000);
        assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(lateTxn, "finance"))
                .isEmpty();
        assertThat(journalEntryJpa.count()).isEqualTo(entriesBeforeClose);
        // No NEW outbox row for the rejected posting (atomic rollback, AC-3).
        assertThat(ledgerOutboxJpa.count()).isEqualTo(outboxRowsBeforeLate);
    }
}
