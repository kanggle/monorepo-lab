# Task ID

TASK-BE-040-fix-port-boundary-and-signed-operator-id

# Title

admin-service — refresh/logout 서비스의 application→infra 경계 위반 해소 + 서명 미검증 JWT payload 디코딩 제거

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-040-admin-refresh-logout

---

# Goal

TASK-BE-040 리뷰에서 발견된 Critical 2건을 해소한다.
1. `AdminLogoutService`/`AdminRefreshTokenService`가 application 레이어에서 infrastructure JPA repository를 직접 import하는 architecture.md 경계 위반을 port/adapter 패턴으로 교정한다.
2. `AdminAuthController`가 refresh JWT payload를 **서명 검증 없이** Base64 디코딩하여 operator id를 추출하는 보안 취약점을 제거하고, 검증된 결과를 서비스 반환값에서만 사용하도록 수정한다.

---

# Scope

## In Scope

### Fix 1 — application→infra 경계 교정 (Critical)
- 포트 인터페이스 신설 (`application/port/`):
  - `OperatorQueryPort` — `findByOperatorId(UUID)`, `findById(long)` (필요 최소 메서드)
  - `AdminRefreshTokenPort` — `findByJti`, `save`, `revokeAllForOperator(operatorId, reason)` 등 서비스가 사용하는 모든 메서드
- 어댑터 신설 (`infrastructure/persistence/`):
  - `OperatorQueryJpaAdapter` (기존 `AdminOperatorJpaRepository` 위임)
  - `AdminRefreshTokenJpaAdapter` (기존 repository 위임)
- `AdminLogoutService`, `AdminRefreshTokenService`에서 JPA repository 직접 의존 제거. 포트만 주입받도록 수정
- `AdminRefreshTokenIssuer` 도 같은 패턴 따르는지 점검 (이미 application에서 repository 사용 중이면 동일 포트 사용)
- architecture.md Allowed Dependencies 다이어그램 준수 증빙
- 030-fix 태스크에서 도입한 `BulkLockIdempotencyPort`/`OperatorLookupPort` 네이밍과 충돌 없이 통일 (필요 시 `OperatorLookupPort` 재사용)

### Fix 2 — 서명 미검증 payload 디코딩 제거 (Critical)
- `AdminAuthController` 의 `extractOperatorIdSafely(refreshToken)` 메서드 및 관련 Base64 디코딩 경로 **전체 삭제**
- `AdminRefreshTokenService.refresh(...)` 반환 타입에 `operatorId: UUID` 포함 (성공 경로)
- 실패 경로(`RefreshTokenReuseDetectedException`, `InvalidRefreshTokenException`)에서도 operator id 필요 시 예외에 `operatorId` 필드 추가하여 전달 — 단, **오직 서비스가 검증된 registry row에서 읽은 값**만 전달
- 컨트롤러는 서비스 반환/예외 필드로만 `operatorIdForAudit` 결정
- `AdminRefreshControllerTest`의 `FAKE_REFRESH_TOKEN`도 실제 RS256 서명된 토큰(`OperatorJwtTestFixture.signRefresh(...)` 등) 사용으로 교체 — `alg:none` 픽스처 제거

### Fix 3 (Warning 대응) — `@Modifying(clearAutomatically = true)`
- `AdminOperatorRefreshTokenJpaRepository`의 `@Modifying` 어노테이션에 `clearAutomatically = true` 추가 (벌크 JPQL 후 1차 캐시 무효화)
- 동일 트랜잭션 내 `revokeAllForOperator` 이후 조회가 stale 엔티티 반환하지 않음을 단위 테스트로 회귀 확인

### Fix 4 (Warning 대응) — `computeTtlSeconds` fallback 명확화
- `AdminLogoutService.computeTtlSeconds`에서 `accessExp == null` 경로를 Javadoc으로 문서화하거나 최소값 `Duration.ofSeconds(60)`로 제한 (이유: 필터 단에서 exp를 request attribute로 전달하나 테스트/예외 경로에서 null일 수 있음)

### (선택 Suggestion) — 주석 정리
- `AdminAuthController`의 `/refresh` operatorIdForAudit 관련 장황한 주석 제거 (Fix 2 후 의미 없어짐)

## Out of Scope

- 다른 application service(예: `AdminLoginService`, 기존 `AccountAdminUseCase`)의 경계 교정은 별도 (리뷰 Scope이 040만)
- `OperatorQueryPort`에 audit 경로의 operator 해석 통합 (현재 `AdminActionAuditor.resolveOperatorPk`는 다른 Port로 분리 — 통합은 후속)

---

# Acceptance Criteria

- [ ] `AdminLogoutService`, `AdminRefreshTokenService`에 `infrastructure.persistence.*Jpa*` import 0건 (grep)
- [ ] `AdminAuthController`에 JWT payload 직접 디코딩 코드 0건 (grep `Base64.*decode`, `extractOperatorIdSafely` 제거)
- [ ] `AdminRefreshTokenService.refresh` 반환 타입에 `operatorId` 포함
- [ ] 재사용 탐지 경로 예외에 `operatorId` 필드 포함(필요 시) 또는 서비스가 직접 audit 호출
- [ ] `AdminRefreshControllerTest` 픽스처가 RS256 서명 토큰 사용
- [ ] `@Modifying(clearAutomatically = true)` 적용 + 회귀 테스트
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md` (Internal Structure Rule, Allowed Dependencies)
- `platform/security-rules.md` (서명 검증 원칙 — 있는 경우)
- `rules/traits/audit-heavy.md` A2

# Related Contracts

- `specs/contracts/http/admin-api.md` (계약 변경 없음)

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 재사용 탐지 경로에서 JWT 서명 검증은 실패할 수도 있음 (만료/위조): 이 경우 operatorId를 알 수 없음 → audit row의 operator_id는 null로 남기거나 감사 생략(fail-open). 현재 V0011 operator_id NOT NULL 제약과의 충돌 재점검 필요 — 설계 결정 포함
- `@Modifying(clearAutomatically = true)` 적용 후 JPA 2차 캐시 사용 시 영향: admin-service에 2차 캐시 없으므로 영향 없음

---

# Failure Scenarios

- 포트 도입 시 Spring wiring 실패 → 컴파일 시 빈 이름 충돌 확인
- 예외 필드 추가 시 기존 `AdminExceptionHandler` 매핑 회귀 → 기존 에러 응답 스키마 불변 확인

---

# Test Requirements

- 단위 테스트: port stub으로 서비스 테스트 가능함을 증빙
- Slice 테스트 픽스처 RS256 서명
- `@Modifying clearAutomatically` 회귀 — 동일 tx에서 revoke 후 read 일관성

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] Ready for review
