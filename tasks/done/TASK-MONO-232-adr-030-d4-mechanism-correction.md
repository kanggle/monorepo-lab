# Task ID

TASK-MONO-232

# Title

**ADR-MONO-030 factual correction** (D4 plane mechanism + §1.1 platform-state) — pre-Step-1 verification against the live `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` found two factual errors in the ACCEPTED ADR. **No decision reversed**: (§1.1) the "wms/scm/erp/finance all completed the row-level `tenant_id` evolution" claim overstated wms (gateway entitlement-trust only; no row-level `tenant_id`) and omitted that ecommerce already has a fixed-slug `TenantClaimValidator`; (D4) consumers do **not** stay on a separate ecommerce `auth-service` — both planes already authenticate via **platform IAM**, split by the existing `account_type` (`CONSUMER`|`OPERATOR`) claim (the standalone auth-service is slated for removal, TASK-BE-132), and D4-B's "IdP pollution" rejection premise was wrong (IAM is designed for B2C consumers). Corrected D4 = **one IdP, two planes by `account_type`**, seller axis inside the OPERATOR plane; the D4 *intent* (consumer plane ≠ operator/seller plane) and the D1 reuse framing are preserved (and sharpened). Recorded as additive correction notes under §1.1 + D4 + a §6 amendment row. Doc-only.

# Status

done

> **DONE (2026-06-12)**: ADR-MONO-030 factual correction merged. §1.1 + D4 additive correction notes + §6 amendment row + fixed the ACCEPTED row's PR ref (#1366). No decision reversed — D4 intent (two planes) + D1 reuse (= ADR-019 D5 evolution applied to ecommerce, scm/erp precedent) preserved. Unblocks Step 1 (TASK-BE-356) on an accurate basis. 분석=Opus 4.8 / 구현=Opus 직접.

# Owner

architecture

# Task Tags

- docs
- adr
- multi-tenant
- ecommerce
- correction

---

# Dependency Markers

- **선행 (prerequisite)**: TASK-MONO-231 (ADR-030 ACCEPTED).
- **corrects**: ADR-MONO-030 §1.1 (platform-state description) + D4 (plane mechanism) — factual amendment, no decision reversed.
- **unblocks**: TASK-BE-356 (ecommerce Step-1 tenancy specs) — proceeds on the corrected basis (account_type plane + ecommerce fixed-slug gate → entitlement-trust evolution).

# Goal

Correct two factual errors in the ACCEPTED ADR-MONO-030 before authoring Step 1, so the slice specs sit on an accurate description of ecommerce's existing IAM integration and the platform's actual multi-tenancy implementation state — without reversing any decision.

# Scope

## In Scope

- `docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md` — additive correction notes under §1.1 (accurate platform-state: ecommerce has a fixed-slug `TenantClaimValidator`; scm/erp carry row-level `tenant_id`, wms does not) + under D4 (plane mechanism = `account_type` on IAM, not a separate auth-service; D4-B premise corrected) + a §6 amendment row + fix the ACCEPTED row PR ref (#1366). D1-D8 decision bodies otherwise byte-unchanged.
- This task file (done) + `tasks/INDEX.md`.
- Doc-only.

## Out of Scope

- **Any decision reversal.** D4's intent (consumer plane ≠ operator/seller plane) and D1's reuse framing stand. The correction changes the *mechanism description* (where consumers authenticate) and a *state description* (which domains carry row-level `tenant_id`), not the decisions.
- Any code, spec (other than the ADR), migration, or `PROJECT.md` change.

# Acceptance Criteria

- **AC-1** §1.1 corrected: ecommerce has a fixed-slug `TenantClaimValidator` (the ADR-019 "before" state); scm/erp carry row-level `tenant_id` (slice references); wms does not. The promotion = ADR-019 D5 evolution applied to ecommerce.
- **AC-2** D4 corrected: one IdP (IAM), two planes by `account_type` (CONSUMER|OPERATOR); consumers are NOT on a separate auth-service (slated for removal); D4-B "pollution" premise corrected (IAM is designed for B2C consumers). Seller axis inside the OPERATOR plane.
- **AC-3** §6 has an amendment row stating **no decision reversed** + the source (live iam-integration.md). ACCEPTED row PR ref fixed to #1366.
- **AC-4** D1-D8 decision *bodies* byte-unchanged; only additive correction notes + §6 row + the PR-ref fix. Doc-only.

# Related Specs

- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` (the live source: `tenant_id='ecommerce'` fixed-slug gate, `account_type` CONSUMER|OPERATOR plane, auth-service removal roadmap)
- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` D5 (the entitlement-trust evolution ecommerce now applies) + `projects/scm-platform` / `projects/erp-platform` migrations (row-level `tenant_id` reference implementations) + `projects/wms-platform/specs/services/*/architecture.md` ("multi-tenant data out of v1")

# Related Contracts

- None (doc-only).

# Edge Cases

- The PROPOSED/ACCEPTED §6 rows are append-only history — they record what was decided *then* (incl. the now-corrected D4-A wording); they are NOT edited. The correction lives in additive notes + the new §6 amendment row.
- The correction must not read as a decision reversal — the D4 intent and D1 reuse framing are explicitly preserved; only mechanism/state descriptions change.

# Failure Scenarios

- If Step 1 specs were authored on the un-corrected D4 (consumers on a separate auth-service), they would contradict the live integration (consumers on IAM via `account_type`) + the auth-service removal roadmap (TASK-BE-132) — propagating the error into the slice's source of truth. This task prevents that.
- If the correction edited a D1-D8 decision body, it would risk reading as a redesign of an ACCEPTED ADR — prevented by using additive notes + a §6 amendment row (ADR-019 amendment discipline).

# Notes

- 분석=Opus 4.8 / 구현 권장=Opus (ADR amendment discipline + accurate reconciliation against the live integration spec). doc-only.
