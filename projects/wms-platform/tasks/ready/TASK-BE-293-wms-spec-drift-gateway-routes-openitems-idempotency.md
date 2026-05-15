# Task ID

TASK-BE-293

# Title

WMS spec drift bundle — gateway route table outbound omission (W9) + stale "Open Items" framing in master/outbound architecture.md (W18) + cross-service Idempotency Redis key shape/cap drift (W15, decision-bearing)

# Status

ready

# Owner

backend

# Task Tags

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

# Goal

Close three GENUINE WMS spec-vs-spec drift findings from the 2026-05-15
portfolio audit, reconciled against the current tree (stale WMS items W2
[eventVersion] and W8 [9 dead-refs] verified already-closed by TASK-BE-144 /
BE-151 / BE-156 — NOT in scope).

After this task: (WI-1) the gateway spec is internally consistent on the
outbound route; (WI-2) master/outbound `architecture.md` Open Items lists no
longer claim completed files are unwritten prerequisites; (WI-3) the
Idempotency Redis key shape/cap divergence across WMS services is either
normalized or its per-service divergence is explicitly documented as
intentional.

Project-internal — all paths under `projects/wms-platform/specs/`.

---

# Scope

## In Scope

**WI-1 — W9 (gateway route table internal inconsistency, spec-only).**
`specs/services/gateway-service/architecture.md:91-98` route table lists only
`/api/v1/master/**`, `/api/v1/inventory/**`, `/api/v1/inbound/**`,
`/api/v1/admin/**`, `/webhooks/erp/asn`, actuator. **Outbound is absent** (and
`/webhooks/erp/order`), while `gateway-service/overview.md:37-38` DOES list
`/api/v1/outbound/**` → `outbound-service:8080` and `/webhooks/erp/order`.
`architecture.md:100-103` correctly documents that `notification-service` is an
event-consumer with no REST surface (so notification's absence is **correct and
not in scope**). Reconcile the two gateway specs: add the outbound route +
`/webhooks/erp/order` to `architecture.md`'s authoritative route table to match
`overview.md` (or, if outbound is genuinely not gateway-exposed, correct
`overview.md` instead — decide against `outbound-service` spec + the actual
outbound contract; the route table that matches the outbound service's declared
external surface is canonical).

**WI-2 — W18 (stale "Open Items" framing, spec-only).**
`specs/services/master-service/architecture.md:300-313` and
`specs/services/outbound-service/architecture.md:631-658` still read
"## Open Items (Before First Implementation Task) — These must be completed
before any TASK-BE-* … is moved to tasks/ready/" and list files (domain-model.md,
the HTTP/event/webhook contracts, idempotency.md, external-integrations.md,
workflows/, state-machines/, sagas/) as undone — **all of which now exist**
(verified by Glob). `master-service/architecture.md:186-187` also still says
"idempotency.md (to be authored before implementation)" despite it existing.
Convert both sections to the **retrospective ✅ backfill-audit format that
`inventory-service/architecture.md:577-585` already uses** (each item gets a
✅ done / ⚠️ partial / ❌ outstanding status; the "must be completed before"
framing removed). `inventory-service` is already in that format — STALE for
that service, do not touch it; it is the reference pattern.

**WI-3 — W15 (Idempotency Redis key shape/cap drift, DECISION-bearing).**
Verified divergence across services:
- master: `master:idem:{SHA-256(idempotencyKey || ":" || method || ":" || path)}`
  — hashed flat key, 64-char raw cap (`master-service/idempotency.md:39,83`)
- inventory / inbound / admin: `{service}:idempotency:{method}:{path_hash}:{idempotency_key}`
  — raw key appended, 128-char cap (`*/idempotency.md`)
- outbound: same shape as inventory but **255-char cap**
  (`outbound-service/idempotency.md:72-73,88`)
- notification: no Redis key — Postgres-backed
  `delivery_idempotency_key = sha256(eventId+channelId+recipient)`
  (`notification-service/idempotency.md:26`) — this is a legitimately different
  mechanism (event-consumer, not request idempotency) and is likely **out** of
  the normalization set; confirm.
