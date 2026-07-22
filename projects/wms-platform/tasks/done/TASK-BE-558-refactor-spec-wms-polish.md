# TASK-BE-558 — refactor-spec: wms-platform structural polish (clean Tier-1 batch)

- **Type**: TASK-BE (spec-refactor — structural/format only, NO requirement/contract/decision change)
- **Status**: done
- **Service**: wms-platform (contracts + inventory / outbound / notification / admin services)
- **Domain/traits**: wms / [event-driven, transactional, multi-tenant, integration-heavy]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical spec polish)

## Goal

Apply the **clean, meaning-preserving** subset surfaced by a full `/refactor-spec wms-platform`
dry-run (80 spec files scanned across 5 parallel scanners; **0 broken markdown links / anchors
repo-wide**). Every applied fix is title/heading normalization, formatting consistency against
verified siblings, an aggregate miscount, a navigation cross-ref present in 4/4 siblings, or removal
of stale prose `(line NNN)` hints — **no API path, schema field, event payload, status code,
state transition, or business rule is touched**.

Findings that would alter meaning, or invasive section reorderings where the outlier is plausibly
intentional, are recorded under **Out of Scope** (not applied) so the audit trail is preserved.

## Scope

**Applied — 15 edits across 11 files (all SEMANTIC-preserving):**

| # | File | Fix | Category |
|---|---|---|---|
| 1 | `contracts/events/notification-events.md:1` | Title `# notification-events — Published Event Contract` → `# Event Contract — notification-service Domain Events` (sibling pattern; master/inbound/inventory/outbound/admin all use `# Event Contract — …`) | naming |
| 2 | `contracts/events/notification-events.md:15` | Heading `## Envelope` → `## Global Envelope` (5 sibling event contracts use `## Global Envelope`) | consistency |
| 3 | `contracts/events/notification-subscriptions.md:1` | Title → `# Event Contract — notification-service subscriptions` (sibling subscription pattern `# Event Contract — <svc> subscriptions`) | naming |
| 4 | `contracts/events/outbound-events.md:688` | `Publisher metrics:` 3-item nested bullet list → single inline comma-separated line (master/inbound/inventory render it inline) | consistency |
| 5 | `contracts/http/inventory-service-api.md:114` | `Full strategy: <path>` plain text → markdown link (inbound/admin/outbound siblings link; target verified) | consistency |
| 6 | `contracts/http/master-service-api.md:110` | Added missing `- Full strategy: [<link>]` nav line under `### Idempotency Semantics` — present in 4/4 sibling HTTP contracts, absent here (additive nav to existing `master-service/idempotency.md`; no contract change) | missing-section |
| 7 | `services/outbound-service/domain-model.md:14` | Scope intro `Seven owned aggregates` → `Six` (list enumerates exactly 6: Order, PickingRequest, PickingConfirmation, PackingUnit, Shipment, OutboundSaga) | consistency |
| 8 | `services/outbound-service/external-integrations.md:749` | `tms-shipment-api.md` References entry de-linked to plain backtick (the other 3 entries in the same list, and the whole sibling inbound list, are plain) | consistency |
| 9 | `services/inventory-service/sagas/reservation-saga.md:183,184,189` | Dropped stale prose `(line 510)` / `(line 523)` hints on `§ Saga Participation` / `§ Saga / Long-running Flow` refs (actual headings now at 527 / 550 — verified; § names retained, brittle line hints removed) | dead-reference |
| 10 | `services/inventory-service/state-machines/reservation-status.md:9-12, 307-313` | Dropped all stale `(line NNN-MMM)` hints on architecture.md / domain-model.md § refs (all verified stale: 529-547→562/564, 297-308→300, 617-628→621, 320-339→325, 340-345→344, 517-525→550); § names retained | dead-reference |
| 11 | `services/notification-service/architecture.md:165,193` | Dropped heading parenthetical suffixes `(subscribed topics)` / `(outbox)` — 5 siblings use plain `## Event Consumption` / `## Event Publication`; qualifier already in body | consistency |
| 12 | `services/admin-service/runbooks/db-role-grants.md:1` | Title `# Runbook — admin-service DB Role Grants` → `# admin-service — DB Role Grants Runbook` (both sibling runbooks use `# <svc> — <Doc> Runbook`) | naming |

## Acceptance Criteria

- [x] All 15 edits applied in isolated worktree (`task/be-558-refactor-spec-wms`), 11 files, `git diff --stat` = +22/-24.
- [x] No markdown link or heading anchor broken by the changes (grep for `#event-consumption-subscribed-topics` / `#event-publication-outbox` / `#envelope` anchors → 0 references repo-wide; the two ADDED links resolve to existing `idempotency.md` targets).
- [x] Zero requirement / contract / schema / status-code / state-transition / business-rule changes.
- [x] Each "outlier vs siblings" claim independently re-verified against the sibling files before applying (not trusted from the scanner).

## Related Specs

- `projects/wms-platform/specs/services/{inbound,outbound,inventory,master,admin,notification,gateway}-service/`
- `projects/wms-platform/specs/contracts/events/README.md` (convention census; confirmed wms is the most internally-consistent of the 7 platforms — hence the small clean batch)

