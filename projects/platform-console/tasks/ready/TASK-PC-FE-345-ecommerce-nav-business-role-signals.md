# Task ID

TASK-PC-FE-345

# Title

`ADR-MONO-083` P8 콘솔 후속 — E-commerce 9 화면 «업무 권한 없음» · 쓰기 버튼을 E-commerce 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · 운영 개요 ecommerce 카드

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-795` 를 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-16 UTC — 792 · 793 · 794 의 콘솔 후속 `TASK-PC-FE-342` · `343` · `344` 와 같다). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `ECOMMERCE_OPERATOR` 만 내므로 **모든 운영자의 E-commerce 9 화면에 거짓 «업무 권한 없음» + 쓰기 버튼 숨김** — 서버는 열어 주는데 화면이 닫는다. 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-795`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` § Next window — TASK-MONO-795). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-342`(P5 SCM) · `TASK-PC-FE-343`(P6 ERP) · `TASK-PC-FE-344`(P7 Finance) — 같은 모양 · `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-795` 가 E-commerce 운영자 경로 58 의 업무 인가를 영역별로 세분하고 **서비스마다 다시 검사**하게 했다(ecommerce `iam-integration.md` § 업무 역할 — 정본). 지금 콘솔의 E-commerce 9 화면은 `serverRequires = ECOMMERCE_OPERATOR`(게이트웨이 판정, `permission-map.ts:285-289`)인데, 795 뒤 그 역할은 auth 가 파생하는 **진입 역할**이라 `/me/domain-access` 에 나오지 않는다. 메뉴 · 버튼 신호를 서버 판정과 같게 만든다.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 — E-commerce 행을 아래 대조표로 · «Scales with P5–P8» 에 P8 완료 표시.
- `apps/console-web/src/shared/guide/permission-map.ts` E-commerce 행(`/ecommerce` · `/ecommerce/products` · `/orders` · `/shippings` · `/promotions` · `/users` · `/sellers` · `/settlements` · `/notifications/templates`, `/ecommerce/guide` 제외):
  - `gate.roles` = E-commerce 카탈로그 역할(상한) — `ECOMMERCE_OPERATOR` 단독이 아니라 13 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 대조표 대로. `ECOMMERCE_GATEWAY_REQUIRES` 상수는 **지운다**(게이트웨이 판정은 신호의 근거가 아니다 — 서비스 판정이 근거).
- 운영 개요(`/dashboards/overview`)의 ecommerce 카드(`GET /api/admin/products?page=0&size=1`): 상품 조회 역할이 없는 운영자에게 `forbidden` 으로 정직하게 보이는지 — `'*'` 와일드카드(플랫폼 SUPER_ADMIN)는 795 뒤에도 그 카드가 열린다(서버가 상품 조회 3 · 이미지 목록 1 에 한해 와일드카드를 유지).
- `/ecommerce` 개요 화면의 요약 카드(주문 · 상품 · 회원 · 판매자 · 프로모션 · 배송 · 알림 요약)가 각 영역 조회 역할이 없을 때 카드별 `forbidden` 으로 보이는지 AC-0 에서 재고 대조표에 적는다.
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 단위 시험.

## Out of Scope

- 서버 판정 변경(795 에서 끝났다) · web-store(소비자 — 무변경).
- 검색 재색인 화면(콘솔 호출자 0 · 게이트웨이가 운영자를 그 경로로 통과시키지 않는다 — `TASK-MONO-795` AC-0 § 2).
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-795` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회 `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|
| `/ecommerce` 개요 | AC-0 (요약 카드별) | — | — |
| `/ecommerce/products` | `ECOMMERCE_PRODUCT_READ` · `ECOMMERCE_PRODUCT_WRITE` | 등록 · 수정 · 삭제 · 옵션 · 재고 · 이미지 | `ECOMMERCE_PRODUCT_WRITE` |
| `/ecommerce/orders` | `ECOMMERCE_ORDER_READ` · `ECOMMERCE_ORDER_WRITE` | 상태 전이 | `ECOMMERCE_ORDER_WRITE` |
| `/ecommerce/shippings` | `ECOMMERCE_SHIPPING_READ` · `ECOMMERCE_SHIPPING_WRITE` | 상태 · 추적 갱신 | `ECOMMERCE_SHIPPING_WRITE` |
| `/ecommerce/promotions` | `ECOMMERCE_PROMOTION_READ` · `ECOMMERCE_PROMOTION_WRITE` | 등록 · 수정 · 삭제 · 쿠폰 발급 | `ECOMMERCE_PROMOTION_WRITE` |
| `/ecommerce/users` | `ECOMMERCE_MEMBER_READ` | (READ-ONLY) | — |
| `/ecommerce/sellers` | `ECOMMERCE_PRODUCT_READ` · `ECOMMERCE_PRODUCT_WRITE` | 등록 · 활성화 · 정지 · 종료 · 구성원 초대 | `ECOMMERCE_PRODUCT_WRITE` |
| `/ecommerce/settlements` | `ECOMMERCE_SETTLEMENT_READ` · `ECOMMERCE_SETTLEMENT_WRITE` | 수수료율 · 기간 개시 · 마감 · 지급 실행 | `ECOMMERCE_SETTLEMENT_WRITE` |
| `/ecommerce/notifications/templates` | `ECOMMERCE_NOTIFICATION_READ` · `ECOMMERCE_NOTIFICATION_WRITE` | 등록 · 수정 | `ECOMMERCE_NOTIFICATION_WRITE` |

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-795` 를 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `ECOMMERCE_ORDER_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 E-commerce 13 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(`OrderOperatorAccess` · `ProductOperatorAccess` · 각 서비스 `OperatorRoleGuard` · `AdminUserController` · `TemplateController`)와 다시 대조.
- [ ] **AC-1** — 계약 § 2.4.3.4 개정이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: 조회 세트(`ecommerce-read`) → E-commerce 메뉴 배지 없음 · 쓰기 버튼 없음 / 주문 운영 세트만(`ecommerce-order-operate`) → `/orders` 배지 없음 · 버튼 보임, 나머지 7 화면 배지 / 회원 조회 없는 세트 → `/users` 배지 / E-commerce 세트 0 → E-commerce 메뉴 전부 배지.
- [ ] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임(지금 규칙 그대로).
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P8
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (`/me/domain-access`)

# Edge Cases

- `ECOMMERCE_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- 판매자 화면은 별도 영역이 아니다 — 상품 영역 역할(소유자 결정 Q1, OD-6 «상품(+판매자)»).
- 테넌트를 고르지 않은 운영자(기본 토큰 — 역할 0)는 E-commerce 전 화면이 서버 403 이다 — 신호도 «업무 권한 없음» 이 맞다. 플랫폼 SUPER_ADMIN `'*'` 는 상품 조회 · 주문 조회만 서버가 열어 준다(TASK-BE-506 그대로) — 이 비대칭을 신호가 거짓으로 덮지 않는지 AC-0 에서 본다.

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김.
2. `ECOMMERCE_OPERATOR` 를 `serverRequires` 에 남긴다 — 진입 역할은 서비스에서 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다(조회 403 화면에 배지가 안 뜬다).
3. 판매자 화면을 별도 역할로 만든다 — 서버에 없는 역할이라 신호가 영원히 «권한 없음».
