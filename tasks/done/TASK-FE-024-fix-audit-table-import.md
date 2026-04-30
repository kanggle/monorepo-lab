---
id: TASK-FE-024
title: "fix: AuditTable.tsx AuditFilters 타입 import 누락 수정"
status: ready
area: frontend
service: admin-web
---

## Goal

`apps/admin-web/src/features/audit/components/AuditTable.tsx` line 11에서 `AuditFilters` 타입을 사용하지만 import가 없어 TypeScript 빌드 오류가 발생한다. import 한 줄 추가로 수정한다.

## Scope

### In

- `apps/admin-web/src/features/audit/components/AuditTable.tsx`
  - line 11 상단에 `import type { AuditFilters } from '../hooks/useAudit';` 추가

### Out

- AuditTable.tsx 로직 변경
- useAudit.ts 변경
- 기타 파일 변경

## Acceptance Criteria

- [ ] `AuditTable.tsx`에 `import type { AuditFilters } from '../hooks/useAudit';` 추가됨
- [ ] `pnpm --filter admin-web build` TypeScript 컴파일 오류 없음 (AuditFilters 관련)
- [ ] `pnpm --filter admin-web test` 전체 통과 (회귀 없음)

## Related Specs

- specs/features/audit-trail.md — 감사 로그 기능 정의

## Related Contracts

- 없음 (타입 import만 수정)

## Edge Cases

- 빌드에 다른 오류가 있다면 이 태스크 범위 밖 — 해당 오류는 별도 태스크로 처리

## Failure Scenarios

- `AuditFilters` 타입이 `useAudit.ts`에서 export되지 않은 경우: 실제로는 export 되어 있음 (`AuditTabs.tsx`가 동일한 import로 정상 동작 중)
