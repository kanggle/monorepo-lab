package com.wms.admin.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.example.security.oauth2.TenantClaimValidator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.api.settings.SettingsController;
import com.wms.admin.application.alert.AlertAcknowledgeService;
import com.wms.admin.application.dashboard.WarehouseMetricsService;
import com.wms.admin.application.settings.LowStockThresholdReader;
import com.wms.admin.application.settings.SettingsService;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.infra.security.OAuth2ResourceServerConfig;
import com.wms.admin.infra.security.SecurityContextReadScopeProvider;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * TASK-MONO-827 (ADR-MONO-083 · owner decisions Q1–Q4, 2026-11-09 UTC) — admin-service's dashboard and
 * settings read gates through the REAL {@link SecurityConfig} chain, the REAL tenant gate
 * ({@link OAuth2ResourceServerConfig#jwtTokenValidator()} — entitlement-trust admission) and
 * really-signed RS256 JWTs.
 *
 * <p>Unlike finance's URL-level gate, admin-service decides on the controllers' {@code @PreAuthorize}, so
 * the real controllers are mounted (their repositories mocked). The discriminator is therefore the
 * status class, not a body: refused = {@code 403 FORBIDDEN} (method security), passed the gate = anything
 * but 400/401/403 (a mocked repository answering {@code null} may well produce a 500 — that is past the gate).
 * 🔴 400 is excluded on purpose: request-parameter binding runs <em>before</em> {@code @PreAuthorize}, so a
 * request missing a required parameter answers 400 without ever reaching the gate — counting it as «passed»
 * would make that cell vacuous (the first run caught exactly this on {@code /throughput}).
 *
 * <p>Rows = the system presets of iam {@code V0052} as the issuer puts them in {@code roles}, plus the
 * three contrasts the ticket requires: no set, another domain's sets only, and the {@code '*'} wildcard.
 * <b>Bite</b>: reintroducing the {@code entitled_domains → ROLE_WMS_VIEWER} synthesis turns the «no set»
 * and «other domain only» rows green-to-red (every read would pass).
 */
@WebMvcTest(controllers = {
        OrderDashboardController.class, ShipmentDashboardController.class, AsnDashboardController.class,
        InventoryDashboardController.class, AdjustmentAuditController.class, MasterRefController.class,
        ThroughputDashboardController.class, MetricsDashboardController.class, AlertDashboardController.class,
        SettingsController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SecurityContextReadScopeProvider.class,
        DashboardBusinessRoleGateSliceTest.RealJwtChainConfig.class})
@DisplayName("admin-service — TASK-MONO-827 대시보드·설정 조회 업무 역할 게이트 (실 체인 · 실서명 JWT)")
class DashboardBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final KeyPair KEYS = generateRsaKeyPair();
    private static final String D = "/api/v1/admin/dashboard";
    private static final String ASN_ID = UUID.randomUUID().toString();
    private static final String WH_ID = UUID.randomUUID().toString();
    private static final String SKU_ID = UUID.randomUUID().toString();
    private static final String LOC_ID = UUID.randomUUID().toString();

    // ── the read paths, grouped by the gate that decides them ─────────────────────
    static List<MockHttpServletRequestBuilder> outbound() {
        return List.of(get(D + "/orders"), get(D + "/shipments"));
    }

    static List<MockHttpServletRequestBuilder> inbound() {
        return List.of(get(D + "/asns"), get(D + "/asns/" + ASN_ID + "/inspection"));
    }

    static List<MockHttpServletRequestBuilder> inventory() {
        return List.of(get(D + "/inventory"),
                get(D + "/inventory/by-key").param("locationId", LOC_ID).param("skuId", SKU_ID),
                get(D + "/adjustments"));
    }

    static List<MockHttpServletRequestBuilder> master() {
        return Stream.of("warehouses", "zones", "locations", "skus", "lots", "partners")
                .map(t -> get(D + "/refs/" + t)).toList();
    }

    /** Throughput · metrics · alert list · settings reads (Q2 cross-area · Q4 settings). */
    static List<MockHttpServletRequestBuilder> crossArea() {
        return List.of(get(D + "/throughput").param("warehouseId", WH_ID)
                        .param("from", "2026-11-01").param("to", "2026-11-07"), get(D + "/metrics").param("period", "week"),
                get(D + "/alerts"), get("/api/v1/admin/settings"), get("/api/v1/admin/settings/inventory.low_stock"));
    }

    // ── iam V0052 system presets, as the issuer puts them in `roles` ──────────────
    static final List<String> WMS_INVENTORY_READ = List.of("INVENTORY_READ");
    static final List<String> WMS_INVENTORY_OPERATE = List.of("INVENTORY_READ", "INVENTORY_WRITE");
    static final List<String> WMS_INBOUND_OPERATE = List.of("INBOUND_READ", "INBOUND_WRITE");
    static final List<String> WMS_OUTBOUND_OPERATE = List.of("OUTBOUND_READ", "OUTBOUND_WRITE");
    static final List<String> WMS_MASTER_READ = List.of("MASTER_READ");
    static final List<String> WMS_OPS_ACTIONS = List.of("WMS_OPERATOR");

    /** Every non-wms role of iam {@code DomainRoleCatalog} — the «other domain's sets only» row. */
    static final List<String> OTHER_DOMAIN_ROLES = List.of(
            "SCM_OPERATOR", "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE", "SCM_DEMAND_PLANNING_READ",
            "SCM_DEMAND_PLANNING_WRITE", "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE",
            "ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE",
            "FINANCE_OPERATOR", "FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE", "FINANCE_LEDGER_READ",
            "FINANCE_LEDGER_WRITE", "FINANCE_LEDGER_CONTROL",
            "ECOMMERCE_OPERATOR", "ECOMMERCE_ORDER_READ", "ECOMMERCE_ORDER_WRITE", "ECOMMERCE_PRODUCT_READ",
            "ECOMMERCE_PRODUCT_WRITE", "ECOMMERCE_MEMBER_READ", "ECOMMERCE_SETTLEMENT_READ",
            "ECOMMERCE_SETTLEMENT_WRITE", "ECOMMERCE_PROMOTION_READ", "ECOMMERCE_PROMOTION_WRITE",
            "ECOMMERCE_SHIPPING_READ", "ECOMMERCE_SHIPPING_WRITE", "ECOMMERCE_NOTIFICATION_READ",
            "ECOMMERCE_NOTIFICATION_WRITE", "FAN_OPERATOR", "MES_OPERATOR");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean OrderSummaryRepository orderRepo;
    @MockitoBean ShipmentSummaryRepository shipmentRepo;
    @MockitoBean AsnSummaryRepository asnRepo;
    @MockitoBean InspectionSummaryRepository inspectionRepo;
    @MockitoBean InventorySnapshotRepository inventoryRepo;
    @MockitoBean LowStockThresholdReader thresholdReader;
    @MockitoBean AdjustmentAuditRepository adjustmentRepo;
    @MockitoBean WarehouseRefRepository warehouseRepo;
    @MockitoBean ZoneRefRepository zoneRepo;
    @MockitoBean LocationRefRepository locationRepo;
    @MockitoBean SkuRefRepository skuRepo;
    @MockitoBean LotRefRepository lotRepo;
    @MockitoBean PartnerRefRepository partnerRepo;
    @MockitoBean ThroughputInboundDailyRepository inboundDailyRepo;
    @MockitoBean ThroughputOutboundDailyRepository outboundDailyRepo;
    @MockitoBean WarehouseMetricsService metricsService;
    @MockitoBean AlertLogRepository alertRepo;
    @MockitoBean AlertAcknowledgeService acknowledgeService;
    @MockitoBean SettingsService settingsService;

    @TestConfiguration
    static class RealJwtChainConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            OAuth2ResourceServerConfig config = new OAuth2ResourceServerConfig();
            ReflectionTestUtils.setField(config, "requiredTenantId", "wms");
            ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
            ReflectionTestUtils.setField(config, "jwkSetUri", ISSUER + "/oauth2/jwks");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(config.jwtTokenValidator());
            return decoder;
        }
    }

    // ── contrasts ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("세트 없음 + 구독(entitled_domains ∋ wms) → 대시보드·설정 조회 전부 403 (합성 제거 · Q1)")
    void subscriptionWithoutAnySet_isRefusedEverywhere() throws Exception {
        expectAll(allReads(), customer(), false);
    }

    @Test
    @DisplayName("다른 도메인 세트만(카탈로그의 비-wms 역할 전부) → 전부 403 — 이름 충돌로 새는 게이트 없음")
    void otherDomainRolesOnly_isRefusedEverywhere() throws Exception {
        expectAll(allReads(), customer(OTHER_DOMAIN_ROLES), false);
    }

    @Test
    @DisplayName("'*' 와일드카드(entitled_domains 없음) → 입장 단계에서 403 TENANT_FORBIDDEN (Q3 — 게이트까지 오지 않는다)")
    void wildcardToken_isRefusedAtAdmission() throws Exception {
        for (MockHttpServletRequestBuilder r : allReads()) {
            mockMvc.perform(r.header("Authorization", "Bearer "
                            + token(c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "*")
                            .claim("roles", List.of("WMS_OPERATOR")))))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("%s must be refused at admission", result.getRequest().getRequestURI())
                            .isEqualTo(403))
                    .andExpect(jsonPath("$.error.code").value("TENANT_FORBIDDEN"));
        }
    }

    // ── single-area presets: their own area + the cross-area reads, nothing else (Q2) ─

    @Test
    @DisplayName("wms-inventory-read / wms-inventory-operate → 재고·조정 + 공통 조회만")
    void inventoryPresets() throws Exception {
        for (List<String> preset : List.of(WMS_INVENTORY_READ, WMS_INVENTORY_OPERATE)) {
            expectAreas(customer(preset), false, false, true, false);
        }
    }

    @Test
    @DisplayName("wms-inbound-operate → ASN·검수 + 공통 조회만")
    void inboundPreset() throws Exception {
        expectAreas(customer(WMS_INBOUND_OPERATE), false, true, false, false);
    }

    @Test
    @DisplayName("wms-outbound-operate → 주문·출하 + 공통 조회만")
    void outboundPreset() throws Exception {
        expectAreas(customer(WMS_OUTBOUND_OPERATE), true, false, false, false);
    }

    @Test
    @DisplayName("wms-master-read → 기준정보 + 공통 조회만")
    void masterPreset() throws Exception {
        expectAreas(customer(WMS_MASTER_READ), false, false, false, true);
    }

    @Test
    @DisplayName("쓰기 역할만 든 맞춤 세트(OUTBOUND_WRITE) → 출고 조회도 열린다 (READ ∨ WRITE)")
    void writeOnlyCustomSet_readsItsArea() throws Exception {
        expectAreas(customer(List.of("OUTBOUND_WRITE")), true, false, false, false);
    }

    // ── presets that read everything, as before ───────────────────────────────────

    @Test
    @DisplayName("wms-ops-actions(WMS_OPERATOR) → 전부 통과 (역할 계층 OPERATOR > VIEWER)")
    void opsActions_readsEverything() throws Exception {
        expectAll(allReads(), customer(WMS_OPS_ACTIONS), true);
    }

    @Test
    @DisplayName("legacy-compat / all-domains-full(카탈로그 전부) → 이전과 같은 집합 = 전부 통과")
    void legacyCompat_readsEverything() throws Exception {
        List<String> all = new ArrayList<>(OTHER_DOMAIN_ROLES);
        all.addAll(List.of("INVENTORY_READ", "INVENTORY_WRITE", "INBOUND_READ", "INBOUND_WRITE",
                "OUTBOUND_READ", "OUTBOUND_WRITE", "MASTER_READ", "WMS_OPERATOR"));
        expectAll(allReads(), customer(all), true);
    }

    @Test
    @DisplayName("wms 고유 테넌트의 WMS_ADMIN → 전부 통과 (계층)")
    void nativeAdmin_readsEverything() throws Exception {
        expectAll(allReads(), c -> c.claim("roles", List.of("WMS_ADMIN")), true);
    }

    // ── the alert edge: list is a read, acknowledge stays WMS_OPERATOR ───────────────

    @Test
    @DisplayName("알림: 조회 업무 역할은 목록 200 · 확인(쓰기) 403 / WMS_OPERATOR 는 목록도 본다")
    void alertListVersusAcknowledge() throws Exception {
        MockHttpServletRequestBuilder ack = post(D + "/alerts/" + UUID.randomUUID() + "/acknowledge");
        expectDenied(ack, customer(WMS_INVENTORY_READ));
        expectAllowed(get(D + "/alerts"), customer(WMS_INVENTORY_READ));
        expectAllowed(get(D + "/alerts"), customer(WMS_OPS_ACTIONS));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    static List<MockHttpServletRequestBuilder> allReads() {
        List<MockHttpServletRequestBuilder> all = new ArrayList<>();
        all.addAll(outbound());
        all.addAll(inbound());
        all.addAll(inventory());
        all.addAll(master());
        all.addAll(crossArea());
        return all;
    }

    /** A customer-tenant operator admitted on entitlement-trust, holding {@code roles} (none if empty). */
    private static Consumer<JWTClaimsSet.Builder> customer(List<String> roles) {
        return c -> {
            c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "demo-corp")
                    .claim("entitled_domains", List.of("wms", "finance", "scm", "erp"));
            if (!roles.isEmpty()) {
                c.claim("roles", roles);
            }
        };
    }

    private static Consumer<JWTClaimsSet.Builder> customer() {
        return customer(List.of());
    }

    /** The holder's own areas pass, the others are refused, the cross-area reads always pass. */
    private void expectAreas(Consumer<JWTClaimsSet.Builder> claims, boolean outbound, boolean inbound,
                             boolean inventory, boolean master) throws Exception {
        expectAll(outbound(), claims, outbound);
        expectAll(inbound(), claims, inbound);
        expectAll(inventory(), claims, inventory);
        expectAll(master(), claims, master);
        expectAll(crossArea(), claims, true);
    }

    private void expectAll(List<MockHttpServletRequestBuilder> requests, Consumer<JWTClaimsSet.Builder> claims,
                           boolean allowed) throws Exception {
        for (MockHttpServletRequestBuilder r : requests) {
            if (allowed) {
                expectAllowed(r, claims);
            } else {
                expectDenied(r, claims);
            }
        }
    }

    private void expectDenied(MockHttpServletRequestBuilder request, Consumer<JWTClaimsSet.Builder> claims)
            throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(claims)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s %s must be refused", result.getRequest().getMethod(),
                                result.getRequest().getRequestURI())
                        .isEqualTo(403))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, Consumer<JWTClaimsSet.Builder> claims)
            throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(claims)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s %s must pass the gate, body=%s", result.getRequest().getMethod(),
                                result.getRequest().getRequestURI(), result.getResponse().getContentAsString())
                        .isNotIn(400, 401, 403));
    }

    private static String token(Consumer<JWTClaimsSet.Builder> extra) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .subject("console-operator-827")
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, "wms");
        extra.accept(b);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), b.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA keypair generation failed", e);
        }
    }
}
