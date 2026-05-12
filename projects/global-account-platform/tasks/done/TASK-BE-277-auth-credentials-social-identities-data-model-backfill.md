# Task ID

TASK-BE-277

# Title

auth-service `credentials` + `social_identities` data-model.md backfill

# Status

done

# Owner

backend

# Task Tags

- spec

---

# Goal

Backfill spec drift on `projects/global-account-platform/specs/services/auth-service/data-model.md` for two tables that the V0005 / V0006 / V0007 migrations modified but were never reflected in the spec:

1. **`credentials`** — V0007 added `tenant_id VARCHAR(32) NOT NULL` (TASK-BE-229 multi-tenant Phase 2/3); V0006 added `email VARCHAR(254) NOT NULL` (login lookup key); V0007 swapped the legacy `idx_credentials_email` for `uk_credentials_tenant_email (tenant_id, email)` composite unique index. None of these are in the spec § credentials section.
2. **`social_identities`** — Entire table section is **absent** from data-model.md. V0005 created it (TASK-BE-228 / OAuth provider linking); V0007 added `tenant_id` + swapped `uk_social_provider_user (provider, provider_user_id)` for `uk_social_tenant_provider_user (tenant_id, provider, provider_user_id)`.

After this task: data-model.md is an accurate snapshot of the live auth-service schema after V0001-V0014.

---

# Scope

## In Scope

- Update `data-model.md` § `credentials` table — add `tenant_id` row, add `email` row, update **인덱스** subsection to reflect V0007 changes (`uk_credentials_tenant_email` composite UNIQUE; `idx_credentials_email` removed).
- Add a **new** `social_identities` table section to data-model.md, mirroring the format used by `credentials` / `refresh_tokens`. Source schema: V0005 creation + V0007 alterations + any subsequent V## migrations.
- Add `tenant_id` rows to both tables' Data Classification Summary (internal grade — same as refresh_tokens.tenant_id).
- Cross-reference back to V0005, V0006, V0007 migrations in the description column for traceability (mirror BE-276 jti widening note style — V0014 referenced).
- Spot-check the migration sequence: confirm no later V## migration further alters credentials or social_identities; if discovered, document.

## Out of Scope

- Production code changes (V0005-V0007 already merged; data correct).
- New migrations.
- Other tables (oauth_clients / oauth_scopes already in sync per BE-276 spot-check; refresh_tokens already fixed by BE-276).
- account-service tables (login_history, account_role_grants) — out of auth-service scope, file separately if drift exists.

---

# Acceptance Criteria

- [ ] data-model.md § credentials reflects post-V0007 schema (tenant_id + email rows + correct indexes).
- [ ] data-model.md gains a new § social_identities section with full schema.
- [ ] Data Classification Summary table updated (tenant_id internal grade for both tables).
- [ ] Migration cross-references (V0005 / V0006 / V0007) appear in column description text where applicable.
- [ ] No production code changes.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/global-account-platform/PROJECT.md`
- `projects/global-account-platform/specs/services/auth-service/data-model.md` (target)
- `projects/global-account-platform/specs/services/auth-service/architecture.md` (cross-ref for OAuth provider linking semantics)
- `projects/global-account-platform/specs/features/multi-tenancy.md` (TASK-BE-229 Phase 2/3 isolation strategy)
- `rules/traits/regulated.md` § R1 (data classification)

# Related Skills

- `.claude/skills/database/schema-spec-authoring/SKILL.md` (if exists)

---

# Related Contracts

- N/A (data-model.md is internal architecture, not contract).

---

# Target Service

- auth-service (spec only)

---

# Implementation Notes

- Read all V## migrations under `apps/auth-service/src/main/resources/db/migration/` to compose the post-V0014 schema:
  - V0001 — credentials + refresh_tokens initial
  - V0005 — social_identities created
  - V0006 — credentials.email added (composite uk_credentials_email then dropped in V0007)
  - V0007 — tenant_id added to credentials + refresh_tokens + social_identities; index swaps
- Confirm the JPA entity mapping: `Credentials.java` and `SocialIdentity.java` for column names + nullability + length.
- For social_identities Data Classification, treat OAuth provider tokens or refresh tokens (if stored) as `confidential` per R1. If only foreign keys (provider + provider_user_id) are stored, classification is `internal`.

---

# Edge Cases

- `social_identities` has additional v2 columns added in a migration after V0007 (e.g., V0015 hypothetically) — capture them all, not just V0005+V0007.
- `email` column on credentials has an unusual length / collation (V0006 might use VARCHAR(254) to match RFC 5321 SMTP max) — verify and document literal.
- Composite indexes in MySQL/MariaDB (auth-service backing DB) use the `uk_` prefix consistently; mirror the naming.

---

# Failure Scenarios

- During audit, discover a 4th drift table not flagged by BE-276 (e.g., `device_sessions` was modified by V## migration and spec is stale) — document as a finding in the PR body for separate follow-up; do NOT bundle into this PR.

---

# Test Requirements

- N/A (spec-only).

---

# Definition of Done

- [ ] credentials section updated
- [ ] social_identities section added
- [ ] Data Classification Summary updated
- [ ] Migration cross-references inline
- [ ] Ready for review

---

# Provenance

Surfaced from TASK-BE-276 spot-check finding (2026-05-11 refresh_tokens.tenant_id backfill, PR #335). Filed as separate task because the credentials + social_identities drift goes beyond BE-276's named single-table scope.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (medium-effort spec backfill — multi-migration consolidation).
