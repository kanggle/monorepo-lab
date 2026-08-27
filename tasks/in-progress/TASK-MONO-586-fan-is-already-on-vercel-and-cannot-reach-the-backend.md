# Task ID

TASK-MONO-586

# Title

`ADR-MONO-067` **단계 4** — 팬은 **이미 Vercel 에 있는데 백엔드에 못 닿는다.** 주소를 만드는 지점은 **1곳**이다.

# Status

in-progress

# Owner

monorepo

# Task Tags

- adr
- frontend
- demo

---

# ⏳ 선행 — **둘. 그리고 「Vercel 프로젝트」는 선행이 아니다.**

| # | 선행 | 상태 |
|---|---|---|
| 1 | `ADR-MONO-068` **승격 트리거**가 이 티켓에서 발화한다 | 🔴 **소유자 결정 사안** — 아래 § |
| 2 | `ADR-MONO-067` AC-0 ③ **OIDC 왕복** | ❌ `TASK-MONO-574` — 🔴 팬은 D4 축에 걸린다 |
| ~~3~~ | ~~Vercel 프로젝트~~ | ✅ **이미 있다 (`kanggle-fan`)** |

🔵🔵 **그래서 이 티켓은 `TASK-MONO-575`(배포 한도)에 걸리지 않는다.** 새 Vercel 프로젝트를
만들지 않기 때문이다. **단계 2(store)·단계 3(console)과 다른 점이 정확히 이것**이고,
`ADR-MONO-067` History 의 **3↔4 순서 열린 질문**에 들어갈 사실이다.
🔴 **그렇다고 내가 순서를 바꾸지 않는다** — `단계 1~4` 는 ACCEPT 된 결정의 일부이고
재지정은 **소유자 정확형 지정** 사안이다. 사실만 올린다.

---

# Goal

**팬의 서버가 백엔드 주소를 런타임에 얻게 한다.** 화면은 이미 Vercel 에서 뜨는데
**데이터가 오지 않는다** — 이 티켓이 그 한 홉이다.

---

# Context — 실측 (2026-08-26)

## 🔴 지금 팬은 Vercel 에서 **껍데기**다

```ts
// src/shared/config/env.ts
gatewayUrl:         process.env.NEXT_PUBLIC_GATEWAY_URL ?? … ?? 'http://fan-platform.local',
gatewayInternalUrl: process.env.GATEWAY_URL_INTERNAL   ?? … ?? 'http://fan-platform.local',
```

🔴 **`NEXT_PUBLIC_*` 은 빌드 타임에 값이 박힌다.** 그런데 데모 IP 는 **부팅마다 바뀐다**
— 그 자리에 넣을 수 있는 고정값이 **존재하지 않는다.** Vercel 에 배포된 판은
`fan-platform.local` 을 부르려 하고, 방문자 브라우저는 그 이름을 해석하지 못한다.

**이것이 `ADR-MONO-067` D2 가 존재하는 이유이고, 팬에는 아직 안 들어갔다:**

```
$ bash scripts/check-demo-resolver-copies.sh
해석기를 가진 앱 1 개 (상한 1)      ← web-store 뿐이다
```

## 🔵 좋은 소식 — 고칠 지점이 **1곳**이다

```
$ grep -rn "gatewayInternalUrl" src --exclude-dir=__tests__ | grep -v config/env.ts
src/shared/api/client.ts:42:  const base = env.gatewayInternalUrl.replace(/\/+$/, '');
```

**주소를 조립하는 곳은 여기 하나**다. `TASK-MONO-578` 이 ADR 을 정정하며 적은 그대로다 —
*"프록시 신설이 아니다. 주소를 만드는 지점 1곳 + D4."*

## 🔴 「Server Action 5파일」 을 다시 세면 **17** 이 나온다 — 둘 다 맞다

`ADR-MONO-067` 은 정정 ① 에서 팬의 서버 경계를 **`'use server'` 5파일**이라고 적었다.
순진하게 다시 세면 **17** 이 나오고, 그러면 ADR 이 틀린 것처럼 보인다. **아니다:**

