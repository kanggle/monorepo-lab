# Task ID

TASK-FAN-FE-015

# Title

멤버십 구독 버튼이 데모에서 눌리지 않는다 — 백엔드 목 PG 는 승인하는데 프런트가 PortOne 키가 없다며 요청 전에 거절한다 (그리고 같은 화면이 "모의 PG 로 처리됩니다" 라고 약속한다)

# Status

done

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

# 🟢 착수 실측 (2026-08-06, 라이브)

## AC-0 — 재측정: 결함이 그대로 재현됐고, 화면도 그대로 거짓말했다

변경 전 이미지로 라이브 클릭:

```
before rows = 2   [MEMBERS_ONLY/CANCELED, MEMBERS_ONLY/ACTIVE]
화면      : "… 결제는 데모용 모의 PG로 처리됩니다."   ← "모의 PG" 문구 존재 = true
클릭      : 「프리미엄으로 업그레이드」
after rows = 2                                        ← 아무 일도 없었다
화면 오류 : "결제 모듈이 설정되지 않았습니다 (PortOne 키 미설정)."
```

🔴 **문구 기반 술어였다면 이 실행이 통과로 보였을 것이다** — 실패한 화면에도
"모의 PG" 가 그대로 있다. 티켓 AC-0 의 경고가 정확했고, 그래서 판정을 **행 수**로 했다.

## 🔵 술어가 ecommerce 와 **반대**다 — 이 티켓의 핵심 판단

BE-572 의 가드 (x) 를 그대로 복사하면 **정반대를 단언**한다. 극성이 다르기 때문이다:

| | 기본값 | opt-in | 불변식 |
|---|---|---|---|
| ecommerce | 실 Toss 어댑터 | `demo-pg` 프로파일 | 프런트 ON ⟺ 프로파일에 `demo-pg` **있음** |
| fan | **목**(`MockPaymentGatewayAdapter` = `@Profile("!portone")`) | `portone` | 프런트 ON ⟺ 프로파일에 `portone` **없음** |

⇒ **팬 백엔드는 이미 목이었다. 몰랐던 것은 프런트뿐이다.** 그래서 가드를 (x) 의 복사가
아니라 **뒤집힌 술어**로 새로 썼다(`(x2)`). 형제 파리티는 "같은 코드" 가 아니라
"같은 성질" 이다.

## 🔴🔴 고치자 그 아래에서 두 번째 결함이 나왔다 — `crypto.randomUUID` 부재

데모 분기가 실제로 도달하기 시작하자 클릭이 이렇게 죽었다:

```
[browser error] TypeError: crypto.randomUUID is not a function
```

`crypto.randomUUID` 는 **secure context(HTTPS 또는 localhost) 전용**이다. 데모는 평문
HTTP 의 `*.local` 이라 그 속성이 아예 없다. **이건 새 결함이 아니라 잠복이었다** —
`requestPortOnePayment` 는 처음부터 그 방식으로 id 를 만들었으므로 **실 PortOne 경로도
HTTP 에서는 같은 벽에 부딪혔을 것**이다. 아무도 못 본 이유는 키 가드가 먼저 리턴해
그 줄까지 간 적이 없었기 때문이다. ⇒ `randomUuid()`(`getRandomValues` 기반 v4,
secure context 요구 없음)로 두 helper 를 함께 교체했다.

🔵 **"막힌 것은 프런트의 사전 가드 하나뿐" 이라는 티켓의 진단은 절반만 맞았다** —
가드를 치우자 그 뒤에 하나가 더 있었다. 표면 하나를 고치면 그 아래 층을 다시 봐야 한다.

## AC-1 — 브라우저 성공 (행 수로 판정)

```
before rows = 2
클릭 「프리미엄으로 업그레이드」
after  rows = 3   [PREMIUM/ACTIVE, MEMBERS_ONLY/CANCELED, MEMBERS_ONLY/CANCELED]
```

PREMIUM/ACTIVE 가 새로 생겼고 기존 MEMBERS_ONLY/ACTIVE 는 업그레이드로 CANCELED 가 됐다
(정상 승격 의미론). 결제창은 열리지 않았고 **백엔드 경로는 전부 실제로 탔다** —
데모 분기는 `paymentId` 만 만들고 구독 요청 자체는 그대로 보낸다.

## AC-3 / AC-4 — 음성 대조를 **같은 이미지**로 했다

env 만 바꿔 재기동(`--force-recreate`, 재빌드 없음):

