# Task ID

TASK-MONO-512

# Title

`FAN_OPERATOR` 는 IdP 가 발급할 수 없는 역할이다 — 팬 도메인의 운영자 평면이 세 서비스에 배선돼 있는데 그 역할에 도달하는 테넌트가 하나도 없다

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- security
- demo

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다.

팬 도메인의 **쓰기 표면은 전부 운영자 역할을 기다리고 있다**:

| 어디 | 무엇 |
|---|---|
| `iam` `OperatorRoleDerivation` | `case "fan", "fan-platform" -> List.of("FAN_OPERATOR")` |
| `artist-service` `SecurityConfig` | `ADMIN_ROLES = {ADMIN, OPERATOR, SUPER_ADMIN, FAN_OPERATOR}` — 아티스트·그룹·팬덤의 POST/PATCH/DELETE 전부 |
| `community-service` `ActorContext.isOperator()` | `FAN_OPERATOR` 를 명시적으로 받는다(`TASK-MONO-417` 이 추가) |

세 곳 모두 `FAN_OPERATOR` 를 **받는다**. 그런데 그것을 **주는** 경로가 없다.

## 실측 (2026-08-05, 로컬 `iam fan console` 슬라이스)

`OperatorRoleDerivation` 은 **선택된 테넌트의 ACTIVE 구독**을 키로 삼는다. 그 arm 에
도달하려면 어떤 테넌트가 `fan`(또는 `fan-platform`) 을 구독해야 하는데:

```
demo-corp 구독 = [ecommerce, wms, scm, erp, finance]     ← fan 없음
fan-platform  = tenant_domain_subscription 행 자체가 없음
```

그래서 두 갈래가 모두 막힌다:

```
# (1) demo-corp 를 assume — 역할에 FAN_OPERATOR 가 없고, 애초에 게이트웨이가 막는다
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_*, INBOUND_*, INVENTORY_*, MASTER_READ]
GET  /api/v1/artists   403 {"code":"TENANT_FORBIDDEN",
                            "message":"tenant_id 'demo-corp' is not allowed"}
POST /api/v1/artists   403 (동일)

# (2) fan-platform 을 assume — IAM 이 거절한다
{"error":"invalid_grant",
 "error_description":"operator is not assigned to the selected tenant"}
```

즉 **두 개의 독립된 결여**가 겹쳐 있다:

1. `R__seed_demo_operator.sql` 에 `operator_tenant_assignment → fan-platform` 행이 없다
2. 있더라도 `tenant_domain_subscription (fan-platform, 'fan')` 행이 없어 역할이 파생되지 않는다

# Goal

팬 도메인의 운영자 표면이 **실제 호출자에게 도달 가능**해진다 — 즉 콘솔 로그인 →
assume → `POST /api/v1/artists` 가 201 을 내는 경로가 존재한다. 그리고 그 경로가
없어야 한다면, **그 사실이 코드에 적혀 있어야 한다**(받는 쪽 세 곳이 존재하지 않는
역할을 기다리는 상태로 남지 않는다).

---

# Scope

## In Scope

- 어느 테넌트가 `fan` 도메인을 구독하는가에 대한 결정 + 그 시드
- `demo-operator` 의 `fan-platform` assume 배정(필요하다면)
- 반대 결정(팬은 운영자 평면을 갖지 않는다)일 경우 세 곳의 `FAN_OPERATOR` 처리 정리

## Out of Scope

- `ARTIST_POST` 저자 모델 — `TASK-FAN-BE-045`. **(가) 를 고쳐도 (나) 는 남는다**
- 콘솔에 팬 운영 섹션 추가 — `ProductCatalog` 에 `fan` 엔트리가 없다(확인함).
  이 티켓은 **API 도달 가능성**만 다룬다

---

# 🔴 결정이 필요한 지점 — 그래서 ADR 이 필요할 수 있다

`TASK-BE-576` 이 확립한 구분이 여기서 그대로 재현된다:

> `demo-corp` = **권한**(구독에서 파생되는 역할) / 도메인 테넌트 = **가시성**(행이 사는 곳)

