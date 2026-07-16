# Integration — iam-platform (IAM) OIDC

> 본 문서는 `erp-platform` 의 모든 서비스가 IAM 를 표준 OIDC IdP(SSO) 로 사용하는 방식을 1쪽으로 요약한다.
> [IAM ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) 의 erp-platform 적용본이며,
> [scm-platform](../../../scm-platform/specs/integration/iam-integration.md) /
> [finance-platform](../../../finance-platform/specs/integration/iam-integration.md) 의 같은 통합 패턴을 따른다.

---

## Tenant Identity

- `tenant_id` = `erp`
- `tenant_type` = `B2B_ENTERPRISE` (내부-서비스 모델 — scm / finance 와 동일 type)
- v1 = backend only — self-service signup endpoint 사용 안 함 (internal-system 경계 — 외부 공개 트래픽 없음). 모든 운영자 / 시스템 계정은
  관리자 API ([account internal provisioning](../../../iam-platform/specs/contracts/http/internal/account-internal-provisioning.md))
  로 생성한다.

---

## OIDC Endpoints (consumed by erp-platform)

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

> **Edge Case — JWKS endpoint 정렬**: V0018 시드 SQL 은 IAM 의 표준
> `/oauth2/jwks` 엔드포인트를 사용한다 (`/.well-known/jwks.json` 아님). erp-platform
> 서비스의 `application.yml` default 도 이 endpoint 와 정렬되어 있다 — scm / finance /
> wms JWKS URI 정렬과 동일.

---

## OAuth Clients (등록은 IAM 의 시드 마이그레이션에서 생성)

V0018 시드 ([TASK-MONO-119](../../../../tasks/done/TASK-MONO-119-erp-platform-bootstrap-artifact.md)):

| Client ID | Grant Types | PKCE | Redirect URIs | Flyway |
|---|---|---|---|---|
| `erp-platform-internal-services-client` | `client_credentials` | No | — | V0018 (TASK-MONO-119) |
| `erp-platform-user-flow-client` | `authorization_code` + `refresh_token` | 필수 | (TBD) | v2 DEFERRED |

`erp-platform-internal-services-client` 의 등록 메타:
- `client_authentication_methods`: `client_secret_basic`
- `access_token_time_to_live`: PT30M (1800 s)
- `aud`: `erp-platform-internal-services-client`
- 부여 가능 scope: `erp.read`, `erp.write`

Secret 은 V0018 Flyway 시드에 BCrypt(strength=10) 해시로 저장.
dev 평문 secret 은 `erp-dev` ([`.env.example`](../../.env.example) 의 `OIDC_INTERNAL_CLIENT_SECRET` 참고).
production 은 `OIDC_INTERNAL_CLIENT_SECRET` 환경 변수로 교체 후 admin API 로 갱신.

V0018 SQL 위치 — [`projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0018__seed_erp_oidc_client.sql`](../../../iam-platform/apps/auth-service/src/main/resources/db/migration/V0018__seed_erp_oidc_client.sql).

> **Edge Case — `sub` claim of client_credentials tokens**: `erp-platform-internal-services-client` 의
> 토큰은 `sub == client_id` 이며 `email` / `roles` claim 이 없다. gateway 의 헤더
> enrichment 필터가 `X-Account-Id` 헤더를 `sub` 값으로 전달하므로 — 다운스트림
> service 에서 `X-Account-Id` 가 client_id 일 수 있다. 사람 / 머신 사용자 구분이
> 필요한 경우 `X-Token-Type` (`user` | `client_credentials`) 헤더로 분기.

---

## Scopes

`<tenant>.<resource>.<action>` 명명 규칙. V0018 시점에는 광범위한 read/write 두 scope 만
등록 — 후속 service 별 세분화 (`erp.masterdata.write` 등) 는 TASK-ERP-BE-001 시점에 결정.

| Scope | 설명 |
|---|---|
| `erp.read` | erp-platform 의 모든 read 리소스 (마스터 조회, 결재 조회, 통합 조회 등) |
| `erp.write` | erp-platform 의 mutation (마스터 생성/수정/폐기, 결재 상신/승인 등) |

OIDC 표준 scope (`openid`, `profile`, `email`, `offline_access`) 는 콘솔 user-flow 도입 시점에 적용.

---

## Token 검증 규칙 (각 erp-platform 서비스의 Resource Server 가 적용)

