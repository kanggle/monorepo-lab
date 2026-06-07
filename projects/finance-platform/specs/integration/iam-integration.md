# Integration — iam-platform (IAM) OIDC

> 본 문서는 `finance-platform` 의 모든 서비스가 IAM 를 표준 OIDC IdP 로 사용하는 방식을 1쪽으로 요약한다.
> [IAM ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) 의 finance-platform 적용본이며,
> [scm-platform](../../../scm-platform/specs/integration/iam-integration.md) /
> [wms-platform](../../../wms-platform/specs/integration/iam-integration.md) 의 같은 통합 패턴을 따른다.

---

## Tenant Identity

- `tenant_id` = `finance`
- `tenant_type` = `B2B_ENTERPRISE` (내부-서비스 모델 — scm 과 동일 type)
- v1 = backend only — self-service signup endpoint 사용 안 함. 모든 운영자 / 시스템 계정은
  관리자 API ([account internal provisioning](../../../iam-platform/specs/contracts/http/internal/account-internal-provisioning.md))
  로 생성한다.

---

## OIDC Endpoints (consumed by finance-platform)

| 항목 | 값 (dev 기본) | 환경 변수 |
|---|---|---|
| Issuer URL | `http://iam.local` | `OIDC_ISSUER_URL` |
| JWKS URI | `${OIDC_ISSUER_URL}/oauth2/jwks` | `JWT_JWKS_URI` |
| OIDC Discovery | `${OIDC_ISSUER_URL}/.well-known/openid-configuration` | n/a |
| Token endpoint | `${OIDC_ISSUER_URL}/oauth2/token` | n/a |
| Authorization endpoint | `${OIDC_ISSUER_URL}/oauth2/authorize` (콘솔 public client 도입 시) | n/a |

Spring Boot 설정 키:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URL}
          jwk-set-uri: ${JWT_JWKS_URI}
```

> **Edge Case — JWKS endpoint 정렬**: V0017 시드 SQL 은 IAM 의 표준
> `/oauth2/jwks` 엔드포인트를 사용한다 (`/.well-known/jwks.json` 아님). finance-platform
> 서비스의 `application.yml` default 도 이 endpoint 와 정렬되어 있다 — scm / wms /
> fan-platform JWKS URI 정렬과 동일.

---

## OAuth Clients (등록은 IAM 의 시드 마이그레이션에서 생성)

V0017 시드 ([TASK-MONO-114](../../../../tasks/done/TASK-MONO-114-finance-platform-bootstrap-artifact.md)):

| Client ID | Grant Types | PKCE | Redirect URIs | Flyway |
|---|---|---|---|---|
| `finance-platform-internal-services-client` | `client_credentials` | No | — | V0017 (TASK-MONO-114) |
| `finance-platform-user-flow-client` | `authorization_code` + `refresh_token` | 필수 | (TBD) | v2 DEFERRED |

`finance-platform-internal-services-client` 의 등록 메타:
- `client_authentication_methods`: `client_secret_basic`
- `access_token_time_to_live`: PT30M (1800 s)
- `aud`: `finance-platform-internal-services-client`
- 부여 가능 scope: `finance.read`, `finance.write`

Secret 은 V0017 Flyway 시드에 BCrypt(strength=10) 해시로 저장.
dev 평문 secret 은 `finance-dev` ([`.env.example`](../../.env.example) 의 `OIDC_INTERNAL_CLIENT_SECRET` 참고).
production 은 `OIDC_INTERNAL_CLIENT_SECRET` 환경 변수로 교체 후 admin API 로 갱신.

V0017 SQL 위치 — [`projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0017__seed_finance_oidc_client.sql`](../../../iam-platform/apps/auth-service/src/main/resources/db/migration/V0017__seed_finance_oidc_client.sql).

> **Edge Case — `sub` claim of client_credentials tokens**: `finance-platform-internal-services-client` 의
> 토큰은 `sub == client_id` 이며 `email` / `roles` claim 이 없다. gateway 의 헤더
> enrichment 필터가 `X-Account-Id` 헤더를 `sub` 값으로 전달하므로 — 다운스트림
> service 에서 `X-Account-Id` 가 client_id 일 수 있다. 사람 / 머신 사용자 구분이
> 필요한 경우 `X-Token-Type` (`user` | `client_credentials`) 헤더로 분기.

---

## Scopes

`<tenant>.<resource>.<action>` 명명 규칙. V0017 시점에는 광범위한 read/write 두 scope 만
등록 — 후속 service 별 세분화 (`finance.account.write` 등) 는 TASK-FIN-BE-001 시점에 결정.

| Scope | 설명 |
|---|---|
| `finance.read` | finance-platform 의 모든 read 리소스 (계좌 조회, 잔액, 거래 내역 등) |
| `finance.write` | finance-platform 의 mutation (계좌 개설, 잔액 hold/release/capture, 자금 이동 등) |

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`) 는 콘솔 user-flow 도입 시점에 적용.

