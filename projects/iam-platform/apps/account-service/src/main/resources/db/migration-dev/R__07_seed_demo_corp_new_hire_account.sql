-- =============================================================================
-- REPEATABLE (R__) — see R__05's header for the full R__-vs-versioned rationale
-- (TASK-MONO-524/207/531); the short version: a repeatable carries no version,
-- so it can never collide with or out-of-order a production migration number.
-- =============================================================================
-- !!! DEV/DEMO ONLY — loaded via spring.flyway.locations ONLY under the `e2e`
-- profile (application-e2e.yml). application.yml pins production to
-- db/migration alone, so nothing here can reach a production account_db. !!!
--
-- TASK-MONO-766 — console `/operators` 생성 폼의 사전 게이트(`CreateOperatorUseCase`,
-- TASK-MONO-334)는 이메일이 **대상 테넌트에 이미 등록된 계정**이어야 운영자 생성을
-- 허용한다(`AccountServiceClient#searchSiteAccounts`, `excludePoolMembers=true` —
-- TASK-BE-615 AC-8: consumer-pool 멤버는 안 쳐준다). 실측(2026-10-06 UTC, 23차 창):
--
--     account_db.accounts  tenant_id='demo-corp'  count = 0
--
-- `demo@demo.com`/`requester@demo.com`(둘 다 demo-corp 운영자)은 `admin_db.
-- admin_operators` 에만 있고 `accounts` 행이 없다 — R__05 의 헤더가 적은 그대로,
-- **operator 평면은 별도 저장소**다(ADR-MONO-034 §1.1). 그래서 콘솔 방문자가
-- demo-corp 소속으로 "새" 운영자를 만들어 보일 입력 이메일 자체가 없었다.
--
-- WHY A DIRECT SEED AND NOT THE REAL SIGNUP/PROVISIONING API
-- ---------------------------------------------------------------------------
-- 둘 다 실측으로 막혀 있다(둘 다 재확인 가능):
--   · `POST /api/accounts/signup`(공개, gateway 라우트 확인됨 — `RouteConfig`
--     의 signup rate-limit 스코프) 는 `X-Tenant-Id` 를 **호출자가 보내면 게이트웨이가
--     지운다**(`JwtAuthenticationFilter` — "Always strip spoofed headers (including
--     X-Tenant-Id from external clients — except on the admin subtree)"). 그 헤더는
--     auth-service 가 **저장된 OIDC authorize 요청의 client_id** 로부터만 채워 넣는다
--     (`SavedRequestTenantResolver`) — demo-corp 에는 그런 공개 가입 클라이언트가
--     없다(B2B 엔터프라이즈 테넌트라 자체 가입 화면이 없다, AC-1).
--   · `POST /internal/tenants/{tenantId}/accounts`(바로 이 목적의 엔터프라이즈
--     프로비저닝 API, TASK-BE-231)는 계약서 1행이 명시한다: *"WMS backends call
--     account-service directly over an internal network path; the gateway does
--     not expose /internal/** to the public internet."* 즉 데모 시드가 도는 호스트
--     (Traefik 앞단)에서는 **라우트 자체가 없다** — 토큰이 있어도 404다.
-- 이 저장소의 `dbexec --why` 관례(`infra/demo/seed/lib.sh`)와 같은 성질의 상황이다:
-- 막힌 것이 "API 없음" 이 아니라 "이 경로에서 그 API 에 도달할 수 없음" 이고, 그 사유를
-- 재검증 가능한 형태로 남긴다. account-service/iam 쪽은 `infra/demo/seed/*.sh` 가
-- 아니라 이미 Flyway dev-seed(R__)로 demo-corp/demo 계정을 심고 있으므로(R__05)
-- 같은 메커니즘을 그대로 따른다 — 새 시드 스크립트를 만들지 않는다.
--
-- WHAT THIS SEEDS
-- ---------------------------------------------------------------------------
-- `demo-corp` 테넌트에 "운영자가 아직 아닌" 계정 1개: identity + account 만. 절대
-- `admin_operators`/`operator_tenant_assignment` 행을 만들지 않는다(Failure
-- Scenarios: 그러면 시연할 대상이 사라진다) · 역할을 미리 주지 않는다(콘솔의 생성
-- 흐름이 생성 시점에 역할을 부여하는 것이 시연 대상이다) · 기존 demo-corp 운영자
-- 행은 손대지 않는다(INSERT IGNORE 전용, UPDATE/DELETE 없음).
--
-- 비밀번호: `Demo1234!` — auth-service R__01 의 demo@demo.com 과 같은 리포 전역
-- 데모 비밀번호 컨벤션(평문은 그 파일의 헤더에만 적혀 있다; 이 파일과 짝 파일
-- auth-service R__03 은 해시만 담는다). 로그인 자체는 이 티켓의 목표가 아니다
-- (AC-4 는 다음 창) — 그래도 만드는 이유는 "계정이 산다"는 것이 이름뿐 아니라
-- 실제 자격증명까지 완전해야, 콘솔이 나중에 그 사람으로 로그인시켜 보여주는 길을
-- 막지 않기 때문이다.
--
-- Idempotent: INSERT IGNORE only — a pre-existing row (re-run / older volume)
-- is a no-op, never corrected in place (same policy as R__05).

INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('0199de71-0000-7000-8000-00000000ad07', 'demo-corp', 'newhire@demo-corp.example', 'ACTIVE', NOW(6), NOW(6), 0);

INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
VALUES
    ('0199de70-0000-7000-8000-00000000ad07', '0199de71-0000-7000-8000-00000000ad07',
     'demo-corp', 'newhire@demo-corp.example', 'ACTIVE', NOW(6), NOW(6), 0);
