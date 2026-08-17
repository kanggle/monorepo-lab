# Task ID

TASK-MONO-546

# Title

모든 통합 테스트가 상속하는 두 클래스에 **자기 테스트가 없다** — `TASK-MONO-545` 의 `allow-empty` 면제가 가린 것

# Status

ready

# Owner

monorepo

# Task Tags

- testing
- libs

---

# 배경

`TASK-MONO-545` 가 넣은 0-발견 가드가 **첫날에 `libs/java-test-support` 를 지목**했다. 확인해
보니 그 모듈은 `src/test` 가 아예 없는 **순수 `java-test-fixtures` 모듈**이라 *회귀가 아니라
모양* 이었고, 근거를 적어 `allow-empty` 에 올렸다.

🔴 **면제는 사실을 하나 더 드러냈다**: 이 모듈이 담은 두 클래스는

- `AbstractIntegrationTest` — 이 저장소의 **모든** Testcontainers 통합 테스트가 상속하는 베이스
- `DockerAvailableCondition` — 그 베이스에 `@ExtendWith` 로 물린 JUnit 5 `ExecutionCondition`

이고, **둘 다 자기 테스트가 없다**. 소비 서비스의 통합 테스트가 초록이면 "잘 도는 것처럼"
보이지만, 그건 **Docker 가 있는 환경에서만** 그렇다.

## 이 공백이 실제로 무엇을 놓치는가

`DockerAvailableCondition` 의 javadoc 이 **이미 겪은 사고**를 적어 두고 있다:

> `@EnabledIf("isDockerAvailable")` 로 서브클래스가 스스로를 막으려 하면, 그 애너테이션을
> 평가하려고 메서드를 호출하는 순간 **클래스 초기화**가 일어나고, 부모의 `static { }` 이
> 먼저 돌아 `ExceptionInInitializerError` 로 죽는다.

즉 `@ExtendWith(DockerAvailableCondition.class)` **한 줄이 사라지면** Docker 없는 개발 머신에서
모든 통합 테스트 클래스가 *SKIPPED* 가 아니라 **초기화 에러**로 죽는다. 그리고 **CI 는 이걸
절대 못 잡는다** — CI 러너에는 Docker 가 항상 있으므로 두 경로가 같은 결과를 낸다.
🔴 **CI 가 구조적으로 볼 수 없는 회귀**이므로, 잡을 방법은 그 배선을 **직접 단언**하는 것뿐이다.

## 🔴 이 티켓의 진짜 마감 조건은 면제 회수다

`TASK-MONO-545` 가 `.github/workflows/ci.yml` 의 `summarise-test-results` 호출에 넣은

```
allow-empty: … libs/java-test-support
```

는 **이 모듈에 테스트가 없다는 사실에만 근거한 예외**다. 테스트가 생기면 그 근거가
사라지므로 **같은 PR 에서 그 줄을 지워야 한다**. 안 지우면 이 모듈은 앞으로 **영구히 가드
밖**에 남는다(면제는 조용히 살아남는다).

---

# Goal

`AbstractIntegrationTest` 와 `DockerAvailableCondition` 이 **자기 테스트를 갖고**,
`libs/java-test-support` 가 **`allow-empty` 없이** 0-발견 가드를 통과한다.

---

# Scope

1. `libs/java-test-support/build.gradle` 에 테스트 의존성 추가
   (형제 관례 = `junit-jupiter` + `assertj-core`, 버전은 Spring Boot BOM).
2. `libs/java-test-support/src/test/java/…` 에 두 테스트 클래스 추가.
3. `.github/workflows/ci.yml` 에서 `allow-empty` 의 `libs/java-test-support` **제거**
   + 그 옆 주석에서 해당 근거 문단 제거.

## Out of Scope

- `AbstractIntegrationTest` 의 **동작 변경**. 이 티켓은 테스트를 붙이는 것이지 고치는 것이 아니다.
- 컨테이너를 실제로 띄우는 테스트. 이 모듈의 `test` 는 `build-and-test`(**Docker-free 설계**)에서
  돌므로, 여기 테스트는 컨테이너를 기동해선 안 된다.

---

# Acceptance Criteria

- [ ] **AC-0 (전제 실측)** — `AbstractIntegrationTest` 의 `static { }` 는 **클래스 초기화 시
      컨테이너를 띄운다**. 따라서 이 테스트가 그 클래스를 **초기화하지 않는다**는 것을
      먼저 확인한다: 애너테이션 조회는 초기화를 유발하지 않으므로
      `Class.forName(name, false, loader)` 로 **명시적으로** 로드할 것.
      🔴 이걸 틀리면 Docker-free 여야 할 유닛 잡이 MySQL+Kafka 를 띄운다.
