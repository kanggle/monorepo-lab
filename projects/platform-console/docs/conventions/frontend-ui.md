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

**The same field renders in the same format everywhere.** Pick the precision from
what the *field* means, not from the screen it happens to be on: if `createdAt`
is a `formatDateTime` in the detail view, it is a `formatDateTime` in the list
too. Do not drop the time component "because it's a table" — a list row and a
detail row showing the same value must not disagree. (`OrdersTable.tsx` and
`OrderDetail.tsx` both `formatDateTime` the same `createdAt`; that agreement is
the rule, not a coincidence.)

> This rule is the reason § 3's `StatusBadge` residue mattered: the *status*
> dimension had drifted exactly the way this rule forbids for dates — the same
> `ACTIVE` master rendered green in a list and grey in a detail — until
> TASK-PC-FE-242 closed it. Same-field/same-rendering is a convention about
> **fields**, not about dates.

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

### Residue: none (TASK-PC-FE-242)

**Every entity-status chip in `console-web` now renders through `StatusBadge`,
`statusToneClass`, or `statusToneColorClass`.** Hand-written status palettes:
zero.

This section previously listed *three* non-adopters, all in `erp-ops`. That
count was wrong, and the way it was wrong is worth keeping: the residue had been
counted **inside one feature**, while the rule is console-wide. Re-measuring by
the actual predicate — *a status pill whose palette does not come from the
shared tone map* — found **15 chips across 12 components**, and the shape of the
miss was systematic:

> **TASK-PC-FE-159's console-wide roll-out reached the *list/table* surfaces and
> stopped there. The *detail* surfaces kept their hand-rolled chips.**

So the same field rendered two different colours depending on which screen you
were looking at: an `ACTIVE` ERP master was **green in the list and grey in the
detail** (all five master detail screens); a `SEPARATED` employee was grey in
the list and amber in the detail; a `REVOKED` delegation was red in
`DelegationGrantList` and grey in `DelegationFactCard`. These were not style
drift — they were the *status* twin of the § 1 list/detail consistency rule.

One was an outright bug rather than an inconsistency: `ledger-ops/FxRatesTable`'s
four chips (`피드 활성`/`피드 비활성`, `STALE`/`최신`) carried **no `dark:`
variants at all**, so they rendered light-on-light in the console's dark theme.
Every tone in `StatusBadge` ships its dark variant, so taking the palette from
the tone map fixed the bug as a side effect of the migration.

### The three levels of the escape hatch

Reach for the *narrowest* one that works:

| Level | Use when | What you get |
|---|---|---|
| `<StatusBadge tone={…}>` | the default — a status chip | the component: pill geometry + tone palette + `data-testid` passthrough |
| `statusToneClass(tone)` | the element must carry props `StatusBadge` does not forward (`data-status`, `data-retired`, `title`) — e.g. `EffectivePeriodBadge`, `DelegationFactStatusBadge`, `erp-ops/approval-common.tsx` | the full pill className (geometry + palette) for your own `<span>` |
| `statusToneColorClass(tone)` | the **geometry itself** must differ — e.g. `FxRatesTable`'s page-level feed indicator is a `rounded-full px-3 py-1 text-sm` badge carrying a sentence, not a table chip | the tone's colours **only** (bg + fg + `dark:` variants) |

Hand-writing `bg-*-100 text-*-800` is not on this list. That is exactly how a
chip ends up with no dark variant.

### Deliberately NOT status chips — do not "discover" these again

The following also use colour, and are **out of scope by design**. They are
listed so a future sweep doesn't file them as residue:

- **Service-health dots** — `bg-green-500` / `bg-red-500` round dots in
  `ErpOverviewScreen`, `ScmOverview`, `wms-overview-cell`, `iam-overview`'s
  `overview-labels`, `catalog/ServiceTile`. A dot is a different affordance from
  a pill and carries a health signal, not an entity status enum.
- **Service-health status pills** — `DomainHealthCard`'s `HEALTH_VISUAL` pill
  (`domain-health-card-*-visual`) renders the raw four-value Spring actuator
  `HealthStatus` (`UP`/`DOWN`/`OUT_OF_SERVICE`/`UNKNOWN`) as *one visual per
  value* — a glyph (`OK`/`X`/`!`/`?`) + colour + label, documented in
  `domain-health/api/types.ts`. It is a service-health indicator, not an entity
  lifecycle enum: `StatusBadge`'s five semantic tones carry no glyph affordance
  and do not model these four actuator values. The 3-tone `healthTone()` **dot**
  in `DomainHealthSummaryCard` is the *collapsed glance* of the same signal; the
  pill is its full-fidelity detail. Both are by design — not two colour systems
  for one signal (TASK-PC-FE-247).
