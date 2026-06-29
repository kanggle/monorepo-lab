package com.example.scmplatform.procurement.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link WebhookSignatureFilter} using {@code MockHttpServletRequest}
 * / {@code MockHttpServletResponse} and a Mockito-mocked verifier.
 *
 * <p>Test count: 5
 */
class WebhookSignatureFilterTest {

    private static final String URL = "/api/procurement/webhooks/asn";
    private static final byte[] BODY = "{\"tenantId\":\"scm\"}".getBytes(StandardCharsets.UTF_8);

    private final WebhookSignatureVerifier verifier = mock(WebhookSignatureVerifier.class);
    // Mirror the Spring-managed ObjectMapper (JSR-310 registered) so the
    // Instant in ApiErrorBody serialises — the bean injected in production has
    // the module registered by Spring Boot's Jackson auto-configuration.
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final WebhookSignatureFilter filter = new WebhookSignatureFilter(verifier, objectMapper);

    private MockHttpServletRequest webhookPost() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL);
        req.setRequestURI(URL);
        req.setContent(BODY);
        req.addHeader("X-Supplier-Signature", "deadbeef");
        req.addHeader("X-Supplier-Timestamp", "1750000000");
        return req;
    }

    @Test
    @DisplayName("webhook POST + verifier OK → chain proceeds with a re-readable wrapped body")
    void verifierOkChainProceeds() throws Exception {
        MockHttpServletRequest req = webhookPost();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        // verifier called with the raw body + headers.
        verify(verifier).verify(BODY, "1750000000", "deadbeef");

        // The chain received the wrapped request, and its body is still readable.
        ArgumentCaptor<ServletRequest> captor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(captor.capture(), any());
        ServletRequest passed = captor.getValue();
        assertThat(passed).isInstanceOf(CachedBodyHttpServletRequestWrapper.class);
        byte[] reread = passed.getInputStream().readAllBytes();
        assertThat(reread).isEqualTo(BODY);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("verifier throws → 401 UNAUTHORIZED JSON with the reason, chain NOT invoked")
    void verifierThrowsWrites401() throws Exception {
        MockHttpServletRequest req = webhookPost();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new WebhookVerificationException("WEBHOOK_REPLAY_DETECTED"))
                .when(verifier).verify(any(), any(), any());

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentType()).contains("application/json");
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("WEBHOOK_REPLAY_DETECTED");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("non-webhook path → passthrough, verifier never called")
    void nonWebhookPathPassthrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/procurement/po");
        req.setRequestURI("/api/procurement/po");
        req.setContent(BODY);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(verifier, never()).verify(any(), any(), any());
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("GET on a webhook path → passthrough, verifier never called")
    void getOnWebhookPathPassthrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", URL);
        req.setRequestURI(URL);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(verifier, never()).verify(any(), any(), any());
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("missing signature/timestamp headers are forwarded as null to the verifier")
    void missingHeadersForwardedAsNull() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", URL);
        req.setRequestURI(URL);
        req.setContent(BODY);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(verifier).verify(BODY, null, null);
        verify(chain, times(1)).doFilter(any(), any());
        Mockito.verifyNoMoreInteractions(verifier);
    }
}
