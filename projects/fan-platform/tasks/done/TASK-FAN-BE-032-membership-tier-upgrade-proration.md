# Task ID

TASK-FAN-BE-032

# Title

membership-service: tier-aware pricing + MEMBERS_ONLY → PREMIUM upgrade with prorated daily credit (supersede)

# Status

done

# Owner

backend

# Task Tags

- code
- test

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

`PREMIUM ⊇ MEMBERS_ONLY` (AccessPolicy.tierGrants), so the correct model is a
**ladder/upgrade**, not two co-held memberships. Today the backend charges a flat
`PRICE_PER_MONTH_MINOR = 9_900` for BOTH tiers (inconsistent with the UI's
decorative 7,900 / 17,900), and a fan who holds MEMBERS_ONLY and subscribes to
PREMIUM ends up holding **both** active rows (the members-only fee is wasted). This
task makes the upgrade a real, prorated upgrade:

1. **Tier-aware pricing** — MEMBERS_ONLY 7,900 / PREMIUM 17,900 per month (aligns
   the charge with the displayed prices; removes the flat 9,900).
2. **Upgrade detection + supersede** — subscribing PREMIUM while holding an active
   MEMBERS_ONLY membership cancels (supersedes) the members-only row so only
   PREMIUM stays active.
3. **Prorated daily credit (차감형)** — the unused portion of the members-only
   membership is credited against the premium charge:
   `charge = PREMIUM_MONTHLY × planMonths − membersRemainingValue`
   where `membersRemainingValue = ceil0(remainingWholeDays) × (MEMBERS_MONTHLY / 30)`
   (whole-day proration so the amount is stable within a day). `charge` is
   floored at 0.

The PortOne payment (portone profile) must equal this server-computed `charge` —
the tamper guard already verifies `paidTotal == amountMinor`, and for an upgrade
`amountMinor` IS the prorated charge (the client cannot underpay). A companion
**upgrade-quote endpoint** lets the frontend (TASK-FAN-FE-011) learn the prorated
amount BEFORE opening the payment window, computed by the SAME method the
subscribe path re-verifies against.

Domain money math lives in a pure, unit-tested helper (no framework deps) so the
quote and the charge are provably the same formula.

---

# Scope

## In Scope

- **Pricing**: replace flat `PRICE_PER_MONTH_MINOR` with a tier-aware source
  (`MEMBERS_ONLY = 7_900`, `PREMIUM = 17_900` minor units/month). Both
  `SubscribeUseCase` and `RenewMembershipUseCase` charge by the membership's tier.
- **Proration helper** (pure): `UpgradeProration` — given `(membersRemainingDays,
  premiumMonthly, membersMonthly, planMonths)` → `{ chargeMinor, creditMinor,
  remainingDays }`. Whole-day, floored-at-0.
- **Upgrade path in `SubscribeUseCase`**: when `cmd.tier() == PREMIUM` AND the fan
  holds an ACTIVE MEMBERS_ONLY membership (read-time active) → compute the prorated
  charge, authorize/verify THAT amount, on approval **cancel the members-only row**
  (state ACTIVE → CANCELED, reason `SUPERSEDED_BY_UPGRADE`) and create the PREMIUM
  membership, all in ONE transaction. Emits the existing `activated` event for the
  premium + a `canceled` event for the superseded members-only.
- **Upgrade-quote read**: `GET /api/fan/memberships/upgrade-quote?tier=PREMIUM&planMonths=N`
  → `{ tier, planMonths, listPriceMinor, creditMinor, chargeMinor, supersedesMembershipId }`.
  Returns the plain (non-prorated) price when there is nothing to supersede.
- `MembershipRepository`: a scoped "find active membership of tier T for
  (account, tenant)" lookup (read-time active) if not already present.
- Contract + architecture doc updates (pricing, upgrade semantics, quote endpoint).
- Tests: `UpgradeProrationTest` (pure math incl. floor-at-0, whole-day, zero
  remaining); `SubscribeUseCaseTest` upgrade branch (supersede + prorated authorize
  amount + both events); a Testcontainers IT exercising the full upgrade
  (members-only active → subscribe premium → members-only CANCELED, premium active,
  prorated charge).

## Out of Scope

- DOWNGRADE (PREMIUM → MEMBERS_ONLY) — not an upgrade; normal subscribe or a later
  task. Do not special-case it here.
