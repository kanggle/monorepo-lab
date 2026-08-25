# ADR-MONO-067 — 데모의 방문자 화면을 전부 Vercel 에서 서비스한다

**Status:** ACCEPTED
**Date:** 2026-08-22
**주관 티켓:** 없음 — 이 결정이 티켓의 **선행**이다. ACCEPT 이후에 티켓을 판다.
**선행:** [`ADR-MONO-017`](ADR-MONO-017-platform-console-bff-architecture.md) (console BFF) · [`ADR-MONO-001`](ADR-MONO-001-port-prefix-scaling.md) (Local Network Convention) · `TASK-MONO-389`(론처 최초 = S3+CloudFront) · `TASK-MONO-557`(론처에 이름 묶인 Vercel 주소 추가)
**출처:** 소유자 요청 — *"화면은 다 버셀에서 서비스하도록 이관할거야: 기동페이지, 스토어, 팬, 콘솔"* (2026-08-22)

## History

- 2026-08-22 — PROPOSED. 요청을 받고 구조를 실측하니, 이것은 *"배포 위치를 옮긴다"* 가 아니라
  **오리진 스킴이 바뀌는 결정**임이 드러났다. Vercel 은 HTTPS 전용이고 데모 백엔드는 평문 HTTP 라,
  브라우저가 두 층 사이를 어떻게 건너는지가 결정의 본체다. 소유권/경계 결정이므로
  `platform/architecture-decision-rule.md` 의 ACCEPTED 게이트에 걸린다.
  🔴 **ACCEPT 는 소유자가 한다.** 아래 § 추천은 추천일 뿐이다.
- 2026-08-22 — **ACCEPTED: `B + 단계 1~4 + D1 + D2 + D3(Vercel 정본) + D4 별도`** (소유자 정확형 지정).
  추천과 일치하지만 **추천이 승인이 된 것이 아니라** 소유자가 별도로 지정했다 —
  `platform/architecture-decision-rule.md` § The ACCEPTED Gate 는 `진행`/`추천대로` 같은 긍정
  신호로는 열리지 않는다.
  🔴 **ACCEPT 가 AC-0 을 면제하지 않는다.** 아래 § AC-0 의 4항목 중 *"Vercel 함수에서 평문 HTTP
  업스트림 호출이 되는가"* 는 **이 결정 자체를 무너뜨릴 수 있는 항목**이다 — 그것이 거짓이면
  (B) 는 성립하지 않고, 그때는 이 ADR 을 SUPERSEDE 하는 후속 결정이 필요하다.
  **단계 1(론처 이중 배포 정리)은 AC-0 과 독립**이므로 먼저 착수 가능하다.
- 2026-08-25 — **근거 정정 2건 (`TASK-MONO-578`). 결정 · 단계 순서 · Status 는 무변경.**
  이 ADR 안에 **재지 않고 쓴 주장 두 개**가 살아 있었고, 소스 실측이 둘 다 뒤집었다:
  **정정 ①** *"fan 은 프록시 층 자체가 없다"* = **거짓**(Server Action 5파일, 클라에서 나가는
  fetch 1건이 상대경로, 주소 만드는 지점 1곳) · **정정 ②** *"브라우저가 `iam.local` 에 못 박힌다"*
  = **지지되지 않음**(클라 독자 0). 🔴 정정을 **두 자리에 함께** 넣었다 — 같은 주장이 § 단계 순서와
  § 결정 표에 있었고, 한쪽만 고치면 **살아남은 거짓이 더 자주 읽히는 절**에 남는다.
  🔴🔴 **열린 질문 — 소유자에게 올린다**: 정정 ① 로 **3↔4 순서의 근거가 비었다.** 남은 두 축에서
  fan 이 더 싸 보인다(D2 플러그 지점 **fan 1** vs **console 8 오리진**, D4 는 **둘 다** 걸린다).
  그러나 순서는 ACCEPT 된 결정의 일부라 **이 정정이 바꿀 수 없다** — 3↔4 를 다시 지정할지는
  **소유자 정확형 지정** 사안이다. 🔵 **단계 1·2 는 영향 없다.**

---

## Context — 실측한 것

### 지금의 층 구성

