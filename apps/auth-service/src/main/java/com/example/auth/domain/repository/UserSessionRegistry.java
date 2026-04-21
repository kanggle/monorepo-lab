package com.example.auth.domain.repository;

import java.util.UUID;

public interface UserSessionRegistry {

    /**
     * 로그인 시 새로 등록된 세션 ID와, 최대 동시 세션 수 초과 시 제거된 세션 ID를 함께 반환한다.
     *
     * @param newSessionId    새 세션의 식별자 (SHA-256 해시)
     * @param evictedSessionId 제거된 세션의 식별자, 없으면 null
     */
    record RegistrationResult(String newSessionId, String evictedSessionId) {}

    /**
     * 로그인 시 세션을 등록한다.
     *
     * @return 등록 결과 — newSessionId는 항상 존재, evictedSessionId는 제거된 세션이 있을 때만 non-null
     */
    RegistrationResult registerSession(UUID userId, String refreshToken, long inactivityTimeoutSeconds);

    /**
     * 토큰 갱신 시 세션을 교체하고 활동 시간을 갱신한다 (rotation).
     */
    void rotateSession(UUID userId, String oldRefreshToken, String newRefreshToken, long inactivityTimeoutSeconds);

    /**
     * 로그아웃 시 세션 레지스트리에서 세션을 제거한다.
     */
    void removeSession(UUID userId, String refreshToken);

    /**
     * 사용자의 모든 세션을 제거한다 (계정 탈퇴/비활성화 시).
     */
    void removeAllSessions(UUID userId);
}
