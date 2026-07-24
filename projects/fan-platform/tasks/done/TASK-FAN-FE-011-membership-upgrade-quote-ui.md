# Task ID

TASK-FAN-FE-011

# Title

fan-platform-web: tier-aware payment amount + MEMBERS_ONLY → PREMIUM upgrade quote UI (prorated credit)

# Status

done

# Owner

frontend

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

Frontend half of the tier upgrade/proration feature (TASK-FAN-BE-032), merged
atomically with it. Two changes:

1. **Tier-aware payment amount** — the PortOne `totalAmount` must equal what the
   backend charges. Backend pricing is now tier-aware (MEMBERS_ONLY 7,900 /
   PREMIUM 17,900/month) instead of the flat 9,900 currently hard-coded in
   `MONTHLY_CHARGE_KRW`. The checkout helper must charge the tier's price.
2. **Upgrade quote UI** — when a fan holds an ACTIVE MEMBERS_ONLY membership and
   chooses PREMIUM, this is an **upgrade**: fetch the prorated quote
   (`GET /api/v1/memberships/upgrade-quote?tier=PREMIUM&planMonths=N`), show the
   credit ("멤버스전용 잔여 크레딧 −₩X,XXX → ₩Y,YYY 결제"), and open the PortOne
   window for the **prorated** amount. On success the members-only is superseded
   server-side.

---

# Scope

## In Scope

- `features/membership/lib/portone-checkout.ts`: replace the flat `MONTHLY_CHARGE_KRW`
  with a tier→price map (MEMBERS_ONLY 7900 / PREMIUM 17900) and accept an explicit
  `amountKrw` (so an upgrade can pass the quoted, prorated amount rather than the
  list price).
- `features/membership/api/actions.ts` (or a new fetcher): `getUpgradeQuote(tier,
  planMonths)` → `{ chargeMinor, creditMinor, supersedesMembershipId, listPriceMinor }`.
- `SubscribePanel`: when the PREMIUM card is chosen and the fan holds active
  MEMBERS_ONLY (`heldActiveTiers` already passed), fetch the quote, render the
  credit line + the CTA as "프리미엄으로 업그레이드", and open PortOne for
  `quote.chargeMinor`. The plain (non-upgrade) path opens for the tier list price.
- Tests: `subscribe-panel.test.tsx` — upgrade case (holds MEMBERS_ONLY → PREMIUM
  card shows credit + "업그레이드" + checkout uses the quoted amount); plain case
  uses the tier list price; SDK + quote mocked. FE-009 tier-superset assertions
  preserved. (FE-009 already suppresses MEMBERS_ONLY when PREMIUM is held.)

## Out of Scope

- Backend proration/supersede — TASK-FAN-BE-032.
- Downgrade UI.
- Renew UI amount already follows the tier via the shared helper (verify only).

---

# Acceptance Criteria

- [ ] PortOne `totalAmount` = MEMBERS_ONLY 7,900×N / PREMIUM 17,900×N for a plain
      subscribe (matches the backend so verification passes).
- [ ] Holding active MEMBERS_ONLY + choosing PREMIUM: the card shows the prorated
      credit + charge and a "업그레이드" CTA; PortOne opens for `quote.chargeMinor`;
      on success the members-only is superseded (verified via the refreshed list).
- [ ] No active MEMBERS_ONLY: PREMIUM opens for the full 17,900×N (no quote credit).
- [ ] `tsc` + `next lint` + `vitest` + `next build` green (SDK + quote mocked).

---

# Related Specs

- `specs/services/fan-platform-web/architecture.md`

# Related Contracts

- `specs/contracts/http/membership-api.md` (§ Subscribe pricing, § Upgrade Quote)

---

# Target App

- `fan-platform-web`

---

# Edge Cases

- Quote fetch fails → fall back to the full list price (do not block the purchase);
  surface a soft notice. The backend still re-verifies + supersedes.
- Charge quoted as 0 (credit ≥ premium) → skip the payment window, call subscribe
  directly (backend treats a 0-charge upgrade as approved) OR show "₩0 — 크레딧으로
  전액 충당" then a confirm; keep consistent with BE-032's 0-charge handling.
- Members-only expired (read-time inactive) → not an upgrade → full price (backend
  quote returns list price).

---

# Failure Scenarios

- Sending the list price to PortOne on an upgrade → overpays and the backend (which
  expects the prorated charge) declines the amount → wrongful decline. Use the
  quote's `chargeMinor`.
- Hard-coding a single flat amount → mismatch with tier-aware backend → decline.

---

# Test Requirements

- Unit `subscribe-panel.test.tsx`: plain PREMIUM → checkout amount 17,900; upgrade
  (holds MEMBERS_ONLY) → quote fetched, credit shown, checkout amount = quoted
  charge; MEMBERS_ONLY → 7,900. FE-009 superset assertions preserved.

---

# Definition of Done

- [ ] Implementation completed (tier amount + quote UI + upgrade CTA)
- [ ] vitest + tsc + lint + build green (mocked)
- [ ] Merged atomically with TASK-FAN-BE-032
- [ ] Ready for review
