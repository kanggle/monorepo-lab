# Task ID

TASK-MONO-092

# Title

ADR-MONO-012 PROPOSED — cross-project architecture.md canonical form decision (refactor-spec Tier 3 reconsider)

# Status

done

# Owner

monorepo

# Task Tags

- monorepo
- adr
- governance
- cross-project
- architecture
- refactor-spec

---

# Goal

`/refactor-spec all --dry-run` Tier 3 backlog 재고려 (직전 8-task cycle 종결 후) — cross-project architecture.md form 의 substantial divergence 가 발견. ADR-MONO-012 PROPOSED 작성 으로 governance 결정 자료 마련. ACCEPTED 후 별 multi-task migration cycle.

## Finding scope (5 distinct architecture.md formats across 5 projects)

| Project | H1 form | Service Type form | Identity table |
|---|---|---|---|
| WMS | `# <svc> — Architecture` + intro | `### Service Type Composition` (H3 sub-section in Identity table row) | ✓ 있음 |
| fan-platform | `# <svc> — Architecture` + intro | `## Service Type` (H2, separate) | ✓ 있음 |
| SCM | `# <svc> — Architecture` + intro | `## Service Type` (H2, separate) | ✗ 없음 (hybrid) |
| ecommerce | `# Service Architecture` (flat) | `## Service Type` (H2, separate) | ✗ 없음 |
| GAP | `# Service Architecture — <svc>` (flat) | `## Service Type` (H2, separate) | ✗ 없음 |

5 projects = 5 styles. Per-project intentional 또는 author origin drift unclear.

## Why ADR-level (not refactor-spec scope)

- refactor-spec Rules 명시: "No new rules or decisions / No new constraints". canonical form 선택 = new structural convention.
- Migration scope substantial (ecommerce 13 + GAP 8 + SCM 3 = ~24 architecture.md additive authoring).
- HARDSTOP-10 hook 가 WMS `### Service Type Composition` H3 형식 validation — cross-project 적용 시 hook propagation 필요.

# Scope

## In Scope

- `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` 신규 author (PROPOSED status).
- `docs/adr/INDEX.md` 에 ADR-MONO-012 entry 추가.

## Out of Scope

- ADR ACCEPTED 전환 (별 task, user 명시 결정 후).
- Multi-task migration cycle (ecommerce 13 + GAP 8 + SCM 3 architecture.md migration) — ADR ACCEPTED 후 별 task.
- HARDSTOP-10 hook cross-project propagation — migration phase 의 일부.
- production code change (전부 spec governance / authoring).

# Acceptance Criteria

- [x] `docs/adr/ADR-MONO-012-cross-project-architecture-md-canonical-form.md` 작성, Status: PROPOSED.
- [x] § Context 에 5 project × 5 format divergence catalog 명시 (§ 1.1) + 3-axis decomposition (§ 1.2) + author origin trace (§ 1.3) + HARDSTOP-10 hook implication (§ 1.4).
- [x] § Decision 에 PROPOSED canonical form 선택 (D1 WMS Identity-table + Service Type Composition) + rationale + D2-D5 (trigger / migration order / hook propagation / indefinite-PROPOSED tolerated).
- [x] § Alternatives Considered 에 Path A/B/C 비교 (WMS canonical / ecommerce flat canonical / per-project intentional).
- [x] § Consequences 에 migration cost (~24 file) + HARDSTOP-10 propagation impact + D4 OVERRIDE ADR-MONO-003a § D1.1 적용 명시.
- [x] § Outstanding follow-ups 에 multi-task post-ACCEPTED cycle outline (F-T3-A migration order SCM→GAP→ecommerce + F-T3-B hook propagation + fan partial-align + 6th project bootstrap trigger).
- [x] `docs/adr/INDEX.md` 에 ADR-MONO-012 entry 추가 (PROPOSED row, 2026-05-15).
- [x] ADR 본문 dead-ref 0 (sibling ADR-MONO-003a/009/HARDSTOP-10/refactor-spec.md/INDEX.md + 5 sample architecture.md path resolution PASS).
- [x] Production code / spec migration 0 변경 (ADR authoring only).

# Related Specs

- `docs/adr/INDEX.md` (target — entry 추가)
- `docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` (sibling PROPOSED ADR template reference)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` (D1 IN-scope / D2 OUT 분류 reference)
- `projects/wms-platform/specs/services/inventory-service/architecture.md` (WMS canonical form sample)
- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` (ecommerce flat form sample)
- `projects/global-account-platform/specs/services/auth-service/architecture.md` (GAP flat form sample)
- `projects/scm-platform/specs/services/procurement-service/architecture.md` (SCM hybrid form sample)
- `projects/fan-platform/specs/services/community-service/architecture.md` (fan WMS-emulated form sample)
- CLAUDE.md § Hard Stop Rules HARDSTOP-10 (Service Type declaration enforce)
- `.claude/commands/refactor-spec.md` § Operational Patterns (Tier 3 audit-only path)

