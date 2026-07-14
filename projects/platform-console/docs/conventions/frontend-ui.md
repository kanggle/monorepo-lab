# Frontend UI Conventions — `console-web`

This is the **canonical home** for three UI conventions that `console-web` already
enforces in practice but that, until this document, existed nowhere in the
repository — only in closed task bodies and agent memory. Agent memory is
demoted to "worked-incident record"; **rule bodies live here**. If this
document and a stale memory note disagree, this document (re-verified against
current code — see the provenance table at the end) wins.

See also: [`docs/onboarding/local-dev.md`](../onboarding/local-dev.md) for
bring-up; this document is the UI-layer counterpart (conventions, not
infrastructure).

---

## 1. Date/time formatting

**Rule**: never call `Date#toLocaleString` / `Date#toLocaleDateString` (or any
other `toLocale*` Date formatter) directly at a call site. Use the shared
helpers in [`shared/lib/datetime.ts`](../../apps/console-web/src/shared/lib/datetime.ts):

```ts
formatDateTime(iso: string | null | undefined, placeholder = '-'): string
// → "2026. 6. 23. 17:59:15" — date + 24-hour time

formatDate(iso: string | null | undefined, placeholder = '-'): string
// → "2026. 6. 23." — date only
```

- **`formatDateTime`** — record timestamps where the *instant* matters
  (created/updated/occurred/fetched-at, audit rows).
- **`formatDate`** — day-granular values where only the *calendar day*
  carries meaning.
- Both are tolerant: empty/absent input → `placeholder` (`"-"` by default);
  an unparseable string is returned verbatim — **never throws**.

### Why the two formatting details are load-bearing, not style

- **`hourCycle: 'h23'`** — ko-KR's `hour12: false` renders midnight as
  `"24:00:00"` instead of `"00:00:00"`. `hourCycle: 'h23'` is the fix. This is
  a **bug fix wearing the clothes of a style rule** — if a future pass drops
  it "for simplicity", midnight timestamps regress to `24:00:00`.
- **`timeZone: 'Asia/Seoul'` (pinned, not the local/browser zone)** — this is
  a KST operator console. SSR renders the initial HTML in the *container's*
  zone (often UTC in CI/prod containers); the browser then hydrates in the
  *user's* zone (KST). If the formatter used the ambient/local zone instead of
  a pinned one, the two renders would produce different text for the same
  timestamp → a React hydration mismatch on every server-seeded detail/list
  view. Pinning the zone makes SSR and client output byte-identical
  regardless of container zone.

### Known exception: UTC day-edge (`formatPromotionDay`)

Promotion start/end dates are stored as **UTC day-edge instants**
(`use-promotion-form.ts` `dayToInstant`: start → `00:00:00Z`, end →
`23:59:59Z` — end-of-day inclusive window). Rendering `23:59:59Z` through the
general, Asia/Seoul-pinned `formatDate` would roll it into the *next* calendar
day under KST's +9 offset — a real bug, not a formatting nuance.

[`features/ecommerce-ops/components/promotion-format.ts`](../../apps/console-web/src/features/ecommerce-ops/components/promotion-format.ts)
carries a **separate** helper, `formatPromotionDay(iso: string): string`, that
formats in `timeZone: 'UTC'` instead of `Asia/Seoul` specifically to keep the
rendered calendar day matching the day the instant was chosen for. It is used
by `PromotionsTable` and `PromotionDetailFields` for `startDate`/`endDate`
only.

**This is a deliberate, narrow exception — not something to "unify" into
`formatDate`.** If the two are merged, promotion end dates will silently
render one day early in the common case (any KST reader). Any change to this
helper must preserve the UTC anchor.

### AC-3 — can "no direct `toLocale*`" be mechanized with zero false positives?

**No — do not add a lint rule for this** (also excluded by this task's Out of
Scope). Two independent reasons:

1. **The helpers themselves, plus the day-edge exception, legitimately call
   `toLocaleString`/`toLocaleDateString`.** A rule would need an exception
   allowlist for `shared/lib/datetime.ts` and
   `features/ecommerce-ops/components/promotion-format.ts` from day one, or it
   is red on the first run.
2. **A much bigger source of false positives, not mentioned in the prior
   agent-memory description**: the codebase calls `Number.prototype.
   toLocaleString()` pervasively — **40+ call sites** across almost every
   `features/*` module (currency formatting `₩{n.toLocaleString('ko-KR')}`,
   plain counts `n.toLocaleString()`, e.g. `OrderDetail.tsx`,
   `ProductDetail.tsx`, `FinanceOverviewScreen.tsx`,
   `IamOverviewSummaryCards.tsx`, `WmsOverviewCountTile.tsx`, and many more).
   These have **nothing to do with date formatting** — they format numbers —
   and are entirely legitimate. A syntax-level `no-restricted-syntax` rule
   keyed on the method name `toLocaleString` cannot distinguish "called on a
   `Date`" from "called on a `number`" without a type-aware lint (TypeScript
   type-checker integration via `@typescript-eslint`), which is materially
   more expensive to build and maintain than a syntax rule, and was explicitly
   ruled out of scope for this task.

