# Task ID

TASK-FAN-FE-015

# Title

멤버십 구독 버튼이 데모에서 눌리지 않는다 — 백엔드 목 PG 는 승인하는데 프런트가 PortOne 키가 없다며 요청 전에 거절한다 (그리고 같은 화면이 "모의 PG 로 처리됩니다" 라고 약속한다)

# Status

ready

# Owner

fan-platform

# Task Tags

- frontend
- demo
- payment

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다. 티켓의 AC-4 "멤버십 구독이 브라우저에서
성공한다" 가 이 결함으로 **달성 불가**였다.

## 실측 (2026-08-05, 브라우저 · 결과로 판정)

「프리미엄으로 업그레이드」 클릭:

```
memberships 행 수: 2 → 2          ← 아무 일도 일어나지 않았다
화면: "결제 모듈이 설정되지 않았습니다 (PortOne 키 미설정)."
```

`requestPortOnePayment` 의 첫 줄이 그것이다:

```ts
if (!env.portoneStoreId || !env.portoneChannelKey) {
  return { ok: false, message: '결제 모듈이 설정되지 않았습니다 (PortOne 키 미설정).' };
}
```

그리고 그 값들은 어디에도 없다 — `docker-compose.yml` 의 `fan-platform-web` env 에도,
`infra/demo/demo.env` 에도, 실행 중 컨테이너 env 에도 `PORTONE` 이 **0건**(전수 확인).

## 🔴 백엔드는 멀쩡하다

`SubscribeUseCase` 는 `PaymentGatewayPort` 를 통해 검증하고, 기본 프로파일에서는
목 게이트웨이가 승인한다. 시드가 **API 로 구독을 성공시킨다**(실측 201, ACTIVE).
막힌 것은 프런트의 사전 가드 하나뿐이다.

## 🔴 게다가 화면이 거짓말을 한다

`membership/page.tsx` 는 하드코딩된 문구를 띄운다:

> 멤버 전용·프리미엄 콘텐츠를 위한 구독입니다. **결제는 데모용 모의 PG로 처리됩니다.**

스위치는 없는데 문구만 있다. 면접관은 "모의 PG" 를 읽고 누른 뒤 "결제 모듈 미설정"
을 본다. **문구가 코드보다 앞서 있다** — 이 저장소가 반복해서 당한 모양이다.

## 형제 파리티 — web-store 는 이미 받았다

`TASK-BE-572` 가 ecommerce 스토어프런트에 정확히 이 스위치를 넣었다:

- 백엔드 `demo-pg` 프로파일(목이 승인, 나머지는 전부 진짜)
- 프런트는 `NEXT_PUBLIC_*` 이 아니라 **`/api/store-config` 런타임 조회**로 분기
  (🔴 `NEXT_PUBLIC_*` 은 **빌드타임에 인라인**되므로 compose env 로는 못 바꾼다 —
  이 티켓도 같은 함정을 밟는다)
- 설정 조회 실패는 **"데모 아님"** 으로 폴백(반대면 일시 장애가 실 스토어를 전부 승인으로 바꾼다)
- 렌더된 compose 에서 백엔드 프로파일 ↔ 프런트 플래그 정합을 보는 CI 가드

`fan-platform-web` 은 그 파리티에서 빠진 straggler다.

---

# Goal

데모에서 멤버십 구독이 **브라우저로** 성공한다 — 그리고 실제 PG 를 쓰는 설정에서는
지금과 똑같이 PortOne 결제창을 연다.

---

# Scope

## In Scope

- `fan-platform-web` 의 데모 결제 분기(런타임 조회)
- `membership/page.tsx` 의 "모의 PG" 문구를 **실제 상태와 연동**
- 백엔드 프로파일 ↔ 프런트 플래그 정합 가드(BE-572 의 가드 (x) 형제)

## Out of Scope

- PortOne 실키 — **절대 저장소에 넣지 않는다.** 데모는 목으로 돈다
- membership-service 의 PG 포트 — 이미 프로파일로 갈린다(`portone` 아니면 목)

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 실측을 다시 받아 본다. **행 수로** 판정한다.
      🔴 화면 문구로 판정하면 안 된다 — 이 화면은 실패했을 때도 "모의 PG" 라고 적혀
      있어서, 문구 기반 술어는 실제로 **거짓 통과**를 냈다(개발 중 실측)
- [ ] **AC-1 (브라우저 성공)** — 데모 설정에서 구독 클릭 → `memberships` 행이 늘고
      화면이 새 등급을 보여준다. 서버 액션 200 만으로는 부족하다
- [ ] **AC-2 (실 PG 경로 보존)** — PortOne 키가 설정된 구성에서는 결제창이 그대로 열린다.
      데모 분기가 기본값이 되면 안 된다(BE-572 가 `!portone` 을 복제하지 않은 이유와 동일)
- [ ] **AC-3 (문구가 상태를 따른다)** — "모의 PG" 문구는 실제로 데모 분기일 때만 나온다.
      **음성 대조**: 실 PG 구성에서 그 문구가 사라지는 것을 확인한다
- [ ] **AC-4 (빌드타임 함정)** — 플래그가 `NEXT_PUBLIC_*` 이면 compose env 변경이
      **반영되지 않는다.** 런타임 조회임을 테스트나 가드로 고정한다
- [ ] **AC-5 (정합 가드)** — 백엔드 목 PG ↔ 프런트 데모 플래그가 어긋난 조합이 CI 에서
      걸린다. 네거티브 3종(백엔드만/프런트만/동시)을 확인한다

---

# Related Specs

- `web/fan-platform-web/src/features/membership/lib/portone-checkout.ts` (사전 가드)
- `web/fan-platform-web/src/shared/config/env.ts` (`portoneStoreId` / `portoneChannelKey`)
- `web/fan-platform-web/src/app/(main)/membership/page.tsx` (하드코딩된 문구)
- `apps/membership-service/.../application/SubscribeUseCase.java` (백엔드 — 이미 정상)
- ecommerce `TASK-BE-572` 의 `/api/store-config` + 가드 (x) — 선례

# Edge Cases

- **자동 갱신(빌링키) 경로**도 같은 사전 가드를 쓴다(`portone-billing-key.ts`) — 함께 볼 것
- 업그레이드는 0원일 수 있다(크레딧 ≥ 정가) — 백엔드가 PG 를 아예 부르지 않는다.
  그 경로는 지금도 열릴 수 있으니 **테스트 픽스처가 그 경로로 새지 않게** 할 것

# Failure Scenarios

- **문구만 고친다** — 버튼은 여전히 안 눌린다
- **`NEXT_PUBLIC_PORTONE_*` 에 아무 값이나 넣는다** — 결제창이 열리고 실 PG 에서 실패한다.
  "설정됐다" 와 "동작한다" 는 다르다
- **데모 분기를 기본값으로 만든다** — 실 배포가 조용히 전부 승인이 된다

# Definition of Done

- [ ] 구현 + 가드
- [ ] AC-1 브라우저 증거(행 수 변화)
- [ ] AC-3 음성 대조
- [ ] Ready for review
