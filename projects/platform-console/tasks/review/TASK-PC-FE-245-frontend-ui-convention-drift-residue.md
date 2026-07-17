# TASK-PC-FE-245 — `frontend-ui.md` 컨벤션 드리프트 잔여 정렬

**Status:** review
**Area:** platform-console / console-web · `features/tenants` + `features/erp-ops`
**Type:** `TASK-PC-FE` (frontend refactor — 컨벤션 정렬, 행동/데이터 불변)
**Lifecycle:** backlog(2026-07-18 발굴) → 구현 → review
**정경:** `projects/platform-console/docs/conventions/frontend-ui.md` (SoT — PC-FE-241 정경화)

> **구현 완료 (2026-07-18, 미머지)**: 정경 §2 드리프트 2건(단독 outlier)을 형제 패턴에 정렬. 검증: tsc 0 · lint clean · tenant/erp 54 tests GREEN, 테스트 무수정.

---

# Goal

정경 `frontend-ui.md` §2 대비 **단독 outlier** 2건을 형제 패턴에 정렬. 데이터·라벨·testid 불변, 구조/순서만 컨벤션에 맞춤.

# Scope (구현 완료)

## 1. `TenantDetail.tsx` — hand-rolled DetailHeader → 공용 `DetailHeader` (§2)

`features/tenants/components/TenantDetail.tsx:79-99` 의 인라인 헤더(`<div className="mb-4 flex flex-wrap items-center justify-between gap-3">` + `<h1>` + 수정 버튼 + `<Link>` 목록)를 공용 `shared/ui/DetailHeader`(PC-FE-237 승격)로 교체. **바이트 동일 DOM** — DetailHeader 의 래퍼·h1·`flex gap-2` 액션행이 원본과 동일 클래스이고, 수정 버튼은 `actions` prop, back 은 `backHref`/`backTestId`. `testid`(`tenant-detail-heading`·`tenant-detail-back`·`tenant-detail-edit`)·`aria-labelledby` 보존. 미사용된 `import Link` 제거.

## 2. `BusinessPartnerDetail.tsx` — `<dl>` 필드 순서 (§2 "Status … immediately after the label")

`features/erp-ops/components/BusinessPartnerDetail.tsx` 의 순서 코드→이름→**유형(속성)**→상태→유효기간→결제조건 을 코드→이름→**상태**→유형→유효기간→결제조건 으로 재정렬(상태 블록을 유형 앞으로 이동). 형제 4종(`JobGrade`/`Department`/`Employee`/`CostCenter`Detail = 코드/사번→이름→상태 즉시)에 정렬. `StatusBadge`·testid·라벨 불변.

# Out

- 색·톤·데이터·라벨·testid 일체 불변. 순수 구조/순서 정렬.

# Acceptance Criteria

- [x] TenantDetail 공용 DetailHeader 채택(바이트 동일 DOM, testid 보존, Link import 정리).
- [x] BusinessPartnerDetail 상태 필드를 라벨 직후로 이동(형제 정렬).
- [x] 검증: tsc 0 · next lint clean · `TenantDetail.test.tsx`·`tenants-detail-page`·`erp-api`·`erp-overview-state` 54 tests GREEN, 기존 테스트 무수정. BusinessPartnerDetail 렌더 컴포넌트 테스트 부재 확인(순서 미단언 → 재정렬 안전).

# Related Specs

- 정경 `docs/conventions/frontend-ui.md` §2. 선례: PC-FE-241(정경화)/242(status chip 잔여).

# Edge Cases / Failure Scenarios

- ⚠️ 색/순서 단언 테스트 부재(PC-FE-242 교훈) → vitest GREEN 은 계약 보존 증거이지 시각 정합 증거 아님. DOM 순서·헤더 구조 육안/스냅샷 확인 권장.
- TenantDetail 스왑이 non-byte-identical 이면 tenant 테스트 RED → 54 GREEN 으로 배제.

# Review Notes

- 발굴: 2026-07-18 리팩토링 스윕(컨벤션 드리프트 스캔). 오케스트레이터 직접 구현.
- 정경화(PC-FE-242) 가 "잔여가 아닌 것" 4범주를 명시했으므로 다음 스윕이 이 둘을 재발견하지 않음 — 이 2건은 그 범주 밖의 실제 §2 이탈.
