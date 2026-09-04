# Task ID

TASK-MONO-604

# Title

데모 호스트가 **Vercel 로 옮겨간 스토어를 계속 서빙**한다 — 그런데 그 compose 는 로컬·CI 도 쓴다

# Status

done

# Owner

monorepo

# Task Tags

- adr
- demo
- ci

---

# 배경 — `TASK-MONO-581` ⑤ 에서 분리됐다 (2026-08-29)

`ADR-MONO-067` 단계 2 가 web-store 를 Vercel 로 옮겼고, `TASK-MONO-583` 이 론처 링크를,
`TASK-MONO-603` 이 가시성을 넘겼다. **그런데 데모 호스트는 여전히 자기 사본을 서빙한다.**

실측 (2026-08-29T16:11Z, 데모 running):

| | |
|---|---|
| `http://web.ecommerce.3-38-176-240.sslip.io/` | **200** — 데모 호스트 사본 |
| `https://store.hubwang.com/products` | **200** — Vercel 판, 시드 상품 8개 |

⇒ **둘 다 살아 있다.** 방문자 경로는 Vercel 로 갔으므로 데모 사본은 이제 **아무도 안 보는
40여 개 컨테이너 중 하나**이고, 32GB 인스턴스에서 그 메모리는 공짜가 아니다
(`TASK-MONO-552` AC-0: 정상 상태 31.5 GiB 중 **약 29 GiB 사용 · MemAvailable 2.4 GiB**).

## 🔴 581 이 이 일의 크기를 과소평가했다

581 의 표는 ⑤ 를 *"재굽기 ✅ 기동 ✅"* 로 적었다 — 마치 재굽기 창 안에서 끝나는 일처럼.
**아니다.**

```
projects/ecommerce-microservices-platform/docker-compose.yml:1218   web-store:
```

그 파일은 **데모 전용이 아니다.** 같은 파일을 쓰는 다른 소비자:

| 소비자 | 근거 |
|---|---|
| 로컬 스택 | `docs/guides/interview-demo-walkthrough.md:63` — `http://web.ecommerce.local` 을 방문 경로로 적는다 |
| CI | 잡 `Frontend E2E smoke (web-store + fan-platform-web + console-web, Playwright)` |

⇒ **거기서 서비스를 지우면 데모만이 아니라 로컬 워크스루와 CI e2e 가 같이 죽는다.**

🔴 그리고 **라이브 `docker rm` 은 답이 아니다** — 다음 부팅에 AMI 안의 compose 가 다시 만든다.
이 일의 산출물은 **영속적인 선언**이어야 한다.

---

# Goal

**데모 프로파일에서만** web-store 를 띄우지 않는다. 로컬과 CI 는 그대로 둔다.
그리고 그 억제가 **어디에 선언됐는지** 한 곳에서 읽히게 한다.

---

# Scope

**In:**

- 데모 전용 억제 기전 (구현 판단 — 후보는 § Edge Cases)
- `infra/demo/verify-demo-wrapper.sh` — 억제가 **실제로 걸리는지** 실행 대조
- 억제 후 `web.ecommerce.<DEMO_DOMAIN>` 이 404 가 되는 것을 **정상으로** 문서화

**Out:**

- `projects/ecommerce-microservices-platform/docker-compose.yml` 에서 서비스 **삭제** — 금지.
  로컬·CI 가 죽는다.
- 론처 링크·가시성 — `TASK-MONO-583` · `TASK-MONO-603` 에서 끝났다
- console·fan 의 이관 — 단계 3·4

---

# Acceptance Criteria

## AC-0 — 전제 재측정 (**착수 전**)

1. `https://store.hubwang.com/products` 가 **2xx 이고 실데이터를 렌더**하는지 확인한다.
   🔴 **이것이 선행이다** — Vercel 판이 죽어 있는데 데모 사본을 끄면 방문자는 **어느 쪽에서도**
   스토어를 못 본다. (`TASK-MONO-581` § AC-4 정정이 이름 댄 술어이고, 조건은 「580 머지」가
   아니라 **「Vercel 판이 실제로 서빙된다」**이다.)
2. 그 compose 를 읽는 소비자를 **다시 전수한다** — 이 티켓이 적은 둘(로컬 워크스루 · CI e2e)이
   여전히 전부인지. 🔴 하나라도 늘었으면 억제 기전의 선택이 달라진다.

