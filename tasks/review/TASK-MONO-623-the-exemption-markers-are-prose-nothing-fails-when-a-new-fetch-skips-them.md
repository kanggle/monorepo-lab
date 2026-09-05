# Task ID

TASK-MONO-623

# Title

`DEMO-URL-EXEMPT:` 마커는 **산문이다.** 새 `fetch(` 가 해석도 마커도 없이 들어와도 아무도 안 문다.

# Status

review

# Owner

monorepo

# Task Tags

- ci
- frontend
- demo

---

# Goal

**«백엔드로 나가는 fetch 는 런타임 해석을 지나거나, 왜 안 지나는지를 선언한다»** 를 가드가
재게 한다. 지금은 그 문장이 주석에만 있다.

---

# Context — `TASK-MONO-585` 가 만든 **선언된 공백**

585 는 console-web 의 `fetch(` **22곳**을 전수로 분류했다:

| 분류 | 건수 | 지금 무엇이 지키나 |
|---|---:|---|
| `resolveBackendUrl` 을 지난다 | 6 | — |
| 상대경로 리터럴(같은 오리진) | 4 | — |
| `// DEMO-URL-EXEMPT:` 마커로 사유 선언 | 12 | **아무것도 안 지킨다** |

🔴 **23번째 `fetch(` 가 `.local` 주소로 들어오면 CI 는 초록이다.** 증상은 조용하다 — 데모
배포에서 **그 화면만** 죽고 나머지가 멀쩡해 원인이 안 보인다(585 의 Edge Case 가 미리 적은 그것).

🔵 **585 가 이것을 «잊은» 것이 아니라 «범위 밖» 으로 판정했다.** 그 근거를 그대로 옮긴다:
보편 가드로 만들려면 형제 둘의 같은 자리에도 마커가 필요하고, 그것은 다른 프로젝트의 diff 다.

```
projects/fan-platform/web/fan-platform-web/src/shared/auth/auth-callbacks.ts:50
    await fetch(`${env.oidcIssuerUrl}/oauth2/token`, { … })
projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth-callbacks.ts:68
    await fetch(`${OIDC_ISSUER_URL}/oauth2/token`, { … })
```

둘 다 585 가 console 에서 `DEMO-URL-EXEMPT: oidc-issuer` 로 선언한 것과 **같은 축**이다
(`ADR-MONO-069` `C2` — 발급자는 고정된 이름이고 데모 IP 에서 파생되지 않는다).

## 🔴 이 티켓이 **안 하는 것** — 이미 있는 가드와 헷갈리지 마라

| 가드 | 재는 것 | 이 티켓과의 관계 |
|---|---|---|
| `check-client-graph-backend-origins.mjs` (585 신설) | **브라우저가** 백엔드 오리진 리터럴에 닿는가 | 다른 축이다. 그쪽은 «누가 아는가», 이쪽은 «어디로 나가는가» |
| `check-demo-resolver-copies.sh` | 앱이 해석기 **구현**을 갖는가 | 다른 축. 배선의 유무는 안 본다 |
| `scan-client-bundle-origins.mjs` | 구워진 산출물 | 빌드가 필요해 PR 마다 못 돈다 |

---

# Scope

**In:**

- 새 가드 하나 (자리는 repo-root `scripts/` — **project-agnostic 이어야 한다**, HARDSTOP-03)
- 형제 둘(`fan-platform-web` · `web-store`)에 같은 규약의 마커 추가
- `ci.yml` 잡 + 순수 positive 필터

**Out:**

- 마커의 **사유가 옳은지** 판정 — 그것은 사람이 읽는 것이다. 가드는 «선언이 있는가» 만 잰다.
- `infra/demo/auth-forwarder` 의 프록시 `fetch(target)` — 그 앱은 **모든** 요청을 전달하는
  것이 일이므로 이 술어의 모집단이 아닐 수 있다. AC-0 에서 판정한다.

---

# Acceptance Criteria

## AC-0 — 모집단을 **먼저 세고**, 술어가 형제에서도 성립하는지 확인한다

1. `@demo/backend-resolver` 를 선언한 앱을 전수로 세고(오늘 **4**), 각 앱의 `fetch(` 를
   전수로 센다. 🔴 `refetch()` 가 `fetch(` 에 걸린다 — 술어에 `\bfetch\(` 를 쓰고,
   **걸러진 건수를 함께 출력**하라(0이면 술어가 이상한 것이다).
2. 각 건을 세 갈래로 분류한다: 상대경로 리터럴 / 해석기 경유 / 미분류.
   **미분류 목록을 이 티켓에 적는다** — 그것이 형제에 달아야 할 마커의 정확한 수다.
