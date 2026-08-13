# ADR-MONO-063 — 아티스트 디렉터리의 **쓰기 평면**: 아티스트·그룹·팬덤을 누가 만드는가 (그리고 워크로드 신원이 그 문에 닿는가)

**Status:** PROPOSED
**Date:** 2026-08-13
**History:** PROPOSED 2026-08-13 (this record). **ACCEPT is a human gate — this record authorises no code by itself.** 승인은 소유자의 **정확형** 지시(`ADR-MONO-063 ACCEPTED — <A|B|C|D1|D2>`)를 요구하며, 일반적인 "진행"/"proceed"/"추천대로"/"1번" 은 이것을 승인하지 않는다. 작성 에이전트는 자기 제안을 스스로 ACCEPT 할 수 없다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate). 🔴 이 문서의 § 추천 은 **작성 에이전트의 선호**이며, 그것을 소유자의 선택으로 읽는 것이 그 규정이 금지하는 바로 그 행위다 — `ADR-MONO-059` 와 `ADR-MONO-061` 의 History 가 각각 그 이유로 한 번씩 멈춘 기록을 갖고 있다.
**Decision driver:** `TASK-MONO-522` AC-0 재측정(2026-08-13) — 세 숫자가 **전부 0** 으로 재확인됐고, 그래서 이 티켓의 전제가 산다. 아티스트 디렉터리의 9개 쓰기 매처는 **어떤 호출자로도 도달할 수 없다**.
**Related:** `TASK-MONO-522`(이 결정의 소유 티켓) · `ADR-MONO-059`(fan 저작 신원 평면 — B 배제가 **binding**, 이 ADR 이 그 경계에 붙는다) · `ADR-MONO-061`(워크로드 토큰 인가 평면 — **rider 를 이 티켓에 명시 인계**) · `TASK-MONO-512`(매처를 열지 않기로 확정하며 이 갭을 발견) · `TASK-MONO-514`(`WorkloadRoleCatalog` 구현 — admin-tier 를 이 ADR 까지 보류) · `TASK-FAN-BE-045`/`ADR-004`(artist-service 의 `/internal/**` 워크로드 체인) · `platform/security-rules.md` · `platform/contracts/jwt-standard-claims.md`

---

## 실측 (AC-0, 2026-08-13 — 소스와 라이브 양쪽, 대조군 포함)

`TASK-MONO-522` AC-0 이 요구한 세 숫자다. **셋 다 0 이면 전제가 살고, 하나라도 0 이 아니면 이 티켓은 `ADR-MONO-059` 위반을 되돌리는 작업이 된다.** 결과는 셋 다 0 이다.

| # | 축 | 소스(마이그레이션·시드 전수) | 라이브 DB (`iam_mysql-data` 볼륨) |
|---|---|---|---|
| 1 | `tenant_domain_subscription` 의 `fan` 행 | **0** | **0** |
| 2 | `fan-platform` 의 `operator_tenant_assignment` 행 | **0** | **0** |
| 3 | `fan-platform` 테넌트 계정 중 admin-tier `account_roles` 보유 | **0** | **0** |

🔴 **0 을 부재로 읽을 수 있는 이유는 대조군이 붙어 있기 때문이다.** 빈 DB 나 빗나간 술어도 0 을 낸다:

```
CONTROL  tenants=12  subs=18  account_roles=6  assignments=5  accounts=14   ← 신선 볼륨 아님
[1] 대조  ecommerce 2 · erp 3 · finance 5 · scm 3 · wms 5  = 18            ← fan 만 없다
[2] 대조  acme-corp 1 · demo-corp 2 · ecommerce 1 · globex-corp 1 = 5      ← fan-platform 만 없다
[3] 대조  fan-platform 의 account_roles = ARTIST 3 · FAN 3 = 6             ← 그 테넌트 행은 읽힌다
```

`subs=18` / `tenants=12` 는 `ADR-MONO-059` 가 2026-08-07 에 기록한 모집단(18행/12테넌트)과 **정확히 일치한다** — 그 사이에 아무도 열지 않았다. [3] 의 대조군이 특히 중요하다: fan-platform 의 역할 행이 **6건 읽히는데** admin-tier 만 0 이므로, 이 0 은 "그 테넌트를 조회하지 못했다" 가 아니다.