## AC-1 — 억제는 **데모 프로파일에서만** 건다

- 로컬 `docker compose up` 은 **영향 없음**
- CI 의 `Frontend E2E smoke` 는 **영향 없음**
- 🔴 억제가 어디에 선언됐는지 **한 곳**에서 읽혀야 한다. 두 벌이면 하나만 고쳐진다.

## AC-2 — 가드가 **실행해서** 대조한다

- 데모 렌더에 `web-store` 가 **없다**는 것을 `docker compose config` 로 확인한다
  (파일 grep 이 아니라 **렌더 결과**다 — 오버라이드는 병합 후에야 판정된다).
- 🔴 **대조군**: 같은 검사를 **로컬 프로파일**에 걸면 `web-store` 가 **있어야** 한다.
  한쪽만 보면 「전부 껐다」와 구별되지 않는다.
- 🔴 **bite**: 억제를 지우면 가드가 문다.

## AC-3 — 부팅 판정과의 정합

`TASK-MONO-583` 이 `demo-up.sh` 를 고쳐 **데모 호스트 행만** 찌르게 했고, `web.ecommerce` 는
이미 `data-served="vercel"` 이라 **찌르지 않는다**(2026-08-29 실기동 확인:
`[demo] ✔ HTTP 표면 2/2: console=307 web.fan-platform=307`).

🔵 **그러므로 이 티켓은 부팅 판정을 안 건드린다** — 583 이 먼저 가서 길을 냈다.
🔴 **반대로: 583 이 없었다면 이 티켓이 부팅을 영구히 못 끝내게 만들었을 것이다.**
그 순서를 기록해 둔다(다음 사람이 583 을 «불필요한 선행» 으로 읽지 않도록).

## AC-4 — 라이브 확인 (**다음 기동 창**)

- `web.ecommerce.<DEMO_DOMAIN>` → **404** (전환 완료의 신호)
- 부팅이 정상 종료하고 `HTTP 표면 2/2` 가 그대로인지
- 🔵 이 칸은 **재굽기가 선행**이다(억제는 AMI 의 compose 에 들어가야 효력이 있다).
  ⇒ 다음 재굽기 번들에 묶어라. **이 티켓만을 위해 창을 열지 마라.**

---

---

# 구현 결과 (2026-09-01 UTC)

## 🔴🔴 AC-0 ① — **오늘은 통과할 수 없다. 그리고 그것이 이 AC 의 위치를 바꾼다**

| 잰 것 | 값 |
|---|---|
| `https://store.hubwang.com/products` | **200 / 45,849 B** ✅ |
| 그 200 이 **실데이터인가** | 🔴 **아니다** — 본문 텍스트가 *"데모 서버가 꺼져 있어 상품 데이터를 불러올 수 없습니다"* + *"상품 목록을 불러오는 데 실패했습니다"*. 상품 링크 **0개**, 가격 문자열 **0건** |
| 음성 대조군 `/products-does-not-exist-604` | `404 / 11,067 B` ⇒ 위 200 은 **catch-all 이 아니라 실제 라우트**다 |
| 원인 (추측 아님) | 컨트롤 플레인 `GET /status` = **`{"state":"stopped","ip":null,"used_minutes":0,"budget_minutes":600}`** |

🔵 **추출기를 먼저 의심했다** — 링크 0개는 내 술어가 틀렸을 때도 나온다. 그래서 HTML
주석·`<script>`·`<style>` 을 지운 뒤 본문 텍스트를 **눈으로** 읽었고, 거기 있던 것은
빈 목록이 아니라 **에러 문구**였다. [[feedback_my_verification_predicate_is_the_likeliest_defect]]

### ⇒ 🔴 AC-0 ①은 **데모가 꺼져 있는 동안 원리적으로 참이 될 수 없다**

Vercel 판은 상품 데이터를 **런타임에 데모 백엔드에서** 가져온다
(`apps/web-store/src/shared/config/demo-backend.ts` — `/status` 가 `running` 이 아니면
주소를 만들지 않는다. 그 설계는 옳다: 옛 IP 로 붙는 것이 가장 나쁘다).

