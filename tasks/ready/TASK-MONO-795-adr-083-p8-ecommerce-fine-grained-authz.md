# Task ID

TASK-MONO-795

# Title

`ADR-MONO-083` P8 — E-commerce 세분 서버 인가: 관리 영역별 조회/변경 역할 · 게이트웨이 단독 판정을 **서비스 재검사**까지 · `ECOMMERCE_OPERATOR` 는 진입 역할로 · iam 카탈로그 확장

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (10 서비스 · 게이트웨이 · 원자 PR — 크기가 크면 AC-0 에서 서비스 묶음별 슬라이스로 나눈다)
> 🔴 **cross-project 원자 PR(슬라이스마다)**: ecommerce 인가 + iam 카탈로그 · «이전 호환» E-commerce 세트 갱신.

# Dependency Markers

- **선행**: `TASK-BE-631` · `TASK-BE-632`.
- 형제: `TASK-MONO-792` · `793` · `794`.

# Goal

지금 관리 표면의 유일한 판정은 게이트웨이 `AccountTypeEnforcementFilter` 의 `roles ∋ ECOMMERCE_OPERATOR` 이고(`:99-108`), 서비스 다수가 `permitAll` 로 헤더를 믿는다(`order-service …/OrderSecurityConfig.java:146-157`). 주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림 · 검색을 조회/변경으로 나누고, **서비스 단에서도** 같은 역할을 검사한다.

# Scope

## In Scope

- 계약 먼저: ecommerce 서비스 명세 · 관리 API 계약에 역할 · 판정 표.
- 서비스별 재검사(`OperatorRoleGuard` 사본들을 한 술어로 — 형제 서비스 grep 먼저, CLAUDE.md § 10) · 게이트웨이 경로 판정과 일치.
- `ECOMMERCE_OPERATOR` = 진입 역할. 소비자(`CUSTOMER`) 경로 · «운영자-공개» 읽기 트리(`/api/promotions` · `/api/shippings` · `/api/notifications`) · `/internal/sellers/**` 워크로드 분기 무변경.
- iam 카탈로그 · «이전 호환» E-commerce 세트 · 시스템 프리셋.

## Out of Scope

- 판매자 범위(`X-Seller-Scope`) 주입(ADR-030 Step 4 — 데이터 범위).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 관리 컨트롤러 전수 · 게이트웨이 경로 판정 · 서비스별 재검사 유무 · 업무 구분안(OD-6) · 슬라이스 분할 계획.
- [ ] **AC-1** — 🔴 대조군: 주문 세트만 → 상품 변경 403 · 주문 200 / 조회 세트 → 변경 403 / 진입 역할만 → 업무 변경 403 / 🔴 게이트웨이를 거치지 않은 서비스 직접 호출도 같은 판정.
- [ ] **AC-2** — 이전 호환 대조군 · 소비자 경로 무변경 대조군.
- [ ] **AC-3** — 배포 상태를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3 · § 새로 생기는 위험(E-commerce 행)
- `projects/ecommerce-microservices-platform/specs/services/*/architecture.md`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/*` · iam `admin-api.md` § 카탈로그

# Edge Cases

- 운영자-공개 읽기 트리에서 운영자/소비자 판정이 서비스 헤더로 내려가는 곳 — 새 역할로도 같은 분기.
- 플랫폼 와일드카드 GET 전용 — 무변경.

# Failure Scenarios

1. 게이트웨이 경로 판정에만 세분을 넣는다 — 서비스 간 직접 호출 · 헤더 위조가 우회한다.
2. 서비스마다 사본 술어를 따로 고친다 — 한 사본만 고쳐진다.
