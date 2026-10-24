package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.AccountApplicationService.HoldResult;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.CaptureHoldCommand;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.command.ReleaseHoldCommand;
import com.example.finance.account.application.command.TopUpCommand;
import com.example.finance.account.application.command.TransferCommand;
import com.example.finance.account.application.command.UpgradeKycCommand;
import com.example.finance.account.application.port.outbound.ClockPort;
import com.example.finance.account.domain.error.DomainErrors.SanctionHitException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-812 (ADR-MONO-084 P7) — {@code GET /api/finance/accounts/metrics} end to end on
 * <b>real MySQL</b> (production engine; H2 forbidden), through the real {@code SecurityConfig} +
 * validator chain with signed tokens. Every row is written by the <b>real fund-movement
 * path</b> ({@link AccountApplicationService}) under an injected {@link ClockPort}, so the
 * HOLD / CAPTURE / RELEASE / FAILED rows are exactly what production writes — not hand-typed
 * SQL that could disagree with it.
 *
 * <p>Isolation from the sibling ITs (one shared MySQL per JVM): this class writes under its own
 * tenants ({@value #TENANT} / {@value #OTHER_TENANT}), admitted by the tenant gate's
 * entitlement-trust leg ({@code entitled_domains ∋ finance}) plus the account read role.
 *
 * <p>Clock: Fri 2026-10-23 14:05 KST. Week = [Mon 10-19 00:00 KST, now); previous =
 * [Mon 10-12 00:00 KST, Fri 10-16 14:05 KST).
 */
@AutoConfigureMockMvc
class TransactionMetricsIntegrationTest extends AbstractAccountIntegrationTest {

    static final String TENANT = "fin-metrics-812";
    static final String OTHER_TENANT = "fin-metrics-812-other";

    private static final Instant NOW = Instant.parse("2026-10-23T05:05:00Z");
    private static final Instant LAST_WEEK = Instant.parse("2026-10-13T01:00:00Z");   // Tue 10-13 10:00 KST
    private static final Instant THIS_WEEK = Instant.parse("2026-10-19T01:00:00Z");   // Mon 10-19 10:00 KST

    private static final AtomicReference<Instant> CLOCK = new AtomicReference<>(NOW);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AccountApplicationService service;

    @MockitoBean
    ClockPort clockPort;

    @BeforeAll
    static void publishKey() {
        publishSigningKey();
    }

    @BeforeEach
    void wireClock() {
        when(clockPort.now()).thenAnswer(inv -> CLOCK.get());
    }

    private static ActorContext holder(String tenant) {
        return new ActorContext("holder-812", tenant, Set.of());
    }

    private static ActorContext operator(String tenant) {
        return new ActorContext("op-812", tenant, Set.of("FINANCE_OPERATOR", "FINANCE_ACCOUNT_WRITE"));
    }

    private String openActive(String tenant, String ownerRef, String currency) {
        String id = service.openAccount(new OpenAccountCommand(holder(tenant), ownerRef, currency, "NONE"))
                .accountId();
        service.upgradeKyc(new UpgradeKycCommand(operator(tenant), id, "FULL", "kyc verified"));
        return id;
    }

    private void topUp(String tenant, String accountId, long minor, String currency) {
        service.topUp(new TopUpCommand(operator(tenant), accountId, Long.toString(minor), currency, "812 IT"));
    }

    private String hold(String tenant, String accountId, long minor) {
        HoldResult r = service.placeHold(new PlaceHoldCommand(holder(tenant), accountId,
                Long.toString(minor), "KRW", 604_800, "812 IT"));
        return r.hold().holdId();
    }

    private String readToken(String tenant) {
        return token(claims -> claims
                .claim("tenant_id", tenant)
                .claim("entitled_domains", List.of("finance"))
                .claim("roles", List.of("FINANCE_OPERATOR", "FINANCE_ACCOUNT_READ")));
    }

    private JsonNode metrics(String token) throws Exception {
        String body = mockMvc.perform(get("/api/finance/accounts/metrics?period=week")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static Map<String, String> side(JsonNode body, String side) {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode m : body.get("metrics")) {
            out.put(m.get("key").asText(), m.get(side).asText());
        }
        return out;
    }

    @Test
    @DisplayName("🔴 AC-1·2·3·4 — settled money only, per currency, tenant-scoped, bucketed on KST days under either JVM zone")
    void endToEnd() throws Exception {
        // ---- previous week: one top-up (counts), one hold (never counts) ----
        CLOCK.set(LAST_WEEK);
        String a = openActive(TENANT, "owner-812-a", "KRW");
        topUp(TENANT, a, 1_000_000, "KRW");
        String heldLastWeek = hold(TENANT, a, 40_000);

        // ---- this week ----
        CLOCK.set(THIS_WEEK);
        topUp(TENANT, a, 500_000, "KRW");
        String b = openActive(TENANT, "owner-812-b", "KRW");
        service.transfer(new TransferCommand(holder(TENANT), a, b, "50000", "KRW", "p2p"));
        // Hold placed last week, PARTIALLY captured this week: only the captured 12,000 moves,
        // and it lands in this week (Edge Cases: attributed to the capture time).
        service.captureHold(new CaptureHoldCommand(holder(TENANT), a, heldLastWeek, "12000", "KRW"));
        // An active hold and a released hold: neither moves any amount.
        hold(TENANT, a, 100_000);
        String released = hold(TENANT, a, 30_000);
        service.releaseHold(new ReleaseHoldCommand(holder(TENANT), a, released));
        // A failed transaction: the sanction screen rejects the top-up; the FAILED row is durable.
        String sanctioned = openActive(TENANT, "SANCTIONED-OWNER", "KRW");
        assertThatThrownBy(() -> topUp(TENANT, sanctioned, 777_000, "KRW"))
                .isInstanceOf(SanctionHitException.class);
        // A USD account: cents, never added to won.
        String c = openActive(TENANT, "owner-812-c", "USD");
        topUp(TENANT, c, 2_550, "USD");
        // Another tenant's activity in the same window.
        String other = openActive(OTHER_TENANT, "owner-812-x", "KRW");
        topUp(OTHER_TENANT, other, 999_000, "KRW");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transactions where tenant_id = ? and status = 'FAILED'",
                Long.class, TENANT)).as("the failed row really exists").isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from transactions where tenant_id = ? and type in ('HOLD','RELEASE') "
                        + "and status = 'COMPLETED'", Long.class, TENANT))
                .as("hold / release rows really are COMPLETED — the type rule is what excludes them")
                .isEqualTo(4L);

        CLOCK.set(NOW);
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : List.of("UTC", "Asia/Seoul")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                JsonNode body = metrics(readToken(TENANT));

                assertThat(body.get("current").get("from").asText()).as(zone).isEqualTo("2026-10-19T00:00:00+09:00");
                Map<String, String> cur = side(body, "current");
                assertThat(cur).as(zone).containsExactly(
                        Map.entry("topupCount", "2"),
                        Map.entry("topupAmount:KRW", "500000"),
                        Map.entry("topupAmount:USD", "2550"),
                        Map.entry("transferCount", "1"),
                        Map.entry("transferAmount:KRW", "50000"),
                        Map.entry("transferAmount:USD", "0"),
                        Map.entry("captureCount", "1"),
                        Map.entry("captureAmount:KRW", "12000"),
                        Map.entry("captureAmount:USD", "0"));
                Map<String, String> prev = side(body, "previous");
                assertThat(prev.get("topupCount")).as(zone).isEqualTo("1");
                assertThat(prev.get("topupAmount:KRW")).as(zone).isEqualTo("1000000");
                assertThat(prev.get("captureAmount:KRW")).as(zone).isEqualTo("0");

                // Every row of this week settled on Mon 10-19 KST.
                JsonNode monday = body.get("series").get(0);
                assertThat(monday.get("date").asText()).isEqualTo("2026-10-19");
                assertThat(monday.get("values").get("topupAmount:KRW").asText()).isEqualTo("500000");
                assertThat(monday.get("values").get("captureAmount:KRW").asText()).isEqualTo("12000");
                assertThat(body.get("series").get(1).get("values").get("topupCount").asText()).isEqualTo("0");

                // AC-3: no account identity anywhere in the body.
                String raw = body.toString();
                assertThat(raw).doesNotContain(a).doesNotContain(b).doesNotContain(c)
                        .doesNotContain("owner-812").doesNotContainIgnoringCase("accountId");
            }
        } finally {
            TimeZone.setDefault(original);
        }

        // Tenant isolation: the other tenant sees only its own top-up.
        Map<String, String> otherCur = side(metrics(readToken(OTHER_TENANT)), "current");
        assertThat(otherCur.get("topupCount")).isEqualTo("1");
        assertThat(otherCur.get("topupAmount:KRW")).isEqualTo("999000");
        assertThat(otherCur).doesNotContainKey("topupAmount:USD");
        assertThat(otherCur.get("transferCount")).isEqualTo("0");
    }

    @Test
    @DisplayName("AC-4 authorization: ledger-only operator → 403 PERMISSION_DENIED; no token → 401; bad period → 400")
    void authorizationAndErrors() throws Exception {
        String ledgerOnly = token(claims -> claims
                .claim("tenant_id", TENANT)
                .claim("entitled_domains", List.of("finance"))
                .claim("roles", List.of("FINANCE_OPERATOR", "FINANCE_LEDGER_READ")));
        mockMvc.perform(get("/api/finance/accounts/metrics?period=week")
                        .header("Authorization", "Bearer " + ledgerOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/finance/accounts/metrics?period=week"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/finance/accounts/metrics?period=year")
                        .header("Authorization", "Bearer " + readToken(TENANT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