⇒ **「Vercel 판이 서빙된다」와 「데모가 켜져 있다」는 독립이 아니다.** 그런데 이 AC 는
«착수 전» 에 놓여 있다 — **판정 시점과 착수 시점이 구조적으로 어긋나 있다.**

🔵 **그래도 이 AC 가 지키려는 것은 살아 있다**: *"Vercel 판이 죽은 채로 억제하면 방문자가
어느 쪽에서도 스토어를 못 본다."* 그 위험이 실현되는 시점은 **오늘이 아니라 다음 기동**이다
— 억제는 AMI 의 compose 에 들어가야 효력이 있고(AC-4 가 이미 그렇게 적었다), 데모가 꺼져
있는 지금은 **데모 사본도 어차피 안 보인다.**

### ⇒ 정정: **AC-0 ①의 판정 시점은 AC-4 와 같은 창이다**

🔴 **다음 재굽기·기동 창에서, 억제된 AMI 로 부팅한 직후 AC-0 ①을 먼저 재라.**
거기서 거짓이면 그 창 안에서 이 오버라이드를 걷어라 —
`infra/demo/ecommerce-vercel.override.yml` 한 줄이고 되돌리기가 싸다.
[[feedback_measure_the_plans_premise_before_starting_the_phase]]

---

## 🔴 AC-0 ② — 모집단을 다시 셌다. **티켓의 둘 중 하나가 틀렸고, 하나가 빠졌다**

이 티켓은 소비자를 *"로컬 워크스루 · CI e2e"* 둘로 적었다. 전수해 보니:

| # | 소비자 | compose 를 쓰나 | **web-store 를 띄우나** |
|---|---|---|---|
| 1 | 데모 (`infra/demo/projects.sh` 의 `[ecommerce]` 체인) | ✅ | ✅ **이 티켓의 대상** |
| 2 | 로컬 (`npm run ecommerce:up` · 워크스루 §2) | ✅ | ✅ |
| 3 | CI `Frontend E2E smoke` | 🔴 **아니다** | 🔴 **아니다** |
| 4 | `nightly-e2e.yml` full-stack | ✅ | 🔵 **아니다** — 서비스를 이름으로 열거하고 `web-store` 는 그 목록에 없다(Next 는 `pnpm start` 로 별도 기동) |
| 5 | `scripts/check-cross-project-topic-relay.sh` | 🔵 파일을 **읽기만** 한다(kafka listeners) | ❌ |

- **③ 이 틀렸다**: 그 잡의 주석이 *"No backend stack needed"* 라고 적고 있고, 스텝은
  `pnpm e2e:smoke` 뿐이다 — **compose 를 아예 안 쓴다.**
- **④ 가 빠졌다**: `nightly-e2e.yml:534` 가 `docker compose -f docker-compose.yml
  -f docker-compose.ci.yml -f docker-compose.iam-fullstack.yml up …` 을 돈다.
  🔵 다행히 `web-store` 를 열거하지 않아 이 변경과 무관하다 — **그러나 그것은 세어 본
  뒤에 할 수 있는 말이다.**

⇒ **`web-store` 를 실제로 띄우는 소비자는 둘, 「데모」와 「로컬」이다. CI 는 어느 잡도 안 띄운다.**
[[feedback_recount_population_dont_inherit_scope]]

---

## ✅ AC-1 — 억제는 **데모 프로파일에서만**. 기전과 술어를 **같이** 골랐다

**채택: 데모 오버라이드가 `profiles:` 를 «추가» 한다** —
`infra/demo/ecommerce-vercel.override.yml` (신규) + `projects.sh` 의 `[ecommerce]` 한 줄.

실측 (데몬 없이 `docker compose config` 만으로 — 렌더는 클라이언트 측이다):

| 렌더 | 서비스 수 | `web-store` |
|---|---|---|
| 데모 체인 (base + relay) | **34** | 있음 |
| 데모 체인 + 이 오버라이드 | **33** | 🔵 **없음** |
| 로컬 (base 단독) — **대조군** | 34 | 있음 |

🔴 **다른 후보를 왜 버렸는지도 파일 헤더에 적었다** — Edge Cases 의 셋 다 술어와 어긋난다:
`deploy.replicas: 0` 은 **렌더에 남고**, `--scale` 은 **렌더에 안 보이며**, base 의
`profiles:` 는 **로컬 기본 동작을 바꾼다**. 기전과 술어를 따로 고르면 가드가 영원히 공허하다.

