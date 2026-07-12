# Task ID

TASK-MONO-368

# Title

ecommerce 의 "per-tenant" rate limit 은 **인증 트래픽 전체가 공유하는 버킷 하나**다 — 쇼퍼 한 명이 마켓플레이스를 429 시킬 수 있다

# Status

done

# Owner

monorepo

# Task Tags

- security
- availability
- gateway
- rate-limit
- drift-guard

---

# Goal

`TenantRouteRateLimitConfig` 는 인증 요청을 `rate:ecommerce-gw:<routeId>:t:<tenantId>` 로 키잉하고, Javadoc 은 M7 을 인용하며 **"one tenant's burst cannot consume another tenant's bucket"** 을 약속한다.

**그 약속은 지배적 트래픽에 대해 거짓이다.** 쇼퍼의 `tenant_id` 는 **상수 `ecommerce`** 이기 때문이다:

- 인증 코드 플로우의 `tenant_id` 는 **개시한 OAuth 클라이언트**에서 나온다 — `SavedRequestTenantResolver.java:40-42`.
- ecommerce OIDC 클라이언트들은 `tenant_id='ecommerce'` 로 시드된다 — `auth-service .../V0012__seed_ecommerce_oidc_clients.sql:25,45`.
- ⇒ **모든 쇼퍼 토큰이 같은 `tenant_id`** ⇒ 키의 tenant 세그먼트가 상수 ⇒ **라우트당 버킷 하나를 전 인증 사용자가 공유**한다.

그리고 그 버킷은 `replenishRate: 100` / `burstCapacity: 200` 이다(`application.yml` — 전 라우트 동일).

## 결과

1. **인증된 쇼퍼 한 명이 전체 마켓플레이스를 429 시킬 수 있다.** 버킷이 공유되므로 한 계정의 버스트가 다른 모든 계정을 굶긴다. **M7 이 막으려던 바로 그 실패**를, tenant 대신 account 축에서 그대로 재현한다.
2. **익명 트래픽은 IP 로 올바르게 쪼개진다**(`:ip:<ip>` 세그먼트, `TenantRouteRateLimitConfig.java:121-125`). ⇒ **개별 식별이 가능한 인증 사용자만 뭉치고, 식별이 불가능한 익명은 쪼개진다.** 정확히 거꾸로다.
3. `TASK-BE-405` 는 기존 `ipKeyResolver` 를 tenant 키로 **교체**했다. 지배적 트래픽에 대해 그 교체는 **per-caller 격리를 제거**했다 — 개선이 아니라 회귀다.

## 왜 아무도 못 봤나

**격리 속성은 부하가 걸려야 관측된다.** 데모/포트폴리오 트래픽은 버킷을 채우지 못하므로 공유 버킷과 개별 버킷이 구분되지 않는다. 테스트도 못 잡는다 — `TenantRouteRateLimitConfigTest` 는 **두 개의 서로 다른 `tenant_id` 를 가진 JWT 를 직접 만들어** 키가 다름을 단언한다. **실제로는 그 두 값이 동시에 존재할 수 없다는 사실**을 테스트는 알 수 없다. 픽스처가 프로덕션이 생산하지 않는 입력을 만들면, 테스트는 **현실에 없는 격리**를 증명한다.

---

# 착수 전 실측 (2026-07-12, `bde2e8fb0` — 착수 시 재검증)

## 함대 키잉 지형

| 게이트웨이 | tenant gate | 인증 트래픽 키 | tenant 값이 변수인가 | 판정 |
|---|---|---|---|---|
| **ecommerce** | **accept-any** (`acceptAnyWellFormedTenant`) | `:t:<tenant_id>` | ❌ 쇼퍼는 전부 `ecommerce` | 🔴 **버킷 공유** |
| **wms** | dual-accept | `<ip>:<routeId>` | — | ✅ 정책 L92 기본값 준수 |
| **scm** | dual-accept + wildcard | `:acct:<sub>` | — | ✅ per-caller 격리 |
| **fan** | **strict** (`tenant_id == fan-platform`) | `:acct:<sub>` | ❌ 상수 | ✅ **올바름** — 상수 축을 피해 account 로 키잉 |
| **finance** | dual-accept + wildcard | `:acct:<sub>` | — | ✅ per-caller 격리 |
| **erp** | dual-accept + wildcard | `:acct:<sub>` | — | ✅ per-caller 격리 |

