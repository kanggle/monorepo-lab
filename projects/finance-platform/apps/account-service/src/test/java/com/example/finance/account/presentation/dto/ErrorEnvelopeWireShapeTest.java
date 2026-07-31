package com.example.finance.account.presentation.dto;

import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-FIN-BE-066 (ADR-MONO-058 § D2) — evidence that retiring finance's local
 * {@code ApiErrorBody} in favour of the shared {@link ErrorResponse} is
 * <strong>wire-preserving</strong>, and that the resulting envelope is stronger than the
 * one it replaced.
 *
 * <p>The retired record was
 * {@code (String code, String message, Map<String,Object> details, Instant timestamp)}
 * annotated {@code @JsonInclude(NON_NULL)}. Two properties had to be checked rather than
 * assumed:
 *
 * <ol>
 *   <li><strong>{@code details} absence.</strong> No arm in either finance service ever
 *       populated it (the 3-argument factory had zero call sites), so {@code NON_NULL}
 *       dropped it from every response ever emitted. {@link ErrorResponse} not having the
 *       field at all therefore produces the identical key set — asserted below.</li>
 *   <li><strong>{@code timestamp} rendering.</strong> An {@code Instant} field renders as
 *       ISO-8601 text only when {@code SerializationFeature.WRITE_DATES_AS_TIMESTAMPS} is
 *       disabled, which is <em>not</em> a Jackson or a spring-web default — only Spring
 *       Boot's {@code JacksonAutoConfiguration} disables it, and Boot's mapper is
 *       {@code @ConditionalOnMissingBean}. Neither finance service contributes an
 *       {@code ObjectMapper} bean today, so the old envelope did render ISO text; but that
 *       was a property of the <em>context</em>, not of the envelope. {@link ErrorResponse}
 *       pre-formats with {@code Instant.now().toString()}, so it is ISO text by
 *       construction under any mapper. Both halves are asserted, including the negative
 *       control that proves the check can fail.</li>
 * </ol>
 */
@DisplayName("에러 봉투 wire shape 테스트 (ADR-MONO-058 D2 채택)")
class ErrorEnvelopeWireShapeTest {

    /** Stand-in for the retired {@code ApiErrorBody}'s {@code Instant}-typed timestamp. */
    private record InstantTimestampEnvelope(String code, String message, Instant timestamp) {
    }

    @Test
    @DisplayName("ErrorResponse 는 정확히 {code, message, timestamp} 3키 — details 키 없음")
    void keySetIsExactlyThreeFields() throws Exception {
        JsonNode json = new ObjectMapper()
                .readTree(new ObjectMapper().writeValueAsString(
                        ErrorResponse.of("ACCOUNT_NOT_FOUND", "no such account")));

        java.util.List<String> keys = new java.util.ArrayList<>();
        json.fieldNames().forEachRemaining(keys::add);

        assertThat(keys).containsExactlyInAnyOrder("code", "message", "timestamp");
        assertThat(json.has("details"))
                .as("the retired ApiErrorBody suppressed a never-populated details key; "
                        + "the shared envelope must not introduce one either")
                .isFalse();
    }

    @Test
    @DisplayName("timestamp 는 mapper 설정과 무관하게 ISO-8601 문자열 (bare / spring-web / Boot 자동설정)")
    void timestampIsIsoStringUnderEveryMapper() throws Exception {
        ErrorResponse response = ErrorResponse.of("INTERNAL_ERROR", "boom");

        assertIsoStringTimestamp(new ObjectMapper(), response);
        assertIsoStringTimestamp(Jackson2ObjectMapperBuilder.json().build(), response);

        new ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                        .of(JacksonAutoConfiguration.class))
                .run(context -> assertIsoStringTimestamp(
                        context.getBean(ObjectMapper.class), response));
    }

    /**
     * The negative control. Without it the test above could be passing because the mappers
     * happen to be configured for ISO output rather than because the envelope pre-formats —
     * this proves a raw {@code Instant} really is mapper-dependent, i.e. that the property
     * being asserted is the envelope's and not the context's.
     */
    @Test
    @DisplayName("대조군: Instant 타입 필드는 bare mapper 에서 숫자로 직렬화된다 (구 봉투의 취약점)")
    void rawInstantIsMapperDependent() throws Exception {
        ObjectMapper bare = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        JsonNode json = bare.readTree(bare.writeValueAsString(
                new InstantTimestampEnvelope("X", "y", Instant.now())));

        assertThat(json.get("timestamp").isTextual())
                .as("a raw Instant renders as ISO text only when WRITE_DATES_AS_TIMESTAMPS "
                        + "is disabled — which no Jackson/spring-web default does")
                .isFalse();
    }

    private static void assertIsoStringTimestamp(ObjectMapper mapper, ErrorResponse response)
            throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

        assertThat(json.get("timestamp").isTextual())
                .as("timestamp must be a JSON string for mapper %s", mapper)
                .isTrue();
        assertThat(Instant.parse(json.get("timestamp").asText())).isNotNull();
    }
}
