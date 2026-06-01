# Task ID

TASK-MONO-164

# Title

Author **ADR-MONO-021 PROPOSED** — `account_type` OIDC claim source. Records WHERE the platform-required `account_type` (CONSUMER|OPERATOR) claim is stored and HOW GAP auth-service derives it at issuance (D1-D5, CHOSEN-PROPOSED). Resolves the HARDSTOP-09 surfaced when "implement the `account_type` claim" was requested: `jwt-standard-claims.md` requires the claim and the ecommerce gateway 403s every authenticated request when it is absent, but no spec/ADR specifies its source. Doc-only; ACCEPTED + implementation are separate tasks (sibling ADR-019/020 staged-child pattern).

# Status

done

> **완료 (2026-06-02)**: impl PR #1006 (squash `20f19c26`). ADR-MONO-021 PROPOSED publish — `account_type` OIDC 클레임 source 결정 기록(HARDSTOP-09 해소). D1=per-account `auth_db.credentials.account_type` denormalize(tenant_id 패턴; tenant_type·client/scope 도출 기각) / D2=provisioning 설정(signup→CONSUMER, operator→OPERATOR) / D3=access+id token 주입 / D4=staged net-positive un-break / D5=userinfo 보류. **동기 구체**: gateway `AccountTypeEnforcementFilter` 가 클레임 부재 시 모든 인증요청 403(`"CONSUMER".equals(null)=false`) → 실 correctness 갭(현재 SKIP_GAP_E2E 로 가려짐). 어느 ADR 도 amend 안 함(net-new claim). ADR-003a §3 row #27(Meta-policy, sibling #13/#18/#22/#25). doc-only(201 lines, apps/ 0). 3차원 ✓(docs fast-lane, MERGED `20f19c26`/tip 일치/0 fail). **후속(user-gated)**: ADR-021 ACCEPTED transition(별 task, sibling MONO-153/157) → 구현(credentials 컬럼+customizer/provider+provisioning+e2e). 분석=Opus 4.8 / 구현=Opus.

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **triggered by**: HARDSTOP-09 — implementing `account_type` requires an architecture decision (source/storage model) not in any spec/ADR.
- **sits alongside**: ADR-MONO-019/020 (tenant model) — `account_type` is orthogonal (classifies the person CONSUMER/OPERATOR, not the customer). No amend.
- **consumers (unchanged, evidence)**: `AccountTypeEnforcementFilter` + `JwtHeaderEnrichmentFilter` (ecommerce gateway) already read the claim.

# Goal

Publish ADR-MONO-021 PROPOSED so the `account_type` claim can be implemented to a recorded decision (per-account, denormalized on the credential) rather than an implicit one — with ACCEPTED and execution gated as separate user-explicit-intent tasks.

# Scope

- `docs/adr/ADR-MONO-021-account-type-claim-source.md` (NEW, Status PROPOSED) — D1 source model (per-account on `auth_db.credentials.account_type`, mirror `tenant_id`; reject tenant_type / client-scope derivation) + D2 provisioning assignment + D3 access+id-token injection + D4 staged net-positive migration + D5 userinfo deferred.
- `docs/adr/ADR-MONO-003a-*.md` § 3 audit table — append row #27 (Meta-policy: ADR-021 PROPOSED publish; same one-off category as rows #13/#18/#22/#25 — does NOT add to § D1).
- `docs/adr/INDEX.md` — register ADR-MONO-021 (if the index lists ADRs).
- Doc-only. NO schema/code change (HARDSTOP-09 remediation option 2: record the decision, PAUSE until ACCEPTED).

# Acceptance Criteria

- **AC-1** ADR-MONO-021 exists with Status PROPOSED, D1-D5 CHOSEN-PROPOSED, the contract/gateway evidence, and the staged § 3.3 roadmap.
- **AC-2** The decision driver names the concrete breakage (gateway 403s on absent claim) + the HARDSTOP-09 (source unspecified).
- **AC-3** D1-B (tenant_type) and D1-C (client/scope) are recorded as rejected with reasons (ecommerce has both types in one tenant; per-account immutability).
- **AC-4** ADR-003a § 3 audit row #27 appended (append-only; rows #1-#26 byte-unchanged).
- **AC-5** Doc-only diff (no `apps/` code, no migrations).

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (the requiring contract)
- `platform/service-types/identity-platform.md` (per-account-immutable framing)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § account_type + § gateway rules.

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#26 byte-unchanged.
- `account_type` must NOT be conflated with `tenant_type` (B2C/B2B) — the ADR makes the orthogonality explicit.

# Failure Scenarios

- If the ADR is authored AND code is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED). This task is doc-only.
