# Task ID

TASK-MONO-527

# Title

repo-root `libs/` 13개 중 **6개만 CI에서 자기 `check` 를 돌린다** — 나머지 7개의 테스트와 ADR-MONO-048/049 가드는 한 번도 실행된 적이 없고, 그중 하나는 자기 주석에 "매 변경마다 돌아간다" 고 적어 두었다

# Status

review

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

# 🟢 착수 (2026-08-13 UTC) — 전 AC 완료

## 🔴 AC-0 재계수에서 **내 탐지식이 먼저 틀렸다**

첫 계수기는 `settings.gradle` 을 그대로 훑어 `'…'` 를 문자열로 읽었다. 그 파일의 긴 `//`
주석 블록에는 **산문 아포스트로피**가 있고, 그래서 문장 조각이 모듈 이름으로 잡혀
*"project-scoped libs 3개"* 라는 거짓 수가 나왔다(실제 1개). 주석을 먼저 제거하고 경로
형식(`[a-z0-9-]` 세그먼트만)을 강제해 다시 셌다.

**확정 수치**:

| 모집단 | include | 자기 `check` 실행 | 누락 |
|---|---|---|---|
| repo-root `libs/` | **13** | **7** | **6** |
| `projects/*/libs/*` | 1 | 1 | **0** ✅ |

⇒ 티켓 제목의 *"13개 중 6개만"* 은 **MONO-521 이 java-security-servlet 을 추가하기 전** 수치였다.
착수 시점의 참값은 **7 실행 / 6 누락**이다. 누락 6: `java-gateway` · `java-notification` ·
`java-web-servlet` · `payment-core` · `payment-portone` · `payment-toss`.

🔵 **`projects/*/libs/*` 는 이미 정상**이었다(finance-common). 티켓이 물어보라고 한 축이고,
답은 "문제 없음" 이다.

## AC-1 — 켜 보니 **전부 GREEN**, 그리고 커버리지는 작지 않았다

로컬 6개 `:check` 전부 통과. 🔵 티켓이 경고한 "켜자마자 RED" 는 일어나지 않았다.

🔴 **그런데 "몇 개가 늘었나" 를 처음엔 0으로 셌다** — `tests="N"` 속성을 grep 했는데 그 형식이
아니었다. `<testcase` 를 세니:

| 모듈 | 그동안 CI 에서 한 번도 안 돈 테스트 |
|---|---|
| java-gateway | 77 |
| java-web-servlet | 43 |
| java-notification | 40 |
| payment-portone | 25 |
| payment-toss | 18 |
| payment-core | 3 |
| **합계** | **206** |

여기에 `assertNoServletOnReactiveEdge`(ADR-MONO-048 § D1)와
`libs:java-security-servlet:assertClasspathNeutrality` 가 더해진다.

## AC-2 — 루트 가드: 배선 + **주석 정정**

`:assertNoApiOnSharedLibs` 를 libs 잡 태스크 목록에 명시했다. `build.gradle` 의
`tasks.named('check') { dependsOn … }` 는 **남겼다** — 그것은 개발자 로컬 `./gradlew check`
용 표면이다. 주석은 지우지 않고 **정정**했다: *"루트 check 가 CI 잡에서 돈다"* 는 진술이
처음부터 거짓이었고, 그 주석이 인용한 문장(*"돌지 않는 가드는 초록을 보고한다"*)이
자기 자신을 서술하고 있었다는 것까지 적었다. **두 표면은 서로를 추론할 수 없다**는 것이
이 결함의 핵심이라 그 문장을 남겼다.

## AC-3/AC-4 — 드리프트 가드 + 실물 bite

`scripts/check-libs-ci-coverage.sh` + CI 잡 `libs-ci-coverage`(경로 필터는 `settings.gradle` ·
`.github/workflows/**` · 가드 스크립트 자신, **`code-changed` 와 AND 하지 않는다** — 이 드리프트의
두 도착 경로가 모두 비-Java 라 AND 하면 가드가 통째로 꺼진다).

**실물 트리 bite 2종**(자기검증 사본이 아니라 진짜 파일, 주입량 먼저 확인 후 판정):

```
BITE 1  ci.yml 에서 :libs:java-gateway:check 삭제  → FAIL, ":libs:java-gateway" 를 지목
BITE 2  settings.gradle 에 'libs:java-phantom' 추가 → rc=1, ":libs:java-phantom" 을 지목
복원 후                                              → rc=0, 트리 modified 0
```

