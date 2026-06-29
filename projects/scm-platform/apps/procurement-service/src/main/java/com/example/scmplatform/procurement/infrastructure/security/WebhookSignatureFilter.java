package com.example.scmplatform.procurement.infrastructure.security;

import com.example.scmplatform.procurement.presentation.dto.ApiErrorBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Verifies the HMAC-SHA256 signature, timestamp freshness, and replay nonce of
 * inbound supplier webhooks BEFORE the request reaches the controllers.
 *
 * <p>This is a servlet filter (runs before the {@code DispatcherServlet}), so
 * the {@code @ExceptionHandler} advice does not apply to it — on failure it
 * writes the 401 {@link ApiErrorBody} JSON directly.
 *
 * <p>Only {@code POST} requests under {@code /api/procurement/webhooks/} are
 * inspected; everything else passes straight through. The request body is
 * wrapped in a {@link CachedBodyHttpServletRequestWrapper} so the controller can
 * still read {@code @RequestBody} after the filter has read it for the HMAC.
 */
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private static final String WEBHOOK_PATH_PREFIX = "/api/procurement/webhooks/";
    private static final String SIGNATURE_HEADER = "X-Supplier-Signature";
    private static final String TIMESTAMP_HEADER = "X-Supplier-Timestamp";

    private final WebhookSignatureVerifier verifier;
    private final ObjectMapper objectMapper;

    public WebhookSignatureFilter(WebhookSignatureVerifier verifier, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())
                || !request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequestWrapper wrapped = new CachedBodyHttpServletRequestWrapper(request);
        String signature = request.getHeader(SIGNATURE_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);

        try {
            verifier.verify(wrapped.getCachedBody(), timestamp, signature);
        } catch (WebhookVerificationException e) {
            writeUnauthorized(response, e.getCode());
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiErrorBody.of("UNAUTHORIZED", reason)));
    }
}
