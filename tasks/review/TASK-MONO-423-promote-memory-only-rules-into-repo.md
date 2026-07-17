# TASK-MONO-423 — four repo-wide rules exist only in one operator's private AI memory; a fresh session or a different developer never sees them

- **Type**: TASK-MONO (monorepo-level — shared paths `platform/`)
- **Status**: review
- **Target**: `platform/security-rules.md`, `platform/git-workflow-policy.md`, `platform/testing-strategy.md`, `platform/architecture-decision-rule.md`
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security invariant + rule wording)

## Goal

A 2026-07-17 memory audit compared the AI operator's private memory against the repo's own canonical
files. Four rules turned out to live **only** in private memory. Private memory is per-user and per-machine:
another developer, a fresh AI session on a different host, or a subagent that never loaded that memory
**cannot** learn them. One of the four has already been re-created as a real security defect **four separate
times**. Promote each into the repo file that already owns its subject.

This is the repo's own stated principle applied to itself: **"1곳에만 있는 규칙 = 사실상 없는 규칙"** — and a
rule in *private* memory is worse than one place; it is zero places from the repo's point of view.

## AC-0 — 착수 시 재측정 (this ticket is audit-born; the audit is a hypothesis, not a source)

The numbers and absences below were measured on 2026-07-17. **Re-measure before writing each rule; the code
and the repo files win over this ticket's prose.** Specifically re-verify, per rule, that the destination
file still does not already state it (an intervening task may have landed it). If a rule is found already
present → drop that item and note it; do **not** duplicate it.

> Rationale: this ticket was produced by an audit, and this repo has repeatedly caught audit-born tickets
> asserting counts that were already wrong when written.

## AC-1 — `platform/security-rules.md` § Authorization: a verified token proves authentication, not authorisation

- **Absence verified**: `security-rules.md` states the **sole issuer** rule (§ Authentication) but nowhere
  states that the sole issuer mints *both* end-user and machine credentials, so `issuer + signature +
  "is authenticated"` cannot distinguish them.
- **Cost of the gap (why this one is first)**: the fleet re-created this exact defect **4×** — an
  internal-only surface gated on issuer + `.authenticated()` (or a bare token decode) admits an **end-user**
  token. The *code* fix was promoted to a shared library; **the rule was never written down**, so each new
  internal surface re-derives it or doesn't.
- Add, under § Authorization, **project-agnostically** (no service names, API paths, or domain entities —
  this is a shared file, HARDSTOP-03): the two acceptable discriminators (client-credentials `sub`
  allow-list **or** a workload-only required scope), that the discriminator must be enforced **where the
  token is validated** (a filter a test profile can bypass is not enforcement), and that every legitimate
  caller must be proven to carry the discriminator **before** enforcing (else the fix is an outage).

## AC-2 — `platform/git-workflow-policy.md`: a PR whose base ≠ `main` gets **zero** checks

- **Absence verified**: `.github/workflows/ci.yml` is `pull_request: branches: [main]`, so a PR opened
  against a non-`main` base **matches no workflow at all** — it receives 0 checks and is not merge-blocked.
  The policy file discusses stacked PRs (§ Post-Merge Branch Hygiene, base-ref-deletion hazard) but **never
  states this invariant**, and a broadened grep for it across `platform/`, `docs/guides/`, `CLAUDE.md`
  returns nothing (detector self-verified against a known-true term).
- **Why it matters**: "0 failing checks" and "CI approved this" are indistinguishable at the merge button.
  This is the failure mode where **CI-unseen code reaches `main` through the normal, green-looking path**.
- Add the invariant + the practical consequence: open spec and impl PRs **both at `base=main`** and merge
  sequentially, rather than stacking.

## AC-3 — `platform/testing-strategy.md` § Testcontainers Conventions: never bind `java.sql.Timestamp` in fixtures

- **Absence verified**: absent from `testing-strategy.md` and all of `platform/`.
- The connector formats a `java.sql.Timestamp` in the **JVM default timezone** while the ORM reads a naive
  `DATETIME` back as UTC → a fixed offset skew. **RED only on non-UTC hosts; CI (UTC) can never prove the
  fix either way** — so the guard is invisible to the lane that is supposed to be authoritative.
