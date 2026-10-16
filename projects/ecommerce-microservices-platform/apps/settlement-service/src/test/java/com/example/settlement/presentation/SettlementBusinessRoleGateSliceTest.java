package com.example.settlement.presentation;

import com.example.settlement.TestSettlementServiceApplication;
import com.example.settlement.application.service.CloseSettlementPeriodUseCase;
import com.example.settlement.application.service.CommissionRateAdminService;
import com.example.settlement.application.service.ExecuteSellerPayoutsUseCase;
import com.example.settlement.application.service.OpenSettlementPeriodUseCase;
import com.example.settlement.application.service.QuerySettlementPeriodUseCase;
import com.example.settlement.application.service.SettlementQueryService;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions 2026-10-16 UTC) — settlement-service re-checks the E-commerce business
 * role on every operator path ({@code /api/admin/settlements/**}, 5 reads · 4 writes). Before this ticket the service check was «{@code X-User-Role} ∋
 * {@code ECOMMERCE_OPERATOR}» — the entry role, which every business-role holder now carries.
 *
 * <p>Decision (ecommerce iam-integration.md § 업무 역할): GET = {@code ECOMMERCE_SETTLEMENT_READ ∨ ECOMMERCE_SETTLEMENT_WRITE}; the rate PUT, period open, period close and payout execution = {@code ECOMMERCE_SETTLEMENT_WRITE} (owner decision Q2 — no separate control role). The platform wildcard ({@code X-Tenant-Id = '*'},
 * no roles) is NOT admitted here — as today.
 *
 * <p>«Allowed» = anything but 403 (the use cases are mocks).
 */
@WebMvcTest({SettlementController.class, SettlementPeriodController.class})
@ContextConfiguration(classes = TestSettlementServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("TASK-MONO-795 settlement 업무 역할 게이트 (경로 9 전수 · 직접 호출)")
class SettlementBusinessRoleGateSliceTest {

    private static final String ENTRY = "ECOMMERCE_OPERATOR";
    private static final String ALL_BUSINESS = String.join(",", ENTRY,
            "ECOMMERCE_ORDER_READ", "ECOMMERCE_ORDER_WRITE", "ECOMMERCE_PRODUCT_READ", "ECOMMERCE_PRODUCT_WRITE",
            "ECOMMERCE_MEMBER_READ", "ECOMMERCE_SETTLEMENT_READ", "ECOMMERCE_SETTLEMENT_WRITE",
            "ECOMMERCE_PROMOTION_READ", "ECOMMERCE_PROMOTION_WRITE", "ECOMMERCE_SHIPPING_READ",
            "ECOMMERCE_SHIPPING_WRITE", "ECOMMERCE_NOTIFICATION_READ", "ECOMMERCE_NOTIFICATION_WRITE");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService queryService;

    @MockitoBean
    private CommissionRateAdminService rateAdminService;

    @MockitoBean
    private OpenSettlementPeriodUseCase openPeriod;

    @MockitoBean
    private CloseSettlementPeriodUseCase closePeriod;

    @MockitoBean
    private QuerySettlementPeriodUseCase queryPeriod;

    @MockitoBean
    private ExecuteSellerPayoutsUseCase executePayouts;

    private record Endpoint(String name, boolean write, MockHttpServletRequestBuilder request) {}

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<Endpoint> endpoints() {
        return List.of(
                new Endpoint("accruals", false, get("/api/admin/settlements/accruals")),
                new Endpoint("balance", false, get("/api/admin/settlements/sellers/s-1/balance")),
                new Endpoint("rate.get", false, get("/api/admin/settlements/commission-rates/s-1")),
                new Endpoint("periods.list", false, get("/api/admin/settlements/periods")),
                new Endpoint("payouts.list", false, get("/api/admin/settlements/periods/p-1/payouts")),
                new Endpoint("rate.put", true, json(put("/api/admin/settlements/commission-rates/s-1"), "{\"rateBps\":500}")),
                new Endpoint("periods.open", true, json(post("/api/admin/settlements/periods"), "{\"from\":\"2026-06-01T00:00:00Z\",\"to\":\"2026-07-01T00:00:00Z\"}")),
                new Endpoint("periods.close", true, post("/api/admin/settlements/periods/p-1/close").header("X-User-Id", "op-1")),
                new Endpoint("payouts.execute", true, post("/api/admin/settlements/periods/p-1/payouts/execute")));
    }

    /** persona · X-User-Role · X-Tenant-Id · may read · may write. */
    static Stream<Arguments> personas() {
        return Stream.of(
                Arguments.of("settlement read set", ENTRY + ",ECOMMERCE_SETTLEMENT_READ", "ecommerce", true, false),
                Arguments.of("settlement operate set", ENTRY + ",ECOMMERCE_SETTLEMENT_READ,ECOMMERCE_SETTLEMENT_WRITE", "ecommerce", true, true),
                Arguments.of("write role alone still reads", ENTRY + ",ECOMMERCE_SETTLEMENT_WRITE", "ecommerce", true, true),
                Arguments.of("🔴 product operate set only (another area)", ENTRY + ",ECOMMERCE_PRODUCT_READ,ECOMMERCE_PRODUCT_WRITE", "ecommerce", false, false),
                Arguments.of("🔴 entry role alone", ENTRY, "ecommerce", false, false),
                Arguments.of("CUSTOMER", "CUSTOMER", "ecommerce", false, false),
                Arguments.of("blank X-User-Role", "", "ecommerce", false, false),
                Arguments.of("legacy-compat (every business role)", ALL_BUSINESS, "ecommerce", true, true),
                Arguments.of("lower-case role (comparison ignores case)", "ecommerce_settlement_read", "ecommerce", true, false),
                Arguments.of("'*' wildcard — not admitted here (as today)", "", "*", false, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("personas")
    @DisplayName("each persona × the settlement operator paths")
    void gate(String persona, String roleHeader, String tenant, boolean mayRead, boolean mayWrite) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Endpoint e : endpoints()) {
            int status = mockMvc.perform(e.request().header("X-User-Role", roleHeader).header("X-Tenant-Id", tenant))
                    .andReturn().getResponse().getStatus();
            boolean expectAllowed = e.write() ? mayWrite : mayRead;
            if (expectAllowed == (status == 403)) {
                wrong.add(e.name() + " → " + status + " (expected " + (expectAllowed ? "allowed" : "403") + ")");
            }
        }
        assertThat(wrong).as(persona).isEmpty();
    }

    @Test
    @DisplayName("🔴 no X-User-Role header at all (a direct call that skipped the gateway) → 403 on every path")
    void noHeader_denied() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Endpoint e : endpoints()) {
            int status = mockMvc.perform(e.request()).andReturn().getResponse().getStatus();
            if (status != 403) {
                wrong.add(e.name() + " → " + status);
            }
        }
        assertThat(wrong).isEmpty();
    }
}
