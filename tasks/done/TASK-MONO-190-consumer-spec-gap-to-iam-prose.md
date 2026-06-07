# Task ID

TASK-MONO-190

# Title

Align the **consumer-project spec prose** that still names the IdP "GAP" → **"IAM"**, finishing the gap→iam rename arc's last functional-prose residue. The IdP project was renamed `global-account-platform` → `iam-platform` (MONO-179) and its **own** specs were aligned to "IAM" (MONO-188); the 6 consumer projects' specs still describe the IdP as "GAP" (352 uppercase occurrences across 72 files). Uppercase-token-only rename — the English word "gap", the retained OIDC provider id `gap`, and historical task-filename links are untouched.

# Status

done

# Owner

claude (Opus 4.8) — cross-project terminology alignment (6 projects' `specs/`, one atomic PR per CLAUDE.md § Cross-Project Changes + gap→iam arc precedent MONO-181/185/188). Prose-only; zero code/contract-value change.

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행**: MONO-179 (project rename `global-account-platform`→`iam-platform`), MONO-188 (iam-platform's OWN specs GAP→IAM, `/refactor-spec iam-platform`). This task does the symmetric alignment for the **consumer** side.
- **맥락**: memory `project_gap_to_iam_rename` — "residue=consumer 프로젝트 spec prose 'GAP'". With this task, the gap→iam **spec-prose dimension reaches 0** (functional residue → only intentional retentions remain: Flyway seed values, ADR historical bodies, persisted-contract `gap` provider id / `tenant_id='global-account-platform'`).

# Goal

After this task, no consumer-project spec describes the IdP as "GAP": `git grep -lwE "GAP" -- projects/{erp,scm,finance,fan,wms}-platform/specs projects/ecommerce-microservices-platform/specs` returns **0 files**. The lowercase `gap` (English word + provider id + filenames) is **unchanged** (still 19 occurrences). No code, contract value, link target, or meaning changes — terminology only.

# Scope

## Transformation rule (single, mechanical)

Replace the uppercase platform-name token **`GAP` → `IAM`** under the 6 consumer projects' `specs/` trees, matched as `(?<![A-Za-z])GAP(?![A-Za-z])` (not surrounded by a Latin letter). This:

- converts every platform reference and its compounds: `GAP IdP`/`GAP JWKS`/`GAP OIDC`/`GAP's`/`GAP-signed`/`GAP-issued`/`GAP 의`/`GAP V0012`/`GAP ADR-001`/`GAP auth-api.md` → `IAM …`;
- **does NOT touch** the lowercase English word `gap` (`honest gap`, `recorded gap`, `spec gap`, `known gap`, `forward gap`), the OIDC provider id `gap` (`/api/auth/signin/gap`, `signIn('gap')`), or historical task-filename links (`TASK-MONO-042-gap-v0013-…`) — all lowercase, all out of the match.

## In Scope

`projects/{erp-platform,scm-platform,finance-platform,fan-platform,wms-platform}/specs/**` + `projects/ecommerce-microservices-platform/specs/**` — 72 `.md` files containing uppercase `GAP`.

## Out of Scope (intentional retentions — do NOT change)

- Lowercase `gap` in any form (English word, provider id, filename links).
- Issuer values — already correct (`http://iam.local`, legacy `iam-platform`); no stale `gap` issuer in specs (verified 0).
- Flyway seed values / `tenant_id='global-account-platform'` / ADR historical bodies / persisted provider id `gap` (MONO-179 retentions).
- iam-platform's own specs (done in MONO-188); shared docs (MONO-185); `docs/adr/` links (MONO-189).
- Consumer `knowledge/`, project `docs/`, code, tasks — out of this prose-residue scope.

# Acceptance Criteria

- AC-1: `git grep -lwE "GAP" --` the 6 consumer `specs/` trees returns **0 files** (was 72).
- AC-2: lowercase `gap` count under those trees is **unchanged** = 19 (English word + provider id + filenames intact).
- AC-3: OIDC provider id retained — `git grep -nE "signin/gap|signIn\('gap'\)" --` consumer specs unchanged (3 hits).
- AC-4: `git diff` touches only `projects/*/specs/**.md`; every changed line is a `GAP`→`IAM` token swap (no path, link URL, code-fence value, or English-word change). No `.md` link target altered (`global-account-platform` string remains 0).

# Related Specs

- The 72 consumer spec files (prose label only; semantics unchanged). Canonical IdP term now matches iam-platform's own specs ("IAM", MONO-188).

# Related Contracts

- None. Persisted-contract `gap`/`global-account-platform` values explicitly retained (MONO-179).

# Edge Cases

- **Lowercase `gap` = English word** — the dominant lowercase use is the noun "gap" (honest/recorded/spec gap). The uppercase-only match is precisely what protects it. A case-insensitive replace would corrupt prose — forbidden.
- **Korean adjacency** — `GAP의` (no space) would be missed by a naive `\b`; the `(?![A-Za-z])` lookahead matches before Korean, so glued forms convert too. (Verified: no glued forms exist today, but the rule is robust.)
- **`GAP V00xx` / `GAP <doc>.md`** — the project qualifier renames (`IAM V00xx`, `IAM auth-api.md`); the version numbers and filenames are unchanged (prose qualifier only, not a path).

# Failure Scenarios

- **Case-insensitive / substring replace** → destroys English-word `gap`, provider id, filenames → meaning change. Prevented by the uppercase lookaround rule + AC-2/AC-3.
- **Touching link URLs** → broken links. Prevented by AC-4 (`global-account-platform` stays 0; no URL contains uppercase `GAP` — verified).