- Bind `LocalDateTime.ofInstant(instant, ZoneOffset.UTC)` instead. This belongs next to the existing
  Testcontainers conventions and complements § G4 (*a threshold calibrated on your host is a proposition
  about your host*), which already owns the host-vs-runner axis.

## AC-4 — `platform/architecture-decision-rule.md`: define what ACCEPTED requires

- **Absence verified**: the file says only *"PAUSE until ACCEPTED"* (§ Mandatory Rule remediation option 2).
  The **gate's semantics** — what makes an ADR ACCEPTED — appear nowhere in `platform/`.
- Add: PROPOSED→ACCEPTED requires an **explicit, exact-form human intent** naming the ADR; a bare
  "진행"/"proceed"/"go ahead" on a message that happens to contain an ADR does **not** satisfy it; and an
  agent **must never self-ACCEPT** its own proposed ADR.

## Scope

- **In**: the four `platform/*.md` edits above.
- **Out**:
  - **`.claude/hooks/README.md`** (2 further promotion candidates — new hooks must be UTF-8 **BOM** and read
    with `-Encoding UTF8` or they silently no-op; and hook **wiring**/matcher coverage cannot be
    fixture-tested and needs a live two-tool probe). Edits under `.claude/hooks` are **classifier-blocked**
    → the patch is handed to a human. Separate follow-up.
  - **The `.claude/` classifier-block map** (`CLAUDE.md` L191 + `git-workflow-policy.md` § `.claude/`
    Self-Modification say `hooks|agents|commands` are all blocked; the operator's *measured* map says only
    `hooks/` + `settings.json` block, and `commands/`/`agents/`/`config/` pass). **One of the two is wrong**
    and adjudicating requires a live probe, not a doc edit. Separate follow-up.
- **Not a rewrite**: each item is an addition to a file that already owns its subject. No section is
  restructured, no existing rule is relaxed. No `## Overrides` block is introduced.

## Acceptance Criteria

- **AC-0**: each rule re-verified as still absent from its destination before it is written (drop + note any
  that landed meanwhile).
- **AC-1..AC-4**: as above; each rule lands in the file that already owns its subject.
- **AC-5**: `platform/` shared-file discipline holds — **no service names, API paths, or domain entities** in
  any added text (HARDSTOP-03). Each rule reads as a platform invariant, not a war story.
- **AC-6**: `./gradlew check` unaffected (docs-only); CI `changes` gate green, code lanes SKIPPED.
- **AC-7**: the corresponding memory entries are **not deleted** — they are retargeted to point at the new
  canonical home, per this repo's established convention (memory keeps the worked incident + the measured
  numbers; `platform/` owns the rule). The memory becomes the detail layer, not a second source of truth.

## Related Specs / Contracts

- `platform/security-rules.md` § Authentication (sole-issuer rule the AC-1 gap sits on top of)
- `platform/testing-strategy.md` § CI Guards / Drift Detectors § G4 (host-vs-runner axis AC-3 complements)
- `CLAUDE.md` § Source of Truth Priority (layer 5 = `platform/` — the destination layer for all four)

## Edge Cases / Failure Scenarios

- **The promotion is not lossless by default.** This repo has already watched a canonicalisation drop one
  rule on the way across. After writing each rule, **compare against the memory body it came from** and
  confirm nothing was dropped in transit.
- **A rule stated abstractly can stop biting.** AC-1's value is the *discriminator inventory* step ("prove
  every legitimate caller carries it first"); if that is compressed away, the rule reads as "add a scope"
  and the next implementer causes an outage instead of a defect.
- **Do not let AC-2 read as "never stack branches".** The invariant is about the **PR base**, not about
  local branch topology; stacked *branches* are fine, a PR with `base != main` is not.
- **Adding a rule is not enforcing it.** None of these four gain a CI guard here. AC-8 of a future ticket, if
  any, would ask whether each rule's violation is mechanically detectable — most are not. Write down that the
  rule is prose-enforced (§ G8: "write down what the guard does not cover").
