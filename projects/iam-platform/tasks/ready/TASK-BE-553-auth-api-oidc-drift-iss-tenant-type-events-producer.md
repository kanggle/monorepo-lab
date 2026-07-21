# TASK-BE-553 — Reconcile auth-api.md OIDC drift: iss, tenant_type, events-README producer

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (three spec-doc corrections, no behavior change; verify each against code first)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (three UNCERTAIN claims, all CONFIRMED against
> origin/main `aa535c22b`). Sibling audit tasks: iam envelope/straggler work already merged (#2830), and the shared-file
> `TOKEN_TENANT_MISMATCH` status question is deliberately split into a **root** task (`TASK-MONO-462`) because it edits
> `platform/error-handling.md` and needs a source-of-truth ruling — do not fold it here.

---

## Goal

`projects/iam-platform/specs/contracts/http/auth-api.md` post-TASK-BE-251 declares its SAS/OIDC section authoritative, yet
three facts in the file (and one events-README row) still describe the pre-SAS world and now contradict both the code and
the file's own OIDC section. Correct all three so the spec matches shipped behavior.

## Scope

**In scope (all doc-only):**

1. **`iss` claim** — `auth-api.md:452` (`| iss | iam-platform |`) and `:463` ("gateway-service 는 `iss == iam-platform` 을 강제 검증한다").
   Code sets `iss` to the configured OIDC issuer URL: `apps/auth-service/.../oauth2/AuthorizationServerConfig.java:356-357`
   (`AuthorizationServerSettings.builder().issuer(issuerUrl)`, `issuerUrl` from `${oidc.issuer-url:http://localhost:8081}`,
   `application.yml:52-56`). The literal `iam-platform` is never an `iss` value anywhere. The gateway allowlists issuers
   (`apps/gateway-service/.../TokenValidator.java:20-33`: legacy `iam` + SAS issuer URL, never `iam-platform`).
   Correct `:452`/`:463` to the OIDC-URL value + the actual allowlist behavior. The file's own lines 179/277 already state it correctly.
2. **`tenant_type` value** — the OIDC section uses stale short alias `B2C` at `auth-api.md:183, 212, 279, 292`. Code enum is
   `apps/account-service/.../domain/tenant/TenantType.java:12-15` = `{B2C_CONSUMER, B2B_ENTERPRISE}` (default
   `TenantContext.java:17` = `"B2C_CONSUMER"`). The file's line 458 and every other spec (`multi-tenancy.md`, `admin-api.md`,
   `tenant-events.md`, `account-service/architecture.md`, `auth-to-account.md`) already use `B2C_CONSUMER`. Replace the four `B2C`.
3. **events-README producer attribution** — `specs/contracts/events/README.md:40` (`partnership-events.md | partnership-service — producer`)
   and `:43` (`tenant-events.md | tenant-service — producer`). No such app dirs exist; both publishers live in **admin-service**
   (`apps/admin-service/.../outbox/OutboxPartnershipEventPublisher.java`, `OutboxTenantEventPublisher.java`), and the linked
   contract files themselves say `admin-service` (`partnership-events.md:1,7`, `tenant-events.md:1,7`). Change both rows to `admin-service — producer`.

**Out of scope:** `platform/error-handling.md` TOKEN_TENANT_MISMATCH status (→ `TASK-MONO-462`); any code change; the legacy
"Token Specification" section's already-correct lines (`:458`).

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Before editing, re-confirm each of the three drifts still holds at the current
  `main` tip (the four cited code anchors + the four `B2C` line numbers, which may shift). If any already match, drop that item.
- **AC-1** — `iss` in the Token-Specification table describes the configured `oidc.issuer-url` (a URL), and the gateway text
  describes the `allowed-issuers` allowlist, not `iss == iam-platform`.
- **AC-2** — All four OIDC-section `tenant_type` occurrences read `B2C_CONSUMER`; no bare `B2C` remains in `auth-api.md`
  (grep `\bB2C\b` returns only `B2C_CONSUMER` / `B2B_ENTERPRISE`).
- **AC-3** — events `README.md` rows for `partnership-events.md` and `tenant-events.md` both read `admin-service — producer`,
  matching their linked contract files and the publisher classes.
- **AC-4** — No behavior change; only `auth-api.md` and `events/README.md` are touched. Grep the iam specs for any *other*
  `iam-platform`-as-iss or bare-`B2C` occurrences and fix or explicitly note them (this task found three by reading one file).

## Related Specs
- `projects/iam-platform/specs/contracts/http/auth-api.md` (§ Token Specification + § SAS/OIDC)
- `projects/iam-platform/specs/contracts/events/README.md` § Contract Index
- `projects/iam-platform/specs/services/account-service/architecture.md` (tenant_type reference, already correct)

## Related Contracts
- `partnership-events.md`, `tenant-events.md` (already say `admin-service` — the README index is the outlier)

## Edge Cases
- The file is internally split (legacy Token-Spec vs authoritative OIDC section). Fix the OIDC/authoritative values; do not
  "reconcile" by dragging the already-correct lines back to the legacy ones.
- `iss` correction must not imply the gateway enforces a single literal — it allowlists (legacy `iam` + SAS URL) during the sunset.

## Failure Scenarios
- **F1 — editing only the 3 named spots.** Each was found by reading one file; AC-4's grep census guards against siblings.
- **F2 — flipping the wrong side.** Three code/contract artifacts agree on OIDC-URL / `B2C_CONSUMER` / `admin-service`; the spec
  outlier is the stale side. AC-0 re-measurement makes the direction explicit.
