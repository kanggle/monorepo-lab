# Task ID

TASK-MONO-618

# Title

🔴 **`ADR-MONO-067` 단계 4 의 억제 몫 — 데모가 Vercel 로 옮겨간 팬을 계속 서빙한다.**
🔴🔴 그리고 형제(`604`)의 처방을 **그대로 복사하면 정반대를 단언한다** — 결제 mock 의 극성이 반대다.

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- adr-followup
- guard

---

# 🔎 어디서 왔나 — **두 티켓이 서로에게 떠넘겨 사라질 뻔한 일이다**

`TASK-MONO-612` AC-3 이 이 일을 미리 세어 `TASK-MONO-586` 에 CORRECTION 으로 적었다
(*"이 티켓이 해야 하는 것 (AC 에 넣어라)"*). 🔴 **그런데 586 은 `review/`(frozen) 이라 AC 를
못 얻는다.** 그래서 그 셋은 **어느 활성 큐에도 행이 없는 채로** 남아 있었다.
`TASK-MONO-616` § AC-3 이 그 공백을 이름으로 적었고 이 티켓이 그 행이다.
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

## 왜 586 이 이것을 못 하나

`ADR-MONO-067` 단계 **2**(web-store)는 **세 티켓**이 나눠 들었다 — 해석기(`580`) ·
**억제**(`604`) · 링크/가시성(`583`·`603`). 586 은 단계 4 의 **해석기 몫**이고,
**억제 몫에 해당하는 티켓이 없다.** 이 티켓이 `604` 의 팬 판이다.

---

# 🔵 선행 조건이 **막 충족됐다** (2026-09-03 실측)

`604` 의 AC-0 ① 이 세운 규칙: *"Vercel 판이 죽어 있는데 데모 사본을 끄면 방문자는 **어느
쪽에서도** 그 화면을 못 본다."* 팬에 대한 그 선행이 **`TASK-MONO-616` 기동 창 #3 에서
처음으로 충족됐다**:

```
POST https://fan.hubwang.com/api/auth/signin/iam
  → 302 https://auth.hubwang.com/oauth2/authorize?client_id=fan-platform-user-flow-client…
  → /login 200 (4,247 B) → 자격 제출 → 콜백
GET /api/auth/session → accountId · tenantId=fan-platform · roles=["FAN"]
```

⇒ `OIDC_ISSUER_URL` 이 발효됐고 **D4 축이 팬 표면에서 닫혔다.** 586 이 *"팬의 익명 도달
페이지는 11개 중 1개(`/login`)뿐이고 나머지는 **D4** 가 풀려야 보인다"* 라고 적은 그 D4 다.

🔴 **그러나 「로그인이 된다」는 「화면들이 데이터를 낸다」가 아니다.** 616 창 #3 은 왕복까지만
쟀고 **로그인 뒤 화면은 안 쟀다.** ⇒ **AC-0 이 그것부터 잰다.** 형제 축에서 정확히 이
구별이 문제였다 — `604` 는 `/products` 가 **200 인데 에러 문구를 렌더**하고 있었다.
[[feedback_a_verifiable_mechanism_is_not_the_cause]]

---

# 🔴🔴 형제와 **다른 점** — 극성이 반대다

`infra/demo/demo.env:279-285` 가 근거다:

| | 기본 | mock 이 되려면 | 불변식 |
|---|---|---|---|
| ecommerce | 실 Toss | `demo-pg` 프로파일을 **켠다** | 프런트 플래그 ON ⟺ `demo-pg` **ON** |
| **fan** | **목**(`MockPaymentGatewayAdapter` = `@Profile("!portone")`) | 아무것도 안 켠다 | 프런트 플래그 ON ⟺ `portone` **OFF** |

⇒ 가드 **(x2)** 는 (x) 의 사본이 아니다. 기대값이 `DEMO_PAYMENT_MOCK=1` 인 것은 같지만
**그 근거가 되는 백엔드 조건이 반대**다. 🔴 (x) 의 미집행 문구에 `demo-pg` 를 적으면
팬 축에서 **거짓**이 된다. [[feedback_grep_the_siblings_before_fixing_it_yourself]]

