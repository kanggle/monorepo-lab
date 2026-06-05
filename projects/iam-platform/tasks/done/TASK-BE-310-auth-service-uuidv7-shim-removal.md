# Task ID

TASK-BE-310

# Title

auth-service UuidV7 shim 제거 — `@Deprecated(forRemoval=true, since="TASK-BE-028c")` 마킹된 forwarding shim (`com.example.auth.domain.session.UuidV7`) 을 제거하고 단일 production caller (`RegisterOrUpdateDeviceSessionUseCase`) 를 canonical `com.example.common.id.UuidV7` 로 이관한다. TASK-BE-028c-fix § "후속 클린업 태스크로 RegisterOrUpdateDeviceSessionUseCase 이관" 약속 closure. backend `-Xlint:all` removal 잔여 1건 해소.

# Status

done

# Owner

backend

# Task Tags

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

# Dependency Markers

- **depends on**: 없음. TASK-BE-028c (libs/java-common 으로 `UuidV7` 승격) + TASK-BE-028c-fix (forwarding shim 의 `@Deprecated(forRemoval=true)` 마킹 + javadoc migration path 명시) 양쪽 이미 main DONE.
- **origin**: backend `-Xlint:all` audit (이전 cycle PR #743 this-escape sweep 직후). 잔여 deprecation 9 + removal 1 중 removal 1건이 본 shim 의 `@Deprecated(forRemoval=true)` 마킹임. shim javadoc § "New code MUST NOT reference this class — it is slated for removal." 의 약속을 실행한다.
- **prerequisite for**: 없음 (cleanup-only task). 후속 task 트리거 없음.

---

# Goal

`com.example.auth.domain.session.UuidV7` (forwarding shim, `@Deprecated(forRemoval=true, since="TASK-BE-028c")`) 을 제거한다.

Migration path 는 shim 의 javadoc 가 명시 — API surface 가 canonical `com.example.common.id.UuidV7` 와 byte-identical (`randomUuid()`, `randomString()`, `timestampMs(UUID)` 모두 forward). 단일 production caller (`RegisterOrUpdateDeviceSessionUseCase`) 의 `import` 만 교체하면 호출 사이트 변경 0 (identical method 시그니처).

목표:

1. `RegisterOrUpdateDeviceSessionUseCase.java` 의 `import com.example.auth.domain.session.UuidV7;` → `import com.example.common.id.UuidV7;` 1-line 교체.
2. `UuidV7.java` (shim, `apps/auth-service/src/main/java/com/example/auth/domain/session/UuidV7.java`) 파일 삭제.
3. `UuidV7ShimTest.java` (`apps/auth-service/src/test/java/com/example/auth/domain/session/UuidV7ShimTest.java`) 파일 삭제 — shim 의 javadoc § "When the shim is eventually removed, this test is expected to be deleted alongside it." 명시.

**스코프 외 (out of scope)**: 다른 9개의 deprecation warning (per-call-site migration intent audit 필요 — 별 task 들로 처리).

---

# Decision authority

- **Why no spec change**: shim 의 javadoc + TASK-BE-028c-fix § Fix 2 가 이미 migration path 권위. 새로운 API/behavior 결정 없음.
- **Why no contract change**: 본 task 가 다루는 file 들은 모두 `auth-service` 의 internal implementation. 외부 contract (event payload, HTTP API) 영향 없음.
- **Why shim test 도 삭제**: `UuidV7ShimTest` 가 verifying 하는 것은 "shim forwards to canonical correctly" — shim 이 제거되면 검증 대상 자체가 없음. canonical `UuidV7` 의 v7 invariant 는 `libs/java-common/src/test/java/com/example/common/id/UuidV7Test.java` 가 이미 cover (project_be_153_driven_audit_series 시리즈 시점 verify 됨).
- **Why no ADR**: HARDSTOP-09 not triggered. 단순 forwarding shim 제거 = `architecture.md` 의 architectural decision 영향 0. shared-library-policy.md 의 "v1 promoted utility 이후 legacy shim sunset" 정상 lifecycle.
- **Why no behavior change**: `UUID v7` 생성 logic 가 forward target (canonical) 에 그대로 남음 — `RegisterOrUpdateDeviceSessionUseCase.execute(...)` 가 호출하는 `UuidV7.randomString()` 의 반환 type/format 가 byte-identical (same `com.example.common.id.UuidV7.randomString()` 가 양쪽에서 동일 결과 반환).

---

# Scope

## In Scope

**Specs (spec PR — this PR)**:

- 본 task file.
- `projects/global-account-platform/tasks/INDEX.md` — ready entry.

**Code (impl PR — out of scope here, dispatch shape)**:

- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/application/RegisterOrUpdateDeviceSessionUseCase.java` — `import` 교체 1줄.
- `projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/domain/session/UuidV7.java` — DELETE.
- `projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/domain/session/UuidV7ShimTest.java` — DELETE.

**Tests**:

- 추가 test 작성 없음 (canonical `UuidV7Test` 가 이미 cover). 기존 `auth-service` unit + integration suite 가 PASS 유지 검증 (`RegisterOrUpdateDeviceSessionUseCase` 가 device-session register 시 UUID v7 생성 — `DeviceSessionRegisterIntegrationTest` (or 유사) 가 implicit verify).

## Out of Scope

- 다른 9개의 deprecation warning (`@Deprecated` non-removal): per-call-site audit 가 필요 (migration target 확정 + intent decision). 별 task 분리.
- shared library `com.example.common.id.UuidV7` 의 API 확장.
- `device-session` 의 UUID format 변경.
- 다른 services 의 UUID 생성 경로 audit.

---

# Acceptance Criteria

**AC-1** — `RegisterOrUpdateDeviceSessionUseCase.java` 의 import 가 `com.example.common.id.UuidV7` 로 교체되었고, 본문의 `UuidV7.randomString()` 호출은 byte-unchanged.

**AC-2** — `apps/auth-service/src/main/java/com/example/auth/domain/session/UuidV7.java` 파일이 main 트리에 존재하지 않는다 (`git ls-files` empty match).

**AC-3** — `apps/auth-service/src/test/java/com/example/auth/domain/session/UuidV7ShimTest.java` 파일이 main 트리에 존재하지 않는다.

**AC-4** — Repo-wide grep `com\.example\.auth\.domain\.session\.UuidV7` 가 production code 에 0 match — 단 `tasks/done/TASK-BE-028c-*.md` 같은 historical 문서 hit 은 허용.

**AC-5** — `./gradlew :projects:global-account-platform:apps:auth-service:check` LOCAL `:compileJava` 시 `[removal]` warning 가 발생하지 않는다.

**AC-6** — CI Linux runner 의 `Integration (global-account-platform, Testcontainers)` job 가 GREEN — `device-session` 관련 IT 가 새 import 경로 + 동일 UUID v7 invariant 로 PASS.

**AC-7** — Zero retrofit 검증: `git diff --stat origin/main -- 'projects/{wms,scm,erp,fan,ecommerce,finance,platform-console}-platform/'` empty, `git diff --stat origin/main -- 'libs/'` empty.

---

# Related Specs

- `projects/global-account-platform/specs/services/auth-service/device-session.md` — `RegisterOrUpdateDeviceSessionUseCase` 의 deviceId 생성 invariant (UUID v7) — 변경 없음 (canonical 가 동일 invariant 보장).
- 본 task 가 spec 변경 없이 implementation-only.

---

# Related Contracts

- 없음. `auth-service` 내부 implementation cleanup.

---

# Edge Cases

- **호출 사이트가 method 호출 외에 `UuidV7.class` reference 가 있는 경우**: grep 으로 production code 에 0 match 확인 (`com\.example\.auth\.domain\.session\.UuidV7` literal). 현재 단일 import-and-static-method-call 패턴만 존재함을 확인했다.
- **`@SuppressWarnings("deprecation")` 잔재**: `UuidV7ShimTest` 가 class-level `@SuppressWarnings("deprecation")` 를 가지지만 test 파일 자체를 삭제하므로 cleanup 불필요.

---

# Failure Scenarios

- **F1 — import 교체 시 `com.example.common.id.UuidV7` 가 같은 method signature 를 제공하지 않을 때**: spec 시점 verify — `randomString(): String`, `randomUuid(): UUID`, `timestampMs(UUID): long` 세 method 가 byte-identical 시그니처임을 `libs/java-common/src/main/java/com/example/common/id/UuidV7.java` (line 30/57/62) 확인. impl 시점 추가 검증 없음.
- **F2 — 다른 import 가 같은 alias `UuidV7` 를 사용**: `RegisterOrUpdateDeviceSessionUseCase.java` 의 import 영역 검토 — line 7 가 단일 `UuidV7` import. 충돌 없음.
- **F3 — IT 가 `UuidV7ShimTest` 의 패키지 reference 를 hard-code 했을 경우**: `UuidV7ShimTest` 는 standalone unit test. 다른 test 의 reference 없음 (grep 검증).

---

# Implementation hints (dispatch agent)

1. `git checkout -b task/be-310-impl-uuidv7-shim-removal`
2. Edit `RegisterOrUpdateDeviceSessionUseCase.java` line 7: `import com.example.auth.domain.session.UuidV7;` → `import com.example.common.id.UuidV7;`.
3. `git rm projects/global-account-platform/apps/auth-service/src/main/java/com/example/auth/domain/session/UuidV7.java`.
4. `git rm projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/domain/session/UuidV7ShimTest.java`.
5. `./gradlew :projects:global-account-platform:apps:auth-service:compileJava` LOCAL — `[removal]` warning 0 확인.
6. `./gradlew :projects:global-account-platform:apps:auth-service:test --tests "*Session*"` LOCAL (Docker-free unit subset) — green.
7. Push branch + PR; CI Linux Testcontainers job 가 IT GREEN.
8. BE-303 3-dim 객관 머지 검증 후 close chore.

---

# 분석 / 구현 / 리뷰 모델 권장

- **분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — 단순 mechanical sweep (1-line import + 2 file delete)**.
