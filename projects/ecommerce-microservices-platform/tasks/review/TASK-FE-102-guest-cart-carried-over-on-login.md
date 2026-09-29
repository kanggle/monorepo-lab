# Task ID

TASK-FE-102

# Status

review

# Title

로그인하지 않아도 장바구니를 쓴다 — 비로그인 장바구니, 로그인하면 계정 장바구니로 이어진다 (주문은 여전히 로그인 필요)

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- cart
- auth

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 백엔드 변경 없음(장바구니는 이미 클라이언트 localStorage 전용). 설계 결정은 소유자가 내렸다. 조심할 곳은 한 곳: 두 장바구니의 저장 분리(아래 Edge Cases 🔴).

---

# Goal

소유자 결정(2026-09-29): **비로그인 방문자도 장바구니를 쓴다. 로그인하면 이어 간다.** 로그아웃 시 비우는 규칙(EF-3)은 그대로.

지금은 `specs/use-cases/cart-and-order.md` UC-0 이 «인증 필수»(EF-1 비로그인 담기 → 로그인 리디렉트·아무것도 담지 않음, EF-2 `/cart` → 로그인 리디렉트)이고 코드가 그대로 구현한다:

- `features/cart/model/cart-context.tsx` — 비로그인이면 `addItem` 무시, 저장소 비움, 보이는 목록 `[]`
- `features/cart/ui/AddToCartButton.tsx` — 비로그인 클릭 → `/login?redirect=…`
- `app/(store)/cart/page.tsx` — `useRequireAuth()`
- `middleware.ts` — `/cart` 가 공개 목록에 없어 `/login?from=/cart` 로 307
- `widgets/header/Header.tsx` — 장바구니 아이콘·뱃지를 인증 상태에서만

백엔드는 관계없다: 장바구니 서버 API 가 없고, 주문(`POST /api/orders`)은 항목을 요청 본문으로 받으며 인증이 필요하다 — 그대로 둔다.

# Scope

## In Scope

1. **스펙 먼저** — `specs/use-cases/cart-and-order.md` UC-0(«로그인 없이도 가능», AF-1 로그인 시 합치기, AF-2 비로그인 주문하기 → 로그인, EF-1·2 폐지, EF-3 유지·보강), `specs/services/web-store/overview.md`(Cart state · `/cart` public · 원칙 3 · Out-of-Scope 에서 anonymous cart 삭제).
2. **저장 분리** — 계정 장바구니 = 기존 키 `cart`, 비로그인 장바구니 = 새 키 `cart:guest`.
   - 비로그인으로 로드 → `cart` 는 **지운다**(지금과 같음 — EF-3 의 보호) · `cart:guest` 를 읽는다.
   - 로그인으로 로드 → `cart` 를 읽고 `cart:guest` 를 **합친 뒤** `cart:guest` 를 지운다. 합치기 = 같은 `productId`+`variantId` 면 수량 합, 아니면 뒤에 붙인다.
   - 저장은 현재 소유자(비로그인/계정)의 키에만.
3. `AddToCartButton` — 로그인 리디렉트 제거, 항상 담고 토스트.
4. `/cart` — `useRequireAuth` 제거. `DemoHeartbeat` 는 **로그인일 때만**(그 머리 주석: 공개 열람이 EC2 를 켜 두면 안 된다).
5. `middleware.ts` — `/cart` 를 공개 목록에(정확히 `/cart` — `/checkout*` 은 그대로 보호).
6. `Header` — 장바구니 아이콘·뱃지를 비로그인에게도.
7. 테스트: 아래 AC.

## Out of Scope

- 서버 저장 장바구니·기기 간 동기화(UC-0 관련 규칙 그대로)
- 주문·결제 인증(바꾸지 않는다)
- 로그아웃 시 비우기 정책(EF-3 그대로)

# Acceptance Criteria

