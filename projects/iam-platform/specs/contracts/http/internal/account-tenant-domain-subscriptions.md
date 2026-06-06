# Internal HTTP Contract: Tenant↔Domain Subscription read (account-service)

> **TASK-BE-322 (ADR-MONO-019 § 3.3 step 1 — D2 entitlement authority).**
> account-service owns the `tenant_domain_subscription` N:M table (which
> tenants are entitled to which federated product/domain). admin-service reads
> ACTIVE subscriptions here to derive the platform-console product catalog
> `tenants[]` binding (ADR-019 **D4**; producer surface
> [`console-registry-api.md` § Subscription-driven domain binding](../console-registry-api.md)).

**호출 방향**: admin-service (client) → account-service (server)
**노출 경로**: `/internal/tenant-domain-subscriptions`
**인증** (TASK-BE-318b 호출측 / TASK-BE-319b 수신측): `Authorization: Bearer <IAM client_credentials JWT>` — admin-service 가 `admin-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고, account-service 가 JWKS 서명 + issuer 로 검증한다. 신규 `/internal/` 경로이므로 기존 `InternalApiFilter` + OAuth2 resource-server `.authenticated()` 게이트가 자동 적용되며 별도 보안 설정은 없다. 정적 `X-Internal-Token` 은 미사용.

---

## GET /internal/tenant-domain-subscriptions

ACTIVE 상태인 tenant↔domain 구독 전체를 조회한다. 선택적으로 `domainKey` 로 필터링한다. Read-only — 감사 row 없음 (`TenantAccountQueryUseCase` / `ListTenantsUseCase` read-path 정책 동일).

**Query Parameters**:

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `domainKey` | string | No | 단일 도메인키 필터 (`iam`\|`wms`\|`scm`\|`erp`\|`finance`). 생략/공백이면 전체 ACTIVE 구독 반환. |
| `tenantId` | string | No | 단일 테넌트 id 필터 — 그 테넌트의 ACTIVE 구독만 반환. auth-service 발급-시점 entitled_domains populate(ADR-019 keystone)용 역조회. 생략/공백이면 필터 미적용. |

> **`tenantId` + `domainKey` 조합**: 둘 다 지정 시 AND 로 합성된다 (그 테넌트의, 그 domainKey 인 ACTIVE 구독). `tenantId` 역조회(TASK-BE-324 ADR-019 § 3.3 keystone)는 auth-service `TenantClaimTokenCustomizer` 가 authorization_code/refresh_token 발급 시 그 테넌트의 ACTIVE domainKey 목록을 받아 서명된 `entitled_domains` claim 으로 주입하는 데 쓰인다 (실패/빈 결과 → claim 생략 fail-soft, net-zero).

**Response 200 OK**:
```json
{
  "items": [
    { "tenantId": "wms", "domainKey": "wms" },
    { "tenantId": "scm", "domainKey": "scm" },
    { "tenantId": "erp", "domainKey": "erp" },
    { "tenantId": "finance", "domainKey": "finance" }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `items[].tenantId` | string | 구독 보유 테넌트 id (`tenants.tenant_id` FK) |
| `items[].domainKey` | string | 구독 대상 product/domain key (catalog key; tenant id 아님) |

> **`iam` 부재 (의도적)**: `iam` product 는 admin-side 에서 `bindsAllTenants=true` 로 전체 등록 테넌트를 federate 하므로 이 표면을 조회하지 않는다. 구독 테이블에 `iam` 행을 시드하면 이중계산이 되므로 backward-compat 시드는 `iam` 을 포함하지 않는다.

> **Net-zero (ADR-019 step 1)**: backward-compat 시드(`(wms,wms)`/`(scm,scm)`/`(erp,erp)`/`(finance,finance)` self-subscription)로 인해 consumer(admin) catalog derivation 결과가 기존과 byte-identical 하다.

> **Net-positive (ADR-019 step 2 — TASK-BE-325)**: step 2 부터 실 고객 테넌트(예: `acme-corp`)가 이 표면을 통해 catalog `tenants[]` 에 도메인-슬러그와 함께 등장한다. 응답 envelope shape(`items[].tenantId` + `items[].domainKey`)과 `tenants: string[]` 은 불변(ADR-019 D4); values 만 실 고객 id 를 포함하도록 확장된다.

**Errors**:

| Status | Code | Condition |
|---|---|---|
| 401 | `UNAUTHORIZED` | `Authorization: Bearer` JWT 누락/무효 (resource-server fail-closed) |

> admin-service 측에서 account-service 5xx/timeout/CB-open 은 `DownstreamFailureException` → registry `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` 으로 매핑된다 (부분 카탈로그 미반환; `console-registry-api.md § Errors` 정합).

---

## Caller Constraints (admin-service 측)

- 타임아웃: 연결 3s, 읽기 10s (`AccountServiceTenantClient` 와 동일).
- 재시도 / Circuit breaker: `accountService` 설정 공유 (`@Retry`/`@CircuitBreaker`).
- 읽기 전용 — 감사 row 없음.

## Server Constraints (account-service 측)

- Read-only; 상태 변경 없음 (구독 관리 표면은 후속 step).
- ACTIVE 구독만 반환 (SUSPENDED 구독 제외).
