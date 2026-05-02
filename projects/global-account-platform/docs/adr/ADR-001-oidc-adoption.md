# ADR-001 — OIDC Authorization Server 채택 여부

**Status:** ACCEPTED
**Date:** 2026-05-01 (ACCEPTED 2026-05-01)
**Decision driver:** GAP IdP 승격 — 6개 도메인(fan-platform, wms, ecommerce[향후], erp[향후], scm[향후], mes[향후], fan-community[향후])의 공유 인증 공급자 역할
**Supersedes:** none
**Related:** `specs/features/multi-tenancy.md`, `specs/features/authentication.md`, `specs/contracts/http/auth-api.md`

**Accepted Decisions:**
- **D1 = A** — Full OIDC Authorization Server (Spring Authorization Server)
- **D2 = D2-b** — 단계 폐기 (`POST /api/auth/login` 90일 deprecate 후 제거)
- **D3 = D3-b** — 경량 bulk provisioning (`POST /accounts:bulk`), SCIM 풀 지원은 별도 ADR
- **D4 = D4-c** — Consumer 라이브러리 없이 표준 OIDC + 통합 가이드 문서
- **D5 = ACCEPTED** — Follow-up 태스크 TASK-BE-251~259 (9건, GAP 내부) + TASK-MONO-019 (1건, cross-project) 발행

---

## 1. Context

`global-account-platform`(GAP)은 PROJECT.md 선언상 이미 multi-tenant 계정 플랫폼이며, 1차 소비자 `fan-platform`(B2C)과 2차 소비자 `wms`(B2B)가 이 플랫폼의 JWT를 사용한다. 향후 4개 B2B 도메인(`erp`, `scm`, `mes`, `fan-community`)이 추가 소비자로 합류할 계획이다.

현재 인증 흐름은 **자체 JWT 발급**이다:

- `POST /api/auth/login` (이메일·패스워드 + 옵션 `tenantId`) → access/refresh token pair 발급
- access token은 RS256으로 서명되며 `iss=global-account-platform`, `tenant_id`, `tenant_type`, `scope`, `device_id` claim 포함
- 소비 서비스는 `Authorization: Bearer <jwt>` 를 받아 자체적으로 검증
- `POST /api/auth/oauth/{authorize,callback}` 은 GAP가 외부 소셜 provider(Google/Kakao 등)의 OAuth client 역할을 할 때 사용 — GAP 자체가 authorization server인 것은 아님

**문제:**

1. **표준 OIDC 미지원.** `/.well-known/openid-configuration`, `/oauth2/jwks` (계정 서비스용), `/oauth2/token` (`grant_type=*`), `/oauth2/userinfo` 가 specs에 없다. 소비 서비스는 표준 OIDC 클라이언트 라이브러리(Spring Security OAuth2 Client, Auth0 SDK, NextAuth 등)를 사용할 수 없고 GAP 전용 통합 코드를 매번 작성해야 한다.
2. **Service-to-service 인증 모델 부재.** 사용자 로그인(`password` 흐름)만 있고, B2B 소비 서비스가 서버 간 호출에서 GAP 토큰을 획득하는 표준 경로(`client_credentials` grant)가 없다.
3. **외부 파트너 / 다언어 소비자 확장 어려움.** 모든 신규 통합이 Java + 자체 라이브러리 작성을 전제로 한다.

이 ADR은 위 문제를 해결할 **3가지 옵션을 비교**하고, 권고안을 제시한 뒤 사용자 결정을 요청한다.

---

## 2. Constraints

이 결정에 영향을 주는 제약:

- **포트폴리오 프로젝트.** 최종 사용자는 채용 평가자. "프로덕션 지향 설계" 가 PROJECT.md의 명시 목표이므로 표준 준수가 강한 가산점이 된다.
- **Java 17 + Spring Boot 3.x.** Spring Authorization Server (SAS) 1.x는 안정적으로 사용 가능하다.
- **multi-tenant 격리는 이미 구현됨.** JWT에 `tenant_id` claim이 들어가는 구조가 이미 있어, 어느 옵션을 선택해도 멀티테넌트 처리는 보존된다.
- **현재 소비 서비스 = `fan-platform` (FE: Next.js, BE 없음, 직접 GAP 호출), `wms` (Java/Spring), `community-service` + `membership-service` (frozen demo).** 활성 변경 대상은 fan-platform 클라이언트와 wms뿐.
- **신규 후보 도메인 4개는 specs도 없는 미래 항목.** 결정 시 무게는 "현재 소비자 마이그레이션 비용 < 미래 소비자 통합 비용 절감"으로 판단해야 한다.
- **Standalone 레포 배포 전략.** 결정이 standalone wms-platform·gap-platform 레포 분리 전략과 충돌하지 않아야 한다.