- **AC-0** 착수=재측정: 위 Goal 의 다섯 곳이 여전히 그 모양이고, 장바구니 서버 API 가 여전히 없다.
- **AC-1** 비로그인: 담기 → 담긴다(`cart:guest`) · `/cart` 가 200 으로 목록을 보여 준다 · 헤더 뱃지가 개수를 보여 준다.
- **AC-2** 로그인 이어 가기: `cart:guest` 가 있는 채 로그인 → 계정 장바구니에 합쳐지고(같은 상품 수량 합) `cart:guest` 는 비워진다.
- **AC-3** 🔴 격리: (a) 계정 장바구니(`cart`)가 남은 채 비로그인으로 로드 → 보이지 않고 지워진다 (b) 로그인→로그아웃 전환에서 계정 항목이 `cart:guest` 로 **새지 않는다**(전환 렌더의 저장 순서까지) (c) 로그아웃 → 계정 장바구니 비움(EF-3).
- **AC-4** 주문은 여전히 로그인: 비로그인 `/checkout` → `/login` (middleware 단위 + e2e `auth-redirect.spec.ts` 의 checkout 칸 유지).
- **AC-5** web-store 단위 테스트 전체 · lint · build 초록. 새 동작의 e2e(비로그인 담기 → `/cart` 에 보임)를 e2e-smoke 또는 e2e 에 하나. 로컬에서 앱의 e2e-smoke 를 돌린다.

# Related Specs

- `projects/ecommerce-microservices-platform/specs/use-cases/cart-and-order.md` UC-0, UC-1
- `projects/ecommerce-microservices-platform/specs/services/web-store/overview.md`

# Related Contracts

- 없음(백엔드·API 변경 없음)

# Edge Cases

- 🔴 **전환 렌더의 저장 순서** — 로그아웃 순간 한 렌더 동안 `items` 는 아직 계정 항목인데 인증은 비로그인이다. «현재 인증 상태의 키로 저장» 을 그대로 하면 계정 항목이 `cart:guest` 에 써져 다음 방문자에게 보인다. 항목이 **누구 것인지(소유자)** 를 상태에 같이 들고, 소유자와 현재 인증이 다르면 저장·표시하지 않는다.
- 인증 로딩 중 — 아무것도 읽거나 쓰지 않는다(지금과 같음).
- 담아 둔 가격이 오래됨 — 비로그인 장바구니는 더 오래 살 수 있다. 이 티켓은 가격을 다시 확인하지 않는다(지금도 계정 장바구니가 같은 모양). 주문 요청의 `unitPrice` 를 서버가 재검증하는지는 별도 확인 대상.
- 로그인 중 OIDC 로 사이트를 떠났다 돌아옴 — localStorage 는 같은 origin 이라 남는다.
- `shared/config/api.ts:69` 와 `auth-context` `logout` 이 `cart` 를 지운다 — 계정 키라 그대로 맞다.

# Failure Scenarios

- 계정 장바구니가 `cart:guest` 로 새어 공용 브라우저의 다음 방문자에게 보임 — AC-3 (a)(b).
- 합치기가 `cart:guest` 를 지우지 않아 로그아웃 뒤 같은 항목이 다시 나타남 — AC-2.
- `/cart` 를 공개로 바꾸며 `startsWith('/cart')` 류로 넓혀 다른 경로까지 열림 — 정확히 `/cart` 만.
- 비로그인 `/cart` 가 하트비트를 보내 데모 EC2 를 켜 둠 — In Scope 4.

---

# 구현 결과 (2026-09-29 UTC · 분석=구현=Opus 5.5)

## 무엇을 바꿨나

- `features/cart/model/cart-context.tsx` — 상태를 `{ owner: 'guest' | 'account' | null, items }` 로. 키 `cart`(계정) / `cart:guest`(비로그인). 로그인 로드 = `mergeCarts(계정, 비로그인)` → 저장 → `cart:guest` 삭제. 비로그인 로드 = `cart` 삭제 → `cart:guest` 읽기. 저장·표시는 **`owner === 현재 인증`** 일 때만(전환 렌더 누수 차단).
- `AddToCartButton` — 로그인 리디렉트 제거. `/cart` 페이지 — `useRequireAuth` 제거, `DemoHeartbeat` 는 로그인일 때만. `middleware.ts` — 정확히 `pathname === '/cart'` 공개. `Header` — 장바구니 아이콘·뱃지 비로그인에게도.
- 스펙 `specs/use-cases/cart-and-order.md` UC-0, `specs/services/web-store/overview.md`. `README.md` e2e 표 · `DemoHeartbeat` 머리 주석.

## 테스트