🔵 **곁다리 확증 — `ADR-MONO-061` 의 fail-closed 기본값이 실제로 잠겨 있다.** 라이브 `auth_db.oauth_clients` = **10 cc / 16 total**, 그 열 개의 id 가 `WorkloadRoleCatalog` 의 키 집합과 **정확히 일치**한다. 그 카탈로그에서 role 을 받는 클라이언트는 `wms-internal-services-client` 하나뿐이고(`MASTER_READ`/`MASTER_WRITE`), `community-service-client` 를 포함한 나머지 아홉은 빈 맵이다. `WorkloadRoleCatalogTest#noWorkloadClientHoldsAnAdminTierRole` 가 그것을 **주석이 아니라 단언으로** 잡고 있으며, 그 테스트의 존재 이유가 *"`TASK-MONO-522` 가 답할 때까지"* 라고 그 파일에 적혀 있다. **즉 이 ADR 이 여는 문이 아니라, 이 ADR 이 열 수도 있는 문의 자물쇠가 지금 채워져 있다.**

---

## 이것이 왜 ADR 인가 — 두 개의 구속력이 여기서 만난다

```java
// artist-service SecurityConfig — 9개 매처가 전부 이 상수를 쓴다
ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR" };
POST/PATCH/DELETE  /api/artists/** · /api/artist-groups/** · /api/fandoms/**
```

- **`ADR-MONO-059`(ACCEPTED — A)** 는 *"`B2C_CONSUMER` 테넌트를 운영자가 assume 하는 새 조합은 **열지 않는다**"* 를 binding 으로 확정했다. `fan-platform` 은 `B2C_CONSUMER` 다 ⇒ 구독 행·배정 행을 넣어 `FAN_OPERATOR` 를 파생시키는 것은 **미완의 작업이 아니라 결정에 반하는 작업**이다.
- **`ADR-MONO-061`(ACCEPTED — C)** 는 머신 토큰이 `roles` 를 실을 수 있게 만들면서, *"fan artist-service 의 `ADMIN_ROLES` 매처를 워크로드 신원에 여는가"* 를 **미결로 이 티켓에 넘겼다**. 그 ACCEPT 는 plain `C` 였고, `C + fan admin 개방` 이 나란히 제시된 적이 없으므로 **여는 것으로도 닫는 것으로도 확정되지 않았다.**

⇒ 어느 방향이든 새 ADR 이 필요하다(HARDSTOP-09). 그래서 이 문서가 있다.

### 🔴 폭발반경은 artist-service 가 아니다 — **네 이름이 community-service 와 공유된다**

이 결정의 진짜 비용은 아티스트 디렉터리에 있지 않다. `ADMIN_ROLES` 의 네 이름은 community-service 의 `ActorContext.isOperator()` 가 받는 **바로 그 네 이름**이다:

```java
// community-service ActorContext
public boolean isOperator() {
    return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN") || hasRole("FAN_OPERATOR");
}
public boolean owns(String authorAccountId) { return authorAccountId.equals(accountId) || isOperator(); }
```

`fan-platform` 테넌트의 어떤 주체든 이 네 이름 중 하나를 들면, artist-service 의 매처를 통과하는 **부수 효과로** community-service 에서 다음이 참이 된다:

1. `PublishPostUseCase` 의 `ARTIST_POST` 게이트 통과 ⇒ **운영자 대리 저작 = `ADR-MONO-059` 가 배제한 B**.
2. `owns()` 가 테넌트 내 **모든 저자**의 `MEMBERS_ONLY`/`PREMIUM` 글에 편집·가시성을 준다.

즉 *"아티스트를 만들 수 있게 한다"* 를 **이 네 이름 중 하나를 발급해서** 달성하면, 그것이 사람이든 기계든 **옆문으로 B 를 여는 것**이다. `TASK-MONO-522` 의 Failure Scenario 가 정확히 이것을 적어 두었다. 아래 선택지는 이 성질로 갈린다 — **네 이름을 건드리는가, 아닌가.**

🔵 **실행 가능성 확인(어느 개방안이든 공통):** `POST /api/artists` 의 `RegisterArtistRequest` 는 `accountId` 를 **필수 필드로 받는다**. 따라서 API 로 만든 아티스트도 `FAN-BE-045` 가 세운 조인 보증(`artists.account_id` ↔ `follows.artistAccountId`)을 시드와 동일하게 재현할 수 있다 ⇒ AC-4 의 "회수" 는 기술적으로 가능하다. 🔴 다만 엔티티 id 는 **서버가 UUIDv7 로 만든다** — 시드의 고정 id 리터럴은 게시물 블록이 이미 치른 것과 같은 대가(발행 후 되찾기)를 치러야 한다.

---

## 선택지