| 층 | 어디 | 무엇 |
|---|---|---|
| 론처(정적 HTML) | **Vercel + CloudFront 이중 배포** | 버튼과 화면 목록. `md5 6437f1fe…` 세 곳(두 배포 + 저장소) 동일 |
| 컨트롤 플레인 | AWS API Gateway + Lambda | `/start` `/stop` `/status` `/domains` |
| 데모 스택 | **AWS EC2 1대** | 102 컨테이너 / 9 도메인. Traefik 이 `<도메인>.<ip>.sslip.io` 로 분기 |

론처는 `TASK-MONO-389`(2026-07-14)에서 **S3+CloudFront 로 먼저** 났고, `TASK-MONO-557`(2026-08-19)이
*"이름에 묶인 주소"* 를 위해 **Vercel 배포를 덧붙였다.** CloudFront 판은 지금도 살아 있다
(terraform 상태에 `aws_s3_bucket.site` · `aws_cloudfront_distribution.site` 등 존재, 응답 200).

### 프런트 세 앱이 백엔드 주소를 어떻게 얻는가

| 앱 | 읽는 값 | 기본값 |
|---|---|---|
| console-web | `NEXT_PUBLIC_APP_URL` 외 **8곳** | `http://console.local` · `http://iam.local` |
| fan-platform-web | `NEXT_PUBLIC_GATEWAY_URL` | `http://fan-platform.local` |
| web-store | `NEXT_PUBLIC_API_URL` / `API_URL_INTERNAL` | `http://localhost:8080` |

### 각 앱이 이미 서버 라우트를 통과하는 정도 (2026-08-22 실측)

| 앱 | 상대 `/api` fetch | env 기반 절대 fetch | `route.ts` |
|---|---:|---:|---:|
| console-web | 5 | 5 | **159** |
| web-store | 3 | **0** | 5 |
| fan-platform-web | 1 | 1 | 2 |

🔵 **이 표는 대리지표다.** 정규식으로 센 것이라 SSR/서버 컴포넌트의 직접 호출과 브라우저 호출을
완벽히 가르지 못한다. **결정의 근거로는 충분하지만 착수 전에 다시 세야 한다**(아래 AC-0).

### ✅ AC-0 ① 완료 (2026-08-22, `TASK-MONO-565`) — 산출물로 다시 셌다

위 표는 **소스 정규식 대리지표**였다. 세 앱을 실제로 빌드해 **클라이언트 청크
(`.next/static/**/*.js`) 에서** 다시 셌다(`.next/server/**` 는 세지 않는다 — 서버는 평문
HTTP 를 불러도 되고 (B) 가 바로 그것을 전제로 한다).

**🔴 양성 대조군이 먼저다.** 같은 빌드 안에서 두 값을 함께 주입해 **반대 방향**으로 갈렸다:

| 주입 | client | server | 뜻 |
|---|---:|---:|---|
| `NEXT_PUBLIC_TOSS_CLIENT_KEY` (web-store, 클라가 읽음) | **1** | 0 | 스캐너에 **눈이 있다** |
| `NEXT_PUBLIC_API_URL` (web-store, 서버 분기 전용) | **0** | 4 | web-store 의 0 은 **진짜 0** |
| `NEXT_PUBLIC_GATEWAY_URL` (fan) | **1** | 4 | 공개 env 는 클라에 인라인된다 |
| `OIDC_CLIENT_SECRET` (fan) | **0** | 0 | 🔵 **시크릿은 어느 번들에도 안 실린다 — 실측** |

**실측 결과:**

| 앱 | 클라이언트 번들의 백엔드 오리진 | 원인 | 빌드 |
|---|---:|---|---|
| **web-store** | **0** | 클라 분기가 상대경로 `/api/bff` **리터럴** | `RC=1`(아래 주) |
| **fan** | **2** — `fan-platform.local` · `iam.local` | 서버·클라 설정이 **한 env 모듈** | `RC=0` |
| **console** | **7** — `console/iam/wms/scm/finance/erp/ecommerce.local` | **zod 스키마 `.default(...)`**, 전부 **한 청크**(`6921-*.js`) | `RC=0` |

🔴 **숫자를 두 번 고쳤다.** 초판 술어는 미니파이 조각(`http://n`·`https://a`)을 오리진으로
셌고, 그다음 판은 `localhost` 를 전부 백엔드로 셌다. **출처를 열어 보고서야** 알았다 —
web-store 의 `localhost` 3건은 NextAuth 자기 오리진·`startsWith()` **문자열 비교용 리터럴**이라
부를 주소가 아니다. 숫자만 비교했으면 web-store 를 "2건"으로 적어 **틀린 채 나란히** 놓았을 것이다.

