---
id: TASK-FE-017
title: "Fix: TASK-FE-015 audit-log E2E — OPERATOR_CREATE 빈 결과 환경 처리 누락"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-015 리뷰에서 발견된 문제를 수정한다.

`audit-log.spec.ts`의 세 번째 테스트(`operator management actions appear in audit log`)가 `OPERATOR_CREATE` 감사 로그가 존재하지 않는 환경에서는 항상 실패한다. TASK-FE-015의 Edge Case 요구사항("결과가 없을 때의 처리를 테스트에서 명시")을 이행하지 않은 것이다.

## Background

- TASK-FE-015 리뷰 지적: `audit-log.spec.ts` 세 번째 테스트가 `page.getByText('OPERATOR_CREATE').first().toBeVisible()`로 단순 가시성만 검증한다.
- 스택이 기동 중이지만 `OPERATOR_CREATE` 감사 레코드가 없는 환경(예: 최초 배포 직후, 데이터 없는 QA)에서는 해당 assertion이 실패하여 테스트가 flaky해진다.
- 원 태스크 Edge Case: "`OPERATOR_CREATE` 감사 로그가 없는 환경: 결과가 없을 때의 처리를 테스트에서 명시"

## Scope

### In

1. **`tests/e2e/audit-log.spec.ts`** (수정)
   - 세 번째 테스트: `OPERATOR_CREATE` 필터 적용 후 결과가 있을 수도 없을 수도 있다는 것을 명시적으로 처리
   - 권장 패턴:
     ```ts
     const rows = page.getByText('OPERATOR_CREATE');
     const count = await rows.count();
     if (count > 0) {
       await expect(rows.first()).toBeVisible();
     } else {
       // 빈 결과가 명시적으로 허용됨을 주석으로 기록
       // 예: await expect(page.getByText(/결과가 없습니다|No results/)).toBeVisible();
       // 또는 단순히 table 가시성 확인 (이미 첫 번째 테스트에서 검증됨)
     }
     ```
   - 또는, 테스트 시작 시 `operator-management.spec.ts`의 operator 생성 플로우를 선행 실행하여 레코드를 보장하는 방식도 허용

### Out

- 다른 spec 파일 수정 불필요 (`operator-management.spec.ts`, `login-lock.spec.ts`는 TASK-FE-015에서 정상 처리됨)
- 실제 백엔드 API mocking 도입
- 신규 시나리오 추가

## Acceptance Criteria

- [ ] `audit-log.spec.ts` 세 번째 테스트: `OPERATOR_CREATE` 레코드가 없는 환경에서도 실패하지 않음
- [ ] `OPERATOR_CREATE` 레코드가 있는 환경에서는 해당 텍스트를 검증함 (기존 의도 유지)
- [ ] 빈 결과 처리 방식이 코드 주석으로 명시됨
- [ ] `E2E_ENABLED=1` 없으면 모든 스펙 skip 유지
- [ ] 기존 테스트 구조(test.describe, test.skip 패턴) 유지

## Related Specs

- `specs/features/audit-trail.md`

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Edge Cases

- `E2E_ENABLED` 미설정: 모든 테스트 skip 유지
- `OPERATOR_CREATE` 감사 로그가 없는 환경: 테스트가 skip이 아닌 "빈 결과 허용"으로 통과해야 함
- `OPERATOR_CREATE` 감사 로그가 있는 환경: 실제 결과 텍스트 검증이 수행되어야 함

## Failure Scenarios

- 백엔드 미기동 시: timeout → skip 패턴 유지 (기존 동작 그대로)

## Test Requirements

- 수정 파일 1개 (`audit-log.spec.ts`)
- E2E 테스트이므로 별도 unit/integration 테스트 불필요
