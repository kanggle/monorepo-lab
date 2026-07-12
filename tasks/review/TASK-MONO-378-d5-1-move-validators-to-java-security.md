# Task ID

TASK-MONO-378

# Title

`ADR-MONO-049` **D5-1** — 두 중립 validator 를 `libs/java-gateway` → `libs/java-security` 로 이관. **ADR 이 "새 배선 없음" 이라 적은 단계인데, 실제로는 게이트웨이 3개가 컴파일이 깨진다**

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

- **근거**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`ACCEPTED` 2026-07-13, 범위 A** (`TASK-MONO-377`). § D1 · § D2 · § D3 · **§ D5 단계 1** · § 1.7 · § 6.
- **선행 (머지됨)**: `TASK-MONO-377` (ACCEPT + 범위 A + § 1.7 실측).
- **후속**: **D5-2** (`libs/java-security-servlet` + 정경 `TenantClaimEnforcer`). **이 task 가 랜딩한 뒤에 티켓팅한다** — D5 8단계는 전부 `libs/` + `settings.gradle` 을 건드려 **구조적으로 직렬**이다.
- **⚠️ 단일 worktree 직렬화**: 이 시리즈는 같은 공유파일을 연속으로 건드린다. **병렬 착수 금지.**

---

# Goal

`ADR-MONO-049` § D5 의 **첫 단계**. `AllowedIssuersValidator` 와 `TenantClaimValidator` 를 **reactive 게이트웨이 라이브러리에서 프레임워크 중립 라이브러리로** 옮긴다. 그래야 **20개 servlet 서비스가 그것들을 소비할 수 있다** — 지금은 소비하려면 `libs/java-gateway` 를 끌어와야 하고, 그러면 WebFlux + Spring Cloud Gateway 가 servlet 서비스의 런타임 클래스패스에 올라온다(§ D1 이 격리하는 그 누출, `TASK-MONO-044a` 의 재현).

**servlet 서비스는 하나도 안 건드린다.** 사본 삭제는 D5-3 부터다. 이 단계는 **집을 짓는 것**이다.

## 🔴 그런데 ADR 이 이 단계를 "공짜" 라고 적었고, 그건 거짓이다

§ D1 과 § D5 가 둘 다 이렇게 말한다:

> *"`java-gateway` 와 게이트웨이 6개는 **이미 의존한다** — **새 배선 없음.**"*

**두 절 다 틀렸고, 두 번째는 빌드를 깨뜨린다** (`TASK-MONO-377` § 1.7 실측, `ae54af58d`):

| 주장 | 실측 |
|---|---|
| `libs/java-gateway` 가 `libs/java-security` 에 의존한다 | **아니다.** `build.gradle` 에 **project 의존이 하나도 없다**(SCG · `starter-security` · `oauth2-resource-server` · `data-redis-reactive` · micrometer · jackson). 그리고 **두 클래스를 코드에서 실제로 쓴다** — `GatewayJwtDecoders:64` 가 `new AllowedIssuersValidator(...)` 를 생성하고 `TenantClaimValidator` 를 파라미터로 받으며, `JwtClaims:32` 가 `TenantClaimValidator.CLAIM_TENANT_ID` 를 읽는다. |
| 게이트웨이 6개가 이미 `java-security` 를 선언한다 | **3개만** — fan · scm · wms. **ecommerce · erp · finance 는 안 한다.** 지금 컴파일되는 이유는 클래스가 `java-gateway` **안에** 있고 그걸 직접 선언하기 때문이다. Gradle `implementation` 은 **소비자의 컴파일 클래스패스로 전이되지 않으므로**, 클래스가 나가는 순간 **그 3개는 컴파일이 깨진다.** |

**§ D2 가 그 유혹적인 해법(`api project(':libs:java-security')`)을 금지한다** — 그리고 그 금지는 옳다(`ADR-MONO-048` § D1: `api` 엣지는 "중립" 모듈이 중립 아닌 것을 전이시키기 시작하는 통로다). ⇒ **각 소비자가 직접 선언한다.**

