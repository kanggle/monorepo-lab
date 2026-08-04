# Task ID

TASK-BE-571

# Title

데모 단일 아이덴티티 시드 — 한 이메일/비밀번호로 스토어프런트 · 팬 · 콘솔(5도메인) 전부 로그인

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# 배경 — 왜 "아이디 하나" 가 자명하지 않은가

자격증명은 **테넌트 스코프**다: `credentials` 의 유니크 키가 `(tenant_id, email)` 이고
(`V0007__add_tenant_id_to_auth_tables.sql`), 로그인 시 테넌트는 **OIDC 클라이언트가 결정**한다
(`SavedRequestTenantResolver` 가 saved `/oauth2/authorize` 의 `client_id` → 그 클라이언트의
`custom.tenant_id`). 따라서 세 표면은 서로 다른 테넌트로 인증된다:

| 표면 | 클라이언트 | 테넌트 |
|---|---|---|
| 스토어프런트(web-store) | `ecommerce-web-store-client` | `ecommerce` |
| 팬 웹 | `fan-platform-user-flow-client` | `fan-platform` |
| 콘솔 | `platform-console-web` | `iam` (V0024 가 `gap` → `iam` 로 변경) |

⇒ **사용자 체감은 아이디 하나이되, 저장 형태는 세 테넌트의 자격증명 세 행이다.** 이것은 우회가
아니라 현행 모델이다 — `ADR-MONO-034` U2 가 "step-3 은 link-first, **login/credential consolidation 은
step 4 로 연기**" 로 명시했고, 운영자↔소비자 브릿지는 `admin_operators.oidc_subject`(소비자
`account_id` 를 가리키는 포인터)로 이미 정의돼 있다. **ADR 신규 불필요** — 이 태스크는 그 모델의 적용이다.

콘솔의 도메인 접근은 **assume-tenant 시 구독 도메인에서 역할이 파생**된다
(`OperatorRoleDerivation.fromEntitledDomains`). 5도메인을 구독한 테넌트 하나를 만들면
`ECOMMERCE_OPERATOR` · `WMS_OPERATOR`(+granular) · `SCM_OPERATOR` · `ERP_OPERATOR` · `FINANCE_OPERATOR`
가 한 번에 파생되어 **스위처 전환 없이** 5섹션이 열린다. 5도메인 게이트웨이·서비스가 전부
`TenantClaimValidator ... .trustEntitledDomains()` 로 배선돼 있어 `tenant_id=demo-corp` 토큰이
각 도메인 게이트를 통과한다(2026-08-05 코드 확인, 12곳).

---

# Goal

`SPRING_PROFILES_ACTIVE=e2e` 로 기동한 IAM 스택에서 아래가 성립한다.

```
아이디  demo@demo.com
비번    Demo1234!     (9자 · 대/소/숫자/특수 4종 → PasswordPolicy 통과)
```

1. 스토어프런트에서 로그인 → 토큰 `tenant_id=ecommerce`, `roles:[CUSTOMER]`
2. 팬 웹에서 로그인 → 토큰 `tenant_id=fan-platform`, `roles:[FAN]`
3. 콘솔에서 로그인 → 운영자 교환 성공 → `demo-corp` assume → 5 operator role 파생

---

# Scope

## In Scope

- **auth-service**: `src/main/resources/db/migration-dev/` **신설** + `application-e2e.yml` 의
  `spring.flyway.locations` 에 추가 → 세 테넌트의 `credentials` 행(BCrypt 해시)
- **account-service**: `db/migration-dev/` — 테넌트 `demo-corp` + `tenant_domain_subscription` 5행
  (ecommerce · wms · scm · erp · finance, ACTIVE) + `accounts` 3행 + `identities` 3행 +
  `accounts.identity_id` 연결
- **admin-service**: `db/migration-dev/` — `admin_operators`(`oidc_subject` = iam 테넌트 account_id)
  + `operator_tenant_assignment`(→ `demo-corp`)
