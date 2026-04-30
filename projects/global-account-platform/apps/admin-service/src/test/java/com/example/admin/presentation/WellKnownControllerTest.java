package com.example.admin.presentation;

import com.example.admin.infrastructure.security.AdminJwtKeyStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the JWKS endpoint:
 *  - unauthenticated 200 OK (no SecurityContext required)
 *  - standard JWKS shape {@code {keys:[{kty,use,alg,kid,n,e}, ...]}}
 *  - active kid present
 */
@WebMvcTest(controllers = WellKnownController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
        })
@Import(WellKnownControllerTest.TestKeysConfig.class)
class WellKnownControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returns_jwks_document_unauthenticated() throws Exception {
        mockMvc.perform(get("/.well-known/admin/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("v1"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
    }

    @TestConfiguration
    static class TestKeysConfig {
        @Bean
        AdminJwtKeyStore adminJwtKeyStore() throws Exception {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            return new AdminJwtKeyStore(Map.of("v1", pem), "v1");
        }
    }
}
