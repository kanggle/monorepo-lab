# Task ID

TASK-PC-FE-015

# Title

platform-console post-Phase-5/6/7 + Option (a) closure placeholder narrative sweep — BE-305 sister (consumer-side stale drift closure)

# Status

ready

# Owner

frontend (spec-only, doc-only)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: nothing. All referenced reality is on `origin/main`:
  - ADR-MONO-008 ACCEPTED 2026-05-19 (finance bootstrap)
  - ADR-MONO-016 ACCEPTED 2026-05-19 (erp bootstrap)
  - ADR-MONO-013 § D6 Phase 5 COMPLETE 2026-05-19/20 (finance — FIN-BE-005 + FE-009)
  - ADR-MONO-013 § D6 Phase 6 COMPLETE 2026-05-20 (erp — ERP-BE-001 / MONO-124 / ERP-BE-002 / FE-010)
  - TASK-PC-BE-001 console-bff skeleton DONE 2026-05-20 (Phase 7 BFF lands)
  - TASK-PC-FE-011 MVP "Operator Overview" composition route DONE 2026-05-20 (§ 2.4.9.1)
  - TASK-PC-FE-013 "Domain Health Overview" composition route DONE 2026-05-21 (§ 2.4.9.2)
  - TASK-BE-304 + TASK-PC-FE-014 Phase 2 option (a) end-to-end activation DONE 2026-05-21 (operator profile `finance_default_account_id` carrier + console consumer adoption)
  - TASK-BE-305 GAP `ProductCatalog` finance/erp `available: true` reality-alignment DONE 2026-05-21 (producer side — `console-registry-api.md` + `multi-tenancy.md` synced + `ProductCatalog.java` 2-line flip)
- **origin**: surfaced during the TASK-BE-305 post-merge audit (2026-05-21). BE-305 closed the producer-side (GAP `console-registry-api.md` + `multi-tenancy.md`) but the **platform-console consumer-side** carries the same stale narrative in 2 files / 5 places — `ServiceTile.tsx` behavior already adapts correctly to `available: true` (TASK-PC-FE-001 era, both branches handled), but the **spec/onboarding narrative** still says "finance/erp 생성 전까지 available:false coming soon" and "BFF added when it lands" etc. This task aligns the consumer-side narrative to match what already merged (BE-302 / BE-305 reality-alignment pattern, 4회째 적용).
- **prerequisite for**: nothing.
- **spec-first**: spec PR (this task md + INDEX entry, markdown fast-lane) → impl PR (5 surgical line edits across `PROJECT.md` + `console-integration-contract.md`, doc-only) → close chore PR (Status `ready → done` + INDEX move).
- **no ADR** (BE-302 reality-alignment pattern, 4회째): every referenced architectural decision is already ACCEPTED (ADR-008/013/014/015/016/017), every Phase 5/6/7 + Option (a) Phase 2 chain merged. This task only aligns the consumer-side narrative; competing convention 부재 → ADR-trigger N/A.

---

# Goal

5 active stale-narrative drifts surfaced post-Phase-5/6/7 + Option (a) closure. All 5 are in **2 consumer-side files** in `projects/platform-console/`:

| # | File | Line | Stale | Live reality |
|---|---|---|---|---|
| 1 | `PROJECT.md` | 22 | `"erp/finance는 생성 전까지 available:false 'coming soon' 타일이며, 생성 후 레지스트리 설정만으로 켜진다"` | finance V1 live (Phase 5) + erp V1 live (Phase 6); TASK-BE-305 flipped `ProductCatalog` already |
| 2 | `specs/contracts/console-integration-contract.md` | 41 | `"available | boolean | false → rendered as 'coming soon' (e.g. erp/finance pre-bootstrap)"` | All 5 federated domains live; the example is stale (no longer applies) |
| 3 | `specs/contracts/console-integration-contract.md` | 1243-1246 | `"Phase 7 = MVP-only at this commit. Subsequent Phase 7 dashboards (e.g. domain health, throughput) are separate future tasks ... they will be added as § 2.4.9.2, § 2.4.9.3, ..."` | § 2.4.9.2 (Domain Health) merged 2026-05-21 (TASK-PC-FE-013); only throughput remains future |
| 4 | `specs/contracts/console-integration-contract.md` | 1519 | `"console-bff aggregation endpoint shapes (ADR-MONO-013 Phase 7 — added when the BFF lands)"` (Out of Scope) | BFF lands (TASK-PC-BE-001 DONE 2026-05-20); § 2.4.9.1 + § 2.4.9.2 aggregation endpoint shapes already in this contract |
| 5 | `specs/contracts/console-integration-contract.md` | 1520 | `"finance/erp domain contracts (governed by ADR-MONO-008 / future erp ADR)"` (Out of Scope) | `future erp ADR` = ADR-MONO-016 ACCEPTED 2026-05-19 |

