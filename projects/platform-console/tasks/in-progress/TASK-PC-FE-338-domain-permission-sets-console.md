# Task ID

TASK-PC-FE-338

# Title

`ADR-MONO-083` P4 — 콘솔: «IAM 관리 역할» / «업무 권한 세트» 화면 분리 · 세트 · 운영자 · 그룹 배정 화면 · 메뉴 · 버튼의 두 신호(구독 없음 / 업무 권한 없음)

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(화면) — 오류 매핑 · 권한 신호 판정만 Opus 검토
> 🔴 메뉴 숨김은 **편의**다. 이 단계의 시험은 «숨겼다» 가 아니라 «숨긴 것과 서버 403 이 일치한다» 를 본다. 샘플 화면만 보고 인가가 검증됐다고 보고하지 않는다.

# Dependency Markers

- **선행**: `TASK-MONO-791`(P1 계약) · `TASK-BE-631`(P2 API — `main` 에 있어야 화면이 붙는다). 업무 권한으로 메뉴를 좁히는 부분은 `TASK-BE-632`(P3) 배포 뒤에야 실제 권한과 일치한다 — P3 전에는 «업무 권한 없음» 신호가 서버 판정과 어긋나지 않게 P2 의 실효 권한 API 만 읽는다.

# Goal

관리자가 테넌트별로 운영자 · 그룹에 업무 권한 세트를 배정 · 회수하고 출처와 합산 결과를 보게 한다. 운영자에게는 구독 없음과 권한 없음을 구분해 보이고, 조회 전용이면 변경 버튼을 숨긴다(최종 판정은 서버).

# Scope

## In Scope

- 지금 «권한 세트»(`/permission-sets` — 실은 `admin_roles`, `PermissionSetsScreen.tsx:1-20`) → «IAM 관리 역할» 로 이름 · 문구 변경(경로 유지 여부는 P1 계약 · e2e grep 결과로) · 새 «업무 권한 세트» 화면(서버 카탈로그에서 역할 선택 · 시스템 프리셋 읽기 전용 · 배정된 세트의 변경/삭제 규칙 표시 · 세분 미지원 도메인은 «전체 업무(세분 미지원)» 로만).
- 운영자 화면: 테넌트별 직접 배정 · 그룹에서 받은 세트와 출처 · 합산 실효 업무 권한 · **직접 배정만 회수 버튼**.
- 그룹 화면: 세트 여러 개 배정 · 회수 · 적용 대상(멤버)과 실제 부여 결과.
- 메뉴 · 화면 · 버튼: `console-nav-exposure.ts` 에 «업무 권한 없음» 신호 추가(실효 권한 API — 토큰 디코드 아님) · 구독 배지와 구분 · 조회 전용이면 변경 버튼 숨김 · 직접 URL 진입은 지금처럼 서버 403 → 화면 안 «권한 없음».
- 원장 사본 갱신: `permission-map.ts`(파생 줄 인용 `:24, 245` 제거) · `iam-guide/data.ts` · `shared/sample/fixtures/iam.ts`.
- 운영자 초대 흐름: 초대 시 세트 지정이 P1 계약에 들어가면 연결(없으면 범위 밖으로 기록).
- (OD-2) assume 토큰 TTL 단축 시 재교환 경로가 만료 전에 도는지 확인.

## Out of Scope

- 이번 변경과 무관한 레이아웃 · 문구 · 기능.
- 도메인 화면 내부의 세분 버튼(도메인 세분 단계 P5~P8 이 카탈로그를 넓힌 뒤 각 화면).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 메뉴 판정 입력 · `/permission-sets` 를 가리키는 e2e · 단위 시험 · 가이드 사본 전수 grep(`tests/e2e` · `e2e-smoke` · `*-nav.test.tsx`).
- [ ] **AC-1** — 재고 세트 운영자: WMS 재고 메뉴 열림 · 출고 메뉴 «권한 없음» · SCM 미구독 테넌트면 «구독 필요»(두 신호 구분) — 단위 시험 + 같은 조건의 서버 응답(403/200)과 대조한 기록.
- [ ] **AC-2** — 조회 세트: 변경 버튼 숨김 · 같은 API 직접 호출은 서버 403(P3 배포 뒤 라이브, 그 전엔 ⚪ 이유).
- [ ] **AC-3** — 운영자 화면: 그룹 출처 세트에는 회수 버튼 없음 · 직접 회수 뒤 그룹분 유지가 화면에 보임.
- [ ] **AC-4** — 화면 이름 분리 · 기존 IAM 관리 역할 동작 불변(회귀 시험).
- [ ] **AC-5** — 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인 · Vercel 배포 상태를 따로 적는다(`main` 직접 배포는 꺼져 있다 — `vercel.json`).

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (P1 갱신본) · `projects/iam-platform/specs/contracts/http/admin-api.md` (P1)

