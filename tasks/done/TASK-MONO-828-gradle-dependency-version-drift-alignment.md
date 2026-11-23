# Task ID

TASK-MONO-828

# Title

Gradle 의존성 버전 드리프트 정렬 — 같은 좌표가 모듈마다 다른 버전(`nimbus-jose-jwt` 3종 · `wiremock` 2종 + 옛 좌표 · iam e2e 의 낡은 6개)을 저장소 다수 버전으로

# Status

done

# Owner

monorepo

# Task Tags

- build
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (선언 몇 줄 · CI 가 판정)
> 기안 2026-11-24 UTC. 출처: 소유자 요청 «모노레포 전체 리팩토링 검토» → 제안 순서 1a → 소유자 «전부 진행».

# Dependency Markers

- 후속: `TASK-MONO-829`(버전을 `gradle.properties` 로 끌어올림) — 이 티켓이 먼저 머지돼야 829 가 «한 좌표 = 한 값» 위에서 순수 리팩토링이 된다.

# Goal

빌드 파일 82개의 직접 박힌 버전 좌표를 전수로 셌다(2026-11-24, `git grep` over `*.gradle`). 같은 좌표가 여러 버전으로 갈라진 곳:

| 좌표 | 현재 | 위치 |
|---|---|---|
| `com.nimbusds:nimbus-jose-jwt` | 9.48 ×22 · **9.40 ×5** · **9.37.3 ×1** | 9.40 = wms admin/inbound/inventory/master/outbound · 9.37.3 = ecommerce gateway-service (전부 `testImplementation`) |
| `org.wiremock:wiremock-standalone` | **3.9.1 ×7** · 3.10.0 ×2 | 3.9.1 = ecommerce product · iam account/admin/auth/gateway · scm logistics · wms notification |
| `com.github.tomakehurst:wiremock-jre8-standalone:3.0.1` | 옛 좌표 ×1 | iam security-service |
| iam `tests/e2e` | junit-bom **5.10.3** · testcontainers-bom **1.20.4** · jackson **2.17.2** · `mysql:mysql-connector-java:8.0.33`(이전된 옛 좌표) · assertj **3.26.3** · slf4j-simple **2.0.13** | 나머지 저장소: 5.11.4 · 1.21.3 · 2.18.2 · `com.mysql:mysql-connector-j:9.1.0` · 3.27.0 · 2.0.16 |

드리프트는 결함의 온상이다 — 같은 시험 픽스처가 서비스마다 다른 라이브러리 동작 위에서 돈다. 이 티켓은 **값만** 맞춘다(구조는 829).

# Scope

## In Scope

- 위 표의 모든 행을 «저장소 다수 버전» 으로: nimbus → 9.48 · wiremock → 3.10.0(이미 payment-service · libs/payment-toss 에서 증명) · iam security-service 를 `org.wiremock:wiremock-standalone:3.10.0` 으로 · iam e2e 6개를 오른쪽 열 값으로.

## Out of Scope

- 버전을 속성으로 끌어올리는 것(829).
- Spring Boot / Spring Cloud BOM 버전, 다수 버전 자체의 업그레이드.

# Acceptance Criteria

- [x] AC-1: 위 표의 좌표마다 `*.gradle` 전체에서 버전이 **하나**다(`git grep -oh` 집계로 확인, 수치를 PR 본문에 인용).
- [x] AC-2: 영향 모듈 전부 `compileTestJava` rc=0 (로컬, 각 명시 rc).
- [ ] AC-3: PR CI 에서 영향 모듈의 Build & Test · integration · iam e2e smoke 잡이 **실제로 실행되어** success(SKIPPED 는 통과로 치지 않는다 — 실행된 잡 이름을 PR 본문에 적는다).
- [x] AC-4: iam security-service 의 wiremock 사용 코드가 좌표 변경 뒤에도 같은 패키지(`com.github.tomakehurst.wiremock`)로 컴파일된다 — WireMock 3 는 패키지를 유지한다; 아니면 멈추고 보고.

# Related Specs

