# Task ID

TASK-MONO-614

# Title

`ADR-MONO-068 § D6 = B2` 구현 — 해석기를 **공유 패키지 하나**로 옮긴다 (루트 워크스페이스 없이). 🔴 그리고 **가드의 명제가 뒤집힌다**

# Status

done

# Owner

monorepo

# Task Tags

- adr
- ci
- demo

---

# ⏳ 선행 — **하나. 그리고 그것은 순서가 바뀐 결과다**

| # | 선행 | 상태 |
|---|---|---|
| 1 | **`TASK-MONO-613`** — 가드 모집단이 `projects/` 밖 Next 앱을 못 본다 | 🔴 **필수 선행** — 아래 § 왜 |

🔴 **613 은 기안 시점엔 «독립» 이라고 적혀 있었다.** `B2` 가 채택되면서 성격이 바뀌었다:
새 가드가 재야 하는 명제는 *"**어떤 앱도** 자기 구현을 갖지 않는다"* 이고, 그것을 재려면
**모든 앱을 볼 수 있어야** 한다. 포워더는 `projects/` 밖에 놓일 수 있다 ⇒ **모집단에 구멍이
있으면 새 명제는 그 구멍만큼 거짓**이다. (`ADR-MONO-068 § D6.1 ②`)

---

# Goal

`resolveDemoBackend()` 를 **패키지 하나**에 두고, 소비자는 그것을 **import 한다**.
루트 pnpm 워크스페이스는 **만들지 않는다**(`B2` 가 `B` 와 갈리는 지점).

---

# Scope

**포함**

- 공유 패키지 신설 — 🔵 **최초가 아니다** (`ADR-MONO-068 § D6.2` 정정):
  `projects/ecommerce-microservices-platform/packages/` 에 **`@repo/*` 여섯 개**가 이미
  있고 `web-store` 가 **`workspace:*`** 로 쓴다. 관용구도 있다(`@repo/<name>` ·
  `main: ./src/index.ts` · `private: true`) — **그것을 따르라.**
  🔴 **새로운 것은 「패키지」가 아니라 「워크스페이스를 건너는 연결」이다** —
  `fan-platform-web` 은 **다른 pnpm 워크스페이스**(`web/*`)에 있고 `workspace:*` 는 그
  경계를 못 넘는다. 둘 곳과 이름은 **AC-1** 에서 정한다.
- 소비자 전환 **둘** — `web-store` · `fan-platform-web`
- 🔴 **가드 교체** — `scripts/check-demo-resolver-copies.sh` (§ D5.3: 삭제가 아니라 교체)
- 🔵 Vercel «Root Directory 밖 포함» 축의 **관측**(AC-4)

**제외**

- 포워더 앱 자체 — `TASK-MONO-610`. 🔵 이 티켓은 그 앱이 **import 할 수 있는 상태**를 만든다
- 루트 pnpm 워크스페이스 — **`B` 이고 채택되지 않았다**
- `ADR-MONO-069` 의 `C2` 재검토 — 별개 축

---

# 🔴 크로스 프로젝트 — **한 PR 에 원자적으로**

패키지 신설 + 소비자 둘 전환 + 가드 교체가 **같은 커밋**이다(`CLAUDE.md` § Cross-Project
Changes). 한쪽만 가면 그 창에서 main 이 깨진다 — 옛 가드는 «사본이 서로 같은가» 를 재는데
한 소비자만 전환하면 **비교 대상이 사라지거나 달라진다.**

---

# Acceptance Criteria

## AC-0 — 착수 시 재측정 (verify-then-act)

1. 가드가 여전히 `해석기를 가진 앱 2 개 (승격 3)` 을 내는가.
2. `next.config.*` 전수와 그중 `projects/` 밖의 수 (🔵 오늘 = **3 / 0**).
3. 🔴 **613 이 머지됐는가** — 안 됐으면 착수하지 않는다.
4. 두 사본이 **여전히 정규화 동일**한가. 다르면 그 차이가 **패키지 API 의 입력**이다.

## AC-1 — 🔴 패키지의 **자리와 이름**을 정하고 **이유를 적는다**

후보와 각각의 걸림돌(착수 시 재확인):

| 후보 | 걸림돌 |
|---|---|
| 루트 `libs/<name>` | 🔴 그 디렉터리는 **Gradle 세계**다(`settings.gradle` 이 열거). TS 를 넣으면 두 빌드 시스템이 한 이름을 공유한다 |
| 루트 `packages/<name>` | 새 최상위 디렉터리 — `TEMPLATE.md` § Repository Layout 갱신 필요 |
| `infra/demo/<name>` | 🔵 소비자가 전부 데모 축이라 의미가 맞는다. 🔴 그러나 `infra/` 는 지금까지 **런타임 코드가 아니었다** |

