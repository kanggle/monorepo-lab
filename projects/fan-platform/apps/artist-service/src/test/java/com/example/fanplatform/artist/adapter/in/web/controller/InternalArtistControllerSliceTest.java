package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.port.in.CheckArtistAccountUseCase;
import com.example.fanplatform.artist.testsupport.JwtTestHelper;
import com.example.fanplatform.artist.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link InternalArtistController} — param binding
 * ({@code accountId}/{@code tenantId} 1:1 to {@link CheckArtistAccountUseCase}),
 * the {@code exists} boolean response shape, and the workload-identity
 * security chain (200 / 403 / 401) that {@link SliceTestSecurityConfig} now
 * mirrors from production (TASK-FAN-BE-045 AC-6, ADR-004 A).
 */
@WebMvcTest(controllers = InternalArtistController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class InternalArtistControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CheckArtistAccountUseCase checkArtistAccountUseCase;

    private String workloadBearer() {
        return "Bearer " + jwt.signWorkloadToken("community-service-client");
    }

    @Test
    @DisplayName("workload token, account exists in tenant -> 200 { exists: true }; query params bind by NAME, not position")
    void exists_true_bindsParamsByName() throws Exception {
        when(checkArtistAccountUseCase.isArtistAccount(eq("acc-1"), eq("fan-platform")))
                .thenReturn(true);

        // Params supplied in the REVERSE of the method signature's (accountId, tenantId)
        // declaration order: if binding were positional rather than name-based, this
        // would call isArtistAccount("fan-platform", "acc-1") and the stub above would
        // not match, failing the test with an UnnecessaryStubbingException / wrong result.
        mockMvc.perform(get("/internal/artists/exists")
                        .header("Authorization", workloadBearer())
                        .param("tenantId", "fan-platform")
                        .param("accountId", "acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    @DisplayName("workload token, no such artist account -> 200 { exists: false } (NOT an error status)")
    void exists_false_isNotAnErrorStatus() throws Exception {
        when(checkArtistAccountUseCase.isArtistAccount(eq("acc-2"), eq("fan-platform")))
                .thenReturn(false);

        mockMvc.perform(get("/internal/artists/exists")
                        .header("Authorization", workloadBearer())
                        .param("accountId", "acc-2")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    @DisplayName("missing required query param (tenantId) -> 400 VALIDATION_ERROR")
    void missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/internal/artists/exists")
                        .header("Authorization", workloadBearer())
                        .param("accountId", "acc-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("no token on /internal/** -> 401")
    void noToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/artists/exists")
                        .param("accountId", "acc-1")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a valid END-USER token (no artist.read scope) on /internal/** -> 403, NOT 200")
    void endUserToken_isForbiddenNotOk() throws Exception {
        // The load-bearing case: proves the workload discriminator actually
        // discriminates — an ordinary logged-in fan's token must not reach the
        // use case at all.
        mockMvc.perform(get("/internal/artists/exists")
                        .header("Authorization", "Bearer " + jwt.signFanToken("fan-1"))
                        .param("accountId", "acc-1")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("🔴 a token carrying fan-platform.artist.read (the END-USER resource scope) is REFUSED, not admitted")
    void fanResourceScope_isRefused_notTheMachineScope() throws Exception {
        // fan-platform.artist.read is the resource scope IAM migration V0030 grants the
        // fan web client, and the demo seed requests it on an ordinary user token.
        // WorkloadIdentityAuthoritiesConverter.REQUIRED_WORKLOAD_SCOPE gates on the
        // DIFFERENT machine-scope family ("artist.read"). The two scope strings differ
        // only by a "fan-platform." prefix; if the discriminator ever keyed on the wrong
        // one, every logged-in fan would clear ROLE_INTERNAL. Pinned here so a future
        // edit cannot blur the distinction.
        mockMvc.perform(get("/internal/artists/exists")
                        .header("Authorization", "Bearer " + jwt.signFanResourceScopedToken("fan-1"))
                        .param("accountId", "acc-1")
                        .param("tenantId", "fan-platform"))
                .andExpect(status().isForbidden());
    }
}