Decide: **(A) Normalize** the request-idempotency services (master, inventory,
inbound, outbound, admin) onto one key shape + one cap, OR **(B) Document the
divergence as intentional** with a per-service rationale (e.g. master's hash
form for key-length safety vs others' debuggable raw form) in a single
authoritative place. Record the decision + rationale; if it sets a portfolio
idempotency convention, raise an ADR rather than deciding WMS-only.

## Out of Scope

- W2 (eventVersion int-vs-string) — STALE: all 6 WMS event contracts now
  integer `1` (TASK-BE-144).
- W8 (9 dead-references) — STALE: sagas/, state-machines/,
  external-integrations.md, notification idempotency.md, runbooks/dlt-replay.md
  all verified to exist (TASK-BE-151/156).
- `inventory-service/architecture.md` Open Items — STALE, already in
  retrospective ✅ format; it is the WI-2 *reference*, not an edit target.
- notification-service idempotency mechanism (Postgres) — different concern;
  not part of the WI-3 normalization set (confirm and exclude).
- Any `apps/` production code / test (spec-only — WI-3 aligns the *spec*; any
  code conformance is a separate downstream task if normalization is chosen).

---

# Acceptance Criteria

- [ ] WI-1: `gateway-service/architecture.md` route table and
      `gateway-service/overview.md` agree on the outbound route +
      `/webhooks/erp/order` (no internal contradiction); notification's
      no-REST-surface note retained.
- [ ] WI-2: master & outbound `architecture.md` Open Items sections use the
      `inventory-service` retrospective ✅/⚠️/❌ format; no "must be completed
      before … moved to tasks/ready/" framing for files that exist; the
      `master-service:186-187` "to be authored" line corrected.
- [ ] WI-2: ADR-MONO-012 canonical `architecture.md` form preserved (the
      reformatted Open Items section does not disturb canonical structure).
- [ ] WI-3: a decision (A normalize / B document) is recorded with rationale;
      if (A) the 5 request-idempotency services' specs state one shape + one
      cap; if (B) a single authoritative spec documents each service's shape +
      cap + why they differ. notification's Postgres mechanism explicitly
      scoped out.
- [ ] WI-3: if a portfolio-wide idempotency convention is set, an ADR is raised
      (not decided WMS-only).
- [ ] `validate-rules` clean; no `apps/` diff.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> read `projects/wms-platform/PROJECT.md` and load `rules/common.md` +
> declared domain/trait files. Determine each target service's Service Type
> from its `architecture.md` and read the matching
> `platform/service-types/<type>.md`.

- `specs/services/gateway-service/architecture.md` (L91-103), `…/overview.md`
  (L37-38) — WI-1.
- `specs/services/master-service/architecture.md` (L300-313, L186-187),
  `specs/services/outbound-service/architecture.md` (L631-658) — WI-2 targets.
- `specs/services/inventory-service/architecture.md` (L577-585) — WI-2
  reference pattern (do not edit).
- `specs/services/{master,inventory,inbound,outbound,admin}-service/idempotency.md`
  — WI-3 divergence sources.
- `specs/services/notification-service/idempotency.md` (L26) — WI-3 excluded
  mechanism (confirm scope-out).
- `specs/contracts/http/outbound-service-api.md`,
  `specs/contracts/webhooks/erp-order-webhook.md` — WI-1 outbound surface
  authority.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — primary (WI-1/WI-2).
- `.claude/skills/validate-rules/SKILL.md` — post-check.

---

# Related Contracts

- `specs/contracts/http/outbound-service-api.md`,
  `specs/contracts/webhooks/erp-order-webhook.md` (WI-1 — determine canonical
  outbound external surface; no envelope change).
- No event contract touched (W2 STALE, excluded).

---

# Target Service

- `gateway-service` (WI-1), `master-service` + `outbound-service` (WI-2),
  `master/inventory/inbound/outbound/admin-service` idempotency specs (WI-3)

---

# Architecture

No architecture-style change. WI-2 reformats an existing section to the
already-established retrospective pattern while preserving ADR-MONO-012
canonical form.

---

# Implementation Notes

1. WI-1/WI-2 are mechanical spec reconciliation; WI-3 is decision-bearing.
   Bundle or split per `feedback_pr_bundling` — WI-3's ADR (if raised) may
   warrant its own PR.
2. WI-1 canonical direction: the route table matching the outbound service's
   *declared external contract surface* wins; align the other gateway spec to
   it, do not just copy `overview.md` blindly.
3. WI-2: copy `inventory-service`'s retrospective-format *structure*; verify
   each listed file's existence via Glob before marking ✅.
4. WI-3 is a spec decision only — do NOT change `apps/` idempotency code here
   even if (A) normalize is chosen; that conformance is a separate downstream
   task.
5. "(writing) → ready" stage — this spec PR adds the task to `ready/` + WMS
   INDEX only.

---

# Edge Cases

- WI-1: `/webhooks/erp/asn` (inbound) is present and correct — do not remove it
  while adding `/webhooks/erp/order` (outbound).
- WI-2: an Open Items entry that genuinely is NOT done (⚠️/❌) → mark it
  honestly, do not blanket-✅; the point is accuracy, not green-washing.
- WI-3: master's 64-char cap may be a deliberate Redis-key-length safety choice
  vs outbound's 255 — capture that as the rationale if (B), or reconcile the
  cap consciously if (A).

# Failure Scenarios

- WI-1 adds outbound to the route table but a downstream guard elsewhere still
  404s `/api/v1/outbound/**` per the old table → spec says one thing,
  (eventual) impl another; ensure the route table is the single authority.
- WI-2 marks a file ✅ that does not exist → re-introduces a dead reference;
  Glob-verify each.
- WI-3 normalizes the spec (A) without flagging the downstream code-conformance
  task → spec/code drift in the other direction.

---

# Test Requirements

- Spec-only. Verification:
  - WI-1: gateway architecture.md ↔ overview.md route agreement (grep/diff).
  - WI-2: no "must be completed before" framing for existing files; format
    matches inventory-service; Glob-verified ✅ marks.
  - WI-3: single shape+cap (A) or single documented rationale (B); notification
    scoped out.
  - `validate-rules` clean; no `apps/` diff.

---

# Definition of Done

- [ ] WI-1 gateway route specs consistent (outbound + erp/order added/reconciled)
- [ ] WI-2 master/outbound Open Items in retrospective ✅ format, canonical
      architecture.md form preserved
- [ ] WI-3 decision recorded + executed (normalize or document); ADR raised if
      portfolio-wide; notification excluded
- [ ] `validate-rules` clean; no `apps/` diff
- [ ] Branch: `task/be-293-wms-spec-drift` (substring `master` 금지)
- [ ] Spec PR adds this file to `ready/` + WMS INDEX ready list only
- [ ] Ready for review