🔴 **고르고 나서 「왜 나머지가 아닌가」를 적어라.** 이 저장소는 «읽히지 않는 설정 파일은
무해가 아니라 거짓 증거» 를 이미 배웠다 — 자리 선택도 같다.

## AC-2 — 소비자 **둘**을 전환한다

- `web-store` · `fan-platform-web` 이 패키지를 **import** 한다
- 🔴 `file:` / 상대경로 의존이다 — **워크스페이스 프로토콜(`workspace:*`) 이 아니다**
  (그건 `B` 다)
- 🔵 각 앱의 **기존 테스트가 그대로 통과**해야 한다. 통과 못 하면 그것은 패키지 API 가
  두 소비자의 차이를 **못 담은 것**이다

## AC-3 — 🔴🔴 **가드의 명제를 뒤집는다** (§ D6.1 ①)

| | 지금 | **바꿀 것** |
|---|---|---|
| 명제 | 사본들이 서로 같은가 | **앱이 자기 구현을 갖지 않는가** |
| RED | 정규화 다름 · 앱 3개 이상 | **앱 안에 구현이 하나라도 있으면** |
| 마커 | 세기 위한 표시 | **금지된 것의 표시** |

- 🔴 **공허한 초록을 남기지 마라** — `B2` 아래에서 옛 명제는 사본이 0이라 **자동으로 참**이다
- 🔴 **bite**: 앱 안에 구현을 하나 되돌려 놓으면 RED 여야 한다. **주입을 먼저 단언**하라
- 🔵 **대조군**: 패키지 자신은 구현을 **가져야** 한다 — 「전부 지웠다」와 구별되지 않으면 안 된다
- 🔵 § D2 의 마커는 **유지**한다. 의미만 바뀐다

## AC-4 — 🔵 Vercel 「Root Directory 밖 포함」을 **관측으로 가른다**

`B2` 는 세 앱이 Root Directory **밖**의 패키지를 참조하게 만든다. 그 설정은 **대시보드에만**
있다. 🔴 **추정하지 마라** — 판별은 «머지 뒤 그 앱의 배포가 성공하는가» 다.

- 실패하면 증상은 **install 단계**에서 나온다(`fan-platform-web/VERCEL.md` § 왜
  `installCommand` 가 있는가 — 같은 축에서 이미 한 번 데였다)
- 🔴 **세 프로젝트를 동시에 실패시킬 수 있다** ⇒ 되돌리는 법을 **먼저** 적고 착수하라

## AC-5 — 🔵 이 구현이 **안 고치는 것**을 적는다

- 포워더 앱 — `TASK-MONO-610`
- 루트 워크스페이스 — `B` 이고 채택되지 않았다
- 해석기의 **동작** — 이 티켓은 **자리만** 옮긴다. 동작이 바뀌면 그것은 결함이다

---

---

# 구현 결과 (2026-09-02 UTC)

## ✅ AC-0 — 착수 시 재측정 (넷 다)

| # | 잰 것 | 값 |
|---|---|---|
| ① | 가드 기준선 | `해석기를 가진 앱 2 개 (승격 3) · 정규화 비교 1 쌍 · 앱 소스 1233 개 · 선언 앱 3 개 · 대조군 2 건`, rc=0 |
| ② | `next.config.*` 전수 / `projects/` 밖 | **3 / 0** (기안 시점과 같다) |
| ③ | 613 머지 | ✅ `#3584` → 스쿼시 `8e6153792`, `#3585` 로 close |
| ④ | 두 사본의 **진짜 차이** | 🔵 주석만 벗기면 **코드 71줄 중 4줄** — `SERVICE_PREFIX` · 폴백 env 이름 2개 · 폴백 기본값 |

🔵 **④ 가 곧 패키지 API 의 입력이다.** 정규화가 지우던 축과 정확히 같다 ⇒
`DemoBackendResolverConfig` 는 필드가 **셋**이고, 넷째가 생기면 그건 설정이 아니라
**동작 분기**라고 타입 주석에 적어 두었다.

## ✅ AC-1 — 자리 = `infra/demo/backend-resolver/`, 이름 = `@demo/backend-resolver`

**고른 이유**: `DEMO_API_BASE` 는 앱 설정이 아니라 **계약**이고 그 이름을 정하는 것이
`infra/demo/aws/site/build.sh` 다. 같은 계약의 **다른 클라이언트**(론처 `index.html`)와
같은 파생 규칙을 쓰는 `demo-boot.sh` 가 **이미 `infra/demo/` 에 산다**. 공유 코드라는
이유만으로 계약에서 떼어 내면 파생 규칙을 바꾸는 사람이 볼 파일이 두 디렉터리로 흩어진다.

**왜 나머지가 아닌가** (전문은 `infra/demo/backend-resolver/README.md § 자리`):

