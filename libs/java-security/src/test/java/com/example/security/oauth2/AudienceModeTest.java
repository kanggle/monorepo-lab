package com.example.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AudienceMode — 기본값 없음, 모르는 값은 기동 실패")
class AudienceModeTest {

    @Test
    @DisplayName("대소문자·공백 무관하게 SHADOW / ENFORCE 를 읽는다")
    void parsesKnownValues() {
        assertThat(AudienceMode.parse("SHADOW")).isEqualTo(AudienceMode.SHADOW);
        assertThat(AudienceMode.parse(" enforce ")).isEqualTo(AudienceMode.ENFORCE);
    }

    @Test
    @DisplayName("null · 빈 값 · 모르는 값 → IllegalArgumentException (기본값으로 떨어지지 않는다)")
    void rejectsMissingOrUnknown() {
        assertThatThrownBy(() -> AudienceMode.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AudienceMode.parse(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AudienceMode.parse("OFF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OFF");
    }
}
