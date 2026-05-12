# ADR-MONO-009 — Chrome DevTools MCP Visual Regression Loop (OpenAI Harness gap #4)

**Status:** PROPOSED
**Date:** 2026-05-13
**History:** PROPOSED 2026-05-13 (TASK-MONO-072 — gap #4 scope/entry-trigger pre-authored; not the implementation authorisation).
**Decision driver:** Memory `reference_openai_harness_engineering.md` enumerates 4 OpenAI Harness gaps derived from Lopopolo 2025. Gaps A, #2, #3 are CLOSED (delivered 2026-05-12~13 via ADR-MONO-006 + ADR-MONO-007 + TASK-MONO-059/060/061/062/064/065/066/067/068). Gap #4 — Chrome DevTools MCP visual regression loop with DOM + console + screenshot triple snapshot BEFORE/AFTER LOOP UNTIL CLEAN — is the only outstanding entry. Pre-authoring this ADR documents the scope so a future "first visual regression incident" moment has a concrete plan to execute against, rather than ad-hoc authoring under decision pressure.
**Supersedes:** none.
**Related:** [ADR-MONO-006](ADR-MONO-006-lint-remediation-as-agent-context.md) (gap A precedent — Phase 1+2+3 staged delivery pattern), [ADR-MONO-007](ADR-MONO-007-worktree-ephemeral-observability-stack.md) (gap #3 precedent — opt-in lifecycle, ephemeral isolation, 5-mode failure catalog), memory [`reference_openai_harness_engineering`](../../../memory/reference_openai_harness_engineering.md) (gap source), [ADR-MONO-003a](ADR-MONO-003a-d4-override-scope-canonicalization.md) § D1.2 (OpenAI Harness gap series IN-scope of D4 OVERRIDE — gap #4 inherits authorisation without re-asking).

---

## 1. Context

### 1.1 Gap status as of 2026-05-13

| Gap | Status | Closure PRs |
|---|---|---|
| A — lint remediation message as agent context | CLOSED 2026-05-12 | #383, #386, #388 |
| #2 — doc-gardening automation | CLOSED 2026-05-12 | #393, #394 |
| #3 — worktree-ephemeral observability stack | CLOSED 2026-05-12~13 | #400, #402, #404, #406, #407, #408, #409 (+ ADR-MONO-007) |
| **#4 — Chrome DevTools MCP visual regression loop** | **OPEN (this ADR PROPOSED)** | — |

Memory `reference_openai_harness_engineering.md` describes gap #4:

> | **눈** (UI 가시성) | Chrome DevTools MCP + 워크트리별 앱 부팅 | BEFORE/AFTER 스냅샷 + console clear + LOOP UNTIL CLEAN |
>
> | **Chrome DevTools MCP 검증 루프** | Playwright e2e 만, BEFORE/AFTER 박제 X | **OPEN** — visual regression 도입 시 DOM+console+screenshot 3종 박제가 정답 |

The pattern (Lopopolo 2025): agents iterating on UI work need a feedback loop that captures DOM tree + console log + visual screenshot at both BEFORE (pre-change baseline) and AFTER (post-change current state), then loops "make change → snapshot → diff → adjust" until console has zero errors AND visual diff is intentional. This is materially stronger than Playwright's e2e assertions (which test functional behaviour but not visual appearance or console quality).

### 1.2 Why pre-author before need triggers

Same reasoning as ADR-MONO-003b § 1.2: decision-time authoring is when criteria slip and reviewer trust is lowest. The pre-author pattern that worked for Phase 5 launch should work for gap #4 implementation. PROPOSED status now = scope documented; ACCEPTED transition when a real visual-regression incident creates demand (or user-explicit chooses to invest in the harness anyway).

### 1.3 Why this might stay PROPOSED forever

The current portfolio is mostly backend services (5 of 5 active projects are Spring Boot multi-service backends; only 2 have frontends — ecommerce-web/admin and fan-platform-web). Visual regression as a class of issue requires:

1. Frontend code being actively iterated (current state: 2 frontends, both v1-stable).
2. A real visual bug shipping or nearly-shipping (none recorded in incidents/).
3. Iteration loops where the agent edits UI and needs visible feedback.

If those conditions don't materialise, this ADR stays PROPOSED indefinitely — and that's a legitimate outcome. The PROPOSED documentation alone signals the gap was recognized and decided rather than missed. Per memory `reference_openai_harness_engineering.md`, gap #4 is also the lowest-priority gap (positioned last in the priority action list).

### 1.4 Scope: what "visual regression loop" means

The triple snapshot:

| Layer | Capture | Why it matters |
|---|---|---|
| **DOM** | `document.documentElement.outerHTML` (or a normalized subset filtering ephemeral attrs like timestamps) | Structural change detection — "the div moved" / "the a11y label disappeared" |
| **Console** | All `console.log/warn/error` entries from page load to capture moment | Zero-error invariant — "no missing key warnings" / "no 404s on assets" |
| **Screenshot** | Full-page PNG at canonical viewport (e.g. 1280×720 + 1280×2000 full-page) | Pixel-level visual appearance |

BEFORE/AFTER: at task start, capture BEFORE for every page touched. After task changes, capture AFTER. Diff each layer; loop until console clean + screenshot diff is intentional (operator confirms or auto-accepts within tolerance).

---

## 2. Decision

### D1 — Triple-snapshot scope

| Layer | What's captured | Normalisation |
|---|---|---|
| **DOM** | `outerHTML` of `document.documentElement` after `DOMContentLoaded + 500ms` (settle window for hydration) | Strip: data-reactroot, `data-rsbs-*` (ephemeral animation libs), inline `style="transform: ..."` for in-flight transitions, ISO-8601 timestamps in text nodes |
| **Console** | All entries from `console.log/info/warn/error/debug` since page navigation, captured via `Page.consoleMessageAdded` MCP event | Strip: source maps file paths, line numbers (keep level + text). Group repeated entries (count). |
| **Screenshot** | Two PNGs per page: (1) above-the-fold at 1280×720, (2) full-page rolling capture (height = actual page height, capped 5000px) | Antialiasing: stable across runs; font: load-block (`document.fonts.ready`) before capture; viewport DPR=1 for determinism |

Capture timing per page: after `await page.waitForLoadState('networkidle')` + 500ms idle window. Skips dynamic content polling.

### D2 — Integration mode

#### D2.1 — Chrome DevTools MCP server

Register at `.claude/mcp/chrome-devtools.json` (matches Claude Code MCP config layout). Server runs locally via `chrome-devtools-mcp` npm package (or Anthropic-published binary at the time of ACCEPTED transition — pick what's stable). Agent invokes via tool calls like `chrome_devtools__capture_dom`, `chrome_devtools__capture_console`, `chrome_devtools__capture_screenshot`.

If Chrome DevTools MCP server is not available or not installable at ACCEPTED transition, fall back to **Playwright with explicit triple-capture** wrapped in a thin skill helper — same outputs, different tooling.

#### D2.2 — Opt-in lifecycle

Mirror ADR-MONO-007 § D3 (gap #3 observability stack) opt-in pattern:

- **Default**: off. No MCP server invocation in standard agent flows.
- **Opt-in flag** for frontend tasks: `agent: visual-regression` task tag → CLAUDE.md hook injects a session-start instruction to use the MCP snapshot tools BEFORE/AFTER each meaningful UI change.
- **Manual invocation**: agent calls the MCP tool directly when the task warrants (per agent's own judgement).

Default-off matches the "harness when needed, silent otherwise" pattern from gap #3.

#### D2.3 — Per-worktree isolation

Each worktree runs its own browser instance (Chrome DevTools MCP supports multiple sessions). Snapshots written to `tests/visual/snapshots/<worktree-hash>/...` to avoid cross-worktree contamination. Same `WORKTREE_HASH` env var as ADR-MONO-007.

### D3 — Loop semantics

#### D3.1 — BEFORE stamp moment

For a frontend task, BEFORE = the snapshot taken at task-start, AFTER any pulls/setup but BEFORE any code change. Captured for every page the task touches (declared in the task spec's Scope section as `Pages: [/login, /dashboard, ...]`).

#### D3.2 — AFTER stamp moment

After each meaningful change (e.g. after each `Edit` tool batch that touched frontend files), capture AFTER. The agent compares AFTER against BEFORE locally:

- **DOM diff** — structural diff using a library like `dom-diff` or `jsdom + custom`. Pass: no unintended structural drift. Fail: structural change not declared in task scope.
- **Console diff** — exact-text diff filtered for new errors/warnings. Pass: zero new errors. Fail: new error introduced.
- **Screenshot diff** — pixel diff via `pixelmatch` or similar. Pass: within tolerance (default 0.1% pixels different OR all diffs in declared-touched components). Fail: visual drift outside declared scope.

#### D3.3 — LOOP UNTIL CLEAN

Convergence:
- **CLEAN** = DOM pass + Console pass + Screenshot pass.
- **NOT CLEAN** = any of the three fails → agent re-edits + re-snapshots AFTER → re-diffs → loop.
- **Hard exit** after N iterations (default 8) — if not converged, surface to operator with current state + diff summary.

#### D3.4 — Acceptance moment

When CLEAN, the task is visually validated. The new AFTER snapshot becomes the next BEFORE for future tasks touching the same page. Task spec must include the snapshot file paths in its "Files Changed" section.

### D4 — Storage

#### D4.1 — In-repo committed snapshots

Snapshots committed under `tests/visual/snapshots/<page-slug>/{dom.html,console.log,viewport.png,fullpage.png}` per page per project. Reasoning:

- Pixel-diff requires a baseline reference. Without committed BEFORE, every run starts from zero history.
- Committed snapshots are reviewable in PRs (diff visible as image-diff in GitHub UI).
- Storage cost: ~100 KiB per page × 5-10 pages × 2 frontends = ~1-2 MiB total for current portfolio. Acceptable.

#### D4.2 — Retention

Snapshots rotate only when intentionally accepted. No time-based rotation. Stale snapshots (referencing removed pages) garbage-collected manually on PR cleanup.

#### D4.3 — Per-worktree ephemeral capture, then merge

During a task, AFTER snapshots are written to a per-worktree temp dir (e.g. `tests/visual/snapshots-wip-<worktree-hash>/`). On task acceptance, accepted AFTER replaces BEFORE in the committed `tests/visual/snapshots/` directory. Wip dir is gitignored.

### D5 — ACCEPTED transition criteria

PROPOSED → ACCEPTED requires ALL of:

| # | Criterion |
|---|---|
| **D5.1** | Visual regression as a class of issue has been triggered — either (a) a real visible-output bug shipped or nearly shipped in the portfolio, OR (b) user explicitly chooses to invest in the harness preemptively. |
| **D5.2** | Chrome DevTools MCP server availability confirmed (or Playwright fallback per D2.1 picked). |
| **D5.3** | At least one frontend project (ecommerce-web / fan-platform-web / future) has active iteration where the harness adds value (vs maintenance-only stability). |
| **D5.4** | User-explicit intent recorded matching one of D5.5 phrasings. |
| **D5.5** | Intent forms accepted: "ADR-009 ACCEPTED", "gap #4 시작", "visual regression harness 도입". |

If D5.1 never fires, ADR-009 stays PROPOSED forever — and that's the correct outcome.

---

## 3. Consequences

### 3.1 PROPOSED merge (this PR)

- Memory `reference_openai_harness_engineering.md` gap #4 row updates from OPEN to PROPOSED-via-ADR-009.
- 4/4 OpenAI Harness gaps now formally addressed (3 CLOSED + 1 PROPOSED). Portfolio narrative completion.
- No actual MCP server registration. No snapshot directory. No skill authoring.

### 3.2 ACCEPTED moment (future, when D5 triggers)

- Phase 1 (mechanical): MCP server config + skill scaffolding + opt-in Gradle flag.
- Phase 2 (integration): first BEFORE snapshot captured for ecommerce-web + fan-platform-web critical pages.
- Phase 3 (loop): first task uses the harness end-to-end.

### 3.3 First-use moment

- Pixel tolerance calibrated against real diffs (typically 0.1% is too tight for first runs; 1-2% more realistic).
- DOM normalisation list expanded as ephemeral attrs surface.
- Console error allowlist authored (e.g. third-party SDKs that warn-by-design).

### 3.4 Future-self / future-LLM-session

- A session prompted to "do frontend work, capture visual regression" reads this ADR § D1–D3 before mutating.
- A session asking "is there a visual harness?" reads § Status — answer is no until ACCEPTED, planned but unused.

---

## 4. Alternatives Considered

### 4.1 Playwright-only (status quo)

Playwright tests already run in `frontend-e2e-smoke` CI job. They cover behavioural assertions (`click X, expect Y to be visible`) but not:
- Pixel-level visual diff.
- Console-error invariant (Playwright would have to manually instrument console listeners).
- DOM snapshot history.

Rejected as insufficient: behaviour-only assertions miss "the button moved 10px to the left" or "we added a warning to console for every page load". Per Lopopolo 2025 pattern, agent-driven UI work needs the wider feedback surface.

### 4.2 Percy / Chromatic / Reg Suit SaaS

Visual regression services that host snapshots externally and provide pixel-diff UI.

Rejected: external service dependency for a portfolio repo. Cost (Percy: $149+/mo for 10k snapshots). Lock-in. The OpenAI Harness pattern explicitly wants the harness *in the codebase* — "Encode into codebase as markdown" per memory `reference_openai_harness_engineering`.

### 4.3 Implement now, skip PROPOSED stage

Considered: ACCEPTED immediately and ship Phase 1 mechanical scaffolding.

Rejected: D5.1 trigger condition not yet satisfied (no visual regression incident in portfolio). Implementing now = pre-investment without immediate payback. The PROPOSED-status documentation alone signals the gap was recognized; the actual harness can wait.

### 4.4 Defer entirely (don't author ADR)

Considered: skip ADR-009, just leave gap #4 as OPEN in memory.

Rejected: same reasoning as ADR-MONO-003b § 4.2. Decision-time authoring is when criteria slip. Pre-authoring is cheap; deferring decision quality is expensive.

---

## 5. Relationship to ADR-MONO-006 and ADR-MONO-007

| Aspect | ADR-MONO-006 (gap A) | ADR-MONO-007 (gap #3) | ADR-MONO-009 (gap #4, this ADR) |
|---|---|---|---|
| Status | ACCEPTED 2026-05-12 | ACCEPTED 2026-05-12 | PROPOSED 2026-05-13 |
| Delivery pattern | Phase 1+2+3 mechanical | Phase 0+1+2+3 (policy → scaffolding → skill+gradle → coverage+CI) | TBD at ACCEPTED — likely 3-phase: scaffolding → first use → expansion |
| Opt-in mechanism | Always-on (hook injection) | Opt-in (`-Pobservability=on`) | Opt-in (default off; agent task-tag opt-in) |
| Per-worktree isolation | n/a (universal) | Yes (WORKTREE_HASH compose project) | Yes (WORKTREE_HASH snapshot dir) |
| Failure catalog | 4-block remediation per hook | OBSERVE-QUERY-NN 5-mode catalog | TBD at ACCEPTED |

**Practical reading order for a new session evaluating "should we implement gap #4 now?"**:

1. Read this ADR § D5 — entry criteria.
2. Check current portfolio state — does a frontend project have active iteration with visual concerns?
3. If yes → user-explicit intent + ACCEPTED transition + Phase 1 scaffolding.
4. If no → ADR-009 stays PROPOSED.

---

## 6. Status Transition History

Append-only.

| Date | Transition | D5.1 trigger source | User intent quote | PR |
|---|---|---|---|---|
| 2026-05-13 | created PROPOSED | n/a (pre-author) | n/a | TBD (this PR) |

(ACCEPTED row reserved.)

---

## 7. Provenance

- Memory `reference_openai_harness_engineering.md` — original gap source (Lopopolo 2025 reference). Gap #4 description anchored at "Chrome DevTools MCP 검증 루프 / DOM+console+screenshot 3종 박제".
- ADR-MONO-006 — gap A delivery (closes 1 of 4 harness gaps).
- ADR-MONO-007 — gap #3 delivery (closes 2 of 4); also establishes the per-worktree ephemeral pattern this ADR D2.3 borrows.
- TASK-MONO-072 spec — pre-launch documentation argument.
- ADR-MONO-003a § D1.2 — OpenAI Harness gap series IN-scope of D4 OVERRIDE; gap #4 inherits authorisation. No fresh OVERRIDE-permission ADR needed.

분석=Opus 4.7 / 구현=Opus 4.7 (meta-policy authoring — D1 layer decomposition + D3 loop semantics + D5 entry trigger calibration require interpretive judgement).