---

## Token 검증 규칙 (각 finance-platform 서비스의 Resource Server 가 적용)

1. **서명 검증** — IAM 의 JWKS 로 RS256 서명 검증.
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (`JwtTimestampValidator`).
3. **Issuer 검증** — SAS issuer + legacy `iam-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Tenant 검증** — `tenant_id` claim 이 `finance` 또는 `*` (SUPER_ADMIN platform-scope) 인 경우만 통과. 그 외 (`wms`, `ecommerce`, `fan-platform`, `scm`, 향후 `erp`) → `tenant_mismatch` → 403 `TENANT_FORBIDDEN`.
5. **Scope 검증** — 다운스트림 service 의 `SecurityConfig` 가 `X-Scopes` 헤더 또는 SecurityContext 의 `Jwt.getClaimAsString("scope")` 로 enforce.

---

## platform-console Operator Read Consumer (ADR-MONO-013)

> 본 절은 [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) (ACCEPTED 2026-05-16) Model B 의 finance-side **(B) document/accept** 다. **새 capability·auth model·OAuth client·route·code 변경이 아니다** — 위 § Token 검증 규칙 의 *기존* 체인이 이미 허용하는 것을 명시적으로 기록할 뿐이다. finance 도메인 거버넌스는 [ADR-MONO-008](../../../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) 그대로이며 본 절로 재결정되지 않는다. 선행 동형: scm 의 [`TASK-SCM-BE-015`](../../../scm-platform/specs/integration/iam-integration.md#platform-console-operator-read-consumer-adr-mono-013) (Phase 4) — 본 절은 그 **Phase 5 analog**.

[`platform-console`](../../../platform-console/PROJECT.md) 은 별도의 ADR-MONO-013-governed 프로젝트로, **Model B(콘솔이 유일한 프론트엔드)** 하에 finance 의 운영자 화면을 finance 의 **기존 read API 를 server-side 호출**하여 렌더한다. finance 는 backend-only 를 유지한다 (§ 3.3; `PROJECT.md` 가 `frontend-app` service_type 을 두지 않는 것이 정상).

- **Sanctioned external read consumer**: `platform-console` 은 finance 의 v1-live **read** 표면만 소비한다:
  - `GET /api/finance/accounts/{id}` (계좌 + 잔액)
  - `GET /api/finance/accounts/{id}/balances`
  - `GET /api/finance/accounts/{id}/transactions` (paginated)

  (gateway-service 도입 시 `/api/v1/finance/**` → `/api/finance/**` rewrite — 콘솔 계약에 투명. finance v1 = gateway-service deferred, account-service 직접 JWT.)
- **Credential = IAM 자신의 `platform-console-web` 콘솔 클라이언트 토큰** (IAM / ADR-MONO-013 / ADR-MONO-014 소유). 사람 운영자의 IAM OIDC access token (RS256, `tenant_id=finance` 또는 SUPER_ADMIN `*`, `X-Token-Type=user`) 을 `Authorization: Bearer` 로 server-side 전달한다. 이는 **`finance-platform-internal-services-client` 가 아니며**, 위 § OAuth Clients 의 **deferred `finance-platform-user-flow-client` 도 아니다** (후자는 계속 deferred 이며 본 소비와 **무관** — 콘솔은 IAM 의 콘솔 클라이언트를 쓴다). 검증 경로는 위 § Token 검증 규칙 의 **기존** `AllowedIssuersValidator` (#3) + `TenantClaimValidator` (#4) + JWKS(#1) 체인 그대로 — **신규 finance OAuth client / gateway·service route / code / auth-model 변경 없음**.
- **Read-only (write/mutation 미소비)**: finance 의 **write/mutation** 표면 (`POST /accounts`, `/kyc/upgrade`, `/holds`, `/holds/{holdId}/capture`, `/holds/{holdId}/release`, `/transfers`) 은 콘솔이 소비하지 않는다. 이는 도메인 자금이동 / 운영자-도메인 mutation 으로 `Idempotency-Key` (fintech F1) 를 요구하며, v1 에서 operator-parity 콘솔 표면이 아니다 (finance 는 v1 `admin-service` 없음 — reconciliation 큐 / KYC-hold 검토 / 한도 정책의 v2 `admin-service` 운영자 표면은 ADR-MONO-008 § D3 / `PROJECT.md` v2 Service Map 으로 deferred). scm 선례가 PO write 를 제외한 것과 동일하게 read-only.
- **단일-org 보존**: finance 의 의도적 `multi-tenant` 미선언 (`PROJECT.md` § Out of Scope) 은 **불변** — 테넌트 스코핑은 IAM claim + 위 § Token 검증 규칙 #4 의 기존 producer-side `TenantClaimValidator` 게이트로 유지된다. 콘솔의 `multi-tenant`/`integration-heavy`/`audit-heavy` trait 은 **콘솔의** 책임이지 finance 의 것이 아니다 — finance 분류(domain/traits/service_types) 는 변경되지 않는다.
- **fintech producer 의무 (콘솔이 준수해야 할, producer-authoritative 사실의 cross-ref — 신규 finance 요구가 아님)**: ① **F5 money shape** — 모든 금액은 `{ amount: "<string-integer-minor-units>", currency }` (통화별 minor-unit scale; **float 아님**) 가 producer wire 계약이다 ([`account-api.md`](../contracts/http/account-api.md) 참조). 콘솔은 이를 충실히 렌더해야 한다 (float 강제/정밀도 손실 금지). ② finance 는 `data_sensitivity: confidential` + **F7** (PII / 규제 식별자는 producer-side 마스킹) — 콘솔은 잔액 / 거래 / 계좌 ref 를 로깅하지 않는다. ③ 규제 상태 (`PENDING_KYC|ACTIVE|RESTRICTED|FROZEN|CLOSED`, txn `FAILED|REVERSED`, sanction-driven) 는 정직하게 표면화한다 (숨기지 않음; 미지/미래 enum 은 generic label, throw 금지). 이들은 `account-api.md` / `account-service/architecture.md` 가 권위이며 본 절은 cross-ref 만 한다 (해당 spec 미변경).
- **Producer immutability**: 본 절은 **cross-reference only**. finance read 계약의 변경은 finance project-internal spec-first 변경(`account-api.md`)이며 본 절은 그것을 따를 뿐 재정의하지 않는다. 콘솔-side 의무는 platform-console [`console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md) **§ 2.4.7** (`TASK-PC-FE-009` 가 작성) 이며, 콘솔이 재사용하는 per-domain-credential 규칙은 같은 계약 § 2.4.5/§ 2.4.6 (FE-007 wms / FE-008 scm) 이다.

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `tenant_id != finance` (cross-tenant, 그리고 `*` 가 아님) | 403 | `TENANT_FORBIDDEN` |
| 유효 토큰이지만 scope/role 부족 | 403 | `FORBIDDEN` |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 을 따른다.

---

## dev smoke test

dev 토큰 발급:
```bash
curl -u finance-platform-internal-services-client:finance-dev \
     -d "grant_type=client_credentials&scope=finance.read" \
     http://iam.local/oauth2/token
```

응답 JWT decode 시 검증해야 할 claim:
- `iss` = `http://iam.local`
- `aud` = `finance-platform-internal-services-client`
- `tenant_id` = `finance`
- `scope` = `finance.read`
- `sub` = `finance-platform-internal-services-client`

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정.
- [ ] 콘솔 user-flow 도입 시점에 `finance-platform-user-flow-client` 의 V0NN 시드 추가 + redirect URI 갱신.
- [ ] `finance-platform-internal-services-client` 의 client_secret 을 secret manager 로 회전 (production).
- [ ] D2-b deprecation 윈도우 종료 시 allowed-issuers 에서 `iam-platform` 제거.
- [ ] IAM 의 `finance` 테넌트 (V0017 account-service 시드) 는 TASK-MONO-114 에서 등록됨.

---

## 참조

- [IAM ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — IAM IdP 승급
- [IAM auth-api.md § OAuth2 Clients](../../../iam-platform/specs/contracts/http/auth-api.md)
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- [scm-platform 의 동일 통합](../../../scm-platform/specs/integration/iam-integration.md) — reference pattern
- [wms-platform 의 동일 통합](../../../wms-platform/specs/integration/iam-integration.md) — reference pattern
- [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — platform-console Model B (governing; § platform-console Operator Read Consumer 의 권위) / [ADR-MONO-008](../../../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) — finance 도메인 거버넌스 (불변, cross-ref)
- [platform-console `console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md) — 콘솔-side 소비 의무 (§ 2.4.7 = TASK-PC-FE-009; § 2.4.5/§ 2.4.6 per-domain-credential 규칙)
- [scm `TASK-SCM-BE-015`](../../../scm-platform/tasks/done/TASK-SCM-BE-015-platform-console-operator-read-consumer-reconciliation.md) — Phase 4 선례 / TASK-FIN-BE-005 — 본 reconciliation (Phase 5 analog) / TASK-PC-FE-009 — 본 절이 unblock 하는 종속 태스크
- TASK-MONO-114 — IAM V0017 finance-platform OIDC/tenant 시드 (V0017 auth: `finance-platform-internal-services-client`, V0017 account: `finance` tenant)
- TASK-FIN-BE-001 — 본 통합의 첫 구현 태스크 (account-service bootstrap)
