# Task ID

TASK-BE-559

# Title

security-service 자체 코드를 `com.example.security` → `com.example.security.service` 로 이전 (shared `libs/java-security` 와의 루트 패키지 충돌 제거)

# Status

in-progress

# Owner

backend

# Task Tags

<!-- Select all that apply. Used by entrypoint.md to determine which auxiliary specs to read. -->
<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`libs/java-security`(공유 라이브러리)와 `projects/iam-platform/apps/security-service`(배포 서비스)가 **동일한 루트 Java 패키지 `com.example.security` 를 공유**한다. 그리고 서비스의 진입점이 하필 그 루트에 있다:

```java
// apps/security-service/src/main/java/com/example/security/SecurityApplication.java
package com.example.security;

@SpringBootApplication   // ← 기본 component-scan base package = com.example.security
@EnableScheduling
public class SecurityApplication { ... }
```

`@SpringBootApplication` 의 기본 스캔 base package 는 **선언된 클래스의 패키지**다. 따라서 `security-service` 의 컴포넌트 스캔 범위가 **공유 라이브러리 `libs/java-security` 의 패키지 트리 전체**(`com.example.security.access`, `.jwt`, `.oauth2`, `.password`, `.pii`, `.redis`)를 덮는다.

**오늘 무해한 이유는 단 하나** — `libs/java-security` 에 Spring stereotype(`@Component`/`@Configuration`/`@Service`/`@Repository`/`@Bean`/`@ConfigurationProperties`/auto-config) 이 붙은 클래스가 **현재 0개**이기 때문이다(착수 시 재측정 대상, AC-0). 즉 이것은 **설계상의 안전이 아니라 우연한 상태**다.

누군가 `libs/java-security` 에 Spring bean 을 하나 추가하는 순간 — *보안 라이브러리에 bean 이 추가되는 것은 매우 개연성 높은 변경이다* — 그 bean 은:

1. **`security-service` 의 애플리케이션 컨텍스트에만 조용히 auto-register 된다.** 같은 라이브러리를 쓰는 다른 6개 서비스(wms 3종·finance·erp·scm·iam gateway 등)에는 등록되지 않는다 → **소비자마다 컨텍스트가 갈라진다.**
2. 이 비대칭은 **컴파일러도 유닛 테스트도 잡지 못한다.** 어떤 타입 검사도 "누가 나를 스캔하는가"를 묻지 않는다.
3. 그 bean 이 부팅을 깨뜨릴 때만 보인다 — 즉 **문제 발생 지점이 결함 도입 지점에서 멀다.**
4. 두 산출물의 향후 **JPMS 모듈화/sealed jar 패키징을 구조적으로 막는다**(split package).

**이 task 이후 참이 되어야 하는 것**: `security-service` 의 자체 소스는 어떤 파일도 `com.example.security` 패키지에 **직접** 존재하지 않으며, `SecurityApplication` 의 기본 컴포넌트 스캔 base package 가 **공유 라이브러리의 어떤 패키지도 조상으로 갖지 않는다.** 즉 라이브러리에 bean 을 추가해도 `security-service` 컨텍스트에 자동 유입되지 않는다.

**라이브러리는 옳다. 충돌하는 쪽은 서비스다** — `libs/java-security` 의 패키지는 손대지 않는다(TASK-MONO-034 가 `com.gap.security` → `com.example.security` 로 정규화한 결과이며, 8개 프로젝트가 이미 이 좌표로 import 한다. 라이브러리를 옮기면 `com.example.security.jwt.*`/`.access.*`/`.oauth2.*` 를 참조하는 **다른 프로젝트 전체**가 함께 깨지는 cross-project 원자 변경이 되고, 이 티켓의 위험도가 몇 배로 커진다).

---

# 대상 패키지 결정 — `com.example.security.service`

