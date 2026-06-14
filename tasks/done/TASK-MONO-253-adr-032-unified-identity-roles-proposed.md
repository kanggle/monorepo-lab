# Task ID

TASK-MONO-253

# Title

Author **ADR-MONO-032 PROPOSED** — Unified identity model (single account → roles set; remove the `account_type` CONSUMER/OPERATOR partition). Records the decision to collapse the `account_type` single-immutable xor partition into the already-existing `roles` set, so one identity may simultaneously hold consumer-facing and operator-facing roles (Google-IAM "one identity + role bindings"). **Supersedes ADR-MONO-021.** Triggered by the user's explicit request (2026-06-14) to move to the Google-style unified-identity model after the CONSUMER/OPERATOR walk-through + AWS-vs-GCP comparison. Resolves HARDSTOP-09 (the identity model would otherwise be baked at implementation) + honors `jwt-standard-claims.md` § Change Rule (contract-first). Doc-only; ACCEPTED + implementation are separate tasks (sibling ADR-019/020/021/023/024 staged-child pattern).

# Status

done

> **완료 (2026-06-14)**: impl PR #1513. ADR-MONO-032 PROPOSED publish — 통합 identity 모델 결정 기록(`account_type` CONSUMER/OPERATOR xor 파티션 제거 → 기존 `roles` set 이 유일 authz 축; Google-IAM "one identity + role bindings"). **동기**: `jwt-standard-claims.md` L21 이 한 사람의 customer+operator 겸직을 구조적으로 금지(*"must provision separate accounts"*) + L85-92 no-cross-type-SSO → 사용자 명시 요청(Google식 통합 identity, AskUserQuestion D1=roles 집합/option A). 6-decision(D1-D6, CHOSEN-PROPOSED): D1 roles-set 유일 축(multi-value type·single-active-role-switch[AWS AssumeRole] 기각) / D2 aud-scoped roles 유지·account_type drop·role-scoped SSO / D3 gateway role-based admission / D4 isolation=aud-scope+role-presence / D5 backward-compat staged dual-read(zero mis-auth window) / D6 one-account-many-roles. **Supersedes ADR-021**(ACCEPTED 시점 발효). `jwt-standard-claims.md` breaking amend 는 contract-first 로 post-ACCEPTED 실행(D5 step0). doc-only(apps/·contracts/ 0). ADR-003a audit row #33(append-only). 후속(user-gated)=ADR-032 ACCEPTED transition(별 task, sibling MONO-165) → 구현 6-step(계약 rewrite → dual-read gateways → roles-only issuance → account unify → drop legacy → e2e). 분석=Opus 4.8 / 구현 권장=Opus.

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **triggered by**: user-explicit request 2026-06-14 ("Google식 통합 identity 겸직 모델로 바꾸고 싶어") + AskUserQuestion D1 = "roles 집합 클레임" (option A). Implementing the unified model without recording the decision would bake the identity model (HARDSTOP-09).
- **supersedes**: ADR-MONO-021 (ACCEPTED — `account_type` claim source). ADR-032 removes the claim's reason-to-exist.
- **breaking amend**: `platform/contracts/jwt-standard-claims.md` (§ Change Rule mandates contract-first — the contract rewrite is a post-ACCEPTED execution step, NOT in this PROPOSED ADR).
- **reconciles**: ADR-MONO-002 RBAC lineage + ADR-MONO-020/024/025/026 (role-grant substrate ADR-032 makes the sole top-level axis).

# Goal

Publish ADR-MONO-032 PROPOSED so the unified-identity model can be implemented to a recorded decision (single account, roles-set as the sole authorization axis, `account_type` removed) rather than an implicit one — with ACCEPTED and execution gated as separate user-explicit-intent tasks, and the breaking `jwt-standard-claims.md` rewrite landed contract-first at execution.

# Scope

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (NEW, Status PROPOSED) — D1 identity model (remove `account_type`; roles-set sole axis; reject multi-value type + single-active-role-switch) + D2 token shape (keep aud-scoped `roles`, drop `account_type`, role/assignment-scoped SSO) + D3 gateway role-based admission + D4 isolation via aud-scope+role-presence + D5 backward-compatible staged migration + D6 one-account-many-roles credential/provisioning.
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #33 (Meta-policy: ADR-032 PROPOSED publish; same one-off pre-author category as rows #13/#18/#22/#25/#27/#29; does NOT add to § D1).
- Doc-only. NO contract/schema/code change (HARDSTOP-09 remediation option 2: record the decision, PAUSE until ACCEPTED; `jwt-standard-claims.md` § Change Rule requires the contract rewrite to precede implementation — at the post-ACCEPTED execution step, not here).

# Acceptance Criteria

- **AC-1** ADR-MONO-032 exists with Status PROPOSED, D1-D6 CHOSEN-PROPOSED, the contract/gateway/RBAC evidence, and the staged § 3.3 roadmap.
- **AC-2** The decision driver names the concrete limitation (one person cannot be both customer and operator under the L21 xor) + the Google-IAM reference model + the user-explicit request.
- **AC-3** D1-B (multi-value `account_type`) and D1-C (single-active-role token-exchange / AWS AssumeRole shape) are recorded as rejected with reasons.
- **AC-4** ADR-032 explicitly **supersedes** ADR-MONO-021 and records the breaking-amend relationship to `jwt-standard-claims.md` (contract-first, deferred to execution).
- **AC-5** ADR-003a § 3 audit row #33 appended (append-only; rows #1-#32 byte-unchanged).
- **AC-6** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations).

# Related Specs

- `platform/contracts/jwt-standard-claims.md` (the requiring contract — § Account Types L12-22, § Standard Claims L46/L48, § SSO L85-92, § Gateway Enforcement L119-124)
- `platform/service-types/identity-platform.md` (the per-account-immutable framing this ADR overturns)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § Account Types + § Standard Claims (`account_type` / `roles`) + § Gateway Enforcement Rules.

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#32 byte-unchanged.
- ADR-032 must NOT rewrite `jwt-standard-claims.md` in this PR — the contract's § Change Rule requires the rewrite to precede *implementation*, which lands at the post-ACCEPTED execution step (D5 step 0), not at PROPOSED.
- The supersession of ADR-021 takes effect only at ADR-032 ACCEPTED — ADR-021 stays ACCEPTED-and-authoritative until then (this PROPOSED ADR records the intent, does not flip ADR-021).

# Failure Scenarios

- If the ADR is authored AND code/contract is implemented in the same task → violates HARDSTOP-09 remediation (decision must precede + be ACCEPTED) AND the contract § Change Rule sequencing. This task is doc-only.
- If ADR-032 is self-ACCEPTED in the same task → violates the staged-child pattern (ACCEPTED is a separate user-explicit-intent task). PROPOSED only.
