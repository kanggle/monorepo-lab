# Task ID

TASK-FAN-FE-009

# Title

fan-platform-web: don't offer MEMBERS_ONLY subscribe when PREMIUM is already held (tier superset UI)

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

`PREMIUM ⊇ MEMBERS_ONLY` — a PREMIUM subscription already grants all MEMBERS_ONLY
content (`membership-service` `AccessPolicy.tierGrants`: `heldTier == PREMIUM →
true for any requiredTier`). But the subscribe UI (`SubscribePanel`) offers the
two tiers as independent products: a fan who holds PREMIUM still sees a live
"구독하기" button on the MEMBERS_ONLY card. Buying it is pure waste — it grants
nothing the fan doesn't already have, and creates a redundant second membership
row.

After this task: when the fan actively holds PREMIUM, the MEMBERS_ONLY card no
longer offers a subscribe button — it shows a disabled "프리미엄에 포함됨"
(included in premium) note instead. The reverse is intentionally unchanged: a fan
who holds only MEMBERS_ONLY still sees the PREMIUM card as a live subscribe
(the upgrade path stays open).

This is a pure presentation change in `SubscribePanel` — no backend, contract, or
access-policy change. The server-side tier hierarchy already resolves access
correctly; this only stops the UI from offering a redundant purchase.

---

# Scope

## In Scope

- `features/membership/ui/SubscribePanel.tsx`: derive `hasPremium` from
  `heldActiveTiers`; render the MEMBERS_ONLY card's action slot as a disabled
  "프리미엄에 포함됨" note (instead of the subscribe button) when
  `hasPremium && !isHeld(MEMBERS_ONLY)`.
- `__tests__/subscribe-panel.test.tsx`: update the `['PREMIUM']` case (the
  MEMBERS_ONLY subscribe button is now suppressed) and add a `['MEMBERS_ONLY']`
  case proving the PREMIUM upgrade path is still offered.

## Out of Scope

- `membership-service` `AccessPolicy` / tier hierarchy (already correct —
  PREMIUM ⊇ MEMBERS_ONLY; unchanged).
- Backend supersession/upgrade semantics (subscribing PREMIUM does NOT cancel a
  held MEMBERS_ONLY row — that is option (b), a separate backend decision, not
  taken here).
- The membership page's `heldActiveTiers` derivation (unchanged — it already
  passes the active tiers).
- Payment token field / PG flow (separate track).

---

# Acceptance Criteria

- [ ] When `heldActiveTiers` contains `PREMIUM`: the MEMBERS_ONLY card shows
      "프리미엄에 포함됨" and has NO subscribe button; the PREMIUM card shows
      "이용 중인 멤버십".
- [ ] When `heldActiveTiers` contains only `MEMBERS_ONLY`: the PREMIUM card still
      shows a live "구독하기" button (upgrade path preserved).
- [ ] When `heldActiveTiers` is empty: both cards show "구독하기" (unchanged).
- [ ] `tsc --noEmit`, `next lint`, and `vitest` pass.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read
> `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md`
> and `rules/traits/<trait>.md` matching the declared classification.

- `specs/services/membership-service/architecture.md` (§ Tier hierarchy —
  PREMIUM ⊇ MEMBERS_ONLY)

# Related Contracts

- `specs/contracts/http/membership-api.md` (unchanged — no API change)

---

# Target App

- `fan-platform-web`

---

# Edge Cases

- Fan holds BOTH MEMBERS_ONLY and PREMIUM (legacy/redundant data): both cards
  show "이용 중인 멤버십" (each `isHeld`), no regression.
- Fan holds neither: both cards offer subscribe (unchanged).
- Fan holds only MEMBERS_ONLY: MEMBERS_ONLY shows in-use, PREMIUM offers
  subscribe (upgrade).

---

# Failure Scenarios

- Suppressing the PREMIUM card when MEMBERS_ONLY is held → would block the upgrade
  path (explicitly rejected; only the MEMBERS_ONLY card is suppressed, and only
  when PREMIUM is held).
- Reading the wrong tier direction (suppressing on any held tier) → would hide a
  valid purchase; the test's `['MEMBERS_ONLY']` case guards this.

---

# Test Requirements

- Unit (`subscribe-panel.test.tsx`): `['PREMIUM']` → MEMBERS_ONLY suppressed
  (0 subscribe buttons, "프리미엄에 포함됨" shown); `['MEMBERS_ONLY']` → PREMIUM
  still offered (1 subscribe button); `[]` → both offered (2 buttons, unchanged).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added/updated
- [ ] Tests passing
- [ ] Contracts updated if needed (none expected)
- [ ] Specs updated first if required (none expected)
- [ ] Ready for review
