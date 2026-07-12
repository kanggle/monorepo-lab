# TASK-BE-390 — Remove the legacy `iam` issuer from the gateway after the D2-b deprecation window (~2026-08-01)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (small config + test removal once the window has closed)

> ⏳ **SCHEDULED / DO NOT START before 2026-08-01.** This is a date-gated cleanup recorded as a durable backlog item (the in-session scheduler is ephemeral and would not survive to the target date; git is the reliable tracker). The authoritative date lives in the code itself: `gateway-service/.../application.yml` comments the `allowed-issuers` list as the *"D2-b deprecation window (~2026-08-01)"*. Before starting, re-verify the window has actually closed and the legacy issuer is unused (see Acceptance Criteria AC-0).

---

## 🔴 SCOPE WARNING (added 2026-07-12 — `TASK-MONO-365`)

**This task sees one gateway of seven. The legacy `,iam` default lives in all of them** — wms, scm, fan, **ecommerce**, finance, erp, and iam.

Its AC-3 greps only this project's tree, so a diligent execution of this task **removes the legacy issuer from ecommerce alone and leaves six gateways still accepting a retired issuer** — while everyone believes the sunset has happened. That is the failure mode: not that the work is wrong, but that **finishing it looks like finishing the job.**

**The complete list of what must happen on that date is [`TASK-MONO-367`](../../../../tasks/ready/TASK-MONO-367-fleet-wide-legacy-issuer-sunset.md)** (root-level, because `,iam` spans six projects and a project task may not edit another project — `CLAUDE.md` shared/project boundary).

**Do not run this task standalone.** Either execute it as part of MONO-367, or let MONO-367 absorb it (MONO-367 § AC-5 requires that the two do not both own the same file).

**Also relevant:** the servlet services in finance/erp enforce the same allowlist in their `ServiceLevelOAuth2Config`. Removing the issuer at the gateways alone leaves the inner layer of the defense-in-depth still accepting it.

---

## Goal

During the ADR-MONO-019 D2-b plane-separation migration, the ecommerce gateway accepts **two** `iss` claim values so tokens minted by both the new SAS issuer and the legacy path validate during the transition:

```yaml
# gateway-service/src/main/resources/application.yml (~L231-235)
oauth2:
  # Acceptable iss claim values during the D2-b deprecation window
  # (~2026-08-01). Both the SAS issuer (default http://iam.local) and the
  # legacy 'iam' string must validate while the legacy path is being deprecated.
  allowed-issuers: ${OIDC_ALLOWED_ISSUERS:${OIDC_ISSUER_URL:http://localhost:8081},iam}
```

Once the deprecation window closes (~2026-08-01) and no live token carries `iss=iam`, the legacy string is dead weight and a small attack-surface widening (an extra accepted issuer). Remove it.

## Scope

**In scope (only after AC-0 confirms the window is closed):**

1. `gateway-service/src/main/resources/application.yml` — drop the trailing `,iam` from the `allowed-issuers` default so only the SAS issuer (`OIDC_ISSUER_URL`) is accepted; tidy the now-obsolete `~2026-08-01` deprecation comment.
2. `AllowedIssuersValidator` test — remove the `legacyIssuerPasses` (or equivalent) case that asserts `iss=iam` validates; keep/clarify the negative case (an unknown issuer is rejected).
3. If any docker-compose / e2e / k8s env still sets `OIDC_ALLOWED_ISSUERS` to include `iam`, remove the legacy entry there too (grep first).

**Out of scope:** the SAS issuer itself, `TenantClaimValidator`, JWKS config, any ADR decision change (D2-b already decided the deprecation; this only executes the post-window cleanup).

## Acceptance Criteria

- **AC-0 (gate)** — Confirm the current date is ≥ 2026-08-01 **and** the D2-b window is genuinely closed: no live token/issuer config emits `iss=iam` (grep `iam` across gateway config + e2e fixtures + `specs/integration/iam-integration.md`; confirm the SAS issuer is the sole minter). If anything still relies on `iam`, **STOP** and report — do not remove early.
- **AC-1** — `allowed-issuers` default contains only the SAS issuer; `,iam` is gone; the deprecation comment is removed/updated.
- **AC-2** — No test asserts `iss=iam` validates; the unknown-issuer-rejected test still passes. `gateway-service` build + tests GREEN.
- **AC-3** — `grep -rn "\biam\b" gateway-service/src/main/resources docker-compose* tests/` shows no surviving legacy-issuer accept-list entry (excluding unrelated `iam.local`/iam-platform references).
- **AC-4** — If `specs/integration/iam-integration.md` documents the dual-accept window, update it to reflect the legacy issuer's removal (this is **not** a pure net-zero refactor — it removes a legacy acceptance path, so the spec must stay in sync).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` (token validation / allowed issuers).
- `docs/adr/ADR-MONO-019-*` § D2-b (plane separation / issuer deprecation window — the decision this task executes).

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` (`iss` claim authority — informational).

## Edge Cases

- **Window not actually closed** — `~2026-08-01` is approximate; the real trigger is "no live `iss=iam` token remains," not the calendar. AC-0 gates on the real condition, not just the date.
- **Env override still injects `iam`** — a deployment env var `OIDC_ALLOWED_ISSUERS` could re-add it; AC-3 greps compose/k8s/e2e to catch that.

## Failure Scenarios

- **F1 — premature removal** — removing `iam` while a legacy-issued token is still in flight would 401 those callers. Guarded by AC-0 (verify-then-remove).
- **F2 — spec drift** — removing the accept path in code but leaving the dual-accept narrative in `iam-integration.md` re-creates spec drift. Guarded by AC-4.
