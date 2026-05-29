# ADR-005: 서비스 간(Workload) 인증 — `client_credentials` 단기 JWT

**Status**: PROPOSED
**Date**: 2026-05-29 (proposed)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: ADR-001 (OIDC Adoption), ADR-003 (SAS public-client converter — `client_credentials` grant 가 이미 동작함을 입증한 회귀 매트릭스), 내부 계약 `specs/contracts/http/internal/*.md` (10개 경로)

---

## Context

본 플랫폼의 **사용자(edge) 신원**은 현대 클라우드 콘솔(AWS/GCP)이 수렴하는 모델을 충실히 따른다:

- **중앙 IdP + Federation**: GAP `auth-service` = Spring Authorization Server(full OIDC — discovery/JWKS/token/userinfo/revocation/introspection) + 외부 IdP 3종(Google/Kakao/Microsoft).
- **Short-lived 임시 자격증명**: access token TTL 1800s + refresh token rotation + 재사용 탐지(`SasRefreshTokenAuthenticationProvider`).
- **최소권한 + 세밀한 정책**: `RequiresPermissionAspect` deny-by-default RBAC + DENIED 감사 기록.

그러나 **서비스 간(workload) 신원**은 그 수준에 미달한다:

| 수신 서비스 | `/internal/**` 보호 | 메커니즘 |
|---|---|---|
| account-service | ✅ 검증함 | `InternalApiFilter` — `X-Internal-Token` **정적 공유 토큰** (fail-closed) |
| security-service | ✅ 검증함 | `X-Internal-Token` 정적 공유 토큰 |
| auth-service | ❌ 미보호 | `/internal/**` = `permitAll()` (내부망 경계만 신뢰) |

내부 호출 경로는 10개(`specs/contracts/http/internal/*.md`): admin→account, admin→auth, gateway→auth, account→auth(credential), auth→account(+social), security→account, community→account, community→membership, account-internal-provisioning.

### 클라우드 대비 갭

| 패턴 | AWS | GCP | 현재 GAP |
|---|---|---|---|
| 워크로드 신원 | IAM Role | Service Account | 없음(정적 공유 토큰) |
| 자격증명 | STS 임시(IMDS) | 단기 OAuth(메타데이터) | 정적 `X-Internal-Token` / permitAll |
| keyless | ✅ | ✅(키파일 비권장) | ❌ |

정적 공유 토큰은 (1) 유출 시 전 서비스 영향, (2) 회전 부재, (3) 호출자 신원 구분 불가 — workload identity 의 핵심 속성을 결여한다. 내부 계약 `auth-internal.md` 도 *"향후 X-Internal-Token 또는 mTLS 추가 예정"* 으로 갭을 명시해 둔 상태다.

### 결정적 전제 — 인프라가 이미 존재

ADR-003 회귀 매트릭스에서 **`client_credentials (test-internal-client) → 200 PASS`** 가 확인된다. 즉 GAP `auth-service` 는 이미 `client_credentials` grant 를 지원하는 OIDC AS 다. 본 ADR 의 결정은 **새 인증 인프라 신축이 아니라, 이미 있는 OIDC AS 를 서비스 간 경로에 재사용**하는 것이다.

---

## Decision (Proposed)

### 옵션 비교

| 옵션 | 설명 | keyless | 장점 | 단점 |
|---|---|---|---|---|
| **A. `client_credentials` 단기 JWT** | 각 서비스가 GAP 에서 `client_credentials` grant 로 단기 JWT 발급 → `Authorization: Bearer` 로 `/internal/**` 호출. 수신측은 JWKS 로 검증 | △ (client_secret 1개 정적 잔존) | GAP OIDC AS **이미 지원** → 신축 최소. edge 신원과 동일 인프라로 일관. 호출자 신원(client_id)·만료·회전 확보 | client_secret 은 여전히 정적 비밀 |
| **B. mTLS (X.509)** | 서비스 간 상호 TLS 인증서 | ✅ 완전 | AWS IAM Roles Anywhere 급 완전 keyless | docker-compose 환경 인증서 발급·회전·배포 관리 부담. 포트폴리오 규모 대비 과함 |
| **C. `X-Internal-Token` 강화** | 정적 토큰을 서비스별로 분리/회전 | ❌ | 변경 최소 | 정적 비밀 본질 불변 → ④ workload identity 미충족 |

### 권장 — 옵션 A

**근거**:

1. **인프라 재사용**: `client_credentials` grant 가 이미 동작(ADR-003 입증). 토큰 발급자(=AWS STS / GCP 메타데이터 역할)를 GAP 에 맡기면 추가 컴포넌트 0.
2. **edge 신원과 일관**: 사용자 신원이 이미 OIDC+JWT+JWKS. 서비스 간도 같은 검증 경로(JWKS resource-server)로 통일 → 인지·운영 부담 최소.
3. **workload identity 속성 확보**: 호출자 신원(`client_id`/`sub`)·단기 만료·중앙 발급이 정적 공유 토큰 대비 명백한 진전. ④ 충족, ① 대폭 개선.
4. **B(mTLS)는 과함**: docker-compose 포트폴리오에 인증서 PKI 관리는 비용 대비 효과 낮음. 완전 keyless 는 후속으로 분리(아래 Consequences).

### 핵심 설계

