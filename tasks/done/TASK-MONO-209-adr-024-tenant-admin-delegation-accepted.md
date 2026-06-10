# Task ID

TASK-MONO-209

# Title

ADR-MONO-024 (Tenant-Admin Delegation) PROPOSED → ACCEPTED transition, incorporating two user-directed gate adjustments — D4 A→B (in-tenant sub-delegation via a new `tenant.admin.delegate` permission, own-tenant-confined) and D5 → C (entitlement self-service via a separate `TENANT_BILLING_ADMIN` role carrying `subscription.manage`, not bundled into `TENANT_ADMIN`). Doc-only governance flip + ADR-003a § 3 audit row #32. Authorizes the § 3.3 execution roadmap (UNPAUSED).

# Status

done

> **완료 (2026-06-10)**: impl PR #1252 (squash `f50c85180955ebf0176c63e07c2647db5c1ed477`). ADR-MONO-024 PROPOSED→ACCEPTED doc-only flip + 사용자 게이트 조정 2건 반영. **D4 A→B**(in-tenant sub-delegation 허용 — `TENANT_ADMIN`+신규 `tenant.admin.delegate` 권한으로 자기 테넌트 한정 `TENANT_ADMIN` 임명; D2 confinement 로 cross-tenant 구조적 불가) + **D5 → C**(별도 `TENANT_BILLING_ADMIN` role[`subscription.manage`], `TENANT_ADMIN` 미번들 → IAM↔entitlement 위임 role 레벨 분리 유지, ADR-023 separability 실현). D1-D3/D6/D7 direction 불변. ADR-020 byte-unchanged(PROPOSED additive note 가 final). ADR-003a § 3 row #32 append-only(rows #1–#31 불변). 구현 0. 3차원 ✓(MERGED `f50c8518`/origin tip 일치/PR 체크 CLEAN). 후속=§ 3.3 roadmap UNPAUSED: ① D2 confinement(net-zero) → ② `TENANT_ADMIN`+`TENANT_BILLING_ADMIN`+`tenant.admin.delegate`+assign/unassign 표면+grant-menu → ③ delegation proof e2e(MONO-207 harness 재사용). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- adr
- meta-policy
- multi-tenant
- rbac
- iam

---

# Dependency Markers

- **finalises**: TASK-MONO-208 (ADR-024 PROPOSED, #1250 squash `ca119093`).
- **user-gated**: ACCEPTED transition per the staged-child pattern (sibling ADR-023/MONO-206); the two D4/D5 adjustments are user-explicit decisions at the gate.
- **unblocks**: the § 3.3 execution roadmap — step 1 D2 confinement (net-zero) → step 2 `TENANT_ADMIN`+`TENANT_BILLING_ADMIN`+`tenant.admin.delegate`+assign/unassign surface+grant-menu → step 3 delegation proof e2e — each a separate future task, now dependency-correct from this ACCEPTED main.

# Goal

Finalise ADR-MONO-024 as ACCEPTED with the user's two gate adjustments, so the tenant-admin delegation execution roadmap is authorized and dependency-correct. Doc-only — no implementation.

# Scope

- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` — Status PROPOSED → ACCEPTED; Date/History ACCEPTED clause; § 1.3 unpause note; § 2 intro; **D4 table A→B** (in-tenant sub-delegation via `tenant.admin.delegate`, own-tenant-confined); **D5 table → C** (separate `TENANT_BILLING_ADMIN` role); D1/D3 mechanics updated where the two adjustments touch; § 3.1/3.2/3.3 + § 4/§ 5 updated; § 6 ACCEPTED row. D1-D3/D6/D7 directions unchanged.
- `docs/adr/ADR-MONO-003a-...md` § 3 — audit row #32 (Meta-policy; one-off; ACCEPTED-with-gate-adjustments).

# Acceptance Criteria

- **AC-1** ADR-024 Status = ACCEPTED; the D4 table shows B CHOSEN (sub-delegation via `tenant.admin.delegate`, own-tenant-confined) and the D5 table shows C CHOSEN (separate `TENANT_BILLING_ADMIN` role); both note the user gate-adjustment provenance.
- **AC-2** D1-D3/D6/D7 decision *directions* are unchanged from PROPOSED; only the mechanics the two adjustments touch are updated (grant-menu admits in-tenant delegated grants; D2 confinement extends to the subscription admin surface).
- **AC-3** Plane separation preserved: `subscription.manage` is in `TENANT_BILLING_ADMIN`, NOT in `TENANT_ADMIN` (the IAM and entitlement delegation planes stay distinct roles); no cross-tenant or platform escalation is introduced (sub-delegation is own-tenant only, behind an explicit permission).
- **AC-4** ADR-003a § 3 row #32 appended after #31 (append-only; rows #1–#31 byte-unchanged); ADR-020/019/021/023 byte-unchanged.
- **AC-5** No implementation: no role/permission migration, no evaluator/endpoint code — all post-ACCEPTED (§ 3.3). Doc-only PR.

# Related Specs

- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (this ADR)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (the `subscription.manage` separability D5-C realizes as a separate role)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (Seed Roles + evaluator the roadmap extends)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the surfaces mutated at post-ACCEPTED step 2)

# Edge Cases

- This ACCEPTED is NOT a pure byte-unchanged finalise — it incorporates the user's D4/D5 gate adjustments; History + the audit row record that explicitly (honest provenance, not a silent re-decide).
- HARDSTOP-04: ADR-020 stays additive-note-only (its PROPOSED-stage note is final; ACCEPTED flips only ADR-024).
- ADR-003a append-only — row #32 after #31; rows #1–#31 byte-unchanged.

# Failure Scenarios

- If `subscription.manage` were folded into `TENANT_ADMIN` during the flip → plane re-coupling (D5-C exists precisely to avoid this); AC-3 fails.
- If sub-delegation were specified without the own-tenant D2 confinement or the explicit `tenant.admin.delegate` gate → cross-tenant/platform escalation; AC-3 fails.
- If the flip edited ADR-020 D1-D6 bodies → HARDSTOP-04 violation.