1. **서명 검증** — IAM 의 JWKS 로 RS256 서명 검증.
2. **표준 클레임 검증** — `exp`, `nbf`, `iat` (`JwtTimestampValidator`).
3. **Issuer 검증** — SAS issuer + legacy `iam-platform` 양쪽 허용 (D2-b deprecate 호환).
4. **Tenant 검증** — `tenant_id` claim 이 `erp` 또는 `*` (SUPER_ADMIN platform-scope) 인 경우만 통과. 그 외 (`wms`, `ecommerce`, `fan-platform`, `scm`, `finance`) → `tenant_mismatch` → 403 `TENANT_FORBIDDEN`.
5. **Scope 검증** — 다운스트림 service 의 `SecurityConfig` 가 `X-Scopes` 헤더 또는 SecurityContext 의 `Jwt.getClaimAsString("scope")` 로 enforce.
6. **internal-only 경계** — 외부(비-내부망·비-SSO) 트래픽은 게이트웨이/네트워크 정책에서 거부 (도메인 룰 E7).

> **운영자 도메인 role 의 출처 (roles-only 모델)**: ADR-MONO-032 (통합 identity — `roles` 가 유일한 인가 축) + ADR-MONO-035 (operator 인증 통합) 로 `account_type` claim/column/gateway-leg 는 전부 제거되었다 (ADR-032 D5 step 4 + step 5, COMPLETE 2026-06-15). 운영자의 erp 도메인 인가는 JWT `roles` claim 을 타며, 운영자의 도메인 role 은 **assume-tenant 교환 시 IAM 이 선택 tenant 의 entitled domains 에서 파생**한다 (`erp → ERP_OPERATOR`, `OperatorRoleDerivation`; ADR-MONO-035 O1 / step 4a) — 운영자의 assignment 를 fail-closed 로 검증한 뒤 가산된다. erp 서비스의 `isOperator()` 는 이 `roles ∋ ERP_OPERATOR`/`ERP_ADMIN`/`SUPER_ADMIN` 를 읽는다. (erp 코드는 이미 roles 기반 — `account_type` 참조 0; 본 모델은 IAM 발행 측이 권위, erp 는 소비만 한다.)

---

## platform-console Operator Read Consumer (ADR-MONO-013)

