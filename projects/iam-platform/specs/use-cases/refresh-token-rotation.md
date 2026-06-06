# Use Case: Refresh Token Rotation

---

## UC-3: 정상 토큰 갱신

### Actor
- 인증된 사용자 (access token 만료, refresh token 유효)

### Precondition
- 유효한 refresh token 보유
- 해당 refresh token이 revoke되지 않음
- 해당 refresh token이 이미 rotation되지 않음 (재사용 아님)

### Main Flow
1. 클라이언트가 `POST /api/auth/refresh` 에 현재 refresh token을 전송한다
2. auth-service가 refresh token의 `jti`를 블랙리스트(Redis)에서 확인한다
3. auth-service가 `refresh:invalidate-all:{account_id}` 존재 여부를 확인한다
4. auth-service가 DB에서 해당 `jti`의 refresh_tokens row를 조회한다
5. 해당 row에 이미 자식(rotated_from을 참조하는 다른 row)이 있는지 확인한다 → **없으면 정상**
6. 새 access token(30분) + 새 refresh token(7일) 발급
7. 새 refresh_tokens row 생성 (rotated_from = 기존 jti)
8. `auth.token.refreshed` 이벤트 발행 (outbox)
9. 응답 200: `{ accessToken, refreshToken, expiresIn, tokenType }`

### Exception Flow
- **EF-1**: refresh token 만료 → 401 `TOKEN_EXPIRED`
- **EF-2**: refresh token 블랙리스트에 존재 → 401 `SESSION_REVOKED`
- **EF-3**: `invalidate-all` 플래그 존재 + token의 `iat` < 플래그 시각 → 401 `SESSION_REVOKED`
- **EF-4**: 계정 상태 LOCKED/DELETED → 403 (refresh 차단)

---

## UC-4: 토큰 재사용 탐지

### Actor
- 공격자 또는 탈취된 토큰을 보유한 제3자

### Precondition
- refresh token A가 이미 rotation되어 token B가 발급된 상태
- 공격자가 token A를 다시 사용 시도

### Main Flow
1. 클라이언트가 이미 rotation된 refresh token A를 `POST /api/auth/refresh`에 전송한다
2. auth-service가 DB에서 token A의 row를 조회한다
3. token A의 `rotated_from`을 참조하는 자식 row(token B)가 이미 존재함을 감지한다
4. **재사용 탐지 확정**: 해당 account_id의 **모든** refresh_tokens를 `revoked=TRUE`로 일괄 처리
5. `refresh:invalidate-all:{account_id}` Redis 키 설정 (TTL = refresh token 최대 수명)
6. `auth.token.reuse.detected` 이벤트 발행 (outbox) — security-service가 즉시 AUTO_LOCK
7. 응답 401: `TOKEN_REUSE_DETECTED`
8. security-service가 이벤트를 소비하여 `suspicious_events` 기록 (ruleCode=TOKEN_REUSE, riskScore=100)
9. security-service가 account-service에 `POST /internal/accounts/{id}/lock` 호출

### Post-Condition
- 해당 계정의 모든 세션 무효화
- 계정 상태 LOCKED (auto-detect)
- 사용자는 운영자 unlock 후 재로그인 필요

---

## Related Contracts
- HTTP: [auth-api.md](../contracts/http/auth-api.md) `POST /api/auth/refresh`
- Events: [auth-events.md](../contracts/events/auth-events.md), [session-events.md](../contracts/events/session-events.md)
- Internal: [security-to-account.md](../contracts/http/internal/security-to-account.md) (auto-lock)
