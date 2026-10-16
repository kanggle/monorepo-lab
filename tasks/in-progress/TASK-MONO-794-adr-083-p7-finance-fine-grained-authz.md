# Task ID

TASK-MONO-794

# Title

`ADR-MONO-083` P7 — Finance 세분 서버 인가: 계좌 · 원장의 조회/변경 역할 · `FINANCE_OPERATOR` · 일반 `OPERATOR/ADMIN` 은 진입 역할로 · iam 카탈로그 확장

# Status

in-progress

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (필터 체인 권한 · 원장 고위험 쓰기 · 원자 PR)
> 🔴 **cross-project 원자 PR**: finance 인가 + iam 카탈로그 · «이전 호환» Finance 세트 갱신.

# Dependency Markers

- **선행**: `TASK-BE-631` · `TASK-BE-632`.
- 형제: `TASK-MONO-792` · `793` · `795`.

# Goal

Finance 쓰기는 `SCOPE_finance.write ∨ OPERATOR/ADMIN/SUPER_ADMIN/FINANCE_OPERATOR` 하나로 열리고(`account-service …/SecurityConfig.java:116-124` · `ledger-service …/SecurityConfig.java:118-126`), 원장은 분개 · 환율 덮어쓰기 · 기간 마감 · 대사 해소가 같은 권한이다. 계좌 · 원장을 조회/변경으로 나눈다.

# Scope

## In Scope

- 계약 먼저: finance 서비스 명세 · 계약(🔵 이번 조사에서 finance 명세를 열지 않았다 — AC-0).
- 두 서비스 필터 체인 · 앱 단 판정 갱신. account 의 KYC · 충전 «운영자 전용» 앱 단 검사는 새 역할 축에 맞춰 유지.
- 🔴 원장의 기간 마감 · 환율 덮어쓰기: «원장 변경» 에 포함할지, 관리 등급으로 떼어 **지금처럼 운영자에게 줄지 · 아무에게도 안 줄지** — ADR-083 D2(지금 발급 안 하는 등급은 자동 부여 금지)에 따라 AC-0 에서 측정 후 소유자 결정으로 올린다.
- iam 카탈로그 · «이전 호환» Finance 세트 · 시스템 프리셋.
- `SCOPE_finance.write` — 콘솔 클라이언트에 등록되지 않았음을 재확인하고, 등록 시 같은 우회가 된다는 가드(시험 또는 명세 문장)를 둔다.

## Out of Scope

- Finance 데이터 범위(테넌트 행 격리만 — 무변경).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: finance 명세 · 코드 판정 대조 · 원장 고위험 쓰기 목록 · 업무 구분안(OD-6) · 원장 고위험 쓰기 처리 소유자 결정. → § AC-0 기록(2026-10-16 UTC) — 측정 완료 · 🔴 **소유자 결정 대기**(Q1 원장 고위험 쓰기 = 이 티켓이 요구한 결정 · Q2 역할 이름 · 프리셋 · Q3 계좌 KYC · 충전) → § 질문과 추천.
- [ ] **AC-1** — 🔴 대조군: 계좌 세트만 → 원장 변경 403 · 계좌 200 / 조회 세트 → 변경 403 / 진입 역할만 → 업무 변경 403.
- [ ] **AC-2** — 이전 호환 대조군(기존 운영자 같은 API 집합 200).
- [ ] **AC-3** — 배포 상태를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3 · § 도메인 업무 구분 제안(Finance 행)
- `projects/finance-platform/specs/services/*/architecture.md`

# Related Contracts

- `projects/finance-platform/specs/contracts/http/*` · iam `admin-api.md` § 카탈로그

# Edge Cases

- `FINANCE_VIEWER`(구독 합성 READ) — 세분 조회 세트를 무력화하는지 AC-0 측정(ERP 와 같은 질문).
- 와일드카드 GET 전용 권한 — 무변경.

# Failure Scenarios

1. 일반 `OPERATOR` / `ADMIN` 역할 문자열을 쓰기 권한에 남긴다 — 다른 경로로 그 문자열을 받은 토큰이 전부 연다.
2. 원장 고위험 쓰기를 소유자 결정 없이 «원장 변경» 세트에 넣는다 — 관리 등급의 조용한 부여.

---

# AC-0 기록 — 2026-10-16 UTC (base `baff0fd45` = 793 머지 직후 · `origin/main` 과 차이 0) · 🔴 **STOP — 착수 전 소유자 결정 필요**

