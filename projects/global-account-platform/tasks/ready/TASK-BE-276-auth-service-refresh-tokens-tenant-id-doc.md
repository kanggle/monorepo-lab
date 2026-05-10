# Task ID

TASK-BE-276

# Title

auth-service refresh_tokens.tenant_id column doc verification + backfill

# Status

ready

# Owner

backend

# Task Tags

- spec

---

# Goal

`projects/global-account-platform/specs/services/auth-service/data-model.md` `refresh_tokens` table definition (lines 25–40 per /refactor-spec audit) does NOT show a `tenant_id` column, but the spec narrative (line 174) claims "모두 `tenant_id` NOT NULL" (TASK-BE-248 multi-tenant Phase 2/3 closure). Verify whether:
- (A) Column exists in DB but doc is stale → backfill the column row in data-model.md.
- (B) Column missing from DB (TASK-BE-248 compliance gap) → file follow-up with migration + production code change.

After this task: `refresh_tokens` table definition in data-model.md is accurate vs the actual schema, and any compliance gap is escalated.

---

# Scope

## In Scope

- Read `apps/auth-service/src/main/resources/db/migration/` for the refresh_tokens table definition (latest schema after V0014 + any Phase 2/3 migration files).
- Compare against `data-model.md` § refresh_tokens table.
- Decision tree:
  - **If column exists**: add row `| tenant_id | VARCHAR(32) | NOT NULL, INDEX | internal | (R8) cross-tenant isolation 키 |` to data-model.md + add `idx_rt_tenant_account` index documentation.
  - **If column missing**: file separate task with Flyway migration + JPA entity update + IT regression verification (this would touch production code — NOT this task).
- Spot-check `oauth_clients`, `oauth_scopes`, `login_history`, `account_role_grants` tables for the same potential drift (memory `project_gap_idp_promotion.md` says "all tables" got `tenant_id` NOT NULL).

## Out of Scope

- Database migration (file separately if column missing).
- Production code changes.

---

# Acceptance Criteria

- [ ] `data-model.md` `refresh_tokens` table reflects actual schema.
- [ ] If 4 other tables are spot-checked, document audit result in PR body.
- [ ] If migration gap surfaces, separate task filed.
- [ ] No production code changes (spec-only).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `projects/global-account-platform/specs/services/auth-service/data-model.md` (target)
- `projects/global-account-platform/docs/adr/ADR-001-gap-oidc-as-promotion.md` (multi-tenant Phase 2/3 context)

---

# Related Contracts

- N/A.

---

# Target Service

- auth-service (spec only — unless gap triggers code task)

---

# Implementation Notes

- `find apps/auth-service/src/main/resources/db/migration/ -name "*.sql" | xargs grep -l "refresh_tokens"` to locate all refresh_tokens-related migrations.
- The TASK-BE-248 multi-tenant migration likely adds tenant_id via ALTER TABLE in a later V file; check that the latest schema is the merge of all migrations.

---

# Edge Cases

- Column exists but is nullable (not NOT NULL per spec narrative) → flag as separate finding for production code review.
- Index exists but with a different name than `idx_rt_tenant_account` → use the actual name.

---

# Failure Scenarios

- Migration files conflict (two V files both add tenant_id) → document the merge sequence as findings.

---

# Test Requirements

- N/A (spec-only).

---

# Definition of Done

- [ ] data-model.md verified or updated
- [ ] Audit result for 4 other tables documented in PR body
- [ ] No production code changes (or separate task filed)
- [ ] Ready for review

---

# Provenance

Surfaced from `/refactor-spec all` (2026-05-11) audit Finding [GAP 2]. Skipped from PR #326 because requires DB schema verification beyond spec-internal drift.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (audit-and-backfill, low complexity).