- 위 시드가 **prod 프로파일에 도달하지 않음**을 지키는 가드 테스트

## Out of Scope

- `db/migration/`(운영 마이그레이션) 수정 — 데모 시드는 dev 전용이다
- 기존 데모 계정(`e2e-super-admin`, `multi-operator`, `acme-corp`, `globex-corp`) 변경/삭제 —
  다른 하네스와 CI 가 의존한다. **추가만 한다**
- 신규 API / 콘솔 화면
- 도메인 데이터 시드 — `TASK-MONO-506` 소유

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 코드로 재확인한다: (a) auth-service 에 `migration-dev` 가 실제로
      **없는지**, (b) `e2e` 프로파일에서 account/admin 의 `migration-dev` 가 로드되는지
      (`application-e2e.yml` 의 `locations`), (c) 콘솔 클라이언트의 현재 `tenant_id` 값.
      이 티켓의 배경 표와 어긋나면 코드가 이긴다
- [ ] **AC-1 (ADR-034 U4 정합)** — 생성되는 모든 account 가 `identities` 레코드를 갖고
      `accounts.identity_id` 가 채워진다. 기존 아이덴티티는 **하나도 병합·변경되지 않는다**
      (U3 "forced/silent merge 금지")
- [ ] **AC-2 (세 표면 로그인)** — 세 클라이언트 각각으로 실제 authorization_code 흐름을 돌려
      발급 토큰의 `tenant_id` / `roles` 클레임을 단언한다. 최소 1개 표면은 **브라우저 왕복**으로 확인한다
      (헤드리스 fetch 는 SAS 의 `Accept: text/html` 요구 때문에 실 브라우저와 다르게 동작할 수 있다)
- [ ] **AC-3 (assume → 5역할)** — `demo-corp` assume-tenant 토큰의 `roles` 클레임이
      `ECOMMERCE_OPERATOR` · `WMS_OPERATOR` · `SCM_OPERATOR` · `ERP_OPERATOR` · `FINANCE_OPERATOR` 를
      전부 포함한다(WMS granular 역할 포함 여부도 실측 기록)
- [ ] **AC-4 (운영자 교환)** — 콘솔 로그인 시 `admin_operators.oidc_subject` 가 토큰 `sub`(=account_id)와
      일치해 operator 교환이 성공한다. 불일치는 이 시드의 가장 흔한 실패 모드이므로 값의 출처를
      마이그레이션 안에서 **결정적으로** 만든다(하드코딩 UUID 를 세 DB 가 공유하게 하거나, 조회로 유도)
- [ ] **AC-5 (prod 미도달)** — `prod` 프로파일로 기동하면 `migration-dev` 가 로드되지 않는다.
      admin-service `application-prod.yml` 이 이미 이 불변식을 갖고 있으므로 **auth-service 에도
      동일 불변식이 성립**하는지 확인하고, 없으면 만든다. 가드 테스트로 고정
- [ ] **AC-6 (idempotent / 재적용 안전)** — Flyway 재실행 및 기존 볼륨 위 적용에서 오류 없음.
      기존 시드(globex/acme/initech/umbrella/ippilot)와 버전 번호가 충돌하지 않는다
- [ ] **AC-7 (비밀번호 정책)** — `Demo1234!` 가 `PasswordPolicy.validate` 를 통과함을 단위 테스트로 고정한다
      (해시를 직접 INSERT 하면 정책을 우회하므로, 정책이 바뀌어 비번을 못 바꾸게 되는 상황을 막는다)

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `projects/iam-platform/PROJECT.md`
> 의 domain/traits 로 `rules/common.md` + `rules/domains/<domain>.md` + `rules/traits/<trait>.md` 로드.

- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` — U1~U7 (특히 U3 안전 불변식, U4)
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` — D5/D6-A
- `projects/iam-platform/specs/features/multi-tenancy.md`
- `platform/security-rules.md`
- `platform/jwt-standard-claims.md`

# Related Skills

- `.claude/skills/backend/...`
- `.claude/skills/INDEX.md`

---