3. 🔴 «해석기 경유» 를 어떤 **lexical** 술어로 판정할지 정하고, 그 술어가 형제 둘의 실제
   코드에서 성립하는지 확인한다(585 는 `resolveBackendUrl(` 또는 `resolved*` 식별자 +
   그 식별자가 `await resolveBackendUrl(` 로 할당됨, 두 형태를 썼다). 형제는 다른 이름
   (`resolveUpstreamBaseUrl`)을 쓴다 — **공유 패키지의 export 이름**으로 술어를 세우면
   project-agnostic 하게 성립한다.
4. AC-0 (2)의 미분류가 **0건**이면 이 티켓은 **가드만** 만들고 끝난다. 0이 아니면 (그리고
   그럴 것이다) 형제 diff 가 범위에 들어온다.

## AC-1 — 가드가 문다

미분류 `fetch(` 가 하나라도 있으면 RED. 메시지는 파일:줄과 **두 처방**(해석기를 지나게
하거나, 마커로 사유를 선언하거나)을 준다.

## AC-2 — 가드가 **안 물어야 할 때** 안 문다

- 상대경로 리터럴 → 통과
- 마커가 있는 자리 → 통과
- 🔴 **마커 문자열이 주석이 아닌 곳(예: 이 티켓 문서, 가드 자신의 설명)에서 매치되지 않는가** —
  판별자가 자기 설명 문구에 걸리는 함정을 이 저장소가 세 번 밟았다.

## AC-3 — bite

`--self-test` 로 합성 트리에서 **무는 칸과 안 무는 칸을 각각** 증명한다.
🔴 **주입이 실제로 됐는지 먼저 단언하라** — 585 에서 그것이 두 번 결함을 잡았다(상대 임포트가
하나도 해석 안 되는데 초록 · `'use server'` 를 몰라 형제를 거짓으로 빨갛게).
🔴 그리고 **착수 전 트리(= 형제에 마커를 달기 전)에 대고 돌려 RED 를 확인**하라. 그것이
「이 가드가 실제로 무언가를 잡았다」의 유일한 증거다.

## AC-4 — 모집단 하한