---

## 3. Options

### Option A — Full OIDC Authorization Server

GAP `auth-service`를 표준 OIDC authorization server로 승격한다.

**추가/변경할 엔드포인트:**

| 엔드포인트 | grant/용도 |
|---|---|
| `GET /.well-known/openid-configuration` | discovery |
| `GET /oauth2/jwks` | 공개 키 (현재 admin 전용 JWKS는 분리 유지) |
| `GET /oauth2/authorize` | `authorization_code` (PKCE 필수) |
| `POST /oauth2/token` | `authorization_code`, `client_credentials`, `refresh_token` |
| `GET /oauth2/userinfo` | ID token claims 조회 |
| `POST /oauth2/revoke` | RFC 7009 token revocation |
| `POST /oauth2/introspect` | RFC 7662 token introspection (gateway 자체 검증을 보완) |

**신규 도메인:**

- `oauth_clients` 테이블: `(client_id, client_secret_hash, redirect_uris, allowed_grants, allowed_scopes, tenant_id)` — 테넌트 단위 client 등록
- `oauth_scopes` 테이블: 시스템 정의 scope (`openid`, `profile`, `email`, `offline_access`) + 테넌트 정의 scope
- `oauth_consent` 테이블: B2C 소비자 consent 기록 (B2B는 admin 사전 동의로 skip)

**구현 기반:** Spring Authorization Server 1.x. 현재 `auth-service` 의 `JwtTokenIssuer` 등 자체 발급 로직은 SAS의 `JwtEncoder` 위임으로 대체.

**소비 서비스 측:**

- `wms`, 신규 ERP/SCM/MES: Spring Security OAuth2 Resource Server (`spring-security-oauth2-resource-server`) — 표준 라이브러리만 추가, JWKS URI 설정만 하면 검증 자동
- `fan-platform` (Next.js): `next-auth` + GAP custom OIDC provider 설정
- 서비스 간 호출: `client_credentials` grant → `WebClient.filter(ServerOAuth2AuthorizedClientExchangeFilterFunction)` 패턴

**Pros:**

- 모든 소비 서비스가 표준 라이브러리로 통합. 신규 도메인 추가 비용이 일회성 client 등록으로 축소.
- service-to-service 인증(`client_credentials`)이 자연스럽게 해결.
- 외부 파트너(예: 사내 다른 팀의 별도 서비스, 미래의 SaaS 통합)가 등장해도 표준 OIDC client로 등록만 하면 됨.
- 채용 평가자 시각: "OAuth2/OIDC를 표준대로 구현했다"가 강한 시그널.
- SAS는 Spring 팀의 1차 라이브러리. 보안 패치·토큰 처리·PKCE·DPoP 등 sharp edges를 위임 가능.

**Cons:**

- `auth-service` 코드/스펙 분량이 약 1.5~2배로 증가. consent 화면(B2C용), client 관리 admin UI, scope 모델, error response 표준화 등.
- `oauth_clients`/`oauth_consent` 신규 테이블 + 마이그레이션.
- 기존 `POST /api/auth/login` 흐름과의 공존 정책 필요(deprecate/병행/대체).
- B2C 소비자(`fan-platform`)에 redirect-based 로그인 UX 변경 — 현재 SPA에서 직접 POST하는 흐름이 OIDC redirect flow로 바뀜.
- 학습/구현 시간 (작성자 기준): SAS 도입·테스트·문서화 포함 추정 8~12 일.

**Estimated effort:** L (8~12 working days)

---

### Option B — Custom SDK 유지 (closed IdP)

현재 구조 그대로 두고, 소비 서비스 통합을 돕는 **공유 라이브러리**(`libs/java-gap-client`)를 추가한다.

**변경 사항:**

