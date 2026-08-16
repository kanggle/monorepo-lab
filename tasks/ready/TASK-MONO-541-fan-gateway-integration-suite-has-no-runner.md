# Task ID

TASK-MONO-541

# Title

팬 게이트웨이 통합 스위트에 **러너가 없다** — `check` 는 태그로 빼고 통합 레인은 넷만 나열해서, 그 프로젝트의 엣지 라우트 계약 전체가 어디서도 돌지 않는다

# Status

ready

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

- [ ] **AC-0 (전제 재확인)** — 착수 시 팬 통합 잡의 `gradle-tasks` 에 게이트웨이가
      **여전히 없는지** 확인한다. 있으면 **STOP**(누군가 이미 넣었다).
- [ ] **AC-1** — 팬 통합 잡이 `gateway-service:integrationTest` 를 포함한다.
- [ ] **AC-2 (실행 증거 — 이것이 판정이다)** — 그 잡의 산출물에서
      `GatewayRouteRewriteTest` 의 `tests` · `failures` · `errors` · **`skipped`** 네
      값을 모두 읽어 적는다. 🔴 **`BUILD SUCCESSFUL` 도 잡 초록도 판정이 아니다** —
      `BE-049` 에서 실제로 `rc=0 · BUILD SUCCESSFUL` 인데 XML 이 `skipped=10` 인 회차가
      나왔다(Docker 미검출 → `disabledWithoutDocker` 전량 스킵). **`skipped=0` 과
      `tests≥10` 을 함께** 단언한다.
- [ ] **AC-3 (bite — 레인이 실제로 무는가)** — 라우트를 일부러 깨서(예: 재작성 정규식의
      접두사를 바꿔) **그 잡이 빨개지는지** 확인하고 되돌린다. 🔴 초록만 보면 "잡이
      추가됐다" 와 "잡이 그 스위트를 본다" 가 구별되지 않는다.
- [ ] **AC-4 (모집단)** — 위 Scope 의 두 집합을 8개 프로젝트에 대해 세고 차집합을 티켓
      본문에 표로 남긴다. 0 건이면 0 건이라고 적는다. 발견된 낙오마다 후속 티켓 ID 를 적는다.
- [ ] **AC-5** — 잡 시간 증가를 적는다. 팬 통합 잡이 타임아웃에 접근하면 분리를 제안하되
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
