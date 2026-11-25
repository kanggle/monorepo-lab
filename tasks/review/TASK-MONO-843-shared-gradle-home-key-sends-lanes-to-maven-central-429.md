# Task ID

TASK-MONO-843

# Title

재사용 워크플로 레인들이 gradle-home 캐시 키 하나를 나눠 써서, 자기 의존성이 빠진 캐시를 복원하고 Maven Central 에 받으러 갔다가 429(Too Many Requests)를 맞는다 — 2026-11-23~25 사흘에 실패 잡 11개 · `main` 빨강 6번

# Status

review

# Owner

monorepo

# Task Tags

- ci
- build

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (CI 설정 · 래퍼 스크립트 — 단 AC-0 은 실측부터)
> 기안 2026-11-25 UTC. 출처: 세션 인계(2026-11-25)의 «Maven Central 다운로드 실패가 또 반복되면 — 먼저 빈도를 재라» 후보를 실측.

# Dependency Markers

- 선행(형제 수리): `TASK-MONO-797` — 같은 구조(«레인들이 키 하나를 공유하고 먼저 저장한 쪽이 이긴다»)를 not-found 기록 쪽에서 밝히고 `gradlew-retry-not-found.sh` 로 덮었다. 이 티켓은 같은 구조의 **다른 증상**(빠진 의존성 → 실제 다운로드 → 429)이다. 그 스크립트의 술어(`ArtifactNotFoundException` · `ModuleVersionNotFoundException`)는 429 를 잡지 않는다.

# Goal

## 실측 (2026-11-25 UTC, `gh run list --created ">=2026-10-26"` → CI · Nightly 455 런, 최종 실패 또는 재실행 20 런의 실패 잡 35개 로그 분류)

- Maven Central 다운로드 실패 잡 **11개** — 전부 **2026-11-23 21:01Z 이후**, 전부 `Received status code 429 from server: Too Many Requests`. 타임아웃 · 연결 끊김 · 5xx 는 **0**. 그 전(10-26 ~ 11-23 21:01Z) CI · Nightly 231 런에서는 0, 그 뒤 224 런에서 11.
  - `main` push 최종 실패 6(CI 4 · Nightly 2) · PR/nightly 재실행으로 통과 5.
  - 11개 모두 재사용 워크플로 레인: `_integration.yml`(job id `integration`) 6 · `_platform-e2e.yml`(job id `e2e`) 5.
  - 받으러 간 파일은 레인마다 다르다 — iam B: `spring-boot-starter-oauth2-authorization-server-3.4.1.jar` 등 20 · ecommerce B/C: `springdoc-openapi-starter-webmvc-ui-2.7.0.pom` · scm e2e: `shedlock-*-6.2.0.jar` · `resilience4j-spring-boot3-2.2.0.jar` 등 12 · fan: `resilience4j-annotations-2.2.0.jar`.
- 같은 창의 나머지 실패: 11-05~09 Playwright 브라우저 설치 실패 23 잡(11-09 이후 0 — 이 티켓 밖) · 11-24 18:05 integration 테스트 실패 1 · 11-01 task-id 충돌 가드 1.

## 기전 (로그로 확인한 것)

- 실패 잡의 `setup-gradle` 은 캐시를 **복원했다** — 단 정확한 키가 아니라 restore-key 접두어로: `gradle-home-v1|Linux-X64|integration[b8a35c2f917259958c9abe8f2ff1b5ff]-<다른 sha>`.
- **iam B(run 45486339490)와 ecommerce B(run 45582005513)가 같은 접두어 `integration[b8a35c2f…]` 를 복원했다** — 프로젝트가 달라도 재사용 워크플로의 job id 가 같으면 키가 같다(797 이 `e2e[…]` 에서 본 것과 같은 구조).
- 그래서 복원된 Gradle home 은 **마지막으로 저장한 다른 레인**의 의존성을 담고, 이 레인에만 필요한 것은 비어 있다 → 실행마다 그것을 Maven Central 에 다시 받는다 → 다운로드 수가 레인 수 × 실행 수로 커지고 11-24~25 의 런 폭증(전체 워크플로 기준 하루 127~163 런, 11-18~22 는 하루 1~3) 속에서 429 를 맞는다.
- 🔴 **재지 않은 것:** 429 가 11-23 부터 시작된 이유(우리 요청 수 증가 · 공유 러너 IP · Maven Central 쪽 정책 변경 중 무엇인지). 10-27~28 이틀에도 CI · Nightly 97 런이 있었는데 429 는 0 이었다 — «런이 많아서» 만으로는 설명이 끝나지 않는다. 이 티켓은 원인을 그쪽에 걸지 않는다: 어떤 이유로 429 가 오든 **애초에 받을 필요가 없는 파일을 받는 것**을 없애는 게 목표다.

