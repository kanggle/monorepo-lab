# ADR-004: `Follow.artistAccountId` 의 실재 검증 — 이음매를 어디에 두는가

**Status:** Accepted — **A**
**Date**: 2026-08-11 (proposed) · **2026-08-11 ACCEPTED — A** (소유자 정확형)
**Deciders**: kanggle
**Supersedes**: —
**Relates to**: [`ADR-MONO-059`](../../../../docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md)(이 ADR 이 그 전제의 공백을 메운다), `TASK-FAN-BE-045` AC-6(이 ADR 이 게이트한다), `TASK-MONO-512`(후속), `specs/services/community-service/architecture.md` § Service Type Composition · § Forbidden dependencies, `specs/services/artist-service/architecture.md`, `ADR-MONO-005`(`/internal/**` Order(1) 체인)

---

## Context

`ADR-MONO-059` 가 **A**(아티스트에게 실제 계정을 준다)로 ACCEPTED 되면서, § 결과 표가
`TASK-FAN-BE-045` 에 **"스키마 + 온보딩 + 조인 검증"** 셋을 배정했다. 그중 조인 검증은
ADR 본문에서 이렇게 표현됐다:

> `FollowArtistUseCase` 의 무검증 필드에 **검증할 대상이 생긴다**(`artists.account_id` 참조)

🔴 **그 한 단어("참조")가 이 ADR 이 존재하는 이유다.** 그것은 **로컬 참조**를 전제한
표현이고, `ADR-MONO-059` 본문 어디에도 *cross-service* 라는 말이 없다. 착수 직전 실측에서
전제가 무너졌다.

### 이것은 이 저장소가 최근에 겪은 것과 같은 형태다

`TASK-SCM-BE-059` 가 착수하며 *"이 티켓/ADR 의 전제 하나가 무너졌다 — **그 ADR 은 내가
썼다**"* 를 기록했다. 같은 축이다: **ADR 이 결정을 옳게 내렸는데, 그 결정을 실행하는 데
필요한 사실 하나를 재보지 않았다.** `ADR-MONO-059` 의 A 채택 자체는 이 ADR 이 다시 열지
않는다 — 여기서 정하는 것은 **그 A 를 어떤 이음매로 실행하는가** 뿐이다.

---

## 실측 (2026-08-11, 전부 코드/스키마 확인 — 추론 아님)

### ① 두 테이블은 서로 다른 데이터베이스에 있다

| 테이블 | 서비스 | 데이터베이스 |
|---|---|---|
| `follows` (`artist_account_id VARCHAR(36) NOT NULL`) | community-service | `fanplatform_community` |
| `artists` (16컬럼) | artist-service | `fanplatform_artist` |

`application.yml` 의 `datasource.url` 이 각각 `POSTGRES_DB_COMMUNITY` / `POSTGRES_DB_ARTIST`
로 갈린다. ⇒ **FK 는 성립할 수 없다.**

### ② DB reach-in 은 이미 명시적으로 금지돼 있다

`specs/services/community-service/architecture.md` § Forbidden dependencies 가
**artist-service 를 이름으로 지목**한다:

> Cross-service repository imports (community-service does not reach into
> **artist-service** / membership-service tables; membership access goes over an HTTP
> client — `HttpMembershipChecker`, FAN-BE-010 — never a DB-level reach-in).

⇒ 우회로는 처음부터 없다. 남은 것은 **동기 호출** 또는 **이벤트 투영** 뿐이다.

### ③ `artists` 에 계정 컬럼이 0개인 것은 그대로다

`V1__init.sql` 의 `CREATE TABLE artists` 16컬럼 중 `account` 를 포함하는 컬럼 **0건**
(`ADR-MONO-059` AC-0 재측정과 일치). 이 ADR 은 그 컬럼을 더하는 것(FAN-BE-045 AC-1b)을
막지 않는다 — 컬럼은 어느 안에서도 필요하다.

### ④ `FollowArtistUseCase` 는 지금 두 가지만 본다

