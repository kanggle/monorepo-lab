# Task ID

TASK-PC-FE-346

# Title

`ADR-MONO-083` P8 콘솔 후속 (2) — E-commerce 쓰기 버튼을 영역 `*_WRITE` 역할로 숨김 (`permission-map.ts` `writeRequires` · 계약 § 2.4.3.4 «Read-only sets hide write buttons»)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (규칙 · 데이터는 있다 — 7 화면 배선 + 시험)
> 기안 2026-10-16 UTC — `TASK-PC-FE-345` 에서 분리(그 티켓 § 범위 결정). 서버는 이미 라이브(`TASK-MONO-795`, 28차 창) — 머지 게이트 없음.

# Dependency Markers

- **선행**: `TASK-PC-FE-345`(E-commerce 메뉴 `serverRequires` · 진입 역할 가드 — 머지 뒤 착수, 같은 `permission-map.ts` 행을 고친다).
- 같은 모양: `TASK-PC-FE-342`(SCM — `ReplenishmentScreen` · `SeedConfigScreen` 의 `canWrite` · «조회 전용» 문구 · 페이지가 `canWriteOn` 으로 계산).

# Goal

`TASK-PC-FE-345` 는 E-commerce 메뉴 신호만 고쳤다. 영역 조회 세트(`ecommerce-read` · 주문 운영 세트만 등)를 가진 운영자에게 다른 영역의 쓰기 버튼이 보이고, 누르면 서버 `403` 이다. 계약 § 2.4.3.4 규칙대로 화면별 `writeRequires` 를 선언하고 버튼을 숨긴다.

# Scope

## In Scope

- 계약 먼저: § 2.4.3.4 «Declared today» 표에 E-commerce 7 화면 행 · «Scales with P5–P8» 의 P8 문장에서 «버튼 미선언» 을 지운다.
- `permission-map.ts` E-commerce 행 `writeRequires`:

  | 화면(하위 라우트 포함) | 버튼 | `writeRequires` |
  |---|---|---|
  | `/ecommerce/products` (`/new` · `/[id]` · `/[id]/edit`) | 등록 · 수정 · 삭제 · 옵션 · 재고 · 이미지 | `ECOMMERCE_PRODUCT_WRITE` |
  | `/ecommerce/sellers` (`/new` · `/[id]`) | 등록 · 활성화 · 정지 · 종료 · 구성원 초대 | `ECOMMERCE_PRODUCT_WRITE` |
  | `/ecommerce/orders` (`/[id]`) | 상태 전이 | `ECOMMERCE_ORDER_WRITE` |
  | `/ecommerce/shippings` | 상태 · 추적 갱신 | `ECOMMERCE_SHIPPING_WRITE` |
  | `/ecommerce/promotions` (`/new` · `/[id]` · `/[id]/edit`) | 등록 · 수정 · 삭제 · 쿠폰 발급 | `ECOMMERCE_PROMOTION_WRITE` |
  | `/ecommerce/settlements` (`/periods/[id]`) | 수수료율 · 기간 개시 · 마감 · 지급 실행 | `ECOMMERCE_SETTLEMENT_WRITE` |
  | `/ecommerce/notifications/templates` (`/new` · `/[id]/edit`) | 등록 · 수정 | `ECOMMERCE_NOTIFICATION_WRITE` |

- 각 화면 컴포넌트가 `canWrite` 를 받아 버튼 · «새로 만들기» 링크를 숨기고 «조회 전용» 문구를 보인다. `new` · `edit` 라우트에 직접 들어온 쓰기 권한 없는 운영자는 폼 대신 «조회 전용» 안내(서버가 어차피 403).
- 페이지는 `getDomainBusinessRoles({ ecommerce })`(`app/(console)/_lib/domain-business-roles.ts`) + `canWriteOn(href, roles)`.

## Out of Scope

- 메뉴 신호(`TASK-PC-FE-345`) · 서버 판정(795) · 회원 화면(조회만 — 버튼 없음).

# Acceptance Criteria

- [ ] **AC-0** — 7 화면의 쓰기 버튼 · 링크 · 라우트 전수(컴포넌트 · testid)와 위 표를 서버(`OrderOperatorAccess` · `ProductOperatorAccess` · 각 `OperatorRoleGuard` · `TemplateController`)와 대조. e2e · smoke · federation 의 해당 testid 사용처 grep.
- [ ] **AC-1** — 계약 개정이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: `ecommerce-read` → 7 화면 쓰기 버튼 없음 + «조회 전용» / `ecommerce-order-operate` 만 → 주문 상태 전이 버튼 보임, 다른 화면 버튼 없음 / 상품 운영 세트 → 상품 · 판매자 버튼 보임.
- [ ] **AC-3** — fail-open: row 12 실패 · 테넌트 없음 · 샘플 방문자 → 버튼 보임.
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P8
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` § 업무 역할

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4

# Edge Cases

- 판매자 화면 = 상품 영역 역할(795 Q1).
- 정산 지급 실행에 통제 역할은 없다(795 Q2) — `ECOMMERCE_SETTLEMENT_WRITE` 하나.
- 데모 운영자(legacy-compat)는 13 역할 전부 — 데모 · nightly e2e 의 버튼은 그대로 보여야 한다.

# Failure Scenarios

1. 진입 역할(`ECOMMERCE_OPERATOR`)을 `writeRequires` 에 넣는다 — row 12 에 없어 모든 운영자의 버튼이 숨는다(345 의 가드가 막는다).
2. 버튼만 숨기고 `new` · `edit` 라우트는 연다 — 폼을 다 채운 뒤 403.
3. 선언만 하고 배선을 빠뜨린 화면 — 계약이 숨는다고 말하는 버튼이 보인다.