🔵 **한 곳**: 억제 선언은 그 파일 한 줄, 체인 등록은 `projects.sh` 한 줄. 그 둘의 효력을
가드 (z19)가 **렌더로** 확인한다.

---

## ✅ AC-2 — 가드 (z19). **대조군과 bite 를 같이 넣었다**

`infra/demo/verify-demo-wrapper.sh` 에 정적 가드 (z19) 신설. 다섯 칸:

| 칸 | 무엇 |
|---|---|
| 체인 등록 | 억제 파일이 `[ecommerce]` 체인에 있는가 (파일만 있고 체인에 없으면 **효력 0 인데 조용하다**) |
| **주입 확인** | 억제 파일을 **뺀** 렌더에는 `web-store` 가 **있어야** 한다 — 없으면 이 가드는 아무것도 증명 못 한다 |
| 판정 | 실제 체인 렌더에 `web-store` 가 **없어야** 한다 |
| **대조군** | base 단독 렌더에는 **있어야** 한다 — 없으면 로컬·CI 를 깬 것이다 |
| 범위 | 앞의 두 렌더 차이가 **정확히 1개(web-store)** 여야 한다 |

🔴 그리고 **바닥(20 서비스)** 을 깔았다 — 렌더가 깨지면 목록이 통째로 비고, 그 0행은
«억제됨» 과 **구별되지 않는다**. [[env_empty_detector_output_is_not_absence]]
🔵 유령 참조(`depends_on: web-store`) 0건도 같이 본다.

### bite — 두 번 물렸다

| 주입 | 결과 |
|---|---|
| 체인에서 억제 파일 제거 | rc=1 — 🔵 다만 **가드 (i) 가 먼저** 물었다(`Traefik alias 가 없는 라우터`). (i)와 (z19)가 이 축에서 **겹친다** |
| 604 를 **통째로** 되돌림(억제 stanza 제거 + alias 복원) | rc=1 — **(z19)가 물었다**: *"데모 렌더에 web-store 가 여전히 있습니다"* ✅ |

🔵 주입 자체를 매번 먼저 단언했다. 🔴 그리고 **첫 bite 시도는 내 단언이 틀려 중단됐다**
(`'profiles:' not in n` — 파일 **헤더 주석**에 그 문자열이 있었다). 판별자가 자기 설명
문구에 걸린 것이고, 단언이 없었으면 «주입 안 된 초록» 을 bite 실패로 오독했을 것이다.
[[feedback_a_discriminator_can_match_its_own_documentation]]

---

## 🔴🔴 예상 못 한 것 둘 — **다른 가드 두 개가 물었고, 둘 다 옳았다**

### ① 가드 (i) — 라우터를 지우면 **alias 가 고아가 된다**

`infra/traefik/docker-compose.yml` 의 손으로 관리하는 alias 목록에
`web.ecommerce.${DEMO_DOMAIN:-local}` 이 있었다. 억제로 라우터가 사라지자 (i)가
*"라우터가 없는 고아 alias"* 로 물었다.

⇒ **alias 를 지웠다.** 그 목록의 존재 이유가 *"AWS 인스턴스는 자기 공인 IP 로 하이핀할 수
없다"* 인 **데모 기전**이고, 데모에 그 이름의 라우터가 없어졌으므로 짝이 같이 가는 것이 맞다.
🔵 로컬은 hosts 파일로 `web.ecommerce.local` 을 해소하므로 영향이 없다.
🔴 형제 `web.fan-platform` 은 단계 4(`TASK-MONO-586`)가 같은 자리를 밟는다 — 주석에 적었다.

🔵 **부수 정정**: `demo-up.sh` 가 부팅 로그에 `web.ecommerce.${DEMO_DOMAIN}` 을 안내
호스트로 찍고 있었다. 이제 거짓이라 목록에서 빼고, 대신 *"스토어는 Vercel"* 한 줄을 넣었다.

### ② 가드 (x) — **결제 mock 정합의 프런트 절반이 저장소 밖으로 이사 갔다**

(x)는 `payment-service`(`demo-pg`) ↔ `web-store`(`DEMO_PAYMENT_MOCK=1`) 의 **동의**를
잰다. 억제로 프런트 절반이 렌더에서 사라지자 (x)가 물었다.

