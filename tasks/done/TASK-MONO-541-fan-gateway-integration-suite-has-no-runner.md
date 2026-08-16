# Task ID

TASK-MONO-541

# Title

팬 게이트웨이 통합 스위트에 **러너가 없다** — `check` 는 태그로 빼고 통합 레인은 넷만 나열해서, 그 프로젝트의 엣지 라우트 계약 전체가 어디서도 돌지 않는다

# Status

done

# Owner

monorepo

# Task Tags

- ci
- testing

---

# 배경

`TASK-FAN-BE-049` AC-6(게이트웨이를 통한 호출로 판정)을 실제로 재려다 발견했다.

`GatewayRouteRewriteTest` 는 그 프로젝트의 **외부 경로 계약 전체**를 담는 스위트인데
(`/api/v1/**` → 다운스트림 `/api/**` 재작성 10건), **어느 CI 잡도 그것을 실행하지 않는다.**

두 개의 정당한 결정이 맞물려 만든 공백이다 — 어느 쪽도 그 자체로는 틀리지 않았다:

| 층 | 무엇이 참인가 |
|---|---|
| `gateway-service/build.gradle` | `test` 는 `integration` 태그를 **제외**한다(빠른 피드백을 Docker-free 로 유지). `integrationTest` 태스크가 그 태그만 돌리되 **`check` 에 묶이지 않는다** |
| `ci.yml` 빌드 잡 | `:projects:fan-platform:apps:gateway-service:check` 를 돌린다 — 위 규칙에 따라 통합은 **안 돈다** |
| `ci.yml` 팬 통합 잡 | `community` · `artist` · `membership` · `notification` **넷만** 나열한다 — 게이트웨이가 **없다** |

⇒ `check` 는 "돌긴 돈다" 는 인상을 주고, 통합 레인은 "통합은 커버된다" 는 인상을 준다.
**두 인상이 겹치는 자리에 게이트웨이가 빠져 있다.**

## 🔴 그 공백이 실제로 무엇을 숨겼는지 — 가설이 아니라 실측이다

`TASK-FAN-BE-049` 에서 이 스위트를 (아마 오랜만에) 돌리자 **10개 전부
`initializationError`** 로 죽어 있었다: `NullPointerException … "jwks" is null`.
`@DynamicPropertySource` supplier 가 **컨텍스트 생성 시점**에 평가되는데
`@TestInstance(PER_CLASS)` 에서는 그것이 `@BeforeAll` **보다 먼저** 일어나, 거기서 만들던
`jwks`/`downstream` 이 아직 null 이었다.

**baseline 대조로 확인했다** — 새 케이스를 걷어내고 돌려도 동일 실패 ⇒ 선재 결함이고,
`BE-049` 가 하네스를 static 초기화로 고쳐 **9개가 되살아났다**.

🔴 요점은 그 결함이 아니라 **그것이 살아남은 방식**이다: 안 도는 스위트는 썩고, 썩은 줄
아무도 모른다. 지금 되살린 10개도 **여전히 CI 밖**이므로 같은 일이 다시 일어난다.

---

# Goal

팬 게이트웨이의 `integrationTest` 가 CI 에서 실제로 실행된다.

---

# Scope

- `.github/workflows/ci.yml` 의 팬 통합 잡 `gradle-tasks` 에
  `:projects:fan-platform:apps:gateway-service:integrationTest` 를 추가.
- 그 잡의 `report-paths` 글롭이 게이트웨이 산출물도 집는지 확인(현재
  `projects/fan-platform/apps/*/build/...` 이므로 아마 이미 맞다 — **확인하고 적을 것**,
  맞다고 가정하지 말 것).

## 🔴 함께 세되, 이 티켓에서 고치지는 말 것 — **모집단을 다시 세라**