| 기각 | 이유 (실측 포함) |
|---|---|
| 루트 `libs/<n>` | `CLAUDE.md` 가 **`shared Java libraries`** 라고 명시. 실측: `libs/` 확장자 = java 189 · gradle 13 · md 2 · imports 2 · xml 1, **TS/JS 0**. 🔴 더 나쁜 것은 **가드가 못 본다**는 점 — `check-libs-ci-coverage.sh` 의 모집단은 디스크가 아니라 **`settings.gradle` 이 포함한 모듈**이라, TS 디렉터리가 하나 늘어도 그 커버리지 가드는 *"전부 자기 `:check` 를 돌린다"* 는 **초록을 그대로** 낸다 |
| 루트 `packages/<n>` | 새 최상위 자체는 비용이 아니다. 🔴 기각 이유는 **읽히는 방식** — 루트 `packages/` 는 `§ D6` 의 **`B`(루트 워크스페이스)** 와 구별되지 않고, 다음 사람이 거기 두 번째 패키지를 놓는다. 🔵 루트 `package.json` 이 자기 `description` 에 *"No workspace / no dependencies at this level"* 이라고 적어 두었다 |
| 한 프로젝트의 `packages/` | `fan-platform` 이 `ecommerce` 내부를 의존하게 된다 — 경계 위반이고 `TEMPLATE.md` 의 추출이 깨진다 |

🔴🔴 **이 티켓의 후보 표에 거짓이 하나 있었다.** *"`infra/` 는 지금까지 **런타임 코드가
아니었다**"* — **틀렸다**. [`infra/demo/aws/site/`](../../infra/demo/aws/site/) 는
`vercel.json` + `build.sh` + `index.html` 을 가진 **배포되는 Vercel 프로젝트**
(`kanggle-portfolio`)다. 걸림돌 셋 중 하나가 **실재하지 않았다**.

🔵 **자리를 `infra/demo/` 로 고른 부수 효과**: 새 최상위 디렉터리가 없으므로
`CLAUDE.md` · `TEMPLATE.md` 의 § Repository Layout 을 **건드리지 않는다**.

🔵 **이름의 스코프만 관용구와 다르다.** `private: true` · `version: 0.0.0` ·
`main`/`types` = `./src/index.ts` 는 `@repo/*` 를 따랐다. 스코프를 `@repo` 로 두지 않은 이유는
`@repo/*` 가 **그 워크스페이스 안에서 `workspace:*` 로 해석되는 이름들**이기 때문이다 —
같은 스코프를 쓰면 한 `package.json` 안에 `workspace:` 와 `file:` 로 해석되는 `@repo/*` 가
섞여 읽는 사람이 멤버 여부를 이름으로 구별할 수 없게 된다.

## ✅ AC-2 — 소비자 둘 전환

두 앱의 `demo-backend.ts` 는 이제 **설정 셋을 넘기는 배선**이다(`file:../../../../infra/demo/
backend-resolver`, 두 앱의 깊이가 같아 문자열도 같다). `workspace:*` 가 **아니다** — 그건 `B` 다.

🔴 **`transpilePackages` 가 필요했다** — `main` 이 TS 소스라 없으면 `next build` 가
node_modules 안 TS 를 만나 죽는다. web-store 는 목록에 한 줄 추가, fan 은 **신설**했다.

| 검증 | 결과 |
|---|---|
| fan 해석기 스위트 | ✅ **15 passed / 15**, 코드 한 줄도 안 고쳤다 |
| fan · web-store `tsc --noEmit` | ✅ **둘 다 rc=0** — `file:` 심링크와 타입이 실제로 풀린다 |
| lockfile | ✅ 둘 다 갱신(`file:../../infra/demo/backend-resolver` 로 해석) — ecommerce CI 는 `--frozen-lockfile` 이라 **필수** |
| 🔴 web-store 스위트 | **로컬 실행 불가** — 아래 |

🔴 **web-store 의 vitest 는 이 호스트에서 안 돈다**: `ERR_PACKAGE_IMPORT_NOT_DEFINED
"#module-evaluator"` (vitest 4.1.0 × Node 24.14). 🔵 **내 탓이 아님을 대조군으로 확증했다** —
내 변경이 **닿지도 않은** `widgets/demo-notice/__tests__` 도 **같은 지점에서 같은 오류**로
죽는다. CI 는 Node 20 이라 통과한다. **권위는 CI 다.**

🔴🔴 **그래서 「fan 스위트가 통과한다」 가 공허하지 않은지 따로 증명했다 (bite)**:
패키지의 `demoDomainFromIp` 를 `.sslip.io` → `.BITE-INJECTED.io` 로 바꾸고(**주입을 먼저
단언**: 문자열 1건) 같은 스위트를 돌렸다 → **2 failed**. 원복 후 다시 **15 passed**.
⇒ 그 스위트는 **실제로 패키지를 지난다**. 낡은 사본을 보고 있던 것이 아니다.

