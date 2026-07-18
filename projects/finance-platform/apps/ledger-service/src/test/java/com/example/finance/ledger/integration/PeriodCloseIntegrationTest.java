package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Period-close end-to-end integration (Testcontainers MySQL + real Kafka +
 * MockWebServer JWKS). Drives the full 2nd-increment loop over the consumer + REST
 * surfaces (architecture.md § Accounting Period, TASK-FIN-BE-008 AC-1..AC-6):
 *
 * <ol>
 *   <li>publish completed events → entries post (net-zero, no period yet);</li>
 *   <li>open a window covering now, close it → CLOSED + entryCount + snapshot ==
 *       the live trial balance, in balance (AC-1/AC-4);</li>
 *   <li>a late completed event into the closed window posts NO entry (→ DLT,
 *       LEDGER_PERIOD_CLOSED) (AC-3);</li>
 *   <li>a non-overlapping window opens; an overlapping window → 422; re-close → 409
 *       (AC-2/AC-1); list/detail shapes; cross-tenant → 403 (AC-5).</li>
 * </ol>
 *
 * Ordering matters — all happy-path postings happen BEFORE the close (a window
 * covering now blocks every posting once CLOSED), so this is one ordered test.
 */
class PeriodCloseIntegrationTest extends AbstractLedgerIntegrationTest {

    @Test
    void periodCloseLocksTheWindowAndCapturesTheTrialBalanceSnapshot() throws Exception {
        String token = financeWriteToken();

        // (1) Happy-path postings BEFORE any period — net-zero (no period defined).
        String topupAcct = "acc-" + newId();
        String topupTxn = newId();
        publish(TOPIC_COMPLETED, topupAcct,
                completedEnvelope(newId(), topupTxn, topupAcct, "TOPUP", 150_000L, "KRW", null));
        String from = "acc-" + newId();
        String to = "acc-" + newId();
        String transferTxn = newId();
        publish(TOPIC_COMPLETED, from,
                completedEnvelope(newId(), transferTxn, from, "TRANSFER", 70_000L, "KRW", to));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(topupTxn, "finance"))
                    .isPresent();
            assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(transferTxn, "finance"))
                    .isPresent();
        });

        long entriesBeforeClose = journalEntryJpa.count();
        JsonNode liveTb = readJson(get("/api/finance/ledger/trial-balance", token));
        String liveDebit = liveTb.at("/data/grandDebitTotal/amount").asText();
        String liveCredit = liveTb.at("/data/grandCreditTotal/amount").asText();

        // (2) Open a window covering now, then close it.
        Instant windowFrom = Instant.now().minus(Duration.ofHours(1));
        Instant windowTo = Instant.now().plus(Duration.ofHours(1));
        HttpResponse<String> openResp = post("/api/finance/ledger/periods", token,
                windowBody(windowFrom, windowTo));
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = readJson(openResp).at("/data/periodId").asText();
        assertThat(readJson(openResp).at("/data/status").asText()).isEqualTo("OPEN");

        HttpResponse<String> closeResp =
                post("/api/finance/ledger/periods/" + periodId + "/close", token, null);
        assertThat(closeResp.statusCode()).isEqualTo(200);
        JsonNode closed = readJson(closeResp);
        assertThat(closed.at("/data/status").asText()).isEqualTo("CLOSED");
        assertThat(closed.at("/data/closedBy").asText()).isEqualTo("user-1");
        assertThat(closed.at("/data/entryCount").asLong()).isEqualTo(entriesBeforeClose);
        // (AC-4) snapshot grand totals are in balance AND equal the live trial balance.
        assertThat(closed.at("/data/snapshot/inBalance").asBoolean()).isTrue();
        assertThat(closed.at("/data/snapshot/grandDebitTotal/amount").asText())
                .isEqualTo(closed.at("/data/snapshot/grandCreditTotal/amount").asText());
        assertThat(closed.at("/data/snapshot/grandDebitTotal/amount").asText()).isEqualTo(liveDebit);
        assertThat(closed.at("/data/snapshot/grandCreditTotal/amount").asText()).isEqualTo(liveCredit);

        // (3) A late completed event whose entry would post into the closed window →
        // rejected (LEDGER_PERIOD_CLOSED) → DLT after retries → NO new journal entry.
        String lateAcct = "acc-" + newId();
        String lateTxn = newId();
        publish(TOPIC_COMPLETED, lateAcct,
                completedEnvelope(newId(), lateTxn, lateAcct, "TOPUP", 99_000L, "KRW", null));
        // Give the consumer + retry/DLT machinery time to run; the entry must not appear.
        Thread.sleep(6000);
        assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(lateTxn, "finance"))
                .isEmpty();
        assertThat(journalEntryJpa.count()).isEqualTo(entriesBeforeClose);

        // (4) A non-overlapping window opens (201); an overlapping one → 422.
        Instant nextFrom = windowTo;                          // abuts the closed window's to
        Instant nextTo = windowTo.plus(Duration.ofHours(2));
        assertThat(post("/api/finance/ledger/periods", token, windowBody(nextFrom, nextTo))
                .statusCode()).isEqualTo(201);
        // overlaps the first (closed) window
        assertThat(post("/api/finance/ledger/periods", token,
                windowBody(windowFrom.plus(Duration.ofMinutes(10)),
                        windowTo.plus(Duration.ofMinutes(10))))
                .statusCode()).isEqualTo(422);

        // re-close the already-closed period → 409.
        assertThat(post("/api/finance/ledger/periods/" + periodId + "/close", token, null)
                .statusCode()).isEqualTo(409);

        // unknown period → 404.
        assertThat(get("/api/finance/ledger/periods/" + newId(), token).statusCode())
                .isEqualTo(404);

        // (5) list returns the periods; detail returns the snapshot for the CLOSED one.
        JsonNode list = readJson(get("/api/finance/ledger/periods", token));
        assertThat(list.at("/data").size()).isGreaterThanOrEqualTo(2);
        assertThat(list.at("/meta/totalElements").asLong()).isGreaterThanOrEqualTo(2);

        JsonNode detail = readJson(get("/api/finance/ledger/periods/" + periodId, token));
        assertThat(detail.at("/data/status").asText()).isEqualTo("CLOSED");
        assertThat(detail.at("/data/snapshot/inBalance").asBoolean()).isTrue();

        // cross-tenant token → 403.
        assertThat(get("/api/finance/ledger/periods", crossTenantToken()).statusCode())
                .isEqualTo(403);
    }

    private String windowBody(Instant from, Instant to) {
        return "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}";
    }
}
