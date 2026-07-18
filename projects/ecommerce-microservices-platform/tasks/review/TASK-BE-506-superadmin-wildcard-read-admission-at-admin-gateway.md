# Task ID

TASK-BE-506

# Title

ecommerce gateway 의 account-type 평면(`AccountTypeEnforcementFilter`)이 플랫폼 SUPER_ADMIN 의 **wildcard 토큰**(`tenant_id="*"`, roles 없음)을 `/api/admin/**` 에서 403 시킨다 — 콘솔 operator-overview 의 ecommerce 카드가 `forbidden`. READ(safe method) 한정으로 열어 finance/erp 와 정합화한다

# Status

review

# Owner

ecommerce-microservices-platform

# Task Tags

- code
- security
- gateway

---

# Dependency Markers

- **선행 없음** — 단독 착수 가능.
- **진단 출처 (감사 provenance)**: `TASK-FIN-BE-050` sibling-parity 감사(2026-07-18) 가 이 straggler 를 지목했다. 같은 감사가 낳은 자매 정합 수정 = finance `TASK-FIN-BE-048/049`, erp `TASK-ERP-BE-031`. 본 task 는 그 세 도메인과 **같은 정책 결정**(super-admin overview 는 도메인 카드를 READ 로 볼 수 있어야 한다)을 ecommerce 에 적용한다.
- **선행 사실 확인**: layer-1 tenant gate 는 이미 wildcard 를 admit 한다(`OAuth2ResourceServerConfig.tenantGate()` → `TenantClaimValidator.allowSuperAdminWildcard()`). 본 task 는 layer-2(account-type 평면)의 straggler 만 좁게 연다.

---

# Goal

플랫폼 super-admin 이 콘솔 operator-overview 에서 ecommerce 카드를 **읽을 수 있게** 한다 — READ(GET/HEAD) 한정, mutation 은 그대로 operator-gated.

## 근본 원인 (FIN-BE-050 감사가 고정 — 재조사 불필요)

플랫폼 super-admin 의 콘솔 operator-overview 는 운영자의 **base OIDC 도메인-facing 토큰**을 forward 한다:

- `tenant_id = '*'`
- scope = `openid profile email tenant.read`
- **roles 클레임 없음** — admin-plane `SUPER_ADMIN` 은 도메인 토큰에서 의도적으로 제외(ADR-033 S2 / ADR-034 U5)
- `entitled_domains = []`

ecommerce overview 카드는 **도달 가능**하다 — console-bff `EcommerceOverviewReadAdapter` 가 ecommerce 게이트웨이를 통해 `GET /api/admin/products` 를 호출한다.

- **Layer-1 (게이트웨이 tenant gate)** — `OAuth2ResourceServerConfig`(~:95-100) 가 `allowSuperAdminWildcard()` 를 wiring → `tenant_id='*'` 는 admit 된다.
- **Layer-2 (straggler)** — `AccountTypeEnforcementFilter`(~:75-78) 가 `/api/admin/**` 를 `ECOMMERCE_OPERATOR` role 로 게이트한다. super-admin base 토큰엔 roles 가 없다 → **403 FORBIDDEN** → overview ecommerce 카드가 `forbidden`.

## 정책 결정 (이미 확정)

**OPEN it.** super-admin 의 overview 는 ecommerce 카드를 볼 수 있어야 한다 — finance(FIN-BE-048/049) · erp(ERP-BE-031) 수정과 동일한 정합성.

---

# Scope

## 대상 파일 (프로덕션)

`projects/ecommerce-microservices-platform/apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java`

`/api/admin/**` 분기에서, super-admin **wildcard** 토큰(`tenant_id='*'`)을 **safe method(GET/HEAD) 에 한해** admit 한다. 그 외 모든 것(write 메서드 POST/PUT/PATCH/DELETE, non-wildcard caller)은 계속 `ECOMMERCE_OPERATOR` 를 요구한다.

- 필터의 기존 토큰/클레임 추출 재사용(새 JWT decode 경로 추가 금지). `auth.getToken()` 으로 얻은 `Jwt` 에서 `token.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID)`.
- **공유 상수** `TenantClaimValidator.WILDCARD_TENANT`(`"*"`, layer-1 이 게이트하는 바로 그 상수) 사용 — `"*"` 하드코딩 금지.
- HTTP 메서드는 `exchange.getRequest().getMethod()` → `HttpMethod.GET`/`HEAD` 검사.

