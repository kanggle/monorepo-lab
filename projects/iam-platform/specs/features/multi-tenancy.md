# Feature: Multi-Tenancy

## Purpose

이 플랫폼은 단일 제품(팬플랫폼) 전용 계정 시스템에서 **여러 제품이 공유하는 계정·인증 공급자(account/auth provider)**로 진화한다. 1차 소비자는 B2C 팬플랫폼(`fan-platform`)이고, 2차 소비자는 B2B 내부 시스템 WMS(`wms`)이다. 향후 ERP·SCM·MES 등 추가 엔터프라이즈 제품도 동일한 게이트웨이를 통해 위임 인증한다.

본 문서는:

- 테넌트 모델(식별자·등록 방식)
- 데이터 격리 수준과 적용 범위
- JWT 변경(tenant_id claim)
- 테넌트별 역할 집합과 cross-tenant 보안 규칙
- WMS·향후 enterprise 소비자를 위한 internal provisioning API
- 기존 단일-테넌트 데이터의 마이그레이션 정책

을 정의한다.

---

## Related Services

| Service | Role |
|---|---|
| account-service | 테넌트 메타·계정 소유. `tenant_id` 컬럼 강제. 내부 provisioning 엔드포인트 제공 |
| auth-service | 로그인 시 `tenant_id` 조회 → JWT payload에 claim 포함 |
| gateway-service | JWT `tenant_id` 검증, 다운스트림으로 `X-Tenant-Id` 헤더 전파, tenant rate limit |
| admin-service | 운영자가 다룰 수 있는 테넌트 범위 제어, 테넌트별 audit query 분리 |
| security-service | 감사·보안 이벤트에 `tenant_id` 보존, tenant 단위 비정상 탐지 |

---

## Tenant Model

### TenantId

- **타입**: 문자열 (slug). 정규식 `^[a-z][a-z0-9-]{1,31}$` (소문자·숫자·하이픈, 1~32자)
- **예시**: `fan-platform`, `wms`, `erp`, `scm`, `mes`
- **불변**: 한 번 발급된 `tenant_id`는 변경 불가. 회수·재할당 금지(감사 트레일·외부 토큰과의 정합)
- **노출 범위**: JWT claim, internal API URL path, audit log, downstream HTTP header

### Tenant 엔터티 (account-service `domain/tenant/`)

| 필드 | 설명 |
|---|---|
| `tenant_id` | 위 슬러그. PK |
| `display_name` | UI 표시용. 예: "Fan Platform", "Warehouse Management System" |
| `tenant_type` | enum: `B2C_CONSUMER`, `B2B_ENTERPRISE`. 격리 정책·기본 역할셋 결정 |
| `status` | enum: `ACTIVE`, `SUSPENDED`. SUSPENDED 테넌트는 신규 로그인·가입 차단 |
| `created_at`, `updated_at` | 감사 |

### 테넌트 등록 방식

테넌트는 **운영자(SUPER_ADMIN)에 의해서만 등록**된다. self-service 가입 없음.

> 신규 소비 도메인이 합류할 때의 단일 진입 가이드는 [consumer-integration-guide.md](consumer-integration-guide.md) 참조 — Phase 1 (테넌트 등록) ~ Phase 6 (운영 체크리스트) 단선화.