- [ ] **AC-1 (`DockerAvailableCondition`)** — 최소한 다음을 단언한다:
      `ExecutionCondition` 구현 · 결과가 non-null 이며 예외를 전파하지 않음 ·
      **두 번 호출하면 같은 인스턴스**(프로브가 1회만 돈다는 설계) ·
      판정이 `DockerClientFactory.instance().isDockerAvailable()` 와 일치하고 사유 문자열이 그 분기의 것.
      🔴 **행사되지 않은 분기를 적을 것** — 이 호스트에 Docker 가 있으면 disabled 분기는
      **행사되지 않는다**. "테스트가 있다"와 "두 분기가 다 돈다"는 다르다.
- [ ] **AC-2 (`AbstractIntegrationTest` 배선 핀)** — `@Tag("integration")` 과
      `@ExtendWith(DockerAvailableCondition.class)` 가 붙어 있음, `abstract` 임,
      `sharedContainerProperties` 가 **static** 이며 `@DynamicPropertySource` 가 붙어 있음,
      `MYSQL`/`KAFKA` 가 `protected static` 임을 단언한다.
      🔵 이건 핀이므로 **근거를 계약(javadoc + 위 사고 기록)과 대조**해 적을 것 — 핀은 지키려던
      결함을 얼릴 수 있다.
- [ ] **AC-3 (bite)** — 각 단언이 실제로 무는지 확인한다. 최소 2건:
      `@ExtendWith` 를 떼면 AC-2 가 실패하고, `sharedContainerProperties` 에서 `static` 을 빼면
      그 단언이 실패한다. 확인 후 되돌린다.
      🔴 **컴파일 에러로 실패시키지 말 것** — 그건 모듈이 빌드된다는 것만 보인다.
- [ ] **AC-4 (면제 회수 + 가드가 실제로 센다)** — `ci.yml` 의 `allow-empty` 에서
      `libs/java-test-support` 를 지우고, CI **초록 회차**의 `build-and-test` 로그에서
      `java-test-support:check` 행의 `tests` 가 **0 이 아님**을 읽어 적는다.
      🔵 숫자를 요구하는 술어로 읽을 것(`TEST-SUMMARY … tests=[0-9]+`).
- [ ] **AC-5 (Docker-free 유지 확인)** — `build-and-test` 잡의 wall-clock 이 이전 회차 대비
      **컨테이너 기동 규모로 늘지 않았음**을 확인한다. 🔵 단일 표본을 성질로 승격하지 말고,
      "늘지 않았다"가 아니라 **관측된 값과 비교 대상**을 적을 것.

---

# Related Specs

- `platform/testing-strategy.md` — 통합 테스트 전략
- `.github/workflows/ci.yml` — `build-and-test` 의 `allow-empty`
- `.github/actions/summarise-test-results/action.yml` — 면제 메커니즘 (`TASK-MONO-545`)

# Related Contracts

없음 — 테스트 지원 라이브러리이며 API·이벤트 계약을 건드리지 않는다.

---

# Edge Cases

- **`DockerClientFactory` 프로브가 CI 러너에서 느릴 수 있다** — 컨테이너를 띄우진 않지만
  데몬 핑은 한다. 유닛 잡에 수 초가 붙는다면 그 값을 적을 것.
- **`java-test-fixtures` 플러그인은 `test` 소스셋에 `testFixtures` 출력을 자동으로 얹는다** —
  별도 의존 선언 없이 두 클래스를 참조할 수 있어야 한다. 안 되면 그 사실이 먼저다.
- **disabled 분기는 Docker 있는 호스트에서 행사 불가** — 이걸 억지로 행사하려고 프로브를
  주입 가능하게 리팩터링하는 것은 **동작 변경**이므로 Out of Scope. 못 한 것을 적는다.

# Failure Scenarios

- **`allow-empty` 를 안 지운다** → 테스트를 붙여 놓고도 이 모듈은 영구히 가드 밖에 남는다.
  이 티켓의 실질적 실패.
- **`AbstractIntegrationTest` 를 초기화하는 테스트를 쓴다** → Docker-free 유닛 잡이 MySQL+Kafka 를
  띄우고, 그 사실은 잡이 느려진 것으로만 나타난다.
- **핀만 쓰고 bite 를 안 한다** → 애너테이션 이름을 오타 내도 통과하는 단언이 남는다.