🔴🔴 **그리고 그 조합은 지금 어긋나 있을 가능성이 높다.** `kanggle-store` 프로덕션 env 에
`DEMO_PAYMENT_MOCK` 이 **없고**(2026-09-01 키 전수), 코드는
`route.ts:22` 에서 `=== '1'` 로 읽는다. 백엔드는 `demo.env:276` 이 `demo-pg` 다.
(x)의 주석이 그 조합의 증상을 이미 적어 뒀다 — *"프런트가 더미 키로 Toss SDK 를 로드하다
실패 배너"*.

🔴 **그러나 이것은 선언 기반 추론이지 라이브 측정이 아니다** —
`/api/store-config` 는 **`307 → /login`**(인증 게이트)이라 익명으로는 못 읽는다.
그 간극을 섞지 않으려고 **`TASK-MONO-612` 를 기안했다**(AC-0 이 그 측정부터 한다).

**(x) 를 어떻게 고쳤나** — 공허 통과도, 영구 빨강도 만들지 않는다:

- `web-store` 가 렌더에 없으면, 그 부재가 **선언된 억제 때문인지** 먼저 확인한다
  (체인에 오버라이드가 있는가). 아니면 FAIL — «누가 지웠는지 모르는» 상태를 통과시키지 않는다.
- 맞으면 **백엔드 절반만** 재고, `ok` 줄에 *"이 축은 CI 에서 미집행이다. 소유 티켓:
  TASK-MONO-612"* 를 **매 실행마다** 찍는다.

🔴 그냥 통과시켰으면 빈 문자열 둘이 «둘 다 꺼짐» 으로 합격했을 것이고, 그냥 FAIL 로 두면
소유자가 Vercel env 를 넣기 전까지 main 이 영구 빨강이라 **가드가 꺼졌을 것이다.**
[[feedback_a_non_vacuity_floor_under_a_draining_population]]

---

## ✅ AC-3 — 부팅 판정과의 정합 (확인만)

`demo-up.sh:366-371` 이 이미 *"하한 ①: 론처가 약속하는 화면의 총 수 = 3 … 위 3 에서
web.ecommerce 를 뺀 값 — TASK-MONO-583 이 그 행을 Vercel 로"* 라고 적고 있다.
⇒ **부팅 판정은 안 건드린다.** 583 이 먼저 가서 길을 냈다는 이 티켓의 서술이 소스에서 확인됐다.

## 🙋 AC-4 — **남는다** (다음 재굽기·기동 창)

- `web.ecommerce.<DEMO_DOMAIN>` → **404**
- 부팅 정상 종료 · `HTTP 표면 2/2` 유지
- 🔴 **그리고 그 창에서 AC-0 ①을 먼저 재라** (위 정정)
- 🔵 **이 티켓만을 위해 창을 열지 마라** — 번들에 묶어라.

## 🔵 범위 밖으로 남기는 것 — 이름은 대 둔다

`infra/demo/seed/seed-ecommerce.sh:70` 이 매 부팅마다
`http://web.ecommerce.${DEMO_DOMAIN}/api/auth/callback/iam` 를 IdP 에 시드한다.
억제 후 그 콜백은 **없는 표면**을 가리킨다. 🔵 무해하다(쓰이지 않는 `redirect_uri`),
그리고 매 부팅에 도메인 파생으로 다시 써지므로 «죽은 IP» 부류도 아니다.
🔴 그러나 **Vercel 판이 필요로 하는 콜백**(`https://store.hubwang.com/…`)은 별개 축이고,
`web-store/VERCEL.md` 가 *"여기서 즉흥으로 정하지 마라"* 라고 못박은 자리다 —
`ADR-MONO-067` § D4 / `TASK-MONO-610` 의 몫이다. **여기서 손대지 않는다.**

## 검증

| 게이트 | 결과 |
|---|---|
| `bash infra/demo/verify-demo-wrapper.sh` (정적 전체) | **rc=0** — (z19) 포함 |
| `bash -n` (verify · demo-up · projects.sh) | 통과 |
| bite ×2 | rc=1 / rc=1 (주입 확인 후) |

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) § 단계 2
- `TASK-MONO-581` § ⑤ — 이 티켓이 그 분리분이다
- `TASK-MONO-583` · `TASK-MONO-603` — 링크와 가시성. 이미 끝났다

