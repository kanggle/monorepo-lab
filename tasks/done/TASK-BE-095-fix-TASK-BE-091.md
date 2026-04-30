---
id: TASK-BE-095
title: "fix(spec): TASK-BE-091 review 지적 사항 수정 — retention.md profile_image_url 마스킹 필드 추가 + gracePeriodEndsAt 필드 의미 명확화"
status: ready
area: backend
service: account-service
---

## Goal

TASK-BE-091 리뷰에서 발견된 두 가지 문제를 `specs/services/account-service/retention.md`에서 수정한다:

1. **[Critical]** AC에 명시된 `profile_image_url` 필드가 retention.md 섹션 2.5 마스킹 대상 테이블에 포함되지 않고 "향후 도입 시" 주석으로만 처리됨. TASK-BE-091 AC 요건(`profile_image_url → anonymized 형태`)을 충족하도록 마스킹 대상 필드로 명시해야 한다.

2. **[Warning]** retention.md 섹션 2.7에서 재발행 `account.deleted (anonymized=true)` 이벤트의 `gracePeriodEndsAt` 필드 값을 "익명화 시각"으로 채운다고 명시하고 있으나, `account-events.md` 계약의 `gracePeriodEndsAt` 필드 의미(유예 종료 시각)와 충돌한다. 익명화 완료 시각은 별도 필드(`anonymizedAt`)로 표현하거나, 재발행 이벤트에서 해당 필드 의미를 계약과 일치시키도록 수정해야 한다.

## Scope

### In

- `specs/services/account-service/retention.md` 수정
  - 섹션 2.5: `profiles.profile_image_url` 행을 마스킹 대상 테이블에 추가 (`NULL` 처리, `confidential` 등급)
  - 섹션 2.7: 재발행 이벤트 payload 설명에서 `gracePeriodEndsAt` 필드 의미를 account-events.md 계약과 일치시키도록 수정 (원래 유예 종료 시각으로 채우고, 익명화 완료 시각이 필요하다면 `anonymizedAt` 필드 추가 또는 계약과 협의)

### Out

- `specs/contracts/events/account-events.md` 변경 (필드 추가가 필요할 경우 별도 태스크)
- 배치 스케줄러 코드 구현 (TASK-BE-092, TASK-BE-093)
- `data-model.md` 변경 (profile_image_url이 data-model.md에 이미 존재할 경우 retention.md만 수정; 존재하지 않으면 In Scope로 추가 필요 — 구현자가 확인)

## Acceptance Criteria

- [ ] `retention.md` 섹션 2.5 마스킹 대상 테이블에 `profiles.profile_image_url` 행이 추가되어 있고, 처리 후 값(`NULL`)과 분류 등급(`confidential`)이 명시됨
- [ ] `retention.md` 섹션 2.7 재발행 이벤트 설명이 `account-events.md`의 `gracePeriodEndsAt` 필드 의미(유예 종료 시각)와 일치함. "익명화 시각" 혼동 문구가 제거되고, 필요 시 `anonymizedAt` 필드가 `account-events.md`에 추가되거나 기존 필드 의미가 명확화됨
- [ ] 섹션 2.5의 마스킹 완료 처리 절차(섹션 2.6)에도 `profile_image_url → NULL` 단계가 반영됨

## Related Specs

- `specs/services/account-service/retention.md` — 수정 대상 파일
- `specs/features/account-lifecycle.md` — 상태 전이 규칙 (참조만)
- `rules/traits/regulated.md` — R7 PII 익명화 요구

## Related Skills

- `.claude/skills/review-checklist/SKILL.md`

## Related Contracts

- `specs/contracts/events/account-events.md` — `account.deleted` 이벤트 스키마 (`gracePeriodEndsAt` 필드 의미 확인)

## Target Service

- `account-service`

## Architecture

Follow:

- `specs/services/account-service/architecture.md`

## Implementation Notes

- `profile_image_url` 컬럼이 `profiles` 테이블에 실제로 존재하는지 `specs/services/account-service/data-model.md`를 먼저 확인할 것. 존재하지 않는 경우 retention.md에 "향후 도입 시 마스킹 대상" 주석과 함께 명시하되 TASK-BE-091 AC 요건에 맞게 현재 스펙에 포함시킬 것.
- `gracePeriodEndsAt` 수정 방향 결정 시 account-events.md 계약 변경이 필요한지 판단 필요. 계약 변경이 필요하면 Contract Rule에 따라 account-events.md를 retention.md와 동일 PR에서 갱신.

## Edge Cases

- `profiles.profile_image_url`이 `data-model.md`에 없을 경우: retention.md에 향후 도입 시 마스킹 대상으로 명시하되, TASK-BE-091 AC 준수를 위해 필드명을 마스킹 대상 표에 포함
- `gracePeriodEndsAt` 수정으로 account-events.md 변경이 불필요한 경우 (기존 필드 의미 유지 + 재발행 이벤트에서 원래 유예 종료 시각으로 채우는 것으로 충분): 계약 변경 없이 retention.md만 수정

## Failure Scenarios

- retention.md 수정 후 TASK-BE-092/TASK-BE-093 구현 시 `profile_image_url` 마스킹 단계가 누락되어 Hard Stop 발생 — 수정 후 TASK-BE-092/093 구현 태스크에 전파 확인
- account-events.md 계약 불일치가 해소되지 않으면 다운스트림 consumer(auth-service, security-service)가 `anonymized=true` 이벤트의 `gracePeriodEndsAt` 필드를 잘못 해석하는 런타임 버그 발생 가능