- `libs/java-gap-client/` 신규 — `GapJwtVerifier`, `GapClient` (사용자 조회), `GapAuthFilter` (Spring 필터)
- `account-internal-provisioning.md` 보완 (B2B onboarding 안정화)
- 그 외 인증 프로토콜은 현행 유지

**Pros:**

- `auth-service` 변경 최소. 현재 작동하는 흐름 보존.
- SAS 학습 비용 없음.
- B2C 소비자 UX 변화 없음.
- 구현 시간 짧음 (라이브러리 + provisioning 보완 추정 3~5 일).

**Cons:**

- Java 외 언어 소비자가 등장하면 매번 SDK 재작성. 다언어 지원이 사실상 봉쇄됨.
- service-to-service 인증을 위한 별도 메커니즘이 여전히 필요 (현재 `X-Internal-Token` + mTLS).
- 외부 파트너 통합 시 GAP 전용 문서·SDK를 학습시켜야 함.
- 채용 평가자 시각: "표준이 있는데 자체 구현을 선택한 이유"를 설명 부담.
- "GAP는 IdP" 라고 부르기 어려움 — 사실상 "공유 인증 API + 라이브러리".

**Estimated effort:** S (3~5 working days)

---

### Option C — Hybrid (표준 검증 + 자체 발급)

JWT **검증** 측면만 OIDC 표준에 맞추고, **발급** 측면은 현재 `POST /api/auth/login` 그대로 유지한다.

**추가할 엔드포인트:**

| 엔드포인트 | 용도 |
|---|---|
| `GET /.well-known/openid-configuration` | discovery (issuer, JWKS URI, supported algs만 광고) |
| `GET /oauth2/jwks` | 공개 키 |

**광고 내용:**

- discovery 문서에 `authorization_endpoint`/`token_endpoint`는 비워두거나 자체 경로(`/api/auth/login`)를 비표준으로 적시
- `id_token_signing_alg_values_supported`, `subject_types_supported` 등 검증 측 메타데이터만 표준대로 노출

**소비 서비스 측:**

- Spring Security OAuth2 Resource Server를 사용해 access token 검증은 표준 라이브러리로 처리 가능 (이게 가장 실수 많은 부분이었으므로 큰 가치)
- 발급 흐름은 여전히 GAP 전용 SDK 또는 직접 `POST /api/auth/login` 호출

**Pros:**

- 최소 변경으로 표준 검증 라이브러리의 이점만 취함.
- 현재 Login UX 보존.
- SAS 학습 부담 회피.
- 추정 2~3 일 작업.

**Cons:**

- "표준 OIDC 라고 광고하지만 표준이 아닌 부분이 더 많은" 어색한 구조 — 클라이언트 라이브러리가 discovery 문서를 신뢰해 `token_endpoint`를 호출하면 실패.
- service-to-service 인증 문제 미해결.
- 외부 파트너 통합·다언어 지원 문제 미해결 (Option B와 동일).
- 채용 평가자 시각: "왜 어중간한 표준 채택?" 의문 발생 가능.
- 향후 Option A로 마이그레이션할 때 "구버전 비표준 흐름과 신표준 OIDC 공존" 부담을 미루는 셈.

**Estimated effort:** XS (2~3 working days)

---

## 4. Recommendation

**Option A (Full OIDC Authorization Server)** 를 권고한다.

근거:

1. **포트폴리오 가치.** "프로덕션 지향 설계" 라는 PROJECT.md 선언과 가장 정합. 평가자 관점에서 OIDC + SAS 구현은 강한 기술 시그널.
2. **확장성.** 신규 도메인(ERP/SCM/MES) 추가가 client 등록만으로 가능 — 한 번의 큰 투자로 6개 서비스 + 미래 N개 통합 비용을 평탄화.
3. **service-to-service 인증의 자연스러운 해결.** B2B 마이크로서비스 간 호출이 표준 `client_credentials` grant로 처리되므로 별도 internal token 인프라가 불필요해짐.
4. **공존 비용 통제 가능.** 현재 `POST /api/auth/login` 은 SAS의 `authorization_code` flow와 병행 운영하다가 후속 단계에서 deprecate하면 되고, 둘은 동일한 `oauth_clients` 등록 모델을 공유하지 않으므로 충돌이 적다.
5. **Spring Authorization Server 의 성숙도.** 1.x는 production-ready. 직접 OAuth2 protocol 구현보다 안전.

