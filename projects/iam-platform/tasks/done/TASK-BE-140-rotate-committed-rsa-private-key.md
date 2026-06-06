# Task ID

TASK-BE-140

# Title

auth-service — 커밋된 RSA private key 회수 및 .gitignore/startup guard 추가 (Critical C-1)

# Status

done

# Owner

backend

# Task Tags

- security
- critical

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

2026-04-27 보안 리뷰(C-1, Critical)에서 실 RSA private key 가 저장소에 커밋된 것이 확인됐다.

- 파일: `apps/auth-service/src/main/resources/keys/private.pem`
- 28라인 PKCS8 RSA 키, `application.yml` 의 `auth.jwt.private-key-path` 기본값(`classpath:keys/private.pem`)이 이 키를 사용
- `.gitignore` 에 `*.pem` / `keys/*` 패턴 부재 — 미래 재커밋 방지 안 됨

저장소 read 권한이 있는 누구나(과거 git history 포함) 이 키로 임의 JWT 를 서명해 전체 플랫폼 인증을 우회할 수 있다.

본 태스크는 다음을 수행한다:

1. `.gitignore` 에 키 파일 패턴 추가 (`**/keys/*.pem`, `**/keys/*.key`)
2. classpath 기본 키를 placeholder 로 대체 (`private.pem.example`) 하고 실 키는 추적 제거
3. 기동 시점에 `auth.jwt.private-key-path` 가 classpath 리소스 + 비-`local` 프로파일이면 `IllegalStateException` 발생하는 startup guard 추가
4. **운영 키 회전 자체는 사람 손이 필요한 작업** — 본 태스크는 코드/설정 change 까지만 책임지며, 회전 절차는 별도 운영 런북으로 사용자가 수행

---

# Scope

## In Scope

- `.gitignore` 업데이트
- `apps/auth-service/src/main/resources/keys/private.pem` git 추적 제거 (`git rm --cached` 후 placeholder 추가)
- `apps/auth-service/src/main/resources/keys/public.pem` 도 함께 추적 제거 검토 (대응 키 쌍)
- `JwtConfig` 또는 신규 `JwtKeyPathValidator` 컴포넌트에 startup guard 추가:
  - 활성 프로파일이 `local`/`test` 아님 + `private-key-path` 가 `classpath:` 로 시작 → `IllegalStateException` 으로 부팅 실패
- `apps/auth-service/src/main/resources/application.yml` 의 기본값을 placeholder 경로로 변경하거나, env 미지정 시 명확한 에러 메시지가 나오도록 변경

## Out of Scope

- 운영 환경 키 회전 (사람 손, 별도 런북)
- git history rewrite (`git filter-repo` 등) — 별도 의사결정 필요
- 다른 서비스의 키 처리(account-service 등)는 본 태스크 범위 외 — 별도 점검 필요 시 후속 태스크
- `public.pem` 은 공개해도 무방하므로 `.gitignore` 추가는 선택 사항

---

# Acceptance Criteria

- [ ] `.gitignore` 에 `**/keys/*.pem` / `**/keys/*.key` 패턴 추가됨
- [ ] `apps/auth-service/src/main/resources/keys/private.pem` 가 git 추적에서 제거됨 (`git ls-files` 결과 미포함)
- [ ] 동일 디렉토리에 `private.pem.example` placeholder 파일 추가, README 또는 주석으로 사용법 안내
- [ ] 비-local 프로파일에서 `private-key-path` 미설정 또는 classpath 기본값 시 부팅이 `IllegalStateException` 으로 실패
- [ ] `:apps:auth-service:test` 통과 (테스트 프로파일에서는 가드 우회되도록)
- [ ] 운영 키 회전 필요성을 README 또는 회수 안내 문서에 명시

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `platform/service-types/<해당>.md`

# Related Skills

- `.claude/skills/backend/security/SKILL.md` (있으면)

---

# Related Contracts

- 없음 (코드/설정 변경, API 계약 영향 없음)

---

# Target Service

- `auth-service`

---

# Architecture

Follow `specs/services/auth-service/architecture.md`.

---

# Implementation Notes

- startup guard 위치: `JwtConfig` 의 `@PostConstruct` 또는 별도 `@Component` `ApplicationListener<ApplicationReadyEvent>`.
- 가드 조건:
  ```java
  if (!isLocalOrTestProfile() && privateKeyPath.startsWith("classpath:")) {
      throw new IllegalStateException(
          "JWT private key path must be filesystem path in non-local profiles. " +
          "Set JWT_PRIVATE_KEY_PATH to a runtime-supplied secret.");
  }
  ```
- placeholder 파일 내용: 명백한 더미 텍스트 + 사용 안내 코멘트.

---

# Edge Cases

- 테스트 프로파일에서는 classpath 키 사용 허용 — `Environment.getActiveProfiles()` 로 분기.
- CI 환경에서 `JWT_PRIVATE_KEY_PATH` 가 없으면 부팅 실패 — 의도된 동작. CI 워크플로에 secret 주입 또는 `local` 프로파일 사용 안내.

---

# Failure Scenarios

- placeholder 파일이 실제 키처럼 보이면 혼란 야기 — 명백한 더미임을 표시.
- `git rm --cached` 만 하고 commit 안 하면 추적 제거 안 됨 — 반드시 동일 PR 에서 커밋.
- git history 는 여전히 키 보유 — 이는 본 태스크 out-of-scope, 운영 회전으로만 무력화 가능.

---

# Test Requirements

- `JwtKeyPathValidatorTest` (또는 `JwtConfigTest`):
  - `prod` 프로파일 + classpath 경로 → `IllegalStateException`
  - `prod` 프로파일 + 파일시스템 경로 → 정상 부팅
  - `local`/`test` 프로파일 + classpath 경로 → 정상 부팅
- 통합 테스트는 기존 동작 유지 확인.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] private.pem 추적 제거 확인 (`git ls-files`)
- [ ] .gitignore 업데이트 확인
- [ ] startup guard 동작 확인
- [ ] Ready for review
- [ ] **별도 운영 런북에서 키 회전이 수행되어야 함을 README 에 명시**