| 술어 | 개수 |
|---|---:|
| **모듈 최상단** `'use server'` (첫 3줄) | **5** ← ADR 이 센 것 |
| 함수 **내부 인라인** `'use server'` | 12 |
| 합계 | 17 |

🔵 **모듈 최상단이 「이 파일이 서버 경계다」의 술어**이고, 인라인은 그 파일 안의 개별 액션이다.
⇒ **ADR 의 5는 정확하다.** 이 표를 여기 남기는 이유는, 다음 사람이 17 을 보고
*"ADR 이 낡았다"* 로 오독하는 것을 막기 위해서다. **술어가 다르면 숫자가 다르다.**

## 🔴🔴 이 티켓은 `ADR-MONO-068` 의 승격 트리거를 발화시킨다

`ADR-MONO-068` = *"앱별로 구현한다. 단 **두 번째 구현이 생기는 순간 CI 가 RED**"*, 숫자는 **2**.
지금 해석기를 가진 앱은 **하나**(web-store)다. ⇒ **이 티켓이 두 번째면 가드가 RED 다.
결함이 아니라 설계된 동작이다.**

그 시점의 선택지와 대가:

| 선택지 | 대가 |
|---|---|
| **공유 패키지로 승격** | 🔴 세 프로젝트가 **각각 별도 pnpm 워크스페이스**다 — `ecommerce: apps/*+packages/*` · `fan: **web/***` · `console-web: 단독`. 게다가 팬은 `workspace:*` 의존이 **0건**이라 받을 자리조차 없다 ⇒ **루트 워크스페이스 신설**. `ADR-MONO-068` 이 되돌리기 비용으로 명시한 그 항목이다 |
| **두 번째 사본 감수** | 트리거를 2 → 3 으로 올려야 하고 **트리거가 스스로 약해진다** |
| **repo-root `libs/` 의 TS 판** | 해석 로직 자체는 project-agnostic 이라 HARDSTOP-03 을 통과할 여지가 있으나, **거기엔 TS 모듈이 없다**(Java 전용) |

🔴 **이 티켓은 고르지 않는다.** `ADR-MONO-068` 의 결정을 바꾸는 일 = **소유자 정확형 지정**.
⇒ **AC-0 에서 멈추고 올린다.**

🔵 **`TASK-MONO-585`(단계 3, console)와 이 결정을 공유한다** — 먼저 착수하는 쪽이 만난다.

---

# Scope

**In:**

- `projects/fan-platform/web/fan-platform-web/src/shared/api/client.ts` — 주소를 얻는 방식
- 해석기 — `ADR-MONO-068` 의 답이 정한 자리
- 데모 꺼짐 상태 표현(web-store 의 `DemoBackendNotice` 와 같은 요구)
- `TEMPLATE.md` § 공개 호스트명 배분 — `fan.hubwang.com` 상태 갱신

**Out:**

- 🔴 **승격 여부** → 소유자 (AC-0 에서 올린다)
- OIDC/쿠키 → **D4**, `TASK-MONO-574` · `TASK-MONO-576`
- Vercel 프로젝트 생성 → **불필요**(이미 있다)
- `NEXTAUTH_URL` · 도메인 연결 → `TASK-MONO-584` AC-5(소유자)
- 🔵 **`NEXT_PUBLIC_PORTONE_STORE_ID` / `_CHANNEL_KEY`** → 소유자 (Vercel env). 로컬 `.env.local` 이 주던 값이라 **Vercel 에는 없다** — 아래 선실측 § 참조

---

---

# ✅ AC-0 ①③ 선실측 완료 (2026-08-26) — 그리고 소스의 자기 서술 하나가 틀렸다

**측정한 트리**: `monorepo-lab` 본 체크아웃 `c5edff16b`(clean). 이 브랜치와 팬 소스가
**바이트 동일**함을 먼저 확인했다(`git diff --stat c5edff16b HEAD -- projects/fan-platform/` = 빈 출력).
빌드는 그 자리에서 새로 했다(`rm -rf .next` 선행) — 🔴 *pull 한 체크아웃은 낡은 빌드를 갖고 있다.*

