# Task ID

TASK-MONO-504

# Title

Fix stale finance-platform / erp-platform READMEs (same class as TASK-MONO-503, unpublished so out of that
task's scope) + correct fan-platform artist-events.md's inaccurate "planned consumer" claims

# Status

done

# Owner

monorepo

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

A cross-project "is every implemented service actually used" audit (2026-08-04, 8 parallel Explore agents, one
per project + platform-console) found no dead services, but surfaced two independent, low-risk documentation-drift
findings that are safe/cheap to fix and were explicitly approved by the user:

1. **`finance-platform/README.md` and `erp-platform/README.md` are severely stale** — the exact same class of bug
   `TASK-MONO-503` fixed in the 5 published standalone-repo READMEs, but these two projects are registered in
   `scripts/sync-portfolio.sh` and NOT yet published as standalone repos, so they were explicitly out of scope
   for that task (see its Out of Scope §). Both READMEs currently describe their projects as unimplemented
   skeletons; both are actually mature, fully-implemented, gateway-fronted backends.
2. **`fan-platform/specs/contracts/events/artist-events.md` inaccurately labels every topic's consumer list as
   "Consumers (planned): search-service"** — but fan-platform has no `search-service` anywhere in its `apps/`
   directory (the audit confirms zero `@KafkaListener` in the entire monorepo subscribes to any `artist.*`
   topic). Unlike the same pattern in `ecommerce-microservices-platform`/`scm-platform`/`finance-platform`'s own
   event docs (which say "no consumer yet, v2/future" — an accurate, self-documented forward-declaration), this
   file names a specific consumer service that was never built in this project, which reads as an active plan
   rather than the honest "nothing consumes this today" it actually is.

The user explicitly chose the low-cost "fix documentation only" option for finding 2 over "build a real
consumer" or "delete the event publishing" — this task does NOT change any runtime behavior anywhere.

After this task: `finance-platform/README.md` and `erp-platform/README.md` accurately describe their current
shipped scope (matching the rigor `TASK-MONO-503` applied to the other 5), and `artist-events.md` accurately
states that no consumer currently exists for any of its 6 topics, without naming a specific service that isn't
real.

---

# Scope

## In Scope

**`projects/finance-platform/README.md`** — rewrite the stale sections:
- Status/overview text currently framing the project as "skeleton only, account-service 미가동" — update to
  reflect the actual state: `libs:finance-common`, `apps:account-service`, `apps:ledger-service`,
  `apps:gateway-service` (all in `settings.gradle`), gateway added by `tasks/done/TASK-MONO-357-finance-erp-gateways.md`
  (ADR-MONO-048 D7 step 4) specifically to close a policy violation (`platform/api-gateway-policy.md`) where
  Traefik routed straight to account-service with no JWT/rate-limit at the edge.
- The "no gateway module, Traefik routes straight to the services" claim — no longer true; `docker-compose.yml`
  now only Traefik-labels `gateway-service`, not the two backend services.
- Add whatever service-map/architecture description matches the actual GAP RS256 JWT + Redis rate-limit gateway
  routing (`Path=/api/finance/accounts/**` → account-service, `Path=/api/finance/ledger/**` → ledger-service).

**`projects/erp-platform/README.md`** — rewrite the stale sections:
- Status/overview text currently framing the project as "skeleton only, masterdata-service 미가동, no gateway" —
  update to reflect: 5 modules in `settings.gradle` (`masterdata-service`, `read-model-service`,
  `approval-service`, `notification-service`, `gateway-service`), 40 completed tasks in
  `projects/erp-platform/tasks/done/`, gateway added by the same `TASK-MONO-357`.
- Note the console integration is real and mature (platform-console's `console-bff`/`console-web` both have live
  erp adapters/UI — `/erp/masters`, `/erp/approval`, `/erp/delegation`, `/erp/orgview`).

**`projects/fan-platform/specs/contracts/events/artist-events.md`** — for all 5 topics that currently have a
"Consumers (planned):" section referencing `search-service` (`artist.registered.v1`, `artist.published.v1`,
`artist.updated.v1`, `artist.archived.v1`, `artist.group_created.v1`) and the one topic
(`artist.group_member_changed.v1`) that has no consumer note at all:
- Replace "Consumers (planned): search-service..." with accurate, honest language matching this repo's own
  established convention for this exact situation (see `ecommerce-microservices-platform`'s
  `specs/contracts/events/settlement-events.md` — *"Consumers: none yet ... published so future subscribers ...
  can consume it without a producer change"* — or `scm-platform`'s `specs/contracts/events/scm-procurement-events.md`
  for the "v2-deferred, no v1 use case consumes it" phrasing). Do not simply delete the aspiration — state plainly
  that no consumer exists today, and if a future consumer is genuinely still plausible, name it without implying
  it's already planned/scheduled work.
- Also correct the `artist.archived.v1` and `artist.published.v1` sections' references to `community-service`/
  `notification-service (v2)` "planned" consumption the same way — these are equally unbuilt today (confirmed:
  zero `@KafkaListener` for any `artist.*` topic anywhere in the monorepo).
- Add a `Consumers:` note (accurate, not "planned") to `artist.group_member_changed.v1`, which currently has none.

## Out of Scope

- Building an actual consumer for any `artist.*` event (a `search-service`, a `community-service` archival
  handler, a `notification-service` broadcast) — the user explicitly chose the docs-only option over this.
- Removing/simplifying `artist-service`'s outbox event-publishing code itself — the user explicitly did not
  choose the "delete the event publishing" option either; the producer code, outbox tables, and topics stay
  exactly as they are, only the consumer-claim documentation changes.
- `finance-platform`/`erp-platform` standalone-repo publication — separate, bigger decision (first publish vs.
  resync), not bundled here; these two READMEs are being fixed as monorepo source-of-truth documents only (same
  "will reach the standalone repo on next portfolio sync" caveat `TASK-MONO-503` recorded, except these two
  aren't even published yet so there's no standalone repo for this to reach until that separate decision is made).
- The two other minor doc-staleness items the audit surfaced in passing (scm gateway's outdated "until BE-002,
  503" comment; `logistics-service`'s outdated "Kafka scaffold only" comment; `FinanceHealthReadAdapter`'s
  javadoc; `platform-console/apps/console-web/README.md`'s "Phase 1 skeleton" line; `application.yml`'s stale
  comment about ecommerce not being an Operator Overview leg) — these are code-comment/secondary-doc drift, lower
  value than the two findings above, and not part of what the user approved for this task. File separately if
  wanted.
- `env_console_erp_gateway_and_notification_wiring` (a Claude-memory item, not a repo file) — confirmed during
  the audit to describe a different, untracked fed-e2e demo-only nginx gateway, not `erp-platform`'s real
  `apps/gateway-service`; not touched by this task, not a repo file to begin with.

---

# Acceptance Criteria

- [ ] **AC-0 (re-verify gate)** — Before rewriting each file, re-confirm the current shipped state still matches
      what's described above: `settings.gradle` module lists for finance-platform and erp-platform, and a fresh
      repo-wide grep confirming zero `@KafkaListener`/consumer subscribes to any `artist.*` topic. Time has
      passed since the audit; don't inherit its snapshot uncritically.
- [ ] **AC-1** — `finance-platform/README.md` no longer claims "no gateway module" / "skeleton only, account-service
      미가동"; accurately describes the 3-service + gateway shipped state.
- [ ] **AC-2** — `erp-platform/README.md` no longer claims "skeleton only, masterdata-service 미가동" / "no
      gateway"; accurately describes the 5-service shipped state and its console integration.
- [ ] **AC-3** — `artist-events.md`'s 6 topic sections all have an accurate `Consumers:` note — none names
      `search-service`, `community-service`, or `notification-service` as if consumption is scheduled/planned
      work when no such work is scheduled; language matches this repo's established "no consumer yet" convention
      (cite the ecommerce/scm precedent files) rather than inventing new phrasing.
- [ ] **AC-4** — No runtime code changed anywhere — `git diff --stat` for this task's PR touches only the 3
      documentation files above (plus this task's own file + `tasks/INDEX.md`).
- [ ] **AC-5** — Every claim added or changed is traceable to a concrete, checked fact (a `settings.gradle` entry,
      a task file in `tasks/done/`, a grep result) — no new speculative claims.

---

# Related Specs

- `tasks/done/TASK-MONO-357-finance-erp-gateways.md` — added the gateway modules both stale READMEs fail to
  mention.
- `tasks/done/TASK-MONO-503-portfolio-readme-drift-reconciliation-2026-08-04.md` — the directly analogous,
  already-completed sibling task this one extends to the 2 unpublished projects.
- `projects/ecommerce-microservices-platform/specs/contracts/events/settlement-events.md` and
  `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` — the established in-repo convention
  for honestly documenting a published-but-unconsumed event, to match phrasing style against.
- `projects/finance-platform/PROJECT.md`, `projects/erp-platform/PROJECT.md`, `projects/fan-platform/PROJECT.md`
  — current authoritative state for each project.

# Related Contracts

- `projects/fan-platform/specs/contracts/events/artist-events.md` (the file being corrected — this task's Scope
  IS a contract-doc correction, not a contract change; no payload/topic/schema changes, only the Consumers prose).

---

# Target Service

- N/A (documentation-only; `finance-platform/README.md`, `erp-platform/README.md`,
  `fan-platform/specs/contracts/events/artist-events.md`)

---

# Architecture

N/A — no code architecture involved.

---

# Edge Cases

- If AC-0 finds a real consumer for any `artist.*` topic now exists (unlikely but possible if time has passed),
  do not overwrite that with "no consumer" — reflect the current truth, not this task's filing-time snapshot.
- If `finance-platform`/`erp-platform` gained additional services since the audit, describe the actual current
  set, not the one enumerated above.

---

# Failure Scenarios

- **F1 — scope creep into building a consumer or touching runtime code.** Guarded by AC-4 (diff-stat check) and
  Out of Scope — the user explicitly declined those options for this task.
- **F2 — inventing a new "planned consumer" phrasing that reads as a commitment/roadmap item.** The whole point
  of this task is replacing an inaccurate implied commitment with an honest "no consumer today." Guarded by
  AC-3's explicit citation of the ecommerce/scm precedent language to copy the *tone* of, not just the fact.

---

# Test Requirements

- No automated test suite covers README/spec-doc prose in this repo. Verification is manual: re-read each of the
  3 files post-edit against the AC-0 ground-truth check, and diff-review for accidental Markdown breakage.

---

# Definition of Done

- [ ] `finance-platform/README.md` corrected (AC-1).
- [ ] `erp-platform/README.md` corrected (AC-2).
- [ ] `artist-events.md`'s 6 topics all carry accurate consumer language (AC-3).
- [ ] No runtime file touched (AC-4).

---

# Provenance

Surfaced 2026-08-04 by an 8-project parallel Explore-agent audit (one agent per project + platform-console)
answering the user's question "구현된 서비스는 모두 사용되어지고 있는 서비스인거야? 개발만 되고 미사용되는
서비스는 없어?" — no dead *services* were found anywhere, but these 2 documentation-drift findings were flagged
as the highest-value, lowest-risk follow-ups. The user was asked and explicitly chose: (1) docs-only fix for the
artist-events consumer claims (over building a real consumer or deleting the publisher), and (2) bundle both into
one task and proceed immediately (over deferring).

분석=Sonnet 5 / 구현 권장=Sonnet 5 (pure documentation correction against already-gathered, already-cited ground
truth; no architecture judgment left open).
