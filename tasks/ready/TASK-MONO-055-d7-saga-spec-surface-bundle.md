# Task ID

TASK-MONO-055

# Title

ADR-MONO-005 § D7 spec surface bundle — 6 service `architecture.md` "Saga / Long-running Flow" section + 2 rule pointer edits

# Status

ready

# Owner

backend / monorepo

# Task Tags

- spec
- rules
- saga
- adr-followup

---

# Goal

Operationalise [ADR-MONO-005](../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) § D7 — the **specification surface** that makes the policy enforceable on future PRs and gates the ADR's PROPOSED → ACCEPTED transition (alongside TASK-BE-139).

The ADR itself (PR #358, merged 2026-05-11) ships the policy narrative only. The per-service `architecture.md` declarations + rule pointers were deferred to this task per the `tasks/INDEX.md` PR Separation Rule (and to keep each PR focused on a single concern).

---

# Scope

## In Scope

### 6 per-service `architecture.md` edits — new "Saga / Long-running Flow" section

Each section is a compact table declaring: flow name, category (A/B/C/D), grace / poll / cap / backoff (whichever apply), metric names, escalation event (Category A), compliance status (Compliant / Gap with follow-up task pointer). Pointer to ADR-MONO-005 at the top.

| Service | Category | Compliance | Source-of-truth for declared values |
|---|---|---|---|
| `projects/wms-platform/specs/services/outbound-service/architecture.md` | **A** (orchestrated multi-step saga) | Compliant — reference impl | `outbound.saga.sweeper.{threshold-seconds:300,fixed-delay-ms:60000,max-attempts:5,batch-size:100}`, metric `outbound.saga.sweeper.{run.count,recovery.fired,exhausted.count}`, event `outbound.alert.saga.recovery.exhausted` |
| `projects/wms-platform/specs/services/notification-service/architecture.md` | **C** (single-step retry+DLT) | Compliant — reference impl | `wms.notification.delivery.{backoff-seconds:1,5,30,120,600, retry-poll-interval-ms:5000}`, cap 5 attempts, error code `DELIVERY_RETRY_EXHAUSTED`, outbox terminal `notification.delivered.v1 outcome=FAILED_RETRY_EXHAUSTED` |
| `projects/wms-platform/specs/services/inventory-service/architecture.md` | **D** (TTL expiry sweep) | Compliant — reference impl | `inventory.reservation.ttl-job.{interval-ms:60000,batch-size:200}`, terminal `RELEASED` via `ReservationExpiryJob.releaseExpired` |
| `projects/scm-platform/specs/services/procurement-service/architecture.md` | **B** (synchronous external) | Compliant — reference impl | `@CircuitBreaker(supplier)` 50 % / 10-call window, `@Retry(supplier)` 3 attempts exp+jitter, `@Bulkhead(supplier)` 20 concurrent, fail-CLOSED `SUPPLIER_UNAVAILABLE` HTTP 503 |
| `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` | **A** (choreographed) | **Gap** — Scenario B (stuck-detector deferred → TASK-BE-138 DEFERRED) | No orchestrator row in v1; `PAYMENT_PENDING` rows queryable by ops |
| `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` | **B** (synchronous external × 2) | **Gap** — TASK-BE-139 READY (gates ADR ACCEPTED) | `TossPaymentsAdapter.confirmPayment` + `cancelPayment` both unwrapped today |

### 2 rule pointer edits (1 line each)

- `rules/traits/transactional.md` § Required Artifacts — add: "Multi-step sagas (Category A per ADR-MONO-005) MUST follow § D3 (sweeper + escalation event + metric naming)."
- `platform/event-driven-policy.md` § Consumer Rules — add: "Saga escalation events MUST follow the `<service>.alert.saga.recovery.exhausted` topic name (ADR-MONO-005 § D3)."

## Out of Scope

- Production code changes (Toss Payments R4j wrap is **TASK-BE-139**, order stuck-detector is **TASK-BE-138**, reservation metric is **TASK-BE-140**).
- Library abstraction (rejected in ADR § 3).
- ADR amendments — the ADR's text is settled; this task is purely the D7 surface.
- New escalation events for Category C / D — ADR § D5 explicitly chooses to reuse the existing terminal artefacts (notification's outbox event with `outcome=FAILED_RETRY_EXHAUSTED`; inventory's terminal `RELEASED`).

---

# Related Specs

- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` (§ D7 is the source of this task)
- `rules/traits/transactional.md` § Required Artifacts (target of one pointer edit)
- `platform/event-driven-policy.md` § Consumer Rules (target of one pointer edit)
- Each of the 6 service `architecture.md` files listed in Scope

# Related Contracts

None directly modified. The escalation event schema `<service>.alert.saga.recovery.exhausted` is declared in ADR § D3 and already realised by outbound-service (`SagaRecoveryExhaustedEvent`); future Category A services will use the same shape.

---

# Acceptance Criteria

- AC-01: All 6 service `architecture.md` files carry a new section titled "Saga / Long-running Flow" with the agreed compact table format (flow name, category, declared values, status), placed at a coherent position relative to existing sections (typically just before `## Change Rule` for the minimal ecommerce files, or in topical position for the richer wms / scm files).
- AC-02: Each section's first line is a Markdown link to `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`.
- AC-03: `rules/traits/transactional.md` § Required Artifacts contains the new line referencing ADR-MONO-005 § D3.
- AC-04: `platform/event-driven-policy.md` § Consumer Rules contains the new line referencing the escalation event topic name.
- AC-05: `./gradlew check` is unchanged from main baseline (spec / docs only).
- AC-06: Conventional Commit scope: `feat(rules)` (the rule pointer edits dominate) — single bundled PR per `feedback_pr_bundling.md`.
- AC-07: After merge, ADR-MONO-005's verification § 4.3 condition 2 is satisfied. Combined with TASK-BE-139 (gate 1/2), the ADR can transition PROPOSED → ACCEPTED.

---

# Edge Cases

- **EC-01**: Service `architecture.md` already carries narrative on its saga (e.g. outbound-service `## Outbound Saga (the heart of this service)` + `## Saga Sweeper (recovery)`). Resolution: keep the existing rich sections; the new "Saga / Long-running Flow" section is a *compact summary* declaring the policy-relevant values in one place. The pre-existing detail sections continue to own the narrative — they don't conflict, they elaborate.
- **EC-02**: Minimal ecommerce architecture.md (order-service / payment-service) doesn't have an `## Outbox` or `## Event Publication` section structured the same way as wms. Resolution: place the new section in the same compact form regardless; the table format is uniform across all 6 services.
- **EC-03**: ADR-MONO-003 D4 churn-clock — this PR touches `rules/traits/transactional.md` + `platform/event-driven-policy.md`. Per the D4 OVERRIDE precedent (memory `project_monorepo_template_strategy.md`), single-line cross-reference additions are not the "structural shared-library churn" that D4 was designed to gate. Phase 5 re-evaluation date is **not** reset.

---

# Failure Scenarios

- **FS-01**: Reviewer prefers a different section title (e.g. "Saga Compliance" instead of "Saga / Long-running Flow"). Resolution: title is cosmetic — adjust uniformly across all 6 files in a fix-up commit if requested.
- **FS-02**: One of the 6 services' current values drift from ADR's stated defaults (e.g. a service uses cap = 3 instead of 5). Resolution: declare the actual value with a note pointing to either a deviation rationale OR a follow-up cap-alignment task. The audit during this task should surface any such drift.
- **FS-03**: Future Category A services don't naturally inherit the section template. Mitigation: this task explicitly establishes the template; a `.claude/skills/messaging/` skill could later codify it, but is out of scope here.

---

# Notes / Open Questions

- Whether to add a uniform "Saga / Long-running Flow" section to services that **don't** currently have any such flow (e.g. `master-service`, `admin-service`, `gateway-service`). Decision: NO — the section is required only for services owning at least one in-scope flow per the ADR audit. Empty declarations create noise.
- Whether to also add the section to GAP services (auth-service, etc.). Decision: NO for now — GAP's saga-shaped flows (RT rotation, OAuth callback) are documented in ADR-003 / 004 (project-local). They can opt in later if they realise a Category A/B/C/D flow.

---

# Recommendation

진행 권장 (분석=Opus 4.7 / 구현=Opus — cross-project spec surface, 8 file edits across 3 projects + 2 shared rule files. D4 OVERRIDE applies. ADR-MONO-005 ACCEPTED gate 1/2.)
