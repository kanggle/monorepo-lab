---
id: TASK-FE-015
title: "Fix: TASK-FE-012 E2E 시나리오 안정성 개선"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-012에서 발견된 E2E 테스트 품질 이슈를 수정한다.

1. `operator-management.spec.ts` — 운영자 생성 후 목록 재조회 완료를 기다리지 않아 발생하는 flaky assertion 수정
2. `audit-log.spec.ts` — 세 번째 테스트의 filter assertion이 약하여 유효한 검증이 되지 않는 문제 수정
3. `login-lock.spec.ts` — 기존 레퍼런스 파일의 로그인 label selector(`이메일`)가 실제 `LoginForm.tsx`의 label(`운영자 ID`)과 불일치하는 문제 수정 (회귀 방지)

## Background

- TASK-FE-012 리뷰에서 발견: `operator-management.spec.ts` 두 번째 테스트가 운영자 생성 성공 토스트와 동시에 목록 갱신을 단순히 `getByText`로 확인하여, 목록 재페치 완료 전에 assertion이 실행될 수 있다.
- `audit-log.spec.ts` 세 번째 테스트가 `OPERATOR_CREATE`로 필터 후 table 가시성만 확인하여 실제로 필터 결과를 검증하지 않는다.
- `login-lock.spec.ts`가 `이메일` label을 사용하지만 실제 `LoginForm.tsx`는 `운영자 ID` label을 사용한다. 이 패턴 파일을 그대로 두면 E2E 실행 시 항상 실패한다.

## Scope

### In

1. **`tests/e2e/operator-management.spec.ts`** (수정)
   - 생성 버튼 클릭 후 `waitForResponse` 또는 `waitForSelector`를 사용해 API 응답 + 목록 재렌더 완료를 기다린 후 email 노출 assertion
   - 또는 dialog 닫힘을 기다린 후 목록 재조회 완료 확인

2. **`tests/e2e/audit-log.spec.ts`** (수정)
   - 세 번째 테스트(`OPERATOR_CREATE` 필터): 조회 결과에 `OPERATOR_CREATE` 텍스트가 보이는지 확인하는 assertion 추가
   - 결과가 없을 수 있는 환경을 고려하여 `locator.first().toBeVisible()` 또는 테이블에 적어도 한 행이 있음을 확인하는 assertion으로 개선

3. **`tests/e2e/login-lock.spec.ts`** (수정)
   - `getByLabel('이메일')` → `getByLabel('운영자 ID')`로 수정 (실제 LoginForm label과 일치)

### Out

- 실제 백엔드 API mocking 도입
- 신규 시나리오 추가

## Acceptance Criteria

- [ ] `operator-management.spec.ts`: 운영자 생성 후 목록에 해당 이메일이 표시되는 것을 안정적으로 검증 (네트워크 응답 또는 DOM 완료 대기 후)
- [ ] `audit-log.spec.ts`: `OPERATOR_CREATE` 필터 테스트가 실제 결과 텍스트를 검증하거나, 결과 없음도 명시적으로 다루는 assertion 포함
- [ ] `login-lock.spec.ts`: 로그인 label selector가 `운영자 ID`로 수정되어 실제 환경에서 실행 가능
- [ ] `E2E_ENABLED=1` 없으면 모든 스펙 skip 유지
- [ ] 기존 테스트 구조(test.describe, test.skip 패턴) 유지

## Related Specs

- `specs/features/operator-management.md`
- `specs/features/audit-trail.md`

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Edge Cases

- `E2E_ENABLED` 미설정: 모든 테스트 skip 유지
- 운영자 생성 후 목록이 비동기로 갱신되는 경우: 명시적 대기 로직으로 처리
- `OPERATOR_CREATE` 감사 로그가 없는 환경: 결과가 없을 때의 처리를 테스트에서 명시

## Failure Scenarios

- 백엔드 미기동 시: timeout → skip 패턴 유지 (기존 동작 그대로)
- 목록 재페치가 지연되는 경우: `waitForResponse` 또는 Playwright의 auto-wait를 활용하여 flaky 방지

## Test Requirements

- 수정 파일 3개 (`operator-management.spec.ts`, `audit-log.spec.ts`, `login-lock.spec.ts`)
- E2E 테스트이므로 별도 unit/integration 테스트 불필요