🔵 현재 (x2) 의 실제 술어(`verify-demo-wrapper.sh:1249-1257`):
`SPRING_PROFILES_ACTIVE` 에 `portone` 이 있으면 `x2_real=1`, `x2_back_mock = 1 - x2_real`,
그리고 `x2_back_mock == x2_front`(= `DEMO_PAYMENT_MOCK`)를 요구한다.
**팬 프런트가 렌더에서 사라지면 이 등식의 한쪽이 없어진다.**

---

# Goal

데모가 `web.fan-platform.<DEMO_DOMAIN>` 을 **서빙하지 않게** 하고, 그 사실을 가드가
**실행해서** 대조하며, 그 변경이 **(x2) 를 공허하게도 영구 빨갛게도** 만들지 않게 한다.

# Scope

- **포함**: 팬용 억제 override 선언 + `COMPOSE[fan]` 체인 등록 · (z19) 형태의 가드(팬 판) ·
  **(x2) 의 미집행 분기를 팬의 극성으로** · Traefik alias · 부팅 판정/론처 링크 정합
- **제외**: `kanggle-fan` 의 Vercel env 투입(🙋 소유자) — 단, AC-0 이 **필요 여부를 실측**한다
- **제외**: 억제 **런타임** 판정 — `TASK-MONO-617` 의 몫. 🔵 그 티켓이 술어를 선언에서
  유도하도록 요구하므로, 이 티켓의 억제 선언은 **자동으로 그 모집단에 들어가야 한다**
- **제외**: console(단계 3)

---

# Acceptance Criteria

## AC-0 — 🔴 **전제를 잰다. 「로그인이 된다」로 넘어가지 마라** (착수 전)