```
[호출측]  서비스 부팅/호출 시
  POST /oauth2/token (grant_type=client_credentials, client_id=svc-<name>, client_secret=…)
   → 단기 access_token(JWT) 수신 (캐시 + 만료 전 갱신)
   → 내부 호출에 Authorization: Bearer <jwt> 첨부

[수신측]  /internal/** 보안 체인
  X-Internal-Token(InternalApiFilter)  →  OAuth2 Resource Server (JWKS 검증)
   - iss = GAP issuer, JWKS = auth-service /oauth2/jwks
   - 선택적으로 client_id allowlist (호출자 신원 검증)
```

서비스별 registered client(`svc-admin`, `svc-account`, `svc-security`, …)를 `client_credentials` grant 로 등록(migration seed). 수신측은 `/internal/**` 를 permitAll/정적토큰에서 **JWT resource-server 검증**으로 전환한다.

### 무중단 마이그레이션 (4단계)

| 단계 | 내용 | 무중단 보장 |
|---|---|---|
| **1. 발급 인프라** | GAP `client_credentials` 서비스 client 등록(seed) + 토큰 발급 검증 | 신규 추가, 기존 경로 불변 |
| **2. 수신측 이중 허용** | 각 수신 서비스가 **JWT 검증을 추가하되 X-Internal-Token 병행 허용** | 둘 중 하나 통과 → 기존 호출자 안 깨짐 |
| **3. 호출측 전환** | 각 호출 서비스가 Bearer JWT 로 전환 | 수신측이 이미 둘 다 받음 |
| **4. 정적 토큰 제거** | `X-Internal-Token`/`InternalApiFilter` 제거 + auth `/internal` permitAll 제거 + 계약 spec 갱신 | 모든 호출자 전환 완료 후 |

### 영향범위

- **GAP auth-service**: `client_credentials` 서비스 client seed(migration), JWT 에 서비스 신원 claim.
- **수신측 SecurityConfig** (account/security/auth + 잠재적으로 community/membership): `/internal/**` JWT resource-server 검증.
- **호출측 클라이언트** (admin/account/auth/security/community/gateway): 토큰 획득·캐시·첨부 로직.
- **내부 계약 spec 10개**: "인증" 절을 `X-Internal-Token`/permitAll → Bearer JWT 로 갱신.
- **테스트/인프라**: 각 서비스 IT + e2e + docker-compose env(`INTERNAL_API_TOKEN`) 정리.

---

## Consequences

### Positive

- **④ Workload Identity 충족**: 서비스 간 호출이 중앙 발급 단기 JWT 기반 → 호출자 신원·만료·회전 확보. AWS IAM Role / GCP Service Account 모델의 축소판.
- **① Keyless 대폭 개선**: 정적 공유 토큰(전 서비스 1개) → 서비스별 단기 토큰. auth `/internal` permitAll 해소.
- **edge 신원과 단일 검증 경로**: JWKS resource-server 하나로 사용자·서비스 토큰 모두 검증 → 일관성·운영 단순화.

### Negative

- **client_secret 정적 잔존**: `client_credentials` 는 client_secret(정적 비밀)을 요구한다. 토큰은 단기지만 secret 자체는 정적 → **완전 keyless 아님**.
  - **완전 keyless(mTLS / SPIFFE)는 후속 ADR 로 분리**한다. AWS IAM Roles Anywhere 급 X.509 워크로드 신원은 docker-compose 포트폴리오 단계에선 비용 대비 효과가 낮고, 본 ADR 의 옵션 A 가 ④의 실질을 이미 충족하므로 단계적 진화로 둔다. (mTLS 전환 시 본 ADR 의 JWT 검증 경로는 그대로 두고 전송 계층만 교체 가능 — 상호 배타 아님)
- **cross-service 마이그레이션 비용**: GAP 내부 다수 서비스 + 10 계약 spec. 무중단 4단계 진행 필요.
- **GAP 가용성 의존 증가**: 서비스 간 호출 전 토큰 발급이 필요 → auth-service 장애 시 영향 확대. 토큰 캐시 + 만료 여유로 완화하되, GAP 자체는 SPOF 성격 강화.

### Neutral

- JWT 검증은 JWKS 로컬 캐시라 매 호출 네트워크 round-trip 없음(토큰 발급 시점에만).
- 토큰 캐시 TTL·갱신 정책은 단계 1에서 결정(access TTL 1800s 재사용 또는 서비스간 전용 단축 TTL).

---

## Implementation Roadmap

본 ADR 채택 후 단계별 task 로 구현한다. 무중단 순서(2→3→4)를 강제한다.

- **TASK-BE-317** (본 ADR 와 함께 발행): 단계 1+2 — GAP `client_credentials` 서비스 client 인프라 + 수신측 JWT 검증 추가(X-Internal-Token 병행). 기존 경로 회귀 0 보장.
- **TASK-BE-318** (후속): 단계 3 — 호출측 Bearer JWT 전환.
- **TASK-BE-319** (후속): 단계 4 — 정적 토큰/permitAll 제거 + 계약 spec 10개 갱신 + env 정리.

각 후속 task 는 선행 task main 머지 후 발행한다(병행 허용 → 전환 → 제거 순서 보장).

---

## References

- ADR-001 (OIDC Adoption) — SAS 도입, GAP = OIDC AS
- ADR-003 — `client_credentials (test-internal-client) → 200 PASS` 회귀 매트릭스(본 ADR 의 인프라 존재 입증)
- `InternalApiFilter` (`apps/account-service/.../infrastructure/config/InternalApiFilter.java`) — 현재 정적 토큰 검증
- `specs/contracts/http/internal/*.md` (10개) — 갱신 대상 내부 계약
- AWS IAM Roles / STS / Roles Anywhere, GCP Service Account / Workload Identity — 비교 모델
- RFC 6749 §4.4 (client_credentials grant)