# Edge Cases

- 실효 권한 API 실패 — 메뉴를 **열어 두고**(편의 기능이므로 fail-open 표시) 서버가 판정한다. «권한 없음» 으로 잘못 닫지 않는다.
- 같은 운영자가 테넌트 전환 — 메뉴 신호가 새 테넌트 기준으로 다시 계산.

# Failure Scenarios

1. 메뉴 숨김을 토큰 디코드로 판정 — 서버 판정과 어긋난다(`(console)/layout.tsx` 는 디코드를 표시용으로만 쓴다).
2. «IAM 관리 역할» 과 «업무 권한 세트» 가 같은 화면 · 같은 이름으로 남는다 — 관리자가 관리 역할을 업무 권한으로 오해한다.

---

# Implementation Record

## Slice plan (2026-10-14 UTC)

| Slice | Content | AC |
|---|---|---|
| **a** | Name split («IAM 관리 역할») · new «업무 권한 세트» screen (`/domain-permission-sets`, contract rows 1–5) · ledger copies | AC-0 · AC-4 (part) |
| b | Operator detail «업무 권한» (rows 6–8) · group detail «업무 권한 세트» (rows 9–11) | AC-3 |
| c | Nav «업무 권한 없음» signal (row 12) · read-only set hides buttons · OD-2 re-exchange · nightly / Vercel | AC-1 · AC-2 · AC-5 |

## AC-0: re-measurement (2026-10-14 UTC)

- References to `/permission-sets` or «권한 세트»:
  - **e2e · smoke: 0** (searched `**/e2e/**`, `**/e2e-smoke/**`, `*.spec.ts`).
  - Unit tests: `sidebar-iam-group`, `PermissionSetsScreen`, `IamGuideScreen`, `GlobalGuideScreen`, `permission-map-drift`, `sample-fixtures-schema-iam`, `rbac-catalog-api`, `sidebar-role-subscription`.
  - Guide copies: `permission-map.ts`, `iam-guide/data.ts`, `domain-features.ts`, `sample/coverage.ts`.
- ⇒ **The route stays at `/permission-sets`** (no e2e moves). Only the name changes. The testids are kept.

## Slice a: what landed

- **Rename.** Every visible name and line of copy for the `/permission-sets` view now reads «IAM 관리 역할». One line was added: "this is an IAM management permission bundle, not domain business permissions → «업무 권한 세트»".
  - ⚪ Some comments still say «권한 세트». They describe the historical ticket name, TASK-PC-FE-228, not a visible label.
- **New screen** `/domain-permission-sets`, in the nav right after «IAM 관리 역할».
  - Presets carry the «시스템» badge, are read-only, and offer «복제해서 새 세트».
  - Tenant sets show `grantCount` / `groupCount`.
  - The role picker is grouped by domain. A `fineGrained=false` role shows its `label` verbatim.
  - An unsubscribed domain is disabled with «구독 없음». When subscription data is unknown, nothing is disabled; the server's 422 is final.
  - **Status honesty.** The screen always shows an «적용 예정» note. Edit and delete confirms carry the latency copy for before P3 (**at most 30 minutes**, never «즉시»).
- **BFF.**
  - `Idempotency-Key` is sent on create only.
  - A PATCH or DELETE body that contains a key is refused with 422 (`.strict()`).
  - 204 passes through as `NextResponse(null)`.
- **Ledgers.**
  - `permission-map` gets a new row, and `domain_access.manage` is added to the seed matrix (SUPER_ADMIN · TENANT_ADMIN, the same as V0052).
  - `iam-guide` adds the menu, the role, and the key description.
  - Sample coverage is `pending`: there is no fixture yet (the same precedent as `tenant_entry_policy`).
- **Review corrections (main session).** The subagent added an error message for `DOMAIN_ACCESS_MANAGE_REQUIRED`. **No producer has that code** (the producer answers `403 PERMISSION_DENIED`), so I removed it.

## Slice b — what landed (#4347 follow-up)

- **Operator «업무 권한»** — `/operators` has no detail page; it is a table with row action dialogs. Following the `OrgScopeDialog` precedent, a «업무 권한» button on each row opens `OperatorDomainAccessDialog`.
  - Per set, its sources are shown as badges: «직접» · «이전 호환» · «그룹: {name}» · «모든 테넌트» (`sources[].tenantId='*'`).
  - Effective roles are grouped by domain. Roles of a domain the tenant has not subscribed to are marked «구독 없음 — 토큰에 실리지 않음»; when subscription data is unknown, nothing is marked.
  - **The revoke button appears only on a set with a `DIRECT`/`MIGRATION` source.** A set with only group sources shows a link to the group instead.
  - The confirm copy says group copies stay, and gives the latency (at most 30 minutes).
  - A grant sends no `Idempotency-Key`. Self-grant has its own confirm copy (OD-4).
