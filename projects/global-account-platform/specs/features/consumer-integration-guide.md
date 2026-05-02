# Feature: Consumer Integration Guide (OIDC)

> 본 문서는 [ADR-001](../../docs/adr/ADR-001-oidc-adoption.md) (D1=A, D4=D4-c) 의 결정에 따라
> GAP를 표준 OIDC IdP로 이용하는 신규 소비 서비스의 **단일 진입 통합 가이드**다.
> 구현 specs (TASK-BE-251 / TASK-BE-252 / TASK-BE-256) 와 동기 유지 의무를 가진다.
> ADR-001 또는 OIDC 컨트랙트가 변경되면 본 가이드도 같은 PR 안에서 갱신해야 한다.

---

## Purpose

GAP는 6개 도메인 (`fan-platform`, `wms`, 향후 `erp`/`scm`/`mes`/`fan-community`) 의 공유 인증 공급자로 동작하며,
신규 도메인이 합류할 때마다 다음 정보가 분산되어 있어 동일한 탐색 비용이 반복 발생한다:

- 테넌트 등록 절차 → [admin-api.md § Tenant Lifecycle](../contracts/http/admin-api.md#tenant-lifecycle-task-be-256)
- 사용자 onboarding → [account-internal-provisioning.md](../contracts/http/internal/account-internal-provisioning.md)
- OIDC discovery / JWKS → [auth-api.md § OAuth2 / OIDC Endpoints](../contracts/http/auth-api.md#oauth2--oidc-endpoints-standard-adr-001)
- Gateway 라우팅·헤더 전파 → [gateway-api.md](../contracts/http/gateway-api.md)
- 이벤트 구독 → [account-events.md](../contracts/events/account-events.md), [auth-events.md](../contracts/events/auth-events.md), [tenant-events.md](../contracts/events/tenant-events.md)
- 멀티테넌시 보안 규칙 → [multi-tenancy.md](multi-tenancy.md)

본 가이드는 위 정보를 **6 개 Phase + 운영 체크리스트 + 코드 예시** 로 단선화하여
신규 소비 서비스가 가이드 1편으로 합류 가능하게 한다.

---

## 적용 범위

본 가이드는 다음 두 가지 통합 패턴을 다룬다:

| 패턴 | 설명 | 대표 grant |
|---|---|---|
| 사용자 인증 위임 (User Delegation) | 최종 사용자가 GAP에서 로그인하고, 소비 서비스는 GAP가 발급한 access/id token 으로 사용자를 식별 | `authorization_code` + PKCE |
| Service-to-Service | 소비 서비스 백엔드가 다른 백엔드(GAP 또는 GAP 보호 영역) 호출 시 자기 자신을 인증 | `client_credentials` |

두 패턴은 같은 OIDC IdP를 공유하므로 client 등록·token 검증·이벤트 구독은 동일한 인프라를 재사용한다.
다만 token 종류 (id_token 유무) 와 발급 흐름 (redirect 유무) 만 달라진다.

### 본 가이드가 다루지 않는 것

- GAP 자체의 SAS 내부 구현 — [auth-service architecture](../services/auth-service/architecture.md)
- 외부 파트너 portal 또는 self-service client 등록 UI — 별도 후속
- Python / Go / Ruby 등 그 외 언어 예시 — 필요 시 후속에서 부록 추가
- 소비 서비스의 도메인 권한 매트릭스 — 해당 서비스가 자체 소유

---

## 사전 준비 체크리스트

Phase 1을 시작하기 전에 다음 항목을 확정해 둔다:

- [ ] 도메인 이름 후보 (slug). 정규식 `^[a-z][a-z0-9-]{1,31}$`. 예: `erp`, `scm`, `mes`. 예약어(`admin`, `internal`, `system`, `null`, `default`, `public`, `gap`, `auth`, `oauth`, `me`) 사용 불가.
- [ ] 테넌트 유형 — `B2C_CONSUMER` (외부 사용자 직접 가입) 또는 `B2B_ENTERPRISE` (admin provisioning 으로만 사용자 생성).
- [ ] redirect URI 목록 (사용자 인증 위임 시) — 정확히 일치하는 절대 URL. 와일드카드 미지원.
- [ ] 필요한 grant type 조합 — `authorization_code` (사용자 로그인), `refresh_token` (토큰 갱신), `client_credentials` (S2S).
- [ ] 필요한 scope — 표준 (`openid`, `profile`, `email`, `offline_access`) + 도메인 정의 scope (예: `wms.inventory.read`).
- [ ] 운영 환경별 `OIDC_ISSUER_URL` 값 (dev / stg / prod). gateway 경유 외부 URL이며 trailing slash 포함 여부까지 정확히 일치해야 한다.
- [ ] 이벤트 구독에 사용할 Kafka consumer group ID — 도메인 이름 + 서비스 이름 형태 권장 (`erp-account-sync`).

---

## Phase 1 — 테넌트 등록

### 흐름

운영자(`SUPER_ADMIN`) 가 admin-service의 테넌트 lifecycle API로 신규 테넌트를 등록한다.
self-service 등록 경로는 존재하지 않는다 ([multi-tenancy.md § Tenant Model](multi-tenancy.md#tenant-model)).

### 요청

```
POST /api/admin/tenants
Authorization: Bearer <operator-token>      # token_type=admin
X-Operator-Reason: Onboard ERP domain Q3 2026
Idempotency-Key: <UUID>                     # 권장
Content-Type: application/json

{
  "tenantId": "erp",
  "displayName": "Enterprise Resource Planning",
  "tenantType": "B2B_ENTERPRISE"
}
```

### 응답 (201)

```json
{
  "tenantId": "erp",
  "displayName": "Enterprise Resource Planning",
  "tenantType": "B2B_ENTERPRISE",
  "status": "ACTIVE",
  "createdAt": "2026-05-02T09:00:00Z",
  "updatedAt": "2026-05-02T09:00:00Z"
}
```

### Side Effects

- `admin_actions` 에 `action_code=TENANT_CREATE` 행 INSERT.
- Outbox `tenant.created` 이벤트 발행 — schemaVersion 1, 파티션 키는 `tenantId`.
- 신규 SUSPEND 시에는 `tenant.suspended`, 복귀 시 `tenant.reactivated` 이벤트가 발행된다 ([tenant-events.md](../contracts/events/tenant-events.md)).

### 에러

| Status | Code | 의미 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | tenantId 정규식 위반, displayName 길이 위반 |
| 400 | `TENANT_ID_RESERVED` | 예약어 사용 |
| 403 | `PERMISSION_DENIED` | `tenant.manage` 권한 없음 |
| 403 | `TENANT_SCOPE_DENIED` | 비 SUPER_ADMIN 호출 |
| 409 | `TENANT_ALREADY_EXISTS` | 동일 tenantId 존재 |

### tenantId 규칙 요약

- 슬러그: `^[a-z][a-z0-9-]{1,31}$`
- **불변** — 한 번 발급되면 변경·재할당·회수 불가 ([multi-tenancy.md § Tenant Model](multi-tenancy.md#tenantid)).
- JWT claim, internal API path, audit log, downstream HTTP `X-Tenant-Id` 헤더로 전 영역에 노출된다.

---

## Phase 2 — OAuth Client 등록

### 흐름

테넌트 등록이 완료되면 운영자가 admin-service의 OAuth client API로 client 자격을 발급한다.
client 는 항상 **테넌트 단위로 등록** 되며, `oauth_clients.tenant_id` 가 NOT NULL 이다.

> 본 엔드포인트(`POST /api/admin/oauth-clients`) 의 정식 contract 는 **TASK-BE-252 후속에서 확정** 된다.
> 본 가이드는 ADR-001 § 3 Option A 가 명시한 데이터 모델을 기준으로 한 **목표 상태** 사양이며,
> 252 contract PR 머지 시 본 섹션이 1:1 cross-check 후 동기화된다.

### 요청 (목표 상태)

```
POST /api/admin/oauth-clients
Authorization: Bearer <operator-token>
X-Operator-Reason: Issue ERP backend client
Content-Type: application/json

{
  "tenantId": "erp",
  "clientName": "ERP Backend",
  "clientType": "confidential",                      # confidential | public
  "redirectUris": [
    "https://erp.example.com/oauth/callback"
  ],
  "allowedGrants": ["authorization_code", "refresh_token", "client_credentials"],
  "allowedScopes": ["openid", "profile", "email", "offline_access", "erp.read"],
  "tokenEndpointAuthMethod": "client_secret_basic"   # client_secret_basic | none(public+PKCE)
}
```

### 응답 (201, 목표 상태)

```json
{
  "clientId": "erp-backend-01HS3K5TM9R2X5N8P0WJ4F8K2A",
  "clientSecret": "<plaintext, 단 1회 노출>",
  "tenantId": "erp",
  "redirectUris": ["https://erp.example.com/oauth/callback"],
  "allowedGrants": ["authorization_code", "refresh_token", "client_credentials"],
  "allowedScopes": ["openid", "profile", "email", "offline_access", "erp.read"],
  "createdAt": "2026-05-02T09:05:00Z"
}
```

### 운영 결정 가이드

| 의사결정 | 권고 |
|---|---|
| confidential vs public | 백엔드가 secret 보관 가능하면 confidential. SPA / 모바일 native 등 secret 보관 불가 환경만 public + PKCE |
| redirect URI 등록 | 정확히 일치 비교 — 와일드카드 미지원. 운영 / 스테이징 / 로컬 개발용을 각각 등록 |
| allowedGrants | 사용자 인증만 필요하면 `authorization_code` + `refresh_token`. 백엔드 간 호출 추가 시 `client_credentials` 추가 |
| 토큰 endpoint 인증 | confidential → `client_secret_basic`, public PKCE → `none` |
| client_secret 보관 | 응답 본문의 평문은 1회만 노출. **secret store** (HashiCorp Vault, AWS Secrets Manager 등) 즉시 이관 |

### Side Effects

- `oauth_clients` 테이블에 `client_id`, `client_secret_hash` (BCrypt), `redirect_uris`, `allowed_grants`, `allowed_scopes`, `tenant_id` row 1건 INSERT.
- `admin_actions` 에 `OAUTH_CLIENT_CREATE` 감사 row 기록.
- secret 평문은 GAP DB에 저장하지 않는다 — hash 만 저장 ([ADR-001 § 7 Risks](../../docs/adr/ADR-001-oidc-adoption.md#7-risks--mitigations)).

---

## Phase 3 — 표준 OIDC Discovery + JWKS 설정

### Discovery 엔드포인트

GAP는 RFC 8414 OIDC Discovery 1.0 을 준수한다.

```
GET /.well-known/openid-configuration
```

응답에 광고되는 필드 ([auth-api.md § OIDC Endpoints](../contracts/http/auth-api.md)):

| 필드 | 값 |
|---|---|
| `issuer` | `OIDC_ISSUER_URL` 환경변수 (예: `https://gap.example.com`) |
| `authorization_endpoint` | `${issuer}/oauth2/authorize` |
| `token_endpoint` | `${issuer}/oauth2/token` |
| `jwks_uri` | `${issuer}/oauth2/jwks` |
| `userinfo_endpoint` | `${issuer}/oauth2/userinfo` |
| `revocation_endpoint` | `${issuer}/oauth2/revoke` |
| `introspection_endpoint` | `${issuer}/oauth2/introspect` |
| `response_types_supported` | `["code"]` |
| `grant_types_supported` | `["authorization_code", "client_credentials", "refresh_token"]` |
| `id_token_signing_alg_values_supported` | `["RS256"]` |
| `code_challenge_methods_supported` | `["S256"]` |
| `token_endpoint_auth_methods_supported` | `["client_secret_basic", "none"]` |

### Spring Security 설정 (Resource Server)

소비 서비스가 GAP가 발급한 access token을 검증하는 모드. Spring Security는 issuer-uri 한 줄 설정만으로
discovery 문서를 자동 fetch 하고 JWKS URI를 캐시한다.

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URL}        # 예: https://gap.example.com
```

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
}
```

### JWKS 캐싱 동작

- Spring Security 의 `NimbusJwtDecoder` 는 첫 요청 시 `jwks_uri` 를 fetch 하여 in-memory 캐싱.
- `Cache-Control: max-age` 응답 헤더 또는 기본 5분 TTL 후 재fetch.
- `kid` mismatch 발생 시 즉시 재fetch — 키 회전 시 무중단 ([gateway-to-auth.md](../contracts/http/internal/gateway-to-auth.md)).
- 운영상 권장: `OIDC_ISSUER_URL` 은 gateway-facing 외부 URL이어야 한다. 컨테이너 내부 DNS 명을 그대로 사용하면 `iss` claim 비교에서 mismatch 발생.

### 검증 시 강제 항목

소비 서비스가 access token 을 받은 후 **반드시** 검증해야 하는 항목:

1. RS256 서명 — Spring Security 가 자동 처리.
2. `iss` claim == `OIDC_ISSUER_URL` — Spring Security 가 자동 처리.
3. `exp` 미만료 — 자동 처리.
4. `tenant_id` claim 존재 — 누락 시 거부 (defense-in-depth, 본 가이드 § Phase 6 운영 체크리스트 참조).
5. (선택) `tenant_id` == 자기 도메인 slug — cross-tenant 공격 차단.

---

## Phase 4 — Service-to-Service 인증 (`client_credentials`)

### 흐름

소비 서비스 백엔드가 다른 백엔드(GAP 보호 영역 또는 동일 GAP를 공유하는 다른 도메인) 를 호출할 때
자기 자신을 GAP에 등록된 client로 인증한다. 사용자 컨텍스트가 없는 백그라운드 작업에 적합.

```
POST /oauth2/token
Authorization: Basic <base64(clientId:clientSecret)>
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=erp.read
```

응답 (200):

```json
{
  "access_token": "<JWT>",
  "token_type": "Bearer",
  "expires_in": 1800,
  "scope": "erp.read"
}
```

> `client_credentials` 응답은 `refresh_token` 을 반환하지 않는다. 만료 시 client 가 재요청.
> `tenant_id` claim 은 client 등록 시의 테넌트로 자동 채워진다.

### Spring Boot 예시 — `WebClient` 자동 인증 필터

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          gap-erp:
            provider: gap
            client-id: ${GAP_CLIENT_ID}
            client-secret: ${GAP_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: erp.read
        provider:
          gap:
            issuer-uri: ${OIDC_ISSUER_URL}      # discovery 자동 사용
```

```java
@Configuration
public class WebClientConfig {

    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository registrations,
            ReactiveOAuth2AuthorizedClientService clientService) {

        var provider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                registrations, clientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    @Bean
    public WebClient gapWebClient(ReactiveOAuth2AuthorizedClientManager manager) {
        var oauth2 = new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        oauth2.setDefaultClientRegistrationId("gap-erp");
        return WebClient.builder()
                .filter(oauth2)
                .build();
    }
}
```

호출 시 별도 `Authorization` 헤더 작성 없이 필터가 token 을 자동으로 발급·재사용한다:

```java
@Service
public class InventoryClient {

    private final WebClient gapWebClient;

    public Mono<InventorySnapshot> fetch(String tenantId, String sku) {
        return gapWebClient.get()
                .uri("https://wms.example.com/api/inventory/{sku}", sku)
                .header("X-Tenant-Id", tenantId)         // gateway 가 JWT claim 으로 다시 덮어씀
                .retrieve()
                .bodyToMono(InventorySnapshot.class);
    }
}
```

### Node.js 예시 — `openid-client`

best-effort 수준의 표준 라이브러리 사용 예시.

```bash
npm install openid-client
```

```js
// gapClient.js
const { Issuer } = require('openid-client');

let cached;

async function getGapTokens() {
  if (cached && cached.expiresAt > Date.now() + 30_000) {
    return cached.token;
  }
  const issuer = await Issuer.discover(process.env.OIDC_ISSUER_URL);
  const client = new issuer.Client({
    client_id: process.env.GAP_CLIENT_ID,
    client_secret: process.env.GAP_CLIENT_SECRET,
    token_endpoint_auth_method: 'client_secret_basic',
  });
  const token = await client.grant({
    grant_type: 'client_credentials',
    scope: 'erp.read',
  });
  cached = {
    token,
    expiresAt: Date.now() + token.expires_in * 1000,
  };
  return token;
}

module.exports = { getGapTokens };
```

```js
// callWms.js
const fetch = require('node-fetch');
const { getGapTokens } = require('./gapClient');

async function fetchInventory(sku) {
  const token = await getGapTokens();
  const res = await fetch(`https://wms.example.com/api/inventory/${sku}`, {
    headers: {
      Authorization: `Bearer ${token.access_token}`,
      'X-Tenant-Id': 'erp',
    },
  });
  if (!res.ok) throw new Error(`WMS call failed: ${res.status}`);
  return res.json();
}
```

### S2S 토큰 운영 규칙

- 만료 30초 전부터 미리 갱신 (premature refresh) — race condition 방지.
- 401 응답 수신 시 캐시 무효화 후 1회 재발급 시도.
- token 자체를 로그에 출력 금지. `jti` 또는 hash 만 기록.
- 동일 client 가 여러 인스턴스로 수평 확장된 경우, 각 인스턴스가 독립적으로 token 캐시.

---

## Phase 5 — 이벤트 구독 (사용자 라이프사이클)

### 구독해야 할 4개 이벤트

소비 서비스가 GAP 사용자 캐시·downstream 처리를 일관되게 유지하려면 다음 4개 토픽을 구독한다.
모든 페이로드에 `tenant_id` 필드가 포함되며, 소비자는 자기 테넌트의 이벤트만 처리한다.

| 토픽 | 트리거 | 소비 의무 |
|---|---|---|
| `account.created` | 신규 계정 생성 | 도메인 user 캐시에 신규 row insert (또는 just-in-time 으로 미리 비워두기) |
| `account.locked` | 계정 LOCKED 전이 | 도메인 user 캐시 상태 갱신 + 활성 세션·작업 큐에서 사용자 제외 |
| `account.deleted` | 삭제 (유예 진입 또는 anonymized) | 도메인 user 캐시 soft-delete + GDPR downstream 처리 (§ 아래 "GDPR downstream") |
| `account.status.changed` | 모든 상태 전이 | 일반 fallback. `account.locked` 전용 컨슈머가 없으면 이 토픽으로 통합 처리 |

> account.locked / account.unlocked / account.status.changed 는 동시 발행 — 소비자는 idempotent 처리 (eventId dedupe) 로 1회만 처리.

### 페이로드 형태 (요약)

자세한 schema는 [account-events.md](../contracts/events/account-events.md) 참조.

```json
{
  "eventId": "01HS3K5TM9R2X5N8P0WJ4F8K2A",
  "eventType": "account.deleted",
  "source": "account-service",
  "occurredAt": "2026-05-02T10:00:00Z",
  "schemaVersion": 2,
  "partitionKey": "01923abc-def0-7890-abcd-ef0123456789",
  "payload": {
    "accountId": "01923abc-def0-7890-abcd-ef0123456789",
    "tenantId": "erp",
    "reasonCode": "USER_REQUEST",
    "actorType": "user",
    "actorId": "01923abc-def0-7890-abcd-ef0123456789",
    "deletedAt": "2026-05-02T10:00:00Z",
    "gracePeriodEndsAt": "2026-06-01T10:00:00Z",
    "anonymized": false
  }
}
```

### 캐시 무효화 패턴

```java
@KafkaListener(topics = "account.locked", groupId = "erp-account-sync")
public void onAccountLocked(ConsumerRecord<String, AccountEvent> rec) {
    AccountEvent ev = rec.value();
    if (!"erp".equals(ev.payload().tenantId())) {
        return;                                 // 다른 테넌트는 무시 (단일 클러스터 공유 시)
    }
    if (!eventDedupe.markProcessed(ev.eventId())) {
        return;                                 // 멱등 — 이미 처리된 eventId
    }
    userCache.invalidate(ev.payload().accountId());
    sessionRegistry.evictBy(ev.payload().accountId());
}
```

### GDPR downstream 처리 (`account.deleted`)

`account.deleted` 이벤트는 두 단계로 발행될 수 있다:

1. **유예 진입** (`anonymized: false`, `gracePeriodEndsAt` 설정) — 도메인 데이터는 즉시 logical delete. 사용자 검색·로그인 차단.
2. **유예 종료 후 익명화** (`anonymized: true`) — 도메인이 보유한 PII (이메일·이름 등) 도 마스킹·삭제. audit / billing / 통계 row의 외래키만 남기고 모든 식별자 컬럼 NULL 처리.

```java
@KafkaListener(topics = "account.deleted", groupId = "erp-account-sync")
public void onAccountDeleted(ConsumerRecord<String, AccountEvent> rec) {
    AccountDeletedPayload p = rec.value().payload();
    if (!"erp".equals(p.tenantId())) return;
    if (Boolean.TRUE.equals(p.anonymized())) {
        userPiiAnonymizer.anonymize(p.accountId());     // 2단계: PII 마스킹
    } else {
        userCache.softDelete(p.accountId());            // 1단계: logical delete
        domainAccessRevoker.revokeAll(p.accountId());
    }
}
```

세부 GDPR 정책은 [data-rights.md](data-rights.md) 참조.

> **TASK-BE-258 — 소비자 의무 계약**: GAP 내부 소비자(security-service, community-service, membership-service, admin-service) 각각의 마스킹 SLA와 실패 처리 의무는 [account-events.md § Consumer Obligations](../contracts/events/account-events.md#consumer-obligations-task-be-258) 에 표 형식으로 정의되어 있다. 외부 소비자(WMS 등) 도 해당 표의 "External 소비자 가이드" 절을 준수해야 한다.
>
> security-service의 reference 구현(`AccountDeletedAnonymizedConsumer`)이 마스킹 완료 후 발행하는 `security.pii.masked` audit 이벤트 스펙: [security-events.md § security.pii.masked](../contracts/events/security-events.md#securitypiimasked-task-be-258).

### 컨슈머 규칙 요약

- **멱등 처리 필수** — `eventId` (UUID v7) 기반 dedupe. Redis + DB 이중 방어 권장.
- **`tenant_id` 필터링** — 단일 Kafka 클러스터를 다수 테넌트가 공유하므로 자기 테넌트 외 이벤트는 skip.
- **schema tolerance** — 알 수 없는 필드는 무시 (forward-compatible). `schemaVersion` 미지원 시 DLQ.
- **DLQ** — `<topic>.dlq`. 3회 지수 백오프 재시도 후 이관.
- **순서** — `accountId` 단일 파티션 키. 같은 사용자의 이벤트는 발행 순서대로 도착하지만 다른 사용자 사이의 순서는 보장 없음.
- **trace propagation** — Kafka 헤더의 `traceparent` 를 MDC 로 복원.

---

## Phase 6 — 운영 체크리스트

### `tenant_id` claim 검증 (필수)

| 위치 | 검사 | 실패 시 |
|---|---|---|
| Resource server 필터 | `tenant_id` claim 존재 | 401 — token 거부 |
| Resource server 필터 | `tenant_id` == 자기 도메인 slug | 403 `TENANT_SCOPE_DENIED` (cross-tenant 공격 차단) |
| Domain repository | `WHERE tenant_id = ?` 명시 | leak 발생 시 즉시 보안 사고 |
| 이벤트 컨슈머 | payload `tenant_id` == 자기 슬러그 | skip (단일 클러스터 공유) |

Spring Security 에서 `tenant_id` 검증을 강제하는 최소 코드:

```java
@Component
public class TenantIdJwtValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedTenantId;

    public TenantIdJwtValidator(@Value("${app.expected-tenant-id}") String expectedTenantId) {
        this.expectedTenantId = expectedTenantId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || !tenantId.equals(expectedTenantId)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "tenant_scope_denied",
                    "tenant_id claim missing or mismatched",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
```

```java
@Configuration
public class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(
            OAuth2ResourceServerProperties props,
            TenantIdJwtValidator tenantValidator) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(
                props.getJwt().getIssuerUri());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.getJwt().getIssuerUri()),
                tenantValidator));
        return decoder;
    }
}
```

### Cross-Tenant 거부

- gateway 가 `/internal/tenants/{tenantId}/**` 의 path 와 JWT `tenant_id` 불일치 시 403 `TENANT_SCOPE_DENIED` 를 반환한다 ([gateway-api.md § Internal Provisioning Routes](../contracts/http/gateway-api.md#internal-provisioning-routes)).
- 다운스트림 서비스는 동일 검사를 다시 수행한다 (defense-in-depth).
- `X-Tenant-Id` 헤더는 외부 입력값을 신뢰하지 않는다 — gateway 가 JWT claim 기반으로 덮어쓴다 ([gateway-api.md § Request Headers](../contracts/http/gateway-api.md)).

### JWKS endpoint 가용성 모니터링

- discovery 캐시는 in-memory 이므로 단발 장애에는 강건하지만, 콜드 스타트 직후 GAP 가 다운된 상태이면 모든 검증이 실패.
- 운영 모니터링 항목:
  - `${OIDC_ISSUER_URL}/.well-known/openid-configuration` 200 응답 (1분 간격)
  - `${OIDC_ISSUER_URL}/oauth2/jwks` 200 응답 + `keys` 배열 비어있지 않음
  - 키 회전 시 `kid` 변경 후 1분 이내 신규 token 검증 성공률 추적
- circuit breaker 권장 — JWKS 미가용 시 신규 token 검증을 일시 차단 (Spring Resilience4j).

### 토큰 만료 / 갱신 동작

- access token TTL: **30분** (`expires_in: 1800`).
- refresh token TTL: **7일** (`authorization_code` / `refresh_token` grant 만 발급).
- `refresh_token` 사용 시 rotation — 기존 refresh token은 즉시 무효, 새 pair 발급 ([authentication.md § Token Rotation](authentication.md#토큰-갱신-refresh-token-rotation)).
- 재사용 탐지 시 해당 account 의 **모든 세션 즉시 revoke** + `auth.token.reuse.detected` 이벤트 발행 (zero tolerance).
- `client_credentials` 응답에는 refresh token 이 없다 — 만료 시 client 가 재요청.

### Deprecation 주의

- `POST /api/auth/login` 은 ADR-001 D2-b 단계 폐기 대상 (2026-08-01 제거 목표). 신규 통합은 사용 금지.
- 응답 헤더 `Deprecation: true`, `Sunset: Sun, 01 Aug 2026 00:00:00 GMT` 로 통지된다.
- 신규 클라이언트는 처음부터 `POST /oauth2/token` 사용.

### 운영 체크리스트 (deployment 직전)

- [ ] `OIDC_ISSUER_URL` 환경별로 정확히 설정 (dev / stg / prod).
- [ ] `tenant_id` claim 검증 필터 활성화.
- [ ] cross-tenant 회귀 테스트 1건 이상 (`다른 tenant_id 토큰으로 자기 도메인 API 호출 → 403`).
- [ ] JWKS endpoint health check 운영 모니터링 등록.
- [ ] client_secret 시크릿 스토어 보관 (DB 평문 금지).
- [ ] Kafka consumer group ID 사전 등록 + lag 모니터링 알람.
- [ ] DLQ 토픽 `<topic>.dlq` 처리 절차 문서화.
- [ ] 4개 이벤트 토픽 구독 — `account.created`, `account.locked`, `account.deleted`, `account.status.changed` (또는 통합 `account.status.changed` 만).
- [ ] `account.deleted` anonymized=true 처리 경로 검증 (GDPR).
- [ ] 운영 사고 대응 대비: GAP 장애 시 fail-closed 기본값 (캐시된 사용자 정보로 read-only 모드만 허용).

---

## 통합 시뮬레이션 — `erp` 도메인 합류 예시

본 가이드 단일 문서로 신규 도메인이 합류 가능한지 검증하기 위한 표.

| 단계 | 액션 | 본 가이드 위치 |
|---|---|---|
| 1 | tenantId, tenantType 결정 | § 사전 준비 체크리스트 |
| 2 | `POST /api/admin/tenants` 호출 (운영자) | § Phase 1 |
| 3 | `POST /api/admin/oauth-clients` 호출 — backend client 발급 | § Phase 2 |
| 4 | client_secret 을 secret store 에 보관 | § Phase 2 운영 결정 가이드 |
| 5 | `OIDC_ISSUER_URL` 환경변수 설정 | § Phase 3 |
| 6 | `spring-boot-starter-oauth2-resource-server` 의존성 추가 + `issuer-uri` 설정 | § Phase 3 |
| 7 | `TenantIdJwtValidator` 추가하여 cross-tenant 방어 | § Phase 6 운영 체크리스트 |
| 8 | S2S 호출 필요 시 `client_credentials` + `WebClient` 필터 구성 | § Phase 4 |
| 9 | Kafka consumer 4개 토픽 구독 | § Phase 5 |
| 10 | account 신규 생성 (admin provisioning, B2B 인 경우) | [account-internal-provisioning.md](../contracts/http/internal/account-internal-provisioning.md) |
| 11 | 사용자가 `/oauth2/authorize` 로 redirect → 로그인 → callback | § Phase 4 흐름 + [auth-api.md § OIDC Endpoints](../contracts/http/auth-api.md) |
| 12 | 운영 모니터링 활성화 (JWKS health, lag, DLQ) | § Phase 6 운영 체크리스트 |

가이드 외부 spec 참조 횟수: 4회 ([admin-api.md](../contracts/http/admin-api.md), [auth-api.md](../contracts/http/auth-api.md), [gateway-api.md](../contracts/http/gateway-api.md), [account-internal-provisioning.md](../contracts/http/internal/account-internal-provisioning.md)) — 가이드 acceptance criteria "5회 이하" 만족.

---

## Edge Cases

- **`tenant_id` claim 누락 토큰** — gateway grace period 가 활성화되어 있으면 `fan-platform` 으로 fallback. 신규 소비자는 **fallback 비활성** 가정으로 구현 (누락=거부).
- **redirect URI 정확 일치 실패** — 와일드카드·trailing slash·port 차이 모두 거부. 환경별로 등록 필요.
- **토큰 발급 직후 키 회전** — JWKS 캐시가 회전 전 key 만 캐시한 상태에서 새 token 도착하면 `kid` mismatch → 재fetch. 30초 이내 자동 복구.
- **이벤트 중복 수신** — Kafka at-least-once. `eventId` dedupe 미적용 시 중복 처리 발생.
- **다른 테넌트의 account.deleted 수신** — 단일 클러스터 공유 시 발생. `tenantId` 필터 누락 시 자기 도메인의 동일 accountId 와 충돌 가능 (정확히 같은 UUID 가 다른 테넌트에 존재할 가능성은 낮지만 0 은 아니다).
- **client_secret 분실** — admin-service 에서 client 재발급 (rotate). 평문은 1회만 노출.
- **`POST /api/auth/login` legacy 응답 처리** — sunset 까지는 동작하지만 신규 통합은 표준 OIDC 만 사용.

---

## Failure Scenarios

| 시나리오 | 증상 | 권장 대응 |
|---|---|---|
| GAP discovery endpoint 일시 장애 | 콜드 스타트 시 검증 실패 | retry + circuit breaker. 기존 검증 캐시는 단기간 유지 |
| JWKS 키 회전 직후 검증 실패 burst | 30초 내외 401 spike | NimbusJwtDecoder 자동 재fetch — 별도 조치 불필요. 알림만 등록 |
| Kafka consumer lag 폭증 | account 캐시 stale | lag 임계치 알람 + 일시적으로 pull-through (직접 GAP API 조회) fallback |
| DLQ 누적 | schema 변경 또는 미지원 eventType | DLQ 메시지 inspect → 신규 schemaVersion 지원 후 재처리 |
| client_credentials token 발급 실패 | S2S 호출 401 burst | 캐시 무효화 + 1회 재발급 시도. 반복 실패 시 client 등록 상태 점검 |
| cross-tenant token 사용 시도 (의도적 공격) | 403 `TENANT_SCOPE_DENIED` 반복 | 보안 이벤트 발행 + IP 차단 (rate limit) |

---

## Related Specs

본 가이드는 다음 specs 의 정보를 단선화한다 — 각 항목의 정식 source 는 해당 문서:

- [ADR-001 — OIDC Authorization Server 채택](../../docs/adr/ADR-001-oidc-adoption.md) (특히 § 5 D4-c)
- [multi-tenancy.md](multi-tenancy.md) — 테넌트 모델, 격리 전략, cross-tenant 보안 규칙
- [authentication.md](authentication.md) — 사용자 인증 흐름, refresh rotation, token 사양
- [data-rights.md](data-rights.md) — GDPR 삭제 / 익명화 정책 (downstream 처리 의무)
- [auth-api.md § OAuth2 / OIDC Endpoints](../contracts/http/auth-api.md#oauth2--oidc-endpoints-standard-adr-001) — discovery, JWKS, token, userinfo, revoke, introspect
- [admin-api.md § Tenant Lifecycle](../contracts/http/admin-api.md#tenant-lifecycle-task-be-256) — `POST /api/admin/tenants` 및 lifecycle 4종
- [gateway-api.md](../contracts/http/gateway-api.md) — 라우팅, `X-Tenant-Id` 헤더 전파, Internal Provisioning Routes
- [account-internal-provisioning.md](../contracts/http/internal/account-internal-provisioning.md) — B2B 사용자 생성·관리
- [account-events.md](../contracts/events/account-events.md) — 4개 lifecycle 이벤트 schema
- [auth-events.md](../contracts/events/auth-events.md) — 인증 이벤트 schema, IP 마스킹 표준
- [tenant-events.md](../contracts/events/tenant-events.md) — `tenant.created` / `tenant.suspended` / `tenant.reactivated` / `tenant.updated`

---

## Change Policy

본 가이드는 **spec 의 일부** 다. 다음 변경 발생 시 같은 PR 안에서 본 가이드를 동기 갱신해야 한다:

- ADR-001 의 결정 항목 (D1~D5) 변경
- `auth-api.md` 의 OAuth2 / OIDC 엔드포인트 형태 또는 grant type 추가·제거
- `admin-api.md` 의 tenant lifecycle / oauth-clients 엔드포인트 변경
- `account-events.md` / `auth-events.md` / `tenant-events.md` 의 schemaVersion bump
- `multi-tenancy.md` 의 격리 전략 또는 `tenant_id` claim 형식 변경
- gateway 의 `X-Tenant-Id` 전파 규칙 변경

drift 가 발생한 경우 본 가이드를 따르는 신규 소비자가 잘못된 구현을 채택할 위험이 있으므로
contract change PR review 시 reviewer 는 본 가이드 동기 갱신 여부를 명시적으로 확인한다.
