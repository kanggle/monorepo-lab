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

## POST /internal/tenant-domain-subscriptions

> **TASK-BE-342 (ADR-MONO-023 § 3.3 step 2 — D3 mutation surface).** 신규 구독 생성(`subscribe`). 호출 방향: admin-service(operator-facing `subscription.manage` 게이트 통과 후 위임) → account-service. **entitlement 평면의 쓰기는 account-service 가 소유**(ADR-023 D3-A); admin-service 는 RBAC 게이트 + 감사만 담당하고 본 endpoint 로 위임한다(account-service 는 IAM 을 읽지 않음 — ADR-023 D2).

**Request Body**:
```json
{
  "tenantId": "acme-corp",
  "domainKey": "scm",
  "status": "ACTIVE",
  "actorType": "operator",
  "actorId": "op-123",
  "reason": "신규 계약"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `tenantId` | string | Yes | 구독 보유 테넌트 id (`tenants.tenant_id` FK — 미존재 시 404). |
| `domainKey` | string | Yes | 구독 대상 product/domain key (`wms`\|`scm`\|`erp`\|`finance`). |
| `status` | string | No | 생성 상태 — `PENDING` 또는 `ACTIVE`(기본). `SUSPENDED`/`CANCELLED` 로는 생성 불가. |
| `actorType` | string | No | `operator`(기본) \| `system`. |
| `actorId` | string | No | 운영자 식별자(감사·이벤트용). |
| `reason` | string | No | 운영 사유. |

**Response 201 Created**: `{ "tenantId", "domainKey", "previousStatus": null, "currentStatus", "occurredAt" }`

발행: `tenant.subscription.changed` (previousStatus=null).

---

## PATCH /internal/tenant-domain-subscriptions/{tenantId}/{domainKey}

> **TASK-BE-342 (ADR-MONO-023 D1/D4).** 기존 구독의 상태 전이(`suspend`/`resume`/`cancel`). 전이는 `SubscriptionStatus` 상태머신 가드(ADR-023 D1: PENDING→ACTIVE|CANCELLED; ACTIVE→SUSPENDED|CANCELLED; SUSPENDED→ACTIVE|CANCELLED; CANCELLED terminal)를 통과해야 한다.

**Request Body**:
```json
{ "status": "SUSPENDED", "actorType": "operator", "actorId": "op-123", "reason": "미납" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | string | Yes | 목표 상태 — `ACTIVE`\|`SUSPENDED`\|`CANCELLED`. |
| `actorType` | string | No | `operator`(기본) \| `system`. |
| `actorId` | string | No | 운영자 식별자. |
| `reason` | string | No | 운영 사유. |

**Response 200 OK**: `{ "tenantId", "domainKey", "previousStatus", "currentStatus", "occurredAt" }`

발행: `tenant.subscription.changed`.

> **평면 분리 (ADR-023 D2)**: `SUSPENDED`/`CANCELLED` 전이는 entitlement 평면만 바꾼다 — 그 테넌트의 도메인이 카탈로그(ADR-019 D4) + 다음-발급 `entitled_domains`(ADR-019 D5)에서 빠지지만, operator 할당·RBAC(admin-service)는 **보존**된다. `SUSPENDED→ACTIVE` 재개는 재부여 없이 접근을 복구한다(GCP billing↔IAM parity). 이미-발급된 단기 토큰은 즉시 폐기되지 않고 짧은 TTL 로 만료된다(ADR-019 ③).

**Errors** (POST/PATCH 공통):

| Status | Code | Condition |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수 필드 누락/형식 오류, 또는 생성 시 `status` 가 `creatable`(PENDING\|ACTIVE) 밖. |
| 401 | `UNAUTHORIZED` | `Authorization: Bearer` JWT 누락/무효. |
| 404 | `TENANT_NOT_FOUND` | `tenantId` 미등록 테넌트(POST). |
| 404 | `SUBSCRIPTION_NOT_FOUND` | 대상 구독 부재(PATCH). |
| 409 | `SUBSCRIPTION_ALREADY_EXISTS` | (POST) 동일 `(tenantId, domainKey)` 구독이 이미 존재 — 전이는 PATCH 사용. |
| 409 | `SUBSCRIPTION_TRANSITION_INVALID` | (PATCH) `SubscriptionStatus` 가드 위반(예: CANCELLED→ACTIVE, PENDING→SUSPENDED, self). |

---

## Caller Constraints (admin-service 측)

- 타임아웃: 연결 3s, 읽기 10s (`AccountServiceTenantClient` 와 동일).
- 재시도 / Circuit breaker: `accountService` 설정 공유 (`@Retry`/`@CircuitBreaker`).
- 읽기(GET)는 감사 row 없음; mutation(POST/PATCH) 운영자 행위 감사는 admin-service `subscription.manage` 게이트에서 `AdminActionAuditor` 로 기록(ADR-023 D3, step 2b).

## Server Constraints (account-service 측)

- GET: Read-only; ACTIVE 구독만 반환 (SUSPENDED/CANCELLED 제외).
- POST/PATCH: mutation 은 `@Transactional` 경계에서 (a) `SubscriptionStatus` 가드 적용 → (b) 저장 → (c) `tenant.subscription.changed` outbox 이벤트 발행을 원자적으로 수행한다. account-service 는 IAM(admin_db RBAC/할당)을 읽지 않는다 (ADR-023 D2 단방향 의존).