1. **`fan.hubwang.com` 의 로그인 뒤 화면이 실데이터를 내는가.** 🔴 상태코드 금지 —
   `604` 가 정한 형태로: 에러 문구 **부재** + 도메인 개체(아티스트/구독 등) **≥1**.
   🔵 **음성 대조군**: `/zzz-nope-618`. 🔴 상태코드·바이트로는 `/` 와 구별되지 않는다 —
   `Location` 의 `?from=` 을 읽어라(616 창 #3 이 고친 술어).
   ⇒ **거짓이면 억제하지 마라.** 방문자가 어느 쪽에서도 팬을 못 보게 된다.
2. **그 compose 를 읽는 소비자를 전수한다.** 🔴 `604` AC-0 ② 가 이 자리에서 **티켓이 적은
   둘 중 하나가 틀렸고 하나가 빠진 것**을 찾았다 — 상속하지 말고 다시 세라
   (`infra/demo/projects.sh` · 로컬 `npm run` · `.github/workflows/*.yml` 전수).
3. **`kanggle-fan` 프로덕션 env 에 `DEMO_PAYMENT_MOCK` 이 있는가**
   (2026-09-02 실측: `NEXTAUTH_SECRET`·`NEXTAUTH_URL`·`OIDC_CLIENT_ID`·`OIDC_CLIENT_SECRET`
   **네 개뿐 — 없다**. 🔴 그 뒤 `OIDC_ISSUER_URL` 이 추가됐으므로 목록은 **낡았다**).
4. 🔴 **`TASK-MONO-617` 의 상태를 확인한다** — 먼저 갔으면 이 티켓의 억제 선언이 그
   런타임 판정의 모집단에 **자동으로** 들어가는지 확인하고, 안 들어가면 그것이 결함이다.

## AC-1 — 억제는 **데모 프로파일에서만**

- 로컬 `docker compose up` · 로컬 워크스루 · CI 는 **영향 없음**
- 🔴 억제 선언은 **한 곳**에서 읽혀야 한다. 두 벌이면 하나만 고쳐진다.
- 🔵 기전은 형제와 같은 모양이 자연스럽다(데모 오버라이드가 `profiles:` 를 «추가»).
  🔴 그러나 `604` 가 다른 후보를 버린 이유를 **팬에서 다시 확인**하라 — `deploy.replicas: 0`
  은 렌더에 남고, `--scale` 은 렌더에 안 보이며, base 의 `profiles:` 는 로컬을 바꾼다.

## AC-2 — 가드가 **실행해서** 대조한다 (팬 판 (z19))

`604` AC-2 의 다섯 칸을 팬으로: **체인 등록 · 주입 확인 · 판정 · 대조군(base 단독) · 범위(정확히 1개)**
\+ 🔴 **바닥**(렌더가 깨지면 목록이 통째로 비고 그 0행은 «억제됨»과 구별되지 않는다)
\+ 유령 참조(`depends_on: fan-platform-web`) 0건.
🔴 **bite**: 억제를 지우면 문다.

## AC-3 — 🔴🔴 **(x2) 를 팬의 극성으로** 고친다

(x) 가 ecommerce 에서 한 것과 **같은 형태**, **반대 극성**:

- 팬 프런트가 렌더에 없으면, 그 부재가 **선언된 억제 때문인지** 먼저 확인한다
  (체인에 오버라이드가 있는가). 아니면 **FAIL** — 「누가 지웠는지 모르는」 상태를 통과시키지 않는다.
- 맞으면 **백엔드 절반만** 재고(`portone` **부재** ⇒ 목), 미집행 문구를 매 실행마다 찍는다.
  🔴 문구에 **`demo-pg` 를 쓰지 마라** — 팬의 조건은 `portone` **OFF** 다.
- 🔴 문구는 **누가·언제·무슨 명령·기대값·원장**을 담는다. 명령은 `--project kanggle-fan`,
  원장은 팬 쪽 `VERCEL.md`.
- 🔴 **아무것도 안 하고 문구만 지우는 것은 금지** — 그러면 공백이 조용해진다.

## AC-4 — 부팅 판정 · alias · 링크 정합

- 🔴 `demo-up.sh` 의 **`HTTP 표면 2/2`** 는 `console` 과 **`web.fan-platform`** 이다
  (616 창 #3 실측: `[demo] ✔ HTTP 표면 2/2: console=307 web.fan-platform=307`).
  억제하면 **찌를 수 있는 표면이 1개가 된다** ⇒ **두 상수를 같이 고쳐야 한다**(실측 확인):

  | 상수 | 지금 | 무엇인가 | 억제 뒤 |
  |---|---|---|---|
  | `SURFACE_ROW_FLOOR` (`demo-up.sh:507`) | **3** | 론처가 약속하는 화면의 **총 수**(console · web.ecommerce · web.fan-platform) | 팬 행도 Vercel 이 되면 재검토 |
  | `SURFACE_FLOOR` (`demo-up.sh:513`) | **2** | 그중 **데모가 서빙해서 찌를 수 있는** 수 | **1** |

  🔴 안 고치면 부팅 완료 판정이 **영구히 실패**한다. `TASK-MONO-583` 이 형제 축에서 미리 낸
  길이고, `604` AC-3 이 *"583 이 없었다면 이 티켓이 부팅을 영구히 못 끝내게 만들었을 것"*
  이라고 적은 그 자리다. 🔵 두 상수는 **환경변수로 덮을 수 있게** 돼 있으나
  (`DEMO_SURFACE_*`) 기본값을 안 고치면 데모 호스트가 그대로 문다.
- Traefik 의 손관리 alias 목록에서 **`web.fan-platform.${DEMO_DOMAIN:-local}`**
  (`infra/traefik/docker-compose.yml:93`) 을 뺀다 — 라우터가 사라지면 가드 **(i)** 가
  «고아 alias» 로 문다(`604` 가 실측). 🔵 같은 파일 **:84** 이 이미
  *"형제 `web.fan-platform` 은 단계 4"* 라고 이 티켓을 기다리고 있다.
- 론처/부팅 로그가 그 호스트를 안내하고 있으면 **거짓이 된다** — 「팬은 Vercel」로 바꾼다.
  🔵 론처(`infra/demo/aws/site/index.html`)의 `data-surface` 행이 `SURFACE_SRC` 이므로
  **두 벌이 아니라 한 벌**이다 — 거기를 고치면 부팅 판정이 따라온다(`demo-up.sh:488` 주석).

## AC-5 — 라이브 확인 (**다음 기동 창**)

- `web.fan-platform.<DEMO_DOMAIN>` → **404**
- 🔴 **상태코드만으로 판정하지 마라** — `docker ps -a` 로 **컨테이너 부재**를 같이 본다.
  `TASK-MONO-610` 창 #1 이 정확히 그 오독을 했고, `615` C1 이 두 축으로 고쳤다.
  🔵 **양성 대조군**: 억제 안 된 화면(`console`)이 **307** 이어야 한다.
- 🔴 이 칸은 **재굽기가 선행**이다(억제는 AMI 의 compose 에 들어가야 효력이 있고,
  그보다 오래된 컨테이너는 `--remove-orphans` 로도 안 지워진다). **이 티켓만을 위해 창을
  열지 마라** — 번들에 묶어라.
- 🔴 그리고 그 창의 매니페스트에 이 칸을 **미리 적어라**(`TASK-MONO-616` 의 교훈).

## AC-6 — 이 구현이 **안 고치는 것**을 적는다

소유자 대기(팬 Vercel env) · 억제의 런타임 판정(`617`) · console(단계 3) · **(f) 벽**.

---

# Related Specs

- `TASK-MONO-586` § CORRECTION (2026-09-02) — 이 일의 원문 세 줄 · § CORRECTION (2026-09-03) 라이브 축 PASS
- `TASK-MONO-612` § AC-3 — 이 일을 미리 센 티켓 · § (x2) 극성
- `TASK-MONO-604` — **형제 판(단계 2 의 억제 몫)**. AC-1·AC-2 의 형태를 그대로 쓴다
- `TASK-MONO-583` — 부팅 판정이 먼저 길을 낸 선례
- `TASK-MONO-616` § AC-1 칸2 · § AC-3 — 선행 충족의 실측과 이 티켓의 발의 근거
- `TASK-MONO-617` — 억제의 런타임 판정(모집단이 이 티켓의 선언을 포함해야 한다)
- [`ADR-MONO-067`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) § 단계 4
- `infra/demo/demo.env:276-285` — 두 도메인의 극성이 적힌 자리

# Related Contracts

없음.

# Edge Cases

- 🔴 **팬은 로그인 뒤에야 화면이 열린다** — 익명 도달 페이지가 `/login` 하나뿐이라, AC-0 ①
  을 익명으로 재면 **항상 「데이터 없음」**이 나온다. 세션을 들고 재라.
- 🔴 **`DEMO_PAYMENT_MOCK` 는 한 값, 두 프런트**(`demo.env:279`). 팬만 보고 값을 바꾸면
  ecommerce 쪽 (x) 가 문다.
- 🔵 **로컬은 hosts 파일로 `web.fan-platform.local` 을 해소**하므로 alias 제거의 영향이 없다
  (형제 축에서 확인된 성질). 🔴 그래도 **다시 확인**하라 — 상속하지 마라.
- 🔴 억제 뒤 시드가 **없는 표면**을 가리키는 `redirect_uri` 를 매 부팅 등록할 수 있다
  (`604` 가 ecommerce 에서 «무해하지만 이름은 적는다» 로 남긴 것). 팬 판을 확인하고 적어라.

# Failure Scenarios

| 상황 | 잘못된 처리 | 옳은 처리 |
|---|---|---|
| 「로그인이 되니 Vercel 판은 산다」 | AC-0 ① 을 건너뛴다 | 🔴 **화면이 데이터를 내는지**를 따로 재라 — 200 이 에러 문구일 수 있다 |
| (x) 의 문구를 (x2) 로 복사 | `demo-pg` 를 적어 **정반대를 단언** | 🔴 팬은 `portone` **OFF** 가 mock 조건이다 |
| 부팅 지문 `2/2` 를 안 고침 | 부팅이 **영구 실패** | 🔴 억제와 **같은 PR** 에서 하한·판정을 고쳐라 |
| `web.fan-platform` 404 만 보고 통과 | 되살아난 컨테이너의 404 와 구별 불가 | 🔴 **`docker ps -a` 로 부재**를 같이 봐라 + 양성 대조군 |
| alias 를 남겨 둠 | 가드 (i) 가 «고아 alias» 로 문다 | 🔴 라우터와 alias 를 **같이** 지운다 |
| 이 티켓만을 위해 창을 연다 | 예산 낭비 | 🔴 **재굽기 번들에 묶고**, 그 창의 매니페스트에 미리 적어라 |