**⇒ 세 앱이 아니라 두 종류의 문제였다.** fan 과 console 은 원인이 **동일**하다(서버 env 모듈이
클라이언트 번들에서 도달 가능). 고치는 모양도 같다 — **모듈 경계 분리**. web-store 는 그 경계를
이미 그어 놓았고, 그래서 **2단계 파일럿 선정은 산출물 수준에서 강화된다.**

🔵 **저장소가 제약 2 를 이미 알고 우회해 뒀다** — `web-store/src/app/api/store-config/route.ts`
주석: *"왜 route handler 이고 `NEXT_PUBLIC_*` 이 아닌가: Next 는 `NEXT_PUBLIC_*` 을 **빌드 타임에
인라인**한다… `NEXT_PUBLIC_TOSS_CLIENT_KEY` 가 바로 그렇게 인라인되고, 그것이 정확히 그 이유다."*

### 🔴 그리고 이것은 "문자열이 있다" 보다 나쁘다

비공개 env 는 **어느 번들에도 인라인되지 않는다**(실측: `OIDC_ISSUER_URL` client=0/server=0 —
런타임 조회다). 그러면 브라우저에서 `process.env.OIDC_ISSUER_URL` 은 `undefined` 이고,
fan 의 클라이언트 번들에 남은 `oidcIssuerUrl` 기본값 ~~**`http://iam.local` 이 항상 쓰이게 된다**~~
~~즉 **브라우저가 `iam.local` 에 못 박힌다.**~~

🔵 단, 이 측정이 잰 것은 **존재**이지 **사용**이 아니다. 그 필드를 클라이언트 코드가 실제로
읽는지는 따로 확인해야 한다. **과대주장하지 않는다** — 다만 D4(OIDC/쿠키 축)가 왜 별도 결정이어야
하는지의 근거는 이것으로 하나 더 늘었다.

#### ✅ 정정 ② — 그 "따로 확인"을 했다 (2026-08-25, `TASK-MONO-578`)

위 문단이 스스로 열어 둔 항목의 **답**이다. 철회가 아니라 **닫힘**이다 — 원문은 정직하게 유보를 달았고,
유보가 가리킨 측정을 이제 했다.

| 잰 것 | 값 |
|---|---:|
| `@/shared/config/env` 비테스트 임포터 | **6** |
| 그중 `'use client'` | **2** — `portone-billing-key.ts` · `portone-checkout.ts` |
| 그 2개가 읽는 필드 | `portoneStoreId` · `portoneChannelKey` **뿐** |
| **`oidcIssuerUrl` · `gatewayUrl` 을 읽는 클라이언트 지점** | **0** |
| `oidcIssuerUrl` 를 읽는 3모듈의 임포터 (1홉 전수) | **전부 서버** — `auth.ts` · `session.ts` · `middleware.ts` · `Header.tsx` · `[...nextauth]/route.ts` · `login/page.tsx` |

⇒ 문자열이 번들에 **있는** 이유는 두 portone 클라 모듈이 **env 모듈 전체**를 클라이언트 그래프로
끌어오기 때문이고, **그 값을 읽는 클라 코드는 없다.** 브라우저는 `iam.local` 에 못 박혀 있지 않다.

🔴 **그래도 D1 가드의 대상은 그대로다.** *"안 읽힌다"* 는 *"번들에 없다"* 가 아니다. 안 읽히던 값이
읽히게 되는 변경은 **조용하고**, 잡기 쉬운 축은 **존재**다 — 가드는 존재를 잡아야 한다.

🔴 **이 측정의 한계 3줄**: ① **1홉** 임포터 전수이지 다홉 도달성 증명이 아니다 ② **소스** 측정이다
③ **산출물** 측정이 아니다(산출물은 `TASK-MONO-565` 가 했고 그것이 잰 것은 **존재**다).
술어가 말할 수 있는 것 이상을 주장하지 않는다.

🔵 **D4 가 별도 결정이어야 하는 근거는 줄지 않는다** — 그 근거의 본체는 *"로그인 리다이렉트는 최상위
내비게이션이라 프록시로 감쌀 수 없다"* 이고, 이 정정은 거기에 닿지 않는다. 사라진 것은 **덤으로
얹었던 근거 하나**뿐이다.