## ✅ AC-3 — 🔴🔴 가드의 명제를 뒤집었다 (§ D5.3 대로 **교체**, 삭제 아님)

| | 이전 판 ② | **현재 판** |
|---|---|---|
| 명제 | 사본들이 서로 같은가 | **앱이 자기 구현을 갖지 않는가** |
| RED | 정규화 다름 · 앱 3개 이상 | **`PACKAGE_DIR` 밖에 구현이 하나라도 있으면** |
| 마커 | 세기 위한 표시 | **「여기 구현이 있다」 — 패키지 밖이면 금지** |

**구현 지문은 셋이고 OR** 다: ① `process.env.DEMO_API_BASE` 를 **읽는다** ② 구현 마커
`DEMO-RESOLVER:` ③ 옛 시그니처 `export (async )?function resolveDemoBackend`.
하나만 쓰면 나머지로 우회된다(마커 안 붙이기 / 이름 바꾸기). ①은 **계약**이라 못 바꾼다.

🔵 소비자 마커는 `DEMO-RESOLVER-CONSUMER:` 로 갈랐다(6개 파일) — 소비자는 정당하게 여럿이고,
같은 문자열이면 가드가 소비자를 구현으로 센다.

### 🔴🔴 A/B — 「공허한 초록」은 주장이 아니라 **실측**이다

옛 가드를 `git show origin/main:` 으로 **꺼내** 같은 두 세계에 돌렸다:

| 세계 | 옛 가드 | 새 가드 |
|---|---|---|
| **A** 승격 완료(구현은 패키지에만) | **rc=0** — 스스로 *"정규화 비교 **0 쌍** · 이 통과는 '갈라지지 않았다' 가 아니라 '비교할 것이 없다'"* 를 출력하며 초록 | rc=0, «앱이 구현을 갖지 않는다» |
| **B** 앱 하나가 구현을 되찾아옴 | 🔴 **rc=2** — 사유가 *"해석기의 내보내는 API 가 바뀌었거나 **판별 패턴이 죽었습니다**"* | **rc=1** + 정확한 처방 |

🔴🔴 **B 가 A 보다 나쁘다.** 옛 가드는 침묵하지 않고 **가드 자신을 의심하라고 말한다** —
그 오진의 자연스러운 다음 행동은 **가드 완화**이고, `§ D3` 이 금지한 바로 그것이다.
⇒ 교체의 이유는 «안 문다» 가 아니라 **«물어도 틀린 곳을 가리킨다»** 다. 이 표를 스크립트
헤더와 self-test 주석에 그대로 넣었다.

### 🔴 내 첫 술어가 틀렸다 — 그리고 그 결함을 칸으로 못 박았다

지문 ①의 첫 판은 계약 **이름**(`DEMO_API_BASE`)이었다. 라이브에서 돌리자 **소비자 위젯
둘이 RED** 로 나왔다. 열어 보니 구현이 아니라 **주석의 산문**이었다 —
*"판정이 `DEMO_API_BASE`(비공개 env)에 달려 있고…"*.
🔴 이 저장소가 이미 이름 붙여 둔 실패다: **판별자가 자기 설명 문구에 걸린다.**
⇒ 지문을 «읽는 행위»(`process.env.` 까지)로 좁혔고, **새 칸 「🔵 산문의 이름 언급은 안 문다」**
가 그 위젯의 실제 두 줄(산문 + 옛 정규식 원문 인용)을 픽스처로 얼린다.

🔵 그 대신 **대조군의 패턴은 이름 그대로 두었다**(`CONTRACT_RE`) — 론처는 `window.DEMO_API_BASE`
로 읽어 `process.env` 형태가 아니다. 지문을 그대로 대조군에 쓰면 **매일 exit 2** 가 된다.
두 패턴이 재는 것이 다르다는 것을 헤더에 적었다.

### 검증

| | |
|---|---|
| self-test | ✅ **13 passed / 0 failed** (12칸 → 13칸) |
| 라이브 | ✅ `OK — 구현은 패키지 1곳뿐(infra/demo/backend-resolver, 파일 1 개) · 앱 안 구현 0 건 · 앱 소스 1234 개 · 선언 앱 3 개 · 대조군 2 건` rc=0 |
| 대조군 4종 | 패키지가 비면 **2** · 탐지기 사망 **2** · 선언 앱 안 보임 **2** · 론처가 모집단 안 **2** |

## 🔴🔴 티켓이 **안 적은** 범위 셋 — 전부 이 PR 에 넣었다

### ① 두 판정자의 `PATHSPECS` 에 패키지 자리가 없었다

