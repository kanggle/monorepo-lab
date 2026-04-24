package com.example.auth.contract;

import com.example.auth.domain.event.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static com.example.auth.contract.ContractTestHelper.assertFieldsMatch;

/**
 * auth-service 이벤트 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/events/auth-events.md
 */
@DisplayName("Auth Event 컨트랙트 테스트 — specs/contracts/events/auth-events.md")
class AuthEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String SPEC_REF = "specs/contracts/events/auth-events.md";
    private static final Set<String> ENVELOPE_FIELDS = Set.of("event_id", "event_type", "occurred_at", "source", "payload");

    // ─── UserSignedUp ───────────────────────────────────────────────────

    @Test
    @DisplayName("UserSignedUp envelope은 스펙 정의 필드만 포함한다")
    void userSignedUp_envelope_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동"));
        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("UserSignedUp payload는 {userId, email, name}만 포함한다")
    void userSignedUp_payload_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new UserSignedUp(UUID.randomUUID(), "test@example.com", "홍길동"));
        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "email", "name"),
                SPEC_REF + " UserSignedUp payload");
    }

    // ─── UserLoggedIn ───────────────────────────────────────────────────

    @Test
    @DisplayName("UserLoggedIn envelope은 스펙 정의 필드만 포함한다")
    void userLoggedIn_envelope_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new UserLoggedIn(UUID.randomUUID(), "test@example.com", "127.0.0.1", "Mozilla/5.0"));
        String json = objectMapper.writeValueAsString(event);
        assertFieldsMatch(json, ENVELOPE_FIELDS, SPEC_REF + " envelope");
    }

    @Test
    @DisplayName("UserLoggedIn payload는 {userId, email, ipAddress, userAgent}만 포함한다")
    void userLoggedIn_payload_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new UserLoggedIn(UUID.randomUUID(), "test@example.com", "127.0.0.1", "Mozilla/5.0"));
        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "email", "ipAddress", "userAgent"),
                SPEC_REF + " UserLoggedIn payload");
    }

    // ─── UserLoggedOut ──────────────────────────────────────────────────

    @Test
    @DisplayName("UserLoggedOut payload는 {userId, sessionId}만 포함한다")
    void userLoggedOut_payload_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new UserLoggedOut(UUID.randomUUID(), "session-hash-abc"));
        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "sessionId"),
                SPEC_REF + " UserLoggedOut payload");
    }

    // ─── TokenRefreshed ─────────────────────────────────────────────────

    @Test
    @DisplayName("TokenRefreshed payload는 {userId, sessionId}만 포함한다")
    void tokenRefreshed_payload_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new TokenRefreshed(UUID.randomUUID(), "session-hash-abc"));
        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "sessionId"),
                SPEC_REF + " TokenRefreshed payload");
    }

    // ─── LoginFailed ────────────────────────────────────────────────────

    @Test
    @DisplayName("LoginFailed payload는 {email, ipAddress, reason}만 포함한다")
    void loginFailed_payload_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new LoginFailed("test@example.com", "127.0.0.1", "INVALID_CREDENTIALS"));
        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("email", "ipAddress", "reason"),
                SPEC_REF + " LoginFailed payload");
    }

    // ─── SessionLimitExceeded ───────────────────────────────────────────

    @Test
    @DisplayName("SessionLimitExceeded payload는 {userId, evictedSessionId, newSessionId}만 포함한다")
    void sessionLimitExceeded_payload_matchesSpec() throws Exception {
        AuthEvent event = AuthEvent.of(new SessionLimitExceeded(UUID.randomUUID(), "old-session", "new-session"));
        JsonNode payload = objectMapper.readTree(objectMapper.writeValueAsString(event)).get("payload");
        assertFieldsMatch(payload, Set.of("userId", "evictedSessionId", "newSessionId"),
                SPEC_REF + " SessionLimitExceeded payload");
    }
}
