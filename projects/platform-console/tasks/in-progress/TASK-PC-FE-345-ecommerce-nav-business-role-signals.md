# Task ID

TASK-PC-FE-345

# Title

`ADR-MONO-083` P8 콘솔 후속 — E-commerce 9 화면 «업무 권한 없음» · 쓰기 버튼을 E-commerce 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · 운영 개요 ecommerce 카드

# Status

in-progress

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
- [x] **AC-1** — 계약 § 2.4.3.4 개정이 코드보다 먼저. → red 커밋 `96489e285`(계약 · 시험만).
- [ ] **AC-2** — 🔴 대조군: 조회 세트(`ecommerce-read`) → E-commerce 메뉴 배지 없음 · 쓰기 버튼 없음 / 주문 운영 세트만(`ecommerce-order-operate`) → `/orders` 배지 없음 · 버튼 보임, 나머지 7 화면 배지 / 회원 조회 없는 세트 → `/users` 배지 / E-commerce 세트 0 → E-commerce 메뉴 전부 배지. → **메뉴 절반 닫힘**(`console-nav-business-permission.test.ts` § TASK-PC-FE-345 · `sidebar-business-permission.test.tsx`). **버튼 절반(«쓰기 버튼 없음» · «버튼 보임») 은 `TASK-PC-FE-346` 으로 이관** — § 범위 결정.
- [x] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임(지금 규칙 그대로). → fail-open 시험(row 12 `undefined`) · 버튼은 이 PR 에서 선언 없음 = 늘 보임.
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

# Merge gate — 28th window

