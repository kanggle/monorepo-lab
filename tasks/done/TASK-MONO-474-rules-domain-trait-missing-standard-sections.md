# Task ID

TASK-MONO-474

# Title

규칙 라이브러리 Tier-3 authoring — domain/trait sibling 누락 표준 섹션 채우기 (ecommerce + Family-B traits)

# Status

done

# Owner

monorepo (root tasks/ — shared `rules/`)

# Task Tags

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: `TASK-MONO-470` `/refactor-spec platform+rules` 감사의 Tier-3 findings 1.1 / 1.2. MONO-470 은 Tier-1(기계적)만, MONO-471 은 Tier-2 defects 만 랜딩. **이 task 는 authoring(신규 내용) 이라 refactor-spec 밖** — 감사가 "MEANING-CHANGE: skip → separate authoring task" 로 명시 분리.
- **prerequisite for**: 없음.
- **execution constraint**: `rules/domains/ecommerce.md` + `rules/traits/{batch-heavy,content-heavy,multi-tenant,read-heavy}.md`. classifier block 아님. **authoring = 사용자/도메인 검토 필요** (새 규칙 문장은 새 제약이 될 수 있음 — sibling 패턴 + 각 파일 기존 규칙에서 도출만, 새 결정 금지).
- **model**: 분석=Opus 4.8 / 구현=Opus 4.8 (규칙 authoring, 신중).

---

# Goal

domain/trait sibling 그룹에서 다수 파일이 보유하나 일부가 누락한 표준 섹션을, **각 파일의 기존 규칙 + sibling 패턴에서 도출**하여 채운다. 새 규칙·새 제약을 만들지 않고 이미 그 파일에 있는 내용을 표준 섹션 구조로 표면화한다.

---

# Scope

## In Scope (authoring — 각 섹션은 파일 기존 내용에서 도출, 새 결정 금지)

1. **1.1 — `rules/domains/ecommerce.md` 표준 섹션 4개 추가** (다른 6 domain sibling 이 전부 보유: `erp/fintech/saas/scm/wms/fan-platform`):
   - `## Integration Boundaries` (`### 외부 (플랫폼 경계 바깥)` / `### 내부 (같은 프로젝트 내 다른 서비스)`) — ecommerce 의 기존 서비스 경계(PG·product·order·payment·search 등, 파일 내 Bounded Contexts + Mandatory Rules 에서 도출).
   - `## Required Artifacts` — sibling 패턴 + ecommerce 기존 규칙에서 도출.
   - `## Interaction with Common Rules` — 현 `## 관련 traits`(2.2/3.2)를 이 표준 heading 으로 이관·보강(sibling 은 platform/architecture·error-handling cross-ref 포함).
   - `## Checklist (Review Gate)` — ecommerce 의 Mandatory Rules/Forbidden Patterns 를 review 항목으로 재표면화(새 규칙 아님).
   - **재배치**: `Standard Error Codes` 를 `Ubiquitous Language` 뒤·`Mandatory Rules` 앞으로(sibling 순서 정합, 순수 이동).
2. **1.2 — Family-B trait 4파일 표준 섹션 3개 추가** (`batch-heavy/content-heavy/multi-tenant/read-heavy`; Family-A 5파일 `audit-heavy/integration-heavy/internal-system/regulated/transactional` 이 보유):
   - `## Required Artifacts` · `## Interaction with Common Rules` · `## Checklist (Review Gate)` — 각 trait 의 기존 Mandatory Rules 에서 도출.
   - **2.1 정리**: 4파일의 malformed `## Overrides` 스텁("해당 없음", 문서화된 bullet 포맷 위반, 12 sibling 은 섹션 생략)을 삭제 또는 정경 포맷 정합(둘 중 authoring 판단 — 삭제가 sibling 다수 정합).

## Out of Scope

- **F1/F5**(`#`/`##` umbrella·primary level 정규화) — 저가치 cosmetic + **`hardstop-rules.md` 는 훅 결합**(`.claude/hooks/hardstop-detect.ps1` + `hardstop-body-canonical-sync.ps1` 테스트가 파싱, classifier-blocked) → 별건 신중 검토. MONO-472 조사 기록.
- **새 error code / 새 rule / 새 제약** — 도출만, 신규 결정 금지. 도출 불가한 섹션 내용은 finding 으로 보고하고 사용자 확인.
- 2.5(error-code dup pattern)·F8(hardstop Change Rule 면제) — 판단 별건.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)**: 착수 시 sibling 섹션 skeleton 재확인(MONO-470 이후 변경 가능 — Anti-patterns→Forbidden 은 이미 470 랜딩).
- [ ] **AC-1**: ecommerce.md 가 6 domain sibling 과 동일 섹션 집합·순서 보유(4 섹션 추가 + Standard Error Codes 재배치).
- [ ] **AC-2**: Family-B trait 4파일이 Family-A 5파일과 동일 섹션 집합 보유(3 섹션 추가 + Overrides 스텁 정리).
- [ ] **AC-3 (새 결정 없음)**: 추가된 모든 섹션 내용이 그 파일의 기존 규칙 또는 sibling 패턴에서 도출됨(새 error code·새 제약 0). 도출 불가분은 finding 보고.
- [ ] **AC-4 (참조 무손상)**: 추가/이관 heading 이 기존 anchor 인용 안 깨뜨림(grep). `claude-reference-integrity`·`domain-error-code-registry`·`index-queue-drift` 가드 GREEN.
- [ ] **AC-5 (scope-lock)**: diff = ecommerce.md + 4 trait 파일 + task lifecycle 만.

---

# Related Specs

- `rules/domains/ecommerce.md`(1.1) + 6 domain sibling(패턴 기준: `erp/fintech/saas/scm/wms/fan-platform`).
- `rules/traits/{batch-heavy,content-heavy,multi-tenant,read-heavy}.md`(1.2) + Family-A 5 sibling(패턴 기준).
- `rules/README.md § On-Demand Generation Policy`(생성 포맷 = ecommerce/transactional 구조 따름).

# Related Contracts

- None. 규칙 문서 구조. 새 error code 금지(AC-3) → 계약 무영향.

---

# Edge Cases

- **Integration Boundaries 내용이 도출 불가** — ecommerce 외부 통합점이 파일에 없으면 새로 발명 금지(그건 결정). 파일·ecommerce specs 에서 도출되는 만큼만, 나머지는 finding.
- **Checklist 가 새 규칙을 은근히 추가** — review 항목은 기존 Mandatory Rules/Forbidden Patterns 의 재표면화여야 함, 새 gate 추가 금지.
- **Overrides 스텁 삭제 vs 정합** — 삭제가 12 sibling 다수 정합이나, "명시적 no-override 선언" 가치 주장 가능 → authoring 판단 + 근거 기록.

---

# Failure Scenarios

- **authoring 이 새 규칙 주입** → AC-3 위반. 도출만.
- **cosmetic F1/F5 를 여기 끌어옴** → Out of Scope(hardstop 훅 결합 위험).
- **다른 파일 수정** → AC-5 fail.

---

# Verification

- (미착수 — ready 백로그) 사용자 검토 필요한 authoring. 도메인/trait 별 분할 착수 가능.
- 출처=MONO-470 감사 Tier-3 findings 1.1/1.2. F1/F5 skip 근거·hardstop 훅 결합=MONO-472 조사.
- 분석·구현=Opus 4.8.