# Related Contracts

해당 없음.

# Target Service

해당 없음 — monorepo-level governance ADR.

# Edge Cases

- A: PROPOSED status 가 indefinite 가능성 — ADR-MONO-008 (finance bootstrap) / ADR-MONO-009 (Chrome DevTools MCP) sibling 사례 답습. 사용자 명시 ACCEPTED 시점에 migration cycle 진입.
- B: canonical 선택 시 fan-platform (이미 WMS-emulated) vs SCM (hybrid) 가 cost-asymmetric — fan 은 거의 align 됨, SCM/ecommerce/GAP 은 substantial. PROPOSED 단계에서 명시.

# Failure Scenarios

- A: PROPOSED ADR 가 future audit 의 reference 가치 없음 = INDEX entry orphan. mitigate: INDEX 에 PROPOSED status 명시 + future trigger criteria (e.g. "next architecture.md author 시 form 선택 충돌").
- B: canonical 선택 후 fan-platform 처럼 partial-aligned project 의 어느 element 가 누락된 경우 — Migration Plan 에 per-project audit step 명시.

# Validation Plan

1. ADR 작성 후 `bash` link checker on ADR file + INDEX entry = 0 broken.
2. INDEX 에 ADR-MONO-012 row 위치 확인 (ADR-MONO-011 다음).
3. `git diff --stat` = 2 file / ADR 본문 ~150-200 line + INDEX 1 line.
4. ADR 의 5 sample project architecture.md cross-ref 모두 실재 path resolve.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) ADR 본문 + INDEX entry + lifecycle move ready/ → review/.
- branch name `task/mono-092-adr-architecture-md-canonical-form` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- ADR-MONO-009 (PROPOSED, gap #4 pre-author pattern) sibling 답습.
- 9th refactor-spec cycle task / Tier 3 governance escalation (refactor-spec audit-only path).

# Outcome

**Status: DONE** (PR #526 squash `ad0ed510`, draft → ready → squash-merge).

ADR-MONO-012 PROPOSED publication — refactor-spec Tier 3 governance escalation. 0 production code / 0 spec migration (post-ACCEPTED 별 task).

## ADR-MONO-012 본문 (3 file / +354 / -0)

- **Body** (~230 line): § 1 Context (5 project × 5 format catalog + 3-axis decomposition + author origin trace + HARDSTOP-10 hook implication + refactor-spec scope rationale + pre-author rationale) + § 2 Decision (D1 WMS Identity-table canonical PROPOSED, D2 user-explicit ACCEPTED trigger, D3 migration order SCM→GAP→ecommerce, D4 hook propagation, D5 indefinite-PROPOSED tolerated) + § 3 Path A/B/C weighing + § 4 Consequences per outcome + § 5 Verification (PROPOSED + post-ACCEPTED) + § 6 Outstanding follow-ups (F-T3-A migration / F-T3-B hook / fan partial-align catch-up / 6th project bootstrap trigger) + § 7 References (sibling ADRs + 5 sample architecture.md).
- **INDEX.md**: ADR-MONO-012 row added (PROPOSED status, 2026-05-15).
- **Task lifecycle**: ready → review → done.

## Status decision summary

- **D1 PROPOSED canonical** = WMS Identity-table + `### Service Type Composition` H3 sub-section.
- **D2 ACCEPTED trigger** = user-explicit (migration scope ~24 file = substantial cycle budget commitment).
- **D5 indefinite-PROPOSED tolerated** = legitimate outcome per ADR-MONO-009 pre-author pattern.

## CI

1 SUCCESS (`changes`) / 16 SKIPPED / 0 fail — markdown-only path-filter (docs/adr/ + tasks/ 영역 모두 `code-changed` filter false). mergeStateStatus CLEAN.

## Verification

- Dead-ref checker on ADR body + INDEX = 0 broken (3 sibling ADRs + HARDSTOP-10 + refactor-spec.md + 5 sample architecture.md paths 모두 resolve).
- 9th refactor-spec cycle task (BE-165/283/SCM-BE-013/BE-284/BE-285/MONO-091/BE-286/BE-287 + 본 MONO-092 = 9 task / 76 fix + 1 ADR).

## Next cycle outlook

ADR-MONO-012 ACCEPTED 시점:
- F-T3-A migration cycle: SCM (3 file) → GAP (8 file) → ecommerce (13 file) = ~3 task / ~24 file migration.
- F-T3-B HARDSTOP-10 hook propagation: hook source update + cross-project enforce surface 검증.
- fan-platform partial-align catch-up (D1 interpretation 따라 결정).

D5 outcome 시: PROPOSED 영구 유지, next architecture.md author decision reference 가치 만 유지. ADR-MONO-009 precedent.
