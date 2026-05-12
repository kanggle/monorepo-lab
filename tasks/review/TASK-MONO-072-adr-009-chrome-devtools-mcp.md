# Task ID

TASK-MONO-072

# Title

Publish ADR-MONO-009 (PROPOSED) — Chrome DevTools MCP visual regression loop (OpenAI Harness gap #4)

# Status

review

# Owner

monorepo

# Task Tags

- spec
- adr
- harness-engineering
- visual-regression

---

# Goal

Author `ADR-MONO-009` in **PROPOSED** status to define the scope, integration mode, and entry criteria for the **last remaining OpenAI Harness gap (#4)** from memory `reference_openai_harness_engineering.md` — visual regression checking via Chrome DevTools MCP, with DOM + console + screenshot triple-snapshot BEFORE/AFTER LOOP UNTIL CLEAN.

Per memory `reference_openai_harness_engineering.md`:

| Gap | Lopopolo 2025 pattern | monorepo-lab status |
|---|---|---|
| **눈** (UI 가시성) | Chrome DevTools MCP + 워크트리별 앱 부팅 | BEFORE/AFTER 스냅샷 + console clear + LOOP UNTIL CLEAN |
| **Chrome DevTools MCP 검증 루프** | Playwright e2e 만, BEFORE/AFTER 박제 X | **OPEN** — visual regression 도입 시 DOM+console+screenshot 3종 박제가 정답 |

Gaps A, #2, #3 are all CLOSED (delivered 2026-05-12~13). Gap #4 is the only outstanding harness engineering gap. PROPOSED status here = scope + criteria documented; actual implementation awaits user-explicit ACCEPTED transition AND visual-regression need triggering (the memory notes "visual regression 도입 시" — entry condition is the introduction of visual regression as a class of issue, not yet present in the portfolio).

---

# Scope

## In Scope

### A. New ADR file

`docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` — Status: **PROPOSED** 2026-05-13.

Required sections:

- **Context** — gap #4 source (memory `reference_openai_harness_engineering`), why this is the last gap (A/#2/#3 closed in 2026-05-12~13), why visual regression is a real class of issue worth a harness for, why pre-author criteria before need triggers.
- **Decision** — five sub-decisions:
  - **D1** — Scope (DOM + console + screenshot triple snapshot — what each captures, what they snapshot).
  - **D2** — Integration mode (Chrome DevTools MCP server registration, where it lives in `.claude/`, opt-in vs always-on).
  - **D3** — Loop semantics (BEFORE/AFTER stamping per task, LOOP UNTIL CLEAN convergence definition, exit criteria).
  - **D4** — Storage (snapshots committed to repo vs ephemeral, retention policy).
  - **D5** — ACCEPTED transition criteria (entry condition for actually implementing).
- **Consequences** — what changes at PROPOSED merge vs ACCEPTED transition vs first use.
- **Alternatives Considered** — at minimum: (i) Playwright-only (status quo, rejected: no DOM/console history), (ii) Percy/Chromatic SaaS (rejected: external service dependency for portfolio repo), (iii) full implementation now (rejected: no triggering need).
- **Relationship to ADR-MONO-006 + 007** — gap series sibling.
- **Status transition history** — empty placeholder for ACCEPTED row.

### B. Cross-reference updates

- Memory `reference_openai_harness_engineering.md` — update gap #4 status from OPEN to "PROPOSED via ADR-MONO-009 (2026-05-13, TASK-MONO-072)".

### C. No other changes

- No `.claude/mcp/` server registration (deferred to ACCEPTED transition).
- No `.claude/skills/` visual-regression skill authoring (deferred).
- No Playwright config change (deferred).
- No snapshot directory creation (deferred).

## Out of Scope

- ACCEPTED transition of ADR-MONO-009 (PROPOSED only).
- Actual Chrome DevTools MCP server integration. Gated on ACCEPTED.
- Authoring a visual-regression skill at `.claude/skills/cross-cutting/visual-regression/SKILL.md`. Deferred.
- Integration into existing E2E suites (`projects/wms-platform/apps/gateway-service/src/e2eTest/...` etc). Deferred.
- A first BEFORE/AFTER snapshot stamping run. Deferred.

---

# Acceptance Criteria

- [ ] `docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` exists with `Status: PROPOSED`, dated 2026-05-13.
- [ ] ADR-009 contains: Context / Decision (D1–D5) / Consequences / Alternatives Considered (≥3) / Relationship to ADR-MONO-006+007 / Status transition history section (empty placeholder).
- [ ] **D1 (Scope)** explicitly enumerates what DOM / console / screenshot each capture.
- [ ] **D2 (Integration)** records MCP server placement (e.g. `.claude/mcp/chrome-devtools.json`) and opt-in lifecycle (Gradle `-PvisualRegression=on` or equivalent).
- [ ] **D3 (Loop)** defines BEFORE/AFTER stamp moment and LOOP UNTIL CLEAN convergence rule.
- [ ] **D4 (Storage)** picks: in-repo committed snapshots (e.g. `tests/visual/snapshots/`) vs ephemeral artifact-only.
- [ ] **D5 (ACCEPTED criteria)** explicitly states the entry trigger (e.g. "first visual regression incident occurs in portfolio code" or "user explicitly initiates").
- [ ] Memory `reference_openai_harness_engineering.md` gap #4 row updated to reference ADR-MONO-009 PROPOSED.
- [ ] No service code touched. No `libs/` / `apps/` / `projects/` diff.
- [ ] CI green (path-filter `docs(adr)` flag only).

# Related Specs

- `docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md` (gap A precedent — staged Phase 1+2+3 pattern)
- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` (gap #3 precedent — opt-in lifecycle pattern, ephemeral isolation)
- Memory `reference_openai_harness_engineering.md` (gap series origin)
- Per ADR-MONO-003a § D1.2 — OpenAI Harness gap series is pre-authorised D4 OVERRIDE scope; gap #4 inherits that authorisation without re-asking.

# Related Contracts

None — meta-policy ADR. No HTTP / event contract change.

# Edge Cases

- **Chrome DevTools MCP server availability** — MCP is an experimental Claude Code feature surface. ADR-009 D2 may need to record fallback (e.g. Playwright `--video=on` + `--screenshot=on` if MCP not installable). PROPOSED records the ideal target; ACCEPTED transition picks the actual mechanism based on Claude Code support state at that moment.
- **Visual regression class not yet present** — D5 notes that visual regression needs to be introduced as a real concern before this harness pays back. Without a real visual bug to catch, the harness is overhead. The entry trigger waits for at least one visible-output incident.
- **Snapshot storage cost** — full-page screenshots can be ~100 KiB each. A growing repo grows monotonically. D4 may pick artifact-only storage to avoid repo bloat (but then no BEFORE history); or in-repo with rotation policy.
- **Cross-platform pixel drift** — different OS / browser versions produce slightly different screenshots. Standard solution: pixel-diff tolerance threshold. ADR-009 may defer to ACCEPTED transition for tolerance picking.

# Failure Scenarios

- **Reviewer asks "is gap #4 really needed?"** — answer: it's the last harness engineering gap from memory; closing it completes the portfolio's "agent-first operational discipline" narrative. Even if never triggered, the PROPOSED-status documentation alone signals the gap was recognized and decided rather than missed.
- **Reviewer asks "PROPOSED forever?"** — possible. PROPOSED ADR-009 staying open indefinitely is a legitimate outcome — it means visual regression was never a problem for this portfolio. The PROPOSED → ACCEPTED transition only fires on real need.
- **Reviewer asks "is this OVERRIDE-class?"** — yes, per ADR-MONO-003a § D1.2 (OpenAI Harness gap series). gap #4 inherits OVERRIDE authorisation; no fresh ADR for permission needed (only for content decisions).

---

# Implementation Plan

1. Author `docs/adr/ADR-MONO-009-chrome-devtools-mcp-visual-regression.md` per the structure above (Status: PROPOSED).
2. Update memory `reference_openai_harness_engineering.md` gap #4 row.
3. Single bundled commit.
4. Lifecycle: ready → review on PR creation.
5. Push branch + open PR.
6. After merge: close chore (review → done).

# Estimated Cost

- Files: ADR-009 new (~200 LOC) + memory (~3 LOC) + this task file. Total ≈ 230 LOC.
- CI: path-filter `docs(adr)` flag only → ~20s baseline.
- Time: ~1 hour authoring + commit/push.

분석=Opus 4.7 / 구현 권장=Opus 4.7 (meta-policy authoring — D1 scope decomposition + D5 entry trigger require interpretive judgement).