> 측정 = 코드 읽기(정적). 라이브 DB · 라이브 토큰은 열지 않았다(⚪). 이 기록 뒤 구현 커밋은 **없다** — Q1(이 티켓이 AC-0 에서 소유자 결정으로 올리라고 적은 것) · Q2 · Q3 의 답을 받은 뒤 계약부터 시작한다. 선례 = `TASK-MONO-792`(SCM) · `TASK-MONO-793`(ERP) — 그 두 티켓의 상시 결정은 아래 § 4 에서 **유추로 그대로 적용**하고 질문으로 올리지 않았다.

## 0. 선행 조건 — 충족

- `TASK-BE-631` · `TASK-BE-632` done(`d819e3781`). 운영자 assume 토큰의 범위 = 콘솔 클라이언트 등록 범위 − `OPERATOR_EXCLUDED_SCOPES`(`AssumeTenantAuthenticationProvider.java:105` = `{erp.write}`).
- 🔵 **`finance.write` · `finance.read` 는 콘솔 클라이언트에 없다(재확인)**: 등록 범위 = `V0015`(`openid, profile, email, tenant.read`) + `V0023`(`erp.write`). `finance.*` 의 등록처는 auth `V0017:72` 의 `finance-platform-internal-services-client`(워크로드, `client_credentials`, 역할 없음) **하나뿐**(finance 밖 `projects/` grep). ⇒ 지금은 범위 우회가 없다. 등록이 바뀌면 같은 구멍 — 가드는 § 4 구현 노트.
- 🔵 **일반 `OPERATOR` · `ADMIN` · `SUPER_ADMIN` 역할 문자열의 발급처 = 0**: auth-service `src/main` 에서 세 문자열이 나오는 곳은 `WorkloadRoleCatalog.java:110` `ADMIN_TIER_ROLES`(**금지 목록**) 하나뿐. 운영자 assume = `OperatorRoleDerivation` 파생(`finance` → `FINANCE_OPERATOR`, `:212`) · 기본 로그인 = 소비자 역할 · 워크로드 = 금지 목록 밖. ⇒ 필터 체인의 세 문자열은 지금 **아무도 안 여는 문**이고, 남기면 Failure 1 그대로다(793 에서 `ERP_ADMIN` · `SUPER_ADMIN` 을 뺀 것과 같은 판단).

## 1. 지금 판정 (2 서비스 · 필터 체인 `hasAnyAuthority` — 앱 단 판정은 account 두 곳뿐)

| 서비스 | READ (`GET /api/finance/**`) | WRITE (`POST/PUT/PATCH/DELETE`) | 앱 단 | 출처 |
|---|---|---|---|---|
| account | `SCOPE_finance.read` ∨ `SCOPE_finance.write` ∨ `ROLE_{OPERATOR,ADMIN,SUPER_ADMIN,FINANCE_OPERATOR}` ∨ **`ROLE_FINANCE_VIEWER`(합성)** ∨ `ROLE_FINANCE_SUPERADMIN_READ`(`'*'` 합성) | `SCOPE_finance.write` ∨ 위 운영자 역할 4 | KYC 승급 · 충전 = `actor.isOperator()`(같은 역할 4 — 범위만 가진 워크로드는 403) · 감사 `ActorType` = `isOperator()` ? OPERATOR : HOLDER | `account …/SecurityConfig.java:85-87,106-123` · `ActorContext.java:17-30` · `AccountApplicationService.java:140,355` |
| ledger | 위와 같음 | 위와 같음 — 🔴 **분개 · 기간 마감 · 환율 덮어쓰기 · 대사 해소가 같은 한 권한** | 없음(`ActorContext` 는 `hasRole` · `identity()` 만) | `ledger …/SecurityConfig.java:87-89,108-125` · `ledger …/ActorContext.java` |