Verdict: **not cleanly mechanizable at the cost this task is scoped for.** If
a type-aware guard is wanted later, it needs its own ticket and must special-
case (a) the two files above and (b) numeric `toLocaleString` calls (by
checking the receiver's static type, not the method name alone) — otherwise
it is red on day one, and a day-one-red rule gets disabled rather than fixed.

### Established by

- `#2096` — unify ecommerce date/time formatting (shared `formatDateTime` +
  date-only promo path)
- `#2099` — apply shared date/time convention console-wide
- `#2110` — apply the same convention to `web-store` (see § 4 below)

---

## 2. `DetailHeader` + `<dl>` field order

**Rule**: every detail/edit page header uses the shared
[`shared/ui/DetailHeader.tsx`](../../apps/console-web/src/shared/ui/DetailHeader.tsx)
component. Do not hand-roll an inline `<div className="flex justify-between">
… <Link>…</Link></div>` header.

```ts
interface DetailHeaderProps {
  headingId: string;      // id for the h1; referenced by the section's aria-labelledby
  title: string;          // page title, convention: "OO 상세"
  backHref: string;       // route the back button returns to
  backTestId: string;     // stable test id for the back button
  backLabel?: string;     // default "목록"; pass "상세" when back returns to a parent detail, not a list
  actions?: ReactNode;    // extra action buttons (수정 / 삭제 / 쿠폰 발급 …), rendered left of the back button
}
```

`DetailHeader` was originally introduced under
`features/ecommerce-ops/components/` and **promoted to `shared/ui/`
2026-07-10 (TASK-PC-FE-237 § F)** so non-ecommerce surfaces (org-hierarchy,
IAM) could reuse the same back-to-list affordance and title convention without
duplicating the markup. Behaviour is unchanged across the move.

**`<dl>` field order** (structural convention — there is no shared `<Dl>`
component enforcing this; it is discipline, so it is exactly the kind of rule
that drifts silently without being written down):

> **name → status → identifiers/attributes → dates** (dates are always last)

- The primary label comes first — usually a `name` field; where the entity
  has no name of its own (e.g. an order), its primary identifier fills that
  slot instead.
- Status (rendered via `<StatusBadge>`, § 3) comes immediately after the
  label.
- Domain identifiers and attributes (IDs, prices, counts, discount terms, …)
  follow, in whatever order suits the domain.
- Timestamps (`createdAt`/`updatedAt`, day-granular dates) are always last —
  they are metadata about the record, not part of its identity, and grouping
  them last keeps the scan order (what is it → what state is it in → its
  details → when) consistent across ~20 detail screens.

This exact order is spelled out in-repo already at the call-site level — see
[`PromotionDetailFields.tsx`](../../apps/console-web/src/features/ecommerce-ops/components/PromotionDetailFields.tsx)'s
own doc comment: *"공유 `<dl>`을 **명칭→상태→식별자→날짜** order"*
(`shared <dl> in name → status → identifier → date order`). `ProductDetail.tsx`
(상품명 → 상태 → 가격/상품 ID/셀러 ID → 설명) and `OrderDetail.tsx` (주문 ID →
상태 → 사용자 ID/총액 → 주문일/수정일) follow the same order.

### Established by

- `#2079` — unify ecommerce detail-page back-to-list header (`DetailHeader`
  origin)
- `#2084` / `#2085` — "목록"/"상세" back-button label variants (list-bound vs
  detail-bound back navigation)
- `#2087` — unify ecommerce detail field order (name → status → id/attrs →
  dates)
- TASK-PC-FE-237 § F — promotion of `DetailHeader` to `shared/ui/`

---

## 3. `StatusBadge` + `statusToneClass` escape hatch

**Rule**: render every status chip through
[`@/shared/ui/StatusBadge`](../../apps/console-web/src/shared/ui/StatusBadge.tsx).
Never re-implement the pill markup or a colour map at a call site.

```ts
export type StatusTone = 'success' | 'progress' | 'warning' | 'danger' | 'neutral';

function StatusBadge({ tone = 'neutral', children, className, 'data-testid': testId }): JSX.Element;
function statusToneClass(tone: StatusTone): string; // escape hatch, see below
```

- The palette is **semantic, not per-status**: `StatusBadge` owns five tones
  (`success`/`progress`/`warning`/`danger`/`neutral`) with their Tailwind
  classes and dark-mode variants baked in. A single global
  `status → colour` map is impossible — `ACTIVE` and `SHIPPED` are different
  enums belonging to different domains — so **each domain owns only its own
  `status → StatusTone` mapping function** (e.g. `productStatusTone`,
  `orderStatusTone`, `outboundStatusTone`, `poStatusTone`, …; 20 such
  functions exist across `features/*/api/*-types.ts` as of this writing) and
  renders through the shared component.
- `tone` defaults to `neutral` — the safe value for an unknown, future, or
  absent status (TOLERANCE invariant: an unrecognised producer status must
  never crash the console).
- The badge's label is the **raw status string**, kept verbatim, so
  screen-reader text and `*-status` test assertions stay in lock-step with
  the producer enum.
- **`statusToneClass(tone)`** is the escape hatch for the rare call site that
  needs the tone's raw className rather than the `<StatusBadge>` element
  itself — e.g. `erp-ops/components/approval-common.tsx`'s local
  `StatusBadge` wrapper, which needs an extra `data-terminal` attribute that
  the shared component's props don't expose, so it composes its own `<span>`
  with `className={statusToneClass(approvalStatusTone(status))}` instead of
  hand-picking colours. Using `statusToneClass` to *inject the shared
  palette* into a structurally-different element is the sanctioned escape
  hatch; hand-writing colour classes (`bg-green-100 text-green-800 …`) is not.

### Known residue — not yet migrated

Three erp org/delegation surfaces still hand-roll their own status chip
markup instead of using `StatusBadge`/`statusToneClass`:

- `features/erp-ops/components/DelegationFactCard.tsx` — local `StatusBadge`
  function with its own hardcoded Tailwind classes.
- `features/erp-ops/components/DelegationGrantList.tsx` — local
  `DelegationStatusBadge` component, same pattern.
- `features/erp-ops/components/EmployeeOrgViewCard.tsx` — inline
  `rounded bg-muted …` className on the status cell, no component at all.

This is **known, out-of-scope residue** (per this task's Scope), not a
newly-discovered defect — recorded here so it isn't "discovered" again and
isn't mistaken for the pattern to copy. Migrating them is a separate,
optional follow-up.

### Established by

- TASK-PC-FE-158 — extract shared `StatusBadge` + semantic tone palette
  (wms-outbound + ecommerce users/sellers migrated)
- TASK-PC-FE-159 — roll `StatusBadge` out to the remaining domains (orders,
  shipping, products, promotions, finance, ledger, scm, replenishment, …)

---

## 4. `web-store` — canonical home for the date/time convention

The date/time convention (§ 1) also applies to `web-store`
(`projects/ecommerce-microservices-platform/apps/web-store`) — a **different
app in a different project**, which cannot import `console-web`'s
app-internal `shared/lib/datetime.ts`. It carries its own copy at
`projects/ecommerce-microservices-platform/apps/web-store/src/shared/lib/datetime.ts`,
ported by `#2110` and already labelled in its own doc comment as
*"Convention (mirrors platform-console)"*.

**Decision (AC-4): this document is the canonical home for the rule body.**
`web-store`'s copy is a necessary duplication of the *implementation*
(different app, can't share a module across projects) but must not duplicate
the *rule's rationale* — the hydration-mismatch and midnight-quirk reasoning
above lives here, once.

**Residual gap, left for a follow-up task**: `web-store`'s
`datetime.ts` doc comment does not yet point back to this document (only says
"mirrors platform-console" without a link). Adding that one-line pointer is a
change inside `projects/ecommerce-microservices-platform/`, a different
project with its own task lifecycle; this task's Target App is `console-web`
docs only (doc-only, no code touched in either app — see the task's `Target
App` section), so the pointer edit itself is intentionally **not** made here.
File a small task in `projects/ecommerce-microservices-platform/tasks/ready/`
to add:

```
// See platform-console's docs/conventions/frontend-ui.md § 1 for the
// rationale (hydration-mismatch pinning, ko-KR midnight quirk); do not
// duplicate the rationale here — only the implementation is duplicated.
```

to the top of `web-store`'s `datetime.ts`, without copying the rule body
itself.

---

## Provenance / re-verification (AC-1)

This document was written after re-reading the current code (2026-07-14), not
copied from the 12-day-old memory notes that surfaced the gap. Result: the
memory's factual description of the three conventions matched current code
exactly (helper signatures, `DetailHeader` props, the `StatusTone` union, the
`formatPromotionDay` UTC exception) — see the task's Implementation Notes for
the full comparison table, including two points the memory did **not**
capture (the `Number#toLocaleString` false-positive volume in § 1's AC-3
verdict, and the exact `DelegationFactCard`/`DelegationGrantList`/
`EmployeeOrgViewCard` non-adopters in § 3).

| Rule | Footnote (task / PR) |
|---|---|
| `formatDateTime`/`formatDate`, `hourCycle:'h23'`, `timeZone:'Asia/Seoul'` | `#2096`, `#2099` |
| `formatPromotionDay` UTC day-edge exception | `#2096` (introduced alongside the general helper) |
| `web-store` date/time copy | `#2110` |
| `DetailHeader` origin + back-button label variants | `#2079`, `#2084`, `#2085` |
| `<dl>` field order (name → status → id/attrs → dates) | `#2087` |
| `DetailHeader` promoted to `shared/ui/` | TASK-PC-FE-237 § F |
| `StatusBadge` extraction | TASK-PC-FE-158 |
| `StatusBadge` roll-out to remaining domains | TASK-PC-FE-159 |
