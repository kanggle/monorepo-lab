# Task ID

TASK-MONO-386

# Title

BE-507 이전 소비자의 tenant 소급 재배정 — **그런데 재배정할 대상도, 식별 신호도 없다.** 마이그레이션이 아니라 **결정(D1)** 이 이 티켓의 본체다 (게이트웨이 `acceptAnyWellFormedTenant` 제거의 유일한 선행)

# Status

ready

# Owner

monorepo

# Task Tags

- decide
- data
- tenant
- iam
- ecommerce

---

# ⚠️ 이 티켓은 마이그레이션 티켓이 아니다

`TASK-BE-507` 이 Out of Scope 로 남긴 *"기존 `fan-platform` 로 찍힌 ecommerce 소비자의 소급 재배정"* 을 착수하려 했으나, **선행 실측(2026-07-13)이 그 전제를 무너뜨렸다**:

1. **식별 신호가 없다** — 어떤 계정이 "ecommerce 쇼핑객"인지 저장된 상태로부터 **완전하게 도출할 수 없다**(아래 §신호 표).
2. **보존할 프로덕션 데이터가 없다** — 어떤 Flyway 시드도 소비자 계정을 만들지 않고(`INSERT INTO accounts|credentials` 비-테스트 SQL **0건**), 데모는 **갓 부팅 시 `credentials` 가 비어 있다**(`infra/demo/verify-demo-wrapper.sh:628` 의 기존 실측 — *"가입이 유일한 입구"*).

⇒ 남은 모집단은 **오래 살아남은 데모 인스턴스에 라이브 가입으로 쌓인 행들**뿐이다. **마이그레이션 설계 이전에, 이 행들을 어떻게 할 것인지가 사람 결정이다.**

---

# 왜 지금 이게 급한가 — 보안 엣지가 이것 때문에 꺼져 있다

ecommerce 게이트웨이는 `acceptAnyWellFormedTenant()` 로 **아무 tenant 나 통과**시킨다(`libs/java-security/.../TenantClaimValidator.java:189-191`; ecommerce 만 켰다). 이 플래그가 **BE-506 의 조용한 오스코핑을 아무도 못 잡게 만든 마지막 구멍**이다.

BE-507 이후 **신규** 소비자는 진짜 `tenant=ecommerce` 를 갖는다 ⇒ 플래그 제거가 *원리적으로* 가능해졌다. **그러나 제거하면, BE-507 의 cross-tenant 폴백으로 살아 있는 `fan-platform` 구 소비자가 전원 403 이 된다.** 즉:

> **이 티켓이 닫히기 전에는 `acceptAnyWellFormedTenant` 를 제거할 수 없다.** 이 티켓이 그 선행이다.

---

# 실측 (2026-07-13) — 식별 신호 전수