- `ROLE_FINANCE_VIEWER` = `entitled_domains ∋ finance` 면 합성(`ActorContextJwtAuthenticationConverter.java` account `:106` · ledger `:107`), `ROLE_FINANCE_SUPERADMIN_READ` = `tenant_id='*'` 면 합성(account `:123` · ledger `:124`). 둘 다 READ 만.
- 🔴 **`FINANCE_VIEWER` 는 «조회 세트 없음 → 조회 403» 을 깬다**(Edge Case 의 질문 — 실측): 운영자 assume 토큰은 선택 테넌트의 ACTIVE 구독을 `entitled_domains` 로 **항상** 싣는다(793 § 2 와 같은 근거 — `TenantClaimTokenCustomizer.populateEntitledDomains`). ⇒ Finance 세트가 0 이어도 Finance 를 구독한 테넌트에 assume 한 운영자는 계좌 · 원장 READ 전부가 200. 793 Q1 과 **같은 모양** ⇒ 상시 결정(걷는다)을 유추로 적용(§ 4).

## 2. 엔드포인트 전수 (JWT 경로 **39** = 조회 19 · 변경 20 — 컨트롤러 `@*Mapping` 전수)

| 서비스 | 조회 | 변경 |
|---|---|---|
| account (10) | `GET /accounts/{id}` · `/{id}/balances` · `/{id}/transactions` (3) | 개설 `POST /accounts` · 🔵 **KYC 승급** `POST /{id}/kyc/upgrade`(운영자 전용) · 🔵 **충전** `POST /{id}/topups`(운영자 전용 — 돈이 들어오는 유일한 길) · 홀드 `POST /{id}/holds` · `…/capture` · `…/release` · 이체 `POST /{id}/transfers` (7) |
| ledger (29) | 분개 `GET /entries/{id}` · 계정 `GET /accounts/{code}/entries` · `/balance` · 시산표 `GET /trial-balance` · 기간 `GET /periods` · `/{id}` · 환율 `GET /fx-rates` · `/{ccy}/history` · 정산 설정 `GET /settlements/cost-flow-config` · `/cost-flow-config/accounts` · `/fx-rate-override/{ccy}` · `/{code}/{ccy}/lots` · 대사 `GET /reconciliation/statements/{id}` · `/discrepancies` · `/discrepancies/{id}` · `/fx-tolerance` (16) | **(가) 업무 쓰기 6** — 수동 분개 `POST /entries` · 정산 `POST /settlements` · 재평가 `POST /revaluations` · 명세 수입 `POST /reconciliation/statements` · 대사 해소 `POST /reconciliation/discrepancies/{id}/resolve` · 환율 갱신 `POST /fx-rates/refresh` · **(나) 통제 쓰기 7** — 기간 개시 `POST /periods` · 🔴 기간 마감 `POST /periods/{id}/close` · 🔴 환율 덮어쓰기 `PUT /settlements/fx-rate-override/{ccy}` · 대사 환차 허용오차 `PUT /reconciliation/fx-tolerance` · 원가흐름 기본값 `PUT /settlements/cost-flow-config` · 계정별 원가흐름 `PUT` · `DELETE /settlements/cost-flow-config/accounts/{code}` (13) |

(가)/(나) 구분은 **구현자의 분류**다 — (나) = «다른 쓰기의 결과를 바꾸는 설정 · 기간 상태», (가) = «건별 업무 행위». 원본 경로는 `ledger …/presentation/controller/*Controller.java` 7개.

## 3. 호출자 전수

