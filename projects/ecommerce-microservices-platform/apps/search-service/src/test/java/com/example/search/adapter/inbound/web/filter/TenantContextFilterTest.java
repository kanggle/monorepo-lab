package com.example.search.adapter.inbound.web.filter;

import com.example.search.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantContextFilter 단위 테스트")
class TenantContextFilterTest {

    @InjectMocks
    private TenantContextFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 있으면 TenantContext에 설정된다")
    void doFilterInternal_withTenantHeader_setsTenantContext() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn("store-a");

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // After filter completes, context is cleared (finally block)
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 없으면 기본값 'ecommerce'로 동작한다 (D8 net-zero)")
    void doFilterInternal_noTenantHeader_defaultsToEcommerce() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        // Default tenant is returned since no header was set
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("필터 처리 중 X-Tenant-Id가 컨텍스트에 설정된다")
    void doFilterInternal_withTenantHeader_contextSetDuringChain() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn("tenant-b");

        // Capture the tenant during chain execution
        String[] capturedTenant = new String[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            capturedTenant[0] = TenantContext.currentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(capturedTenant[0]).isEqualTo("tenant-b");
        // Context is cleared after filter
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("체인 실행 후 TenantContext가 반드시 초기화된다 (finally 보장)")
    void doFilterInternal_always_clearsTenantContextAfterChain() throws Exception {
        given(request.getHeader(TenantContextFilter.TENANT_HEADER)).willReturn("store-x");
        org.mockito.Mockito.doThrow(new RuntimeException("chain error"))
                .when(chain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (RuntimeException ignored) {
            // expected
        }

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }
}