| 후보 | 충돌 해소? | 판단 |
|---|---|---|
| `com.example.security.service` | ✅ 스캔 루트가 라이브러리 패키지들의 **형제**가 된다 | **채택** |
| `com.example.iam.security` | ✅ | 기각 — 형제 iam 서비스 4개가 전부 `com.example.{auth,account,admin,gateway}` 이다. 새 `com.example.iam.*` 접두를 혼자 도입하면 *"iam 전체를 `com.example.iam.*` 로 옮겨야 하나"* 라는 **훨씬 큰 결정**을 부른다 — 이 티켓의 범위 밖 |
| `com.example.securityservice` | ✅ | 기각 — 저장소 어디에도 없는 네이밍 형태 |
| 라이브러리를 이동 | ✅ | 기각 — 8개 프로젝트 파급 cross-project 원자 변경 (위 참조) |

**채택안이 실제로 문제를 없애는지 확인** (기계적으로):

- 이전 후 스캔 루트 = `com.example.security.service`.
- 라이브러리 패키지 = `com.example.security.{access,jwt,oauth2,password,pii,redis}` (+ `libs/java-security-servlet` 의 `com.example.security.servlet`).
- 이들은 스캔 루트의 **조상도 자손도 아닌 형제**다 → 라이브러리에 bean 을 추가해도 스캔되지 않는다. ✅
- split package: 이전 후 `com.example.security` 패키지에 **클래스를 가진 산출물은 0개**가 된다(라이브러리도 서브패키지에만 클래스를 둔다). 어떤 패키지도 두 jar 에 동시에 존재하지 않는다 → JPMS/sealed jar 차단 요인 해소. ✅

**남는 잔여 위험(수용)**: 누군가 라이브러리에 `com.example.security.service.*` 라는 패키지를 만들면 다시 겹친다. 이는 개연성이 없고(라이브러리에는 `service` 레이어 개념이 없다), 발생 시 split package 로 즉시 드러나는 리뷰 가능한 오류다.

**네이밍 규약과의 관계**: `platform/naming-conventions.md` § Packages 는 `com.example.{service}.{layer}` 를 규정한다. 이 티켓 이후 `security-service` 는 `com.example.security.service.{layer}` 가 되어 `{service}` 자리가 `security.service` 로 한 단계 깊어진다. 이는 **규약 위반이 아니라 규약이 예상하지 못한 충돌 사례**다 — `{service}` 좌표(`security`)를 공유 라이브러리가 선점한 유일한 케이스. 규약 파일 자체는 이 티켓에서 **수정하지 않는다**(단일 예외를 규약으로 승격하지 않는다). 대신 `security-service/architecture.md` 가 자기 패키지 트리와 그 근거를 선언한다 — 규약이 위임한 지점("Sub-package structure is defined per service in `specs/services/<service>/architecture.md`")이다.

---

# Scope

## In Scope

1. **`specs/services/security-service/architecture.md` 선행 갱신** (Source of Truth Priority: 스펙이 먼저). `## Internal Structure Rule` 의 트리 루트 `apps/security-service/src/main/java/com/example/security/` → `.../com/example/security/service/` 로 갱신 + **왜 이 좌표인가**(라이브러리 충돌)를 짧은 단락으로 선언.
2. `apps/security-service/src/main/java/**` 89개 파일: 디렉터리 이동(`com/example/security/X` → `com/example/security/service/X`) + `package` 선언 + intra-service `import` 갱신.
3. `apps/security-service/src/test/java/**` 48개 파일: 동일. **테스트 로직은 한 줄도 바꾸지 않는다** — `package`/`import` 라인만.
4. **문자열로 패키지를 참조하는 지점** (컴파일러가 못 잡는 곳, 이 티켓의 실질적 위험 지점):
   - `src/main/resources/application.yml:48` — `spring.deserializer.value.delegate.class: com.example.security.infrastructure.kafka.StrictJsonStringDeserializer`
   - `src/test/resources/application-test.yml:67` — 동일 키
   - `infrastructure/config/JpaConfig.java` — `@EnableJpaRepositories(basePackages = "com.example.security.infrastructure.persistence")` + `@EntityScan(basePackages = ...)`
   - `query/internal/QueryExceptionHandler.java` — `@RestControllerAdvice(basePackages = "com.example.security.query.internal")`
   - javadoc 내 `{@code}`/`{@link}` 로 적힌 FQCN (`OutboxConfig`, `ProcessedEventJpaRepository`, `SecurityOutboxJpaEntity`)