# Related Contracts

- `specs/contracts/http/` — **변경 없음**(시드만). 토큰 클레임 형태는
  `platform/jwt-standard-claims.md` 를 따르며 이 태스크가 바꾸지 않는다

---

# Target Service

- `auth-service`
- `account-service`
- `admin-service`

---

# Architecture

- `projects/iam-platform/specs/services/auth-service/architecture.md`
- `projects/iam-platform/specs/services/account-service/architecture.md`
- `projects/iam-platform/specs/services/admin-service/architecture.md`

---

# Implementation Notes

- **세 DB 를 가로지르는 UUID 정합이 이 태스크의 핵심 난이도다.** `auth_db.credentials.account_id`,
  `account_db.accounts.id`, `admin_db.admin_operators.oidc_subject` 가 같은 값이어야 한다.
  세 서비스의 Flyway 는 서로를 모르므로, 값을 **리터럴 상수로 고정**하는 편이 조회보다 안전하다
  (기존 dev 시드 `V0028__seed_dev_operator_oidc_subject.sql` 가 같은 문제를 어떻게 풀었는지 먼저 볼 것).
- `tenant_domain_subscription` 은 auth-service 가 **로그인 시점에 라이브 조회**하므로
  런타임 INSERT 도 다음 로그인에 반영된다 — 다만 이 태스크의 산출물은 마이그레이션이어야 한다
  (재현 가능성이 목적).
- 버전 번호는 기존 `migration-dev` 계열과 충돌하지 않게 잡는다(account-service 는 `V9001~V9004` 사용 중).
- 데모 계정은 **비밀번호를 쉽게** 유지하는 것이 요구사항이다. 정책을 완화하지 말고, 정책을 통과하는
  쉬운 비번을 쓴다.

---

# Edge Cases

- 이미 `demo@demo.com` 이 어느 테넌트에 존재하는 볼륨에 적용될 때 → 유니크 위반. idempotent 처리
- 콘솔 운영자 교환은 `oidc_subject` 불일치 시 401 이며, 화면에는 `operator_exchange_unavailable`
  로 보인다 — 부하로 인한 5s 타임아웃과 **증상이 같다.** 원인 구분을 로그로 한다
- `iam` 테넌트에 소비자 account 를 만드는 것이 기존 예약어/제약과 충돌하지 않는지 확인
  (`iam` 은 admin-service 예약어 집합에 있다)
- assume-tenant 는 `operator_tenant_assignment` 조회가 fail-closed 다 — 행이 없으면 스위처에 안 보인다

---

# Failure Scenarios

- **세 DB UUID 불일치** → 로그인은 되는데 콘솔만 안 됨(가장 흔함). AC-4
- **`migration-dev` 가 prod 에 실린다** → 데모 계정이 운영 경로에 존재. AC-5 가드
- **기존 데모 계정을 건드림** → fed-e2e / console-demo / CI 가 깨진다. Out of Scope 준수
- **CI green 인데 라이브 실패** — 인증 경로는 CI 가 실 토큰 흐름을 재현하지 못할 수 있다
  (선례: CI 13/0 GREEN 인데 라이브 403). AC-2 의 브라우저 왕복이 유일한 증거
- **정책 변경으로 데모 비번이 무효화** → AC-7 단위 테스트가 조기 경보

---

# Test Requirements

- 단위: `PasswordPolicy` 로 데모 비번 검증(AC-7)
- 통합: `e2e` 프로파일 부팅 후 세 테넌트 credential 존재 + `identity_id` 연결 단언
- 통합: assume-tenant 토큰 `roles` 클레임 단언(AC-3)
- 가드: `prod` 프로파일에서 dev 시드 미적용(AC-5)
- 라이브: 브라우저 로그인 1회 이상(AC-2)

---

# Definition of Done

- [ ] 구현 완료
- [ ] 테스트 추가 · 통과
- [ ] 라이브 브라우저 검증 증거 기록
- [ ] 기존 데모 계정 무변경 확인
- [ ] Ready for review
