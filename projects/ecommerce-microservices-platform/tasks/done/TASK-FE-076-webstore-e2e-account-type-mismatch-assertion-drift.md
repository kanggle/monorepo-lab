# TASK-FE-076 — Fix stale `account_type_mismatch` e2e assertion (`'admin'`) breaking nightly web-store full-stack

**Status:** done

**Type:** TASK-FE (project-internal — `projects/ecommerce-microservices-platform/apps/web-store/e2e/` only)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (single-line e2e assertion fix; verify against the rendered LoginForm copy)

---

## Goal

The `Nightly E2E` workflow's **Frontend E2E full-stack (web-store, Playwright + docker compose)** job has been RED on the last several push-to-main runs (e.g. run 27757274795 @ `074adc8`). The sole failing test is
`e2e/auth-redirect.spec.ts:50` → `로그인 페이지에 ?error=account_type_mismatch 가 있으면 안내가 표시된다`.

Root cause is **assertion drift, not a product defect**. The `account_type_mismatch` / `role_denied` error copy in
[`LoginForm.tsx`](../../apps/web-store/src/features/auth/ui/LoginForm.tsx) renders
`"operator 계정으로는 web-store 에 접근할 수 없습니다. 운영자 콘솔을 이용해 주세요."` (no `admin`), and the vitest unit test
(`src/__tests__/login-form-error-vocab.test.tsx`, `ROLE_DENIED` constant) was updated to the new copy — but the
Playwright spec line 50 still asserts `toContainText('admin')`. The regular `CI` workflow stayed GREEN because the
PR gate runs `e2e:smoke` (`playwright.smoke.config.ts`) only; this full-stack spec is full-tier and runs solely in
`nightly-e2e.yml`, so the drift was invisible at merge time.

## Scope

**In scope** — `apps/web-store/e2e/auth-redirect.spec.ts` only:

1. Update the line-50 assertion to match the current `role_denied` copy — assert a stable substring of the rendered
   banner (`'운영자 콘솔'`) instead of the obsolete `'admin'`. Keep the `[role="alert"].alert-error` scoping (it guards
   against Next.js's route announcer, per the existing inline comment).

**Out of scope:** any change to `LoginForm.tsx` copy (the rendered message is correct); the smoke-vs-full tier split;
other e2e specs (a repo-wide grep confirmed `'admin'` assertion appears only at this one site).

## Acceptance Criteria

- **AC-0 (verify-then-act)** — confirm the rendered copy in `LoginForm.tsx` for `account_type_mismatch` → `role_denied`
  before changing the assertion; the new substring must be a literal substring of that message.
- **AC-1** — `auth-redirect.spec.ts:50` asserts the current copy (`'운영자 콘솔'`); no `'admin'` remains in the spec.
- **AC-2 (behavior verified)** — the `Nightly E2E` web-store full-stack job (or a local full-stack
  `docker compose` + `pnpm e2e` run of `auth-redirect.spec.ts`) passes the `account_type_mismatch` test end-to-end.
  Restoring nightly main GREEN is the authoritative gate.
- **AC-3** — `pnpm lint` + `tsc` green for web-store before push (per memory `env_console_web_local_verify_needs_lint`).

## Related Specs

- web-store auth/login feature — the `role_denied` error vocabulary (`LoginForm.tsx` error map).

## Related Contracts

None — no API/event change; the `/login?error=account_type_mismatch` redirect contract is unchanged.

## Edge Cases

- The unit test (`login-form-error-vocab.test.tsx`) already pins the full `ROLE_DENIED` string; keep the e2e substring
  a subset of it so the two stay consistent if the copy changes again.

## Failure Scenarios

- Asserting the full sentence verbatim in e2e → brittle to punctuation/whitespace; prefer the `'운영자 콘솔'` substring.
- Local `playwright test --list` alone does not exercise the assertion — only a full-stack run (or nightly) proves AC-2.
