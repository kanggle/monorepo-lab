# Task ID

TASK-BE-320

# Title

account-service 테스트의 죽은 `X-Internal-Token` scaffolding 정리 — TASK-BE-319b 후속 기계적 위생 작업. `InternalApiFilter` 가 JWT 전용으로 확정(X-token 경로 제거)된 뒤에도 account IT/슬라이스 테스트 11종에 남아있는 inert `registry.add("internal.api.token", …)` + `.header("X-Internal-Token", …)` 잔재를 제거해, "X-token 이 여전히 유효"하다는 오독 여지를 없앤다. 기능/회귀 무영향(전부 `@ActiveProfiles("test")` bypass 로 통과 중).

# Status

done

> **완료 (2026-05-30)**: impl PR #949 (squash `9720fdbe`). account-service 테스트 11종에서 inert `internal.api.token`/`X-Internal-Token` scaffolding 제거 — 11 files −59/+1, 프로덕션 코드 0. 보존 2종(`InternalApiFilterTest`/`InternalDualAuthSliceTest`)의 negative 회귀 게이트 무변경. CI GREEN(특히 Integration global-account-platform 2m4s + gap docker-compose smoke — account IT 실제 실행 통과로 AC-3/AC-4 충족). ADR-005 단계 4 문서화 후속 정리 종결.

# Owner

backend

# Task Tags

- code
- test
- chore

---

# Dependency Markers

- **depends on**: TASK-BE-319b(account 수신측 `InternalApiFilter` X-token 경로 제거 + `internal.api.token` 프로퍼티 제거 — main DONE). 본 task 는 그 후속 순수 정리.
- 선행/후속 블로커 없음. 단독 진행 가능.

---

# Goal

TASK-BE-319b 로 account-service `/internal/**` 인증이 GAP `client_credentials` JWT 단일로 확정되면서 `internal.api.token` 프로퍼티와 `X-Internal-Token` 헤더 경로가 코드에서 완전히 제거되었다. 그런데 account-service 테스트 11종에는 그 시절의 scaffolding(`registry.add("internal.api.token", …)`, `.header("X-Internal-Token", INTERNAL_TOKEN)`, `INTERNAL_TOKEN` 상수)이 **죽은 채** 남아있다. 이들은 `@ActiveProfiles("test")` bypass 로 인증이 통과되므로 기능에는 무영향이지만, 미래 독자가 X-token 이 여전히 인증에 쓰인다고 오독할 수 있다. 이 죽은 scaffolding 을 제거한다.

# Scope

## In scope

대상 11종 (account-service `src/test`):

**registry-only (6종)** — `registry.add("internal.api.token", …)` 한 줄만 제거:
- `integration/SignupRollbackIntegrationTest.java`
- `integration/SignupAuthServiceDelayIntegrationTest.java`
- `integration/AccountEventPublisherIntegrationTest.java`
- `infrastructure/scheduler/AccountAnonymizationSchedulerIntegrationTest.java`
- `infrastructure/scheduler/AccountDormantSchedulerIntegrationTest.java`
- `infrastructure/kafka/LoginSucceededConsumerIntegrationTest.java`

**registry + header (4종)** — `registry.add("internal.api.token", …)` 줄 + 모든 `.header("X-Internal-Token", …)` 호출 + 미사용이 된 `INTERNAL_TOKEN`(및 유사) 상수 제거:
- `integration/AccountSignupIntegrationTest.java`
- `integration/AccountRoleProvisioningIntegrationTest.java`
- `integration/BulkProvisioningIntegrationTest.java`
- `integration/TenantProvisioningIntegrationTest.java`

**header-only (1종, 슬라이스)** — 모든 `.header("X-Internal-Token", …)` + 미사용 상수 제거:
- `presentation/InternalControllerTest.java`

## Out of scope

- **보존(절대 수정 금지)**: `infrastructure/config/InternalApiFilterTest.java` + `infrastructure/config/InternalDualAuthSliceTest.java` — 이들의 `X-Internal-Token` 참조는 "X-token 이 무시/거부됨"을 **단언하는 의도된 negative test**(BE-319b 회복증명). 제거하면 회귀 게이트가 사라진다.
- 프로덕션 코드 변경(0). 본 task 는 테스트 전용.
- 다른 서비스(security/admin/auth/membership) 테스트 — 별건/무관.
- e2e `OIDC_ISSUER_URL` 정합(별도 후보 task — latent landmine).

# Acceptance Criteria

- **AC-1**: 위 11종에서 `internal.api.token` / `X-Internal-Token` 잔재가 0 (`registry.add` 줄·헤더 호출·미사용 상수 제거).
- **AC-2**: 보존 2종(`InternalApiFilterTest`, `InternalDualAuthSliceTest`)은 **무변경** — X-token negative 단언 유지.
- **AC-3**: account-service 가 컴파일되고(미사용 상수/임포트 잔재 없음), 전 IT + 단위 테스트 회귀 0 — CI Linux GREEN.
- **AC-4**: gap e2e smoke GREEN(기능 무영향 확인).
- **AC-5**: 프로덕션 코드 diff 0 byte.

# Related Specs

- `specs/contracts/http/internal/*.md` — 이미 BE-319b 에서 Bearer JWT 로 갱신됨(본 task 는 spec 변경 없음).
- `specs/services/account-service/architecture.md` — `/internal/**` JWT 전용(BE-319b 반영 완료).

# Related Contracts

- account `/internal/accounts/**` — 인증 = Bearer JWT 단일(변경 없음, 테스트 정리만).

# Edge Cases

- **registry.add 다중**: 각 파일의 `@DynamicPropertySource` 에는 DB/flyway/base-url 등 다른 `registry.add` 가 공존 — `internal.api.token` 줄만 제거하고 나머지 보존.
- **상수 공유 참조**: `INTERNAL_TOKEN` 상수가 제거 후 어디서도 참조되지 않는지 확인 후 선언 제거(부분 참조가 남으면 컴파일 에러). 슬라이스/IT 별로 상수명이 다를 수 있음(`INTERNAL_TOKEN` 등) — grep 으로 잔여 참조 0 확인.
- **builder 체인**: `.header(...)` 한 줄 제거 시 인접 `.contentType`/`.content`/`.andExpect` 체인 문법 보존.
- **보존 2종 오삭제 방지**: config 패키지 2 파일은 손대지 않는다(AC-2).

# Failure Scenarios

- 미사용 상수를 남겨 컴파일 경고/오해 → grep 으로 잔여 참조 확인 후 선언까지 제거.
- 보존 2종을 잘못 정리 → BE-319b negative 회귀 게이트 소실 → AC-2 STOP 가드.
- 다른 `registry.add` 를 함께 지워 IT 깨짐 → `internal.api.token` 키만 타겟.

---

# Implementation Design Notes

- 순수 기계적. 프로덕션 코드 0. `git grep "X-Internal-Token"` / `internal.api.token` 가 account-service `src/test` 에서 보존 2종(config 패키지)만 남을 때까지 제거.
- 착수 전후 grep 으로 잔여 검증. 컴파일 확인(`./gradlew :projects:global-account-platform:apps:account-service:compileTestJava`) 후 CI 에 IT/e2e 권위 게이트 위임(로컬 Testcontainers 는 Rancher npipe 회귀로 간헐 스킵).

---

# Notes

- ADR-005 단계 4(BE-319b) 의 문서화된 후속 "account IT inert X-token scaffolding 기계적 정리". 기능 무영향, 위생 목적.
