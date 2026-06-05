package com.example.account.domain.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenantId 값 객체 단위 테스트")
class TenantIdTest {

    // ── valid slugs ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("FAN_PLATFORM 상수는 'fan-platform' 값을 갖는다")
    void fanPlatformConstant_hasExpectedValue() {
        assertThat(TenantId.FAN_PLATFORM.value()).isEqualTo("fan-platform");
    }

    @ParameterizedTest(name = "\"{0}\" is valid")
    @ValueSource(strings = {
            "fan-platform",   // canonical tenant
            "wms",            // minimal length (3 chars, but 'w' + 'ms' satisfies pattern)
            "ab",             // 2 chars: lower + lower
            "a1",             // lower + digit
            "tenant-name-01", // typical slug with hyphens and digits
            "ab12345678901234567890123456789",  // 32 chars (max)
    })
    @DisplayName("유효한 슬러그는 예외를 던지지 않는다")
    void validSlug_noException(String slug) {
        TenantId tenantId = new TenantId(slug);
        assertThat(tenantId.value()).isEqualTo(slug);
    }

    // ── invalid slugs ────────────────────────────────────────────────────────

    @Test
    @DisplayName("null은 IllegalArgumentException을 던진다")
    void null_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    @Test
    @DisplayName("빈 문자열은 IllegalArgumentException을 던진다")
    void empty_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("단일 문자('a')는 패턴 미충족으로 예외를 던진다 (최소 2자)")
    void singleChar_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId("a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    @Test
    @DisplayName("대문자 포함('WMS')은 IllegalArgumentException을 던진다")
    void uppercase_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId("WMS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    @Test
    @DisplayName("숫자로 시작하는 슬러그는 IllegalArgumentException을 던진다")
    void startsWithDigit_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId("1fan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    @Test
    @DisplayName("하이픈으로 시작하는 슬러그는 IllegalArgumentException을 던진다")
    void startsWithHyphen_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId("-fan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    @Test
    @DisplayName("언더스코어 포함 슬러그는 IllegalArgumentException을 던진다")
    void underscore_throwsIllegalArgument() {
        assertThatThrownBy(() -> new TenantId("fan_platform"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    @Test
    @DisplayName("33자 슬러그는 최대 길이 초과로 IllegalArgumentException을 던진다")
    void thirtyThreeChars_throwsIllegalArgument() {
        // 'a' + 32 chars = 33 total (regex allows at most 32)
        String slug = "a" + "b".repeat(32);
        assertThat(slug).hasSize(33);
        assertThatThrownBy(() -> new TenantId(slug))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant_id");
    }

    // ── equality & record semantics ──────────────────────────────────────────

    @Test
    @DisplayName("동일 value를 가진 두 TenantId는 equals/hashCode가 같다")
    void equalTenantIds_equalsAndHashCode() {
        TenantId a = new TenantId("fan-platform");
        TenantId b = new TenantId("fan-platform");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("다른 value를 가진 두 TenantId는 equals가 false이다")
    void differentTenantIds_notEqual() {
        TenantId a = new TenantId("fan-platform");
        TenantId b = new TenantId("wms");
        assertThat(a).isNotEqualTo(b);
    }
}