**양성 대조군**: `NEXT_PUBLIC_GATEWAY_URL=http://ac0-probe.invalid` 주입.
판정을 읽기 **전에** 착지부터 확인했다 → `.next/static/chunks/992-33060dded47555d9.js`. ✅

## ① 산출물 오리진 재계수 — **`TASK-MONO-565` 의 «2» 는 그대로 유효하다**

| 버킷 | 건수 | 내용 |
|---|---:|---|
| backend | 4 | 프로브 1 + **`fan-platform.local` · `iam.local` · `localhost:3002`** |
| thirdParty | 1 | `cdn.portone.io` |
| benign | 4 | `w3.org` · `react.dev` · `nextjs.org` · `github.com` |

🔴 **스크립트는 3 이라 하고 565 는 2 라 한다. 여기서 "565 가 과소계수했다" 로 닫으면 틀린다.**
셋째 `localhost:3002` 는 `env.nextAuthUrl` 이고, 565 는 **web-store 에서 같은 종류를 이미
손으로 제외**했다(그 티켓 § *"localhost 3건은 NextAuth 자기 오리진 … 부를 주소가 아니다"*).
즉 **두 숫자는 서로 다른 술어**다 — 스크립트=「번들에 오리진 모양 문자열이 있다」,
565 의 표=「브라우저가 **부를** 백엔드 오리진」.

**그 제외가 옳은지 실측으로 확인했다** (추론이 아니라 대조군 있는 측정):

| 검사 | 클라이언트 `.next/static` | 서버 `.next/server` |
|---|---|---|
| `post_logout_redirect_uri` | **0건** | `chunks/493.js` ✅ |
| `end_session` | **0건** | — |

`env.nextAuthUrl` 의 **유일한 독자**는 `src/shared/auth/federated-logout.ts:66` 이고,
그 파일의 특징 문자열이 클라 번들에 **없다**(서버 번들엔 있다 = 내 검색 술어가 작동한다는 대조군).
⇒ **서버 전용. 제외가 옳다. 팬의 백엔드 오리진은 여전히 2건이고, 4일간 드리프트 없다.**

## ③ 「주소를 만드는 지점 1곳」 재계수 — 유효

`client.ts:42` 그대로. ①의 2건과 모순이 아니다 — **fetch 호출 지점 수**와
**번들에 박힌 오리진 수**는 다른 축이다.

## 🔴🔴 그리고 처방 하나가 여기서도 반증됐다 — `TASK-MONO-585` 와 **같은 기전**

프로브가 `fan-platform.local` 을 **대체하지 않고 옆에 나타났다.**

```
NEXT_PUBLIC_GATEWAY_URL=http://ac0-probe.invalid 로 빌드
→ 청크 992 에 ac0-probe.invalid 와 fan-platform.local 이 **둘 다** 있음
```

`?? 'http://fan-platform.local'` 의 리터럴이 **소스에 있으니 env 가 뭐든 컴파일돼 들어간다.**
⇒ *"Vercel 환경변수를 채우면 된다"* 는 처방은 **팬에서도 틀렸다.** 585 는 zod `.default()`,
586 은 `??` 폴백 — 문법만 다르고 **고칠 곳은 똑같이 모듈 경계**다.

🔵 **이것이 `ADR-MONO-068` 승격 트리거가 발화하는 자리다.** 두 앱에서 원인이 같다는 것이
이제 산문이 아니라 **양쪽 다 프로브 대조군으로 실측**됐다.

## 🔴 `env.ts` 가 자기에 대해 적은 말이 틀렸다

```ts
// src/shared/config/env.ts:6-8
// Browser-exposed values MUST start with NEXT_PUBLIC_*. The non-public ones
// are accessed only from server components / server actions / route handlers
// so leaking into the client bundle is rejected at build time.
```

**클라이언트 청크 992 의 실제 내용:**

```js
...(d=c.env.OIDC_CLIENT_SECRET)?d:"",nextAuthUrl:null!=(o=c.env.NEXTAUTH_URL)?o:"http://localhost:3002",portoneStoreId:"store-675f..."
```

