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

/**
 * Ingest-time period-lock integration (7th increment, TASK-FIN-BE-013 — the
 * authoritative gate, AC-1..3). Testcontainers MySQL + real Kafka + MockWebServer
 * JWKS.
 *
 * <p>Once an accounting period is CLOSED, ingesting an external statement whose
 * {@code statementDate} falls in that period is rejected with 422
 * {@code RECONCILIATION_PERIOD_LOCKED} and nothing is persisted (no statement row,
 * no discrepancy, no outbox event). A statement whose date is NOT in any closed
 * period ingests normally (net-zero — 201, matches + OPEN discrepancies as in
 * FIN-BE-010).
 *
 * <p>Statement dates used are in the past (January / March 2026). The period window
 * closed here covers only January — it does NOT block "now" postings in sibling IT
 * classes. {@code cleanLedgerState} truncates {@code accounting_period} before each
 * method.
 */
class IngestStatementPeriodLockIntegrationTest extends AbstractLedgerIntegrationTest {

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

    /**
     * Open + close an accounting period covering {@code [from, to)} for the finance
     * tenant. Returns the periodId for optional further assertions.
     */
    private String openAndClosePeriod(String token, Instant from, Instant to) throws Exception {
        HttpResponse<String> openResp = post("/api/finance/ledger/periods", token,
                "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        String periodId = readJson(openResp).at("/data/periodId").asText();

        HttpResponse<String> closeResp =
                post("/api/finance/ledger/periods/" + periodId + "/close", token, null);
        assertThat(closeResp.statusCode()).isEqualTo(200);
        assertThat(readJson(closeResp).at("/data/status").asText()).isEqualTo("CLOSED");
        return periodId;
    }

    /**
     * Attempt to ingest a CASH_CLEARING statement dated {@code statementDate} with a
     * single unmatched DEBIT line (externalRef unique per call). Returns the raw
     * HTTP response.
     */
    private HttpResponse<String> tryIngest(String token, LocalDate statementDate,
                                           String externalRef) throws Exception {
        String body = """
                { "ledgerAccountCode": "CASH_CLEARING", "source": "BANK",
                  "statementDate": "%s",
                  "lines": [
                    { "externalRef": "%s",
                      "money": { "amount": "80000", "currency": "KRW" },
                      "direction": "DEBIT", "valueDate": "%s", "description": "test" } ] }
                """.formatted(statementDate, externalRef, statementDate);
        return post("/api/finance/ledger/reconciliation/statements", token, body);
    }

    @Test
    void ingestIntoClosedPeriodIsRejectedAndNothingPersisted() throws Exception {
        String token = financeWriteToken();

        // (1) Open + close an accounting period covering January 2026
        //     [Jan 1 00:00Z, Feb 1 00:00Z) — covers 2026-01-31 start-of-day UTC.
        Instant windowFrom = LocalDate.parse("2026-01-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowTo   = LocalDate.parse("2026-02-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        openAndClosePeriod(token, windowFrom, windowTo);

        // (AC-1) Ingest with statementDate=2026-01-31 (inside the closed period) → 422
        //        RECONCILIATION_PERIOD_LOCKED.
        LocalDate lockedDate = LocalDate.parse("2026-01-31");
        HttpResponse<String> lockedResp = tryIngest(token, lockedDate, "BANKTXN-INGEST-LOCK");
        assertThat(lockedResp.statusCode()).isEqualTo(422);
        JsonNode lockedBody = readJson(lockedResp);
        assertThat(lockedBody.get("code").asText()).isEqualTo("RECONCILIATION_PERIOD_LOCKED");

        // (AC-1) Assert no statement row was persisted — the statements list should be
        //        empty (no statement created under the tenant for the locked account).
        //        We also verify the outbox has no reconciliation-completed row for this
        //        ingest (guard ran before any write → no outbox entry was appended).
        int statementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_statement WHERE tenant_id = 'finance'",
                Integer.class);
        assertThat(statementCount).as("no statement row should be persisted on locked ingest")
                .isZero();

        // No discrepancy rows either.
        int discrepancyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_discrepancy WHERE tenant_id = 'finance'",
                Integer.class);
        assertThat(discrepancyCount).as("no discrepancy row should be persisted on locked ingest")
                .isZero();

        // No outbox row for reconciliation.completed (the guard threw before publish).
        // ledger_outbox stores the base event_type ('...completed'); the '.v1' topic
        // suffix is appended by the relay's TopicResolver at publish time, not stored.
        int outboxReconRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_outbox WHERE event_type = 'finance.ledger.reconciliation.completed'",
                Integer.class);
        assertThat(outboxReconRows)
                .as("no reconciliation.completed outbox row should be written on locked ingest")
                .isZero();

