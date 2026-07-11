# Task ID

TASK-BE-501

# Title

ecommerce gateway 가 `X-Actor-Id` 를 **strip 하지 않아** 클라이언트 위조 헤더가 백엔드까지 통과한다 (`platform/api-gateway-policy.md` L74 정면 위반) — 더불어 `tenant_mismatch` 를 자기 javadoc 이 약속한 403 이 아니라 401 로 응답한다

# Status

done

# Owner

ecommerce-microservices-platform

# Task Tags

- security
- gateway
- contract

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능. 프로덕션 코드 변경이지만 **행동을 좁히는** 방향(더 막고, 더 정확히 거절)이라 되돌림 위험이 낮다.
- **자매 task (동일 진단에서 파생, 비차단)**: `TASK-BE-502`(wms gateway strip 집합 + fail-open 협소화). 같은 게이트웨이 드리프트 진단의 산물이며 **같은 PR 로 묶어 랜딩**한다. 파일이 서로 겹치지 않으므로 순서 의존 없음.
- **후속 (별건)**: `libs/java-gateway` 추출. 본 task 는 **추출 전에** 반드시 선행해야 한다 — 갈라진 보안 동작을 그대로 둔 채 공유 라이브러리로 합치면, 어느 도메인의 보안 동작이 조용히 바뀌는 것이 "리팩토링" 으로 위장된다.

---

# Goal

게이트웨이 드리프트 진단(2026-07-11) 에서 확정된 ecommerce gateway 의 결함 2건을 고친다.

## 결함 A (주) — `X-Actor-Id` 미차단 = 감사 주체 위조 경로

`platform/api-gateway-policy.md` L74 는 **명시적으로** 다음을 의무화한다:

> The gateway strips any client-supplied `X-User-Id`, `X-User-Role`, `X-User-Email`, **`X-Actor-Id`** headers **before** the JWT filter runs.

그런데 ecommerce 의 `IdentityHeaderStripFilter.IDENTITY_HEADERS` 는:

```java
"X-User-Id", "X-User-Email", "X-User-Role", "X-Account-Type", "X-Tenant-Id"
```

**`X-Actor-Id` 가 없다.** 그리고 `JwtHeaderEnrichmentFilter` 도 `X-Actor-Id` 를 설정하지 않는다(설정하는 헤더 = `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Tenant-Id`).

⇒ **strip ✗ + enrich ✗ = 클라이언트가 보낸 `X-Actor-Id` 값이 아무 검증 없이 백엔드에 그대로 도달한다.**

**"enrich 가 덮어쓰니 괜찮다" 는 방어는 성립하지 않는다** — 세 가지 이유로:

1. `X-Actor-Id` 는 ecommerce enrich 목록에 **아예 없다**. 덮어쓸 코드가 없다.
2. 설령 있었더라도, `JwtHeaderEnrichmentFilter` 는 JWT 가 있을 때만 동작한다. **public 라우트**(`GET /api/products/**`, `GET /api/reviews/products/**`, `PUBLIC_PATHS`, 캐리어 웹훅)에는 JWT 가 없어 필터가 no-op 이고, strip 만이 유일한 방어선이다.
3. strip 은 **정책이 요구하는 방어선 그 자체**다. enrich 는 보강이지 대체재가 아니다 — 정책이 "strip **before** the JWT filter runs" 라고 순서까지 못박은 이유다.

**폭발 반경 (정직하게)**: ecommerce `src/main` 에서 `X-Actor-Id` 를 **읽는 코드는 현재 0건**이다. 즉 오늘 당장 익스플로잇되지는 않는다 — **장전 안 된 총**이다. 그러나:

- 같은 저장소의 **wms 는 이미 12개 컨트롤러가 `X-Actor-Id` 를 감사 주체로 신뢰**한다 (`admin-service` 5곳: `UserController:41`, `RoleController:41`, `SettingsController:34`, `AlertDashboardController:37`, `AssignmentController:34` / `master-service` 7곳: `Warehouse/Zone/Sku/Partner/Lot/Location/LocationCreate` Controller 의 `ACTOR_HEADER = "X-Actor-Id"`). `AdminEventEnvelopeBuilder:22` 는 감사 이벤트의 actor 를 이 헤더에서 받는다고 문서화한다.
- 즉 이 패턴은 **가설이 아니라 이 코드베이스의 실재하는 관행**이며, ecommerce 개발자가 동일 관행을 채택하는 순간 **감사 로그 위조**(임의의 actor 로 위장한 변이 기록)가 성립한다.
- 리더가 0건인 지금이 **가장 싸게 막을 수 있는 시점**이다.

