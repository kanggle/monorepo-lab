package com.example.libs.payment.portone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Verifies the HMAC signature of an incoming PortOne V2 webhook (ADR-MONO-057, D2). Vendor-specific
 * and reusable — any future PortOne recurring consumer gets it for free. Deliberately separate from
 * {@link PortOnePaymentAdapter}: it needs no {@code RestClient}, no API secret, no bean wiring — it
 * is a pure function of (webhook secret, raw body, headers).
 *
 * <p><b>ONE job: prove the message is authentically from PortOne.</b> PortOne V2 webhooks follow
 * the <a href="https://www.standardwebhooks.com/">Standard Webhooks</a> convention:
 * HMAC-SHA256 over the signed content {@code "{webhook-id}.{webhook-timestamp}.{rawBody}"}, with
 * the three headers {@code webhook-id}, {@code webhook-timestamp}, {@code webhook-signature}, and a
 * base64 secret conventionally prefixed {@code whsec_}. The {@code webhook-signature} header is a
 * space-separated list of {@code v1,<base64-hmac>} tokens (a payload may carry several during a
 * secret rotation); a match against any is accepted.
 *
 * <p><b>Trust boundary — this verifier does NOT trust the payload's money fields.</b> On a valid
 * signature it parses ONLY the minimal routing fields (a {@code paymentId} and an event type) and
 * returns them. It deliberately does NOT read/return the amount or status — per ADR-MONO-057 §D3 a
 * webhook is a durability backstop, only a trigger for the consumer to re-verify the real outcome
 * via {@link PortOnePaymentAdapter#verify}. Never treat the returned event as proof money moved.
 *
 * <p><b>Never throws — absence means "reject".</b> Missing/malformed headers, a malformed secret, a
 * signature mismatch, or a tampered body all yield {@link Optional#empty()} (the caller responds
 * 401). Only an authentic message yields a present {@link VerifiedWebhookEvent}.
 *
 * <p><b>NOT live-verified.</b> Like {@link PortOnePaymentAdapter#chargeBillingKey}, this is a
 * best-effort implementation of PortOne's documented webhook convention. The exact header names,
 * signature-token format, secret prefix, and HMAC algorithm MUST be reconfirmed against PortOne's
 * current V2 webhook reference before this is wired to a live endpoint.
 */
public final class PortOneWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(PortOneWebhookVerifier.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SECRET_PREFIX = "whsec_";
    private static final String HEADER_ID = "webhook-id";
    private static final String HEADER_TIMESTAMP = "webhook-timestamp";
    private static final String HEADER_SIGNATURE = "webhook-signature";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The minimal, trustworthy result of a verified webhook: the fields needed to route the event
     * to a consumer's reconcile step. Deliberately carries no amount/status (see class javadoc).
     *
     * @param paymentId the PortOne payment reference the event concerns (nullable if the authentic
     *                  body carried none — the signature was still valid)
     * @param eventType the PortOne event type (e.g. {@code "Transaction.Paid"}; nullable if absent)
     */
    public record VerifiedWebhookEvent(String paymentId, String eventType) {}

    /**
     * Verify a webhook's signature and, if authentic, extract its routing fields.
     *
     * @param secret  the configured webhook secret (base64, conventionally {@code whsec_}-prefixed)
     * @param rawBody the exact raw request body bytes as received, decoded to a String — signature
     *                verification requires the untouched body (re-serialising the parsed JSON would
     *                change the bytes and break the HMAC)
     * @param headers the request headers (looked up case-insensitively)
     * @return the parsed {@link VerifiedWebhookEvent} when the signature is valid; {@link Optional#empty()}
     *         when any header/secret is missing or malformed, or the signature does not match.
     */
    public Optional<VerifiedWebhookEvent> verify(String secret, String rawBody, Map<String, String> headers) {
        if (secret == null || rawBody == null || headers == null) {
            return Optional.empty();
        }
        String id = header(headers, HEADER_ID);
        String timestamp = header(headers, HEADER_TIMESTAMP);
        String signatureHeader = header(headers, HEADER_SIGNATURE);
        if (id == null || timestamp == null || signatureHeader == null) {
            log.warn("PortOne webhook: missing signature header(s) -> reject");
            return Optional.empty();
        }

        final String expectedSignature;
        try {
            byte[] key = decodeSecret(secret);
            String signedContent = id + "." + timestamp + "." + rawBody;
            expectedSignature = base64Hmac(key, signedContent);
        } catch (Exception e) {
            // Malformed secret / crypto failure — cannot verify, so reject (never throw).
            log.warn("PortOne webhook: signature computation failed ({}) -> reject", e.getClass().getSimpleName());
            return Optional.empty();
        }

        if (!signatureMatches(signatureHeader, expectedSignature)) {
            log.warn("PortOne webhook: signature mismatch (tampered body/forged/wrong secret) -> reject");
            return Optional.empty();
        }

        // Authentic. Extract ONLY routing fields — never the amount/status (re-verify via verify()).
        return Optional.of(parseRoutingFields(rawBody));
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static byte[] decodeSecret(String secret) {
        String raw = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        return Base64.getDecoder().decode(raw);
    }

    private static String base64Hmac(byte[] key, String signedContent) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        byte[] digest = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    private static boolean signatureMatches(String signatureHeader, String expectedSignature) {
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        // The header is a space-separated list of `version,signature` tokens (Standard Webhooks).
        for (String token : signatureHeader.split(" ")) {
            if (token.isBlank()) {
                continue;
            }
            int comma = token.indexOf(',');
            String candidate = comma >= 0 ? token.substring(comma + 1) : token;
            // Constant-time comparison — MessageDigest.isEqual does not short-circuit on length.
            if (MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), expectedBytes)) {
                return true;
            }
        }
        return false;
    }

    private static VerifiedWebhookEvent parseRoutingFields(String rawBody) {
        try {
            JsonNode root = MAPPER.readTree(rawBody);
            // PortOne Standard-Webhooks payload: { "type": "...", "data": { "paymentId": "..." } }.
            // Fall back to a top-level paymentId for convention drift; both nullable.
            String eventType = text(root.path("type"));
            String paymentId = text(root.path("data").path("paymentId"));
            if (paymentId == null) {
                paymentId = text(root.path("paymentId"));
            }
            return new VerifiedWebhookEvent(paymentId, eventType);
        } catch (Exception e) {
            // Signature was valid, so the message is authentic — but the body is not the JSON shape
            // we expect. Return an empty-fielded authentic event rather than pretending it forged.
            log.warn("PortOne webhook: authentic body but unparseable routing fields ({})",
                    e.getClass().getSimpleName());
            return new VerifiedWebhookEvent(null, null);
        }
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
