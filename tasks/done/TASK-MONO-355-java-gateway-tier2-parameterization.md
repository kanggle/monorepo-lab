# Task ID

TASK-MONO-355

# Title

`libs/java-gateway` **Tier 2** 파라미터화 (`IdentityHeaderStripFilter` · `JwtHeaderEnrichmentFilter` · `TenantClaimValidator` · `OAuth2ResourceServerConfig`) → wms·scm·fan 이관 — ADR-MONO-048 D7 step 2

# Status

done

# Owner

monorepo

# Task Tags

- refactor
- shared-library
- gateway
- security

---

# Dependency Markers

- **선행 (머지됨)**: `TASK-MONO-351` (PR #2417 squash `80e33a6c6`) — 모듈이 실제로 선다는 것과, D6 증명 절차(바이트동일 / 소비자 테스트 무수정 / mutation check)가 이 저장소에서 **작동한다**는 것이 실증됨. 특히 `GatewayComponentScanTest` 가 이미 각 게이트웨이에 있으므로, 이 task 가 lib 에 새 `@Component` 를 넣어도 **미등록 침묵 실패**는 이미 봉인돼 있다.
- **선행 (ACCEPTED)**: [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) § D3(Tier 2 파라미터 축) · § D5(테넌트 게이트 4정책) · § D6(**행동 불변 = 증명 의무**).
- **후속 (본 task 랜딩 후 spawn)**: `TASK-MONO-356`(ecommerce 이관) → `357`(finance/erp 게이트웨이 = `TASK-MONO-347` direction A 해소).
- **번호 주의**: ADR D7 의 원래 step 2/3/4 는 `352`/`353`/`354` 였으나 **동시 세션이 그 셋을 전부 무관한 task 에 배정**했다(352=error-code registry, 353=bitnami-kafka, 354=repo hygiene). D7 표는 `355`/`356`/`357` 로 정정됨. **`TASK-MONO-351` done 파일 본문의 "후속=352→353→354" 는 작성 시점의 기록이며 지금은 무효** — 로드맵 SoT 는 ADR D7 이다.
- **범위 밖**: `iam` 게이트웨이(ADR D2) · `ecommerce`(step 3) · **rate-limit 키잉 전략**(아래 § Out of Scope — 사람 결정 필요).

---

# Goal

ADR-MONO-048 D3 의 **Tier 2** — 도메인마다 다르지만 **변이 축이 열거 가능한** 클래스들을 파라미터화해 `libs/java-gateway` 로 올리고, wms·scm·fan 을 이관한다.

step 1 이 "변이 0" 만 다뤄 **모듈 구조 리스크**를 격리했다면, 이 step 의 리스크 예산은 전부 **파라미터화** 한 곳에 쓴다. 즉 이 task 가 틀리면 그것은 곧 파라미터 설계의 실패다.

**이 task 의 성패는 "몇 줄을 지웠나" 가 아니라 "세 도메인의 보안 동작이 정확히 그대로인가" 로만 판정한다** (D6).

---

# Scope

## 대상 (3 도메인 × 4 클래스 = 12 파일 → lib 4 클래스 + 도메인별 설정)

| 클래스 | 변이 축 | 도메인 값 (착수 시 소스로 재확인할 것) |
|---|---|---|
| `IdentityHeaderStripFilter` | strip 집합 | wms 6개 / scm 8개(`X-Token-Type`·`X-Scopes` 추가) / fan — **baseline + add-only** |
| `JwtHeaderEnrichmentFilter` | 주입 헤더 집합 (+ tenant 전파 플래그) | wms/scm/fan 은 `X-Actor-Id` 주입 |
| `TenantClaimValidator` | 3 플래그 `requireTenantMatch` · `allowWildcard` · `entitlementTrust` | **ADR D5 표가 유일한 권위** |
| `OAuth2ResourceServerConfig` | 프로퍼티 접두사 | `@Value` → `@ConfigurationProperties` |

## strip 집합은 **add-only 비대칭**이다 (ADR D3, 협상 불가)

도메인은 baseline 에 헤더를 **추가**할 수 있고, **뺄 수 없다**. 빼기를 허용하는 API 는 `TASK-BE-501`/`502` 가 막 메운 구멍을 **더 예쁜 문법으로 재개방**한다. 파라미터 타입 자체가 이걸 물리적으로 불가능하게 만들 것 (예: `Set<String> additionalHeaders` 를 받아 baseline 과 **합집합**만 취하고, baseline 을 통째로 교체하는 생성자를 두지 않는다).

## `TenantClaimValidator` — 가장 위험한 지점

ADR D5 가 네 도메인의 게이트가 **전부 문서화된 의도**임을 소스로 확증했기 때문에**만** 이 파라미터화가 허용된다. 3 플래그로 네 정책을 정확히 표현하되:

- **wms**: 엄격 일치 + `entitled_domains` dual-accept, **`*` wildcard 없음** (javadoc 이 명시적 선택이라고 적어둠 — 보존)
- **scm**: 일치 + `*` wildcard + entitlement dual-accept
- **fan**: 일치 + `*` wildcard, **entitlement 분기 없음** (fan 은 엔타이틀먼트 평면 **밖**이다 — `ProductCatalog.ENTRIES` 부재. 분기를 넣으면 **프로덕션 보안 필터 안의 죽은 코드**가 된다)

`GatewayErrorCodes.TENANT_MISMATCH` 는 step 1 에서 이미 lib 으로 승격됐다(각 도메인 validator 가 위임 중). 이 task 에서 validator 본체가 lib 으로 올라가면 그 위임 상수는 **자연스럽게 흡수**된다 — 새로 하드코딩하지 말 것.

---

# Out of Scope — `RateLimitConfig` 키잉 전략 (**사람 결정 필요**)

**ADR D7 표의 `RateLimitConfig` 행("변이 축 = key namespace")은 틀렸다.** 착수 전 실측 결과(2026-07-11, step 1 머지 시점 코드):

| | key namespace | **키잉 전략 (라우트가 실제로 바인딩)** |
|---|---|---|
| `wms` | **없음** — 키가 맨 `{ip}:{routeId}` | `key-resolver: "#{@clientIpKeyResolver}"` → **클라이언트 IP 기준** |
| `scm` | `rate:scm-platform` | `key-resolver: "#{@accountKeyResolver}"` → **인증 account subject 기준**(미인증 시 IP 폴백) |
| `fan` | `rate:fan-platform` | `key-resolver: "#{@accountKeyResolver}"` — 동일 |

**죽은 빈이 아니다.** 세 도메인의 `application.yml` 라우트가 각각 위 resolver 를 명시적으로 지목한다. scm 과 fan 의 `RateLimitConfig` 는 `KEY_PREFIX` **문자열 하나**만 다르다(나머지 바이트 동일). **wms 만 이질적이다.**

이건 **살아 있는 행동 비대칭**이고, 어느 쪽으로 통일하든 보안 성질이 바뀐다:

- **wms 가 IP 기준인 결과**: 같은 NAT/사내 egress 뒤의 전 사용자가 **한 버킷을 공유**한다(시끄러운 이웃 하나가 사무실 전체를 스로틀). 반대로 인증된 남용자가 IP 를 돌리면 **계정 단위로는 전혀 제한받지 않는다**.
- **wms 에 네임스페이스가 없는 결과**: wms 게이트웨이가 다른 도메인과 Redis 를 공유하게 되면 `{ip}:{routeId}` 키가 **도메인을 가로질러 조용히 충돌**한다(현재 compose 는 프로젝트별 Redis 라 피해 0 — 잠재 결함).

**왜 여기서 안 고치는가.** ADR D5 가 테넌트 게이트에 대해 세운 기준이 그대로 적용된다 — *네 정책이 전부 **문서화된 의도**임을 확증했기 때문에**만** 파라미터화가 허용된다*. 그런데 **wms 의 IP-only 선택에는 근거가 없다**: javadoc 은 무엇을 하는지(`(clientIp, routeId)` 스코프)만 적을 뿐 **왜 account 기준이 아닌지**를 말하지 않는다 (D5 의 no-wildcard 는 "의도적"이라고 명시돼 있던 것과 대조적). 근거가 없는 축에서 파라미터 기본값을 고르는 것은 **주인 없는 정책 결정**이며, 그것이 정확히 D6 이 금지하는 "리팩토링 옷을 입은 행동 변경" 이다.

**따라서 이 task 에서 `RateLimitConfig` 는:**

1. **공유 가능한 부분만** 올린다 — 세 도메인이 바이트 동일한 `resolveClientIp` / `resolveRouteId` 헬퍼 (X-Forwarded-For 첫 값 → remote address → `unknown`; `GATEWAY_ROUTE_ATTR` 부재 시 `unknown` + WARN, **NPE 금지**). `failOpenRateLimiter` `@Primary` 빈은 이미 lib `FailOpenRateLimiter` 를 쓰고 있다.
2. **`KeyResolver` 빈 자체는 각 서비스에 잔류**시킨다 (ADR D4 "단일 소비자 클래스는 서비스에" 와 일관). wms 의 키는 **맨 그대로** 두어 Redis 키가 한 바이트도 안 바뀌게 한다 — 접두사를 붙이는 것만으로도 **기존 버킷이 전부 리셋**되는 행동 변경이다.
3. wms 의 IP-only + 네임스페이스 부재는 **별도 결정 사항으로 기록**한다. 이 task 의 close 노트가 그 결론을 `tasks/INDEX.md` 로 옮긴다.

---

# Acceptance Criteria

- **AC-1** — `libs/java-gateway` 에 Tier 2 4클래스가 파라미터화되어 존재하고, **테스트가 실제로 실행**된다. `BUILD SUCCESSFUL` 이 아니라 **XML 리포트의 `tests` > 0 / `skipped=0`** 으로 확인할 것 (0건 실행 거짓 GREEN 을 못 거른다).
- **AC-2** — **파라미터 값별 테스트**: 각 도메인 값이 lib 테스트에서 직접 행사된다. 특히 `TenantClaimValidator` 3 플래그 × 세 도메인 조합, `IdentityHeaderStripFilter` 의 **baseline 을 뺄 수 없음**(타입 수준 또는 테스트로 단언).
- **AC-3 — 행동 불변 (Tier 2 형태)**: 각 도메인의 파라미터가 **이전 동작을 정확히 재현**함을 증명. 바이트 동일이 불가능한 tier 이므로 증명 수단은 **테스트**다 — 이관 전 각 도메인 테스트를 실행해 기준선을 잡고, 이관 후 **동일 assertion 이 동일하게 통과**함을 보인다.
- **AC-4 — 소비자 테스트 무수정**: 잔류 게이트웨이 테스트는 **import/javadoc 만** 바뀐다. **assertion 을 고쳐야 통과하는 테스트가 하나라도 나오면 그것은 행동 변경이며, 그 순간 STOP** — 파라미터 설계가 틀린 것이지 테스트가 틀린 게 아니다 (ADR D6·§1.3 `failsOpenOnAnyReactiveError` 사고).
- **AC-5 — mutation check**: 결함을 재주입해 스위트가 **무는지** 확인. 최소 3종 — ① wms 에 `*` wildcard 허용(→ wms 테스트 RED) ② fan 에 entitlement 분기 추가(→ 죽은 코드 검출) ③ strip baseline 에서 헤더 1개 제거(→ RED). **통과하는 가드 ≠ 무는 가드**.
- **AC-6** — lib + 게이트웨이 3개 전체 스위트 **0 실패 / 0 skipped**.
- **AC-7 — `RateLimitConfig` 키잉 전략 무변경**: wms 의 Redis 키가 **바이트 그대로**(`{ip}:{routeId}`, 접두사 없음)이고 scm/fan 이 `accountKeyResolver` 를 계속 바인딩함을 확인. **이 AC 가 실패하는 방향으로 "정리" 하지 말 것** — § Out of Scope.
- **AC-8** — 순감소 라인 수 측정 (step 1: `projects/` −2,515 / `libs/` +1,150).

---

# Related Specs

- [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — § D3(Tier 2 축) · **§ D5(테넌트 게이트 4정책 = 유일한 권위)** · § D6(증명 의무) · § D7(로드맵)
- [`platform/api-gateway-policy.md`](../../platform/api-gateway-policy.md) — 라이브러리는 이 정책을 **구현**할 뿐 **재정의하지 않는다**
- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) — § Catalog(`libs/java-gateway` 항목) · § Forbidden(도메인 로직 금지)
- ADR-MONO-019 § D5/D6 — 테넌트 게이트의 상위 권위

# Related Contracts

없음. **외부 계약 표면 변경 0** — 이 task 가 계약을 건드린다면 그것은 행동 변경이므로 D6 위반이다.

---

# Edge Cases

- **`@Component` 스캔** — lib 의 새 필터/설정은 서비스 base package 밖이다. step 1 이 `scanBasePackages` + `GatewayComponentScanTest` 로 이미 봉인했으나, **새로 추가되는 lib 빈이 그 테스트에 실제로 걸리는지** 확인할 것(테스트가 클래스 목록을 열거한다면 갱신 필요).
- **필터 순서 불변식** — strip → requestId → retryAfter. step 1 은 `IdentityHeaderStripFilter` 가 도메인 잔류였기에 각 게이트웨이에 `GatewayFilterOrderingTest` 를 뒀다. 이 task 에서 strip 필터가 lib 으로 올라가면 **그 테스트의 의미가 바뀐다** — 삭제하지 말고 **lib 경계를 가로지르는 순서 단언으로 유지**할 것.
- **`@ConfigurationProperties` 전환** — `@Value` 에서 옮길 때 **프로퍼티 키 문자열이 바뀌면 기존 배포 설정이 조용히 무시**된다(기본값으로 부팅 → 보안 설정이 소리 없이 느슨해짐). 키를 보존하거나, 바뀐다면 각 도메인 `application.yml` 을 같은 커밋에서 갱신하고 **부팅 테스트로 값이 실제로 바인딩됨을 단언**할 것.
- **`api` 금지** — lib 은 `implementation` 만. 컴파일 에러를 `api` 로 지우면 WebFlux 가 전이 누출된다(MONO-044a 의 거울상).

# Failure Scenarios

- **파라미터가 설정 언어로 번진다** — 플래그가 늘어나 "설정으로 뭐든 되는" 클래스가 되면 도메인 정책이 코드에서 사라지고 yml 로 흩어진다. ADR 이 명시한 **부정적 결과**. 축은 D3 표의 것으로 **고정**하고, 새 축이 필요해 보이면 그것은 이 task 의 신호가 아니라 **ADR 재검토 신호**다.
- **테스트를 고쳐서 통과시킨다** — AC-4 가 이걸 잡는다. 통과시키려 고친 테스트는 리팩토링 옷을 입은 행동 변경이다.
- **`RateLimitConfig` 를 "김에 통일"한다** — § Out of Scope. 세 도메인의 rate-limit 키를 한 커밋에서 바꾸면, 그건 정리가 아니라 **주인 없는 보안 정책 결정**이다.
- **fan 에 entitlement 분기를 넣는다** — "세 도메인을 대칭으로" 라는 미학적 충동이 **프로덕션 보안 필터에 죽은 코드**를 심는다. fan 은 엔타이틀먼트 평면 밖이다(이미 한 번 오탐으로 발굴된 적 있음 — 재발굴 금지).