# Related Contracts

없음.

---

# Edge Cases

- **억제 기전의 후보**: ⑴ `infra/demo/*.override.yml` 에서 `web-store` 를
  `deploy.replicas: 0`(compose v2 에서 무시될 수 있다 — 확인 필요) ⑵ `demo-up.sh` 가
  `--scale web-store=0` 을 붙인다 ⑶ 기본 compose 에 `profiles:` 를 달고 로컬·CI 만 그
  프로파일을 켠다. 🔴 ⑶ 은 **로컬 기본 동작을 바꾼다** — walkthrough 를 같이 고쳐야 한다.
- 🔴 **`--scale` 은 `demo-up.sh` 안에만 있으면 `docker compose config` 로 안 보인다.**
  AC-2 의 술어가 렌더 결과를 본다면 ⑵ 는 그 술어로 판정 불가다 — **기전과 술어를 같이 고르라.**
- 🔴 web-store 컨테이너가 사라지면 그 도메인의 **헬스 스냅샷 구성이 바뀐다.** 론처의
  `data-domain="ecommerce"` 행은 이제 `data-served="vercel"` 이라 헬스를 안 보지만,
  `demo-status.sh` 의 도메인 판정(`partial` 계산)은 여전히 그 스택을 센다 — **`partial` 이
  덜 뜨는지 더 뜨는지** 확인하라.
- ecommerce 스택의 다른 서비스가 web-store 에 `depends_on` 을 걸고 있으면 억제가 그 서비스를
  같이 막는다. **렌더로 확인하라.**

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 기본 compose 에서 삭제 | 로컬 워크스루·CI e2e 가 죽는다 | Scope Out · AC-1 |
| 라이브 `docker rm` 으로 끝냄 | 다음 부팅에 되살아난다 | Goal — 영속 선언 |
| 억제만 하고 가드 없음 | 다음 사람이 되돌려도 **아무도 모른다** | AC-2 |
| 대조군 없이 「없음」만 확인 | 「전부 껐다」와 구별 불가 | AC-2 둘째 칸 |
| Vercel 판이 죽은 채로 억제 | 방문자가 **어느 쪽에서도** 못 본다 | AC-0 ① |

---

# 🔴🔴 CORRECTION (2026-09-02 UTC) — AC-4 는 **미충족**이다. 그리고 404 가 통과처럼 보인다

`TASK-MONO-610` 기동 창에서 실측했다. 이 티켓은 `review/` 라 표를 고칠 수 없으므로
여기에 순수 추가한다. **AC-4 를 「통과」로 읽지 마라.**

## 무엇을 봤나

| 시점 | `web.ecommerce.<DEMO_DOMAIN>` | 읽는 법 |
|---|---|---|
| 창 시작(AMI 판본, 억제 파일 없음) | **200 / 64,979 B** | 데모가 사본을 서빙 중 |
| 저장소 갱신 + 스택 재기동 뒤 | **200 / 64,979 B** | 🔴 억제 선언이 들어왔는데도 그대로 |
| EC2 부팅 뒤(새 도메인) | **404 / 19 B** | 🔴🔴 **통과가 아니다 — 아래** |

## 🔴🔴 그 404 는 억제의 결과가 아니다

부팅 뒤 컨테이너는 **살아 있다**:

```
ecommerce-web-store | Up (healthy) | restart=unless-stopped
created = 13:04:34   (억제가 호스트에 오기 «전» 부팅)
traefik.http.routers.ecommerce-web.rule=Host(`web.ecommerce.43-202-166-3.sslip.io`)  ← 어제 도메인
[label-drift] ecommerce-web-store (-p ecommerce) → 43-202-166-3.sslip.io
```

⇒ 새 도메인에서 404 가 나는 이유는 **되살아난 컨테이너가 낡은 호스트명 라벨을 들고 있어서**다.
AC-4 가 «전환 완료의 신호» 로 기대한 404 와 **모양이 같고 기전이 다르다.**
🔵 `check-label-drift.sh` 가 부팅 로그에서 이것을 정확히 잡아 줬다.

## 🔴 왜 억제가 발효되지 않았나 — 기전

