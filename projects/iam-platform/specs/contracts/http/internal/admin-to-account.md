# Internal HTTP Contract: admin-service → account-service

admin-service가 운영자 명령으로 account-service에 계정 상태 변경(lock/unlock/delete)을 요청한다.

**호출 방향**: admin-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*`
**인증** (TASK-BE-318b 호출측 / TASK-BE-319b 수신측): `Authorization: Bearer <IAM client_credentials JWT>` — admin-service 가 `admin-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고, account-service 가 JWKS 서명 + issuer 로 검증한다. 정적 `X-Internal-Token` 은 제거됨.

---

## Tenant Confinement — `X-Tenant-Id` (TASK-BE-467)

모든 **변이(mutation) 엔드포인트** (`/lock`, `/unlock`, `/delete`, `/gdpr-delete`, `/export`) 는 선택적 `X-Tenant-Id` 헤더로 대상 계정을 **행위자의 활성 테넌트**에 가둔다. 이는 읽기 경로(`GET /internal/accounts` 의 `tenantId` 쿼리, TASK-BE-357)와 동일한 자세이며, 변이 경로를 읽기 경로와 **테넌트 패리티**로 맞춘다.

- **헤더 존재 + 구체 slug**: account-service 는 `findById(TenantId.of(header), accountId)` 로 조회한다. 대상 계정이 **다른 테넌트**에 있으면 tenant-scoped 조회가 empty 를 반환 → **`404 ACCOUNT_NOT_FOUND`** (enumeration-safe: 타 테넌트 존재를 확인해 주는 403 을 절대 반환하지 않는다). 계정은 변이되지 않는다.
  🔵 **TASK-BE-616**: 헤더가 **소비자 사이트**면 그 사이트의 ACTIVE 멤버인 **풀 계정**도 찾는다([multi-tenancy.md § 소비자 계정 풀 § 5](../../features/multi-tenancy.md) «단건 표면까지»).
  그 사이트의 멤버(멤버십 `ACTIVE` 또는 `LOCKED` — `TASK-BE-621`)가 아닌 풀 계정 → 여전히 `404`. 🔵 616 은 «잠금 · 해제도 풀 계정 하나에» 였고, 아래 621 이 바꿨다.
  🔴 **잠금 · 해제도 예외 — `TASK-BE-621` (소유자 결정 2026-10-04 «사이트 운영자가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다. 계정 전체 잠금은 플랫폼 관리자만.»)**:
  헤더가 소비자 사이트이고 대상이 그 사이트의 **풀 멤버**면 `/lock` · `/unlock` 은 계정을 바꾸지 **않고** 그 사이트 멤버십만 `LOCKED`/`ACTIVE` 로 만든다
  (사유와 무관 — 이름 있는 사이트 헤더로 `/lock` 을 부르는 호출자는 admin-service 뿐). 응답 `scope = SITE_MEMBERSHIP`(아래 각 엔드포인트). 그 사이트의 **자기 계정**(풀 아님)은 지금처럼 계정을 잠근다.
  🔴 **삭제는 예외 — `TASK-BE-619` (소유자 결정 2026-10-03 «사이트 운영자 삭제 권한 = 자기 사이트 멤버십만»)**: 헤더가 소비자 사이트이고 대상이 그 사이트의
  **풀 멤버**면 `/gdpr-delete` · `/delete` 는 계정을 지우지 **않고** 그 사이트 멤버십만 `LEFT`(`left_by = OPERATOR`, `left_by_actor_id = operatorId`)로 만든다.
  응답의 `scope = SITE_MEMBERSHIP` 이 그것을 말한다(아래 각 엔드포인트). 그 사이트의 **자기 계정**(풀 아님)은 지금처럼 삭제된다.