# Scope

## In Scope

1. **AC-0 실측 먼저** (구현 전):
   - (a) `gradle/actions/setup-gradle@v4` 가 레인별로 키를 가르는 방법을 **그 액션의 문서 · 소스에서** 확인한다(추측 금지 — 입력 이름 · 환경변수 이름을 인용). 재사용 워크플로를 부르는 쪽이 넘기는 레인 식별자(예: matrix · 입력값)를 키에 넣을 수 있는지.
   - (b) 실패한 레인에서 «복원은 됐는데 받으러 간» 파일 수를 센다(키 분리 후 0 에 가까워져야 하는 기준값).
   - (c) Actions 캐시 사용량(`gh cache list` 합계)과 저장소 한도 — 키를 레인별로 나누면 항목 수가 늘어난다.
   - (d) Gradle 이 429 를 스스로 재시도하는지(이 저장소의 Gradle 8.14.3 기준, 문서 · 실제 로그에서).
2. 재사용 워크플로 두 개(`_integration.yml` · `_platform-e2e.yml`)의 gradle-home 캐시 키에 **레인 식별자**를 넣는다(AC-0 (a) 의 방법으로).
3. `gradlew-retry-not-found.sh` 와 같은 원칙(원인을 이름 붙여 한 번만, 숨기지 않음)으로 **429 만** 한 번 재시도할지 — AC-0 (d) 결과로 판단해 Implementation Evidence 에 «함/안 함 + 이유» 를 적는다. 하면 술어는 `status code 429` 만 물고(다른 실패는 재시도 금지 — 797 의 «what this deliberately does NOT do» 유지), `::warning::` 을 남긴다.

## Out of Scope

- 🔴 **미러 저장소 추가**(Google Maven Central 미러 · 사설 프록시 등) — 의존성을 받는 출처(공급망)를 바꾸는 결정이라 **소유자 몫**. 2 · 3 뒤에도 429 가 남으면 그 수치를 들고 소유자에게 묻는다.
- `ci.yml` 의 비재사용 잡들(`build-and-test` · `*-boot-jars`) — 11개 중 0. 수치가 생기면 별 티켓.
- 11-05~09 Playwright 설치 실패(이미 멈춤) · 11-24 18:05 integration 테스트 실패.

# Acceptance Criteria

- [ ] AC-0: In Scope 1 의 (a)~(d) 를 실측해 Implementation Evidence 에 적는다(인용 · 숫자 · 명령). (a) 가 «레인별 키를 가를 방법이 없다» 로 나오면 구현하지 말고 그 근거와 함께 멈춘다.
- [ ] AC-1: 서로 다른 프로젝트의 `_integration.yml` 레인 둘(예: iam B · ecommerce B)이 **서로 다른** gradle-home 키를 저장 · 복원한다 — PR CI 로그의 `Restored cache entry with key …` / `Cache saved …` 줄로 보인다. `_platform-e2e.yml` 레인도 같은 방법으로.
- [ ] AC-2: 키 분리가 실제로 효과가 있다 — 같은 레인을 **두 번째로** 돌렸을 때(캐시가 그 레인 자신의 것으로 채워진 뒤) Maven Central 다운로드 수가 AC-0 (b) 기준값보다 줄어든다. 숫자 두 개를 적는다.
- [ ] AC-3: 재시도 래퍼를 바꿨다면 — 429 를 주입한 로그(또는 고정 fixture)로 «재시도 1회 · warning» 이, 429 가 아닌 실패로 «재시도 없음 · 원래 종료코드» 가 각각 증명된다(bite 는 주입이 실제로 들어갔는지 먼저 확인).
- [ ] AC-4: PR CI 실패 0 · 가드 전부(스테이지 뒤) rc=0 · `scripts/` 에 파일을 더하거나 뺐다면 **모든** 가드를 돌린다(`check-ls-files-guard-count.sh` 포함).
- [ ] AC-5: 머지 뒤 `main` 에서 7일 동안 429 실패 잡 수를 다시 센다(이 티켓 기준 사흘 11). 0 이 아니면 그 수치를 Out of Scope 의 미러 결정 근거로 소유자에게 넘긴다.

# Related Specs

