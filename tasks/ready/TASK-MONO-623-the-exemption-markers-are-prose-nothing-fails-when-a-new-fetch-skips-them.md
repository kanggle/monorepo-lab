# Task ID

TASK-MONO-623

# Title

`DEMO-URL-EXEMPT:` 마커는 **산문이다.** 새 `fetch(` 가 해석도 마커도 없이 들어와도 아무도 안 문다.

# Status

ready

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
