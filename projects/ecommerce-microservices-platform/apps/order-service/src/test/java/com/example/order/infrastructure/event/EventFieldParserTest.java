package com.example.order.infrastructure.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventFieldParser 유틸리티 메서드 단위 테스트")
class EventFieldParserTest {

    @Nested
    @DisplayName("isBlank 메서드")
    class IsBlank {

        @Test
        @DisplayName("null 값은 빈 값으로 판별된다")
        void isBlank_null_returnsTrue() {
            assertThat(EventFieldParser.isBlank(null)).isTrue();
        }

        @Test
        @DisplayName("빈 문자열은 빈 값으로 판별된다")
        void isBlank_emptyString_returnsTrue() {
            assertThat(EventFieldParser.isBlank("")).isTrue();
        }

        @Test
        @DisplayName("공백 문자열은 빈 값으로 판별된다")
        void isBlank_blankString_returnsTrue() {
            assertThat(EventFieldParser.isBlank("  ")).isTrue();
        }

        @Test
        @DisplayName("값이 있는 문자열은 빈 값이 아닌 것으로 판별된다")
        void isBlank_nonBlankString_returnsFalse() {
            assertThat(EventFieldParser.isBlank("hello")).isFalse();
        }
    }

    @Nested
    @DisplayName("parseInstant 메서드")
    class ParseInstant {

        @Test
        @DisplayName("유효한 ISO-8601 문자열은 올바른 Instant로 파싱된다")
        void parseInstant_validIso8601String_returnsCorrectInstant() {
            Instant result = EventFieldParser.parseInstant("2026-03-24T10:00:00Z", "occurredAt");

            assertThat(result).isEqualTo(Instant.parse("2026-03-24T10:00:00Z"));
        }

        @Test
        @DisplayName("null 값이면 fieldName을 포함한 IllegalArgumentException이 발생한다")
        void parseInstant_null_throwsIllegalArgumentExceptionWithFieldName() {
            assertThatThrownBy(() -> EventFieldParser.parseInstant(null, "occurredAt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("occurredAt");
        }

        @Test
        @DisplayName("빈 문자열이면 IllegalArgumentException이 발생한다")
        void parseInstant_emptyString_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> EventFieldParser.parseInstant("", "occurredAt"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("공백 문자열이면 IllegalArgumentException이 발생한다")
        void parseInstant_blankString_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> EventFieldParser.parseInstant("  ", "occurredAt"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("잘못된 형식의 문자열이면 'Failed to parse' 메시지를 포함한 IllegalArgumentException이 발생한다")
        void parseInstant_invalidFormat_throwsIllegalArgumentExceptionWithFailedToParseMessage() {
            assertThatThrownBy(() -> EventFieldParser.parseInstant("not-a-date", "occurredAt"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Failed to parse");
        }

        @Test
        @DisplayName("null 값으로 발생한 예외 메시지에는 fieldName이 포함된다")
        void parseInstant_nullWithCustomFieldName_exceptionMessageContainsFieldName() {
            String fieldName = "eventTimestamp";

            assertThatThrownBy(() -> EventFieldParser.parseInstant(null, fieldName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(fieldName);
        }
    }
}