- `platform/versioning-policy.md` · `platform/testing-strategy.md`.

# Related Skills

- 없음.

# Related Contracts

- 없음.

# Target Service

- 표의 모듈들(테스트 클래스패스만, iam e2e 제외 전부 `testImplementation`).

# Architecture

- 해당 없음(선언 값 변경).

# Implementation Notes

- `mysql-connector-j` 9.x 는 드라이버 클래스가 `com.mysql.cj.jdbc.Driver` — iam e2e 가 옛 클래스명(`com.mysql.jdbc.Driver`)을 문자열로 쓰는지 grep.

# Edge Cases

- nimbus 는 Spring Security 가 전이로도 끌어온다 — 시험 클래스패스에서 9.48 이 이기는 것은 오늘 22곳과 같은 조건이다.

# Failure Scenarios

- path filter 때문에 영향 모듈 잡이 SKIPPED 인 채 초록 → AC-3 이 막는다.
- iam e2e 는 PR 에선 smoke 만 돈다; full 은 nightly — 머지 뒤 첫 nightly 의 iam e2e full 결과를 확인해 close 때 적는다.

# Test Requirements

- 기존 시험 무수정 통과.

# Definition of Done

- AC 전부 · 머지.

# Implementation Evidence (2026-11-24 UTC)

- **AC-1** — 좌표별 버전 수(`git grep -oh` over `*.gradle`): `nimbus-jose-jwt` 9.48 ×28 · `wiremock-standalone` 3.10.0 ×10 · `wiremock-jre8-standalone` 0 · `testcontainers-bom` 1.21.3 ×40 · `junit-bom` 5.11.4 ×1 · `jackson-databind` 2.18.2 ×4 · `jackson-datatype-jsr310` 2.18.2 ×3 · `mysql-connector-j` 9.1.0 ×2(옛 `mysql:mysql-connector-java` 0) · `assertj-core` 3.27.0 ×4 · `slf4j-*` 2.0.16 ×4.
- **해석 그래프 대조** — init script 로 전 프로젝트(84) 의 `*Classpath` 해석 결과를 전후 덤프(해석 실패 0). 차이 158줄, **바뀐 프로젝트 = 정확히 손댄 15개**, 바뀐 설정 = 시험 클래스패스뿐. 차이 좌표 = nimbus · wiremock · iam e2e 의 junit/testcontainers/jackson/assertj/slf4j/mysql 계열과 그 전이(docker-java · byte-buddy · protobuf). 의도 밖 좌표 0.
- **AC-2** — 영향 15개 모듈 `compileTestJava` 한 호출, `BUILD SUCCESSFUL` rc=0.
- **AC-4** — iam security-service 시험 3개가 `com.github.tomakehurst.wiremock` 패키지 그대로 컴파일(AC-2 에 포함).
- **AC-3** — PR CI 에서 판정(아래 PR 본문).

## CORRECTION (close chore, 2026-11-23 UTC)

- **AC-3 닫힘(실측)** — impl PR #4636(squash `aa46c4361`) 의 CI 에서 실행되어 success: `Build & Test (JDK 21, Linux)` · `Integration (iam A/B)` · `Integration (ecommerce A/B/C)` · `Integration (scm-platform)` · `Integration (inventory + inbound + gateway-service)` · `Integration (master-service + notification-service + outbound-service)` · `E2E smoke (iam docker-compose)`. 실패·취소 0.
- **Failure Scenario 2 닫힘** — 머지 커밋 `aa46c4361` 의 nightly-e2e(run `45265362304`, push 트리거)에서 `E2E full (iam docker-compose)` **success**.
- **날짜 정정** — 이 파일의 «2026-11-24 UTC» 는 KST 날짜를 UTC 로 잘못 적은 것이다. 기안 · 구현 · 머지는 모두 **2026-11-23 UTC**(KST 11-24 새벽 — 이 호스트의 KST 00:00–09:00 함정). 날짜를 읽는 게이트가 이 파일을 보지 않아 CI 는 초록이었다.