**자기검증 10/10** — 실물 트리를 복사·변형해서 돈다(손으로 지은 픽스처는 실물보다 관대하다).
대조군(무변형 통과)·양방향 bite·fail-closed 2종·**주석 처리된 엔트리는 커버리지가 아님**
포함.

## 🔴🔴 이 가드는 개발 중에 **두 번 거짓 초록을 냈다** — 원인은 자기검증의 구조였다

자기검증이 **10/10 PASS 인데 실제 호출은 rc=1, 출력 0바이트**인 상태가 두 번 있었다:

1. `comm -23 <(…) <(…)` 의 process substitution 이 msys 에서 셸을 죽였다.
2. `cat "$wfdir"/*.yml "$wfdir"/*.yaml` — 이 저장소엔 `.yaml` 워크플로가 없어 글롭이 리터럴로
   남고 `cat` 이 1을 내며 `pipefail` 이 그것을 전파했다.
   🔴 **그 줄의 주석은 정확히 그 함정을 피하려고 `cat` 을 골랐다고 적혀 있었다.** 의도는
   적혀 있었고 코드가 그걸 달성하지 못했다.

**왜 자기검증이 못 봤나**: 모든 케이스가 `run_check … || got=$?` 로 호출되는데, 그 문맥은
`set -e` 를 통째로 끈다. 즉 **errexit 로 죽는 결함은 자기검증에서 구조적으로 보이지 않는다.**
→ 자기검증 첫 케이스를 **별도 프로세스로 진짜 엔트리포인트를 실행**하는 것으로 바꿨다.
그 케이스가 없었으면 이 PR 은 "초록"으로 머지되고 CI 잡만 조용히 빨갰을 것이다.
[[env_standalone_guard_test_must_replicate_pipefail]] [[feedback_my_verification_predicate_is_the_likeliest_defect]]

## AC-5 — 판정 술어는 문자열이 아니라 **태스크 그래프**

CI 의 실제 태스크 목록을 `--dry-run` 으로 돌려 각 모듈의 `test` 가 그래프에 **나타나는지**로
판정했다: **13/13 등장**. 그래프에 들어온 가드 4종:
`:assertNoApiOnSharedLibs` · `:libs:java-gateway:assertNoServletOnReactiveEdge` ·
`:libs:java-security:assertClasspathNeutrality` ·
`:libs:java-security-servlet:assertClasspathNeutrality` — 뒤 둘 중 하나와 첫 둘은
**이 저장소에서 처음 실행된다.**

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 완료. repo-root **13 include / 7 실행 / 6 누락**, `projects/*/libs/*`
      **1/1 정상**. 🔴 첫 계수기가 settings.gradle 주석의 아포스트로피를 문자열로 읽어
      거짓 수를 냈고, 주석 제거 + 경로 형식 강제로 고쳤다. 티켓 제목의 "6개만" 은
      MONO-521 **이전** 수치였다
- [x] **AC-1 (실행)** — 완료. 누락 6개 전부 CI 목록에 추가, 로컬 **전부 GREEN**(RED 없음).
      🔴 늘어난 커버리지를 처음엔 0으로 셌다(`tests=` 속성 형식 오독) — 실제 **206 테스트 케이스**
- [x] **AC-2 (루트 가드)** — 완료. `:assertNoApiOnSharedLibs` 명시 + `build.gradle` 주석 정정
      (dependsOn 은 로컬 표면으로 유지, CI 도달성은 별도 표면임을 명시)
- [x] **AC-3 (드리프트 가드)** — 완료. `check-libs-ci-coverage.sh` + `libs-ci-coverage` 잡,
      경로 필터는 `code-changed` 와 **AND 하지 않는다**
- [x] **AC-4 (bite)** — 완료. **실물 트리** 2방향 bite(모듈 삭제 / 유령 모듈 추가) 전부 RED,
      복원 후 GREEN·트리 clean. 강제 rerun 없음(bash 스크립트라 Gradle 캐시 무관)
- [x] **AC-5 (도달 판정의 술어)** — 완료. `--dry-run` 그래프에서 **13/13** 모듈의 `test` 등장,
      가드 4종 등장. 🔵 이미 돌던 모듈이 같은 실행에 나타나는 것이 대조군이다

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