Each is a single-line (or small block) fix that aligns the narrative to live state. None require code change. None require ADR amendment.

# Decision authority (why no ADR, why 2 PRs spec + impl + close)

**No ADR (BE-302 reality-alignment pattern, 4회째 적용)**: the governing architectural decisions are pre-recorded in ADR-MONO-008, ADR-MONO-013 § D6, ADR-MONO-014, ADR-MONO-015, ADR-MONO-016, ADR-MONO-017 — all ACCEPTED. The Phase 5/6/7 + Option (a) Phase 2 chains were executed under those decisions. This task aligns the consumer-side narrative; there is no competing convention to choose between, no architecture decision open.

**Why spec → impl → close 3-PR chain (not single PR)**: keeps the project lifecycle (spec PR records the audit + scope; impl PR carries the actual edits) consistent with every other task on this project (BE-302 / BE-303 / BE-304 / BE-305 / PC-FE-011..014 — all 3-PR chain). The doc-only nature does not justify deviation from convention.

**Why no `ProductCatalog.java` re-touch**: TASK-BE-305 already flipped `available: false → true` on the GAP producer side; this task is consumer-side narrative only. The producer code is byte-unchanged in this task.

**Why no `ServiceTile.tsx` re-touch**: the component already handles both `available` branches (TASK-PC-FE-001 era); the BE-305 flip is already rendered correctly. No FE code change needed.

---

# Scope

## In Scope

**Spec PR**:

- `projects/platform-console/tasks/ready/TASK-PC-FE-015-post-phase-5-6-7-placeholder-narrative-sweep.md` — this task md.
- `projects/platform-console/tasks/INDEX.md` — ready entry.

**Impl PR (5 surgical line/block edits, 2 files, doc-only)**:

- `projects/platform-console/PROJECT.md` (1 edit, line 22):
  - BEFORE: `데이터 드라이브 카탈로그: 서비스 카탈로그는 GAP의 product/tenant 레지스트리에서 읽는다. erp/finance는 생성 전까지 available:false "coming soon" 타일이며, 생성 후 레지스트리 설정만으로 켜진다(콘솔 재작업 0).`
  - AFTER: narrative updated to reflect 5/5 federated domains all V1 live (gap + wms + scm + erp + finance), all `available: true` per TASK-BE-305 reality-alignment; the catalog's data-driven nature is preserved as the *forward-looking* property (new products in the future), not as a description of erp/finance specifically.

- `projects/platform-console/specs/contracts/console-integration-contract.md` (4 edits):

  - **Line 41** (item shape table `available` row, the inline example):
    - BEFORE: `| available | boolean | false → rendered as "coming soon" (e.g. erp/finance pre-bootstrap) |`
    - AFTER: `| available | boolean | false → rendered as "coming soon"; reserved for future product additions (all 5 federated domains v1 live as of TASK-BE-305 2026-05-21) |`

  - **Lines 1243-1246** (Phase 7 MVP-only blockquote):
    - BEFORE: `> Phase 7 = MVP-only at this commit. Subsequent Phase 7 dashboards (e.g. domain health, throughput) are separate future tasks (ADR-MONO-017 § D8); they will be added as § 2.4.9.2, § 2.4.9.3, ... sub-sections, each inheriting the hard invariants above.`
    - AFTER: blockquote updated to reflect current state — MVP "Operator Overview" (§ 2.4.9.1, TASK-PC-FE-011) + "Domain Health Overview" (§ 2.4.9.2, TASK-PC-FE-013) are merged; subsequent Phase 7 dashboards (e.g. throughput) remain separate future tasks following the same § 2.4.9.X additive pattern.

  - **Line 1519** (Out of Scope §, `console-bff` aggregation bullet):
    - BEFORE: `- console-bff aggregation endpoint shapes (ADR-MONO-013 Phase 7 — added when the BFF lands).`
    - AFTER: this line should be **removed** — `console-bff` aggregation endpoint shapes are now **in scope** (§ 2.4.9.1 + § 2.4.9.2 added by TASK-PC-FE-011 + TASK-PC-FE-013). Removing the line entirely is the correct fix (rather than editing it to a no-op or relocating).

  - **Line 1520** (Out of Scope §, finance/erp domain contracts bullet):
    - BEFORE: `- finance/erp domain contracts (governed by ADR-MONO-008 / future erp ADR).`
    - AFTER: `- finance/erp domain contracts (governed by ADR-MONO-008 / ADR-MONO-016 — both ACCEPTED 2026-05-19).`