- **서버 쪽 교차 도메인 호출자 = 0**: `/api/finance` 를 부르는 코드는 finance 밖에서 `platform-console` · 데모 시드 · e2e 뿐(`projects/*/apps/*/src/main` grep). Finance 가 다른 도메인을 부르는 것도 0(finance HTTP 클라이언트 = 외부 환율 피드 `HttpFxRateProviderAdapter` · `RealFxRateProviderAdapter` 뿐).
- **도메인 안 운영자 토큰 호출 = 0**: account → ledger 는 Kafka(`finance.transaction.completed.v1`) 뿐 — 792 Q2 / 793 Q3 같은 «좁은 교차 허용» 이 **필요한 자리가 없다**.
- **개인/본인 엔드포인트 = 0**: 793 Q2 같은 «나에 관한 것» 경로가 finance 에는 없다(계좌 소유자 = `ownerRef` 문자열, 토큰 `sub` 와 묶이지 않음).
- **콘솔**(같은 운영자 토큰): finance-ops 화면 = 계좌 조회 3 · ledger-ops 화면 = 원장 조회 + **쓰기 2**(대사 해소 `ledger-reconciliation-api.ts:63-82` · 환율 갱신 `ledger-fx-rates-api.ts:52`) · 운영 개요 Finance 카드 · Finance 개요. 콘솔은 계좌 쓰기를 부르지 않는다(iam-integration.md § platform-console Operator Read Consumer). ⇒ 통제 쓰기 7(나)의 콘솔 호출자 = **0**.
- **데모 시드** `infra/demo/seed/seed-finance.sh` — 운영자 assume(`demo-corp`)으로 개설 · KYC · 충전 · 이체 + 시산표 조회. 그 운영자는 `legacy-compat`(`R__seed_zz_demo_domain_permission_sets.sql:36-52`) ⇒ V0058 이전으로 따라온다(계좌 변경 역할 필요).
- **e2e(역할 없는 / `'*'` 토큰 — 792 교훈)**: federation `finance-golden-path.spec.ts` = SUPER_ADMIN `'*'` 토큰으로 `/finance` 화면만(와일드카드 READ 유지 ⇒ 무영향 — 추론). `tenant-switch-rescope.spec.ts` = multi-operator assume(acme-corp) · seed.sql `:725-730` legacy-compat ⇒ 이전으로 따라온다. 🔴 `entitlement-trust-crossdomain.spec.ts` = acme 운영자가 쿠키(`console_active_tenant=acme-corp`)만 미리 놓고 들어온다 — Finance 카드 **NOT forbidden** 을 단언. PC-FE-338 뒤 `getDomainFacingToken`(`session.ts:310-321`)은 선택 테넌트가 있으면 기본 토큰으로 떨어지지 않으므로(재-assume) assume 토큰 + legacy-compat 경로로 **통과할 것**으로 추론 — ⚪ 브랜치 실행으로 확인 예정(통과하지 못하면 이 스펙이 `FINANCE_VIEWER` 에 기대고 있었다는 뜻). console e2e(`tests/e2e/*.spec.ts`)에 Finance 호출 스펙 0.
- **이전할 세트**: `FINANCE_OPERATOR` 를 담은 세트 = 정적으로 `finance-full`(`V0052:155`) · `legacy-compat` · `all-domains-full` + ⚪ 관리자가 만든 테넌트 세트 · 파트너십 `delegated_scope`/`participant_scope`. 라이브 행 수 ⚪ → 672 다음 창. Flyway 다음 빈 번호 = **`V0058`**(`origin/main` `baff0fd45` — `V0057` 이 마지막, 2026-10-16 00:13 UTC).
- 시험 영향(구현 때): finance 시험 10 파일이 일반 역할 · `FINANCE_VIEWER` · `FINANCE_OPERATOR` 를 36 곳에서 쓴다(account `ActorContextTest` · 변환기 시험 · `ScopeEnforcementHttpIntegrationTest` · ledger 변환기 · `LedgerScopeEnforcementHttpIntegrationTest` · gateway 시험 3 — gateway 는 `roleOrScope` 존재 판정이라 무변경 예상).

## 4. 상시 결정의 유추 적용 (질문 아님 — 792 · 793 그대로)

- 역할 = 영역별 READ/WRITE · GET = READ ∨ WRITE · 그 밖 = WRITE. 진입 역할 `FINANCE_OPERATOR` = auth `ENTRY_ROLES` 한 행으로 파생 · 카탈로그에서 제거 · 배정 · 위임된 것은 버림 · 진입 역할 위임 → 422.
- **`FINANCE_VIEWER` 합성 제거**(793 Q1) · `ROLE_FINANCE_SUPERADMIN_READ`(`'*'` GET) 유지 · 워크로드 범위(`finance.read/write`)는 계속 통한다(필터 체인의 범위 갈래 유지 — SCM 과 달리 403 이 되지 않는다).
- **일반 `OPERATOR/ADMIN/SUPER_ADMIN` 제거**(§ 0 — 발급처 0, Failure 1). 판정은 Finance 업무 역할 · 범위 · `'*'` GET 만.
- V0058: `FINANCE_OPERATOR` 를 담은 **모든** 세트 · 파트너십 scope 에 새 업무 역할 전부를 넣은 뒤 `FINANCE_OPERATOR` 삭제(넓힌 뒤 지움) · 형태 시험 + IT 로 고정.
- 테넌트 미선택 운영자(기본 토큰 — 역할 0)의 Finance 조회 403 = **수용**(793 Q1 과 같은 결과).
- 콘솔(`permission-map.ts` Finance `serverRequires/writeRequires` · 계약 § 2.4.3.4 표 · 운영 개요 Finance 카드) = **새 ready 콘솔 티켓**, «794 를 실은 굽기 라이브 확인 뒤에만 머지».
- `SCOPE_finance.write` 가드: 구현자 추천 = auth `OPERATOR_EXCLUDED_SCOPES` 에 `finance.write` 를 더하고(지금은 효과 0 — 등록이 바뀌어도 운영자 토큰엔 안 실린다) + 그것을 단언하는 시험 + iam-integration.md 문장. (명세 문장만으로 그칠지는 이의가 있으면 Q 로.)

