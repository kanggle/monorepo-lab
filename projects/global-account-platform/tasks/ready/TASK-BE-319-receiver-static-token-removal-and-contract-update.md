# Task ID

TASK-BE-319

# Title

서비스 간(workload) 인증 무중단 전환 — 단계 4 (정적 토큰 제거 + 계약 갱신): account-service / security-service `/internal/**` 의 정적 `X-Internal-Token`(`InternalApiFilter` / `InternalAuthFilter` 의 X-token 경로) 지원을 제거해 **GAP JWT 단일 인증**으로 확정하고, 내부 계약 spec 의 인증 절을 Bearer 로 갱신한다. + docker-compose `INTERNAL_API_TOKEN` 정리. (ADR-005 옵션 A 단계 4)

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

- **depends on**: TASK-BE-317(이중 허용) + **모든 호출자의 Bearer 전환 완료**.
  - **security 수신측** X-token 제거 선행 요건: TASK-BE-318b(admin→security). (security `/internal/security` 의 유일 호출자=admin.)
  - **account 수신측** X-token 제거 선행 요건: TASK-BE-318(security→account, 완료) + TASK-BE-318b(admin→account) + TASK-BE-318c(auth→account) + **membership→account 전환**.
- ⚠️ **membership→account 미전환 블로커**: membership-service `AccountStatusClient` 는 아직 `X-Internal-Token` 사용(TASK-BE-253 follow-up, 미완). account 수신측에서 X-token 을 제거하면 membership→account 호출이 깨진다. **account 부분은 membership→account 전환(별도 task — BE-253 follow-up 재활성 또는 신규)이 선행되어야 한다.**
- **권장 분할**: 선행 요건이 다르므로 본 task 를 **BE-319a(security 수신측 제거, BE-318b 후)** 와 **BE-319b(account 수신측 제거, 모든 account 호출자+membership 후)** 로 쪼개 진행해도 된다.
- **⚠️ 분할 적용됨 (2026-05-30)**: security half 는 **TASK-BE-319a** 로 분리·구현됨(`InternalAuthFilter` JWT 전용 + 계약/spec/env 정리). **본 task(BE-319)의 잔여 scope = account 수신측(=BE-319b)** 이며 여전히 **membership→account 전환 미완으로 블록**. account 부분만 본 task 에서 진행한다(아래 In scope 의 security-service 항목은 BE-319a 에서 완료).

---

# Goal

수신측이 더 이상 정적 `X-Internal-Token` 을 허용하지 않고 **GAP `client_credentials` JWT 만** 으로 `/internal/**` 를 인증하도록 확정한다. 내부 계약 spec 과 배포 설정(`INTERNAL_API_TOKEN`)에서 정적 토큰 잔재를 제거한다.

# Scope

## In scope

- **account-service**: `InternalApiFilter` 의 X-Internal-Token 검증 경로 제거(JWT resource-server 만 유지). `/internal/**` = `.authenticated()` 유지(JWT 전용). `internal.api.token` / bypass 프로퍼티 정리. (선행: 모든 account 호출자 Bearer 전환 + membership.)
- **security-service**: `InternalAuthFilter` 의 X-Internal-Token 경로 제거(JWT(`NimbusJwtDecoder`)만 유지). `security-service.internal-token` / `INTERNAL_SERVICE_TOKEN` 정리. (선행: BE-318b.)
- **내부 계약 spec** 의 인증 절 갱신: `specs/contracts/http/internal/*.md` 중 X-Internal-Token 으로 기술된 경로(admin-to-account / auth-to-account(+social) / security-to-account / account-internal-provisioning 등)를 `Authorization: Bearer <GAP client_credentials JWT>` 로 갱신. (auth-internal / *-to-auth 는 auth 수신측 미전환이면 보류.)
- **docker-compose / env**: `INTERNAL_API_TOKEN` / `INTERNAL_SERVICE_TOKEN` 주입 제거(`docker-compose*.yml`, e2e 포함), JWKS/issuer env 로 대체 확인.
- 테스트: 수신측이 X-Internal-Token 으로는 거부(JWT 없으면 401/403), 유효 JWT 로만 통과. 기존 X-token 가정 테스트 정리.

## Out of scope

- auth-service **수신측** `/internal/**` permitAll → JWT 전환(별도 task; 그에 딸린 admin→auth / account→auth 호출자 전환 포함).
- membership→account 호출자 전환(선행 별도 task).
- mTLS / 완전 keyless → 후속 ADR.

