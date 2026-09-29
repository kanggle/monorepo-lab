# Task ID

TASK-FE-103

# Status

done

# Title

데모 꺼짐 배너가 장바구니를 «잠긴 기능» 으로 말하지 않는다 — TASK-FE-102 후속

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- demo
- copy

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku — 문구 한 문장과 그 단언.

---

# Goal

`TASK-FE-102`(#4078, 머지 `735bd02e1`)로 장바구니는 로그인 없이 쓴다 — 브라우저에만 저장하므로 데모 서버가 꺼져 있어도 담긴다(2026-09-29 라이브 확인: 샘플 모드 상품 상세에서 옵션 선택 → 담기 → `cart:guest` 저장 · 뱃지 1 · `/cart` 표시).

그런데 데모 꺼짐 배너(`widgets/demo-notice/DemoBackendNoticeClient.tsx`)는 여전히 «데모 서버가 꺼져 있어 로그인할 수 없고, **장바구니**·주문 같은 로그인 후 기능도 잠겨 있습니다» 라고 말한다. 방문자는 «잠겼다» 는 말을 읽는 동안 실제로는 담을 수 있다 — `TASK-MONO-642` 가 고친 «배너가 화면과 어긋난다» 와 같은 종류의 결함이다. 소유자 지시(2026-09-29): «진행».

# Scope

## In Scope

- 배너 문구: «…로그인할 수 없고, 주문 같은 로그인 후 기능도 잠겨 있습니다. 장바구니는 로그인 없이 담아 둘 수 있습니다. …». «로그인 후 기능» 용어는 유지(`TASK-MONO-680` — 론처 카드와 같은 말).
- `DemoBackendNotice.test.tsx` 에 칸 하나: 장바구니를 잠긴 기능으로 말하지 않는다 · 주문은 잠겼다고 말한다.
- `README.md` 데모 안내 한 줄(«로그인 후 기능(장바구니·주문)»).

## Out of Scope

- 팬 배너(`fan-platform-web` — 팬의 잠긴 기능은 글쓰기·멤버십, 장바구니 없음)
- 론처 사이트 문구(`infra/demo/aws/site/index.html` 은 «글쓰기·주문·운영» — 장바구니를 말하지 않는다)

# Acceptance Criteria

- **AC-1** 꺼짐 배너 텍스트에 «장바구니·주문» 이 없고, «주문 같은 로그인 후» · «장바구니는 로그인 없이» 가 있다 — 렌더된 DOM 단언.
- **AC-2** 기존 칸(«샘플» · «불러올 수 없» 부재 · «로그인 후 기능» 존재)이 그대로 초록.
- **AC-3** 배너 문장을 단언하던 다른 곳이 없다(`git grep` — 2026-09-29 확인: 테스트·e2e·스크립트에 없음).

# Related Specs

- `projects/ecommerce-microservices-platform/specs/use-cases/cart-and-order.md` UC-0 (TASK-FE-102 갱신)

# Related Contracts

- 없음

# Edge Cases

- «켜지는 중» 배너는 장바구니를 말하지 않는다 — 바꾸지 않는다.

# Failure Scenarios

- 문구를 부드럽게 하다 «로그인 후 기능» 이 잠긴 사실까지 지움 — AC-2 의 기존 칸이 막는다.

## CORRECTION (2026-09-29 UTC) — 종결 (4차원 검증)

- (a) PR **#4081** `state=MERGED` (2026-09-29T12:45:24Z) · (b) `origin/main` 끝 = 스쿼시 **`d187e3cba`** · (c) 머지 시점 `statusCheckRollup` 실패 0 / 68. 🔵 첫 런의 `Frontend lint & build` 는 한 번 빨갰다 — `src/app/layout.tsx`(이 PR 이 안 건드린 파일)의 `next/font` 가 Google 폰트 응답을 못 읽음(`Cannot read properties of null (reading '1')`). 같은 레이아웃이 직전 #4078 CI 에서 컴파일됐으므로 러너의 외부 fetch 로 보고 **실패 잡만** 재실행 → 통과.
- (d) AC:
  - **AC-1** ✅ 렌더 DOM 단언(CI `DemoBackendNotice.test.tsx` 10 tests, 새 칸 포함) + **라이브**: store.hubwang.com `/products` 꺼짐 배너 = «…로그인할 수 없고, 주문 같은 로그인 후 기능도 잠겨 있습니다. 장바구니는 로그인 없이 담아 둘 수 있습니다. …» (머지 후 두 번째 폴링에서 반영 확인).
  - **AC-2** ✅ 같은 파일의 기존 칸(«샘플» · «불러올 수 없» 부재 · «로그인 후 기능») 초록.
  - **AC-3** ✅ 배너 문장을 단언하는 다른 곳 없음(`git grep`, 착수 시).
