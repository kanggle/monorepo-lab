# Task ID

TASK-SCM-BE-027

# Title

scm-side spec reconciliation — recognise platform-console as a sanctioned operator **action** consumer of demand-planning replenishment suggestions (read + approve/dismiss)

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- adr

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: `TASK-SCM-BE-024` (demand-planning-service bootstrap — **done**; the `/api/v1/demand-planning/**` capability that this task documents the console consumer of) and `TASK-SCM-BE-022` (replenishment-subscriptions contract — **done**). ADR-MONO-027 ACCEPTED governs the loop.
- **extends**: `TASK-SCM-BE-015` (the existing *platform-console operator **read** consumer* acknowledgment in [`gateway-public-routes.md`](../../specs/contracts/http/gateway-public-routes.md) § *platform-console operator read consumer*). That subsection is explicitly **read-only** ("The console consumes only the reads above"). This task **widens** it to cover the demand-planning **operator-action** surface (`POST /suggestions/{id}/approve`, `/dismiss`) — exactly the spec-first reconciliation that gated FE-008, now for the loop's operator-gate writes.
- **blocks**: `TASK-PC-FE-077` (platform-console *scm replenishment suggestions operator screen*). FE-077 is the console consumer; per CLAUDE.md "Specs win over tasks", the producer spec must acknowledge the console **action** consumer **before** the console code lands (mirrors SCM-BE-015 ⊃ FE-008). FE-077 is `ready` but gated on this task merging first.
- **(B) document/accept, not a capability change**: demand-planning's gateway-public operator-action routes already exist (built by SCM-BE-024; [`demand-planning-api.md`](../../specs/contracts/http/demand-planning-api.md) § Route publicity declares `POST /suggestions/{id}/approve|dismiss` = "gateway-public (operator action)", auth = IAM RS256 `tenant_id=scm` fail-closed + entitlement-trust dual-accept). This task **documents that the console is a sanctioned caller** of them — no new route, no new OAuth client, no new gateway code, no auth-model change. Same shape as SCM-BE-015.

# Goal

Reconcile the scm gateway contract so it acknowledges `platform-console` (ADR-MONO-013 Model B operator console) as a sanctioned **operator-action** consumer of the demand-planning replenishment-suggestion surface — not merely a read consumer. Today [`gateway-public-routes.md`](../../specs/contracts/http/gateway-public-routes.md) § *platform-console operator read consumer* lists only procurement/inventory-visibility **reads** and states "**Read-only.** The console consumes only the reads above." The replenishment operator gate (ADR-MONO-027 D5 — a human operator approves a `SUGGESTED` reorder, which materialises a **DRAFT** PO, or dismisses it) is the console's natural operational surface, and demand-planning's own contract already declares those routes operator-action gateway-public. The producer spec must say so before the console (FE-077) consumes it.

This is a **spec/document-accept** task (production code = 0), the demand-planning analog of SCM-BE-015. It also reconciles the stale gateway route-catalogue line for demand-planning if SCM-BE-024 left it as "route reserved".

# Scope

## In Scope

- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md`:
  - Widen the **§ *platform-console operator read consumer*** subsection (or add a clearly-titled sibling subsection, e.g. *platform-console operator action consumer (demand-planning replenishment gate)*) to record:
    - **Consumed (operator read)**: `GET /api/v1/demand-planning/suggestions`, `GET /api/v1/demand-planning/suggestions/{id}`.
    - **Consumed (operator action — the net-new acknowledgment)**: `POST /api/v1/demand-planning/suggestions/{id}/approve`, `POST /api/v1/demand-planning/suggestions/{id}/dismiss`.
    - **Out (not console-consumed)**: the `GET|PUT /policies/{skuCode}` and `GET|PUT /sku-supplier-map/{skuCode}` **seed/admin** routes (operator/admin seed surface, not the operator gate — keep them out of the console acknowledgment unless/until a separate seeding screen is specced).
  - State the **credential is unchanged** — the console calls server-side with the human operator's IAM `platform-console-web` OIDC access token (RS256, ADR-001), validated by the **existing** gateway chain (`AllowedIssuersValidator` + `TenantClaimValidator` `tenant_id ∈ { scm, * }` + `JwtHeaderEnrichmentFilter` `X-Token-Type=user`). No new scm OAuth client, no new gateway route, no new gateway code. This is the **same** credential the read consumer already uses (SCM-BE-015) — actions ride the same token, not a privileged exchange.
  - Note the **operator-gate invariant** that makes this a safe write to expose: approve materialises a **DRAFT** PO only (ADR-MONO-027 D5) — never an auto-SUBMIT; dismissal only releases the open-suggestion guard. The console action does not bypass procurement's `DRAFT → SUBMITTED` operator step. Single-organization preserved (the `multi-tenant` non-declaration in [`PROJECT.md`](../../PROJECT.md) is **unaffected**; tenant scoping stays the IAM `tenant_id` claim enforced producer-side).
  - **Cross-reference, not redefine**: authoritative endpoint shapes/idempotency/error-codes stay in [`demand-planning-api.md`](../../specs/contracts/http/demand-planning-api.md) (unchanged by this task). Point the console-side obligation at platform-console [`console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md) § 2.4.6.1 (authored by FE-077).
  - **Route-catalogue reconciliation**: if the `/api/v1/demand-planning/**` route-catalogue line still reads "**route reserved (ADR-MONO-027)** — activated by TASK-SCM-BE-024 impl", and SCM-BE-024 has shipped the route, update it to **live** with the route-table fields (predicate, internal target, RewritePath, auth, rate-limit) in the same form as the procurement/inventory-visibility catalogue entries. If the route is genuinely not yet wired in the gateway, **STOP and report** (the demand-planning operator surface cannot be console-consumed until the gateway route is live — this becomes a real prerequisite, not a doc fix).