5. 저장소 **전역** 잔존 참조 검증 (AC-6).

## Out of Scope

- ❌ **`libs/java-security` 의 패키지·파일 일절 변경 금지.** 라이브러리는 옳다.
- ❌ `libs/java-security-servlet`(`com.example.security.servlet`) 변경 금지 — security-service 는 이 라이브러리에 의존조차 하지 않는다(`build.gradle` 확인됨).
- ❌ 다른 iam 서비스(`auth`/`account`/`admin`/`gateway`) 변경 금지.
- ❌ 다른 프로젝트(wms/finance/erp/scm/ecommerce/fan) 변경 금지 — 이들은 `com.example.security.jwt.*` 등 **라이브러리**를 참조하며, 라이브러리는 움직이지 않으므로 무영향.
- ❌ `platform/naming-conventions.md` 수정 금지 (위 § 대상 패키지 결정 참조).
- ❌ 클래스명·메서드명·시그니처·로직·테스트 단언 변경 금지. **행동 보존 리팩토링**이다.
- ❌ 컴포넌트 스캔 범위를 넓히는 방향의 어떤 수정도 금지 — `@ComponentScan("com.example.security")` 같은 "부팅 복구"는 이 티켓이 없애려는 결함 그 자체다.
- ❌ DB 스키마/Flyway/계약(HTTP·이벤트) 변경 금지. Kafka topic·envelope·`source` 필드 값(`"security-service"`) 불변.

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정).** 착수 시점에 `libs/java-security` 의 Spring stereotype 개수를 다시 센다. 감사 시점 측정치는 **가설**이다. 0 이 아니면 → 결함이 이미 라이브(잠재 아님)이므로 그 사실을 PR 본문에 기록하고 티켓의 긴급도를 격상해 보고한다(범위는 동일).
- [ ] **AC-1.** `specs/services/security-service/architecture.md` 가 코드보다 **먼저** 갱신되어 새 패키지 트리와 그 근거를 선언한다.
- [ ] **AC-2.** `security/` **바로 아래**에 `.java` 파일이 0건이다 — 서비스는 `com.example.security` 패키지에 클래스를 갖지 않는다.
  ```
  git ls-files ":(glob)projects/iam-platform/apps/security-service/src/*/java/com/example/security/*.java"   # → 0
  git ls-files ":(glob).../com/example/security/service/*.java"                                              # → SecurityApplication.java (알려진 답 자기검증)
  ```
  **`:(glob)` 매직이 필수다.** git 의 기본 pathspec 에서 `*` 는 `/` 를 넘어가므로, 매직 없이 쓴 같은 패턴은 트리 전체(137건)를 세고 **이전 전후 모두 같은 숫자를 내놓는다** — 즉 아무것도 검증하지 못한다. 두 번째 줄(알려진 답)이 그 오작동을 잡는 자기검증이다.
