-- TASK-MONO-717 (소유자 결정 ⓐ) — product-service 의 워크로드 클라이언트를 등록한다.
--
-- 왜 다섯 번째인가 (V0019 가 「왜 이 넷인가」를 열거했으므로 같은 자리를 채운다)
-- ------------------------------------------------------------------------------
--   product-service-client — ecommerce product-service → account /internal/**
--
-- ADR-MONO-042 (D2/D4/D5) 가 셀러 등록 시 셀러-운영자 계정을 account-service 의
-- /internal/** 로 프로비저닝하도록 정했고, product-service 는 그 호출을
-- `AccountServiceSellerProvisioner` 로 구현하면서 client_credentials 자격을
-- `IAM_CLIENT_ID`(기본값 `product-service-client`)로 **가정**했다. 그런데 그 client_id 는
-- 어느 시드에도 등록된 적이 없다.
--
-- 🔴🔴 **이 줄에 `$`+`{…}` 플레이스홀더 문법을 쓰지 마라 — 주석 안이어도 안 된다.**
--    Flyway 는 마이그레이션 텍스트 **전체**에 치환을 돌리고, 주석을 예외로 두지 않는다.
--    첫 판이 그 문법으로 설정 이름을 인용했다가 CI 가 이렇게 죽었다(2026-09-23 실측):
--      Unable to parse statement in V0036__… at line 4 col 1.
--      No value provided for placeholder: <그 이름>
--    ⇒ **설명 문구가 런타임을 깨뜨렸다.** iam 의 모든 통합 테스트가 컨텍스트 로드에서
--    실패했고(auth-service 의 Flyway 가 부팅 때 돈다) 증상은 이 파일을 가리켰지만 원인은
--    SQL 이 아니라 **주석**이었다. 🔵 형제 V0019 에는 이 문법이 0건이다.
--
-- 🔴 이것은 추론이 아니라 실측이다 (TASK-MONO-672 § 14차 창 · TASK-MONO-717 § AC-0):
--
--   {"level":"WARN","logger":"…AccountServiceSellerProvisioner",
--    "message":"seller provisioning failed (fail-soft, seller stays PENDING)
--               tenant=ecommerce seller=demo-seller: I/O error on POST request for
--               \"http://localhost:8081/oauth2/token\": Connection refused"}
--
-- 주소가 안 설정돼 `localhost` 로 떨어진 것이 먼저 보였지만, 주소를 고쳐도 다음은
-- `invalid_client` 였을 것이다 — **호출자 자격이 만들어진 적이 없기 때문**이다.
-- ⇒ 이 프로비저닝은 이 저장소에서 **한 번도 동작한 적이 없다**. 데모 배선의 공백이
--   아니라 등록의 공백이고, 그래서 고침이 compose 가 아니라 여기에서 시작한다.
--
-- 스코프
-- ------------------------------------------------------------------------------
-- `internal.invoke` 하나. 🔴 이것은 **장식이 아니라 관문**이다 — account-service 의
-- `SecurityConfig` 가 `internal.api.jwt.required-scope`(기본 `internal.invoke`)를
-- `internalTokenValidator()` 에 핀으로 박는다(TASK-BE-514). V0019 헤더가 적은
-- *"receiving side does not enforce it in TASK-BE-317"* 는 **낡았다**: 그 하드닝은
-- 이미 출하됐다. 서명+issuer 만으로는 시스템 토큰과 사용자 토큰이 안 갈린다.
--
-- 역할
-- ------------------------------------------------------------------------------
-- 없다. `WorkloadRoleCatalog` 에 **빈 맵으로 명시**한다 — 그 파일의 규약대로
-- 「빈 맵 = 재 봤고 답은 없음」이고 「부재 = 아무도 안 봄」이다. /internal/** 는
-- 스코프로 막히지 역할로 막히지 않으므로, 역할을 주면 이 자격이 도메인 표면으로
-- 넓어진다. ⇒ ADR-MONO-061 / ADR-MONO-063 은 **건드리지 않는다**.
--
-- 테넌트
-- ------------------------------------------------------------------------------
-- V0019 와 같다: `global-account-platform` / `INTERNAL`. 수신 resource server 는
-- 서명+issuer(+스코프)만 검증하고 테넌트를 핀하지 않으므로 이 클레임은 정보성이다.
-- `TenantClaimTokenCustomizer` 가 발급 시 비어 있지 않은 값을 요구하므로 이 둘이
-- 그 fail-closed 가드를 만족한다.
-- 🔵 `ecommerce` 가 아니라 `global-account-platform` 인 이유: 이 자격은 **어느 한
--    테넌트의 것이 아니다** — 셀러는 어느 테넌트에서도 등록될 수 있고, 호출은
--    `/internal/tenants/{tenantId}/…` 로 대상 테넌트를 **경로에** 싣는다.
--
-- 비밀
-- ------------------------------------------------------------------------------
-- client_secret_hash = BCrypt(strength=10) of the literal "secret" — V0008/V0009/V0019 가
-- 공유하고 `BcryptHashPinTest` 가 핀하는 그 해시. Mapper 가 "{bcrypt}" 를 앞에 붙인다.
-- 🔴 운영은 `PRODUCT_SERVICE_CLIENT_SECRET`(caller 측 `IAM_CLIENT_SECRET`)로 회전해야
--    한다 — V0019 가 같은 문장을 적었고 여기도 같다.
--
-- 멱등
-- ------------------------------------------------------------------------------
-- client_id 는 UNIQUE(uk_oauth_clients_client_id). `product-service-client` 는 기존
-- 시드(test-internal-client / *-service-client / *-internal-services-client)와 충돌하지 않는다.

INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'product-service-client-id',
    'product-service-client',
    'global-account-platform',
    'INTERNAL',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Product Service Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["internal.invoke"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);