```java
if (artistAccountId.equals(actor.accountId())) throw new SelfFollowForbiddenException();
if (followRepository.exists(actor.accountId(), artistAccountId, actor.tenantId()))
    throw new AlreadyFollowingException();
followRepository.save(Follow.create(actor.accountId(), artistAccountId, actor.tenantId()));
```

자기팔로우 · 중복 뿐이다. **실재 검증 0.** ⇒ 피드 조인은 지금 **우연히만** 성립한다.

### ⑤ 동기 선례는 있으나, artist-service 쪽 절반이 없다

| | membership-service (선례) | artist-service (대상) |
|---|---|---|
| `/internal/**` 컨트롤러 | `InternalAccessController` · `GET /internal/membership/access` | **없음** |
| `/internal/**` Order(1) 보안 체인 (`ADR-MONO-005`) | 있음 | **없음** (`grep -rn "internal"` → **0건**) |
| community 쪽 클라이언트 | `HttpMembershipChecker`(workload identity, **fail-closed**) | 없음 |
| e2e 탈출구 | ~~`community.membership-service.enabled=false` → inert fallback~~ — **`TASK-FAN-INT-006` 이 삭제** | 없음 |

⇒ 동기 안은 "기존 패턴을 한 번 더" 가 아니라 **artist-service 에 없는 표면을 신설**하는
일이고, 그래서 계약 선갱신 대상이다.

### ⑥ 🔴 이벤트 안은 **선언된 Service Type 을 바꾼다**

`specs/services/community-service/architecture.md`:

- L26 — `| Event consumption | none (single-type rest-api) |`
- L33 — *"**No inbound event-consumer surface** — the outbox is publication-only."*

artist-service 는 `artist.registered.v1` · `artist.published.v1` 등 **6개 토픽을 이미
발행**하고 community-service 의 `@KafkaListener` 는 **0건**이다. 즉 이음매는 외부화됐는데
짝이 없다 — 그 자체는 이벤트 안의 **논거**다. 그러나 짝을 채우는 순간 community-service 는
단일타입 `rest-api` 가 아니게 되고, 그것은 `check-service-type-drift.sh` 가 보는 **선언**이다.

---

## Decision Drivers

1. **`ADR-MONO-059` A 의 요점을 지킨다** — "조인이 우연이 아니라 구조가 된다". 검증을 빼면
   A 를 고른 이유가 사라진다(`FAN-BE-045` 가 Failure Scenario 로 명시).
2. **선언된 아키텍처를 암묵적으로 바꾸지 않는다** (`architecture-decision-rule.md` § Mandatory Rule).
3. **가드가 꺼진 채 초록이 되지 않게 한다** — 어떤 안이든 e2e 탈출구가 "항상 통과" 형이면
   검증을 넣고도 검증이 없는 것과 같다(`MONO-360` 이 이름 붙인 실패 모드).
4. **팔로우는 팬의 상시 경로**다 — 결제·발행보다 호출 빈도가 높고 장애 민감도가 다르다.

---

## 선택지

### A. 동기 internal 엔드포인트 (`HttpMembershipChecker` 형)

artist-service 에 `/internal/**` Order(1) 체인 + `GET /internal/artists/{accountId}`
(또는 `?accountId=`)를 신설하고, community-service 가 workload identity 로 호출한다.
`FollowArtistUseCase` 가 저장 전에 부른다.

- ✅ **선언된 Service Type 이 어느 쪽도 바뀌지 않는다.** 두 서비스 모두 `rest-api` 그대로
- ✅ 형태가 이미 저장소에 있다 — `ADR-MONO-005` 의 `/internal/**` 체인 + fail-closed 클라이언트
- ✅ **즉시 일관성**: 잘못된 `artistAccountId` 는 저장 자체가 안 된다. 사후 정합 복구 불필요
- ❌ artist-service 에 **없는 표면을 신설**한다(보안 체인 + 컨트롤러 + 계약 행)
- ❌ 팔로우 경로에 **동기 결합**이 생긴다. fail-closed 면 artist-service 장애 = 팔로우 불가
  (membership 선례가 fail-closed 이므로 일관되기는 하다)