`profiles:` 로 가려진 서비스는 compose 에게 **«고아» 가 아니라 «비활성»** 이다. 따라서
`docker compose down --remove-orphans` 는 그 컨테이너를 **내리지도 지우지도 않는다.**
⇒ **억제 선언보다 오래된 컨테이너가 존재하는 호스트에서는 재기동으로 발효되지 않고**,
`restart=unless-stopped` 때문에 EC2 부팅에서도 되살아난다.

⇒ AC-4 의 *"🔵 이 칸은 재굽기가 선행이다"* 는 **맞지만 사유가 얕았다.** 파일이 AMI 에
없다는 것뿐 아니라, **이미 만들어진 컨테이너가 profile 게이트 밖에 있다**는 것이 본질이다.

## ✅ 같이 잰 것 — AC-4 의 둘째 기준은 그대로다

```
[demo] ✔ HTTP 표면 2/2: console=307 web.fan-platform=307     (부팅 전·후 동일)
```

## ⇒ 남은 일

1. 재굽기(그 AMI 에는 이 컨테이너가 애초에 없다), **또는**
2. 억제가 발효될 때 **기존 컨테이너를 명시적으로 제거**하는 단계(예: `--remove-orphans`
   로는 부족하므로 `docker rm -f` 를 억제 대상에 한정해 수행), **또는**
3. 억제 기전을 «렌더에서 빼기» 가 아니라 **«내려가는 것이 관측되는» 기전**으로 바꾼다.

