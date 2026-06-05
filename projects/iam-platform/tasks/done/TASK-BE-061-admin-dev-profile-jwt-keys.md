# Task ID

TASK-BE-061

# Title

admin-service dev profile — JWT PEM / TOTP 키 baked-in 설정

# Status

ready

# Owner

backend

# Task Tags

- code

# depends_on

(없음)

---

# Goal

admin-service는 `ADMIN_JWT_V1_PEM`(다중 라인 PKCS#8 PEM), `ADMIN_TOTP_V1_KEY`(base64 32바이트) 환경변수를 필수로 요구한다. 현재 `application.yml` 기본값은 placeholder(`dev-only-placeholder-pkcs8`)로 실제 파싱 실패를 유발하여 개발자가 매번 긴 PEM을 bash `export`로 주입해야 한다.

테스트 값은 이미 `docker-compose.e2e.yml`과 admin-service 단위 테스트에 공개되어 있으므로(명시적으로 "FIXED TEST VALUES, DO NOT use in production" 표기), 이를 `application-dev.yml`에 baked-in하여 `./gradlew :apps:admin-service:bootRun --args='--spring.profiles.active=dev'`만으로 기동 가능하게 만든다.

발견 시점: 세션 말미 로컬 전체 스택 기동 시 admin-service 부팅 실패 (`PEM for kid=v1 is not valid base64`).

---

# Scope

## In Scope

1. **신규 파일**: `apps/admin-service/src/main/resources/application-dev.yml`
   - `admin.jwt.signing-keys.v1`: docker-compose.e2e.yml에 있는 테스트 PKCS#8 PEM (다중 라인, YAML literal block `|`)
   - `admin.totp.signing-keys.v1`: `REVWX09OTFlfVE9UUF9LRVlfMzJfQllURVNfISEhWFg=` (동일 테스트 값)
   - 프로파일 상단 주석: "DEV PROFILE ONLY — FIXED TEST VALUES, never deploy to production"

2. **신규 파일**: `apps/admin-service/src/main/resources/keys/dev-admin-private.pem` (대안)
   - 또는 `application-dev.yml`에 `signing-keys.v1`을 classpath 참조로 (`classpath:keys/dev-admin-private.pem`)

   → 구현 시 둘 중 편한 쪽 선택. YAML literal block이 더 간단.

3. **README 보강**
   - Quick Start 섹션에 admin-service 기동 방법 추가:
     ```bash
     ./gradlew :apps:admin-service:bootRun --args='--spring.profiles.active=dev'
     ```
   - 또는 `SPRING_PROFILES_ACTIVE=dev` env로도 가능함을 명시

4. **`.gitignore` 확인** — `application-dev.yml`이 무시 대상에 들어있지 않은지 확인. 테스트값만 담기므로 체크인되어도 무방.

## Out of Scope

- 프로덕션 secret 관리 (Vault, AWS Secrets Manager 등)
- 다른 서비스(auth-service 등)의 JWT 서명키 관리 — 이들은 이미 `classpath:keys/private.pem` 자동 로드로 동작 중
- 새 dev 키 페어 생성 — 기존 테스트 값 그대로 재활용
- admin-web 로그인 흐름 변경

---

# Acceptance Criteria

- [ ] `apps/admin-service/src/main/resources/application-dev.yml` 존재
- [ ] YAML 내 `admin.jwt.signing-keys.v1`에 docker-compose.e2e.yml과 동일한 테스트 PEM 포함
- [ ] YAML 내 `admin.totp.signing-keys.v1`에 동일 테스트 base64 키 포함
- [ ] 상단에 `# DEV PROFILE ONLY — FIXED TEST VALUES, never deploy to production` 주석
- [ ] `./gradlew :apps:admin-service:bootRun --args='--spring.profiles.active=dev'` 로 `ADMIN_JWT_V1_PEM` / `ADMIN_TOTP_V1_KEY` env 미설정 상태에서 기동 성공 (`Started AdminApplication in N seconds` 로그 + `/actuator/health` 200)
- [ ] README Quick Start에 기동 명령어 반영
- [ ] `./gradlew :apps:admin-service:test` 회귀 없음

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/jwt-keys.md` (존재 시)
- `platform/security-rules.md`

---

# Related Contracts

(없음 — 서비스 내부 설정)

---

# Target Service

- `apps/admin-service`

---

# Architecture

layered 4-layer 구조 그대로. infrastructure/config/JwtConfig가 동일 property 소스 읽음.

---

# Edge Cases

- dev profile이 프로덕션에서 실수로 활성화 → 운영 환경 `SPRING_PROFILES_ACTIVE=prod,...`로 명시적으로 분리되어 있을 것. README에 "dev profile은 로컬 전용" 경고
- PEM의 라인 끝 공백 / CRLF 이슈 (Windows) → YAML `|` literal block은 LF 유지, `AdminJwtKeyStore.parsePkcs8RsaPrivateKey`가 `\\s`로 whitespace 제거하므로 개행 처리 문제 없음
- `test` profile과 충돌 가능성 → admin-service 현 test 구성 확인 후 다른 값 사용하지 않도록

---

# Failure Scenarios

- dev profile 미활성 + env 미설정 → 현재와 동일하게 placeholder PEM로 부팅 실패 → 이 태스크 AC는 dev profile 활성 경로만 커버. README에 명시.
- PEM copy 실수 (truncated) → bootRun 시 즉시 base64 decode 오류 → 테스트 PEM 원본(docker-compose.e2e.yml)과 diff 비교 가능

---

# Test Requirements

- `./gradlew :apps:admin-service:test` 회귀 없음
- 수동 smoke:
  ```bash
  docker compose up -d mysql redis kafka
  ./gradlew :apps:admin-service:bootRun --args='--spring.profiles.active=dev'
  curl http://localhost:8085/actuator/health | grep '"status":"UP"'
  ```

---

# Definition of Done

- [ ] `application-dev.yml` 작성 + 테스트 값 주입
- [ ] README 기동 명령 업데이트
- [ ] 단위 테스트 회귀 없음
- [ ] 수동 smoke 통과
- [ ] Ready for review
