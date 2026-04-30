# Task ID

TASK-BE-002

# Title

libs smoke build + java-security 모듈 scaffold — JWT 서명/검증, 패스워드 해시 유틸리티 기반 구축

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-001

---

# Goal

`libs/java-security` 모듈에 auth-service와 gateway-service가 공유할 JWT 서명/검증 유틸리티, 패스워드 해시(argon2id) wrapper, Redis 키 헬퍼의 **인터페이스와 최소 구현체**를 추가하고, 나머지 5개 libs 모듈이 이상 없이 빌드되는지 확인한다.

---

# Scope

## In Scope

- `libs/java-security/src/main/java/` 패키지 구조 생성
- JWT 서명·검증 유틸 (RS256, `JwtSigner`, `JwtVerifier`, `JwksProvider` 인터페이스)
- 패스워드 해시 유틸 (`PasswordHasher` 인터페이스 + argon2id 기본 구현)
- `build.gradle` 의존성 추가 (JJWT 또는 Nimbus, argon2-jvm 또는 password4j)
- 단위 테스트 (해시 생성·검증, JWT 발급·검증 라운드트립)
- 6개 libs 전체 `./gradlew :libs:java-security:build` 포함 smoke test

## Out of Scope

- 서비스 모듈에 libs 연동 (TASK-BE-004, 005, 007에서)
- Redis 키 헬퍼 상세 구현 (인터페이스만, 구현은 각 서비스)
- OAuth 관련 코드

---

# Acceptance Criteria

- [ ] `libs/java-security/src/main/java/` 에 패키지 구조 존재
- [ ] `JwtSigner` 인터페이스 + RS256 기본 구현 존재
- [ ] `JwtVerifier` 인터페이스 + RS256 기본 구현 존재
- [ ] `PasswordHasher` 인터페이스 + argon2id 기본 구현 존재
- [ ] `PasswordHasher.hash(plain)` → `PasswordHasher.verify(plain, hash)` 라운드트립 테스트 통과
- [ ] `JwtSigner.sign(claims)` → `JwtVerifier.verify(token)` 라운드트립 테스트 통과
- [ ] `./gradlew :libs:java-security:build` 성공
- [ ] 나머지 5개 libs도 빌드 통과 (의존성 깨지지 않음)

---

# Related Specs

- `specs/services/auth-service/architecture.md` — domain/jwt, domain/credentials
- `specs/services/gateway-service/architecture.md` — security/TokenValidator
- `specs/features/authentication.md` — JWT 스펙, 패스워드 정책
- `specs/features/password-management.md` — argon2id 파라미터
- `platform/shared-library-policy.md` — libs에 도메인 로직 금지

# Related Skills

- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/backend/implementation-workflow/SKILL.md`

---

# Related Contracts

없음 (라이브러리 태스크)

---

# Target Service

- `libs/java-security`

---

# Architecture

공유 라이브러리. [platform/shared-library-policy.md](../../platform/shared-library-policy.md) 준수. 기술 유틸만 — 도메인 규칙(PasswordPolicy 등)은 auth-service 도메인 레이어에 둔다.

---

# Implementation Notes

- `JwtSigner`/`JwtVerifier`는 **인터페이스**로 선언. 기본 구현은 JJWT 또는 Nimbus JWT 라이브러리 사용
- RSA 키 페어는 테스트에서 in-memory 생성. 프로덕션 키 로딩은 서비스 infrastructure 레이어의 책임
- `PasswordHasher`는 알고리즘을 생성자 파라미터로 받아 argon2id 파라미터(memory=65536, iterations=3, parallelism=1)를 외부에서 설정 가능하게
- 이 모듈은 Spring 의존성을 **가지지 않아야** 한다 — 순수 Java 라이브러리

---

# Edge Cases

- argon2id 파라미터가 너무 높으면 테스트 시간 과다 → 테스트 전용 저파라미터 프로파일 사용
- JWT 키 페어 생성 실패 (환경에 따라 SecureRandom 미지원) → 테스트에서 deterministic seed

---

# Failure Scenarios

- argon2-jvm 네이티브 라이브러리가 Windows에서 로드 실패 → password4j 대안 검토 (pure Java fallback)
- libs/java-security가 다른 libs에 의존성 추가 시 순환 → java-security는 java-common만 의존

---

# Test Requirements

- `PasswordHasherTest`: hash + verify 성공, 잘못된 패스워드 verify 실패
- `JwtSignerVerifierTest`: sign + verify 성공, 만료 토큰 검증 실패, 잘못된 키로 검증 실패
- `./gradlew :libs:java-security:test` 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `./gradlew build` 전체 통과
- [ ] Ready for review