- [ ] **AC-3.** `SecurityApplication` 이 `package com.example.security.service;` 이고, `@SpringBootApplication` 에 `scanBasePackages` / 별도 `@ComponentScan` 인자가 **추가되지 않았다**(기본 스캔이 곧 격리다 — 명시 인자는 같은 결함을 문자열로 재도입할 여지를 만든다).
- [ ] **AC-4.** 서비스 트리 안에 문자열/애노테이션 인자/javadoc 형태로 남은 옛 좌표가 0건이다: `grep -rn 'com\.example\.security\.\(consumer\|application\|domain\|infrastructure\|query\)' apps/security-service/` → 0.
- [ ] **AC-5.** `application.yml` / `application-test.yml` 의 `spring.deserializer.value.delegate.class` 두 곳이 새 FQCN 을 가리킨다. (이 두 줄은 **컴파일되지 않는다** — 틀리면 Kafka 소비가 런타임에 죽는다. `DlqRoutingIntegrationTest` 가 실제 판정자.)
- [ ] **AC-6 (저장소 전역).** 리터럴 `com.example.security` 를 **저장소 전체**에 grep 하여, 남은 매치가 전부 (a) `libs/java-security`·`libs/java-security-servlet` 자신, (b) 그 라이브러리의 서브패키지(`.jwt`/`.access`/`.oauth2`/`.password`/`.pii`/`.redis`/`.servlet`)를 참조하는 정당한 소비자, (c) `tasks/done/` 의 역사 기록(수정 금지), (d) `com.example.security.service.*` (새 좌표) 중 하나임을 확인하고 그 분류 결과를 PR 본문에 싣는다.
- [ ] **AC-7 (동일 테스트 개수).** `:...:security-service:test` **와** `:...:security-service:integrationTest` **둘 다** GREEN 이고, 각각의 테스트 개수가 baseline 과 동일하다(tests/failures/skipped). 개수가 줄면 = 패키지 이동으로 테스트가 **발견되지 않게** 된 것이다(초록으로 보이는 유실). baseline 은 착수 전 같은 두 명령으로 측정해 PR 본문에 기록한다.
  > **⚠️ `:test` 는 이 티켓을 검증하지 못한다.** `projects/iam-platform/build.gradle` 의 `test { useJUnitPlatform { excludeTags 'integration' } }` 때문에 `@Tag("integration")` 클래스 6개(= 애플리케이션 컨텍스트를 실제로 띄우는 유일한 클래스들)가 **`:test` 에서 통째로 제외**된다. `:test` 만 돌리고 초록을 보면, **부팅이 깨져도 초록**이다. 컨텍스트 부팅은 `:integrationTest` 만이 증명한다.
  > **baseline 개수 자체도 재검증 대상이다.** 컨테이너 기동 실패로 중단된 클래스는 실제 테스트 N개 대신 `initializationError` **1개**만 XML 에 남긴다 → baseline 총계가 실제 모집단보다 작게 나온다. 그 숫자를 "기대값"으로 물려받으면 이후 정상 실행이 **증가**로 보여 가짜 경보가 된다. baseline 이 RED 였다면 **클래스 단위로 모집단을 다시 세라**.
- [ ] **AC-8 (부팅).** Testcontainers IT 6종(`SecurityServiceIntegrationTest`/`DetectionE2EIntegrationTest`/`DlqRoutingIntegrationTest`/`PiiMaskingIntegrationTest`/`CrossTenantVelocityIntegrationTest`/`LoginHistoryImmutabilityIntegrationTest`)이 `:integrationTest` 에서 **실제로 컨텍스트를 띄워** GREEN. 로컬 Windows Docker 는 권위가 아니다 — **CI Linux 레인이 권위**.
- [ ] **AC-9 (행동 보존 증명).** `git diff` 상 `src/test/java` 의 변경 라인이 **`package` / `import` 라인만**임을 확인한다(테스트 단언 변경 0). 프로덕션 쪽도 패키지·import·문자열 좌표 외 변경 0.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification.

- `platform/refactoring-policy.md` — 이 변경의 분류 = **Restructure Package (High risk)**. Mandatory #1(행동 무변경)·#2(전후 그린)·#3(한 번에 한 종류)
- `platform/naming-conventions.md` § Packages
- `platform/shared-library-policy.md` — 라이브러리↔소비자 경계
- `platform/dependency-rules.md`
- `platform/testing-strategy.md`
- `platform/service-types/event-consumer.md` (primary) + `platform/service-types/rest-api.md` (secondary)
- `projects/iam-platform/specs/services/security-service/architecture.md` — **이 티켓이 먼저 갱신하는 대상**
- `rules/domains/saas.md`, `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`

# Related Skills

