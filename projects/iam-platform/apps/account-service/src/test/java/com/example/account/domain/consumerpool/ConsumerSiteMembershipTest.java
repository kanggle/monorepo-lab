package com.example.account.domain.consumerpool;

import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-BE-614 — 소비자 사이트 멤버십 · 소비자 사이트 판정")
class ConsumerSiteMembershipTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    @DisplayName("가입 = 그 사이트 이용 동의 → ACTIVE, consented_at = 가입 시각")
    void joinOnSignup_isActiveAndConsentedAtSignup() {
        ConsumerSiteMembership m = ConsumerSiteMembership.joinOnSignup("acc-1", new TenantId("ecommerce"), NOW);

        assertThat(m.isActive()).isTrue();
        assertThat(m.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.ACTIVE);
        assertThat(m.getConsentedAt()).isEqualTo(NOW);
        assertThat(m.getSiteTenantId().value()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("data-model.md: site_tenant_id 가 consumer-pool 이면 안 된다(애플리케이션 검사)")
    void siteCannotBeThePool() {
        assertThatThrownBy(() -> ConsumerSiteMembership.joinOnSignup("acc-1", TenantId.CONSUMER_POOL, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumer-pool");
    }

    @Test
    @DisplayName("소비자 사이트 = B2C_CONSUMER 중 consumer-pool 제외 · B2B 는 아니다")
    void consumerSite_isB2cOtherThanThePool() {
        assertThat(tenant("fan-platform", TenantType.B2C_CONSUMER).isConsumerSite()).isTrue();
        assertThat(tenant("ecommerce", TenantType.B2C_CONSUMER).isConsumerSite()).isTrue();
        assertThat(tenant("consumer-pool", TenantType.B2C_CONSUMER).isConsumerSite()).isFalse();
        assertThat(tenant("wms", TenantType.B2B_ENTERPRISE).isConsumerSite()).isFalse();
        assertThat(TenantId.CONSUMER_POOL.isConsumerPool()).isTrue();
        assertThat(TenantId.FAN_PLATFORM.isConsumerPool()).isFalse();
    }

    private static Tenant tenant(String id, TenantType type) {
        return Tenant.reconstitute(new TenantId(id), id, type, TenantStatus.ACTIVE, NOW, NOW);
    }
}
