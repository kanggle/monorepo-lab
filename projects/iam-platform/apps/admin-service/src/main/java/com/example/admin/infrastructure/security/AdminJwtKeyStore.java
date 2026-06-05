package com.example.admin.infrastructure.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses a map of {@code kid → PKCS#8 PEM private key} and exposes the
 * derived public keys. Fails fast if an RSA key is smaller than 2048 bits
 * or malformed.
 *
 * <p>Used by {@link JwtSigner} (private key for active kid) and the JWKS
 * endpoint (all public keys).
 */
public final class AdminJwtKeyStore {

    private static final int MIN_RSA_KEY_BITS = 2048;

    private final Map<String, RSAPrivateCrtKey> privateKeys;
    private final Map<String, RSAPublicKey> publicKeys;
    private final String activeKid;

    public AdminJwtKeyStore(Map<String, String> pemByKid, String activeKid) {
        Objects.requireNonNull(pemByKid, "pemByKid must not be null");
        Objects.requireNonNull(activeKid, "activeKid must not be null");
        if (pemByKid.isEmpty()) {
            throw new IllegalStateException("admin.jwt.signing-keys must not be empty");
        }
        if (!pemByKid.containsKey(activeKid)) {
            throw new IllegalStateException(
                    "admin.jwt.active-signing-kid=" + activeKid
                            + " is not present in admin.jwt.signing-keys");
        }

        Map<String, RSAPrivateCrtKey> priv = new LinkedHashMap<>();
        Map<String, RSAPublicKey> pub = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : pemByKid.entrySet()) {
            String kid = e.getKey();
            RSAPrivateCrtKey privateKey = parsePkcs8RsaPrivateKey(kid, e.getValue());
            if (privateKey.getModulus().bitLength() < MIN_RSA_KEY_BITS) {
                throw new IllegalStateException(
                        "RSA private key for kid=" + kid
                                + " must be at least " + MIN_RSA_KEY_BITS + " bits, got "
                                + privateKey.getModulus().bitLength());
            }
            priv.put(kid, privateKey);
            pub.put(kid, derivePublicKey(privateKey));
        }
        this.privateKeys = Map.copyOf(priv);
        this.publicKeys = Map.copyOf(pub);
        this.activeKid = activeKid;
    }

    public String activeKid() {
        return activeKid;
    }

    public RSAPrivateCrtKey activePrivateKey() {
        return privateKeys.get(activeKid);
    }

    public Optional<PublicKey> publicKey(String kid) {
        return Optional.ofNullable(publicKeys.get(kid));
    }

    public Map<String, RSAPublicKey> publicKeys() {
        return publicKeys;
    }

    private static RSAPrivateCrtKey parsePkcs8RsaPrivateKey(String kid, String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("PEM for kid=" + kid + " is blank");
        }
        String normalized = pem
                .replaceAll("-----BEGIN [A-Z0-9 ]+-----", "")
                .replaceAll("-----END [A-Z0-9 ]+-----", "")
                .replaceAll("\\s", "");
        byte[] der;
        try {
            der = Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "PEM for kid=" + kid + " is not valid base64", ex);
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey key = kf.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey crt)) {
                throw new IllegalStateException(
                        "PEM for kid=" + kid + " is not an RSA PKCS#8 CRT key");
            }
            return crt;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException(
                    "Failed to parse PKCS#8 RSA key for kid=" + kid + ": " + ex.getMessage(), ex);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to derive RSA public key: " + ex.getMessage(), ex);
        }
    }
}
