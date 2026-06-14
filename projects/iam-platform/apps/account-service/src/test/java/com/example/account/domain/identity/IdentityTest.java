package com.example.account.domain.identity;

import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Identity 도메인 (ADR-MONO-034 U1-A 중앙 신원 레지스트리)")
class IdentityTest {

    @Test
    @DisplayName("create — fresh UUID identityId 생성 + 이메일 정규화 + 상태 ACTIVE")
    void create_generatesFreshIdentity() {
        Identity identity = Identity.create(TenantId.FAN_PLATFORM, "  Person@Example.COM ");

        assertThat(identity.getIdentityId()).isNotBlank();
        assertThat(identity.getTenantId()).isEqualTo(TenantId.FAN_PLATFORM);
        assertThat(identity.getPrimaryEmail()).isEqualTo("person@example.com"); // normalized
        assertThat(identity.getStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(identity.getVersion()).isZero();
        assertThat(identity.getCreatedAt()).isNotNull();
        assertThat(identity.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create — 두 identity 의 identityId 는 서로 다르다 (per-person 고유)")
    void create_uniqueIds() {
        Identity a = Identity.create(TenantId.FAN_PLATFORM, "a@example.com");
        Identity b = Identity.create(TenantId.FAN_PLATFORM, "b@example.com");

        assertThat(a.getIdentityId()).isNotEqualTo(b.getIdentityId());
    }

    @Test
    @DisplayName("create — null tenant → 거부")
    void create_nullTenant_rejected() {
        assertThatThrownBy(() -> Identity.create(null, "x@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create — 잘못된 이메일 형식 → 거부")
    void create_invalidEmail_rejected() {
        assertThatThrownBy(() -> Identity.create(TenantId.FAN_PLATFORM, "not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reconstitute — 영속 상태 그대로 복원 (mapper 라운드트립)")
    void reconstitute_roundTrip() {
        Instant created = Instant.parse("2026-06-14T00:00:00Z");
        Instant updated = Instant.parse("2026-06-14T01:00:00Z");

        Identity identity = Identity.reconstitute(
                "id-123", TenantId.FAN_PLATFORM, "kept@example.com",
                IdentityStatus.INACTIVE, created, updated, 7);

        assertThat(identity.getIdentityId()).isEqualTo("id-123");
        assertThat(identity.getPrimaryEmail()).isEqualTo("kept@example.com");
        assertThat(identity.getStatus()).isEqualTo(IdentityStatus.INACTIVE);
        assertThat(identity.getCreatedAt()).isEqualTo(created);
        assertThat(identity.getUpdatedAt()).isEqualTo(updated);
        assertThat(identity.getVersion()).isEqualTo(7);
    }
}