        // (AC-2 net-zero control) Ingest with statementDate=2026-03-15 (outside any
        //        closed period) → 201; statement is persisted with an OPEN discrepancy
        //        (the line is unmatched — no internal lines in the cleared account).
        LocalDate openDate = LocalDate.parse("2026-03-15");
        HttpResponse<String> openResp = tryIngest(token, openDate, "BANKTXN-INGEST-OPEN");
        assertThat(openResp.statusCode()).isEqualTo(201);
        JsonNode openData = readJson(openResp).get("data");
        assertThat(openData.get("matchedCount").asInt()).isZero();
        assertThat(openData.get("discrepancyCount").asInt()).isEqualTo(1);
        for (JsonNode d : openData.get("discrepancies")) {
            assertThat(d.get("status").asText()).isEqualTo("OPEN");
        }

        // One statement row now exists (from the net-zero ingest).
        int statementCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_statement WHERE tenant_id = 'finance'",
                Integer.class);
        assertThat(statementCountAfter).isEqualTo(1);

        // (AC-3) Cross-tenant JWT on the ingest endpoint → 403 (tenant gate).
        HttpResponse<String> crossTenant = tryIngest(crossTenantToken(), openDate, "BANKTXN-X");
        assertThat(crossTenant.statusCode()).isEqualTo(403);

        // (AC-1 boundary) statementDate exactly on the period's from-day (Jan 1, 2026)
        //        maps to Jan 1 00:00Z (from-inclusive) — also inside the closed period → 422.
        LocalDate boundaryDate = LocalDate.parse("2026-01-01");
        HttpResponse<String> boundaryResp = tryIngest(token, boundaryDate, "BANKTXN-BOUNDARY");
        assertThat(boundaryResp.statusCode())
                .as("boundary date on period from-day (from-inclusive) should also be locked")
                .isEqualTo(422);
        assertThat(readJson(boundaryResp).get("code").asText()).isEqualTo("RECONCILIATION_PERIOD_LOCKED");
    }

    @Test
    void ingestWithNoClosedPeriodSucceeds() throws Exception {
        // No period created at all → findCovering empty → net-zero, ingest proceeds.
        String token = financeWriteToken();

        HttpResponse<String> resp = tryIngest(token, LocalDate.parse("2026-06-15"), "BANKTXN-FREE");
        assertThat(resp.statusCode()).isEqualTo(201);
        JsonNode data = readJson(resp).get("data");
        assertThat(data.get("matchedCount").asInt()).isZero();
        assertThat(data.get("discrepancyCount").asInt()).isEqualTo(1);

        // Statement persisted.
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_statement WHERE tenant_id = 'finance'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void ingestWithOpenPeriodCoveringDateSucceeds() throws Exception {
        // An OPEN period covering the statementDate does NOT lock (only CLOSED locks).
        String token = financeWriteToken();
        LocalDate stmtDate = LocalDate.parse("2026-04-15");

        // Open a period covering April 2026, but do NOT close it.
        Instant from = LocalDate.parse("2026-04-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = LocalDate.parse("2026-05-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        HttpResponse<String> openResp = post("/api/finance/ledger/periods", token,
                "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}");
        assertThat(openResp.statusCode()).isEqualTo(201);
        // Period is OPEN → ingest should proceed (net-zero).

        HttpResponse<String> ingestResp = tryIngest(token, stmtDate, "BANKTXN-OPEN-PERIOD");
        assertThat(ingestResp.statusCode())
                .as("an OPEN period covering the date must not lock the ingest")
                .isEqualTo(201);
        JsonNode data = readJson(ingestResp).get("data");
        assertThat(data.get("discrepancyCount").asInt()).isEqualTo(1);
    }
}