- Refund/payout of the credit as money — the credit only reduces the upgrade charge
  (never a negative charge; floored at 0).
- Multi-tier beyond the two existing tiers.
- Frontend — TASK-FAN-FE-011 (tier-aware amount + quote fetch + upgrade UI). Merge
  atomically with this task (the pricing + quote contract are shared).

---

# Acceptance Criteria

- [ ] MEMBERS_ONLY subscribe charges 7,900 × planMonths; PREMIUM subscribe (no
      prior members-only) charges 17,900 × planMonths. Renew charges by the renewed
      tier.
- [ ] Subscribing PREMIUM while holding an ACTIVE MEMBERS_ONLY: the members-only row
      becomes CANCELED (reason SUPERSEDED_BY_UPGRADE), a PREMIUM row is created
      ACTIVE, and the authorized/charged amount equals
      `17,900×planMonths − ceil0(remainingDays)×(7,900/30)` (floored at 0).
- [ ] `upgrade-quote` returns that same `chargeMinor` + `creditMinor` +
      `supersedesMembershipId`; with no active members-only it returns the plain
      price and null supersedes id.
- [ ] Under the `portone` profile the PortOne payment amount must equal the prorated
      `chargeMinor` or it is declined (tamper guard — reuses the amount check).
- [ ] Idempotent replay of the upgrade returns the same premium + does not
      re-supersede / double-charge.
- [ ] `./gradlew :…:membership-service:check` green (pure proration unit + upgrade
      IT via Testcontainers on CI).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 (read
> `PROJECT.md` + rule layers).

- `specs/services/membership-service/architecture.md` (§ State Machine — add the
  upgrade/supersede transition; § pricing)
- `docs/adr/ADR-001-real-pg-portone-verification-boundary.md` (the prorated charge
  is what the portone adapter verifies)

# Related Contracts

- `specs/contracts/http/membership-api.md` (§ Subscribe pricing + upgrade
  semantics; new § Upgrade Quote; `SUPERSEDED_BY_UPGRADE` cancel reason)

---

# Target App

- `membership-service`

---

# Edge Cases

- Members-only already expired (read-time inactive) → NOT an upgrade credit source;
  treat as a plain PREMIUM subscribe (no supersede, full price).
- Remaining days rounds to 0 (expires today) → credit 0 → full premium price.
- Credit ≥ premium charge (long members-only remaining, short premium plan) →
  charge floored at 0 (PortOne amount 0 is a documented degenerate; mock approves,
  and the portone path treats a 0-charge upgrade as a $0 authorization — decide:
  skip PG call when charge==0 and approve, documented).
- Fan holds BOTH members-only and premium (legacy) → subscribing premium again is
  suppressed by FE-009; if it reaches the backend, supersede the members-only.
- Concurrent upgrade + members-only expiry sweep → the supersede runs in the
  subscribe TX with a scoped re-read; the sweep's `activated`/`expired` markers are
  independent.

---

# Failure Scenarios

- Computing the quote and the charge with two different formulas → client pays the
  quote, backend expects a different amount → wrongful decline. Guard: ONE pure
  `UpgradeProration` used by both paths (unit-asserted equal).
- Superseding the members-only but the PG later declines → the whole TX rolls back
  (supersede + create + events are one transaction); the members-only stays active.
- Charging full premium on an upgrade (forgetting the credit) → overcharge; the IT
  asserts the prorated amount.
- Client requesting a lower PortOne amount than the server-computed charge → amount
  mismatch → declined (portone tamper guard).

---

# Test Requirements

- Unit `UpgradeProrationTest`: whole-day proration, floor-at-0, zero remaining,
  credit≥charge, and quote==charge equality.
- Unit `SubscribeUseCaseTest`: upgrade branch supersedes members-only + authorizes
  the prorated amount + emits activated(premium)+canceled(members); non-upgrade
  path unchanged.
- IT (Testcontainers): members-only active → subscribe premium → members-only
  CANCELED(SUPERSEDED_BY_UPGRADE) + premium ACTIVE + prorated paymentRef; quote
  endpoint matches.

---

# Definition of Done

- [ ] Implementation completed (tier pricing + upgrade/supersede + proration + quote)
- [ ] Unit + Testcontainers tests added + passing (CI-safe)
- [ ] Contract + architecture updated
- [ ] Merged atomically with TASK-FAN-FE-011
- [ ] Ready for review
