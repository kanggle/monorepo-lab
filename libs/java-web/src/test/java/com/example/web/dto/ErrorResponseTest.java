package com.example.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse 단위 테스트")
class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("of 팩토리 메서드로 생성 시 code, message, timestamp가 설정된다")
    void of_validInput_setsAllFields() {
        ErrorResponse response = ErrorResponse.of("UNAUTHORIZED", "Access token is required");

        assertThat(response.code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.message()).isEqualTo("Access token is required");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.timestamp()).isNotBlank();
    }

    @Test
    @DisplayName("JSON 직렬화 시 code, message, timestamp 필드가 포함된다")
    void serialize_validResponse_containsAllFields() throws Exception {
        ErrorResponse response = ErrorResponse.of("INTERNAL_ERROR", "Unexpected server-side error");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"code\":\"INTERNAL_ERROR\"");
        assertThat(json).contains("\"message\":\"Unexpected server-side error\"");
        assertThat(json).contains("\"timestamp\":");
    }

    @Test
    @DisplayName("JSON 역직렬화 시 올바른 객체가 생성된다")
    void deserialize_validJson_producesCorrectObject() throws Exception {
        String json = """
                {
                  "code": "NOT_FOUND",
                  "message": "Requested resource does not exist",
                  "timestamp": "2026-03-28T10:00:00Z"
                }
                """;

        ErrorResponse response = objectMapper.readValue(json, ErrorResponse.class);

        assertThat(response.code()).isEqualTo("NOT_FOUND");
        assertThat(response.message()).isEqualTo("Requested resource does not exist");
        assertThat(response.timestamp()).isEqualTo("2026-03-28T10:00:00Z");
    }

    @Test
    @DisplayName("timestamp는 ISO 8601 형식이다")
    void timestamp_isIso8601() {
        ErrorResponse response = ErrorResponse.of("VALIDATION_ERROR", "Field is required");

        assertThat(java.time.Instant.parse(response.timestamp())).isNotNull();
    }
}