## Out of Scope

- **`ProductCatalog.java` re-touch** — already flipped by TASK-BE-305 (producer side); no consumer-side equivalent code change needed (BE-305 § Decision authority: consumer flip 불요, `ServiceTile.tsx` already handles both `available` branches).
- **`ServiceTile.tsx` re-touch** — already handles both `available: true` (interactive) and `available: false` (non-interactive "coming soon") branches per TASK-PC-FE-001 era. No FE code change.
- **`tasks/done/*.md` historical records re-write** — every match in `tasks/done/` is a historical record of what was true *at task author time*. Per BE-302 / BE-303 / BE-305 convention, `tasks/done/` is **append-only** (the closure record stands as written; we do not rewrite history). The active spec/PROJECT.md surface is the only edit target.
- **5 producer specs outside GAP** (`wms-platform/`, `scm-platform/`, `finance-platform/`, `erp-platform/`, `fan-platform/`, `ecommerce-platform/`) — byte-unchanged in this task. Their own placeholder narratives (if any) are not in scope here.
- **Other monorepo-level files** (root `README.md`, `docs/project-overview.md`, ADRs, `rules/`, `libs/`) — byte-unchanged in this task. If a related drift surfaces during the audit, it is **logged honestly** but **not fixed here** — a separate task per scope.
- **ADR amendment** — none. The architectural decisions (ADR-008/013/016/017) are all ACCEPTED and govern; this task is their consequence, not a new decision.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: spec PR contains exactly 2 files (this task md + INDEX entry). No production code, no spec md edits. Markdown path-filter fast-lane.
- **AC-2 (impl PR surgical, scope-locked)**: impl PR `git diff --stat origin/main` shows exactly **2 files modified** — `PROJECT.md` (1 line) + `console-integration-contract.md` (4 edits: 1 row in table at line 41 + 1 blockquote at lines 1243-1246 + 2 bullets at lines 1519/1520 with 1 deletion + 1 reword). No code, no test, no other spec.
- **AC-3 (BE-305 verbatim reuse)**: the AFTER narrative for `PROJECT.md` and `console-integration-contract.md:41` cross-references TASK-BE-305 (`available: true` reality-alignment) verbatim. No new narrative introduced; the consumer narrative aligns to the producer-side language from BE-305 spec PR (#695).
- **AC-4 (`ProductCatalog` byte-unchanged)**: `git diff origin/main -- projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/console/ProductCatalog.java` = empty. Producer-side BE-305 flip is not re-touched.
- **AC-5 (5 producers + ADRs + libs byte-unchanged)**: `git diff --stat origin/main -- 'projects/{wms,scm,finance,erp,fan,ecommerce}-platform/' projects/global-account-platform/ docs/adr/ rules/ libs/ tasks/ platform/` = **empty** in impl PR.
- **AC-6 (no `tasks/done/*.md` re-touch)**: `git diff --stat origin/main -- projects/platform-console/tasks/done/` = **empty** in impl PR (historical append-only).
- **AC-7 (no FE/BE code re-touch)**: `git diff --stat origin/main -- projects/platform-console/apps/` = **empty** in impl PR (no console-web, no console-bff).
- **AC-8 (no ADR)**: `git diff --stat origin/main -- docs/adr/` = **empty** in both spec + impl PRs.
- **AC-9 (CI green)**: markdown fast-lane only (`changes` + `Frontend E2E smoke` pass; all Gradle / Integration jobs skipped). No code/test job triggered. **BE-303 3-dim verified at close chore** per [`CLAUDE.md § Task Rules`](../../../../CLAUDE.md).

# Related Specs

- `projects/platform-console/PROJECT.md` § 데이터 드라이브 카탈로그 (1 line edited).
- `projects/platform-console/specs/contracts/console-integration-contract.md`:
  - § 2.2 Item shape table (line 41, 1 row edited).
  - § 2.4.9.1 + § 2.4.9.2 nearby blockquote (lines 1243-1246, edited).
  - § 4 Out of Scope (lines 1519-1520, 1 line removed + 1 line edited).

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — the consumer contract being aligned. **No element / shape / surface change** — only narrative is updated. All element shapes (§ 2.1 / 2.2 / 2.4.x / 2.5 / 2.6 / 2.4.9.1 / 2.4.9.2 / § 3) are byte-unchanged (the line 41 row description text changes, but the type `boolean` and the rendering rule `false → "coming soon"` are byte-unchanged).
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` — read-reference only (BE-305 producer spec, already aligned). Not edited.

# Edge Cases

- **Phase 7 third dashboard (throughput) future authoring**: the line 1243-1246 blockquote should still reference *future* dashboards (throughput per ADR-MONO-017 § 3.3 #4) — the AFTER text reflects MVP + Domain Health merged, throughput remains future. (Not silent removal of the "future" framing — that would be over-correction.)
- **Phase 8 (federation hardening) future authoring**: the line 1243-1246 blockquote does not need to mention Phase 8; ADR-MONO-013 § D6 row 8 is the governing reference for Phase 8, not § 2.4.9.X. Out of scope here.
- **`tasks/done/TASK-PC-FE-011 § Honest gaps (a)` historical narrative**: line 128 references option (a) as deferred; this is historical (was true at FE-011 author time, before TASK-BE-304 + TASK-PC-FE-014 closed the chain). Per AC-6, `tasks/done/` is append-only — no fix.
- **The line 1054 reference to "future ADR-amendment"** (§ 3 parity matrix immutability): this is a *legitimate forward-looking* statement (parity matrix is locked until a future ADR amends it; no ADR currently amends it). **Not stale, no fix.**
- **`PROJECT.md` Korean narrative**: the consumer narrative may be authored in Korean (existing style); the AFTER text continues in Korean for consistency.

# Failure Scenarios

- **The impl PR also re-touches `tasks/done/*.md`** → AC-6 fail (append-only discipline violated). **Reject in review.**
- **The impl PR also re-touches `ProductCatalog.java`** → AC-4 fail (BE-305 already did this — re-touch would either re-flip (regression) or no-op (pointless)). **Reject.**
- **The impl PR also re-touches FE/BE code** (`apps/console-web/`, `apps/console-bff/`) → AC-7 fail. **Reject.**
- **The AFTER text introduces a new architectural claim** (e.g. "subsequent dashboards will use a different pattern") → over-correction; the AFTER text only updates the *factual state* (what's merged, what remains future), not the architectural intent. **Reject** if a reviewer's edit drifts into new architectural framing.
- **The AFTER text removes the "future" framing for throughput** → over-correction; ADR-MONO-017 § 3.3 #4 still pre-authorizes future throughput dashboard. Keep the "future" framing for the remaining (throughput) dashboard. **Reject** if silent removal.
- **A reviewer requests ADR amendment** → reject per § Decision authority (BE-302 / BE-305 pattern — reality-alignment with no competing convention).
- **A reviewer requests adding "deprecated" markers** → reject; the BEFORE text was a placeholder that became stale, not a public API contract that needs migration.

# Verification

1. Spec PR diff: exactly 2 files (this task md + INDEX entry). `git diff --stat origin/main -- projects/platform-console/specs/ projects/platform-console/apps/` is **empty** in spec PR.
2. Impl PR diff: exactly 2 files (`PROJECT.md` + `console-integration-contract.md`). `git diff --stat origin/main -- projects/platform-console/{apps,tasks}/` is **empty** in impl PR (no FE/BE code; no INDEX/task md — those belong to close chore).
3. Markdown fast-lane: `changes` job + `Frontend E2E smoke` pass; all Gradle / Integration / build jobs SKIPPED.
4. AC-4 / AC-5 / AC-6 / AC-7 / AC-8 grep zero.
5. Self-CI 20/20 GREEN (markdown fast-lane); BE-303 3-dim verified at close chore start.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (BE-302 / BE-305 mechanical reality-alignment, 5 surgical doc edits in 2 files; no judgement beyond the already-decided fact + the BE-305 producer-side language reuse) — or executed directly in this session given the small scope and the BE-305 spec PR as direct reference / 리뷰=Opus 4.7 (inline self-review + AC-2 surgical-diff + AC-4/5/6/7/8 scope-lock verification + BE-303 3-dim).