> **이 오류가 이 혈통에서 방향이 다른 첫 번째다.** 카운트가 낮았던 세 번은 불편이었다. **깨짐을 과소평가한 것은 main 이 빨개지는 것이다.** 읽어서 발견한 게 다행이지 빨간 빌드로 발견할 뻔했다.

---

# Scope

## In Scope

1. **클래스 2개 + 테스트 2개 이관**
   - `libs/java-gateway/.../apigateway/security/{AllowedIssuersValidator,TenantClaimValidator}.java`
   - `libs/java-gateway/src/test/.../security/{AllowedIssuersValidatorTest,TenantClaimValidatorTest}.java`
   - → `libs/java-security` 로. **패키지 권장 = `com.example.security.oauth2`** — 기존 `com.example.security.jwt` 는 **jjwt 기반**(진짜 프레임워크 무관)이고, 이 둘은 **Spring Security OAuth2** 바인딩이다. 같은 패키지에 섞으면 그 구분이 흐려진다.
2. **`libs/java-security/build.gradle`** — `implementation 'org.springframework.security:spring-security-oauth2-jose'` 추가. **⚠️ 이 모듈은 현재 Spring 의존이 하나도 없다**(jjwt + password4j 뿐). 이게 **32개 소비자 전원의 런타임 클래스패스에 Spring Security 를 올린다** — § D3 V1 이 *정확히* 무엇이 오면 안 되는지 단언해야 하는 이유다(WebFlux ✗ · SCG ✗ · `jakarta.servlet` ✗ — "Spring 금지" 라는 뭉뚱그린 단언이 아니라).
3. **의존 4줄 추가** — `libs/java-gateway` + **ecommerce · erp · finance** 게이트웨이. 전부 `implementation project(':libs:java-security')`. **`api` 금지**(§ D2).
4. **게이트웨이 6개의 import 갱신** — ecommerce · erp · fan · finance · scm · wms (`com.example.apigateway.security.*` → `com.example.security.oauth2.*`). **iam 게이트웨이는 이 클래스를 안 쓴다**(실측 0건) — 건드리지 않는다.
5. **§ D3 V1/V2 빌드 단언** — `libs:java-security` 의 `runtimeClasspath` 에 WebFlux **0** · SCG **0** · `jakarta.servlet` **0**. **mutation 으로 물게 만든다**(§ 6).

## Out of Scope

- **servlet 서비스 20개** — 사본 삭제는 D5-3 부터. 이 단계는 **하나도** 안 건드린다.
- **`libs/java-security-servlet`** — D5-2.
- **`TenantClaimEnforcer`** — servlet-bound. D5-2.
- **게이트웨이의 정책 변경** — **행동 불변**. 정책 4형태(§ 1.6)는 **이미** MONO-355 builder 로 표현돼 있다. 이 단계는 **클래스를 옮길 뿐 정책을 안 만진다**.
- **iam 게이트웨이** — § D6.

---

# Acceptance Criteria

- [x] **AC-1 (행동 불변 — 증명의 본체)** — ✅ 게이트웨이 6개 스위트 **304 tests / skipped 0 / failures 0**, **수정 없이** 통과. **`projects/**/src/test/` 에서 바뀐 *코드* 줄 = 0** (import 20줄 + Javadoc `{@link}` 1줄뿐). *스위트를 고쳐야 했다면 이관이 행동을 바꾼 것이다*(§ 6 V6) — 안 고쳤다.
  *(첫 검증식은 Javadoc `{@link}` 줄을 assertion 으로 오검출했다. 술어를 정교화해 재확인 — 오늘만 같은 클래스 일곱 번째.)*