### 단계 순서 — **바꾸지 않는다** (근거는 갱신)

번들 노출 건수만 보면 console(7) > fan(2) 이라 순서가 뒤집힐 것처럼 보인다. 그러나
**노출 건수와 이관 비용은 다른 축**이다:

- console 의 7건은 **한 청크의 한 스키마 모듈**에 몰려 있다 — 고칠 지점이 사실상 하나다.
- ~~fan 은 **프록시 층 자체가 없다**(`route.ts` 2개) — 경계를 새로 만들어야 한다.~~
  🔴 **거짓이다.** 아래 **정정 ①** 이 실측으로 대체한다(2026-08-25, `TASK-MONO-578`).

⇒ ~~ADR 이 쓴 근거(`route.ts` 수 = 이미 있는 BFF 면적)가 이관 비용에 더 가깝다.~~
🔴 **3↔4 순서(console 먼저 · fan 나중)의 근거는 이제 비어 있다.** 그러나 **순서는 바꾸지 않는다** —
`단계 1~4` 는 소유자 **정확형 지정**으로 ACCEPT 된 결정의 일부이고, 근거가 무너졌다고 내가 결정을
다시 쓰는 것은 **self-ACCEPT** 다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate).
다시 지정할지는 § History 2026-08-25 의 **열린 질문**으로 올린다.
🔵 다만 *"console 이 노출은 더 많다"* 는 사실은 남긴다 — 3단계의 **첫 작업이 무엇인지**를 정해 준다.
🔵 **단계 2(web-store 파일럿)는 흔들리지 않는다** — web-store 가 뽑힌 이유는 D4 축에 **아예 안
걸린다**는 것이었고, 이 정정은 그 사실을 건드리지 않는다.

### 🔴 정정 ① — fan 에는 프록시 층이 **있다** (2026-08-25 실측, `TASK-MONO-578`)

위 취소선 주장은 `route.ts` 개수로 유추한 것이었다. 소스를 열어 세니 **다른 그림**이 나왔다:

| 잰 것 | 값 | 어디 |
|---|---:|---|
| `'use server'` 파일 | **5** | `features/{follow,post,membership,notification}/api/*` |
| 비테스트 `fetch(` 호출 지점 **전수** | **3** | `demo-payment.ts` · `client.ts` · `auth-callbacks.ts` |
| 그중 **클라이언트**에서 나가는 것 | **1** | `demo-payment.ts:20` |
| 그 1건의 대상 | **상대경로** `/api/payment-config` | 백엔드 오리진이 아니다 |
| 백엔드 주소를 **만드는** 지점 | **1** | `shared/api/client.ts:42` (`env.gatewayInternalUrl`) |

그리고 `shared/api/client.ts` 의 docblock 이 스스로 그렇게 적어 뒀다:

> *"Browser-side fetches are intentionally **not implemented** in this module — all read paths go
> through Server Components (RSC fetch) and write paths through Server Actions (`'use server'`).
> This keeps the access_token on the server and out of the client bundle."*

🔴 **`route.ts` 를 세는 술어가 재려던 것을 재지 못했다.** 그 술어는 *"서버 경계가 있는가"* 를
물으려 했는데, **Server Action 은 그 술어에 안 걸리면서 하는 일은 같다**. 개수 차이는 프록시의
**유무**가 아니라 **모양**(route handler BFF 냐 Server Action 이냐)이었다.

⇒ **fan 에 남은 진짜 비용은 프록시 신설이 아니라 D4(OIDC/쿠키)** 다 — fan 은 NextAuth 를 쓰고
그 `issuer` 가 부팅마다 움직인다(`TASK-MONO-576` AC-1.5). **console 도 같은 축에 걸린다.**
즉 3·4 를 가르는 축은 **D4 가 아니라** D2 플러그 지점 수인데, 그 축에서는 fan 이 **1곳**이다.

### 부수 발견 — web-store 는 **이 호스트에서 빌드가 실패한다**

`output: standalone` 이 심볼릭 링크를 만들려다 `EPERM` 으로 죽어 `BUILD_RC=1` 이다(Windows).
실패 지점이 **클라이언트 청크 생성 이후**(`Compiled successfully` → `Generating static pages
(23/23)` → `Finalizing` 다음)라 이 측정은 유효하다. CI(Linux)에서는 통과하므로 지금껏 안 보였다.

### Traefik 에 TLS 는 없다