| 후보 | 신호로 쓸 수 있나 | 근거 |
|---|---|---|
| `social_identities.tenant_id` (auth_db) | **PARTIAL — 양성 전용** | `V0007__add_tenant_id_to_auth_tables.sql` 이 컬럼 추가. 소셜 브라우저 로그인은 **개시 클라이언트**에서 tenant 를 유도해 이 행에 찍는다(`SocialLoginBrowserController` → `SocialIdentityPersistStep`, BE-396 이후). ⇒ `social_identities.tenant_id='ecommerce'` **∧** `accounts.tenant_id='fan-platform'` = **모순 = 소셜 쇼핑객 확정**. 그러나 ① **폼 가입은 이 행 자체가 없다**(커버리지 0) ② V0007 이 기존 행을 `DEFAULT 'fan-platform'` 으로 백필 ③ 저장요청 없으면 리졸버가 fan 폴백 ④ 기존 identity 행은 **갱신 안 됨**(first-write-wins). ⇒ **`='ecommerce'` 는 증명하지만 `='fan-platform'` 은 아무것도 증명하지 않는다.** |
| `oauth2_authorization.registered_client_id` (SAS) | **PARTIAL — 폼 가입을 덮는 유일한 신호** | `V0008__create_oauth_tables.sql:87-122`, JDBC 영속(`AuthorizationServerConfig` → `JpaOAuth2AuthorizationService`). `attributes` blob 에 principal(+`account_id`)이 실려 사람↔클라이언트 매핑이 된다(BE-465 가 라이브 DB 로 확인). 그러나 ① **가입이 아니라 로그인 기록**이다(D1 의 정의 = "가입한 클라이언트") ② **로그아웃/폐기 시 행이 삭제된다** ⇒ 한 번이라도 로그아웃한 쇼핑객은 **투명인간**. |
| `oauth_consent` | **NO — 실제로 0행** | 스키마상 완벽한 (principal, client, tenant) 매핑이지만, `V0012` 가 web-store 클라이언트에 `require-authorization-consent:false` 를 심어 **행이 아예 안 쓰인다.** |
| `refresh_tokens.tenant_id` | **NO** | 토큰 claim 의 **복사본**이다(`DomainSyncOAuth2AuthorizationService.extractTenantId`) — 폼 로그인 구 소비자는 `fan-platform`. 오염된 값의 사본이지 독립 증인이 아니다. |
| ecommerce `user_profiles` | **NO** | `AccountCreatedConsumer` 가 `account.created` 를 **tenant/클라이언트 필터 없이 전부** 투영한다(fan 소비자·타 tenant 스태프 포함). tenant 스탬프도 이벤트의 `fan-platform` 을 그대로 받는다. **존재도 스탬프도 판별력 0.** |
| ecommerce 거래 활동 (`orders.user_id` 등) | **YES — 양성 전용** | `orders.user_id` ← 게이트웨이 `X-User-Id` ← JWT `sub` = **IAM account UUID**(ADR-MONO-040 / `jwt-standard-claims.md`). 주문이 있으면 쇼핑객 확정. 그러나 **가입만 하고 거래 안 한 쇼핑객은 침묵** ⇒ 모집단을 열거하지 못하고 부분집합만 준다. |
| `account_status_history` / `outbox` / `login_history` / `device_sessions` | **NO** | 클라이언트 컬럼 자체가 없다. 게다가 **web-store 쇼핑객은 `login_history` 행을 아예 안 만든다** — SAS 브라우저 경로는 `auth.login.succeeded` 를 발행하지 않는다(`CredentialAuthenticationProvider` / `SocialIdentityPersistStep` 이 명시적으로 미발행). |

**결론**: 완전하고 정확한 소급 재배정은 **저장된 상태로부터 도출 불가능**하다. 달성 가능한 최선은 **하한(lower bound)** 이다 — `social_identities='ecommerce'` ∪ `oauth2_authorization`(web-store 클라이언트) ∪ ecommerce 거래 이력. 나머지는 **원리적으로 구분 불가**다.

---

# Acceptance Criteria

- [ ] **AC-0 (모집단 실측 — 본체 1/2)** — 재배정 대상이 **실재하는지** 라이브 인스턴스에서 확인한다. 장수 데모 인스턴스가 있으면 그 DB 에서: `SELECT tenant_id, COUNT(*) FROM accounts GROUP BY 1` · `credentials` 동일 · `SELECT COUNT(*) FROM social_identities WHERE tenant_id='ecommerce'` · ecommerce `SELECT tenant_id, COUNT(*) FROM orders GROUP BY 1`. **행이 0 이면 이 티켓은 D1=A 로 즉시 닫힌다**(대상이 없으므로 마이그레이션도 폐기도 무의미 — 문서만 정리). 로컬 Docker 가 죽어 있으므로 데모 기동이 선행.
- [ ] **AC-1 (D1 결정 — 본체 2/2, 사람 게이트)** — 아래 3 안 중 택일. **에이전트 단독 결정 금지**(프로덕션-성 데이터 + 보안 엣지 결정).
- [ ] **AC-2** — 결정에 따른 실행 + `acceptAnyWellFormedTenant` 제거 가능 여부를 확정해 후속 티켓으로 넘긴다(제거 자체는 이 티켓 밖).

