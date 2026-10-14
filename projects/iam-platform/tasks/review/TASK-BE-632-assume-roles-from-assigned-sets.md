# Task ID

TASK-BE-632

# Title

`ADR-MONO-083` P3 — auth-service: assume `roles` = 배정된 세트 ∩ 구독 · 상한 · 구독 파생 제거 · 운영자 토큰에서 `erp.write` 제거 · assume 토큰 TTL(OD-2) · 회수 지연 실측

# Status

review

# Owner

backend

# Task Tags

- code
- test
- deploy

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (토큰 발급 · 전환 순간의 대조군 · 롤백 창)
> 🔴 **이 단계가 실제로 권한을 좁힌다.** 배포 게이트 = `TASK-BE-631` 배포 + 백필 완료 판정 SQL = 0(라이브). 게이트가 ⚪ 면 배포하지 않는다.

# Dependency Markers

- **선행**: `TASK-BE-631` 머지 **및 배포**(AC-5 완료 판정 0) · `TASK-MONO-791`(P1 계약).
- 🔴 **Deployment prerequisite (TTL 300 s) — added 2026-10-14 UTC**: `TASK-PC-FE-338` slice c-1 (#4349 `808fa9e84`) must be **live on the production console (Vercel)**.
  - Without it, once the assume token drops to 300 s the console makes domain calls with the **base token of the login tenant** for 25 minutes out of every 30. The domain screens also fall to «테넌트를 먼저 선택하세요».
  - Before deploying, look up the production deployment of `808fa9e84` or later in GitHub Deployments `Production – kanggle-console`, and confirm it is `success`. A main merge is not proof of deployment.
- **Follow-up (console)**: `TASK-PC-FE-338` slice c-2 (the «업무 권한 없음» nav signal and button hiding for read-only sets) can only be measured against the server after this ticket is deployed.
- **후속**: `TASK-MONO-792`~`795`(도메인 세분 — 카탈로그가 넓어지면 이 경로가 그대로 싣는다).

# Goal

운영자 assume 의 `roles` 를 «admin-service 가 준 `domainRoles` ∩ 카탈로그(테넌트 entitled domains = 구독 ∩ 조직 상한)» 로 바꾸고, 구독 파생과 범위 우회(`erp.write`)를 없앤다. 기존 토큰이 언제 권한을 잃는지 숫자로 잰다.

# Scope

## In Scope

- `TenantClaimTokenCustomizer` 운영자 분기: `roles` = `domainRoles` ∩ derive(entitled) — 파트너십 `delegatedScope` 처럼 **그대로 싣지 않는다**. `OperatorRoleDerivation` 의 운영자 경로 제거(카탈로그는 P2 의 한 자리). 빈 결과 = `roles` 생략(지금 규칙).
- 판정 실패 = assume 거절(지금 fail-closed 유지) · `domainRoles` 없음/빈 값 = 역할 0 — 어떤 경로도 구독 전체로 떨어지지 않음.
- `AssumeTenantAuthenticationProvider` — 운영자 assume 토큰 `scope` 에서 도메인 쓰기 범위(`erp.write`) 제외(워크로드 분기 · `erp-platform-internal-services-client` 무변경). 백필로 기존 운영자는 `ERP_OPERATOR` 를 받으므로 ERP 쓰기는 역할로 유지된다.
- (OD-2 기본값) assume 토큰만 TTL 5분 — 기본 토큰 · 리프레시 TTL 무변경. 콘솔 재교환 빈도 영향은 P4 와 대조.
- 파트너십 경로 무변경 단언 — 호스트 테넌트 세트를 읽지 않는다(위임 상한을 넓히지 않음).
- 파생 줄 번호를 인용하던 바깥 핀 정리: `console-web …/permission-map.ts:24, 245` · `tests/federation-hardening-e2e/specs/scm-inbound-expected-loop.spec.ts:49, 74` · `infra/demo/seed/` 문서 · `DemoCsOperatorDerivedRolesTest`.
- 롤백(ADR D8): 이 PR 을 되돌리는 배포가 복구다 — 전환 플래그를 두지 않는다. 롤백 창 = 머지 ~ 다음 AMI 굽기, 그 뒤 파생 코드 잔재 0 확인.

## Out of Scope

- admin 쪽 저장 · API(P2) · 콘솔(P4) · 도메인 세분(P5~P8).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 운영자 도메인 역할 입구가 여전히 둘(일반 assume · 파트너십)뿐인지 · `erp.write` 등록 상태 · 파생을 인용하는 바깥 핀 전수. 🔴 **OD-3 재확인**: 기본값(«`'*'` 도 배정된 세트만»)이면 플랫폼 운영자는 ADR-082 D2 로 회사 소속 · 세트 배정을 가질 수 없어 **도메인 역할이 0** 이 된다 — 데모 `demo-platform` 등 영향 행을 세서 **착수 전 소유자에게 보이고 답을 기록**한다. 🟢 **답 도착(2026-10-14 UTC, `TASK-MONO-791` AC-0 · ADR-083 § OD 소유자 결정)**: «세트 배정 허용» — `'*'` 운영자는 `SUPER_ADMIN` 이 준 테넌트별 / `'*'` 테넌트 세트를 받는다 · 백필 `'*'` × `legacy-compat`. ⇒ 이 단계의 대조군에 «`'*'` 운영자: 백필 직후 `roles` 바이트 동일 · `legacy-compat` 회수 뒤 `fan-platform` 의 `FAN_OPERATOR` 없음 · 다시 배정하면 있음» 을 더한다.
- [ ] **AC-1** — 🔴 대조군(실패 쪽 먼저): A 가 WMS · SCM 구독 · 재고 세트 운영자 → 토큰 `roles` 에 `INVENTORY_*` 만 · `OUTBOUND_*` · `SCM_OPERATOR` 없음 / 출고 세트 → 반대 / SCM 세트 → `SCM_OPERATOR` 만. 조회 세트 → `*_READ` 만.
- [ ] **AC-2** — 상한: 세트는 있으나 구독 해지 → 다음 교환에서 그 역할 없음 · 조직 노드 상한 밖 도메인 → 없음.
- [ ] **AC-3** — 실패: 판정 호출 실패 → `invalid_grant` · `domainRoles` 빈 배열 → `roles` 생략 · entitled 조회 실패 → `roles` 생략(지금 규칙) — 셋 다 구독 전체 아님.
- [ ] **AC-4** — 이전 대조군: 백필된 기존 운영자의 assume `roles` 가 전환 전과 **바이트 동일**(같은 테넌트 · 같은 구독) · 백필 뒤 초대된 운영자는 `roles` 없음.
- [ ] **AC-5** — 🔴 `erp.write` 제거: ERP 세트 없는 운영자의 ERP 쓰기 403(전환 전 200 — 실패 쪽 먼저 단언) · `ERP_OPERATOR` 세트 운영자 쓰기 200 · 워크로드 클라이언트 쓰기 200.
- [ ] **AC-6** — 회수 지연 실측(D7): 세트 회수 · 그룹 탈퇴 · 운영자 정지 · 구독 해지 · 세트 구성 변경 각각 → 기존 토큰으로 도메인 API 호출이 403 이 되는 시각 = 토큰 `exp`(OD-2 값) 이내, 다음 교환에서 즉시 반영. 측정값을 계약 문장과 대조.
- [ ] **AC-7** — 파트너십: 파트너 운영자에게 호스트 테넌트 세트 행을 넣어도 토큰 `roles` 가 `delegated ∩ participant` 를 넘지 않음.
- [ ] **AC-8** — 배포 상태를 따로 적는다(`main` 머지 · AMI · 데모 창 라이브 — 재고팀/출고팀 시나리오를 실제 도메인 API 로). 라이브 없으면 ⚪ 와 이유.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D3 · D6 · D7 · D8 · § OD 대조
- `docs/adr/ADR-MONO-035-…md` O1 (덧붙임 — O1-A 대체) · `ADR-MONO-045-…md` D3

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (P1) · assume 토큰 클레임 문서(P1 AC-0 이 정한 정본)

# Edge Cases

- 같은 운영자가 A · B 를 번갈아 assume — 토큰마다 그 테넌트의 세트만.
- 세트에 카탈로그에 없는 역할이 남아 있음(카탈로그 축소 뒤) — 교집합으로 걸러져 싣지 않음.
- assume 토큰 TTL 을 줄였는데 콘솔이 만료 전에 재교환하지 않는 화면 — P4 와 대조(만료 시 401 재로그인 고리 금지).

# Failure Scenarios

1. `domainRoles` 를 파트너십처럼 그대로 싣는다 — 구독 해지 · 상한 밖 역할이 토큰에 남는다.
2. 파생 코드를 «폴백» 으로 남긴다 — 판정 장애가 업무 전체 권한이 된다.
3. 역할만 바꾸고 `erp.write` 를 둔다 — ERP 쓰기가 세트와 무관하게 열린 채다.
4. 백필 완료 판정 없이 배포 — 백필 빠진 운영자가 업무 0 이 된다.

---

# Implementation Record (2026-10-14 UTC)

## AC-0: re-measurement

- **Entry points for operator domain roles are still two**: the normal assume (`TenantClaimTokenCustomizer#customizeForAssumeTenant`, after `populateEntitledDomains`) and partnership (`delegatedScope != null` → `applyCrossOrgCap`, then return). The workload branch carries no derived roles (`customizeForWorkloadAssumeTenant`).
- **`erp.write`**: registered on the console client in V0023. `AssumeTenantAuthenticationProvider` put every registered scope of the client onto the assumed token's `authorizedScopes`. ERP checks writes as `RoleScopeAuthorizationAdapter` WRITE = `erp.write ∨ isOperator()` (ERP_OPERATOR · ERP_ADMIN · SUPER_ADMIN). Removing `erp.write` therefore leaves ERP writes open only to holders of `ERP_OPERATOR`.
- **Catalog equality**: admin `DomainRoleCatalog` (14 roles) = auth `OperatorRoleDerivation` (union over every domain) = 14 roles. ⇒ The legacy-compat holder's `roles` are **byte-identical** to before (derive order is kept).
- 🔴 **Operators seeded after Flyway**: the V0052 backfill only sees operators that exist when it runs. Three runtime seeds load **after** admin Flyway:
  - `tests/federation-hardening-e2e/fixtures/seed.sql` (nightly federation)
  - `console-web/tests/e2e/fixtures/seed.sql` (nightly console)
  - `scripts/console-demo/seed/01-iam.sql`

  The operators they seed hold **no sets**. With P3 alone they would carry no domain role and their domain calls would 403 (e.g. `scm-inbound-expected-loop.spec.ts` INBOUND_READ). ⇒ I appended the same legacy-compat rows the backfill would have written to all three files. The demo `R__` seeds were already covered by BE-631 `R__seed_zz_demo_domain_permission_sets.sql`.
- **OD-3 control**: a `'*'` operator has its `domainRoles` computed by admin from the `'*'` rows. The auth path is the same as for a normal operator (no extra code).
- **External pins citing the derivation**: grepping finds 62 files. Classified:
  - **(changed here)** auth unit tests, ITs and `DemoCsOperatorDerivedRolesTest`; the federation spec comments; the demo seed README and `lib.sh`; the three runtime seeds.
  - **(contracts — no change needed)** P1 already split the wording into «from the P3 deploy» and «before deploy (now)».
  - **(handed off)** `console-web permission-map.ts:24, 249 · 556 · 612 · 636 · 694`. The drift test only checks that the file exists. The guide wording (domain row = derived) is switched by `TASK-PC-FE-338` c-2 after P3 is deployed.
  - **(left as is)** Explanations in other domains such as «operators hold SCM_OPERATOR» stay true for legacy-compat holders.

## Changes

- `OperatorAssignmentPort.AssignmentResult` gains `domainRoles` (null = absent = roles 0).
- `AdminAssignmentClient.parseDomainRoles` and the `AssumeTenantAuthenticationToken` constructor/getter carry it through.
- `TenantClaimTokenCustomizer`: on the normal path `roles = assignedWithinCeiling(domainRoles, OperatorRoleDerivation.fromEntitledDomains(entitled))`.
  - The order is the ceiling's order.
  - null, `[]`, an empty ceiling, or no overlap → the claim is omitted.
  - **No branch falls back to the subscription.**
- `OperatorRoleDerivation`: the javadoc now states it is a **ceiling** and must move together with admin `DomainRoleCatalog` (code unchanged).
- `AssumeTenantAuthenticationProvider` (operator branch only):
  - `authorizedScopes` = registered scopes − `{erp.write}`;
  - access-token TTL = `min(client, 300 s)` via a copy of `RegisteredClient`, so the base token, refresh token and workload branch are unchanged.

## AC status

- [x] **AC-0** — above.
- [x] **AC-1** — Customizer unit tests: inventory set on a WMS+SCM tenant → exactly `INVENTORY_READ`/`INVENTORY_WRITE`. Outbound set → `OUTBOUND_*`, SCM set → `SCM_OPERATOR`, read set → `*_READ`. IT on the wire: `roles` == `["INVENTORY_READ","INVENTORY_WRITE"]`.
- [x] **AC-2** — Unit: a set whose domain is unsubscribed → that role is dropped; only unsubscribed roles → claim omitted. (The org-node ceiling is computed by account-service inside `entitled_domains`, so it is the same intersection.)
- [x] **AC-3** — Unit: `[]`, absent and entitled-empty → claim omitted. IT: a fully subscribed tenant with `[]` → no `roles`. A failed judgement call → `invalid_grant` is the existing fail-closed path (no change, existing tests).
- [ ] **AC-4** — Unit: a legacy-compat holder yields the same bytes as the old derivation. Rows for operators invited after the backfill are proven by BE-631 (0 rows). ⚪ **Live «byte-identical before and after the switch» is not measured** (needs deployment). Closed once the demo window is measured.
- [ ] **AC-5** — Unit: the operator assumed scope has no `erp.write`, and the registration itself keeps it. IT: the assumed token's `scope` has no `erp.write` (the old `contains` assertion flipped). ⚪ **Live ERP writes not measured**: 403 without an ERP set, 200 with one, 200 for the workload client. Needs deployment.
- [ ] **AC-6** — ⚪ Revocation-latency measurement is live-only (once the token TTL is 300 s, an existing token turns 403 at most 300 s after revocation). Not measured before deployment.
- [x] **AC-7** — Unit: a partnership grant with the whole catalog in `domainRoles` → `roles` = `delegatedScope.roles` exactly. The partnership IT is unchanged.
- [ ] **AC-8** — ⚪ Deployment status recorded separately (below).

## Deployment gates (all must hold before deploy)

1. `TASK-BE-631` V0052 is applied on demo/production, and the backfill completeness SQL (BE-631 data-model.md) measures **0 live**.
2. The console `808fa9e84` (PC-FE-338 c-1) or later is a **Production deployment `success`** — **met** (2026-10-14 01:59Z, measured).
3. This PR is merged, then the AMI is baked and the demo window opens. Rollback = a deploy that reverts this PR (ADR D8, no switch flag).

## Merge record (2026-10-14 UTC)

- #4351 → `08f2da4a3`. Failing checks at merge: 0.
- Integration lane B **PASSED**, confirmed in the log: `happy` (no `erp.write`, `expires_in` 300), BE-632 AC-1 (wire), BE-632 AC-3 (wire), STEP 5.
- 🔴 **Merge ≠ deployment.** This stays in `review/` until the three deployment gates above are met, the AMI is baked, and AC-4 · AC-5 · AC-6 · AC-8 are measured in the demo window. If AC-4/5/6/8 still cannot be measured live, the reason is recorded as ⚪ and the ticket is not closed.

## 27th demo window — live measurement (2026-10-14 UTC)

AMI `ami-0775fa009a12a824d` baked at `73daae263` (`bake.sh --ref bake-73daae263`, provenance `ami-tag`). Instance `i-029065c2b2d7b8121` came up at 16:57Z: 89 containers · unhealthy 0 · seed failures 0. All measurements ran on the host through SSM; claims were decoded, tokens never printed.

**Deployment gate 1 (backfill) — met.** Flyway `0050`–`0053` all succeeded. Re-measured after every seed had finished (17:09:30Z):
- ① canonical predicate = **0**. The pre-cutover population is 0 memberships and 2 platform operators; both platform operators have `'*'` set rows.
- ② window predicate = **0** over 6 memberships (9 set rows).
- 🔴 The first attempt used `version = '52'` and gave a **vacuous 0**: Flyway stores `0052`, so the cutover was NULL. It was caught by printing the cutover and the population sizes.

| AC | Result | Evidence |
|---|---|---|
| **AC-4** | ✅ | The demo operator assumes `demo-corp`. `roles` = `[ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR, WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE, INBOUND_READ, INBOUND_WRITE, INVENTORY_READ, INVENTORY_WRITE, MASTER_READ]`, which is the `infra/demo/seed/README.md` list expanded, in order. `scope` has no `erp.write`. `exp − iat = 300`. |
| **AC-1** (live) | ✅ | `requester@demo.com`, `wms-inventory-operate` only → `roles = [INVENTORY_READ, INVENTORY_WRITE]`. Inventory + outbound sets → `[OUTBOUND_*, INVENTORY_*]`, and in that state: outbound GET 200, **SCM supplier registration 403** `PERMISSION_DENIED` («OPERATOR actor required»), **ERP department create 403** («actor lacks required scope write»). Neither write created data. Control: with the full sets, outbound GET 200 → with the inventory set only, 403. 🔵 The SCM *PO list* GET is 200 by design (a non-operator reads as BUYER), so the run order's «SCM = 403» meant an operator-only SCM action. |
| **AC-5** | 🟡 | 403 without an ERP set: ✅ (above). ERP write with an ERP set: covered by the same boot's seed, which created 22 ERP master rows with the demo operator token (`ERP_OPERATOR`) and 0 failures. ⚪ **Workload-client ERP write: not measured.** It needs that client's secret on the host; not attempted this window. |
| **AC-6** (set revoke) | 🟡 | After revoking a set, re-exchanging gives a token without the role **immediately**, and that token gets 403. The **existing** token keeps working until `exp` and turns **401 at `exp + 62 s` and `exp + 63 s`** (two runs, 10 s and 5 s polling). ⇒ The real maximum is ≈ **360 s, not 300 s**. The resource servers seem to allow ~60 s of clock skew. 🔴 This contradicts the contract sentence «at most 300 s after revocation», so it needs a decision: change the sentence to `exp + skew`, or reduce the skew. ⚪ Group leave · operator suspend · subscription cancel · set edit were not measured (owner decision 2026-10-15: they touch the whole demo). |
| **AC-8** | ✅ | Merge `08f2da4a3` (#4351, 2026-10-14) → AMI 27th `ami-0775fa009a12a824d` (`73daae263`, pin #4364) → live in the 27th window (2026-10-14 16:57Z–18:37Z), with the inventory/outbound narrowing above checked against the real domain APIs. |

- **Demo data changed (owner decision 2026-10-15):** `requester@demo.com` in `demo-corp` no longer holds `legacy-compat`, which cannot be granted through the API. It holds the five subscribed `*-full` presets as `DIRECT` instead. Its assumed `roles` were re-checked **byte-identical** to before. `all-domains-full` was refused with 422 because it includes the unsubscribed `fan` and `mes`. The next re-bake (fresh volume) restores the seed.
- Found on the way (not this ticket): WMS inventory list 500, a `ClassCastException` on `Instant` → `Timestamp` → `TASK-BE-637`.
- **Still in `review/`.** AC-5 (workload client) and AC-6 (four revocation kinds, plus the skew finding) are open, and the rule above says the ticket is not closed with ⚪. Whether to close with these recorded ⚪ is an owner decision.