`infra/traefik/` · `infra/demo/*.yml` 에서 `acme` / `certresolver` / `letsencrypt` / `tls` **0건**.
데모는 평문 HTTP 전용이다.

---

## 🔴 결정을 강제하는 두 제약

### 제약 1 — Mixed content: HTTPS 페이지는 평문 HTTP 를 **못 부른다**

Vercel 은 HTTPS 전용이고 백엔드는 `http://iam.<ip>.sslip.io` 다. 브라우저는 HTTPS 문서에서 나가는
`http://` **서브리소스 요청을 차단**한다. CORS 설정으로 풀리는 문제가 아니다 — 요청이 나가지도 않는다.

🔵 단, **최상위 내비게이션은 막히지 않는다.** OIDC 로그인은 `fetch` 가 아니라 브라우저를
`http://iam.<ip>/oauth2/authorize` 로 **보내는** 것이라 mixed content 규칙에 걸리지 않는다.
대신 **HTTPS 사이트의 세션 쿠키와 평문 IdP 사이**에 별도의 문제가 생긴다(`SameSite=None` 은
`Secure` 를 요구하고, `Secure` 쿠키는 평문 오리진에서 안 붙는다). 이 저장소는 그 반대 방향으로
이미 데인 적이 있다. **이 축은 아직 측정되지 않았다** — AC-0 의 본체다.

### 제약 2 — 백엔드 주소가 **부팅마다 바뀌는데** `NEXT_PUBLIC_*` 은 **빌드 타임에 박힌다**

`NEXT_PUBLIC_*` 는 빌드 시 번들에 문자열로 인라인된다. 데모 IP 는 켤 때마다 달라진다
(2026-08-22 하루에만 `54-181-1-212` → `43-200-129-91` → `54-116-51-195`).
⇒ **한 번 구운 Vercel 빌드는 다음 부팅의 데모를 가리킬 수 없다.**

🔵 론처는 이 문제를 이미 풀어 놓았다 — `/status` 로 IP 를 받아 링크를 **그 자리에서** 만든다.
즉 해법의 모양은 저장소 안에 이미 있다: **주소는 빌드 산출물이 아니라 런타임 조회 결과여야 한다.**

---

## 선택지

### (A) 백엔드에 TLS 를 준다 — Traefik + Let's Encrypt

브라우저가 `https://iam.<ip>.sslip.io` 를 직접 부른다. 앱 코드 변경 최소.

- ➖ **IP 가 부팅마다 바뀌므로 매 기동마다 새 인증서**가 필요하다. Let's Encrypt 는 등록 도메인 기준
  주당 발급 상한이 있고, `sslip.io` 는 **공용 서픽스**라 남들과 상한을 나눠 쓴다 —
  데모를 자주 켜면 **발급이 막힐 수 있고, 그때 데모 전체가 죽는다.**
- ➖ 부팅 시간에 ACME 왕복이 더해진다(현재 644초).
- ➖ 실패가 **부팅 경로**에 들어온다 — 오늘 552 가 겨우 판정 가능하게 만든 그 경로다.

### (B) 브라우저는 Vercel 만 부른다 — 모든 백엔드 호출을 **Next 서버 라우트로 프록시**

브라우저 → `https://<app>.vercel.app/api/...` (HTTPS) → Vercel 서버 → `http://<도메인>.<ip>.sslip.io` (평문).
서버 대 서버 구간이라 mixed content 가 성립하지 않는다. 백엔드 주소는 **서버에서 런타임에** 정한다.

- ➕ **이 저장소의 기존 패턴이다.** console-web 은 이미 `route.ts` 159개, web-store 는 브라우저
  절대 fetch **0건**. `ADR-MONO-017` 이 console BFF 를 이미 그렇게 정의했다.
- ➕ `NEXT_PUBLIC_*` 빌드 인라인 문제가 **사라진다** — 브라우저는 상대 경로만 안다.
- ➖ 모든 트래픽이 Vercel 함수를 거친다. **무료 플랜의 한도가 새 병목**이 된다
  (오늘 이미 배포 rate limit 으로 CI 가 빨개졌다 — 같은 플랜의 다른 한도다).
- ➖ OIDC 리다이렉트는 프록시로 못 감싼다(최상위 내비게이션). **그 축은 따로 풀어야 한다.**

### (C) 백엔드도 서버리스로 이관

