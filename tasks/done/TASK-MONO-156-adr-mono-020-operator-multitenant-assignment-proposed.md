# Task ID

TASK-MONO-156

# Title

ADR-MONO-020 작성 (PROPOSED) — operator ↔ multi-customer assignment (D3-B, AWS IAM Identity Center "user → multiple account assignments" parity). ADR-019 가 step 4 extension 으로 deferred 한 N:M operator-tenant 축 + 그 핵심 미해결점(active-tenant 토큰 스코핑)을 결정 기록. doc-only, 구현 없음.

# Status

done

> **완료 (2026-05-31)**: impl PR #977 (squash `3be2ba51`). 신규 `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` **PROPOSED** — ADR-019 § D3 가 deferred 한 N:M operator↔tenant assignment(D3-B, AWS IAM Identity Center "user → multiple account assignments" parity) + MONO-154 가 미명세로 표면화한 **active-tenant 토큰 스코핑**(도메인-facing OIDC 토큰 tenant_id 가 로그인 시 `credentials.tenant_id` 고정 → multi-assignment operator 세션-중 전환 불가)을 6-decision(CHOSEN-PROPOSED)로 결정 기록. **D2(crux)** = RFC8693 assume-tenant exchange(AWS STS AssumeRole analog, ADR-014 확장; assignment ∩ subscription 검증 후 selected tenant_id+entitled_domains short-lived 토큰 발급). D1 N:M 테이블(dual-read) / D3 entitled_domains=선택 고객 구독만(least-priv, keystone) / D4 console-web drives, **BFF pass-through 유지(ADR-017 D6)** / D5 per-assignment permission-set / D6 backward-compatible staged. **HARDSTOP-04**: ADR-019 § D3 뒤 additive supersession note(D1-D6 body byte-unchanged). **ADR-003a § 3 row #25**(Meta-policy PROPOSED publish, sibling #13/#18/#22). **staged**: PROPOSED 만 — ACCEPTED + 구현은 별 user-gated tasks(sibling ADR-019→MONO-153 패턴). docs-only. **3차원**(MERGED `3be2ba51` / tip 일치 / pre-merge 0, docs fast-lane). **BE-299 re-stage** ✓. **출처/메타**: ADR-019 런타임 활성화 arc 완료 후, 잔여 3개(gap=no-op / step4=시기상조 / D3-B=미명세) 중 유일하게 implement 가능했던 게 "D3-B ADR authoring" — HARDSTOP-09(미명세 축 → 새 ADR)를 추측 구현 대신 결정 기록으로 정공법. 후속: ADR-020 ACCEPTED 승급(별 task) → 실행(operator_tenant_assignment + assume-tenant exchange + console switcher) = ADR-019 step 4 extension entry point.

# Owner

backend

# Task Tags

- docs
- architecture
- multi-tenant

---

# Dependency Markers

- **depends on**: ADR-MONO-019 ACCEPTED(MONO-153) — D3-A(single-value `admin_operators.tenant_id` MVP) 가 본 ADR 이 supersede/extend 하는 결정. § 3.3 step 4 extension(D3-B) + Option B("Deferred, not rejected") 가 본 ADR 의 출처.
- **surfaced by**: TASK-MONO-154 런타임 조사 — operator OIDC 토큰 `tenant_id` 가 로그인 시 `credentials.tenant_id` 에서 고정됨 → multi-고객 세션 전환의 active-tenant 토큰 스코핑이 **어느 ADR 에도 미명세**(HARDSTOP-09). 본 ADR 이 그 결정을 기록.
- **staged-child 패턴**: ADR-014→MONO-110 / ADR-015→MONO-112 / ADR-017→MONO-126 / ADR-018→MONO-138 / **ADR-019→MONO-152(PROPOSED)/MONO-153(ACCEPTED)**. 본 task = ADR-020 **PROPOSED** 만(ACCEPTED 는 별 user-explicit-intent gated task).
- **orthogonal to**: ADR-005/TASK-BE-317.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (cross-cutting tenant-auth 결정 기록; HARDSTOP-04/09 규율; AWS STS AssumeRole/Identity Center parity; staged PROPOSED).

---

# Goal

