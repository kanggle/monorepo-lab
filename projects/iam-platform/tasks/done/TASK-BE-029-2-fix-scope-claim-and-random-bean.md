# Task ID

TASK-BE-029-2-fix-scope-claim-and-random-bean

# Title

admin-service — Bootstrap token scope claim 추가 + Recovery SecureRandom bean 주입화

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-029-2-admin-totp-enrollment

---

# Goal

029-2 리뷰에서 발견된 Critical 2건을 해소한다. Bootstrap token이 스펙이 요구하는 `scope` claim을 실제로 발급/검증하도록 하고, `TotpEnrollmentService`의 static `SecureRandom`을 주입 가능한 의존성으로 전환한다.

---

# Scope

## In Scope

### Fix 1 — Bootstrap token `scope` claim
- `security.md §Bootstrap Token` 및 `admin-api.md §Bootstrap Token` 양쪽이 요구하는 `scope: ["2fa_enroll", "2fa_verify"]` claim을 `BootstrapTokenService.issue(...)` claims 맵에 추가.
- `issue` 시점에 호출자가 scope 집합을 전달(`Set<String> scopes`). 발급 경로:
  - login enrollment-required 응답: `{"2fa_enroll", "2fa_verify"}` 두 scope 포함
  - verify 이후 재enroll 경로가 필요하다면 동일 scope
- `BootstrapAuthenticationFilter` 또는 `BootstrapTokenService.verify`가 요청 경로별로 필요한 scope가 포함되어 있는지 검증:
  - `POST /api/admin/auth/2fa/enroll` → `"2fa_enroll"` 필수
  - `POST /api/admin/auth/2fa/verify` → `"2fa_verify"` 필수
- 누락/불일치 시 401 `INVALID_BOOTSTRAP_TOKEN`
- 테스트: BootstrapTokenServiceTest에 scope 검증 케이스 2종 추가 (포함/누락)

### Fix 2 — Recovery SecureRandom 주입화
- `TotpEnrollmentService`의 `private static final SecureRandom RECOVERY_RANDOM`을 생성자 주입 또는 `@Bean SecureRandom` 주입으로 전환.
- 옵션 A (권장): 공용 `@Bean SecureRandom secureRandom()` 을 `infrastructure/security/CryptoConfig` 등에 등록하고 `TotpGenerator`와 동일한 패턴으로 주입.
- 옵션 B: 서비스 내부에서 생성자에 `Supplier<SecureRandom>` 또는 `SecureRandom` 필드로 받기.
- 단위 테스트에서 결정적 seed 주입으로 recovery code 생성 검증 가능하도록 개선.

### (선택 Warning 대응)
- `AdminOperatorTotpJpaEntity.version` 타입을 `int`로 정렬 (DDL은 BIGINT 유지, 오버플로 현실 위험 없음 → 컨벤션 정합).
- enrollment 경로의 operator not-found 예외를 `AuditFailureException`이 아닌 `IllegalStateException` 또는 별도 도메인 예외로 분리.

## Out of Scope

- Flyway 스키마 변경 (version 컬럼 타입 수정 여부는 별도 판단)
- 새 엔드포인트/계약 변경

---

# Acceptance Criteria

- [ ] Bootstrap token JWS claims에 `scope` 배열이 존재
- [ ] enroll/verify 각 경로가 필요한 scope 미포함 토큰을 401로 거부
- [ ] `TotpEnrollmentService`가 static SecureRandom 미사용 (생성자/Bean 주입)
- [ ] 단위 테스트가 결정적 seed 주입으로 recovery code 검증 가능
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/security.md`
- `specs/contracts/http/admin-api.md`
- `rules/traits/regulated.md` R9

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 잘못된 scope 값은 단순 누락으로 간주
- 테스트 시 고정 seed SecureRandom은 실제 보안 경로에 영향 없음(구성 가능)

---

# Failure Scenarios

- scope 검증이 filter 내부에서 수행될 때 IOException 발생 → 500(컨트롤러 경유). 현 단계 수용.

---

# Test Requirements

- `BootstrapTokenServiceTest`에 scope 케이스 2종
- `TotpEnrollmentServiceTest`에 결정적 recovery code 케이스

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] Ready for review
