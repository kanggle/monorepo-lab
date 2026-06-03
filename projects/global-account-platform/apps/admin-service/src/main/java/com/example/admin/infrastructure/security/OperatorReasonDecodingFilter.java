package com.example.admin.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

/**
 * TASK-MONO-176 — percent-decode the {@code X-Operator-Reason} header.
 *
 * <p>HTTP header values are ByteStrings (ISO-8859-1, byte ≤ 255), so a console
 * operator's free-form audit reason that contains non-Latin-1 text (e.g. Korean
 * "테스트 1") cannot be carried RAW — the browser/Node {@code fetch()} throws
 * {@code TypeError: Cannot convert argument to a ByteString …} before the
 * request is even sent, surfacing in the console as a generic "operators
 * unavailable". The console therefore now {@code encodeURIComponent}s the reason
 * (operators-api / accounts-api) so the wire header is pure ASCII; this filter
 * decodes it back to the original UTF-8 string for ALL {@code /api/admin/**}
 * controllers that read {@code @RequestHeader("X-Operator-Reason")} — a single
 * uniform decode point (no per-controller edits, future-proof).
 *
 * <p>Tolerant: a value with no percent-escapes round-trips unchanged
 * ({@code URLDecoder} only rewrites {@code %XX} and {@code +}; the console
 * encoder emits {@code %20} for space and never a raw {@code +}). A malformed
 * escape sequence falls back to the raw value rather than failing the request.
 *
 * <p>Registered as a plain servlet filter (NOT part of the Spring Security
 * chain) so it wraps the request independently of authentication ordering; the
 * wrapper propagates down the chain to Spring MVC argument resolution.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class OperatorReasonDecodingFilter extends OncePerRequestFilter {

    static final String REASON_HEADER = "X-Operator-Reason";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String raw = request.getHeader(REASON_HEADER);
        if (raw == null || raw.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String decoded = decode(raw);
        if (decoded.equals(raw)) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new DecodedReasonRequest(request, decoded), response);
    }

    /** Percent-decode (UTF-8); tolerant — malformed input returns the raw value. */
    static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** Request wrapper exposing the decoded {@code X-Operator-Reason} value. */
    private static final class DecodedReasonRequest extends HttpServletRequestWrapper {
        private final String decodedReason;

        DecodedReasonRequest(HttpServletRequest request, String decodedReason) {
            super(request);
            this.decodedReason = decodedReason;
        }

        @Override
        public String getHeader(String name) {
            if (REASON_HEADER.equalsIgnoreCase(name)) {
                return decodedReason;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (REASON_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(decodedReason));
            }
            return super.getHeaders(name);
        }
    }
}
