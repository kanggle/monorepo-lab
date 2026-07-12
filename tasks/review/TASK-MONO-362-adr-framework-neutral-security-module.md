# Task ID

TASK-MONO-362

# Title

`ADR-MONO-049` (PROPOSED) 저술 — **프레임워크 중립 보안 모듈**: servlet 보안 클래스 3종 × 6사본 = 18파일 중복. **산출물은 코드가 아니라 결정 문서다.**

# Status

review

# Owner

monorepo

# Task Tags

- adr
- architecture
- shared-library
- security

---

# Dependency Markers

- **선행 (머지됨)**: [`TASK-MONO-361`](../done/TASK-MONO-361-stale-gateway-deferred-claims.md) (PR #2438 `9746d5040`) — 이 18파일을 읽는 사람이 **"게이트웨이는 아직 없다"** 는 stale 을 먼저 만나지 않도록 치웠다. **그 정리가 이 ADR 의 전제다** — 중복을 없애도 되는지 판단하려면 그 파일들이 참말을 해야 한다.
- **선행 (ACCEPTED)**: [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — `libs/java-gateway`(reactive). **§ D1 이 이 task 의 제약을 만든다**: 그 모듈은 servlet 소비자가 쓸 수 없다.
- **게이트**: [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule — *"New shared-library introduction … requires an ADR"*. **이 task 가 없으면 구현은 HARDSTOP-09.**
- **선례**: [`TASK-MONO-349`](../done/TASK-MONO-349-adr-shared-reactive-gateway-library.md) — 같은 게이트에 걸려 ADR-MONO-048 을 낳은 task. **산출물이 코드가 아니라 결정 문서**라는 점도 같다.

---

# ⚠️ ACCEPT 게이트 (읽고 시작할 것)

**이 task 는 ADR 을 `PROPOSED` 로 발행하는 데서 끝난다.** `PROPOSED → ACCEPTED` 는 **사람의 정확형 intent**(`ADR-MONO-049 ACCEPTED`)로만 넘어간다. **에이전트 self-ACCEPT 금지** — 그냥 "진행" 은 통과시키지 못한다.

**구현(모듈 신설 + 18파일 마이그레이션)은 이 task 범위 밖이다.** ACCEPT 후 별도 task 로 쪼갠다.

---

# Goal

**`AllowedIssuersValidator` · `TenantClaimValidator` · `TenantClaimEnforcer` 가 finance/erp servlet 서비스 6곳에 각각 사본으로 존재한다 = 18파일.** 보안 경계의 손-복사본이고, `libs/java-gateway` 로는 공유할 수 없다.

**이 ADR 이 답해야 하는 질문**: *이 중복을 어떻게 없앨 것인가 — 아니면 없애지 않기로 하고 그 이유를 기록할 것인가.*

---

# 착수 전 실측 (2026-07-12, `9746d5040` — ADR 본문의 근거. 재검증할 것)

## 사본 지도

| 클래스 | servlet 사본 | lib 정본 | 비고 |
|---|---|---|---|
| `AllowedIssuersValidator` | 6 | ✅ `libs/java-gateway` | **본문 바이트 동일 (6 + lib = 7 copies)** |
| `TenantClaimValidator` | 6 | ✅ (형태 다름) | lib 판은 MONO-355 에서 **빌더로 파라미터화** — servlet 판은 단순형 |
| `TenantClaimEnforcer` | 6 | ❌ 없음 | **servlet 전용**(`OncePerRequestFilter`) — 게이트웨이엔 대응물 없음 |

**서비스**: finance{account, ledger} · erp{approval, masterdata, notification, read-model}.

## 세 가지 사실

1. **프레임워크 중립 확정** — 3종 전부 reactor / WebFlux / `ServerHttp` 참조 **0건**. `TenantClaimEnforcer` 만 `jakarta.servlet` 에 묶인다(그래서 lib 대응물이 없다).
2. **동작 드리프트 0** — 6사본 전부 동작 동일. **아직 안 깨졌다.**
3. **그러나 텍스트는 이미 갈라졌다** — 정규화 본문 비교: `TenantClaimValidator` 6개 중 **4개**, `TenantClaimEnforcer` 6개 중 **4개**가 서로 다르다. 차이는 전부 **포매팅**(줄바꿈 · `java.util.ArrayList` FQN vs import · 지역변수 추출) + **정당한 도메인 프로퍼티 키**(`erpplatform...:erp` vs `financeplatform...:finance`). **보안 동작 차이는 없다.**

⇒ **"손으로 유지되고 있다" 는 증거는 이미 있다. 아직 깨지지 않았을 뿐이다.**

## 왜 이게 진짜인가 — 이미 한 번 일어난 일

ADR-048 의 논거는 가설이 아니었다: **`FailOpenRateLimiter` 의 "모든 `Throwable` 을 삼킨다" 수정이 scm/fan/ecommerce 에 도달하고 wms 를 조용히 빠뜨렸다.** wms 의 테스트 이름이 `failsOpenOnAnyReactiveError` 였는데도 — **결함이 계약으로 적혀 있었다.** 사본 4개에서 일어난 일이고, **여기는 6개다.**

## 왜 `libs/java-gateway` 로는 안 되는가

`implementation` 의존이 **소비자의 런타임 클래스패스에 올라간다** ⇒ servlet 서비스가 그 모듈을 쓰면 **WebFlux + Spring Cloud Gateway 를 함께 끌어온다.** ADR-048 § D1 이 격리하려던 바로 그 누출이고, **MONO-044a(servlet/reactive bleed 사고)의 거울상**이다.

## 왜 텍스트 가드가 답이 아닌가 (`TASK-MONO-360` 을 지어놓고도)

*"6사본 본문이 정본과 동일한가" 를 CI 가 단언한다* — 매력적이지만 **틀렸다**:

- (a) **이 레포에 포매터가 없다**(spotless/checkstyle/google-java-format 전부 부재). 사본은 이미 포매팅이 갈라져 있으므로 그 가드는 **첫날부터 RED** 다 — `TASK-MONO-360` 이 못박은 실패 모드에 정면으로 걸린다: ***첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다 — skip 이 초록으로 보고되니까.***
- (b) `TenantClaimValidator` 는 lib 판이 **빌더로 파라미터화**돼 servlet 판과 형태가 다르다 ⇒ **비교할 텍스트 자체가 없다.**
- (c) 포매팅을 강제로 수렴시켜 가드를 세우면, 그건 **사본을 유지하기로 결정한 것**이다. 그 결정도 ADR 이 명시적으로 내려야 한다.

**⇒ 봉합할지 없앨지를 고르는 것이 이 ADR 이다.**

---

# Scope

## ADR 이 반드시 다뤄야 할 것

1. **결정 (D1) — 무엇을 만드는가**: 프레임워크 중립 모듈(가칭 `libs/java-security`)? 그 경계는? **`TenantClaimEnforcer` 는 `jakarta.servlet` 에 묶여 있어 "프레임워크 중립" 이 아니다** — 이걸 어디에 둘 것인가가 이 ADR 의 가장 어려운 지점이다. 선택지: ①중립 모듈 + servlet 전용 모듈 2개 ②servlet 모듈 하나(중립 클래스도 함께) ③`TenantClaimEnforcer` 는 사본 유지(+이유 기록).
2. **결정 (D2) — 의존 방향**: `libs/java-gateway`(reactive) 도 새 중립 모듈을 **소비**하는가? 그러면 `AllowedIssuersValidator` 는 **정본이 하나**가 된다(7 copies → 1). 아니면 gateway 는 자기 것을 유지하는가(= 중복이 절반만 해소).
3. **결정 (D3) — 누출 격리 증명**: 새 모듈 소비자의 `runtimeClasspath` 에 WebFlux/SCG **0건**임을 **어떻게 강제**할 것인가(ADR-048 D1 의 AC-6 가 선례 — gradle 태스크로 단언).
4. **결정 (D4) — 도메인 정책은 어디에 남는가**: `TenantClaimValidator` 는 도메인마다 게이트가 다르다(wms=엔타이틀먼트만, scm/fan=wildcard+엔타이틀먼트, ecommerce=any-well-formed). **MONO-355 가 이미 이 문제를 빌더로 풀었다** — servlet 판도 같은 빌더를 쓰는가? 그러면 **6개 도메인 게이트 정책이 한 곳에 모인다**(좋음) vs **한 파일의 오타가 6개 엣지를 연다**(355 가 "모든 스위치는 닫힘이 기본" 으로 대응한 위험).
5. **결정 (D5) — 마이그레이션 순서**: 18파일을 한 PR 에? 도메인별로? **각 서비스의 기존 테스트가 사본을 직접 생성**하므로(`new TenantClaimValidator("erp")`), 교체 시 테스트도 함께 움직인다.
6. **비-결정 (Non-goals)**: **서비스-레벨 검증기 자체를 제거하는 것은 out** — 이중방어이고, **하중을 받는다**(MONO-361 실측: console-bff 가 게이트웨이를 우회해 백엔드를 직결). MONO-361 이 그 이유를 6개 `ServiceLevelOAuth2Config` javadoc 에 못박아 뒀다.
7. **대안 기각 사유 기록**: ①텍스트 가드(위 § 참조) ②`libs/java-gateway` 재사용(런타임 누출) ③현상 유지(사본 6개 = FailOpenRateLimiter 재현 대기).

## Out of Scope

- **구현.** ACCEPT 전에는 코드를 쓰지 않는다(HARDSTOP-09).
- **iam 게이트웨이의 자체 JWT 검증 코드** — 별건. `libs/java-gateway` 를 **소비하지 않는 유일한 게이트웨이**이고, Spring Security OAuth2 RS 대신 `TokenValidator`/`JwksCache`/`TokenBucketRateLimiter` 를 직접 구현했다. **D7 은 "추출 비용이 안 맞는다" 는 이유로 제외했지, 안전성을 승인한 적이 없다.** 별도 실사 필요.
- **wms rate-limit 키잉**(IP 단독·근거 없음, MONO-355 부터 사람 결정 대기).

---

# Acceptance Criteria

- [ ] **AC-1** — `docs/adr/ADR-MONO-049-*.md` 가 **`PROPOSED`** 상태로 존재한다. **`ACCEPTED` 로 쓰지 말 것**(self-ACCEPT 금지).
- [ ] **AC-2 — 근거는 실측이다**: § 착수 전 실측의 세 가지 사실(중립성 · 동작 드리프트 0 · 텍스트 이미 갈라짐)을 **ADR 본문에서 재검증**하고 수치를 기록한다. **"중복은 나쁘다" 는 DRY 논거로 쓰지 말 것** — ADR-048 의 진짜 논거는 *"보안 경계의 사본은 수정을 잃어버리는 메커니즘"* 이었고 그건 `FailOpenRateLimiter` 로 **실증**됐다.
- [ ] **AC-3 — D1~D5 각각에 결정과 그 근거**. 특히 **`TenantClaimEnforcer` 의 `jakarta.servlet` 결합**을 얼버무리지 말 것(§ Scope D1).
- [ ] **AC-4 — 기각된 대안**을 이유와 함께 기록한다(텍스트 가드 · lib 재사용 · 현상 유지). **텍스트 가드 기각 사유에 "포매터 부재 → 첫날 RED" 를 명시** — MONO-360 이 세운 규율을 내가 어기지 않았음을 남긴다.
- [ ] **AC-5 — 비용 실측**: ADR-048 § AC-1 이 *"게이트웨이의 ~81% 가 라이브러리"* 를 숫자로 답했듯, 이 ADR 도 **"18파일 중 몇 줄이 사라지는가"** 를 답한다. **작으면 그것이 산출물이다** — 모듈 신설 비용이 이득을 넘으면 **현상 유지 + 그 이유 기록**이 옳은 결정일 수 있다.
- [ ] **AC-6** — 후속 구현 task 를 **ADR 안에 로드맵으로** 적되, **`tasks/ready/` 에 만들지는 않는다**(ACCEPT 전이므로).
- [ ] **AC-7** — 코드 변경 **0**. `./gradlew check` 무영향.

---

# Related Specs

- [`platform/shared-library-policy.md`](../../platform/shared-library-policy.md) § Change Rule — **이 ADR 을 강제하는 규칙**
- [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) — § D1(누출 격리) · § 1.1(servlet 사본 정정 노트) · § D5(도메인 게이트 정책) · § AC-1(비용 실측 선례)
- `ADR-MONO-044a` — servlet/reactive bleed 사고 (D1 의 근거)
- `ADR-MONO-019` § D5 — entitlement-trust dual-accept (사본들이 구현하는 정책)
- [`TASK-MONO-355`](../done/TASK-MONO-355-java-gateway-tier2-parameterization.md) — `TenantClaimValidator` 빌더 파라미터화 + **"모든 스위치는 닫힘이 기본"** 규율
- [`TASK-MONO-360`](../done/TASK-MONO-360-gateway-declaration-drift-guard.md) — *첫날 RED 인 가드는 꺼진다* (텍스트 가드 기각 사유)

# Related Contracts

없음 — 결정 문서. 런타임 표면 불변.

---

# Edge Cases

- **DRY 를 논거로 쓰는 것** — AC-2. 사본이 나쁜 이유는 중복이라서가 아니라 **수정이 사본 하나를 조용히 빠뜨리기 때문**이다. 그 차이가 ADR 의 설득력 전부다.
- **`TenantClaimEnforcer` 를 "프레임워크 중립" 이라 뭉개기** — `jakarta.servlet.http.HttpServletRequest` 에 묶여 있다. **중립 모듈에 넣을 수 없다.** 얼버무리면 ACCEPT 후 구현이 막힌다.
- **6개 게이트 정책을 한 파일에 모으는 위험** — 355 가 이미 겪었다(*"보안 클래스의 기본값이 문을 열면 오타 하나가 네 개를 연다"*). 빌더 재사용을 제안한다면 **닫힘-기본 규율을 ADR 에 명시 승계**할 것.
- **비용이 이득을 넘을 수 있다** — AC-5. **"현상 유지" 도 정당한 결정**이며, 그 경우 ADR 은 *왜 사본을 유지하기로 했는지*와 *무엇이 바뀌면 재검토하는지*를 적는다. 결론을 미리 정해두고 쓰지 말 것.

# Failure Scenarios

- **에이전트가 `ACCEPTED` 로 써버린다** → 정책 결정이 아무 승인 없이 일어난다. Guard: § ACCEPT 게이트 + AC-1. **이 게이트가 실제로 작동한 기록이 둘 있다**(MONO-349, MONO-357).
- **ADR 을 쓰면서 "김에" 모듈을 만든다** → HARDSTOP-09. Guard: § Out of Scope.
- **논거를 부풀린다** — 동작 드리프트는 **0** 이다. *"이미 보안이 깨졌다"* 고 쓰면 거짓이고, 그 거짓이 들키는 순간 진짜 논거(텍스트는 이미 갈라졌고 메커니즘은 실증됐다)까지 같이 죽는다. Guard: AC-2.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-357` § 별건으로 기록됐고, `TASK-MONO-361` 이 실측하며 **수치가 틀렸음이 드러났다**(12파일/2클래스 → **18파일/3클래스**; `TenantClaimEnforcer` 누락). ADR-MONO-048 § 1.1 의 "정정본" 조차 여전히 과소평가였다.

분석=Opus 4.8 / 구현 권장=**Opus** (아키텍처·정책 판단. 기계적 문서 작성이 아니다 — `TenantClaimEnforcer` 의 servlet 결합과 "현상 유지도 정답일 수 있다" 는 판단이 핵심).
</content>