- 2026-10-16 UTC — gate met: the bake carrying 792/793/794/795 was live-verified in the 28th window (2026-10-16) — may now be implemented/merged. (AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`; this ticket's source `TASK-MONO-795` → `done/`; results in `TASK-MONO-672` § 28th window — harvest.)

# 구현 기록 — 2026-10-16 UTC (branch `task/pc-fe-345-ecommerce-nav`, base `af989d90d`)

> 순서 변경(코디네이터, 2026-10-16): 342 다음에 343 이 아니라 **345 를 먼저** — 아래 AC-0 의 라이브 퇴행 때문.

## AC-0 — 🔴 라이브 퇴행 확인 (코드로 증명) · 서버 대조

**판정: 확인됨 — `TASK-MONO-795` 굽기(28차 창, 2026-10-16 UTC 라이브)가 들여온 라이브 퇴행.** ecommerce 를 구독한 활성 테넌트의 **모든** 운영자에게 E-commerce 9 메뉴 + 상위 «E-Commerce» 에 거짓 «업무 권한 없음» 배지가 붙는다(서버는 열어 준다). 버튼은 숨지 않는다(E-commerce 는 `writeRequires` 선언이 없었다).

증명 사슬(코드 · 이미 기록된 라이브 수치):
1. row 12 의 `roles` = 그 운영자에게 걸린 세트들의 `domain_permission_set_role` 합집합(`OperatorDomainAccessUseCase.myDomainAccess` → `assemble` `:242-267` — 세트 역할을 그대로 모은다, 파생 없음). 진입 역할을 덧붙이는 곳은 auth 토큰 발급(`withDerivedEntryRoles`)뿐이고 row 12 는 그 경로를 지나지 않는다.
2. V0059 `:68-88` — `ECOMMERCE_OPERATOR` 를 가진 **모든** 세트에 13 역할을 넣은 뒤 `DELETE … role_name = 'ECOMMERCE_OPERATOR'`. 반복 시드(`migration-dev` `R__*`)에 `domain_permission_set_role` 를 쓰는 것 0(grep). 라이브: `TASK-MONO-795` § 28차 창 V0059 행 수 `sets_with_ecommerce_operator` **0**.
3. ⇒ legacy-compat 운영자(`demo@demo.com` 포함)의 row 12 = E-commerce 13 역할, `ECOMMERCE_OPERATOR` 없음.
4. 콘솔 c-2: E-commerce 9 행 `serverRequires = ['ECOMMERCE_OPERATOR']`(`permission-map.ts` `ECOMMERCE_GATEWAY_REQUIRES`). `leafLacksBusinessPermission` = 구독 ∧ `!holdsAny(row12 ∩ 구독, ['ECOMMERCE_OPERATOR'])` = **참** → 9 잎 전부 배지 → `businessPermissionBadgeKeys` 가 상위 `ecommerce` 키에도 배지(모든 자식 배지).
5. 시험 고정: red 에서 `🔴 live regression pin` 이 정확히 이 입력(13 역할)으로 `/ecommerce` 배지 = true 를 낸다.

⚪ 라이브 화면으로는 보지 않았다(데모 창 밖). 위 4 단계는 각각 코드 줄 · 기록된 라이브 수치로 닫힌다 — 화면 관찰은 다음 창 AC-4.

c-2 시험이 이것을 못 잡은 이유: `console-nav-business-permission.test.ts` 의 «with ECOMMERCE_OPERATOR → nothing badged» 가 row 12 에 **실제로는 오지 않는** 역할을 입력으로 썼다(대조군 입력이 현실 밖 — 795 가 그 전제를 무너뜨렸다). 이번 PR 은 그 시험을 «진입 역할만 → 배지» 로 뒤집고, 진입 역할 4 개가 어떤 선언에도 들어가지 못하게 가드를 더했다.

서버 대조(base `af989d90d`, ecommerce `iam-integration.md` § 업무 역할 정본 `:103-130`): order `OrderOperatorAccess:21-22` · product `ProductOperatorAccess:23-48`(판매자 · 판매자 구성원도 이 판정 — `AdminSellerController:34,51` · `AdminSellerMemberController:22-50`) · settlement / promotion / shipping `OperatorRoleGuard:21-22` · notification `TemplateController:37-38` · user `AdminUserController:27`(`MEMBER_READ` 만). GET = READ ∨ WRITE · 그 밖 = WRITE. ⇒ 티켓 대조표 그대로.
- `/ecommerce` 개요(티켓의 «AC-0 (요약 카드별)» 칸): `features/ecommerce-ops/api/overview-state.ts:131-137` 카드 7 = 상품 · 주문 · 배송 · 프로모션 · 사용자 · 셀러 · 알림 템플릿(+ 주문 목록/인사이트 · 셀러 목록) — 셀마다 `403` → `forbidden`(`:149-160`). 정산은 안 읽는다. ⇒ `serverRequires` = 주문 · 상품 · 회원 · 프로모션 · 배송 · 알림의 READ/WRITE(하나라도 있으면 화면이 열리고 나머지 카드만 «권한 없음»).
- 운영 개요(`/dashboards/overview`) ecommerce 카드: ⚪ 이번에 재지 않았다(BFF 합성 — 상품 조회 없는 운영자의 `forbidden` 표시는 다음 창 라이브 칸).
- `'*'` 플랫폼 운영자 비대칭(Edge Case 3): 신호는 활성 테넌트가 있을 때만 계산되고(없으면 fail-open), `'*'` 기본 토큰 경로(상품 · 주문 조회 와일드카드)는 row 12 와 무관 — 이번 PR 은 그 갈래를 바꾸지 않는다. ⚪ 라이브 미측정.

## 범위 결정 — 쓰기 버튼은 분리 (`TASK-PC-FE-346`)

대조표의 `writeRequires`(상품 · 주문 · 배송 · 프로모션 · 판매자 · 정산 · 알림 템플릿)는 **선언하지 않았다**. 선언하면 계약 «Declared today» 가 «그 버튼은 숨는다» 고 말하게 되는데, 그 버튼들은 7 화면 · 약 20 라우트(목록 · 상세 · new · edit)에 흩어져 있고 지금 어느 것도 `canWrite` 를 받지 않는다 — 배선 없는 선언은 «아무도 그리지 않는 데이터» 다. 라이브 퇴행(배지)을 빨리 내보내는 것이 먼저라 이 PR 은 메뉴 신호만 고치고, 버튼 배선은 `TASK-PC-FE-346`(ready, 이 PR 에서 기안)으로 나눴다. 그 사이 조회 세트 운영자는 쓰기 버튼을 보고 누르면 서버 `403` → 화면 안 «권한 없음»(안전 쪽 실패, 795 전과 같은 체감). ⇒ AC-2 의 «쓰기 버튼 없음» · «버튼 보임» 절반은 346 으로 넘어간다.

## Red — 커밋 `96489e285` (계약 · 시험 · 티켓만, 제품 코드 0)

- 로컬 전체: **11 failed | 4576 passed (4587)** — `console-nav-business-permission.test.ts` 10 · `sidebar-business-permission.test.tsx` 1, 전부 `AssertionError`. `tsc` rc=0.
- CI run `38958876577`, `Frontend unit tests (… console-web, vitest)` job **`117168660021`**: console-web **11 failed | 4576 passed (4587)**, 실패 파일 2 · 실패 11 건 전부 `AssertionError`(로그에서 셈).
- 🔴 퇴행 고정 시험(`🔴 live regression pin`): legacy-compat row 12(13 역할) 입력에 `/ecommerce` 배지 = **true** — 지금 라이브와 같은 판정이 시험에서 재현됨.

## Green — 데이터만

- `permission-map.ts`: `ECOMMERCE_GATEWAY_REQUIRES` 삭제 · `ECOMMERCE_ROLES`(13) · `ECOMMERCE_GET`(영역별 GET 판정) · 9 행 `roles` = 13 · `serverRequires` = 위 대조표(개요 = 6 영역 합집합).
- 로컬: `npx vitest run` **399 files / 4587 passed** · `tsc` rc=0 · `next lint` rc=0(경고 1 = 기존 `received-invitations/route.ts:26`).
- CI 커밋 `2f60a6051` run `38960061078`: `Frontend unit tests (… console-web, vitest)` job **`117172644972`** — **399 files / 4587 passed**(로그에서 셈) · 같은 job 의 tsc · lint 단계 실행 · 실행된 검사 13 전부 pass · 53 skipped.
- nightly `nightly-e2e.yml`(브랜치 dispatch) run **`38960050012`** = success(15 job 전부) · 콘솔 job `Platform Console E2E full-stack` **`117172886508`**: **12 passed · 0 flaky · 0 failed**(개요 TIMEOUT flake 없음).
- ⚪ AC-4: `main` 미머지(DRAFT) · Vercel 미배포 · 라이브 화면 미확인 — 머지 후 Vercel 배포만으로 라이브 배지가 사라져야 한다(백엔드 변경 없음, 굽기 불필요). 다음 창: 데모 운영자로 E-commerce 메뉴에 배지 없음 · `ecommerce-order-operate` 만 준 운영자는 주문 외 7 메뉴 배지.

## 함께 적는 것

- 🔵 가이드 문안이 지금 틀림(이 티켓 범위 밖, 코디네이터가 끝에 후속 기안): `features/scm-guide/data.ts:363-369`(«SCM_OPERATOR 하나뿐») · `features/iam-guide/data.ts:139,683`(도메인 역할 목록에 `*_OPERATOR`) — 795 뒤로는 `ECOMMERCE_OPERATOR` 도 같은 처지.