### A. `ADMIN_ROLES` 매처를 **워크로드 신원에 연다** (rider = **예**)

`WorkloadRoleCatalog` 에서 fan 쪽 cc 클라이언트(또는 신설 클라이언트)에 새 scope(예: `artist.admin`)를 달고 그 scope 에 `ADMIN` 을 매단다. 매처는 **코드 변경 0** — 발급만 바뀐다. 호출자는 `seed-fan.sh` 가 cc 토큰을 받아 POST 하는 형태가 되고, 그러면 AC-4 의 직접-DB 블록이 **회수된다**.

- ✅ 매처·컨트롤러·역할 모델 **전부 그대로**. 변경은 발급 표 한 줄.
- ✅ 실재하는 호출자가 생긴다(데모 시드). "받는 쪽은 있는데 주는 쪽이 없다" 가 해소된다.
- ❌ **위 § 폭발반경이 그대로 발화한다.** 그 토큰은 `tenant_id=fan-platform` 이라 community-service 의 `TenantClaimValidator` 를 통과하고, `roles=[ADMIN]` 이므로 `isOperator()` 가 **기계에 대해 참**이 된다 ⇒ `ADR-MONO-059` 가 배제한 B 를 **기계에게** 여는 것이다. 이 ADR 이 그것을 **명시적으로 허용**해야 하며, 조용히 지나가면 안 된다.
- ❌ `WorkloadRoleCatalogTest#noWorkloadClientHoldsAnAdminTierRole` 를 **삭제해야 한다**. 그 테스트는 이 질문을 기다리는 홀드이므로 삭제 자체는 정당하지만, 삭제하는 순간 *"어떤 워크로드도 admin-tier 를 못 든다"* 라는 저장소 전역 불변식이 사라진다 — 19개 서비스 / 6개 프로젝트가 그 불변식 위에 있다.
- ❌ **사람이 아닌 것이 아티스트를 만든다** (티켓 Edge Case 가 결정의 일부로 지목한 판단).

### B. **새 역할**을 만들어 사람 평면으로 연다 (rider = **아니오**)

`ARTIST_ADMIN`(가칭)을 신설하고 9개 매처를 `hasAnyRole(ADMIN_ROLES + ARTIST_ADMIN)` 으로 넓힌다. 발급은 `account_roles` 한 행 — `R__06` 이 `ARTIST` 를 넣는 것과 **완전히 같은 모양**이고, 그래서 새 발급 메커니즘이 필요 없다.

- ✅ 🔴 **`isOperator()` 의 집합을 건드리지 않는다.** 새 이름은 그 넷에 없으므로 `ADR-MONO-059` 의 B 는 **닫힌 채로 남는다** — 이것이 B 안의 핵심 성질이고, A 와 갈리는 지점이다.
- ✅ 발급 평면이 이미 존재한다(파생이 아니라 **부여** 역할). 구독 행도 배정 행도 필요 없으므로 `ADR-MONO-059` 의 binding 을 스치지 않는다.
- ✅ 사람이 아티스트를 만든다 — 도메인이 모델링하려던 모양에 가장 가깝다.
- ❌ 역할 모델에 역할이 하나 는다. 그리고 *"그 계정은 운영자인가"* 라는 질문이 생긴다 — 구조적으로는 **아니다**(테넌트를 assume 할 수 없고 `isOperator()` 도 아니다). 그 구분이 이 안을 정당하게 만드는 전부이므로 ADR 에 명시해야 한다.
- ❌ **그 역할을 누가 드는가**를 정해야 한다(데모 계정 하나? 아티스트 본인? — 아티스트 본인이면 "아티스트가 다른 아티스트를 만든다").
- ❌ 🔵 **UI 가 없다.** `ProductCatalog` 에 `fan` 엔트리가 없어 콘솔에 화면이 생기지 않는다 ⇒ 실사용 호출자는 여전히 시드뿐이다.

### C. `/internal/**` 워크로드 체인에 **쓰기 표면을 추가한다** (rider = **아니오 — 다른 문을 준다**)

`ADMIN_ROLES` 매처는 손대지 않고 도달 불가인 채로 둔 뒤, `InternalArtistController` 쪽에 쓰기 오퍼레이션을 더한다. 게이트는 이미 있는 `ROLE_INTERNAL`(= `artist.read` 계열 머신 scope)이다.