# Acceptance Criteria

- **AC-1**: account-service `/internal/**` 가 **유효 GAP JWT 로만** 통과하고, `X-Internal-Token` 만 보낸 요청은 401(fail-closed). `InternalApiFilter` 의 X-token 경로 제거됨.
- **AC-2**: security-service `/internal/security/**` 가 동일(유효 JWT 만 통과, X-token 거부 403/401).
- **AC-3**: 내부 계약 spec 의 해당 경로 인증 절이 Bearer JWT 로 갱신됨(X-Internal-Token 기술 제거).
- **AC-4**: docker-compose / env 에서 `INTERNAL_API_TOKEN`/`INTERNAL_SERVICE_TOKEN` 잔재 제거, JWKS/issuer 설정으로 동작 확인(e2e GREEN).
- **AC-5**: 모든 in-scope 호출자(security/admin/auth, + membership)가 이미 Bearer 인 상태에서 회귀 0 — 전 서비스 IT + gap e2e smoke GREEN.
- **AC-6**: 선행 요건 미충족 시(예: membership 미전환) account 부분은 진행하지 않는다(STOP) — security 부분만 분리 진행 가능.

# Related Specs

- `specs/contracts/http/internal/*.md` (10개 — 인증 절 갱신 대상)
- `specs/services/{account,security}-service/architecture.md` (`/internal/**` 보안 경계 — JWT 전용으로 갱신)
- ADR-005 § 무중단 마이그레이션 단계 4 + Implementation Roadmap

# Related Contracts

- account `/internal/accounts/**`, security `/internal/security/**` — 인증을 Bearer JWT 단일로 확정(X-Internal-Token 제거).
- `GET /oauth2/jwks` (검증 키), `POST /oauth2/token` (호출자 토큰 발급) — 변경 없음.

# Edge Cases

- **선행 호출자 누락**: 한 호출자라도 X-token 으로 남아있는데 수신측에서 제거하면 그 경로 401 → AC-6 STOP 가드 + 착수 전 호출자 전수 확인(grep `X-Internal-Token` 송신부).
- **bypass / test profile**: 기존 `bypassWhenUnconfigured` / `test`·`standalone` profile 우회 경로의 운명 결정 — JWT 전용 후에도 test 슬라이스가 동작하도록(테스트는 mock decoder / 고정 JWT) 재정비.
- **계약 spec 과 코드 동시 변경**: spec(상위 SoT)을 코드와 같은 PR 로 갱신(계약이 먼저/같이).
- **docker-compose 제거 누락**: env 가 남아도 무해하나(미사용), 깔끔히 제거하고 JWKS env 존재 확인.

# Failure Scenarios

- X-token 제거 후 미전환 호출자(특히 membership) 깨짐 → AC-6 STOP + 전수 확인.
- test 슬라이스가 X-token 가정으로 깨짐 → JWT 전용 테스트로 재작성.
- e2e(gap docker-compose)에서 INTERNAL_API_TOKEN 제거 후 서비스 간 호출 실패 → 호출자 Bearer + JWKS env 동작 사전 검증.

---

# Implementation Design Notes

- 단계 4 는 **계약(SoT) → 코드** 순. spec 의 인증 절을 먼저/같은 PR 로 갱신.
- account: `InternalApiFilter` 를 제거하거나 X-token 분기만 삭제하고 `.authenticated()`+`oauth2ResourceServer` 유지. security: `InternalAuthFilter` 에서 X-token 분기 삭제, JWT(`bearerJwtValid`)만 남김.
- 착수 전 `git grep "X-Internal-Token"` / `INTERNAL_API_TOKEN` / `INTERNAL_SERVICE_TOKEN` 송신·수신·설정 전수 확인. membership 송신부가 남아있으면 account 부분 STOP.
- 선행 의존이 복잡하므로 BE-319a(security) / BE-319b(account) 분할 권장.

---

# Notes

- ADR-005 단계 4 (마지막). 완료 시 ④ workload identity 의 정적 비밀(공유 X-Internal-Token) 제거 — client_secret 만 정적 잔존(완전 keyless 는 mTLS 후속 ADR).
- 선행: security 부분=BE-318b 후 / account 부분=BE-318(완료)+BE-318b+BE-318c+membership 전환 후.