🔴 어느 쪽이든 **가드 (z19) 는 렌더만 본다** ⇒ 렌더는 초록인데 컨테이너는 도는 상태를
(z19)가 판정하지 못한다. 그 공백이 이 CORRECTION 의 존재 이유다.
[[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

---

# 🟢 CORRECTION (2026-09-03 UTC #2) — **AC-0 ① 이 닫혔다.** `TASK-MONO-616` 기동 창 #3

이 티켓이 스스로 적은 정정 — *"AC-0 ①의 판정 시점은 AC-4 와 같은 창이다"* — 을
`TASK-MONO-616` 이 매니페스트 **칸 1** 로 들고 창에서 쟀다. `review/` 라 표를 못 고치므로
순수 추가한다.

## 판정 — **PASS**. 상태코드가 아니라 **세 술어가 전부 뒤집혔다**

| 술어 | 창 전 (데모 OFF, 09-03) | 창 안 (데모 ON, 09-03) |
|---|---|---|
| `https://store.hubwang.com/products` | 200 / 45,849 B | **200** |
| *"데모 서버가 꺼져 있어…"* 출현 | **1건** | 🟢 **0건** |
| 상품 링크 `href="/products/…"` | **0개** | 🟢 **8개** (`b0000000-…-0001` ~ `-0008`) |
| 가격 문자열 | **0건** | 🟢 **8건** (`59,000 원` · `1,590,000 원` · `3,490,000 원` …) |
| 음성 대조군 `/products-does-not-exist-604` | 404 / 11,067 B | **404** ⇒ 200 은 catch-all 이 아니다 |

⇒ **Vercel 판은 데모가 켜져 있는 동안 실데이터를 낸다.** 이 AC 가 지키려던 위험
(*"Vercel 판이 죽은 채로 억제하면 방문자가 어느 쪽에서도 스토어를 못 본다"*)은
**실현되지 않았다.**

🔵 같은 창에서 `web.ecommerce.54-117-6-10.sslip.io` 는 **404** 였다 — 그리고 그 404 는
console·`web.fan-platform` 이 **307 로 올라온 시점에도** 404 였다. 부팅 초기에는 셋 다
404 라 판별력이 없고, **형제가 307 이 된 순간의 404** 만이 억제의 증거다. `TASK-MONO-615`
가 닫은 C1(컨테이너 부재)과 같은 방향이다.

## 🔴🔴 그런데 이 판정은 **거의 반대로 랜딩될 뻔했다** — 내 술어가 틀렸다

첫 실행에서 가격이 **0건**으로 나왔고 하네스는 **FAIL** 을 찍었다. 원인은 데이터가 아니라
정규식이다:

```
추출된 본문:  … 슬림핏 데님 청바지  59,000 원 …      ← 「원」 앞에 공백
내 술어:      [0-9][0-9,]*원                          ← 공백을 허용하지 않음
```

공백은 사이트가 넣은 것이 아니라 **내 추출기가** 넣었다(가격과 단위가 다른 태그에 있어
태그를 공백으로 치환하면 그 자리에 생긴다). 술어를 `[0-9][0-9,]*[[:space:]]*원` 으로
고치자 **8건**이 되었고, **같은 술어를 창 전 기준선 파일에 걸면 여전히 0** 이다
(⇒ 느슨해진 것이 아니라 판별력을 유지한 채 고쳐졌다).

🔴 **실패의 방향이 나빴다**: 틀린 술어가 만든 것은 「모르겠다」가 아니라 **「AC-0 ① 거짓」**
이었고, 이 티켓의 정정은 그 경우 *"그 창 안에서 오버라이드를 걷어라"* 를 처방한다.
즉 **내 정규식 하나가 억제 되돌리기를 발동시킬 뻔했다.**
[[feedback_my_verification_predicate_is_the_likeliest_defect]]

## 🔴 이 CORRECTION 이 닫지 **않는** 것

**(z19) 가드 공백** — 09-02 CORRECTION 이 적은 *"렌더는 초록인데 컨테이너는 도는 상태를
(z19)가 판정하지 못한다"* 는 **그대로 열려 있다.** 이 창은 측정 창이었고 코드 작업은
범위 밖이었다(`TASK-MONO-616` Scope). 🔴 **그래서 이 티켓은 `review/` 에 남는다** —
AC 는 전부 닫혔지만 그 공백에 주인이 없는 채로 `done/` 에 넣으면 아무도 다시 안 본다.
[[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

---

# 🟢 종료 CORRECTION (2026-09-04 UTC) — **`review/` 에 남긴 유일한 사유가 닫혔다**

이 티켓의 마지막 CORRECTION 은 *"AC 는 전부 닫혔지만 그 공백에 주인이 없는 채로 `done/` 에
넣으면 아무도 다시 안 본다"* 로 끝났다. 🔴 **그 공백 하나만이 close 를 막고 있었다** —
AC-0 ~ AC-4 는 이미 닫혀 있었다. 그 공백이 주인을 얻었고, 라이브에서 판정됐다.

| 09-03 이 적은 공백 | 주인 | 지금 |
|---|---|---|
| *"렌더는 초록인데 컨테이너는 도는 상태를 (z19)가 판정하지 못한다"* | `TASK-MONO-617` (impl PR `#3613`, 스쿼시 `9271aec32`) | 🟢 **닫혔다** |

## 무엇이 그것을 닫았나 — 선언이 아니라 **부팅 로그**

`617` 은 (z19) 를 고치는 대신 **부팅마다 도는 판정**(`demo-up.sh` 의 `post_up_call`)을
만들었다. 술어는 렌더가 아니라 **`docker ps -a` 의 런타임 결과**다. 기동 창 #4
(2026-09-04, `TASK-MONO-621` 칸 4)의 호스트 로그:

```
[suppressed] OK — 억제 대상 2개(검사 2건) 모두 컨테이너가 존재하지 않습니다
```

🔵 **「2개」에 ecommerce 가 들어 있다** — 이 티켓이 억제한 바로 그 대상이다. 즉 09-02
CORRECTION 이 관측한 «렌더에서 빠졌는데 컨테이너는 돌더라» 상태가 **이번엔 없다는 것이
실행으로 판정됐다.** 재굽기 #8(`ami-0fc6e4b7ac34c4ef5`)이 그 컨테이너가 애초에 없는
AMI 를 만들었고, `617` 의 판정이 그것을 **매 부팅 확인**한다.

🔴 **이 판정은 「가드가 초록이었다」가 아니라 「가드가 실행돼서 초록이었다」이다.** 그
구별이 이 티켓의 09-02 CORRECTION 이 요구한 전부였다.
[[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

## 🔴 이 종료가 주장하지 **않는** 것

- **소화율은 소급되지 않는다.** 창 #1~#3 의 미측정은 그때의 판정으로 남는다.
- **(z19) 자체는 여전히 렌더만 본다.** `617` 이 만든 것은 그 옆에 선 **두 번째 판정**이고,
  이 티켓이 요구한 것은 「그 상태를 누군가 판정한다」였지 「(z19)가 판정한다」가 아니었다.
