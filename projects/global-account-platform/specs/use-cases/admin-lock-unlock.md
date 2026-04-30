# Use Case: Admin Lock / Unlock / Force Logout

---

## UC-11: 운영자 계정 잠금

### Actor
- Operator (ACCOUNT_ADMIN 이상)

### Precondition
- 대상 계정 status=ACTIVE
- 운영자가 잠금이 필요하다고 판단 (CS 문의, 보안 인시던트 등)

### Main Flow
1. 운영자가 admin dashboard에서 대상 계정을 선택하고 "Lock" 버튼 클릭
2. 사유(reason) 입력 필수. 내부 티켓 번호(ticketId) 선택 입력
3. `POST /api/admin/accounts/{id}/lock` 호출 (Idempotency-Key + X-Operator-Reason)
4. admin-service가 운영자 토큰·역할을 검증한다
5. `admin_actions` row 생성 (actionCode=ACCOUNT_LOCK, outcome=IN_PROGRESS)
6. `POST /internal/accounts/{id}/lock` 호출 (reason=ADMIN_LOCK)
7. account-service: `AccountStatusMachine.transition(ACTIVE, LOCKED, ADMIN_LOCK)` → `account.locked` 이벤트
8. admin-service: `admin_actions.outcome = SUCCESS` 갱신 + `admin.action.performed` 이벤트
9. auth-service: `account.locked` 소비 → 해당 계정 전체 세션 revoke
10. 응답 200: `{ accountId, previousStatus: "ACTIVE", currentStatus: "LOCKED", operatorId, auditId }`

### Exception Flow
- **EF-1**: 대상이 이미 LOCKED → 멱등 응답 (previousStatus=LOCKED, currentStatus=LOCKED)
- **EF-2**: 대상이 DELETED → 400 `STATE_TRANSITION_INVALID`
- **EF-3**: 권한 부족 → 403 + audit row (outcome=DENIED)
- **EF-4**: account-service 장애 → 502 + audit row (outcome=FAILURE)

---

## UC-12: 운영자 계정 잠금 해제

### Actor
- Operator (ACCOUNT_ADMIN 이상)

### Precondition
- 대상 계정 status=LOCKED
- 잠금 사유가 해소됨

### Main Flow
1. 운영자가 "Unlock" 선택, 사유 입력
2. `POST /api/admin/accounts/{id}/unlock` 호출
3. admin-service: 인증·감사 → `POST /internal/accounts/{id}/unlock`
4. account-service: LOCKED→ACTIVE 전이 → `account.unlocked` 이벤트
5. admin-service: audit SUCCESS + 이벤트
6. 응답 200

### Exception Flow
- **EF-1**: LOCKED가 아님 → 400 `STATE_TRANSITION_INVALID`

---

## UC-13: 운영자 강제 로그아웃

### Actor
- Operator (ACCOUNT_ADMIN 이상)

### Precondition
- 대상 계정이 활성 세션을 가지고 있음

### Main Flow
1. 운영자가 "Force Logout" 선택, 사유 입력
2. `POST /api/admin/sessions/{accountId}/revoke` 호출
3. admin-service: 인증·감사 → `POST /internal/auth/accounts/{id}/force-logout`
4. auth-service: 해당 account의 모든 refresh_tokens `revoked=TRUE` + `refresh:invalidate-all:{account_id}` Redis SET
5. `session.revoked` 이벤트 발행 (revokeReason=ADMIN_FORCE_LOGOUT)
6. admin-service: audit SUCCESS + 이벤트
7. 응답 200: `{ accountId, revokedSessionCount, auditId }`

### Post-Condition
- 해당 계정의 모든 세션 무효화
- 기존 access token은 만료까지 유효 (stateless, 최대 30분)
- 사용자는 refresh 시 401

---

## UC-14: 운영자 감사 조회

### Actor
- Operator (AUDITOR 이상)

### Precondition
- 없음 (조회 권한만 확인)

### Main Flow
1. 운영자가 admin dashboard에서 감사 탭 진입
2. 검색 조건 입력: accountId, 기간, actionCode, source
3. `GET /api/admin/audit?accountId=...&from=...&to=...&source=...` 호출
4. admin-service: 운영자 인증·권한 확인
5. admin-service: security-service query + 자체 admin_actions 조회 → 통합·정렬
6. **meta-audit**: 이 조회 자체를 `admin_actions` row로 기록 (actionCode=AUDIT_QUERY)
7. 응답 200: 페이지네이션된 통합 감사 뷰

### Exception Flow
- **EF-1**: AUDITOR 미만 역할 → 403
- **EF-2**: security-service 장애 → 부분 응답 (admin_actions만) + 경고 표시

---

## Related Contracts
- HTTP: [admin-api.md](../contracts/http/admin-api.md)
- Internal: [admin-to-auth.md](../contracts/http/internal/admin-to-auth.md), [admin-to-account.md](../contracts/http/internal/admin-to-account.md)
- Events: [admin-events.md](../contracts/events/admin-events.md), [account-events.md](../contracts/events/account-events.md), [session-events.md](../contracts/events/session-events.md)