앱 수·`fetch(` 수에 하한을 둔다. 🔴 하한은 «위반 수» 가 아니라 **«계측기가 살아 있는가»** 에
건다 — 위반 0건은 정당하게 참일 수 있지만(그것이 목표다) 스캔 0건은 순회가 죽은 것이다.

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — D1/D2
- [`docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md) — § D6 = B2
- `TASK-MONO-585` § 부수 관찰 3 — 이 티켓이 그 문단에서 나왔다

# Related Contracts

없음.

---

# Edge Cases

- 🔴 `infra/demo/auth-forwarder` 는 **프록시**다. 모든 요청을 전달하는 것이 그 앱의 일이므로
  «해석기를 지나야 한다» 가 그대로 성립하지 않을 수 있다 — AC-0 에서 판정하고, 제외한다면
  **왜 제외하는지**를 가드 안에 적어라(제외 자체가 다음 사람에게는 구멍으로 보인다).
- 🔴 마커를 **줄 수로** 찾지 마라(«fetch 위 N줄»). 주석이 길어지면 조용히 못 찾는다.
  585 는 5줄짜리 마커를 달았다.
- 🔴 형제에 마커를 다는 diff 는 **다른 프로젝트**를 건드린다. 한 PR 로 묶어라
  (`CLAUDE.md` § Cross-Project Changes) — 나눠 내면 그 사이 main 이 빨갛다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 술어가 `refetch()` 를 센다 | 모집단이 부풀고 형제가 거짓 빨강 | AC-0 (1) — `\bfetch\(` + 걸러진 건수 출력 |
| 형제 마커를 나중에 | 그 사이 main RED | Edge Case 3 — 한 PR |
| 가드가 자기 문서에 걸린다 | 무한 RED, 그리고 문서를 지우는 습관 | AC-2 (3) |
| 「위반 0건」을 통과로 읽는다 | 순회가 죽어도 영원히 초록 | AC-4 |
| 착수 전 RED 를 확인 안 함 | **무는지 모르는 가드**를 랜딩 | AC-3 |

---

# ✅ 구현 (2026-09-05 UTC)

## AC-0 — 모집단을 먼저 셌고, **술어를 두 번 고쳤다**

### (1) 모집단 — 🔴 첫 계수가 **5** 를 냈다. 정답은 **4** 다

`package.json` 을 통째로 grep 하면 패키지 **자신의 `name` 필드**가 걸려
`infra/demo/backend-resolver` 가 «소비자» 로 들어온다. 소비 여부는 `dependencies` /
`devDependencies` 에만 있다.

```
소비자 앱 4:  infra/demo/auth-forwarder · web-store · fan-platform-web · console-web
자기 이름:    infra/demo/backend-resolver   ← 모집단 아님
```

🔵 이것이 왜 중요한가: 해석기 **구현** 자신에는 `fetch(base + '/status')` 가 있다. 모집단에
넣으면 그 자리가 «해석을 안 지나는 fetch» 로 잡히고, 그것을 고치려는 시도는 **구현이 자기를
해석하게** 만드는 무의미한 순환이 된다. self-test `(f)` 가 이 축을 고정한다.

### (1b) `\bfetch\(` — 걸러진 건수를 **함께** 낸다

AC-0 이 미리 경고한 그대로다. 실측: 느슨 `fetch(` **65** → 엄격 `\bfetch\(` **39**,
걸러진 것 **26**(`refetch(` · `prefetch(`). 🔴 이 수가 **0이면 술어가 이상한 것**이므로
가드가 매 실행에 함께 출력한다. (가드의 최종 계수는 주석 제거 후 기준으로 사이트 **32**,
걸러진 **33** — 두 수의 단위가 다르므로 **각각 이름을 붙여** 낸다.)

### (2) 세 갈래 분류 — 미분류 **2건**

| 분류 | 건수 |
|---|---:|
| 상대경로 리터럴(같은 오리진) | 9 |
| 파일 자격(해석기를 아는 파일) | 10 |
| `DEMO-URL-EXEMPT:` 마커 | 11 → (형제 둘 추가 후) **13** |
| **미분류** | **2** |

```
web-store/src/shared/auth/auth-callbacks.ts:68   `${OIDC_ISSUER_URL}/oauth2/token`
fan-platform-web/src/shared/auth/auth-callbacks.ts:50  `${env.oidcIssuerUrl}/oauth2/token`
```

⇒ AC-0 (4) 의 분기대로 **형제 diff 가 범위에 들어왔다**(한 PR, § Cross-Project Changes).

### (3) 🔴🔴 **lexical 술어가 형제 셋에서 전부 성립하지 않았다**

첫 술어는 585 가 console 에서 쓴 모양(*"fetch 인자에 해석기 이름이 보인다"*)이었다.
형제의 실제 코드를 열어 보니 **셋 다 그 모양이 아니다**:

| 앱 | fetch 인자 | 해석이 일어나는 자리 |
|---|---|---|
| auth-forwarder | `fetch(target, …)` | 같은 함수 **30줄 위** `resolver.resolveDemoBackend()` |
| web-store | `fetch(targetUrl, …)` | 헬퍼 `buildTargetUrl()` **안** |
| fan | `fetch(url, init)` | 헬퍼 `buildUrl()` **안** |

🔴 인자만 보는 술어는 **옳게 해석 중인 셋을 전부 거짓 빨강**으로 만든다. 그런 가드는 고칠
방법이 없어서 결국 꺼지고, **꺼진 가드는 없는 가드다.** ⇒ 술어를 둘로 나눴다:

- **(A) 파일 자격** — 절대 URL 로 나가는 `fetch(` 가 있는 파일은, 그 파일이 해석기의 export
  중 하나를 **참조**하거나 그 자리에 **마커**가 있어야 한다. 헬퍼를 거치든 30줄 위에서
  부르든 «이 파일은 해석을 안다» 는 참이다.
- **(B) env 직결 금지** — (A) 를 통과했더라도 인자가 env 를 **직접 보간**하면
  (`${env.X}` · `${X_URL}` · `process.env.X`) 그 자리는 lexical 로 해석됐거나 마커가 있어야
  한다. 🔴 (B) 가 없으면 (A) 는 «이미 축복받은 파일» 의 두 번째 미해석 fetch 를 못 잡고,
  **그것이 이 결함의 실제 도착 모양**이다 — 새 백엔드 주소는 언제나 env 에서 온다.

🔵 술어를 **공유 패키지의 export 이름**(`resolveDemoBackend` · `resolveDemoBackendState` ·
`resolveUpstreamBaseUrl` + console 래퍼 `resolveBackendUrl`)으로 세웠으므로 project-agnostic
하다(HARDSTOP-03) — 스크립트에 프로젝트 이름이 없다.

### (4) 판정

미분류가 2건이므로 **가드 + 형제 마커** 둘 다 이 PR 에 들어온다.

## AC-1 — 문다 ✅

착수 전(형제 마커를 달기 **전**) 실트리 실행: **rc=1**, 정확히 위 2건을 파일:줄과 함께 지목.
메시지는 **두 처방**(해석기를 지나게 / 마커로 사유 선언)을 준다.

## AC-2 — 안 물어야 할 때 안 문다 ✅

- 상대경로 리터럴 → 통과 (self-test `(c)`)
- 마커가 있는 자리 → 통과 (`(a2)`)
- 🔴 **주석 안의 `fetch(` 에 안 걸린다** → `(g)`. 주석을 먼저 걷어내되 **줄 번호는 보존**한다
  (공백으로 치환) — 안 그러면 지목하는 줄이 어긋난다.
- 🔴 이 문서와 가드 자신의 설명 문구에도 안 걸린다(같은 기전).

## AC-3 — bite ✅ · 그리고 **착수 전 RED 를 먼저 확인했다**

self-test **11칸**: 무는 5 · 일부러 안 무는 6.

```
--self-test  rc=0
실트리(형제 마커 前)  rc=1   미분류 2 — web-store · fan 의 auth-callbacks.ts
실트리(형제 마커 後)  rc=0   사이트 32 전부 분류 (상대 9 · 자격 10 · 마커 13 · 미분류 0)
```

### 🔴 그리고 Edge Case 하나를 내가 어겼다 — **마커를 줄 수로 찾고 있었다**

첫 판은 `MARKER_WINDOW = 12` 였다. 이 티켓의 Edge Case 가 *"마커를 줄 수로 찾지 마라
(«fetch 위 N줄»). 주석이 길어지면 조용히 못 찾는다"* 라고 **미리** 적어 둔 바로 그것이다.
🔴 그 실패는 방향이 고약하다: **잘 설명한 자리일수록 빨개진다**(이 PR 이 형제에 단 마커가
이미 7줄이다). ⇒ 술어를 **구조**로 바꿨다 — fetch 바로 위의 **연속 주석 블록**을 코드 줄이
나올 때까지 거슬러 올라간다. 블록이 100줄이어도 놓치지 않고, 블록 **밖**의 마커는 인정하지
않는다(엉뚱한 자리의 면제가 파일 전체의 통행증이 되는 것을 막는다).
self-test `(b2)`(31줄 블록) · `(b3)`(사이에 코드 줄) 이 양쪽을 고정한다.

## AC-4 — 모집단 하한 ✅

하한은 «위반 수» 가 아니라 **«계측기가 살아 있는가»** 에 걸었다: 소비자 앱 ≥ 3 ·
fetch 사이트 ≥ 15. 위반 0건은 정당하게 참일 수 있지만(그게 목표다) **앱 0 · 사이트 0 은
열거가 죽은 것**이고, 그 둘은 출력이 같다.

---

# 📌 Edge Case 판정 기록

| Edge Case | 판정 |
|---|---|
| `refetch()` 가 `fetch(` 에 걸린다 | ✅ `\bfetch\(` + **걸러진 건수 출력**(0이면 술어 이상) |
| `infra/demo/auth-forwarder` 는 프록시다 — 모집단인가 | ✅ **모집단이고, 면제가 필요 없다.** 그 파일이 `resolver.resolveDemoBackend()` 를 부르므로 (A) 파일 자격으로 통과한다 — 「프록시라서 예외」라는 **특례를 만들 필요가 없었다.** 🔵 AC-0 이 «판정하고, 제외한다면 이유를 가드 안에 적어라» 라고 했는데 **제외하지 않았으므로** 적을 것이 없다 |
| 마커를 줄 수로 찾지 마라 | ✅ 위 AC-3 § — 첫 판이 어겼고 구조로 고쳤다 |
| 형제 diff 는 한 PR 로 | ✅ 이 PR 하나 |

---

# ✅ 게이트 (각각 **독립 statement**)

```
node check-fetch-resolution.mjs                 rc=0
node check-fetch-resolution.mjs --self-test     rc=0   (11칸)
node check-client-graph-backend-origins.mjs     rc=0   (+ --self-test rc=0)
bash check-demo-resolver-copies.sh              rc=0
bash check-index-queue-drift.sh                 rc=0
bash check-task-id-collision.sh                 rc=0
bash check-walkthrough-ledger-drift.sh          rc=0
bash check-lifecycle-stage-dirs.sh              rc=0
bash check-public-domains.sh                    rc=0
bash check-message-backticks.sh                 rc=0
bash check-required-check-names.sh              rc=0
bash check-vercel-build-triggers.sh             rc=0
```

형제 프로젝트(소스를 건드렸다):

```
fan-platform      pnpm test  rc=0  (24 files / 159 tests) · tsc rc=0 · lint rc=0
web-store         tsc rc=0 · lint rc=0
```

## 🔴 web-store 의 vitest 는 **이 호스트에서 못 돈다** — CI 권위

```
ERR_PACKAGE_IMPORT_NOT_DEFINED: "#module-evaluator"   (vitest 4.1.0, Node 24.14.0)
```

🔵 **내 변경 탓이 아님을 대역 내 대조군으로 갈랐다**: 같은 오류가 `@repo/api-client` 에서도
난다 — 이 PR 의 diff 가 **한 줄도 닿지 않는** 패키지다. 그리고 오류는 vitest 자신의 `dist`
안에서 나는 **시작 오류**라 테스트 파일이 로드되기 전이다. 이 호스트의 알려진 함정이고
(`env_webstore_vitest4_node24_module_evaluator`), 그 워크스페이스는 CI(Node 20)가 권위다.
🔴 **`TASK-MONO-585` 가 이 제약을 console-web 에 잘못 상속했던 것과 같은 문장이지만, 여기서는
참이다** — 앱마다 vitest 판이 다르다(console 2.1.9 / ecommerce·fan-web-store 4.1.0).
⇒ 「형제가 그렇다」가 아니라 **그 앱에서 직접 재는 것**이 판정이다.

---

# 🔵 이 가드가 **못 잡는 것** (선언된 공백 — 숨기지 않는다)

(A) 로 자격을 얻은 파일 안에서 env 를 **변수에 먼저 담았다가** 그 변수를 fetch 에 넘기는
새 자리. (B) 는 인자를 보므로 그 한 단계를 못 따라간다. 데이터 흐름 분석에는 타입체커가
필요하고, 그 비용은 이 축이 지금 지는 위험보다 크다. 🔴 **«가드가 있으니 괜찮다» 로 읽지
마라** — 잡히는 것은 **직결**뿐이다. 이 문장은 가드 파일 헤더에도 있다.

---

# 📋 AC 대조 — `in-progress → review`

| AC | 동사 | 닫혔나 |
|---|---|---|
| AC-0 (1) | «전수로 센다 + 걸러진 건수를 출력하라» | ✅ 앱 4 · 사이트 32 · 걸러진 33 |
| AC-0 (2) | «세 갈래로 분류하고 **미분류 목록을 적는다**» | ✅ 2건, 파일:줄로 |
| AC-0 (3) | «술어를 정하고 **형제에서 성립하는지 확인**» | ✅ 성립 안 해서 **술어를 바꿨다** |
| AC-0 (4) | «0이면 가드만, 아니면 형제 diff» | ✅ 2건 ⇒ 형제 diff 포함 |
| AC-1 | «문다» | ✅ 착수 전 rc=1, 2건 지목 |
| AC-2 | «안 물어야 할 때 안 문다» | ✅ 상대경로·마커·자기 문서 |
| AC-3 | «bite» | ✅ 11칸 + **착수 전 RED 확인** |
| AC-4 | «하한을 둔다» | ✅ 앱 ≥3 · 사이트 ≥15, 계측기 축에 |

남은 AC 없음 ⇒ `review`.

---

# 🔵 부수 관찰 — **`Status` 필드는 어느 가드도 안 본다**

이 티켓 자신이 그 증거다. `ready/ → in-progress/` 로 옮길 때 **파일 안의 `Status` 를 안
바꿨고**(`ready` 인 채로 `in-progress/` 에 있었다), 그 상태로
`check-index-queue-drift.sh` 는 **rc=0** 이었다 — 그 가드의 술어는 «INDEX 표의 행 ↔ 큐
디렉터리» 이지 «파일 안의 Status ↔ 디렉터리» 가 아니다.

🔵 잡은 것은 가드가 아니라 `review/` 로 넘길 때 쓴 **`assert` 한 줄**이었다
(`git mv` 뒤 Status 를 바꾸는 `CLAUDE.md` 절차의 그 단언). 🔴 그러니 그 절차를 «귀찮은
확인» 으로 읽지 마라 — 이 축에서는 **그것이 유일한 판정자**다.

🔵 이번엔 푸시 전에 잡혀 아무 데도 안 남았다. 가드를 새로 만들 것을 제안하지는 않는다 —
모집단(전 프로젝트의 모든 큐 파일)에 비해 이득이 작고, 이 축은 close chore 의 단언이
이미 덮는다. **적어 두는 이유는 「가드가 초록이었다」를 「Status 가 맞다」로 읽지 않게
하기 위해서**다.
