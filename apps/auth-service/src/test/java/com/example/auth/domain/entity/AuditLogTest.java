package com.example.auth.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.example.auth.domain.entity.AuditResult.FAILURE;
import static com.example.auth.domain.entity.AuditResult.SUCCESS;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuditLog 도메인 엔티티 단위 테스트")
class AuditLogTest {

    @Test
    @DisplayName("정상 생성 - 모든 필드가 설정된다")
    void create_success() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        AuditLog log = AuditLog.create(
            userId, "user@example.com", AuditEventType.LOGIN_SUCCESS,
            "127.0.0.1", "Mozilla/5.0", SUCCESS, null);

        assertThat(log.getId()).isNotNull();
        assertThat(log.getUserId()).isEqualTo(userId);
        assertThat(log.getEmail()).isEqualTo("user@example.com");
        assertThat(log.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(log.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(log.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(log.getResult()).isEqualTo(SUCCESS);
        assertThat(log.getFailureReason()).isNull();
        assertThat(log.getCreatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("로그인 실패 - userId가 null이어도 생성된다")
    void create_loginFailure_nullUserId() {
        AuditLog log = AuditLog.create(
            null, "unknown@example.com", AuditEventType.LOGIN_FAILURE,
            "10.0.0.1", null, FAILURE, "INVALID_CREDENTIALS");

        assertThat(log.getUserId()).isNull();
        assertThat(log.getFailureReason()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(log.getResult()).isEqualTo(FAILURE);
    }

    @Test
    @DisplayName("userAgent가 500자를 초과하면 잘린다")
    void create_longUserAgent_truncated() {
        String longUserAgent = "A".repeat(600);

        AuditLog log = AuditLog.create(
            UUID.randomUUID(), "user@example.com", AuditEventType.SIGNUP,
            "127.0.0.1", longUserAgent, SUCCESS, null);

        assertThat(log.getUserAgent()).hasSize(500);
    }

    @Test
    @DisplayName("userAgent가 null이면 null로 저장된다")
    void create_nullUserAgent_staysNull() {
        AuditLog log = AuditLog.create(
            UUID.randomUUID(), "user@example.com", AuditEventType.LOGOUT,
            "127.0.0.1", null, SUCCESS, null);

        assertThat(log.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("각 이벤트 타입에 대해 AuditLog가 생성된다")
    void create_allEventTypes() {
        for (AuditEventType type : AuditEventType.values()) {
            AuditLog log = AuditLog.create(
                UUID.randomUUID(), "user@example.com", type,
                null, null, SUCCESS, null);
            assertThat(log.getEventType()).isEqualTo(type);
        }
    }
}