권고하지 않는 이유:

- **Option B**: 현재 비용은 작지만 "GAP를 IdP로 만들겠다"는 본 작업의 목적과 정면으로 어긋남. 다언어/외부 파트너 확장 봉쇄.
- **Option C**: "표준 절반 채택" 의 어색한 결과물. 검증 측 표준화의 이점은 크지만 발급 측 비표준이 곧 다시 문제로 돌아옴. 마이그레이션을 미루는 효과.

---

## 5. Decision Items (사용자 확인 필요)

이 ADR을 PROPOSED → ACCEPTED 로 전환하기 위해 사용자가 확인해줄 항목:

### D1. 옵션 선택

- [ ] **A** — Full OIDC (권고)
- [ ] **B** — Custom SDK 유지
- [ ] **C** — Hybrid (검증만 표준)

### D2. (Option A 선택 시) 발급 흐름 공존 정책

- [ ] **D2-a** 병행 운영 — `POST /api/auth/login` 과 `/oauth2/authorize` 무기한 공존
- [ ] **D2-b** 단계 폐기 — 신규 client는 OIDC만 사용, 기존 `fan-platform` 마이그레이션 후 `/api/auth/login` deprecate (90일 후 제거)
- [ ] **D2-c** 즉시 대체 — `/api/auth/login` 즉시 제거, fan-platform·wms 동시 OIDC 마이그레이션

권고: **D2-b** (단계 폐기). 마이그레이션 위험 분산 + 이중 유지보수 기간 한정.

### D3. SCIM 2.0 채택 범위 (어느 옵션이든 별도 결정)

B2B 소비자가 사용자 디렉터리를 GAP에 동기화할 때:

- [ ] **D3-a** SCIM 2.0 표준 (`/scim/v2/Users`, `/scim/v2/Groups`) 풀 지원
- [ ] **D3-b** 경량 bulk provisioning — `POST /internal/tenants/{id}/accounts:bulk` 만 추가
- [ ] **D3-c** 현행 단건 API만 유지 (각 소비자가 자체 동기화 구현)

권고: **D3-b** (경량 bulk). 포트폴리오 단일 작성자가 SCIM 풀 지원·테스트하기는 과투자. bulk만으로 ERP/MES 초기 onboarding 충족.

### D4. Consumer integration 라이브러리 전략

- [ ] **D4-a** Spring Boot Starter 1개 (`libs/java-gap-client`) — Java 소비자 한정
- [ ] **D4-b** Spring Boot Starter + 별도 언어 가이드 문서 (Node.js / Python 통합 지침)
- [ ] **D4-c** 라이브러리 없이 표준 OIDC discovery + 가이드만 (Option A 채택 시)

권고: Option A 채택 시 **D4-c**. 표준 OIDC라는 게 곧 "라이브러리 불필요"의 이점이므로 굳이 자체 starter를 만들 필요가 적음. WMS는 Spring Security OAuth2 Resource Server 의존성 1줄로 충분.

### D5. ADR 채택 여부 (별도 항목)

- [ ] PROPOSED → ACCEPTED 로 승격하고 본 ADR을 결정 근거로 follow-up 태스크 발행

---

## 6. Consequences (옵션별 후속 태스크)

> D4-c 결정의 산출물로 [specs/features/consumer-integration-guide.md](../../specs/features/consumer-integration-guide.md) 가 신규 소비 서비스의 단일 진입 가이드를 제공한다 (TASK-BE-254). 이후 OIDC contract 변경 시 본 ADR과 함께 가이드도 동기 갱신한다.

### Option A 선택 시 (실제 발행된 태스크)