- `.claude/skills/backend/` (구현 규약)
- `.claude/skills/testing-backend/`

---

# Related Contracts

**계약 변경 없음.** 아래는 "바뀌지 않아야 하는 것"의 목록이다 (검증용):

- `projects/iam-platform/specs/contracts/http/security-query-api.md` — 경로·응답 shape 불변
- `projects/iam-platform/specs/contracts/http/internal/security-to-account.md` — 불변
- `projects/iam-platform/specs/contracts/events/` — 발행 `security.*` 이벤트의 envelope 7필드, 특히 `source: "security-service"` **문자열 리터럴 불변**(패키지명과 무관)
- Kafka topic 이름 불변 (`topicFor` 매핑)

---

# Target Service

- `security-service` (단일 서비스. 다른 서비스는 건드리지 않는다)

---

# Architecture

Follow:

- `projects/iam-platform/specs/services/security-service/architecture.md` (본 티켓에서 선행 갱신)

레이어 구조(`consumer` / `application` / `domain` / `query` / `infrastructure`)와 허용·금지 의존 방향은 **완전히 그대로**다. 바뀌는 것은 트리의 **루트 좌표 하나**뿐이다.

---

# Implementation Notes

**이것은 IDE 의 "Rename Package" 한 번이면 끝나는 기계적 변경이다. 위험은 기계가 못 보는 곳에만 있다.**

1. **순서**: architecture.md → main 이동 → test 이동 → 문자열 좌표 4종 → 빌드 → 전역 grep.
2. **`refactoring-policy.md` Prohibited "프로덕션 코드와 테스트 코드를 같은 변경에 섞지 말 것" 에 대하여**: 패키지 이동에서는 분리가 **불가능**하다(테스트가 컴파일되지 않는다). 다만 그 규칙의 취지(테스트 리팩토링과 프로덕션 리팩토링의 혼입 방지)는 유지된다 — 테스트 파일은 `package`/`import` 라인 외에 **한 글자도** 바뀌지 않으며 AC-9 가 그것을 diff 로 증명한다. 이 근거를 PR 본문에 명시한다.
3. **파일 이동은 `git mv`** 로 한다(rename 으로 추적되어 리뷰 diff 가 읽을 수 있게 유지된다).
4. **`grep` 은 두 종류를 따로 돌린다**:
   - `com\.example\.security\.(consumer|application|domain|infrastructure|query)` — 서비스 소유 좌표
   - `com\.example\.security([^.A-Za-z0-9_]|$)` — **점 없이 끝나는** bare 참조. `com.example.security.*` 만 grep 하면 `@ComponentScan("com.example.security")` 같은 루트 문자열을 놓친다. (착수 시점 측정: bare 매치는 `SecurityApplication.java:1` 과 `tasks/done/TASK-MONO-034` 두 곳뿐)
5. **부팅이 깨지면 스캔 범위를 넓혀 "고치지" 말 것.** `scanBasePackages = "com.example.security"` 로 되돌리는 것은 이 티켓이 제거하려는 결함의 재도입이다. 깨졌다면 반드시 **놓친 문자열 참조**가 원인이다 — 위 4번의 두 grep 과 §In Scope 4번 목록을 다시 훑는다.
6. **`libs/java-messaging` 의 `AbstractOutboxPublisher` / `OutboxRow`** 등 라이브러리 상속 지점은 타입 참조이므로 컴파일러가 잡는다 — 위험 지점 아님.
7. **Flyway 마이그레이션 파일(V0001~V0011)은 손대지 않는다.** 이미 적용된 마이그레이션은 불변이며, 어차피 패키지명을 담지 않는다.
8. **`Dockerfile`**: `apps/security-service/build/libs/` 의 bootJar 를 복사할 뿐 패키지 경로를 담지 않는다 — 변경 불필요(확인됨). `bootJar` 의 `Main-Class` 는 Spring Boot 플러그인이 자동 탐지한다.

---

# Edge Cases

