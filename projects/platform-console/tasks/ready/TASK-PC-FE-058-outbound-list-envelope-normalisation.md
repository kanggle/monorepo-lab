# Task ID

TASK-PC-FE-058

# Title

console-web wms-outbound-ops: parse the live outbound-service list envelope (`{items, page:int, size, total}`) — fix surfaced by the TASK-PC-FE-057 live integration

# Status

ready

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl) — console parser robustness fix closing a contract↔implementation envelope drift.

# Task Tags

- code
- test

---

# Dependency Markers

- **surfaced by**: the TASK-PC-FE-057 live console integration (ADR-MONO-022 §D7). The `/wms/outbound` orders list **degraded** because the console parsed the response against the documented `outbound-service-api.md` § Pagination envelope `{ content, page:{number,size,totalElements,totalPages}, sort }`, but the live `outbound-service` `PagedResponse` DTO actually serialises FLAT as `{ items, page:<int>, size, total }`. Zod threw → section degraded. The Docker-free vitest suite mocked the documented shape, so the drift was invisible until a real console hit the real service (the §14 unit-mocks-contract pattern).
- **paired with**: TASK-BE-344 (the sibling §5.1 saga-endpoint gap the same live integration surfaced).
- **note**: the *root* discrepancy is the service DTO not matching its own documented § Pagination contract. A stricter follow-up could reconcile `PagedResponse` to the contract; this task makes the **consumer tolerant of both shapes** (robust-in-what-you-accept) so the console works against the real service now without a coordinated cross-project change.

# Goal

Make the console's outbound list parser (`OutboundOrderPageSchema`) tolerant of BOTH the documented `{content, page:{…}, sort}` envelope AND the live service's flat `{items, page:<int>, size, total}` envelope, normalising to the internal `{content, page:{number,size,totalElements,totalPages}}` the screen consumes — so `/wms/outbound` renders the order list against the real `outbound-service`.

# Scope

## In Scope

- **`src/features/wms-outbound-ops/api/types.ts`** — `outboundPage(row)` now `z.preprocess(normaliseOutboundPage, …)`:
  - documented nested shape (`content` array + `page` object) → pass through unchanged;
  - flat live shape `{items, page:<int>, size, total}` → normalise to `{content: items, page:{number:page, size, totalElements:total, totalPages: ceil(total/size)}}`;
  - tolerant of missing fields (sane defaults).
- **Tests** (`tests/unit/outbound-envelope.test.ts`) — flat live shape normalises; documented shape still passes (regression); `totalPages` math; row tolerance.

## Out of Scope

- Any `outbound-service` change (the service DTO drift is noted; a contract reconciliation is a separate optional wms task).
- Any other console parser (only the list envelope drifted; detail/saga/picking shapes already match).

# Acceptance Criteria

- [ ] `OutboundOrderPageSchema.parse({items:[row], page:0, size:20, total:1})` → `{content:[row], page:{number:0,size:20,totalElements:1,totalPages:1}}` (live shape parses, no throw).
- [ ] The documented `{content, page:{…}, sort}` shape still parses unchanged (regression — the existing outbound suites stay green).
- [ ] `totalPages` derived correctly from `total`/`size`; `number` from the flat `page` int.
- [ ] `pnpm exec vitest run` green (new + existing, no regression); `npx tsc --noEmit` clean; scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5.1 (the wms outbound surface — TASK-PC-FE-057)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` § Pagination / §1.3 (the producer envelope — the drift origin)

# Related Contracts

- **Consumed (unchanged)**: `outbound-service-api.md` §1.3 list. This task only adjusts the consumer's tolerance; no contract is redefined.

# Target Service

- `platform-console` / `apps/console-web` — `features/wms-outbound-ops/api/types.ts` parser tolerance + a unit test. Read-only/robustness; no UI/behavior change beyond the list now rendering against the real service.

# Architecture

- Robust-in-what-you-accept: the consumer normalises producer envelope variants at the parse boundary (a zod `preprocess`), keeping the rest of the feature on the single internal `{content, page:{…}}` shape the screen already uses.

# Edge Cases

- Empty list `{items:[], page:0, size:20, total:0}` → `{content:[], page:{…, totalElements:0, totalPages:1}}` (no throw).
- Multi-page `{items:[…], page:1, size:2, total:5}` → `totalPages:3`, `number:1`.
- Already-normalised `{content, page:{…}}` → unchanged (regression).

# Failure Scenarios

- Parser stays strict to the documented shape → the real service's `{items,…}` throws → `/wms/outbound` degrades (the bug this fixes) → AC asserts the live shape parses.
- Normalisation breaks the documented shape → existing tests fail → AC asserts the documented shape still passes.

# Definition of Done

- [ ] `outboundPage` normalises both envelopes; list renders against the real outbound-service
- [ ] vitest + tsc green, no regression; scope = console-web only
- [ ] Acceptance Criteria satisfied
- [ ] Ready for review