- ✅ **역할 평면 변경 0.** `ADR-MONO-059` 도 `ADR-MONO-061` 의 fail-closed 기본값도 건드리지 않는다.
- ✅ 이미 있는 문의 확장이다 — artist-service 는 `ADR-004`/`FAN-BE-045` 로 워크로드 체인과 실재 원격 호출자(community-service)를 이미 갖고 있다.
- ✅ `platform/security-rules.md` 의 *"internal-only = subject allow-list **또는** required scope"* 규정에 그대로 맞는다.
- ❌ **게이트웨이 라우팅이 안 된다**(`/api/v1/**` 만 매핑) — 도커 내부망에서만 도달한다. 시드가 그 안에서 도는지는 구현 AC 로 확인해야 한다.
- ❌ 🔴 **공개 API 의 쓰기 표면은 여전히 죽은 채 남는다** — 같은 결함의 쌍둥이가 생긴다. 그래서 C 를 고르면 9개 매처의 처리(D1/D2 의 하위 질문)를 **함께** 정해야 한다.
- ❌ *"아티스트 디렉터리 관리가 internal-only 표면인가"* 는 규정이 아니라 **제품 판단**이다.

### D. **닫는다 — v1 범위 밖으로 명문화한다** (rider = **아니오**)

쓰기 평면을 열지 않기로 확정하고, 그 사실을 코드와 시드에 적는다. 🔴 **두 하위형이 있고, 둘은 성격이 다르다:**

- **D1 — 매처 9개를 유지하고 사유를 이 결정으로 갱신한다.** `TASK-MONO-512` 가 이미 써 둔 주석을 "522 소관" 에서 "ADR-063 이 닫았다" 로 확정한다. `ADR-MONO-059` 를 **전혀 건드리지 않는다**.
- **D2 — 매처 9개를 제거한다.** 제거하면 그 라우트는 `anyRequestDenied()` 로 떨어져 **기본 거부**가 된다(아무도 못 드는 역할을 명시하는 것보다 정직한 형태). 🔴 그러나 `ADMIN_ROLES` 는 `ADR-MONO-059` 가 열거한 **`FAN_OPERATOR` 수용부 중 하나**이고, 그 ADR 은 수용부 제거(그 문서의 옵션 D)를 **채택하지 않았다**. 따라서 D2 는 `ADR-MONO-059` 의 D 를 부분적으로 재개하는 것이며, **이 ADR 이 그것을 명시 승인해야** 성립한다(`TASK-MONO-522` Out of Scope 가 정확히 이 점을 지적한다).

- ✅ 결정 비용 최소. 포트폴리오 데모로서 *"아티스트 등록은 v1 범위 밖"* 은 완전히 정당한 답이다.
- ✅ 🔵 **데모는 막히지 않는다** — 디렉터리는 시드가 넣고 읽기 라우트는 정상 동작한다.
- ❌ 직접-DB 시드가 **영구화**된다. AC-4 는 그것을 "잠정" 에서 "결정에 의해 영구히" 로 바꾸는 것으로 충족된다(제거가 아니라 확정).

---

## 🔴 `ADR-MONO-061` 이 넘긴 rider 에 대한 답 — 선택지별로 명시한다

인계된 질문: **"fan artist-service 의 `ADMIN_ROLES` 매처를 워크로드 신원에 여는가"**

| 선택지 | rider 답 | `WorkloadRoleCatalog` | `isOperator()` 가 기계에 대해 참이 되나 |
|---|---|---|---|
| **A** | **예** | fan cc 클라이언트에 `ADMIN` 부여, admin-tier 테스트 **삭제** | **참이 된다** — `ADR-MONO-059` 의 B 가 기계에게 열린다. 이 ADR 이 명시 허용해야 함 |
| **B** | 아니오 | 변경 없음(빈 맵 유지) | 거짓 유지 |
| **C** | 아니오 (다른 문) | 변경 없음 | 거짓 유지 |
| **D1/D2** | 아니오 | 변경 없음 | 거짓 유지 |

🔴 **조용히 빠뜨리는 것은 답이 아니다** — `ADR-MONO-061` § "이 ACCEPT 가 결정하지 않은 것" 이 그 점을 명시적으로 적었다. 어느 선택지를 고르든 이 표의 해당 행이 이 ADR 의 결론으로 기록된다.

---

## 추천 — **D1** (다만 이것은 제안이지 결정이 아니다)

**모든 개방안의 유일한 호출자가 데모 시드다.** 콘솔에 화면이 없고(`ProductCatalog` 에 `fan` 엔트리 없음), 다른 서비스도 아티스트를 만들지 않는다. 그러면 A·B·C 는 *"시드가 우회하려고 만들어진 부재를 메우려고, 그 시드만이 호출하는 API 를 여는 것"* 이 된다 — `TASK-MONO-512` 가 처음 제기한 **"받는 쪽은 있는데 주는 쪽이 없다"** 와 같은 모양을 한 층 위에서 반복하는 것이고, `TASK-MONO-522` 배경이 스스로 그 위험을 적어 두었다.