- 등록 경로: admin-service의 운영자 명령 — 4개 엔드포인트 (`POST /api/admin/tenants`, `GET /api/admin/tenants`, `GET /api/admin/tenants/{id}`, `PATCH /api/admin/tenants/{id}`). 상세 contract 는 [admin-api.md § Tenant Lifecycle](../contracts/http/admin-api.md#tenant-lifecycle-task-be-256) 참조 (TASK-BE-256).
- 등록 시 `admin_actions`에 `action_code=TENANT_CREATE` 기록 + outbox 이벤트 [tenant-events.md](../contracts/events/tenant-events.md) `tenant.created` 발행 (audit-heavy).
- 테넌트 SUSPEND/REACTIVATE 도 동일하게 admin-service 경유 — `tenant.suspended` / `tenant.reactivated` outbox 이벤트 발행. account-service 가 이 이벤트를 소비해 SUSPENDED 테넌트의 신규 로그인·가입을 차단한다.
- 예약어 (`admin`, `internal`, `system`, `null`, `default`, `public`, `gap`, `iam`, `auth`, `oauth`, `me`) 는 `tenantId` 로 등록 불가 (`400 TENANT_ID_RESERVED`).
- 테넌트 삭제 미지원 — 감사 트레일·외부 토큰 정합으로 인해 SUSPEND 만 가능.

#### `oauth_clients.tenant_id` 가 실재 테넌트가 아닌 경우 (TASK-BE-581)

`auth_db.oauth_clients.tenant_id` 는 `account_db.tenants` 를 가리키지만 **두 DB가 갈라져 있어
FK 로 강제할 수 없고 마이그레이션도 각자 돈다**. 값은 세 범주 중 하나여야 하며, 그 외는 결함이다.

| 범주 | 뜻 | `tenants` 행 | 예 |
|---|---|---|---|
| 실재 테넌트 | 정상 | **있어야 함** | `ecommerce`, `wms`, `fan-platform` |
| **예약 슬러그** | 위 예약어. 플랫폼 자신의 운영 슬러그이므로 아무도 등록할 수 없다 | **없는 것이 정상** | `iam` (`platform-console-web`, `V0024`) |
| **INTERNAL 워크로드 센티넬** | 제품 테넌트에 묶이지 않는 GAP 내부 서비스 신원. 클라이언트 행의 `tenant_type='INTERNAL'` 이 판별자 | **없는 것이 정상** | `global-account-platform` (`V0019` 의 4개 client_credentials 클라이언트) |

- 뒤 두 범주는 `tenants` 행이 없는 것이 **설계대로**이므로, 브라우저 가입 경로가 그 값에 닿으면
  가입은 100% `404 TENANT_NOT_FOUND` 다. 그래서 [signup.md § 브라우저 회원가입 화면의 제시
  조건](signup.md) 이 그 경로를 막는다.
- INTERNAL 센티넬 클라이언트는 `client_credentials` 전용이어야 한다 — `authorization_code` 를
  얻는 순간 브라우저 경로가 생기고 `iam` 과 동일한 결함이 된다.
- 🔴 **미해소 인접 결함**: `global-account-platform` 은 위 예약어 목록에 **없고** 슬러그 정규식
  `^[a-z][a-z0-9-]{1,31}$` 을 통과하므로, 현재 소비자가 그 이름으로 테넌트를 등록할 수 있다.
  등록되면 내부 워크로드 센티넬이 실재 제품 테넌트와 충돌한다. 예약어 추가는 admin-api 계약
  변경이라 별도 티켓으로 다룬다 (TASK-BE-581 범위 밖 — 발견만 기록).

가드: `OAuthClientTenantReferenceIntegrationTest` (auth-service, `@Tag("integration")`).
모집단은 **production 마이그레이션만 적용한 DB** 에서 읽는다 — dev 시드가 섞이면 production 에서
깨진 상태에 초록이 된다.

---

## Org Node Model (ADR-MONO-047)

한 회사(paying company)가 **각자 격리된 여러 개의 서비스**를 소유하고 싶을 때, flat 한 `tenant` 레지스트리만으로는 표현할 수 없다 — 유일한 격리 단위가 tenant 이므로 "회사 전체 = 한 tenant"(서비스 격리 없음)이거나 "서비스마다 별도 tenant"(그것들을 회사로 다시 묶는 객체 없음) 둘 중 하나였다. [ADR-MONO-047](../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) 은 그 중간 계층 — **회사 → 서비스 → 도메인** 3축 구조 — 를 도입한다. 이는 AWS Organizations 와 GCP Resource Hierarchy 가 제공하는 "격리 경계 **위의** 그룹핑 노드" 패싯의 앱-레벨 이식이다.

### 3-축 구조

| 축 | 객체 | 역할 | 이 플랫폼 |
|---|---|---|---|
| 회사 (그룹핑) | `org_node` | 데이터 없는 nestable 그룹핑 노드 + 엔타이틀먼트 실링 | **본 ADR 신규** ([account-service data-model § org_node](../services/account-service/data-model.md#org_node)) |
| 서비스 (격리 leaf) | `tenant` | 단일 flat 격리/빌링 키 | **불변** (ADR-019, 본 문서 [Tenant Model](#tenant-model)) |
| 도메인 (권한) | `tenant_domain_subscription` → 파생 역할 | leaf 내부의 도메인 권한 | **불변** (ADR-035) |

하이퍼스케일러 패리티 ([ADR-MONO-047](../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § 1.2):

| 개념 | AWS | GCP | 이 플랫폼 |
|---|---|---|---|
| 데이터 없는 그룹핑 노드 (nestable) | Organizational Unit (OU) | Folder | **`org_node`** |
| 격리/빌링 leaf | Account | Project | **`tenant`** (불변) |
| leaf 내부 권한 | IAM roles/policies | IAM roles | domain subscription → 역할 (불변) |
| 트리 하향 상속 정책 | **SCP (deny-ceiling)** | Org Policy / IAM Deny (guardrail) | **엔타이틀먼트 실링 (deny-only, D2)** |
| 노드 위임 관리자 | delegated administrator @ OU | Folder IAM admin | **`ORG_ADMIN @ node`** (D5) |

### M1 보존 (그룹핑이지 중첩이 아님)

`org_node` 는 tenant 를 **그룹핑(GROUP)** 할 뿐 **중첩(NEST)** 하지 않는다 ([ADR-MONO-047](../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D1-A, § 3.1):

- `tenant_id` 는 여전히 **단일 flat 격리 축**이다 ([rules/traits/multi-tenant.md](../../../../rules/traits/multi-tenant.md) M1). 트리는 leaf **위에** 그룹핑을 더할 뿐 leaf 를 쪼개지 않는다.
- 토큰은 여전히 정확히 **하나의 `tenant_id`** 만 담는다. org 트리는 **발급 전 실링 계산**에만 참여하며 토큰의 격리 신원에는 일절 관여하지 않는다 (D6).
- 모든 row-isolation 가드(`WHERE tenant_id = ?`, `@TenantScoped`, M1–M7)는 **바이트 불변**이다. (참조: sub-tenant/nested tenant 안(D1-B)이 **거부**된 이유가 바로 이 blast radius — 하이퍼스케일러도 Account/Project 를 flat 하게 유지했다.)

### 실링(ceiling) 의미 — deny-only, narrow-only

노드에 붙는 엔타이틀먼트 실링은 하위로 상속되는 **최대(maximum) 도메인 집합**이며, tenant 가 구독/파생할 수 있는 것을 **좁히기만(narrow)** 할 뿐 **부여하지(grant) 않는다** ([ADR-MONO-047](../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § D2-A). leaf 의 유효 실링 = 루트→노드 체인의 **교집합**(`child ⊆ parent` 강제). 잘못 설정된 노드는 도달 범위를 **줄일 뿐**(안전한 실패), 절대 넓히지 않는다.

```
effectiveCeiling(tenant) = tenant.org_node_id IS NULL ? UNBOUNDED
                                                      : ⋂ ceiling(n)  for n in chain(root..node)
```

> ⚠ **`UNBOUNDED ≠ {}` 트랩.** `org_node_id = NULL`(ungrouped) 는 `UNBOUNDED`(실링 없음 = 레거시 동작, D7 net-zero)이고, 명시적으로 빈 실링 `BOUNDED({})` 는 그 **반대** — "아무 도메인도 구독 불가"(fail-closed)이다. 둘을 혼동하면 lazy 마이그레이션이 모든 tenant 를 조용히 잠가버린다. 저장 계층에서 `ceiling_mode`(`UNBOUNDED`/`BOUNDED`) 컬럼이 이 둘을 절대 conflate 하지 않도록 분리한다 ([account-service data-model § org_node](../services/account-service/data-model.md#org_node)). 도메인 파생과의 합성은 `derive(E ∩ C) = derive(E) ∩ derive(C)`(ADR-035 도메인-키 per-domain 파생)이므로 auth-service `TenantClaimTokenCustomizer` 는 바이트 불변이고, 실링 교집합은 account-service 소스에서 **한 번만** 적용된다 (D6 seam).

### `org_node` (본 ADR) vs `org_scope` (ADR-025) — 혼동 금지

**둘 다 트리지만 완전히 다른 축**이며 이름이 비슷해 쉽게 혼동된다 ([ADR-MONO-047](../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) § 5 Neutral, [ADR-MONO-025](../../../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md)):

| | **`org_node`** (본 ADR-047) | **`org_scope`** (ADR-025) |
|---|---|---|
| 위치 | tenant **위**의 트리 | **한 tenant/도메인 안**의 부서 서브트리 |
| 무엇을 하나 | tenant 그룹핑 + 엔타이틀먼트 실링 상속 | row 에 대한 데이터 필터 (ABAC data-scope) |
| 무엇에 작용 | 어떤 tenant 가 어떤 **도메인**을 구독 가능한가 (entitlement) | 한 도메인 안에서 어떤 **row/부서**를 볼 수 있는가 (data isolation) |
| 격리 키와의 관계 | 격리 키 **위**(above) — `tenant_id` 를 건드리지 않음 | 격리 키 **안**(inside) — 이미 tenant-scoped 된 데이터를 추가로 좁힘 |
| 방향성 | narrow-only (deny-ceiling) | narrow-only (ADR-025 "data-scope narrows, never grants") |

한 문장으로: **`org_node` 는 "이 회사가 어떤 서비스·도메인을 살 수 있는가"의 상한이고, `org_scope` 는 "한 서비스 안에서 이 사람이 어떤 부서 데이터를 볼 수 있는가"의 필터다.** 둘은 서로 다른 계층에서 각각 독립적으로 좁히는 게이트다 (enforcement-stack 순서: RBAC → tenant-scope(incl. `ORG_ADMIN` 서브트리) → **org-node 실링** → **ABAC org_scope** → access-condition, D6).

### 관련 ADR

- [ADR-MONO-047](../../../../docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md) — 본 모델의 권위 (D1–D7).
- [ADR-MONO-019](../../../../docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md) § D1 — customer-tenant = 격리 키(이 트리가 위에 얹히는 leaf). ADR-047 이 optional parent 노드를 additive 추가.
- [ADR-MONO-023](../../../../docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md) — 엔타이틀먼트/IAM plane 분리. 실링은 entitlement 만 제한할 뿐 IAM 역할을 직접 mint 하지 않는다.
- [ADR-MONO-024](../../../../docs/adr/ADR-MONO-024-tenant-admin-delegation.md) § D2 — 위임 관리자·no-escalation. ADR-047 이 `ORG_ADMIN @ node` 의 서브트리 드라이버를 additive 추가.
- [ADR-MONO-025](../../../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md) — `org_scope`(intra-tenant 부서 data-scope). 위 혼동 금지 표 참조.
- [ADR-MONO-035](../../../../docs/adr/ADR-MONO-035-operator-auth-unification-model.md) § O1 — subscription→역할 파생. 실링은 그 출력을 교집합으로 좁힌다(byte-unchanged derivation).

---

## Isolation Strategy

### 격리 수준

**모든 서비스에서 row-level isolation으로 시작한다.** 동일 schema에 `tenant_id` 컬럼을 추가하고, 모든 도메인 테이블의 unique index/foreign key/쿼리에 `tenant_id`를 명시적으로 포함한다.

| 테넌트 유형 | 격리 수준 | 비고 |
|---|---|---|
| B2C consumer (`fan-platform`, 향후 `ecommerce` 등) | row-level (shared schema) | 다수 테넌트가 등장해도 비용 효율을 위해 shared schema 유지 |
| B2B enterprise (`wms`, `erp`, `scm`, `mes`) | row-level (shared schema, 초기) | 규모·계약 요건에 따라 schema-level로 승격 가능. 트리거는 별도 ADR로 결정 |

schema-level 또는 DB-level 격리로 전환되는 시점은:

1. 단일 enterprise 테넌트의 데이터 볼륨이 다른 테넌트 합계의 50% 이상으로 비대해진 경우
2. 계약상 물리적 격리가 명시된 경우
3. 컴플라이언스(규제) 요구가 row-level로 충족 불가능한 경우

위 조건이 발생하면 ADR을 작성하고 `specs/services/<service>/architecture.md`를 갱신한다.

### 적용 범위 (서비스별)

- **account-service**: `accounts`, `profiles`, `account_status_history`, `outbox_events`에 `tenant_id` NOT NULL. unique index `(tenant_id, email)`로 변경(테넌트 간 동일 이메일 허용). 🔵 소비자 사이트의 새 계정은 예약 테넌트 `consumer-pool` 에 하나로 산다 — [§ 소비자 계정 풀](#소비자-계정-풀--소비자-사이트끼리-계정-하나-adr-mono-078-a-task-mono-742)(ADR-MONO-078)
- **auth-service**: `credentials`, `refresh_tokens`, `social_identities`에 `tenant_id` NOT NULL. unique index `(tenant_id, provider, provider_user_id)` (소셜 식별자도 테넌트별 분리)
- **admin-service**: `admin_operators`, `admin_operator_roles`, `admin_actions`에 `tenant_id`(또는 `target_tenant_id`) NOT NULL. SUPER_ADMIN은 cross-tenant 운영을 위해 `tenant_id = '*'` 같은 와일드카드 또는 별도 platform-scope role을 가짐(상세는 admin-service 스펙 후속)
- **security-service**: 보안 이벤트(`auth.login.attempted`, `auth.login.failed`, `account.status.changed` 등) 모두 `tenant_id` 페이로드 필수. 테넌트별 비정상 탐지 임계치 운용 가능
- **gateway-service**: 라우팅·rate limit 키에 `tenant_id` 포함. `rate:login:{tenant_id}:{ip}` 형태

---

## JWT Changes

### Access Token Payload

기존 payload에 다음 claim을 추가한다.

| Claim | 타입 | 설명 |
|---|---|---|
| `tenant_id` | string | 토큰을 발급한 테넌트의 slug. 필수 |
| `tenant_type` | string | `B2C_CONSUMER` \| `B2B_ENTERPRISE`. 라우팅·권한 검사 힌트 |
| `roles` | string[] | 기존 필드. 테넌트별 역할 집합. 의미는 `tenant_id` 컨텍스트에서만 유효 |

### Refresh Token

- DB `refresh_tokens.tenant_id` NOT NULL. rotation 시 `tenant_id` 일치 검증 필수
- cross-tenant refresh(다른 테넌트의 refresh로 다른 테넌트 access 발급)는 **절대 금지** → `TOKEN_TENANT_MISMATCH`.
  응답은 경로마다 다르다: **SAS `POST /oauth2/token`(`refresh_token` grant) = `400 invalid_grant`**
  (`error_description=TOKEN_TENANT_MISMATCH`, RFC 6749 §5.2 — 브라우저 세션의 정본 경로), 레거시 REST
  `POST /api/auth/refresh` = `403 TOKEN_TENANT_MISMATCH`([auth-api.md](../contracts/http/auth-api.md) § POST /api/auth/refresh —
  의도된 divergence). ~~이 줄은 403 만 적고 있었다~~ — 코드는 SAS 경로에서 처음부터 400 이었다(TASK-BE-604 에서 정정).
- **«같은 테넌트» 의 기준 (TASK-BE-604, 2026-09-26)** — SAS 경로의 비교는 미러 행(`refresh_tokens.tenant_id`) 대 **세션의 로그인
  시점 테넌트**(SAS 인가에 저장된 resource-owner principal details 의 `tenant_id` = 그 세션 토큰의 `tenant_id` claim)다.
  **client 의 테넌트가 아니다** — 둘은 [아래 로그인 규칙](#로그인-가능한-계정과-client-task-be-604)이 허용하는 교차 테넌트 로그인에서 다르고,
  client 테넌트로 비교하면 그 세션의 모든 refresh 가 거부된다. 최초 미러 행(발급 시 claim 에서)과 회전 미러 행(회전 시 같은
  규칙으로)이 같은 값을 갖는다. principal 에 `tenant_id`+`tenant_type` 이 없으면 claim 과 같은 폴백으로 client 테넌트.
- **거부는 최종이다** — TASK-BE-604 이전에는 SAS 기본 refresh provider 가 우리 provider 의 거부를 다시 처리해 200 으로 발급했다
  (미러 행 폐기·만료·테넌트 불일치 전부 무력화 — TASK-BE-603 CORRECTION). 지금은 제거되어 있다.

### Gateway 검증

- gateway는 JWT 서명·만료뿐 아니라 `tenant_id` claim 존재를 검사한다. 누락 시 401
- 라우트 패턴에 따라 다운스트림 서비스로 `X-Tenant-Id` 헤더를 전파한다. 다운스트림은 JWT claim과 헤더 일치를 재검증한다 (defense-in-depth)
- **예외 — `/api/admin/**`** (TASK-MONO-737): gateway 는 이 서브트리에서 JWT 를 검증하지 않으므로(검증은 admin-service 위임)
  claim 으로 찍을 값이 없고, 호출자의 `X-Tenant-Id`(운영자가 고른 활성 테넌트)를 **벗기지 않고 전달**한다. admin-service 는 그 값을
  **신뢰하지 않고** 운영자 범위로 판정한다(범위 밖 → 403). 벗기던 동안 admin-service 는 운영자 홈 테넌트로 떨어져, 전환한 테넌트의
  계정 잠금이 404 였다([`gateway-api.md` § Admin Routes](../contracts/http/gateway-api.md)).

---

## Per-Tenant Roles

### 원칙

- **역할 이름은 테넌트 컨텍스트 안에서만 의미를 가진다.** 동일한 `WAREHOUSE_ADMIN` role을 다른 테넌트에 부여해도 권한 의미는 다를 수 있다
- 역할 정의(role definitions)는 `(tenant_id, role_name)` 복합키로 관리. 물리 스토리지는 `account_roles` 테이블 ([specs/services/account-service/data-model.md § account_roles](../services/account-service/data-model.md#account_roles))
- 권한 매트릭스(role → permissions)는 테넌트마다 독립
- **기본 정책 (TASK-BE-255)**: admin 이 사전 등록한 역할만 부여 가능. 등록되지 않은 역할 이름은 provisioning API 가 400 으로 거부 — `account_roles.role_name` 자체는 자유 문자열 (정규식 `^[A-Z][A-Z0-9_]*$` 강제) 이며, 테넌트별 허용 역할 카탈로그 (`tenant_role_definitions`) 도입은 별도 후속 태스크

### 기본 역할 (예시)

#### `fan-platform` (B2C_CONSUMER)
- `MEMBER` (기본 가입자)
- `VERIFIED_MEMBER` (이메일 검증 완료)
- 운영자 역할은 admin-service의 platform-level role(`SUPER_ADMIN`, `ACCOUNT_ADMIN`, `AUDITOR`)이 담당

#### `wms` (B2B_ENTERPRISE)
- `WAREHOUSE_ADMIN` — 창고 운영 전반 관리. 사용자 생성·역할 부여 가능(WMS 내부)
- `INBOUND_OPERATOR` — 입고 관련 작업
- `OUTBOUND_OPERATOR` — 출고 관련 작업
- `INVENTORY_VIEWER` — 재고 조회 전용 (read-only)

> WMS의 위 4개 역할은 본 플랫폼의 `roles` claim에 그대로 노출된다. WMS 애플리케이션은 자체 권한 매트릭스를 보유하고 본 플랫폼의 claim을 신뢰한다. 본 플랫폼은 역할 이름 등록·계정 ↔ 역할 매핑·revocation만 책임진다.

### Cross-Tenant Operator

- `SUPER_ADMIN`은 platform-scope role로 테넌트 경계를 넘어 운영 가능. JWT claim에 `tenant_id="*"` 또는 별도 `platform_scope=true` 플래그를 사용한다(구체 형식은 admin-service 스펙 갱신 시 확정)
- 모든 cross-tenant 작업은 `admin_actions`에 `tenant_id=*` + `target_tenant_id=<대상>`으로 기록 (audit-heavy 정합)

---

## Internal Provisioning API

### 목적

WMS·ERP 등 enterprise tenant는 **자체 가입 페이지를 두지 않고** 본 플랫폼의 internal API를 통해 사용자를 생성·관리한다. 일반 사용자는 셀프 가입(SSO/이메일 가입)을 사용하지 않는다.

### 엔드포인트 (account-service `presentation/internal/`)

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/internal/tenants/{tenantId}/accounts` | 신규 사용자 생성 (이메일·초기 비밀번호·역할 배열) |
| `GET` | `/internal/tenants/{tenantId}/accounts` | 테넌트 사용자 목록 조회 (페이지네이션, 필터: status, role) |
| `GET` | `/internal/tenants/{tenantId}/accounts/{accountId}` | 단일 사용자 조회 |
| `PATCH` | `/internal/tenants/{tenantId}/accounts/{accountId}/roles` | 역할 전체 교체 |
| `PATCH` | `/internal/tenants/{tenantId}/accounts/{accountId}/status` | 상태 변경 (ACTIVE↔LOCKED, DELETED) |
| `POST` | `/internal/tenants/{tenantId}/accounts/{accountId}/password-reset` | 운영자에 의한 비밀번호 재설정 토큰 발급 |

### 인증·인가

- 인증: mTLS 또는 service-to-service 토큰. **gateway 비공개 라우트** (외부 인터넷 도달 금지)
- 인가: 호출 주체가 해당 `tenantId`에 대한 provisioning 권한을 보유해야 함. 주체 유형:
  - WMS의 시스템 계정(WMS 백엔드 → 본 플랫폼) — `tenant_id=wms`로만 호출 가능
  - 본 플랫폼의 `SUPER_ADMIN` 운영자 — 모든 테넌트로 호출 가능
- URL path의 `{tenantId}`와 인증 주체의 tenant scope가 불일치하면 **403 TENANT_SCOPE_DENIED**

### 동작 규칙

- 신규 계정 생성은 internal API에서 **tenant 등록 여부를 사전 검증** (`tenants.status = ACTIVE`)
- 이메일 unique index는 `(tenant_id, email)` 기준 → 같은 이메일이 `fan-platform`과 `wms`에 동시에 존재 가능
- 모든 mutation은 `admin_actions`에 기록(`OPERATOR_PROVISIONING_*` action_code) — internal 호출이라도 감사 의무 동일

---

## Cross-Tenant Security Rules

### 토큰 검증 (필수)

| 위치 | 검사 |
|---|---|
| gateway | JWT `tenant_id` claim 존재 확인. 누락 시 401 `TOKEN_INVALID` |
| gateway | 라우트가 internal provisioning이면 path `{tenantId}` ↔ JWT `tenant_id` 또는 platform-scope 검사 |
| 다운스트림 서비스 | `X-Tenant-Id` 헤더 ↔ JWT claim 재검증 (defense-in-depth) |
| 도메인 쿼리 | 모든 read/write 쿼리는 `WHERE tenant_id = ?` 명시 — application layer에서 강제 |

### 로그인 가능한 계정과 client (TASK-BE-604)

🔴 TASK-BE-604 이전에는 **이 규칙이 스펙 어디에도 없었다**(`specs/{features,services,contracts}` · ADR-MONO-044 ·
TASK-BE-309/507 · TASK-MONO-334/386 검토 — BE-604 § AC-0 (iii) 검토). 코드만 있었다. 아래는 소유자 결정 D (2026-09-26 UTC)다.

폼 로그인(`POST /login`, `CredentialAuthenticationProvider`)이 어느 자격(`credentials` 행)을 찾는가:

| 시작 client (저장된 `/oauth2/authorize` 의 `client_id`) | 그 client 테넌트에 자격 있음 | 없음 |
|---|---|---|
| **콘솔** — 테넌트 `iam` (`platform-console-web`) | 그 자격 | **교차 테넌트 조회** — 이메일이 정확히 한 테넌트에 있으면 그 자격, 둘 이상이면 fail-closed(`LOGIN_TENANT_AMBIGUOUS`) |
| **소비자** — 그 밖의 모든 client | 그 자격 | **로그인 실패** — 틀린 비밀번호와 같은 `/login?error`, `CREDENTIALS_INVALID` |
| 시작 client 없음 (저장된 authorize 요청 없음 — `/login` 직접 방문) | — | 교차 테넌트 조회 (위와 같음) |

- **콘솔이 교차 조회를 갖는 이유** — ADR-MONO-044 D5 셀프 온보딩 운영자는 `iam` 자격이 없다(운영자 `oidc_subject` = 소비자
  `account_id`, 비밀번호 NULL — `FirstAdminProvisioner`). 그들은 소비자 테넌트 자격으로만 콘솔에 들어온다. 콘솔 client 는
  역할을 주지 않는다 — 운영자 매핑이 없으면 admin 교환 401 → 온보딩(TASK-BE-604 § AC-0 (iii) 검토).
- **소비자 client 가 교차 조회를 잃은 이유** — TASK-BE-507 이 그것을 «BE-507 이전 `fan-platform` 쇼핑객» 폴백으로 남겼으나
  TASK-MONO-386 이 그 모집단을 **0** 으로 실측했고, 그 세션은 쓸모도 없었다(역할 시드 없음 → web-store 익명 · 게이트웨이 테넌트
  차단). 해당자는 그 테넌트에서 새로 가입한다(`(tenant_id, email)` 복합 unique).
- **응답에 힌트를 주지 않는다** — «다른 테넌트에 계정이 있습니다» 는 계정 열거다. 소유자 기본값(변경 가능): 없는 이메일과 같은
  `/login?error`.
- 로그인 세션의 테넌트(토큰 `tenant_id`)는 **자격 행의 테넌트**다 — 교차 조회로 찾았어도 client 테넌트가 아니다. refresh 는 그
  테넌트로 판정한다([Refresh Token](#refresh-token)).
- ~~이 표가 막지 않는 것 (TASK-BE-604 에서 확인, 후속): (1) SSO … (2) 소셜 로그인 …~~ — (1) 은 아래 **SSO** 절이 닫았고(TASK-BE-605),
  (2) 는 아래 **소셜 로그인** 절에 결정과 남은 불일치를 적었다.

#### 이미 로그인된 IAM 브라우저 세션의 재사용 — SSO (TASK-BE-605, 소유자 결정 ① (b) 재인증, 2026-09-26 UTC)

폼 로그인 표는 **비밀번호를 받을 때만** 돈다. 이미 인증된 세션으로 다른 client 의 `/oauth2/authorize` 를 열면 자격 조회가 없으므로,
authorize 시점에 따로 판정한다(`AuthorizeSessionTenantGate`, SAS `OAuth2AuthorizationEndpointFilter` 바로 앞):

| 요청 client 의 테넌트 | 세션 테넌트 = client 테넌트 | 다름 (플랫폼 스코프 `'*'` 포함) |
|---|---|---|
| **콘솔** — `iam` | 그대로 통과 | 세션의 이메일로 **`iam` 자격이 있으면 재인증**(TASK-BE-610) — 콘솔 `/login` 은 범위 조회로 그 `iam` 자격을 고른다. **없으면 그대로 통과** — ADR-MONO-044 D5 운영자는 소비자 테넌트 세션으로만 콘솔에 온다 |
| **소비자** — 그 밖의 모든 client | 그대로 통과 | **재인증** — 이 요청만 미인증으로 취급 → 그 client 의 `/login`(가입 힌트면 `/signup`). 로그인은 위 표대로 그 client 테넌트의 자격을 고른다 |
| client 없음 · 모르는 client · 테넌트 설정 없는 client · 세션 없음 | 판정하지 않음 — SAS 가 기존대로 답한다 | |

- **세션 테넌트** = 토큰 `tenant_id` claim 을 만드는 규칙과 **같은 함수**(`AuthorizationSessionTenant`): principal details 에 `tenant_id`+`tenant_type`
  이 둘 다 있으면 그 `tenant_id`, 아니면 client 테넌트. 즉 게이트는 «이 코드로 나갈 토큰의 테넌트가 client 테넌트인가» 를 묻는다.
- **왜** — 교차 테넌트 세션의 토큰은 쓸모가 없었다: 역할은 세션 테넌트로 조회되고(`listAccountRoles(sessionTenant, …)`) 플랫폼 시드는
  세션 테넌트가 client 플랫폼일 때만 발화한다. 스토어에 로그인한 뒤 팬 client 를 열면 `tenant_id=ecommerce` · 역할 없음 토큰이 나왔고,
  그 사람이 가진 **팬 자격은 한 번도 쓰이지 않았다**(BE-604 § ⑧ 로컬 측정). 이제 그 자리에서 팬 로그인 화면이 뜨고 팬 자격으로 들어간다.
  `identity-platform` § SSO Scope Rules(«대상 플랫폼에 역할이 있으면 같은 세션에서 토큰을 받을 수 있다(MAY)») 와 충돌하지 않는다 —
  교차 테넌트 세션은 대상 플랫폼에 역할이 없다.
- **루프 없음** — 재로그인 뒤 세션 테넌트 = client 테넌트다: 폼은 client 테넌트 자격을 범위 조회로 고르고(없으면 로그인 실패 — 재시도할 것이
  없다), 소셜은 client 테넌트를 찍는다(아래). 재개된 authorize 는 게이트를 통과한다(`SsoTenantGateIntegrationTest`).
- **UX 대가** — 테넌트가 다른 서비스로 옮겨 가면 **그 서비스의 로그인 화면이 한 번** 뜬다. IAM 브라우저 세션은 한 번에 한 principal 만
  들고 있으므로, 팬에 로그인한 뒤 스토어로 돌아가면 스토어 로그인이 다시 한 번 뜬다. **이미 발급된 토큰(각 서비스의 앱 세션)은 건드리지
  않는다** — 영향은 다음 authorize 뿐이다. 로그인 화면을 떠나면 원래 세션은 그대로다(게이트는 세션을 무효화하지 않고 이 요청의 보안
  컨텍스트만 비운다 — 저장된 요청도 그래서 살아남는다).
- **콘솔 조건부 재인증 (TASK-BE-610, 소유자 결정 ① 2026-09-26 UTC)** — 같은 브라우저에서 스토어 · 팬에 로그인한 뒤 콘솔을 열면 소비자
  principal 이 콘솔 토큰이 되어 운영자 교환이 «운영자 아님» → `/onboarding` 으로 떨어졌다(16차 창, `demo@demo.com`). 그 사람이 `iam` 자격을
  갖고 있어도 SSO 는 그것을 고르지 않았다. 이제 게이트는 세션 principal 의 이메일(details `email`, 없으면 principal 이름)로
  `credentials (tenant_id='iam', email)` 을 찾아 **있으면** 재인증한다.
  - **판정 키 = 이메일** — 폼 로그인 표가 자격을 고르는 키와 같다. 재인증은 **로그인을 다시 요구할 뿐** 아무 계정도 잇지 않으므로, 이메일 일치가
    `iam` 계정에 대한 접근을 주지 않는다(그 비밀번호를 알아야 들어간다).
  - **루프 없음** — 콘솔 client 의 폼 로그인은 범위 조회로 그 `iam` 자격을 먼저 고른다(위 표) → 세션 테넌트 `iam` = client 테넌트 → 통과.
  - **D5 는 그대로** — `iam` 자격이 없는 소비자(셀프 온보딩 운영자 포함)는 지금처럼 통과한다.
  - **조회 실패는 재인증하지 않는다(통과)** — 실패에 재인증으로 답하면 D5 소비자가 재로그인 → 같은 소비자 세션 → 다시 실패 → 재인증의 루프가
    된다. 실패는 경고 로그로 남기고 BE-610 이전 동작(통과)으로 간다.
- `prompt=none` 은 OIDC 대로 `login_required` 로 client 에 돌아간다. SAS 1.4.1 은 `prompt=login` 을 구현하지 않는다(값 검증만) — 이 게이트를
  프롬프트로 표현할 수 없는 이유다.

#### 소셜 로그인 — 신원은 시작 client 의 테넌트 안에서만 찾는다 (TASK-BE-605 결정 ② (iii) · 구현 TASK-BE-611)

- **규칙**: 신원 조회 키는 `(tenant_id = 시작 client 의 테넌트, provider, provider_user_id)` — § 적용 범위의 unique 인덱스와 **같은 모양**이다
  (`SocialIdentityRepository.findByTenantIdAndProviderAndProviderUserId`, 조회 두 곳 = `OAuthLoginUseCase` 해소 · `SocialLoginSteps` upsert).
  세션 테넌트도 같은 client 테넌트로 찍힌다(`SocialLoginBrowserController`) — 그래서 위 SSO 게이트와 맞물린다(재인증 뒤 항상 통과).
- **그 테넌트에 신원이 없으면 그 테넌트에서 가입한다** — account-service `social-signup` 에 client 테넌트를 보내고, 그 테넌트 안에서 이메일로
  기존 계정을 찾아 연결하거나 새로 만든다([oauth-social-login.md § 계정 연결 전략](oauth-social-login.md#계정-연결-전략)). 폼 로그인과 같은 결과다 —
  **테넌트마다 한 계정**. 같은 구글 사용자가 스토어 → 팬 순서로 들어오면 계정 둘 · 신원 행 둘(테넌트마다 하나)이다.
  🔵 **ADR-MONO-078 이후 소비자 사이트에서는 바뀐다 — 구현됨(`TASK-BE-617`)**: 소비자 사이트 client 는 `consumer-pool` 신원 행을 **먼저** 찾고, 새 소셜 가입은
  (풀 플래그가 켜져 있으면) 풀 계정이고 신원 행은 `consumer-pool` 에 하나다([§ 소비자 계정 풀](#소비자-계정-풀--소비자-사이트끼리-계정-하나-adr-mono-078-a-task-mono-742) § 2). 이 문단의 «테넌트마다 한 계정» 은
  B2B 테넌트 · 플래그 꺼짐 · 078 이전에 만들어진 사이트별 신원(그대로 그 사이트 계정으로 간다)에 남는다.
- **BE-611 이전**: 조회가 전역이라 다른 테넌트에서 만든 신원이 잡혔다 — `ecommerce` 에서 만든 신원으로 팬 client 에 들어가면 `ecommerce`
  계정이 `tenant_id=fan-platform` 세션으로 들어갔다(16차 창 구조적 재현, `TASK-MONO-672` 항목 18). 두 테넌트에 행이 생기면 전역 조회는 결과가
  둘이 되어 그 신원의 로그인이 영구히 실패할 수 있었다(`TASK-BE-602` 후속 ①) — 한정 조회는 unique 키 그대로라 결과가 최대 하나다.
- **기존 교차 신원은 옮기지 않는다** (소유자 결정 AC-0 ②) — 이미 만들어진 행은 그대로 둔다. 그 사람이 다른 테넌트 client 로 다음에 들어오면
  그 테넌트의 신원 행이 새로 생기고, 계정은 위 가입 규칙대로 정해진다(그 테넌트에 같은 이메일 계정이 없으면 새 계정). BE-507 이전에 생긴
  «신원 행 테넌트 ≠ 계정 행 테넌트» 행은 그 신원 행의 테넌트 client 로 들어올 때 여전히 그 계정으로 간다 — 상태 · 이벤트 테넌트는 계정 행에서
  읽으므로(BE-602) 판정은 계정의 실제 테넌트로 한다.

### 소비자 계정 풀 — 소비자 사이트끼리 계정 하나 (ADR-MONO-078 A, `TASK-MONO-742`)

> 🔴 이 절은 **계약이 먼저다**(2026-10-01). 아래 규칙의 구현은 `TASK-BE-614`(저장) · `TASK-BE-615`(로그인·토큰) ·
> `TASK-BE-616`(첫 방문 동의) · `TASK-MONO-743`(기존 계정 묶기 — 2026-10-05 대상 0 으로 구현 없이 종결) · `TASK-BE-617`(소셜)이다. 구현이 끝나기 전까지 위 표들(로그인 · SSO ·
> 소셜)이 **지금 동작**이고, 이 절은 그것들이 **소비자 사이트에 대해** 어떻게 바뀌는지를 정한다. 콘솔(`iam`)과 소비자가 아닌 테넌트는 이 절 밖이다(D1).

**용어** — *소비자 사이트* = 셀프 가입을 받는 client 의 테넌트(`fan-platform` · `ecommerce`). *풀 계정* = 이 절의 계정.
*사이트별 계정* = ADR-MONO-078 이전 모양(한 소비자 테넌트에 속한 계정) — 묶이기 전까지 그대로 동작한다.

#### 1. 저장 — 예약 테넌트 `consumer-pool` + 사이트 멤버십 (AC-4 결정)

| 무엇 | 어디에 | 바뀌는 제약 |
|---|---|---|
| 풀 계정 | `accounts` 행, `tenant_id = 'consumer-pool'` | 없음 — `(tenant_id, email)` UNIQUE 가 풀 안의 이메일 유일성을 그대로 지킨다. `tenants` 에 `consumer-pool` 행을 하나 둔다(`fk_accounts_tenant_id` 때문) |
| 풀 자격 | `credentials` 행, `tenant_id = 'consumer-pool'` | 없음 — `(tenant_id, email)` UNIQUE 그대로 |
| 사이트 멤버십 | **신설** `consumer_site_memberships(account_id, site_tenant_id, status, consented_at)` — PK `(account_id, site_tenant_id)` | 신설 테이블만. 기존 테이블 무변경 |
| 사이트별 역할(시드 밖) | **신설** `consumer_site_roles(account_id, site_tenant_id, role_name)` → 멤버십 FK | 신설만. `account_roles` 의 복합 FK `(tenant_id, account_id) → accounts(tenant_id, id)` 는 **건드리지 않는다** — 풀 계정의 사이트 역할을 거기 넣으면 사이트 테넌트 ≠ 계정 테넌트라 FK 가 깨진다 |
| 소셜 신원 | `social_identities` 행, `tenant_id = 'consumer-pool'` | 없음 — `(tenant_id, provider, provider_user_id)` UNIQUE 가 풀 안에서 그대로 |

- 🔴 **`consumer-pool` 은 저장값이다. 토큰에 절대 나오지 않는다** — 토큰의 `tenant_id` 는 요청한 client 의 사이트 테넌트다(`jwt-standard-claims.md` `tenant_id` 행).
  그 값을 등록한 OAuth client 는 **없어야** 하고(있으면 그 client 가 풀 값을 토큰에 싣는다), 어느 게이트웨이도 그 값을 받아들이지 않는다.
- 왜 «테넌트 없는 행»(`tenant_id` NULL)이 아닌가 — 컬럼이 NOT NULL 이고, NULL 은 MySQL UNIQUE 에서 중복 검사를 받지 않아 풀 안 이메일 유일성이 사라진다.

#### 2. 가입 — 소비자 client 의 새 가입은 풀로

- 소비자 client 에서 오는 폼·소셜 가입은 풀 계정을 만들고, 그 client 의 사이트 멤버십을 **같이** 만든다(가입 = 그 사이트 이용 동의).
- 🔴 **같은 이메일의 사이트별 계정이 이미 있으면 풀 가입을 받지 않는다** — 대신 «그 이메일로 로그인한 뒤 전환» 으로 안내한다.
  받으면 같은 이메일에 풀 계정과 사이트별 계정이 공존하고, 로그인 폼은 둘 중 하나의 비밀번호만 검사할 수 있다 — 남이 그 이메일로 풀 계정을
  만들면 **원래 주인이 자기 사이트 계정에 못 들어간다**. 가입 화면의 «이미 가입된 이메일» 은 지금 가입이 이미 내는 응답과 같은 부류다(새 열거 경로가 아니다).
- 🔴 **역방향도 같다 (`TASK-BE-616`)**: **풀 계정이 먼저 있는** 이메일로 내부 계정 생성(`POST /internal/tenants/{t}/accounts` — 운영자 프로비저닝 · 벌크 ·
  product-service 셀러 온보딩)이 **소비자 사이트**에 오면 거절한다 — 그 엔드포인트의 기존 중복 응답 `409 ACCOUNT_ALREADY_EXISTS`. B2B · 고객 테넌트(wms · erp ·
  demo-corp …)는 대상이 아니다(D1 — 테넌트별 계정). 잠정 결과: 풀 쇼퍼를 같은 이메일로 셀러 온보딩할 수 없다 — 셀러 계정은 지금 `seller+<tenant>+<sellerId>@marketplace.local` 기계 계정이라 실제로 부딪히지 않는다. 사람 계정을 셀러에 연결하는 모델은 `ADR-MONO-079`(`TASK-MONO-747`, `TASK-MONO-745` 흡수) 몫이고, 그 전까지는
  product-service 의 기존 fail-soft 대로 `PENDING_PROVISIONING` 에 머문다.
- 🔴 **소셜 가입도 같다 (`TASK-BE-620` 방어 → `TASK-BE-617` 풀 소셜)**: 소비자 사이트 client 의 새 소셜 가입(`POST /internal/accounts/social-signup`)은 풀 플래그가
  켜져 있으면 **풀 계정** + 그 사이트 멤버십을 만들고, 신원 행은 `consumer-pool` 에 쓴다(auth-service 는 신원을 `consumer-pool` 에서 먼저 찾는다 — 폼 로그인의 풀-먼저와 같은 결).
  순서([auth-to-account-social.md](../contracts/http/internal/auth-to-account-social.md)): ① 그 사이트 안에 같은 이메일의 **사이트 계정**이 있으면 지금처럼 그 계정으로 들어간다
  (사이트별 계정은 묶이기 전까지 그대로 — 기존 동작) ② 같은 이메일의 **풀 계정**이 있으면 **거절**(`409 ACCOUNT_ALREADY_EXISTS` → `error=email_registered`) —
  🔴 **이메일로 풀 계정에 붙이지 않는다**(ADR-MONO-078 D2: 이메일을 검증하지 않는 제공자를 통한 탈취 경로. `TASK-BE-617` AC-2 대조군) ③ 다른 소비자 사이트에 같은 이메일의
  사이트 계정이 있으면 거절(폼 가입과 같은 공존 금지) ④ 그 밖 → 풀 계정. 플래그가 꺼져 있으면 사이트 계정을 만들고 ②만 남는다(`TASK-BE-620` 그대로).
  🔵 `TASK-BE-620` 의 검사는 **지우지 않았다** — 풀 경로에서는 ② 가 곧 D2 대조군이고, 꺼진 경로에서는 620 그대로의 방어다(`TASK-BE-617` § 620 결정).

#### 3. 기존 계정 — 한 사이트에만 있으면 **같은 id 로** 풀로 옮긴다

- 사이트별 계정이 **한 사이트에만** 있는 사람(대다수)은 그 계정의 `tenant_id` 를 `consumer-pool` 로 옮기고 그 사이트 멤버십을 만든다.
  **id 가 그대로라** 그 사이트의 데이터(팬 팔로우, 스토어 주문)를 옮길 필요가 없다.
  **옮기는 시점 = 일괄** (`TASK-BE-618` 결정 — 다음 로그인 때 지연 이동은 기각: 로그인 한 번에 세 서비스 쓰기가 끼고, 휴면 계정은 영영 사이트 계정으로 남는다):
  재실행 가능한 내부 유지보수 엔드포인트 `POST /internal/consumer-pool/legacy-moves`(account-service —
  [account-maintenance-internal.md](../contracts/http/internal/account-maintenance-internal.md)). 내부 프로비저닝이 080 전까지 사이트 계정을 계속 만들므로 **몇 번이든 다시 돈다**.
  계정마다 account_db 트랜잭션 하나이고, auth-service 의 자격 이동(`POST /internal/auth/consumer-pool/moves`)이 그 트랜잭션의 **마지막 단계**다 — 거절·실패하면 아무것도 옮겨지지 않는다.
  🔴 같이 옮기는 행과 **옮기지 않는** 행 (`TASK-BE-618` 착수 시 정정 — 아래가 정본이다):

  | 행 | 이동 | 왜 |
  |---|---|---|
  | `accounts` · `profiles` · 그 계정의 `identities` 행 · `credentials` | `tenant_id` → `consumer-pool` | 남으면 그 행의 조회가 404 |
  | `account_roles`(그 사이트) | → `consumer_site_roles(account, site, role)` 후 원래 행 삭제(계정 테넌트 변경 **전에** — 복합 FK) | § 1 |
  | `consumer_site_memberships` | **신설** `(account, site, ACTIVE, consented_at = 계정 생성 시각)` | 가입 = 그 사이트 동의(§ 2) |
  | `refresh_tokens` | **옮기지 않는다** | 미러 행 테넌트는 계정이 아니라 **세션 테넌트**(= 토큰의 사이트, § 4 «refresh») — 이미 목표 모양이다. 옮기면 refresh 가 `TOKEN_TENANT_MISMATCH` |
  | `social_identities` | **옮기지 않는다** — 소셜 신원이 있는 계정은 **통째로 건너뛴다**(`SOCIAL_LINKED`) — 🔵 `TASK-BE-617` 결정(2026-10-05 UTC): **사이트 계정으로 남는다** | 소셜 로그인이 `(사이트, provider, 사용자)` 로 찾는다 — 옮기면 그 사람의 소셜 로그인이 끊긴다. 617 이 조회를 풀-먼저로 바꿨지만 이동기는 바꾸지 않았다: 그 모집단은 배포 환경에서 0 이고(실제 소셜 키가 배포된 적 없음 · 617 이후 소비자 사이트의 새 소셜 가입은 풀) 남은 사람은 사이트 신원 그대로 로그인된다(AC-3). 그 이메일들에는 § 2 의 «사이트 계정 이메일로 풀 가입 거절» 이 계속 걸린다. 되살리는 조건 = 그 계정 수가 0 이 아닌 것이 실측될 때 — 이동기 판정 4 를 «신원 행도 같이 옮긴다» 로 바꾸는 티켓을 연다 |
  | `account_status_history` | **옮기지 않는다** | append-only(DB 트리거, audit-heavy A3). 읽기는 전부 `account_id` 로만 — 404 가 나지 않는다 |

  운영자 측면 판정(아래 표)에는 **운영자 신원 연결**(ADR-MONO-034 U3 — `admin_operators.identity_id` 가 이 계정의 신원)도 들어간다: 신원 행을 풀로 옮기면 그 운영자의
  신원이 풀 신원이 된다. 판정은 auth-service 가 admin-service 에 묻는다(fail-closed — 못 물으면 옮기지 않는다).
- 같은 이메일로 **두 사이트**에 계정이 있는 사람은 자동으로 옮기지 않는다 — 본인이 두 계정을 모두 증명하고 묶는다(`TASK-MONO-743`, ADR-MONO-078 D2). 🔵 2026-10-05: 743 은 대상 0(새 이메일 가입은 처음부터 풀)으로 구현 없이 종결 — 그런 계정이 생기면 옮기지 않은 채 남고, 묶기는 743 닫기 기록의 «되살리는 조건» 으로 새로 연다.
  묶기 전에는 두 계정이 지금처럼 따로 동작한다.
- 🔴 **운영자 측면이 붙은 사이트 계정은 이 단계(§ 3 의 일괄·지연 이동)에서 옮기지 않는다 — 각자 자기 단계에서 옮긴다** (소유자 결정 2026-10-01 UTC:
  «셀러를 풀에 포함, 080 후보 등록»):

  | 계정 | 이동 단계 | 왜 그 단계까지 기다리나 |
  |---|---|---|
  | 이커머스 **셀러**(`ADR-MONO-042`, `SELLER` 역할) | ~~`TASK-MONO-745`~~ → **옮기지 않는다**(2026-10-02 소유자 결정: 셀러 계정은 사람이 로그인하지 않는 기계 계정 — 사람 계정 연결은 `ADR-MONO-079`, `TASK-MONO-747`). 아래 칸은 당시의 근거 | product-service 가 셀러를 `(tenant_id, seller)` 로 찾고 상태 이벤트를 `(tenantId, accountId)` 로 소비한다 — 그 두 곳을 먼저 «계정 id» 로 고쳐야 옮겨도 셀러를 놓치지 않는다. 옮긴 뒤 `SELLER` 는 `consumer_site_roles(account, ecommerce, SELLER)` 로 가고, 스토어 토큰은 시드와 **합쳐** `["CUSTOMER","SELLER"]` 가 된다(§ 4 역할 규칙) |
  | 셀프 온보딩 운영자(`ADR-MONO-044` D5 — 운영자 `oidc_subject` 가 이 계정 id) | `ADR-MONO-080` 후보(`TASK-MONO-746`) | 운영자 규칙(«운영자는 대상 테넌트 계정에만», `TASK-MONO-334`)을 바꾸는 결정이 먼저다 |

  옮기기 전까지 이 계정들은 지금처럼 동작한다. 내부 프로비저닝(`/internal/tenants/{tenantId}/accounts`)은 080 이 바꾸기 전까지 **사이트 테넌트에** 계정을 만든다.
  팬 `ARTIST` 역할(`ADR-MONO-059`)은 운영자 측면이 아니라 팬 사이트 역할이다 — § 3 의 이동에서 `consumer_site_roles(account, fan-platform, ARTIST)` 로 간다(id 가 그대로라 `artists.account_id` 는 무변경).
- **옮기기 전, 운영자 측면 계정의 이메일로 소비자 client 풀 가입이 오면 거절한다** (구현자 기본값 — `TASK-BE-614` AC-6, 소유자가 한 줄로 뒤집을 수 있다).
  § 2 의 공존 금지와 같은 이유다: 받으면 같은 이메일에 풀 계정과 사이트 계정이 공존해, 스토어 폼 로그인(풀 먼저)이 그 사람의 사이트 계정 비밀번호를 거절하고
  콘솔 교차 조회가 `LOGIN_TENANT_AMBIGUOUS` 로 막힌다 — 그리고 이메일 인증이 없으니 **남이** 그 이메일로 가입만 해도 그렇게 된다. 거절의 대가는 그 사람이
  자기 단계(745 / 080)까지 그 이메일로 다른 소비자 사이트에 못 들어간다는 것이다.

#### 4. 로그인 · authorize · 토큰

| 시작 client | 세션 principal | 결과 |
|---|---|---|
| 소비자 | 풀 계정, 그 사이트 멤버십 **있음** | 폼 없이 그 사이트 토큰 — `sub` = 풀 계정 id, `tenant_id` = 그 사이트, `roles` = 그 사이트 역할만 |
| 소비자 | 풀 계정, 멤버십 **없음** | 그 사이트 **동의 화면 한 번**(`TASK-BE-616`) → 멤버십 생성 → 토큰 |
| 소비자 | 사이트별 계정(묶이지 않음) | **지금 그대로** — 테넌트가 다르면 재인증(위 SSO 표) |
| 콘솔 | 풀 계정 | 위 SSO 표의 콘솔 행 그대로(D1). 풀 계정은 운영자 권한을 주지 않는다 |

- 폼 로그인의 자격 선택: 소비자 client 는 **풀 자격을 먼저**, 없으면 그 client 테넌트의 사이트별 자격을 찾는다. § 2 의 가입 거절과 § 3 의 이동이
  «같은 이메일에 풀 자격과 사이트별 자격이 공존» 을 막으므로 순서가 결과를 바꾸지 않는다 — 🔴 공존이 생기면 그것이 결함이다(`TASK-BE-615` 의 대조군).
- **역할(AC-5 결정)**: 풀 principal 의 토큰 역할 = 그 사이트의 시드 역할(`RoleSeedPolicy` — `ecommerce → CUSTOMER`, `fan-platform → FAN`) ∪
  `consumer_site_roles(account, 그 사이트)`. 다른 사이트 역할은 **싣지 않는다**. 멤버십 없는 사이트로는 토큰을 만들지 않는다.
  🔴 지금 발급 경로는 저장 역할을 그대로 싣는다(`TenantClaimTokenCustomizer.java:872-874`) — 풀 principal 에 대해 사이트로 거르는 것이 `TASK-BE-615` 의 일이다.
  → 구현됨(`TenantClaimTokenCustomizer#customizeForPoolPrincipal`): 사이트별 계정의 «저장 역할이 있으면 그것만» 규칙은 **그대로**다(셀러 `[SELLER]` — `TASK-BE-615` AC-7 대조군).
- **refresh**: 풀 principal 의 refresh 미러 행 테넌트는 **토큰의 사이트 테넌트**다 — 스토어에서 받은 refresh 로 팬 토큰을 받지 못한다(`TOKEN_TENANT_MISMATCH` 그대로).
- **콘솔 교차 조회**: 위 로그인 표의 콘솔 행은 «이메일이 정확히 한 테넌트에» 를 센다. 풀 계정은 `consumer-pool` 한 행이라, 같은 이메일이 팬·스토어에
  따로 있어 `LOGIN_TENANT_AMBIGUOUS` 로 막히던 사람이 **묶은 뒤에는** 하나로 풀린다 — 행동 변경이므로 `TASK-BE-615` 가 시험으로 고정한다.
  🔵 `TASK-BE-615` 구현: 교차 조회는 풀 자격 하나로 풀려 **로그인은 된다**. 그러나 콘솔 세션 테넌트는 `consumer-pool` 그대로라(풀 principal 을 콘솔로는
  사상하지 않는다 — D1) 발급자가 `consumer-pool` 발급을 거절한다(`TASK-BE-614` 게이트) — **콘솔 토큰은 없다**(`invalid_grant`). 풀 계정의 운영자 경로는 `ADR-MONO-080` 후보(`TASK-MONO-746`).
- **멤버십 없는 사이트 — `TASK-BE-616` 전까지의 결과 (`TASK-BE-615` 결정)**: authorize 는 재인증하지 않고 코드를 준다(재인증하면 그 client 의 폼이
  풀 자격을 먼저 골라 같은 세션 → 같은 게이트 → 무한 반복). **토큰 엔드포인트가 `invalid_grant` 로 거절**한다 — 토큰 없음 · 루프 없음. 616 의 동의 화면이 이 자리에 들어온다.
  멤버십 조회 실패도 토큰 없음(fail-closed). 갱신 때마다 다시 묻는다 — 멤버십이 `LEFT` 가 되면 다음 refresh 부터 토큰이 없다.
  → **`TASK-BE-616` 이후 (구현됨)**: 멤버십 **행이 없는** 소비자 사이트의 authorize(`GET`)는 코드 대신 **동의 화면**(`/consent`)이다 — 게이트가 그 authorize 요청을
  세션에 보관한다(전용 세션 속성 — 로그인 이어가기와 섞이지 않는다; 화면을 다시 열거나 뒤로 가도 남아 있다).
  **동의** → account-service 가 멤버십(ACTIVE · `consented_at`)을 쓰고 그 사이트로 `account.created` 를 1회 낸다([auth-to-account.md § PUT consumer-members](../contracts/http/internal/auth-to-account.md#put-internaltenantstenantidconsumer-membersaccountid--첫-방문-동의-task-be-616))
  → 보관한 authorize 가 다시 돌아 멤버로 통과 → 그 사이트 토큰. **거절** → 쓰기 없이 client 의 **등록된** redirect URI 로 `error=access_denied`(+`state`) —
  토큰 없음 · 루프 없음 · IAM 세션은 그대로(다른 사이트 SSO 유지). `prompt=none` 은 화면을 띄울 수 없으므로 `error=consent_required`.
  redirect URI 가 등록값과 **정확히** 같지 않으면 리다이렉트하지 않는다(오류 응답도 리다이렉트다 — 열린 리다이렉터 금지). `POST` authorize 와 `LEFT` 멤버십은
  동의 화면을 띄우지 않고 위 615 결과 그대로(토큰 거절) — 동의는 떠난 멤버십을 다시 열지 않는다. 콘솔(`iam`)과 B2B client 에는 동의 화면이 없다.
  → **`TASK-BE-619` 이후**: `LEFT` 중 **본인이 떠난 것**(`leftBy = SELF`)은 행 없음과 같이 **동의 화면**이고, 동의하면 `ACTIVE` 로 돌아온다(이벤트 없음 — § 5
  «사이트 탈퇴 vs 계정 삭제»). **운영자가 내보낸 것**(`leftBy = OPERATOR`)과 작성자 기록이 없는 `LEFT` 는 위 그대로(동의 화면 없음 · 토큰 거절).
  → **`TASK-BE-621` 이후**: 그 사이트 운영자가 잠근 멤버십(`LOCKED`)도 운영자 `LEFT` 와 같다 — 동의 화면 없음 · 토큰 거절(`invalid_grant`) · 다른 사이트는 그대로.
  동의 화면의 부제는 그 화면 전용이다 — «{사이트 이름}에서 내 IAM 계정을 쓰도록 허용합니다.»(사이트 이름 = 보관된 client 의 사이트 테넌트 `display_name`, 조회
  실패 시 «이 사이트»). 사이트의 **로그인** 부제(«…로그인하세요»)는 쓰지 않는다(이미 로그인한 사람에게 하는 말이 아니다 — 소유자 결정 2026-10-03).
- **소비자 사이트가 아닌 client**(B2B — wms · erp …): 풀 세션은 다른 테넌트 세션처럼 **재인증**, 그 client 의 폼은 풀 자격을 보지 않는다(풀-먼저는 소비자 사이트 규칙).
- **로그아웃 범위 (소유자 결정 2026-10-01 «전체»)**: 어느 소비자 사이트에서 로그아웃하든(RP-initiated `/connect/logout`) **IAM 브라우저 세션이 끝난다** —
  다른 사이트의 다음 authorize 는 로그인 화면이다. 이미 발급된 다른 사이트의 앱 세션(그 사이트의 토큰 · refresh)은 **그 사이트의 만료 · 자체 로그아웃**을 따른다
  (IAM 이 그 토큰을 폐기하지 않는다). IAM 세션은 브라우저에 하나라 이것은 새 코드가 아니라 기존 동작이고, `TASK-BE-615` 가 풀 계정에 대해 시험으로 고정했다.

#### 5. 사이트 테넌트로 계정을 찾는 표면

`/internal/tenants/{tenantId}/accounts` 목록·조회와 콘솔 «고객 신원 → 계정 운영» 처럼 **사이트 테넌트로 계정을 찾는** 표면은, 그 사이트
멤버십이 있는 풀 계정을 **포함해야** 한다. 안 그러면 운영자가 `ecommerce` 로 전환했을 때 새로 가입한 쇼핑객이 목록에서 사라진다
(계정 행은 `consumer-pool` 에 있다). 🔴 이것은 § 격리 회귀 방지의 «`tenant_id` 를 첫 인자로» 규칙의 **확장**이지 예외가 아니다 — 입력은 여전히 사이트 테넌트이고,
판정이 «계정 테넌트 = 입력» 에서 «계정 테넌트 = 입력 **또는** (풀 ∧ 그 사이트 멤버)» 로 넓어진다. 구현 `TASK-BE-614`.

🔵 **단건 표면까지 (`TASK-BE-616`)** — 요청이 준 사이트 테넌트(게이트웨이 `X-Tenant-Id` · 운영자 활성 테넌트 · `/internal/tenants/{t}/…` 경로)로 계정 **하나**를
찾는 표면도 같은 술어다(account-service `SiteAccountLookup`, 614 와 같은 질의 · 같은 플래그): `/api/accounts/me`(GET · PATCH profile · DELETE) · `/me/status` ·
이메일 인증 재발송 · `/internal/accounts/{id}/status` · 헤더가 사이트를 말하는 `lock`/`unlock`/`delete` · `gdpr-delete` · `export` ·
`/internal/tenants/{t}/accounts/{id}/status` · `password-reset`. 🔴 **넓히지 않는 것**: `account_roles` 쓰기(`roles` PATCH · `:add`/`:remove` — 복합 FK
`(tenant_id, account_id) → accounts` 가 풀 계정을 사이트 테넌트로 담을 수 없다; 사이트 역할은 `consumer_site_roles`) · 계정 → identity 조회 · 운영자 생성의
identity 해석(운영자 규칙 = `ADR-MONO-080` 후보).
🔵 **역할 GET 은 `TASK-BE-618` 에서 넓혔다** — 그 사이트 `account_roles` 가 비고 계정이 그 사이트의 ACTIVE 풀 멤버면 `consumer_site_roles(account, site)` 를 답한다
(§ 3 이동 전에 로그인한 세션의 refresh 가 이 조회로 역할을 싣는다 — [account-internal-provisioning.md § roles GET](../contracts/http/internal/account-internal-provisioning.md#get-internaltenantstenantidaccountsaccountidroles)).
풀 principal 의 발급은 여전히 consumer-members 읽기를 쓴다.
- **비멤버 대조군**: 사이트 A 에만 멤버인 풀 계정을 사이트 B 로 찾으면 404 — 읽기 · 쓰기 모두.
- 🔵 **«그 사이트 멤버» = 멤버십 `ACTIVE` 또는 `LOCKED` (`TASK-BE-621`)** — 위 술어의 «멤버» 는 그 사이트에서 **잠긴** 풀 계정도 포함한다(목록 · 이메일 검색 · 단건).
  잠긴 회원이 그 사이트의 목록·단건 조회에서 사라지면 그 사이트 운영자가 해제할 길이 없다. `LEFT` 는 여전히 멤버가 아니다(찾히지 않는다 → 404).
- 🔴 **쓰기의 범위는 «계정 하나»** — **삭제와 운영자 잠금·해제, 사이트 백엔드의 프로비저닝 상태 변경을 뺀** 상태 전이(휴면 등)는 **풀 계정 하나**에 일어난다. 이벤트는 계정의 테넌트 `consumer-pool` 로 한 번
  (account-events.md § 상태 전이). 운영자 이력 행(`account_status_history`)도 계정의 테넌트로 적힌다. GDPR **내보내기**는 그 사람이 멤버인 어느 사이트의
  운영자든 할 수 있다(읽기). (`TASK-BE-616` 은 잠금도 여기 넣었다 — 스토어 운영자의 잠금이 팬에서도 잠갔다. `TASK-BE-621` 이 아래 «사이트 잠금» 으로 바꿨다.)
- 🔴 **잠금도 다르다 — «사이트 잠금» vs «계정 잠금» (`TASK-BE-621`, 소유자 결정 2026-10-04 «사이트 운영자가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다.
  계정 전체 잠금은 플랫폼 관리자만.»)**. 아래 «사이트 탈퇴 vs 계정 삭제» 와 같은 설계다.

  | 행위자 | «사이트 잠금» (그 사이트 멤버십 → `LOCKED`) | «계정 잠금» (풀 계정 → `LOCKED`, 모든 소비자 사이트에서) |
  |---|---|---|
  | **사이트 운영자** (활성 테넌트 = 그 사이트, 플랫폼 스코프 아님) | 콘솔 잠금 · 해제 · 일괄 잠금 → 내부 `/lock` · `/unlock`(헤더 = 사이트) — 대상이 **풀 멤버**면 **그 사이트 멤버십만** `LOCKED`/`ACTIVE`. 계정 · 다른 사이트 멤버십 무변경, 이벤트 없음. 응답 `scope = SITE_MEMBERSHIP` ([admin-to-account.md § lock](../contracts/http/internal/admin-to-account.md)) | **할 수 없다** — 해제도(계정 전체 잠금은 사이트 운영자가 풀지 못한다). 그 사이트의 **자기 계정**(풀 아님)은 지금처럼 계정을 잠근다 |
  | **플랫폼 관리자** (SUPER_ADMIN — `isPlatformScope`) | (해당 없음) | 콘솔 잠금 · 해제 — admin-service 가 활성 테넌트와 무관하게 하류 `*` 를 찍고, account-service 는 계정 행 자신의 테넌트로 찾아 계정을 잠근다(`scope = ACCOUNT`, `account.locked` · 세션 폐기 그대로) |
  | **사이트 백엔드** (기계 경로 — 자기 테넌트 토큰으로 `PATCH /internal/tenants/{t}/accounts/{id}/status`, `TASK-BE-622`) | 대상이 사이트 `t` 의 **풀 멤버**면 `LOCKED` → 그 사이트 멤버십 `LOCKED` · `ACTIVE` → 멤버십 `ACTIVE` · `DELETED` → 멤버십 `LEFT`(`OPERATOR`, 아래 «사이트 탈퇴»). 계정 · 다른 사이트 무변경, 이력 행 · 이벤트 없음. 응답 `scope = SITE_MEMBERSHIP` ([account-internal-provisioning.md § Consumer-pool member](../contracts/http/internal/account-internal-provisioning.md)) | **할 수 없다**(계정 전체 잠금을 풀지도 못한다). 사이트 **자기 계정**(이커머스 셀러 운영 계정 등)은 지금처럼 계정 상태가 바뀐다(`scope = ACCOUNT`) |
  | **자동** (security-service `AUTO_DETECT`, 헤더 없음) · **본인 복구** (`USER_RECOVERY`) | (해당 없음) | 지금 그대로 — 계정 전체(자격·IAM 세션이 사이트마다가 아니라 하나다). 소유자 결정 2026-10-04 «계정 전체 유지» (`TASK-BE-621` § 소유자 결정 1) |

  - **`LOCKED` 멤버십**: 그 사이트로는 토큰이 없다(§ 4 — `ACTIVE` 멤버십만 발급; authorize 게이트는 동의 화면을 띄우지 않고 통과, 토큰 엔드포인트가 `invalid_grant`).
    **동의로 열리지 않는다**(동의 `PUT` 무변경), 본인 «사이트 탈퇴» 도 무변경(떠났다 다시 동의해 잠금을 벗지 못한다). 운영자 «GDPR 삭제» 는 `LOCKED → LEFT`(`OPERATOR`).
    잠금은 그 사이트 `consumer_site_roles` 를 지우지 않는다 — 해제하면 원래대로. 잠금 기록 `locked_at` · `locked_by_actor_id`(account-service `V0033`).
  - 다른 사이트는 영향이 없다 — 대조 시험 `ConsumerSiteLockIntegrationTest`(account-service). 🔵 이미 발급된 그 사이트 access token 은 만료까지 산다(refresh 는 거절).
  - 내부 프로비저닝 `PATCH /internal/tenants/{t}/accounts/{id}/status`(사이트 백엔드의 기계 경로)도 같은 규칙이다 — 소유자 결정 2026-10-04 «별도 티켓으로 적용»
    (`TASK-BE-621` § 소유자 결정 2 → `TASK-BE-622`). 위 표 «사이트 백엔드» 행. 🔵 2026-10-04 전수: 지금 이 경로의 유일한 호출자(이커머스 product-service 셀러 정지·폐점)는
    셀러 **운영 계정**(사이트 자기 계정)을 겨누므로 동작이 바뀌지 않는다. 대조 시험 `ProvisionStatusSiteScopeIntegrationTest`(account-service).
- 🔴 **삭제는 다르다 — «사이트 탈퇴» vs «계정 삭제» (`TASK-BE-619`, 소유자 결정 2026-10-03)**. 위 616 결정은 «사이트 운영자의 GDPR 삭제 = 풀 계정 삭제(모든
  사이트에서)» 였고, 소유자가 이것을 **«사이트 운영자 삭제 권한 = 자기 사이트 멤버십만»** 으로 바꿨다 — *한 사이트가 다른 사이트의 회원 데이터를 지울 수 없다.*
  풀 계정 삭제(GDPR 마스킹 포함)는 **본인 또는 플랫폼 관리자**만 한다.

  | 행위자 | «사이트 탈퇴» (그 사이트 멤버십 → `LEFT`) | «계정 삭제» (풀 계정 → `DELETED`, 모든 소비자 사이트에서) |
  |---|---|---|
  | **본인** (그 사이트 토큰) | `DELETE /api/accounts/me/site-membership` — 토큰의 사이트만, `left_by = SELF` ([account-api.md](../contracts/http/account-api.md)) | `DELETE /api/accounts/me` — 풀 계정 하나(616 그대로) |
  | **사이트 운영자** (활성 테넌트 = 그 사이트) | 콘솔 «GDPR 삭제» · 내부 `/delete` — 대상이 **풀 멤버**면 **그 사이트 멤버십만** `LEFT`, `left_by = OPERATOR`. 계정 · PII · 다른 사이트 멤버십 무변경. 응답 `scope = SITE_MEMBERSHIP` ([admin-to-account.md § gdpr-delete](../contracts/http/internal/admin-to-account.md)) | **할 수 없다**. 그 사이트의 **자기 계정**(풀이 아닌 사이트별 계정)은 지금처럼 삭제된다 — 그 사이트만의 계정이다 |
  | **플랫폼 관리자** (SUPER_ADMIN — 테넌트를 말하지 않는 `X-Tenant-Id: *`) | (해당 없음 — 필요하면 그 사이트 운영자로 행동) | 콘솔 «GDPR 삭제» — admin-service 가 플랫폼 스코프 운영자에게 하류 `*` 를 찍고, account-service 는 **계정 행 자신의 테넌트**로 찾아 계정 자체를 지운다(`scope = ACCOUNT`) |

  - **탈퇴 후 복귀 (소유자 결정 2026-10-03 «다시 동의하면 복귀»)**: **본인이** 떠난 사이트(`left_by = SELF`)는 다시 로그인해 그 사이트 동의 화면에서 «동의» 하면
    `ACTIVE` 로 돌아온다(§ 4 동의). **운영자가** 내보낸 사이트(`left_by = OPERATOR`)는 동의로 다시 열리지 **않는다** — 동의 화면도 뜨지 않고 토큰도 없다(615 그대로).
    두 경우는 멤버십 행의 `left_by` 로 가른다(account-service `V0032`). 기록된 작성자가 없는 `LEFT` 행(619 이전엔 작성자가 없었다 — 0행)은 운영자 쪽으로 읽는다(보수).
  - 본인이 떠난 뒤 그 사이트 운영자가 내보내면 `OPERATOR` 로 다시 기록된다(내보냄이 남는다). 운영자가 내보낸 뒤 본인이 «탈퇴» 해도 그대로다(복귀 가능으로 낮추지 못한다).
  - 떠날 때 그 사이트의 `consumer_site_roles` 행(ARTIST · SELLER …)은 지운다 — 돌아오면 그 사이트 시드 역할부터 다시 시작한다(동의가 운영자가 줬던 역할을 몰래 되살리지
    않게. 구현자 기본값, 소유자가 한 줄로 뒤집을 수 있다).
  - 떠남은 **이벤트를 내지 않는다**: 그 사이트 토큰은 다음 authorize · refresh 부터 발급되지 않고(615 — 멤버십 `LEFT` = 토큰 없음), 다시 돌아와도
    `account.created` 를 다시 내지 않는다(§ 6 «사이트마다 한 번»). 사이트가 자기 쪽 회원 데이터를 어떻게 하는지는 그 사이트의 일이다(이 절 밖).
  - 다른 사이트는 영향이 없다 — 대조 시험 `ConsumerSiteLeaveIntegrationTest`(account-service).

#### 6. 이벤트 (AC-3 결정)

`account.created` 는 **계정이 한 사이트에 처음 들어갈 때** 그 사이트 테넌트로 **한 번씩** 나간다 — 풀 가입 시 가입한 사이트로 한 번, 다른 사이트의
첫 방문 동의 때 그 사이트로 한 번. 이벤트의 뜻이 «계정이 생겼다» 에서 «이 계정이 이 테넌트에서 쓸 수 있게 됐다» 로 넓어지지만, **소비자가 보는 모양은
그대로**다 — 이커머스 user-service 의 `AccountCreatedConsumer` 는 `tenantId=ecommerce` 인 이벤트로 프로필을 만들던 그대로 만든다.
풀 단위로 한 번만 내는 안은 기각 — 그 이벤트의 `tenantId` 가 `consumer-pool` 이 되어 이커머스 소비자가 프로필을 **엉뚱한 테넌트**로 만든다.
상세 계약: [account-events.md § account.created](../contracts/events/account-events.md#accountcreated).

#### 7. 계약 문장과 그것을 지키는 시험의 자리 (AC-6)

| 문장 | 시험 자리 |
|---|---|
| 풀 값은 토큰에 나오지 않는다 · `tenant_id` = 사이트 | `TASK-BE-615` — 풀 principal 로 두 client 토큰을 디코드해 `tenant_id` 를 단언 |
| 같은 `sub` | `TASK-BE-615` — 같은 세션의 두 사이트 토큰 `sub` 동일 |
| 사이트 역할만 | `TASK-BE-615` — 스토어 토큰에 `FAN` 없음 |
| 멤버십 없으면 토큰 없음 · 동의 화면 | `TASK-BE-616` |
| 같은 이메일 공존 금지(§ 2) | `TASK-BE-614` — 사이트별 계정이 있는 이메일로 풀 가입 → 거절 |
| 사이트별 계정은 재인증 유지 | `TASK-BE-615` — `SsoTenantGateIntegrationTest` 기존 칸 그대로 초록 |
| 이메일만으로 안 묶인다 | `TASK-BE-617` 대조군 (`TASK-MONO-743` 은 2026-10-05 구현 없이 종결) — account-service `SocialSignupUseCaseConsumerPoolTest`(풀 이메일 → 409 · 경합도 409) · `ConsumerPoolSocialSignupIntegrationTest`; auth-service `OAuthLoginUseCaseConsumerPoolTest` · `ConsumerPoolSocialLoginIntegrationTest`(비밀번호 풀 계정 이메일의 소셜 → `email_registered` · 신원 행 0) |
| 소셜 가입 = 풀 계정 · 신원 행 `consumer-pool` · 다른 사이트는 동의 화면 · 사이트별 소셜 신원은 그대로 | `TASK-BE-617` — auth-service `ConsumerPoolSocialLoginIntegrationTest`; account-service `ConsumerPoolSocialSignupIntegrationTest` |
| 사이트로 찾는 목록에 풀 멤버 포함 | `TASK-BE-614` — `ecommerce` 목록에 풀 가입 쇼핑객 |
| `account.created` 사이트별 1회 | `TASK-BE-614`(가입) · `TASK-BE-616`(동의) — 이벤트 `tenantId` 단언 |
| 한 사이트 계정의 이동(§ 3) — 옮길 행 전부 · 셀러/두 사이트 제외 · 실패 시 무변경 · 재실행 · `account.created` 없음 | `TASK-BE-618` — account-service `ConsumerPoolLegacyMoveIntegrationTest` |
| 이동한 계정이 같은 비밀번호로 같은 `sub` · 이동 전 refresh 가 계속 된다 | `TASK-BE-618` — auth-service `ConsumerPoolLegacyMoveIntegrationTest` |
| 사이트 운영자의 삭제 = 그 사이트 멤버십만 · 다른 사이트 · 계정 무변경 · 플랫폼(`*`)만 계정 삭제 · 본인 탈퇴는 재동의로 복귀, 운영자 탈퇴는 아님 | `TASK-BE-619` — account-service `ConsumerSiteLeaveIntegrationTest` · `PoolMemberSiteSurfacesIntegrationTest`; auth-service `AuthorizeSessionTenantGatePoolTest`(SELF → 동의 화면 · OPERATOR → 통과·발급 거절) |
| 사이트 운영자의 잠금·해제 = 그 사이트 멤버십만 · 다른 사이트 · 계정 무변경 · 플랫폼(`*`)만 계정 잠금 · `LOCKED` 는 동의·본인 탈퇴로 안 열림 | `TASK-BE-621` — account-service `ConsumerSiteLockIntegrationTest`; auth-service `AuthorizeSessionTenantGatePoolTest`(LOCKED → 통과·발급 거절, 동의 화면 없음); admin-service `AccountAdminControllerSliceTest`(플랫폼 스코프 → `*` · 사이트 운영자 → 활성 테넌트) · `AccountAdminUseCaseTest`(감사 `SITE_MEMBERSHIP_LOCKED`) |

### 격리 회귀 방지

- **Repository 레벨**: 모든 JPA repository 메서드는 `tenant_id` 파라미터를 첫 번째 인자로 받는다. `findById(id)`처럼 `tenant_id` 없는 조회 메서드는 금지(컴파일 또는 정적 분석으로 차단)
  - **문서화된 예외 1건 (TASK-BE-602)** — account-service `AccountRepository.findByIdResolvingTenant(accountId)`. 테넌트를 **입력으로 받지 않고
    출력으로 돌려주는** 조회로, 내부 전용 `GET /internal/accounts/{accountId}/status-with-tenant`(소셜 로그인의 상태 조회) 하나만 쓴다.
    성립 조건(전역 유일 PK · 응답에 그 행의 `tenant_id` 포함 · 내부 전용 · PII 없음)과 근거는
    [auth-to-account.md § status-with-tenant](../contracts/http/internal/auth-to-account.md#get-internalaccountsaccountidstatus-with-tenant).
    이 예외를 다른 조회의 근거로 넓히지 않는다 — 새 예외는 같은 형식으로 여기에 한 줄씩 적는다.
    - **같은 finder 의 두 번째 소비처 (TASK-MONO-735, 2026-09-26 소유자 결정 (c))** — 내부 전용 `POST /internal/accounts/{accountId}/lock` ·
      `/unlock` · `/delete` 가 `X-Tenant-Id` 가 **없거나 공백이거나 `*`** 일 때만 대상 계정을 이 finder 로 찾는다(헤더 없는 자동 잠금 ·
      SUPER_ADMIN(`*`) 잠금이 `fan-platform` 밖 계정을 404 로 놓치던 결함). 🔴 **헤더가 구체 테넌트를 말하면 지금처럼 그 테넌트로 한정**
      (교차 → 404) — 헤더 유무와 무관하게 풀면 격리가 풀린다. 계정 테넌트를 **확실히** 아는 호출자(product-service 셀러 정지)는
      심층 방어로 헤더를 **명시**한다. 🔴 security-service 자동 잠금은 **싣지 않는다**(소유자 결정 2026-09-26) — 이벤트의 테넌트는
      세션 테넌트라 교차 테넌트 세션에서 계정 테넌트와 달라 404(미잠금)가 된다(`TASK-BE-611`). 근거·조건: [admin-to-account.md § Tenant Confinement](../contracts/http/internal/admin-to-account.md#tenant-confinement--x-tenant-id-task-be-467).
      `/gdpr-delete` · `/export` 는 이 소비처가 **아니다**(여전히 `fan-platform` 기본값).
    - **같은 finder 의 세 번째 소비처 (TASK-BE-619, 2026-10-03 소유자 결정 «풀 계정 삭제는 본인 또는 플랫폼 관리자»)** — 내부 `POST /internal/accounts/{id}/gdpr-delete`
      가 `X-Tenant-Id` 가 **없거나 공백이거나 `*`** 일 때 대상 계정을 이 finder 로 찾는다(MONO-735 와 같은 조건 · 같은 갈림). admin-service 는 **플랫폼 스코프
      운영자**에게만 `*` 를 찍는다. 🔴 헤더가 구체 테넌트를 말하면 그대로 한정된다(교차 → 404). `/export` 는 여전히 이 소비처가 아니다.
  - ⚪ «정적 분석으로 차단» 은 현재 **없다**(2026-09-25 확인: account-service 테스트에 ArchUnit/리플렉션 기반 규칙 0). 지금 이 규칙을 지키는 것은 리뷰뿐이다.
- **Specification/QueryDSL**: 동적 쿼리 빌더에 tenant predicate가 자동 주입되도록 base specification 제공
- **테스트**: 모든 도메인 통합 테스트에 **cross-tenant leak 회귀 테스트** 포함 — 다른 `tenant_id`로 동일 PK·이메일 조회 시 결과가 격리되는지 검증

### Rate Limit

- key 패턴에 `tenant_id` 포함: `rate:login:{tenant_id}:{ip}`, `rate:signup:{tenant_id}:{ip}`
- 테넌트별 quota를 별도로 운용 가능 (예: WMS는 더 높은 internal quota)

### Event 발행

- 모든 outbox 이벤트(`account.created`, `auth.login.succeeded` 등)에 `tenant_id` 페이로드 필수
- security-service는 tenant 단위로 비정상 탐지 임계치를 분리 적용 가능

---

## Migration of Existing Data

### 현재 상태

현재 모든 데이터(`accounts`, `profiles`, `credentials`, `refresh_tokens`, `social_identities`, `admin_operators`, `account_status_history` 등)는 단일 테넌트 가정 하에 작성되어 있다.

### 마이그레이션 단계

1. **사전 단계**: `tenants` 테이블 생성 후 `('fan-platform', 'Fan Platform', 'B2C_CONSUMER', 'ACTIVE')` row 1건 삽입
2. **스키마 변경 (Flyway)**:
   - 도메인 테이블에 `tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform'` 추가
   - DEFAULT 값으로 모든 기존 row 백필
   - 백필 완료 후 DEFAULT 제거(NOT NULL은 유지)
   - 기존 unique index 제거 후 `(tenant_id, email)` 등 복합 unique index 재생성
3. **애플리케이션 변경**: tenant-aware repository·JWT claim·gateway 헤더 전파를 단계적으로 도입
4. **검증**: 기존 사용자가 그대로 로그인되며 모든 토큰이 `tenant_id=fan-platform` claim과 함께 발급되는지 통합 테스트
5. **WMS 테넌트 등록**: 운영자 명령으로 `tenants` 테이블에 `wms` 등록. WMS 백엔드의 system credential 발급
6. **JWT key rotation 불필요**: claim 추가만이며 서명 키는 재사용

### 호환성

- 마이그레이션 진행 중 발급된 기존 access/refresh token은 `tenant_id` claim이 없을 수 있음 → gateway는 grace period(예: 30일) 동안 누락 시 `fan-platform`으로 간주하는 fallback 정책을 운영 가능. 이후 grace 만료 시 누락 토큰은 401 처리

> grace policy의 실제 적용 여부·기간은 운영 배포 시점에 별도 결정. 본 스펙은 옵션을 명시할 뿐 강제하지 않음.

---

## Edge Cases

- 동일 이메일이 `fan-platform`·`wms`에 동시에 존재 — 허용. 로그인은 항상 tenant 컨텍스트 위에서 수행
- WMS 사용자가 fan-platform 토큰으로 WMS API 호출 시도 — gateway가 path tenant 또는 다운스트림이 `tenant_id` 불일치로 403
- SUPER_ADMIN이 SUSPENDED 테넌트 사용자 강제 로그아웃 — 허용 (운영 회수 시나리오)
- WMS 테넌트 SUSPENDED 시 — 신규 로그인·refresh 모두 거부. 기존 access token은 만료까지 유효(짧은 TTL이 안전망)
- `tenant_id`가 path와 JWT 모두에 있는데 둘이 다름 — 항상 거부, 로그에 기록 (잠재적 공격 시그널)
- 마이그레이션 중 `tenant_id` 컬럼 추가 직후 NULL row 잔존 — 백필 완료 전 NOT NULL 제약 활성화 금지

---

## Platform Console (TASK-BE-296)

`platform-console` 는 IAM 가 발급하는 토큰으로 federated 운영 콘솔을 구성한다
(ADR-MONO-013 D5,
[console-integration-contract](../../../platform-console/specs/contracts/console-integration-contract.md)).
IAM 는 두 가지 producer-side 선행물을 제공한다.

### OIDC public client `platform-console-web`

- auth-service `oauth_clients` 에 Flyway 시드 (V0015) 된 **public client**:
  `client_id=platform-console-web`, `client_authentication_methods=["none"]`
  (client secret 없음), `authorization_grant_types=authorization_code,refresh_token`,
  PKCE 필수 (`require-proof-key=true`), scope = `openid` `profile` `email`
  `tenant.read`.
- 등록된 `redirect_uri` (OAuth2 **정확 일치** 검증이므로 전수를 적는다):
  | URI | 시드 |
  |---|---|
  | `http://console.local/api/auth/callback` | `V0015` |
  | `http://localhost:3000/api/auth/callback` | `V0015` |
  | `https://console.hubwang.com/api/auth/callback` | `V0034` (TASK-BE-589, `ADR-MONO-067` 단계 3) |

  콜백 경로에 **`/iam` 접미사가 없다** — 이 저장소의 다른 클라이언트와 다르다
  (`TASK-MONO-460`). 데모 부팅 시 `infra/demo/seed-demo-domain.sh` 가 `.local` 항목의
  데모-도메인 사본을 **덧붙이지만**, `.hubwang.com` 항목은 그 술어(`LIKE '%.local/%'`)에
  걸리지 않아 부팅마다 그대로 남는다.
- refresh token 회전·재사용 탐지는 기존 public-client lineage 와 **동일 경로**
  (`PublicClientRefreshTokenAuthenticationConverter` +
  `PublicClientRevokeAuthenticationConverter` +
  `SasRefreshTokenAuthenticationProvider`, ADR-003 옵션 B closure /
  TASK-BE-272/274) — converter 가 `ClientAuthenticationMethod.NONE` 인 **모든**
  등록 client 에 대해 fire 하므로 신규 wiring 불필요. 회전 시
  `reuse-refresh-tokens=false` 로 매 refresh rotation, 재사용 시 체인 전체
  invalidate.
- `tenant_id='iam'` 로 스코프됨 — `iam` 은 [TenantId](#tenantid) 예약어이므로
  consumer 테넌트와 충돌 불가. 운영자 신원·cross-tenant 선택은 이 client 의
  `tenant_id` 가 아니라 admin-service operator JWT + ADR-002 tenant-scope
  sentinel 로 결정된다.

### Product / Tenant Registry read surface

- admin-service 가 `GET /api/admin/console/registry` 로 노출 (operator JWT 필수,
  read-only, audit row 없음). 상세 contract:
  [console-registry-api.md](../contracts/http/console-registry-api.md).
- 응답은 7개 product (`iam`/`wms`/`scm`/`erp`/`finance`/`ecommerce`/`fan`) 의 catalog
  이며 각 product 의 `available` 플래그 + 운영자가 선택 가능한 `tenants` +
  `displayName` + `baseRoute` 를 담는다 (console-integration-contract § 2.2
  shape).
- **operator-scoped + tenant-aware**: platform-scope(`tenant_id='*'`,
  SUPER_ADMIN) 운영자는 등록된 모든 ACTIVE 테넌트를 선택 가능; 단일 테넌트
  운영자는 자신의 테넌트 1개만 (`tenants` length ≤ 1). 다른 테넌트의 slug 는
  어떤 product 의 `tenants` 에도 노출되지 않는다 (cross-tenant 격리 회귀 테스트
  필수, 본 문서 [격리 회귀 방지](#격리-회귀-방지) + M6).
- `fan` (TASK-MONO-751 — ADR-MONO-079 D4-A, 팬 **디렉터리** 관리: 소속사·아티스트·그룹) 은
  `fan-platform` 의 `fan` 구독(account-service V0031)으로 바인딩되고 **플랫폼 운영자(`'*'`)
  에게만** 나열된다(라이더 R3). 고객사 운영자에게는 `fan-platform` 을 이름한 assignment row
  가 있어도 어떤 product 의 `tenants` 에도 나오지 않는다 — assume 게이트(TASK-MONO-750
  step 2b)와 같은 술어 `AdminOperator.isPlatformOperatorOnlyTenant`.
- `admin_operators.confined_tenant_id`(TASK-MONO-751, 2026-10-03 소유자 결정 «데모 운영자는 팬 전용으로») 가 비-NULL 인 운영자는
  그 **한 테넌트만** assume 할 수 있고 레지스트리에도 그 테넌트만 나온다 — `'*'` 의 «모든 테넌트» 보다 먼저 적용되는 좁히기.
  데모 플랫폼 운영자(`platform@demo.com`)는 `fan-platform` 에 묶여 있다. NULL = 기존 동작.
- 6 federated domains (`iam` + `wms` + `scm` + `erp` + `finance` + `ecommerce`)
  는 모두 V1 live 이며 `available:true` 로 노출된다 (TASK-BE-305 2026-05-21
  reality-alignment — finance Phase 5 COMPLETE 2026-05-19/20 + erp Phase 6
  COMPLETE 2026-05-20 per ADR-MONO-013 § D6; `ecommerce` 는 TASK-MONO-240
  2026-06-13 per ADR-MONO-030 ACCEPTED — `tenant_domain_subscription`
  `domain_key='ecommerce'` self-seed V0022 로 subscription-driven 바인딩).
  **렌더는 data-driven (console-web 코드 변경 0)**: 기존 멤버의 `available`
  flip/`displayName`/`tenants` 변경은 registry 변경만으로 충분하고 `ServiceTile`
  의 interactive/non-interactive 분기를 결정한다. **단, 새 `productKey` 추가는
  예외** — console-web `ProductKeySchema` Zod enum 1줄 확장이 필수 (고정-멤버십
  가드; 누락 시 registry 응답이 `RegistryResponseSchema.parse` 에서 throw →
  전 catalog degraded). 따라서 새 도메인 추가는 producer item + consumer enum 을
  동일 atomic PR 로 (Change Rule 3; ADR-MONO-030 § 6 factual correction).
- tenant 목록은 account-service 가 owns (`tenants` 테이블) 하며 admin-service
  `ListTenantsUseCase` 의 read-through proxy 로 조회. account-service 불가
  시 부분 catalog 가 아니라 503 (degradation 은 콘솔 섹션 한정).

---

## Related Contracts

본 feature 도입에 따라 다음 컨트랙트의 업데이트가 필요하다 (실제 갱신은 본 스펙 승인 후 별도 PR):

- HTTP: `auth-api.md` (login/refresh 응답에 tenant_id 명시), `account-api.md` (signup의 tenant 컨텍스트), `admin-api.md` (tenant CRUD)
- Internal: `auth-to-account.md` (credential lookup에 tenant 파라미터), `account-internal-provisioning.md` (신규)
- Events: 모든 이벤트 페이로드에 `tenant_id` 필드 추가, 스키마 버전 +1

---

## Out of Scope (이 스펙에서 다루지 않음)

- 테넌트별 빌링/usage metering — 본 플랫폼은 계정 인프라이며 과금은 다루지 않음
- 테넌트별 데이터 export/이전 — 향후 enterprise 계약 요건이 발생할 때 별도 스펙
- schema-level 또는 DB-level 격리로의 전환 절차 상세 — 트리거 조건 발생 시 ADR로 결정
- WMS 자체의 권한 매트릭스 — WMS 프로젝트가 소유. 본 플랫폼은 role 이름과 매핑만 관리
- B2B SSO(SAML/OIDC IdP federation) — 향후 enterprise 요구사항. 별도 feature spec
