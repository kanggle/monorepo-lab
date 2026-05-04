# Task ID

TASK-MONO-034

# Title

libs/java-security 패키지명 com.gap.security → com.example.security 정규화

# Status

in-progress

# Owner

backend

# Task Tags

- refactor
- libs
- chore

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-MONO-031 audit 에서 발견된 `libs/java-security` 모듈의 project-specific 패키지명 (`com.gap.security.*`) 을 표준 패키지명 (`com.example.security.*`) 으로 정규화한다.

`platform/shared-library-policy.md` 는 공유 라이브러리가 어느 특정 프로젝트 도메인 용어를 패키지명으로 사용해선 안 된다고 명시한다. `com.gap.security.*` 는 GAP(global-account-platform) 을 노출하므로, 다른 프로젝트(wms, ecommerce, fan-platform)가 이 라이브러리를 사용할 때 `com.gap.*` import 가 강제되는 문제가 있다.

---

# Scope

## In Scope

### 1. libs/java-security 소스 파일 패키지명 변경

변경 전: `com.gap.security.{jwt,password,pii,redis}`
변경 후: `com.example.security.{jwt,password,pii,redis}`

대상 파일 (main + test 모두):
- `com/gap/security/jwt/JwksProvider.java`
- `com/gap/security/jwt/JwtSigner.java`
- `com/gap/security/jwt/JwtVerificationException.java`
- `com/gap/security/jwt/JwtVerifier.java`
- `com/gap/security/jwt/Rs256JwtSigner.java`
- `com/gap/security/jwt/Rs256JwtVerifier.java`
- `com/gap/security/password/Argon2idPasswordHasher.java`
- `com/gap/security/password/PasswordHasher.java`
- `com/gap/security/pii/PiiMaskingUtils.java`
- `com/gap/security/redis/RedisKeyHelper.java`
- Test files in `src/test/java/com/gap/security/`

디렉토리 이동: `src/main/java/com/gap/` → `src/main/java/com/example/`

### 2. global-account-platform import 일괄 갱신

`projects/global-account-platform` 에서 `import com.gap.security.*` 를 `import com.example.security.*` 로 변경.

### 3. fan-platform + wms-platform 확인

현재 0 importers 이지만 빌드 dep 선언이 있으므로 빌드 확인 후 import 변경 없음 확인.

## Out of Scope

- wms / fan-platform 의 미사용 java-security 빌드 dep 제거 — 별도 task (제거 시 사전 검토 필요)
- `JwksProvider`, `RedisKeyHelper` 의 0-importer 정리 — 별도 결정 필요

---

# Acceptance Criteria

- [ ] `libs/java-security` 내 모든 파일이 `com.example.security.*` 패키지 사용.
- [ ] `projects/global-account-platform` 의 모든 `import com.gap.security.*` 가 `import com.example.security.*` 로 교체.
- [ ] `./gradlew :libs:java-security:check` PASS.
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:check` PASS (+ 다른 GAP 서비스).
- [ ] `./gradlew :projects:wms-platform:apps:gateway-service:compileJava` PASS.
- [ ] `./gradlew :projects:fan-platform:apps:gateway-service:compileJava` PASS.

---

# Related Specs

- `platform/shared-library-policy.md`
- `tasks/review/TASK-MONO-031-libs-audit.md`

---

# Edge Cases

- `com.gap.security.*` 패키지 문자열이 Java 외 파일(YAML, 문서 등)에 하드코딩된 경우 함께 갱신.
- IDE auto-import 잔재: grep 으로 이중 확인.

---

# Failure Scenarios

- GAP 서비스 중 reflection 으로 `com.gap.security.*` 클래스를 로드하는 경우: 런타임 `NoClassDefFoundError`. 변경 후 통합 테스트까지 확인.

---

# Test Requirements

- `./gradlew check` (libs + 영향 서비스) PASS 확인.

---

# Definition of Done

- [ ] 패키지명 정규화 완료.
- [ ] 영향 서비스 빌드/테스트 PASS.
- [ ] Ready for review.

---

# Prerequisites

- TASK-MONO-031 완료 (audit 선행)
