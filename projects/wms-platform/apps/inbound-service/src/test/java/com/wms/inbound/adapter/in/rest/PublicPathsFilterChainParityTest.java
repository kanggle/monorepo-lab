package com.wms.inbound.adapter.in.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.application.port.in.CancelAsnUseCase;
import com.wms.inbound.application.port.in.CloseAsnUseCase;
import com.wms.inbound.application.port.in.QueryAsnUseCase;
import com.wms.inbound.application.port.in.ReceiveAsnUseCase;
import com.wms.inbound.config.PublicPaths;
import com.wms.inbound.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real-{@code SecurityFilterChain} classification-parity check for
 * inbound-service's {@link PublicPaths} adoption (ADR-MONO-058 § D5,
 * TASK-BE-570 AC-2).
 *
 * <p>This is the load-bearing regression net: {@code PublicPaths.isPublic(...)}
 * (see {@code PublicPathsTest}) is a brand-new method that never gated the
 * filter chain before this task — the actual regression surface is the
 * {@code .requestMatchers(...)} call site, which changed from a literal
 * {@code PUBLIC_PATHS} array to {@link PublicPaths#asAntPatterns()}. Every
 * request below is driven through the real {@link SecurityConfig} bean,
 * including the {@code /webhooks/erp/asn} entry inbound-service alone
 * carries.
 */
@WebMvcTest(controllers = AsnController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
class PublicPathsFilterChainParityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiveAsnUseCase receiveAsnUseCase;

    @MockitoBean
    private CancelAsnUseCase cancelAsnUseCase;

    @MockitoBean
    private CloseAsnUseCase closeAsnUseCase;

    @MockitoBean
    private QueryAsnUseCase queryAsnUseCase;

    @Test
    @DisplayName("asAntPatterns() 의 모든 엔트리가 인증 없이 통과한다 (webhook 포함, AC-2)")
    void everyGeneratedAntPattern_bypassesAuth() throws Exception {
        for (String probe : probePathsFor(PublicPaths.asAntPatterns())) {
            mockMvc.perform(get(probe)).andExpect(result -> {
                int status = result.getResponse().getStatus();
                assert status != 401 : probe + " must bypass auth, got " + status;
            });
        }
    }

    @Test
    @DisplayName("actuator/health 서브패스(prefix 경계)도 인증 없이 통과한다 (AC-2, edge case)")
    void healthSubPath_bypassesAuth() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(result -> {
            int status = result.getResponse().getStatus();
            assert status != 401 : "actuator/health/liveness must bypass auth, got " + status;
        });
    }

    @Test
    @DisplayName("보호된 API 경로는 여전히 인증을 요구한다 (AC-2, 대조군)")
    void protectedApiRoute_stillRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/inbound/asns"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    private static List<String> probePathsFor(String[] antPatterns) {
        return List.of(antPatterns).stream()
                .map(p -> p.endsWith("/**") ? p.substring(0, p.length() - 2) : p)
                .toList();
    }
}