- **`@RestControllerAdvice(basePackages = ...)`** — 문자열이 틀리면 컴파일은 통과하고, 예외 핸들러가 컨트롤러에 **붙지 않는다**. 증상은 에러 응답 포맷이 조용히 Spring 기본값으로 바뀌는 것 → `QueryExceptionHandler` 를 다루는 slice test 가 판정자.
- **`@EnableJpaRepositories` / `@EntityScan` 문자열** — 틀리면 부팅 시 repository bean 미발견(요란하게 실패). 조용한 실패가 아니라는 점은 다행이지만, `ddl-auto=validate` 와 겹쳐 오진하기 쉽다.
- **YAML 의 `value.delegate.class`** — 틀리면 컨텍스트는 정상 기동하고 **Kafka 소비 시점에** 죽는다. 유닛/슬라이스 테스트는 전부 초록일 수 있다. IT 가 유일한 판정자.
- **테스트 개수 감소** — 새 디렉터리로 옮기다 파일 하나가 소스 세트 밖(예: `com/example/security/` 잔류 빈 디렉터리)에 남으면 Gradle 이 조용히 건너뛴다. 초록인데 유실이다 → AC-7.
- **Windows 대소문자 무관 파일시스템** — `git mv` 로 이동한 뒤 옛 디렉터리가 인덱스에 유령으로 남을 수 있다. `git status` 와 `git ls-files` **양쪽**으로 확인한다.
- **`tasks/done/` 의 옛 좌표 문자열** — 역사 기록이다. **수정 금지**(HARDSTOP-05 lifecycle). AC-6 의 분류 (c).
- **javadoc `{@link com.example.security.X}`** — 컴파일 경고조차 안 날 수 있다(`-Xlint` 설정에 따라). grep 으로만 잡힌다.

---

# Failure Scenarios

| 시나리오 | 탐지 | 대응 |
|---|---|---|
| 부팅 실패 (bean not found) | IT / `:test` | 놓친 문자열 좌표를 찾는다. **스캔 범위 확대 금지** |
| Kafka 소비 실패 (deserializer FQCN) | `DlqRoutingIntegrationTest` | YAML 두 곳 재확인 |
| 에러 응답 포맷 변화 | query slice test | `@RestControllerAdvice` basePackages |
| 테스트 개수 감소 | AC-7 baseline 대조 | 소스 세트 밖에 남은 파일 탐색 |
| 저장소 밖(배포 설정·모니터링 대시보드·로그 파서)이 옛 패키지 문자열에 의존 | 저장소 grep 으로는 **탐지 불가** | 이 경우 **중단하고 보고**한다 — 저장소 밖 의존은 이 티켓 단독으로 판단할 수 없다 |
| 라이브러리에 이미 bean 이 있었다(AC-0 이 0 아님) | AC-0 | 범위는 동일하나 "잠재 위험"이 아닌 **라이브 결함**으로 보고 |
| 파일 수가 예상(137)을 크게 초과 | 착수 시 `git ls-files` | 규모 재산정 후 보고 |

**중단 조건**: 저장소 **밖**의 설정(배포 매니페스트·APM/로그 파이프라인의 패키지 필터 등)이 정확한 패키지 문자열에 의존한다는 증거가 나오면, 밀어붙이지 말고 **"더 큰 결정이 필요함"으로 보고**한다.

---

# Test Requirements

- **신규 테스트는 작성하지 않는다.** 행동 보존 리팩토링이므로 새 단언을 추가하면 "무엇이 보존되었는가"가 흐려진다.
- 기존 전량 재실행 — **두 태스크 모두**:
  1. `./gradlew :projects:iam-platform:apps:security-service:test` — 유닛 + slice (`excludeTags 'integration'`)
  2. `./gradlew :projects:iam-platform:apps:security-service:integrationTest` — Testcontainers(MySQL+Kafka+Redis) IT 6종. **애플리케이션 컨텍스트가 실제로 뜨는지는 여기서만 증명된다.**