빌드가 막는 것은 비공개 env 의 **값**이지 **모듈**이 아니다. `env` 객체 리터럴 전체가
브라우저로 간다 — 비공개 3개(`oidcIssuerUrl` · `oidcClientId` · `nextAuthUrl`)의
**기본값 리터럴까지 함께**. 🔴 **이 주석을 믿고 «비공개니까 안전» 으로 판단하면 안 된다.**
(565 가 `iam.local` 에 대해 이미 같은 것을 발견했다. 여기서는 **파일 자신의 반대 진술**과 대조됐다.)

## 🔵 `.env.local` — 이 체크아웃의 빌드는 Vercel 의 빌드가 아니다

본 체크아웃에 **untracked `.env.local`** 이 있다(`NEXT_PUBLIC_GATEWAY_URL` ·
`NEXT_PUBLIC_PORTONE_STORE_ID` · `NEXT_PUBLIC_PORTONE_CHANNEL_KEY` + 비공개 5개).
Vercel 빌드에는 **없다.** 위 청크에 박힌 `portoneStoreId:"store-675f..."` 는
**로컬 파일이 준 값**이고 Vercel 에서는 `''` 가 된다.

🔵 다만 **과대주장하지 않는다** — 소스가 이미 그렇게 적어 뒀다(`env.ts:31`):
*"Empty when unset → the checkout helper reports «결제 모듈 미설정» instead of crashing."*
⇒ 조용한 파손이 아니라 **명시적 미설정 표시**다. 그래도 소유자 체크리스트에는 올린다.

## 🔴🔴 그리고 «로그인 없이 데이터만» 은 성립하지 않는다 — 라우트로 셌다

*"로그인이 안 풀려도 데이터 표시부터 옮기면 방문자에게 보이는 변화가 생긴다"* 는 길이
있는지 물었다. **없다.**

| 앱 | `page.tsx` 전수 | 익명으로 도달 가능 | 가드 |
|---|---:|---:|---|
| `console-web` | **67** | **1** (`(auth)/login`) | `(console)/layout.tsx:92` `if (!(await isAuthenticated())) redirect(...)` — 64개 전부. `(onboarding)` 도 익명이면 `/login`. `src/app/page.tsx` 는 `redirect('/dashboards/overview')` 라 **루트조차 가드 안쪽** |
| `fan-platform-web` | **11** | **1** (`(auth)/login`) | `src/middleware.ts` — `/login`·`/api/auth`·`/_next`·`favicon` 외 **전부** `auth()` 검사 후 `/login?from=…` |

🔵 **이것이 `ADR-MONO-067` 의 주장을 실측으로 바꾼다.** 그 ADR 은
*"D4 축이 안 풀리면 단계 3·4 는 못 간다"* 고 **적기만** 했다. 이제 라우트 수준에서 세어졌다:
**두 앱 모두 익명 표면이 1/67 · 1/11 이고 그 하나는 로그인 화면 자신**이다.

🔴 **따라서 이 티켓의 구현은 계속 진행하되, «방문자에게 보이는 변화» 를 AC 로 적지 마라.**
모듈 경계 정리는 D4 와 무관하게 **선행 작업**이지만, 그 결과가 **보이는** 것은 D4 이후다.
그리고 이 실측은 **`TASK-MONO-574` 의 우선순위를 올린다** — 그것이 두 단계 전부의 유일한 관문이다.

**⇒ AC-0 의 ①③ 은 이 절로 답이 됐다. 남은 것은 ②(승격 결정)와 ④(도메인 응답)뿐이고 둘 다 소유자다.**

# 🔴 부분 완료 (2026-08-26, PR #3479) — **AC-3 만 남았다**

| AC | 상태 |
|---|---|
| AC-0 ①②③④ | ✅ 전부 답을 받았거나 «진행 가능» 으로 판정 |
| AC-1 서버가 런타임에 주소를 얻는다 | ✅ `shared/config/demo-backend.ts` |
| AC-2 마커 | ✅ `DEMO-RESOLVER: fan-platform-web` |
| **AC-3 데모 꺼짐 표현** | 🔴 **미구현** |
| AC-4 검증 | ✅ 단위 **15칸** CI 통과 · 가드 상태 명시(아래) · 산출물 재계수 |

