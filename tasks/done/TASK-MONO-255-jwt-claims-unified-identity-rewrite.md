# Task ID

TASK-MONO-255

# Title

**ADR-MONO-032 D5 step 0 (contract-first)** — rewrite `platform/contracts/jwt-standard-claims.md` for the unified identity model. Remove the `account_type` CONSUMER/OPERATOR xor partition from the contract: make the `roles` set the sole authorization axis, allow one identity to hold consumer + operator roles, lift cross-type SSO, and replace gateway account-type enforcement with role-based admission. Defines the migration-compatibility (dual-read) window so the downstream gateway/issuance steps (D5 steps 1-4) land against a recorded target. Contract-only; no `apps/` code (that is steps 1-6).

# Status

done

> **완료 (2026-06-14)**: ADR-MONO-032 § 3.3 / D5 **step 0 (contract-first)**. `jwt-standard-claims.md` unified-identity 전면 rewrite + `platform/service-types/identity-platform.md` 정렬(계약-층 일관성, HARDSTOP-06 회피). 변경: account_type **Required→Deprecated**(migration-only, gateways MUST NOT gate, D5 step4 제거) / `roles`=유일 authz 축 / § Identity Model(account-type 파티션 제거, consumer-facing·operator-facing=role capability, one identity holds both) / gateway **role-based admission**(fan FAN-family / ecommerce path→admin·consumer role / wms·erp·scm operator role) + **dual-read migration window**(legacy account_type OR role-based, zero mis-auth) / cross-type SSO 해제(role-possession scoped) / X-Account-Type 주입 제거 / § Migration Compatibility 신규(D5 staged 표). identity-platform.md: Account Type Rules→Identity & Role Capability Rules, SSO Scope Rules 재작성, required claims·lifetime·relying-party·gateway-integration·audit 필드 정렬. **contract-only**(apps/ 코드 0). ADR-032 D1-D6 정합(AC-7). 후속 = D5 step1 dual-read gateways(5 gw: ecommerce/wms/erp/scm/fan) 구현. 분석=Opus 4.8 / 구현 권장=Opus.

# Owner

architecture

# Task Tags

- docs
- contract
- security

---

# Dependency Markers

- **executes**: ADR-MONO-032 (ACCEPTED 2026-06-14) § 3.3 step 0 / D5 step 0 — contract-first rewrite. Mandated by the contract's own § Change Rule (the contract MUST be updated before any service emits or any gateway enforces the change).
- **supersedes (claim)**: the `account_type` Required claim defined by ADR-MONO-021 (now SUPERSEDED).
- **unblocks**: D5 step 1 (dual-read gateways) + step 2 (roles-only issuance) + step 3 (account unify) + step 4 (drop legacy) + step 5 (e2e) — each a separate downstream task that implements against this rewritten contract.

# Goal

Land the unified-identity JWT contract (roles-set sole axis; `account_type` demoted to deprecated/migration-only; cross-type SSO lifted; gateway role-based admission with a defined dual-read window) so the gateway + issuance implementation steps proceed against a recorded, ADR-032-consistent target — contract-first per the contract's § Change Rule.

# Scope

- `platform/contracts/jwt-standard-claims.md` — rewrite:
  - **§ Identity Model** (was § Account Types) — one identity + a set of roles; one identity MAY hold consumer-capability (`CUSTOMER`/`FAN`) AND operator-capability (`WMS_OPERATOR`…) roles; remove the L21 xor constraint.
  - **§ Standard Claims** — `account_type` Required → **Deprecated** (migration-only; gateways MUST NOT gate on it; removed at migration completion). `roles` reaffirmed as the sole authorization axis.
  - **§ Role Strategy** — remove the "CONSUMER single-role" asymmetry; roles is a unified set; still aud-scoped.
  - **§ SSO Scope** — lift no-cross-type-SSO; SSO scoped by role-possession on the target platform.
  - **§ Gateway Enforcement** — replace the `account_type` partition (old step 6) with role-based admission (fan: FAN-family; ecommerce path→admin/consumer role; wms/erp/scm: operator role); define the **dual-read migration window** (gateways accept legacy account-type OR role-based during transition); drop `X-Account-Type` injection (deprecated).
  - **§ Migration Compatibility** (NEW) — the ADR-032 D5 staged window: account_type still emitted during dual-read, removed at completion.
  - **Token Examples** — updated to roles-only; add a dual-capability example (one account, consumer + operator roles).
  - **§ Change Rule** — record this contract change.
- Contract-only. NO `apps/` code, NO gateway/issuance change (those are D5 steps 1-4, separate tasks).

# Acceptance Criteria

- **AC-1** § Identity Model replaces § Account Types; the L21 xor constraint ("a single account cannot hold both account types") is removed; one identity holding both consumer and operator roles is explicitly allowed.
- **AC-2** `account_type` is no longer Required — marked Deprecated (migration-only, gateways MUST NOT gate on it, removed at completion); `roles` is the sole authorization axis.
- **AC-3** § Gateway Enforcement defines role-based admission per platform + the dual-read migration window (legacy account-type OR role-based); `X-Account-Type` injection removed/deprecated.
- **AC-4** § SSO lifts the no-cross-type rule (role-possession scoped).
- **AC-5** Token Examples updated (no required account_type) + a dual-capability example added.
- **AC-6** § Change Rule records the change; contract-only diff (no `apps/`).
- **AC-7** Fully consistent with ADR-MONO-032 D1-D6 (no contradiction).

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (the ACCEPTED decision this executes — D1-D6)
- `docs/adr/ADR-MONO-021-account-type-claim-source.md` (SUPERSEDED — the claim being removed)
- `platform/service-types/identity-platform.md` (the per-account-immutable framing — to be reconciled in a later step if it references account_type as required)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (this file).

# Edge Cases

- The contract must define the **dual-read window** so step 1 (dual-read gateways) does not mis-authorize legacy tokens — account_type stays emitted and accepted until D5 step 4.
- `roles` is aud-scoped: the contract must NOT flatten roles cross-platform (ADR-032 D2-A; D2-B rejected) — a token carries only its `aud`'s roles, preserving per-token least privilege.
- Removing `account_type` as Required is a **breaking** contract change — the § Change Rule coordinated-rollout clause applies; the dual-read window is what makes the rollout non-breaking in practice.

# Failure Scenarios

- If the rewrite drops account_type abruptly (no dual-read window) → step 1 gateways would 403 legacy tokens still carrying account_type-based expectations / reject in-flight; the migration window must be explicit.
- If `apps/` gateway or issuance code is changed in this task → violates contract-first sequencing (those are D5 steps 1-4, separate tasks).
- If the rewrite contradicts ADR-032 D1-D6 → HARDSTOP (contract must follow the ACCEPTED decision).