## 결함 B (부) — `tenant_mismatch` → 401 (자기 javadoc 은 403 을 약속)

ecommerce `TenantClaimValidator` javadoc L18 · L36:

> only a blank/missing claim is rejected (`tenant_mismatch` → **403 `TENANT_FORBIDDEN`**)
> ... so the `ServerAuthenticationEntryPoint` **can map a missing tenant to 403** (`TENANT_FORBIDDEN`)

그런데 `SecurityConfig.unauthorizedEntryPoint()` (L94–102) 는 **모든** 인증 실패를 무조건 401 `UNAUTHORIZED` 로 응답한다 — `tenant_mismatch` 분기가 없다. 약속된 `TENANT_FORBIDDEN` 코드는 코드베이스 어디에서도 방출되지 않는다.

scm 은 같은 자리에 분기가 **있다**(`SecurityConfig:56-69` — `OAuth2Error` 를 꺼내 `ERROR_CODE_TENANT_MISMATCH` 면 403 `TENANT_FORBIDDEN`). ecommerce 만 빠졌다.

**이것은 인증 우회가 아니다**(어느 쪽이든 거절된다). 그러나 **계약 위반**이다: 클라이언트가 "토큰이 썩었다"(401 → 재발급하라)와 "토큰은 멀쩡한데 이 테넌트 소관이 아니다"(403 → 재발급해도 소용없다)를 구분할 수 없다. javadoc 이 코드에 대해 **거짓을 말하고 있는 상태**이므로, 코드를 문서에 맞춘다.

---

# Scope

## In Scope

1. `IdentityHeaderStripFilter` 의 `IDENTITY_HEADERS` 에 **`X-Actor-Id` 추가**.
2. `SecurityConfig.unauthorizedEntryPoint()` 에 **`tenant_mismatch` → 403 `TENANT_FORBIDDEN` 분기 추가** (scm `SecurityConfig:56-69` 를 참조 구현으로 삼되, ecommerce 의 `ErrorResponse` + `GatewayMetrics` 관용구를 유지).
3. 두 결함에 대한 **회귀 테스트**. 각 테스트는 픽스를 되돌리면 **반드시 실패해야 한다**(vacuity 방지 — 아래 AC-4).

## Out of Scope

- **`X-Account-Id` / `X-Roles` / `X-Token-Type` / `X-Scopes` 추가** — scm 은 strip 하지만 ecommerce/정책은 요구하지 않는다. 근거 없이 넓히지 않는다. 진짜 정답인 "정책이 superset 을 명시" 는 `libs/java-gateway` 추출 때 공유 기본값으로 다룬다.
- **`JwtHeaderEnrichmentFilter` 가 `X-Actor-Id` 를 설정하도록 변경** — ecommerce 에 리더가 0건이므로 **주입할 이유가 없다**. 안 쓰는 헤더를 만들어 놓으면 다음 사람이 그걸 신뢰한다. strip(방어)만 하고 enrich(공급)는 하지 않는 것이 정확한 상태다. 향후 ecommerce 가 감사 actor 를 필요로 하면 그때 enrich 를 별건으로 추가한다.
- **wms 게이트웨이** — `TASK-BE-502`.
- **fan / scm 게이트웨이** — **결함 없음(진단 결과)**. scm 은 두 결함 모두 이미 올바르다. fan 은 `entitled_domains` 분기가 없지만 그것이 **정확한 상태**다 — `fan` 은 `ProductCatalog.ENTRIES`(= `{iam, wms, scm, erp, finance, ecommerce}`) 에 없어 **구독 가능 도메인이 아니며**(V0019 백필도 `('wms','scm','erp','finance')` 만 시드, `omni-corp` 전-도메인 테넌트도 fan 미구독), `fan-platform` 은 `B2C_CONSUMER` 다. 어떤 테넌트도 `fan` 에 엔타이틀될 수 없으므로 그 분기는 죽은 코드가 된다.
- **`libs/java-gateway` 추출** — 별건. 본 task 가 선행이다.

---

# Acceptance Criteria

