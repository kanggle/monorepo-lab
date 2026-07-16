package com.example.erp.masterdata.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer integration test pinning the <strong>machine-token data-scope</strong> boundary
 * (TASK-ERP-BE-029). Real {@code SecurityFilterChain} + {@code RoleScopeAuthorizationAdapter},
 * valid {@code tenant_id=erp} tokens throughout — so tenant + read/write scope are out of the
 * way and the only variable is whether the caller carries an {@code org_scope} (data-scope).
 *
 * <p>The gap this closes was a dead fallback in {@code ActorContextJwtAuthenticationConverter}
 * intended to give {@code client_credentials} machine tokens platform-wide data-scope; its
 * predicate ({@code roles.contains("client_credentials")}) could never match a real IAM token,
 * so a machine token lands with an EMPTY data-scope and is fail-closed on department-scoped
 * writes. No HTTP-layer test exercised a write with a machine-shaped signed token before, so
 * the documented flow's real behaviour was never pinned. These assertions pin it: reads and
 * unscoped writes work; department-scoped writes require an {@code org_scope}.
 */
@AutoConfigureMockMvc
class MachineTokenDataScopeHttpIntegrationTest extends AbstractMasterdataIntegrationTest {

    private static final String BASE = "/api/erp/masterdata";
    private static RSAKey rsaKey;

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void publishSigningKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-erp-be029").generate();
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");
    }

    /**
     * A signed erp-tenant token. {@code orgScope == null} → NO org_scope claim (the machine /
     * client_credentials shape); a non-null list is emitted as the {@code org_scope} claim (the
     * operator shape).
     */
    private String token(String scope, List<String> orgScope) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("machine-1")
                .issuer("http://test-issuer")
                .claim("tenant_id", "erp")
                .claim("scope", scope)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        if (orgScope != null) claims.claim("org_scope", orgScope);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    // ---- machine token (scope only, NO org_scope): unscoped writes + reads OK -------------------

    @Test
    @DisplayName("machine erp.write (no org_scope) → POST /departments ROOT (parentId=null) → 201")
    void machineTokenCreatesRootDepartment() throws Exception {
        mockMvc.perform(post(BASE + "/departments")
                        .header("Authorization", "Bearer " + token("erp.write", null))
                        .header("Idempotency-Key", "be029-root")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BE029-R\",\"name\":\"Root\",\"parentId\":null,\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("machine erp.write (no org_scope) → POST /job-grades (target null) → 201")
    void machineTokenCreatesJobGrade() throws Exception {
        mockMvc.perform(post(BASE + "/job-grades")
                        .header("Authorization", "Bearer " + token("erp.write", null))
                        .header("Idempotency-Key", "be029-jg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BE029-JG\",\"name\":\"Staff\",\"displayOrder\":1,\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("machine erp.read (no org_scope) → GET /employees (list, target null) → 200")
    void machineTokenReadsList() throws Exception {
        mockMvc.perform(get(BASE + "/employees")
                        .header("Authorization", "Bearer " + token("erp.read", null)))
                .andExpect(status().isOk());
    }

    // ---- machine token: department-scoped writes are fail-closed (the finding) ------------------

    @Test
    @DisplayName("machine erp.write (no org_scope) → POST /employees (departmentId set) → 403 DATA_SCOPE_FORBIDDEN")
    void machineTokenCannotCreateScopedEmployee() throws Exception {
        mockMvc.perform(post(BASE + "/employees")
                        .header("Authorization", "Bearer " + token("erp.write", null))
                        .header("Idempotency-Key", "be029-emp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNumber\":\"E1\",\"name\":\"Jane\",\"departmentId\":\"dept-x\","
                                + "\"costCenterId\":\"cc-x\",\"jobGradeId\":\"jg-x\",\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DATA_SCOPE_FORBIDDEN"));
    }

    @Test
    @DisplayName("machine erp.write (no org_scope) → POST /departments CHILD (parentId set) → 403 DATA_SCOPE_FORBIDDEN")
    void machineTokenCannotCreateChildDepartment() throws Exception {
        // A root department (created by the machine token, target null) is the parent; adding a
        // child targets that parent department → data-scope consulted → empty → fail-closed.
        String createRoot = mockMvc.perform(post(BASE + "/departments")
                        .header("Authorization", "Bearer " + token("erp.write", null))
                        .header("Idempotency-Key", "be029-child-root")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BE029-CR\",\"name\":\"ChildRoot\",\"parentId\":null,\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String rootId = createRoot.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post(BASE + "/departments")
                        .header("Authorization", "Bearer " + token("erp.write", null))
                        .header("Idempotency-Key", "be029-child")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BE029-C\",\"name\":\"Child\",\"parentId\":\"" + rootId + "\",\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DATA_SCOPE_FORBIDDEN"));
    }

    // ---- operator token WITH org_scope=["*"]: the department-scoped write path works -----------

    @Test
    @DisplayName("operator erp.write + org_scope=[\"*\"] → POST /departments CHILD → 201 (org_scope unlocks the scoped write)")
    void operatorWithPlatformOrgScopeCreatesChildDepartment() throws Exception {
        String createRoot = mockMvc.perform(post(BASE + "/departments")
                        .header("Authorization", "Bearer " + token("erp.write", List.of("*")))
                        .header("Idempotency-Key", "be029-op-root")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BE029-OR\",\"name\":\"OpRoot\",\"parentId\":null,\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String rootId = createRoot.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post(BASE + "/departments")
                        .header("Authorization", "Bearer " + token("erp.write", List.of("*")))
                        .header("Idempotency-Key", "be029-op-child")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BE029-OC\",\"name\":\"OpChild\",\"parentId\":\"" + rootId + "\",\"effectiveFrom\":\"2026-07-15\"}"))
                .andExpect(status().isCreated());
    }
}