## D1 — 구 `fan-platform` 소비자를 어떻게 할 것인가

### 옵션 A — **방치 (BE-507 폴백이 영구히 그들을 살린다)**

- **대가**: `acceptAnyWellFormedTenant` 를 **영원히 못 뗀다**. 구 소비자의 토큰은 계속 `tenant_id=fan-platform` 이고, 게이트웨이가 그걸 통과시켜야 하므로. ⇒ **BE-506 이 짚은 구멍이 상시 열려 있다.**
- 적합한 경우: 실측 결과 구 소비자가 **다수 실재하고** 그들의 데이터가 의미 있을 때.

### 옵션 B — **데모 소비자 폐기 (권장 후보)**

- 근거: **시드가 소비자를 만들지 않고**(0건), **갓 부팅한 데모는 `credentials` 가 빈다**(실측). 따라서 존재하는 소비자는 **데모 인스턴스에 우연히 쌓인 라이브 가입**이며 **보존 가치가 정의된 적이 없다.**
- 실행: 데모 DB 의 소비자 계정/크리덴셜/프로필/소셜identity + ecommerce 측 파생 행(`user_profiles`, 주문 등) 정리 → 이후 가입은 전부 올바른 tenant.
- **이득**: `acceptAnyWellFormedTenant` 제거가 **즉시 가능**해진다.
- **⚠️ 파괴적**: 되돌릴 수 없다. AC-0 이 "행 0 또는 무의미"를 보이지 않는 한 사람 승인 필수.

### 옵션 C — **부분 재배정 (하한만)**

- 위 양성-전용 신호로 식별되는 쇼핑객만 `ecommerce` 로 이동, 나머지는 fan 에 남김.
- **대가**: 절반만 맞는 상태가 **영구 고착**된다. 남은 fan 구 소비자 때문에 게이트웨이 플래그는 **여전히 못 뗀다** ⇒ **A 의 대가를 치르면서 C 의 복잡도를 얹는다.** 실행 비용 대비 이득이 가장 나쁘다.

**권장 = B**(AC-0 이 "보존 가치 있는 모집단 없음"을 확인하는 조건 하에). **A 는 보안 엣지를 영구 포기**하는 선택이고, **C 는 두 대가를 모두 치른다.**

---

# Scope

## In Scope

- AC-0 모집단 실측(데모 기동 필요) · D1 결정 기록 · 결정 실행.
- iam `account_db` / `auth_db` 와 ecommerce 파생 데이터 양쪽(= **cross-project** 이므로 root task).

## Out of Scope

- **`acceptAnyWellFormedTenant` 제거 자체** — 이 티켓이 그 **선행**일 뿐. 제거는 후속 티켓(가드 회귀 테스트 포함).
- **`RoleSeedPolicy`** → `TASK-MONO-381`. **tenant 를 고쳐도 role 가드는 여전히 vacuous** 하다(BE-506 실측).
- 신규 가입 경로 — `TASK-BE-507` 에서 완료.

---

# Dependency Markers

- **선행 (done)**: `TASK-BE-506`(반경 실측) · `TASK-BE-507`(소비자 tenant 해석; **cross-tenant 폴백이 구 소비자를 살리고 있다** — 이 티켓이 그 폴백의 수명을 정한다).
- **후속 (이 티켓이 게이트)**: ecommerce 게이트웨이 `acceptAnyWellFormedTenant` 제거.
- **연관**: `TASK-MONO-381`(role 가드 vacuity — 독립 축) · `TASK-MONO-347`(게이트웨이 수렴 계보).

# Related Specs

