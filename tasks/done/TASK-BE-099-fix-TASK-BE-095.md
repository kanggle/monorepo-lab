---
id: TASK-BE-099
title: "fix(spec): TASK-BE-095 review 지적 사항 수정 — retention.md 섹션 3 보존 기간 테이블에 profile_image_url 항목 누락"
status: ready
area: backend
service: account-service
---

## Goal

TASK-BE-095 리뷰에서 발견된 문제를 `specs/services/account-service/retention.md`에서 수정한다:

**[Warning]** `retention.md` 섹션 2.5 마스킹 대상 테이블에 `profiles.profile_image_url`이 추가되었으나, 섹션 3 보존 기간 테이블(Retention Schedule)에는 해당 필드가 누락되어 있다. `rules/traits/regulated.md` R6에 따라 모든 PII·민감 데이터는 보존 기간이 문서에 명시되어야 한다. `profile_image_url`이 `confidential` 등급으로 섹션 2.5에 마스킹 대상으로 선언된 이상, 섹션 3에도 보존 기간이 명시되어야 한다.

## Scope

### In

- `specs/services/account-service/retention.md` 수정
  - 섹션 3 보존 기간 테이블(Retention Schedule)에 `profiles.profile_image_url` 행 추가
    - 분류 등급: `confidential`
    - 보존 기간: 계정 활성 기간 + 유예 30일
    - 종료 시 처리: `NULL` (익명화)
    - 규제 근거: GDPR Art.17, PIPA §21
    - 컬럼 미도입 시 적용 시점 주석 포함 (섹션 2.5의 "향후 도입 시" 정책과 일관성 유지)

### Out

- `specs/services/account-service/data-model.md` 변경 (profile_image_url 컬럼 미도입이므로 별도 태스크)
- 코드 구현 변경 (TASK-BE-092, TASK-BE-093)
- `specs/contracts/events/account-events.md` 변경

## Acceptance Criteria

- [ ] `retention.md` 섹션 3 보존 기간 테이블에 `profiles.profile_image_url` 행이 추가되어 있고, 분류 등급(`confidential`), 보존 기간(계정 활성 기간 + 유예 30일), 종료 시 처리(`NULL`), 규제 근거(GDPR Art.17, PIPA §21)가 명시됨
- [ ] 섹션 2.5의 "향후 도입 시" 정책 노트와 일관성이 유지됨 (컬럼 미도입 시 적용 시점이 섹션 3에도 명확히 표시되거나 주석으로 안내됨)
- [ ] `rules/traits/regulated.md` R6 요건 충족 — 마스킹 대상으로 선언된 모든 confidential 필드가 섹션 3에 보존 기간이 명시됨

## Related Specs

- `specs/services/account-service/retention.md` — 수정 대상 파일
- `rules/traits/regulated.md` — R6 PII 보존 기간 명시 요구

## Related Skills

- `.claude/skills/review-checklist/SKILL.md`

## Related Contracts

- 없음 (계약 변경 불필요)

## Target Service

- `account-service`

## Architecture

Follow:

- `specs/services/account-service/architecture.md`

## Implementation Notes

- 섹션 3 테이블에 행을 추가할 때, `profiles.preferences` 행(internal) 바로 위에 `profiles.profile_image_url` 행을 삽입하여 `profiles` 테이블 관련 행들이 연속되도록 정렬한다.
- 섹션 2.5의 기존 노트(`profiles.profile_image_url 컬럼 도입 노트`)와 일관성을 유지하기 위해, 섹션 3 행에도 "컬럼 도입 시 활성화" 또는 동일한 정책 참조를 추가할 수 있다.

## Edge Cases

- 섹션 3에 행을 추가할 때 기존 행의 순서 또는 정렬 기준이 없는 경우: `profiles` 테이블 관련 기존 행들과 인접하게 삽입
- `profile_image_url` 컬럼이 아직 data-model.md에 없으므로 보존 기간 행에 "(향후 컬럼 도입 시 적용)" 주석을 명시

## Failure Scenarios

- 섹션 3에 보존 기간 행이 추가되지 않으면 `rules/traits/regulated.md` R6 위반이 지속되어 규제 감사 시 누락으로 지적될 수 있음
- 섹션 2.5와 섹션 3이 불일치한 상태로 TASK-BE-092/TASK-BE-093 구현 시 혼동 유발 가능
