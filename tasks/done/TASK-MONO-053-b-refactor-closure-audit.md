# Task ID

TASK-MONO-053

# Title

B (common-rule refactor) closure audit — candidates #4 + #5 (skills/INDEX + BaseEventPublisher javadoc)

# Status

done

# Owner

backend / monorepo

# Task Tags

- audit
- closure
- spec

---

# Goal

Close the two remaining low-cost candidates in memory
`project_b_common_rule_refactor_pending.md`:

- **#4** — `.claude/skills/INDEX.md` 정합화: post-2026-05-04 (TASK-MONO-032) drift 점검 + orphan reference + 도메인 카운트 검증
- **#5** — `libs/java-messaging/.../BaseEventPublisher.java` javadoc cleanup (`"account and admin publishers"` 잔재 reference 제거 / project-agnostic 화)

Both were flagged as "trivial / low cost" in the candidate roster. This audit confirms whether work is still needed.

---

# Audit Findings

## Candidate #4: `.claude/skills/INDEX.md` ↔ filesystem drift

**Method:**

```bash
find .claude/skills -name "SKILL.md" | sort   # 71 files (excluding domain/README.md)
grep '`[^`]*/SKILL.md`' .claude/skills/INDEX.md | wc -l   # 72 catalog entries
```

INDEX has **72 entries** (one for each SKILL.md file, plus `review-checklist/SKILL.md` listed once).

**Cross-check pass per group:**

| Group | INDEX entries | Filesystem files | Status |
|---|---|---|---|
| backend | 22 | 22 | ✓ |
| messaging | 4 | 4 | ✓ |
| database | 4 | 4 | ✓ |
| frontend | 12 | 12 | ✓ |
| search | 3 | 3 | ✓ |
| testing | 5 | 5 | ✓ |
| infra | 8 | 8 | ✓ |
| cross-cutting | 5 | 5 | ✓ |
| service-types | 8 | 8 | ✓ |
| review-checklist | 1 | 1 | ✓ |
| **Total** | **72** | **72** | **✓ exact match** |

Notes:

- `domain/README.md` (no SKILL.md inside) intentional per the file's own opening: "Currently empty — first-project's domain (ecommerce) is served by the existing technology-axis skill tree." Not an INDEX entry needed.
- Memory's reference to `audit-memory` as a "신규 skill" was imprecise — `audit-memory` lives in `.claude/commands/` (slash-command catalog, separate registry), not `.claude/skills/`.

**Conclusion #4:** **RESOLVED — no drift.** INDEX is perfectly synced. No PR action needed.

## Candidate #5: `BaseEventPublisher.java` javadoc cleanup

**Method:**

```bash
grep -rn 'account.*admin\|account and admin\|admin and account' libs/java-messaging   # 0 matches
grep -in 'account\|admin' libs/java-messaging/src/main/**/*.java                       # 0 matches (production)
grep -in 'account\|admin' libs/java-messaging/src/test/**/*.java                       # 12 matches — test fixtures
```

**Findings:**

- Production code: zero references to `account` or `admin` in javadoc or comments. The "account and admin publishers" leak the memory candidate referenced has already been cleaned up (likely during TASK-MONO-049 production-code commits — the surviving reference noted in ADR-MONO-004 § 6 follow-up #5 was specifically the javadoc, and the current javadoc is project-agnostic).
- Test code: `BaseEventPublisherTest` uses `"Account"` as a generic aggregate-type fixture (alongside `"Order"`, `"Inventory"`-style test cases) and `OutboxMetricsAutoConfigurationTest` uses `"account-service"` / `"admin-service"` as parameterized examples for metric-prefix derivation logic. Both are valid project-agnostic test fixtures — the names exemplify the metric-prefix derivation pattern (strip `-service` suffix, replace `-` with `_`), they do not specialize the lib to a domain.

**Conclusion #5:** **RESOLVED — no cleanup needed.** Production javadoc is clean; test fixtures are intentional, project-agnostic naming.

---

# Outcome

Both candidates are **closed without code change**. Memory
`project_b_common_rule_refactor_pending.md` should mark #4 and #5 as
"resolved 2026-05-11 (no drift / no cleanup needed)".

Remaining B refactor candidates:

- **#1** — `CLAUDE.md` 분리 (OpenAI Harness Engineering 패턴 적용). High cost, user-review-required. Pending user direction.
- **#2** — `platform/error-handling.md` catalog audit (Wave 2 = wms domain, **closed by TASK-MONO-051**). Wave 3 (ecommerce/scm/GAP/fan-platform) candidate filed as TASK-MONO-052 follow-up note. Pending user direction.
- **#3** — `rules/` audit 재진입 (taxonomy / activation-rules / domain catalog). Partial — PR #328 sweep + TASK-MONO-051 already touched the domain table. Full sweep pending user direction.

---

# Scope

## In Scope

- This document (audit findings + closure of #4 + #5).
- root `tasks/INDEX.md` done section entry.

## Out of Scope

- Code changes — none needed.
- Test changes — none needed.
- Candidate #1 / #3 — separate tasks, pending user direction.

---

# Acceptance Criteria

- [x] Candidate #4 audit performed (71/72 group counts cross-checked).
- [x] Candidate #5 audit performed (production + test code grep).
- [x] Both candidates confirmed RESOLVED with no required action.
- [x] Audit findings documented in this task md.
- [x] `tasks/INDEX.md` done entry added.

---

# Related Specs

- `.claude/skills/INDEX.md` (target — verified)
- `libs/java-messaging/src/main/java/com/example/messaging/event/BaseEventPublisher.java` (target — verified)
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 6 follow-up #5 (origin of candidate #5)
- Memory `project_b_common_rule_refactor_pending.md` (candidate roster)
- ADR-MONO-003 § 3.4 risk 2 (D4 OVERRIDE rationale — applies but no actual churn this PR)

---

# Test Requirements

N/A — audit + documentation only.

---

# Implementation Notes

- Place this task directly under `tasks/done/` because the work product
  **is** the audit document itself; there is no implementation phase.
- INDEX.md entry mirrors the recent TASK-MONO-051 outcome-style row.
- Single PR per memory `feedback_pr_bundling.md` (small audit closure).

---

# Provenance

memory `project_b_common_rule_refactor_pending.md` candidates #4 + #5.
Filed and closed in the same PR per the audit-only nature of the work.

분석=Opus 4.7 / 구현=Sonnet 4.6 (mechanical audit — grep + cross-check + doc).