> 본 절은 [ADR-MONO-013](../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) (ACCEPTED 2026-05-16) Model B 의 erp-side **(B) document/accept** 다. **새 capability·auth model·OAuth client·route·code 변경이 아니다** — 위 § Token 검증 규칙 의 *기존* 체인 (#1 JWKS RS256 + #3 issuer + #4 tenant `erp`/`*` + #6 internal-only 경계) 이 이미 허용하는 것을 명시적으로 기록할 뿐이다. erp 도메인 거버넌스는 [ADR-MONO-016](../../../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) 그대로이며 본 절로 재결정되지 않는다. 선행 동형: scm 의 [`TASK-SCM-BE-015`](../../../scm-platform/specs/integration/iam-integration.md) (Phase 4) + finance 의 [`TASK-FIN-BE-005`](../../../finance-platform/specs/integration/iam-integration.md#platform-console-operator-read-consumer-adr-mono-013) (Phase 5) — 본 절은 그 **Phase 6 analog** 이며, erp 는 finance 와 동일하게 `gateway-public-routes.md` 가 존재하지 않으므로 **finance 2-file shape** 를 답습한다 (scm 3-file 아님).

[`platform-console`](../../../platform-console/PROJECT.md) 은 별도의 ADR-MONO-013-governed 프로젝트로, **Model B(콘솔이 유일한 프론트엔드)** 하에 erp 의 운영자 화면을 erp 의 **기존 read API 를 server-side 호출**하여 렌더한다. erp 는 backend-only 를 유지한다 (§ 3.3; ADR-MONO-016 § D3.1 *"platform-console parity-slice as binding UI decision"* 가 이를 명시; `PROJECT.md` 가 `frontend-app` service_type 을 두지 않는 것이 정상).

- **Sanctioned external read consumer**: `platform-console` 은 erp 의 v1-live **read** 표면만 소비한다 — 5 master × {list, detail} = **10 GET endpoints**:
  - `GET /api/erp/masterdata/departments` (list) + `/{id}` (detail)
  - `GET /api/erp/masterdata/employees` (list) + `/{id}` (detail)
  - `GET /api/erp/masterdata/job-grades` (list) + `/{id}` (detail)
  - `GET /api/erp/masterdata/cost-centers` (list) + `/{id}` (detail)
  - `GET /api/erp/masterdata/business-partners` (list) + `/{id}` (detail)

  모두 `?asOf=<ISO-8601>` point-in-time read query 를 지원 (effective-period `[from, to)` half-open semantics; producer 의 E3 invariant). (gateway-service 도입 시 `/api/v1/erp/**` → `/api/erp/**` rewrite — 콘솔 계약에 투명. erp v1 = gateway-service v1-IN 선언이나 architecture spec 아직 미작성, masterdata-service 직접 JWT.)
- **Credential = IAM 자신의 `platform-console-web` 콘솔 클라이언트 토큰** (IAM / ADR-MONO-013 / ADR-MONO-014 소유). 사람 운영자의 IAM OIDC access token (RS256, `tenant_id=erp` 또는 SUPER_ADMIN `*`, `X-Token-Type=user`) 을 `Authorization: Bearer` 로 server-side 전달한다. 이는 위 § OAuth Clients 의 **`erp-platform-internal-services-client` 가 아니며** (후자는 v1 의 유일한 erp OAuth client 이며 본 소비와 **무관** — 콘솔은 IAM 의 콘솔 클라이언트를 쓴다; erp 는 v1 user-flow client 미발행/미예정). 검증 경로는 위 § Token 검증 규칙 의 **기존** JWKS(#1) + issuer(#3) + tenant(#4) 체인 그대로 — **신규 erp OAuth client / gateway·service route / code / auth-model 변경 없음**.
- **`internal-only 경계` 명료화 (위 #6 / 도메인 룰 E7, 약화가 아닌 명료화)**: #6 의 "외부(비-내부망·비-SSO) 트래픽" 은 *비-IAM-SSO* 트래픽 (raw public internet, 비신뢰 네트워크, 직접 호출) 을 지칭한다. ADR-MONO-013-governed `platform-console` 은 **IAM-SSO 로 인증된 사람 운영자 토큰**을 보유하고 **내부 Traefik 라우팅을 통과**하여 도달하므로 SSO 경계 **내부** 에 있다 — 외부 우회가 아니다. 본 절은 #6 을 byte-identical 로 보존하며, 콘솔이 #6 에 의해 어떻게 허용되는지를 명시적으로 기록할 뿐이다.
- **Read-only (write/mutation 미소비)**: erp 의 **write/mutation** 표면 ([`masterdata-api.md`](../contracts/http/masterdata-api.md) 의 16 non-GET endpoints — 5×`POST` create / 5×`PATCH` / 5×`POST /retire` / 1×`POST .../move-parent`) 은 콘솔이 소비하지 않는다. 이들은 운영자-도메인 mutation 으로 `Idempotency-Key` (도메인 룰 F1) + role-scoped authorization (E6 fail-CLOSED) + append-only audit (E8) 을 요구하며, v1 에서 operator-parity 콘솔 표면이 아니다 (erp 는 v1 `admin-service` 없음; v2 `approval-service` / `read-model-service` / future `admin-service` 운영자 표면은 ADR-MONO-016 § D3 v2 Service Map / `PROJECT.md` § v1 OUT 으로 deferred). scm/finance 선례가 write 를 제외한 것과 동일하게 read-only.
- **단일-org 보존**: erp 의 의도적 `multi-tenant` 미선언 (`PROJECT.md` frontmatter `traits: [internal-system, transactional, audit-heavy]`) 은 **불변** — 테넌트 스코핑은 IAM claim + 위 § Token 검증 규칙 #4 의 기존 producer-side `tenant_id ∈ {erp,*}` 게이트로 유지된다. 콘솔의 `multi-tenant`/`integration-heavy`/`audit-heavy` trait 은 **콘솔의** 책임이지 erp 의 것이 아니다 — erp 분류(domain/traits/service_types/data_sensitivity) 는 변경되지 않는다.
- **erp internal-system producer 의무 (콘솔이 준수해야 할, producer-authoritative 사실의 cross-ref — 신규 erp 요구가 아님)**: ① **`data_sensitivity: confidential` + audit-heavy** — 콘솔은 직원 PII (이름·연락처) / 거래처 금융 식별자 / 비용센터 민감 속성을 로깅하지 않는다 (E7 의 외부 트래픽 거부 보호와 별개로 콘솔-side 의무). ② **E1 reference integrity** — 콘솔이 retire 된 master 를 참조해도, masterdata-service 가 reject 한 reason 을 충실히 표면화한다 (자체 sanitize 금지). ③ **E2 effective dating `[from, to)`** — `effective_to` 가 채워진 row (retired) 와 NULL row (active) 를 정직하게 시각적으로 구분 렌더한다. ④ **E3 point-in-time read** — `?asOf=<past>` 가 그 시점의 상태를 반환하는 것을 콘솔이 의도 그대로 렌더한다 (현재 상태로 substitute 금지). ⑤ **E8 append-only audit log** — 콘솔 read 자체는 audit_log 에 기록되지 않지만 (read-only), 콘솔이 표시하는 master 의 변경 history 는 producer-side audit 가 권위이며 콘솔은 그것을 충실히 렌더한다. 이들은 [`masterdata-api.md`](../contracts/http/masterdata-api.md) / [`masterdata-service/architecture.md`](../services/masterdata-service/architecture.md) 가 권위이며 본 절은 cross-ref 만 한다 (해당 spec 미변경).
- **Producer immutability**: 본 절은 **cross-reference only**. erp read 계약의 변경은 erp project-internal spec-first 변경(`masterdata-api.md`)이며 본 절은 그것을 따를 뿐 재정의하지 않는다. 콘솔-side 의무는 platform-console [`console-integration-contract.md`](../../../platform-console/specs/contracts/console-integration-contract.md) **§ 2.4.8** (`TASK-PC-FE-010` 가 작성) 이며, 콘솔이 재사용하는 per-domain-credential 규칙은 같은 계약 § 2.4.5/§ 2.4.6/§ 2.4.7 (FE-007 wms / FE-008 scm / FE-009 finance) 이다 — 본 절은 **Phase 6** analog 로서 그 검증된 계약을 재구성 없이 상속한다.

---

## Error Responses

| 시나리오 | HTTP | error.code |
|---|---|---|
| Authorization 헤더 누락 / 만료 / 서명 불일치 | 401 | `UNAUTHORIZED` |
| `tenant_id != erp` (cross-tenant, 그리고 `*` 가 아님) | 403 | `TENANT_FORBIDDEN` |
| 유효 토큰이지만 scope/role 부족 | 403 | `FORBIDDEN` |

`platform/error-handling.md` 의 envelope 형식 (`{ "code", "message", "timestamp" }`) 을 따른다.

---

## dev smoke test

dev 토큰 발급:
```bash
curl -u erp-platform-internal-services-client:erp-dev \
     -d "grant_type=client_credentials&scope=erp.read" \
     http://iam.local/oauth2/token
```

응답 JWT decode 시 검증해야 할 claim:
- `iss` = `http://iam.local`
- `aud` = `erp-platform-internal-services-client`
- `tenant_id` = `erp`
- `scope` = `erp.read`
- `sub` = `erp-platform-internal-services-client`

> **⚠️ 이 머신 토큰의 write 한계 (TASK-ERP-BE-029)**: `client_credentials` 토큰은 `roles`·`org_scope`(data-scope) claim 을 **갖지 않는다** (위 § Edge Case). masterdata-service 의 write-side 인가(`RoleScopeAuthorizationAdapter`)는 **부서 타겟이 있는** mutation 에 대해 data-scope 포함을 요구하므로, 이 토큰(scope=`erp.write`)으로는 **read + 부서-타겟이 없는 write 만** 가능하다:
> - ✅ 가능: 모든 list/detail read, **루트** 부서 생성(`parentId=null`), job-grade·business-partner 생성/수정/폐기 (target 없음).
> - ❌ 403 `DATA_SCOPE_FORBIDDEN`: employee·cost-center 생성/수정/폐기, **자식** 부서 생성/이동/폐기 — 즉 부서 subtree 를 타겟하는 모든 write.
>
> 부서-스코프 write 는 `org_scope`(멤버십 파생 subtree-root 집합, 또는 platform `["*"]`)를 담은 **운영자 토큰**으로 수행해야 한다. 머신 토큰에 platform-wide data-scope 를 부여하려던 converter fallback 은 **dead code 였고 TASK-ERP-BE-029 에서 제거**되었다 (predicate 가 실 IAM 토큰과 불일치 — 절대 발화하지 않았다). read-model-service 의 read gate 는 반대로 absent `org_scope` 를 platform 으로 취급한다(BE-007 net-zero) — read/write 는 **의도적으로** 다르다.

---

## 운영 체크리스트

- [ ] dev / stg / prod 별 `OIDC_ISSUER_URL` 확정.
- [ ] 콘솔 user-flow 도입 시점에 `erp-platform-user-flow-client` 의 V0NN 시드 추가 + redirect URI 갱신.
- [ ] `erp-platform-internal-services-client` 의 client_secret 을 secret manager 로 회전 (production).
- [ ] D2-b deprecation 윈도우 종료 시 allowed-issuers 에서 `iam-platform` 제거.
- [ ] IAM 의 `erp` 테넌트 (V0018 account-service 시드) 는 TASK-MONO-119 에서 등록됨.

---

## 참조

- [IAM ADR-001](../../../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — IAM IdP 승급
- [IAM auth-api.md § OAuth2 Clients](../../../iam-platform/specs/contracts/http/auth-api.md)
- [platform/contracts/jwt-standard-claims.md](../../../../platform/contracts/jwt-standard-claims.md) — JWT 클레임 표준
- [scm-platform 의 동일 통합](../../../scm-platform/specs/integration/iam-integration.md) — reference pattern
- [finance-platform 의 동일 통합](../../../finance-platform/specs/integration/iam-integration.md) — reference pattern
- TASK-MONO-119 — IAM V0018 erp-platform OIDC/tenant 시드 (V0018 auth: `erp-platform-internal-services-client`, V0018 account: `erp` tenant)
- TASK-ERP-BE-001 — 본 통합의 첫 구현 태스크 (masterdata-service bootstrap)