- [ ] AC-1 — ecommerce `IdentityHeaderStripFilter.IDENTITY_HEADERS` 가 `X-Actor-Id` 를 포함한다. 정책 L74 가 요구하는 4개(`X-User-Id`, `X-User-Role`, `X-User-Email`, `X-Actor-Id`) 를 **전부** 포함함을 테스트로 단언.
- [ ] AC-2 — **public 라우트**(JWT 없음) 로 `X-Actor-Id: attacker` 를 보낸 요청이 백엔드에 도달할 때 그 헤더가 **없다**. 인증 라우트도 동일. (enrich 가 no-op 인 경로에서 strip 이 유일한 방어선임을 실증)
- [ ] AC-3 — `tenant_mismatch` 오류코드를 단 토큰이 **403** + 바디 코드 `TENANT_FORBIDDEN` 을 받는다. 그 외 인증 실패는 여전히 **401** `UNAUTHORIZED` 다(회귀 없음).
- [ ] AC-4 — **비-공허성(non-vacuity) 증명**: 픽스를 되돌린 상태(= `X-Actor-Id` 를 strip 집합에서 제거 / 403 분기 제거)에서 새 테스트가 **실제로 실패**함을 확인하고 그 결과를 PR 에 기록한다. "테스트가 통과했다" 는 픽스의 증거가 아니다 — **결함을 주입했을 때 무는지**가 증거다.
- [ ] AC-5 — 기존 ecommerce gateway 테스트 전부 GREEN(회귀 없음). CI `Build & Test (JDK 21, Linux)` + `Integration (ecommerce, Testcontainers)` GREEN.

---

# Related Specs

