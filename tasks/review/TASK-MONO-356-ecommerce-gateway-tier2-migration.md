# Task ID

TASK-MONO-356

# Title

`ecommerce` 게이트웨이를 `libs/java-gateway` Tier 2 로 이관 (+ `requireTenantMatch` 플래그 도입 · `FailOpenRateLimiter` 델리게이트 시그니처 수렴) — ADR-MONO-048 D7 step 3

# Status

review

# Owner

monorepo

# Task Tags

- refactor
- shared-library
- gateway
- security

---

# Dependency Markers

- **선행 (머지됨)**: `TASK-MONO-355` (PR #2425 squash `caa188e78`) — Tier 2 파라미터화. lib 에 `TenantClaimValidator`(빌더·기본값 전부 닫힘) · `IdentityHeaderStripFilter`(add-only baseline 8) · `JwtHeaderEnrichmentFilter`(매핑 리스트) · `JwtClaims` · `GatewayJwtDecoders` 존재. **행동 불변 증명 절차(mutation check + production-빈 seam)가 실제로 작동함이 실증됨** — 이 task 는 그 절차를 그대로 쓴다.
- **선행 (머지됨)**: `TASK-MONO-351` (PR #2417) — 모듈 + Tier 1. ecommerce 는 이미 `AllowedIssuersValidator` 를 채택 중.
- **후속**: `TASK-MONO-357`(finance/erp 게이트웨이 신설 = **`TASK-MONO-347` direction A 해소**).
- **범위 밖**: `iam` 게이트웨이(ADR D2) · **rate-limit 키잉 전략**(MONO-355 § Out of Scope — wms IP-only 는 사람 결정 대기).

---

# Goal

네 번째이자 마지막 게이트웨이를 공유 라이브러리로 들인다.

ecommerce 는 **가장 많이 갈라진 소비자**다(ADR § 1.1 "부분공유 + 자체확장"). 따라서 이 step 은 앞의 둘과 성격이 다르다 — 355 가 "세 도메인이 이미 같은 모양이었음을 파라미터로 표현"한 것이라면, 이 step 은 **정말로 다른 것이 파라미터 안에 들어가는지**를 시험한다. 안 들어간다면 그건 이 task 의 실패가 아니라 **파라미터 설계가 틀렸다는 신호**이고, ADR D3 를 다시 봐야 한다.

---

# Scope

## 착수 전 실측 (2026-07-11, `caa188e78` 시점 — 재확인할 것)

### ① `IdentityHeaderStripFilter` — ecommerce 는 baseline **보다 적게** 지운다

| | strip 집합 |
|---|---|
| lib `BASELINE_HEADERS` | 8개 |
| ecommerce | **6개** — `X-Account-Id` 와 `X-Roles` 가 **없다** |

**add-only baseline 을 채택하면 ecommerce 의 strip 집합이 넓어진다** = 행동 변경(조이는 방향). 안전한가?

- ecommerce 서비스가 읽는 신원 헤더 전수: `X-User-Id`(305) · `X-User-Role`(194) · `X-Tenant-Id`(32) · `X-User-Email`(10) · `X-Account-Type`(8) · `X-Actor-Id`(6) — **6개 모두 이미 strip 집합 안**.
- `X-Account-Id` / `X-Roles` **읽는 곳 0건** (gateway 제외 전수 grep).

⇒ baseline 채택은 **무해한 심층방어**(net-zero). 다만 **착수 시 재확인**할 것 — 이 사이에 reader 가 생겼다면 그 reader 는 **클라이언트 위조 헤더를 신뢰하고 있는 것**이므로(BE-501 과 동일 결함 클래스) 이 task 는 정리가 아니라 **취약점 수정**이 되고, 그 사실을 PR 이 명시해야 한다.

### ② `TenantClaimValidator` — **`requireTenantMatch` 플래그가 여기서 도착한다**

ecommerce 게이트: **non-blank 면 무엇이든 통과**(blank/부재만 거부). 근거는 확고하다 — ADR-MONO-019 § D5/D6 + ADR-MONO-030 § 2.4: ecommerce 는 **멀티테넌트 마켓플레이스 SaaS** 이고, 엔타이틀먼트는 **IAM 발급 시점**에 결정되며 엣지는 발급을 신뢰한다. 테넌트 분리는 영속계층 `WHERE tenant_id` 가 강제하고 **M6 cross-tenant-leak IT 가 그걸 증명**한다.

MONO-355 는 이 플래그를 **일부러 넣지 않았다** — 소비자가 없는 상태에서 세 개의 엄격한 프로덕션 엣지에 accept-anything 분기를 심는 것을 거부했기 때문이다. **이 task 가 소비자와 함께 가져온다.**

**설계 제약 (협상 불가)**: 기본값은 계속 **닫힘**이어야 한다. `forTenant(x)` 는 여전히 정확 일치만 통과시키고, 이 모드는 **명시 호출**(예: `.acceptAnyWellFormedTenant()`)로만 열린다. 그리고 **lib 테스트가 wms/scm/fan 정책에 이 모드가 켜지지 않았음을 단언**해야 한다 — 공유 보안 클래스에서 게이트를 여는 플래그가 추가될 때, 그것이 다른 세 엣지로 새지 않았다는 증거가 diff 안에 있어야 한다.

### ③ `FailOpenRateLimiter` — 델리게이트 시그니처 수렴

| | 생성자 |
|---|---|
| lib | `FailOpenRateLimiter(RedisRateLimiter delegate, MeterRegistry)` — **구체 타입** |
| ecommerce | `FailOpenRateLimiter(RateLimiter<RedisRateLimiter.Config> delegate, MeterRegistry)` — **일반화** |

ecommerce 가 일반화한 이유는 실재한다: 자기 `OverrideAwareRateLimiter`(테넌트별 rate-limit 오버라이드 — 마켓플레이스 요구사항, D4 로 서비스 잔류)를 감싸야 하는데 그건 `RedisRateLimiter` 가 **아니다**.

⇒ **lib 쪽을 넓힌다**(`RedisRateLimiter` → `RateLimiter<RedisRateLimiter.Config>`). wms/scm/fan 이 넘기는 `RedisRateLimiter` 는 그 인터페이스를 구현하므로 **컴파일·행동 모두 불변**. 넓히기는 안전하고 좁히기는 아니다. ecommerce 사본은 삭제하고 lib 을 채택 — **BE-502 가 고친 "모든 `Throwable` 을 Redis 장애로 삼킴" 결함이 네 번째 사본에서 되살아나지 않도록** 하는 것이 이 라이브러리의 존재 이유다.

### ④ `JwtHeaderEnrichmentFilter`

ecommerce 주입: `X-User-Id` · `X-User-Email` · `X-User-Role`(always) · `X-Tenant-Id`. `X-Account-Id`/`X-Roles`/`X-Scopes`/`X-Token-Type` 주입 없음. 매핑 리스트로 표현 가능해 보인다 — **착수 시 각 헤더의 존재 규칙(null-skip vs blank-skip vs always)을 소스에서 직접 확인**할 것(355 가 이 축에서 세 도메인이 실제로 달랐음을 발견했다).

### ⑤ ~~조사 필요 — `X-Account-Type` 의 출처~~ → **정정: 이 항목은 틀렸다**

**착수 시 재측정 결과, "다운스트림 8곳이 `X-Account-Type` 을 읽는다" 는 이 task 를 쓰면서 만든 grep 아티팩트였다.** 당시 census 명령에 `-h`(파일명 억제)가 있어 뒤따르는 `grep -v gateway-service` 필터가 **아무것도 걸러내지 못했고**, 게이트웨이 자신의 출현 8건을 다운스트림으로 오독했다. 게이트웨이 javadoc 이 옳았다 — **읽는 다운스트림은 0건**이다.

**올바른 census (2026-07-12)** — ecommerce 비-게이트웨이 코드가 실제로 읽는 신원 헤더:

| 헤더 | 읽는 곳 | 게이트웨이 strip 집합에 있나 |
|---|---|---|
| `X-User-Id` | 291 | ✅ |
| `X-User-Role` | 179 | ✅ |
| `X-Tenant-Id` | 25 | ✅ |
| **`X-Seller-Scope`** | **5** | ❌ **없다** |
| `X-Account-Type` · `X-User-Email` · `X-Actor-Id` · `X-Account-Id` · `X-Roles` | **0** | — |

### ⑤′ **진짜 발견 — `X-Seller-Scope` 가 strip 되지 않는다**

`order` · `product` · `settlement` **세 서비스**가 각각 `SellerScopeContextFilter`(`OncePerRequestFilter`, `HIGHEST_PRECEDENCE+1`)를 두고 **인바운드 요청에서** `X-Seller-Scope` 를 읽어 `SellerScopeContext` 에 바인딩하며, 각 리포지토리가 이를 `AND EXISTS(... seller_id = :sellerScope)` 로 건다(ADR-MONO-030 Step 3 §3.3 — 셀러 데이터-스코프 축).

그 필터의 javadoc 은 이렇게 적혀 있다: *"Reads the **gateway-injected** `X-Seller-Scope` header"*, *"The **gateway only forwards this header on the OPERATOR plane**"*. **둘 다 사실이 아니었다** — 게이트웨이는 이 헤더를 **strip 하지도 inject 하지도 않으며**, `/api/admin/orders/**` · `/api/admin/products/**` · `/api/admin/settlements/**` 를 라우팅한다. 즉 **클라이언트가 보낸 값이 그대로 그 필터들에 도달했다.**

**오늘 악용 가능하지는 않다 — 순전히 순서 운이다.** 이 헤더를 생산하는 코드가 저장소에 **0건**이고(claim→헤더 배선은 ADR-MONO-030 **Step 4**, 미구현), 비활성 상태에서 헤더 부재 = **무제한**(ADR-MONO-025 의 문서화된 net-zero / fail-OPEN 불변, BE-363 F1: *"셀러 제약 없는 테넌트 운영자는 전체를 봐야 한다 — fail-closed 금지"*)이다. 위조해봐야 **자기 시야를 좁힐 뿐**이다.

**Step 4 가 주입을 켜는 순간 구멍이 열린다** — 자기 `seller_id` 로 가둬진 셀러가 자기 `X-Seller-Scope` 를 실어 보내면, 지우는 게 없으니 **전체 테넌트 조망으로 탈출**한다. `platform/api-gateway-policy.md` § Identity Header Handling 은 이미 게이트웨이가 클라이언트발 신원 헤더를 거부하라고 요구한다. `TASK-BE-501` 과 같은 결함 클래스이되, **도달 가능해지기 한 걸음 전에** 잡은 것이다.

⇒ **`X-Seller-Scope` 를 ecommerce strip 추가분에 넣는다.** 오늘 net-zero, 활성화가 구멍을 함께 배포하지 못하게 만든다. (주입은 별개 결정 — Step 4 의 몫이며 이 task 의 몫이 아니다.)

## D4 — 서비스에 잔류 (이관 대상 아님)

`SecurityConfig`(라우트 지식: `/api/shippings/carrier-webhook`, `GET /api/products/**` public, CORS preflight) · `GatewayMetrics` · `RateLimiterConfig` / `RateLimitOverrideProperties` / `TenantRouteRateLimitConfig` / `OverrideAwareRateLimiter`(테넌트별 오버라이드 = 다른 도메인에 없는 진짜 요구사항) · `SwaggerAggregationConfig` · `AccountTypeEnforcementFilter` · `RequestLoggingFilter` · `RouteService`.

**"나중에 공유될지도 모르니" 로 `libs/` 에 올리지 말 것** (ADR § D4).

---

# Acceptance Criteria

- **AC-1** — ecommerce 게이트웨이의 `IdentityHeaderStripFilter` · `JwtHeaderEnrichmentFilter` · `TenantClaimValidator` · `FailOpenRateLimiter` · OAuth2 검증체인이 lib 것으로 대체되고, 도메인 사본이 삭제된다. lib 테스트 **실행 수 > 0 / skipped=0** 를 **XML 로** 확인(`BUILD SUCCESSFUL` 은 0건 실행을 못 거른다).
- **AC-2 — 플래그가 새지 않았음**: `requireTenantMatch=false`(accept-any) 모드가 **ecommerce 에만** 켜져 있고 wms/scm/fan 게이트는 그대로임을 **lib 테스트가 직접 단언**한다. 기본값은 여전히 닫힘.
- **AC-3 — 행동 불변**: ecommerce 게이트웨이 기존 테스트가 **assertion 무수정**으로 통과. 생성 라인은 355 의 seam 패턴을 따라 **production 빈**에서 가져온다(하드코딩 사본 금지 — 그래야 mutation 이 문다).
- **AC-4 — strip 집합 확대의 명시적 회계**: baseline 채택으로 ecommerce 가 새로 지우게 되는 헤더(`X-Account-Id`, `X-Roles`)에 대해 **reader 0건**임을 재확인하고 PR 에 기록. reader 가 있으면 **STOP** 하고 취약점 수정으로 재프레이밍.
- **AC-5 — `FailOpenRateLimiter` 넓히기 검증**: wms/scm/fan 3개 게이트웨이 스위트가 **무수정 통과**(넓히기는 기존 호출자에게 투명해야 한다). ecommerce 의 `OverrideAwareRateLimiter` 감싸기가 실제로 동작함을 테스트로 단언.
- **AC-6 — mutation check**: 최소 3종이 **물어야** 한다 — ① ecommerce 게이트에서 accept-any 를 끄기(→ ecommerce 테스트 RED) ② wms 게이트에 accept-any 켜기(→ wms 테스트 RED = 플래그 누출 검출) ③ lib `FailOpenRateLimiter` 의 Redis-예외 판별 predicate 제거(→ RED). **통과하는 가드 ≠ 무는 가드.**
- **AC-7** — lib + 게이트웨이 4개 전체 스위트 **0 실패 / 0 skipped**.
- **AC-8** — 순감소 라인 수 측정. 4개 게이트웨이 전체에서 남은 중복이 **0** 임을 확인(= D7 step 1~3 의 종료 조건).

---

# Related Specs

- [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — § D3(Tier 2) · **§ D4(ecommerce 잔류 클래스 = 명시)** · § D5(ecommerce 게이트 = non-blank 전부 허용, 근거 확고) · § D6(증명 의무) · § D7(로드맵)
- ADR-MONO-019 § D5/D6 · **ADR-MONO-030 § 2.4** — ecommerce 멀티테넌트 SaaS 엣지 정책의 권위
- ADR-MONO-024 D2 — 테넌트 confinement (영속계층 `WHERE tenant_id`)
- [`platform/api-gateway-policy.md`](../../platform/api-gateway-policy.md) · [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md)

# Related Contracts

없음. **외부 계약 표면 변경 0.**

---

# Edge Cases

- **`X-Account-Type` 배선**(§ 5) — 이해 전 손대지 말 것.
- **`@Component` 스캔** — ecommerce `GatewayApplication` 은 현재 lib 패키지를 **스캔하지 않는다**(MONO-351 이 의도적으로 그렇게 뒀다: ecommerce 는 `AllowedIssuersValidator` 만 `new` 로 쓰는 소비자였다). lib 필터를 빈으로 들이려면 스캔 또는 명시적 `@Bean` 배선이 필요하고, **놓치면 필터가 조용히 미등록**된다(단위테스트 전부 통과·빌드 GREEN·부팅 성공·보안 체인 없음). wms/scm/fan 처럼 `GatewayComponentScanTest` 를 **신설**하거나, 355 처럼 `@Bean` 으로 배선하고 그 config 를 테스트가 직접 읽게 할 것.
- **ecommerce `SecurityConfig` 는 lib 것이 아니다** — 자체 구현이며 `com.example.web.dto.ErrorResponse` + `GatewayMetrics` 에 의존한다. lib `SecurityConfig` 로 갈아끼우려는 충동을 경계할 것(D4 가 명시적으로 금지).
- **`api` 금지** — lib 은 `implementation` 만(MONO-044a 거울상).

# Failure Scenarios

- **파라미터가 안 맞는데 억지로 맞춘다** — ecommerce 가 파라미터에 안 들어가면 그건 **ADR D3 재검토 신호**이지, 파라미터를 하나 더 늘릴 신호가 아니다. 축을 늘려 "설정으로 뭐든 되는" 클래스가 되면 도메인 정책이 코드에서 사라진다(ADR 이 명시한 부정적 결과).
- **accept-any 플래그가 다른 엣지로 샌다** — 기본값이 열려 있거나, 실수로 wms 배선에 붙거나. AC-2/AC-6② 가 이걸 잡는다. **이 플래그는 이 라이브러리에서 유일하게 게이트를 여는 스위치다** — 다루는 방식이 그에 걸맞아야 한다.
- **`FailOpenRateLimiter` 를 좁힌다** — 넓히기(`RedisRateLimiter` → `RateLimiter<Config>`)는 안전하지만, 반대로 ecommerce 를 lib 의 구체 시그니처에 맞추려 하면 `OverrideAwareRateLimiter` 를 감쌀 수 없어 **테넌트별 오버라이드가 소리 없이 사라진다**.
- **테스트를 고쳐서 통과시킨다** — AC-3. 통과시키려 고친 테스트는 리팩토링 옷을 입은 행동 변경이다.
