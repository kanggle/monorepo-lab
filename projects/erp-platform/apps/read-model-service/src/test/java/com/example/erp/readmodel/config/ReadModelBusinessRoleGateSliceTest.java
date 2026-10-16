package com.example.erp.readmodel.config;

import com.example.common.page.PageResult;
import com.example.erp.readmodel.adapter.inbound.web.ApprovalFactController;
import com.example.erp.readmodel.adapter.inbound.web.DelegationFactController;
import com.example.erp.readmodel.adapter.inbound.web.EmployeeOrgViewController;
import com.example.erp.readmodel.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.erp.readmodel.application.QueryApprovalFactUseCase;
import com.example.erp.readmodel.application.QueryDelegationFactUseCase;
import com.example.erp.readmodel.application.QueryEmployeeOrgViewUseCase;
import com.example.erp.readmodel.presentation.security.ReadAuthorizationGate;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions Q1 · Q6, 2026-10-15 UTC) — read-model's business-role gate
 * through the real chain + the REAL {@link ReadAuthorizationGate} (use cases mocked): {@code employees*} =
 * masterdata read, {@code approvals*} · {@code delegations*} = approval read; the entry role alone and the
 * subscription alone open neither. Really RSA-signed tokens against a JWKS this test serves, like the
 * service's {@link SecurityChainAssemblySliceTest}.
 */
@WebMvcTest(controllers = {EmployeeOrgViewController.class, ApprovalFactController.class,
        DelegationFactController.class})
@Import({SecurityConfig.class, ServiceLevelOAuth2Config.class, GlobalExceptionHandler.class,
        ReadAuthorizationGate.class})
@DisplayName("read-model-service — ERP business-role gate (TASK-MONO-793)")
class ReadModelBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://test-issuer";
    private static final String EMPLOYEES = "/api/erp/read-model/employees";
    private static final String APPROVALS = "/api/erp/read-model/approvals";
    private static final String DELEGATIONS = "/api/erp/read-model/delegations";

    private static final RSAKey RSA_KEY;
    @SuppressWarnings("resource")
    private static final MockWebServer JWKS = new MockWebServer();

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("mono-793-readmodel").generate();
            String jwks = "{\"keys\":[" + RSA_KEY.toPublicJWK().toJSONString() + "]}";
            JWKS.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse().setHeader("Content-Type", "application/json").setBody(jwks);
                }
            });
            JWKS.start();
        } catch (Exception e) {
            throw new IllegalStateException("business-role gate fixture failed to start", e);
        }
    }

    @DynamicPropertySource
    static void oauthProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("erpplatform.oauth2.allowed-issuers", () -> ISSUER);
        registry.add("erpplatform.oauth2.required-tenant-id", () -> "erp");
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryEmployeeOrgViewUseCase employees;
    @MockitoBean
    QueryApprovalFactUseCase approvals;
    @MockitoBean
    QueryDelegationFactUseCase delegations;

    @BeforeEach
    void emptyPages() {
        lenient().when(employees.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
        lenient().when(approvals.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
        lenient().when(delegations.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
    }

    private static String bearer(String tenantId, Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject("reader-1")
                    .issuer(ISSUER)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .claim("tenant_id", tenantId);
            claims.forEach(builder::claim);
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(), builder.build());
            jwt.sign(new RSASSASigner(RSA_KEY));
            return "Bearer " + jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign the test token", e);
        }
    }

    /** A customer-tenant operator token as auth-service mints it (entitled_domains rides every assume token). */
    private static String operator(List<String> roles) {
        return bearer("erp", Map.of("roles", roles, "entitled_domains", List.of("erp")));
    }

    private void expect(String auth, int employeesStatus, int approvalsStatus) throws Exception {
        mockMvc.perform(get(EMPLOYEES).header("Authorization", auth)).andExpect(status().is(employeesStatus));
        mockMvc.perform(get(APPROVALS).header("Authorization", auth)).andExpect(status().is(approvalsStatus));
        mockMvc.perform(get(DELEGATIONS).header("Authorization", auth)).andExpect(status().is(approvalsStatus));
    }

    @Test
    @DisplayName("🔴 793 Q6: masterdata read set → employees 200 · approvals / delegations 403")
    void masterdataReadSet() throws Exception {
        expect(operator(List.of("ERP_OPERATOR", "ERP_MASTERDATA_READ")), 200, 403);
    }

    @Test
    @DisplayName("🔴 793 Q6: approval set → approvals / delegations 200 · employees 403")
    void approvalSet() throws Exception {
        expect(operator(List.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE")), 403, 200);
    }

    @Test
    @DisplayName("🔴 793 AC-1: ERP_OPERATOR alone → all three 403")
    void entryRoleAlone() throws Exception {
        expect(operator(List.of("ERP_OPERATOR")), 403, 403);
    }

    @Test
    @DisplayName("🔴 793 Q1: subscription only (entitled_domains ∋ erp, no role) → all three 403 PERMISSION_DENIED")
    void subscriptionOnly() throws Exception {
        String auth = bearer("erp", Map.of("entitled_domains", List.of("erp")));
        expect(auth, 403, 403);
        mockMvc.perform(get(EMPLOYEES).header("Authorization", auth))
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("793 AC-2 control: legacy-compat token → all three 200")
    void legacyCompat() throws Exception {
        expect(operator(List.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
                "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE")), 200, 200);
    }

    @Test
    @DisplayName("793 control: workload scope erp.read → all three 200 · tenant_id='*' (no role) → all three 200")
    void workloadAndWildcard() throws Exception {
        expect(bearer("erp", Map.of("scope", "erp.read")), 200, 200);
        expect(bearer("*", Map.of()), 200, 200);
    }
}