- [`platform/api-gateway-policy.md`](../../../../platform/api-gateway-policy.md) § Identity Header Handling (L72–77) — **본 결함의 권위 출처**
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/java/com/example/gateway/filter/IdentityHeaderStripFilter.java` (L22–28 — 결함 A)
- `.../gateway/filter/JwtHeaderEnrichmentFilter.java` (L48–83 — `X-Actor-Id` 부재 확인)
- `.../gateway/config/SecurityConfig.java` (L94–102 — 결함 B)
- `.../gateway/security/TenantClaimValidator.java` (L16–37 — 403 을 약속하는 javadoc)
- **참조 구현**: `projects/scm-platform/apps/gateway-service/.../config/SecurityConfig.java` (L51–75 — 403 매핑 분기)
- **패턴 실증**: `projects/wms-platform/apps/admin-service/.../api/**/​*Controller.java` (`ACTOR_HEADER = "X-Actor-Id"` — 12곳)

# Related Contracts

- API 계약 변경 **있음(결함 B)**: cross-tenant 토큰의 응답이 `401 UNAUTHORIZED` → `403 TENANT_FORBIDDEN` 로 바뀐다. 이는 `TenantClaimValidator` javadoc 이 이미 약속한 동작이므로 **문서화된 계약으로의 수렴**이지 신규 계약이 아니다. 그럼에도 클라이언트가 401 을 기대하고 있었다면 영향을 받는다 — Edge Cases 참조.
- 결함 A 는 계약 변경 없음(위조 헤더를 막을 뿐, 정상 요청의 헤더 집합은 불변).

---

# Target Service

- `ecommerce-microservices-platform` / `gateway-service`

---

# Edge Cases

- **`X-Actor-Id` 를 실제로 보내는 정상 클라이언트가 있는가?** — 없어야 한다(정책상 클라이언트가 보낼 수 있는 헤더가 아니다). ecommerce `src/main` 리더 0건으로 확인됨. 그럼에도 e2e/IT 픽스처가 이 헤더를 수동으로 세팅해 통과하고 있었다면 그 테스트는 **원래 잘못된 것**이므로 함께 고친다.
- **403 전환이 프런트를 깬다면?** — `web-store` / `console-web` 이 401 에 로그아웃·재발급 로직을 걸어놨다면, 403 은 그 경로를 타지 않는다. **cross-tenant 토큰은 재발급해도 해결되지 않으므로 403 이 정확하다**(무한 재발급 루프를 오히려 막는다). 그래도 프런트에서 401 만 특별 처리 중인지 확인한다.
- **`tenant_mismatch` 는 언제 나는가?** — ecommerce 의 validator 는 blank/missing `tenant_id` 만 거절한다(`*` 및 임의 슬러그 허용 — 엔타이틀먼트는 IAM 발급 시점에 결정). 즉 이 분기는 **claim 이 아예 없는 토큰**에 대해 발동한다. 흔치 않지만, 정확한 코드를 주는 것이 계약이다.
- **strip 순서** — `IdentityHeaderStripFilter` 는 `HIGHEST_PRECEDENCE`, enrich 는 `-1`. 이 순서가 깨지면 방어가 무너진다. 순서 자체를 단언하는 테스트가 이미 있는지 확인하고, 없으면 추가한다.

---

# Failure Scenarios

| # | 시나리오 | 기대/완화 |
|---|---|---|
| 1 | strip 대신 enrich 에 `X-Actor-Id` 를 추가해 "덮어쓰기" 로 해결 | **틀린 해법.** public 라우트엔 JWT 가 없어 enrich 가 no-op → 위조 헤더 생존. AC-2 가 이 경로를 직접 찌른다 |
| 2 | 리더가 0건이니 안 고쳐도 된다고 판단 | 정책 L74 의 명시적 위반이고, wms 가 같은 헤더를 12곳에서 신뢰한다. 리더가 생기는 순간 감사 위조 |
| 3 | 테스트가 통과하는 것으로 픽스를 증명 | 공허할 수 있다. **AC-4** — 픽스를 되돌려 테스트가 실제로 실패함을 확인해야 증거가 된다 |
| 4 | 403 전환이 기존 401 기대 클라이언트를 깸 | Edge Case 로 확인. javadoc 이 이미 403 을 약속했고, cross-tenant 는 재발급 불가이므로 403 이 의미상 정확 |
| 5 | scm 의 strip 집합(10개) 을 통째로 복사 | 근거 없는 확대. 정책/스펙이 요구하는 것만 고친다(Out of Scope) |

---

# Test Requirements

- gateway 모듈 **단위/슬라이스 테스트**(Docker-free 가능하면 우선): strip 집합 단언 + 403 매핑.
- **필터 통과 후 실제 헤더 상태**를 보는 테스트 — 상수 목록만 단언하면 "필터가 정말 지우는가" 를 증명하지 못한다. 최소한 `ServerWebExchange` 를 태워 mutated request 의 헤더 부재를 확인.
- **AC-4 (mutation check)** 는 수동 절차라도 반드시 수행하고 PR 본문에 붙인다.
- 로컬 Windows Testcontainers 는 npipe flake → **IT 는 CI Linux 가 권위**.

---

# Definition of Done

- [ ] `X-Actor-Id` strip 추가 + 회귀 테스트
- [ ] `tenant_mismatch` → 403 `TENANT_FORBIDDEN` 매핑 + 회귀 테스트
- [ ] AC-4 mutation check 수행 및 PR 기록
- [ ] CI GREEN (Build & Test + Integration (ecommerce))
- [ ] `projects/ecommerce-microservices-platform/tasks/INDEX.md` 갱신

---

# Provenance

2026-07-11. `platform/api-gateway-policy.md` 가 "모든 프로젝트에 게이트웨이가 있다" 고 선언하는데 finance/erp 에는 없다는 드리프트(`TASK-MONO-347`) 를 조사하다가, **"그럼 있는 게이트웨이 5개는 서로 같은가?"** 를 확인하기 위해 게이트웨이 전수 진단을 돌렸다. 결과: 복붙이 아니라 **세 혈통**(wms·scm·fan = 공유 가문 / ecommerce = 부분 공유 + 자체 확장 / iam = 완전 독립 구현)이었고, 그 갈라짐 속에 **진짜 결함 3건**이 있었다(본 task 2건 + `TASK-BE-502` 1건 — 정확히는 2+2 로 wms 에 2건).

**오탐 1건을 기록으로 남긴다**: 진단 서브에이전트는 `entitled_domains` 참조 카운트(wms 3 / scm 5 / **fan 0**) 를 근거로 "fan 에 엔타이틀먼트 dual-accept 가 누락됐다" 고 보고했다. **검증해 보니 오탐이었다** — fan 이 0 인 이유는 방치가 아니라 **아키텍처**다(위 Out of Scope 의 근거 4종). 서브에이전트 보고를 코드로 재검증하지 않았다면 죽은 코드를 프로덕션 보안 필터에 심을 뻔했다.

교훈: **복붙은 이미 대가를 치르고 있었다.** `FailOpenRateLimiter` 의 버그 수정이 세 도메인에만 반영되고 wms 는 빠졌고, strip 집합이 도메인마다 5·5·8·10개로 갈렸다. **어느 것이 맞는 버전인지 코드만 봐서는 알 수 없었고, 실제로 아무도 모르고 있었다.** 그래서 `libs/java-gateway` 추출은 단순 DRY 리팩토링이 아니라 **보안 수렴 작업**이며, 수렴 전에 각 결함을 개별로 고쳐 놓아야 추출 PR 이 "행동 불변" 을 정직하게 주장할 수 있다.

분석=Opus 4.8 / 구현 권장=Opus (보안 경계 + 계약 변경. 특히 AC-2/AC-4 의 "enrich 는 strip 의 대체재가 아니다" 논증을 테스트로 정확히 옮기는 것이 핵심).