- Optionally `gap-integration.md` / `iam-integration.md` only if the read-consumer acknowledgment there must mirror the action widening (keep additive, byte-minimal — same discipline as SCM-BE-015).

## Out of Scope

- Any change to `demand-planning-api.md` endpoint shapes, idempotency semantics, or error codes (authoritative, consumed unchanged).
- The demand-planning `policies` / `sku-supplier-map` **seed** routes as a console surface (admin-seed, not the operator gate).
- Any production code, gateway filter, OAuth client, or new route (this is document/accept of an existing capability; if the capability is genuinely missing → STOP per the route-catalogue check above).
- The console-side screen + contract section (`§ 2.4.6.1`) — that is FE-077 in platform-console.
- scm `PROJECT.md` classification bytes (must stay unchanged — single-org preserved).

# Acceptance Criteria

- [ ] `gateway-public-routes.md` records `platform-console` as a sanctioned operator-**action** consumer of demand-planning `GET /suggestions{,/{id}}` + `POST /suggestions/{id}/{approve,dismiss}`, with the credential explicitly unchanged (IAM `platform-console-web` OIDC token, existing gateway chain, `tenant_id ∈ {scm,*}`); the `policies`/`sku-supplier-map` seed routes are explicitly **not** console-consumed.
- [ ] The operator-gate invariant (approve → **DRAFT** PO only, never auto-SUBMIT; single-org preserved; `PROJECT.md` `multi-tenant` non-declaration unaffected) is stated.
- [ ] Authoritative shapes stay in `demand-planning-api.md` (unchanged); the console obligation is pointed at `console-integration-contract.md` § 2.4.6.1 (cross-reference, no redefinition).
- [ ] The demand-planning route-catalogue line reflects reality — **live** with full route fields if SCM-BE-024 wired it; otherwise the task STOPs and reports the missing-capability prerequisite.
- [ ] Change is **additive + spec-only** (production code 0); scm classification bytes unchanged; spec internal-link lint clean; `validate-rules` no new inconsistency.
- [ ] Merged **before** FE-077 code (spec-first cross-project gate — the FE-077 dependency marker links this task).

# Related Specs

> Target project = `scm-platform`. This is a contract/spec reconciliation; no service-type code change. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md` § D2/D5 (the operator gate — approve materialises DRAFT PO)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 Model B / § D6 (the governing console ADR; why platform-console is a sanctioned external consumer)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (**changed** — the platform-console operator consumer acknowledgment widened to actions + route-catalogue reconciled)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (authoritative producer — consumed unchanged; route publicity already declares operator-action gateway-public)
- `projects/scm-platform/specs/services/demand-planning-service/architecture.md` (the service SCM-BE-024 bootstrapped)
- `projects/scm-platform/PROJECT.md` (single-org; `multi-tenant` non-declaration must stay unaffected)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.6 (the existing read binding FE-077 extends with § 2.4.6.1 — cross-reference)

---

# Related Contracts

- **Changed (this task)**: scm `gateway-public-routes.md` — platform-console operator-action consumer acknowledgment + demand-planning route-catalogue line.
- **Consumed (unchanged, authoritative — scm-owned)**: `demand-planning-api.md` (suggestions read + approve/dismiss action shapes, idempotency, error codes).
- **Downstream consumer obligation (other project, FE-077)**: platform-console `console-integration-contract.md` § 2.4.6.1.

---

# Edge Cases

- The `/api/v1/demand-planning/**` gateway route is genuinely not yet wired (catalogue line still "reserved" and SCM-BE-024 did not ship the route) → **STOP and report**: this is a real capability gap, not a doc reconciliation; the console action surface cannot exist until the route is live.
- Someone reads the widened acknowledgment as also sanctioning the `policies`/`sku-supplier-map` seed routes for the console → the subsection must explicitly fence those out (admin-seed, not the operator gate).
- A reviewer assumes operator actions require a privileged token exchange (like IAM's admin-service) → the acknowledgment must state actions ride the **same** IAM OIDC `tenant_id=scm` token as the reads (scm has no operator-token-exchange; the gate is server-side validation + the DRAFT-PO invariant, not a stronger credential).

# Failure Scenarios

- FE-077 code starts before this merges → spec-first violation; the FE-077 dependency marker + AC gate it on this task.
- The widening accidentally edits `demand-planning-api.md` shapes or `PROJECT.md` classification → scope violation (this is consumer-acknowledgment only; producer + classification untouched).
- The route-catalogue line is left stale ("reserved") while the console consumes a live route → contract drift; the route-catalogue reconciliation AC closes it.

---

# Recommended Implementation Model

- **Sonnet** — spec/document-accept reconciliation (production code 0), pattern fully established by SCM-BE-015. Escalate to **Opus** only if the route-catalogue check reveals the gateway route is unwired (then it is a real capability prerequisite, re-scope).

---

# Definition of Done

- [ ] `gateway-public-routes.md` widened to operator-action consumer + route-catalogue reconciled (or STOP-reported if route unwired)
- [ ] Additive, spec-only, classification bytes unchanged; cross-references correct; link lint + `validate-rules` clean
- [ ] Merged before FE-077 (spec-first gate)
- [ ] Acceptance Criteria all satisfied
- [ ] Ready for review
