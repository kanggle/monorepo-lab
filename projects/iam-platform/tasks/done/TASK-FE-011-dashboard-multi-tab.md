---
id: TASK-FE-011
title: "대시보드 페이지 멀티탭 UI"
status: ready
area: frontend
service: admin-web
---

## Goal

현재 단일 Grafana iframe으로 구성된 `/dashboards` 페이지를 **3개 탭**으로 개선한다.
탭: 계정 현황 | 보안 이벤트 | 시스템 상태. 각 탭은 별도 Grafana 대시보드 URL을 사용하며, URL 쿼리 파라미터(`?tab=accounts`)로 선택 상태가 유지된다.

## Scope

### In

1. **`env.ts`** — `NEXT_PUBLIC_GRAFANA_BASE_URL` 제거 → 3개 URL 변수로 교체
   - `NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL` (default: `https://grafana.internal/d/accounts`)
   - `NEXT_PUBLIC_GRAFANA_SECURITY_URL` (default: `https://grafana.internal/d/security`)
   - `NEXT_PUBLIC_GRAFANA_SYSTEM_URL` (default: `https://grafana.internal/d/system`)
2. **`apps/admin-web/src/app/(console)/dashboards/_components/DashboardTabs.tsx`** (신규 클라이언트 컴포넌트)
   - `useRouter` + `useSearchParams` 사용
   - 활성 탭: `border-b-2 border-primary font-medium`, 비활성: `text-muted-foreground`
3. **`apps/admin-web/src/app/(console)/dashboards/page.tsx`** 수정
   - `searchParams.tab` 으로 활성 탭 결정, 유효하지 않으면 `accounts` fallback
   - 서버 컴포넌트 유지 (searchParams prop 사용)
   - 활성 탭의 iframe만 렌더링 (탭 전환 시 URL 변경 → 서버 re-render)

### Out

- Grafana URL 자체 변경 (devops 영역)
- iframe 내부 상호작용
- 탭별 접근 권한 분리

## Acceptance Criteria

- [ ] `/dashboards` 페이지에 3개 탭 표시: `계정 현황`, `보안 이벤트`, `시스템 상태`
- [ ] 기본 탭은 `계정 현황` (`?tab=accounts` 또는 파라미터 없음)
- [ ] 탭 클릭 시 URL이 `/dashboards?tab={id}` 로 변경되고 해당 iframe 표시
- [ ] 잘못된 tab 파라미터(`?tab=invalid`) → `계정 현황` fallback
- [ ] 각 탭 iframe은 해당 Grafana URL을 `src`로 사용
- [ ] 기존 레이아웃 회귀 없음 (사이드바, 헤더)
- [ ] 단위 테스트: DashboardTabs 컴포넌트 렌더링 + 활성 상태 표시

## Related Specs

- `specs/services/admin-service/architecture.md` (참조용)

## Related Contracts

없음 (백엔드 API 변경 없음)

## Edge Cases

- `?tab=` (빈 값): `accounts` fallback
- `?tab=ACCOUNTS` (대소문자 불일치): `accounts` fallback (소문자 정규화)
- `NEXT_PUBLIC_GRAFANA_*_URL` 미설정 시: z.string().url().default(...) 기본값 사용

## Failure Scenarios

- 없음 (외부 API 호출 없음)

## Test Requirements

- `tests/unit/DashboardTabs.test.tsx` 신규 작성
  - 3개 탭 버튼 렌더링 확인
  - activeTab prop에 따른 활성 스타일 확인
  - 탭 클릭 시 router.push 호출 확인
