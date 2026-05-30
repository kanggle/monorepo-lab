# Task ID

TASK-MONO-153

# Title

ADR-MONO-019 PROPOSED → ACCEPTED transition + recording — platform-console Real Customer-Tenant Model authorization (doc-only; ADR-014/MONO-110 + ADR-015/MONO-112 + ADR-017/MONO-126 + ADR-018/MONO-138 동형 staged-child pattern)

# Status

ready

# Owner

architecture / docs (monorepo root tasks/ — shared `docs/adr/` cross-cutting governance flip)

# Task Tags

- docs
- governance
- adr

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: ADR-MONO-019 PROPOSED merged (TASK-MONO-152 #953 main squash `b4ec7edc`, 2026-05-30).
- **prerequisite for**: the § 3.3 4-step execution roadmap (step 1 GAP model+catalog → step 2 real customers → step 3 per-domain dual-accept gate (Opus) → step 4 cleanup). **All PAUSED until this ACCEPTED transition lands.**
- **orthogonal to**: ADR-005 (GAP) / TASK-BE-317 (service-to-service workload identity ① + ④). Shares no files; either may land first.
- **model**: 분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (doc-only governance flip — sibling staged-child pattern 답습; mechanical string-replace + byte-unchanged 검증).

---

# Goal

ADR-MONO-019 (platform-console Real Customer-Tenant Model — decoupling the customer/tenant axis from the product/domain axis; PROPOSED via TASK-MONO-152 #953, main squash `b4ec7edc`) 를 **§ D6 step 0 / § D8 가 명기한 staged-child pattern** 절차대로 **ACCEPTED 로 전환**하고 recording 한다. 임의 self-declare 가 아니라 **user-explicit intent 기반**: 사용자 first message *"ADR-MONO-019 PROPOSED → ACCEPTED 승급 task ... 작성·머지"* + *"진행해"* (ambiguous form 아닌 명시 confirm form; sibling ADR-017 *"ADR-017 ACCEPTED"* / ADR-018 *"ADR-018 ACCEPTED"* 동형). ADR-MONO-014/015/017/018 의 PROPOSED → ACCEPTED 전환과 **정확히 동형 staged-child transition**.

본 task 머지 후 ADR-MONO-019 § 3.3 4-step execution roadmap 이 **UNPAUSED** 되어 step 1 (GAP backward-compatible model+catalog) 부터 dependency-correct base 로 진행 가능.

## ACCEPTED 시점 확정 (D1–D6 finalised)

ADR-MONO-019 PROPOSED 의 CHOSEN-PROPOSED direction 6 축 모두 **그대로 finalised** (변경 0; sibling ADR-014/015/017/018 ACCEPTED 시점 D-decisions byte-unchanged 와 동형):

- **D1** = reuse account-service `tenants` registry as the customer-tenant entity (semantic + seed; no new entity table).
- **D2** = new `tenant_domain_subscription` N:M table in GAP account-service (entitlement authority).
- **D3** = keep single-value `admin_operators.tenant_id` (+ `'*'`) for MVP; multi-assignment join table deferred (least-privilege; D3-C rejected).
- **D4** = subscription-driven `selectableTenants`; registry envelope shape byte-stable; zero `console-web` change.
- **D5** = domain isolation gate `tenant_id == slug` → entitlement-trust; highest-risk; dual-accept window.
- **D6** = 4-step zero-regression migration (step 0 ACCEPTED → 1 model+catalog → 2 real customers → 3 per-domain dual-accept gate (Opus) → 4 cleanup).

---

# Scope

## In Scope (impl PR = doc-only)

1. **`docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md`**:
   - `**Status:** PROPOSED` → `**Status:** ACCEPTED`.
   - `**History:**` 에 ACCEPTED clause append (PROPOSED clause byte-unchanged).
   - § 1.3 staged-pattern 서술에 minimal past-tense 정합 (*"ACCEPTED transition WAS executed as TASK-MONO-153 ... — D1-D6 finalised byte-unchanged; 4 execution steps now UNPAUSED"*). D1-D6 결정 본문 + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance **byte-unchanged** (HARDSTOP-04).
   - § 6 Status Transition History 에 ACCEPTED row **append** (PROPOSED row byte-unchanged except `#<this>` → `#953` dangling placeholder resolution — PROPOSED PR 번호 채움, 결정 무변경).
2. **`docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md`** § 3 audit table 에 row #24 **append** (ADR-019 § 3.3 step 0 명시 지시 *"ADR-MONO-003a § 3 audit-row append"*; sibling row #23 = ADR-017 ACCEPTED 형식; rows #1–#23 byte-unchanged).
3. **`tasks/INDEX.md`**: `## ready` 에 본 task entry + (reconcile) TASK-MONO-152 를 `## ready` → `## done` 으로 이동 (close chore #954 가 task 파일만 `git mv` 하고 INDEX 미갱신한 누락 보정).

## Out of Scope

- **ADR-MONO-013 § History 추가 note**: PROPOSED 단계 (MONO-152) 가 이미 demo-simplification supersession note (count 7→8) 추가 완료 → ACCEPTED 단계는 ADR-013 **byte-unchanged** (sibling ADR-014/015/017 ACCEPTED 의 ADR-013 미접촉 다수 패턴 답습; ADR-018/MONO-138 만 ACCEPTED 에서 ADR-013 추가 note 했으나 그건 Phase 8 final-row closure significance 였고 본 ADR-019 는 PROPOSED 가 이미 supersession 을 기록했으므로 재기록 불요).
- **ADR-MONO-019 D1–D6 결정 본문 변경 금지** — ACCEPTED 는 *finalise* (byte-unchanged confirm). mechanics 변경 필요 시 = PROPOSED 가 잘못 결정된 것이므로 **STOP + 사용자 보고**.
- **§ 3.3 4-step execution artifact 일체**: `tenant_domain_subscription` 테이블/Flyway/seed (step 1), real customer seed (step 2), domain `TenantClaimValidator` dual-accept + isolation IT (step 3), catalog/contract rewrite + cleanup (step 4). 모두 post-ACCEPTED future tasks (본 PR 에 0).
- 코드 / `projects/` / `apps/` / 빌드 / CI / contract 변경 0 (doc-only governance).
- ADR-MONO-014/015/016/017/018/002/005 + 모든 다른 ADR byte-unchanged.

---

# Acceptance Criteria

- **AC-1**: ADR-MONO-019 `Status: PROPOSED → ACCEPTED`; History ACCEPTED clause append (PROPOSED clause byte-unchanged); § 6 ACCEPTED row append (PROPOSED row byte-unchanged except `#<this>`→`#953` resolution); § 1.3 minimal past-tense 정합.
- **AC-2**: D1-D6 결정 본문 + § 2 + § 3 + § 4 + § 5 + § 7 **byte-identical** (`git diff origin/main` 객관 검증) — ACCEPTED = finalise, 재결정 아님 (HARDSTOP-04).
- **AC-3**: ADR-MONO-003a § 3 row #24 append (rows #1-#23 byte-unchanged); ADR-019 § 3.3 step 0 instruction 충족, sibling row #23 형식.
- **AC-4**: ADR-MONO-013 + 014/015/016/017/018/002/005 + 모든 다른 ADR **byte-unchanged** (HARDSTOP-04).
- **AC-5**: `tasks/INDEX.md` — 본 task `## ready` 등재 + MONO-152 `## ready`→`## done` reconcile.
- **AC-6 (scope-lock)**: 코드/`projects/`/`apps/`/`.github/`/contract 변경 0 — `git diff origin/main` 는 ADR-019 + ADR-003a + tasks/INDEX + 본 task 파일만.
- **AC-7**: 본 task 머지 후 ADR-MONO-019 ACCEPTED → § 3.3 4-step execution roadmap 의 dependency-correct authorization base 확립 (sibling ADR-017 → PC-BE-001/PC-FE-011 staged-execution 동형).

---

# Related Specs

- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` (target — PROPOSED → ACCEPTED flip)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 (audit-row #24 append per ADR-019 § 3.3 step 0)
- `docs/adr/ADR-MONO-014/015/017/018-*.md` — staged-child ACCEPTED transition precedents (TASK-MONO-110/112/126/138 single doc-only flip)
- `tasks/done/TASK-MONO-138-adr-mono-018-accepted-transition.md` — 가장 가까운 sibling task (직접 답습 reference)
- `tasks/done/TASK-MONO-152-adr-mono-019-customer-tenant-model-proposed.md` — 직전 단계 (PROPOSED authoring)
- `platform/hardstop-rules.md` HARDSTOP-04 (additive-only ADR amendment) / HARDSTOP-09 (PAUSE-until-ACCEPTED)

# Related Contracts

- **None changed** (doc-only ADR flip + audit-row + INDEX). PROPOSED 단계의 contract scope 정의 그대로, ACCEPTED 는 *finalise* 만.
- **Cross-referenced** (post-ACCEPTED future scope, NOT this PR): `console-integration-contract.md` § 2.2/§ 2.4.x + `console-registry-api.md` (step 1/4), per-domain `TenantClaimValidator` specs (step 3).

---

# Edge Cases

- **§ 6 `#<this>` dangling placeholder**: PROPOSED row 가 `#<this> (TASK-MONO-152)` 로 남음 (PROPOSED authoring 시 PR 번호 미해소) → `#953` 으로 채움. 결정 무변경 — accuracy-fill (sibling ADR-017 § 6 PROPOSED row placeholder resolution 동형).
- **ADR-013 재note 유혹**: ADR-018/MONO-138 이 ACCEPTED 에서 ADR-013 note 추가했으나 본 ADR-019 는 PROPOSED 가 이미 supersession note(7→8) 추가 → ACCEPTED 는 ADR-013 byte-unchanged (sibling ADR-014/015/017 다수 패턴; 중복 note 방지).
- **ADR-003a row 비대칭**: ADR-019 PROPOSED(MONO-152) 가 § 3 row 미추가 → 본 ACCEPTED row #24 가 ADR-019 의 단일 lifecycle 기록 (ADR-018 은 양 stage 모두 미추가; ADR-017 은 양 stage 추가 — 혼재 선례 중 ADR-019 § 3.3 step 0 의 명시 지시를 따름).
- **D1-D6 mechanics 변경 필요 시**: ACCEPTED 가 결정을 바꿔야 한다면 PROPOSED 본문 오류 → STOP + 사용자 보고 (정상 ACCEPTED path 아님).

---

# Failure Scenarios

- ADR-019 D1-D6 결정 본문 변경 (HARDSTOP-04 위반) → reject; ACCEPTED 는 byte-unchanged finalise.
- ADR-013/014/015/016/017/018 추가 amendment → reject; ACCEPTED 는 자기-ADR (019) 만 flip + ADR-003a row + INDEX.
- § History PROPOSED clause / § 6 PROPOSED row 본문 변경 (append-only 위반, `#<this>`→`#953` 외) → reject.
- execution artifact (`tenant_domain_subscription` / seed / validator / catalog rewrite) 가 본 PR 에 leak → reject; § 3.3 4-step future tasks 의 일.
- user-explicit intent 미확보 → reject; 본 task 의 origin = 사용자 first-message 명시 confirm form 이 그 gate 충족.

---

# Verification

- `git diff origin/main` confirms: only `docs/adr/ADR-MONO-019-*.md` (Status/History/§ 1.3/§ 6) + `docs/adr/ADR-MONO-003a-*.md` (§ 3 row #24) + `tasks/INDEX.md` + 본 task 파일.
- ADR-MONO-019 § 1/§ 2/§ 3/§ 4/§ 5/§ 7 byte-identical; ADR-013 + 014/015/016/017/018/002/005 byte-identical.
- doc-only → CI `changes` markdown fast-lane PASS + matrix skipping 예상 (sibling MONO-138/152 동형).
- BE-303 3-dim merge verification (impl PR) + BE-299 re-stage check (close chore).
- 분석=Opus 4.8 / 구현 권장=Sonnet 4.6.

---

# Notes

- staged-child ACCEPTED transition (sibling ADR-014/015/017/018). PROPOSED scopes architecture; ACCEPTED authorizes execution; § 3.3 4-step execution = 별 future tasks (dependency-correct base = 본 task ACCEPTED main).
- Lifecycle: impl PR (본 task ready 동봉 + ADR-019 flip + ADR-003a row + INDEX) → close chore (ready→done). 사용자 `feedback_pr_bundling` 케이스별 판단 + MONO-152 동형 2-PR.
- Branch `chore/mono-153-adr-019-accepted` — `master` substring 부재 ✓.
