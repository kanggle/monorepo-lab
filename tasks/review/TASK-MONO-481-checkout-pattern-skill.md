# Task ID

TASK-MONO-481

# Title

Author the PG checkout-pattern skill/guide (ADR-MONO-056 D3)

# Status

review

# Owner

docs

# Task Tags

- docs

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

[ADR-MONO-056](../../docs/adr/ADR-MONO-056-payment-gateway-abstraction.md) D3.
The frontend checkout stays a **documented per-project pattern** (strict project
boundary — no cross-project npm package). Capture the pattern so a new site
implements checkout consistently without a shared FE package.

Independent of TASK-MONO-478 (can be authored any time), but most useful once the
`libs/payment` backend port exists to reference.

---

# Scope

## In Scope

- A skill (`.claude/skills/…`) or guide (`docs/guides/…`) documenting: open the
  PG window client-side → obtain `paymentId`/`paymentKey` → server verifies via the
  `libs/payment` port (never trust the client signal, ADR-001).
- Capture the vendor-specific gotchas already learned:
  - **buyer-identity forwarding** — KG이니시스 V2 requires `customer.email`
    (fan-platform FE-012); the client must forward the signed-in buyer's identity.
  - `NEXT_PUBLIC_*` PG keys are **build-time inlined** — rebuild after key change.
  - the client amount MUST equal the backend-verified charge (tamper guard).

## Out of Scope

- A shared npm package (ADR §C — deferred).
- Any code change to existing web apps.

---

# Acceptance Criteria

- [ ] Skill/guide exists and is indexed (`.claude/skills/INDEX.md` if a skill).
- [ ] Documents the verify-boundary + buyer-identity + build-time-inline + amount-match
      gotchas with pointers to the canonical ADRs.

---

# Related Specs

- `docs/adr/ADR-MONO-056-payment-gateway-abstraction.md`
- `projects/fan-platform/docs/adr/ADR-001-real-pg-portone-verification-boundary.md`

# Related Contracts

- None.

---

# Edge Cases

- If authored as a skill under `.claude/skills/`, respect the skills INDEX rule
  (a rule in one place only = no rule) and add the INDEX row.

# Failure Scenarios

- A guide that omits the buyer-identity gotcha → the next site rediscovers the
  KG이니시스 email rejection the hard way (FE-012 was exactly that).

---

# Definition of Done

- [ ] Skill/guide authored + indexed
- [ ] Ready for review

Analysis + implementation model: **Sonnet** (docs).