## Related Contracts

- `projects/wms-platform/specs/contracts/{events,http,webhooks}/*` — structurally polished, semantically unchanged.

## Out of Scope (deferred — recorded, NOT applied)

**Tier-2 structural (outlier may be intentional; needs judgment, separate ticket):**

- `outbound-service/domain-model.md` — infra records `## §7–§12` body-heading vs Scope-list numbering drift (`## 9.` merges ErpOrderWebhookInbox + ErpOrderWebhookDedupe → TmsRequestDedupe/MasterReadModel body headings lag Scope by 1). Two valid fixes (split heading vs collapse Scope) — pick one deliberately.
- `master-service/domain-model.md` — `Reference Data Snapshot` precedes `Forbidden Patterns` (3-of-4 siblings order them the other way).
- `inventory-service/database-design.md` — `Master Read Model` numbered §7 vs §1 in inbound/outbound (high blast: grep external `database-design.md § 7` refs before any renumber).
- `inventory-service/architecture.md` — `## State Machines` standalone H2 vs nested `### State Machine` under Concurrency in siblings.
- `inventory-service/domain-model.md` — extra `## State Machines (Cross-reference)` H2 absent in siblings.
- `inventory-service/idempotency.md` — missing `## N. Cross-References` section present in inbound/outbound idempotency.md.
- `master-service/idempotency.md` — unnumbered headings vs numbered scheme in the other 3 (must preserve `#cross-service-idempotency-key-conventions-intentional-divergence` anchor slug).
- `notification-service/architecture.md` — `## Event Consumption` precedes `## Event Publication` (5 siblings Publication-first). **Deliberately not reordered**: notification is a consumer-centric service, so leading with Consumption is a plausible intentional ordering.
- `webhooks/erp-asn-webhook.md` ↔ `erp-order-webhook.md` — near-identical `Signature Computation` / `Timestamp Verification` / `Replay Dedupe` sections (governed by `rules/traits/integration-heavy.md` I6); dedup-extract is a multi-file restructure, not a mechanical single-file edit.

**Rejected (scanner over-classification — confirmed NOT a defect):**

- `notification-service/architecture.md:59` + `domain-model.md:346` `## Out of Scope (v1)` → `## Out of Scope`: the `(v1)` suffix is **intentional** — the body is dominated by `— v2` deferrals (Email/Push/SMS/Template-UI), i.e. a v1 scope boundary, semantically distinct from the sibling `## Out of Scope` sections that enumerate **permanent** ownership boundaries. Dropping `(v1)` would misrepresent deferred-vs-permanent.

## Report-only semantic findings (require a separate decision — NOT a refactor)

- **`contracts/http/inventory-service-api.md` §4.2 Confirm reservation** — request field `shippedQuantity` breaks the `quantity` convention used elsewhere in the same file AND contradicts `events/inventory-events.md`'s note that the canonical field for this action is `qtyConfirmed` (TASK-BE-437 already fixed the analogous Kafka shape). Renaming a request-body field = API contract change.
- **`webhooks/erp-asn-webhook.md:385`** — documented dev-fallback env `EERP_WEBHOOK_SECRET_<env>` mismatches code constant `EnvVarWebhookSecretAdapter.ENV_PREFIX = "ERP_WEBHOOK_SECRET_"` (keyed by `X-Erp-Source`, not a generic `<env>` suffix). Touches documented secrets contract.
- **`webhooks/erp-order-webhook.md:392`** — documented dev-fallback env `OUTBOUND_ERP_WEBHOOK_SECRET_<env>` mismatches code constant `EnvWebhookSecretAdapter.ENV_PREFIX = "ERP_WEBHOOK_ORDER_SECRET_"`. Same class.
- **`services/gateway-service/public-routes.md:22-31`** — `## Not Public (require JWT)` table omits `/api/v1/outbound/**` → outbound-service, though outbound is declared JWT-protected in this service's own `architecture.md:95` / `overview.md:37`. Completeness gap in an API-path enumeration.
- **`contracts/events/README.md:5`** — census intro says "the 8 contract files below" but the §5 index lists 9 rows (`scm-inbound-expected-subscriptions.md` added later under ADR-MONO-050, after the TASK-MONO-415 census). Self-count drift — but "8" is tied to a dated census provenance, so correcting to "9" is a judgment call, not a clean mechanical fix.

## Edge Cases

- Heading renames verified to have **no** inbound anchor links (`#…` fragment) anywhere in wms-platform → no navigation breakage.
- `(line NNN)` hint removal keeps every `§ Section` name intact, so prose cross-refs still resolve to a stable target (and can no longer drift).

## Failure Scenarios

- If a downstream doc/tool referenced any renamed heading by anchor, it would 404 — mitigated by the repo-wide anchor grep (0 hits) prior to close.
- If `master-service/idempotency.md` or `inventory-service/idempotency.md` were later removed, the two added `Full strategy` links would dangle — same risk profile as the 4 pre-existing sibling links.
