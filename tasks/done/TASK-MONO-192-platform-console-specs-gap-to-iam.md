# Task ID

TASK-MONO-192

# Title

Align **platform-console's specs** (3 files) to IAM — MONO-190 swept the "6 consumer projects" but **omitted platform-console**, the IAM operator-console consumer. Two surfaces: (1) uppercase `GAP` project-name prose → `IAM` (466 refs); (2) lowercase `gap` **domain-slug drift correction** — MONO-180 renamed the code's `DomainTarget.GAP`→`IAM` and the wire/CARD_ORDER to `iam`, but the contract's JSON examples / card-order / metric-label / `gap.baseRoute` still read `gap` (spec drifted from code). The English word "gap" (one "parity gap") is preserved.

# Status

done

# Owner

claude (Opus 4.8) — single-project spec alignment + spec↔code drift correction (platform-console `specs/`, 3 files). One atomic PR. Prose + slug-to-match-code; zero code change (code already emits `iam`).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행**: MONO-179 (rename), MONO-180 (console code `DomainTarget.GAP`→`IAM`, wire `{"domain":"iam"}`, CARD_ORDER `['iam',…]`, §2.4.9 selector byte-verbatim sync), MONO-190 (the 6 consumer specs — **platform-console was not in that list**).
- **맥락**: post-191 repo-wide audit surfaced platform-console specs (466 GAP) as the omitted consumer-spec surface + a spec↔code drift: MONO-180 changed the code's domain slug to `iam` but left the contract's lowercase `gap` examples/labels (a stale-doc defect, CI-invisible — markdown not gated).

# Goal

After this task: `git grep -lwE "GAP" -- projects/platform-console/specs` = **0 files**; lowercase `gap` count = **1** (the English word "parity gap" only); the contract's wire/card/metric examples match the actual code (`"domain": "iam"`, `[iam,wms,scm,finance,erp]`). No code change (code already emits `iam`).

# Scope

## In Scope — platform-console `specs/` (2 transforms, 3 files)

`console-integration-contract.md` (187), `console-web/architecture.md` (72), `console-bff/architecture.md` (15):

1. **`(?<![A-Za-z])GAP(?![A-Za-z])` → `IAM`** — uppercase project-name prose + compounds (`GAP OIDC`/`GAP token`/`GAP-OIDC`/`GAP-domain-scoped`/`GAP federation`/`GAP JWKS`/`GAP's`/`GAP 도메인`/`DomainTarget.GAP`→`DomainTarget.IAM` matching code, …).
2. **lowercase domain-slug `(?<![A-Za-z])gap(?![A-Za-z])` → `iam`, then restore the single English word** — fixes the spec↔code drift: `{ "domain": "gap" }`→`"iam"`, `[gap, wms, …]`→`[iam, …]`, `{gap,wms,…}`→`{iam,…}` metric labels, `` `gap` ``→`` `iam` `` (federation note), `gap health`→`iam health`, `` `gap.baseRoute` ``→`` `iam.baseRoute` ``, `gap · wms`/`gap/wms`→`iam …`. The ONE English word — `> No real parity gap was found` (L1667) — is restored verbatim after the blanket pass.

## Out of Scope (retain)

- English word "gap" (L1667 "parity gap").
- platform-console **code** (`apps/`) — already `IAM`/`iam` (MONO-180); this task only catches the specs up to it.
- Repo-root `docs/adr/` ADR-MONO bodies, `tasks/**`, `knowledge/` (frozen/historical retentions).
- The `iam.local` URLs / `getAccessToken()`·`getOperatorToken()` method names (no GAP/gap token).

# Acceptance Criteria

- AC-1: `git grep -lwE "GAP" -- projects/platform-console/specs` = **0 files** (was 3).
- AC-2: `git grep -cnw "gap" -- projects/platform-console/specs` total = **1** (only L1667 "parity gap").
- AC-3: spec↔code consistency — `console-integration-contract.md` contains `"domain": "iam"` (not `"gap"`) and `[iam, wms, scm, finance, erp]`; matches `DomainHealthResponse.java`/`OperatorOverviewResponse.java` javadoc + console-web `CARD_ORDER`.
- AC-4: `git diff` touches only `projects/platform-console/specs/**`; every changed line a GAP→IAM or gap-slug→iam swap; no link target / `iam.local` URL / method-name altered.

# Related Specs

- `projects/platform-console/specs/{contracts/console-integration-contract.md, services/console-web/architecture.md, services/console-bff/architecture.md}` — aligned to the already-IAM code.
- Cross-ref `iam-platform/specs/contracts/http/console-registry-api.md` (already IAM, MONO-188).

# Related Contracts

- The console-integration-contract's wire `domain` value is corrected `gap`→`iam` to match the producing code (`DomainHealthResponse`/`OperatorOverviewResponse`). This documents the existing wire value; it does not change it.

# Edge Cases

- **Spec-vs-code drift (not retention)** — unlike MONO-190's lowercase `gap` (English word / provider id, retained), here lowercase `gap` is the **domain slug** the code already renamed to `iam`; retaining it would perpetuate a stale-doc defect. Verified against `DomainHealthResponse.java` (`"domain": "iam"`) + `CARD_ORDER = ['iam', …]`.
- **One English-word exception** — blanket lowercase pass + verbatim restore of "parity gap" (L1667) is safer than enumerating fragile slug patterns; AC-2 (=1) is the guard.
- **byte-verbatim selector** — §2.4.9 `GAP→OperatorToken` was already synced to `IAM→` in MONO-180; no `GAP→` remains. `DomainTarget.GAP` prose refs → `DomainTarget.IAM` (matches the enum).

# Failure Scenarios

- **Retaining lowercase `gap` as if MONO-190 policy applied** → leaves wire/card/metric examples contradicting the code. Prevented by AC-3 code cross-check.
- **Blanket lowercase replace without restoring "parity gap"** → corrupts English prose. Prevented by the restore step + AC-2 (=1).
