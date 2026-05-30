# Task ID

TASK-BE-319a

# Title

서비스 간(workload) 인증 무중단 전환 — 단계 4a (수신측 정적 토큰 제거, security-service): security-service `/internal/security/**` 의 정적 `X-Internal-Token`(`InternalAuthFilter` 의 X-token 경로) 지원을 제거해 **GAP client_credentials JWT 단일 인증**으로 확정한다. + 내부 query 계약 spec 인증 절 갱신 + `INTERNAL_SERVICE_TOKEN` env 정리. (ADR-005 옵션 A 단계 4 — security 부분; BE-319 권장 분할의 security half)

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- infra

---

# Dependency Markers

- **depends on**: TASK-BE-317(security 수신측 이중 허용) + TASK-BE-318b(admin→security 호출측 Bearer 전환, **main 머지 완료** #939). security `/internal/security/**` 의 **유일 외부 호출자 = admin**(security-query-api.md: "admin-service만 호출") 이며 BE-318b 로 Bearer 화 완료 → 본 task 의 선행 요건 충족.
- **parent**: TASK-BE-319(수신측 정적 토큰 제거 + 계약 갱신). 본 task 는 BE-319 § "권장 분할" 의 **BE-319a (security 수신측)**. account 수신측(BE-319b)은 membership→account 전환 미완으로 별도 블록.
- **prerequisite of**: 없음(security 축 종결). account 축은 BE-319b 가 별도 추적.

---

# Goal

security-service 수신측(`/internal/security/login-history`, `/internal/security/suspicious-events`)이 더 이상 정적 `X-Internal-Token` 을 허용하지 않고 **GAP `client_credentials` JWT(`Authorization: Bearer`)만** 으로 인증하도록 확정한다. `InternalAuthFilter` 의 X-token 분기 + `security-service.internal-token` / `INTERNAL_SERVICE_TOKEN` 정적 토큰 잔재를 제거하고, 내부 query 계약 spec 의 인증 절을 Bearer 로 갱신한다.

# Scope

## In scope

- **security-service `InternalAuthFilter`**: X-Internal-Token 검증 분기(`internalTokenValid`) + `expectedToken`(`security-service.internal-token`) 제거. GAP JWT(`bearerJwtValid`, `NimbusJwtDecoder`)만 유지. `test`/`standalone` profile bypass 는 보존(테스트 슬라이스/standalone dev 유지 — 정적 비밀이 아닌 profile-gate). 자격증명 없으면 403 PERMISSION_DENIED(fail-closed, 계약 error 형태 보존).
- **config**: security `application.yml` 의 `security-service.internal-token` 제거. `application-test.yml` 의 `internal-token: test-internal-token` 제거. `internal-jwt`(JWT decoder) 블록은 유지.
- **docker-compose.e2e.yml**: security-service 의 `INTERNAL_SERVICE_TOKEN` 주입 제거(BE-318 이후 outbound 는 Bearer 라 이미 미사용; 수신측 X-token 경로 제거와 함께 정리). JWKS/issuer(OIDC) env 로 동작.
- **계약 spec**: `specs/contracts/http/security-query-api.md` 의 "Auth required: internal (mTLS 또는 service token)" → `Authorization: Bearer <GAP client_credentials JWT>` 로 갱신(2개 엔드포인트). `specs/services/security-service/architecture.md` query/ 절에 인증=Bearer JWT 명시.
- **테스트**: `InternalAuthFilterTest` 를 JWT 전용으로 재작성(유효 JWT→통과 / 미제시·무효 JWT→403 / test profile bypass / non-internal 통과). 컨트롤러 슬라이스(`LoginHistoryQueryControllerTest`/`SuspiciousEventQueryControllerTest`) + `SecurityServiceIntegrationTest` 의 X-Internal-Token 헤더 제거(test profile bypass 경유); 슬라이스의 X-token auth-403 케이스는 필터 unit test 로 이전.

## Out of scope

- account-service 수신측 `/internal/**` X-token 제거 → **BE-319b**(membership→account 전환 선행 필요). 본 task 는 security 수신측만.
- auth-service 수신측 `/internal/**` permitAll → JWT 전환(별도 task).
- mTLS / 완전 keyless → 후속 ADR.

# Acceptance Criteria

- **AC-1**: security-service `/internal/security/**` 가 **유효 GAP JWT 로만** 통과하고, `X-Internal-Token` 만 보낸 요청은 403 PERMISSION_DENIED(fail-closed). `InternalAuthFilter` 의 X-token 분기 제거됨.
- **AC-2**: 자격증명 미제시 / 무효 JWT → 403. 유효 JWT(Bearer) → 통과. (test/standalone profile 은 기존대로 bypass.)
- **AC-3**: `security-query-api.md` + `architecture.md` 의 인증 절이 Bearer JWT 로 갱신됨(service token / X-Internal-Token 기술 제거).
- **AC-4**: security `application.yml` / `application-test.yml` 에서 `internal-token` 제거, docker-compose.e2e 에서 security-service `INTERNAL_SERVICE_TOKEN` 제거. JWKS/issuer 설정으로 동작(빌드 + e2e GREEN).
- **AC-5**: 회귀 0 — security-service unit + integrationTest + gap e2e smoke GREEN. admin→security 호출(이미 Bearer)이 정상 동작.
- **AC-6**: 유일 외부 호출자 admin 이 Bearer 임을 착수 전 확인(STOP 가드) — `git grep "/internal/security"` 송신부 = admin SecurityServiceClient(Bearer) 단일.

# Related Specs

- `specs/contracts/http/security-query-api.md` (인증 절 갱신 대상)
- `specs/services/security-service/architecture.md` (query/ 내부 경로 인증 경계)
- ADR-005 § 무중단 마이그레이션 단계 4

# Related Contracts

- security `/internal/security/login-history`, `/internal/security/suspicious-events` — 인증을 Bearer JWT 단일로 확정(X-Internal-Token 제거).
- `GET /oauth2/jwks` (검증 키) — 변경 없음.

# Edge Cases

- **유일 호출자 보장**: security `/internal/security` 의 외부 호출자는 admin SecurityServiceClient 뿐(security-query-api.md). BE-318b 로 Bearer 화 완료 → X-token 제거해도 깨질 호출자 없음. 착수 전 grep 으로 재확인(AC-6).
- **test/standalone bypass**: 정적 비밀이 아닌 profile-gate 라 유지. JWT 전용 후에도 test 슬라이스가 동작하도록 필터 auth 단언은 `InternalAuthFilterTest`(mock `JwtDecoder`)로 집중하고, 컨트롤러 슬라이스/full IT 는 bypass 경유(헤더 불필요).
- **docker-compose env 잔재**: security 의 `INTERNAL_SERVICE_TOKEN` 은 BE-318(outbound Bearer) 이후 수신측 X-token 경로에만 쓰임 — 본 task 로 그 경로 제거 시 완전 미사용 → 삭제.

# Failure Scenarios

- X-token 제거 후 미전환 호출자 깨짐 → AC-6 STOP 가드(유일 호출자 admin=Bearer 확인). security 축은 호출자 1개라 위험 최소.
- test 슬라이스가 X-token 가정으로 깨짐 → JWT(mock decoder) 또는 bypass 경유로 재작성.
- e2e 에서 INTERNAL_SERVICE_TOKEN 제거 후 admin→security 실패 → admin Bearer + JWKS env 동작 사전 검증(CI e2e GREEN).

---

# Implementation Design Notes

- 단계 4 는 **계약(SoT) → 코드** 순. spec 인증 절을 같은 PR 로 갱신.
- `InternalAuthFilter`: `internalTokenValid` + `expectedToken` 필드 + `${security-service.internal-token:}` @Value 제거. `doFilter` = internal path → (bypass profile? 통과) → (bearerJwtValid? 통과) → 403. `InternalJwtDecoderConfig`(JWT decoder) 불변.
- 착수 전 `git grep "X-Internal-Token"` / `INTERNAL_SERVICE_TOKEN` / `security-service.internal-token` 송신·수신·설정 전수 확인 — security 축 한정.

---

# Notes

- ADR-005 단계 4 의 security half. 완료 시 security `/internal/security/**` 가 GAP JWT 단일 인증으로 확정(공유 X-Internal-Token 정적 비밀 제거). account half(BE-319b)는 membership→account 전환 후.