**fan 이 정답을 이미 알고 있었다.** fan 은 `tenant_id` 가 상수(`fan-platform`)임을 알고 account 로 키잉했다. ecommerce 는 같은 상황에서 상수 축을 골랐다.

## 부수 발견 — 내가 쓴 거짓 주석 (MONO-357, 지난주)

`finance/RateLimitConfig.java` 와 `erp` 의 대응 파일 Javadoc:

> *"wms — key by client IP only … and **no documented rationale** for either choice … Neither is a property to inherit on purpose."*

**틀렸다.** `platform/api-gateway-policy.md:92` 가 선언한다:

> *"The gateway applies rate limits per `(clientIp, routeId)` tuple **by default**."*

**wms 는 플랫폼 정책의 선언된 기본값을 지키는 유일한 비-multi-tenant 게이트웨이다.** 그 주석은 정책을 읽지 않고 다수결로 추정해서 썼고, 정책이 기본값으로 못박은 형태를 "정당화 불가"라고 단언한다. **MONO-361 이 방금 고친 stale-comment 와 같은 클래스** — 이번엔 내가 만들었다.

---

# Scope

## In Scope

1. **ecommerce 인증 키에 account 세그먼트 추가** — `rate:ecommerce-gw:<routeId>:t:<tenantId>:acct:<sub>`.
   - **M7 은 여전히 만족된다 — 오히려 더 강하게.** 키가 **더 잘게** 쪼개지므로 서로 다른 tenant 는 **절대** 버킷을 공유하지 않는다(tenant 세그먼트가 다르므로). M7 이 요구하는 것("한 tenant 의 burst 가 다른 tenant 의 latency 영향 X")은 성립하고, **추가로** 같은 tenant 안의 한 계정이 다른 계정을 굶기지 못한다.
   - 익명 키는 **그대로** (`:t:ecommerce:ip:<ip>`) — 이미 올바르다.
2. **`OverrideAwareRateLimiter.parseTenant` 를 반드시 함께 고칠 것.** 현재 `:t:` 이후를 `:ip:` **또는 문자열 끝**까지 읽는다(`OverrideAwareRateLimiter.java:122-134`). `:acct:` 를 그냥 붙이면 tenant 가 `ecommerce:acct:<uuid>` 로 파싱되어 **모든 계정이 서로 다른 "tenant"** 가 되고 ⇒ `overrides.ecommerce.<route>` 조회가 **영원히 빗나가 per-tenant override 가 조용히 죽는다.** 파서가 `:acct:` 에서도 종료해야 한다.
3. **거짓 Javadoc 정정** — finance/erp 의 "wms 는 정당화 불가" 단락. 정책 L92 를 인용하고, wms 가 **준수 측**임을 적는다.
4. **`platform/api-gateway-policy.md` § Rate Limiting 명문화** — 현재 한 문장(`(clientIp, routeId)` by default)이 함대의 실제 3형태 중 1개만 기술한다. 규칙으로 승격:
   - 익명/pre-auth → `(clientIp, routeId)`.
   - **인증 principal 이 있으면 그것으로 키잉**(`sub`) — 식별 가능한 호출자를 IP 로 뭉치지 말 것.
   - tenant 클레임이 **실제로 변수인** 경우에만 tenant 세그먼트를 추가. **상수 클레임으로 키잉하는 것은 격리가 아니라 글로벌 스로틀이다.**
   - 이탈은 해당 프로젝트 `PROJECT.md` § Overrides 에 **기록**.