- 각각 baseline(착수 전) 과 **tests / failures / skipped 개수 동일**
- 로컬 Windows Docker 는 FLAKY — 권위 아님. **CI Linux 레인이 권위**
- 회귀 표면: `:projects:iam-platform:apps:security-service:check`

---

# Verification (implementation record, 2026-07-30)

**AC-0 재측정.** `libs/java-security` 의 Spring stereotype (`@Component`/`@Configuration`/`@Service`/`@Repository`/`@RestController`/`@ControllerAdvice`/`@Bean`/`@ConfigurationProperties`/`@EnableAutoConfiguration`/`@AutoConfiguration`) = **0건** — 감사 시점과 동일. 결함은 **잠재 상태 그대로**였고, 이 티켓은 예방적 조치다. (형제 `libs/java-security-servlet` = `com.example.security.servlet`, 역시 stereotype 0건이며 `security-service` 는 이 라이브러리에 **의존조차 하지 않는다** — `build.gradle` 확인.)

**규모.** `git status --short | grep -c '^R'` = **137** (main 89 + test 48). 전부 `git mv` rename 으로 추적됨. 예상치와 정확히 일치 — 규모 재산정 불필요.

**AC-2.** `git ls-files ":(glob).../com/example/security/*.java"` → **0**. 자기검증 프로브(`.../security/service/*.java`) → `SecurityApplication.java` 1건. *(`:(glob)` 매직 없이 처음 실행했을 때 137 을 반환했다 — git 기본 pathspec 의 `*` 가 `/` 를 넘어가기 때문. 그 술어는 이전 전후 같은 값을 내므로 아무것도 검증하지 못한다. AC-2 본문에 반영함.)*

**AC-3.** `SecurityApplication` = `package com.example.security.service;`. `scanBasePackages` / `@ComponentScan` 인자 **추가 없음**. 격리 근거를 javadoc 으로 클래스에 고정.

**AC-4.** 서비스 트리 내 `com.example.security.(application|consumer|domain|infrastructure|query|integration)` → **0건**. bare `com.example.security` → 신규 javadoc 설명문 3줄뿐(코드 좌표 아님).

**AC-5.** `application.yml` + `application-test.yml` 의 `spring.deserializer.value.delegate.class` 2곳 갱신 완료. 판정자 `DlqRoutingIntegrationTest` GREEN.

