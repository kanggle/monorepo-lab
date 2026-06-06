---
id: TASK-FE-009
title: "TASK-FE-008 수정 — URL.revokeObjectURL 즉시 해제 버그 및 테스트 커버리지 보완"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-008에서 구현된 데이터 내보내기 기능의 두 가지 결함을 수정한다.

1. `URL.revokeObjectURL(url)`을 `a.click()` 직후 동기적으로 호출하여 Firefox/Safari에서 다운로드가 실패할 수 있는 버그를 수정한다.
2. 성공 toast, 실패 toast, isPending 버튼 비활성화, 파일명 형식 등 검증되지 않은 인수 조건에 대한 단위 테스트를 추가한다.

## Background

- TASK-FE-008 리뷰 결과 fix_needed 판정.
- `URL.revokeObjectURL(url)`을 `a.click()` 직후 즉시 호출하면 브라우저가 다운로드를 시작하기 전에 Object URL이 해제되어 Firefox/Safari에서 다운로드가 실패할 수 있다. 표준 패턴은 `setTimeout`으로 비동기 해제하거나 앵커를 DOM에 추가 후 클릭·제거하는 방식이다.
- 기존 테스트(`ExportButton.test.tsx`)는 역할 기반 버튼 표시/숨김만 검증하며, 다운로드 동작·toast·isPending 상태 등 인수 조건 다수를 미검증 상태로 남겼다.

## Scope

### 1. `AccountDetail.tsx` — URL 해제 버그 수정

`handleExport` 함수에서 `URL.revokeObjectURL(url)` 호출을 비동기로 변경한다.

현재 (버그):
```ts
a.click();
URL.revokeObjectURL(url);
```

수정 후:
```ts
document.body.appendChild(a);
a.click();
document.body.removeChild(a);
setTimeout(() => URL.revokeObjectURL(url), 100);
```

또는 동등한 안전한 패턴 중 하나를 적용한다. 앵커를 DOM에 추가하지 않는 경우에도 `URL.revokeObjectURL`은 반드시 `setTimeout` 내에서 호출해야 한다.

### 2. `ExportButton.test.tsx` — 누락된 테스트 케이스 추가

다음 시나리오를 커버하는 테스트를 추가한다:

- **성공 경로**: `mutateAsync`가 성공적인 응답을 반환할 때 `URL.createObjectURL`, `<a>.click`, `URL.revokeObjectURL`이 호출되고 `toast.show('데이터를 내보냈습니다.', 'success')`가 호출된다.
- **파일명 형식**: 생성되는 앵커의 `download` 속성이 `export-{accountId}-{YYYYMMDD}.json` 패턴을 따른다.
- **실패 경로**: `mutateAsync`가 `ApiError`를 throw할 때 `toast.show`가 오류 메시지와 함께 호출된다.
- **isPending 상태**: `isPending: true`일 때 버튼이 `disabled`이고 '처리 중...' 텍스트를 표시한다.

## Acceptance Criteria

- [ ] Firefox/Safari에서 데이터 내보내기 버튼 클릭 시 다운로드가 정상적으로 완료된다 (`URL.revokeObjectURL`이 비동기 호출됨)
- [ ] 성공 시 `toast.show('데이터를 내보냈습니다.', 'success')` 호출을 검증하는 테스트가 존재한다
- [ ] 실패 시 `toast.show(errorMessage, 'error')` 호출을 검증하는 테스트가 존재한다
- [ ] `isPending: true`일 때 버튼이 `disabled`이고 '처리 중...' 텍스트임을 검증하는 테스트가 존재한다
- [ ] 생성되는 파일명이 `export-{accountId}-{YYYYMMDD}.json` 형식임을 검증하는 테스트가 존재한다
- [ ] 모든 기존 테스트가 계속 통과한다
- [ ] `npx vitest run` 전체 패스

## Related Specs

- `specs/features/admin-operations.md` — 데이터 내보내기 동작 정의

## Related Contracts

- `specs/contracts/http/admin-api.md` — `GET /api/admin/accounts/{accountId}/export`

## Edge Cases

- `setTimeout` 지연 중 컴포넌트 언마운트 시에도 URL 해제가 보장되어야 한다 (메모리 누수 방지). `useEffect` cleanup 또는 `AbortController`로 처리하거나, 테스트에서 이를 검증한다.

## Failure Scenarios

- 수정 이후에도 기존 역할 기반 표시/숨김 테스트가 실패하면 구현 오류다 — 기존 동작은 회귀 없어야 한다.