- ➖ 102 컨테이너 / Kafka / Postgres / 8 도메인이다. **다른 프로젝트**다. 이 ADR 의 범위가 아니다.

---

## 결정 — `B + 단계 1~4 + D1 + D2 + D3(Vercel 정본) + D4 별도`

🔵 **이 절은 원래 "추천" 이었고 2026-08-22 소유자 정확형 지정으로 결정이 됐다**(History 참조).
문구를 그대로 두면 *"추천일 뿐"* 과 `Status: ACCEPTED` 가 서로 다른 말을 하게 되므로 제목을 고친다 —
한 사실이 두 절에 있으면 **살아남은 거짓이 더 자주 읽히는 쪽**이 된다.

**(B) + 단계적 이관.** 근거는 실측이다: 이미 세 앱 중 둘이 (B) 의 모양에 가깝고, (A) 는 실패를
**부팅 경로**에 밀어 넣으며 그 실패가 **남과 공유하는 rate limit** 에 달려 있다.

단계 순서도 실측이 정한다 — **남은 일이 적은 것부터**:

| 단계 | 대상 | 근거 | 남은 일 |
|---|---|---|---|
| 1 | **기동페이지** | 이미 Vercel + 런타임 해석 | **이관할 것이 없다.** 대신 **CloudFront 이중 배포를 정리**한다(아래 D3) |
| 2 | **web-store** | 브라우저 절대 fetch **0건** | 서버 라우트의 업스트림을 런타임 해석으로. **(B) 를 가장 싸게 실증하는 파일럿** |
| 3 | **console** | `route.ts` 159개(이미 BFF) | 남은 절대 fetch 5건 + **OIDC/쿠키 축** |
| 4 | **fan** | Server Action **5파일** — 서버 경계가 **이미 있다** | 🔴 **정정됨 (2026-08-25 `TASK-MONO-578`)**: 프록시 신설이 아니다. 주소를 만드는 지점 **1곳**(`client.ts:42`) + **D4**(움직이는 issuer). ↑ § 단계 순서 아래 **정정 ①** |

### D1 — 브라우저는 백엔드 오리진을 모른다

세 앱 모두 브라우저 코드에서 백엔드 절대 URL 을 **금지**한다. 가드로 지킨다(정적 검사:
클라이언트 번들에 `sslip.io` / `NEXT_PUBLIC_*_URL` 유래 절대 백엔드 주소가 나타나면 실패).

### D2 — 백엔드 주소는 **런타임 조회 결과**다

서버 라우트가 요청마다(또는 짧은 TTL 캐시로) 컨트롤 플레인 `/status` 에서 IP 를 얻어 업스트림을
만든다. **빌드 산출물에 IP 가 박히면 안 된다** — 그것이 D1 의 가드가 잡는 것과 같은 결함이다.

### D3 — 론처의 집을 **하나로** 정한다

지금 론처는 Vercel 과 CloudFront 두 곳에 배포된다. 오늘은 md5 가 같지만 **한 사실이 두 집을
가지면 한쪽만 갱신된다.** 그리고 하필 Vercel 쪽이 **배포 rate limit 으로 24시간 막혀 있다** —
이 상태에서 론처를 고치면 CloudFront 만 새 판이 되고, 포트폴리오 링크로 쓰는 **Vercel 쪽이 낡는다.**

두 사본이 같은지 재는 가드는 **지금 없다.** `562`/`563`/`564` 의 신선도 판정자는 *각 배포가
자기 기준으로 최신인가* 를 보지, *두 배포가 서로 같은가* 를 보지 않는다 — **표면 N개는 각각
초록인데 교집합은 아무도 안 재는** 모양이다.

⇒ **Vercel 을 정본으로, CloudFront 판은 폐기**를 추천한다(이름 묶인 주소가 557 의 목적이었다).
폐기 전까지는 **두 사본의 동일성을 재는 가드**를 둔다.

### D4 — OIDC/쿠키 축은 **별도 결정**이다

로그인 리다이렉트는 프록시로 감쌀 수 없다. 이 ADR 은 그 축을 **열린 질문으로 남기고**,
AC-0 실측 뒤 필요하면 후속 ADR 로 가른다. 🔴 **이 축이 안 풀리면 3·4단계는 못 간다** —
2단계(web-store)를 파일럿으로 고른 이유이기도 하다.

---

