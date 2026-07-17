# TASK-PC-FE-245 — `frontend-ui.md` 컨벤션 드리프트 잔여 정렬

**Status:** backlog — candidate (2026-07-18 리팩토링 스윕 발굴)
**Area:** platform-console / console-web · 여러 feature 상세 화면
**Type:** `TASK-PC-FE` (frontend refactor, 컨벤션 정렬)
**Confidence:** HIGH (둘 다 형제 대비 **단독 outlier** — 내부 일관 패턴에 대한 실측 드리프트, 발명한 규칙 아님)
**정경:** `projects/platform-console/docs/conventions/frontend-ui.md` (SoT — TASK-PC-FE-241 이 정경화)

## 발굴 근거 (PC-FE-241/242 "컨벤션 잔여" 계열)

정경 컨벤션 문서 대비 **실측으로 검증된** 드리프트 2건. 각각 구조 동일한 형제들 사이의 유일한 위반자라 "1곳에만 있는 규칙" 함정(= 사실상 없는 규칙)이 아니라 **확립된 규칙의 이탈**임.

### 1. `TenantDetail.tsx` — hand-rolled DetailHeader (§2 위반)

`features/tenants/components/TenantDetail.tsx:79-99`. 정경 §2 축자: *"Do not hand-roll an inline `<div className="flex justify-between"> … <Link>…</Link></div>` header."* L79 이 정확히 그 안티패턴(`<div className="mb-4 flex flex-wrap items-center justify-between gap-3">` 안에 `<h1>테넌트 상세</h1>` + `<Link href="/tenants"><Button variant="ghost" data-testid="tenant-detail-back">목록</Button></Link>`).

- **도달성 확인**: `app/(console)/tenants/[tenantId]/page.tsx` 로 라우팅되는 **실제 라우트 상세 페이지**(임베드 패널 아님 → §2 적용 대상).
- **9-vs-1 outlier**: `app/(console)` 하 `[id]/page.tsx` 10개 중 ecommerce 9개는 전부 공용 `DetailHeader` 경유, `TenantDetail` 만 단독 이탈.
- **처방**: 공용 `shared/ui/DetailHeader`(PC-FE-237 이 승격) 채택.

### 2. `BusinessPartnerDetail.tsx` — `<dl>` 필드 순서 위반 (§2)

`features/erp-ops/components/BusinessPartnerDetail.tsx:63-91`. 정경 §2: *"Status … comes immediately after the label."* 현재 순서 = 코드 → 이름 → **유형(속성)** → 상태 → 유효기간 → 결제조건 (상태 앞에 속성 필드 삽입).

- **5-vs-1 outlier**: 같은 디렉터리 "ERP master" 상세 4형제(`JobGradeDetail`·`DepartmentDetail`·`EmployeeDetail`·`CostCenterDetail`) 전부 코드/사번 → 이름 → **상태(즉시)** → 속성 순. `BusinessPartnerDetail` 만 유일 위반.
- **처방**: 상태를 라벨 직후로 이동.

## ⚠️ 색 단언 테스트 부재 주의 (PC-FE-242 교훈)

콘솔에는 **색/순서를 단언하는 테스트가 사실상 0개** → vitest GREEN 은 계약(testid·라벨·`data-*`) 보존의 증거이지 시각 정합의 증거가 아니다. 착수 시 스크린샷/DOM 순서로 육안 확인하고, 필요하면 순서/토큰 단언 테스트를 함께 추가할 것.

## backlog → ready 게이트

- [ ] 두 화면 수정이 각 형제 패턴과 정확히 일치하는지 확인(§2 축자 대조).
- [ ] AC: `pnpm lint`/`tsc`/vitest 회귀 0. testid·라벨 보존. 시각 순서/헤더 구조 육안 확인.

## Reference

- 정경: `docs/conventions/frontend-ui.md` §2. 선례: TASK-PC-FE-241(정경화)/242(status chip 잔여).
- 발굴: 2026-07-18 콘솔 리팩토링 발굴 스윕(컨벤션 드리프트 스캔).