## 🔵 팬은 경계를 이미 갖고 있었다 — ADR 의 비용 근거가 틀렸다

`TASK-MONO-565` 는 *"팬은 프록시 층 자체가 없다(route.ts 2개) — **경계를 새로 만들어야
한다**"* 고 적었고 그것이 단계 순서 3→4 의 근거였다. **route.ts 계수는 맞지만 추론이 틀렸다** —
팬의 게이트웨이 호출은 **이미 서버 전용**이다(읽기=RSC, 쓰기=Server Action).
실측: `Idempotency-Key` 가 `.next/server` 1건 / `.next/static` **0건**.
⇒ 새로 만들 것은 **경계가 아니라 런타임 주소 해석**이었다.

## AC-4 — 산출물 재계수 (프로브 주입, 같은 자리에서 재빌드)

| | backend 오리진 |
|---|---|
| 이전 | **4** = 프로브 + `fan-platform.local` + `iam.local` + `localhost:3002` |
| 이후 | **0** |

🔵 **양성 대조군**: PortOne 공개값은 **여전히 클라 청크에 있다** — 분리가 과하지 않았다.
🔵 이 티켓의 예상(*"첫 번째만 없어지고 `iam.local` 은 남는다"*)보다 많이 없어졌다.
**모듈이 통째로 빠지면서 셋 다** 없어졌다.

## AC-4 — `check-demo-resolver-copies.sh` 의 상태

```
[resolver-copies] OK — 해석기를 가진 앱 2 개 (승격 3) · 정규화 비교 1 쌍
                  · 앱 소스 1190 개 · 선언 앱 3 개 전부 커버 · 대조군 2 건
```

🔴 **초록으로 만들려고 마커를 빼지 않았다.** 가드를 `ADR-MONO-068` § D5 의 결정(**C**)에
맞게 **교체**했다 — 사본 세기(상한 1) → **정규화 동일성 + 3에서 승격**. self-test **10/10**.

## 🔴 남은 것 — AC-3

web-store 의 `DemoBackendNotice` 에 해당하는 위젯이 팬에 **없다**. 새로 만들어야 하고,
이 PR 의 축(해석기 + 가드)과 분리된다. 🔴 **그래서 이 티켓은 `ready` 로 남는다** —
`review` 로 올리면 AC-3 이 없는 채로 닫힐 위험이 있다.

🔵 다만 지금 사용자에게 보이는 차이는 **어차피 없다** — 팬의 익명 도달 페이지는
**11개 중 1개**(`/login`)뿐이고 나머지는 **D4** 가 풀려야 보인다. AC-3 의 실질 가치도
D4 이후에 생긴다.

---

# Acceptance Criteria

## AC-0 — 착수 전에 **올리고, 답을 받고, 재측정한다**

1. 🔴 **승격 트리거 결정을 소유자에게 올린다**(위 § 표). 답 전에는 해석기를 **두 번째로
   구현하지 않는다** — 구현하고 나서 물으면 그 답은 **이미 정해진 것**이 된다.
2. `TASK-MONO-574`(AC-0 ③)의 상태 확인. **미해결이면 로그인은 이 티켓 밖**임을 명시한다.
   🔵 데이터 표시는 로그인 없이도 성립하는 화면이 있는지 **먼저 확인**한다 — 있으면
   이 티켓만으로 방문자에게 보이는 변화가 생긴다.
3. **주소를 만드는 지점을 다시 센다.** 위 «1곳» 은 오늘 값이다.
4. 🔴 **`fan.hubwang.com` 이 실제로 응답하는지** 확인한다(`TASK-MONO-584` AC-5 는
   소유자 대시보드 작업이라 저장소가 못 잰다). 안 붙어 있어도 **이 티켓은 진행 가능**하지만,
   «라이브 확인» 을 AC 로 적을 수 없다.

