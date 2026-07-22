# TASK-SCM-BE-040 — refactor-spec: scm-platform structural polish (clean Tier-1 batch)

- **Type**: TASK-SCM-BE (spec-refactor — structural/format only, NO requirement/contract/decision change)
- **Status**: ready
- **Service**: scm-platform (contracts)
- **Domain/traits**: scm / [event-driven, transactional, batch-job]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical spec polish)

## Goal

Apply the **clean, meaning-preserving** subset surfaced by a `/refactor-spec scm-platform` scan
(22 spec files across contracts / 4 services / integration; all H1 titles + every relative `.md`
link inventoried; the 10 highest-risk cross-project / root-task / ADR link targets existence-verified;
**0 broken links**). scm is small and — per its own `contracts/events/README.md` census — the
procurement contract is "the clean reference convention", so the clean batch is intentionally tiny.

No API path, schema field, event payload, status code, or business rule is touched.

## Scope

**Applied — 3 edits across 3 files (all SEMANTIC-preserving):**

| # | File | Fix | Category |
|---|---|---|---|
| 1 | `contracts/events/scm-procurement-events.md:1` | Title `# Event Contract: procurement-service` (colon) → `# Event Contract — procurement-service` (em-dash) — the 2 sibling event contracts (`inventory-visibility-subscriptions.md`, `replenishment-subscriptions.md`) both use `# Event Contract — <svc>`; procurement is the sole colon outlier | naming |
| 2 | `contracts/http/demand-planning-api.md:1` | Title `# HTTP API Contract — demand-planning-service` → `# API Contract — demand-planning-service` — the 2 sibling HTTP contracts (`procurement-api.md`, `inventory-visibility-api.md`) both use `# API Contract — <svc>`; the redundant `HTTP ` prefix is the outlier | naming |
| 3 | `contracts/events/inventory-visibility-subscriptions.md:87` | Lone Korean `(예:` → `(e.g.` inside an otherwise 100%-English event-contract file (within-file language consistency; no meaning change) | consistency |

## Acceptance Criteria

- [x] 3 edits applied in isolated worktree (`task/scm-be-040-refactor-spec-scm`), 3 files.
- [x] No markdown link / heading anchor broken (title edits are H1s referenced by path, not anchor; the 10 highest-risk link targets — root `TASK-MONO-042/262`, `ADR-MONO-004/005/013/027`, iam `account-internal-provisioning.md` / `consumer-integration-guide.md` / `multi-tenancy.md` / `ADR-001`, platform-console `console-integration-contract.md` — all verified present).
- [x] Zero requirement / contract / schema / status-code / event-payload / business-rule changes.
- [x] Each "outlier vs siblings" claim independently re-verified against the sibling files.

## Related Specs

- `projects/scm-platform/specs/services/{procurement,inventory-visibility,demand-planning,gateway}-service/`
- `projects/scm-platform/specs/contracts/events/README.md` (convention census — documents the intentional inventory-alert topic/eventType divergence as ADR-gated, NOT to be normalized)

## Related Contracts

- `projects/scm-platform/specs/contracts/{events,http}/*` — polished titles only, semantically unchanged.

## Out of Scope (deferred / not applied)

**Bilingual prose — intentional repo posture, NOT touched:**

- The `자세한 spec 은 … 참조.` lines in `{procurement,gateway,inventory-visibility}-service/overview.md`, and the fully-Korean `integration/iam-integration.md`, are authored bilingual by design (this repo tolerates Korean prose in docs; English for code/commits). Mass Korean→English is a style decision, not a mechanical refactor — explicitly left as-is. (The single `예:` in edit #3 is different: it is one stray token inside an otherwise all-English file.)

**Known ADR-gated divergence — NOT normalized (per README census AC-2):**

- inventory-visibility-service's `scm.inventory.alert.v1` single-shared-topic + `inventory.alert.<type>` eventType (drops `scm.` prefix, string-concatenated, no outbox) diverges from procurement's `scm.procurement.<agg>.<fact>.v1` per-eventType + named-constant + outbox convention. `contracts/events/README.md §1/§2/§6 Follow-up` records this as a real, live divergence whose unification is a **breaking change gated by `platform/event-driven-policy.md § Contract Rule`** — a separate ADR-gated ticket, never a silent refactor.

**Tier-2 structural (not scanned exhaustively under this session's constraint; candidate for a follow-up pass):**

- Cross-service section-ordering / section-set parity across the 4 services' `overview.md` / `architecture.md` / `data-model.md` was not fully diffed this round (the 3-subagent structural scan was interrupted by a session-usage limit). If a later pass finds sibling-outlier section orders, handle them as Tier-2 judgment items (outlier may be intentional), not mechanical.

## Edge Cases

- Title (H1) edits verified to have no inbound anchor links — docs are referenced by path, not by their own H1 fragment.
- The `예:`→`e.g.` edit is inside a markdown table cell; surrounding pipe/escaping untouched.

## Failure Scenarios

- If any doc referenced `scm-procurement-events.md` or `demand-planning-api.md` by an H1 anchor fragment, the title change would 404 that fragment — not the case here (all references are path-based, per the link inventory).
