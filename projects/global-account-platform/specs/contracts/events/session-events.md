# Event Contract: session-events

세션 무효화 관련 이벤트. 발행 주체는 상황에 따라 다름.

---

## Event Envelope

[auth-events.md](auth-events.md)와 동일한 표준 envelope.

---

## session.revoked

세션(refresh token)이 명시적으로 무효화되었을 때 발행. 로그아웃, 강제 revoke, 토큰 재사용 탐지 후 bulk invalidation 등 모든 무효화 경로에서 발행.

**Topic**: `session.revoked`

**발행 주체**:
- `auth-service` — 사용자 자발적 로그아웃, 토큰 재사용 탐지 시 bulk revoke
- `auth-service` — admin-service의 강제 로그아웃 요청을 처리한 결과

**파티션 키**: `account_id`

**Payload**:
```json
{
  "accountId": "string",
  "revokedJtis": ["string (UUID)", "..."],
  "revokeReason": "USER_LOGOUT | ADMIN_FORCE_LOGOUT | TOKEN_REUSE_DETECTED | ACCOUNT_DELETED | ACCOUNT_LOCKED",
  "actorType": "user | operator | system",
  "actorId": "string | null (operator_id if admin, null if system/user)",
  "revokedAt": "2026-04-12T10:00:00Z",
  "totalRevoked": 5
}
```

**Consumers**:
- security-service — login_history에 session revocation 이력 기록
- 관측성 시스템 — revoke rate 메트릭

---

## Design Note

### 왜 별도 토픽인가?

세션 revoke는 여러 시나리오(로그아웃, admin 강제, 보안 탐지, 계정 삭제, 계정 잠금)에서 발생한다. 각 시나리오의 "원인 이벤트"(auth.token.reuse.detected, admin.action.performed, account.deleted 등)와 "결과 이벤트"(session.revoked)를 분리함으로써:

1. Consumer가 "세션이 무효화되었다"라는 단일 사실에 반응 가능 (원인별로 다른 토픽을 구독할 필요 없음)
2. 원인 이벤트는 각자의 도메인 맥락을 유지하고, 세션 무효화라는 **교차 관심사**는 이 토픽이 전담

### revokedJtis 배열

bulk revoke(토큰 재사용, 강제 로그아웃) 시 무효화된 모든 jti를 한 이벤트에 포함. 개별 jti마다 이벤트를 발행하지 않음 — 불필요한 이벤트 폭발 방지.

---

## Consumer Rules

- 멱등 처리: `eventId` 기반 dedupe
- `revokedJtis` 배열이 클 수 있음 (한 계정에 10+ 세션). consumer는 대량 처리에 대비
- forward-compatible: 새 `revokeReason` 추가 시 알 수 없는 값은 `UNKNOWN_REASON`으로 처리
- DLQ: `session.revoked.dlq`