팬의 행은 전부 `tenant_id = 'fan-platform'` 이고 게이트웨이가
`required-tenant-id: fan-platform` 으로 **다른 테넌트의 토큰을 아예 거절한다**
(ecommerce 는 거절하지 않고 통과시킨 뒤 행 필터에서 비웠다 — 팬은 엣지에서 잘린다).
따라서 `demo-corp` 에 `fan` 구독을 얹는 것만으로는 **아무것도 열리지 않는다**.
운영자가 `fan-platform` 에 서야 한다.

그것이 함의하는 것: `fan-platform` 은 B2C_CONSUMER 테넌트인데 **운영자가 assume 하는
테넌트**가 된다. 이 저장소가 그런 조합을 허용하는지가 이 티켓의 실제 질문이다.

---

# ✅ AC-0 재측정 (2026-08-07) — 전제 유지, 그리고 **모집단이 티켓보다 넓다**

## 두 갈래 모두 그대로 막혀 있다

```
assume fan-platform  → 실패 (토큰 발급 안 됨)
assume fan           → 실패
assume demo-corp     → 성공 (대조군 — 계측기가 동작한다는 증거)
```

## 🔴 티켓이 "demo-corp 에 fan 이 없다" 라고 적었는데, 실은 **아무 데도 없다**

`tenant_domain_subscription` 전수(18행, 12테넌트):

```
ecommerce  2      erp  3      finance  5      scm  3      wms  5
fan        0   ← 전 테넌트 통틀어 0건
```

테넌트별로도:

```
demo-corp     ecommerce,erp,finance,scm,wms     ← fan 없음 (티켓 그대로)
fan-platform  NULL                              ← 테넌트는 있는데 구독 0행
```

