package com.example.auth.infrastructure.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides JWKS JSON for the /internal/auth/jwks endpoint.
 */
@Component
public class JwksEndpointProvider {

    private final RSAPublicKey rsaPublicKey;
    private final String kid;

    public JwksEndpointProvider(PublicKey publicKey, @Value("${auth.jwt.kid}") String kid) {
        this.rsaPublicKey = (RSAPublicKey) publicKey;
        this.kid = kid;
    }

    public Map<String, Object> getJwks() {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("kid", kid);
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("n", base64UrlEncode(rsaPublicKey.getModulus().toByteArray()));
        key.put("e", base64UrlEncode(rsaPublicKey.getPublicExponent().toByteArray()));

        Map<String, Object> jwks = new LinkedHashMap<>();
        jwks.put("keys", List.of(key));
        return jwks;
    }

    private static String base64UrlEncode(byte[] bytes) {
        // Strip leading zero byte if present (BigInteger encoding artifact)
        if (bytes.length > 0 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            bytes = stripped;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