`vercel-ignore.sh` 둘 어디에도 `infra/demo/backend-resolver` 가 없었다 ⇒ **해석기를 고쳐도
두 앱이 재배포되지 않는다.** 증상은 «배포 실패» 가 아니라 **«CI 초록 · 사이트는 낡은 판»**
이라 아무도 안 본다(`TASK-MONO-562` 가 정확히 그렇게 데였다). 두 파일에 추가했다.

### ② 트리거 가드의 칸 (12) 가 `workspace:*` 만 봤다

칸 (12)는 *"로컬 경로 의존이 트리거 목록에 덮이는가"* 를 재는데, 필터가
`v.startsWith('workspace:')` 하나였다. 🔴 **`B2` 는 정의상 `file:` 이고 `file:` 은
워크스페이스 «밖» 을 가리키므로, B2 가 만든 의존이 정확히 이 칸의 사각지대로 들어온다.**
`file:` 도 보게 고쳤다(경로가 곧 답이라 이름 해석이 필요 없다).

- 라이브: web-store **7개 중 7개**, fan **1개 중 1개** 덮임. 🔵 fan 은 직전까지 `SKIP` 이었다.
- 🔴 **bite**: fan 의 pathspec 을 빼자(**제거를 먼저 단언**: 잔존 0건) →
  `x (12) 로컬 경로 의존 '@demo/backend-resolver' (infra/demo/backend-resolver) 가 트리거
  목록에 없습니다` · `1개 중 0개` · **rc=1**. 원복 확인.

### ③ 🔴🔴 `613` 이 **술어는 넓혔는데 도달성은 안 넓혔다**

`ci.yml` 의 `demo-resolver-copies` 필터가 `projects/*/{apps,web}/*/src/**` 그대로였다.
즉 613 이 self-test 에 못 박은 *"선언 없는 디렉터리도 문다"*(`services/notifier/src/`)는
**CI 에서 아예 안 돈다** — 물 수 있지만 **불리지 않는다**. 그리고 새 패키지도 필터 밖이었다.
⇒ TS 로 한정한 `**/src/**/*.ts(x)` + `**/next.config.*` + `infra/demo/backend-resolver/**`
로 넓혔다(🔵 `**/src/**` 는 Java 의 `src/main/java` 까지 삼켜 거의 모든 백엔드 PR 에서 돈다).
그리고 `vercel-build-triggers` 에 `**/package.json` 을 넣었다 — 칸 (12)의 **도착 경로**가
거기이기 때문이다(없으면 그 칸은 자기 도착 경로에서 안 돈다).

🔵 **셋 다 같은 부류다**: 판정은 옳은데 **불리지 않거나**, 모집단이 기전을 못 따라간다.
`613` 이 하나를 고치자 그 옆에 같은 모양이 둘 더 있었다.

## 🔵 AC-4 — 관측으로 가른다. **그리고 관측을 머지 «앞» 으로 당겼다**

| 프로젝트 | 「Root Directory 밖 포함」 | 근거 |
|---|---|---|
| `kanggle-store` | ✅ **ON — 실측으로 확정** | Root Directory 는 `apps/web-store` 인데 `@repo/*` **6개를 `workspace:*`** 로 의존한다(그 패키지들은 `packages/*` = **밖**). `workspace:` 는 워크스페이스 루트 없이 해석이 **불가능**하고 패키지가 `private: true` 라 레지스트리 폴백도 없다. 그런데 `https://store.hubwang.com` 이 **200 · `Server: Vercel` · `X-Matched-Path: /` · Next 렌더**를 서빙한다 |
| `kanggle-fan` | 🔴 **미지수** | fan 은 `workspace:*` 의존이 **없다** ⇒ fan 의 배포 성공은 이 축에 대해 **아무것도 증명하지 않는다** |

🔴 `ADR-MONO-068` ㉯(*"Next 빌드가 루트 lockfile 을 본다"*)를 fan 의 증거로 쓰면 **안 된다** —
저장소 어디에도 그 로그의 Vercel 출처가 없다. **로컬 빌드의 관측**이다.

🔵 **그래서 브랜치 이름을 `preview/…` 로 했다.** 세 `vercel.json` 이
`git.deploymentEnabled` 에 `"preview/*": true` 를 두고 있으므로 **머지 전에 실제 Vercel
빌드가 돈다.** 실패해도 프로덕션 URL 은 그대로다(Preview 환경).

