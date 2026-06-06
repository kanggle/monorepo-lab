# Task ID

TASK-MONO-188

# Title

`/refactor-spec iam-platform` **naming** closure — align the iam-platform project's **own** spec prose `GAP` / `Global Account Platform` (옛 프로젝트명) → `IAM` (current-architecture descriptor). 28 files / 121 refs. Project-internal layer that MONO-180/185 deferred; user chose (2026-06-07 AskUserQuestion) "current-arch alignment, historical residue preserved". Naming refactor only — no requirement/contract-semantic change (the few lowercase value edits reconcile the spec to MONO-180's already-deployed reality).

# Status

done

# Owner

claude (Opus 4.8) — `/refactor-spec iam-platform` scan-mode closure (naming category). Single project (iam-platform/specs), one atomic PR.

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code

---

# Dependency Markers

- **선행/맥락**: `/refactor-spec iam-platform` scan (2026-06-07). dead-reference/structure/consistency/duplication/missing/orphan/clarity = clean (MONO-181 spec dead-ref + 186/187 anchor + ADR-012 canonical + validate-rules PASS). **naming (GAP→IAM)** = the sole substantive finding (114 uppercase GAP + 7 "Global Account Platform" across 28 files). This is the iam slice (121/896) of the project-internal spec prose that MONO-180/185 kept as residue.
- **user decision**: AskUserQuestion → "current-arch만 정합 (careful)". glossary/naming-conventions are project-agnostic (no IAM-term mandate) → purely consistency-driven; iam's OWN specs carrying "GAP" was the most incongruent residue.

# Goal

After this task, no `GAP` / `Global Account Platform` (the old project name) remains as a current-architecture descriptor in `projects/iam-platform/specs/**`. The IdP project is named `IAM` throughout its own specs. Lowercase `gap` survives **only** as the intentionally-reserved tenant word (kept per MONO-180). No API endpoint, schema, event payload, or status-code changes. Anchors + dead-refs stay 0.

# Scope

## In Scope (28 files, naming alignment)

- **Uppercase `GAP`→`IAM`** (project name, current-arch): 25 files / 114 refs — contract prose (`GAP auth-service`, `GAP OIDC`, `GAP client_credentials`, `GAP JWKS`...), service/feature/use-case prose, 2 section headings (`#### GAP 내부 소비자 의무 표`, `## GAP OIDC Subject-Token Validation`). Headings safe to rename (verified **0 inbound `#gap-…` anchor links**).
- **`Global Account Platform`→`Identity & Access Management`**: 3 files (account-service/auth-service/gateway-service overview.md).
- **Lowercase value reconciliation to MONO-180-deployed reality** (not new decisions):
  - `account-tenant-domain-subscriptions.md` domainKey enum + §prose `gap`→`iam` (DomainTarget.GAP→IAM slug, MONO-180).
  - `admin-api.md` reserved-tenant-word list: **add `iam`** after `gap` — consistency fix (the other 2 listings `consumer-integration-guide.md`+`multi-tenancy.md` already have both `gap`+`iam`; admin-api was the stale duplicate; code `CreateTenantUseCase.RESERVED` has both).
  - `auth-api.md` + `consumer-integration-guide.md` example issuer `https://gap.example.com`→`https://iam.example.com` (illustrative; the live `iss` is `iam` per MONO-179).

## Out of Scope (residue / other-scope findings, reported not fixed)

- **Lowercase reserved word `gap`** (3 spec listings) — kept (MONO-180 deliberately keeps `gap` reserved so old tokens stay invalid; both `gap`+`iam` now reserved).
- **iam-platform `docs/adr/` ADR bodies** (ADR-001 "GAP as OIDC AS" etc.) — historical decision records, MONO-180 residue (not under `specs/`).
- **NEW finding — `docs/adr/ADR-MONO-001/003` dangling `projects/global-account-platform/...` paths** — rename-introduced broken file-refs in repo-root `docs/adr/` (MONO-181 scanned `platform/`+`specs/` only, not `docs/adr/`). Out of this task's iam-spec scope → **future MONO task** (dead-ref class in `docs/adr/`).
- Any consumer-integration-guide content staleness beyond naming (e.g. "6 도메인 (…향후 erp/scm/mes…)" — mes dropped, erp/scm live) — requirement/content, not naming; reported.

# Acceptance Criteria

- AC-1: `git grep -nE '\bGAP\b|Global Account Platform' -- 'projects/iam-platform/specs/**/*.md'` returns **0**.
- AC-2: Remaining lowercase `gap` in iam specs = exactly the 3 reserved-word listings (intentional); all 3 listings identical (`…public, gap, iam, auth…`) + match code.
- AC-3: Repo-wide anchor-existence sweep = **0 broken** (2 heading renames verified no inbound `#gap-…` links); iam spec file dead-ref = 0.
- AC-4: `git diff` confined to `projects/iam-platform/specs/**` (28 files); no endpoint/schema/payload/status-code change — only project-name prose + the documented lowercase reconciliations.

# Related Specs

- `projects/iam-platform/specs/**` (28 files: contracts/{events,http,http/internal}, features, services/{account,auth,admin,gateway,security}-service + admin-web tombstone).

# Related Contracts

- API/event contract **values unchanged** (endpoints, schemas, payloads, status codes byte-identical). The `domainKey` enum + reserved-set edits **reconcile the spec to MONO-180's deployed contract** (the contract changed in MONO-180; the spec was stale) — not new contract decisions.

# Edge Cases

- **`GAP`→`IAM` collision-free** — every uppercase `GAP` in iam specs is the IdP project name (verified per-context; no English/acronym). Case-sensitive `.Replace` leaves lowercase reserved `gap` untouched.
- **Heading anchor safety** — the 2 GAP headings had 0 inbound `#gap-…` links; renaming them creates `#iam-…` anchors with no dangling inbound (repo-wide anchor sweep = 0 after).
- **"historical residue" interpretation** — per MONO-182/183 precedent, dated events keep DATES but the project NAME aligns to IAM (e.g. admin-web tombstone "IAM admin-web 폐기 2026-05-18"). True GAP-residue (ADR bodies) lives outside `specs/`. No "옛 GAP" provenance literals existed in iam specs (verified) → none to preserve.

# Failure Scenarios

- **Touching a contract VALUE without code backing** → would be a real contract change. The only value edits (domainKey, reserved-set) reconcile to MONO-180's already-deployed code; verified against `CreateTenantUseCase.RESERVED` + DomainTarget.
- **Breaking a heading anchor** → re-run repo-wide anchor sweep (AC-3 = 0).
- **Over-aligning the reserved `gap`** → kept (lowercase reserved word, intentional).