## AC-1 — 서버가 주소를 **런타임에** 얻는다

동작은 web-store 판(`TASK-MONO-580`)과 **같은 세 가지 「하지 않는 것」**:
`DEMO_API_BASE` 부재 → **부르지 않음** · `state != running` → **주소를 안 만듦**
(조용히 옛 IP 로 붙으면 AWS 가 회수·재할당한 **남의 인스턴스**) · `/status` 실패 →
**기존 env 사슬**(판정 불가를 «꺼짐» 으로도 «켜짐» 으로도 번역하지 않는다).

🔵 팬의 백엔드는 **하나**(`fan-platform` 게이트웨이)다 — console 의 여덟 곳과 다르다.

## AC-2 — 마커 (`ADR-MONO-068` D2)

구현에 `DEMO-RESOLVER:` 마커를 단다. 🔴 **마커가 없으면 트리거가 그 구현을 못 센다** —
승격 트리거가 조용히 무력해진다.

## AC-3 — 데모가 꺼져 있을 때

화면은 뜨고 **데이터만 없다.** 침묵하면 방문자가 빈 목록을 «고장» 으로 읽는다.
🔴 로컬·CI 에서는 **아무것도 렌더하지 않는다**(데모가 아닌 곳에서 «꺼졌다» 는 거짓말).

### ✅ 완료 (2026-08-27 UTC)

`src/widgets/demo-notice/DemoBackendNotice.tsx` + 테스트 6칸, `(main)/layout.tsx` 에 마운트.

🔵 **이 절을 여기에 쓸 수 있는 것 자체가 `TASK-MONO-589` 의 산물이다** — 어제까지 훅이
`in-progress/` 본문 편집을 막아서 구현 중 알게 된 것을 태스크에 적을 자리가 없었다.

**세워 둔 블로커 하나가 실측으로 무너졌다.** `DemoBackendNotice` 가 `DEMO-RESOLVER:` 마커를
달고 있어 «팬에 복사하면 승격 트리거(3)가 발화한다» 를 우려했는데, `check-demo-resolver-copies.sh`
를 읽어 보니 둘 다 아니었다:

- 가드는 **파일이 아니라 앱**을 센다(`PROMOTE_AT_APPS=3`). 팬은 `shared/config/demo-backend.ts`
  로 **이미 앱 #2** 라 위젯이 늘어도 앱 수는 그대로다.
- 동일성 비교 집합은 `IMPL_RE='export (async )?function resolveDemoBackend'` 에 걸리는 파일뿐 —
  위젯은 해석기를 **소비**만 하므로 **비교 밖**이다 ⇒ 팬 문구가 web-store 판과 달라도 드리프트가 아니다.

스테이지 후 실측: `해석기를 가진 앱 2 개 (승격 3) · 정규화 비교 1 쌍 · 앱 소스 1191 개` — 변화 없음.

🔴 **마운트 위치 = `(main)/layout.tsx`.** 그 파일 주석의 *"gated by middleware"* 는 **프로덕션에서
거짓**이고(`TASK-FAN-FE-018`: `/nonexistent-xyz` 가 404 라 미들웨어가 안 돈다) 익명 방문자도 이
셸을 본다 ⇒ 배너는 가드 **뒤가 아니라 여기** 있어야 한다. 주석에 그 사실을 적었다.

🔴 **`/login` 은 덮지 않는다 — 알고 비워 둔 자리다.** 데모가 꺼지면 IdP 도 꺼져 로그인도
실패하지만 그것은 «화면은 뜨고 데이터만 없다» 와 **다른 증상**이라 다른 처방이 필요하다
(`TASK-MONO-574` 의 왕복 측정이 먼저). 조용히 같이 처리한 척하면 그 구멍이 안 보이게 된다.

🔴 **bite 를 증명했다** — 위젯을 항상 `null` 로 만들자 **6칸 중 3칸이 빨강**. 음성 대조군
(`host` 는 여전히 렌더된다)이 있어 «렌더 자체가 죽었다» 와 «조건이 거짓이다» 가 갈린다.
원본 4칸에 둘을 더했다: **반쪽 응답**(`state=running` 인데 `ip` 없음 → 배너 떠야 한다)과
**로컬에서 컨트롤 플레인을 부르지도 않는가**.