**AC-6 (저장소 전역).** 리터럴 `com.example.security` 잔존 매치 분류:
- (a)(b) `libs/java-security*` 자신 + 그 서브패키지(`.jwt`/`.access`/`.oauth2`/`.password`/`.pii`/`.redis`/`.servlet`)를 참조하는 8개 프로젝트의 정당한 소비자 — **무변경**. 이 서비스가 쓰는 라이브러리 import 는 `com.example.security.pii.PiiMaskingUtils` 2건이며 **byte-identical 생존**.
- (c) 역사 기록 — `projects/iam-platform/tasks/done/TASK-BE-119`, 루트 `tasks/done/TASK-MONO-034`. lifecycle 동결, **수정 안 함**.
- (c') 루트 `knowledge/incidents/2026-05-07-docker-cli-proxy-regression.md:169` — 2026-05-07 에 **실제로 실행된 명령의 기록(transcript)** 이다(`$ …` + 그때의 `BUILD SUCCESSFUL` 출력). 기록을 고치면 기록이 거짓이 된다. 게다가 그 줄은 이미 시대에 뒤처져 있다(당시 `:integrationTest` 호출 형태). 루트 `knowledge/` 는 **프로젝트 밖 공유 경로**라 이 project-internal 티켓의 범위도 아니다 → **수정 안 함, 보고만**.
- (d) 새 좌표 `com.example.security.service.*` + 본 티켓/architecture.md/INDEX 의 설명문.

**AC-7 / AC-9 (개수 + 행동 보존).**

| | baseline (착수 전) | after |
|---|---|---|
| `:test` | 239 / skipped 0 / **failures 1** | **240** / 0 / **0** |
| `:integrationTest` | *(소스 유래 모집단 20)* | **20** / 0 / 0 (6 클래스 전부) |

> **`:test` 의 239 → 240 은 회귀가 아니라 baseline 오염이다.** baseline 의 1건 실패는 `AccountLockHistoryJpaRepositoryTest` 의 `initializationError`(Testcontainers MySQL `ContainerLaunchException`)였고, 클래스가 초기화 단계에서 중단되면 XML 에 실제 테스트 2개 대신 **`initializationError` 1개**만 남는다. 즉 baseline 의 참 모집단도 **240**(238 + 2)이었다. 그 클래스만 격리 재실행하니 GREEN — 로컬 Windows Docker churn 이지 코드 결함이 아니다. **다른 41개 클래스의 합계는 전후 238 로 정확히 일치.** 오염된 숫자를 "기대값"으로 물려받았다면 정상 실행을 가짜 회귀로 신고했을 것이다.
>
> `:integrationTest` 는 착수 전 측정치가 없어 **소스에서 모집단을 다시 셌다** — 6개 IT 클래스의 `@Test` 20개, `@Disabled` 0, `@ParameterizedTest`/`@RepeatedTest` 0(확장 없음) → 기대값 **정확히 20**. 실측 20/0/0/0 일치. AC-9 가 테스트 소스의 변경을 `package`/`import`/`{@link}` 좌표 3줄로 한정 증명하므로, 이 소스 유래 기대값은 측정된 baseline 과 동등하게 권위 있다.

**AC-9 diff 형태.** `src/test/java` 137파일 중 변경 라인은 `package` / `import` / javadoc `{@link}` FQCN 3줄이 **전부** — 테스트 단언 변경 0. `git diff --numstat` 상 `SecurityApplication.java`(javadoc 추가) 외 **모든 파일의 add == delete**. `git diff --check` 클린(공백 오류 0).

**AC-8 (부팅) — 이 티켓의 핵심 판정.** `:integrationTest` 6/6 클래스 GREEN. Spring 컨텍스트 실기동 + Kafka 리스너 등록 + DLQ 라우팅 + PII 마스킹 확인.

> **⚠️ 도중 발견 — `:test` 만으로는 이 티켓을 검증할 수 없다.** `projects/iam-platform/build.gradle` 의 `test { useJUnitPlatform { excludeTags 'integration' } }` 가 **애플리케이션 컨텍스트를 띄우는 유일한 6개 클래스를 통째로 제외**한다. `:test` 240 GREEN 은 컨텍스트가 뜬다는 증거가 **아니다** — 부팅이 깨져도 초록이 나온다. 처음에 `:test` 만 돌려 초록을 확인했고, 소스 48개 테스트 클래스 대비 XML 42개라는 **불일치를 추적한 끝에** 이 사실을 발견했다. AC-7/AC-8/Test Requirements 를 이에 맞춰 정정함.

**무변경 증명.** `git diff --stat -- libs/` = **비어 있음**. 다른 iam 서비스 4개 = **비어 있음**. 다른 프로젝트 = 변경 0. Flyway/Dockerfile/logback/계약 = 변경 0(패키지 문자열 미포함 확인).

**권위.** 로컬 Windows Docker 는 FLAKY — **CI Linux 레인이 최종 권위**.

---

# Definition of Done

- [x] architecture.md 선행 갱신 (AC-1)
- [ ] main 89 + test 48 파일 이전 완료, `git mv` 로 rename 추적
- [ ] 문자열 좌표 4종 갱신 (AC-4·AC-5)
- [ ] `libs/java-security` diff **0 바이트** — `git diff --stat libs/` 로 증명
- [ ] 다른 iam 서비스 / 다른 프로젝트 diff 0
- [ ] `:test` GREEN, 개수 baseline 동일 (AC-7)
- [ ] 저장소 전역 grep 분류 결과를 PR 본문에 기재 (AC-6)
- [ ] CI GREEN (IT 레인 포함)
- [ ] Ready for review