그리고 **A 는 추천하지 않는 이유가 따로 있다**: 얻는 것은 발급 표 한 줄인데, 대가로 *"어떤 워크로드도 admin-tier 를 들지 않는다"* 는 **19개 서비스 / 6개 프로젝트에 걸친 불변식**을 잃고, `ADR-MONO-059` 가 배제한 B 를 기계에게 연다. 가장 작은 코드 변경이 가장 큰 의미 변경인 경우다.

**D2 가 아니라 D1 을 추천하는 이유**: 매처 제거는 그 자체로 `ADR-MONO-059` 의 미채택 옵션을 재개하는 일이라 결정 하나에 결정 둘을 싣는다. 지금 필요한 것은 *"쓰기 평면을 열지 않는다"* 라는 판정 하나이고, 죽은 수용부 정리는 `FAN_OPERATOR` 수용부 4곳을 **함께** 보는 별도 티켓이 더 정직하다.

🔵 **B 도 정당하다 — 열기로 한다면 B 다.** `isOperator()` 를 건드리지 않고 여는 **유일한 사람 평면**이고, 발급 메커니즘이 이미 있다. *"아티스트 디렉터리는 관리 가능해야 한다"* 가 제품 판단이라면 B 가 그 답이며, 그 경우 이 ADR 은 D1 대신 B 로 ACCEPT 되면 된다. 🔵 **C 도 정당하다** — 특히 데모 시드를 API 호출자로 바꾸는 것(AC-4 회수)이 목표라면, 역할 평면을 전혀 건드리지 않고 그것을 달성하는 유일한 안이다.

---

## 결과 (ACCEPTED 시) — `TASK-MONO-522` 가 수행할 것

| 안 | AC-2/AC-3 | AC-4 (시드 `--why`) | 추가 |
|---|---|---|---|
| **A** | 카탈로그에 부여 + cc 토큰으로 `POST /api/v1/artists` **201** 실측, 자격 없는 호출자는 **403** 대조. 🔴 `isOperator()` 가 참이 되는지 **반드시 함께 판정**하고 이 ADR 이 허용한 범위인지 대조 | **회수**(직접-DB → API) | admin-tier 테스트 삭제 + 그 불변식 상실을 `jwt-standard-claims.md` 에 기록 |
| **B** | 새 역할 정의 + `account_roles` 발급 + 그 토큰으로 **201** 실측, 없는 토큰 **403** 대조. 🔴 그 역할이 `isOperator()` 를 참으로 만들지 **않음**을 단언으로 고정 | **회수** | 역할 이름·보유 주체 확정, `WorkloadRoleCatalog` 는 무변경 |
| **C** | `/internal/artists/**` 쓰기 + cc 토큰 실측, 엔드유저 토큰 **403** 대조 | **회수**(내부망 도달 가능할 때) | 🔴 9개 매처의 처리를 D1/D2 중 하나로 **함께** 확정 |
| **D1** | 매처 9개 **유지**, `SecurityConfig` 주석을 이 ADR 로 갱신 | *"결정에 의해 영구히 직접-DB"* 로 **잠정성 제거** | `ActorContext` 주석도 같은 결정으로 정합 |
| **D2** | 매처 9개 **제거**(→ 기본 거부), 제거 사유 기록 | 동일 | 🔴 `FAN_OPERATOR` 수용부 축소이므로 `ADR-MONO-059` 와의 관계를 이 ADR 이 명시 승인한 범위로 한정 |

어느 쪽이든 **`FollowArtistUseCase` 의 무검증 저장**은 이 ADR 의 주제가 아니다(`FAN-BE-045` 가 `artists.account_id` 검증으로 이미 답했다).

### ACCEPT 가 인가할 것 / 하지 않을 것

인가한다: `TASK-MONO-522` 가 위 표의 **선택된 행**을 수행하는 것. 인가하지 않는다: 다른 행의 작업, `ADR-MONO-059`/`ADR-MONO-061` 의 재해석, `FAN_OPERATOR` 수용부 4곳의 일괄 정리(D2 가 명시 승인하는 1곳 제외), 콘솔에 fan 제품 엔트리를 여는 것. 각 단계는 여전히 자기 task 다(HARDSTOP-09).
