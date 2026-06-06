# TASK-FE-026: refactor — DashboardTabs 상수/타입을 별도 모듈로 분리 (Server/Client boundary)

## Goal

`apps/admin-web/src/app/(console)/dashboards/_components/DashboardTabs.tsx`가 `'use client'` 컴포넌트인데, 서버 컴포넌트(`page.tsx`)가 같은 파일에서 export하는 `DASHBOARD_TABS` 상수와 `DashboardTabId` 타입을 import한다. 이는 Next.js의 server/client boundary anti-pattern으로, 불필요한 클라이언트 코드가 서버 번들로 따라 들어가거나 빌드 경고가 발생할 수 있다.

상수와 타입만 담는 별도 모듈 `tabs.ts`로 분리해 양쪽이 깨끗하게 import하도록 한다.

## Scope

**In:**
- `_components/tabs.ts` 신규 — `DASHBOARD_TABS` 상수 + `DashboardTabId` 타입만
- `_components/DashboardTabs.tsx` — 상수/타입 export 제거, `tabs.ts`에서 import
- `page.tsx` — import 경로 수정 (`./_components/tabs`에서 가져옴)

**Out:**
- 비즈니스 로직 변경 없음 (탭 목록, 라벨, 동작 동일)
- 테스트 변경 없음

## Acceptance Criteria

- [x] `tabs.ts`가 `DASHBOARD_TABS` + `DashboardTabId`를 export
- [x] `DashboardTabs.tsx`('use client')는 더 이상 상수를 export하지 않음
- [x] `page.tsx`(server component)는 `tabs.ts`에서 직접 import
- [x] `npx tsc --noEmit` 통과
- [x] dashboard 페이지 런타임 동작 동일 (변동 없음)

## Related Specs

- 없음 (FE 내부 모듈 정리)

## Related Contracts

- 없음

## Edge Cases

- 다른 파일이 `DashboardTabs.tsx`에서 상수/타입을 import하는 경우: grep 결과 없음 — 영향 없음

## Failure Scenarios

- 없음 (compile-time 검증으로 확인 가능)

## Notes

- 이전 세션에서 working tree에 미커밋된 채 남아있던 변경을 정식 task로 등록하여 마무리.
- 의도/구현 모두 표준적이라 별도 fix 없이 그대로 채택.