ADR-MONO-019 가 D3-A(single-value `admin_operators.tenant_id` MVP)로 한정하고 step 4 extension/Option B 로 **deferred 한 N:M operator↔tenant assignment 축**을 새 ADR 로 결정 기록한다. 동시에 MONO-154 런타임 조사가 표면화한 **미명세 핵심점** — multi-고객 assigned operator 가 switcher 에서 고객을 전환할 때 그 고객으로 스코프된(`tenant_id=<selected>` + `entitled_domains`) 토큰이 도메인에 어떻게 도달하는가 — 를 해소한다(현재 OIDC 토큰 `tenant_id` 는 로그인 시 `credentials.tenant_id` 에서 고정 → 세션 중 전환 불가).

ADR-019 처럼 **결정 기록 + 영향 범위 + 마이그레이션 로드맵만**(구현 0). HARDSTOP-09(미명세 축 → 새 ADR) + HARDSTOP-04(D3-A 단일값 MVP 가정의 supersession 을 implicit 가 아닌 ADR 로 기록).

# Scope

## In scope (doc-only)

1. **신규 `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md`** (Status PROPOSED). ADR-019 구조 답습:
   - Title / Status PROPOSED / History(PROPOSED clause, staged-child) / Decision driver / Supersedes(none) · **Amends ADR-019**(§ D3 additive — D3-A single-value MVP 의 production form 이 D3-B 임을 § History/note 로 기록, ADR-019 D1-D6 body byte-unchanged, HARDSTOP-04) · Related(ADR-019 parent / ADR-014 operator-token-exchange — 본 ADR 의 active-tenant exchange 가 확장 / ADR-002 GAP admin-rbac / ADR-017 D6 BFF pass-through 보존 / multi-tenant M1-M7).
   - § 1 Context(D3-A 단일값의 한계 + multi-assignment 의 실 SaaS shape + active-tenant 토큰 스코핑 미명세점).
   - § 2 Decision (6 decisions, CHOSEN-PROPOSED 방향 + 대안 표):
     - **D1** operator_tenant_assignment N:M 테이블(operator → many customer tenants, assignment 당 permission-set). D3-A 단일값 supersede(dual-read 마이그레이션).
     - **D2 (crux)** active-tenant 토큰 스코핑 메커니즘 — switcher 선택 시 GAP 가 그 고객으로 스코프된 토큰 발급(AWS STS AssumeRole analog): operator 의 assignment(D1) ∩ 고객의 구독(ADR-019 D2) 양쪽 검증 후 `tenant_id=<selected>` + keystone `entitled_domains=<selected 의 구독>` 토큰 발급. 대안: (A) auth-service tenant-context 토큰 endpoint / (B) RFC8693 tenant-scoped token-exchange[권장, ADR-014 패턴 확장] / (C) 전환마다 re-auth[reject].
     - **D3** scoped 토큰의 `entitled_domains` = **선택 고객의 ACTIVE 구독**(union 아님 — 최소권한). keystone(BE-324) derivation 재사용, assume-tenant 경로에 적용.
     - **D4** console-web/console-bff active-tenant flow — switcher → assume-tenant exchange → tenant-scoped 토큰 → **BFF 는 pass-through 유지**(ADR-017 D6 불변). X-Tenant-Id 와 토큰 tenant_id 정합.
     - **D5** permission-set per (operator, tenant) assignment(AWS permission-set analog; RBAC 가 assignment 별 스코프). least-privilege(ADR-019 D3 ⑤ 계승).
     - **D6** zero-regression 마이그레이션 — D3-A single-value `tenant_id` ∪ D1 assignment dual-read; 기존 단일-tenant/`'*'` operator 무중단; 단계별 main-GREEN(BE-317/BE-303 규율).
   - § 3 Consequences(3.1 hard invariants — M1-M7 보존, ADR-017 D6 BFF pass-through 보존, GAP single entitlement authority, ADR-014 exchange 모델 확장 / 3.2 NOT-do — ACCEPTED + 구현 deferred / 3.3 post-ACCEPTED 실행 로드맵 sketch).
   - § 4 Alternatives / § 5 Relationships(ADR-019/014/017/002/005) / § 6 Status Transition History / § 7 Provenance(분석=Opus 4.8 / user "진행").
2. **ADR-019 amend (additive, HARDSTOP-04)**: ADR-019 § D3 또는 § History 에 "D3-A single-value 의 N:M production form 은 ADR-020 에서 결정" 1-blockquote/note 추가. ADR-019 D1-D6 body **byte-unchanged**.
3. **(선택) ADR-003a audit row**: ADR-020 PROPOSED 생성 audit row(sibling ADR-019 PROPOSED row 패턴) — 형식 확인 후.

## Out of scope