| | 헤더 "모의 PG" | 패널 "PortOne 테스트 결제창" | 패널 "모의 PG로 처리" |
|---|---|---|---|
| `DEMO_PAYMENT_MOCK=1` | **true** | false | **true** |
| `DEMO_PAYMENT_MOCK=` | **false** | **true** | false |

🔵 **이 표 자체가 AC-4 의 증명이다** — `NEXT_PUBLIC_*` 로 인라인된 값이라면 같은
이미지에서 답이 바뀔 수 없다. 단위 테스트도 `/api/payment-config` 조회를 고정하고
소스에 `NEXT_PUBLIC_*DEMO*` 가 없음을 단언한다.

🔴 **문구가 하나 더 있었다** — `SubscribePanel` 의 "카드 결제는 PortOne 테스트
결제창에서 진행됩니다." 도 하드코딩이었다(티켓은 헤더 문구만 지목). 함께 상태 연동했다.

## AC-5 — 정합 가드 `(x2)` + 네거티브 3종

| 네거티브 | 결과 |
|---|---|
| 프런트 ON + `portone` ON | **CAUGHT** — `(portone: 1 ⇒ 목: 0)` |
| 프런트 OFF + 목 ON (= 이 티켓 이전 상태) | **CAUGHT** |
| compose 에서 `DEMO_PAYMENT_MOCK` 키 삭제 | **CAUGHT** — "찾지 못했습니다(탐지식이 깨졌습니다)" |

🔴 **두 번째 네거티브는 계측기 결함으로 처음에 통과처럼 보였다.**
`verify-demo-wrapper.sh` 가 `set -a; source demo.env` 로 값을 **덮어써서** 커맨드라인
env 가 무효였다. demo.env 를 임시 수정해 다시 재니 그제야 걸렸다. 그리고 그때는
**가드 (x) 가 먼저 죽어** (x2) 가 실행되지도 않았다 — ecommerce 쪽을 일관되게 OFF 로
맞춰 (x2) 가 유일한 판정자가 되게 한 뒤에야 진짜 측정이 됐다.
[[feedback_measurement_needs_a_validity_predicate]]

## 검증

- `pnpm test` **115건 통과**(18파일), `pnpm lint` 무경고, `tsc --noEmit` 통과
- `verify-demo-wrapper.sh` **정적 검증 PASS**(`(x)` + 신규 `(x2)`)
- 실키는 넣지 않았다 — PortOne env 는 여전히 **0건**이고 데모는 목으로 돈다

## 🔴 범위 밖 발견 (고치지 않고 기록)

`projects/fan-platform` 에 **`.dockerignore` 가 없다.** 로컬에서 `pnpm install` 을 한 뒤
이미지를 빌드하면 Windows용 `node_modules` 가 빌드 컨텍스트로 복사돼
`Cannot find module '/app/web/fan-platform-web/node_modules/next/dist/bin/next'` 로
**빌드가 깨진다**(실측). 이번엔 로컬 `node_modules` 를 잠시 치워 우회했다.
빌드 캐시/레이어에 영향이 있는 변경이라 이 티켓에서 손대지 않았다.

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

- [x] **AC-0 (재측정)** — 위 실측을 다시 받아 본다. **행 수로** 판정한다.
      🔴 화면 문구로 판정하면 안 된다 — 이 화면은 실패했을 때도 "모의 PG" 라고 적혀
      있어서, 문구 기반 술어는 실제로 **거짓 통과**를 냈다(개발 중 실측)
- [x] **AC-1 (브라우저 성공)** — 데모 설정에서 구독 클릭 → `memberships` 행이 늘고
      화면이 새 등급을 보여준다. 서버 액션 200 만으로는 부족하다
- [x] **AC-2 (실 PG 경로 보존)** — PortOne 키가 설정된 구성에서는 결제창이 그대로 열린다.
      데모 분기가 기본값이 되면 안 된다(BE-572 가 `!portone` 을 복제하지 않은 이유와 동일)
- [x] **AC-3 (문구가 상태를 따른다)** — "모의 PG" 문구는 실제로 데모 분기일 때만 나온다.
      **음성 대조**: 실 PG 구성에서 그 문구가 사라지는 것을 확인한다
- [x] **AC-4 (빌드타임 함정)** — 플래그가 `NEXT_PUBLIC_*` 이면 compose env 변경이
      **반영되지 않는다.** 런타임 조회임을 테스트나 가드로 고정한다
- [x] **AC-5 (정합 가드)** — 백엔드 목 PG ↔ 프런트 데모 플래그가 어긋난 조합이 CI 에서
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

- [x] 구현 + 가드
- [x] AC-1 브라우저 증거(행 수 변화)
- [x] AC-3 음성 대조
- [x] Ready for review
