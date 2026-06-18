# TASK-PC-FE-114 — Add a root `global-error.tsx` so cold-deploy client exceptions don't blank the shell

**Status:** done

**Type:** TASK-PC-FE (project-internal — `projects/platform-console/apps/console-web/src/app/` only)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (single additive client component; no domain logic)

---

## Goal

An operator logging into the console immediately after a stack redeploy saw the first screen blank-crash with the bare Next.js message **"Application error: a client-side exception has occurred"**. Live investigation (federation demo stack) traced both reported symptoms to **cold backends + the console's 5s per-leg timeouts**: the OIDC operator-token exchange + active-tenant assume-tenant flow (both keyed on `TOKEN_EXCHANGE_TIMEOUT_MS`) blew the 5s budget (56s first login), and the cold/slow `console-web` failed to serve a JS chunk / RSC payload mid-navigation (`Failed to fetch RSC payload`, `_rsc … ERR_ABORTED`, `ERR_CONNECTION_RESET` observed). The resulting client-side exception had **no boundary to catch it**.

Root cause for the blank crash specifically: the app has `(console)/error.tsx` but **no root `app/global-error.tsx`**. By App Router design, `(console)/error.tsx` catches errors only in the console segment's *children* — NOT its sibling `(console)/layout.tsx` (TenantSwitcher / NotificationBell / catalog fetch), NOT the root layout, and NOT client-side `ChunkLoadError` / RSC-fetch failures. All of those bubble to the root, where the absence of a boundary yields the bare default string.

The login-latency half is mitigated operationally (demo `zz-console-web-timeout-override.yml` bumps `TOKEN_EXCHANGE_TIMEOUT_MS` + `REGISTRY_TIMEOUT_MS` 5s→30s). This task closes the durable code gap: a root boundary so any unhandled error degrades to a friendly, recoverable panel instead of a blank crash.

## Scope

**In scope** — `apps/console-web/src/app/` only:

1. Add `app/global-error.tsx` (`'use client'`, renders its own `<html>`/`<body>` as App Router requires): friendly Korean alert + `reset()` + manual reload affordance, matching the existing `(console)/error.tsx` copy/posture.
2. One-shot auto-reload on `ChunkLoadError` (name match + "Loading chunk … failed" / "failed to fetch dynamically imported module" message match), guarded by a `sessionStorage` flag so a genuinely-broken build cannot reload-loop.
3. Structured `console.error` log (`msg: 'global_render_error'`, `name`, `digest`, `chunk`) consistent with the `console_render_error` line in `(console)/error.tsx`.

**Out of scope:** the demo timeout override (already applied, root-scoped, intentionally uncommitted); any change to `(console)/error.tsx`, the composition/degrade render paths (verified already graceful — all-degraded cards render as `degraded`/`forbidden`/`unknown` placeholders, no crash), or backend timeout constants.

## Acceptance Criteria

- **AC-1** — `app/global-error.tsx` exists, is a client component, renders `<html><body>` (global-error replaces the document), and shows a friendly recoverable panel (NOT the bare Next.js string) for any error that bubbles to root.
- **AC-2** — a `ChunkLoadError` (stale-chunk after redeploy) triggers exactly **one** automatic `window.location.reload()`, gated by a `sessionStorage` one-shot flag (no reload loop on a persistently-broken build).
- **AC-3** — non-chunk errors do NOT auto-reload; they render the panel with working `다시 시도` (`reset()`) and `새로고침` buttons.
- **AC-4** — `pnpm lint` + `tsc` + `vitest` (console-web) green before push (memory `env_console_web_local_verify_needs_lint`); production `next build` succeeds.
- **AC-5 (live, optional)** — rebuilt `console-web` image deployed to the federation demo stack; forcing a root-bubbled error shows the friendly panel, and a simulated stale-chunk reloads once. (Live verify per `env_console_demo_local_redeploy`.)

## Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Server vs Client Components, § Error handling / resilience (integration-heavy posture — degraded paths must not blank the shell).
- project memory: `env_console_demo_local_redeploy` (redeploy + live-verify recipe), `env_console_web_local_verify_needs_lint` (3-tool pre-push gate).

## Related Contracts

None — no API/event surface; purely a client render boundary.

## Edge Cases

- **`global-error.tsx` must render `<html>`/`<body>`** — it replaces the root layout on error; omitting them yields an invalid document. (Distinct from `(console)/error.tsx`, which renders inside the existing shell.)
- **Reload-loop guard** — a persistently-failing build must NOT reload forever; the `sessionStorage` one-shot flag caps it at a single auto-reload, then the manual panel stands.
- **Boundary scope** — `(console)/error.tsx` still owns in-shell page errors (keeps the topbar/sidebar); `global-error.tsx` is the last-resort root catch only. Both coexist.

## Failure Scenarios

- Adding `app/error.tsx` instead of `global-error.tsx` → still won't catch `(console)/layout.tsx` or root-layout throws, nor chunk errors that abort the whole document → symptom persists.
- Auto-reloading without the one-shot guard → infinite reload loop when the build is genuinely broken (not a stale chunk).
- Forgetting `<html>`/`<body>` in `global-error.tsx` → hydration/render error in the boundary itself → back to the bare Next.js default.
