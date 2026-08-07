# ADR-MONO-060 — assume-tenant 토큰의 `sub`: 운영자 행위를 **누구의 것으로** 기록하는가

**Status:** PROPOSED
**Date:** 2026-08-07
**History:** PROPOSED 2026-08-07 (this record). **ACCEPT is a human gate — this record authorises no code.** 승인은 소유자의 **정확형** 지시(`ADR-MONO-060 ACCEPTED — <A|B|C>`)를 요구하며, 일반적인 "진행"/"proceed" 는 이것을 승인하지 않는다. 작성 에이전트는 자기 제안을 스스로 ACCEPT 할 수 없다.
**Decision driver:** `TASK-MONO-515` 의 AC-0 재측정(2026-08-07)이 이것을 erp 결재함 기능 갭이 아니라 **플랫폼 전역의 귀속(attribution) 문제**로 재분류했다 — 6개 게이트웨이 전부가 `X-User-Id ← sub` 이고, assume 토큰의 `sub` 는 사람이 아니라 콘솔 클라이언트다.
**Related:** `TASK-MONO-515`(발굴) · `platform/contracts/jwt-standard-claims.md` § `sub`(이 ADR 이 정면으로 다루는 계약 행) · `ADR-MONO-040`(Phase 2/3 — `sub` = 계정 UUID, `X-User-Id ← sub` 복원) · `ADR-MONO-020`(operator multitenant assignment) · `ADR-MONO-032`(단일 신원 모델)

---

## 실측 (2026-08-07, 전부 재측정)

```
assume 토큰 (demo-corp)     sub = "platform-console-web"     aud = "platform-console-web"
libs/java-security-servlet  ActorClaims.from(jwt): accountId = jwt.getSubject()
게이트웨이 6/6              JwtHeaderMapping.skipIfNull("X-User-Id", JwtClaims::subject)
                            (ecommerce · erp · fan · finance · scm · wms — 전수)
```

⇒ 콘솔로 로그인한 **어느 운영자든**, 모든 도메인에서 `X-User-Id = platform-console-web`
이고 애플리케이션의 `actor.accountId()` 도 같은 값이다.

---

## 🔴 이것은 설계 선호의 문제가 아니다 — **계약을 위반하고 있다**

`platform/contracts/jwt-standard-claims.md` § Standard Claims:

| 클레임 | 타입 | 필수 | 정의 |
|---|---|---|---|
| `sub` | **UUID string** | **Yes** | **Account ID** (globally unique, immutable **across all platforms**) … `X-User-Id ← sub` is fully restored — gateways read `sub` directly |

`platform-console-web` 은 UUID 가 아니고 account id 도 아니다.

🔵 **그리고 이 계약은 assume-tenant 예외를 두는 법을 안다** — 바로 두 행 아래에서
`email` 에 대해 *"Never on `client_credentials` … **nor on the assume-tenant exchange**"* 라고
**명시적으로** 카브아웃한다. `sub` 에는 그런 문장이 없다. ⇒ 침묵이 아니라 **비대칭**이고,
비대칭은 실수가 아니라 신호로 읽어야 한다.

## 🔴 발굴 티켓의 전제 하나가 틀렸다 — **테스트는 반대 단언을 하지 않는다**

`TASK-MONO-515` 는 A 안의 대가를 *"`AssumeTenantExchangeIntegrationTest` 가 명시적으로
반대 단언을 하고 있다 ⇒ 문서화된 결정을 뒤집는 것"* 이라고 적었다. 열어 보니 그 파일의
유일한 `sub` 단언은 이것이다:

```java
// The assumed token's own sub is the acting console client (platform-console-web)
// per the RFC 8693 flow — the account linkage is the validated subject token, not a
// sub claim on the assumed token.
assertThat(basePayload.get("sub").asText())          // ← base 토큰이다
        .as("the consumer login token belongs to the account (…)")
        .isEqualTo(account);
```

단언 대상은 **base 토큰**이고, assume 토큰의 `sub` 에 대한 진술은 **주석**에만 있다.

⇒ **A 는 테스트가 고정한 결정을 뒤집는 것이 아니다.** 아무것도 단언하지 않는 동작을
바꾸는 것이다. A 의 비용은 티켓이 적은 것보다 **낮다**. 🔵 다만 그 주석이 RFC 8693 을
근거로 들고 있으므로, 그 해석의 타당성은 아래 § 에서 별도로 다룬다.

---

## 모집단 — **두 개의 다른 숫자**이고, 섞으면 결정이 틀린 크기로 내려간다

티켓은 *"`sub` 를 사람 식별자로 쓰는 지점을 5개 도메인 전수로 세라"* 고 했다. 세어 보니
질문이 둘로 갈린다.

### ① 사람 키로 **필터링**하는 지점 (기능이 눈에 띄게 깨지는 곳)

| 도메인 | 수 | 어디 |
|---|---|---|
| erp | **3** | 결재 인박스 `findInbox(tenantId, actorId)` · **알림 인박스** `recipient(jwt)=getSubject()` · 자기결재 금지 |
| fan | 6 | 피드·내 글·알림·저자 판정·self-follow — 🔵 **사정권 밖**(팬은 base 토큰이라 `sub` 가 실제 계정) |
| finance · scm · wms | **0** | — |