- [x] **AC-2 (배선 실측)** — ✅ 6개 게이트웨이 전부 컴파일 + 테스트 통과. **ecommerce · erp · finance 포함** — ADR 이 "공짜" 라 적은 그 셋. 각각 `implementation project(':libs:java-security')` **1줄** 신규 선언(+ `libs:java-gateway` 1줄 = **총 4줄**). 전체 `./gradlew check` **BUILD SUCCESSFUL** (FAILED 태스크 0).
- [x] **AC-3 (V1 — 중립성을 빌드가 보증한다)** — ✅ **신규 `assertClasspathNeutrality`** (`libs/java-security/build.gradle`, `check` 에 물림): `runtimeClasspath` **23 아티팩트** 검사, WebFlux/SCG/`jakarta.servlet`/`spring-webmvc`/`tomcat-embed-core` **0**. 아티팩트 0건이면 **exit 2**(공허 통과 금지, MONO-359).
  **mutation ✅**: `spring-boot-starter-web` 주입 → **RED**, `spring-webmvc-6.2.1.jar` · `tomcat-embed-core-10.1.34.jar` 를 이름으로 지목. **주입 건수를 결과 읽기 전에 확인**했다.
  **+ V2 신규 `assertNoServletOnReactiveEdge`**: `libs:java-gateway` 94 아티팩트, servlet **0**. mutation → **RED**. *(V2 는 D5-2 에서 누군가 `java-gateway` 를 `java-security-servlet` 에 의존시키는 순간을 위해 존재한다.)*
- [x] **AC-4 (V4 — `api` 금지)** — ✅ **신규 `assertNoApiOnSharedLibs`** (루트, `check` 에 물림). **텍스트 grep 이 아니라 해결된 의존 그래프**를 본다(rename·alias·`extendsFrom` 우회 불가).
  **mutation ✅**: `java-gateway` 에 `java-library` 플러그인 + `api project(':libs:java-security')` — **AC-2 의 4줄을 없애는 바로 그 지름길** — 주입 → **RED**, 위반 모듈을 경로로 지목.
- [x] **AC-5 (사본 0 — 이 단계는 아직 아무것도 안 지운다)** — ✅ **18 / 18 / 13 = 49 그대로**.
- [x] **AC-6 (패키지 경계)** — ✅ `com.example.security.jwt`(jjwt 기반)에 `org.springframework` import **0건**. Spring Security 바인딩은 신설 `com.example.security.oauth2` 에만.
- [x] **AC-7** — ✅ `libs/java-gateway` 에 두 클래스의 잔존 사본 **0**. `git mv` 로 이동(rename 인식 = 이력 보존).

## 착수 후 드러난 것 — ADR 의 다섯 번째 거짓 주장

**① 순환 의존이 숨어 있었다.** `TenantClaimValidator:52` 가 `GatewayErrorCodes.TENANT_MISMATCH` 를 읽는다. **같은 패키지라 `import` 문이 없어서** 착수 전 `^import` grep 이 못 봤고, 나는 *"java-gateway 의 다른 클래스에 의존하지 않는다 — 깨끗한 이동"* 이라고 **단언했다**. 옮기면 `java-security → java-gateway` 의존이 생겨 **순환**이다.
⇒ **위임 방향을 뒤집었다**: validator 가 리터럴을 소유하고 `GatewayErrorCodes` 가 그것을 가리킨다. 방향이 `java-gateway → java-security` 가 되어 순환이 없고, **MONO-351 의 "리터럴 하나, 양쪽이 그것을 가리킨다" 속성은 그대로**다 — 화살표만 뒤집혔다.
⇒ 그리고 **두 상수의 일치를 단언하던 테스트를 `java-gateway` 쪽으로 옮겼다.** 원래 자리(`java-security`)에 두면 `GatewayErrorCodes` 가 안 보이므로 — **컴파일을 통과시키려고 그 단언을 지웠을 것이고, 드리프트 가드가 이동 중에 조용히 죽었을 것이다.**

