# Task ID

TASK-BE-063

# Title

signup→login 플로우 복구 — credential_hash 저장 경로 결여 해소

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- adr

# depends_on

(없음)

---

# Goal

`POST /api/accounts/signup`은 201로 계정·프로필 row를 생성하지만 **패스워드를 어디에도 저장하지 않는다**. 이후 `POST /api/auth/login` 호출 시 auth-service가 account-service의 internal credential lookup에서 `credentialHash=null`을 받아 `Argon2idPasswordHasher.verify(null, ...)` 호출 → **`NullPointerException` → 500 INTERNAL_ERROR**. 이 플랫폼의 가장 핵심 end-to-end 플로우인 "가입 → 로그인"이 완전히 깨진 상태다.

발견 경로: TASK-BE-058 후속 로컬 스모크 테스트(gateway 8080 → signup 201 → login 500)에서 확인.

---

# Scope

## 현 상태 분석

**auth-service 인프라 (완비)**
- DB: `auth_db.credentials(account_id, credential_hash, algorithm, version, updated_at)` 테이블 존재 (V0001 migration)
- Domain/Repo: `Credential`, `CredentialHash`, `CredentialRepository`, `CredentialJpaEntity`, `CredentialJpaRepository`, `CredentialRepositoryAdapter` 모두 구현

**account-service (스텁 상태)**
- `AccountStatusUseCase.lookupByEmail()`: 명시적으로 `credentialHash=null`, `hashAlgorithm="none"` 반환. 코드 주석에 `// TODO: integrate with auth-service when credential lookup is implemented`

**auth-service LoginUseCase**
- `accountServicePort.lookupCredentialsByEmail(email)` 를 호출해 credential을 조회 — 즉 원격 HTTP 호출을 통해서만 credential을 얻으려 함
- 로컬 `CredentialRepository`를 사용하지 않음 → auth_db.credentials 테이블이 **runtime에 전혀 읽히지 않는 상태**

**signup 플로우**
- `account-service SignupUseCase.execute`는 account + profile만 저장하고 password는 무시
- 어떤 경로로도 auth_db.credentials에 row가 만들어지지 않음

## In Scope — 권장 해결 방향 (ADR 포함)

### 결정 사항 (본 태스크에서 확정해야 함)

**Credential 소유자**: auth-service (실제 구현 상태가 이미 여기에 쏠려 있음)

**Signup flow 보정안 중 택일**:
- **Option A — 동기 분산 write**: SignupUseCase가 Account 저장 후 auth-service의 새 internal endpoint `POST /internal/auth/credentials` 호출하여 argon2 해시 저장. 실패 시 Account 저장을 롤백(Saga 또는 transactional outbox)
- **Option B — 이벤트 기반 비동기 write**: account-service가 `account.credential.create.requested` 이벤트를 outbox로 발행, auth-service consumer가 수신하여 credentials row 생성. 장점: 서비스 간 강결합 없음. 단점: signup 직후 바로 로그인하면 경합(race) 가능성 (짧은 창)
- **Option C — auth-service가 signup을 주관**: `/api/auth/signup` 엔드포인트 신설, auth-service가 credential 저장 + account-service의 `/internal/accounts` 호출로 account/profile 생성 요청. 기존 `/api/accounts/signup`은 deprecate 또는 제거

→ 본 태스크에서 Option 선택 후 그에 맞춰 구현.

### Option A 구체화 (추천 — 빠른 복구)

**auth-service 변경**
1. `POST /internal/auth/credentials` 추가. Body: `{ accountId, email, password }`. argon2id 해시 생성 후 `credentials` 테이블에 insert. 201 or 409(동일 account_id 존재).
2. `LoginUseCase.lookupCredentialsByEmail`: account-service 호출을 제거하고 로컬 `CredentialRepository.findByAccountIdEmail(email)`을 사용. account 상태는 여전히 account-service에서 조회(`GET /internal/accounts/{id}/status`).
3. Contract 문서(`specs/contracts/http/internal/auth-internal.md`) 신설

**account-service 변경**
4. `SignupUseCase.execute`: Account 저장 → profile 저장 → auth-service `POST /internal/auth/credentials` 호출. 실패 시 예외 전파 + 트랜잭션 롤백으로 계정 레코드도 무효화.
5. `AccountStatusUseCase.lookupByEmail`의 credential 스텁 제거 — 계정 상태만 반환하는 API(`/internal/accounts/{id}/status`)에 집중

