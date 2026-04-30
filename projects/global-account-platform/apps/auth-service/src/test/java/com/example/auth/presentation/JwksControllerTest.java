package com.example.auth.presentation;

import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.jwt.JwksEndpointProvider;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JwksController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class JwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwksEndpointProvider jwksEndpointProvider;

    @Test
    @DisplayName("GET /internal/auth/jwks returns JWKS JSON")
    void jwksEndpoint() throws Exception {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("kid", "test-key-001");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("n", "test-modulus");
        key.put("e", "AQAB");

        Map<String, Object> jwks = new LinkedHashMap<>();
        jwks.put("keys", List.of(key));

        when(jwksEndpointProvider.getJwks()).thenReturn(jwks);

        mockMvc.perform(get("/internal/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value("test-key-001"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));
    }
}