- `platform/git-workflow-policy.md`(CI 필수 체크 — 이 레인들은 필수 체크가 아니다: 빨강이어도 머지는 막히지 않는다. 그래서 `main` 빨강 6번이 조용히 지나갔다)
- `.github/actions/setup-java-gradle/gradlew-retry-not-found.sh` 머리 주석(`TASK-MONO-797`)

# Related Contracts

- 없음.

# Edge Cases

- 키를 레인별로 나누면 첫 실행은 캐시 미스(전부 다운로드) — 바로 그 실행이 429 를 맞을 수 있다. 첫 저장이 `main`(cache-read-only: false)에서 일어나야 PR 레인이 쓸 수 있다는 점(PR 은 read-only)도 함께.
- 레인 식별자가 매 실행 바뀌는 값(sha · run id)이면 캐시가 영영 재사용되지 않는다 — 안정적인 값이어야 한다.
- Actions 캐시 한도를 넘으면 오래된 항목이 밀려나 다른 레인이 미스가 난다 — AC-0 (c).

# Failure Scenarios

- 키는 갈렸는데 `gradle-dependencies-v1-<hash>` 같은 하위 캐시 항목이 여전히 레인 간에 공유되어 의존성이 빠진다 — AC-2 의 «두 번째 실행 다운로드 수» 가 이것을 잡는다(키 이름만 보는 AC-1 로는 못 잡는다).
- 429 재시도가 실패 원인을 덮어 레인이 조용히 느려진다 — warning 이 매 실행 남으므로 AC-5 재측정 때 그 수도 센다.

# Test Requirements

- CI 로그 대조 · (래퍼를 바꿨다면) fixture 기반 bite · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

## AC-0 실측

- **(a) 레인별 키 방법 — 있다.** `gradle/actions` `v4`(`ed408507e`) `sources/src/caching/cache-key.ts`:
  - 키 = `${prefix}gradle-<name>-v1|<env>|<job>[<jobInstance>]-<execution>`, restore 키 = `[<jobInstance>]` 까지 → `<job>` 까지 → `<env>` 까지.
  - `getCacheKeyJobInstance()` 는 `GRADLE_BUILD_ACTION_CACHE_KEY_JOB_INSTANCE` 가 있으면 그 값을, 없으면 `hashStrings([github.context.workflow, matrix])`. 재사용 워크플로 안에서 `github.context.workflow` 는 **호출한 쪽** 이름(`CI` · `Nightly …`)이고 matrix 는 비어 있다 → 호출자 14곳이 워크플로마다 같은 해시(`integration[b8a35c2f…]` · CI `e2e[b8a35c2f…]` · nightly `e2e[e9cc7a5f…]`).
  - 🔵 `_JOB` 이 아니라 `_JOB_INSTANCE` 를 덮는 이유: `[…]` 만 바뀌어 restore 의 둘째 단계(`integration` — 아무 형제)가 남는다. 첫 실행은 지금과 같은 수준으로 복원되고, 둘째 실행부터 자기 것.
  - 레인 식별자 = `_integration.yml` 의 `artifact-name` · `_platform-e2e.yml` 의 `report-artifact`. 둘 다 `required: true`. 호출자 14곳(`ci.yml` 11 · `nightly-e2e.yml` 3)의 값 14개가 전부 다르고, 실행마다 바뀌는 값이 없다(iam · ecommerce 의 `${{ matrix.shard }}` 는 고정 A/B/C).
- **(b) 기준값 — 받으러 간 레인 수.** 🔴 단위 정정: 티켓은 «다운로드 파일 수» 를 적었지만 로그에 그 숫자가 없다(Gradle 은 `--info` 없이 다운로드를 찍지 않는다). 대신 setup-gradle 이 **복원한 것과 다른 `gradle-dependencies-v1-<hash>` 항목을 저장한 레인** = 그 실행에서 무언가를 새로 받은 레인을 센다(항목 키는 내용 해시라, 새 항목 = 새 파일).
  - `main` 2026-11-25 12:09 · 12:32 CI 2 런, 12:09~13:30 nightly 8 런(`lanes.js` — scratchpad, 커밋 안 함). 통합 레인 11개는 전부 같은 의존성 항목 `…ad79619b` 를 복원했고, **12:32 CI 에서 5개(master · fan · iam A · iam B · erp)**, 12:09 CI 에서 2개(scm · fan)가 두 번째 항목을 저장했다. CI e2e 3개 중 1(12:32) · 2(12:09). nightly e2e: scm 은 레인이 돈 7 런 중 7, fan 은 7 중 6(12:09 는 정확 키 복원이라 없음), wms 0.
  - 같은 레인이 매번 **같은** 두 번째 항목을 쓴다(fan 통합 `…1fbd036e` 2/2 · nightly scm `…9abcce89` 7/7 · nightly fan `…ce28d12c` 6/6). 그 파일들은 캐시에 이미 있는데, 복원되는 gradle-home 이 형제의 것이라 가리키지 않을 뿐이다 = 이 티켓의 기전이 그대로 보인다.