그리고 `operator_tenant_assignment` 의 테넌트는 **4개뿐**(`acme-corp`·`demo-corp`·
`ecommerce`·`globex-corp`) — `fan-platform` 행 없음(티켓의 결여 #1 확인).

⇒ 티켓은 이것을 *"demo-corp 의 구독 목록에 fan 이 빠졌다"* 로 읽었지만, 실측은
**"이 시스템의 어떤 테넌트도 `fan` 을 구독하지 않는다"** 다. 한 행을 더하는 문제가
아니라 **`fan` 이라는 도메인 키가 구독 평면에 존재한 적이 없다**는 뜻이고, AC-1 의
결정("팬이 운영자 평면을 갖는가")은 그만큼 더 근본적인 질문이다.

## 🔴🔴 신규 — `FAN_OPERATOR` 만이 아니다. **`ROLE_ARTIST` 도 발급 경로가 0** 이다

`PublishPostUseCase:32` 는 `ARTIST` 역할을 **저작 권한**으로 받는다:

```java
if (cmd.postType() == PostType.ARTIST_POST
        && !actor.hasRole(ROLE_ARTIST)
        && !actor.isOperator()) {
    throw new PermissionDeniedException("ARTIST role required to publish ARTIST_POST");
}
```

🔴 **정정 (2026-08-07, 같은 날 늦게).** 이 인용문은 처음에 `!hasRole(ROLE_FAN) &&
!hasRole(ROLE_ARTIST) && !isOperator()` 로 잘못 적혀 PR #3255 로 머지됐다. 실제 조건에
`ROLE_FAN` 은 **없다**. `ADR-003` 이 같은 코드를 정확히 인용하고 있었는데 나는 그것을
열지 않고 내 grep 결과를 옮겨 적었다 — **이 세션이 반복해서 벌받은 바로 그 실수**를,
그 실수를 문서화하는 작업 중에 저질렀다.

🔵 **결론은 바뀌지 않고 오히려 더 강해진다**: `ARTIST_POST` 는 `ARTIST` **또는** 운영자를
요구하는데, `ARTIST` 는 iam 에 0건이고 fan 의 `isOperator()` 가 받는 `FAN_OPERATOR` 도
발급 경로가 0 이다(이 티켓 본문). ⇒ **두 갈래가 모두 닫혀 있어** 실제 호출자는
`ARTIST_POST` 를 만들 수 없다.


그런데 `projects/iam-platform` 전체(`*.java`/`*.yml`/`*.sql`)에서 `ARTIST` **0건**.

🔵 **계측기 검증**: 같은 글롭으로 `FAN_OPERATOR` 는 3건 잡힌다(`DelegatableRoleCatalog`
등) ⇒ 0건은 탐지 실패가 아니라 진짜 부재다.

⇒ **AC-0 이 요구한 "받는 곳 전수" 의 답은 3곳이 아니라, 역할이 2종이다.**
`FAN_OPERATOR`(3곳) + `ARTIST`(1곳, 저작 게이트). AC-1 은 두 역할을 함께 결정해야 한다
— `ARTIST` 만 열면 [[TASK-FAN-BE-045]] 의 저자 문제와 정면으로 얽힌다.

---

---

# ✅ 게이트 해제 — `ADR-MONO-059` **ACCEPTED — A** (2026-08-07)

AC-0 재측정이 이 티켓과 **`TASK-FAN-BE-045`** 을 같은 코드 한 줄로 수렴시켰다:
`PublishPostUseCase` 의 `ARTIST_POST` 게이트는 `hasRole("ARTIST")` **또는**
`isOperator()` 를 요구하는데 **둘 다 발급 경로가 0** 이다. 그래서 두 티켓의 결정을
[`docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md`](../../docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md)
**하나로 묶었고**, 소유자가 **A(아티스트에게 실제 계정)** 를 정확형으로 승인했다.

## 🔴 A 채택이 이 티켓을 **좁힌다** — 그대로 착수하면 안 된다

ADR § 결과 표 A 행: *"`MONO-512` = **좁혀서** 재작성(역할 발급만)"*.

- **B(운영자 대리 저작)가 배제됐다.** ⇒ 이 티켓의 원래 질문 *"`B2C_CONSUMER` 테넌트를
  운영자가 assume 하는 평면을 여는가"* 는 **답이 났다 — 열지 않는다.** `demo-corp` 에
  `fan` 구독을 얹거나 `operator_tenant_assignment` 에 `fan-platform` 을 넣는 작업은
  **범위 밖**이 됐다.
- **남는 질문은 하나뿐**: `artists.account_id` 가 가리키는 계정에 **`ARTIST` 역할을 누가
  발급하나**. ADR § 추천 이 *"A 를 고르더라도 512 의 질문은 남는다 … 다만 훨씬 좁다"* 로
  이미 명명했다 — 테넌트 평면의 새 조합이 아니라 `fan-platform` 안의 역할 부여 문제다.
- **D 는 채택되지 않았다** ⇒ `FAN_OPERATOR` 3곳 · `ARTIST` 1곳 수용부를 **죽은 코드로
  제거하지 않는다**. 아래 AC-4("닫는다로 결정했다면")는 **해당 없음**으로 종결.

🔵 **선후관계**: A 에서는 `FAN-BE-045`(스키마 + 온보딩)가 계정을 만들고, 이 티켓이 그
계정에 역할을 붙인다. 두 티켓은 **같은 worktree 에서 직렬로** 진행하는 편이 안전하다 —
둘 다 `iam-platform` 의 역할 파생 경로와 fan 의 `ActorContext` 를 건드린다.

## ⛔ 착수 보류 (2026-08-11) — 선행이 아직 안 끝났다

이 티켓의 **AC-2 는 `artists.account_id` 가 이미 존재해야 성립한다**("그 계정에 `ARTIST`
역할이 붙는지"). 그 컬럼과 온보딩은 `FAN-BE-045` AC-1b 이고, 아직 랜딩되지 않았다.
⇒ **`FAN-BE-045` 선행, 이 티켓 후행.** 순서를 바꾸면 붙일 계정이 없다.

🔴 **2026-08-11 착수 시도에서 순서를 반대로 추천했다가 티켓 본문을 읽고 정정했다** — 위
🔵 선후관계 줄과 `ADR-MONO-059` § 결과 표 A 행이 둘 다 이 순서를 지정하고 있었다.

✅ **선행의 ADR 게이트는 풀렸다 (2026-08-11)** — `ADR-004` **ACCEPTED — A**(동기 internal
엔드포인트). 그 결정은 `follows` 와 `artists` 가 **다른 DB** 라는, `ADR-MONO-059` 가 모른 채
AC-6 을 배정했던 사실을 메운 것이고 **이 티켓의 범위에는 영향이 없다**(역할 발급은 별개 축).

⇒ 이제 이 티켓의 대기 사유는 게이트가 아니라 **`FAN-BE-045` 의 스키마·온보딩이 아직
랜딩되지 않은 것** 하나뿐이다. 그것이 머지되면 바로 착수 가능하다.

## 🔴 이 티켓이 인수받는 것 — `FAN-BE-045` AC-5(시드 회수) (2026-08-11 실측)

`FAN-BE-045` 착수 1회차가 재보니 **시드 회수를 막는 것은 그 티켓이 아니라 이 티켓**이다:

```java
// artist-service config/SecurityConfig.java
ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR" };
.requestMatchers(HttpMethod.POST, "/api/artists/**", "/api/artists").hasAnyRole(ADMIN_ROLES)
```

`seed-fan.sh` 가 아티스트 3명을 **직접 DB INSERT** 하는 이유가 이것이다 — API 생성이
**403 FORBIDDEN**(실측)이고, 그 역할을 발급할 경로가 없다는 것이 **이 티켓의 결함**이다.
`FAN-BE-045` 는 AC-5 를 *"안 옮긴다 + 사유"* 로 닫고 넘긴다.

⇒ **이 티켓이 닫힐 때 `seed-fan.sh` 의 `dbexec --why` 블록을 함께 회수한다.**
🔴 고쳐진 결함의 면제를 회수하지 않으면 그 면제가 회귀를 가린다 — 이 저장소가 반복해서
물린 지점이고, `--why` 원문이 두 티켓을 함께 인용하고 있어 **한쪽만 닫으면 남는다.**
🔵 옮길 때 **저자는 여전히 데모 계정이 아니어야 한다**(데모 계정이 저자면 `actor.owns()`
로 가시성 게이팅이 통째로 우회돼 시연이 공허해진다 — `FAN-BE-045` AC-5 원문의 경고).

# ✅ 착수 — AC-0 재재측정 (2026-08-11). **이 티켓 본문의 핵심 판단 하나가 틀렸다**

보류가 풀린 뒤 착수 전에 다시 쟀고, 세 가지가 나왔다.

## 🔴🔴 ① "`ARTIST` 가 iam 전체에 0건" 은 맞다. 그것을 **발급 경로의 부재**로 읽은 것이 틀렸다

AC-0(2026-08-07)이 `projects/iam-platform` 전수에서 `ARTIST` **0건**을 찾고 계측기 검증
(`FAN_OPERATOR` 3건)까지 붙였다. **탐지는 옳았고 해석이 틀렸다** — 역할 평면은 이미 열려 있다:

```
AccountRoleName.validate()      ^[A-Z][A-Z0-9_]*$ 정규식 **그것뿐**
                                (화이트리스트·카탈로그·테넌트별 허용목록 0건;
                                 자기 javadoc 이 그것을 "future task" 로 미룬다)
AddAccountRoleUseCase           PATCH /internal/tenants/{t}/accounts/{a}/roles:add (멱등)
TenantClaimTokenCustomizer      저장된 account_roles → roles 클레임
```

⇒ **`ARTIST` 는 코드가 아니라 행이 없었을 뿐이다.** 이 티켓의 크기가 "새 역할 평면 설계" 에서
**"프로비저닝 행을 넣고 토큰에 실린 것을 실측"** 으로 줄어든다. 티켓이 처음부터 못박아 둔
*"DB 에 문자열이 있는 것이 판정이 아니라 토큰에 실린 roles 가 판정"* 은 정확히 이 구조 때문에 옳다.

🔵 대조: `FAN_OPERATOR` 는 **다른 종류의 0건**이다 — `tenant_domain_subscription` 에서
**파생**되므로 구독 행이 필요하고, 그 행을 넣는 것은 `ADR-MONO-059` 가 배제했다.
**같은 "0건" 인데 비용이 다르다**(부여 역할 vs 파생 역할).

## 🔴🔴 ② 신규 — 저장된 역할은 시드를 **대체한다.** `ARTIST` 만 주면 `FAN` 이 사라진다

`TenantClaimTokenCustomizer#populateRoles` 원문:

> stored `account_roles` present → **emitted verbatim (no seed)** · 비었을 때만 `RoleSeedPolicy`

`RoleSeedPolicy.seed("fan-platform") = [FAN]` 이므로, 아티스트 계정에 `ARTIST` **한 줄만**
넣으면 그 계정의 토큰은 `roles=["ARTIST"]` 가 되어 **`FAN` 을 잃는다**.

🔴 이것이 위험한 이유는 지금 **아무 증상도 없기 때문**이다. 실측: 팬 게이트웨이는
`RoleAdmissions.roleOrScope()` 로 **역할이 있기만 하면** 통과시키고, `"FAN"` 을 읽는
프로덕션 코드는 fan-platform 전체에 **0건**(테스트 픽스처에만 존재). 즉 지금 넣으면 초록이고,
누군가 `FAN` 을 읽기 시작하는 날 아티스트만 조용히 떨어져 나간다.
⇒ **`[FAN, ARTIST]` 둘 다 저장한다.** `FanArtistDemoSeedTest` 가 그것을 고정한다.

## 🔴 ③ AC-5 의 `--why` 는 **한 덩어리가 아니라 두 블록**이고, 차단 사유가 서로 다르다

| 블록 | 무엇 | 차단 사유 | 이 티켓이 푸는가 |
|---|---|---|---|
| 1 | 아티스트 · 그룹 · 팬덤 | `POST /api/v1/artists` = `hasAnyRole(ADMIN,OPERATOR,SUPER_ADMIN,**FAN_OPERATOR**)` | ❌ **아니다** |
| 2 | `ARTIST_POST` × 3 가시성 | 실재하는 계정 + **`ARTIST`** 역할 | ✅ 그렇다 |

블록 1 이 요구하는 것은 **admin-tier 역할**이지 `ARTIST` 가 아니다. 그리고 `ADR-MONO-059` A 가
B 를 배제하며 *"B2C_CONSUMER 를 운영자가 assume 하는 조합은 열지 않는다"* 를 binding 으로
확정했으므로, 블록 1 을 여는 것은 **미완의 작업이 아니라 결정에 반하는 작업**이다.
(`account_roles` 에 `ADMIN` 을 부여하는 우회로가 있지만, 그러면 community 의
`isOperator()` 가 참이 되어 **배제된 B 가 옆문으로 열린다** — `owns()` 로 테넌트 내 모든
저자의 게이팅까지 우회된다.)

⇒ **회수는 블록 2 만.** 블록 1 은 `--why` 를 *"MONO-512 가 풀 것"* 에서
*"결정에 의해 API 호출자가 없다 + 남은 질문은 `TASK-MONO-522`"* 로 **다시 썼다**.
🔴 해소된 사유를 그대로 두는 것만이 함정이 아니다 — **해소되지 않을 사유를 "곧 풀린다" 로
남겨 두는 것**도 같은 함정이고, 이쪽이 더 오래 간다.

---

# 🔧 구현 (2026-08-11)

## 발급 경로 — **데이터**이고, iam 코드는 한 줄도 바뀌지 않았다

| 파일 | 내용 |
|---|---|
| account-service `migration-dev/V9006` | 아티스트 3명의 `identities` + `accounts` + `account_roles [FAN, ARTIST]` |
| auth-service `migration-dev/V9002` | 같은 3계정의 `credentials` (`lumi@/noah@/sea@demo.com`, 비밀번호는 데모와 동일) |

🔵 **계정 id = 아티스트 엔티티 id.** `artists.account_id` 를 새 UUID 로 **재지정하지 않고**
그 항등값에 계정을 **만들었다**. 재지정하면 이미 시드된 데모 DB 의 `follows`(API 로 만들어진
행)와 기존 게시물이 옛 값을 든 채 남고, 시드의 아티스트 INSERT 는 `WHERE NOT EXISTS` 라
갱신하지 않으므로 **가장 많이 시연된 스택에서 피드가 조용히 빈다**. 같은 id 로 만들면
`FAN-BE-045` 의 항등 백필이 **소급해서 참**이 되고 다른 테이블은 움직이지 않는다.

## 시드 (AC-5) — 블록 2 회수

`seed-fan.sh` 가 3단계가 됐다: 직접-DB(아티스트/그룹/팬덤) → **아티스트 토큰(API)** → 소비자 토큰(API).
아티스트가 **자기 계정으로 로그인해서** 자기 글을 쓴다. 부수 효과로 `post_status_history` 를
손으로 넣던 블록이 사라졌다 — 이력도 아웃박스 이벤트도 이제 도메인이 만든다.
🔵 게시물 id 가 서버 생성 UUIDv7 이 되어 고정 리터럴을 쓸 수 없으므로, 발행 후 (저자, 제목)으로
되찾아 댓글·리액션에 넘긴다. `published_at` 이 고정 리터럴이 아니게 된 대가는 2번 머리에 적었다
(2회차 실행은 탐지에서 걸러지므로 **상대 순서와 최종 상태는 수렴한다**).

🔴 시드가 스스로 판정한다: 토큰의 `sub` 이 아티스트 엔티티 id 와 같은지, 그리고 **`roles` 에
`ARTIST` 가 실렸는지**를 발행 전에 확인하고 아니면 사유를 지목해 실패한다. 계정·자격증명만 있고
`account_roles` 행이 빠지면 로그인은 멀쩡히 되고 토큰도 나오는데 발행만 403 이라, 여기서 안 보면
원인이 세 겹 밑이다.

## 🔴 가드가 처음엔 물지 않았다 — bite 검증이 잡았다

`FanArtistDemoSeedTest` 는 **세 트리의 파일 3개**(auth `V9002` · account `V9006` ·
`infra/demo/seed/seed-fan.sh`)를 서로 대조한다. 이들은 서로 다른 3개 DB 에 살아서 FK 도
컴파일러도 이들을 비교하지 못한다.

그런데 첫 bite 검증에서 **account `V9006` 의 계정 id 를 틀리게 바꿔도 초록**이었다.
원인은 테스트가 아니라 **Gradle**: 사이드 파일은 `:test` 태스크의 선언된 입력이 아니라
태스크가 `UP-TO-DATE` 로 건너뛰어졌다. `--rerun-tasks` 로 강제하니 5개 중 3개가 RED —
**가드는 옳았고 러너가 그것을 실행하지 않았다.**
⇒ 세 파일을 `inputs.file` 로 선언했다. 재검증: 강제 없이 사이드 파일만 바꿔 **RED(3/5)**,
되돌려 **GREEN(5/5)**. 같은 사각지대를 갖고 있던 기존 `DemoSeedCredentialTest` 의
admin-service 시드도 함께 선언했다(형제를 낙오시키면 다음 사람은 기전이 안 듣는다고 결론낸다).

🔴 **남은 사각지대는 명시한다**: `ci.yml` 의 `iam` 경로 필터가 `projects/iam-platform/**` 이라
**`seed-fan.sh` 만 고친 PR 은 이 레인을 아예 깨우지 않는다.** Gradle 입력으로는 못 고치는
층이고, `TASK-MONO-522` 가 물려받는다.

# Acceptance Criteria

- [x] **AC-0 (재측정) — 완료 2026-08-07 + 착수 시 재재측정 2026-08-11.** 두 갈래 재현 ✅ · 받는 곳 전수 결과
      **역할이 2종**(`FAN_OPERATOR` 3곳 + `ARTIST` 1곳) · 모집단은 "demo-corp 결여" 가
      아니라 **`fan` 구독 0/18행**. 상세는 위 §
- [x] **AC-1 (결정) — 완료 2026-08-07.** `ADR-MONO-059` **ACCEPTED — A**. 팬은 **운영자
      평면을 열지 않는다**(B 배제) — 저작 주체는 아티스트 자신의 계정이다
- [x] **AC-2 (역할 발급 경로) — 완료 2026-08-11.** 붙이는 곳을 코드로 확정했다:
      **프로비저닝 시점의 `account_roles` 부여**(파생 아님) — account-service
      `migration-dev/V9006` + auth-service `migration-dev/V9002`. iam 코드 변경 **0줄**이며,
      그 이유(역할 평면이 정규식뿐이라 데이터 문제다)는 위 § ①.
      🔴 판정 두 겹: `FanArtistRoleSeedIntegrationTest` 가 **실제 MySQL 에 시드를 실행하고
      토큰 발급 때 auth-service 가 부르는 바로 그 엔드포인트**로 `[FAN, ARTIST]` 를 읽는다
      (스텁 아님 — `TASK-BE-579` 가 옆 이음매에서 지적한 모양을 반복하지 않는다).
      그 위에 `TenantClaimTokenCustomizerTest` 의 기존 단언(저장된 역할 → `roles` 클레임
      verbatim)이 얹힌다. ⚠️ **로그인 → 토큰 → 클레임 전 구간**은 아래 § 검증 상태 참조
- [x] **AC-3 (도달 가능성) — 완료(⚠️ 로컬 미실행, CI 가 권위).** `ARTIST` 역할 호출자의
      `POST /posts {ARTIST_POST}` → 201 은 `ArtistPostReachesFollowerFeedIntegrationTest`
      (`FAN-BE-045`)가 **팔로워 피드 도달까지 함께** 고정하고 있다. 이 티켓이 더한 것은
      그 호출자가 **실재하게 됐다**는 것이고, 시드가 매 실행 그것을 판정한다(토큰의 `sub`
      일치 + `roles` 에 `ARTIST` 적재를 발행 **전에** 확인하고 아니면 사유를 지목해 실패)
- [x] **AC-4 (음성 대조) — 완료.** 두 축을 **따로** 고정했다:
      ① 역할 없는 팬 actor → `PermissionDeniedException` (`PublishPostUseCaseTest`,
      `Set.of("FAN")`) ② 부여가 **표적임** — 평범한 fan-platform 계정은 `ARTIST` 를 받지
      않는다(`FanArtistRoleSeedIntegrationTest#aPlainFanAccountIsNotAnArtist`, 그 계정이
      같은 쿼리로 실제 조회되는 것을 먼저 확인해 "빈 목록" 이 조회 실패가 아님을 보증).
      🔴 ①만 있으면 "게이트가 산다" 는 알아도 "부여가 전원에게 새지 않았다" 는 모른다
- [x] **AC-5 (시드 회수) — 완료, 단 **블록 2 만**.** `--why` 는 한 덩어리가 아니었다(§ ③).
      · **블록 2(`ARTIST_POST` 3종) → API 로 이동.** 저자는 아티스트 본인 계정이므로
        *"저자는 여전히 데모 계정이 아니어야 한다"* 는 요구가 **우회가 아니라 구조로** 충족된다
      · **블록 1(아티스트·그룹·팬덤) → 직접-DB 유지 + `--why` 재작성.** 이 티켓이 그것을
        **열지 않기로 확정**했으므로 사유에서 `MONO-512` 를 지우고 결정과 `TASK-MONO-522` 를
        적었다. 🔴 해소되지 않을 사유를 "곧 풀린다" 로 남기는 것도 면제를 남기는 것이다
- [x] **AC-6 (`FAN_OPERATOR` 수용부 3곳) — 완료.** 세 곳 모두에 **왜 남기는지 + 무엇이
      바뀌면 열리는지**를 적었다: iam `OperatorRoleDerivation` (`case "fan","fan-platform"`) ·
      artist-service `ADMIN_ROLES` · community-service `ActorContext.isOperator()`.
      🔵 community 쪽에는 한 줄 더 적었다 — 그 술어가 참이 되는 순간 `PublishPostUseCase` 의
      **두 번째 문**이 열려 배제된 B 가 옆문으로 부활하고 `owns()` 로 가시성까지 우회된다

---

# Related Specs

- `projects/iam-platform/apps/auth-service/.../OperatorRoleDerivation.java`
- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql`
  (특히 `TASK-BE-576` 이 남긴 "outside writer 가 있으면 행 하나 더" 주석)
- `projects/fan-platform/apps/artist-service/.../config/SecurityConfig.java`
- `projects/fan-platform/apps/community-service/.../application/ActorContext.java`
- `infra/demo/seed/seed-fan.sh` (사유 원문)

# Edge Cases

- `OperatorRoleDerivation` 은 `fan` 과 `fan-platform` **둘 다** 받는다 — 어느 키를
  구독 행에 쓰는지에 따라 다른 곳(콘솔 카탈로그 파생)이 반응할 수 있다
- `ProductCatalog` 에 `fan` 이 없으므로 구독 행을 넣어도 콘솔 제품은 늘지 않는다(확인함).
  이것이 "안전하다" 의 근거이자, 동시에 "콘솔에서는 여전히 보이지 않는다" 의 이유다

# Failure Scenarios

- 🔴 **ADR-059 A 를 무시하고 원래 계획(구독+배정)대로 착수한다** — B 가 배제됐으므로 그
  작업은 **승인된 결정과 반대 방향**이다. 아래 세 항목은 이제 *하지 말아야 할 것*의 목록으로
  읽어야 한다(원래는 *하는 법*의 목록이었다)
- **구독만 넣고 배정을 빠뜨린다** — assume 이 `invalid_grant` 로 막힌다(위 실측 (2))
- **배정만 넣고 구독을 빠뜨린다** — assume 은 되는데 `roles` 가 비어 게이트웨이가 403 한다
- **`demo-corp` 에 `fan` 을 얹고 끝낸다** — 게이트웨이가 `TENANT_FORBIDDEN` 으로 자른다.
  ecommerce 의 "200 + 빈 배열" 과 달리 **엣지에서 잘리므로 증상이 다르다**
- 🔴 **`ARTIST` 역할을 DB 에 넣고 "발급된다" 고 판정한다** — 판정은 그 계정의 **토큰에
  실린 `roles`** 이고, 최종 판정은 `PublishPostUseCase` 게이트 통과(201)다

# Definition of Done

- [x] AC-0 재측정 기록 (2026-08-07 + 착수 시 재재측정 2026-08-11 — 본문 판단 1건 정정)
- [x] 결정 — `ADR-MONO-059` ACCEPTED — A (2026-08-07)
- [x] `ARTIST` 역할 발급 경로 확정 (프로비저닝 `account_roles`, iam 코드 0줄) + 실측
- [x] 실측 증거(양성 + 음성) — 부여 표적성 대조 포함
- [x] `seed-fan.sh` 회수 여부 명시 — 블록 2 회수 · 블록 1 은 사유 재작성 + `TASK-MONO-522`
- [x] `FAN_OPERATOR` 3곳을 왜 남기는지 코드에 기록
- [x] 후속 티켓 — `TASK-MONO-522`(아티스트 디렉터리에 API 호출자가 없다: 결정 필요)
- [ ] Ready for review

---

# 🧪 검증 상태 — 실제로 실행된 것과 아닌 것

| 게이트 | 결과 |
|---|---|
| `auth-service:test` (`*DemoSeed*`) | ✅ 실행·통과 — `FanArtistDemoSeedTest` 5/5, `DemoSeedCredentialTest` 5/5 |
| **가드 bite 검증** | ✅ 사이드 파일 1바이트 변조 → **RED 3/5**, 되돌려 **GREEN 5/5** (강제 rerun 없이) |
| `account-service:compileTestJava` | ✅ |
| `fan-platform:artist-service:test` · `community-service:test` | ✅ 실행·통과 |
| `bash -n infra/demo/seed/seed-fan.sh` | ✅ (CI 의 demo-wrapper-smoke 가 도는 것과 같은 검사) |
| **`account-service:integrationTest`** | ⚠️ **로컬 미실행** — Docker 미기동. `FanArtistRoleSeedIntegrationTest` 는 CI `Integration (iam B, Testcontainers)` 샤드가 권위 |
| **로컬 데모 스택 전 구간** (로그인 → 토큰 → 201) | ⚠️ **미실행** |

## 🔴 무엇이 아직 증명되지 않았는지 — 정확히

전 구간(아티스트 로그인 → iam 이 발급한 **진짜 토큰** → 게이트웨이 → `POST /posts` → 201)은
**어떤 CI 레인에서도 돌지 않는다**. live-trio e2e 는 gateway+community+artist 만 띄우고
**iam 이 없기 때문**이다(`TASK-FAN-INT-005` 가 그 사실 자체를 티켓으로 들고 있다).
지금 증명된 것은 그 사슬의 **각 고리**다:

```
account_roles 행이 실재 + 발급 쿼리가 [FAN, ARTIST] 반환   ← FanArtistRoleSeedIntegrationTest (CI)
저장된 roles → 토큰 roles 클레임 verbatim                  ← TenantClaimTokenCustomizerTest (기존)
ARTIST 든 호출자 → 201 + 팔로워 피드 도달                  ← ArtistPostReachesFollowerFeedIntegrationTest (기존)
세 시드 파일의 id 합의                                     ← FanArtistDemoSeedTest (신규, bite 검증됨)
```

🔴 **고리마다 초록인 것과 사슬이 이어지는 것은 다르다** — 이 저장소가 반복해서 물린 지점이다.
사슬 자체를 재는 자리는 데모 스택(`demo-up` → `seed-fan.sh`)이고, 시드가 **매 실행 그것을
판정하도록** 만들어 두었다(sub 일치 + `roles` 적재를 발행 전에 확인). 다음 `demo-up` 이
그 측정이며, 실패하면 사유를 지목해 멈춘다. `TASK-FAN-INT-005` 가 iam 을 트리오에 넣으면
그때 CI 안으로 들어온다.