- 🔴 e2e 탈출구를 `AlwaysAllow` 형으로 만들면 **드라이버 3 을 정면으로 위반**한다 —
  membership 의 `AlwaysAllowMembershipChecker` 가 이미 그 모양이라 복사하기 쉽다

### B. 이벤트 투영 (community 가 `artist.*.v1` 을 구독)

community-service 가 `artist.registered.v1`/`published.v1` 을 구독해 로컬 투영
(`known_artist_accounts`)을 두고 거기서 검증한다.

- ✅ 팔로우 경로에 **동기 결합 0**. artist-service 장애와 무관
- ✅ **이미 발행 중인 이벤트에 짝이 없다** — 외부화된 이음매의 누락된 절반을 채우는 것
- ❌ 🔴 **community-service 의 선언된 Service Type 이 바뀐다**(§ 실측 ⑥). architecture.md
  L26/L33 을 고쳐야 하고 이것이 세 안 중 **가장 큰 선언 변경**이다
- ❌ 이 서비스의 **첫 Kafka 컨슈머** — 멱등·재처리·DLT·정합 복구가 전부 새 표면
- ❌ 🔴 **지연 동안 "실재하는데 아직 투영 안 됨" 을 "없음" 으로 거짓 거부**한다.
  온보딩 직후 팔로우가 정확히 그 창에 들어간다

### C. 검증하지 않는다 — 그것을 **명시적 결정으로 승격**한다

`FollowArtistUseCase` 를 그대로 두고, 검증 부재가 **의도**임을 architecture.md 에 적는다.
`FAN-BE-045` AC-6 은 "해당 없음" 으로 종결한다.

- ✅ 비용 0. 새 표면·새 결합·새 선언 변경 없음
- ✅ 정직하다 — 지금도 그러하고, 적어 두면 다음 조사가 반복되지 않는다
- ❌ 🔴 **`ADR-MONO-059` 가 A 를 고른 이유를 되돌린다.** A 의 첫 번째 ✅ 가 *"조인이 우연이
  아니라 구조가 된다"* 였고, `FAN-BE-045` 는 *"이것을 빼면 A 를 고른 이유가 통째로
  사라진다"* 를 Failure Scenario 로 적어 두었다
- ❌ 피드는 계속 **우연히** 동작한다 — 오타 하나가 조용히 빈 피드를 만들고 아무것도 실패하지 않는다

### 검토했으나 성립하지 않는 것

- **DB FK** — 별도 데이터베이스(§ 실측 ①) + reach-in 금지(§ 실측 ②). 성립 불가.
- **`follows` 를 artist-service 로 이관** — 팔로우는 팬 소유 관계이고 피드 조인이
  community 안에 있다. 테이블 이관 + 피드 재설계는 `ADR-MONO-059` 가 배제한 C 안(조인
  재정의)보다 큰 변경이라 이 티켓의 범위를 벗어난다.

---

## 추천 — **A** (제안이지 결정이 아니다)

드라이버 2 가 갈랐다. B 는 목적(검증)에는 더 맞는 성질(비동기·저결합)을 갖지만, **선언된
Service Type 을 바꾸는 유일한 안**이다. 이 저장소는 그 선언을 가드로 강제하고 있고
(`check-service-type-drift.sh`), *"단일타입 rest-api · 인바운드 컨슈머 표면 없음"* 은
community-service 가 명시적으로 적어 둔 성질이지 우연이 아니다. **더 작은 결정으로 같은
목적을 달성할 수 있으면 큰 선언 변경을 먼저 쓰지 않는다.**

C 는 정직하지만 `ADR-MONO-059` 의 A 를 실질적으로 되돌린다. 소유자가 *"검증까지는 아직
필요 없다"* 고 판단한다면 **그것은 정당한 선택**이고, 그때는 `FAN-BE-045` 의 목표도 함께
줄어든다는 것을 알고 고르는 것이어야 한다.