5. **드리프트 가드 (I4)** — `scripts/check-gateway-drift.sh`: 게이트웨이가 rate-limit 을 **자신의 설정이 상수로 고정한 클레임**으로 키잉하면 RED. 구체적으로: `required-tenant-id` 가 pin 되어 있고 tenant gate 가 **strict**(dual-accept/accept-any 아님)인데 키 리졸버가 `tenant_id` 를 읽으면 → 그 키는 상수다.

## Out of Scope — **사람 결정 필요**

- **wms 를 account 키잉으로 옮길 것인가.** wms 는 전 라우트가 JWT 인증인데 IP 로 키잉한다 ⇒ 한 NAT 뒤의 창고 운영자 전원이 버킷을 공유한다. **그러나 wms 는 현행 정책을 준수하고 있다.** Scope 4 가 정책을 바꾸면 wms 는 **기록된 이탈**이 되거나 정렬돼야 한다. **라이브 엣지의 동작 변경이고 정책 선택이므로 이 task 에서 임의로 하지 않는다.** § Failure Scenarios 참조.
- ADR-MONO-049 구현 — ACCEPT 대기.

---

# Acceptance Criteria

- [ ] **AC-1** — ecommerce 인증 키가 `rate:ecommerce-gw:<routeId>:t:<tenantId>:acct:<sub>`. **같은 tenant 의 서로 다른 두 계정이 서로 다른 키를 받는다**는 것을 단언하는 테스트가 있다. (현재 테스트는 서로 다른 *tenant* 만 단언한다 — 프로덕션에 존재하지 않는 입력이다.)
- [ ] **AC-2** — `parseTenant` 가 `:acct:` 에서 종료한다. **회귀 테스트**: `rate:ecommerce-gw:orders:t:acme-corp:acct:<uuid>` → `parseTenant` == `"acme-corp"` (**`"acme-corp:acct:<uuid>"` 가 아니라**). 이게 없으면 per-tenant override 가 조용히 죽는다.
- [ ] **AC-3** — per-tenant override 가 **여전히 동작**한다(`OverrideAwareRateLimiterTest` 무수정 GREEN, 또는 키 형태 변경분만 반영).
- [ ] **AC-4** — 익명 키 **불변** (`:t:ecommerce:ip:<ip>`). 회귀 없음.
- [ ] **AC-5** — finance/erp 의 거짓 Javadoc 이 정정되고, `platform/api-gateway-policy.md:92` 를 인용한다. **`grep -rn "no documented rationale" projects/` → 0건.**
- [ ] **AC-6** — `platform/api-gateway-policy.md` § Rate Limiting 이 3형태를 모두 규율한다(§ Scope 4).
- [ ] **AC-7 (가드가 무는가)** — I4 가드에 **mutation 주입**: ecommerce 키를 `:t:<tenant>` 로 되돌리면 **RED**. 주입이 실제로 적용됐는지 `git diff --stat` 로 먼저 확인한 뒤 결과를 읽을 것.
- [ ] **AC-8 (가드가 물 기회를 얻는가)** — I4 를 발동시키는 paths-filter 가 **게이트웨이 rate-limit 소스와 application.yml 을 포함**한다. `code-changed` 와 **AND 금지**.
- [ ] **AC-9** — ecommerce 게이트웨이 전체 스위트 0 실패 / 0 skipped.

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/gateway-service/.../config/TenantRouteRateLimitConfig.java`
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../ratelimit/OverrideAwareRateLimiter.java` (§ `parseTenant`)
- `platform/api-gateway-policy.md` § Rate Limiting (L90-103)
- `rules/traits/multi-tenant.md` § M7
- `projects/{finance,erp}-platform/apps/gateway-service/.../config/RateLimitConfig.java` (거짓 Javadoc)
- `scripts/check-gateway-drift.sh` (I1/I2/I3 + 신규 I4)

# Related Contracts

