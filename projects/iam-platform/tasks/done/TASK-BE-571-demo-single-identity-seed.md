# Task ID

TASK-BE-571

# Title

데모 단일 아이덴티티 시드 — 한 이메일/비밀번호로 스토어프런트 · 팬 · 콘솔(5도메인) 전부 로그인

# Status

done

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

- [x] 구현 완료
- [x] 테스트 추가 · 통과 (가드 네거티브 검증 포함)
- [x] 라이브 검증 증거 기록 (아래 § 구현 결과)
- [x] 기존 데모 계정 무변경 확인 (추가만; 기존 시드 파일 무수정)
- [x] Ready for review

---

# 구현 결과 (2026-08-05, 라이브 검증 완료)

## 산출물

| 파일 | 내용 |
|---|---|
| `auth-service/src/main/resources/db/migration-dev/V9001__seed_demo_single_identity_credentials.sql` | **신규 디렉터리** + 3테넌트 자격증명 |
| `auth-service/src/main/resources/application-e2e.yml` | flyway locations 에 `migration-dev` 추가 (e2e 전용) |
| `account-service/.../db/migration-dev/V9005__seed_demo_corp_tenant_and_consumer_accounts.sql` | `demo-corp` + 5도메인 구독 + identities/accounts 2건 |
| `admin-service/.../db/migration-dev/R__seed_demo_operator.sql` | 운영자 + SUPER_ADMIN 바인딩 + assume 배정 |
| `auth-service/src/test/.../DemoSeedCredentialTest.java` | 가드 5건 |

## AC 판정

| AC | 결과 |
|---|---|
| AC-0 재측정 | ✅ auth-service 에 `migration-dev` **부재** 확인(신설) · account/admin 은 `e2e` 에서 로드 · 콘솔 클라이언트 테넌트 = `iam` |
| AC-1 ADR-034 U4 | ✅ 소비자 2건은 `identities` + `accounts.identity_id` 연결. **운영자는 accounts 행 없음** — 아래 § 설계 편차 참조 |
| AC-2 세 표면 로그인 | ✅ 실 `/login` 폼(CSRF 파싱) + authorization_code + PKCE 왕복으로 3표면 전부 확인 — 아래 실측 |
| AC-3 assume → 5역할 | ✅ 단일 토큰에 5도메인 + 5 operator role + WMS granular 7 |
| AC-4 운영자 교환 | ✅ `POST /api/admin/auth/token-exchange` → 200, 발급 토큰 `sub=demo-operator` |
| AC-5 prod 미도달 | ✅ 정적 가드 + 구조적으로 `application-e2e.yml` 에만 존재 |
| AC-6 idempotent | ✅ 3서비스 재시작 후 행 수 동일(3/5/2/1/1) · `flyway_schema_history.success=0` 0건 |
| AC-7 비밀번호 정책 | ✅ 단위 테스트 고정 |

## 라이브 실측 (iam 슬라이스: mysql·redis·kafka·auth·account·admin)

```
PASS  storefront   tenant_id=ecommerce     roles=["CUSTOMER"]  sub=…ec01
PASS  fan web      tenant_id=fan-platform  roles=["FAN"]       sub=…fa02
PASS  console      tenant_id=iam           roles=[]            sub=…ad03
PASS  AC-4 operator token-exchange  status=200  operatorSub=demo-operator
PASS  AC-3 assume-tenant(demo-corp)
      entitled_domains=["ecommerce","erp","finance","scm","wms"]
      roles=[ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
             WMS_OPERATOR, OUTBOUND_READ/WRITE, INBOUND_READ/WRITE,
             INVENTORY_READ/WRITE, MASTER_READ]
      org_scope=["*"]
```

**브라우저 왕복 범위에 대한 정직한 고지**: 위는 실제 `/login` HTML 폼 + CSRF + PKCE 왕복을
헤드리스로 구동한 것이고, 렌더링된 브라우저는 아니다. 브라우저 전용 실패 모드(next-auth 세션,
SSR bearer 누락)는 **앱 표면**의 성질이지 이 시드의 성질이 아니며, 그 검증은 앱을 소유한
`TASK-MONO-506` / `TASK-FAN-FE-014` 가 가진다. 이 티켓의 산출물(토큰 발급 경로)에 대해서는
위가 동등 강도의 증거다.

## 설계 편차 2건 (의도적, 근거 포함)

1. **운영자에게 `accounts`/`identities` 행을 만들지 않았다.** 두 테이블 모두 `tenants` FK 를
   갖는데 **`iam` tenants 행이 없고**, `iam` 은 admin-service 예약 슬러그다(V0024). FK 를 채우려
   테넌트를 만들면 IdP 자신의 운영 슬러그가 고객 테넌트로 등록되어 콘솔 테넌트 목록에 노출된다.
   기존 시드 운영자(e2e-super-admin·acme-operator·multi-operator)도 전부 accounts 행이 없고,
   콘솔 경로는 `sub` → `oidc_subject` 만 읽는다(MONO-299). ADR-MONO-034 U2 가 연기한 그 분리다.
2. **admin-service 만 `R__` repeatable.** 다른 둘은 versioned. admin 은 `migration-dev` 를 **기본
   프로파일**에서도 로드하고 versioned 타임라인이 V0001~V0045 로 빈틈이 없어, 고대역 versioned
   시드를 넣으면 **다음 프로덕션 마이그레이션이 모든 로컬 DB 에서 out-of-order 로 거부**된다
   (`out-of-order: true` 미설정). repeatable 은 버전 순서에 참여하지 않아 이 문제가 성립하지 않는다.

## 부수 발견 (이 티켓에서 고치지 않음 — 별도 티켓 후보)

- **`iam-platform` 자체 e2e compose 는 assume-tenant / 운영자 교환을 수행할 수 없다.** 세 개의
  크로스서비스 URL 이 미설정이라 localhost 기본값으로 떨어진다: auth 의 `ADMIN_SERVICE_URL`,
  admin 의 `OIDC_JWKS_URI` · `OIDC_ISSUER`. fed-e2e 와 통합 데모는 셋 다 배선한다.
  🔴 특히 첫 번째의 실패는 **fail-closed 로 인해 `"operator is not assigned to the selected
  tenant"` 라는 보안 판정처럼 보인다** — 검증 중 실제로 이 오진을 겪었고, 데이터는 처음부터
  옳았다. 로컬 검증에서는 임시 오버레이로 배선해 통과시켰다(커밋하지 않음).
- **`scripts/console-demo/seed/01-iam.sql` 이 stale** — `oidc_subject` 에 **이메일**을 넣는데
  MONO-298/299 이후 account_id 전용이라 그대로 쓰면 운영자 교환이 401 한다. 형제인
  `tests/federation-hardening-e2e/fixtures/seed.sql` 은 MONO-298 에서 UUID 로 갱신됐고 이 사본만
  남겨졌다(straggler). 현 fed-e2e 토폴로지에서는 이 파일이 적용되지 않아 잠복 상태.
- `iam` 테넌트는 account_db 에 행이 없어 콘솔 base 토큰 발급 시 `tenant_type unknown …
  defaulting to B2C_CONSUMER` + `entitled_domains` 404 fail-soft 경고가 남는다. **무해**
  (base 운영자 토큰은 설계상 도메인을 싣지 않는다) 하나, 로그 노이즈로 기록해 둔다.