## 대상 파일 (테스트)

`.../filter/AccountTypeEnforcementFilterTest.java` 확장 (WebFlux 게이트웨이, `MockServerWebExchange` + reactive filter 단위 테스트).

## 범위 밖

- layer-1 tenant gate, 다른 matcher, `IdentityHeaderStripFilter`, resource-server authority 목록 — 무변경.
- 새 API/계약 추가 없음.

---

# Invariant (finance/erp 수정과 동일)

**READ 가시성만 넓히고 mutation 은 절대 넓히지 않는다.** wildcard super-admin 토큰은 `/api/admin/**` 의 어떤 write 에도 여전히 403 이어야 한다. 새 admission 은 엄격히 (safe-method `AND` `tenant_id='*'`) 로만 게이트한다.

## 민감도 근거 (account-type 평면, 게이트웨이가 유일 집행점)

이것은 **account-type 평면 bypass** 이지 resource-server authority-list grant 가 아니다. downstream product-service 는 게이트웨이 뒤에서 header-trust 이므로 **게이트웨이가 유일한 집행점**이다. 따라서 short-circuit 은 정확히 (safe-method AND wildcard-tenant) → admit 이어야 하고, 그보다 넓으면 안 된다. 다른 matcher 를 약화하지 않는다. 기존 reactive 체인 시맨틱(admit 경로가 오늘 반환하는 동일 `Mono`/filter-chain continuation)을 보존한다.

---

# Acceptance Criteria

1. super-admin wildcard 토큰(`tenant_id='*'`, roles 없음) → **GET `/api/admin/**` admit**(필터 통과).
2. 같은 토큰 → **POST/PUT/PATCH/DELETE `/api/admin/**` → 403**(write 는 operator-gated 유지).
3. non-wildcard, non-operator 토큰 → **GET `/api/admin/**` 여전히 403**(admission 은 wildcard 에만 keyed, 인증 자체엔 keyed 아님).
4. `ECOMMERCE_OPERATOR` 토큰 → read/write 양쪽 계속 admit(회귀 없음).
5. wildcard-GET-admitted 단언에 대한 **RED-before/GREEN-after** 확인(short-circuit 제거→RED→복원→GREEN).
6. `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:check` GREEN.
7. `"*"` 하드코딩 0건 — `TenantClaimValidator.WILDCARD_TENANT`/`CLAIM_TENANT_ID` 사용.

---

# Related Specs

- `platform/api-gateway-policy.md` — 게이트웨이 헤더/인가 경계.
- `ADR-033 S2` / `ADR-034 U5` — SUPER_ADMIN 을 도메인 토큰에서 제외(그래서 base 토큰에 roles 가 없다).
- ecommerce `AccountTypeEnforcementFilter` 클래스 javadoc — account-type 평면 admission 규칙(ADR-MONO-035 4b-2a / ADR-032 D3).

# Related Contracts

- product-api (admin 표면) — `GET /api/admin/products` 는 console-bff `EcommerceOverviewReadAdapter` 가 호출하는 read 엔드포인트. 계약 변경 없음(게이트웨이 admission 만 조정).

---

# Edge Cases

- **wildcard + write** → 403(불변식의 핵심 — 명시 테스트).
- **non-wildcard(`tenant_id='ecommerce'`) + roles 없음 + GET** → 403(admission 은 wildcard 에만 keyed, "인증됨" 만으로는 불충분).
- **operator + write** → admit(회귀 없음 — admission 은 additive).
- **HEAD** → GET 과 동일하게 safe-method 로 admit.
- **JWT 없는 public 라우트** → 기존 pass-through 무변경(`/api/admin/**` 아님).

# Failure Scenarios

- **너무 넓게 열림**: wildcard 를 모든 메서드에 admit 하면 super-admin 이 게이트웨이 뒤 header-trust product-service 에 write 할 수 있게 된다 — 불변식 위반. safe-method AND wildcard 로 엄격히 게이트하여 방지.
- **`"*"` 하드코딩**: layer-1 이 게이트하는 상수와 drift 위험. 공유 `WILDCARD_TENANT` 로 방지.
- **reactive 체인 오단락**: admit 경로가 응답을 short-circuit 하면 downstream 이 안 불린다. boolean `allowed` 만 조정하고 기존 `chain.filter(exchange)` continuation 을 그대로 사용하여 방지.