🔴 원시 grep 은 wms 733 을 냈지만 **전부 `WebhookInbox`**(이벤트 배관)였다 — 분류하지 않은
카운트는 대리지표다.

### ② 사람으로 **귀속(기록)** 하는 지점 — 여기가 진짜 크기다

게이트웨이 6/6 이 `X-User-Id ← sub` 이고, 애플리케이션 감사 경로가 `actor.accountId()`
(= `sub`)를 행위자로 적는다. ⇒ **필터가 0인 도메인도 감사 기록은 오염된다.** 예: finance 의
`AuditLog.of(tenantId, …, actor.accountId(), actor.actorType(), …)` 는 fintech F6(감사 불변)
경로인데, 운영자 쓰기의 행위자가 `platform-console-web` 으로 남는다.

🔵 **①은 "화면이 빈다" 로 드러나고 ②는 아무 증상도 내지 않는다.** 그래서 이 결함은 erp
결재함에서만 보였다 — 거기만 값을 **필터로** 썼기 때문이다. 나머지는 조용히 적고 있었다.
⚠️ ②는 코드 경로로 확인했고 이번 회차에 **행 실측은 하지 않았다**(erp·finance 미기동).

---

## 선택지

### A. assume 토큰의 `sub` 를 **계정 UUID** 로 (계약 준수 복원)

- ✅ **계약이 이미 요구하는 것**이다. 새 클레임도 새 규약도 없다
- ✅ 6도메인의 `X-User-Id` 와 감사 기록이 **한 번에** 사람으로 돌아온다. 읽는 쪽 변경 0
- ✅ 테스트가 막지 않는다(위 §)
- ❌ "acting client 가 누구인가" 를 잃는다 ⇒ RFC 8693 의 `act` 를 **함께** 실어 보존해야
  한다(잃어도 되는 정보인지가 이 안의 실질 질문)
- ❌ 토큰 계약 변경이므로 `jwt-standard-claims.md` 갱신 + 게이트웨이 회귀 확인 필요

### B. `sub` 는 두고 별도 클레임(`act` / `on_behalf_of`)에 계정을 싣는다

- ✅ RFC 8693 의 delegation 시맨틱과 형태가 맞다
- ❌ **읽는 쪽이 도메인마다 필요하다** — 6게이트웨이 + 각 애플리케이션. erp 만 고치면
  나머지 5도메인의 감사 기록은 그대로 `platform-console-web` 이다
- ❌ 계약에 **새 클레임**을 추가해야 하고, `sub` 행의 "account id, 모든 플랫폼에서 불변"
  이라는 문장과 여전히 모순인 채로 남는다(계약을 고쳐 카브아웃을 명문화해야 한다)
- 🔴 B 는 "고친다" 가 아니라 **"현재 동작을 계약으로 승격시키고 보완물을 얹는다"** 이다.
  그 선택도 정당하지만, 그렇게 부르는 편이 정직하다

### C. 콘솔 운영자마다 개인 워크로드 신원

- ❌ 계정 수만큼 OAuth 클라이언트가 생긴다. 확장성 없음 — 기록만 하고 **배제 권장**

---

## 추천 — **A** (다만 이것은 제안이지 결정이 아니다)

A 는 **계약이 이미 말하고 있는 것**이고, 읽는 쪽 6도메인을 하나도 건드리지 않으면서
①과 ② 양쪽을 동시에 해결하는 유일한 안이다. B 는 도메인마다 소비자를 새로 만들어야 해서
**부분 적용되기 쉽고**, 부분 적용된 B 는 "일부 도메인만 사람으로 기록되는" 더 나쁜 상태다.

🔴 **A 의 진짜 질문은 "acting client 를 잃어도 되는가"** 이다. 잃으면 안 된다면 A + `act`
클레임(계정은 `sub`, 클라이언트는 `act`)이 RFC 8693 에도 더 맞는 배치다 — RFC 에서
`sub` 는 **위임의 주체(누구를 대신하는가)** 이고 `act` 가 **행위자**다. 지금 구현은 그
둘을 뒤집어 놓았고, 테스트 주석이 그것을 "per the RFC 8693 flow" 라고 적은 것은
**RFC 의 반대**로 읽힌다.

---

## 결과 (ACCEPTED 시)

| 안 | 후속 |
|---|---|
| A | `jwt-standard-claims.md` § `sub` 확인(변경 불요일 수 있다 — 이미 그렇게 적혀 있다) + auth-service 토큰 커스터마이저 + `AssumeTenantExchangeIntegrationTest` 주석/단언 갱신 + 6게이트웨이 회귀 + `MONO-515` 는 **erp 인박스 2곳**이 자동 해결 |
| B | 계약에 새 클레임 + 카브아웃 명문화 + 6도메인 소비자 구현(부분 적용 금지 가드 필요) + erp 2곳 |
| C | 배제 권장 |

어느 안이든 **erp 인박스는 2개**다(결재 + 알림). 하나만 고치면 형제가 낙오한다.

---

## The ACCEPTED Gate

이 ADR 은 **PROPOSED** 다. 해제는 정확형만:

```
ADR-MONO-060 ACCEPTED — <A|B|C>
```

그때까지 `TASK-MONO-515` 는 착수하지 않는다.