| 우선순위 | 태스크 ID | 제목 | 의존성 |
|---|---|---|---|
| P0 | TASK-BE-251 | Spring Authorization Server 도입 + `/oauth2/*` 표준 엔드포인트 | 본 ADR ACCEPTED |
| P0 | TASK-BE-252 | `oauth_clients`/`oauth_scopes`/`oauth_consent` 스키마 + 마이그레이션 + JPA repository | 251과 병렬 |
| P1 | TASK-BE-253 | community/membership-service OIDC 통합 (FROZEN 예외) | 251, 252 완료 후 |
| P1 | TASK-MONO-019 | wms-platform OIDC Resource Server 전환 (cross-project) | 251, 252 완료 후 |
| P1 | TASK-BE-254 | 소비 서비스 통합 가이드 (`consumer-integration-guide.md`) | 251 완료 후 (또는 선행 작성) |
| P1 | TASK-BE-255 | `account_roles` 테이블 스키마 명시 + 마이그레이션 | 본 ADR과 독립 |
| P1 | TASK-BE-256 | 테넌트 onboarding API 계약 완성 (`POST /api/admin/tenants`) | 본 ADR과 독립 (TASK-BE-250 선행 contract) |
| P2 | TASK-BE-257 | bulk provisioning API (`POST /internal/tenants/{id}/accounts:bulk`) | D3-b 결정 적용 |
| P2 | TASK-BE-258 | GDPR 삭제 downstream 전파 계약 + security-service reference 구현 | 본 ADR과 독립 |
| P3 | TASK-BE-259 | `auth.token.reuse.detected` payload에 `tenant_id` 추가 | 본 ADR과 독립 |

### Option B 선택 시 (예상)

| 우선순위 | 태스크 (가칭) | 비고 |
|---|---|---|
| P0 | TASK-BE-251 — `libs/java-gap-client` Spring Boot Starter | OAuth2 표준 미사용 |
| P0 | TASK-BE-252 — `account-internal-provisioning.md` 보완 + bulk API | |
| P1 | (그 외 4개) — 본 ADR과 독립 항목들은 동일 |

### Option C 선택 시 (예상)

| 우선순위 | 태스크 (가칭) | 비고 |
|---|---|---|
| P0 | TASK-BE-251 — `/.well-known/openid-configuration` + `/oauth2/jwks` 엔드포인트 | 검증 측 표준 |
| P1 | TASK-BE-252 — `libs/java-gap-client` (발급 측 라이브러리) | 검증은 표준, 발급은 자체 |
| P1 | (그 외 4개) — 본 ADR과 독립 항목들은 동일 |

---

## 7. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| (Option A) SAS 학습 곡선 — 첫 구현에서 RFC 미준수 발생 가능 | Spring 공식 sample (`spring-authorization-server` GitHub) 그대로 시작 후 multi-tenant 확장만 추가 |
| (Option A) `fan-platform` 로그인 UX 변경 거부감 | D2-b 단계 폐기로 마이그레이션 윈도우 확보. 기존 흐름 90일 병행 |
| (Option A) `oauth_clients` 비밀 관리 — DB 노출 시 즉시 토큰 발급 가능 | `client_secret_hash` 만 저장 (BCrypt), client 등록은 admin-service 경유로 audit 트레일 강제 |
| (어느 옵션이든) multi-tenant 격리 회귀 | 본 ADR의 어느 옵션도 `tenant_id` claim 구조를 변경하지 않음. cross-tenant 회귀 테스트 (TASK-BE-248 시리즈) 활용 |
| 현재 ADR 문서 위치 (`docs/adr/`) 가 신규 디렉터리 | 본 ADR로 컨벤션 시작. 후속 ADR도 `ADR-NNN-<slug>.md` 패턴 |

---

## 8. References

- Specs:
  - `specs/features/multi-tenancy.md`
  - `specs/features/authentication.md`
  - `specs/contracts/http/auth-api.md`
  - `specs/services/auth-service/architecture.md`
  - `specs/contracts/http/internal/account-internal-provisioning.md`
- 외부:
  - [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
  - [RFC 6749 — OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
  - [RFC 7636 — PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
  - [RFC 7662 — Token Introspection](https://datatracker.ietf.org/doc/html/rfc7662)
  - [Spring Authorization Server](https://spring.io/projects/spring-authorization-server)
  - [SCIM 2.0 — RFC 7644](https://datatracker.ietf.org/doc/html/rfc7644)

---

## 9. Decision Log

| Date | Status | Note |
|---|---|---|
| 2026-05-01 | PROPOSED | 초안 작성, 5개 결정 항목 사용자 확인 대기 |
| 2026-05-01 | ACCEPTED | D1=A, D2=D2-b, D3=D3-b, D4=D4-c 확정. TASK-BE-251~259 (9건) + TASK-MONO-019 발행 |
