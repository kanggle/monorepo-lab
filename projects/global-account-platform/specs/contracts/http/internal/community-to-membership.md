# Internal HTTP Contract: community-service → membership-service

community-service는 프리미엄 포스트 접근 권한 확인 시 membership-service를 호출.

**호출 방향**: community-service (client) → membership-service (server)
**노출 경로**: `/internal/membership/*` — 게이트웨이에 노출 금지
**인증** (TASK-BE-253 변경): `Authorization: Bearer <oauth2-access-token>`
- 기존 "내부 서비스 토큰 (mTLS 대체)" 표기는 **deprecated**.
- community-service는 GAP에서 `client_credentials` grant 로 발급한 access token 을 사용한다.
  - 등록 client: `community-service-client` (`tenant_id=fan-platform`, `allowed_grants=[client_credentials]`, `allowed_scopes=[account.read, membership.read]`).
- membership-service 는 표준 OAuth2 Resource Server 로서 토큰 서명·만료·issuer 를 검증하고, 향후 `membership.read` scope 를 추가로 요구할 수 있다.

---

## GET /internal/membership/access

계정이 특정 플랜 레벨 이상의 콘텐츠에 접근 가능한지 확인.

**Required headers**:
| Header | Value | 설명 |
|---|---|---|
| `Authorization` | `Bearer <access-token>` | `client_credentials` 토큰. `tenant_id=fan-platform`, scope 에 `membership.read` 포함 |

**Query Parameters**:
| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| accountId | string (UUID) | Yes | 접근을 시도하는 계정 ID |
| requiredPlanLevel | string | Yes | 접근에 필요한 최소 플랜 레벨 (예: FAN_CLUB) |

**Response 200**:
```json
{
  "accountId": "string",
  "requiredPlanLevel": "FAN_CLUB",
  "allowed": true,
  "activePlanLevel": "FAN_CLUB"
}
```

**Response 200** (접근 거부):
```json
{
  "accountId": "string",
  "requiredPlanLevel": "FAN_CLUB",
  "allowed": false,
  "activePlanLevel": "FREE"
}
```

**Errors**:
| Status | Code | 조건 |
|---|---|---|
| 400 | INVALID_PLAN_LEVEL | 알 수 없는 planLevel 값 |
| 401 | TOKEN_INVALID | 토큰 없음/만료/서명 불일치 (caller 재발급 후 재시도) |
| 403 | TENANT_FORBIDDEN | tenant_id mismatch (caller 가 다른 테넌트 토큰을 사용) |
| 503 | SERVICE_UNAVAILABLE | membership-service 내부 오류 |

---

## Caller Constraints (community-service)

- **타임아웃**: 연결 2s / 응답 3s
- **재시도**: 없음 — fail-closed 정책 (503 시 접근 거부)
- **Circuit Breaker**: 실패율 50% / 10초 슬라이딩 윈도우 → OPEN → fallback `allowed=false`
- **캐시**: 응답 캐시 금지 — 구독 만료 즉시 반영 필요
- **403 처리**: `allowed=false` 응답은 정상 처리, HTTP 403을 사용자에게 반환
- **(TASK-BE-253) OAuth token 자동 갱신**: Spring Security `OAuth2AuthorizedClientManager` 가 토큰 만료/401 응답 시 token endpoint 재호출 후 1회 재시도

---

## Server Constraints (membership-service)

- 이 엔드포인트는 **읽기 전용** — 상태 변경 없음
- `accountId`에 대한 ACTIVE 구독 중 `planLevel >= requiredPlanLevel` 여부만 판단
- 응답 시간 목표: p95 < 30ms (DB 단순 조회)
- (TASK-BE-253) `spring-boot-starter-oauth2-resource-server` 로 모든 `/internal/membership/*`, `/api/membership/*` 요청에 대해 GAP 의 JWKS 로 토큰을 검증하고 `tenant_id=fan-platform` 인 토큰만 통과시킨다 (기타 테넌트 → 403)
