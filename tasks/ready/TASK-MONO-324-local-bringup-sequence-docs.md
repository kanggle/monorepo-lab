# Task ID

TASK-MONO-324

# Title

Local bring-up sequence — shared master (`TEMPLATE.md`) + per-project `docs/onboarding/local-dev.md` for all 8 projects

# Status

ready

# Task Tags

- onboarding

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

Give "bring up a domain-service stack locally so all its resources are operable" a
written, discoverable home. Today the knowledge is split across each project's
`docker-compose.yml` header comments and `.env.example` prose, the single most important
non-obvious fact (a consumer stack's gateway **fails fast** unless `iam.local` is up first)
is buried, and the two existing `docs/onboarding/local-dev.md` files (ecommerce, iam) are
empty stubs while the other six projects have none.

Establish the **ordered bring-up sequence** (register hosts → `traefik:up` → `iam:up` [if
consumer] → `<project>:up` → verify) as a shared, project-agnostic **repo invariant** in the
`TEMPLATE.md § Local Network Convention` master, plus a per-project cross-reference matrix.
Then give every project a thin `docs/onboarding/local-dev.md` that carries only its own
specifics (up/down command, entry hostname, IAM-first flag, at-a-glance inventory, project
notes) and links back to the master — keeping `docker-compose.yml` the authoritative resource
inventory (no re-transcription → no drift).

Monorepo-level because the shared master edit is under `TEMPLATE.md` (repo-root shared) and
the change spans all 8 `projects/<name>/` doc trees in one atomic cross-project PR
(CLAUDE.md § Cross-Project Changes).

---

# Scope

## In Scope

**WI-1 — shared master (`TEMPLATE.md § Local Network Convention`).** Add a `### Local
bring-up sequence` subsection (after `### One-time developer setup`, before `### Adding a new
project`) containing: the 6-step ordered sequence; a per-project bring-up matrix (up/down
command, primary hostname(s), IAM-first flag) for all 8 projects; a note that the project's
`docker-compose.yml` is the authoritative inventory and the matrix is the ordering contract;
a host-agnostic caution that resource-constrained hosts should cold-start in small batches
(explicitly framed as a developer-environment concern, not a repo invariant). Add step 6 to
the greenfield checklist so new projects register in the matrix + add a `local-dev.md`.

**WI-2 — per-project onboarding docs (8).** Create/fill `docs/onboarding/local-dev.md` for
iam-platform, ecommerce-microservices-platform, wms-platform, fan-platform, scm-platform,
erp-platform, finance-platform, platform-console. Each: links to the master sequence; a
specifics table (up/down, status/logs, hostname(s), IAM-first); a copy-paste quick-start; an
at-a-glance service/resource list that defers to `docker-compose.yml`; project-specific notes
(iam = provider ordering; ecommerce = ES cold start + MinIO + fulfillment is separate;
console = thin aggregator + `console-demo:up` for the federated demo; etc.).

## Out of Scope

- Any `docker-compose.yml`, `.env.example`, or `package.json` script change (docs only —
  the compose headers already document the happy path; this task cross-references, does not
  rewrite them).
- Host-specific operational traps (OOM batch sizes, Rancher/WSL2 recovery, prebuilt-jar
  redeploy) — these are developer-environment notes, intentionally kept out of shared/repo
  docs so they are not presented as universal truth.
- Cross-stack demo wiring (`tests/fulfillment-demo/`, `federation-hardening-e2e`) beyond a
  one-line pointer — those are separate stacks, not standalone project bring-up.
- Root `docs/guides/` (human-only per CLAUDE.md; not an AI source of truth).

---

# Acceptance Criteria

- [ ] `TEMPLATE.md` contains `### Local bring-up sequence` with the 6-step order and an
      8-row per-project matrix; the greenfield checklist gains a matrix/onboarding-doc step.
- [ ] All 8 `projects/<name>/docs/onboarding/local-dev.md` exist and are non-empty; the two
      former empty stubs (ecommerce, iam) are filled.
- [ ] Every per-project doc's relative links resolve: `../../../../TEMPLATE.md#local-bring-up-sequence`
      and `../../docker-compose.yml`.
- [ ] The IAM-first flag in the matrix and each doc matches reality (iam-platform = provider
      / no dep; all seven consumers = ✅).
- [ ] `docker-compose.yml` remains the sole authoritative inventory — no doc re-declares the
      full container list as normative (at-a-glance only, with a compose pointer).
- [ ] No HARDSTOP-03 regression: the shared `TEMPLATE.md` edit stays project-agnostic in the
      sequence/greenfield prose (the matrix names projects, which is the existing hostname-
      allocation table's established pattern in the same section).
- [ ] Docs-only; `./gradlew check` not required. Verification = relative-link resolution +
      manual read.

---

# Related Specs

> **Before reading Related Specs**: this is a monorepo-level task touching shared
> `TEMPLATE.md`; read `rules/common.md` and `platform/shared-library-policy.md`. No single
> `PROJECT.md` governs (repo-root + cross-project scope).

- `TEMPLATE.md § Local Network Convention` — WI-1 edit target (master for hostname routing;
  the new subsection lives here).
- `CLAUDE.md § Local Network Convention` / `§ Cross-Project Changes` — the summary that
  redirects to the master; atomic cross-project PR requirement.
- Each `projects/<name>/docker-compose.yml` — the authoritative per-project inventory the
  docs point to (read-only reference).
- Each `projects/<name>/.env.example` — source of the IAM-dependency facts surfaced.

# Related Skills

- `.claude/skills/refactor-spec/SKILL.md` — doc structure/consistency, meaning-preserving.

---

# Related Contracts

- None. No HTTP/event contract under any `projects/<name>/specs/contracts/` is touched.

---

# Target Service

- N/A (shared `TEMPLATE.md` + cross-project onboarding docs — monorepo-level).

---

# Edge Cases

- A project whose main gateway hostname is not directly in a `Host()` label (iam, wms expose
  only `kafka.*`/`grafana.*` sub-hostnames in compose) — the matrix hostname comes from the
  existing `§ Hostname allocation` table (`iam.local`, `wms.local`), which is authoritative.
- platform-console is a thin 2-container aggregator; its doc must distinguish `console:up`
  (standalone, degraded domain panels) from `console-demo:up` (federated demo) rather than
  implying `console:up` alone gives a full console.
- finance-platform exposes two hostnames (`finance.local`, `ledger.local`) and two MySQL
  instances — the doc must not flatten these to one.

# Failure Scenarios

- A per-project doc re-transcribes the full container list as if normative → drifts from
  `docker-compose.yml` on the next compose change. Mitigation: at-a-glance + compose pointer,
  compose is SoT.
- Host-specific OOM/batch guidance leaks into the shared master → misleads developers on
  larger machines. Mitigation: scope note keeps it a developer-environment concern.
- A relative link (`../../../../TEMPLATE.md`) is off by a directory level → broken master
  link. Mitigation: AC verifies link resolution from the `onboarding/` depth.

---

# Test Requirements

- Docs-only; no unit/integration test. Verification: relative-link resolution + manual read +
  IAM-flag cross-check against each `.env.example`.

---

# Definition of Done

- [ ] WI-1 master subsection + matrix + greenfield step added
- [ ] WI-2 all 8 per-project `local-dev.md` present and non-empty
- [ ] Relative links resolve; IAM-first flags correct; compose remains inventory SoT
- [ ] No HARDSTOP-03 regression
- [ ] Branch: `task/mono-324-local-bringup-docs` (substring `master` 금지 — uses `ms-`-free noun)
- [ ] Ready for review
