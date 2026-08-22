# Task ID

TASK-MONO-565

# Title

ADR-MONO-067 AC-0 ① — 프런트 **번들 산출물**에서 절대 백엔드 URL 모집단을 다시 센다

# Status

review

# Owner

monorepo

# Task Tags

- infra
- frontend
- measurement

---

# 배경

[`ADR-MONO-067`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) 이
`B + 단계 1~4 + D1 + D2 + D3(Vercel 정본) + D4 별도` 로 **ACCEPTED** 됐다. 그 ADR 이
단계 순서를 정할 때 쓴 표는 이것이다:

| 앱 | 상대 `/api` fetch | env 기반 절대 fetch | `route.ts` |
|---|---:|---:|---:|
| console-web | 5 | 5 | 159 |
| web-store | 3 | **0** | 5 |
| fan-platform-web | 1 | 1 | 2 |

🔴 **이 표는 소스코드를 정규식으로 센 대리지표다.** ADR 본문이 스스로 그렇게 적었고,
AC-0 ①이 *"번들 산출물에서 다시 세라"* 를 요구하는 이유가 그것이다. 이 숫자가 틀렸다면
*"web-store 가 가장 싼 파일럿"* 이라는 **단계 순서의 전제부터** 다시 봐야 한다.

🔵 AC-0 의 나머지(②③④)는 **Vercel 배포가 필요해 지금 못 잰다** — 배포가 rate limit 으로
24시간 막혀 있다(2026-08-22 11:43 UTC 관측, 오늘 `kanggle-portfolio` 와 `kanggle-fan` 을
번갈아 물었다). ①은 로컬 빌드만으로 가능하므로 먼저 한다.

# Goal

세 프런트 앱의 **클라이언트 번들**(브라우저가 실제로 받는 JS)에 **백엔드 오리진이 몇 건
박혀 있는지**를 산출물에서 세고, 그 수로 ADR 의 단계 순서 근거를 갱신한다.

# Scope

- `projects/ecommerce-microservices-platform/apps/web-store`
- `projects/fan-platform/web/fan-platform-web`
- `projects/platform-console/apps/console-web`
- 산출물: 측정 스크립트 + 결과의 ADR-MONO-067 반영

**범위 밖**: 이관 코드 변경, AC-0 ②③④, 단계 1~4 착수.

## ✅ 결과 (2026-08-22 UTC)

측정 트리 = 메인 체크아웃 `8de648759`. 🔴 티켓 워크트리와 세 앱 소스 blob 이 동일함을
**판정 전에** 대조했다(`console-web` 은 새 워크트리의 Next/Turbopack 함정을 피하려 설치된
체크아웃에서 쟀다). 스캐너: `scripts/scan-client-bundle-origins.mjs`.

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
fan 의 클라이언트 번들에 남은 `oidcIssuerUrl` 기본값 **`http://iam.local` 이 항상 쓰이게 된다** —
배포에서 무엇을 설정하든. 즉 **브라우저가 `iam.local` 에 못 박힌다.**

🔵 단, 이 측정이 잰 것은 **존재**이지 **사용**이 아니다. 그 필드를 클라이언트 코드가 실제로
읽는지는 따로 확인해야 한다. **과대주장하지 않는다** — 다만 D4(OIDC/쿠키 축)가 왜 별도 결정이어야
하는지의 근거는 이것으로 하나 더 늘었다.

### 단계 순서 — **바꾸지 않는다** (근거는 갱신)

번들 노출 건수만 보면 console(7) > fan(2) 이라 순서가 뒤집힐 것처럼 보인다. 그러나
**노출 건수와 이관 비용은 다른 축**이다:

- console 의 7건은 **한 청크의 한 스키마 모듈**에 몰려 있다 — 고칠 지점이 사실상 하나다.
- fan 은 **프록시 층 자체가 없다**(`route.ts` 2개) — 경계를 새로 만들어야 한다.

⇒ ADR 이 쓴 근거(`route.ts` 수 = 이미 있는 BFF 면적)가 이관 비용에 더 가깝다. **순서 2→3→4 유지.**
🔵 다만 *"console 이 노출은 더 많다"* 는 사실은 남긴다 — 3단계의 **첫 작업이 무엇인지**를 정해 준다.

### 부수 발견 — web-store 는 **이 호스트에서 빌드가 실패한다**

