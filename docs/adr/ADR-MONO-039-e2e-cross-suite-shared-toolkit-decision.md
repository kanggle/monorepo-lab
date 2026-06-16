# ADR-MONO-039 — Do NOT build a shared `e2e-toolkit` package; keep the three Playwright suites independent and govern the small cross-suite overlap by a documented login convention

**Status:** ACCEPTED

**History:** PROPOSED 2026-06-16 (TASK-MONO-290 — records the **cross-suite e2e tooling-ownership decision**: whether the OIDC-PKCE login flow + dev credential + Playwright config presets duplicated across the three sibling suites [`tests/federation-hardening-e2e`, `console-web`, `web-store`] should be lifted into a shared package. The duplication is small but spans three **separate pnpm packages** across the root↔`projects/**` boundary, so unifying it is a structural decision — a package that did it without a record would silently bake a workspace-topology + boundary-crossing posture → HARDSTOP-09. This ADR records the four decisions [C1–C4]. **Doc-only; ACCEPTED is a separate user-explicit-intent gate [staged-child pattern, ADR-019/020/021/023/024/032/033/034/035/036/037/038]. Self-ACCEPT prohibited.** Direction CHOSEN-PROPOSED = **Option C** [no package; document the convention], selected by the user via the TASK-MONO-290 direction question.) · ACCEPTED 2026-06-16 (TASK-MONO-290 — user-explicit *"accept"* after the PROPOSED C1–C4 were presented for the explicitly-required ACCEPT gate; the gate was honored — the PROPOSED record was presented and review awaited before any flip, **NOT a self-ACCEPT**. C1–C4 CHOSEN-PROPOSED direction **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical to the PROPOSED draft; flip = Status + this clause + § 6 ACCEPTED row. Delivered in the same PR as the PROPOSED record [the staged-child governance trail is preserved *within* the PR: both § 6 rows], mirroring ADR-033/034/035/036/037/038.)

