# Task ID

TASK-MONO-829

# Title

서드파티 버전 13종을 빌드 파일 수십 곳의 문자열에서 `gradle.properties` 속성 하나씩으로 — **해석 그래프 불변** 리팩토링 + 다시 박히지 않게 막는 가드

# Status

ready

# Owner

monorepo

# Task Tags

- build
- ci
- refactor

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기계적 치환) + 가드 설계는 Opus 검토
> 기안 2026-11-24 UTC. 출처: 소유자 요청 «모노레포 전체 리팩토링 검토» → 제안 순서 1 → 소유자 «전부 진행».

# Dependency Markers

- 선행: `TASK-MONO-828`(드리프트 정렬). 828 이 머지되기 전이면 한 좌표에 값이 둘이라 «속성 하나» 로 바꾸는 순간 동작이 바뀐다 — **828 머지 뒤에만 착수.**

# Goal

`buildSrc` · 버전 카탈로그 · convention plugin 이 **없다**. 루트 `build.gradle` 의 `subprojects {}` 와 `gradle.properties`(`springBootVersion` · `springCloudVersion` · `javaVersion`)가 유일한 공유 지점이고, 나머지 서드파티 버전은 모듈마다 문자열로 박혀 있다(2026-11-24 실측, `*.gradle` 82개):

`testcontainers` 1.21.3(bom ×39 + 모듈 ×63) · `okhttp`/`mockwebserver` 4.12.0 ×57 · `awaitility` 4.2.2 ×29 · `logstash-logback-encoder` 8.0 ×26 · `testcontainers-redis` 2.2.2 ×26 · `nimbus-jose-jwt` 9.48 ×22(828 뒤 ×28) · `resilience4j` 2.2.0 ×30 · `springdoc` 2.7.0 ×13 · `lombok` 1.18.36 ×12 · `wiremock` 3.10.0(828 뒤 ×10) · `shedlock` 6.2.0 ×10 · `jjwt` 0.12.6 ×15 · `kafka-clients` 3.7.0 ×5.

한 버전을 올리려면 수십 파일을 고쳐야 하고, 일부만 고쳐진 결과가 828 이 정렬한 드리프트다. 기존 메커니즘(`gradle.properties` — 이미 `springBootVersion` 이 그 방식)을 쓰므로 **새 빌드 아키텍처를 들이지 않는다**(카탈로그 · 플러그인 도입은 ADR 감 — 범위 밖).

# Scope

## In Scope

- `gradle.properties` 에 속성 13개: `testcontainersVersion` · `okhttpVersion` · `awaitilityVersion` · `logstashLogbackEncoderVersion` · `testcontainersRedisVersion` · `nimbusJoseJwtVersion` · `resilience4jVersion` · `springdocVersion` · `lombokVersion` · `wiremockVersion` · `shedlockVersion` · `jjwtVersion` · `kafkaClientsVersion`.
- 해당 좌표의 모든 선언(루트 `build.gradle` 의 lombok 4줄 포함)을 `"group:artifact:${xVersion}"`(Groovy 큰따옴표)로.
- 새 가드 `scripts/check-gradle-managed-versions.sh` + `ci.yml` 잡 + `changes` 필터 항목: 위 13종의 좌표가 `*.gradle` 에 숫자 버전 문자열로 나타나면 실패. 비공허 하한(속성 13개 존재 · `${…Version}` 참조 수 하한). `--self-test` 로 bite 증명.
- `scripts/` 에 파일이 하나 늘어나므로 의존하는 가드 전부 재실행(특히 `check-ls-files-guard-count.sh` 가 가리키는 산문 집 갱신) — CLAUDE.md § Task Rules.

## Out of Scope

- 버전 카탈로그 · `buildSrc` · convention plugin(ADR 필요).
- `unitTest`/`integrationTest` 태스크 블록 공통화 — 모듈마다 태그 · 의존 태스크가 달라 동작 변경 위험(별도 검토).
- 1~4회만 쓰는 좌표(e2e 모듈의 junit · assertj · jackson 등).

# Acceptance Criteria

- [ ] AC-1: **해석 그래프 불변** — 변경 전후 모든 프로젝트의 `testRuntimeClasspath` · `runtimeClasspath` · `compileClasspath` 해석 결과(좌표:버전 정렬 목록)가 **바이트 동일**. 덤프 방법과 diff 결과(0줄)를 PR 본문에.
- [ ] AC-2: 13종 좌표의 숫자 버전 문자열이 `*.gradle` 에 0건(가드 rc=0).
- [ ] AC-3: 가드 bite — 빌드 파일 하나에 `'org.awaitility:awaitility:4.2.2'` 를 되살리면 rc=1 + 파일·줄 이름 출력, 되돌리면 rc=0. `--self-test` 가 같은 것을 CI 에서 증명.
- [ ] AC-4: `scripts/` 추가로 영향받는 가드 **전부** rc=0(실행한 목록을 PR 본문에).
- [ ] AC-5: PR CI 의 Build & Test 잡들이 실행되어 success.

# Related Specs

- `platform/versioning-policy.md` § Library Versioning(«버전은 `gradle.properties` 에»).
- `platform/testing-strategy.md` § 가드 규칙(G1~G8).

# Related Skills

- 없음.

# Related Contracts

- 없음.

# Target Service

- 전 모듈 빌드 선언(코드 변경 0).

# Architecture

- 기존 `gradle.properties` 메커니즘 재사용 — 아키텍처 변경 없음.

# Implementation Notes

- 그래프 덤프: init script 로 `allprojects { configurations.matching{ it.canBeResolved && it.name in [...] }.each { c -> c.incoming.resolutionResult.allComponents … } }` 를 파일로 — `TZ=UTC`, `--offline` 금지(캐시 차이로 거짓 diff).
- 단일따옴표 문자열 안의 `${}` 는 보간되지 않는다 — 치환 시 큰따옴표로 바꿨는지가 AC-1 이 잡는 첫 결함이다.

# Edge Cases

- `platform("…bom:${v}")` 와 `mavenBom "…"` 둘 다 존재 — 두 형태 모두.
- `settings.gradle`/`gradle.properties` 를 읽지 않는 독립 빌드가 있으면(포함 빌드) 속성이 안 보인다 — 실측으로 확인.

# Failure Scenarios

- 보간 실패 → 버전 문자열이 `${x}` 그대로 → 해석 실패(빌드 빨강, 조용하지 않음) 또는 오타 속성명 → `MissingPropertyException`. 둘 다 AC-1 덤프 단계에서 드러난다.
- 가드 정규식이 `${…}` 를 숫자로 오인하거나 반대로 아무것도 안 무는 것 → AC-3 bite.

# Test Requirements

- AC-1 덤프 diff · 가드 self-test.

# Definition of Done

- AC 전부 · 머지.
