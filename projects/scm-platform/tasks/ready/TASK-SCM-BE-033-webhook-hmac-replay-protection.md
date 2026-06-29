# Task ID

TASK-SCM-BE-033

# Title

Upgrade procurement webhook signature verification to HMAC-SHA256 + timestamp + nonce replay protection (integration-heavy I6 "Partial" → full)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

# Goal

`procurement-service` `WebhookSignatureVerifier` currently does a **fixed shared-secret comparison** with no timestamp/nonce — its own javadoc flags this: *"HMAC + timestamp + replay protection is tracked as a v2 follow-up (`rules/traits/integration-heavy.md` I6 — currently 'Partial')."* The 2026-06-29 discovery sweep confirmed **no ticket exists** for the I6 upgrade. This task closes the I6 gap: replace the plain-secret check with HMAC-SHA256 over the raw body + a signed timestamp (freshness window) + nonce-based replay rejection.

After this task, the procurement inbound webhook authenticates via HMAC-SHA256 signature, rejects stale (outside the freshness window) and replayed (seen-nonce) requests, and `integration-heavy.md` I6 moves from "Partial" to satisfied.

# Scope

## In Scope

- Replace `WebhookSignatureVerifier`'s shared-secret equality with **HMAC-SHA256(raw-body, secret)** constant-time comparison.
- Add **timestamp freshness** validation (signed timestamp header; reject outside a configurable window, e.g. ±5 min).
- Add **nonce replay protection** (reject a previously-seen nonce within the window — Redis or equivalent short-TTL store).
- Update `specs/services/procurement-service/architecture.md` (+ webhook contract if one exists) to describe the HMAC/timestamp/nonce scheme **before** implementation (specs win).

## Out of Scope

- Other inbound surfaces / other SCM services.
- Rotating-secret / multi-key management (single shared secret is fine for v2; multi-key is a later increment if needed).

---

# Acceptance Criteria

- [ ] **AC-1** — Webhook requests are authenticated by HMAC-SHA256 over the raw body; an invalid/missing signature → 401/403 (per the service's error convention).
- [ ] **AC-2** — A request with a timestamp outside the freshness window is rejected (replay-by-delay blocked).
- [ ] **AC-3** — A request reusing a nonce already seen within the window is rejected (replay-by-repeat blocked); a fresh nonce passes.
- [ ] **AC-4** — Signature comparison is constant-time (no early-exit timing leak).
- [ ] **AC-5** — `integration-heavy.md` I6 status for procurement updated from "Partial"; `architecture.md` (+ contract) updated first.
- [ ] **AC-6** — Unit tests (valid/invalid sig, stale ts, replayed nonce, constant-time) + a Testcontainers integration test for the replay-store path; `:integrationTest` GREEN on CI.

---

# Related Specs

- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `rules/traits/integration-heavy.md` (I6 webhook security)
- `platform/security-*` / `platform/testing-strategy.md`

# Related Contracts

- `projects/scm-platform/specs/contracts/http/` webhook entry (if present — add/extend the signature-header spec).

---

# Target Service

- `procurement-service` (scm-platform)

---

# Edge Cases

- Body must be HMAC'd over the **raw** bytes (pre-deserialization) — a re-serialized body changes the digest.
- Clock skew: the freshness window must tolerate reasonable skew without opening a wide replay window.
- Nonce store TTL must be ≥ the freshness window so a replay cannot outlive nonce memory.

# Failure Scenarios

- HMAC over a parsed/re-serialized body → valid requests fail (digest mismatch).
- Nonce TTL < freshness window → a replay submitted after nonce eviction but within freshness passes (replay hole).
- Non-constant-time compare → signature timing oracle.

---

# Test Requirements

- Unit: valid/invalid signature, stale timestamp, replayed vs fresh nonce, constant-time compare.
- Integration (Testcontainers): replay-store (nonce) rejection across the real store.

---

# Definition of Done

- [ ] AC-1…AC-6 satisfied
- [ ] Specs updated before code
- [ ] Ready for review