## AC-4 — 검증

- 해석기 단위 테스트(부재 · `running` 아님 · 타임아웃 · TTL)
- 🔴 **`check-demo-resolver-copies.sh` 의 상태를 명시**한다. RED 라면 **왜 RED 인지와
  AC-0 ① 의 답**을 함께 적는다. **초록으로 만들려고 마커를 빼지 마라.**
- 산출물에서 백엔드 오리진 재계수 — `TASK-MONO-565` 는 팬에 **2건**(`fan-platform.local`·
  `iam.local`)을 셌다. 이 티켓이 첫 번째를 없앤다. 🔵 **`iam.local` 은 남는다**(D4 축).
- ~~🔴 vitest 는 이 호스트에서 못 돈다(Node 24 + vitest 4) ⇒ **CI 권위**~~
  🔵 **정정 (2026-08-27 실측)** — 팬은 **돈다**. 이 줄은 `web-store` 의 제약(vitest **4** +
  Node 24 의 module evaluator 결함)을 팬에 그대로 물려온 것인데, 팬의 `package.json` 은
  **vitest `^3.2.4`** 다. 이 호스트에서 팬 전체 스위트 **23 파일 / 153칸 전부 통과**했고
  `next lint`·`tsc --noEmit` 도 rc=0 이다. ⇒ **팬은 CI 권위가 아니라 로컬에서 판정 가능하다.**
  🔴 일반화: *"이 호스트에서 못 돈다"* 는 **앱마다 다시 확인할 것**이지 저장소 상수가 아니다 —
  같은 모노레포 안에서도 vitest 메이저가 갈린다. [[feedback_local_proves_behaviour_not_performance]]

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — 단계 4 · 정정 ① · D4
- [`docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md) — 승격 트리거
- `TASK-MONO-580` — 따라야 할 형태 · `TASK-MONO-585` — 단계 3, **승격 결정을 공유**
- `TASK-MONO-574` — 팬의 OIDC 왕복 · `TASK-MONO-584` — 도메인

# Related Contracts

없음.

---

# Edge Cases

- 🔴 `client.ts` 는 **서버·클라 양쪽에서 임포트될 수 있다.** 해석기를 거기 직접 부르면
  클라 번들에 `DEMO_API_BASE` 참조가 들어간다 — `TASK-MONO-580` 이 web-store 에서
  **서버 분기에만** 붙인 이유가 그것이다.
- 🔴 `env.ts` 는 **한 모듈**이고 `oidcIssuerUrl` 같은 서버 전용 값도 담는다.
  `TASK-MONO-578` 은 *"클라 독자 0"* 이라 확인했지만 **그건 그때의 값**이다 — 산출물에서 본다.
- 🔴 팬의 `gatewayUrl`(공개)과 `gatewayInternalUrl`(서버)이 **서로 폴백**한다.
  한쪽만 고치면 다른 쪽이 옛 값을 계속 공급한다.
- 팬은 **nightly-e2e 에만 도는 스위트**가 있다 — 머지 시점에 한 번도 안 돌아 본 채 초록일 수 있다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 해석기를 먼저 만들고 승격을 나중에 물음 | 답이 **이미 정해진 것**이 된다 | AC-0 ① |
| 마커 누락 | 승격 트리거가 **조용히 무력화** | AC-2 |
| 트리거를 초록으로 만들려고 마커 제거 | 같은 결과, 더 나쁨(고의) | AC-4 |
| 클라 경로에서 해석기 호출 | `DEMO_API_BASE` 가 **클라 번들로** | Edge Cases 1 |
| `gatewayUrl` 만 고침 | `gatewayInternalUrl` 이 옛 값 공급 | Edge Cases 3 |
| 「17파일」 로 오독해 ADR 을 낡았다고 판단 | 없는 문제를 고친다 | Context § 술어 표 |
| D4 를 이 티켓에서 풀려 함 | 범위 폭발 | Scope Out |