**Builds on:** TASK-MONO-280 (2026-06-16, PR #1755) — extracted the byte-identical **intra-suite** helpers in `tests/federation-hardening-e2e/` (`operatorToken`/`headers`/`codeOf`/`send`/`warmUpAdminOutbox`/`gotoOverview`/`switchTenant`) into two in-package fixtures. That work was confined to one package; the **inter-suite** layer it surfaced is what this ADR decides.

**Related:**
- The three suites: `tests/federation-hardening-e2e/` (root, shared), `projects/platform-console/apps/console-web/tests/e2e/`, `projects/ecommerce-microservices-platform/apps/web-store/e2e/` — each an independent pnpm package with its own `playwright.config.ts` + `pnpm-lock.yaml`.
- `CLAUDE.md` § Shared vs project boundary — root `tests/` is shared/project-agnostic; `projects/**` is project-specific. A relative-import source-share would cross this boundary.
- `platform/shared-library-policy.md` — the project-agnostic bar any new `libs/` package must clear.
- Follow-up tasks gated on this decision: TASK-FE-074 (web-store auth-path cleanup), TASK-PC-FE-113 (console demo-script promotion).
- Project memory `env_worktree_node_modules_junction_cleanup_hazard` — the Windows `node_modules` junction cost a shared workspace package would impose on worktree-isolated sessions.

---

## 1. Context

### 1.1 The cross-suite overlap (code-verified 2026-06-16)

Three Playwright suites each drive a GAP OIDC-PKCE login against the same `auth-service` Spring-Security default `/login` form. The genuinely-overlapping surface is small and stable:

```
 SURFACE                       federation-e2e        console-web           web-store
 OIDC-PKCE login flow          driveOidcPkceLogin    driveOidcPkceLogin    fillGapCredentialForm
   form selector               input[name="username"] input[name="username"] #username  (same inputs)
   landing predicate (positive) startsWith('/dashboards') startsWith('/dashboards') !startsWith('/login') (storefront root)
 dev credential                devpassword123!       devpassword123!       devpassword123!
   + Argon2id seed hash        (GAP V0014 dev seed)  (same)                (same)
 config preset shape           CONSOLE_BASE_URL...   CONSOLE_BASE_URL...   PLAYWRIGHT_BASE_URL...
```

The overlap is **~40 lines of login mechanics + one credential constant + a config preset shape**. It has **already diverged** in two benign ways (selector style `input[name=]` vs `#username`; landing predicate positive vs negative) — both *legitimate per-app differences* (web-store lands on a storefront, not a `/dashboards` console).

### 1.2 Why this is a decision, not a refactor (HARDSTOP-09)

The three suites are **three separate pnpm packages** with independent `pnpm-lock.yaml` files, two of them inside `projects/**` (project-specific) and one at root `tests/` (shared). There is **no import path between them today**. De-duplicating therefore cannot be a mechanical extraction — it requires either (A) joining them into a pnpm workspace around a new shared package, or (B) a root-level source dir imported across the `projects/**` ↔ root boundary. Both bake a structural posture (workspace topology / boundary-crossing). Recording the choice is required before any such change.

### 1.3 The portfolio lens

This is a portfolio monorepo; a clean shared `e2e-toolkit` would have demonstration value. Weighed against: the ~40-line surface, the documented Windows junction/`node_modules` hazard for worktree-isolated sessions, the new publish/build surface, and the CLAUDE.md boundary. The judgement call is whether demonstration value justifies the structural cost at this surface size.

---

## 2. Decision

> Direction is **CHOSEN-PROPOSED = Option C**, selected by the user (TASK-MONO-290 direction question, 2026-06-16); to be finalised (byte-unchanged) at ACCEPTED per the staged-child pattern. **No package / workspace / import change in this ADR.**

### C1 — No shared `e2e-toolkit` package; the three suites stay independent

The cross-suite login flow, dev credential, and config preset are **not** lifted into a shared package or a shared source dir. Each suite keeps owning its own login fixture (`fixtures/login.ts` / `helpers/auth.ts`). At a ~40-line surface across three independently-runnable, separately-locked packages spanning the root↔project boundary, the structural cost of unification (pnpm-workspace integration, a new publish surface, the Windows junction hazard, the boundary crossing) **exceeds** the dedup benefit. YAGNI; the package boundary is intentional, not accidental.

### C2 — Govern the overlap by a documented canonical login convention

So that divergence stays *deliberate* rather than *drift*, the convention each suite's login fixture follows is recorded here and pointed to by a one-line comment in each fixture:

- **Credential** — `devpassword123!` with the GAP V0014 dev-seed Argon2id hash (the single shared dev-seed value). Rotating it is a coordinated change across the suites' seed SQL + fixtures (no single source — accepted, see C3).
- **Landing predicate** — console-targeting suites (`federation-e2e`, `console-web`) use the **positive** `url.pathname.startsWith('/dashboards')`; `web-store` (storefront) uses `!url.pathname.startsWith('/login')` because it lands on the store root, not a console. **This difference is intentional and correct**, not drift.
- **Form selector** — all three target the same Spring-Security `DefaultLoginPageGeneratingFilter` inputs; `input[name="username"]` (canonical) and `#username` (web-store) are equivalent. New suites SHOULD use the `input[name="…"]` form.
- **Config env var** — `CONSOLE_BASE_URL` for console suites, `PLAYWRIGHT_BASE_URL` for web-store — **per-app, intentional** (different apps, different base URLs); not to be "aligned."

### C3 — Drift posture: accept, with an optional lightweight guard, and explicit revisit triggers

No shared code means drift is *possible*; the C2 convention + PR review is the primary guard. An optional CI grep-check asserting the `devpassword123!` constant is byte-identical across the three fixtures is **DEFERRED** (not built unless warranted). **Revisit Option A (shared package) when EITHER:** (a) the shared surface grows beyond login + credential + config — e.g. shared page-objects or multi-step cross-domain flows appear in ≥2 suites; OR (b) a second *drift-caused* test failure is recorded. Until a trigger fires, C1 stands.

### C4 — The follow-up tasks are NOT gated on a shared package

TASK-FE-074 (web-store dead GAP-signup auth-path cleanup) and TASK-PC-FE-113 (console `verify-*.mjs` demo-script promotion) proceed **independently**, each using its own project's login fixture per the C2 convention. The diagnosis's "shared-toolkit is the gate for #4/#5" framing is **dissolved** by this decision.

---

## 3. Consequences

- **Positive** — zero structural change; the CLAUDE.md shared/project boundary is respected; each suite stays independently installable, runnable, and deployable; no Windows `node_modules` junction risk introduced; MONO-280's intra-suite dedup stands undisturbed.
- **Negative** — the ~40-line login flow + credential stay duplicated across three fixtures; drift-prevention relies on the C2 documented convention + reviewer discipline (not a compiler/import guarantee). The optional grep-guard (C3) is the escape hatch if that proves insufficient.
- **Neutral** — `e2e-toolkit` is not added to `libs/`; the portfolio forgoes a shared-e2e-tooling showcase in favour of boundary-clean independent suites (itself a defensible architecture posture to demonstrate).

## 4. Alternatives considered

- **Option A — `libs/e2e-toolkit` pnpm workspace package.** Real, drift-proof code sharing + portfolio demonstration value. Rejected at the current surface size: requires folding three independently-locked packages (two inside `projects/**`) into a shared workspace, a new publish/build surface, and accepting the documented Windows junction hazard for worktree-isolated sessions — cost > benefit for ~40 lines. Re-opened by a C3 revisit trigger.
- **Option B — root `tests/_shared/` source dir, relative/path-mapped import.** Lighter than A (no package), but `projects/**` e2e importing from root `tests/` **crosses the CLAUDE.md shared/project boundary** and complicates each suite's `tsconfig`/`testDir`. Rejected.

## 5. Relationship to prior ADRs

Mirrors the **staged-child governance** of ADR-019/020/021/023/024/032/033/034/035/036/037/038: a PROPOSED record presented for an explicit, user-gated ACCEPT (self-ACCEPT prohibited). Unlike ADR-038 (which lifted shared machinery into `libs/`), ADR-039 records a **decline-to-share** posture — equally a decision worth a record, because doing it silently (either way) bakes a structural stance.

## 6. Decision log

| Date | Status | Task | Note |
|---|---|---|---|
| 2026-06-16 | PROPOSED | TASK-MONO-290 | C1–C4 recorded; direction CHOSEN-PROPOSED = Option C (user direction-question). Awaiting explicit ACCEPT gate (self-ACCEPT prohibited). |
| 2026-06-16 | ACCEPTED | TASK-MONO-290 | User-explicit *"accept"*. C1–C4 finalised byte-unchanged (no re-decide); flip = Status + History clause + this row. Delivered in the same PR (#1757). |

## 7. Provenance

Grounded in the 2026-06-16 cross-suite e2e diagnosis (three suites read; duplication inventory: OIDC-PKCE login flow, `devpassword123!` + Argon2id hash, config presets; the two benign divergences) and the TASK-MONO-280 intra-suite extraction (PR #1755). No code, package, or config changed by this ADR.
