# TASK-BE-174: admin-service 보안 컴포넌트 단위 테스트 추가

## Goal
admin-service 내 테스트 파일이 없는 보안 컴포넌트 3개에 대해 `Unit (infrastructure)` 수준의 단위 테스트를 작성한다.

## Target Service
`apps/admin-service`

## Scope
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/IssuerEnforcingJwtVerifier.java`
  - JJWT 기반 실제 RSA 서명 토큰으로 검증 (iss 일치/불일치, 변조 서명)
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/TotpGenerator.java`
  - RFC 6238 TOTP 생성/검증, newSecret, base32, otpauthUri
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/TotpSecretCipher.java`
  - AES-GCM encrypt/decrypt 라운드트립, 잘못된 AAD/kid/blob 검증

## Acceptance Criteria
- [ ] `IssuerEnforcingJwtVerifierUnitTest`: 정상 iss, 잘못된 iss, 변조 서명 — 3개 테스트
- [ ] `TotpGeneratorUnitTest`: newSecret, 결정적 code, 6자리 형식, verify 성공/실패, otpauthUri 구조 — 6개 테스트
- [ ] `TotpSecretCipherUnitTest`: roundtrip, 잘못된 operatorId/keyId/short blob, 빈 키 init 실패 — 5개 테스트
- [ ] 전체 14개 테스트 통과
- [ ] 파일 이름이 `*UnitTest.java` 규칙 준수 (`platform/testing-strategy.md` Unit (infrastructure))

## Related Specs
- `platform/testing-strategy.md` (Naming Conventions, Unit Tests)
- `specs/services/admin-service/architecture.md`

## Related Contracts
- None

## Edge Cases
- `IssuerEnforcingJwtVerifier`는 `Rs256JwtVerifier`(JJWT)를 delegate로 사용 — 실제 RSA 키로 서명해야 테스트 가능
- `Rs256JwtVerifier`는 iss를 검사하지 않으므로 wrong iss 토큰은 delegate는 통과하고 `IssuerEnforcingJwtVerifier`에서 거부됨
- `TotpGenerator`는 `Clock` 주입 가능 → `Clock.fixed()`로 counter 고정
- `TotpSecretCipher`는 `@PostConstruct`로 초기화 → unit test에서 `cipher.init()` 직접 호출
- AES-GCM에서 다른 operatorId(다른 AAD)로 복호화 시도 → `IllegalStateException("AES-GCM decrypt failed")`

## Failure Scenarios
- `IssuerEnforcingJwtVerifier`: iss 불일치 또는 서명 변조 → `JwtVerificationException`
- `TotpSecretCipher.decrypt`: unknown kid → `IllegalStateException("Unknown TOTP encryption kid: ...")`
- `TotpSecretCipher.decrypt`: 너무 짧은 blob → `IllegalStateException("too short")`
- `TotpSecretCipher.init`: 빈 encryption-keys → `IllegalStateException("must not be empty")`