- **헤더 부재 OR 공백 OR `'*'` (SUPER_ADMIN 플랫폼 스코프)** — 엔드포인트에 따라 둘로 갈린다:
  - **`/lock` · `/unlock` · `/delete` (TASK-MONO-735)**: (🔵 `TASK-BE-621`: `/lock` · `/unlock` 의 이 갈래가 «계정 전체 잠금 = 플랫폼 관리자» 의 경로다 — admin-service 는 플랫폼 스코프 운영자의 잠금·해제에 활성 테넌트 대신 **항상** `'*'` 를 찍는다. 자동 잠금 · 본인 복구 해제는 헤더를 싣지 않아 이 갈래다.) 계정 **행 자신의 테넌트**로 찾는다(`AccountRepository.findByIdResolvingTenant` — [multi-tenancy.md § 격리 회귀 방지](../../../features/multi-tenancy.md#격리-회귀-방지) 의 문서화된 예외 2번째 사용). `accounts.id` 는 전역 유일 PK 라 결과는 최대 한 행이고 테넌트를 섞지 않는다. 어느 테넌트에도 없는 id → `404 ACCOUNT_NOT_FOUND`. `fan-platform` 계정은 결과가 이전과 같다. 🔴 이전(BE-467~MONO-735)엔 `fan-platform` 기본값이라 SUPER_ADMIN(`'*'`)과 헤더 없는 호출자는 **`fan-platform` 밖 계정을 잠그지 못했다**(2026-09-26 16차 창 실측 — `ecommerce` 계정 잠금 404).
  - **`/gdpr-delete` (`TASK-BE-619`)**: `/lock` · `/unlock` · `/delete` 와 **같다** — 계정 행 자신의 테넌트로 찾고(finder 의 세 번째 소비처), **계정 자체를** 지운다
    (풀 계정이면 모든 소비자 사이트에서 — `scope = ACCOUNT`). 이것이 «풀 계정 삭제는 플랫폼 관리자» 의 경로다: admin-service 는 **플랫폼 스코프 운영자**(SUPER_ADMIN)의
    GDPR 삭제에 활성 테넌트 대신 `'*'` 를 찍는다([admin-api.md § gdpr-delete](../admin-api.md)). 619 이전엔 `fan-platform` 기본값이라 SUPER_ADMIN 의 비-fan 계정 GDPR 삭제는 404 였다.
  - **`/export`**: 변경 없음 — `fan-platform` 기본값(BE-467 net-zero). 🔴 SUPER_ADMIN 의 비-fan 계정 export 는 그래서 아직 404 다(기록).
- **헤더 존재 + 구체 slug 는 위 첫 줄 그대로**다 — 계정 행 해소는 **헤더가 테넌트를 말하지 않을 때만** 쓴다. 구체 테넌트를 말한 호출은 결코 다른 테넌트의 계정을 건드리지 않는다(교차 → 404).

admin-service 는 `QueryTenantScopeGate` (읽기 경로와 공유) 로 행위자의 활성 테넌트를 해소해 이 헤더를 스탬프한다. out-of-scope 테넌트 요청은 account-service 도달 전에 admin-service 에서 `403 TENANT_SCOPE_DENIED` 로 차단된다 (best-effort DENIED `admin_actions` row). account-service 측 `X-Tenant-Id` 처리는 defense-in-depth 이며, 새로운 cross-tenant finder 를 추가하지 않는다.

---

## GET /internal/accounts

테넌트 스코프 계정 목록 페이지네이션 조회. admin-service가 `account.read` 권한 보유 운영자의 요청을 대리하여 호출한다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `tenantId` | string (**required**, TASK-BE-357) | 조회 대상 테넌트. account-service는 이 값으로만 필터한다 (effective-scope 게이트는 admin-service가 호출 전 수행 — lock/unlock 과 동일한 internal-trust 자세). `*` (SUPER_ADMIN 전용, admin-service에서 게이트됨) → 전 테넌트 목록. 누락/공백 → `400 VALIDATION_ERROR` (암묵적 cross-tenant 스캔 금지, fail-closed). |
| `status` | enum (optional, TASK-BE-475) | 계정 상태 필터 `ACTIVE`/`LOCKED`/`DORMANT`/`DELETED`. 미지정 → 전체 상태. admin-service가 허용 목록을 이미 검증하므로 정상 흐름에서는 유효값만 도달하나, account-service도 fail-closed 로 파싱한다(허용 외 → `400 VALIDATION_ERROR`). `"*"` 전 테넌트 분기 포함. `email` 단건 조회 시 무시. |
| `page` | int (default 0) | 페이지 번호 |
| `size` | int (default 20, max 100) | 페이지 크기 |

**Response 200**:
```json
{
  "content": [
    {
      "id": "string",
      "email": "string",
      "status": "ACTIVE",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 150,
  "page": 0,
  "size": 20,
  "totalPages": 8
}
```

**Errors**: 400 `VALIDATION_ERROR` (size > 100, 또는 `tenantId` 누락/공백)

---

## GET /internal/accounts?email=

테넌트 내 이메일 단건 조회. TASK-BE-357 이전에는 `tenant_id='fan-platform'` 에 하드코딩돼 있어 다른 테넌트(예: ecommerce) 계정이 이메일로 검색되지 않았다 — 이제 `tenantId` 로 스코프된다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `email` | string (required) | 조회할 이메일 (정확 일치 — `(tenant_id, email)` 유니크 인덱스, 부분/LIKE 검색 아님) |
| `tenantId` | string (**required**, TASK-BE-357) | 조회 대상 테넌트. 특정 테넌트 → 해당 테넌트 내 정확 일치(0 또는 1행). `*` (SUPER_ADMIN 전용) → 전 테넌트에서 동일 이메일 매칭(테넌트마다 별도 행이 있을 수 있어 0..N행). 누락/공백 → `400 VALIDATION_ERROR`. |
| `excludePoolMembers` | boolean (optional, default `false`, TASK-BE-615) | `true` → 그 테넌트의 **자기 계정만**(소비자 계정 풀 멤버 제외 — `iam.consumer-pool.enabled` 와 무관하게 풀 이전 쿼리). admin-service `CreateOperatorUseCase` 의 «대상 테넌트에 가입 계정이 있나» 확인이 보낸다(소유자 결정 2026-10-01: 운영자 생성은 옛 규칙 — 운영자 계정 규칙은 `ADR-MONO-080` 후보 `TASK-MONO-746` 의 몫). 콘솔 계정 운영 검색은 보내지 않는다 — [multi-tenancy.md § 소비자 계정 풀 § 5](../../features/multi-tenancy.md) 대로 풀 멤버 포함. 목록 분기(`email` 없음)에도 같은 뜻 |

**Response 200** (특정 테넌트 단건 매칭):
```json
{
  "content": [
    {
      "id": "string",
      "email": "string",
      "status": "ACTIVE",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

**Errors**: 400 `VALIDATION_ERROR` (`tenantId` 누락/공백)

---

## POST /internal/accounts/{accountId}/lock

운영자에 의한 계정 잠금.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 잠금 대상 |

**Headers**:
- `Idempotency-Key: {admin_action_request_id}` (필수)
- `X-Operator-ID: {operator_id}`
- `X-Tenant-Id: {active_tenant}` (선택, TASK-BE-467 — 부재/`'*'` → 계정 행의 테넌트(TASK-MONO-735; 이전엔 `fan-platform` 기본); [Tenant Confinement](#tenant-confinement--x-tenant-id-task-be-467) 참조)

**Request**:
```json
{
  "reason": "ADMIN_LOCK",
  "operatorId": "string",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "changedAt": "2026-04-12T10:00:00Z",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

> 🔵 시각 필드의 실제 이름은 `changedAt` 이다(`StatusChangeResponse` — 이 문서의 옛 예시 `lockedAt` 은 구현과 달랐다. admin-service 는 그것이 없으면 자기 완료 시각을 쓴다).

**`scope` (`TASK-BE-621`, 소유자 결정 2026-10-04)** — `/unlock` 도 같다:

| 호출 | 대상 | 일어나는 일 | 응답 |
|---|---|---|---|
| `X-Tenant-Id` = 소비자 사이트 (사이트 운영자) | 그 사이트의 **풀 멤버** | 그 사이트 멤버십만 `ACTIVE → LOCKED`(`locked_at` · `locked_by_actor_id = operatorId`). 계정 · 다른 사이트 멤버십 · 그 사이트 역할 무변경. **이벤트 · `account_status_history` 없음** | `scope = SITE_MEMBERSHIP`, `previousStatus`·`currentStatus` = **멤버십** 상태, `siteTenantId` = 그 사이트 |
| `X-Tenant-Id` = 구체 테넌트 | 그 테넌트의 **자기 계정** | 계정 잠금(기존) | `scope = ACCOUNT`, `siteTenantId = null` |
| `X-Tenant-Id` 없음 · 공백 · `*` (플랫폼 관리자 · 자동 잠금) | 계정 행 자신의 테넌트로 찾은 계정(풀 계정 포함) | 계정 잠금 — 풀 계정이면 모든 소비자 사이트에서 | `scope = ACCOUNT` |

- 사이트 범위의 멱등: 이미 `LOCKED` 인 멤버십 잠금 → 200, `previousStatus = currentStatus = LOCKED`(계정 상태 기계의 같은 상태 규칙과 같다).
- 사이트 범위인데 **계정**이 `DELETED` → `409 STATE_TRANSITION_INVALID`.
- 옛 account-service(필드 없음)는 `scope` 를 내지 않는다 — 호출자는 없음을 `ACCOUNT` 로 읽는다.

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED · 허용되지 않는 전이), 404 `ACCOUNT_NOT_FOUND` (존재하지 않거나 **cross-tenant** 대상 — `X-Tenant-Id` ≠ 계정 테넌트, BE-467 · 그 사이트 멤버가 아닌(`LEFT` 포함) 풀 계정)

**Note**: security-to-account의 lock과 같은 엔드포인트를 공유하되, `reason` 필드로 구분 (`ADMIN_LOCK` vs `AUTO_DETECT`). Idempotency-Key 네임스페이스는 다름.

---

## POST /internal/accounts/{accountId}/unlock

운영자에 의한 계정 잠금 해제.

**Headers**: Idempotency-Key + X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Request**:
```json
{
  "reason": "ADMIN_UNLOCK",
  "operatorId": "string",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "LOCKED",
  "currentStatus": "ACTIVE",
  "changedAt": "2026-04-12T10:00:00Z",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

- **`TASK-BE-621`** — `scope` 는 `/lock` 과 같은 표. 사이트 범위 해제 = 그 사이트 멤버십 `LOCKED → ACTIVE`(잠금 기록 지움). 🔴 사이트 운영자는 **계정 전체 잠금을 풀지 못한다** — 사이트 범위 해제는 멤버십만 보므로, 멤버십이 `ACTIVE` 면 멱등 200(`ACTIVE`/`ACTIVE`)이고 계정은 `LOCKED` 그대로다. 계정 해제는 헤더 없음/`*`(플랫폼 관리자 · 본인 복구).

**Errors**: 409 `STATE_TRANSITION_INVALID` (허용되지 않는 전이 — 예: `USER_RECOVERY` 로 운영자 잠금 해제), 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

---

## POST /internal/accounts/{accountId}/delete

운영자에 의한 강제 삭제 (유예 진입).

**Headers**: Idempotency-Key + X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Request**:
```json
{
  "reason": "ADMIN_DELETE | REGULATED_DELETION",
  "operatorId": "string",
  "ticketId": "string (optional)"
}
```

**Response 202 Accepted**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE | LOCKED | DORMANT",
  "currentStatus": "DELETED",
  "gracePeriodEndsAt": "2026-05-12T10:00:00Z",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

- **`TASK-BE-619`** — `scope = SITE_MEMBERSHIP`: 헤더가 소비자 사이트이고 대상이 그 사이트의 풀 멤버 → 그 사이트 멤버십만 `LEFT`(`OPERATOR`). 계정은 그대로라
  `previousStatus = currentStatus` = 계정의 지금 상태, `gracePeriodEndsAt = null`, `siteTenantId` = 그 사이트. 그 밖에는 `scope = ACCOUNT`, `siteTenantId = null`(기존 의미).

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

---

## GET /internal/accounts/{accountId}/status

계정 상태 조회. admin-service가 명령 전 현재 상태 확인 용도.

auth-to-account.md의 동일 엔드포인트 공유.

---

## POST /internal/accounts/{accountId}/gdpr-delete

GDPR/PIPA 삭제권. 계정 상태를 DELETED로 전이하고 PII를 즉시 마스킹한다.

**Headers**: Idempotency-Key + X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Request**:
```json
{
  "reason": "REGULATED_DELETION",
  "operatorId": "string"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "status": "DELETED",
  "emailHash": "string (SHA-256 hex)",
  "maskedAt": "2026-04-18T10:00:00Z",
  "scope": "ACCOUNT | SITE_MEMBERSHIP",
  "siteTenantId": "string | null"
}
```

**`scope` (`TASK-BE-619`, 소유자 결정 2026-10-03)**:

| 호출 | 대상 | 일어나는 일 | 응답 |
|---|---|---|---|
| `X-Tenant-Id` = 소비자 사이트 (사이트 운영자) | 그 사이트의 **풀 멤버** | 그 사이트 멤버십만 `LEFT`(`left_by = OPERATOR`) — **아무것도 지우지 않는다**(계정 · PII · 다른 사이트 멤버십 무변경, 이벤트 없음) | `scope = SITE_MEMBERSHIP`, `status` = 계정의 지금 상태, `emailHash` · `maskedAt` = `null`, `siteTenantId` = 그 사이트 |
| `X-Tenant-Id` = 구체 테넌트 | 그 테넌트의 **자기 계정** | 아래 Server-side behavior 그대로 | `scope = ACCOUNT` |
| `X-Tenant-Id` 없음 · 공백 · `*` (플랫폼 관리자) | 계정 행 자신의 테넌트로 찾은 계정(풀 계정 포함) | 아래 그대로 — 풀 계정이면 모든 소비자 사이트에서 삭제 | `scope = ACCOUNT` |

옛 account-service(필드 없음)는 `scope` 를 내지 않는다 — 호출자는 없음을 `ACCOUNT` 로 읽는다.

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

**Server-side behavior**:
1. AccountStatusMachine.transition() 경유 DELETED 전이
2. 이메일을 SHA-256 해시로 교체 (email_hash 컬럼에 원본 해시 저장)
3. 프로필 PII 필드 NULL 처리 (displayName, phoneNumber, birthDate)
4. deleted_at, masked_at 타임스탬프 기록
5. account.deleted 이벤트 발행 (anonymized=true)

---

## GET /internal/accounts/{accountId}/export

계정 개인 데이터 내보내기.

**Headers**: X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Response 200**:
```json
{
  "accountId": "string",
  "email": "string",
  "status": "string",
  "createdAt": "2026-01-01T00:00:00Z",
  "profile": {
    "displayName": "string",
    "phoneNumber": "string",
    "birthDate": "1990-01-15",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul"
  },
  "exportedAt": "2026-04-18T10:00:00Z"
}
```

**Errors**: 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

---

## Org-Node command gateway (`/internal/org-nodes/*`) — TASK-BE-490 (ADR-MONO-047)

> account-service 가 `tenants` 를 소유하므로 **`org_node` 트리도 소유**한다(ADR-MONO-047 § D6). admin-service 는 `org.manage` 게이트 + 운영자 감사만 담당하는 **thin command gateway** 이며 트리를 저장하지 않고 아래 엔드포인트로 위임한다. cycle/depth/subset(child ⊆ parent) 불변식은 **account-service 서버측에서 강제**되며 — admin-service 는 이를 중복 검사하지 않고 422 를 통과 매핑한다(ADR-MONO-047 § D2·D4). 이 서브트리는 파일 상단의 `/internal/**` 인증 게이트(IAM `client_credentials` Bearer JWT, JWKS+issuer 검증, fail-closed)를 그대로 적용받는다.

### Ceiling wire shape (공통)

```json
{ "mode": "UNBOUNDED" }
{ "mode": "BOUNDED", "domains": ["wms", "erp"] }
```

`UNBOUNDED` = ceiling 없음 = 교집합 항등원("모든 도메인"이 **아님**). `BOUNDED` + `domains: []` = 아무것도 허용 안 함(fail-closed). 둘은 정반대이며 `mode` 로 구분된다.

## GET /internal/org-nodes

전체 org-node **flat** 목록(nested 아님). admin-service 가 `org.manage` reach 스코핑을 호출측에서 수행한다.

**Response 200**:
```json
{
  "items": [
    { "orgNodeId": "b3f1…", "parentId": null, "name": "Acme Corp", "depth": 1, "ceiling": { "mode": "UNBOUNDED" }, "createdAt": "2026-07-10T09:00:00Z", "updatedAt": "2026-07-10T09:00:00Z" }
  ]
}
```

**Errors**: 401 `UNAUTHORIZED` (Bearer JWT 누락/무효, fail-closed).

---

## POST /internal/org-nodes

신규 노드 생성. child ⊆ parent ceiling / depth ≤ 5 는 서버측 강제.

**Request**:
```json
{ "name": "Acme WMS 사업부", "parentId": "b3f1…", "ceiling": { "mode": "BOUNDED", "domains": ["wms"] } }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string | Yes | 1~100 자, 유니크 아님. |
| `parentId` | string \| null | Yes | 부모 UUID 또는 `null`(ROOT). |
| `ceiling` | object | Yes | ceiling wire shape; `⊆ effectiveCeiling(parent)`. |

**Response 201**: 생성된 org-node (GET item shape).

**Errors**:

| Status | Code | Condition |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `name` 길이, ceiling 형식(`BOUNDED` 인데 `domains` 누락 등). |
| 401 | `UNAUTHORIZED` | Bearer JWT 누락/무효. |
| 404 | `ORG_NODE_NOT_FOUND` | `parentId` 미존재. |
| 422 | `ORG_NODE_DEPTH_EXCEEDED` | depth > 5. |
| 422 | `ORG_NODE_CEILING_NOT_SUBSET` | `ceiling ⊄ effectiveCeiling(parent)`. |

---

## GET /internal/org-nodes/{orgNodeId}

**Path Parameters**: `orgNodeId` (UUID).

**Response 200**: 단건 org-node (GET item shape).

**Errors**: 401 `UNAUTHORIZED`, 404 `ORG_NODE_NOT_FOUND`.

---

## PATCH /internal/org-nodes/{orgNodeId}

rename 및/또는 re-parent. cycle/depth/subset 서버 강제.

**Request**:
```json
{ "name": "Acme 물류", "parentId": "d4e9…" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | string (optional) | — | 1~100 자. |
| `parentId` | string (optional) | — | 새 부모 UUID. |

**Response 200**: 변경된 org-node.

**Errors**:

| Status | Code | Condition |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 두 필드 모두 누락, `name` 길이. |
| 401 | `UNAUTHORIZED` | Bearer JWT 누락/무효. |
| 404 | `ORG_NODE_NOT_FOUND` | `orgNodeId` 또는 `parentId` 미존재. |
| 422 | `ORG_NODE_CYCLE` | re-parent 사이클. |
| 422 | `ORG_NODE_DEPTH_EXCEEDED` | subtree depth > 5. |
| 422 | `ORG_NODE_CEILING_NOT_SUBSET` | 노드(또는 후손) ceiling ⊄ 새 조상 체인. |

---

## DELETE /internal/org-nodes/{orgNodeId}

자식 노드·소속 tenant 가 모두 없어야 삭제. 서버측 강제.

**Response 204** (no content).

**Errors**:

| Status | Code | Condition |
|---|---|---|
| 401 | `UNAUTHORIZED` | Bearer JWT 누락/무효. |
| 404 | `ORG_NODE_NOT_FOUND` | `orgNodeId` 미존재. |
| 422 | `ORG_NODE_NOT_EMPTY` | 자식 노드 또는 소속 tenant 존재. |

---

## PUT /internal/org-nodes/{orgNodeId}/ceiling

노드 ceiling 전량 교체. `⊆ parent` / 모든 후손 `⊆ new` 서버 강제. (self-ceiling 편집 방지는 admin-service `strictlyAdministers` 게이트의 책임 — 이 내부 표면은 순수 command.)

**Request**: ceiling wire shape.
```json
{ "mode": "BOUNDED", "domains": ["wms", "erp"] }
```

**Response 200**: 변경된 org-node.

**Errors**:

| Status | Code | Condition |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `mode` 누락, `BOUNDED` 인데 `domains` 누락, 알 수 없는 도메인 키. |
| 401 | `UNAUTHORIZED` | Bearer JWT 누락/무효. |
| 404 | `ORG_NODE_NOT_FOUND` | `orgNodeId` 미존재. |
| 422 | `ORG_NODE_CEILING_NOT_SUBSET` | 새 ceiling ⊄ parent, 또는 후손 ⊄ 새 ceiling. |

---

## GET /internal/org-nodes/{orgNodeId}/tenants

노드 자신 + 모든 후손에 소속된 tenant id 목록.

**Response 200**:
```json
{ "tenantIds": ["acme-wms", "acme-erp"] }
```

**Errors**: 401 `UNAUTHORIZED`, 404 `ORG_NODE_NOT_FOUND`.

> **⚠ 권한 판정 경로 — fail-closed caller 제약 (ADR-MONO-047 § D5).** 이 엔드포인트는 `AdminGrantScopeEvaluator` 가 node-scoped grant 를 tenant 집합으로 **확장**하는 permission-check 경로에서 호출된다. 실패(5xx / timeout / CB-open)는 **반드시 EMPTY 집합**으로 resolve 해야 한다 — 절대 `'*'` 도, 전체 tenant 도 아니다. **실패를 permissive 하게 캐시하지 말 것.** fail 은 reach 를 좁힐 뿐 넓히지 않는다.

---

## GET /internal/org-nodes/{orgNodeId}/effective-ceiling

root→N 체인 ceiling 교집합. `ORG_ADMIN` grant cap(부여 도메인 ⊆ effectiveCeiling) 판정에 사용.

**Response 200**: ceiling wire shape.
```json
{ "mode": "BOUNDED", "domains": ["wms"] }
```

`UNBOUNDED` 는 체인의 모든 노드가 `UNBOUNDED` 일 때만 반환된다.

**Errors**: 401 `UNAUTHORIZED`, 404 `ORG_NODE_NOT_FOUND`.

> **fail-closed.** cap 계산 실패는 grant 를 **거부**(빈 ceiling = 아무 도메인도 부여 불가)로 resolve 한다 — 절대 `UNBOUNDED` 로 폴백하지 않는다.

---

## Tenant placement (`/internal/tenant-placements/*`) — TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07)

> 테넌트의 소속(`tenants.org_node_id`)을 읽고 바꾸는 **순수 command** 표면. 누가 바꿀 수 있는지(양쪽 관리자 판정)는 전부 admin-service 가 한다([admin-api.md](../admin-api.md) § Org Hierarchy «테넌트 소속 — 두기 · 옮기기 · 빼기») — 이 표면은 판정하지 않는다. account-service 가 하는 것은 **존재 확인**(테넌트 · 목적지 노드)과 **조건부 쓰기**(`expectedOrgNodeId`)와 **효과 계산**(구독 ∩ 상한)뿐이다.
>
> 🔴 **왜 `/internal/tenants/{tenantId}/**` 아래가 아닌가.** 게이트웨이가 `/internal/tenants/**` 를 account-service 로 라우팅하고 path `{tenantId}` ↔ JWT `tenant_id` 만 대조한다([gateway-api.md](../gateway-api.md)). 거기에 소속 쓰기를 두면 테넌트 자신의 워크로드 토큰이 `PUT /internal/tenants/{자기}/org-node` 로 **혼자 노드에서 빠져 상한을 벗어날 수 있다** — ADR 개정이 막은 바로 그 길이다. 그래서 게이트웨이 라우트가 없는 별도 접두사를 쓴다. 이 서브트리도 파일 상단의 `/internal/**` 인증 게이트(IAM `client_credentials` Bearer JWT)를 그대로 받는다.

### Placement effect wire shape (공통)

```json
{
  "tenantId": "acme-wms",
  "fromOrgNodeId": "b3f1…",
  "toOrgNodeId": "c7a2…",
  "domainsBefore": ["finance", "wms"],
  "domainsAfter": ["wms"],
  "lostDomains": ["finance"],
  "gainedDomains": []
}
```

`domainsBefore = ACTIVE 구독 ∩ effectiveCeiling(from)`, `domainsAfter = ACTIVE 구독 ∩ effectiveCeiling(to)` — 노드가 `null`(무소속)이면 상한 없음(`UNBOUNDED`, D7). 순서는 ACTIVE 구독 목록 순서. 구독 행은 읽기만 한다.

## GET /internal/tenant-placements/{tenantId}

테넌트의 지금 소속. admin-service 가 출발 쪽 판정에 쓴다.

**Response 200**:
```json
{ "tenantId": "acme-wms", "orgNodeId": "b3f1…" }
{ "tenantId": "fan-platform", "orgNodeId": null }
```

**Errors**: 401 `UNAUTHORIZED`, 404 `TENANT_NOT_FOUND`.

---

## GET /internal/tenant-placements/{tenantId}/preview

소속 변경의 효과 미리보기(쓰기 없음).

**Query parameters**: `orgNodeId` (optional) — 목적지 노드. **생략 = 무소속.**

**Response 200**: placement effect wire shape.

**Errors**: 401 `UNAUTHORIZED`, 404 `TENANT_NOT_FOUND`, 404 `ORG_NODE_NOT_FOUND`(목적지 노드 미존재).

---

## PUT /internal/tenant-placements/{tenantId}

소속 변경 — `Tenant.assignOrgNode()`.

**Request**:
```json
{ "orgNodeId": "c7a2…", "expectedOrgNodeId": "b3f1…" }
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `orgNodeId` | string \| null | Yes(키) | 목적지 노드, `null` = 무소속(빼기). |
| `expectedOrgNodeId` | string \| null | Yes(키) | admin-service 가 **판정한** 출발 위치(`null` = 무소속). 생략은 `null` 로 읽는다. |

**동작** (한 트랜잭션, 테넌트 행 `SELECT … FOR UPDATE`):

1. 테넌트 없음 → 404 `TENANT_NOT_FOUND`.
2. 지금 소속 ≠ `expectedOrgNodeId` → 409 `TENANT_ORG_NODE_CONFLICT`(쓰기 없음). 판정하지 않은 출발지에서 테넌트를 빼 오지 못하게 하는 조건이다.
3. 목적지 노드 없음 → 404 `ORG_NODE_NOT_FOUND`(쓰기 없음). 확인 뒤 커밋 전에 노드가 삭제되면 `tenants.org_node_id` FK 가 커밋을 막고 역시 404 `ORG_NODE_NOT_FOUND`.
4. 목적지 = 지금 소속 → 쓰기 없음, `changed=false`(멱등).
5. 아니면 `Tenant.assignOrgNode(목적지)` 저장, `changed=true`.

**Response 200**: placement effect wire shape + `"changed": true|false`.

**Errors**:

| Status | Code | Condition |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 본문 없음 · 형식 오류. |
| 401 | `UNAUTHORIZED` | Bearer JWT 누락/무효. |
| 404 | `TENANT_NOT_FOUND` | 테넌트 미존재. |
| 404 | `ORG_NODE_NOT_FOUND` | 목적지 노드 미존재(경합 삭제 포함). |
| 409 | `TENANT_ORG_NODE_CONFLICT` | 지금 소속 ≠ `expectedOrgNodeId`. |

> 이벤트 없음 · 감사 행 없음(감사는 admin-service `admin_actions` 가 권위). 토큰의 `entitled_domains` 는 다음 발급부터 새 상한으로 계산된다(D6 seam — `GET /internal/tenants/{tenantId}/entitled-domains` 가 매번 `ACTIVE ∩ ceiling` 을 다시 계산한다).

---

## Server Constraints (account-service 측)

- 모든 상태 변경은 `AccountStatusMachine.transition()` 경유
- `account_status_history`에 `actor_type=operator`, `actor_id=operatorId`, `reason_code`, `ticket_id` 기록
- 해당 이벤트 발행 (outbox): `account.locked`, `account.unlocked`, `account.deleted`
- Idempotency-Key dedupe: 24시간 TTL

## Caller Constraints (admin-service 측)

- 타임아웃: 연결 3s, 읽기 10s
- 재시도: 2회. 409/404는 재시도 금지
- Circuit breaker 적용
- **감사 기록 먼저**: admin_actions row가 먼저 저장된 후 이 HTTP 호출 수행. 호출 실패 시 admin_actions.outcome=FAILURE로 갱신 (A10 fail-closed)
