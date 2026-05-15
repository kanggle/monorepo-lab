# Task ID

TASK-MONO-106

# Title

Register `EXTERNAL_TIMEOUT` in shared `platform/error-handling.md` — close TASK-BE-293 retrospective Open Item #11 ⚠️ (referenced-but-unregistered error code)

# Status

done

# Owner

backend

# Task Tags

- refactor

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

Resolve the honest ⚠️ that TASK-BE-293's retrospective Open Items audit
surfaced (green-wash-prohibited principle):
`projects/wms-platform/specs/services/outbound-service/architecture.md` Open
Item #11 states `EXTERNAL_TIMEOUT` is referenced but **not registered** in the
shared `platform/error-handling.md` registry, and that the original
"(already global)" claim is stale — explicitly nominating a separate
`TASK-MONO-*` (shared path) to register it.

`EXTERNAL_TIMEOUT` is genuinely **referenced** (not unused): the
integration-heavy trait Required Artifact mandates it
(`rules/traits/integration-heavy.md:89`) and the outbound saga spec uses it
(`projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md:732`).
The correct disposition is therefore **register it** (not "drop the
reference").

After this task: `EXTERNAL_TIMEOUT` is in the shared registry, the
`platform/error-handling.md` ↔ spec drift is closed, and outbound Open Item
#11 flips ⚠️ → ✅.

Monorepo-level — touches shared `platform/error-handling.md` (CLAUDE.md →
repo-root `tasks/`, `tasks/INDEX.md` § Root vs Project). It also touches one
project spec (`outbound-service/architecture.md` Open Item #11 status flip)
in the same atomic commit because that spec is the audit that nominated this
task — they must land together (no transiently-stale ⚠️).

---

# Scope

## In Scope

**WI-1 — register `EXTERNAL_TIMEOUT` (shared `platform/error-handling.md`).**

Add `EXTERNAL_TIMEOUT` to the `## Outbound  [domain: wms]` section,
**adjacent to its sibling `EXTERNAL_SERVICE_UNAVAILABLE`** (the de-facto
established placement for this integration-failure code family — the same
section already carries `EXTERNAL_SERVICE_UNAVAILABLE | 503` with a
description distinguishing it from Platform-Common `SERVICE_UNAVAILABLE`).

- HTTP status: **503** — consistent with the sibling
  `EXTERNAL_SERVICE_UNAVAILABLE` (503) and the existing HTTP Status Mapping
  row "Upstream dependency unavailable | 503". The description draws the
  timeout-vs-unreachable operator distinction, mirroring the doc's existing
  `CIRCUIT_OPEN` vs `DOWNSTREAM_ERROR` and `EXTERNAL_SERVICE_UNAVAILABLE` vs
  `SERVICE_UNAVAILABLE` distinction style (a *no response within the call
  deadline after retry exhaustion* signal, distinct from *could not establish
  / circuit OPEN*).

This is the **(B) document-per-established-sibling** disposition: it sets
**no new portfolio convention** (it follows the placement
`EXTERNAL_SERVICE_UNAVAILABLE` already established) and therefore needs **no
ADR** — consistent with the TASK-BE-292/BE-293 meta-principle ((B)
document/accept = no ADR; only (A) normalize/hoist needs an ADR).

**WI-2 — flip outbound Open Item #11 ⚠️ → ✅
(`projects/wms-platform/specs/services/outbound-service/architecture.md`).**

Update the retrospective Open Item #11 status from ⚠️ to ✅, recording that
`EXTERNAL_TIMEOUT` is now registered (this task), and remove the stale
"(already global)" framing residue.

## Out of Scope

- **(A) restructure** — creating a new `Integration-Heavy Trait`
  Platform-Common subsection and hoisting `EXTERNAL_SERVICE_UNAVAILABLE` +
  `EXTERNAL_TIMEOUT` (+ `WEBHOOK_SIGNATURE_INVALID`, `DLQ_RETRY_EXHAUSTED`)
  there to satisfy `platform/error-handling.md` Rule "Codes introduced by a
  trait … belong in the matching Platform-Common subsection, not under the
  domain". That is a **portfolio-wide registry convention change** (moves an
  existing code referenced by multiple projects; alters Platform-Common
  surface + standalone-extraction domain-tagging) → **ADR territory**, a
  separate deferred `TASK-MONO-*` candidate. **Not done here** (scope
  discipline; surfaced honestly, not green-washed).
- `DLQ_RETRY_EXHAUSTED` — listed in `rules/traits/integration-heavy.md:89`
  but **not referenced/consumed anywhere else** (no saga spec, no Open Item,
  no service spec). Registering it now would be speculative authoring of an
  unused code. Out of scope; noted as an adjacent observation, not filed.
- `WEBHOOK_SIGNATURE_INVALID` / `EXTERNAL_SERVICE_UNAVAILABLE` — already
  registered (`error-handling.md` L184 Inbound / L220 Outbound). No change.
- Any production code, contract envelope, or other error code.

---

# Acceptance Criteria

- [ ] WI-1: `grep -n "EXTERNAL_TIMEOUT" platform/error-handling.md` returns a
      registry table row under `## Outbound  [domain: wms]`, adjacent to
      `EXTERNAL_SERVICE_UNAVAILABLE`, with HTTP 503 and a description that
      distinguishes timeout (no response within deadline after retries) from
      `EXTERNAL_SERVICE_UNAVAILABLE` (could not establish / circuit OPEN).
- [ ] WI-1: no new Platform-Common subsection created; no existing code moved
      (the (A) restructure is explicitly NOT performed).
- [ ] WI-2: `outbound-service/architecture.md` Open Item #11 status is ✅
      (not ⚠️), references this task (`TASK-MONO-106`), and the stale
      "(already global)" residue is gone.
- [ ] `grep -rn "EXTERNAL_TIMEOUT"` across the repo: every reference
      (`rules/traits/integration-heavy.md:89`,
      `outbound-service/sagas/outbound-saga.md:732`,
      `outbound-service/architecture.md` Open Item #11) now resolves to a
      registered code — no referenced-but-unregistered occurrence remains.
- [ ] Shared/project edits land in **one atomic commit** (no transiently
      stale ⚠️ between the registry add and the audit-status flip).
- [ ] No new broken links / orphans introduced.

---

# Related Specs

> **Before reading Related Specs**: this is a monorepo-level shared-registry
> task. `platform/error-handling.md` is the single authoritative error
> registry (its own "Change protocol" + "Change Rule"). The WMS project
> context (`projects/wms-platform/PROJECT.md`,
> `rules/traits/integration-heavy.md`) explains *why* the code is required.

- `platform/error-handling.md` — **WI-1 edit target** (shared registry SoT;
  add `EXTERNAL_TIMEOUT` under Outbound `[domain: wms]`, sibling to
  `EXTERNAL_SERVICE_UNAVAILABLE`).
- `projects/wms-platform/specs/services/outbound-service/architecture.md` —
  **WI-2 edit target** (Open Item #11 ⚠️ → ✅).
- `rules/traits/integration-heavy.md:89` — the shared trait Required Artifact
  that mandates `EXTERNAL_TIMEOUT` exist in `platform/error-handling.md`
  (the controlling requirement; not edited).
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md:732`
  — consumer reference confirming the code is genuinely used (not edited).

# Related Skills

- `.claude/commands/refactor-spec.md` — primary (spec/registry drift
  reconciliation).
- `.claude/commands/validate-rules.md` — post-check.

---

# Related Contracts

- None. No HTTP/event envelope changes — only the shared error-code registry
  and a project spec's audit-status line.

---

# Target Service

- Shared registry (`platform/error-handling.md`) + `outbound-service`
  (wms-platform) spec audit-status flip. No service code.

---

# Architecture

No architecture-style change. Registry addition placed per the established
sibling (`EXTERNAL_SERVICE_UNAVAILABLE`); deliberately **not** a registry
restructure (the (A) Platform-Common-subsection hoist is out of scope / ADR
candidate).

---

# Implementation Notes

1. Branch `task/mono-106-external-timeout-error-registry` is based off
   `task/spec-drift-cohort-2026-05-16` (which carries TASK-BE-293's
   retrospective Open Items conversion). WI-2 edits BE-293's ⚠️ Open Item
   #11 → ✅; it logically depends on BE-293's retrospective format, hence
   this base (NOT main / NOT the BE-290/BE-294 line).
2. WI-1: mirror the `EXTERNAL_SERVICE_UNAVAILABLE` row's voice. The
   distinction prose pattern already exists in the doc — reuse it
   (timeout = upstream slow/overloaded, may self-recover, idempotent retry
   reasonable; unavailable = could not establish / circuit shedding).
3. WI-1: do NOT introduce HTTP 504 (a status absent from the doc's HTTP
   Status Mapping table) — that would be a new mapping convention. 503 keeps
   parity with the sibling and the existing "Upstream dependency unavailable
   | 503" row. (504 being arguably more precise is noted as a one-clause
   nuance in the description, not adopted.)
4. WI-2: keep the retrospective Open Items list format BE-293 established
   (✅/⚠️/❌ + candidate-task pointer). Only #11's status + wording changes.
5. Atomic: stage `platform/error-handling.md` + `outbound-service/architecture.md`
   in the same commit so reviewers never see a registered code with a still-⚠️
   audit (or vice-versa).
6. Per `feedback_pr_on_request` + `feedback_pr_bundling`: task file + impl +
   closure on one held branch; **shared `platform/` touched → flag a draft-PR
   question once after push** (do not open a PR unprompted).

---

# Edge Cases

- `EXTERNAL_TIMEOUT` vs `DOWNSTREAM_ERROR` (Platform-Common General, 502 —
  "internal service returned 5xx/timed out"): `EXTERNAL_TIMEOUT` is for
  **third-party** (TMS/ERP/external-WMS) timeouts, `DOWNSTREAM_ERROR` for
  **internal monorepo** services. The description must state this so the two
  are not conflated (same distinction the doc draws for
  `EXTERNAL_SERVICE_UNAVAILABLE` vs `SERVICE_UNAVAILABLE`).
- If the (A) restructure is later pursued via an ADR, `EXTERNAL_TIMEOUT`
  added here moves with `EXTERNAL_SERVICE_UNAVAILABLE` — placing it adjacent
  to the sibling now keeps that future move mechanical (one contiguous block).
- `error-handling.md` "Change Rule"/"Change protocol" require new codes be
  added to this file before use. `EXTERNAL_TIMEOUT` is already *referenced*
  (a pre-existing drift) — this task closes the drift retroactively; note
  this in the impl commit (the code was used ahead of registration; this
  task reconciles, it is not introducing fresh use).

# Failure Scenarios

- WI-1 creates a new `Integration-Heavy Trait` Platform-Common subsection /
  moves `EXTERNAL_SERVICE_UNAVAILABLE` → performs the out-of-scope (A)
  restructure unilaterally without an ADR → portfolio convention change
  smuggled into a drift-close task (the exact anti-pattern BE-292/293's
  meta-principle guards against). Stay (B): add adjacent, move nothing.
- WI-1 registers `EXTERNAL_TIMEOUT` as 504 → introduces an HTTP status not in
  the doc's mapping table = a new mapping convention. Use 503 (sibling
  parity).
- WI-2 flips #11 ✅ but the registry add is omitted / in a separate commit →
  the ✅ is a green-wash (audit claims registered while it is not, or only
  transiently). Atomic single commit is mandatory.
- WI-2 also "resolves" `DLQ_RETRY_EXHAUSTED` by registering it speculatively
  → unused-code authoring; scope creep. Leave it out (documented observation
  only).

---

# Test Requirements

- Spec/registry-only; no unit/integration test. Verification:
  - `grep -n "EXTERNAL_TIMEOUT" platform/error-handling.md` → 1 registry row
    under Outbound `[domain: wms]`, adjacent to `EXTERNAL_SERVICE_UNAVAILABLE`,
    HTTP 503.
  - `grep -rn "EXTERNAL_TIMEOUT"` repo-wide → 0 referenced-but-unregistered
    occurrences (all references resolve to the new registry row).
  - outbound Open Item #11 = ✅, cites `TASK-MONO-106`, no "(already global)"
    residue.
  - `validate-rules` clean (no new broken-link / orphan).

---

# Definition of Done

- [ ] `EXTERNAL_TIMEOUT` registered in `platform/error-handling.md` (Outbound
      `[domain: wms]`, sibling to `EXTERNAL_SERVICE_UNAVAILABLE`, HTTP 503,
      timeout-vs-unreachable distinction prose)
- [ ] No (A) restructure performed; no existing code moved; no new
      Platform-Common subsection
- [ ] outbound Open Item #11 ⚠️ → ✅, references `TASK-MONO-106`
- [ ] Atomic single commit (registry + audit-status flip together)
- [ ] `validate-rules` no new broken-link/orphan
- [ ] Branch: `task/mono-106-external-timeout-error-registry` (off
      `task/spec-drift-cohort-2026-05-16`; substring `master` 금지)
- [ ] Shared `platform/` touched → draft-PR question flagged after push
- [ ] Ready for review
