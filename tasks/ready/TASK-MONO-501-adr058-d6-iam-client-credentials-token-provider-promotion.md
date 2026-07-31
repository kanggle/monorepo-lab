# Task ID

TASK-MONO-501

# Title

ADR-MONO-058 D6 — promote `IamClientCredentialsTokenProvider` (canonical, already-fixed shape) to `libs/java-security`

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`ADR-MONO-058` § 2 D6 found 7 copies of `IamClientCredentialsTokenProvider` across 3 projects (iam, ecommerce, fan), already diverged in ways that matter: `fan-platform/community-service` generalized the hardcoded `scope` into a constructor parameter (strictly better shape), and `ecommerce/batch-worker` carries a UTF-8-encoding fix (RFC 7617 requires UTF-8 for HTTP Basic credentials) plus explicit connect/read timeouts that `ecommerce/product-service` and several iam copies still lack. This is a **live defect distributed unevenly**, not just duplication. Promote the canonical, already-fixed version to `libs/java-security` so per-service adoption closes the defect everywhere at once instead of 7 separate fixes.

---

# Scope

## In Scope

- A single canonical `IamClientCredentialsTokenProvider`-equivalent class in `libs/java-security`, built from the best-of-breed shape: `fan-platform/community-service`'s parameterized `scope` constructor + `ecommerce/batch-worker`'s UTF-8 Basic-auth encoding fix + explicit connect/read timeouts (configurable, not hardcoded — different consumers may need different values).
- Read all 7 existing copies first (iam's copies, `ecommerce/batch-worker`, `ecommerce/product-service`, `fan-platform/community-service`, and any others `grep`-confirmed) to verify this task's understanding of "best-of-breed" against the actual current code, not just the ADR's summary.
- Unit tests: token acquisition happy path, Basic-auth header encoding (assert UTF-8, not platform-default charset — this is the exact defect being closed), timeout configuration honored.

## Out of Scope

- Per-service adoption (iam/ecommerce/fan each get their own task; this task only lands the shared class).
- Any other client-credentials-adjacent scaffolding not named in D6.

---

# Acceptance Criteria

- [ ] Canonical class lands in `libs/java-security`, framework-neutral, no project names in class/method names (`HARDSTOP-03`).
- [ ] UTF-8 Basic-auth encoding verified by an explicit unit test asserting the byte-level encoding (not just "compiles"), since this is the specific bug (RFC 7617) motivating the promotion.
- [ ] Connect/read timeouts are constructor/builder parameters with no default that reproduces the "no timeout at all" defect the ADR flags.
- [ ] `./gradlew :libs:java-security:test` passes.
- [ ] No existing service modified by this task.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `rules/common.md` (this is a shared-library-only task; treat `libs/` per `platform/shared-library-policy.md`'s Decision/Ownership Rule, not a project's own rule layers).

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § 2 D6, § 6 item 1
- `platform/shared-library-policy.md`
- `tasks/done/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md`

---

# Related Contracts

None — internal library API; no wire-format or event contract change (this is an outbound OAuth2 client-credentials call, not an inbound API this repo controls).

---

# Target Service

- `libs/java-security` (shared library — reactive/servlet-neutral per the audit; verify none of the 7 copies actually depend on a servlet-only or reactive-only HTTP client type before placing it here, since `libs/java-security` currently has no reactive/servlet split of its own).

---

# Architecture

N/A — library module.

---

# Implementation Notes

- This is explicitly framed by the ADR as the smallest, lowest-risk D-item and closes a live defect — per § 6 item 1, this should be one of the first ADR-058 items actually implemented (non-binding suggestion, but a reasonable pick given zero adoption of any D-item has happened yet for iam/ecommerce and only D1/D2/D5 have landed for fan).
- Verify the HTTP client each of the 7 copies currently uses (`RestClient`, `WebClient`, raw `HttpClient`, etc.) before deciding the promoted class's own client dependency — forcing a client-type change onto every adopter as a side effect of adopting the shared token provider would be an unannounced behavior/dependency change.

---

# Edge Cases

- If any of the 7 copies has diverged further since the 2026-07-29 audit (new scopes, new auth params), reconcile against current code, not the ADR's snapshot description.

---

# Failure Scenarios

- Promoting a version that reproduces the platform-default-charset Basic-auth bug (instead of the UTF-8 fix already present in `ecommerce/batch-worker`) would re-introduce the defect this task exists to close everywhere at once — verify the fix explicitly via the required unit test, don't assume it's inherited by copy-paste.
- Hardcoding one project's `scope` value instead of parameterizing it (the `fan-platform/community-service` shape) would violate the Ownership Rule and make the promoted class unusable by the other two projects.

---

# Test Requirements

- Unit tests: token acquisition, UTF-8 Basic-auth header byte assertion, timeout parameter propagation, parameterized `scope`.

---

# Definition of Done

- [ ] Canonical class landed in `libs/java-security` with passing unit tests including the UTF-8 encoding assertion
- [ ] No adopting service touched by this PR
- [ ] Task moved to `done`, referencing the per-project adoption tasks (iam, ecommerce, fan) it unblocks
