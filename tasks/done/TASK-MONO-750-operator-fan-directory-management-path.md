# Task ID

TASK-MONO-750

# Title

`ADR-MONO-079` D4-A — 플랫폼 운영자의 **팬 디렉터리 관리 경로**(관리 경로만 열고 대리 저작·커뮤니티·멤버십은 계속 닫는다) · `ADR-MONO-059` 부분 개정의 구현

# Status

done

# Owner

monorepo

# Task Tags

- iam
- fan-platform
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (운영자가 B2C 테넌트에 들어오는 첫 길 — 대조군이 본체)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A · `TASK-MONO-748`(관리할 소속사 API)
- **후속**: `TASK-MONO-751`(콘솔 화면)

# Goal

플랫폼 운영자(라이더 R3 기본값 — 고객사 운영자 아님)가 콘솔에서 `fan-platform` 을 assume 해 artist-service 의 **디렉터리 관리 경로**(소속사 · 아티스트 · 그룹 · 팬덤 쓰기)만 쓸 수 있게 한다. 지금 그 경로는 발급될 수 없는 역할에만 열려 있다(`fan` 도메인 구독 0 · `FAN_OPERATOR` 파생 없음 · `trustEntitledDomains()` 꺼짐 — `FanTenantGatePolicyTest`).

# Scope

## In Scope

- 착수 전 실측(AC-0): 지금 막는 층 전부의 목록(구독 · 역할 파생 · 게이트웨이 · 서비스 디코더/필터 · `WorkloadRoleCatalog`) — 무엇을 어디서 여는지 표로
- `fan` 도메인 구독(플랫폼 운영자만 선택 가능하게) · `FAN_OPERATOR` 파생
- artist-service: **관리 경로에만** 운영자 토큰 신뢰(엔타이틀먼트) — 읽기·다른 경로는 지금 그대로
- community · membership · notification 서비스: 무변경 — 운영자 토큰이 계속 거절됨을 시험으로 고정
- `FanTenantGatePolicyTest` 등 기존 핀 시험을 새 정책에 맞게(«관리 경로만» 으로) 갱신 — 핀을 지우지 않는다
- 계약·스펙: 운영자의 팬 관리 범위

## Out of Scope

- 콘솔 화면(`TASK-MONO-751`) · 고객사 운영자(R3 밖)

# Acceptance Criteria

