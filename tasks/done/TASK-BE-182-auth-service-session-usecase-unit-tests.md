---
id: TASK-BE-182
title: "auth-service 세션 유스케이스 단위 테스트 — 5개 파일"
status: ready
type: TASK-BE
service: auth-service
---

## Goal

auth-service application 계층의 세션 관련 유스케이스 단위 테스트 파일 5개를
공식 태스크로 관리하고 커밋한다. 파일은 이미 작성되어 있으나 git에 추적되지 않는 상태였으며,
`RegisterOrUpdateDeviceSessionUseCaseTest`의 `SessionContext` 생성자 인수 순서 버그를 수정하여 커밋한다.

## Scope

- `apps/auth-service/src/test/java/com/example/auth/application/GetCurrentSessionUseCaseTest.java`
- `apps/auth-service/src/test/java/com/example/auth/application/ListSessionsUseCaseTest.java`
- `apps/auth-service/src/test/java/com/example/auth/application/RegisterOrUpdateDeviceSessionUseCaseTest.java`
- `apps/auth-service/src/test/java/com/example/auth/application/RevokeAllOtherSessionsUseCaseTest.java`
- `apps/auth-service/src/test/java/com/example/auth/application/RevokeSessionUseCaseTest.java`

구현 코드 변경 없음. 테스트 파일 버그 수정 및 커밋만 수행한다.

## Acceptance Criteria

### GetCurrentSessionUseCaseTest (6개)

1. `execute_activeSession_returnsResult` — 정상 세션 → DeviceSessionResult(current=true) 반환
2. `execute_blankDeviceId_throwsSessionNotFound` — blank deviceId → SessionNotFoundException
3. `execute_nullDeviceId_throwsSessionNotFound` — null deviceId → SessionNotFoundException
4. `execute_sessionNotFound_throwsSessionNotFound` — 세션 미존재 → SessionNotFoundException
5. `execute_crossAccountSession_throwsOwnershipMismatch` — 타 계정 세션 → SessionOwnershipMismatchException
6. `execute_revokedSession_throwsSessionNotFound` — revoked 세션 → SessionNotFoundException

### ListSessionsUseCaseTest (3개)

7. `execute_noSessions_returnsEmptyList` — 활성 세션 없음 → 빈 목록, maxActiveSessions 반환
8. `execute_currentDevice_markedAsCurrent` — 현재 디바이스 → current=true, 다른 세션 current=false
9. `execute_nullCurrentDeviceId_allSessionsNotCurrent` — currentDeviceId=null → 모든 세션 current=false

### RegisterOrUpdateDeviceSessionUseCaseTest (4개) — ctx() 버그 수정 포함

10. `execute_knownFingerprint_touchesAndReturnsExisting` — 기존 fingerprint → touch 후 isNew=false
11. `execute_unknownFingerprint_alwaysCreatesNew` — null fingerprint → 항상 새 세션 생성
12. `execute_blankFingerprint_treatedAsUnknown` — 공백 fingerprint → unknown 처리 후 새 세션
13. `execute_newFingerprint_enforcesLimitAndCreatesSession` — 신규 fingerprint → limit 체크 후 세션 생성, evicted 반환

### RevokeAllOtherSessionsUseCaseTest (6개)

14. `execute_twoOtherSessions_revokesBothAndReturnsCount` — 2개 다른 세션 revoke, count=2, 이벤트 2건 발행
15. `execute_noOtherSessions_returnsZero` — 다른 세션 없음 → count=0
16. `execute_blankCurrentDeviceId_throwsSessionNotFound` — blank deviceId → SessionNotFoundException
17. `execute_nullCurrentDeviceId_throwsSessionNotFound` — null deviceId → SessionNotFoundException
18. `execute_currentSessionNotFound_throwsSessionNotFound` — current 세션 미존재 → SessionNotFoundException
19. `execute_currentSessionDifferentAccount_throwsSessionNotFound` — 타 계정 세션 → SessionNotFoundException

### RevokeSessionUseCaseTest (4개)

20. `execute_activeSession_revokesAndPublishesEvent` — 정상 revoke → 토큰 취소, 세션 저장, 이벤트 발행
21. `execute_sessionNotFound_throwsSessionNotFound` — 세션 미존재 → SessionNotFoundException, 토큰 취소 없음
22. `execute_crossAccountSession_throwsOwnershipMismatch` — 타 계정 → SessionOwnershipMismatchException
23. `execute_alreadyRevokedSession_throwsSessionNotFound` — 이미 revoked → SessionNotFoundException

## Related Specs

- `specs/services/auth-service/architecture.md`

## Related Contracts

없음 (단위 테스트, HTTP/이벤트 계약 변경 없음)

## Edge Cases

- `RegisterOrUpdateDeviceSessionUseCaseTest.ctx()` 헬퍼: `SessionContext(ipAddress, userAgent, deviceFingerprint)` 순서 주의
- `ListSessionsUseCaseTest`: `@Value("${...}") maxActiveSessions` 필드를 `ReflectionTestUtils.setField`로 주입
- `RevokeAllOtherSessionsUseCaseTest`: `@Mock RefreshTokenRepository` — findActiveJtisByDeviceId stub 필요 (이벤트 발행에 jti 목록 사용)

## Failure Scenarios

- `SessionContext` 생성자 인수 순서 오류 시 fingerprint가 IP 주소로 처리되어 테스트 실패 — 수정 완료
- 테스트 파일 중 하나라도 컴파일 실패 시 커밋하지 않음
