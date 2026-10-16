package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.ProductImageService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerMemberService;
import com.example.product.application.service.SellerQueryService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.infrastructure.config.ProductSecurityConfig;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions 2026-10-16 UTC) — product-service re-checks the business role on
 * every operator path: products (3 reads · 7 writes), images (1 · 4), sellers (3 · 4) and seller members (1 · 1) —
 * 24 paths. Products and images had NO service check before this ticket (Failure 1); sellers checked only the entry
 * role.
 *
 * <p>Decision (ecommerce iam-integration.md § 업무 역할): GET = {@code ECOMMERCE_PRODUCT_READ ∨ ECOMMERCE_PRODUCT_WRITE};
 * every other method = {@code ECOMMERCE_PRODUCT_WRITE}. The platform wildcard read ({@code X-Tenant-Id = '*'}, no
 * roles — TASK-BE-506) stays exactly where it reaches today: product reads 3 + image list 1, NOT the seller reads.
 *
 * <p>«Allowed» = anything but 403 (the services are mocks).
 */
@WebMvcTest(controllers = {AdminProductController.class, AdminProductImageController.class,
        AdminSellerController.class, AdminSellerMemberController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import({GlobalExceptionHandler.class, ProductSecurityConfig.class})
@DisplayName("TASK-MONO-795 product 업무 역할 게이트 (경로 24 전수 · 직접 호출)")
class ProductBusinessRoleGateSliceTest {

    private static final String ENTRY = "ECOMMERCE_OPERATOR";
    private static final String P = "/api/admin/products/";
    private static final String PID = "00000000-0000-7000-8000-000000000001";
    private static final String VID = "00000000-0000-7000-8000-000000000002";
    private static final String IMG = "00000000-0000-7000-8000-000000000003";
    private static final String ALL_BUSINESS = String.join(",", ENTRY,
            "ECOMMERCE_ORDER_READ", "ECOMMERCE_ORDER_WRITE", "ECOMMERCE_PRODUCT_READ", "ECOMMERCE_PRODUCT_WRITE",
            "ECOMMERCE_MEMBER_READ", "ECOMMERCE_SETTLEMENT_READ", "ECOMMERCE_SETTLEMENT_WRITE",
            "ECOMMERCE_PROMOTION_READ", "ECOMMERCE_PROMOTION_WRITE", "ECOMMERCE_SHIPPING_READ",
            "ECOMMERCE_SHIPPING_WRITE", "ECOMMERCE_NOTIFICATION_READ", "ECOMMERCE_NOTIFICATION_WRITE");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RegisterProductService registerProductService;
    @MockitoBean private UpdateProductService updateProductService;
    @MockitoBean private DeleteProductService deleteProductService;
    @MockitoBean private AdjustStockService adjustStockService;
    @MockitoBean private VariantManagementService variantManagementService;
    @MockitoBean private QueryProductService queryProductService;
    @MockitoBean private ProductImageService productImageService;
    @MockitoBean private MediaUrlResolver mediaUrlResolver;
    @MockitoBean private RegisterSellerService registerSellerService;
    @MockitoBean private SellerQueryService sellerQueryService;
    @MockitoBean private SellerMemberService sellerMemberService;

    /** name · write · wildcard read admitted today. */
    private record Endpoint(String name, boolean write, boolean wildcardRead, MockHttpServletRequestBuilder request) {}

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static List<Endpoint> endpoints() {
        String variants = "{\"name\":\"n\",\"price\":1000,\"variants\":[{\"optionName\":\"o\",\"stock\":1,\"additionalPrice\":0}]}";
        return List.of(
                // products — reads (wildcard admitted today)
                new Endpoint("products.list", false, true, get("/api/admin/products")),
                new Endpoint("products.summary", false, true, get("/api/admin/products/summary")),
                new Endpoint("products.detail", false, true, get(P + PID)),
                // products — writes
                new Endpoint("products.register", true, false,
                        json(post("/api/admin/products"), variants).header("Idempotency-Key", "k-1")),
                new Endpoint("products.update", true, false, json(patch(P + PID), "{\"name\":\"n2\"}")),
                new Endpoint("products.delete", true, false, delete(P + PID)),
                new Endpoint("variants.add", true, false,
                        json(post(P + PID + "/variants"), "{\"optionName\":\"o\",\"stock\":1,\"additionalPrice\":0}")),
                new Endpoint("variants.update", true, false,
                        json(patch(P + PID + "/variants/" + VID), "{\"optionName\":\"o\",\"additionalPrice\":0}")),
                new Endpoint("variants.delete", true, false, delete(P + PID + "/variants/" + VID)),
                new Endpoint("stock.adjust", true, false,
                        json(patch(P + PID + "/stock"), "{\"variantId\":\"" + VID + "\",\"quantity\":1,\"reason\":\"r\"}")
                                .header("Idempotency-Key", "k-2")),
                // images
                new Endpoint("images.list", false, true, get(P + PID + "/images")),
                new Endpoint("images.uploadUrl", true, false,
                        json(post(P + PID + "/images/upload-url"), "{\"contentType\":\"image/png\",\"contentLength\":10}")),
                new Endpoint("images.register", true, false,
                        json(post(P + PID + "/images"), "{\"objectKey\":\"k\",\"sortOrder\":0,\"isPrimary\":false}")),
                new Endpoint("images.update", true, false, json(patch(P + PID + "/images/" + IMG), "{\"sortOrder\":1}")),
                new Endpoint("images.delete", true, false, delete(P + PID + "/images/" + IMG)),
                // sellers — reads (wildcard NOT admitted, as today)
                new Endpoint("sellers.summary", false, false, get("/api/admin/sellers/summary")),
                new Endpoint("sellers.list", false, false, get("/api/admin/sellers")),
                new Endpoint("sellers.detail", false, false, get("/api/admin/sellers/s-1")),
                // sellers — writes
                new Endpoint("sellers.register", true, false,
                        json(post("/api/admin/sellers"), "{\"sellerId\":\"s-1\",\"displayName\":\"d\"}")),
                new Endpoint("sellers.provision", true, false, post("/api/admin/sellers/s-1/provision")),
                new Endpoint("sellers.suspend", true, false, post("/api/admin/sellers/s-1/suspend")),
                new Endpoint("sellers.close", true, false, post("/api/admin/sellers/s-1/close")),
                // seller members
                new Endpoint("members.list", false, false, get("/api/admin/sellers/s-1/members")),
                new Endpoint("members.invite", true, false,
                        json(post("/api/admin/sellers/s-1/invitations"), "{\"email\":\"a@example.com\"}")
                                .header("X-User-Id", "op-1")));
    }

    /** persona · X-User-Role · X-Tenant-Id · may read · may write · wildcard persona. */
    static Stream<Arguments> personas() {
        return Stream.of(
                Arguments.of("product read set", ENTRY + ",ECOMMERCE_PRODUCT_READ", "ecommerce", true, false, false),
                Arguments.of("product operate set", ENTRY + ",ECOMMERCE_PRODUCT_READ,ECOMMERCE_PRODUCT_WRITE", "ecommerce", true, true, false),
                Arguments.of("🔴 order operate set only (another area)", ENTRY + ",ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE", "ecommerce", false, false, false),
                Arguments.of("🔴 entry role alone", ENTRY, "ecommerce", false, false, false),
                Arguments.of("CUSTOMER", "CUSTOMER", "ecommerce", false, false, false),
                Arguments.of("blank X-User-Role", "", "ecommerce", false, false, false),
                Arguments.of("legacy-compat (every business role)", ALL_BUSINESS, "ecommerce", true, true, false),
                Arguments.of("'*' wildcard — product/image reads only, as today", "", "*", false, false, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("personas")
    @DisplayName("each persona × the 24 product operator paths")
    void gate(String persona, String roleHeader, String tenant, boolean mayRead, boolean mayWrite, boolean wildcard)
            throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Endpoint e : endpoints()) {
            int status = mockMvc.perform(e.request().header("X-User-Role", roleHeader).header("X-Tenant-Id", tenant))
                    .andReturn().getResponse().getStatus();
            boolean expectAllowed = wildcard ? (!e.write() && e.wildcardRead()) : (e.write() ? mayWrite : mayRead);
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