**② § D3 이 *"`ADR-MONO-048` § D1 의 AC-6 이 이미 `runtimeClasspath` 격리를 단언한다"* 고 적었는데 — 그 단언은 존재하지 않는다.** AC-6 은 **산문으로만** 쓰였고 태스크가 된 적이 없다(저장소 전수 grep). **이 task 가 만든 V1/V2/V4 가 최초의 실행 가능한 형태다.** § 6 이 스스로 적어둔 말이 이 ADR 자신에게 성립했다: ***"아무도 실패하는 걸 본 적 없는 단언은 Task 를 두른 주석이다."***

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` — § D1 · § D2 · § D3 · § D5(단계 1) · **§ 1.7** · § 6 (V1 · V2 · V4 · V6)
- `docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md` — § D1 (`api` 금지의 출처; servlet/reactive 격리)
- `platform/shared-library-policy.md` — § Change Rule
- `libs/java-security/build.gradle` · `libs/java-gateway/build.gradle` · `settings.gradle`
- `libs/java-gateway/.../security/{GatewayJwtDecoders,JwtClaims}.java` — **두 클래스를 실제로 쓰는 곳**

# Related Contracts

없다 — 런타임 표면 변화 없음, 계약 변화 없음, 행동 변화 없음.

---

# Edge Cases

- **`spring-security-oauth2-jose` 가 32개 소비자 전원에게 간다.** 그게 § D3 V1 이 **"Spring 금지"** 가 아니라 **"WebFlux/SCG/servlet 금지"** 로 쓰여야 하는 이유다. 뭉뚱그린 단언은 **첫날 RED** 이고, 첫날 RED 인 가드는 꺼진다(`TASK-MONO-360`).
- **`JwtClaims` 가 `TenantClaimValidator.CLAIM_TENANT_ID` 를 읽는다** — 상수 하나 때문에 `java-gateway` → `java-security` 의존이 생긴다. **상수를 복제해서 의존을 피하지 말 것**: 그게 이 ADR 이 없애려는 바로 그 패턴이다.
- **`GatewayErrorCodes` 의 Javadoc 이 미래를 말한다** (*"When step 2 moves TenantClaimValidator into the library…"*). **그 주석은 이 task 가 실행하는 그 단계를 가리킨다** — 갱신할 것. 안 하면 stale declaration 이 하나 더 생긴다.
- **패키지가 바뀌면 servlet 사본들의 import 는 안 바뀐다** — 사본은 **자기 프로젝트 패키지**에 있고 라이브러리를 import 하지 않는다. 그래서 이 단계가 servlet 서비스를 안 건드리는 것이 **가능**하다.

# Failure Scenarios

- **`api project(':libs:java-security')` 로 3줄을 아낀다** → § D2 위반. `api` 엣지는 "중립" 모듈이 중립 아닌 것을 전이시키기 시작하는 통로이고, `ADR-MONO-048` § D1 이 이미 금지했다. **불편한 첫 순간에 예외를 얻는 금지는 금지가 아니다.** Guard: AC-4.
- **게이트웨이 스위트를 고쳐서 통과시킨다** → **이관이 행동을 바꿨다는 증거를 지우는 것.** Guard: AC-1 — assertion 은 한 줄도 바뀌면 안 된다.
- **클래스를 복사하고 원본을 안 지운다** → 사본이 49 → 51 이 된다. Guard: AC-7.
- **V1 을 "Spring 이 없다" 로 단언한다** → `spring-security-oauth2-jose` 가 있으므로 **첫날 RED** → 가드가 꺼진다. Guard: AC-3 이 **WebFlux/SCG/servlet** 만 금지한다.

---

# Provenance

`ADR-MONO-049` § D5 단계 1. `TASK-MONO-377` 의 ACCEPT(범위 A)로 게이트가 열렸다.

**착수 전 실측에서 ADR 자신의 D5-1 서술이 거짓임이 드러났다** — *"새 배선 없음"* 인데 실제로는 게이트웨이 3개가 컴파일이 깨지고 `java-gateway` 자신도 의존이 필요하다. **이 혈통에서 네 번째 카운트 정정이고, 방향이 위험한 쪽인 첫 번째다.**

그리고 그 발견 과정 자체가 오늘 여섯 번째 같은 함정이었다: 탐지 명령에 *"blank = 아무것도 참조 안 함"* 이라는 **결론을 미리 적어두고** 출력을 읽으려 했는데, 출력은 비어 있지 않았다. **결론을 먼저 쓰지 말 것.**

분석=Opus 4.8 / 구현 권장=**Opus** (Gradle 의존 전이 규칙 + servlet/reactive 격리 + mutation 검증이 얽힌다. 잘못 고치면 **컴파일은 되는데 servlet 서비스 런타임에 WebFlux 가 올라오는** 상태 = `TASK-MONO-044a` 의 재현).
