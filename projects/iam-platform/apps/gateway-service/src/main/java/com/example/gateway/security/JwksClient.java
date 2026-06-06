package com.example.gateway.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches JWKS from auth-service /internal/auth/jwks endpoint.
 */
@Slf4j
@Component
public class JwksClient {

    private final WebClient jwksWebClient;
    private final ObjectMapper objectMapper;

    public JwksClient(WebClient jwksWebClient, ObjectMapper objectMapper) {
        this.jwksWebClient = jwksWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches JWKS from auth-service and parses into a map of kid -> PublicKey.
     */
    public Mono<Map<String, PublicKey>> fetchJwks() {
        return jwksWebClient.get()
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::parseJwks)
                .doOnSuccess(keys -> log.info("JWKS fetched successfully, keys count: {}", keys.size()))
                .doOnError(e -> log.error("JWKS fetch failed: {}", e.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, PublicKey>> parseJwks(String jwksJson) {
        return Mono.fromCallable(() -> {
            Map<String, Object> jwks = objectMapper.readValue(jwksJson,
                    new TypeReference<Map<String, Object>>() {});
            List<Map<String, String>> keys = (List<Map<String, String>>) jwks.get("keys");
            Map<String, PublicKey> result = new LinkedHashMap<>();

            for (Map<String, String> key : keys) {
                String kid = key.get("kid");
                String kty = key.get("kty");
                if (!"RSA".equals(kty)) {
                    log.warn("Unsupported key type: {} for kid: {}", kty, kid);
                    continue;
                }

                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.get("n")));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.get("e")));
                RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
                result.put(kid, publicKey);
            }

            return result;
        });
    }
}