- **Group «업무 권한 세트»** — a third panel in `GroupDetail`.
  - Add sends `Idempotency-Key` and a reason, then shows «멤버 N명에게 배정했습니다» from `fannedOutRows`. Remove sends no key.
  - On a group that carries sets, both the panel and the member-add dialog show a `domain_access.manage` hint.
- **Architecture boundary.** A cross-feature import is forbidden (`layer-dependency-rules.test.ts`), so each feature owns its own view model. The set picker calls the existing BFF `/api/domain-permission-sets` over HTTP. The latency copy is promoted to `shared/api/domain-permission-sets-copy.ts`; the original path keeps a re-export.
- **Review correction (main session).** The group panel had no «적용 예정» note, so I added one along with a test.
- ⚪ «그룹: {name}» links to `/operator-groups` itself. There is no deep link to one group, because the group selection is held in client state.

## Slice c re-split (2026-10-14 UTC, main session judgement)

- **c-1 (now; must ship before P3 is deployed) — OD-2 assumed-token re-exchange.** I measured three defects:
  1. **`getDomainFacingToken()` fell back to the base token when the assumed-token cookie was missing** (`session.ts`). With a tenant selected, the base token carries the login tenant (`iam`), not the selected one. Today the assumed and base tokens are both 1800 s, so the two cookies lapse almost together. From P3 the assumed token is 300 s, so **for 25 minutes of every 30 every domain call would go out with the wrong token**.
  2. **The domain section gate (`DomainTenantGate`) showed «테넌트를 먼저 선택하세요» when the assumed token was missing.** The contract forbids this downgrade: that notice may appear only once the re-assume itself has been refused.
  3. **`GET /api/auth/refresh` decided «complete session ⇒ return without refreshing» from access + operator alone (`hasCompleteSession`).** Had the gate sent the browser through that hop, it would **loop forever**.
- **c-2 (after P3 is deployed) — the «업무 권한 없음» nav signal (AC-1) and hiding buttons for a read-only set (AC-2).** Before P3 the server still allows everything the subscription covers. Turning these on now would make the screen say "no permission" while the server answers 200, which breaks AC-1's "match the server's verdict". AC-5 (nightly / Vercel) is recorded at the end of c-2.

## Slice c-1 — what landed

- `getDomainFacingToken()`: **fail-closed**. With a tenant selected and no assumed token it returns `null`. Every caller already maps `null` to 401, and the browser client then does one refresh (which re-assumes) and a retry. With no tenant selected it uses the base token as before (net-zero).
- The assumed-token cookie expires **60 s before the token** (`assumedTokenCookieMaxAge`: 300 → 240, 1800 → 1740). All three set sites use it: switch, refresh, and the default-tenant path. Its absence is the re-assume trigger, so the console re-assumes before a domain server could ever see an expired token.
- `hasCompleteSession`: with a tenant selected, the session is complete only when the assumed token is present. If the re-assume is refused, the refresh drops the tenant, so the hop always converges.
- `DomainTenantGate`: a selected tenant with no assumed token and a refresh cookie present goes through the refresh hop, back to the same page.
- Tests:
  - `assumed-token-lapse.test.ts` (new) and the OD-2 block in `domain-tenant-gate.test.tsx` (new).
  - **Five existing fixture files (notification-routes, dashboard-composition-routes, per-domain-credential, sample-mode-cores, sample-mode-proxies) modelled a «tenant cookie only, no assumed token» state that production never produces**: they relied on the fallback. They now carry the assumed token, and domain Bearer assertions expect the assumed token, which is what is actually sent.
  - In two more files (tenant-switch, active-tenant-default), the `maxAge` assertion changed from 1800 to 1740.

## Merge record

| Slice | PR | Merge commit | Failing checks at merge |
|---|---|---|---|
| a | #4347 | `56a15be8b` | 0 |
| b | #4348 | `27cdcebd2` | 0 |
| c-1 | #4349 | `808fa9e84` | 0 |

- **Vercel production — measured, not assumed (GitHub Deployments, `Production – kanggle-console`, 2026-10-14 UTC):**
  - a `56a15be8b` → `success` (00:21Z).
  - b `27cdcebd2` → `success` (01:30Z).
  - c-1 `808fa9e84` → the `Vercel deploy hooks` workflow is `success`, but **no deployment record existed at the time of writing**. Check the record again before deploying P3.
  - 🔴 The ticket body (AC-5) says «`main` direct deploy is off (`vercel.json`)». In practice, **each console merge is reaching Production through the deploy-hook workflow**. Measure by the deployment record, not by that sentence.
- c-1 has been recorded as a **deployment prerequisite** in `TASK-BE-632` (P3) Dependency Markers.
- **Still open: c-2.** It covers AC-1, AC-2 and AC-5, and waits for P3 to deploy. This ticket stays in `in-progress`.
