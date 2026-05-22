# Task ID

TASK-MONO-131

# Title

Root `build.gradle` `-Xlint:-serial` defensive future-proof + libs/ shared base exception 3건 `serialVersionUID` 부여 — backend `-Xlint:all` audit 시 cosmetic `[serial]` warning ~200 file cluster 의 portfolio-wide noise 회피 + key serialization-touch-surface 의 explicit UID 보장.

# Status

done

# Owner

backend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- deploy

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

# Dependency Markers

- **depends on**: 없음. Java baseline `[serial]` warning 은 `-Xlint:all` 또는 `-Xlint:serial` 명시 활성화 시에만 fire. 현재 root `build.gradle` 의 `subprojects { tasks.withType(JavaCompile).configureEach { ... } }` block 에 `-Xlint` 설정 0 (audit verified) — 즉 본 task 의 gradle disable 은 *향후 audit 활성화 시 silently-skip* 보장 defensive future-proof.
- **origin**: ① backend `-Xlint:all` audit chain (post TASK-MONO-130 KafkaContainer migration) 잔여 categories 중 `[serial]` 이 ~200 exception file cluster — Exception 직렬화 미사용 architecture 에서 cosmetic noise. ② Spring Boot microservices + Hexagonal architecture 에서 exception 은 control flow signal 로만 사용 (Kafka event payload 아닌 control flow throw/catch). cross-VM serialize 발생 가능성 = error envelope 직렬화 시점 (`@RestControllerAdvice` → JSON ErrorResponse) — Exception class 자체는 JSON-serialize 되지 않음.
- **prerequisite for**: 없음. LoginController sunset (2026-08-01) + 다른 deferred backlog 와 독립.

---

# Goal

`[serial]` warning category 의 portfolio-wide defensive suppression + libs shared base exception 3건의 explicit `serialVersionUID` 부여.

**Background**:

- Java baseline `-Xlint:serial` warning 은 `Serializable` (혹은 그 supertype `Exception` etc.) 을 implement 한 class 가 명시 `private static final long serialVersionUID` 부재 시 fire.
- 본 codebase 의 ~200 exception classes 모두 `serialVersionUID` 부재 — `-Xlint:all` audit 시 ~200 warning instance.
- 실 codebase architecture 는 exception 의 cross-VM serialization 미사용 (Spring `@RestControllerAdvice` 가 JSON `ErrorResponse` 로 conversion, exception object 자체 직렬화 아님). cosmetic 노이즈.

**Two surgical changes**:

1. **Gradle defensive future-proof** — root `build.gradle` `subprojects { ... }` block 에 `tasks.withType(JavaCompile).configureEach { options.compilerArgs.add('-Xlint:-serial') }` 추가. 향후 `-Xlint:all` 이 enable 되어도 `[serial]` 자동 suppress.

2. **libs shared base exception 3건 explicit UID** — `libs/java-security`, `libs/java-web`, `libs/java-messaging` 의 base exception (`JwtVerificationException`, `AccessDeniedException`, `EventSerializationException`) 에 `private static final long serialVersionUID = 1L;` 추가. 이 3 file 은 cross-module shared base 로 다른 lib + projects exceptions 가 import/extend 할 수 있음 — explicit UID 가 future binary compatibility 보장.

**Per-project / per-service exception classes (~200 files) 는 NOT touched** — gradle suppression 이 cover.

**Behavior change 0**:

- Gradle: warning emission 만 영향 — compile success / runtime / test invariant 0 변화.
- UID: explicit `1L` 부여 = Java auto-generated UID 의 explicit default. 직렬화 동작 byte-unchanged (실제로 직렬화 안 됨).

---

# Decision authority