- 단위: `cart-context.test.tsx`(비로그인 담기 → `cart:guest` · 비로그인 복원 · AC-2 합치기(A×2 + 게스트 A×1·B×2 → A×3·B×2, `cart:guest` 삭제) · AC-2 같은 화면 비로그인→로그인 전환 · AC-3(a) 계정 카트 비로그인 로드 시 지움 · AC-3(b) 로그아웃 전환에 `cart:guest` 로 새지 않음), `logout-cart-integration.test.tsx`(AC-3(b) 단언 추가), `add-to-cart-button.test.tsx`, `header.test.tsx`, `middleware.test.ts`(`/cart` 공개 · `/checkout` 보호 · 대조군 `/cartx`·`/cart/anything`·`/checkout/payment`·`/my/orders` 보호).
- e2e-smoke `smoke.spec.ts`: 🔴 기존 칸이 «비로그인 `/cart` → `/login`» 을 단언하고 있었다 — 그대로 두면 CI 에서 빨간다. 교체: 비로그인 `/cart` 200 · 빈 장바구니 / `cart:guest` 시드 → `/cart` 에 상품 · 헤더 뱃지 2 / 비로그인 `/checkout` → `/login`.
- nightly 전용 e2e `e2e/auth-redirect.spec.ts` — 보호 목록에서 `/cart` 제거(`/checkout` 칸 유지). 🔴 이 파일은 `ci.yml` 이 아니라 `nightly-e2e.yml` 에서만 돈다 — 머지 후 다음 nightly 를 한 번 확인한다.

## 로컬 판정

- `npx tsc --noEmit` rc=0 (tsconfig 가 e2e 까지 덮는다) · `npx next lint` rc=0.
- `next build` — 컴파일·정적 생성 23/23 성공, 이 호스트에서만 standalone 복사 단계가 심볼릭 링크 권한(EPERM)으로 실패(코드 무관, `.next/BUILD_ID` 생성됨).
- e2e-smoke **5/5 통과**(`CI=1`, 포트 3001 비어 있음 확인 후 — 남의 서버 재사용 방지).
- **bite**: `middleware.ts` 의 `/cart` 공개 줄 제거 → 재빌드 → smoke 2 실패(`/cart` 열림 · 비로그인 장바구니 표시), `/checkout` 칸은 통과 → 복원.
- ⚪ **단위 스위트는 로컬 미측정** — web-store vitest 4 는 이 호스트(Node 24)에서 기동 불가(`#module-evaluator`, 알려진 한계). 권위 = CI `Frontend unit tests`(Node 20). 판정은 잡 초록이 아니라 **바꾼 파일들의 `(N tests)` 줄**로 한다. AC-3(b) 의 bite(소유자 검사 제거 → 빨강)도 같은 이유로 로컬에서 못 쟀다.

## CORRECTION (2026-09-29 UTC) — 첫 CI 에서 `cart-ui.test.tsx` 1건 빨강

첫 CI `Frontend unit tests`: 130 파일 중 1 실패 — `cart-ui.test.tsx › 상품이 있으면 장바구니 항목과 합계를 표시한다`(«1,500,000원» 을 못 찾음).

- **원인은 테스트의 옛 전제**: `getItem` 을 **키와 무관하게** 같은 항목으로 mock 했다. 키가 `cart` 하나일 때는 무해했지만, 이제 로그인 로드가 `cart` 와 `cart:guest` 를 **둘 다** 읽어 합치므로 같은 항목이 두 번 들어와 수량 2 → 3,000,000원. 제품 결함이 아니라 «저장 키는 하나» 라는 가정이 mock 에 남아 있던 것 — 그리고 그 가정을 바꾼 것이 이 티켓이다.
- 🔴 내가 놓친 곳: 수정할 테스트를 «인증 전제를 단언하는 파일» 로만 골랐다. 이 파일은 인증이 아니라 **저장소 모양**에 기대고 있어 그 필터에 안 걸렸다. 저장 키를 바꾸는 변경이면 `getItem` 을 mock 하는 파일 전부가 모집단이었다.
- 수정: 이 파일의 두 mock 을 `key === 'cart' ? items : null` 로. 나머지 `getItem` mock 은 `null` 반환이라 무관(grep 으로 확인).
- 같은 런에서 바꾼 파일의 실행 수(잡 로그): `cart-context` 21 · `logout-cart-integration` 2 · `header` 12 · `middleware` 10 · `add-to-cart-button` 5 — 전부 통과, 새 칸 포함.
