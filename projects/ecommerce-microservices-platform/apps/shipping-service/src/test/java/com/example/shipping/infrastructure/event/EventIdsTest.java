package com.example.shipping.infrastructure.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventIds.parseOrNull 단위 테스트 (ADR-MONO-058 D7)")
class EventIdsTest {

    @Test
    @DisplayName("유효한 UUID 문자열은 UUID로 파싱된다")
    void parseOrNull_validUuid_returnsUuid() {
        UUID id = UUID.randomUUID();

        assertThat(EventIds.parseOrNull(id.toString())).isEqualTo(id);
    }

    @Test
    @DisplayName("null 값은 null을 반환한다 (dedupe skip, work는 실행됨)")
    void parseOrNull_null_returnsNull() {
        assertThat(EventIds.parseOrNull(null)).isNull();
    }

    @Test
    @DisplayName("공백 문자열은 null을 반환한다")
    void parseOrNull_blank_returnsNull() {
        assertThat(EventIds.parseOrNull("   ")).isNull();
    }

    @Test
    @DisplayName("비어있지 않은 잘못된 형식이면 IllegalArgumentException이 발생한다 (DLQ 라우팅)")
    void parseOrNull_malformed_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> EventIds.parseOrNull("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
