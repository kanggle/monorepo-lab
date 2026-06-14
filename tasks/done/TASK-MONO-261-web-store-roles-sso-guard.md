# Task ID

TASK-MONO-261

# Title

ADR-MONO-035 **4b-1** (ADR-032 D5 step 4) — rewrite the web-store cross-type SSO guard from `account_type`-based to **`CUSTOMER`-role-based**. This is the **safety-critical precedence** for the `account_type` drop: the web-store NextAuth guard is the **only code-level cross-type SSO gate**, and it reads the `account_type` claim; once `account_type` emission stops (4b-2) an `account_type`-based guard **no-ops** (`p?.account_type && …` is falsy when absent) → an operator could reach the storefront. Switching the guard to require the `CUSTOMER` role (consumers carry it via the step-2 seed; operators carry `ADMIN`/none) keeps the gate effective **before and after** the claim is removed. Must merge **before** 4b-2 (stop emitting `account_type`).

# Status

done

> **완료 (2026-06-14, close-chore TASK-MONO-264)**: PR #1577 squash `9fcbfba27`, 3-dim verified (state=MERGED, mergeCommit=origin/main tip, Frontend lint&build + unit + E2E smoke all GREEN). web-store SSO guard now CUSTOMER-role-based.

# Owner

frontend

# Task Tags

- ecommerce
- web-store
- security
- adr-035

---

# Dependency Markers

- **executes**: ADR-MONO-035 O5 **step 4b** (the cross-type SSO guard rewrite) + O6 (zero-mis-auth ordering). Child of ADR-032 D5 step 4.
- **must precede**: 4b-2 (TASK-MONO-262 — stop emitting `account_type`). Ordering invariant: the role-based guard must be live BEFORE the claim disappears, else the legacy `account_type`-absent guard no-ops and admits operators to the storefront (security regression).
- **depends on**: 4a (TASK-BE-376, merged) — consumers carry `CUSTOMER` (step-2 seed) and operators carry domain roles, so the role check is decisive now.
- **safe now**: `account_type` is still emitted (additive); switching the guard to roles is behavior-preserving (consumers admit on `CUSTOMER`, operators rejected on its absence).

# Goal

The web-store storefront admits a session iff the GAP token's `roles` include `CUSTOMER`; an operator (ecommerce token carries `ADMIN`/no `CUSTOMER`) is rejected at the `signIn` callback (→ `/login?error=account_type_mismatch`) and degraded at the `session` callback — exactly as the legacy `account_type !== 'CONSUMER'` guard did, but role-based so it survives the `account_type` removal.

# Scope

- **MODIFY** `projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth.ts`:
  - Replace `const ALLOWED_ACCOUNT_TYPE = 'CONSUMER'` with `const REQUIRED_CONSUMER_ROLE = 'CUSTOMER'` + a `hasConsumerRole(roles)` helper.
  - `signIn` callback: reject unless `hasConsumerRole(profile.roles)` (was `account_type !== CONSUMER`). Keep the `/login?error=account_type_mismatch` error key (the banner copy is generic; renaming would churn `LoginForm` + e2e — deferred).
  - `session` callback: degrade (anonymous) unless `hasConsumerRole(token.roles)` (was `account_type !== CONSUMER`).
  - The `accountType` field plumbing (profile/jwt/session) is **left as-is** (harmless; it becomes `null` once the claim is dropped in 4b-2; a later cleanup may remove the field).
  - `authorized` callback unchanged (gates on `auth.accountId`, null for a degraded session).
- NO backend change. NO contract change. `account_type` claim is still emitted (that stops in 4b-2).

# Acceptance Criteria

- **AC-1** The storefront guard admits iff `roles` include `CUSTOMER`; a `CUSTOMER`-role session is fully populated, a non-`CUSTOMER` session is degraded to anonymous (→ `/login` mismatch banner).
- **AC-2** No `account_type`-value gating remains in `auth.ts` (the `signIn`/`session` decisions are role-based); `ALLOWED_ACCOUNT_TYPE` is removed (no unused const).
- **AC-3** `pnpm lint` clean (no-unused-vars etc. — the env memory's key gate); the change is tsc-clean for `auth.ts`.
- **AC-4** Existing unit tests pass unchanged: `login-form.test.tsx` (the `account_type_mismatch` banner — error key unchanged), `auth-context.test.tsx` (fixtures carry `roles:['CUSTOMER']`). CI `Frontend lint & build` + `Frontend unit tests` (clean install) are the authoritative gate.
- **AC-5** Behavior-preserving: consumers (CUSTOMER) admit; operators (ADMIN/none) rejected — identical user-visible outcome to the legacy guard, role-based.

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O5 4b — the SSO guard rewrite + the 4a-before-4b/guard-before-emission ordering)
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (§ D2 SSO role-scoped; § D5 step 4 lift cross-type SSO)
- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` (the consumer guard)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the `roles` claim the guard now reads; `account_type` removal is finalized in 4b-2.

# Edge Cases

- A consumer logging in during an account-service outage still carries `CUSTOMER` (the `RoleSeedPolicy` seed is a local floor, independent of account-service), so the guard does not falsely degrade legit consumers.
- An operator using the web-store client gets `ADMIN` (seed `ecommerce → OPERATOR`) / no `CUSTOMER` → rejected.
- Stale cookies (pre-roles era) → token.roles empty → degraded (defense-in-depth) → re-login mints a roles-carrying token.

# Failure Scenarios

- If this lands AFTER 4b-2 (emission stop) → the window between has an `account_type`-absent no-op guard admitting operators (the regression the ordering prevents). 4b-1 MUST precede 4b-2.
- If the guard checked `account_type` absence as "ok" → admits operators once the claim is gone; the role-presence check avoids that.