- **Why monorepo-level (`tasks/`) and NOT project-internal**: root `build.gradle` 변경 = shared library / cross-project effect.
- **Why single impl PR**: 4 file 변경 (1 build.gradle + 3 libs exception), atomic.
- **Why `-Xlint:-serial` (NOT broader `-Xlint:all,-serial`)**: 사용자 선택 옵션 "[serial] gradle disable" 의 literal interpretation — `-Xlint:serial` 만 specific disable. 다른 warning category enable 은 별 scope. `-Xlint:all` 명시 활성화는 향후 task 가 다룰 수 있는 separate audit.
- **Why key 3 libs exception only (NOT all ~200 portfolio exceptions)**: ~200 file 에 UID 추가 = +200 LOC 노이즈 with 0 functional value (Exception 직렬화 미사용). Gradle suppression 가 cover. Libs/ shared base 3 file 만 explicit UID 부여 = library binary compatibility convention + cross-module subtype 가 inherit 안 함 (each subclass 가 자체 UID 필요 if needed, but `1L` default 는 cosmetic).
- **Why no spec/contract change**: build infrastructure + library cosmetic, API/event contract 영향 0.
- **Why no ADR**: HARDSTOP-09 not triggered. Gradle warning config + Java library convention = `shared-library-policy.md` 의 framework lifecycle. 새 architectural decision 아님.

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- 본 task file.
- `tasks/INDEX.md` — root INDEX ready entry.

**Code (impl PR — out of scope here, dispatch shape)**:

- `build.gradle` (root, line 7 `subprojects {...}` block 내부) — `tasks.withType(JavaCompile).configureEach { options.compilerArgs.add('-Xlint:-serial') }` 추가.
- `libs/java-security/src/main/java/com/example/security/jwt/JwtVerificationException.java` — `private static final long serialVersionUID = 1L;` 추가.
- `libs/java-web/src/main/java/com/example/web/exception/AccessDeniedException.java` — `private static final long serialVersionUID = 1L;` 추가.
- `libs/java-messaging/src/main/java/com/example/messaging/event/EventSerializationException.java` — `private static final long serialVersionUID = 1L;` 추가.

**4 file 총 변경 (+5 / -0).**

## Out of Scope

- ~200 portfolio per-service exception classes 의 UID 추가 (gradle suppression 으로 cover, LOC 노이즈 회피).
- Broader `-Xlint:all` 활성화 (별 future task — 현재 baseline 은 default warnings 만).
- `-Xlint:-processing` (annotation processor noise suppression) — 별 future task.
- `@SuppressWarnings("serial")` per-class 추가 (gradle global suppression 가 cover).
- Exception serialization 의 runtime 행동 변경 (control flow only architecture 보존).

---

# Acceptance Criteria

**AC-1** — root `build.gradle` 의 `subprojects {}` block 내부에 `tasks.withType(JavaCompile).configureEach { options.compilerArgs.add('-Xlint:-serial') }` configuration block 가 추가됨.

**AC-2** — `libs/java-security/.../JwtVerificationException.java` 에 `private static final long serialVersionUID = 1L;` field 추가됨.

**AC-3** — `libs/java-web/.../AccessDeniedException.java` 에 `private static final long serialVersionUID = 1L;` field 추가됨.

**AC-4** — `libs/java-messaging/.../EventSerializationException.java` 에 `private static final long serialVersionUID = 1L;` field 추가됨.

**AC-5** — LOCAL `./gradlew compileJava` 모든 subprojects BUILD SUCCESSFUL (gradle config 변경 검증).

**AC-6** — LOCAL `./gradlew --init-script <transient> compileJava` (transient init script 가 `-Xlint:all` enable) 시 `[serial]` warning 가 4 file (build.gradle 적용된 후, libs 3 file 의 UID 추가) 에 대해 emit 안 됨. **검증 방법**: `-Xlint:all` 이 enable 됐다는 가정 하에서도 root build.gradle 의 `-Xlint:-serial` 가 serial category 만 silently-skip 보장.

**AC-7** — CI Linux 전체 build matrix GREEN (compile + test 통과, warning emission 변경 없음).