## 🔴 AC-0 — **착수 전에** 재야 할 것 (ACCEPT 가 이것을 면제하지 않는다)

이 ADR 의 어느 선택지도 아직 **라이브에서 시험되지 않았다.** ACCEPT 는 *어느 방향으로 갈지*를
정한 것이지 *그 방향이 성립한다*를 정한 것이 아니다. 아래를 재기 전에는 **2단계 이후를 착수하지
않는다.** 🔵 **단계 1(론처 이중 배포 정리)은 AC-0 과 독립**이므로 먼저 갈 수 있다.

1. ✅ **완료(2026-08-22, `TASK-MONO-565`) — 위 § 참조.** ~~모집단을 다시 세라.~~ 위 fetch 표는 정규식 대리지표다. 브라우저 번들을 실제로 빌드해
   `sslip.io`/절대 백엔드 URL 이 몇 건 남는지 **산출물에서** 센다(구조가 아니라 행위).
2. ✅ **완료(2026-08-25, `TASK-MONO-571`) — 참이다. 이 ADR 은 무너지지 않는다.**
   ~~**(B) 가 실제로 되는가** — Vercel 함수에서 평문 HTTP 업스트림 호출이 성공하는지 1건으로 확인.
   실패하면 이 ADR 의 추천이 통째로 무너진다.~~

   `kanggle-fan` **프로덕션**의 `nodejs` route handler 에서 잰 **원문 응답**:

   ```json
   { "verdict": "PLAINTEXT_HTTP_EGRESS_WORKS",
     "cells": {
       "plaintextA":   { "url": "http://neverssl.com/", "ok": true, "status": 200, "location": null },
       "plaintextB":   { "url": "http://example.com/",  "ok": true, "status": 200, "location": null },
       "httpsControl": { "url": "https://example.com/", "ok": true, "status": 200, "location": null } } }
   ```

   **판정이 유효한 이유** — 대조군(`httpsControl`)이 통과했다. 그것이 같이 죽었다면 *이그레스가
   아예 없는 런타임*과 *평문만 막힌 런타임*이 **같은 출력**을 내어 판정 불가였다. 그리고 두 평문
   칸이 **`location: null` 인 2xx** 다 — `redirect: 'manual'` 로 불렀으므로 `301 → https` 승격을
   성공으로 오독한 것이 아니다. 주제는 **두 독립 출처**에서 일치했다.

   🔴 **이 통과가 (B) 의 성립을 뜻하지는 않는다.** 잰 것은 *"Vercel 함수가 평문 HTTP 로 나갈 수
   있다"* 이고, 남은 것은 **`sslip.io` DNS 해석 · EC2 보안그룹의 Vercel 이그레스 허용 ·
   80 이외 포트**다(프로브 응답의 `notMeasured` 가 그 셋을 명시한다). 과대주장하지 않는다.
3. **OIDC 왕복** — HTTPS 프런트 ↔ 평문 IdP 에서 로그인이 끝까지 되는지. 🔴 *"안 될 것 같다"* 가
   아니라 **실측**이어야 한다. 이 저장소는 쿠키 축에서 두 방향 모두 데인 적이 있다.
4. **Vercel 무료 플랜의 한도** — 배포 rate limit 외에 함수 호출/실행시간 한도가 데모 트래픽을
   견디는지. 오늘 CI 를 빨갛게 만든 것이 같은 플랜의 다른 한도였다.

---

## Consequences

- ➕ 화면 주소가 **이름에 묶인다.** `sslip.io` 의 기계 주소가 방문자에게 노출되지 않는다.
- ➕ 데모가 꺼져 있어도 **화면 자체는 뜬다**(백엔드 없는 상태를 앱이 표현해야 한다 — 새 요구다).
- ➖ Vercel 무료 플랜이 **새 단일 장애점**이 된다. 오늘 그 플랜의 한도가 이미 한 번 물었다.
- ➖ 모든 데모 트래픽이 Vercel 함수를 거치므로 **지연이 한 홉 늘어난다**(서울 ↔ Vercel 리전).
- ➖ 로컬 개발(`*.local` Traefik)과 데모의 경로가 **갈라진다.** 지금은 같은 모양인데,
  (B) 는 데모에서만 프록시를 태운다 — **로컬에서 초록인 것이 데모를 증명하지 않게 된다.**
  이 저장소가 반복해서 데인 모양이므로, 가드는 **데모 경로에서** 돌아야 한다.
