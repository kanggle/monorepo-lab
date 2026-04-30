# Task ID

TASK-BE-029-3-fix-audit-failclosed-ac-align-seed-guard

# Title

admin-service — login 실패 경로 audit fail-closed + AC 정렬(400) + V0014 prod seed guard

# Status

ready

# Owner

backend

# Task Tags

- code
- adr

# depends_on

- TASK-BE-029-3-admin-login-with-2fa

---

# Goal

029-3 리뷰 Warning 3건을 해소한다. 감사 기록 실패 시 silent swallow를 A10 규정에 맞춰 수정하거나 명시적 override로 기록하고, AC 문구를 구현·계약과 정렬하며, V0014 dev seed가 프로덕션에서 실행되지 않도록 가드한다.

---

# Scope

## In Scope

### Fix 1 — login 실패 경로 audit swallow 정리 (A10)
- 현재 `AdminAuthController.safeRecordLogin`은 `outcome == Outcome.FAILURE`에서 `AuditFailureException`을 삼킨다.
- 두 가지 옵션 중 하나 선택:
  - **옵션 A (strict fail-closed)**: 실패 경로에서도 audit 실패 시 500으로 전파. 원 401 상태 코드가 바뀌지만 A10 엄수.
  - **옵션 B (명시적 override)**: `specs/services/admin-service/architecture.md`의 `## Overrides` 블록에 "로그인 실패 경로 audit best-effort" 조항을 추가하고 `rules/traits/audit-heavy.md#A10`을 명시적으로 참조. 코드는 현 상태 유지.
- 권장: **옵션 B**. 로그인 실패 감사 실패까지 전파하면 사용자 에러 흐름이 500으로 변형되어 관측성/UX가 악화. 단 override는 공식 문서화로 충족.

### Fix 2 — AC 문구와 구현/계약 정렬
- `tasks/done/TASK-BE-029-3-admin-login-with-2fa.md`(이미 done)와 `admin-api.md`/`AdminLoginService`가 "둘 다 미제공 → 400 BAD_REQUEST"로 일치하나, 원 태스크 AC의 "2FA 미제출 → 401" 표현이 남아 리뷰어 혼란 유발.
- 후속 태스크 노트로 AC 정정 버전을 done task 파일에 append(`# Acceptance Criteria (Revision)` 섹션 추가)하거나 `specs/contracts/http/admin-api.md` 인용을 강화.
- 추가로 `AdminLoginControllerTest`에 "require_2fa=TRUE + 둘 다 미제출 → 400" 직접 시나리오 추가 (현재 `InvalidTwoFaCodeException` mock으로 우회됨).

### Fix 3 — V0014 prod seed guard
- `V0014__seed_dev_super_admin_password.sql`이 prod에서 실행되지 않도록 방어:
  - 옵션 A: `callbacks: [FlywayEnvGuard]` 등 애플리케이션 수준 콜백으로 prod profile에서 skip.
  - 옵션 B: 파일명을 `V0014__seed_dev_super_admin_password.sql`로 유지하되 `spring.profiles.active=prod`에서 `flyway.locations`가 이 파일을 포함하지 않도록 `application-prod.yml`을 분리.
  - 옵션 C: SQL 본문에 `SELECT ... WHERE CURRENT_USER() NOT IN ('prod-user')` 식의 런타임 guard 추가 (플랫폼 의존적).
- 권장: **옵션 B** — `application-prod.yml`에 `spring.flyway.locations: classpath:db/migration,classpath:db/migration-prod`로 prod 전용 경로만 포함하고, V0014는 `db/migration-local`로 재배치. 또는 Flyway `ignoreMigrationPatterns: V0014` 설정으로 단순 차단.
- V0014 파일 본문 상단 주석에 "**DEV/LOCAL ONLY — Do not run in production**" 경고 추가.

## Out of Scope

- dev operator 가입 플로우 (UI)
- Flyway 실제 prod 배포 구성 (DevOps 태스크)

---

# Acceptance Criteria

- [ ] 옵션 B 선택 시: `architecture.md` Overrides 블록에 audit-heavy A10 실패 경로 예외 기록. `safeRecordLogin` Javadoc에 override 링크
- [ ] `AdminLoginControllerTest`에 "둘 다 미제출 → 400" 시나리오 추가
- [ ] `V0014__seed_dev_super_admin_password.sql`가 prod에서 실행되지 않음 (옵션 B 또는 동등 방법)
- [ ] V0014 본문에 DEV ONLY 경고 주석
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/contracts/http/admin-api.md`
- `rules/traits/audit-heavy.md` A10
- `rules/traits/regulated.md` R9

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 옵션 A 선택 시 테스트 회귀 (401 기대하는 기존 테스트가 500으로 변경될 가능성) — 옵션 B 권장 사유
- Flyway 경로 분리로 기존 dev 환경이 V0014를 찾지 못하는 회귀 주의: `application-local.yml` 또는 default profile에 포함 경로 유지 필요

---

# Failure Scenarios

- prod seed guard 미작동 → dev 비밀번호로 인증 가능한 SUPER_ADMIN 존재, 심각한 보안 사고. guard 검증 절차 필수.

---

# Test Requirements

- `AdminLoginControllerTest`에 400 시나리오
- prod profile로 부트 시 V0014 미실행 검증 (integration, Docker-gated 가능)

---

# Definition of Done

- [ ] 구현 + 테스트 + 스펙 override 완료
- [ ] Ready for review
