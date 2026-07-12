# Task ID

TASK-MONO-382

# Title

`ADR-MONO-049` **D5-2** — `libs/java-security-servlet` + 정경 `TenantClaimEnforcer`. **13개 사본에 서로 다른 body 가 8개이고, 그 차이 중 최소 하나는 보안 경계다**

# Status

review

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

- [x] **AC-1 (8개 body 의 축이 분류됐다)** — ✅ 13 사본 전수 대조. **8개 body → 정책 축 3개**로 환원되고 나머지는 전부 형식이었다(줄바꿈 · `@Value` 완전수식 vs import · 지역 재선언한 claim 상수).
  ⚠️ **첫 해시가 8을 준 것 자체가 거친 술어였다** — `tr -d ' \t'` 가 **개행을 안 지워** 줄바꿈 차이를 다른 body 로 셌다. 개행까지 지워도 8이 나온 뒤에야 **토큰 차이**임이 확인됐고, 그제서야 축을 뽑을 수 있었다.
  **정책 축 3개** = ①**면제 경로**(`PublicPaths.isPublic` **10** / `+/internal/**` **1**=fan/membership / **`/actuator/` 전체** **2**=scm demand-planning·inventory-visibility) ②**`entitled_domains`**(erp4·finance2·scm3 = **9** / fan **0**) ③**wildcard**(**13/13**). **셋 다 정경의 파라미터가 됐다.**
- [x] **AC-2 (닫힌 상태가 기본)** — ✅ `forTenant(x).build()` = **정확 일치만, 면제 0, wildcard 거부**. **wildcard 는 13개 전부가 켜고 있는데도 기본이 꺼짐** — *기본값은 다수결이 아니라 **누군가 잊었을 때 얻는 것**이고, 그때 원하는 건 닫힌 게이트다.*
- [x] **AC-3 (거부도 단언한다 — § 6 V5)** — ✅ **19 tests / 0 skipped / 0 failures.** 각 스위치를 **켠 상태 + 끈 상태** 둘 다 단언한다.
  **mutation ✅ (이게 AC-3 의 증명이다)**: wildcard 기본값을 `true` 로 뒤집으면 → **19 중 2 FAIL**(*"OFF 면 거부한다"* 단언 둘). 면제 기본값을 `/actuator/` 로 열면 → **1 FAIL**(*"기본값은 `/actuator/health` 조차 면제 안 한다"*). **켠 상태만 테스트했다면 두 mutation 모두 초록으로 통과했을 것이다** — `TASK-MONO-355` 가 wms 에서 발견한 그 구멍.
- [x] **AC-4 (신규 모듈 격리)** — ✅ **신설 `assertClasspathNeutrality`**(`libs:java-security-servlet`): 50 아티팩트, WebFlux/SCG/reactor-netty **0** (servlet 은 **허용** — 이 모듈은 servlet 바인딩이 목적이다). 빈 클래스패스면 **exit 2**(공허 통과 금지).
  **mutation ✅**: `spring-boot-starter-webflux` 주입 → **RED**(`spring-webflux` · `reactor-netty-*` 를 이름으로 지목).
- [x] **AC-5 (reactive 엣지는 여전히 servlet 을 못 본다)** — ✅ `assertNoServletOnReactiveEdge` **GREEN**.
  **mutation ✅**: `libs/java-gateway` 를 `java-security-servlet` 에 의존시키면 → **RED**, 그리고 **D5-1 이 써둔 진단 메시지가 원인을 이름으로 지목한다**: *"가장 유력한 원인: 이 모듈이 `libs:java-security-servlet` 에 의존하게 됐다."* **예언한 실수를 예언한 문장이 잡았다.**
- [x] **AC-6 (소비자 0 · 사본 0 삭제)** — ✅ 사본 **18 / 18 / 13 = 49 그대로**. `projects/` **한 파일도 안 건드렸다**(`git diff --stat origin/main -- projects/` = 비어 있음). `project(':libs:java-security-servlet')` 실제 선언 **0건**(grep 히트 3건은 전부 주석·에러메시지 — **매치를 의존으로 읽지 않고 확인했다**).
- [x] **AC-7** — ✅ `assertNoApiOnSharedLibs` **GREEN**. 전체 `./gradlew check` **BUILD SUCCESSFUL**, FAILED 태스크 **0**, 단언 4개 전부 실행·통과.

## 착수 후 드러난 것 — **두 번째 보안 축**

**AC-1 을 하다가 `fan/membership` 의 `/internal/**` 말고 하나가 더 나왔다.**

`scm/demand-planning` 과 `scm/inventory-visibility` 는 **`PublicPaths` 를 아예 참조하지 않는다**:

```java
protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");   // PublicPaths.isPublic() 이 아니다
}
```

**형제인 `scm/procurement` 의 `PublicPaths` 는 `/actuator/health`·`/info`·`/prometheus` 3개만 면제한다.** 저 둘은 **`/actuator/` 전체** — `env`·`beans`·`heapdump`·`loggers` 포함. **더 넓다.**

### 🟢 그러나 라이브 취약점은 아니다 — 과장하면 진짜 논거까지 죽는다

두 서비스의 `SecurityConfig` 는 `health`/`info`/`prometheus` 만 `permitAll()` 하고 **`anyRequest().denyAll()`** 로 끝난다. `/actuator/env` 는 **Spring Security 가 먼저 막는다**. **실측했다.**

**하지만 선을 지키는 곳과 면제를 정의하는 곳이 다르다.** 누군가 `.requestMatchers("/actuator/**").permitAll()` 을 넣으면 — 평범해 보이는 변경이다 — **그 두 서비스의 모든 actuator 엔드포인트에서 테넌트 게이트가 조용히 사라진다.** 그리고 **그 둘은 `TenantClaimValidator` 를 안 갖는다**(§1.7) — **Enforcer 가 servlet 계층의 유일한 테넌트 검사다.**

⇒ **§ 1.8 에 기록했다. D5-5(scm)가 이 결정을 명시적으로 내려야 한다** — `PublicPaths::isPublic` 로 **좁힐 것인가**(더 안전하지만 **행동 변경**), 아니면 현재 면제를 재현할 것인가. **조용히 결정하면 안 된다.**

> **13개 사본이 갈라진 건 누가 그렇게 결정해서가 아니다.** 손으로 유지되는 파일 13개를 **아무도 대조한 적이 없어서**다. **추출이 그 불일치를 보이게 만들었다** — 이 ADR 의 논지가 주장이 아니라 **증거로** 도착한 셈이다.

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
