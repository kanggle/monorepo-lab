# Use Case: Account Lockout and Unlock

---

## UC-5: 로그인 실패 기반 계정 잠금 (자동)

### Actor
- 공격자 또는 패스워드를 잊은 사용자

### Precondition
- 계정 status=ACTIVE
- 이전 14분 동안 4회 실패 (카운터 = 4)

### Main Flow
1. 사용자가 5번째 로그인 시도를 한다
2. auth-service가 패스워드 검증에 실패한다
3. Redis `login:fail:{tenant_id}:{email_hash}` 카운터가 5로 증가한다
4. auth-service가 임계치(5회) 초과를 감지한다
5. 응답 429: `LOGIN_RATE_LIMITED` + `Retry-After: 900` (15분 TTL 잔여)
6. `auth.login.failed` 이벤트 발행 (failCount=5)
7. security-service가 이벤트를 소비하여 VelocityRule 평가 → riskScore ≥ 80
8. security-service가 `POST /internal/accounts/{id}/lock` 호출 (reason=AUTO_DETECT)
9. account-service가 ACTIVE→LOCKED 전이 + `account.locked` 이벤트 발행
10. auth-service가 `account.locked` 이벤트를 소비하여 해당 계정 세션 전체 revoke

### Post-Condition
- 계정 LOCKED
- 이후 로그인 시도 시 **423** `ACCOUNT_LOCKED` ([`platform/error-handling.md`](../../../../platform/error-handling.md) § Account, TASK-BE-462)
- 운영자 unlock 필요

---

## UC-6: 비정상 로그인 탐지 기반 계정 잠금 (자동)

### Actor
- System (security-service)

### Precondition
- 성공적인 로그인이 발생했으나 비정상 패턴 감지 (예: 한국 → 30분 후 미국)

### Main Flow
1. auth-service가 `auth.login.succeeded` 이벤트를 발행한다
2. security-service가 이벤트를 소비한다
3. GeoAnomalyRule: 이전 성공 지역(KR)과 현재(US) 비교 → 30분에 9000km → riskScore=95
4. finalScore = 95 → ACTION = AUTO_LOCK
5. `suspicious_events` 저장 (ruleCode=GEO_ANOMALY, action_taken=AUTO_LOCK)
6. `security.suspicious.detected` 이벤트 발행
7. `POST /internal/accounts/{id}/lock` 호출 (Idempotency-Key = suspicious_event_id)
8. account-service가 ACTIVE→LOCKED 전이

### Post-Condition
- 동일 UC-5의 post-condition

---

## UC-7: 운영자에 의한 계정 잠금 해제

### Actor
- Operator (ACCOUNT_ADMIN 이상)

### Precondition
- 계정 status=LOCKED
- 운영자가 잠금 사유를 검토하고 해제가 적절하다고 판단

### Main Flow
1. 운영자가 `POST /api/admin/accounts/{id}/unlock` 호출 (reason + ticketId)
2. admin-service가 운영자 인증·권한을 확인한다
3. admin-service가 `admin_actions` row를 생성한다 (outcome=IN_PROGRESS)
4. admin-service가 `POST /internal/accounts/{id}/unlock` 호출한다
5. account-service가 `AccountStatusMachine.transition(LOCKED, ACTIVE, ADMIN_UNLOCK)` 실행
6. `account_status_history` append + `account.unlocked` 이벤트 발행
7. admin-service가 `admin_actions` row를 outcome=SUCCESS로 갱신한다
8. `admin.action.performed` 이벤트 발행
9. 운영자에게 200: `{ accountId, previousStatus, currentStatus, unlockedAt, auditId }`

### Exception Flow
- **EF-1**: 계정이 LOCKED가 아님 → 400 `STATE_TRANSITION_INVALID`
- **EF-2**: 권한 부족 → 403 `PERMISSION_DENIED` + audit row (outcome=DENIED)
- **EF-3**: account-service 장애 → 502 `DOWNSTREAM_ERROR` + audit row (outcome=FAILURE)

### Post-Condition
- 계정 ACTIVE, 로그인 가능
- 감사 기록에 operator_id + reason + ticketId 남음

---

## UC-8: 비밀번호 재설정에 의한 자동 잠금 해제 (자기 복구, TASK-BE-612)

UC-6(자동 잠금)의 대칭. 소유자 결정(`TASK-BE-608` § AC-3): 자동 탐지로 잠긴 사용자가 운영자 없이 복구하는 유일한 경로.
시간 경과 자동 해제와 «운영자 수동 unlock 만» 은 기각됐다.

### Actor
- 잠긴 계정의 사용자 (이메일 소유 증명으로)

### Precondition
- 계정 status=LOCKED 이고, 그 계정을 **잠근 전이**(ACTIVE→LOCKED)의 사유가 `AUTO_DETECT`

### Main Flow
1. 사용자가 비밀번호 재설정을 요청하고(`POST /api/auth/password-reset/request`) 메일의 토큰으로 확인한다(`…/confirm`)
2. auth-service 가 새 비밀번호 저장 · 전 세션 폐기를 커밋한다
3. 커밋 뒤 auth-service 가 상태를 조회하고 LOCKED 면 `POST /internal/accounts/{id}/unlock` (`reason=USER_RECOVERY`, 테넌트 헤더 없음)
4. account-service 가 잠근 전이의 사유를 확인(`AUTO_DETECT`) → `AccountStatusMachine.transition(LOCKED, ACTIVE, USER_RECOVERY)`
5. `account_status_history` append(`actor_type=user`) + `account.unlocked`(`reasonCode=USER_RECOVERY`) 발행

### Exception Flow
- **EF-1**: 잠근 전이의 사유가 `AUTO_DETECT` 가 아님(`ADMIN_LOCK` · `PASSWORD_FAILURE_THRESHOLD` · 운영자 프로비저닝 등) → 409 `STATE_TRANSITION_INVALID`, 계정은 LOCKED 유지. 재설정 자체는 성공(비밀번호는 바뀐다)
- **EF-2**: account-service 장애 → 해제되지 않은 채 재설정은 성공(fail-soft). 사용자는 재설정을 다시 하거나 운영자 해제(UC-7)로 복구

### Post-Condition
- 계정 ACTIVE, 새 비밀번호로 로그인 가능 · 옛 세션은 전부 폐기된 상태

---

## Related Contracts
- HTTP: [auth-api.md](../contracts/http/auth-api.md), [admin-api.md](../contracts/http/admin-api.md)
- Internal: [security-to-account.md](../contracts/http/internal/security-to-account.md), [admin-to-account.md](../contracts/http/internal/admin-to-account.md)
- Events: [auth-events.md](../contracts/events/auth-events.md), [account-events.md](../contracts/events/account-events.md), [security-events.md](../contracts/events/security-events.md), [admin-events.md](../contracts/events/admin-events.md)
