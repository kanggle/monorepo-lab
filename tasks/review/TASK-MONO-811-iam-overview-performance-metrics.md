# Task ID

TASK-MONO-811

# Title

`ADR-MONO-084` P6 — IAM 실적 지표: 로그인 성공 · 실패 · 계정 잠금 · 신규 계정(발생 시각 귀속) · 콘솔 개요 실적 줄

# Status

review

# Owner

monorepo

# Task Tags

- code
- api
- iam
- security
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (지표 원천이 서비스 둘 이상에 흩어져 있다 — 로그인 이력은 security-service, 계정 · 잠금은 account/admin)
> 🔴 **cross-project 원자 PR**: iam 계약 · 엔드포인트와 콘솔 화면을 한 PR 에.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807`~`810` · `812`.

# Goal

IAM 개요(`/iam`)는 운영자 · 계정 · 감사 건수만 보여 준다. 맨 위에 이번 주 · 이번 달 **로그인 성공 · 실패 · 계정 잠금 · 신규 계정**과 직전 대비 · 일별 추이를 붙인다.

# Scope

## In Scope

- 계약 먼저: 지표가 사는 서비스의 계약에 `…/metrics?period=week|month` (P1 공용 모양). 원천: 로그인 이력 = `security-service`(`V0001__create_login_history.sql`) · 계정 생성 · 잠금 = account/admin. **콘솔이 부를 입구 하나**(admin-service 가 모으나, 서비스마다 하나씩 부르나)를 AC-0 에서 정한다 — 둘째면 콘솔 쪽 fan-out 이 늘어난다.
- 집계 · 인가: 운영자 조회 역할(기존 감사 조회와 같은 등급). 🔴 로그인 이력은 개인 정보다 — 집계 숫자만 내려주고 행은 내려주지 않는다.
- 콘솔: `features/iam-overview` 에 P1 «실적 줄».

## Out of Scope

- 의심 이벤트 · 위험 점수 지표.
- 운영자(콘솔 사용자) 활동 지표.

# Acceptance Criteria

- [x] **AC-0** — 재측정: `login_history` 의 컬럼(결과 · 시각 · 테넌트) · 계정 생성 · 잠금 시각이 어디 저장되나(잠금 해제 후 재잠금 셈법) · 서비스 간 입구 결정 · 테넌트 범위(운영자의 테넌트에 속한 사용자만) · 개인정보 규칙(`specs/features/data-rights.md`). 결과 → 이 파일 + 계약 문구.
  - → **닫힘 2026-10-24 UTC** (origin/main `c50dd3d67`, 경로는 `projects/iam-platform/apps/` 기준).
  - **로그인:**
    - `security-service` 는 MySQL 8 이다(`application.yml:8`, `serverTimezone=UTC`).
    - `login_history` 는 `V0001` + `V0008` 이고, `tenant_id` · `outcome` VARCHAR · `occurred_at` DATETIME(6) UTC 다. append-only 트리거가 걸려 있다(`V0002`).
    - `LoginOutcome` = `ATTEMPTED, SUCCESS, FAILURE, RATE_LIMITED, TOKEN_REUSE, REFRESH`. 로그인 한 번은 `ATTEMPTED` 행과 `SUCCESS`/`FAILURE` 행을 함께 남긴다. 그래서 `SUCCESS` · `FAILURE` 만 센다.
    - `(tenant_id, outcome, occurred_at)` 인덱스가 없다 → 이 티켓이 추가한다.
  - **잠금:**
    - `account_lock_history`(`V0007` + `V0008`)는 이벤트당 한 행이다. 잠금 → 해제 → 재잠금 = 2행이다.
    - `account.locked` 이벤트로 채워진다.
    - 사이트 단위 잠금(`consumer_site_memberships.status=LOCKED`)은 이력 없이 덮어쓰기로 저장되고, 이벤트를 낸다는 근거가 없다.
  - **신규 계정:**
    - `accounts.created_at` · `accounts.tenant_id`.
    - 소비자 풀 계정은 `tenant_id='consumer-pool'`(`V0029`)이고, 사이트 가입은 `consumer_site_memberships`(`V0030` · `V0032` · `V0033`)에 있다.
  - 🔴 **풀 계정의 귀속:**
    - 폼 · 소셜 로그인 이벤트는 `credential.getTenantId()` 를 싣는다(auth-service `CredentialAuthenticationProvider.java:401`). 풀 계정이면 그 값은 `consumer-pool` 이다.
    - 스펙이 서로 달랐다: `auth-events.md:50` 은 계정의 테넌트, `:158` 은 «발급된 토큰의 tenant_id». 코드는 `:50` 쪽이다. → `:158` 을 코드에 맞춘다.
    - 데모 소비자는 전원 풀 계정이다(`R__05` · `R__06`, `TASK-MONO-744`).
  - **입구:**
    - admin-service 에는 지표 엔드포인트가 없다.
    - security 내부 API(`/internal/security/**`, `internal.invoke`)는 테넌트 범위가 없다(`SecurityQueryService` 의 `DEFAULT_TENANT_ID`).
    - 감사 조회는 `@RequiresPermission(audit.read)` + `QueryTenantScopeGate` 다. 콘솔은 활성 테넌트를 `tenantId` 쿼리 인자로 보낸다(`iam-audit-read.ts:116`).
  - **개인정보:** `data-rights.md` 에 집계에 관한 규칙은 없다. 익명화는 UPDATE 마스킹이라 행 수가 그대로다 → 과거 집계가 줄지 않는다.
  - **소유자 결정 (2026-10-24 UTC, 선택창):**
    - ⓐ 입구 = **admin-service 하나**(`GET /api/admin/metrics`, `audit.read`, 감사 조회와 같은 테넌트 게이트).
    - ⓑ 풀 계정 = **지금 기록대로 센다** — 로그인 · 잠금은 사이트에서 빠지고, 계약과 화면에 그렇다고 적는다. `newAccount` 는 사이트 가입을 센다. 사이트 귀속은 후속 `TASK-BE-653`(iam, 별도 spec PR).
    - ⓒ `loginFailure` = 시도 수(티켓 기본값).
- [x] **AC-1** — 🔴 대조군: 다른 테넌트의 로그인 · 계정은 안 잡힌다. 실패 로그인 하나 → `loginFailure` 만 움직인다.
  - → `SecurityMetricsQueryServiceTest` · `AccountMetricsQueryServiceTest` · `AdminMetricsUseCaseTest` 가 단언하는 것:
    - 다른 테넌트의 행은 0.
    - `FAILURE` 1행은 `loginFailure` 만 올린다.
    - `ATTEMPTED` 행은 아무 키도 안 올린다.
    - `consumer-pool` 로그인은 사이트에서 0.
    - 풀 계정의 사이트 가입은 그 사이트의 `newAccount` 를 올린다.
  - 실제 MySQL 판정은 `@Tag("integration")` IT 셋이 CI 에서 한다(`SecurityMetricsQueryIntegrationTest` · `AccountMetricsQueryIntegrationTest` · `AdminMetricsIntegrationTest`).
- [x] **AC-2** — 응답에 사용자 식별자 · 이메일 · IP 가 없다(필드 전수 단언).
  - → `AdminMetricsControllerSliceTest` 가 응답 JSON 필드를 전수로 본다: 공용 모양의 필드만 있고 `accountId` · `email` · `ip` 류는 0.
  - 내부 조회도 PII 열을 SELECT 하지 않는다: 로그인은 `outcome` · `occurredAt`, 잠금은 `occurredAt` 만.
- [x] **AC-3** — 경계 시험(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul`) · 인가 없는 토큰 403.
  - → 주입 `Clock` 으로 KST 자정 경계와 짧은 달 경계를 시험한다(형제 시험 구조 복사).
  - 세 서비스의 시험(`SecurityMetricsQueryServiceTest:120` · `AccountMetricsQueryServiceTest:105` · `AdminMetricsUseCaseTest:171`)은 `@ValueSource({"UTC", "Asia/Seoul"})` 로 JVM 기본 시간대를 바꿔 같은 단언을 두 번 돈다.
  - 인가 시험:
    - `audit.read` 없음 → 403 `PERMISSION_DENIED`.
    - 범위 밖 테넌트 · grant 없는 `*` → 403 `TENANT_SCOPE_DENIED`.
    - security 내부 엔드포인트, `internal.invoke` 없음 → 403.
    - 🔵 account 내부 엔드포인트, `internal.invoke` 없음 → **401**. 이 서비스의 기존 `/internal/**` 계약(`TASK-BE-319b`)을 따랐다.
- [x] **AC-4** — 콘솔: 실적 줄 · 칸 실패 · 401 · 샘플 모드 응답.
  - → `IamOverviewScreen` 맨 위에 `PerformanceStrip`(testid `iam-performance`)과 범위 주석(`iam-performance-scope`)이 있다.
  - 칸 실패는 «불러올 수 없음»(0 아님), 403 은 «권한 없음»이고 나머지 개요는 그대로 렌더된다. 401 은 `/login?error=session_expired` 로 보낸다.
  - 샘플 표면 `iam_metrics` 의 수치는 기존 샘플 데이터에서 계산한다. 감사 카드와 숫자가 모순되지 않게 하려는 것이다.
  - 테스트: `iam-metrics-api.test.ts` · `sample-iam-metrics.test.ts` · `iam-overview-state.test.ts` · `IamOverviewScreen.test.tsx`.
- **구현 결정 (기록):**
  - 입구 = `GET /api/admin/metrics?period=&tenantId=`. 인가는 `audit.read` 이고, 테넌트 게이트는 `QueryTenantScopeGate`(새 `ActionCode.METRICS_QUERY`)다.
  - admin 이 security `GET /internal/security/metrics/daily` 와 account `GET /internal/account-metrics/daily` 를 현재 · 직전 창마다 부른다. 하위가 하나라도 실패하면 503 `DOWNSTREAM_ERROR` 다(부분 응답 없음).
  - 🔵 **성공한 조회는 meta-audit 행을 남기지 않는다.** 거절만 DENIED 로 남는다. 응답에 조회 대상이 없고, 개요를 열 때마다 행이 쌓이기 때문이다.
  - 🔵 **`newAccount` 와 재가입:** 스스로 떠났다가 다시 동의하면 `consented_at` 이 덮어써진다. 그래서 그 (계정, 사이트)는 마지막 동의 날에 한 번만 세고, 원래 날의 값은 1 줄어든다(계약 표에 적음).
  - 🔴 **기존 공백 (이 티켓에서 고치지 않았다):** `security-query-api.md` 는 내부 조회를 모두 감사한다(A5)고 적었지만, security-service 코드에는 그 감사가 없다 — 기존 두 엔드포인트 포함. 새 엔드포인트는 계약에 면제 사유를 적었다.
  - 인덱스: security `V0012__metrics_indexes.sql` · account `V0034__metrics_indexes.sql`. main 과 겹치는 번호는 없다.
- **검증 (로컬):**
  - `:security-service:test` 266 · `:account-service:test` 789 · `:admin-service:test` 1420 → 모두 rc=0, 실패 0.
  - console-web: `tsc --noEmit` rc=0 · `pnpm lint` rc=0 · 대상 테스트 8파일 171개 통과.
  - console 전체 스위트는 5,422개 중 1개 실패: `WmsRefPicker` debounce. 이 PR 이 건드리지 않은 wms-ops 테스트이고, 단독 재실행에서 실패 1 · 통과 2, 깨끗한 main 에서는 2/2 통과했다. 원인은 증명하지 않았다 → CI 가 판정한다.
  - ⚪ Docker 가 없어 IT 셋은 로컬에서 돌지 않았다.
- [ ] **AC-5** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5
- `projects/iam-platform/specs/services/security-service/architecture.md` · `admin-service/architecture.md`
- `projects/iam-platform/specs/features/data-rights.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/security-query-api.md` · `admin-api.md` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 같은 사람이 같은 날 열 번 실패 → `loginFailure` 는 시도 수인가 사람 수인가 → AC-0 에서 정하고 계약에 적는다(기본: 시도 수).
- 소비자 풀 계정(ADR-MONO-078)은 어느 테넌트로 세나 → AC-0.

# Failure Scenarios

- 집계 API 가 행을 내려주고 콘솔이 센다 → 개인 정보가 콘솔로 나간다(AC-2 가 막는다).
- 서비스마다 경계 계산을 따로 한다 → P1 `KstPeriodBounds` 가 아닌 곳이 생긴다.
