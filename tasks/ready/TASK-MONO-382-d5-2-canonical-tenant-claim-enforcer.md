# Task ID

TASK-MONO-382

# Title

`ADR-MONO-049` **D5-2** — `libs/java-security-servlet` + 정경 `TenantClaimEnforcer`. **13개 사본에 서로 다른 body 가 8개이고, 그 차이 중 최소 하나는 보안 경계다**

# Status

ready

# Owner

monorepo

# Task Tags

- shared-library
- security
- refactor
- adr-followup

---

# Dependency Markers

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED`, 범위 A**. § D1 · § D3 · **§ D5 단계 2** · § 6.
- **선행 (머지됨)**: `TASK-MONO-378` (D5-1 — 두 중립 validator 가 `libs/java-security` 로 이관됨, squash `3a8f02c59`). **D5-1 이 만든 세 단언**(`assertClasspathNeutrality` · `assertNoServletOnReactiveEdge` · `assertNoApiOnSharedLibs`)**은 이 task 후에도 GREEN 이어야 한다.**
- **후속**: D5-3 (finance) — **이 task 가 랜딩한 뒤** 티켓팅.
- **⚠️ 직렬**: `libs/` + `settings.gradle` 을 건드린다. **병렬 착수 금지.**

---

# Goal

`libs/java-security-servlet` — **ADR 전체에서 유일한 신규 모듈** — 을 만들고 정경 `TenantClaimEnforcer` 를 넣는다. **이 단계에서는 소비자가 없다.** 사본 삭제는 D5-3 부터다.

## 왜 별도 모듈인가 (§ D1 — 이미 결정됨, 재론 금지)

`TenantClaimEnforcer` 는 `OncePerRequestFilter` — **servlet 바인딩**이다. `libs/java-security` 에 넣고 servlet 의존을 `compileOnly` 로 다는 "지름길" 은 **컴파일된다.** 그리고 **reactive 게이트웨이 6개의 런타임 클래스패스에 servlet 바인딩 클래스를 올린다** — 거기엔 servlet API 가 없다. 무언가 그 클래스를 스캔하거나 리플렉션할 때까지는 동작한다. 그 다음엔 **부팅 시점에, 엣지에서, 프로덕션에서** 터진다.

> ***"아직 아무도 로드하지 않아서 동작하는 클래스"* 가 이 작업 전체가 쫓아온 실패 클래스다.** 모듈 하나는 `settings.gradle` 한 줄이다. **그 줄을 써라.**

**`D5-1` 이 이미 그 가드를 깔아뒀다**: `assertNoServletOnReactiveEdge` 가 `libs:java-gateway` 의 런타임 클래스패스에 servlet 아티팩트가 오면 **RED** 다. 누군가 `java-gateway` 를 `java-security-servlet` 에 의존시키는 순간 잡힌다. **그 단언은 정확히 이 task 를 위해 만들어졌다.**

---

## 🔴 그런데 "정경 하나를 뽑는다" 가 순진한 작업이 아니다

**실측 (`origin/main` `3a8f02c59`): 13개 사본, 정규화 후 서로 다른 body 8개.** 그리고 **같은 프로젝트 안에서도 갈렸다**:

| body | 서비스 |
|---|---|
| `00a3b3fa` | erp/approval · erp/masterdata |
| `063a5954` | erp/notification · erp/read-model |
| `96af0f23` | fan/artist · fan/community · fan/notification |
| **`f77ab80a`** | **fan/membership** ← 같은 프로젝트인데 다르다 |
| `df3a4413` | finance/account · finance/ledger |
| `44a7d6b2` | scm/inventory-visibility |
| `aff6eba7` | scm/demand-planning |
| `f5f855fe` | scm/procurement |

### 그리고 그 차이 중 최소 하나는 **보안 경계**다

`fan/artist` ↔ `fan/membership` 실측 diff:

```java
+ private static final String INTERNAL_PREFIX = "/internal/";
...
- return PublicPaths.isPublic(request);
+ String uri = request.getRequestURI();
+ return PublicPaths.isPublic(request)
+         || (uri != null && uri.startsWith(INTERNAL_PREFIX));
```

**`fan/membership` 은 `/internal/**` 에서 테넌트 강제를 건너뛴다. 나머지 셋은 안 그런다.**

⇒ 정경을 순진하게 하나 고르면 **둘 중 하나가 일어난다**:

- artist 판을 쓰면 → **membership 의 `/internal/**` 이 갑자기 테넌트 강제를 받는다** → 서비스 간 호출이 깨진다.
- membership 판을 쓰면 → **나머지 12개 서비스의 `/internal/**` 이 열린다** → **보안 회귀**. 조용히.

**`TASK-MONO-375` 가 확인한 *"라이브 결함 0"* 은 각 사본이 자기 게이트웨이 정책을 미러링한다는 뜻이었다. 이 8개 body 는 그것과 다른 축이다** — servlet 필터의 **경로 예외**이고, 아무도 그것들을 대조한 적이 없다.

> **8개를 "포매팅 차이" 로 가정하지 말 것. 하나는 이미 아니었다.**

---

# Scope

## In Scope

1. **8개 body 를 전수 대조하고 축을 분류한다** — 무엇이 **포매팅**이고 무엇이 **정책**인가. **정책 축은 파라미터가 되고, 포매팅 축은 사라진다.** 최소 하나(`/internal/**` 예외)는 정책이다.
2. **`libs/java-security-servlet`** 신규 모듈 (`settings.gradle` 한 줄).
3. **정경 `TenantClaimEnforcer`** — 발견된 정책 축을 **builder 로 파라미터화**(§ D4; `TenantClaimValidator` 가 이미 그 형태다). **모든 스위치는 닫힌 상태가 기본**(MONO-355) — *공유 보안 클래스의 기본값이 게이트를 열면, 오타 하나로 전부 열린다*.
4. **모듈 자신의 스위트** — 각 스위치에 대해 **무엇을 허용하는가**와 **무엇을 거부하는가**를 **둘 다** 단언한다(§ 6 V5). *게이트가 무엇을 받아들이는지만 기록하는 스위트는, 그것이 무엇을 거부하는지가 바뀌어도 알아채지 못한다.*
5. **`assertClasspathNeutrality` 신설** — `libs:java-security-servlet` 용. **WebFlux 0 · SCG 0** (servlet 은 **허용** — 이 모듈은 servlet 바인딩이 목적이다). **mutation 필수.**
6. **D5-1 의 세 단언이 여전히 GREEN** — 특히 `assertNoServletOnReactiveEdge`.

## Out of Scope

- **소비자 배선** — **이 단계에서는 소비자가 0이다.** 13개 서비스가 정경을 채택하고 사본을 지우는 것은 **D5-3 ~ D5-8**.
- **사본 삭제** — 49개 그대로.
- **`libs/java-gateway` 가 이 모듈에 의존하는 것** — **절대 금지.** reactive 엣지는 servlet 필터를 **볼 수조차 없어야** 한다(§ D1). `assertNoServletOnReactiveEdge` 가 지킨다.
- **`api` 로 재수출** — § D2. `assertNoApiOnSharedLibs` 가 지킨다.

---

# Acceptance Criteria

- [ ] **AC-1 (8개 body 의 축이 분류됐다)** — 13 사본의 정규화 body 8개가 **전수 대조**됐고, 각 차이가 **포매팅** 또는 **정책** 으로 분류돼 기록됐다. **정책 축은 하나도 빠짐없이 정경의 파라미터가 됐다.** *(최소 하나는 정책이다: `fan/membership` 의 `/internal/**` 예외.)*
- [ ] **AC-2 (닫힌 상태가 기본)** — 정경의 모든 스위치가 **기본 닫힘**. 파라미터 없는 생성은 **가장 엄격한** 게이트를 만든다. `/internal/**` 예외는 **명시적으로 켜야** 한다.
- [ ] **AC-3 (거부도 단언한다 — § 6 V5)** — 모듈 스위트가 각 스위치에 대해 **허용 + 거부**를 둘 다 단언한다. **`/internal/**` 예외가 꺼진 상태에서 `/internal/x` 가 실제로 **거부되는지** 단언한다** — 켠 상태만 테스트하면 그 스위치가 사라져도 아무도 모른다.
- [ ] **AC-4 (신규 모듈 격리)** — `libs:java-security-servlet` 의 `runtimeClasspath`: **WebFlux 0 · SCG 0** (servlet 은 허용). **mutation**: `spring-boot-starter-webflux` 주입 → **RED**. **주입 적용 여부를 결과 읽기 전에 확인.**
- [ ] **AC-5 (reactive 엣지는 여전히 servlet 을 못 본다)** — `assertNoServletOnReactiveEdge` **GREEN**. **mutation**: `libs/java-gateway` 를 `java-security-servlet` 에 의존시키면 → **RED**. *(D5-1 이 이 단언을 만든 이유가 바로 이 순간이다.)*
- [ ] **AC-6 (소비자 0 · 사본 0 삭제)** — 이 단계는 **아무 서비스도 안 건드린다**. 사본 **18 / 18 / 13 = 49 그대로**. **줄었으면 범위를 넘었다.**
- [ ] **AC-7** — `assertNoApiOnSharedLibs` **GREEN** (신규 모듈도 `api` 로 재수출되지 않는다).

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D1 · § D2 · § D3 · § D4 · **§ D5 단계 2** · § 6 (V2 · V5)
- `docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md` § D1 — servlet/reactive 격리의 출처
- `libs/java-security/src/main/java/com/example/security/oauth2/TenantClaimValidator.java` — **builder + 닫힌 기본값의 참조 구현**(MONO-355)
- `settings.gradle` · `build.gradle`(루트, `assertNoApiOnSharedLibs`)
- `TASK-MONO-378` — D5-1, 이 task 가 딛는 땅

# Related Contracts

없다 — 이 단계는 소비자가 0이므로 런타임 표면 변화 없음.

---

# Edge Cases

- **`PublicPaths` 는 프로젝트마다 다른 클래스다.** 정경 Enforcer 가 그것을 직접 참조하면 라이브러리가 서비스 클래스에 손을 뻗는 것이다(`shared-library-policy.md` § Dependency Rule 위반). **인터페이스/`Predicate<HttpServletRequest>` 로 주입받는 형태**를 검토할 것 — 이게 이 task 의 진짜 설계 문제다.
- **`compileOnly` 지름길을 다시 제안하지 말 것** — § D1 이 이미 이름을 붙여 기각했다.
- **8개 body 를 "포매팅" 으로 가정하지 말 것** — 하나는 이미 보안 경계였다. 이 저장소가 `grep -c` 로 Javadoc 을 세고 빈 출력을 "없음" 으로 읽어온 횟수를 생각하면, **전수 대조 없이 내리는 결론은 가설이다.**

# Failure Scenarios

- **정경을 하나 고르고 8개 차이를 뭉갠다** → **`/internal/**` 이 12개 서비스에서 열리거나(보안 회귀), membership 의 서비스 간 호출이 깨진다.** 둘 다 조용하다. Guard: AC-1 · AC-3.
- **스위치를 열린 기본값으로 만든다** → 오타 하나가 13개 엣지를 연다. Guard: AC-2.
- **켠 상태만 테스트한다** → 스위치가 사라져도 스위트가 초록이다. Guard: AC-3(**거부를 단언한다**).
- **`java-gateway` 를 이 모듈에 의존시킨다**(클래스 이름이 공유처럼 보여서) → reactive 엣지에 servlet 이 올라온다 = `TASK-MONO-044a` 재현. Guard: AC-5 — **D5-1 이 이미 그 단언을 심어뒀다.**

---

# Provenance

`ADR-MONO-049` § D5 단계 2. `TASK-MONO-378`(D5-1) 이 랜딩해 게이트가 열렸다.

**티켓을 쓰려고 13개 사본을 실제로 대조했더니, ADR 이 세지 않은 축이 나왔다**: 정규화 후 서로 다른 body 가 **8개**이고, **같은 프로젝트(fan) 안에서도 갈렸으며**, 그 차이 중 하나는 **`/internal/**` 에서 테넌트 강제를 건너뛰는 것** — 즉 **보안 경계**다.

`TASK-MONO-375` 가 확인한 *"라이브 결함 0"* 은 **servlet 정책이 자기 게이트웨이를 미러링한다**는 축이었다. **이건 다른 축이고, 아무도 본 적이 없다.**

**이 혈통의 다섯 번째 실측 정정이고, 처음으로 *보안* 축이다.**

분석=Opus 4.8 / 구현 권장=**Opus** (8개 body 의 정책/포매팅 분류가 본체다. 잘못 뭉개면 **컴파일도 되고 테스트도 초록인데 12개 서비스의 `/internal/**` 이 열린다**).
