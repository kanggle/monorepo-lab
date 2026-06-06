# admin-web — RETIRED (2026-05-18, ADR-MONO-013 Phase 3)

> **This service no longer exists.** IAM `admin-web` (the operator-only Next.js
> console, IAM's only `frontend-app`) was **retired on 2026-05-18** under
> [ADR-MONO-013](../../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
> Phase 3. Its entire operator surface was **absorbed by the unified
> [`projects/platform-console/`](../../../../platform-console/PROJECT.md)**
> (Model B — the console is the single UI). IAM is now a **backend-only IdP**.
>
> This file is a deliberate **retirement record (tombstone)** kept at the
> conventional spec entry path so historical references resolve to a meaningful
> pointer rather than a 404. The original detailed specs
> (`architecture.md` / `dependencies.md` / `observability.md`) were removed in
> the same change and are preserved in **git history** (`git log --follow`).

## Why retired

ADR-MONO-013 § D2/§ D4: `wms`/`scm` are backend-only and `erp`/`finance` do
not yet exist, so a single console (Model B) renders every domain's operator
screens via gateway/admin REST APIs. `admin-web` was the only existing product
UI; once the console reached **verified operator parity**, `admin-web` became
redundant and its continued existence would split the operator surface.

## Parity gate (satisfied before removal)

Retirement was **not** a silent delete — ADR-MONO-013 § D4 mandates a
parity-gated, recorded deprecation:

- **Verified**: [ADR-MONO-013 § 6](../../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
  additive note + [console-integration-contract § 3](../../../../platform-console/specs/contracts/console-integration-contract.md)
  — a **16/16 verified parity matrix**, programmatically attested by
  `projects/platform-console/apps/console-web/tests/unit/parity-verification.test.ts`.
- **Operator capability lost: none.** Every `admin-web` capability
  (accounts: search · detail · lock/unlock · bulk-lock · revoke-session ·
  gdpr-delete · export; audit query; security: login-history · suspicious;
  operators: create · edit-roles · change-status · password; operator
  overview) is provided by the console.

## Where it went (absorption map)

| `admin-web` capability | Now in platform-console |
|---|---|
| Operator login / SSO | IAM OIDC `platform-console-web` client + RFC 8693 operator token exchange |
| Accounts (search/detail/lock/unlock/bulk-lock/revoke-session/gdpr-delete/export) | `TASK-PC-FE-002` accounts slice |
| Audit query · security (login-history / suspicious) | `TASK-PC-FE-003` audit/security slice |
| Operators (create / edit-roles / change-status / password) | `TASK-PC-FE-004` operators slice |
| Dashboards | `TASK-PC-FE-005` composed **operator overview** (ADR-MONO-015 — *not* a Grafana iframe) |
| Parity attestation | `TASK-PC-FE-006` verified parity matrix |

IAM-side enablers: `TASK-BE-296` (console OIDC client + product registry),
`TASK-BE-298` (operator token exchange), `TASK-BE-299` (this retirement).

## Governance

- [ADR-MONO-013](../../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md)
  § D4 (parity-gated retirement) + § D6 Phase 3 + § 6 (gate satisfied)
- [ADR-MONO-014](../../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md)
  (operator-auth bridge — token exchange)
- [ADR-MONO-015](../../../../../docs/adr/ADR-MONO-015-platform-console-dashboards-model.md)
  (dashboards = composed operator overview)
- IAM changelog: [`docs/migration-notes.md`](../../../docs/migration-notes.md)
  § "admin-web retirement"
- IAM classification: [`PROJECT.md`](../../../PROJECT.md)
  § "admin-web — RETIRED" (canonical service-map record;
  `service_types` no longer lists `frontend-app`)

## Do not

- Do **not** re-add `apps/admin-web/` or a IAM `frontend-app` — IAM is
  intentionally backend-only (ADR-MONO-013 § D2/§ 3.3). New operator UI work
  is a **platform-console** task.
- Do **not** treat this file as an active service spec — it is a tombstone.
