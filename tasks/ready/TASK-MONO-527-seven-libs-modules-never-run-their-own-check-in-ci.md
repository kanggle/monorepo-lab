# Task ID

TASK-MONO-527

# Title

repo-root `libs/` 13개 중 **6개만 CI에서 자기 `check` 를 돌린다** — 나머지 7개의 테스트와 ADR-MONO-048/049 가드는 한 번도 실행된 적이 없고, 그중 하나는 자기 주석에 "매 변경마다 돌아간다" 고 적어 두었다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- shared-library
- test

---

# 배경 — `TASK-MONO-521` AC-5 가 재다가 나왔다

MONO-521 은 공유 클래스를 `libs/java-security-servlet` 로 승격하면서 AC-5("승격한 모듈이
**실제로 테스트되는 잡에 들어가는지** 확인할 것")를 이행했다. 답은 **아니오**였고, 원인은
그 모듈 하나가 아니었다.

## 실측 (2026-08-13 UTC, `--dry-run` 으로 태스크 그래프 확인)

`.github/workflows/` 전체에서 `:libs:<module>:check` 를 부르는 곳은 `ci.yml` 의 한 잡뿐이고,
거기 적힌 것은 **6개**다:

```
:libs:java-common:check  :libs:java-messaging:check  :libs:java-observability:check
:libs:java-security:check  :libs:java-test-support:check  :libs:java-web:check
```

`settings.gradle` 이 include 하는 repo-root libs 모듈은 **13개**. 빠진 **7개**:

| 모듈 | 자기 `check` 에 달린 것 | CI 실행 |
|---|---|---|
| `java-security-servlet` | 테스트 9클래스 + `assertClasspathNeutrality` | ❌ → MONO-521 이 **이것만** 추가함 |
| `java-gateway` | 테스트 + **`assertNoServletOnReactiveEdge`** | ❌ |
| `java-web-servlet` | 테스트 | ❌ |
| `java-notification` | 테스트 | ❌ |
| `payment-core` / `payment-portone` / `payment-toss` | 테스트 | ❌ |

추가로 **루트 프로젝트의 `check`** 에 달린 `assertNoApiOnSharedLibs` 도 실행되지 않는다 —
CI 는 루트 `:check` 를 부르지 않고 명시적 태스크 목록만 부른다.

## 🔴 컴파일 의존으로 닿는 것은 실행이 아니다

`:projects:fan-platform:apps:artist-service:check --dry-run` 의 그래프에서
`libs:java-security-servlet` 은 이렇게만 나타난다:

```
:libs:java-security-servlet:compileJava SKIPPED
:libs:java-security-servlet:processResources SKIPPED
:libs:java-security-servlet:classes SKIPPED
:libs:java-security-servlet:jar SKIPPED
```

`test` 도 `check` 도 `assertClasspathNeutrality` 도 **없다**. CLAUDE.md § Project-scoped
shared modules 가 경고한 그대로다 — *"컴파일 의존으로만 닿는 모듈은 자기 테스트를 돌리지
않는다."* 소비 서비스의 `:check` 가 초록인 것은 그 모듈이 **컴파일된다**는 뜻일 뿐이다.

🔵 **대조군으로 계측기를 검증했다**: 같은 dry-run 출력에서 `:libs:java-security:check` 는
`assertClasspathNeutrality` 와 `test` 를 정상적으로 끌고 온다. 즉 위의 부재는 탐지 실패가
아니라 실제 부재다.

## 🔴🔴 가드가 자기 도달성을 잘못 적고 있다

`build.gradle:120-122` 는 이렇게 적혀 있다:

```groovy
// The root `check` runs in CI's "Build & Test (JDK 21, Linux)" job, so this assertion is
// reachable on every code change — not only when someone happens to build the module they
// broke. A guard that does not run reports green (TASK-MONO-359 / TASK-MONO-360).
tasks.named('check') { dependsOn 'assertNoApiOnSharedLibs' }
```

**그 잡은 루트 `check` 를 부르지 않는다.** 주석이 인용한 바로 그 교훈("돌지 않는 가드는
초록을 보고한다")의 실례가 되어 있다. `assertNoServletOnReactiveEdge` 도 같은 상태다 —
ADR-MONO-049 § D3 이 "converse 는 가정이 아니라 단언된다" 고 적은 그 단언이다.

---

# Goal

repo-root `libs/` 의 모든 모듈이 자기 `check`(테스트 + 등록된 가드)를 CI 에서 실행한다.
그리고 **새 모듈이 목록에서 빠지면 무언가 실패한다** — 지금은 조용하다.

---

# Scope

## In Scope

- `ci.yml` libs 잡의 태스크 목록에 누락 모듈 추가
- 루트 `assertNoApiOnSharedLibs` 를 실제로 실행되는 자리로 배선
- **목록 드리프트 가드** — `settings.gradle` 의 `libs:*` include 집합과 CI 태스크 목록의
  차집합이 비어 있지 않으면 실패하는 검사
- 추가로 켠 모듈이 RED 면 그 RED 를 고치는 것까지

## Out of Scope

- `libs/java-security-servlet` 추가 — `TASK-MONO-521` 에서 **완료**
- `projects/<p>/libs/*`(finance-common)은 이미 CI 에 있다. 다만 AC-0 이 다시 센다
- 각 모듈 테스트의 내용 개선 — 여기서는 **실행되게** 하는 것까지

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시점에 다시 센다: `settings.gradle` 의 `libs:*` include 수,
      워크플로가 부르는 `:libs:*:check` 수, 그 차집합. 🔴 위 표는 **2026-08-13 UTC 의 수**이고
      MONO-521 이 그중 하나를 이미 바꿨다. 🔴 `projects/*/libs/*` 도 같은 축으로 함께 센다
- [ ] **AC-1 (실행)** — 누락 모듈 전부가 CI 에서 자기 `check` 를 돈다.
      🔴 **켜자마자 RED 일 수 있다** — 한 번도 안 돌아간 테스트다. RED 를 만나면 그것이
      이 티켓의 진짜 산출물이지, 그 모듈을 목록에서 빼는 근거가 아니다
- [ ] **AC-2 (루트 가드)** — `assertNoApiOnSharedLibs` 가 실행되는 잡에 들어간다.
      `build.gradle:120-122` 의 **틀린 주석도 함께 고친다**
- [ ] **AC-3 (드리프트 가드)** — 새 `libs:*` 모듈을 include 하고 CI 목록에 안 넣으면
      **실패한다**. 🔴 가드 자체가 실행되는 잡에 있는지 확인할 것(이 티켓이 잡아낸 결함이
      정확히 그 모양이다)
- [ ] **AC-4 (bite)** — AC-3 가드에 가짜 모듈을 하나 넣어 **실제로 RED 가 되는지** 본다.
      🔴 그리고 그 bite 는 강제 rerun 없이 물어야 한다(캐시된 UP-TO-DATE 가 초록을 보고하면
      가드가 아니라 캐시를 잰 것이다)
- [ ] **AC-5 (도달 판정의 술어)** — "목록에 문자열이 있다" 를 판정으로 쓰지 않는다.
      `--dry-run` 태스크 그래프에 그 모듈의 `test` 가 **나타나는지**로 판정한다.
      🔴 대조군(이미 도는 모듈)이 같은 실행에서 나타나는지도 함께 확인해 계측 실패와 구분한다

---

# Related Specs

- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md)
- `docs/adr/ADR-MONO-048-*` § D1 (`assertNoServletOnReactiveEdge` 의 근거)
- `docs/adr/ADR-MONO-049-*` § D2 / § D3 (`assertNoApiOnSharedLibs`, `assertClasspathNeutrality`)
- `CLAUDE.md` § Project-scoped shared modules (컴파일 의존은 테스트를 돌리지 않는다)
- `tasks/done/TASK-MONO-521-*` (이 결함을 발굴한 AC-5)

# Related Contracts

- 없음 — 빌드/CI 표면이고 HTTP·이벤트 계약을 바꾸지 않는다

# Edge Cases

- 켠 모듈이 Testcontainers 를 요구할 수 있다 — 그러면 Docker-free 잡이 아니라
  integration 레인이 맞는 자리다. 잡 선택을 먼저 정할 것
- `payment-portone` / `payment-toss` 는 **소비자가 아직 없다**(settings.gradle 주석).
  소비자 0인 모듈의 테스트가 CI 시간을 쓰는 것이 맞는지는 판단 대상이지만,
  🔴 "안 돌린다" 를 고르면 그것을 **적어야** 한다 — 지금처럼 침묵으로 두지 않는다
- CI 벽시계가 늘어난다 — [`project_ci_wallclock_playbook`] 의 측정 방식을 따를 것

# Failure Scenarios

- 🔴 **목록에 줄만 추가하고 dry-run 으로 확인하지 않는다** — 오타 하나면 태스크 이름이
  안 맞아 Gradle 이 실패하거나(즉시 발견) 조용히 다른 것을 돌린다. AC-5 가 그래서 있다
- 🔴 **RED 를 만나 그 모듈을 목록에서 뺀다** — 이 티켓 이전 상태로 되돌리는 것이고,
  그때는 "한 번도 안 돌았다" 가 아니라 "돌려 봤더니 빨개서 껐다" 가 된다. 더 나쁘다
- 🔴 **AC-3 가드를 만들고 그 가드를 안 도는 잡에 둔다** — 재귀적으로 같은 결함이다

# Test Requirements

- AC-4 bite: 가짜 `libs:*` include → 가드 RED 확인 → 원복
- AC-5: `--dry-run` 그래프에 각 모듈 `test` 존재 + 대조군 동시 확인

# Definition of Done

- [ ] AC-0 재측정 기록
- [ ] 누락 모듈 CI 실행 (RED 있으면 해결)
- [ ] 루트 가드 배선 + 주석 정정
- [ ] 드리프트 가드 + bite 증거
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=**Sonnet** — CI 목록 + 가드 배선이라 기계적이다. 단, AC-1 에서
처음 도는 테스트가 RED 면 그 지점부터는 해당 모듈 도메인의 판단이 필요하다.