**되돌리는 법** (착수 **전에** 적었다 — `infra/demo/backend-resolver/README.md § 되돌리는 법):
① 머지하지 않는다(브랜치 배포는 Preview 라 프로덕션 무영향) ② 이미 머지됐다면
`git revert <squash-sha>` 한 커밋 — 마이그레이션도 상태도 없다. revert 가 main 에 들어가면
`vercel-deploy.yml` 이 훅을 다시 쏘아 마지막 성공 판을 굽는다 ③ **진짜 수정**은 대시보드에서
그 프로젝트의 *"Include files outside root directory"* 를 켜는 것이고, revert 는 지혈이다.

## ✅ AC-5 — 이 티켓이 **안 하는 것**

- 포워더 앱 — `TASK-MONO-610`. 이 티켓은 그 앱이 **import 할 수 있는 상태**를 만들 뿐이다
- 루트 워크스페이스 — **`B` 이고 채택되지 않았다**. 루트 `pnpm-workspace.yaml` 을 만들지 않았다
- 해석기의 **동작** — 자리만 옮겼다. 🔵 `??` 의 nullish 의미까지 보존했다(폴백 사슬을 배열로
  일반화하면서 `||` 로 바꾸면 `FOO=''` 인 배포에서 폴백이 한 칸 더 흐른다 — 주석에 적었다)
- 패키지 자신의 테스트 — **의도적으로 안 만들었다**. `infra/` 를 도는 프런트 유닛 잡이 없어
  **러너 없는 스위트**가 되고, 러너 없는 스위트는 썩는다. 두 소비자의 스위트가 **각자의
  설정으로** 이 구현을 통과시키는 편이 파라미터화를 더 잘 잰다(README 에 적었다)

# Related Specs

- [`docs/adr/ADR-MONO-068-…md`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md) § D5.3 · § D6 · **§ D6.1**
- `scripts/check-demo-resolver-copies.sh`
- `tasks/ready/TASK-MONO-613-…md` — **선행**
- `tasks/in-progress/TASK-MONO-610-…md` — 이 티켓이 그 선행을 푼다
- `projects/fan-platform/web/fan-platform-web/VERCEL.md` § `installCommand` · § 남은 갈림길

# Related Contracts

없음.

# Edge Cases

- **두 사본이 정규화 동일하지 않다** → AC-0 ④. 그 차이가 API 의 입력이다.
- **패키지가 Vercel install 에서 안 보인다** → AC-4. 되돌리는 법을 먼저 적는다.
- **소비자 테스트가 깨진다** → 패키지 API 가 차이를 못 담은 것이다(AC-2 마지막 줄).
- **613 이 안 끝났다** → 착수하지 않는다(AC-0 ③).

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 가드를 안 바꾼다 | 사본 0 ⇒ 옛 명제가 **자동 참** = 공허한 초록 | AC-3 |
| bite 없이 새 가드를 믿는다 | 새 명제가 원래도 참이었을 수 있다 | AC-3 둘째 칸 |
| 한 소비자만 전환 | 그 창에서 main 이 깨진다 | § 크로스 프로젝트 |
| Root Directory 밖 포함을 **추정** | 세 프로젝트 배포가 동시에 죽는다 | AC-4 |

## CORRECTION — 🔴 CI 가 물었다: 패키지에 `tsconfig.json` 이 없어 소비자 테스트가 **기동하다 죽었다** (2026-09-02)

`#3586` 첫 런에서 **`Frontend unit tests` 가 FAILURE** 였다. 이 티켓의 구현 결과 절이
*"web-store 스위트는 로컬 실행 불가 ⇒ 권위는 CI"* 라고 적은 **바로 그 축**이다.

```
[BUNDLER_INITIALIZE_ERROR] Invalid jsx option: `automatic`.
  File: .../node_modules/@demo/backend-resolver/src/index.ts
```

**기전**: 이 패키지는 TS 소스를 그대로 내보내므로(`main: ./src/index.ts`) 소비자의 변환기가
그 파일을 **직접 파싱**하고, 그때 **그 파일 기준으로** tsconfig 를 찾아 올라간다.
`node_modules/` 안의 이 패키지 위에는 아무 tsconfig 도 없어 기본값으로 떨어지고, 거기서
`jsx: automatic` 이라는 **유효하지 않은 값**이 나온다. 🔵 fan 은 vitest 3 이라 통과했다 —
**두 소비자가 서로 다른 것을 재고 있었다**는 뜻이고, 그래서 fan 15/15 는 이 축을 못 봤다.

### 🔵 수정은 «형제를 grep» 에서 나왔다 — 관측 문구를 따라가지 않았다

`packages/{api-client,types,ui,utils}` 는 **넷 다 자기 `tsconfig.json` 을 갖는다**. 같은
방식(TS 소스 직접 노출)으로 **같은 vitest 4** 를 통과하는 이유가 그것이다.
🔴 그리고 **넷 다 `jsx` 를 설정하지 않는다** ⇒ 여기서도 설정하지 않았다. 오류 문구가 `jsx`
였다고 `jsx` 를 박아 넣는 것은 **동작이 확인된 개체와 다른 값을 고르는 일**이다.
값은 `@repo/tsconfig/base.json` 을 그대로 옮겼다(`extends` 는 못 한다 — 그 패키지는
ecommerce 워크스페이스 멤버이고 이 패키지는 그 밖에 산다).

### 🔴🔴 그런데 그 파일을 만드는 것만으로는 **아무 일도 일어나지 않았을 것이다**

CI 오류 경로가 `node_modules/.pnpm/@demo+backend-resolver@file+..+..+infra+demo+backend-resolver/…`
였다 — pnpm 의 `file:` 은 **심링크가 아니라 가상 스토어로 복사**하고, 그 복사는
`package.json` 의 **`files` 를 따른다**. 이 패키지의 `files` 는 `["src"]` 였다.

**실측** (고치기 전, 소비자가 실제로 받은 것):

```
README.md   package.json   src
```

⇒ `tsconfig.json` 은 **트리에는 있는데 소비자에게는 없는** 상태가 된다. 그러면 이 수정은
**아무 일도 하지 않으면서 고쳐진 것처럼 보이고**, 다음 CI 런이 **같은 오류**를 낸다.
`files` 에 `tsconfig.json` 을 넣고 **재설치 후 사본을 다시 세어** 확인했다:

```
README.md   package.json   src   tsconfig.json      ← 두 워크스페이스 모두
```

🔵 이 저장소가 이미 아는 부류다 — **선언 파일 grep ≠ 런타임 모집단.** 파일을 «만들었다» 는
것과 그것이 «소비자에게 도달한다» 는 것은 다른 사건이고, 후자는 **따로 재야 한다.**

### 재검증

| | |
|---|---|
| 소비자 사본에 tsconfig | ✅ ecommerce · fan **둘 다** 실린다(재설치 후 실측) |
| fan 해석기 스위트 | ✅ 15/15 (회귀 없음) |
| fan `tsc --noEmit` | ✅ rc=0 |
| 가드 | resolver self-test **13/0** · 라이브 rc=0 · vercel-triggers rc=0 · index/task-id/walkthrough rc=0 |
| 🔴 web-store vitest | **여전히 로컬 판정 불가**(vitest 4 × Node 24) — **CI 가 권위다** |

🔵 **부수 효과 하나를 README 에 적었다**: `file:` 이 복사이므로 이 패키지를 고쳐도 소비자는
**재설치 전까지 옛 판을 본다.**

## CORRECTION (2) — 🔴🔴 **바로 위 수정은 원인이 아니었다.** 진짜는 `file:` 이 만든 **realpath** 다 (2026-09-02)

`tsconfig.json` 을 넣고 `files` 에도 실었는데 **CI 가 같은 오류를 냈다**:

```
[BUNDLER_INITIALIZE_ERROR] Invalid jsx option: `automatic`.
  Plugin: vite:oxc
  File: .../node_modules/.pnpm/@demo+backend-resolver@file+…/src/index.ts
```

🔴 **증상이 살아남으면 그것은 원인이 아니었다.** 첫 진단은 *"형제는 tsconfig 가 있고 나는
없다"* 였다 — 그 차이는 **사실**이었지만 **원인이 아니었다.** 검증 가능한 차이를 원인으로
승격시킨 것이다.

### 진짜 기전 — 오류의 `File:` 경로가 내내 그것을 말하고 있었다

형제와 내 패키지의 차이는 «tsconfig 유무» 가 아니라 **«그 파일이 어디에 있는가»** 다.
실측 (`fs.realpathSync('node_modules/<pkg>')`):

| 의존 | realpath |
|---|---|
| `@repo/utils` (`workspace:*`) | `projects/…/packages/utils` — **저장소 실경로** |
| `@demo/backend-resolver` (**`file:`**) | 🔴 `node_modules/.pnpm/@demo+backend-resolver@file+…/` — **node_modules 안** |
| `@demo/backend-resolver` (**`link:`**) | ✅ `infra/demo/backend-resolver` — **저장소 실경로** |

pnpm 의 `file:` 은 **가상 스토어로 복사**하고, `link:` 는 **심링크**한다. Vite/oxc 는 realpath 가
`node_modules` 안인 파일을 앱 소스와 다르게 다루고, 거기서 이 앱에 유효하지 않은 jsx 옵션이
적용된다. ⇒ **`file:` → `link:`**.

### 🔵 그리고 `link:` 는 «새로운 것» 이 아니라 **형제들이 이미 쓰는 기전**이다

락파일이 직접 말한다 — `@repo/api-client` 의 `specifier: workspace:*` 가
**`version: link:../../packages/api-client`** 로 풀린다. 즉 `workspace:*` 는 «워크스페이스에서
이름을 찾은 뒤 `link:` 하는 것» 이고, `B2` 는 그 **이름 찾기만 뺀** 것이다.
🔵 `link:` 는 여전히 **상대경로 의존**이므로 `§ D6` 의 `B2`(*"`file:` / 상대경로 의존"*) 안이고,
루트 워크스페이스도 만들지 않는다.

### 🔴 이 오진이 값싸지 않았던 이유 — 그리고 왜 그래도 두 번 만에 잡혔나

`AC-2` 는 *"각 앱의 **기존 테스트가 그대로 통과**해야 한다"* 를 요구했고, 나는 fan 15/15 로
그것을 만족했다고 적었다. 🔴 **fan 은 vitest 3 이고 web-store 는 vitest 4 다** — 두 소비자가
**서로 다른 것을 재고 있었고**, 이 축은 fan 쪽에 존재하지 않는다. 그리고 web-store 의
스위트는 **이 호스트에서 기동 자체가 안 된다**(Node 24). ⇒ 이 축의 유일한 관측기가 CI 였다.

🔵 **그래도 두 번 만에 잡힌 것은 오류 메시지의 `File:` 경로 덕이다.** 첫 수정이 실패한 뒤
그 경로를 «패키지 이름» 이 아니라 **«어느 트리에 있는가»** 로 읽었고, 그러자 형제와 대조할
수 있는 축이 나왔다. **오류가 이미 답을 갖고 있었다.**

### 재검증 (`link:` 로 바꾼 뒤)

| | |
|---|---|
| realpath | ✅ 두 소비자 모두 `infra/demo/backend-resolver` (node_modules **밖**) |
| lockfile | ✅ `specifier/version: link:../../../../infra/demo/backend-resolver` |
| fan 스위트 | ✅ 15/15 |
| fan · web-store `tsc --noEmit` | ✅ 둘 다 rc=0 |
| fan `next build` | ✅ rc=0 |
| 가드 | resolver self-test **13/0** · 라이브 rc=0 · vercel-triggers rc=0 · index/task-id/walkthrough rc=0 |
| 🔴 web-store vitest | **여전히 로컬 판정 불가** — CI 가 권위다 |

🔵 `tsconfig.json` 은 **남겨 두었다.** 수정은 아니었지만 형제 넷이 전부 갖고 있는 모양이고,
`link:` 인 지금은 realpath 가 저장소 안이라 **실제로 발견된다.** `files` 의 `tsconfig.json`
항목도 남겼다 — 누군가 `file:` 로 되돌리면 그 함정이 되살아난다.

## CORRECTION (3) — ✅ **AC-4 가 관측으로 닫혔다: fan 도 «포함 ON» 이다** (2026-09-02)

구현 결과 절은 AC-4 를 **반쪽**으로 남겨 두었다 — `kanggle-store` 는 ON 확정, `kanggle-fan` 은
**미지수**. 브랜치를 `preview/…` 로 낸 것이 정확히 그 나머지 반쪽을 **머지 전에** 재기
위해서였다. 그 관측이 도착했다.

`gh api repos/…/commits/<sha>/status` 의 **description** (SUCCESS 만 보면 안 된다 — 「빌드했다」
와 「건너뛰었다」가 같은 초록이기 때문이다):

| 프로젝트 | description | 읽는 법 |
|---|---|---|
| `kanggle-portfolio` | `Canceled by Ignored Build Step` | 🔵 이 PR 은 론처를 안 건드린다 ⇒ **판정자가 옳게 건너뛰었다** |
| `kanggle-fan` | ✅ **`Deployment has completed`** | **실제로 빌드했다** |
| `kanggle-store` | ✅ **`Deployment has completed`** | 실제로 빌드했다 |

### ⇒ 판정

두 소비자 모두 **Root Directory 밖**(`infra/demo/backend-resolver`)을 가리키는 의존을 가진
채 **install 과 build 를 통과**했다. 그 경로가 빌드 컨텍스트에 없었다면 `pnpm install` 이
죽거나 `next build` 가 `Module not found` 로 죽는다 — 이 패키지는 `demo-backend.ts` →
`route.ts` / `client.ts` 경로로 **빌드 그래프 안**에 있다.
⇒ **「Include files outside root directory」 는 `kanggle-fan` · `kanggle-store` 둘 다 ON 이다.**

🔵 부수 확인: 론처가 `Canceled by Ignored Build Step` 을 냈다는 것은, `preview/*` 브랜치에서
`ignoreCommand` 가 **저장소 루트에 접근해 정상 발화**했다는 뜻이기도 하다
(`vercel-ignore.sh` 가 `$(git rev-parse --show-toplevel)` 을 쓴다).

### 🔵 그래서 이 절이 «되돌리기» 를 은퇴시키는가 — 아니다

`README.md § 되돌리는 법` 은 그대로 둔다. 그 절은 **이 관측이 없었다면** 필요했을 절차이고,
누군가 네 번째 Vercel 프로젝트를 만들거나 Root Directory 를 바꾸면 **같은 질문이 다시**
열린다. 🔴 지금 확정된 것은 «이 두 프로젝트의 오늘 설정» 이지 «Vercel 이 늘 그렇다» 가 아니다.
