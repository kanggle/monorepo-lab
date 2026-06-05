---
id: TASK-FE-018
title: "Fix: audit-log E2E — ACCOUNT_LOCK 빈 결과 환경 처리 누락"
status: ready
area: frontend
service: admin-web
---

## Goal

`audit-log.spec.ts`의 두 번째 테스트(`filtering by ACCOUNT_LOCK action code shows matching rows`)가 `ACCOUNT_LOCK` 감사 레코드가 존재하지 않는 환경(예: 최초 배포 직후, 데이터 없는 QA)에서 항상 실패하는 flakiness를 해결한다.

TASK-FE-017에서 `OPERATOR_CREATE` 세 번째 테스트에 적용한 조건부 검증 패턴을 `ACCOUNT_LOCK` 두 번째 테스트에도 동일하게 적용한다.

## Background

- `audit-log.spec.ts` 두 번째 테스트는 현재 `page.getByText('ACCOUNT_LOCK').first()` 를 무조건 `toBeVisible()` 로 검증한다.
- `ACCOUNT_LOCK` 감사 레코드가 없는 환경(최초 배포, 데이터 없는 QA, CI)에서는 이 assertion이 실패한다.
- TASK-FE-017 리뷰에서 이 패턴이 지적됐으나, 당시 TASK-FE-017 범위(세 번째 테스트만)를 벗어나 별도 태스크로 분리됐다.
- TASK-FE-017이 세 번째 테스트에 적용한 패턴: `count === 0` 이면 assertion skip, `count > 0` 이면 검증.

## Scope

### In

1. **`apps/admin-web/tests/e2e/audit-log.spec.ts`** 수정
   - 두 번째 테스트(`filtering by ACCOUNT_LOCK action code shows matching rows`) 를 조건부 검증으로 변경
   - 패턴: `const count = await rows.count(); if (count > 0) { await expect(rows.first()).toBeVisible(); }`
   - 주석: `count === 0` 이 허용되는 이유 명시 (TASK-FE-017과 동일 스타일)

### Out

- 다른 E2E 테스트 파일 변경
- E2E 테스트 데이터 시딩(seed) 구현
- 첫 번째 테스트(`SUPER_ADMIN can load audit log list`) 변경

## Acceptance Criteria

- [ ] 두 번째 테스트가 `ACCOUNT_LOCK` 레코드가 없는 환경에서도 통과(skip이 아닌 pass)한다
- [ ] `ACCOUNT_LOCK` 레코드가 존재하는 경우에는 기존과 동일하게 `toBeVisible()` 검증이 수행된다
- [ ] 조건부 처리에 대한 인라인 주석이 포함된다
- [ ] 나머지 두 테스트(`load list`, `OPERATOR_CREATE`)에 회귀 없음
- [ ] `E2E_ENABLED=1` 없이 skip 동작 유지

## Related Specs

- `specs/features/operator-management.md` (감사 로그 관련)

## Related Contracts

없음 (E2E 테스트 파일만 변경)

## Edge Cases

- `ACCOUNT_LOCK` 레코드 수가 0인 경우: assertion을 건너뜀 (pass)
- `ACCOUNT_LOCK` 레코드 수가 1 이상인 경우: 첫 번째 항목 가시성 검증

## Failure Scenarios

- 필터 적용 후 테이블 자체가 렌더링되지 않는 경우: 첫 번째 테스트(`load list`)가 먼저 검출

## Test Requirements

- 변경 대상: `apps/admin-web/tests/e2e/audit-log.spec.ts`
- E2E 테스트는 `E2E_ENABLED=1`이 없으면 자동 skip — CI에서는 별도 실행 불필요
- 단위 테스트 추가 불필요 (E2E 전용 변경)
