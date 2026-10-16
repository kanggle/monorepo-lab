# Task ID

TASK-PC-FE-338

# Title

`ADR-MONO-083` P4 — 콘솔: «IAM 관리 역할» / «업무 권한 세트» 화면 분리 · 세트 · 운영자 · 그룹 배정 화면 · 메뉴 · 버튼의 두 신호(구독 없음 / 업무 권한 없음)

# Status

done

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

- [x] **AC-0** — 재측정: 메뉴 판정 입력 · `/permission-sets` 를 가리키는 e2e · 단위 시험 · 가이드 사본 전수 grep(`tests/e2e` · `e2e-smoke` · `*-nav.test.tsx`). → § AC-0: re-measurement (2026-10-14) + § Slice c-2 — AC-0 re-measurement (2026-10-15).
- [x] **AC-1** — 재고 세트 운영자: WMS 재고 메뉴 열림 · 출고 메뉴 «권한 없음» · SCM 미구독 테넌트면 «구독 필요»(두 신호 구분) — 단위 시험 + 같은 조건의 서버 응답(403/200)과 대조한 기록. → 단위 시험 § Slice c-2 PR 2 · 라이브 대조 § 28차 창 라이브(2026-10-16 UTC).
- [x] **AC-2** — 조회 세트: 변경 버튼 숨김 · 같은 API 직접 호출은 서버 403(P3 배포 뒤 라이브, 그 전엔 ⚪ 이유). → 단위 시험 § Slice c-2 PR 2 · 라이브 § 28차 창 라이브.
- [x] **AC-3** — 운영자 화면: 그룹 출처 세트에는 회수 버튼 없음 · 직접 회수 뒤 그룹분 유지가 화면에 보임. → § Slice b · `OperatorsScreen-domain-access.test.tsx` («GROUP-only set has NO revoke button» · «DIRECT+GROUP set shows the revoke button, and its confirm says the group copy stays»).
- [x] **AC-4** — 화면 이름 분리 · 기존 IAM 관리 역할 동작 불변(회귀 시험). → § Slice a · `PermissionSetsScreen.test.tsx`(기존 동작 칸 무변경 초록).
- [x] **AC-5** — 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인 · Vercel 배포 상태를 따로 적는다(`main` 직접 배포는 꺼져 있다 — `vercel.json`). → § AC-5 기록 (2026-10-15 UTC).

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

## CORRECTION — c-1 re-assumed through the wrong path (2026-10-14 UTC)

- **What happened.** The federation e2e (run 38549120539, `d2d858cf7`) went red. Most specs landed on `/login?error=session_expired`. The cause was **c-1, not P3**. The harness put an active-tenant cookie on its own, without an assumed token. c-1 made that state fail-closed and then sent it through the **full IAM refresh hop**. Two things broke:
  - Screens with no section gate (overview, domain health) went to 401 → «세션 만료».
  - The rotation-race retry ended in `session_expired`, because my change to `hasCompleteSession` counted the state as incomplete.
- **Production impact.** In production the switch path always sets the assumed token together with the tenant. The one path that sets only the tenant is right after onboarding. Its impact was limited: it re-assumed through the refresh hop. **But the wrong half was that it did not follow the contract wording** (§ 2.7: «re-assume from the current base token»).
- **Fix #4354 → `ca176d570`.**
  - Added `GET /api/tenant/reassume`: re-assumes from the base token. Success sets the assumed cookie; a refusal or outage clears the tenant (no loop); a missing base token goes to the refresh hop.
  - The `(console)` layout and the domain section gate send that state through the re-assume hop.
  - `hasCompleteSession` is restored (access + operator).
- **Verification.** Before merging, I **ran the federation e2e on the branch itself** (run 38555155825) → **success**. Locally: tsc, lint, and the 63 tests in the affected suites.
- Vercel Production: `3eb6cbebd` (later than and including `ca176d570`) has a deployment record (2026-10-14 11:31Z). Status `success` is to be rechecked before the window.

## Slice c-2 — AC-0 re-measurement (2026-10-15 UTC, base `5dd4aeb8e`) — 🔴 STOPPED before implementation (HARDSTOP-06)