🔵 **A 를 고를 경우 반드시 함께 결정되는 것** — e2e 탈출구의 모양. membership 의
`AlwaysAllowMembershipChecker` 를 복사하면 드라이버 3 을 위반한다. 권장은 **탈출구를 두지
않는 것**(artist-service 는 live-trio e2e 에 이미 떠 있다)이고, 두어야 한다면 **거부 쪽으로
기본값**을 두는 것이다.

---

## 결과 (ACCEPTED 시)

| 안 | `FAN-BE-045` AC-6 | 부수 작업 | `TASK-MONO-512` |
|---|---|---|---|
| A | 동기 검증 구현 | artist-service `/internal/**` 체인 + 컨트롤러 + **계약 선갱신**(`specs/contracts/http/`), community 클라이언트 + 테스트 | 영향 없음(역할 발급은 별개 축) |
| B | 투영 기반 검증 | community architecture.md § Service Type Composition **개정** + 컨슈머/투영/멱등/DLT + e2e | 영향 없음 |
| C | **해당 없음으로 종결** | architecture.md 에 "검증하지 않음 = 의도" 명문화 | 영향 없음 |

어느 쪽이든 `FAN-BE-045` 의 **AC-1b(스키마 + 온보딩)** 과 **AC-2/AC-3(발행 → 피드 도달 +
음성 대조)** 는 그대로 남는다 — 이 ADR 은 AC-6 하나만 게이트한다.

---

## 결정 — **A** (ACCEPTED 2026-08-11)

**동기 internal 엔드포인트.** 위 § 선택지 A 의 텍스트 그대로이며, 이 절은 그것을 **확정**할
뿐 재서술하지 않는다. § 선택지 / § 추천 / § 결과 는 **byte-unchanged** — ACCEPT 는 확정이지
재결정이 아니다.

### 무엇이 구속력을 갖나

| | 구속력 |
|---|---|
| **A 자체** — artist-service 에 `/internal/**` 표면을 두고 community-service 가 동기·fail-closed 로 호출해 `Follow.artistAccountId` 를 검증 | **binding** |
| **B 배제** — community-service 는 인바운드 이벤트 컨슈머가 **되지 않는다**. `architecture.md` L26 (`Event consumption` = `none`) · L33 *"No inbound event-consumer surface"* 는 **유지** | **binding** |
| **C 배제** — 검증하지 않는 상태를 결정으로 승격하지 않는다. `FAN-BE-045` AC-6 은 **살아 있다** | **binding** |
| § 결과 표 A 행 — artist-service `/internal/**` 체인 + 컨트롤러 + **계약 선갱신** + community 클라이언트 + 테스트 | **binding**(작업 배분) |
| `TASK-MONO-512` 는 영향 없음(역할 발급은 별개 축), 다만 선행 순서는 그대로 — `FAN-BE-045` → `MONO-512` | **binding** |

### 🔴 ACCEPT 가 결정하지 **않은** 것 — e2e 탈출구

§ 추천 말미의 🔵 는 *"A 를 고를 경우 **반드시 함께 결정되는 것** — e2e 탈출구의 모양"* 을
제기했고, 두 가지를 나란히 놓았다: **탈출구를 두지 않는다** / 두되 **거부 쪽 기본값**.
도착한 것은 **plain `A`** 이고 이 rider 는 **언급되지 않았다.**

⇒ **싣기로도 안 싣기로도 확정되지 않았다.** 이것은 `ADR-MONO-060` 이 `act` 클레임에서 겪은
것과 같은 형태이며, 그때의 처리를 그대로 따른다 — **구현이 명시적으로 답하고 그 답을
기록한다.** 조용히 membership 의 `AlwaysAllowMembershipChecker` 를 복사하는 것은 답이
아니라 **드라이버 3 위반**(검증을 넣고도 꺼진 채 초록)이다.

#### rider 의 답 — 두 번 기록된다

