---
id: TASK-FE-010
title: "TASK-FE-009 수정 — 컴포넌트 언마운트 시 Object URL 누수 방지"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-009 리뷰에서 발견된 Edge Case를 수정한다.

`AccountDetail.tsx`의 `handleExport`가 `setTimeout(() => URL.revokeObjectURL(url), 100)` 패턴을 사용하는데, setTimeout 지연(100ms) 중 컴포넌트가 언마운트되면 URL이 해제되지 않아 메모리 누수가 발생할 수 있다. TASK-FE-009 Edge Cases 절은 이를 `useEffect` cleanup 또는 `AbortController`로 처리하거나 테스트로 검증하도록 명시하였으나, 구현과 테스트 모두 해당 시나리오를 다루지 않았다.

## Background

- TASK-FE-009 리뷰 결과 fix_needed 판정.
- `AccountDetail.tsx` line 46: `setTimeout(() => URL.revokeObjectURL(url), 100)` — 타이머 실행 전 언마운트 시 누수.
- TASK-FE-009 Edge Cases: "useEffect cleanup 또는 AbortController로 처리하거나, 테스트에서 이를 검증한다."

## Scope

### 1. `AccountDetail.tsx` — 언마운트 시 타이머 정리

`handleExport` 내부에서 반환된 `timeoutId`를 컴포넌트 언마운트 시 `clearTimeout`으로 정리한다.

권장 패턴 (useRef + useEffect cleanup):

```ts
const revokeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

useEffect(() => {
  return () => {
    if (revokeTimerRef.current !== null) {
      clearTimeout(revokeTimerRef.current);
    }
  };
}, []);

// handleExport 내부:
revokeTimerRef.current = setTimeout(() => URL.revokeObjectURL(url), 100);
```

또는 동등한 cleanup 보장 패턴을 적용한다.

### 2. `ExportButton.test.tsx` — 언마운트 시나리오 테스트 추가

다음 시나리오를 검증하는 테스트를 추가한다:

- **언마운트 후 URL 미해제 검증**: 내보내기 버튼 클릭 직후(setTimeout 만료 전) 컴포넌트를 언마운트하면 `URL.revokeObjectURL`이 호출되지 않아야 한다 (또는 clearTimeout으로 정리되어 타이머가 실행되지 않아야 한다).

## Acceptance Criteria

- [ ] 컴포넌트 언마운트 시 진행 중인 `revokeObjectURL` 타이머가 정리된다 (`clearTimeout` 또는 동등한 메커니즘)
- [ ] 언마운트 후 `URL.revokeObjectURL`이 호출되지 않음을 검증하는 테스트가 존재한다
- [ ] 모든 기존 테스트(TASK-FE-009에서 추가된 테스트 포함)가 계속 통과한다
- [ ] `npx vitest run` 전체 패스

## Related Specs

- `specs/features/admin-operations.md` — 데이터 내보내기 동작 정의

## Related Contracts

- `specs/contracts/http/admin-api.md` — `GET /api/admin/accounts/{accountId}/export`

## Edge Cases

- 언마운트 후 타이머가 정리되더라도 Object URL은 GC 시 자동으로 수거된다 (브라우저 탭 닫힘 등). cleanup은 명시적 정리를 위한 best-effort 보호다.
- 여러 번 연속 클릭 시 이전 타이머 ref가 덮어씌워지는 경우: 각 클릭마다 새 blob URL이 생성되므로, 이전 타이머가 clearTimeout 없이 실행되면 이전 URL도 해제된다 — 정상 동작이다.

## Failure Scenarios

- 수정 이후에도 기존 성공/실패/isPending/파일명/역할 기반 테스트가 실패하면 구현 오류다 — 기존 동작은 회귀 없어야 한다.
