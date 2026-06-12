package com.example.finance.ledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reconciliation period-lock end-to-end integration (6th increment,
 * TASK-FIN-BE-012 — the authoritative gate, AC-3). Testcontainers MySQL + real
 * Kafka + MockWebServer JWKS.
 *
 * <p>Once an accounting period is CLOSED, a {@code ReconciliationDiscrepancy} whose
 * owning statement's {@code statementDate} falls in that period is frozen with the
 * books: {@code resolve} → 422 {@code RECONCILIATION_PERIOD_LOCKED}, the discrepancy
 * stays OPEN. A discrepancy whose statement date is NOT in any closed period resolves
 * normally (net-zero).
 *
 * <p>Statement dates here are in the <b>past</b> (January 2026) and the period window
 * closed covers only that past month — so this never blocks the "now" postings of a
 * sibling IT class (and {@code cleanLedgerState} truncates {@code accounting_period}
 * before each method regardless).
 */
class ReconciliationPeriodLockIntegrationTest extends AbstractLedgerIntegrationTest {

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

    private JsonNode readJson(HttpResponse<String> resp) throws Exception {
        return objectMapper.readTree(resp.body());
    }

    /** Ingest one statement on CASH_CLEARING dated {@code statementDate} with a single
     *  non-matching DEBIT line (→ at least one OPEN discrepancy). Returns the ingest
     *  {@code data} node. */
    private JsonNode ingestUnmatched(String token, LocalDate statementDate, String externalRef)
            throws Exception {
        String body = """
                { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
                  "statementDate": "%s",
                  "lines": [
                    { "externalRef": "%s",
                      "money": { "amount": "70000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "%s", "description": "deposit" } ] }
                """.formatted(statementDate, externalRef, statementDate);
        HttpResponse<String> resp = post("/api/finance/ledger/reconciliation/statements", token, body);
        assertThat(resp.statusCode()).isEqualTo(201);
        return readJson(resp).get("data");
    }

    /** The UNMATCHED_EXTERNAL discrepancy id from an ingest {@code data} node (the line
     *  with {@code externalRef} we ingested — deterministic regardless of any internal
     *  unmatched lines the matcher also records). */
    private static String externalDiscrepancyId(JsonNode ingestData, String externalRef) {
        for (JsonNode d : ingestData.get("discrepancies")) {
            if ("UNMATCHED_EXTERNAL".equals(d.path("type").asText())
                    && externalRef.equals(d.path("externalRef").asText())) {
                assertThat(d.get("status").asText()).isEqualTo("OPEN");
                return d.get("discrepancyId").asText();
            }
        }
        throw new AssertionError("no UNMATCHED_EXTERNAL discrepancy for " + externalRef);
    }

    @Test
    void resolveIntoClosedPeriodIsLockedAndStaysOpen() throws Exception {
        String token = financeReadToken();

        // (1) A clearing-account internal line so CASH_CLEARING exists / matching runs
        //     against a real account (drive a TOPUP via the event path).
        String topupAcct = "acc-" + newId();
        String topupTxn = newId();
        publish(TOPIC_COMPLETED, topupAcct,
                completedEnvelope(newId(), topupTxn, topupAcct, "TOPUP", 150_000L, "KRW", null));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(journalEntryJpa.findBySourceSourceTransactionIdAndTenantId(topupTxn, "finance"))
                        .isPresent());

        // (2) Ingest a statement dated 2026-01-31 → an OPEN UNMATCHED_EXTERNAL discrepancy
        //     (the unmatched TOPUP DR CASH_CLEARING line also yields an UNMATCHED_INTERNAL —
        //     both share this statement; we lock against the external one deterministically).
        LocalDate lockedDate = LocalDate.parse("2026-01-31");
        JsonNode lockedIngest = ingestUnmatched(token, lockedDate, "BANKTXN-LOCK");
        String lockedDiscrepancyId = externalDiscrepancyId(lockedIngest, "BANKTXN-LOCK");

        // (3) Open + close a period [Jan 1 00:00Z, Feb 1 00:00Z) — covers 2026-01-31's
        //     start-of-day UTC instant. A past window: it does NOT block the "now" TOPUP above.
        Instant windowFrom = LocalDate.parse("2026-01-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowTo = LocalDate.parse("2026-02-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        HttpResponse<String> openResp = post("/api/finance/ledger/periods", token,
                "{\"from\":\"" + windowFrom + "\",\"to\":\"" + windowTo + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = readJson(openResp).at("/data/periodId").asText();
        HttpResponse<String> closeResp =
                post("/api/finance/ledger/periods/" + periodId + "/close", token, null);
        assertThat(closeResp.statusCode()).isEqualTo(200);
        assertThat(readJson(closeResp).at("/data/status").asText()).isEqualTo("CLOSED");

        // (AC-1) Resolve the locked discrepancy → 422 RECONCILIATION_PERIOD_LOCKED.
        HttpResponse<String> lockedResolve = post(
                "/api/finance/ledger/reconciliation/discrepancies/" + lockedDiscrepancyId + "/resolve",
                token, "{\"resolutionType\":\"WRITTEN_OFF\",\"note\":\"too late\"}");
        assertThat(lockedResolve.statusCode()).isEqualTo(422);
        assertThat(readJson(lockedResolve).get("code").asText()).isEqualTo("RECONCILIATION_PERIOD_LOCKED");

        // The discrepancy is still OPEN (no mutation, no auto-close) — re-read it.
        JsonNode stillOpen = readJson(get(
                "/api/finance/ledger/reconciliation/discrepancies/" + lockedDiscrepancyId, token))
                .get("data");
        assertThat(stillOpen.get("status").asText()).isEqualTo("OPEN");
        assertThat(stillOpen.has("resolution")).isFalse();

        // (AC-2 control / net-zero) A discrepancy whose statement date (2026-03-15) is in
        // NO closed period → resolve 200 RESOLVED.
        JsonNode openIngest = ingestUnmatched(token, LocalDate.parse("2026-03-15"), "BANKTXN-OPEN");
        String openDiscrepancyId = externalDiscrepancyId(openIngest, "BANKTXN-OPEN");
        HttpResponse<String> openResolve = post(
                "/api/finance/ledger/reconciliation/discrepancies/" + openDiscrepancyId + "/resolve",
                token, "{\"resolutionType\":\"ACCEPTED\"}");
        assertThat(openResolve.statusCode()).isEqualTo(200);
        JsonNode resolved = readJson(openResolve).get("data");
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolution").get("resolutionType").asText()).isEqualTo("ACCEPTED");

        // The locked one remains OPEN in the queue; the control is gone from OPEN.
        JsonNode openQueue = readJson(get(
                "/api/finance/ledger/reconciliation/discrepancies?status=OPEN", token));
        boolean lockedStillQueued = false;
        for (JsonNode d : openQueue.get("data")) {
            if (d.get("discrepancyId").asText().equals(lockedDiscrepancyId)) {
                lockedStillQueued = true;
            }
            assertThat(d.get("discrepancyId").asText()).isNotEqualTo(openDiscrepancyId);
        }
        assertThat(lockedStillQueued).isTrue();

        // (AC-3) Cross-tenant JWT → 403.
        assertThat(post(
                "/api/finance/ledger/reconciliation/discrepancies/" + lockedDiscrepancyId + "/resolve",
                crossTenantToken(), "{\"resolutionType\":\"ACCEPTED\"}").statusCode())
                .isEqualTo(403);
    }
}