| 시점 | 답 | 사유 |
|---|---|---|
| `TASK-FAN-BE-045` (2026-08-11) | **탈출구를 둔다, 기본값은 거부 쪽** | live-trio e2e 에 **iam 이 없어** `client_credentials` 토큰 자체를 만들 수 없다 ⇒ 탈출구가 없으면 모든 팔로우가 fail-closed 로 닫혀 `ArtistAndPostFlowE2ETest` 가 RED. 진짜 checker 를 `@ConditionalOnMissingBean` 폴백으로 둬 **예상 못 한 설정은 전부 검증 ON** 으로 떨어지게 했다(드라이버 3 준수) |
| `TASK-FAN-INT-005` (2026-08-12) | **🔴 탈출구를 두지 않는다 — 삭제** | 위 사유가 **소멸**했다. `FanPlatformE2ETestBase` 가 iam 의 auth-service + MySQL 을 함께 띄우고 community 가 **실제 토큰**을 발급받는다 ⇒ ADR 이 애초에 권고한 *"탈출구를 두지 않는다"* 가 이제 **가능**하다. `UnverifiedArtistAccountChecker` · `community.artist-service.enabled` · e2e env 전부 제거 |

🔴 **이 표가 두 행인 것이 요점이다.** 첫 답은 틀리지 않았다 — 그 시점의 스택에서 가능한
유일한 답이었다. 바뀐 것은 결정이 아니라 **결정을 강제하던 조건**이고, 조건이 사라졌으면
그 조건이 사 온 면제도 회수하는 것이 규율이다. 남겨 두면 다음 사람은 그것을 *정책*으로
읽는다.

~~🔵 membership 쪽 탈출구(`community.membership-service.enabled`)는 **그대로 남는다**~~
✅ **2026-08-12 — `TASK-FAN-INT-006` 이 그것도 지웠다.** 위 문단은 그 탈출구를 남기는 근거로
*"ACTIVE 멤버십 행은 PortOne 결제(빌링키)를 거치는 가입 경로로만 생긴다"* 를 들었다.
🔴 **그 근거가 틀렸다** — `MockPaymentGatewayAdapter` 가 `@Profile("!portone")` 라, PortOne
프로파일을 켜지 않는 모든 스택(CI·e2e·키 없는 로컬)에서 **이미 그것이 결제 어댑터**다.
세울 결제 평면이 없었고, 그래서 e2e 는 제품 경로 그대로 구독한다.

🔵 그래서 이 ADR § Decision Drivers 3 이 지목한 모양 — 허용 빈이 **폴백**으로 선택되는 것 —
은 이제 저장소에 남아 있지 않다. artist 쪽(`havingValue="false"`, 명시 선택)은 INT-005 가,
membership 쪽(`@ConditionalOnMissingBean`, 폴백 선택)은 INT-006 이 지웠다.

### ACCEPT 가 인가하는 것 / 하지 않는 것

인가되는 것은 `TASK-FAN-BE-045` AC-6 의 **착수**뿐이다. 계약(`specs/contracts/http/`)은
CLAUDE.md § Layer Rules 대로 **구현 전에** 갱신돼야 하고, 이 ACCEPT 는 그 계약의 **내용**을
승인하지 않는다.

### 게이트 — 통과했지, 우회하지 않았다

`platform/architecture-decision-rule.md` § The ACCEPTED Gate 가 요구하는 정확형
(`ADR-004 ACCEPTED — A`)이 도착한 뒤에만 전환했다. 🔴 **직전 메시지는 넘기지 않았다** —
소유자가 *"추천대로 진행"* 이라고 했으나, 같은 문서의 § 추천 이 A 를 제안한다는 사실을
소유자의 선택으로 읽는 것이 그 규정이 *"launders an agent's own preference into an accepted
decision"* 이라며 금지하는 바로 그 행위다(`ADR-MONO-059` 가 같은 자리에서 같은 이유로 한 번
멈춰 선 기록이 있다). 멈춰서 다시 물었고, 그때 정확형이 도착했다. **self-ACCEPT 아님.**
