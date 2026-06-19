package com.example.auth.infrastructure.tenant;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.domain.tenant.TenantContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantTypeResolver} (TASK-BE-407).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TenantTypeResolverTest {

    @Mock
    private AccountServicePort accountServicePort;

    @InjectMocks
    private TenantTypeResolver resolver;

    @Test
    @DisplayName("핵심 회귀: non-fan B2C 테넌트(ecommerce) → B2C_CONSUMER 반환 (옛 하드코드는 B2B_ENTERPRISE 로 오분류)")
    void resolve_nonFanB2cTenant_returnsB2cConsumer() {
        when(accountServicePort.getTenantType("ecommerce")).thenReturn(Optional.of("B2C_CONSUMER"));

        assertThat(resolver.resolve("ecommerce")).isEqualTo("B2C_CONSUMER");
    }

    @Test
    @DisplayName("프리시드: fan-platform 은 포트 호출 없이 DEFAULT_TENANT_TYPE 반환")
    void resolve_defaultTenant_isPreSeeded_noPortCall() {
        String result = resolver.resolve(TenantContext.DEFAULT_TENANT_ID);

        assertThat(result).isEqualTo(TenantContext.DEFAULT_TENANT_TYPE);
        verify(accountServicePort, never()).getTenantType(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("캐시: 동일 tenantId 두 번 조회 시 포트는 1회만 호출")
    void resolve_cachesAfterFirstLookup() {
        when(accountServicePort.getTenantType("ecommerce")).thenReturn(Optional.of("B2C_CONSUMER"));

        assertThat(resolver.resolve("ecommerce")).isEqualTo("B2C_CONSUMER");
        assertThat(resolver.resolve("ecommerce")).isEqualTo("B2C_CONSUMER");

        verify(accountServicePort, times(1)).getTenantType("ecommerce");
    }

    @Test
    @DisplayName("미스 → 포트 1회 호출")
    void resolve_miss_callsPortOnce() {
        when(accountServicePort.getTenantType("wms")).thenReturn(Optional.of("B2B_ENTERPRISE"));

        assertThat(resolver.resolve("wms")).isEqualTo("B2B_ENTERPRISE");

        verify(accountServicePort, times(1)).getTenantType("wms");
    }

    @Test
    @DisplayName("미존재 테넌트(empty) → DEFAULT_TENANT_TYPE 폴백, 캐시하지 않음(다음 조회 시 재시도)")
    void resolve_unknownTenant_fallsBackToDefault_andDoesNotCache() {
        when(accountServicePort.getTenantType("ghost")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("ghost")).isEqualTo(TenantContext.DEFAULT_TENANT_TYPE);
        // Not cached: a second call re-queries the port (tenant may be provisioned later).
        assertThat(resolver.resolve("ghost")).isEqualTo(TenantContext.DEFAULT_TENANT_TYPE);

        verify(accountServicePort, times(2)).getTenantType("ghost");
    }

    @Test
    @DisplayName("account-service 장애 → AccountServiceUnavailableException 전파(폴백 금지)")
    void resolve_serviceUnavailable_propagates() {
        when(accountServicePort.getTenantType("ecommerce"))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertThatThrownBy(() -> resolver.resolve("ecommerce"))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @DisplayName("null/blank tenantId → DEFAULT_TENANT_TYPE, 포트 미호출")
    void resolve_blankTenantId_returnsDefault_noPortCall() {
        assertThat(resolver.resolve(null)).isEqualTo(TenantContext.DEFAULT_TENANT_TYPE);
        assertThat(resolver.resolve("  ")).isEqualTo(TenantContext.DEFAULT_TENANT_TYPE);
    }
}
