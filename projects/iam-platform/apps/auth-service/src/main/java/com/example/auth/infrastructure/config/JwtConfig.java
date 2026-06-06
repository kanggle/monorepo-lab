package com.example.auth.infrastructure.config;

import com.example.security.jwt.JwtSigner;
import com.example.security.jwt.Rs256JwtSigner;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.security.password.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${auth.jwt.private-key-path}")
    private Resource privateKeyResource;

    @Value("${auth.jwt.public-key-path}")
    private Resource publicKeyResource;

    @Value("${auth.jwt.kid}")
    private String kid;

    @Bean
    public PrivateKey privateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = readPemKey(privateKeyResource);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    @Bean
    public PublicKey publicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = readPemKey(publicKeyResource);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    @Bean
    public JwtSigner jwtSigner(PrivateKey privateKey) {
        return new Rs256JwtSigner(privateKey, kid);
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new Argon2idPasswordHasher();
    }

    private byte[] readPemKey(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            String pem = new String(is.readAllBytes());
            pem = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(pem);
        }
    }
}