`output: standalone` 이 심볼릭 링크를 만들려다 `EPERM` 으로 죽어 `BUILD_RC=1` 이다(Windows).
실패 지점이 **클라이언트 청크 생성 이후**(`Compiled successfully` → `Generating static pages
(23/23)` → `Finalizing` 다음)라 이 측정은 유효하다. CI(Linux)에서는 통과하므로 지금껏 안 보였다.

---

# Acceptance Criteria

**AC-0 — 술어를 먼저 정하고, 그 술어가 **볼 수 있음**을 증명한다.**

🔴 이 티켓의 결함 모드는 *"0건이 나왔는데 사실은 스캐너가 장님이었다"* 이다. 그러므로
**양성 대조군이 판정보다 먼저다**:

- 알려진 프로브 값을 env 로 주입해 빌드한다(예: `NEXT_PUBLIC_*=http://ac0-probe.invalid`).
- 그 값이 **클라이언트 청크에서 실제로 발견되어야** 한다.
- 발견되지 않으면 이 티켓의 모든 "0건" 은 **판정 불가**다. 스캐너를 고치기 전엔 진행 금지.

**AC-1 — 모집단을 산출물에서 센다.**

- 대상은 **클라이언트 청크만** (`.next/static/**/*.js`).
  🔴 `.next/server/**` 은 **세지 않는다** — 서버는 평문 HTTP 를 불러도 되고 (B) 는 바로
  그것을 전제로 한다. 서버 번들을 섞어 세면 숫자가 부풀고 **틀린 결론**이 나온다.
- 세는 것: 앱 자신의 오리진이 아닌 **절대 오리진 리터럴**
  (`*.sslip.io` · `*.local` · `localhost:<port>` · 그 밖의 `http(s)://호스트`).
- 앱별로 **건수와 실제 문자열**을 함께 남긴다. 🔴 *"몇 건"* 만으로는 무엇을 고쳐야 할지
  알 수 없다 — 이 저장소는 그 차이에 이미 데였다.
- 🔴 **소스맵(`.map`)은 세지 않는다.** 브라우저가 실행하는 것은 `.js` 다.

**AC-2 — 빌드 조건을 명시한다.**

`NEXT_PUBLIC_*` 의 **기본값**이 소스에 박혀 있다(`http://console.local` · `http://iam.local` ·
`http://fan-platform.local` · `http://localhost:8080`). env 없이 빌드하면 그 기본값이
인라인된다. 그러므로 **두 조건**으로 잰다:

1. **env 없음** — 지금 그대로 Vercel 에 올리면 무엇이 박히는가
2. **프로브 env 주입** — 배포가 값을 줄 때 무엇이 박히는가 (AC-0 의 대조군과 같은 빌드)

두 숫자가 다르면 **그 차이 자체가 발견**이다.

**AC-3 — ADR-MONO-067 을 실측값으로 갱신한다.**

ADR 의 대리지표 표 옆에 산출물 기준 수치를 적고, 단계 순서(2 → 3 → 4)가 여전히 옳은지
판정한다. 🔴 **순서가 바뀌면 바뀐 대로 적는다** — 어긋난 칸은 먼저 발견이다.

# Related Specs

- [`ADR-MONO-067`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — 선행이자 목적지
- [`ADR-MONO-017`](../../docs/adr/ADR-MONO-017-platform-console-bff-architecture.md) — console BFF

# Related Contracts

없음 (측정 전용).

# Edge Cases

- 🔴 **`console-web` 에 `node_modules` 가 없다**(2026-08-22 실측). 설치가 선행이고, 새
  워크트리에서의 Next/Turbopack 바이너리 문제를 이 저장소는 이미 겪었다 — 측정은
  **설치가 되어 있는 체크아웃**에서 하고, **어느 트리를 쟀는지 반드시 기록**한다.
- 문자열이 청크에 있다고 브라우저가 반드시 그 주소를 부르는 것은 아니다. 이 측정이 재는
  것은 **"브라우저가 오리진을 아는가"** 이고, D1 이 금지하는 것도 그것이다.
  🔵 이 한계를 결과에 명시한다 — 과대주장하지 않는다.

# Failure Scenarios

- **양성 대조군이 안 잡힘** → 스캐너가 장님. 모든 0건은 판정 불가. 진행 금지.
- **빌드 실패** → 그 앱은 `판정 불가`로 적고 나머지를 진행한다. 🔴 빌드 실패를 "0건" 으로
  기록하지 않는다.
- **두 조건의 결과가 같음** → 프로브가 안 먹었을 가능성을 **먼저** 배제한다(AC-0 대조군이
  그 배제다). 그 뒤에도 같으면 그 사실을 발견으로 적는다.
