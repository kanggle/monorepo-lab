package com.example.admin.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-176 — the {@code X-Operator-Reason} header is percent-decoded so
 * downstream controllers receive the operator's original (possibly non-ASCII,
 * e.g. Korean) audit reason. The console percent-encodes it because HTTP header
 * values are ByteStrings (a raw Korean header makes the browser/Node
 * {@code fetch()} throw before sending).
 */
class OperatorReasonDecodingFilterTest {

    private final OperatorReasonDecodingFilter filter = new OperatorReasonDecodingFilter();

    @Test
    void decode_returns_korean_text_from_percent_encoding() {
        // encodeURIComponent('테스트 1') === '%ED%85%8C%EC%8A%A4%ED%8A%B8%201'
        assertThat(OperatorReasonDecodingFilter.decode("%ED%85%8C%EC%8A%A4%ED%8A%B8%201"))
                .isEqualTo("테스트 1");
    }

    @Test
    void decode_roundtrips_ascii_with_spaces() {
        assertThat(OperatorReasonDecodingFilter.decode("policy%20violation"))
                .isEqualTo("policy violation");
    }

    @Test
    void decode_is_tolerant_of_malformed_escape() {
        // A lone '%' is not a valid escape — return the raw value, do not 500.
        assertThat(OperatorReasonDecodingFilter.decode("50% off")).isEqualTo("50% off");
    }

    @Test
    void filter_exposes_decoded_reason_to_downstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/operators");
        request.addHeader("X-Operator-Reason", "%ED%85%8C%EC%8A%A4%ED%8A%B8%201");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertThat(downstream.getHeader("X-Operator-Reason")).isEqualTo("테스트 1");
        // case-insensitive lookup also resolves to the decoded value
        assertThat(downstream.getHeader("x-operator-reason")).isEqualTo("테스트 1");
    }

    @Test
    void filter_passes_through_when_header_absent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/operators");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
        assertThat(downstream).isSameAs(request);
        assertThat(downstream.getHeader("X-Operator-Reason")).isNull();
    }

    @Test
    void filter_leaves_plain_ascii_reason_unwrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/operators");
        request.addHeader("X-Operator-Reason", "onboarding"); // no escapes → unchanged
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // No wrapping needed when decode is a no-op (same instance forwarded).
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(((HttpServletRequest) chain.getRequest()).getHeader("X-Operator-Reason"))
                .isEqualTo("onboarding");
    }
}