- **(c) 캐시 사용량.** `actions/cache/usage` = **10.68 GB · 1,145 항목**(2026-11-25 13:4xZ) — 이미 기본 한도 10 GB 근처라 LRU 로 밀어내는 중. 내역: gradle-home 300 항목 4.7 GB(평균 ≈16 MB) · gradle-dependencies 22 항목 3.6 GB(97~317 MB) · groovy-dsl 426 · instrumented-jars 426 · node 캐시 4.
  - 바뀌는 것: 지금도 런마다 레인별로 저장을 시도하지만 키가 같아 첫 레인만 쓰인다 → 바꾸면 `main` 런마다 gradle-home 이 최대 14개(≈220 MB) 쓰인다. 의존성 항목은 내용 해시라 **늘지 않는다**(위 두 번째 항목들은 이미 있다). 늘어나는 gradle-home 항목은 한 번도 다시 복원되지 않는 옛 sha 항목부터 LRU 로 밀린다. 매 런 복원되는 큰 의존성 항목은 최근 사용이라 남는다 — 🔴 이것은 추론이다. AC-5 재측정 때 `restore-key` 미스(=OS 단계까지 내려간 복원)를 같이 센다.
- **(d) Gradle 의 429 재시도 — 이미 한다.** `gradle/gradle` `v8.14.3`:
  - `NetworkingIssueVerifier.isTransientClientError` = `SC_REQUEST_TIMEOUT || SC_TOO_MANY_REQUESTS`.
  - `NetworkOperationBackOffAndRetry` = 3회, 1000 ms 에서 시작해 두 배(= 1 s · 2 s 대기, 내부 속성 `org.gradle.internal.network.retry.max.attempts` · `.initial.backOff`).
  - 즉 실패한 11잡은 Gradle 의 재시도 3회 **뒤의** 실패다.

## In Scope 3 판단 — 429 재시도 래퍼: **하지 않음**

- Gradle 이 이미 429 를 일시 오류로 보고 재시도한다. 그 위에 빌드 전체를 다시 도는 래퍼를 얹으면, 429 가 계속될 때 레인 시간이 두 배가 되고 원인은 그대로다.
- `org.gradle.internal.*` 속성으로 재시도를 늘리는 것도 하지 않는다 — 내부 속성이라 Gradle 업그레이드에서 조용히 사라질 수 있다.
- 키 분리 뒤에도 429 가 남으면(AC-5) 그 수치가 미러 결정(소유자)의 근거다. → **AC-3 은 해당 없음(⚪ — 래퍼를 바꾸지 않았다).**

## 변경

- `_integration.yml` · `_platform-e2e.yml`: 잡 수준 `env: GRADLE_BUILD_ACTION_CACHE_KEY_JOB_INSTANCE: ${{ inputs.artifact-name }}` / `${{ inputs.report-artifact }}`. 잡 수준인 이유: setup-gradle 의 post 단계(저장)도 같은 키를 봐야 한다. 잡 id · 체크 이름은 그대로(필수 체크 이름 영향 없음).
- `gradlew-retry-not-found.sh` 머리 주석 · `_platform-e2e.yml` 797 주석: «모든 호출자가 키 하나를 공유한다» 를 현재 사실로 고침(첫 실행 fallback 은 남는다).

## 로컬 가드 (스테이지 뒤)

- `scripts/check-*.sh` · `*.mjs` 51개: 49 rc=0, 비0 둘 = 환경 전제 — `check-erp-single-tenant-ratchet.sh`(데모 스택 미기동 — «안 떠 있으면 실패») · `check-prerendered-demo-verdict.sh`(환경 변수 필요). 둘 다 `main` 에서도 rc=1(같은 사유, 842 때와 같음). YAML 두 파일 `yaml.safe_load` 통과.

# Definition of Done

- AC 전부 · 머지 · 4-dim close · AC-5 재측정은 머지 7일 뒤(필요하면 `⏳` 후속 티켓).