P3 is live (`TASK-BE-632` done, 27th window). c-2's precondition is met. I re-measured the inputs before writing any test. The nav signal rule cannot be implemented as written, because it disagrees with this ticket's AC-1.

**What the server actually decides, per console menu (WMS-subscribed tenant, after P3):**

| Menu | Call the screen makes | Server gate | Operator with no WMS set | Operator with `wms-inventory-operate` only |
|---|---|---|---|---|
| `/wms` · `/wms/inventory` · `/wms/inbound` · `/wms/master` | wms admin-service `/dashboard/*` (`wms-ops/api/*`) | `WMS_VIEWER`, **synthesised from `entitled_domains ∋ wms`** (`wms admin-service SecurityConfig.java:110-141`; ADR-083 ③ records it) | **200** | **200** |
| `/wms/outbound` | outbound-service `GET /orders` (`outbound-order-api.ts:32-44`) | `OUTBOUND_READ ∨ WRITE ∨ ADMIN` (`outbound-service SecurityConfig.java:97`) | 403 | **403** (measured live in the 27th window, BE-632 AC-1) |
| `/wms/operations` | admin-service `/operations/*` · `/settings` | `WMS_ADMIN` (not in the catalog) | 403 | 403 (same for every operator — not a set question) |
| SCM read screens | scm services | `authenticated()` only (ADR-083 ③); PO list GET 200 as BUYER (BE-632 AC-1 note) | **200** | (not subscribed in AC-1's case) |
| ERP / Finance read screens | erp · finance | read = `entitled_domains ∋ erp` / `FINANCE_VIEWER` synthesised (ADR-083 ③) | **200** | — |
| E-commerce | gateway `roles ∋ ECOMMERCE_OPERATOR` | the gateway is the only gate | 403 | — |

**The two specs that disagree:**

1. Contract § 2.4.3.4 «Domain nav — two signals» (`console-integration-contract.md:280`) keys the badge on the **domain**: «no role of that `domainKey` in the response → «업무 권한 없음»».
   - Inventory-set operator: it has a `wms` role, so **no WMS menu gets the badge**. The server answers `/wms/outbound` with 403.
   - Operator with no WMS set: the whole WMS node gets the badge, but `/wms/inventory` · `/wms/inbound` · `/wms/master` answer **200**. The same holds for SCM · ERP · Finance read screens.
2. AC-1 (and the header line «숨긴 것과 서버 403 이 일치한다») asks for **per-menu** signals: inventory menu open, outbound menu «권한 없음», matched against the server's 403/200.

⇒ The contract rule fails AC-1 in **both** directions: it hides nothing that the server refuses (outbound), and it badges screens the server serves (read dashboards). Neither layer can be chosen mechanically. Contract (layer 6) beats task (layer 10), but implementing the contract knowingly ships a signal that contradicts the server, which is the exact Failure Scenario this ticket exists to prevent.

**Second, smaller conflict — latency copy.**
- Contract § 2.4.3.4 (`:269`) still says «만료(최대 5분)».
- The canonical IAM sentence was corrected on 2026-10-15 by owner decision (BE-632 Close 2) to «`exp` + 60 s — 최대 약 360초» (`admin-api.md:3746` · `auth-api.md` § 회수 지연 · `rbac.md:516`).
- The console copy today is still the pre-P3 «최대 30분» (`shared/api/domain-permission-sets-copy.ts:22`).
- ⇒ The console contract has to be aligned first; the wording on screen is a copy choice.

**Other AC-0 inputs (measured):**
- Nav decision inputs: `console-nav-exposure.ts` (`isHrefHiddenFor` = admin roles · `subscriptionBadgeKeys` = registry), fed by `(console)/layout.tsx:257-302`. No consumer of `GET /api/admin/me/domain-access` exists in console-web yet (0 hits in `src/`).
- `permission-map.ts` WMS rows all carry the same `roles: WMS_ROLES`. There is no per-menu required-role data today.
- e2e: `tests/e2e/overview-consolidation.spec.ts:82-87` asserts the ERP drill text `/^ERP(구독 필요)?$/`. A second badge text inside that toggle would break it. `e2e-smoke/sample-visitor-wms.spec.ts` visits WMS routes (sample shell — no operator session, so no signal). `tests/federation-hardening-e2e/`: 0 hits for the badge texts.
- «적용 예정» copies to retire now that P3 is live: `DomainPermissionSetsScreen.tsx:154` · `OperatorDomainAccessDialog.tsx:176` · `GroupDetail.tsx:231` · `iam-guide/data.ts:235` · `permission-map.ts:440`. «최대 30분»: `domain-permission-sets-copy.ts:22`. Pinned by `DomainPermissionSetsScreen.test.tsx:92-95, 246, 284` and `GroupDetail-domain-sets.test.tsx:237-240`.
- Read-only button hiding (AC-2): the WMS screens with mutation buttons are `/wms` (alert acknowledge — `WMS_OPERATOR`) and `/wms/outbound` (pick · pack · ship · cancel · retry — `OUTBOUND_WRITE`). The contract rule (`:270`, «only `access=READ` entries for that domain → hide») is implementable as written. It is not the blocked part, but it needs the same row-12 read that the nav signal needs.

**Decision needed (owner) before c-2 continues:** see the report of this session. No code changed in this slice.

## Slice c-2 — owner decisions (2026-10-15 UTC)

1. **The nav signal is per menu.** Each nav leaf declares the role(s) the server requires for that screen's primary read. The signal shows when the assumed token's `roles` lack them.
   - A leaf the server opens on subscription alone carries no requirement and therefore no signal. As measured above, that covers WMS inventory · inbound · master and the SCM · ERP · Finance reads.
   - «구독 필요» stays a separate signal.
   - The data must scale to `TASK-MONO-792`~`795`: when those add fine-grained roles to another domain, that is just more per-leaf data.
   - Contract § 2.4.3.4 (`console-integration-contract.md:280`) is amended first, then AC-1's wording if needed. Watch the e2e pin `overview-consolidation.spec.ts:82-87`.
2. **Latency copy = «최대 약 6분».** Align contract `:269` and `shared/api/domain-permission-sets-copy.ts:22`. The bound is TTL 300 s plus the resource servers' 60 s skew (BE-632 AC-6 · `auth-api.md` § 회수 지연).
3. **Split into two PRs.**
   - **PR 1 (first):** retire «적용 예정» at all its sites (three screens, the guide, `permission-map`, their tests) and change the latency copy and the contract.
   - **PR 2 (after PR 1 merges, fresh branch from `origin/main`):** the per-menu nav signal and AC-2 button hiding. They share one input. Which input (`/me/domain-access` or the token's `roles`) is decided by the contract that PR 2 amends.

## Slice c-2 PR 1 — red (2026-10-15 UTC)

- Tests only, written before the fix:
  - `DomainPermissionSetsScreen.test.tsx`: effect note, edit/delete confirms «최대 약 6분».
  - `GroupDetail-domain-sets.test.tsx`: effect note.
  - `OperatorsScreen-domain-access.test.tsx`: latency copy and effect note.
  - `domain-permission-sets-post-p3-copy.test.ts` (new): the shared copy, the IAM guide menu and the permission-map row.
- Local run of those four files: **9 failed / 22 passed**. Every failure is an assertion: `AssertionError`, or `TestingLibraryElementError` for the new testids that do not exist yet. `tsc --noEmit -p .` rc=0.

## Slice c-2 PR 1 — what landed

- **Contract first** (`console-integration-contract.md` § 2.4.3.4):
  - «Status honesty» now records that P3 is deployed, that the «적용 예정» note is retired, and the wording of the effect note that replaces it.
  - «Revocation-latency copy» now says «만료(최대 약 6분)», defined as TTL 300 s + 60 s skew, with a link to the canonical IAM sentence. The earlier «최대 5분» is marked superseded.
- **Copy** (`shared/api/domain-permission-sets-copy.ts`):
  - `DOMAIN_SET_LATENCY_COPY` now says «최대 약 6분».
  - New `DOMAIN_SET_EFFECT_COPY`: «운영자가 테넌트를 고르면, 배정된 세트의 역할 중 그 테넌트가 구독한 도메인의 역할만 도메인 토큰에 실립니다.»
- **Three surfaces.** The «적용 예정» note is replaced by the effect note. Testids are renamed `*-applies-later-note` → `*-effect-note`; e2e · smoke · federation have 0 references to either.
  - Set screen: effect + latency.
  - Operator dialog: effect.
  - Group panel: «그룹의 세트는 모든 멤버에게 배정됩니다.» + effect.
- **Guide copies.** The `iam-guide/data.ts` «업무 권한 세트» note and the `permission-map.ts` `/domain-permission-sets` `mismatch` now state the post-P3 behaviour (sets ∩ subscribed domains, next re-exchange, at most about 6 minutes).
- **Green (local).**
  - Full `npx vitest run`: **395 files / 4502 tests passed**, rc=0.
  - `tsc --noEmit -p .`: rc=0.
  - `next lint`: rc=0. Its one warning is pre-existing in an untouched file (`api/onboarding/received-invitations/route.ts:26`).
- **Merge.** #4378 → `0091e1897` (merged by the main session).

## Slice c-2 PR 2 — design (2026-10-15 UTC)

- **Input = row 12 ∩ subscribed domains.** Main-session confirmation 2026-10-15: the input is `GET /api/admin/me/domain-access` intersected with the active tenant's subscribed domains.
  - After P3 this set **equals the assumed token's `roles`**, so the signal predicts the server without decoding the token. The contract forbids a token decode (Failure Scenario 1).
  - The `(console)` layout reads row 12 once per render, alongside the registry. The two WMS pages read it for their own buttons.
- **Contract amended first** (§ 2.4.3.4 «Domain nav — two signals»): the «업무 권한 없음» row is now **per menu**.
  - Each `domain`-gated leaf declares `serverRequires`: the roles of which the server needs at least one for the screen's **primary read**.
  - A leaf the server opens on subscription alone declares nothing and is never badged.
  - A drill parent is badged only when **every** `domain`-gated child is badged.
  - «구독 필요» wins: one cause per item.
  - A measured table records what is declared, and why. P5–P8 (`TASK-MONO-792`~`795`) add data only.
- **Declared today.**
  - `/wms/outbound` → `OUTBOUND_READ · OUTBOUND_WRITE`.
  - Every ecommerce leaf → `ECOMMERCE_OPERATOR`. That is the ecommerce gateway's `/api/admin/**` and operator-on-public rule (`AccountTypeEnforcementFilter.java:104-116`).
  - Everything else declares nothing, per the c-2 AC-0 table.
  - The fan leaves are not measured (ADR-083 ③ ⚪), so they declare nothing.
- **AC-2: the contract's READ-only rule is replaced by a per-screen `writeRequires`.**
  - **Why:** the contract rule (`:270`, «only `access=READ` entries → hide») **cannot be built from row 12**, which returns `{domainKey, role}` with no `access` field. It is also coarser than the server: an `INVENTORY_WRITE` holder is not «READ-only», but `/wms/outbound`'s writes need `OUTBOUND_WRITE`.
  - **What replaces it:** a screen's mutation buttons hide when the operator's business roles hold none of that screen's `writeRequires`. A READ-only set never holds a write role, so AC-2's case is unchanged.
  - **Declared:**
    - `/wms` alert «확인 처리» → `WMS_OPERATOR`.
    - `/wms/outbound` pick · pack · ship · cancel → `OUTBOUND_WRITE`.
    - «발송 재시도» (logistics-service, `authenticated()` only) carries nothing and stays.
  - 🔵 **This is my reading of decision 1 applied to buttons.** The main session asked for «the `:270` rule»; the struck text stays in the contract with the reason. Reported for confirmation.
- **The e2e pin** `overview-consolidation.spec.ts:82-87` (`/^ERP(구독 필요)?$/`) stays true: ERP declares nothing, so it can never carry «업무 권한 없음». A unit test now asserts the same regex on ERP with no business roles.
- **AC-1 wording.** AC-1's «권한 없음» is rendered as the badge text **«업무 권한 없음»** (contract wording).

## Slice c-2 PR 2 — red (2026-10-15 UTC)

- **Stubs:** so that `tsc` compiles with the tests, the red commit carries the type fields (`serverRequires` / `writeRequires`), the new props and the function signatures as **stubs**. They return «no badge / can write / null», so every red is an assertion.
- **New tests:**
  - `console-nav-business-permission.test.ts`
  - `sidebar-business-permission.test.tsx`
  - `my-domain-access-read.test.ts`
  - AC-2 blocks appended to `OutboundOpsScreen.test.tsx` and `WmsOpsScreen.test.tsx`
- **Local run of the 5 files: 15 failed / 68 passed.** All 15 are `AssertionError` or `TestingLibraryElementError` (missing badge testid). `tsc --noEmit -p .` rc=0.

## Slice c-2 PR 2 — what landed

- **`shared/api/iam-operators-read.ts`** — new `getMyDomainRolesOrNull()`, the row 12 read.
  - It uses the same hardened operators core: operator token and `X-Tenant-Id`.
  - It parses only `roles[]`.
  - Every failure returns `null`, which callers read as «don't know».
- **`shared/ui/console-nav-exposure.ts`** — pure functions, no React and no `features/` import:
  - `businessRolesWithin` computes row 12 ∩ subscribed domains. `undefined` = don't know; an empty set = known to hold nothing.
  - `businessPermissionBadgeKeys` returns the badged leaves, plus a parent when all of its `domain` children are badged. «구독 필요» wins over it.
  - `canWriteOn` gives the AC-2 answer.
- **`permission-map.ts` declarations:**
  - `/wms/outbound`: `serverRequires` `OUTBOUND_READ · OUTBOUND_WRITE`, `writeRequires` `OUTBOUND_WRITE`.
  - `/wms`: `writeRequires` `WMS_OPERATOR`.
  - All 9 ecommerce leaves: `serverRequires` `ECOMMERCE_GATEWAY_REQUIRES`.
  - The header comment no longer says «domain row = derived»; this is the BE-632 hand-off of `:24`. It now says sets ∩ subscribed, with `OperatorRoleDerivation` as the ceiling only.
- **Sidebar.** `BusinessPermissionBadge` («업무 권한 없음», a neutral tone that differs from the amber «구독 필요») renders on:
  - drill children;
  - the drill header;
  - top-level parents;
  - flat leaves.

  The testid is `${testid}-business-permission-badge`. The item always stays a link.
- **`(console)` layout.** Row 12 is fired alongside `/me`, and skipped for a sample visitor or when no tenant is selected. The layout passes `myBusinessRoles` to the sidebar.
- **AC-2 screens.**
  - `WmsOpsScreen` / `WmsAlertsTable` take `canAcknowledge`. When it is false, «확인 처리» hides and the note `wms-alerts-read-only-note` shows.
  - `OutboundOpsScreen` → `OutboundOrderDrill` → `OutboundDrillActions` take `canWrite`. When it is false, pick · pack · ship · cancel and the pick-blocked hint hide, the note `outbound-read-only-note` shows, and «발송 재시도» stays.
  - The two pages compute the flag through `app/(console)/wms/_lib/wms-business-roles.ts`: row 12 ∩ (active tenant ∈ the registry's `wms` tenants). The registry is already fetched; no second call.
- **e2e pin.** `overview-consolidation.spec.ts:82-87`: the comment now says why the exact regex stays true. The regex is unchanged.
  - I grepped `tests/e2e`, `e2e-smoke` and `tests/federation-hardening-e2e` for `wms-alert-ack`, `outbound-action`, `business-permission` and «업무 권한 없음»: **0 hits**.
- **Green (local).**
  - Full `npx vitest run`: **398 files / 4543 tests passed**, rc=0.
  - `tsc --noEmit -p .`: rc=0.
  - `next lint`: rc=0. Its one pre-existing warning is in an untouched file.

## Slice c-2 PR 2 — CI and branch e2e (2026-10-15 UTC)

- **CI red.** `f84a71172`, run 38787846471, job 116604482015: **15 failed / 4528 passed (4543)**. The 5 new or extended files are the only failing ones (`Test Files 5 failed | 393 passed`).
- **CI green.** `98d825e81`, run 38789329431:
  - job 116609609615: **4543 passed (4543)**;
  - console smoke job 116609609976: success.
  - `gh pr checks 4381`: 13 pass / 53 skipping / 0 fail.
- **Console nightly on the branch.** `gh workflow run nightly-e2e.yml --ref task/pc-fe-338-c2-pr2-nav-signal`:
  - Run 38789366286 (`98d825e81`) **success**, all 15 jobs.
  - Console job 116610381145: **12 passed**. That is all 5 specs in `tests/e2e` (12 `test(` calls), including `overview-consolidation.spec.ts` with the ERP pin.
  - The «red nightly arrives» job is success, so no issue was opened.
- **Federation e2e on the branch.** `gh workflow run federation-hardening-e2e.yml --ref …`:
  - Run 38789372503 **success**. Job 116610291271: **20 passed · 1 flaky · 0 failed**.
  - **The flaky spec:** `tenant-switch-rescope.spec.ts:110`. On the first attempt the globex `operator-overview-card-scm` was `degraded`, not `ok`; it passed on retry.
  - **Why I don't think this PR caused it:**
    - The assertion is on the overview composition's SCM leg (a domain-token read). It is not on a nav badge.
    - The same spec has flaked before and passed on retry (`TASK-BE-516`: «`tenant-switch-rescope:106` = flake (retry-passed)»).
    - 🔵 The layout's new row-12 call is an admin-service read with the operator token; it does not touch the domain token.

  Recorded, not fixed.

## AC status after PR 2 (2026-10-15 UTC)

- [ ] **AC-1** — Unit-tested against the measured server table (inventory set → «출고» badged, inventory/inbound/master/overview not, SCM «구독 필요» only).
  - The server side of the comparison is the 27th-window live result (BE-632 AC-1: inventory set → outbound GET 403) plus the code reading (dashboards synthesise `WMS_VIEWER` from subscription).
  - ⚪ **A live comparison of the console badge against the server's 403/200 in the same session** needs a running stack with P3; no CI harness seeds sets per operator. → `TASK-MONO-672` § Next window step 3 (rewritten).
- [ ] **AC-2** — Unit-tested: read set → outbound write buttons and «확인 처리» hidden; fail-open control.
  - ⚪ **The direct mutation call → server 403** has the same reason and goes to the same place (MONO-672 step 3). The outbound-service side is already pinned by its own slice tests (`OUTBOUND_READ`-only caller → 403).
- [ ] **AC-5** — The nightly console job on the branch is green (above). ⚪ «First nightly on `main` after merge» and the Vercel Production deployment record need the merge, so they are not measurable yet. MONO-672 step 3 lists them as checks to make before the window.

## 소유자 결정 — c-2 PR 2 (2026-10-15 UTC)

- **AC-2 기준 이탈 인정**: 계약 `:270` «READ 역할만 → 숨김» 대신 화면별 `writeRequires`(출고 = `OUTBOUND_WRITE` · `/wms` 확인 처리 = `WMS_OPERATOR`). 이유: 행 12 응답에 `access` 칸이 없어 만들 수 없고, 만들더라도 `INVENTORY_WRITE` 보유자에게 출고 버튼을 보여 서버 403 과 어긋난다. 메뉴 단위 결정과 같은 원칙(서버 판정 일치). 조회 전용 세트는 여전히 모든 버튼이 숨는다.
- **이커머스 배지 유지**: 9 화면 `serverRequires = ECOMMERCE_OPERATOR`(게이트웨이 규칙) — 서버가 거절하므로 «업무 권한 없음». **팬은 선언 없음** — 서버 검사를 잰 뒤(792~795 또는 별건) 데이터만 추가한다.

## AC-5 기록 — c-2 머지 뒤 (2026-10-15 UTC)

- **머지**: PR 1 #4378 `0091e1897` · PR 2 #4381 `091290a57` — 둘 다 머지 직전 실패 체크 0(12/54 · 13/53 pass/skipping).
- **머지 뒤 첫 nightly(main)**: run 38796953966 (push `091290a57`) success · 콘솔 잡 116636289449 `Running 12 tests using 2 workers` · `12 passed (1.0m)` · flaky 0.
- **Vercel Production(배포 기록으로 잼)**: `0091e1897` → deployment 7101097688 `success` (18:34:24Z) · `091290a57` → deployment 7102759725 `success` (19:50:41Z).
- ⇒ AC-5 닫힘. 남은 것 = AC-1 · AC-2 의 서버 403/200 라이브 대조(`TASK-MONO-672` § Next window 3단계, 773 을 실은 재굽기 창). 티켓은 그때까지 `in-progress/`.

## 28차 창 라이브 (2026-10-16 UTC) — AC-1 · AC-2

- 창: AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`(P3 포함) · 콘솔 = Vercel Production(`091290a57` 이후). 소유자 브라우저, `requester@demo.com` 를 `demo-corp` 에서 `run-338.sh` inv / out / ro / restore 로 세트만 바꿔 가며. 서버 쪽은 같은 세트 · 같은 세션의 assume 토큰으로 직접 호출.
- **AC-1 ✅** — `wms-inventory-operate` 만:
  - 화면: 사이드바 WMS ▸ «출고» 에 «업무 권한 없음», 열면 «이 화면을 조회할 권한이 없습니다»(서버 403). «재고» 는 배지 없음 · 열림.
  - 서버: roles = `[INVENTORY_READ, INVENTORY_WRITE]` · inventory GET 200 · `/admin/dashboard/inventory` 200 · outbound orders **403**.
  - 대조: `wms-outbound-operate` 추가 → 재로그인 → 배지 사라짐 · 출고 열림(서버 outbound GET 200 · 없는 id `:cancel` → 400 = 게이트 통과).
  - «SCM 미구독 테넌트면 «구독 필요»» 칸은 `demo-corp` 가 SCM 구독 테넌트라 이 창에서 조건이 성립하지 않는다 — 단위 시험(§ Slice c-2 PR 2, `sidebar-business-permission.test.tsx`)이 맡는다. AC 의 «단위 시험 + 서버 대조 기록» 중 서버 대조가 필요한 칸(출고 403 ↔ 배지, 재고 200 ↔ 배지 없음)은 위에서 같은 세션으로 닫혔다.
- **AC-2 ✅** — `wms-inventory-operate` + 테넌트 세트 {`OUTBOUND_READ`}:
  - 화면: 출고 목록 열림 · 주문 상세에 피킹 · 패킹 · 출고 확정 · 취소 버튼 없음 + «조회 전용 — 출고 변경(피킹 · 패킹 · 출고 확정 · 주문 취소)에 필요한 업무 권한이 이 테넌트에서 배정되지 않아 해당 버튼을 숨겼습니다.»
  - 서버: outbound GET 200 · 같은 토큰으로 `:cancel` **403**.
- 관측(결함 아님): «발송 재시도»(SCM logistics)는 그대로 보인다 — 서버는 792 Q3 이후 403. 숨기는 것은 `TASK-PC-FE-342` 범위(그때까지 예상된 상태).
- 관측: 콘솔은 assume 토큰을 최대 ~4분 들고 있어(쿠키 240 s — § Slice c-1) 세트 변경은 재로그인 / 재assume 뒤에 화면에 반영된다.
- 복원: 세트 행 = `legacy-compat` `MIGRATION` 1행(`granted_at` 16:17:27.189715) · roles 전부 — 바이트 동일.
- 기록 위치: `TASK-MONO-672` § 28th window — harvest.

## 닫기 (2026-10-16 UTC) — in-progress → done

- **(a)(b)(c) — 구현 PR 6개 전부 `gh pr view` 로 확인**: #4347 `56a15be8b` · #4348 `27cdcebd2` · #4349 `808fa9e84` · #4354 `ca176d570` · #4378 `0091e1897` · #4381 `091290a57` — 모두 `state=MERGED` · `origin/main` 조상 · `statusCheckRollup` 실패 0 (각 9~13 pass / 나머지 skipped).
- **(d) — AC 절을 열어 동사별로 읽음**: AC-0 재측정 기록 있음 · AC-1 «단위 시험 + 서버 대조 기록» = 둘 다 · AC-2 «숨김 · 서버 403(라이브)» = 둘 다 · AC-3 «회수 버튼 없음 · 그룹분 유지가 보임» = 단위 시험 · AC-4 «이름 분리 · 회귀 시험» · AC-5 «nightly 확인 · Vercel 따로 적기» = § AC-5 기록. 전부 닫힘.
- `review/` 단계는 거치지 않고 바로 `done/`: 구현 PR 은 각각 머지 시점에 검토됐고, 마지막 남은 두 AC 는 라이브 측정뿐이었다(코드 변경 없음).
