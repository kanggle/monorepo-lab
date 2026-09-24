-- TASK-MONO-726 (소유자 승인 2026-09-24) — ecommerce batch-worker 의 워크로드 클라이언트를 등록한다.
--
-- 누가 부르나
-- ------------------------------------------------------------------------------
--   ecommerce-internal-services-client — ecommerce batch-worker → order-service /api/internal/orders/**
--
-- batch-worker 의 두 잡(StalePaidOrderConfirmationJob · OrphanCouponReleaseJob)이
-- `POST /api/internal/orders/confirm-paid-stale` · `POST /api/internal/orders/existence` 를
-- 이 client_id 의 client_credentials 토큰으로 부른다(`IamClientCredentialsTokenProvider`,
-- 설정 키 iam.internal-client.client-id 의 기본값).
-- 받는 쪽 order-service `OrderSecurityConfig` 는 `/api/internal/**` 에서 토큰의 sub 를
-- **정확히 이 client_id 하나**로 핀한다(TASK-BE-505, order.internal.oauth2.allowed-client-ids 기본값).
-- ⇒ 두 쪽 코드가 이미 이 이름을 가정했는데 **IdP 에는 한 번도 등록된 적이 없다** —
--   V0036 이 product-service-client 에서 찾은 것과 같은 모양의 공백이다.
--
-- 🔴🔴 **이 파일에 `$`+`{…}` 플레이스홀더 문법을 쓰지 마라 — 주석 안이어도 안 된다.**
--    Flyway 는 마이그레이션 텍스트 전체를 치환하고 주석을 예외로 두지 않는다(V0036 헤더의 사고).
--
-- 스코프
-- ------------------------------------------------------------------------------
-- `internal.invoke` 하나 — V0036 과 같다. 🔵 order-service 의 /api/internal/** 체인은 스코프를
-- 검사하지 않는다(서명 · 시각 · issuer · sub 허용목록). 그래도 등록하는 이유: 이 자격이
-- iam 의 /internal/** 을 부르게 되는 날 account-service 가 요구하는 관문이 그것이고, 빈 스코프
-- 목록은 «재 봤고 답은 없음» 이 아니라 «아무도 안 봄» 으로 읽힌다. batch-worker 는 지금 scope
-- 없이 요청하므로(토큰의 scope 클레임은 비어서 나온다 — 717 CORRECTION 의 실측) 동작에 영향 없다.
--
-- 역할
-- ------------------------------------------------------------------------------
-- 없다. `WorkloadRoleCatalog` 에 빈 맵으로 명시한다 — 이 경로는 sub 로 막히지 역할로 막히지
-- 않는다. ADR-MONO-061 / 063 은 건드리지 않는다.
--
-- 테넌트
-- ------------------------------------------------------------------------------
-- V0019 · V0036 과 같다: `global-account-platform` / `INTERNAL`. 🔵 이 경로에는 테넌트 핀이
-- **없다** — order-service 의 내부 체인은 테넌트 클레임을 읽지 않고, 두 잡은 테넌트를 가로지르는
-- 스윕이다. 그래서 ADR-MONO-076 의 워크로드 테넌트 assume(토큰 교환)도 이 client 에는 주지 않는다
-- (grant 는 client_credentials 하나).
--
-- 비밀
-- ------------------------------------------------------------------------------
-- client_secret_hash = BCrypt(strength=10) of the literal "secret" — V0008/V0009/V0019/V0036 이
-- 공유하고 `BcryptHashPinTest` 가 핀하는 그 해시. 🔴 운영은 caller 측 `IAM_CLIENT_SECRET` 로
-- 회전해야 한다 — V0019 · V0036 이 같은 문장을 적었다.
--
-- 멱등
-- ------------------------------------------------------------------------------
-- client_id 는 UNIQUE(uk_oauth_clients_client_id). 기존 시드 어디에도 이 client_id 는 없다
-- (auth-service db 전수 grep, 2026-09-24).

INSERT INTO oauth_clients (
    id, client_id, tenant_id, tenant_type, client_secret_hash, client_name,
    client_authentication_methods, authorization_grant_types, redirect_uris, scopes,
    client_settings, token_settings, created_at, updated_at
) VALUES (
    'ecommerce-internal-services-client-id',
    'ecommerce-internal-services-client',
    'global-account-platform',
    'INTERNAL',
    '$2a$10$0r6LHGsIgq6d5fkXCHwqQOHcuCA6ds8c8o9bSa25ucakM13V6VpsS',
    'Ecommerce Internal Services Workload Client',
    '["client_secret_basic"]',
    '["client_credentials"]',
    '[]',
    '["internal.invoke"]',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.x509-certificate-bound-access-tokens":false,"settings.token.access-token-time-to-live":["java.time.Duration",1800.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}',
    NOW(),
    NOW()
);