**없다** — rate-limit 키는 내부 구현이다. 다만 **429 를 받는 주체가 바뀐다**: 지금은 "마켓플레이스 전체", 수정 후는 "폭주한 계정". **이것이 이 task 의 요점이다.**

---

# Edge Cases

- **`sub` 가 없는 인증 토큰** — `client_credentials` 토큰은 `entitled_domains` 를 받지 않지만(`TenantClaimTokenCustomizer.java:437-441`) `sub` 는 client_id 다. 키가 `acct:<client_id>` 가 되는 것은 **올바르다**(서비스 계정별 버킷).
- **`sub` 가 blank/null** — 절대 null 키를 만들지 말 것. tenant-only 키로 폴백하고 WARN(익명 경로의 `DEFAULT_TENANT` 폴백과 같은 규율).
- **assume-tenant 토큰** (`tenant_id = acme-corp`, `TenantClaimTokenCustomizer.java:359`) — tenant 세그먼트가 **진짜 변수인 유일한 트래픽**. 이 경로에서 per-tenant override 가 의미를 갖는다 ⇒ **AC-2 가 지키는 것이 바로 이것**이다.
- **`*` (SUPER_ADMIN wildcard)** — ecommerce 는 `allowSuperAdminWildcard()` 를 호출하지 않지만 `acceptAnyWellFormedTenant` 가 `*` 를 well-formed 로 통과시킨다. 키가 `:t:*:acct:<sub>` 가 된다 — account 로 쪼개지므로 **해롭지 않다**(수정 전에는 `:t:*` 단일 버킷).

# Failure Scenarios

- **`parseTenant` 를 안 고치고 `:acct:` 만 붙인다** → per-tenant override 가 **조용히** 죽는다(에러 없음, 로그 없음, 테스트 통과). **이 task 에서 가장 쉬운 자해.** Guard: AC-2.
- **테스트가 프로덕션에 없는 입력을 만든다** → 현행 `TenantRouteRateLimitConfigTest` 가 정확히 그렇다(서로 다른 두 `tenant_id`). 새 테스트는 **같은 tenant, 다른 account** 를 단언해야 한다 — **그게 실제 트래픽이다.** Guard: AC-1.
- **정책만 고치고 wms 를 방치** → 정책이 "인증 principal 로 키잉"을 요구하는데 wms 가 IP 로 키잉하면 wms 는 **새 정책 위반**이 된다. 그러면 § Out of Scope 의 결정이 **강제된다.** 정책 문구에 wms 의 현행을 **명시적 이탈로 기록**하거나, 후속 task 를 열 것. **둘 중 하나를 반드시 할 것 — 조용히 위반 상태로 두지 말 것.**

---

# Provenance

발굴 2026-07-12 — 사용자가 "wms rate-limit 키잉" 결정을 물었고, 답하려고 `platform/api-gateway-policy.md` 를 **처음으로 실제로 읽었다.** L92 가 IP 키잉을 기본값으로 선언하고 있었고 ⇒ **wms 는 이상치가 아니라 유일한 준수자**였다. 그 반전을 확인하려고 함대 6개의 tenant gate 와 클레임 생산 경로를 추적했고, 그 과정에서 **ecommerce 의 tenant 세그먼트가 지배적 트래픽에 대해 상수**라는 것이 나왔다.

**세 번 연속 틀렸다**: ①"wms 가 근거 없는 이상치" (거꾸로 — 유일한 준수자) ②"fan 이 M7 위반" (아니다 — fan 은 상수 축을 피해 account 로 키잉한 **정답**) ③"ecommerce 는 M7 을 정식 구현" (아니다 — 상수로 키잉해 격리가 공허). **세 번 다 이름에서 추론하고 코드를 안 읽어서 틀렸다.** 실제 결함은 내가 처음에 지목한 곳의 **정반대편**에 있었다.

분석=Opus 4.8 / 구현 권장=Opus (라이브 엣지 키 형태 변경 + 조용히-죽는 파서 함정 + 가드 설계).
