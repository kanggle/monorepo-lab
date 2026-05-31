# Internal HTTP Contract: auth-service → admin-service

auth-service가 **assume-tenant** RFC 8693 token-exchange 발급 시점에 운영자의 D1 assignment 를 확인한다 (ADR-MONO-020 § 3.3 step 2, D2).

**호출 방향**: auth-service (client) → admin-service (server)
**노출 경로**: `/internal/operator-assignments/*` — 게이트웨이 퍼블릭 라우트에 노출 금지 ([rules/domains/saas.md](../../../../../../rules/domains/saas.md) S2)
**인증** (TASK-BE-327 호출측/수신측): `Authorization: Bearer <GAP client_credentials JWT>` — auth-service 가 `auth-service-client` 로 GAP `/oauth2/token` 에서 발급받아 첨부하고 ([GapClientCredentialsTokenProvider] 재사용), admin-service 가 GAP JWKS 서명 + issuer 로 검증한다. 정적 토큰 경로 없음. JWT 미제시/무효 시 모든 `/internal/**` 요청은 401 `UNAUTHORIZED` 로 fail-closed (account-service 의 `/internal/**` 체인 미러링).

> **TASK-BE-327 (ADR-MONO-020 D2)** — 이 edge 는 assume-tenant 발급 시점의 **1회성(one-shot) read** 이다. 도메인→GAP 의 per-request callback 이 **아니다** (ADR-020 § 3.1 은 후자만 금지한다; assignment store(D1)·assume-tenant 발급(D2)·entitled_domains 도출(D3) 은 모두 GAP 내부에 머무르므로 auth↔admin 조율은 GAP-internal). admin_actions row 를 쓰지 않는다 (read-only — ADR-014 token-exchange "not audited" 규칙과 동일).

---

## GET /internal/operator-assignments/check

선택된 customer tenant 에 대한 운영자의 **effective tenant scope** 포함 여부를 확인한다. auth-service 가 assume-tenant 토큰을 발급하기 전 fail-closed 게이트로 호출한다.

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `oidcSubject` | string (UUID) | Yes | 운영자의 GAP OIDC subject (`sub` = account_id). `admin_operators.oidc_subject` 로 운영자 row 를 fail-closed 조회 |
| `tenantId` | string (slug) | Yes | 선택된(assume 대상) customer tenant id |

**Response 200**:
```json
{
  "assigned": true
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `assigned` | boolean | 운영자의 effective tenant scope 가 `tenantId` 를 포함하면 `true`, 아니면 `false` |

**판정 규칙** (server-side, admin-service):

1. `oidcSubject` → `admin_operators` row 조회 (`AdminOperatorPort.findByOidcSubject`). row 없음 OR 비-`ACTIVE` → `assigned=false` (fail-closed; 운영자 존재 여부를 boolean 너머로 노출하지 않는다).
2. platform-scope 운영자 (`tenant_id == '*'`, `isPlatformScope()`) → 비어있지 않은 모든 `tenantId` 에 대해 `assigned=true` (sentinel 이 모든 tenant 를 부여; assumed 토큰은 여전히 선택된 구체 `tenant_id` 를 운반한다 — `'*'` 토큰은 절대 발급되지 않는다).
3. 그 외 → `assigned = TenantScopeResolver.resolveEffectiveTenantScope(internalId, homeTenant).contains(tenantId)` (D1 assignment rows ∪ {legacy home tenant} — BE-326 dual-read).
4. `tenantId` blank → `assigned=false`.

**Side Effect**: 없음 (read-only — `admin_actions` row 미기록).

**Errors**:

| Status | 조건 |
|---|---|
| 401 `UNAUTHORIZED` | GAP client_credentials JWT 미제시/무효 (`/internal/**` 체인 fail-closed) |
| 400 `VALIDATION_ERROR` | `oidcSubject`/`tenantId` 파라미터 누락 |

운영자 미존재/비-ACTIVE/미할당은 모두 `200 {assigned:false}` 로 응답한다 (열거 방어; 별도 4xx 로 구분하지 않는다).

---

## Caller Constraints (auth-service 측 — **fail-CLOSED**)

- 타임아웃: 연결 3s, 읽기 5s (account edge 와 동일 정책)
- 재시도: 2회 (지수 백오프 + jitter). 4xx 는 재시도 금지
- Circuit breaker: 실패율 50% / 10초 sliding window → open → half-open
- **⚠️ fail-CLOSED**: admin-service 장애 시(`assigned=false` / 4xx / 5xx / circuit-open / timeout / IO 모두) **assume-tenant 발급 거부** (`AssumeTenantDeniedException` → RFC 8693 `invalid_grant` / 400, 토큰 미발급). 이 게이트는 **절대 fail-soft 하지 않는다** — account-service `entitled_domains` 도출(fail-soft)과 정반대 정책이다. 인가 게이트이므로 가용성에 의존해 토큰을 발급해서는 안 된다 (격리 위반 = isolation breach).
- **감사 기록 없음**: read 이므로 "audit first" 가 적용되지 않는다 (admin-to-account 의 lock/unlock 명령과 다름).