## 5. 질문과 추천 (소유자)

| Q | 질문 | 추천 (구현자 선호) | 대안 |
|---|---|---|---|
| **Q1** 🔴 (티켓이 요구한 결정 · ADR-083 § 도메인 업무 구분 제안 Finance 행 · D2) | 원장 **통제 쓰기 7**(§ 2 (나) — 기간 개시 · **마감** · **환율 덮어쓰기** · 환차 허용오차 · 원가흐름 설정 3)을 어디에 두나 | **(나) 별도 업무 역할 `FINANCE_LEDGER_CONTROL`** — V0058 이 `FINANCE_OPERATOR` 를 담은 세트 · 파트너십에 **함께** 넣는다 ⇒ 기존 운영자 API 집합 무변경(AC-2). 프리셋 `finance-ledger-operate` 에는 **넣지 않고** `finance-full` · `all-domains-full` · `legacy-compat` 에만. 🔵 D2 의 «지금 발급 안 하는 등급의 자동 부여 금지» 에 걸리지 않는다는 해석 — 이 7 경로는 **오늘 `FINANCE_OPERATOR` 로 발급 · 행사되고 있다**(조용한 새 부여가 아니라 같은 권한의 이름 분리). 이름에 `ADMIN` 을 쓰지 않는다(D2 의 관리 등급과 구별). 콘솔 호출자 0 ⇒ 화면 영향 없음 | (가) 13 전부 `FINANCE_LEDGER_WRITE`(OD-6 글자 그대로 · 역할 4) — 단순하나 «일상 분개 담당자» 에게 기간 마감 · 환율 덮어쓰기가 같이 간다(Failure 2 의 모양) · (다) 통제 7 을 **아무 업무 역할에도** 두지 않음(워크로드 범위만) — 운영자의 기간 마감 API 경로가 닫힌다(기능 퇴행 · 콘솔 호출자 0 이라 화면 퇴행은 없음) · (나′) (나)와 같되 수동 분개 · 재평가 · 정산까지 통제로 |
| **Q2** | 역할 이름 · 프리셋 | 역할 `FINANCE_ACCOUNT_READ` · `FINANCE_ACCOUNT_WRITE` · `FINANCE_LEDGER_READ` · `FINANCE_LEDGER_WRITE` (+ Q1 (나)면 `FINANCE_LEDGER_CONTROL` = 5). 판정: account READ = AC_READ ∨ AC_WRITE · WRITE = AC_WRITE · ledger READ = LG_READ ∨ LG_WRITE ∨ LG_CONTROL · 업무 쓰기 6 = LG_WRITE · 통제 쓰기 7 = LG_CONTROL(쓰기와 **독립** — CONTROL 이 LG_WRITE 를 함의하지 않는다). 프리셋: `finance-read`(조회 둘) · `finance-account-operate` · `finance-ledger-operate` · `finance-full`(전부) · `all-domains-full` · `legacy-compat` 확장 | CONTROL 이 LG_WRITE 를 함의(CONTROL 단독으로 업무 쓰기도 연다) |
| **Q3** | 계좌 **KYC 승급 · 충전**(운영자 전용 앱 단 검사)의 등급 | **`FINANCE_ACCOUNT_WRITE` 그대로** — 앱 단 검사는 `isOperator()`(4 문자열) → «`FINANCE_ACCOUNT_WRITE` 역할 보유» 로(범위만 가진 워크로드는 지금처럼 403). 데모 시드의 KYC · 충전 경로가 그대로 산다. 감사 `ActorType` = 파생 `FINANCE_OPERATOR` 보유 여부(지금과 같은 뜻) | 충전(돈의 유입) · KYC 를 Q1 처럼 통제 역할(`FINANCE_ACCOUNT_CONTROL`)로 분리 |
| 순서 | 콘솔 데이터 | 별도 콘솔 PR, 굽기 라이브 뒤(792 · 793 과 같음) | — |
