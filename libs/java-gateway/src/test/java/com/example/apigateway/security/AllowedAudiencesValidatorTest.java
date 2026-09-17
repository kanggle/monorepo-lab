package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@code jwt-standard-claims.md} § JWT Validation rule 5, in isolation (TASK-MONO-696).
 */
@DisplayName("AllowedAudiencesValidator — aud ∩ 허용목록 ≠ ∅ (rule 5)")
class AllowedAudiencesValidatorTest {

    private static final String GATEWAY = "edge-a";
    private static final List<String> ALLOWLIST = List.of("console-client", "web-client");

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Nested
    @DisplayName("기동 실패 — 허용목록·모드는 비울 수 없다")
    class FailClosedConstruction {

        @Test
        @DisplayName("빈 허용목록 → IllegalArgumentException (아무 aud 나 허용으로 강등되지 않는다)")
        void emptyAllowlist_throws() {
            assertThatThrownBy(() -> new AllowedAudiencesValidator(GATEWAY, List.of(), AudienceMode.SHADOW, registry))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allowedAudiences");
        }

        @Test
        @DisplayName("null 허용목록 · 공백뿐인 허용목록 → IllegalArgumentException")
        void nullOrBlankOnlyAllowlist_throws() {
            assertThatThrownBy(() -> new AllowedAudiencesValidator(GATEWAY, null, AudienceMode.ENFORCE, registry))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AllowedAudiencesValidator(
                    GATEWAY, Arrays.asList(" ", "", null), AudienceMode.ENFORCE, registry))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 게이트웨이 이름 · null 모드 → 실패")
        void blankGatewayOrNullMode_throws() {
            assertThatThrownBy(() -> new AllowedAudiencesValidator(" ", ALLOWLIST, AudienceMode.SHADOW, registry))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AllowedAudiencesValidator(GATEWAY, ALLOWLIST, null, registry))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("허용목록 값은 트림되고 빈 항목은 버려진다")
        void allowlistIsTrimmed() {
            AllowedAudiencesValidator v = new AllowedAudiencesValidator(
                    GATEWAY, Arrays.asList(" console-client ", "", null), AudienceMode.ENFORCE, registry);
            assertThat(v.allowedAudiences()).containsExactly("console-client");
        }
    }

    @Nested
    @DisplayName("ENFORCE")
    class Enforce {

        private AllowedAudiencesValidator validator;

        @BeforeEach
        void build() {
            validator = new AllowedAudiencesValidator(GATEWAY, ALLOWLIST, AudienceMode.ENFORCE, registry);
        }

        @Test
        @DisplayName("aud = 허용 client 하나 → 통과, match 1")
        void allowedSingle_passes() {
            assertThat(validator.validate(jwt(List.of("web-client"))).hasErrors()).isFalse();
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MATCH)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("aud 배열에 허용 client 가 하나라도 있으면 통과 (교집합이지 '정확히 하나' 가 아니다)")
        void arrayContainingAllowed_passes() {
            assertThat(validator.validate(jwt(List.of("stranger", "console-client"))).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("aud 가 허용목록 밖 → audience_mismatch, mismatch_rejected 1")
        void foreign_fails() {
            OAuth2TokenValidatorResult r = validator.validate(jwt(List.of("stranger")));
            assertThat(r.getErrors()).extracting(e -> e.getErrorCode())
                    .containsExactly(AllowedAudiencesValidator.ERROR_CODE_AUDIENCE_MISMATCH);
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED)).isEqualTo(1.0);
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MATCH)).isZero();
        }

        @Test
        @DisplayName("aud 없음 = 빈 집합 → 실패")
        void missing_fails() {
            assertThat(validator.validate(jwt(null)).getErrors()).extracting(e -> e.getErrorCode())
                    .containsExactly(AllowedAudiencesValidator.ERROR_CODE_AUDIENCE_MISMATCH);
        }

        @Test
        @DisplayName("대소문자가 다른 client id 는 다른 client 다")
        void caseSensitive() {
            assertThat(validator.validate(jwt(List.of("WEB-CLIENT"))).hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("SHADOW — 거절하지 않고 기록한다")
    class Shadow {

        private AllowedAudiencesValidator validator;

        @BeforeEach
        void build() {
            validator = new AllowedAudiencesValidator(GATEWAY, ALLOWLIST, AudienceMode.SHADOW, registry);
        }

        @Test
        @DisplayName("허용목록 밖 aud → 통과 + mismatch_shadowed 1")
        void foreign_passesAndCounts() {
            assertThat(validator.validate(jwt(List.of("stranger"))).hasErrors()).isFalse();
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("aud 없음 → 통과 + mismatch_shadowed 1")
        void missing_passesAndCounts() {
            assertThat(validator.validate(jwt(null)).hasErrors()).isFalse();
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("허용 aud → match 1, mismatch 0")
        void allowed_countsMatch() {
            validator.validate(jwt(List.of("console-client")));
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MATCH)).isEqualTo(1.0);
            assertThat(count(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED)).isZero();
        }
    }

    /**
     * The aud values come from the token. As tags they would hand series cardinality to whoever
     * can get a client registered, so the metric carries exactly {gateway, outcome}.
     */
    @Test
    @DisplayName("메트릭 태그는 gateway · outcome 뿐이다 — aud 값은 태그가 아니다")
    void metricTagsAreGatewayAndOutcomeOnly() {
        AllowedAudiencesValidator validator =
                new AllowedAudiencesValidator(GATEWAY, ALLOWLIST, AudienceMode.SHADOW, registry);
        validator.validate(jwt(List.of("stranger-1")));
        validator.validate(jwt(List.of("stranger-2")));

        List<Meter> meters = new ArrayList<>(registry.find(AllowedAudiencesValidator.METRIC_NAME).meters());
        assertThat(meters).hasSize(2); // match + mismatch_shadowed, regardless of how many aud values arrived
        for (Meter meter : meters) {
            Set<String> keys = meter.getId().getTags().stream().map(t -> t.getKey()).collect(Collectors.toSet());
            assertThat(keys).containsExactlyInAnyOrder(
                    AllowedAudiencesValidator.TAG_GATEWAY, AllowedAudiencesValidator.TAG_OUTCOME);
            assertThat(meter.getId().getTag(AllowedAudiencesValidator.TAG_OUTCOME))
                    .doesNotContain("stranger");
        }
    }

    private double count(String outcome) {
        Counter counter = registry.find(AllowedAudiencesValidator.METRIC_NAME)
                .tag(AllowedAudiencesValidator.TAG_GATEWAY, GATEWAY)
                .tag(AllowedAudiencesValidator.TAG_OUTCOME, outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static Jwt jwt(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-1")
                .claim("jti", "jti-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }
}
