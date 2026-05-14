# Task ID

TASK-BE-284

# Title

admin-service data-model.md PiiMaskingUtils reference rename → PiiMaskingService (refactor-spec Tier 2 closure)

# Status

review

# Owner

global-account-platform

# Task Tags

- gap
- admin-service
- spec
- dead-reference
- judgment
- cleanup

---

# Goal

`/refactor-spec all --dry-run` Tier 2 finding **BE-283 deferred 1건 closure** — admin-service `data-model.md:170` 의 "참조 구현" sample link 가 production code 와 mismatch.

**Finding** (BE-283 § Out of Scope 에서 deferred):
- Spec line (L170): `참조 구현: [apps/security-service/.../domain/util/PiiMaskingUtils.java](../../../apps/security-service/src/main/java/com/example/security/domain/util/PiiMaskingUtils.java)`
- Production 실제: `apps/security-service/src/main/java/com/example/security/application/pii/PiiMaskingService.java` (rename: package `domain/util/` → `application/pii/` + class `PiiMaskingUtils` → `PiiMaskingService`)

**Judgment**: **Option A — rename ref to actual class** (3 option 중 채택):
- **A (chosen)**: rename to `application/pii/PiiMaskingService.java` — author 의도 (참조 구현 가리키기) 보존 + production code 와 sync. 자연.
- **B (rejected)**: drop sample link (참조 구현 sentence 만 텍스트로 남기기) — author 의도 약화, navigation 가치 잃음.
- **C (rejected)**: drop entire "참조 구현" sentence — 의미적 손실 (admin-service utility 도입 가이드 reference 가치 제거).

# Scope

## In Scope

- `projects/global-account-platform/specs/services/admin-service/data-model.md` L170 — 1-line Edit:
  - Link text + target 둘 다 rename: `apps/security-service/.../domain/util/PiiMaskingUtils.java` → `apps/security-service/.../application/pii/PiiMaskingService.java`

## Out of Scope

- Production code 의 class 명 변경 (production 이 source-of-truth, spec 이 따라감).
- 다른 PII masking 관련 spec 의 align — 검증 필요 시 별 task.

# Acceptance Criteria

- [ ] L170 link target file 실재 (`apps/security-service/src/main/java/com/example/security/application/pii/PiiMaskingService.java` exists).
- [ ] `bash /tmp/check_gap_links.sh` 결과 dead-ref 0 (BE-283 시점 잔존 1건 = PiiMaskingUtils, 본 task 후 0).
- [ ] Production code / spec contract / requirement 0 변경 (참조 구현 reference 만 rename, 본문 의미 동일).
- [ ] 같은 sentence 의 "TASK-BE-028" reference + admin-service `AdminPiiMaskingUtils` 예정 명시 보존.

# Related Specs

- `projects/global-account-platform/specs/services/admin-service/data-model.md` (TASK-BE-XXX, admin service authoring)
- TASK-BE-283 precedent (sibling — § Out of Scope 에서 Tier 2 defer 결정 + BE-284 후보 명시)
- TASK-BE-165 / SCM-BE-013 (refactor-spec Tier 1/3 시리즈, single-PR closure 답습)

# Related Contracts

해당 없음 (sample link rename only).

# Target Service

해당 없음 — admin-service spec 내 sample link 1건 rename.

# Edge Cases

- A: production 의 `PiiMaskingService` 가 다시 rename 될 가능성 — class 명 변경 시 spec 도 같은 PR 에 sync (CLAUDE.md § Cross-Project Changes atomic rule 정신 답습, 단 한 spec ref 라 작음).
- B: "참조 구현" 이 future 의 deprecation candidate 일 가능성 — admin-service `AdminPiiMaskingUtils` 도입 시 본 reference 자체 drop 검토 (별 task 후보, 본 task 범위 외).

# Failure Scenarios

- A: production 의 PiiMaskingService 가 또 다른 package 로 이동 → `find` 재실행 + 정확한 현재 path 인용.
- B: 같은 sample link 다른 위치 (다른 spec) 에도 존재 → grep 으로 single hit 검증.

# Validation Plan

1. Edit 후 `[ -e "projects/global-account-platform/specs/services/admin-service/../../../apps/security-service/src/main/java/com/example/security/application/pii/PiiMaskingService.java" ]` exit 0.
2. `bash /tmp/check_gap_links.sh` 결과 broken count = 0.
3. `git diff --stat` = 1 file / 1 line edit.

# Implementation Notes

- 2 commit / 1 branch: (1) ready/ task author, (2) Edit + lifecycle move ready/ → review/.
- branch name `task/be-284-piimaskingutils-ref-rename` — CLAUDE.md § Cross-Project Changes "Branch name constraint" 준수.
- TASK-BE-165 / TASK-BE-283 / TASK-SCM-BE-013 precedent 답습 (refactor-spec cycle 4번째 single-PR closure, judgment-required Tier 2).
- **refactor-spec cycle 종결**: 5 (BE-165 WMS) → 47 (BE-283 GAP Tier 3) → 1 (SCM-BE-013) → 1 (본 task, GAP Tier 2 judgment). Portfolio dead-ref 잔존 = 0 예상.

# Outcome

(완료 후 갱신)
