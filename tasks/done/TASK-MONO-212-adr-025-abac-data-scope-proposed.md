# Task ID

TASK-MONO-212

# Title

ADR-MONO-025 (PROPOSED) — ABAC Data-Scope Generalization. Author the committed PROPOSED ADR that formalises the erp-only `org_scope` data-scope (ADR-MONO-020 § D3 amendment) into a named, documented, cross-domain **attribute-based data-scope** pattern: the canonical signed `data_scope` claim (with `org_scope` retained as a dual-read alias, which erp already does), a per-domain opaque-token interpretation contract (deny-by-default, `["*"]`/absent = unrestricted = net-zero), and a first extension to a second domain (PROPOSED: wms warehouse-scope). The higher-cost 2단계 (role + condition: time/IP/resource-tag) is explicitly DEFERRED — no policy engine. Doc-only; the ACCEPTED transition + execution are separate follow-up tasks (ADR § 3.3).

# Status

done

> **완료 (2026-06-11)**: PROPOSED ADR 작성 PR #1268 (squash `1484c611`). 3차원 ✓ (MERGED / origin/main tip=`1484c611` 일치 / doc-only 체크 pass). `ADR-MONO-025` Status=PROPOSED, D1~D7 + alternatives + 019~024 관계 + Status Transition History 작성. 핵심 발견 반영: erp converter 가 이미 `org_scope`/`data_scope` dual-read(latent hook) → D1 canonical `data_scope`+`org_scope` alias 무손실 / 주입 producer=`TenantClaimTokenCustomizer`(데모 워킹트리 파일) → D5 producer 무변경(consumer-side canonicalisation)으로 데모파일 미접촉. 다음=ACCEPTED 전환(사용자 게이트: D3 첫 도메인 wms vs finance 등) 후 §3.3 실행. 분석=Opus 4.8.

# Owner

backend

# Task Tags

- adr
- abac
- multi-tenant
- iam
- doc

---

# Dependency Markers

- **amends**: ADR-MONO-020 § D3 (the erp `org_scope` data-scope) additively — generalises the one-off into a cross-domain pattern.
- **follows**: ADR-MONO-023 (axis ③, CLOSED) + ADR-MONO-024 (axis ①, CLOSED) — this is axis ② of the same AWS/GCP-comparison improvement list, ADR-first per the established 019…024 staged pattern.
- **blocks**: the ADR § 3.3 execution roadmap (ACCEPTED transition → `platform/abac-data-scope.md` + shared dual-read util → wms enforcement). None start until ACCEPTED.

# Goal

Record the ABAC data-scope decision so a second domain can adopt data-scoping from a contract instead of re-deriving erp's code, while keeping the change net-zero and explicitly bounding scope to 1단계 (the attribute) — deferring 2단계 (conditions) and rejecting a full policy engine.

# Scope

- NEW `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` (Status PROPOSED) — D1-D7 + alternatives + relationship to ADR-019/020/021/023/024 + Status Transition History.
- This task file.

**Out of scope** (post-ACCEPTED, separate tasks): the `platform/abac-data-scope.md` contract, the shared dual-read utility, any wms enforcement code, any producer/token-customizer change, any 2단계 condition work.

# Acceptance Criteria

- **AC-1** `ADR-MONO-025` exists with Status PROPOSED and the D1-D7 decisions (canonical `data_scope` + `org_scope` alias; per-domain opaque-token interpretation + deny-by-default; first domain = wms (PROPOSED); net-zero; producer-unchanged consumer-side canonicalisation; 2단계 deferred; staged execution).
- **AC-2** The ADR records the accurate current state: `org_scope` is the erp data-scope from ADR-020 § D3, carried as a signed claim, and erp's converter already dual-reads `org_scope`/`data_scope` (the latent generalisation hook).
- **AC-3** The ADR explicitly defers 2단계 (role+condition) and rejects a full policy engine (§ D6 + Alternatives).
- **AC-4** Status Transition History has the PROPOSED row with the user intent quote.
- **AC-5** Doc-only — no code, no contract, no producer change in this PR.

# Related Specs

- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` § D3 (the `org_scope` amendment being generalised)
- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (sibling RBAC axis; ABAC composes with it)

# Related Contracts

- none (the `platform/abac-data-scope.md` contract is a post-ACCEPTED deliverable)

# Edge Cases

- The claim producer (`TenantClaimTokenCustomizer`) currently emits `org_scope`; the ADR's D5 keeps it unchanged (consumer-side canonicalisation), which — beneficially — means 1단계 implementation need not touch that file.
- erp already dual-reads `org_scope`/`data_scope`, so the canonical-name decision (D1) is net-zero for erp.
- `["*"]`/null/absent = unrestricted must be stated as the net-zero invariant so a second domain's opt-in default is "behave as today".

# Failure Scenarios

- If the ADR named a full policy engine as the decision, it would re-introduce the 고비용 scope the user explicitly rejected — § D6 + Alternatives bound it to the attribute only.
- If D1 chose a hard rename instead of the alias, 1단계 would become a breaking, churny producer change — the dual-read alias keeps it net-zero.
- If the ADR omitted deny-by-default, a mis-interpreted scope token could widen access — § 3.1 pins data-scope as narrow-only, never escalation.
