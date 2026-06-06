---
id: TASK-FE-012
title: "E2E 추가 시나리오: 운영자 관리·감사 로그·Export"
status: ready
area: frontend
service: admin-web
---

## Goal

기존 `login-lock.spec.ts` 에 이어, 최근 구현된 기능(운영자 관리, 데이터 Export, 감사 로그)에 대한 E2E 시나리오를 추가한다. 모두 `E2E_ENABLED=1` 환경에서만 실행된다.

## Background

- Playwright 설정: `apps/admin-web/playwright.config.ts` — `testDir: ./tests/e2e`
- 기존 `login-lock.spec.ts` 패턴을 그대로 따름 (`test.skip(!process.env.E2E_ENABLED)`)
- 현재 e2e 폴더: `tests/e2e/login-lock.spec.ts` 만 존재

## Scope

### In

1. **`tests/e2e/operator-management.spec.ts`** (신규)
   - SUPER_ADMIN 로그인 → `/operators` 접근 → 운영자 목록 확인
   - 운영자 생성 폼 열기 → 필수 필드 입력 → 제출 → 목록에 표시

2. **`tests/e2e/account-export.spec.ts`** (신규)
   - SUPER_ADMIN 로그인 → 계정 검색 → 상세 → Export 버튼 표시 확인
   - Export 버튼 클릭 → 다운로드 트리거(파일 저장 확인)

3. **`tests/e2e/audit-log.spec.ts`** (신규)
   - SUPER_ADMIN 로그인 → `/audit` → 조회 버튼 → 목록 표시 확인
   - action_code 필터로 `ACCOUNT_LOCK` 검색 → 결과 표시 확인

### Out

- 실제 백엔드 API mocking (실 스택 대상)
- 모든 UI 상태 커버리지 (핵심 경로만)

## Acceptance Criteria

- [ ] `operator-management.spec.ts`: SUPER_ADMIN이 `/operators` 에서 운영자 목록을 볼 수 있음 확인
- [ ] `account-export.spec.ts`: Export 버튼이 SUPER_ADMIN에게 표시됨 확인
- [ ] `audit-log.spec.ts`: `/audit` 조회가 정상 동작 확인
- [ ] 모든 스펙 파일은 `E2E_ENABLED=1` 없으면 skip
- [ ] 기존 `login-lock.spec.ts` 회귀 없음

## Related Specs

- `specs/features/operator-management.md`
- `specs/features/admin-operations.md`
- `specs/features/audit-trail.md`

## Related Contracts

- `specs/contracts/http/admin-api.md`

## Edge Cases

- `E2E_ENABLED` 미설정: 모든 테스트 skip
- 환경변수 `E2E_OP_EMAIL`, `E2E_OP_PASSWORD` 미설정 시 기본값 사용

## Failure Scenarios

- 백엔드 미기동 시: 각 테스트 timeout → 기존 패턴대로 skip으로 처리

## Test Requirements

- 신규 파일 3개 작성 (e2e 시나리오 = 테스트 자체)
- 각 파일당 최소 1개 `test.describe` + 1개 `test`
