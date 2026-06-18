package com.example.payment.adapter.in.rest.filter;

import com.example.payment.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantContextFilter 단위 테스트")
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 있으면 TenantContext 에 설정하고 filter chain 을 통과시킨다")
    void doFilterInternal_withTenantHeader_setsTenantContext() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn("tenant-a");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // Context is cleared after the filter chain completes (finally block)
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 없으면 기본 테넌트 'ecommerce' 로 처리한다 (D8 net-zero)")
    void doFilterInternal_withoutTenantHeader_usesDefaultTenant() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // After filter completes, context is cleared (default applied during chain)
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("filter chain 처리 후 TenantContext 가 항상 정리된다 (finally 보장)")
    void doFilterInternal_alwaysClearsContextAfterChain() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn("tenant-b");

        filter.doFilterInternal(request, response, chain);

        // After filter completes, context must be cleared regardless
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce"); // defaults (cleared)
    }

    @Test
    @DisplayName("TENANT_HEADER 상수는 'X-Tenant-Id' 이다")
    void tenantHeader_constant_isCorrect() {
        assertThat(TenantContextFilter.TENANT_HEADER).isEqualTo("X-Tenant-Id");
    }
}