- `projects/iam-platform/tasks/done/TASK-BE-506-*.md` — 반경 실측(오배정의 전말)
- `projects/iam-platform/tasks/done/TASK-BE-507-*.md` — 수정 + 폴백 설계
- `libs/java-security/src/main/java/com/example/security/oauth2/TenantClaimValidator.java:189-191` — 제거 대상 플래그
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../config/OAuth2ResourceServerConfig.java:81-85` — 유일한 사용처
- `infra/demo/verify-demo-wrapper.sh:628` — *"갓 부팅한 데모의 credentials 는 비어 있다"* 실측

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `tenant_id` · `sub`(= account UUID; ecommerce 거래 이력을 계정에 잇는 유일한 끈)

# Edge Cases

- **같은 사람이 fan 과 ecommerce 양쪽 쇼핑객** — B 를 택하면 자연 해소(재가입). C 를 택하면 어느 쪽으로 옮길지 결정 불가 = C 의 또 다른 결함.
- **거래 이력이 있는 구 쇼핑객** — 계정을 옮기면(C) 그의 **주문은 `orders.tenant_id='fan-platform'` 로 남는다** ⇒ 계정과 주문의 tenant 가 어긋나 **새로운 오스코핑**이 생긴다. **C 를 택하면 ecommerce 파생 데이터도 함께 옮겨야 한다**(반경이 iam 을 넘는다 — 이것이 root task 인 이유).
- **인-플라이트 세션/토큰** — 재배정/폐기 시 기존 refresh token 은 무효화해야 한다(tenant 불일치 → `TOKEN_TENANT_MISMATCH`).

# Failure Scenarios

- **F1 — "재배정"을 자명한 작업으로 착수** → 식별 신호가 없어 **부분적으로만 옮기고 끝난다**(=C 를 의도치 않게 선택). 그 상태는 게이트웨이 플래그를 못 떼면서 복잡도만 남긴다. **AC-0/AC-1 이 이걸 막는다.**
- **F2 — 데모 데이터를 "프로덕션"으로 착각** → 보존할 이유 없는 행 때문에 보안 엣지를 영구 포기(A). 실측이 이걸 가른다.
- **F3 — 폐기(B)를 승인 없이 실행** → 파괴적, 복구 불가. **사람 승인 필수.**

# Test Requirements

- AC-0 = 라이브 DB 카운트(실측 기록이 산출물).
- B/C 실행 시: 실행 후 재가입/로그인 왕복이 동작하고, 기존 fan 소비자 회귀가 없음을 확인.
- 후속(플래그 제거) 티켓에서: `TenantGatePolicyLeakTest` 계열 가드가 ecommerce 도 커버하도록.

# Definition of Done

- [ ] AC-0 실측 기록 + D1 결정 기록.
- [ ] 결정 실행(또는 "대상 없음 → 문서만 정리").
- [ ] `acceptAnyWellFormedTenant` 제거 가능 여부 확정 + 후속 티켓 발행(가능한 경우).
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-13 — `TASK-BE-507` 이 Out of Scope 로 남긴 항목을 착수하려다, **선행 실측이 전제를 무너뜨렸다.**

**이 저장소가 반복해 배운 것의 또 다른 얼굴**: *"소급 재배정"* 은 **작업처럼 들리지만 사실은 결정**이었다. 착수했다면 식별 신호가 없다는 사실을 **마이그레이션을 절반쯤 짠 뒤에** 발견했을 것이고, 그 결과는 **아무도 고르지 않은 옵션 C**(반쪽 정합 + 보안 엣지 영구 포기)였을 것이다. `project_untickected_backlog_candidates_2026_06_19` 의 규칙 — *"REAL-GAP 은 착수 전 구현상태 검증 필수"* — 이 여기서도 값을 했다.

분석=Opus 4.8 / 실행 권장=**사람 결정 후 재평가**(B 면 Sonnet 으로 충분, C 면 Opus).