**AC-8** — Production code byte-unchanged: `git diff --stat origin/main -- 'projects/'` empty. libs/ 의 3 file 만 +1 line 씩 (UID field), 다른 production class 0 변경.

**AC-9** — Zero ADR drift: `git diff --stat origin/main -- 'docs/adr/'` empty.

**AC-10** — Zero spec/contract drift: `git diff --stat origin/main -- 'projects/*/specs/' 'platform/' 'rules/'` empty.

---

# Related Specs

- `platform/shared-library-policy.md` — libs/ shared library 의 binary compatibility convention. Explicit `serialVersionUID` 가 향후 schema evolution 시 InvalidClassException 회피 책임.
- 본 task 가 spec 변경 없이 implementation-only.

---

# Related Contracts

- 없음. Build infrastructure + library internal cosmetic.

---

# Edge Cases

- **`-Xlint:-serial` syntax**: `options.compilerArgs.add('-Xlint:-serial')` 가 정확한 syntax. `-` prefix 가 specific warning category disable. `-Xlint:all,-serial` 도 가능하지만 본 task 는 specific-only disable (전체 enable 은 별 scope).
- **Future `-Xlint:all` activation**: 별 task 가 enable 시 본 task 의 `-Xlint:-serial` 가 정상 작동 — `addAll(['-Xlint:all', '-Xlint:-serial'])` order 무관 (Java compiler 가 모두 parse).
- **Exception subclass inheritance**: subclass 가 base UID 를 inherit 안 함 (Java Serializable convention). 각 subclass 가 자체 UID 가 필요하지만 gradle suppression 이 cover.
- **UID value `1L`**: Java 의 explicit-default initial value. Production 사용 시 schema evolution 시점에 변경 가능 (back-compat 깨질 때만). 본 task 시점 = initial declaration.
- **Lombok `@Data` / `@EqualsAndHashCode` interaction**: 3 target libs exception 모두 Lombok annotation 미사용 — plain Java. 충돌 없음 (audit verified).

---

# Failure Scenarios

- **F1 — Gradle `options.compilerArgs` add 시 syntax 오류**: LOCAL `./gradlew compileJava` 가 catch. Gradle Groovy DSL 의 `addAll([...])` form 사용 (`.add('-Xlint:-serial')` 도 가능). 발생 시 STOP + syntax revert.
- **F2 — Subprojects `JavaCompile` task 가 `subprojects {}` lifecycle 에서 미존재**: `tasks.withType(JavaCompile).configureEach { ... }` 가 *lazy* configuration — task 생성 시점에 적용. timing 문제 없음.
- **F3 — Lombok generated code 가 `serialVersionUID` 충돌**: 3 target 모두 Lombok-free, 충돌 없음 (verified).

---

# Implementation hints (dispatch agent)

1. `git checkout -b task/mono-131-impl-xlint-serial-disable-and-uid-add`.
2. Root `build.gradle` 의 `subprojects {}` block 끝부분 (closing `}` 전) 에 다음 추가:
   ```
   tasks.withType(JavaCompile).configureEach {
       options.compilerArgs.add('-Xlint:-serial')
   }
   ```
3. 3 libs exception file 에 `private static final long serialVersionUID = 1L;` 추가 (class declaration 직후, 첫 field/constructor 이전):
   - `libs/java-security/src/main/java/com/example/security/jwt/JwtVerificationException.java`
   - `libs/java-web/src/main/java/com/example/web/exception/AccessDeniedException.java`
   - `libs/java-messaging/src/main/java/com/example/messaging/event/EventSerializationException.java`
4. LOCAL verify:
   ```
   ./gradlew compileJava --no-daemon -q  # expect BUILD SUCCESSFUL
   ```
5. Push branch + open PR; CI Linux 가 권위 (전체 build matrix GREEN).
6. BE-303 3-dim 객관 머지 검증 후 close chore.

---

# 분석 / 구현 / 리뷰 모델 권장

- **분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (simple — 4 file / 5 LOC, mechanical)**.