**libs/java-test-support**
6. 통합 테스트 fixture 업데이트 — 신규 계정 생성 시 credential 자동 주입 유틸

## Out of Scope

- OAuth 소셜 로그인 (이미 별도 플로우, credential_hash 없이 동작)
- 2FA
- Credential rotation / reset
- Apple / 기타 provider

---

# Acceptance Criteria

- [ ] 새 계정 생성 → 같은 자격으로 로그인 → 200 + JWT 페어 성공 (E2E)
- [ ] local smoke: `curl -X POST :8080/api/accounts/signup ...`(201) → 즉시 `curl -X POST :8080/api/auth/login ...` → 200
- [ ] 잘못된 password → 401 `CREDENTIALS_INVALID`
- [ ] 중복 email signup → 409 `ACCOUNT_ALREADY_EXISTS`
- [ ] 선택한 Option에 따른 contract 문서 업데이트 (A 선택 시 `auth-internal.md` 신설)
- [ ] LoginUseCase가 account-service를 credential 용도로 호출하지 않음 (계정 상태 조회만 유지)
- [ ] account-service의 `lookupCredentialsByEmail` TODO 제거 또는 API 자체 제거
- [ ] `./gradlew build` CI green
- [ ] signup/login 통합 테스트 2종 이상 추가 또는 기존 `AuthIntegrationTest.loginSuccess` 등이 실제로 credential 저장 경로까지 포함해 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/data-model.md` (credentials 테이블 소유권 확정)
- `specs/services/account-service/architecture.md`
- `specs/features/authentication.md`
- `platform/architecture-decision-rule.md` (ADR 형식)

---

# Related Contracts

- 신규: `specs/contracts/http/internal/auth-internal.md` (Option A)
- 기존: `specs/contracts/http/auth-api.md` (login 응답/에러 코드)
- 기존: `specs/contracts/http/internal/account-to-auth.md` 또는 `auth-to-account.md` (현 신호 경로 정리)

---

# Target Service

- `apps/auth-service` (primary — credential 소유)
- `apps/account-service` (signup 플로우 수정)

---

# Architecture

auth-service layered 4-layer, account-service 동일. Cross-service 동기 호출 도입(Option A) 시 `platform/error-handling.md` + resilience4j 기존 패턴 재사용.

---

# Edge Cases

- Account는 저장됐으나 credential 저장 실패 → transaction 전체 롤백 (또는 compensating 이벤트). Saga 패턴 판단 필요
- 동시 signup 경합 (이미 `DataIntegrityViolationException` 처리 중이지만 credential 쪽도 경합 가능)
- GDPR email masking (`maskEmail`)과 credential lookup — masked 상태에서는 credential 조회 불가 (이미 deleted 계정)
- 기존에 credential 없이 생성된 account 레코드(향후 마이그레이션 시나리오): 로그인 불가 안내, 비번 재설정 플로우 or 계정 삭제 요청

---

# Failure Scenarios

- auth-service internal endpoint 장애: signup은 503 fail-closed (Option A). 사용자 재시도
- credentials insert 실패 후 account 저장만 성공: 트랜잭션/Saga로 롤백
- 기존 production에 null credential을 가진 account row 존재 시 → 로그인이 `CredentialsInvalidException` 대신 NPE (현재 증상). 근본 fix 전에는 null-guard 단기 패치 권장(아래 Note)

---

# Note — 단기 패치 옵션 (본 태스크 본 스코프에 포함)

근본 fix가 완료되기 전까지, `LoginUseCase`에서 credential lookup이 null을 반환할 경우 NPE 대신 `CredentialsInvalidException`으로 변환하는 방어 한 줄 추가(즉시 패치). 이후 근본 해결(Option A/B/C) 시 해당 방어 코드가 불필요해지면 제거. 이 단기 패치만으로는 가입→로그인이 작동하지 않지만, 적어도 500 NPE 증상은 사라짐.

---

# Test Requirements

- `AuthIntegrationTest.loginSuccess` 시나리오를 signup→login end-to-end로 확장 (fixture가 credential을 실제 저장 후 login 시도)
- unit: `LoginUseCase` credential null 처리 방어(설령 Option A로 근본 해결돼도 fail-safe)
- slice: `SignupUseCase` credential 저장 호출 검증(mock auth-service client)

---

# Definition of Done

- [ ] Option 선택 (ADR 문서화)
- [ ] 구현 + 단위/슬라이스/통합 테스트
- [ ] local smoke: gateway 경유 signup→login 200
- [ ] contract 문서 일치
- [ ] Ready for review