- **Attribute chips** — `BusinessPartner`'s `유형` (`partnerType`),
  `DelegationFactCard`'s `ScopeCell` (`GLOBAL`/`REQUEST`). These render a
  *property*, not a lifecycle state; forcing them onto a five-tone semantic
  status palette would assign them a meaning they don't have.
- **Row highlights / annotation tags** — `ApprovalDetail`'s current-step row,
  `JournalEntryDetail`'s `환산 (revaluation)` marker, `NotificationBell`'s unread
  count.
- **Semantic-token indicators** — `TrialBalanceTable` / `PeriodDetail` /
  `WmsInventoryDataTable` / `OperatorBadges` use `bg-muted` and
  `bg-destructive/15` (theme tokens, which already adapt to dark mode) for
  computed validity/lock indicators, not for a producer status enum.

### Established by

- TASK-PC-FE-158 — extract shared `StatusBadge` + semantic tone palette
  (wms-outbound + ecommerce users/sellers migrated)
- TASK-PC-FE-159 — roll `StatusBadge` out to the remaining domains (orders,
  shipping, products, promotions, finance, ledger, scm, replenishment, …) —
  **list/table surfaces only, as it turned out**
- TASK-PC-FE-242 — close the tail: the five ERP master *detail* screens, the two
  delegation badges (+ a single `delegationStatusTone` so the fact card and the
  grant list stop disagreeing), `EffectivePeriodBadge`'s parallel
  `normal|warn|danger` vocabulary, `EmployeeOrgViewCard`, `WmsAlertsTable`,
  `accounts/AccountStatusBadge` (a second implementation of this component), and
  `FxRatesTable`'s dark-mode bug. Adds `statusToneColorClass`.

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

## 5. Local verification gate — `pnpm lint` is not optional

**Rule**: before pushing a change to any of this monorepo's Next.js apps, run
**`pnpm lint`** as well as the typecheck and the unit tests.

```bash
# console-web (projects/platform-console/apps/console-web)
pnpm lint && npx tsc --noEmit && pnpm test
```

**`tsc --noEmit` GREEN + `pnpm test` GREEN does not mean CI is GREEN.** The two
are blind to a whole class of failure that CI treats as fatal:

| Check | Catches | Blind to |
|---|---|---|
| `npx tsc --noEmit` | type errors | **unused imports/vars** — `noUnusedLocals`/`noUnusedParameters` are not enabled, so `tsc` does not consider them errors |
| `pnpm test` (vitest) | logic regressions | lint entirely — vitest does not run ESLint |
| `pnpm lint` | `@typescript-eslint/no-unused-vars` and the rest of the Next.js ESLint config | runtime behaviour, navigation (see § 5.2) |

The lint rule that bites most often is `@typescript-eslint/no-unused-vars`, and
it bites for a mundane reason: **deleting the last use of something rarely
deletes the import.** Refactor a loader into a generic fallback, drop a
`<Suspense>`, replace a token bridge — the identifier goes, the `import` line
stays, and only ESLint objects.

### 5.1 Which CI job enforces it

Lint is a **dedicated CI step for all three frontend apps** — not merely a side
effect of the build (measured against `.github/workflows/ci.yml`, 2026-08-06):

| App | Job that runs `pnpm lint` | Step |
|---|---|---|
| `console-web` | `Frontend unit tests (ecommerce + fan-platform + console-web, vitest)` | `Lint (console-web)` — runs *after* `pnpm test` and `npx tsc --noEmit` in the same job |
| `web-store` (ecommerce) | `Frontend lint & build (ecommerce + fan-platform)` | `Lint (ecommerce)`, then `Build (ecommerce)` |
| `fan-platform-web` | `Frontend lint & build (ecommerce + fan-platform)` | `Lint (fan-platform)`, then `Build (fan-platform)` |

There is a **second, independent path** to the same failure: `next build` runs
ESLint after compiling and reports violations as `Failed to compile`. So
`Frontend E2E smoke (web-store + fan-platform-web + console-web, Playwright)`
— which does `pnpm build` for `fan-platform-web` and `console-web` before
driving Playwright — goes red on a lint violation too, before a single test
runs.

**Prefer the mechanism over the job name when reasoning about this.** Job names
get renamed; "lint runs as its own CI step, and `next build` runs it again"
survives. One consequence of the two paths: a single unused import can take out
*two* jobs at once, which is what makes the failure look alarming out of
proportion to its one-line fix.

> Note the asymmetry, so a green run isn't over-read: `console-web` is the only
> app with an explicit `npx tsc --noEmit` CI step. `web-store` and
> `fan-platform-web` get their typecheck implicitly, via `pnpm build`.

### 5.2 What this gate does **not** catch

Three GREEN checks locally is a necessary condition, not a sufficient one.
Known gaps, each owned elsewhere:

- **Playwright smoke URL assertions** — `e2e-smoke/*.spec.ts` asserts URL globs
  (`page.waitForURL('**/login')`). Change a guard or login redirect (e.g. bare
  `/login` → `/login?redirect=<dest>`) and the glob stops matching → timeout
  RED, while lint + tsc + vitest all stay GREEN because unit tests don't
  navigate. Rule body:
  [`platform/testing-strategy.md`](../../../../platform/testing-strategy.md)
  § "Frontend E2E Smoke (Playwright URL assertions)" — repo-universal, so it
  lives there rather than here.
- **Testcontainers integration lanes** — untouched by any frontend check; CI
  Linux is the authority (Docker-on-Windows is unreliable locally).
- **Nightly-only suites** — the console and web-store full-stack e2e suites run
  in `nightly-e2e.yml`, not `ci.yml`. A route/nav/testid/heading change can
  merge green having never been exercised (see `CLAUDE.md` § Git discipline,
  "Post-merge nightly check").

### 5.3 `web-store` and `fan-platform-web` — same gate, different local reality

The rule applies to all three apps; what differs is **how much of it you can
actually run on a developer machine**. Re-measured 2026-09-05 (UTC) from each
app's `package.json` — all three rows, not just the one that changed:

| App | Next | vitest | `lint` script | Local gate |
|---|---|---|---|---|
| `console-web` | `^15.1.0` | `^2.1.4` | `next lint` | all three run locally |
| `web-store` | `^15.1.0` | `^4.1.0` | `next lint` | `pnpm lint` + `tsc` only — **vitest 4 does not start under Node 24**; CI (Node 20) is the authority for its unit tests |
| `fan-platform-web` | `^15.1.0` | `^3.2.4` | `next lint --dir src` | not measured on this host — do not assume either way |

The Next column is a **spec, not a version**. `^15.1.0` resolved to 15.5.25 /
15.5.14 / 15.5.15 respectively on 2026-09-05; a fresh install resolves it again.
Do not read a number out of this table.

`console-web` held `15.0.3` — an exact pin, with React 19 **RC** as its required
partner — until TASK-PC-FE-274, when Vercel refused to deploy it: the build
succeeded and the *deployment* was rejected with `Vulnerable version of Next.js
detected`. The three siblings, already on `^15.1.0`, deployed fine. That is what
an exact pin costs when nothing re-examines it.

One consequence lands in this section directly: under 15.5, `next lint` prints
`` `next lint` is deprecated and will be removed in Next.js 16 `` and Next also
warns that it inferred a workspace root from the repo-root lockfile. Both are
**warnings**; `pnpm lint` still exits 0 and still reports `No ESLint warnings or
errors`. The gate above is `rc=0` — do not read a warning as a failure, and do
not silence one by widening a task's scope. Migrating off `next lint` is its own
decision, owed before Next 16.

Two things follow. First, **"CI is the authority" is not a licence to skip the
part that does run** — `pnpm lint` and `tsc` work fine for `web-store`, and they
are exactly the checks that catch the unused-import class. Second,
`fan-platform-web`'s local behaviour has never been measured by the incidents
behind this section; it is listed as unmeasured rather than assumed green,
because recording an unmeasured app as passing is how a table like this starts
lying.

The rule body for all three lives **here**, once — the same decision § 4 made
for the date/time convention. Only the implementations are per-app.

### Established by

- `#1489` (TASK-PC-FE-076) — the incident: an `ErpUnavailableError` import left
  unused by an erp-state 4-loader refactor. Local `tsc` + vitest 1351 GREEN,
  both frontend CI jobs RED, fixed by deleting one import line. The OTEL
  `module not found` warnings in that build log were pre-existing noise — the
  real cause is always the line right after `Failed to compile`.
- TASK-FE-074 / FE-075 / FE-076 / FE-082 / BE-430 — `web-store` task ACs that
  each restated this gate individually (FE-075 also hit it for real: an unused
  import left behind after token-bridge removal). Restating a rule in five task
  bodies is what having no canonical home looks like.
- TASK-PC-FE-272 — this section (rule moved out of agent memory into the repo).
- TASK-PC-FE-274 — § 5.3's table re-measured and the "a warning is not a
  failure" clause added, after `console-web`'s pinned Next was refused by
  Vercel. The table itself was the second copy of a fact whose first copy is
  `package.json`, and **nothing guards it** — it went stale the moment the
  dependency moved. That is why the bump task carried updating it as an
  acceptance criterion rather than trusting anyone to notice.

> Deliberately **not** moved here from the originating agent memory: the
> worktree `node_modules` junction setup and its teardown-order hazard. That is
> worktree operations, not a UI convention — it stays in agent memory.

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
| § 5 local verification gate (`pnpm lint`), CI job names, per-app tooling table | TASK-PC-FE-272 (`#1489` incident; job names + `package.json` versions re-measured 2026-08-06, not copied from the memory note) |
