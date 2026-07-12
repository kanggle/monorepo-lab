# Task ID

TASK-MONO-357

# Title

`finance` / `erp` 게이트웨이 신설 — `libs/java-gateway` 소비. **`TASK-MONO-347` direction A 해소** (ADR-MONO-048 D7 step 4)

# Status

done

# Owner

monorepo

# Task Tags

- feature
- gateway
- security
- shared-library

---

# Dependency Markers

- **선행 (머지됨)**: `TASK-MONO-351`(모듈+Tier1, PR #2417) · `TASK-MONO-355`(Tier2, PR #2425) · `TASK-MONO-356`(ecommerce, PR #2427 `7967ac12d`). **이 셋이 없으면 이 task 는 클래스 15개 손복사였다** — 그 비용이야말로 finance/erp 가 게이트웨이를 못 가진 이유였고, ADR-MONO-048 이 제거한 것이다.
- **선행 (ACCEPTED)**: [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) § D7 step 4. **`TASK-MONO-347` 의 AC-0(방향 A vs B)은 사람의 몫이었고, 사용자가 이 ADR 을 ACCEPT 함으로써 direction A 를 택했다** — 에이전트의 자의적 선택이 아니다.
- **해소 대상**: [`TASK-MONO-347`](TASK-MONO-347-gateway-policy-finance-erp-drift.md) — 정책(`api-gateway-policy.md`: *"Every project … has a gateway service"* · *"All external traffic **MUST** pass through the gateway"*)과 코드(게이트웨이 부재)와 계획(`PROJECT.md` 가 게이트웨이를 **v1 IN** 으로 기재)이 서로 다른 말을 하는 3자 드리프트.
- **범위 밖**: **servlet 서비스의 validator 중복 제거** — 아래 § 별건 참조. 별도 **ADR 필요**.

---

# Goal

`platform/api-gateway-policy.md` 가 예외 없이 요구하는 게이트웨이를 finance / erp 에 세워, **정책·계획·코드 3자 드리프트를 코드 쪽에서 닫는다**(direction A).

**이 task 의 값어치는 "게이트웨이 2개 추가" 가 아니라 D7 전체의 논거 검증이다.** ADR 은 *"게이트웨이를 세우는 데 클래스 15개를 손복사해야 했다는 것이 finance/erp 가 게이트웨이를 못 가진 이유이며, 그 비용이야말로 이 라이브러리가 제거하는 것"* 이라 주장했다. 이 task 가 그 주장을 **실측**한다 — 새 게이트웨이가 route yml + 프로퍼티 + 도메인 정책 선언부(2 클래스)만으로 서면 논거는 참이고, 그 이상이 필요하면 라이브러리가 아직 덜 된 것이다.

---

# Scope

## 착수 전 실측 (2026-07-12, `7967ac12d`)

- **finance**: `account-service` · `ledger-service` (2)
- **erp**: `approval-service` · `masterdata-service` · `notification-service` · `read-model-service` (4)
- 두 프로젝트 모두 `gateway-service` **모듈 부재**(`settings.gradle` · 소스 트리 · `specs/services/` 전부).
- **런타임 외부 표면 0** — 현재 console-bff **내부 호출 전용**(`FINANCE_BASE_URL` / `ERP_BASE_URL` 직접 지정).
- 6개 서비스 전부 `ServiceLevelOAuth2Config` 로 게이트웨이 검증 체인을 **서비스 안에 복제**해 뒀다(자기설명 주석 존재) — 게이트웨이가 없었기 때문. 게이트웨이가 생겨도 **이건 그대로 둔다**(이중 방어; 제거는 별개 결정).

## 구현

1. **`libs/java-gateway` 소비하는 게이트웨이 2개 신설** — wms/scm/fan 을 참조 구현으로.
   - `GatewayServiceApplication` — **`scanBasePackages` 에 `com.example.apigateway` 명시**(빠뜨리면 필터가 조용히 미등록. 단위테스트 통과·빌드 GREEN·부팅 성공·**보안 체인 없음**).
   - `GatewayIdentityConfig` — strip 추가분(있으면) + enrichment 매핑. **strip 은 add-only**.
   - `OAuth2ResourceServerConfig` — 프로퍼티 접두사(`finance.oauth2.*` / `erp.oauth2.*`) + `tenantGate()` seam.
   - `RateLimitConfig` — **wms/scm/fan 중 무엇을 복사할지 결정해야 한다. § 함정① 참조.**
   - route yml — 각 서비스로.
2. **`settings.gradle` include** + 각 `build.gradle`(`implementation project(':libs:java-gateway')`, **`api` 금지**).
3. **`specs/services/gateway-service/`** 신설(두 프로젝트) — `architecture.md` 의 `Service Type` 선언 필수(미선언 = HARDSTOP-10).
4. **`PROJECT.md` v1 IN 기재와 코드 정합** 확인 — 드리프트의 세 번째 축.
5. **인프라**: docker-compose(게이트웨이 hostname 라벨 — `TEMPLATE.md` § Local Network Convention, `PORT_PREFIX` 금지) · **CI `dorny/paths-filter` 에 항목 추가**(negation 금지, pure-positive `code-changed` AND-합성 — MONO-074/075 quirk) · **`nightly-e2e.yml` 4곳 갱신**(ci.yml 만 고치면 nightly RED — 기록된 드리프트).
6. **`JwksHealthProbe` 를 lib 으로 승격** (아래 § 동반 정리) — 새 게이트웨이 2개가 이걸 원하고, 지금이 올바른 시점이다.

## 동반 정리 — `JwksHealthProbe` (ADR § D4 정정본 참조)

scm/fan 이 **본문 100% 동일** 사본을 갖고 있고, ADR § D4 가 이를 "단일 소비자" 라 적은 것은 **틀렸다**(소비자 2, 곧 4).

**순진한 이관 금지**: `@Component` 인데 **wms 가 lib 패키지를 스캔**하므로 그대로 옮기면 **wms 가 가진 적 없는 JWKS 프로브를 등록**한다 = dedup 을 명분으로 한 조용한 행동 변경(D6 위반). ⇒ **lib 에서는 `@Component` 를 떼고**, 원하는 도메인이 `@Bean` 으로 배선한다(scm/fan/finance/erp — **wms 는 배선하지 않는다**). **mutation**: lib 에 `@Component` 를 되붙이면 wms 컨텍스트에 빈이 늘어남을 테스트가 잡아야 한다.

---

# 별건 (이 task 범위 밖 — **ADR 필요**)

**`AllowedIssuersValidator` / `TenantClaimValidator` 가 finance/erp servlet 서비스 6곳에 각각 사본으로 또 있다**(12파일). erp 사본은 lib 것과 **본문 동일**. ⇒ 이 두 보안 클래스의 실제 사본 수는 **각각 10개**였고, **ADR-MONO-048 § 1.1 은 게이트웨이 디렉터리 밖을 보지 않았다**(§1.1 에 정정 노트 삽입 완료).

**`libs/java-gateway` 로는 공유할 수 없다** — 두 클래스는 프레임워크 중립이지만(reactor/WebFlux 참조 0건) **모듈이 아니다**: `implementation` 의존은 소비자의 **런타임** 클래스패스에 올라가므로, servlet 서비스가 이 모듈을 쓰면 WebFlux+SCG 를 함께 끌어온다(= D1 이 격리하는 바로 그 누출, MONO-044a 의 거울상).

⇒ **프레임워크 중립 모듈**이 필요하고, 그건 새 shared-library 결정이므로 `platform/shared-library-policy.md` § Change Rule 에 의해 **ADR 게이트**에 걸린다(ADR-MONO-048 을 낳은 바로 그 규칙). **이 task 에서 하지 말 것.**

---

# Acceptance Criteria

- **AC-1 — 논거 실측**: 새 게이트웨이 2개가 실제로 필요로 한 **신규 코드 라인 수**를 측정해 기록한다. ADR 의 주장("route yml + 프로퍼티 몇 개")이 참인지 거짓인지 숫자로 답할 것. **거짓이면 그것이 이 task 의 산출물이다** — 라이브러리가 덜 된 지점을 가리킨다.
- **AC-2 — 컴포넌트 스캔 가드**: 두 게이트웨이 각각 `GatewayComponentScanTest`. **mutation: 스캔에서 lib 제거 → 빌드 RED.**
- **AC-3 — 테넌트 게이트 정책 명시**: finance/erp 각각의 게이트 정책(wildcard? entitlement?)을 **소스 근거와 함께** 결정하고, 도메인 테스트가 **허용하는 것과 거부하기로 한 것 둘 다** 단언한다(355 의 교훈: 스위트는 늘 전자만 못박고 후자를 빠뜨린다). 근거를 못 찾으면 **STOP** — 기본값 선택이 주인 없는 정책 결정이 된다(D5).
- **AC-4 — strip/enrich 집합**: 각 도메인 다운스트림이 **실제로 읽는** 신원 헤더를 census 하고(**`-h` 금지 — 356 의 grep 아티팩트 교훈: 파일명을 지우면 뒤의 경로 필터가 무력화된다**), strip 집합이 그 전부를 덮는지 확인. 덮지 않으면 그것은 **위조 가능한 신원 헤더**다(356 의 `X-Seller-Scope` 와 동일 클래스).
- **AC-5 — `JwksHealthProbe`**: lib 승격 + `@Component` 제거 + 도메인 `@Bean` 배선. **wms 는 배선하지 않는다.** mutation 으로 확인.
- **AC-6 — 격리**: `libs:java-gateway` 소비자는 게이트웨이만. servlet 서비스 `runtimeClasspath` 의 SCG/WebFlux **0건** 유지.
- **AC-7 — 드리프트 3축 정합**: 정책(수정 불필요) · `PROJECT.md`(v1 IN 유지, 이제 참) · 코드(게이트웨이 존재). `TASK-MONO-347` 이 닫힌다.
- **AC-8 — CI**: `paths-filter` 항목 추가(**negation 금지**) + **`nightly-e2e.yml` 4곳** 갱신. 누락 시 nightly RED(기록된 드리프트).
- **AC-9** — 전체 스위트 0 실패 / 0 skipped.

---

# Related Specs

- [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — § D7 step 4 · § D4(정정본: `JwksHealthProbe`) · § 1.1(정정본: servlet 사본 12개) · § D5 · § D6
- [`platform/api-gateway-policy.md`](../../platform/api-gateway-policy.md) — 이 task 가 코드를 맞추려는 정책
- [`TEMPLATE.md` § Local Network Convention](../../TEMPLATE.md) — 게이트웨이 hostname 등록 (`PORT_PREFIX` 금지)
- `projects/{finance,erp}-platform/PROJECT.md` — 게이트웨이 v1 IN 기재

# Related Contracts

없음(신설 엣지, 기존 계약 표면 불변). **단, 게이트웨이 도입으로 신원 헤더가 새로 주입되므로 다운스트림이 읽는 헤더 집합이 바뀌면 그건 계약 변경이다** — AC-4 가 그걸 잡는다.

---

# Edge Cases

- **① `RateLimitConfig` 를 무엇으로 복사할 것인가** — wms(IP 기준·네임스페이스 없음) vs scm/fan(account subject 기준·`rate:<domain>` 접두사)이 **다르고, wms 쪽 근거는 문서에 없다**(MONO-355 § Out of Scope, 사람 결정 대기). **새 게이트웨이는 근거 있는 쪽(scm/fan 형태)을 택하고 그 선택을 명시 기록할 것** — 근거 없는 쪽을 새 코드에 복제하면 미해결 결정을 확산시키는 것이다.
- **② 외부 표면이 0인데 게이트웨이를 세우는 의미** — 지금은 console-bff 내부 호출뿐이다. 게이트웨이는 (a)정책 정합 (b)외부 노출 시 즉시 사용 가능 (c)신원 헤더 strip→enrich 이중방어를 준다. **console-bff 를 게이트웨이 경유로 바꿀지는 별개 결정** — 바꾸면 라우팅·타임아웃·헬스 표면이 전부 움직인다. **이 task 에서 하지 말 것**(그러면 이건 리팩토링이 아니라 마이그레이션이다).
- **③ `@api` 금지** — MONO-044a 거울상.
- **④ HARDSTOP-10** — `specs/services/gateway-service/architecture.md` 에 `Service Type` 미선언 시 정지.

# Failure Scenarios

- **"거의 공짜" 라는 ADR 의 주장을 검증 없이 반복한다** — AC-1 이 숫자를 요구하는 이유다. 실제로 비싸다면 그 사실이 산출물이다.
- **근거 없는 정책을 새 코드에 복제한다** — § 함정①. 미해결 결정의 확산은 정리가 아니다.
- **`JwksHealthProbe` 를 순진하게 옮긴다** — wms 가 조용히 새 빈을 얻는다(D6 위반).
- **servlet validator 중복을 "김에" 정리한다** — § 별건. **ADR 게이트**에 걸린다(HARDSTOP-09). 게이트가 실제로 작동한 기록이 이미 둘 있다(MONO-349, 그리고 지금 이것).
