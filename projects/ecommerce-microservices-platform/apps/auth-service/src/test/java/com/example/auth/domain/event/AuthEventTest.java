package com.example.auth.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthEvent 단위 테스트")
class AuthEventTest {

    @Test
    @DisplayName("of()로 생성한 AuthEvent는 eventId, occurredAt, source가 자동 설정된다")
    void of_setsEnvelopeFields() {
        Instant before = Instant.now();
        UserSignedUp payload = new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동");

        AuthEvent event = AuthEvent.of(payload);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isAfterOrEqualTo(before);
        assertThat(event.source()).isEqualTo("auth-service");
        assertThat(event.payload()).isEqualTo(payload);
    }

    @Test
    @DisplayName("eventType은 payload 클래스의 simple name이다")
    void of_eventTypeMatchesPayloadClassName() {
        assertThat(AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "a@b.com", "name")).eventType())
            .isEqualTo("UserSignedUp");
        assertThat(AuthEvent.of(new UserLoggedIn(UUID.randomUUID(), "a@b.com", null, null)).eventType())
            .isEqualTo("UserLoggedIn");
        assertThat(AuthEvent.of(new UserLoggedOut(UUID.randomUUID(), "session-id")).eventType())
            .isEqualTo("UserLoggedOut");
        assertThat(AuthEvent.of(new TokenRefreshed(UUID.randomUUID(), "session-id")).eventType())
            .isEqualTo("TokenRefreshed");
        assertThat(AuthEvent.of(new LoginFailed("a@b.com", null, "INVALID_CREDENTIALS")).eventType())
            .isEqualTo("LoginFailed");
        assertThat(AuthEvent.of(new SessionLimitExceeded(UUID.randomUUID(), "old-hash", "new-hash")).eventType())
            .isEqualTo("SessionLimitExceeded");
    }

    @Test
    @DisplayName("호출마다 고유한 eventId가 생성된다")
    void of_uniqueEventIdPerCall() {
        UserSignedUp payload = new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동");

        AuthEvent event1 = AuthEvent.of(payload);
        AuthEvent event2 = AuthEvent.of(payload);

        assertThat(event1.eventId()).isNotEqualTo(event2.eventId());
    }
}