- 구현(operator_tenant_assignment 테이블/마이그레이션/exchange endpoint/console flow) — ADR-020 ACCEPTED 후 별 task.
- ADR-020 PROPOSED→ACCEPTED 승급(별 user-explicit-intent gated task, sibling MONO-153 패턴).
- ADR-019 D1-D6 body 변경(amend 는 additive note 만).
- step 4 cleanup(legacy slug 제거 — 별건, 시기상조).

# Acceptance Criteria

- **AC-1**: `docs/adr/ADR-MONO-020-...md` Status PROPOSED, 6-decision(D1-D6) CHOSEN-PROPOSED 방향 + 대안 표, ADR-019 구조 답습.
- **AC-2 (crux)**: D2 가 active-tenant 토큰 스코핑(MONO-154 미명세점)을 명시적으로 결정(AssumeRole analog, assignment ∩ subscription 검증, tenant-scoped 토큰 발급).
- **AC-3 (HARDSTOP-04)**: ADR-019 amend 는 additive(D3-A MVP 의 production form 지시 note); ADR-019 D1-D6 body byte-unchanged.
- **AC-4 (staged)**: PROPOSED 만 — ACCEPTED 는 별 task 로 명시(sibling 패턴). 구현 0.
- **AC-5**: ADR-017 D6 BFF pass-through + multi-tenant M1-M7 + ADR-014 exchange 모델 + GAP single entitlement authority 가 hard invariant 로 보존됨을 § 3.1 에 명시.
- **AC-6 (doc-only)**: 변경 = ADR-020 신규 + ADR-019 additive note(+선택 003a row) + 본 task. code 0.

# Related Specs

- `docs/adr/ADR-MONO-019-...md` § 2 D3 + § 3.3 step 4 extension(D3-B 출처). `docs/adr/ADR-MONO-014-...md`(operator-token-exchange — D2 가 확장). `docs/adr/ADR-MONO-017-...md` D6(BFF pass-through 보존). `projects/global-account-platform/docs/adr/ADR-002-...md`(admin-rbac scope). `rules/traits/multi-tenant.md` M1-M7.
- `projects/global-account-platform/tasks/done/TASK-MONO-154-...md`(active-tenant 토큰 스코핑 미명세점 surface).

# Related Contracts

- 결정 기록만 — contract 변경 없음(reconciliation 은 ADR-020 ACCEPTED 후 실행 task).

# Related Code

- (참조만, 무변경) `admin_operators`(D3-A single-value), `TenantClaimTokenCustomizer`(keystone, D2 derivation 재사용 대상), console-bff credential(D4 pass-through), TokenExchangeService/OperatorAccessTokenIssuer(D2 exchange 모델 확장 대상).

# Edge Cases

- **HARDSTOP-04**: ADR-019 amend 는 반드시 additive. D1-D6 body 한 글자도 변경 금지.
- **staged**: ACCEPTED 를 본 task 에서 하지 말 것(sibling 패턴 — 별 user-gated).
- **least-privilege**: D3(entitled_domains) 는 선택 고객 구독만(union 금지) — ADR-019 D3 Option C reject 사유(⑤) 계승.
- **BFF 불변**: D4 가 console-bff 를 rewriter 로 만들면 ADR-017 D6 위반 — pass-through 유지.

# Failure Scenarios

- ADR-019 body 변경 → HARDSTOP-04 위반 → additive note 만.
- D2 미결정(active-tenant 스코핑 안 정하면) → ADR 의 핵심 누락 → AC-2.
- 구현 포함 → scope 위반(decision record only) → doc-only.
- ACCEPTED 동시 처리 → staged 패턴 위반.

---

# Implementation Design Notes

- ADR-019 를 구조 템플릿으로(§1-7 + 대안 표 + staged History). D2 가 핵심 — MONO-154 가 표면화한 미명세점을 AssumeRole analog(RFC8693 tenant-scoped exchange, ADR-014 확장)로 결정.
- ADR-019 amend = additive note only(HARDSTOP-04). 구현 = Opus(결정 기록).

---

# Notes

- ADR-019 가 deferred 한 D3-B 의 결정 기록. MONO-152(ADR-019 PROPOSED 작성) 패턴 답습. 후속: ADR-020 ACCEPTED 승급(별 task) → 실행(operator_tenant_assignment + assume-tenant exchange + console flow). 이것이 ADR-019 step 4 extension 의 entry point. root `docs/adr/` = monorepo-level, root tasks/.