- [x] **AC-0** — 막는 층 실측 표. → § 구현 기록 § AC-0
- [ ] **AC-1** — 🔴 대조군 한 시험 안에서: 운영자 토큰으로 artist-service 관리 경로 2xx · 커뮤니티 `ARTIST_POST` 403 · 멤버십 403 · 고객사 운영자의 `fan-platform` assume 거절. → 🟡 **네 다리 각각 ✅, «한 시험 안에서» 는 ⚪** — 네 다리가 서로 다른 Spring Boot 앱(별도 JVM·별도 보안 체인)이라 단일 JUnit 시험이 될 수 없다. 대신 **같은 토큰 모양**(`tenant_id=fan-platform` · `roles=[FAN_OPERATOR]` · `entitled_domains=[fan]`)을 각 서비스의 **실제 운영 체인** 슬라이스 시험에 넣었다(§ 구현 기록 § 시험). 한 프로세스에서 넷을 함께 보는 e2e 는 이 저장소에 하네스가 없고 Docker 도 꺼져 있어 측정하지 못했다 — **소유자 판정 몫**(이 AC 를 «각 다리» 로 닫을지).
- [x] **AC-2** — 소비자(팬) 토큰은 관리 경로에 여전히 403. → `SecurityChainAssemblySliceTest.mutatingRouteRequiresAnAdminTierRole`(기존) + `PlatformOperatorDirectoryPath.fanIsStillRefusedOnTheAgencyWrite`(신규)
- [x] **AC-3** — `ADR-MONO-059` 의 부분 개정 범위와 시험이 일치한다(059 «부분 개정» 절). → § 구현 기록 § AC-3 대조

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D4-A · R3
- `docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md` § 부분 개정

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`(엔타이틀먼트·역할) · artist-service 관리 API

# Edge Cases

- 운영자가 팬 테넌트에 있을 때 팬 웹 화면(소비자 쪽)에 그 토큰이 새지 않는다.

# Failure Scenarios

1. 엔타이틀먼트 신뢰를 서비스 전체에 켜 커뮤니티 쓰기까지 열린다 — 059 가 배제한 대리 저작.

---

# 구현 기록 (2026-10-02 UTC · 분석·구현=Opus 5.5)

## AC-0 — 지금 막는 층 (착수 전 실측, 코드 기준)

🔴 **결론 먼저**: 플랫폼 운영자(`admin_operators.tenant_id='*'`)는 **이미** `fan-platform` 을 assume 할 수 있었다(assignment check 2번 — platform-scope 는 모든 테넌트 `assigned=true`). 그 토큰을 막던 것은 **단 한 층 — `fan` 구독 0행**이었다. 구독이 없으니 역할이 파생되지 않고(`roles` 생략), 역할 없는 토큰은 게이트웨이 role admission 에서 403. **구독 한 행을 넣는 순간 열리는 것은 디렉터리만이 아니었다** — 아래 9·10행.

| # | 층 | 위치 | 착수 전 | 이 티켓 |
|---|---|---|---|---|
| 1 | 도메인 구독 | account-service `tenant_domain_subscription` | `fan` 0행(V0019 wms/scm/erp/finance · V0022 ecommerce) → assume 토큰에 `FAN_OPERATOR` 없음 | **V0031**: `(fan-platform, fan, ACTIVE)` — V0022 와 같은 모양 |
| 2 | 구독 쓰기 표면 | admin-service `ManageSubscriptionUseCase` | `domainKey` 자유 문자열 — 고객사 `TENANT_BILLING_ADMIN` 이 자기 테넌트에 `fan` 구독 가능(닿지 않는 `FAN_OPERATOR` 를 파생) | `fan`·`fan-platform` 키는 **`fan-platform` 테넌트만** — 그 테넌트의 `subscription.manage` 는 `'*'` grant 만 가진다 ⇒ 플랫폼 운영자만 선택 가능 |
| 3 | assume 게이트 | admin-service `OperatorAssignmentCheckUseCase` | `'*'` 운영자: 모든 테넌트 `assigned=true`. 고객사 운영자: assignment row 가 있으면 `assigned=true` — SUPER_ADMIN 이 row 를 만들 수 있었다 · 파트너십 host 분기도 열려 있었다 | **step 2b**: `fan-platform` 은 `'*'` 만. row·파트너십 분기 **앞에서** `assigned=false` |
| 3b | assignment 생성 | admin-service `ManageOperatorAssignmentUseCase` | `fan-platform` row 생성 가능(SUPER_ADMIN) | 모든 actor 403 `TENANT_SCOPE_DENIED`(consumer-pool 과 같은 답) · 해제는 허용 |
| 4 | 역할 파생 | auth-service `OperatorRoleDerivation` | `fan`/`fan-platform` → `FAN_OPERATOR` arm 존재, 구독 0 이라 도달 불가 | 코드 무변경 · 주석 갱신(도달 가능, 어디서 닫히는지) |
| 5 | 워크로드 역할 | auth-service `WorkloadRoleCatalog` | 워크로드 client 에 admin-tier 0 — **사람 경로와 무관**(assume-tenant 운영자 분기는 이 표를 안 본다) | 무변경 · 주석 1절 · `WorkloadRoleCatalogTest` 9/9 |
| 6 | 게이트웨이 테넌트 게이트 | fan gateway `tenantGate()` | 동등성 + `'*'`, 엔타이틀먼트 OFF → `fan-platform` assume 토큰은 **통과**(막지 않는 층) | 무변경 · 핀 2(플랫폼 운영자 통과 / 고객사 엔타이틀먼트 거절) |
| 6b | 게이트웨이 role admission | `RoleAdmissions.roleOrScope()` | 역할 없는 토큰 403 — **착수 전 운영자가 실제로 걸리던 곳**(1행의 결과) | 무변경 |
| 6c | 게이트웨이 audience | `allowed-audiences=fan-platform-user-flow-client`, `SHADOW` | 콘솔 client 의 `aud` 는 **mismatch 로그만**(거절 아님) | 무변경 — 🔴 ENFORCE 전환 전 콘솔 client 를 측정해 넣어야 한다(아래 후속) |
| 7 | artist-service 디코더·필터 | `ServiceLevelOAuth2Config` | 동등성 + `'*'`, 엔타이틀먼트 OFF → `fan-platform` 토큰 통과 | **무변경**(켜면 R3 위반 — 아래 이탈 1) · 주석 |
| 8 | artist-service 경로 규칙 | `SecurityConfig` `ADMIN_ROLES` | `FAN_OPERATOR` 이미 포함 — **디렉터리 쓰기 매처에만**, 읽기 `authenticated()`, `/internal/**` 워크로드 체인, 나머지 `denyAll()` ⇒ 「관리 경로에만」 은 **이미 이 파일의 모양**이다 | 코드 무변경 · 주석 · 핀 7 |
| 9 | community | `SecurityConfig` `.authenticated()` + `ActorContext.isOperator()` 가 `FAN_OPERATOR` 포함 | 🔴 **막는 층 0** — 1행만 생기면 운영자가 `ARTIST_POST` 를 쓰고 `owns()` 로 모든 작성자의 글을 편집 = ADR-059 가 배제한 갈래 B | 체인 규칙 `END_USER_NOT_OPERATOR` — `FAN_OPERATOR` 는 `/api/community/**` 전부 403 |
| 10 | membership · notification | `SecurityConfig` `.authenticated("/api/fan/**")` | 🔴 **막는 층 0** — 운영자가 자기 이름으로 구독·인박스 | 같은 체인 규칙 |

## 무엇을 바꿨나

- **iam / account-service** — `V0031__seed_fan_platform_fan_domain_subscription.sql`(신규). `ConsumerPoolMigrationOnExistingVolumeIntegrationTest` 의 업그레이드를 `target=30` 으로 고정(그 시험의 «정확히 V0029·V0030» 전제가 V0031 로 깨지지 않게 — 시험 대상 무변경).
- **iam / admin-service** — `AdminOperator`(`FAN_PLATFORM_TENANT_ID` · `FAN_DOMAIN_KEYS` · `isPlatformOperatorOnlyTenant` · `isReservedFanSubscription`) · `OperatorAssignmentCheckUseCase` step 2b · `ManageOperatorAssignmentUseCase` · `ManageSubscriptionUseCase`. 새 오류 코드 없음(기존 `TENANT_SCOPE_DENIED`).
- **iam / auth-service** — 주석만: `OperatorRoleDerivation`(arm 도달 가능) · `WorkloadRoleCatalog`(사람 경로이지 워크로드 아님).
- **fan / community · membership · notification** — `SecurityConfig` 의 `.authenticated(<prefix>)` → `.requestMatchers(<prefix>).access(END_USER_NOT_OPERATOR)` = `authenticated ∧ ¬hasRole(FAN_OPERATOR)`(Spring `AuthorizationManagers` 조합 — 익명은 여전히 401). community `ActorContext.isOperator()` 는 코드 무변경, 주석으로 «이제 한 층 위에서 막힌다» 기록.
- **fan / artist-service · gateway** — 코드 무변경, 주석(엔타이틀먼트를 켜지 않는 두 번째 이유 = R3).
- **계약** — fan `artist-api.md`(헤더: 플랫폼 운영자의 디렉터리 경로·동등성·`/internal` 403) · `community-api.md`/`membership-api.md`/`notification-api.md`(운영자 토큰 거절 — 403 `PERMISSION_DENIED`) · iam `admin-api.md`(assignment 생성의 `fan-platform` 거절 · `fan` 구독 제약) · `internal/auth-to-admin.md`(판정 규칙 5).

**신뢰한 정확한 경로 · 역할 · 키**: 역할 `FAN_OPERATOR`(assume-tenant 파생) · 구독 키 `fan`(테넌트 `fan-platform`) · 경로 = artist-service 의 `POST/PATCH/DELETE /api/artists/**`, `POST/PATCH/DELETE /api/artist-groups/**`, `POST/PATCH /api/fandoms/**`, `POST/PATCH/DELETE /api/agencies/**`(쓰기, `ADMIN_ROLES`) + 같은 네 자원의 `GET`(읽기, `authenticated()`) — **기존 매처 그대로**, 새로 연 매처 0.

## 이탈 (티켓 문구와 다르게 한 것 — 전부 «더 좁게»)

1. 🔴 **«엔타이틀먼트 신뢰» 를 켜지 않았다.** 플랫폼 운영자는 `fan-platform` 을 **직접** assume 하므로 토큰이 `tenant_id=fan-platform` — 동등성으로 이미 통과한다(콘솔이 ecommerce 를 `ecommerce` assume 으로 여는 것과 같은 모양). 엔타이틀먼트 분기가 추가로 여는 것은 **고객사 테넌트의 토큰**(`tenant_id=demo-corp` + fan 엔타이틀먼트)뿐이고 그건 R3 가 배제한 대상이다(그 쓰기는 디렉터리가 아닌 고객사 테넌트 id 로 떨어지기도 한다). 덧붙여 실측: 공유 `TenantClaimValidator` 의 엔타이틀먼트 분기는 claim 을 **필수 테넌트 id `fan-platform`** 과 비교한다 — iam 이 실제로 싣는 키는 `fan` 이라, 문구대로 켰어도 운영자 토큰을 여는 데는 쓸모가 없고 `fan-platform` 키로 구독한 고객사만 연다.
2. 🔴 **community · membership · notification 은 «무변경» 일 수 없었다.** 막는 층이 0 이었다(AC-0 9·10행) — 1행(구독)만 넣고 이 셋을 그대로 두면 059 가 배제한 대리 저작이 그대로 열린다(실패 시나리오 1 이 «엔타이틀먼트» 가 아니라 «동등성» 으로 실현된다). 그래서 «운영자 토큰이 계속 거절됨» 을 지키려고 세 서비스 체인에 거절 규칙을 넣었다. 059 부분 개정 절의 «계속 닫힌 것» 과 정확히 같은 범위다.
3. **AC-1 의 «한 시험»** — 위 AC 줄 참고(⚪).

## 시험 (명령 · rc · 개수)

모두 Git Bash, `./gradlew <task> > log 2>&1; echo rc=$?` 로 실행(파이프 없음). 개수는 `build/test-results/test/*.xml` 합계.

| 모듈 | 명령 | rc | 결과 |
|---|---|---|---|
| iam admin-service | `:projects:iam-platform:apps:admin-service:test` | 0 | 885 tests · 0 fail · 58 skipped(`@Tag("integration")`) — 신규 `FanPlatformPlatformOperatorOnlyTest` 9/9 |
| iam account-service | `:projects:iam-platform:apps:account-service:test` | 0 | 602 · 0 fail · 47 skipped |
| iam auth-service | `:projects:iam-platform:apps:auth-service:test` | 0 | 1011 · 0 fail · 33 skipped — `OperatorRoleDerivationTest` 9/9 · `WorkloadRoleCatalogTest` 9/9 |
| fan artist-service | `:projects:fan-platform:apps:artist-service:test` | 0 | 237 · 0 fail — 신규 `SecurityChainAssemblySliceTest.PlatformOperatorDirectoryPath` 7 · `FanTenantGatePolicyTest.OperatorPathIsEqualityNotEntitlement` 2 |
| fan community-service | `:…:community-service:test` | 0 | 210 · 0 fail — 신규 `OperatorTokenRefused` 5(ARTIST_POST 403 · 피드 403 · ARTIST/FAN 대조 · 익명 401) |
| fan membership-service | `:…:membership-service:test` | 0 | 151 · 0 fail — 신규 3(목록 403 · 구독 403 · FAN 대조) |
| fan notification-service | `:…:notification-service:test` | 0 | 128 · 0 fail — 신규 2(인박스 403 · FAN 대조) |
| fan gateway-service | `:…:gateway-service:test` | 0 | 50 · 0 fail — `TenantClaimValidatorTest` 신규 2 |

**AC-1 의 네 다리 ↔ 시험**: 관리 경로 통과 = `PlatformOperatorDirectoryPath.operatorIsAdmittedOnTheDirectoryWrite`(POST `/api/artists` → 422, 즉 게이트 통과 후 검증 단계) · `ARTIST_POST` 403 = community `OperatorTokenRefused.operatorCannotPublishArtistPost` · 멤버십 403 = membership `operatorCannotListMemberships`/`operatorCannotSubscribe` · 고객사 운영자 assume 거절 = admin `FanPlatformPlatformOperatorOnlyTest.AssumeGate.customerOperator_withRow_isRefused`(row 가 있어도) + artist/gateway 의 R3 핀(고객사 토큰이 디렉터리에 오면 `TENANT_FORBIDDEN`). 🔵 «2xx» 는 슬라이스에서 422/404 로 관측된다 — 게이트 통과 뒤 디스패처가 답한 것(유스케이스는 목) — 실제 2xx 는 통합 시험 몫(⚪).

**비-관리 경로 대조**: 운영자 토큰 → `/internal/artists/exists` 403(워크로드 체인 메시지로 판별) · 미등록 경로 403.

## 물림(bite) — 세 개, 전부 복사로 원복(`cmp` 동일 확인, `git checkout` 안 씀)

1. community `SecurityConfig` 의 `.access(END_USER_NOT_OPERATOR)` → `.authenticated()`(제한 제거) ⇒ `OperatorTokenRefused` 2 실패(ARTIST_POST 403 · 피드 403). rc=1.
2. artist `ServiceLevelOAuth2Config` + fan gateway `tenantGate()` 에 `.trustEntitledDomains()` 추가 ⇒ artist 5 실패 · gateway 2 실패(R3 핀 포함). 🔴 **첫 시도에서 내 R3 핀은 초록으로 남았다** — `entitled_domains=["fan"]` 만 실었는데 검증기는 `fan-platform` 과 비교하므로 켜도 거절된다. 핀을 iam 이 파생하는 두 키 `["fan","fan-platform"]` 로 바꾼 뒤 재물림에서 빨강 확인. rc=1.
3. admin `OperatorAssignmentCheckUseCase` step 2b 무력화(`if (false && …)`) ⇒ `FanPlatformPlatformOperatorOnlyTest` 2 실패(row 있는 고객사 운영자 · 파트너십 분기 미도달). rc=1.

## 로컬 판정

- ✅ 위 단위·슬라이스 전부 rc=0.
- ⚪ **Testcontainers(`@Tag("integration")`) 미실행** — `docker info` rc=1(Docker 꺼짐). 해당: account-service `ConsumerPoolMigrationOnExistingVolumeIntegrationTest`(이 티켓이 target 을 30 으로 고정 — CI 가 판정) · V0031 이 실제 MySQL 에 적용되는지 · auth-service `AssumeTenantExchangeIntegrationTest`(assigned=false → `invalid_grant` 는 기존 동작). CI 몫.
- ⚪ 단일 프로세스 e2e(콘솔 → assume → 팬 게이트웨이 → 네 서비스) — 하네스 없음.
- ⚪ 엣지 케이스 «팬 웹에 토큰이 새지 않는다» — 이 티켓은 토큰 운반 경로를 바꾸지 않는다(assume 토큰은 콘솔 client 에게 발급되고 console-web 서버가 든다, 팬 웹은 자기 client 토큰만). 새 시험 없음, 751 이 서버 라우트를 만들 때의 몫.

## AC-3 대조 — `ADR-MONO-059` § 부분 개정

| 059 부분 개정 문장 | 시험 |
|---|---|
| 열리는 것: 플랫폼 운영자가 `fan-platform` assume → 디렉터리 관리, artist-service 관리 경로에만 | admin `AssumeGate.platformOperator_isAssigned` · artist `PlatformOperatorDirectoryPath`(쓰기 통과·읽기 통과·`/internal` 403·미등록 403) |
| 계속 닫힌 것: 대리 저작(`ARTIST_POST`) | community `operatorCannotPublishArtistPost` |
| 계속 닫힌 것: 커뮤니티 | community `operatorCannotReadTheFeed` |
| 계속 닫힌 것: 멤버십 | membership `operatorCannotListMemberships` · `operatorCannotSubscribe` |
| 계속 닫힌 것: 알림 | notification `operatorCannotReadTheInbox` |
| (079 R3) 고객사 운영자 아님 | admin `customerOperator_withRow_isRefused` · `assignToFanPlatform_refused` · `customerTenant_fanDomain_refused` · artist/gateway R3 핀 |

## 후속 — 이 티켓이 만든 사실 (751 · 소유자)

- 🔴 **데모 로그인(`demo@demo.com`)은 이 길을 못 쓴다.** 그 운영자의 홈은 `demo-corp`(고객사) — R3 정의(`tenant_id='*'`)상 고객사 운영자다. dev 시드에 platform-scope 운영자는 **0명**(admin-service `migration-dev` 실측). `TASK-MONO-751` 이 데모에서 화면을 보이려면 ① 플랫폼 운영자 데모 신원을 시드하거나 ② 라이더 R3 를 뒤집어야 한다 — **소유자 결정**. 이 티켓은 시드하지 않았다.
- fan gateway `allowed-audiences` 에 콘솔 client 가 없다(SHADOW 라 지금은 로그만). 751 이 실제로 닿은 뒤 측정해 넣어야 ENFORCE 전환이 콘솔을 끊지 않는다.
- 콘솔 레지스트리 `ProductCatalog` 에 `fan` 항목 없음 — 넣으면 console-web `ProductKeySchema` 를 같은 PR 에서 바꿔야 한다(그 클래스 javadoc). 751 몫.
- 부수 효과(무해): 팬 소비자 토큰에 `entitled_domains=["fan"]` 가 실린다(ecommerce 소비자 토큰이 V0022 이후 `["ecommerce"]` 를 싣는 것과 같다). 팬 엣지·서비스는 그 claim 을 읽지 않는다.

---

## CORRECTION (2026-10-03 UTC) — 4차원 종결 (소유자 결정)

- (a) #4125 `MERGED` · (b) 스쿼시 `d5f317508` 가 `origin/main` 에 포함 · (c) 머지 시점 rollup 실패 0/67.
- (d) AC-0·2·3 본문 근거로 닫힘. **AC-1 — 소유자 결정 (2026-10-03 UTC): «(a) 서비스별 시험 넷으로 충족한다»**. 문구는 «한 시험 안에서» 였지만 네 서비스가 별개 앱이라 같은 토큰 모양(`tenant_id=fan-platform` · `roles=[FAN_OPERATOR]` · `entitled_domains=[fan]`)을 각 서비스의 실제 보안 체인에 넣은 네 셀로 쟀다: artist `PlatformOperatorDirectoryPath.operatorIsAdmittedOnTheDirectoryWrite`(게이트 통과) · community `operatorCannotPublishArtistPost`(403) · membership `operatorCannotListMemberships`/`operatorCannotSubscribe`(403) · admin `customerOperator_withRow_isRefused`(고객사 운영자 거절). 소유자가 이것을 AC-1 의 충족으로 받았다.
- 머지 시점 rollup 의 `Integration (iam B)` 첫 빨강(기존 볼륨 IT 의 target 없는 재실행이 새 V0031 을 적용)은 같은 PR 의 `3f9e398fe` 로 고쳐진 뒤 머지됐다 — 최종 rollup 실패 0.
