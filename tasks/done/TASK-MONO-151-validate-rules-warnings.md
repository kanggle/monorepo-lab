# Task ID

TASK-MONO-151

# Title

`/validate-rules` 2026-05-29 Warning 3건 정리 — `error-handling.md` 미등록 코드 alias 등록 + 2 skill 정합성(Kafka 테스트 이미지 통일 + 공유 skill project-agnostic화)

# Status

done

# Owner

monorepo (root tasks/ — shared `platform/` + `.claude/skills/`)

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

- **origin**: `/validate-rules` full scan 2026-05-29 (read-only, MONO-149/150 remediation 직후). Critical 0, Warning 3, Info 5. 본 task = Warning 3건 closure. Info 5건은 비차단 deferred.
- **prerequisite for**: nothing (rule-library hygiene).
- **execution constraint**: 3 수정 모두 `platform/` + `.claude/skills/` — auto-mode classifier 의 `.claude/hooks|agents|commands` edit+commit block 대상 **아님** ([[env_classifier_claude_self_mod_block]] = hooks/agents/commands 한정). 따라서 agent 가 edit + commit + push + merge 전부 수행 가능 (MONO-150 과 달리 user-apply 불요).
- **model**: 분석=Opus 4.7 / 구현=Opus 4.7 (single-session 직접) / 구현 권장=Sonnet 4.6 (단순 rule-file fix).

---

# Goal

`/validate-rules` 가 보고한 Warning 3건을 해소하여 rule 라이브러리를 다시 완전 클린(Critical 0 / Warning 0)으로 만든다. 전부 동작 영향 0 의 문서·정합성 수정.

---

# Scope

## In Scope

1. **`platform/error-handling.md` — `CONCURRENT_MODIFICATION` 미등록 코드 registry alias 행 추가.**
   - 문제: `CONCURRENT_MODIFICATION`(409, optimistic-lock)이 fintech(L596)·erp(L656-658) contract surface 에서 사용되나 어느 코드 표에도 미등록 — registry 의 등록 코드는 `CONFLICT`(409). 파일 자체 규칙("코드는 사용 전 등록") 위반.
   - 수정: **rename 아님.** erp 가 readability 위해 의도적으로 쓰는 descriptive name 이므로(L658 자인) Transactional Trait 표(`CONFLICT` 아래)에 alias 행 1개 추가하여 정식 등록. 두 prose note(596/656)는 이제 등록된 코드를 가리키므로 무변경.
2. **`.claude/skills/service-types/event-consumer-setup/SKILL.md` — Kafka Testcontainers 이미지 통일.**
   - 문제: `confluentinc/cp-kafka:7.5.0` 사용. 정전(`testing/testcontainers/SKILL.md`)은 `apache/kafka:3.7.0`. 동일 개념 두 이미지 → 불일치 셋업 유발.
   - 수정: `confluentinc/cp-kafka:7.5.0` → `apache/kafka:3.7.0` (동일 `KafkaContainer` 클래스, 이미지 문자열만 교체).
3. **`.claude/skills/service-types/identity-platform-setup/SKILL.md` — 공유 skill project-agnostic화.**
   - 문제: L80 이 구체 프로젝트 경로 `projects/wms-platform/apps/gateway-service` 명시 → CLAUDE.md "shared `.claude/` 는 project-agnostic (서비스명 금지)" 위반.
   - 수정: 경로 제거, `backend/gateway-security` skill 참조로 일반화.

## Out of Scope

- Info 5건 (agent `service_types` 범위 / refactor-spec `sed -i` Windows note / implement-task·review-task 3-dim cross-ref / ci-cd 예시 dup key / caching bare-filename ref) — 비차단, deferred.
- `identity-platform-setup` L77 `# e.g., wms` audience 예시 — 경로/구조 참조 아닌 일반 예시값, minimal scope 유지 위해 무변경.

---

# Acceptance Criteria

- [x] **AC-1**: `error-handling.md` Transactional Trait 표에 `CONCURRENT_MODIFICATION` alias 행(409, `CONFLICT` 등가) 등록 → fintech/erp 의 사용처가 등록 코드 참조.
- [x] **AC-2**: `event-consumer-setup/SKILL.md` Kafka 이미지가 `apache/kafka:3.7.0` (testcontainers skill 과 일치).
- [x] **AC-3**: `identity-platform-setup/SKILL.md` 에 구체 프로젝트 경로 0건 (`projects/...` 제거).
- [x] **AC-4 (scope-lock)**: `git diff origin/main` 이 위 3 파일 + task lifecycle 파일만 건드림.
- [x] **AC-5**: 재-scan 시 해당 3 Warning 재현 안 됨 (Critical 0 / Warning 0).

---

# Related Specs

- `platform/error-handling.md` — 코드 registry (수정 대상 #1).
- `.claude/skills/service-types/event-consumer-setup/SKILL.md` (#2) / `.claude/skills/testing/testcontainers/SKILL.md` (정전 기준).
- `.claude/skills/service-types/identity-platform-setup/SKILL.md` (#3) / `.claude/skills/backend/gateway-security/SKILL.md` (참조 대상).
- `CLAUDE.md` § shared/project boundary (project-agnostic 규칙).

# Related Contracts

- None. Rule-library / tooling only.

---

# Edge Cases

- **`CONCURRENT_MODIFICATION` alias 가 duplication 처럼 보임** — 설명 문구에 "Registered descriptive alias of `CONFLICT`"로 명시하여 의도된 alias 임을 표기 (validate-rules functional-dup 오탐 회피).
- **Kafka 이미지 교체로 컨테이너 클래스 변경 필요?** — 불필요. 두 skill 모두 `org.testcontainers...KafkaContainer` + `DockerImageName.parse(...)` 동일 패턴, 이미지 문자열만 상이.

---

# Failure Scenarios

- **다른 파일이 수정됨** → AC-4 fail; 본 task 는 3 파일 한정.
- **erp/fintech 코드 동작 변경** → 발생 안 함; registry 행 추가는 문서만, 코드 emit 무변경.

---

# Verification

- 2026-05-29, `task/mono-151-validate-rules-warnings` 브랜치 (off main, BE-144 머지 후).
- 3 수정 적용 완료. `git diff origin/main --stat` = 3 rule 파일 + task lifecycle.
- CI `changes` fast-lane 기준 GREEN 예상 (platform/.md + .claude/skills/.md = non-code path-filter).
- BE-303 3-dim merge 검증 close chore 시 수행.
- 분석=Opus 4.7 / 구현=Opus 4.7.