이 결함은 "한 줄이 빠졌다" 가 아니라 **"목록과 트리가 어긋났는데 아무도 안 센다"** 이다.
게이트웨이 하나만 넣으면 같은 계열의 다음 낙오를 못 잡는다. 그러므로 **8개 프로젝트 전체**
에 대해 다음을 세고 표로 남긴다:

> `integrationTest` 태스크를 **선언한** 모듈 집합  ↔  어느 CI 잡의 `gradle-tasks` 에
> **나열된** 모듈 집합

차집합이 이 티켓의 진짜 산출물이다. 🔵 차집합이 **0 이면 그것도 산출물**이다("게이트웨이
하나뿐이었다" 를 실측으로 적는다). 발견된 다른 낙오는 **각각 별도 티켓**으로 — 서비스마다
첫 CI 실행이 자기만의 하네스 갭을 드러낸다는 것이 이 저장소의 반복 관측이라, 한 PR 에
묶으면 무엇이 왜 빨간지 갈리지 않는다.

## Out of Scope

- 게이트웨이 하네스 수정 — `TASK-FAN-BE-049` 가 이미 했다(static 초기화 전환).
- 다른 프로젝트의 낙오 **수정**(위 표에서 나오면 티켓만 세운다).
- `check` 가 `integration` 태그를 빼는 규칙 변경 — 그것은 의도된 설계다.

---

# Acceptance Criteria

- [x] **AC-0 (전제 재확인)** — 착수 시 팬 통합 잡의 `gradle-tasks` 에 게이트웨이가
      **여전히 없는지** 확인한다. 있으면 **STOP**(누군가 이미 넣었다).
- [x] **AC-1** — 팬 통합 잡이 `gateway-service:integrationTest` 를 포함한다.
- [x] **AC-2 (실행 증거 — 이것이 판정이다)** — 그 잡의 산출물에서
      `GatewayRouteRewriteTest` 의 `tests` · `failures` · `errors` · **`skipped`** 네
      값을 모두 읽어 적는다. 🔴 **`BUILD SUCCESSFUL` 도 잡 초록도 판정이 아니다** —
      `BE-049` 에서 실제로 `rc=0 · BUILD SUCCESSFUL` 인데 XML 이 `skipped=10` 인 회차가
      나왔다(Docker 미검출 → `disabledWithoutDocker` 전량 스킵). **`skipped=0` 과
      `tests≥10` 을 함께** 단언한다.
- [x] **AC-3 (bite — 레인이 실제로 무는가)** — 라우트를 일부러 깨서(예: 재작성 정규식의
      접두사를 바꿔) **그 잡이 빨개지는지** 확인하고 되돌린다. 🔴 초록만 보면 "잡이
      추가됐다" 와 "잡이 그 스위트를 본다" 가 구별되지 않는다.
- [x] **AC-4 (모집단)** — 위 Scope 의 두 집합을 8개 프로젝트에 대해 세고 차집합을 티켓
      본문에 표로 남긴다. 0 건이면 0 건이라고 적는다. 발견된 낙오마다 후속 티켓 ID 를 적는다.
- [x] **AC-5** — 잡 시간 증가를 적는다. 팬 통합 잡이 타임아웃에 접근하면 분리를 제안하되
      **이 티켓에서 분리하지는 않는다**(추측으로 나누지 말고 측정값을 남긴다).

---

# Related Specs

- `.github/workflows/ci.yml` — 팬 통합 잡 (변경 대상)
- `projects/fan-platform/apps/gateway-service/build.gradle` — `integrationTest` 태스크 정의

# Related Contracts

없음 — CI 배선 변경이며 API·이벤트 계약을 건드리지 않는다.

---

# Edge Cases

- **`report-paths` 글롭** — `apps/*/build/...` 라 게이트웨이도 포함될 것으로 보이나,
  아티팩트가 실제로 올라왔는지 **다운로드해서** 확인한다. 리포트가 없으면 AC-2 를 잴
  수단이 사라지고, 그 상태는 잡 초록과 구별되지 않는다.
- **경로 필터** — 팬 통합 잡은 `needs.changes.outputs.fan` 등으로 게이팅된다. 게이트웨이만
  건드린 PR 이 그 필터에 걸리는지 확인할 것(안 걸리면 새 레인이 정작 필요할 때 안 돈다).
- **Docker 가용성** — 러너에서 Testcontainers 가 못 뜨면 `disabledWithoutDocker` 가
  **조용히 스킵**한다. AC-2 의 `skipped=0` 이 이 경우를 잡는 유일한 단언이다.
- **Redis 컨테이너 추가 부하** — 게이트웨이 스위트는 Redis 1개를 더 띄운다. 다른 넷과
  같은 잡에서 도는 것이 자원상 문제되면 AC-5 의 측정값이 그 근거가 된다.

# Failure Scenarios

- **잡 초록으로 끝낸다** → 전량 스킵이 초록으로 보고된다. 실제로 그 회차를 봤다(AC-2).
- **게이트웨이 한 줄만 넣고 닫는다** → 같은 계열의 다음 낙오가 그대로 남는다. 이 결함은
  누락 한 건이 아니라 **목록과 트리를 대조하는 것이 없다**는 구조다(AC-4).
- **bite 를 생략한다** → 잡이 스위트를 실제로 보는지 모른 채 "추가했다" 로 닫힌다(AC-3).
- **발견된 낙오를 한 PR 에 묶는다** → 서비스별 첫 CI 실행이 각자 하네스 갭을 드러내므로,
  무엇이 왜 빨간지 갈리지 않는다(이 저장소의 반복 관측).

---

# ✅ 실행 결과 (2026-08-17)

## AC-4 (모집단 재계수) — **이것이 이 티켓의 진짜 산출물이었고, 실제로 두 번째를 찾았다**

`integrationTest` 를 **선언한** 모듈 **39** ↔ 어느 CI 잡에 **나열된** 모듈 **41**.
스크립트로 전수 대조했다(하드코딩 목록 대조 금지 — 그것이 이 결함의 구조다).

| 차집합에 나타난 모듈 | 판정 |
|---|---|
| `projects/fan-platform/apps/gateway-service` | 🔴 **진짜 낙오** — 이 티켓이 닫음 |
| `projects/scm-platform/apps/gateway-service` | 🔴 **진짜 낙오 — 새 발견** ⇒ `TASK-MONO-542` |
| `projects/iam-platform` (루트) | 🔵 **계수 인공물** — `subprojects { }`(L7) 에서 선언하므로 5개 앱이 상속하고 그 5개는 전부 나열돼 있다 |

🔵 세 번째 행을 지우지 않는다 — 결함이 아니지만, 적지 않으면 다음 사람이 같은 계수를 하고
같은 의심을 반복한다.

**scm 은 추측이 아니다 — 돌려 봤다**: `files=5 tests=5 failures=5 skipped=0`,
`jwks is null` 6회. **팬과 글자 그대로 같은 결함**(`@TestInstance(PER_CLASS)` +
`@BeforeAll` 초기화)이고 같은 이유로 안 보였다. 진단을 `TASK-MONO-542` 본문에 그대로
넘겼으므로 그 티켓은 미스터리가 아니라 **답을 들고 시작한다**.

⇒ *"게이트웨이 한 줄만 넣고 닫으면 다음 낙오를 못 잡는다"* 는 Scope 의 우려가 **맞았다.**

## 🔴 레인을 붙이려다 내가 BE-049 에서 만든 결함을 밟았다

`TASK-FAN-BE-049` 는 이 스위트를 **한 클래스만**(`--tests '*GatewayRouteRewriteTest*'`)
돌려 10/10 초록을 보고했다. **스위트 전체를 돌리자 20개 중 9개가 실패했다.**

| 실행 방식 | 결과 |
|---|---|
| `GatewayRouteRewriteTest` 단독 | **10/10 초록** |
| 스위트 전체 | **20 중 9 실패** (전부 `GatewayRouteRewriteTest`) |

원인은 **BE-049 가 `@AfterAll` 의 managed stop 을 제거한 것의 이면**이다. 공유
`MockWebServer` 가 이제 스위트 전체에서 **하나**인데, `GatewayRateLimitIntegrationTest`
가 **50개를 enqueue 하고 리미터가 통과시킨 소수만 소비**한다 ⇒ 남은 **~45개의 stale
`200 {}`** 가 큐에 앉아 이후 테스트에 먼저 응답된다. 증상이 정확히 그 모양이었다:
`expected:<201 CREATED> but was:<200 OK>` 3건 + 기록된 경로 불일치 6건.

🔴 **"격리 통과" 는 "스위트 통과" 가 아니다.** BE-049 의 보고는 그 클래스에 대해서는
참이었지만, 레인이 돌리는 것은 **스위트**다 — 러너를 붙이는 이 티켓이 아니었으면 그 차이는
CI 첫 실행에서 빨간불로 나타났을 것이다.

**고침**: 베이스에 `@BeforeEach` 로 공유 큐를 리셋한다 —
`setDispatcher(new QueueDispatcher())`(응답 큐엔 clear() 가 없으므로 새 큐로 교체) +
기록된 요청 드레인. 서버 자체를 클래스별로 만들 수는 없다: 라우트 URI 가
`@DynamicPropertySource` 로 배선되고 Spring 이 스위트 전체에 **하나의 컨텍스트를 캐시**
하므로, 클래스별 서버는 캐시된 라우트를 죽은 포트로 남긴다.

## AC 판정

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | 착수 시 팬 통합 잡 `gradle-tasks` 에 게이트웨이 부재 확인 |
| AC-1 | ✅ | 잡에 `:projects:fan-platform:apps:gateway-service:integrationTest` 추가. `report-paths` 는 `apps/*` 글롭이라 구조상 포함 — **아티팩트로 확인**(AC-2) |
| AC-2 | ✅ | 아래 수치 — 로컬 **20/0/0/skip 0**, CI 잡 산출물로 재확인 |
| AC-3 | ✅ | bite — 아래 참조 |
| AC-4 | ✅ | 위 재계수 표. 차집합 3 중 **진짜 2 · 인공물 1** |
| AC-5 | ✅ | 잡 시간 — 아래 |

로컬 실측(XML 네 값 전부, `BUILD SUCCESSFUL` 로 판정하지 않음):

| 클래스 | tests | fail | skip |
|---|---|---|---|
| `GatewayBootstrapIntegrationTest` | 5 | 0 | 0 |
| `GatewayHealthCheckIntegrationTest` | 2 | 0 | 0 |
| `GatewayPrometheusIsolationTest` | 2 | 0 | 0 |
| `GatewayRateLimitIntegrationTest` | 1 | 0 | 0 |
| `GatewayRouteRewriteTest` | 10 | 0 | 0 |
| **합계** | **20** | **0** | **0** |

## AC-1 후반 · AC-2 — 잡 산출물에서 잰 값

`report-paths` 의 `apps/*` 글롭이 게이트웨이 산출물을 **실제로 집었다**(가정하지 않고
아티팩트를 내려받아 확인). CI 첫 실행:

| 클래스 | tests | fail | err | skip |
|---|---|---|---|---|
| `GatewayBootstrapIntegrationTest` | 5 | **1** | 0 | 0 |
| `GatewayHealthCheckIntegrationTest` | 2 | 0 | 0 | 0 |
| `GatewayPrometheusIsolationTest` | 2 | 0 | 0 | 0 |
| `GatewayRateLimitIntegrationTest` | 1 | 0 | 0 | 0 |
| `GatewayRouteRewriteTest` | 10 | 0 | 0 | 0 |
| **합계** | **20** | **1** | 0 | **0** |

🔴 **로컬 20/20 초록이 CI 에서 유지되지 않았다.** 실패는 `tamperedTokenSignatureReturns401`
의 `Timeout on blocking read for 5000000000 NANOSECONDS` 하나다. 이 경로는 게이트웨이가
토큰을 거절하는 지점이라 **다운스트림에 닿지 않는다** ⇒ 내가 고친 큐 누수와 무관하다.

### 🔴 아티팩트가 최신 시도를 반영하지 않는다 (판정 도구의 함정)

재실행 후 잡은 `success` 인데 `gh run download` 로 받은 아티팩트는 **여전히 `fail=1`**
이었다. 확인하니 그 이름의 아티팩트는 **하나뿐이고 생성 시각이 1차 시도**(16:56)다 —
재실행은 새 아티팩트를 올리지 않았다. ⇒ **"아티팩트에서 네 값을 읽는다" 는 조용히 낡은
시도를 잴 수 있다.** 이 회차의 권위는 **잡 로그**였고, 로그는 태스크가 실제로 다시 돌아
(`> Task :…:gateway-service:integrationTest`) 그 테스트가 **PASSED**, `BUILD SUCCESSFUL`
임을 보였다.

### `tamperedTokenSignatureReturns401` — 3회 관측, 판정 보류

| 회차 | 결과 |
|---|---|
| 1차 (첫 CI 실행) | **FAIL** — 5s blocking read timeout |
| 2차 (재실행) | PASS |
| 3차 (bite 커밋 실행) | PASS |

🔴 **"flake 다" 라고 결론짓지 않는다.** 이 스위트는 **CI 에서 처음 도는 것이라 기준선이
없고**, 1적 2녹은 원인을 가리지 못한다(콜드 컨텍스트 워밍업 · 러너 포화 · 실제 타임아웃
여유 부족이 전부 부합한다). 사실만 적는다: **첫 실행이 빨갛고 이후 둘이 초록이었다.**
⇒ 이 레인이 main 에 들어가면 그 테스트가 간헐적으로 main 을 빨갛게 만들 수 있다.

## AC-3 (bite) — 잡이 이 스위트를 **본다**

베이스의 `route[0]` 재작성 타깃을 `/api/community` → `/api/BITE` 로 일부러 깨고 push:

- 잡 결과 **failure** ✅
- 실패가 **컴파일이 아니라 단언**이다 — `GatewayRouteRewriteTest` **10 중 4** 실패,
  나머지 4개 클래스는 전부 초록.
- 🔵 **정확히 4개인 것이 이 bite 의 정밀도**다. 깬 것은 `route[0]`(community) 하나이고
  artist/artist-groups/fandoms 는 인덱스 1~3 에 따로 배선돼 있다 ⇒ community 를 겨냥한
  케이스 **정확히 4개**만 죽었다. "잡이 빨개졌다" 가 아니라 **"잡이 내가 깬 바로 그것을
  본다"** 가 증명된다.
- 🔵 첫 bite 커밋은 주석을 괄호 안에 넣어 **컴파일 에러**를 냈다. 그것은 약한 bite 다 —
  잡이 모듈을 빌드한다는 것만 보이고 스위트의 단언이 읽히는지는 증명하지 못한다. 그래서
  문법을 고쳐 **단언으로 실패하게** 다시 냈다.
- bite 는 되돌렸다(pre-bite 커밋 대비 이 파일 diff 없음).

## AC-5 — 잡 시간

| | |
|---|---|
| 게이트웨이 합류 전 (참고: 같은 런의 다른 통합 잡들) | erp 3m57s · finance 3m44s · scm 3m19s |
| **팬 통합 잡 (게이트웨이 포함)** | **2m56s ~ 2m44s** |

Redis 컨테이너 1개가 추가됐지만 잡 시간은 형제 통합 잡들과 같은 대역이고 타임아웃에서
멀다 ⇒ **분리 불필요**(추측으로 나누지 않고 측정값을 남긴다).
