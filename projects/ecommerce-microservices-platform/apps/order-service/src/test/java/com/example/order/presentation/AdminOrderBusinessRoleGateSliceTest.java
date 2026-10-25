package com.example.order.presentation;

import com.example.order.TestOrderServiceApplication;
import com.example.order.application.service.AdminOrderStatusService;
import com.example.order.application.service.OrderQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions 2026-10-16 UTC) — order-service re-checks the business role on
 * every operator path ({@code /api/admin/orders/**}, 4 reads + 1 write). Until this ticket the gateway was the only
 * decision here (Failure 1); a call that does not pass the gateway now gets the same answer.
 *
 * <p>Decision (ecommerce iam-integration.md § 업무 역할): GET = {@code ECOMMERCE_ORDER_READ ∨ ECOMMERCE_ORDER_WRITE}
 * ∨ ({@code X-Tenant-Id = '*'} — the platform wildcard read, TASK-BE-506, kept as is); the status change =
 * {@code ECOMMERCE_ORDER_WRITE}. The entry role {@code ECOMMERCE_OPERATOR} alone opens nothing.
 *
 * <p>«Allowed» = anything but 403 (the services are mocks — a 200 or a 500 both prove the gate let it through).
 */
@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = TestOrderServiceApplication.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("TASK-MONO-795 order 업무 역할 게이트 (경로 5 전수 · 직접 호출)")
class AdminOrderBusinessRoleGateSliceTest {

    private static final String ENTRY = "ECOMMERCE_OPERATOR";
    private static final String ALL_BUSINESS = String.join(",", ENTRY,
            "ECOMMERCE_ORDER_READ", "ECOMMERCE_ORDER_WRITE", "ECOMMERCE_PRODUCT_READ", "ECOMMERCE_PRODUCT_WRITE",
            "ECOMMERCE_MEMBER_READ", "ECOMMERCE_SETTLEMENT_READ", "ECOMMERCE_SETTLEMENT_WRITE",
            "ECOMMERCE_PROMOTION_READ", "ECOMMERCE_PROMOTION_WRITE", "ECOMMERCE_SHIPPING_READ",
            "ECOMMERCE_SHIPPING_WRITE", "ECOMMERCE_NOTIFICATION_READ", "ECOMMERCE_NOTIFICATION_WRITE");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderQueryService orderQueryService;

    @MockitoBean
    private AdminOrderStatusService adminOrderStatusService;

    private record Endpoint(String name, boolean write) {
        MockHttpServletRequestBuilder request() {
            return switch (name) {
                case "summary" -> get("/api/admin/orders/summary");
                case "insights" -> get("/api/admin/orders/insights");
                // TASK-MONO-807 — the overview performance metrics: a read like the rest.
                case "metrics" -> get("/api/admin/orders/metrics").param("period", "week");
                case "list" -> get("/api/admin/orders");
                case "detail" -> get("/api/admin/orders/order-1");
                case "status" -> post("/api/admin/orders/order-1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}");
                default -> throw new IllegalArgumentException(name);
            };
        }
    }

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("summary", false), new Endpoint("insights", false), new Endpoint("metrics", false),
            new Endpoint("list", false), new Endpoint("detail", false), new Endpoint("status", true));

    /** persona header · tenant header · may read · may write. */
    static Stream<Arguments> personas() {
        return Stream.of(
                Arguments.of("order read set", ENTRY + ",ECOMMERCE_ORDER_READ", "ecommerce", true, false),
                Arguments.of("order operate set", ENTRY + ",ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE", "ecommerce", true, true),
                Arguments.of("write role alone (no READ) still reads", ENTRY + ",ECOMMERCE_ORDER_WRITE", "ecommerce", true, true),
                Arguments.of("🔴 product operate set only (another area)", ENTRY + ",ECOMMERCE_PRODUCT_READ,ECOMMERCE_PRODUCT_WRITE", "ecommerce", false, false),
                Arguments.of("🔴 entry role alone", ENTRY, "ecommerce", false, false),
                Arguments.of("CUSTOMER", "CUSTOMER", "ecommerce", false, false),
                Arguments.of("blank X-User-Role", "", "ecommerce", false, false),
                Arguments.of("legacy-compat (every business role)", ALL_BUSINESS, "ecommerce", true, true),
                Arguments.of("lower-case role (comparison ignores case)", "ecommerce_order_read", "ecommerce", true, false),
                Arguments.of("substring is not membership", "ECOMMERCE_ORDER_READX", "ecommerce", false, false),
                // TASK-BE-659 — the wildcard tenant owns no order: every read used to answer 200 with nothing
                // (zeros / an empty page / 404). Now all reads refuse it, as /metrics already did (TASK-MONO-817).
                Arguments.of("🔴 '*' wildcard (platform SUPER_ADMIN, no roles) — every read 403", "", "*", false, false),
                Arguments.of("🔴 '*' wildcard WITH the order read role — every read 403", ENTRY + ",ECOMMERCE_ORDER_READ", "*", false, false));
    }

    /**
     * TASK-MONO-817 (metrics) · TASK-BE-659 (every other read) — the wildcard tenant is refused whatever the roles
     * (it would read all zeros / nothing). Kept as its own term so a persona that holds the roles still expects 403.
     */
    private static boolean wildcardRefused(Endpoint e, String tenant) {
        return !e.write() && "*".equals(tenant);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("personas")
    @DisplayName("each persona × the six order operator paths")
    void gate(String persona, String roleHeader, String tenant, boolean mayRead, boolean mayWrite) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Endpoint e : ENDPOINTS) {
            int status = mockMvc.perform(e.request().header("X-User-Role", roleHeader).header("X-Tenant-Id", tenant))
                    .andReturn().getResponse().getStatus();
            boolean expectAllowed = (e.write() ? mayWrite : mayRead) && !wildcardRefused(e, tenant);
            if (expectAllowed == (status == 403)) {
                wrong.add(e.name() + " → " + status + " (expected " + (expectAllowed ? "allowed" : "403") + ")");
            }
        }
        assertThat(wrong).as(persona).isEmpty();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("🔴 no X-User-Role header at all (a direct call that skipped the gateway) → 403 on every path")
    void noHeader_denied() throws Exception {
        for (Endpoint e : ENDPOINTS) {
            assertThat(mockMvc.perform(e.request()).andReturn().getResponse().getStatus()).as(e.name()).isEqualTo(403);
        }
    }
}
