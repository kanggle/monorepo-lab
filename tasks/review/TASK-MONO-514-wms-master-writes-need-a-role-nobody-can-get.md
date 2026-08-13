# Task ID

TASK-MONO-514

# Title

WMS 마스터 데이터를 쓸 수 있는 자격증명이 이 플랫폼에 없다 — `MASTER_WRITE` 를 발급하는 경로가 없고, 워크로드 클라이언트는 scope 만 싣는다

# Status

review

# Owner

monorepo

# Task Tags

- iam
- security
- demo

---

# 배경

`TASK-MONO-510`(백오피스 시드) AC-0 이 발굴했다.

master-service 의 쓰기는 전부 이렇게 인가한다:

```java
@PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
```

그런데 그 역할을 **주는 경로가 없다.**

## 실측 (2026-08-05, 로컬 `iam wms console` 슬라이스)

**(1) 운영자 토큰** — 콘솔 로그인 → RFC 8693 assume `demo-corp`:

```
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE, INBOUND_READ, INBOUND_WRITE,
         INVENTORY_READ, INVENTORY_WRITE, MASTER_READ]

POST /api/v1/master/warehouses  403 {"code":"FORBIDDEN",
                                     "message":"Insufficient privileges for this operation"}
```

🔴 **비대칭에 주목하라**: `OperatorRoleDerivation.WMS_OPERATOR_ROLES` 는 outbound ·
inbound · inventory 에 **READ 와 WRITE 를 둘 다** 주는데 master 만 **READ 뿐**이다.
그 클래스의 주석은 `*_ADMIN` 을 제외한 이유는 적어 두었지만(취소 · 강제 사가 실패 ·
마스터 데이터 쓰기), ~~`MASTER_WRITE` 자체가 왜 빠졌는지는 적혀 있지 않다~~. 주석은
"master-data writes" 를 ADMIN 티어로 분류하는데 **코드의 술어는 `MASTER_WRITE` 이지
`MASTER_ADMIN` 이 아니다** — 분류와 술어가 어긋나 있다.

> 🔴 **취소선 부분은 틀렸다** — 착수해 보니 이유는 `TASK-BE-433` 본문에 적혀 있고
> 테스트가 강제하고 있었다(아래 § 정정). 뒤 문장(분류↔술어 불일치)은 참이고 그것이
> AC-4 다.

**(2) 워크로드 클라이언트** — `wms-internal-services-client`, `client_credentials`:

```
scope=internal.invoke              → invalid_scope (등록돼 있지 않다)
scope=wms.master.write             → 발급됨. tenant_id=wms, roles 클레임 **없음**
POST /api/v1/master/warehouses     403 (동일)
```

🔴 **scope 는 아무것도 열지 못한다** — master-service 는 **role** 로 인가하기 때문이다.
워크로드 토큰에 role 클레임이 실리지 않으므로, `wms.master.write` scope 를 들고도
`hasRole('MASTER_WRITE')` 는 거짓이다. **이름이 맞는 scope 가 존재한다는 사실이
그 scope 가 무언가를 연다는 증거가 아니다.**

## 🔴 재측정 시 반드시 키를 바꿔라

wms 의 변이 엔드포인트는 `Idempotency-Key`(UUID)를 요구하고, **같은 키로 다시 부르면
실패 응답까지 그대로 재생한다** — 두 번째 403 의 타임스탬프가 첫 번째와 **바이트 단위로
동일**했다. 그 상태로 "워크로드 클라이언트도 막혔다" 를 결론 낼 뻔했고, 키를 바꿔 다시
받고서야 실측이 됐다(결론은 같았지만 근거는 그때 처음 생겼다).

---

# 🟢 착수 (2026-08-06) — AC-0 · AC-4 완료, AC-1 은 사용자 결정 대기

## 🔴🔴 정정 — 이 티켓의 전제 한 줄이 틀렸다

배경이 *"`MASTER_WRITE` 자체가 왜 빠졌는지는 적혀 있지 않다"* 라고 적었다.
**적혀 있다.** 그것도 결정을 내린 티켓 본문에, 그리고 **테스트로 강제**되고 있다:

```
projects/iam-platform/tasks/done/TASK-BE-433-wms-operator-granular-service-roles.md
  "Decision (user-chosen, Option A)"
  Scope        : ADMIN-tier (…, master-data writes) is **deliberately excluded**
                 (a higher grant, out of scope)
  AC-2         : no *_ADMIN / WMS_ADMIN / **MASTER_WRITE** in the wms set
  Out of scope : ADMIN-tier operator grants (a separate higher entitlement)

OperatorRoleDerivationTest#wms_excludesAdminTier
  .doesNotContain("WMS_ADMIN", …, "MASTER_ADMIN", **"MASTER_WRITE"**)
```

⇒ **AC-1 의 선택지 ①(운영자에게 `MASTER_WRITE` 추가)은 "대칭 복원" 이 아니라
사용자가 선택했고 테스트가 지키는 결정을 뒤집는 것이다.** 이 티켓이 그것을
"비대칭 = 실수" 로 읽은 이유는 **주석만 보고 그 규칙을 만든 티켓과 테스트를 열지
않았기** 때문이다. [[feedback_my_own_ticket_cited_a_spec_that_says_otherwise]]

🔵 **다만 이 티켓이 잡아낸 불일치는 진짜다.** BE-433 은 "master-data writes" 를
**ADMIN 티어**로 분류했는데, wms 계약(`master-service-api.md § roles`)은
create/update = `MASTER_WRITE`, deactivate/reactivate = `MASTER_ADMIN` 이다.
제외는 의도였지만 **그 이유를 틀린 이름으로 적어** 두어 읽는 사람에게는 누락처럼
보였다. 그것이 AC-4 이고, 이번에 고쳤다.

## ① AC-0 — 재측정 (새 Idempotency-Key)

```
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE, INBOUND_READ, INBOUND_WRITE,
         INVENTORY_READ, INVENTORY_WRITE, MASTER_READ]        ← MASTER_WRITE 없음

GET  /api/v1/master/warehouses   200  totalElements=1        ← READ 는 통과
POST /api/v1/master/warehouses   403  FORBIDDEN  (key A, ts …52.553Z)
POST /api/v1/master/warehouses   403  FORBIDDEN  (key B, ts …52.904Z)
```

🔵 **타임스탬프가 다르다** ⇒ 멱등 재생이 아니라 두 번 다 실제로 평가됐다(배경이
경고한 함정을 통과했다).

🔴 **400 은 판정이 아니다** — 처음엔 잘못된 필드명으로 보내 `400 VALIDATION_ERROR`
가 났다. 빈 검증은 `@PreAuthorize` **이전**에 돌므로 400 은 인가에 대해 아무것도
말하지 않는다. 계약의 실제 스키마(`warehouseCode`/`name`/`address`/`timezone`)로
고쳐 보내고서야 403 이 나왔다.

**워크로드 클라이언트**: 이번 회차엔 **라이브 재측정하지 않았다** — 데모 설정에
`wms-internal-services-client` 의 평문 시크릿이 없다(마이그레이션엔 해시만). 대신
**코드 경로로 확인**했다: `master-service` 의 `SecurityConfig` 는 권한을 `roles`
클레임에서만 만든다(`setAuthoritiesClaimName("roles")` + `ROLE_` 접두). scope 는
어떤 authority 도 만들지 않는다. 2026-08-05 실측은 그대로 유효하다.

## ② AC-0 — 요구처 / 발급처 전수

**요구처** — 저장소 전체에서 `hasRole('MASTER_WRITE')` 를 쓰는 서비스는
`wms-platform/apps/master-service` **하나**뿐. 그 안의 `@PreAuthorize` 분포:

| 술어 | 개수 |
|---|---|
| `MASTER_READ or MASTER_WRITE or MASTER_ADMIN` | 18 |
| `MASTER_WRITE or MASTER_ADMIN` | 12 |
| `MASTER_ADMIN` | 12 |

**발급처 — 0건.** `OperatorRoleDerivation` 도 `DelegatableRoleCatalog` 도
`MASTER_READ` 까지만 싣고, iam 마이그레이션에 `MASTER_WRITE` 를 주는 시드는 없다.

🔴 **후보가 하나 나왔는데 미끼였다**:
`wms-platform/apps/admin-service/.../PermissionCatalog.java` 가 `"MASTER_WRITE"` 를
**알고 있다**(자체 RBAC 의 permission 문자열 카탈로그). 그러나 그 모델은 **JWT 로
흐르지 않는다** — wms admin 은 할당을 `wms.admin.assignment.v1` 로 발행할 뿐이고
iam 은 그것을 구독하지 않으며, master-service 는 `roles` 클레임만 본다. **이름이
존재한다는 사실이 그것이 무언가를 연다는 증거가 아니다** — 이 티켓이 scope 로
배운 교훈이 role 이름에도 그대로 적용됐다.
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

## ③ `INVENTORY_RESERVE` 는 함께 다루면 안 된다 (Edge Case 답)

계약이 명시한다 — `inventory-service-api.md`:

> `INVENTORY_RESERVE` is a **machine-to-machine** scope. **Human users do not hold it.**

⇒ 운영자 엔타이틀먼트에 넣는 것은 **계약 위반**이다. 예약이 막히는 문제의 답은
운영자 역할 확대가 아니라 **워크로드 자격증명**(선택지 ③ 계열)이며 별도 결정이다.
이 티켓에서 묶지 않는다.

## ④ AC-4 — 주석 정합 (완료, 동작 변경 없음)

`OperatorRoleDerivation` 의 두 주석을 고쳐 (a) `MASTER_WRITE` 제외가 의도이며
BE-433 AC-2 + 테스트가 지킨다는 것, (b) 그것이 "ADMIN 티어라서" 가 **아니라는**
것(계약은 create/update 를 `MASTER_WRITE` 로 둔다), (c) 뒤집으려면 역할 모델
결정이라는 것을 적었다.

## ⑤ 🔴 AC-1 은 여기서 멈춘다 — 세 안 모두 사용자 결정이 필요하다

| 안 | 성격 | 왜 자체 승인 불가 |
|---|---|---|
| ① 운영자에 `MASTER_WRITE` 추가 | **BE-433 의 user-chosen 결정 뒤집기** | 테스트가 지키는 결정을 에이전트가 되돌릴 수 없다. 운영자 티어 상향 = 역할 모델 변경 ⇒ ADR |
| ② 마스터 전용 운영 역할 신설 | **새 역할** | 역할 모델 변경 ⇒ ADR |
| ③ 워크로드 토큰에 role 클레임 | **토큰 발급 모델 변경** | scope↔role 간극을 메우는 구조 변경 ⇒ ADR. 다만 `INVENTORY_RESERVE` 까지 한 번에 푸는 유일한 안 |

🔵 **데모 관점의 사실**: 지금도 데모는 성립한다 — 마스터 데이터는 Flyway dev 시드로
들어가고, 콘솔 마스터 화면은 **읽기가 된다**(`GET` 200). 막히는 것은 **API 로 새
마스터를 만드는 것**뿐이므로 이 결정이 데모를 막고 있지는 않다.

---

# 🟢 착수 2회차 (2026-08-13 UTC) — AC-1 의 세 안에 **숫자를 붙였다** (결정은 여전히 오너)

`TASK-MONO-521` 이 워크로드 신원 판정기 모집단을 재고 나서야 ③의 비용을 셀 수 있게 됐다.
아래는 **결정 준비**이지 결정이 아니다 — 셋 다 ADR 급이라는 §⑤의 결론은 그대로다.

## 변경 지점 vs 도달 범위 — ③은 그 둘이 크게 다르다

| 안 | 코드 변경 지점 | **효과가 닿는 범위** | `INVENTORY_RESERVE` |
|---|---|---|---|
| ① 운영자에 `MASTER_WRITE` | `OperatorRoleDerivation` 상수 1곳 + `OperatorRoleDerivationTest#wms_excludesAdminTier` **assertion 1개** | wms 운영자 토큰만 | ❌ 계약이 금지(§③) |
| ② 마스터 전용 역할 신설 | 새 역할의 **발급 평면**(파생 or 부여) + master-service **42개 술어** 중 어디까지 인정할지 | wms 만 | ❌ |
| ③ 워크로드 토큰에 role 클레임 | `TenantClaimTokenCustomizer#customizeForClientCredentials` **1곳** | 🔴🔴 **cc 클라이언트 12개 × 그 클레임으로 authority 를 만드는 서비스 19개 / 6개 프로젝트** | ✅ **유일** |

**측정 근거**:

- **cc 클라이언트 12개** — 마이그레이션의 INSERT 를 **문(statement) 단위**로 파싱해 셌다
  (줄 단위로 세면 여러 줄에 걸친 seed 를 놓친다). 🔵 대조군: 비-cc 4개가 정확히
  브라우저 클라이언트(`demo-spa` · `ecommerce-admin-dashboard` · `ecommerce-web-store` ·
  `fan-platform-user-flow`)로 떨어졌다 — 탐지식이 전부를 매치하는 상태가 아님이 확인된다.
  🔴 곁다리: `wms-user-flow-client` 가 cc 그랜트를 갖고 있다(이름과 다르다).
- **19개 서비스** — `roles` 클레임을 authority 로 바꾸는 지점의 전수:
  `libs/java-security-servlet` 의 `ActorClaims` 를 통해 erp 4 · fan 4 · finance 2 · scm 4 ·
  wms 5. wms 5개는 그 위에 자기 `setAuthoritiesClaimName("roles")` 도 갖고 있다.

⇒ **③은 "한 줄 고치기" 로 보이지만 wms 변경이 아니다.** 클레임이 생기는 순간
**6개 프로젝트 19개 서비스가 동시에** 머신 토큰에 authority 를 부여하기 시작한다.
어떤 cc 클라이언트에 어떤 role 을 줄지는 그 다음 문제이고, **비어 있는 role 집합으로
시작하더라도 그 순간부터 "무엇을 넣으면 어디가 열리는가" 가 전 저장소 질문이 된다.**

## 🔴 ③은 `ADR-MONO-059`(그리고 `TASK-MONO-522`)와 맞물린다

fan artist-service 의 쓰기 매처는 `hasAnyRole(ADMIN, OPERATOR, SUPER_ADMIN, FAN_OPERATOR)`
이고, 그 체인은 `ActorContextJwtAuthenticationConverter`(= `roles` 를 읽는 위 19개 중 하나)를
쓴다. **지금 머신 토큰이 role 을 안 실어서 그 게이트는 머신에게 닫혀 있다.** ③은 그 전제를
없앤다 ⇒ `TASK-MONO-522` 의 Edge Case *"관리 API 를 워크로드 신원으로 연다"* 가
**실현 가능해지고**, 동시에 `ADR-MONO-059` 가 닫아 둔 평면에 새 문이 생기는지를
그 ADR 이 명시적으로 답해야 한다.

⇒ **③을 고르면 514 와 522 는 한 ADR 에서 같이 답해야 한다.** ①/② 를 고르면 둘은 독립이다.
이것이 두 티켓의 순서를 묶는 실제 이유다(추측이 아니라 위 배선에서 나온다).

## 결정에 필요한 것

세 안 중 하나를 **정확형으로** 골라 주면 그 안의 ADR 초안 + 배선 + AC-2/AC-3 실측까지
진행한다. 🔴 이 티켓은 스스로 고르지 않는다 — ①은 사용자가 고른 BE-433 결정을 뒤집는
것이고, ②③은 역할/토큰 모델 변경이다.

---

# 🟢 구현 (2026-08-13 UTC) — ADR-MONO-061 C 이행. AC-1 · AC-2 · AC-3 완료

## ⓪ 🔴🔴 모집단이 또 틀렸다 — **cc 클라이언트는 12개가 아니라 10개**

2회차가 적은 *"cc 12 / 비-cc 4"* 를 물려받지 않고 다시 셌다(마이그레이션을 문(statement)
단위로 파싱하고 DELETE 까지 재생). 결과:

| | 2회차 기록 | 재계수 (2026-08-13) |
|---|---|---|
| 전체 등록 클라이언트 | — | **16** |
| `client_credentials` | 12 | **10** |
| 비-cc(브라우저) | 4 | **6** |

교차검증: `INSERT INTO oauth_clients` **17건** = cc 리터럴 11 + authz 리터럴 6이고, 그중
`membership-service-client` 를 V0029 가 지운다 ⇒ 16 = 10 + 6. 두 방법이 일치한다.

🔴 2회차의 *"곁다리: `wms-user-flow-client` 가 cc 그랜트를 갖고 있다(이름과 다르다)"* 는
**틀렸다** — V0010 은 그것을 `["authorization_code","refresh_token"]` 로 심는다. 대조군이라며
적어 둔 4개가 실은 6개였다. **"탐지식이 전부를 매치하지는 않는다" 는 그때의 안심 근거가, 정작
다른 수를 세면서 성립하고 있었다.** [[feedback_recount_population_dont_inherit_scope]]

## ① 계약 먼저 (ADR 이 구속력을 준 순서)

`platform/contracts/jwt-standard-claims.md`:

- § Gateway Enforcement Rules 의 *"machine tokens authorize on the `scope` axis, **not** `roles`"*
  를 개정했다. 🔴 그 문장은 **엣지에 대해 참이고 그 뒤 전부에 대해 거짓**이었다 — 입장(admission)
  은 바깥 문일 뿐이고, 도메인 서비스 안의 모든 `@PreAuthorize` 는 `roles` 로 권한을 만든다.
- `roles` 행 `Required: Yes` → **`Conditional`**. 워크로드 토큰에 대해 `Yes` 는 이 계약이 쓰인
  날부터 참인 적이 없었다(`email` 이 BE-577 에서 겪은 것과 같은 방향의 오류).
- 제약 **4개**를 계약에 명시했다: 클라이언트별 명시 열거 · **요청이 받은 scope 로 게이팅** ·
  없는 것이 기본값 · `/internal/**` 의 scope 축 불변.
- Change log 갱신. `check-jwt-claims-registry.sh` **rc=0**(새 클레임이 아니라 기존 `roles` 행이다).

## ② 배선 — `WorkloadRoleCatalog`

`customizeForClientCredentials` → `populateWorkloadRoles`. 표는 **정적**이다. 🔴 `populateRoles`
를 재사용할 수 없는 이유가 바로 그것이다 — cc 발급이 곧 account-service 를 부를 Bearer 를 만드는
행위라서, 이 경로에서의 조회는 커스터마이저를 무한 재진입시킨다. **정적 표는 I/O 가 없고, 그래서
이 그랜트에서 roles 다리가 애초에 가능해진다.** 그 이유를 코드에 적어 뒀다.

🔴 **처음 구현은 클라이언트 단위였고, 그건 최소권한 구멍이었다.** `wms.master.read` 만 요청해도
`MASTER_WRITE` 가 실렸다 — **등록이 토큰을 결정**하고 요청은 무의미해진다. scope 단위로 바꿨다
(`email` 이 `email` scope 로 게이팅되는 것과 같은 모양). 그 결과가 아래 실측의 **가장 날카로운
대조군**이 됐다: *같은 클라이언트·같은 시크릿, 다른 것은 요청 scope 뿐*.

**부여 = 10개 중 1개뿐**:

| 클라이언트 | scope | role |
|---|---|---|
| `wms-internal-services-client` | `wms.master.write` | `MASTER_WRITE` |
| `wms-internal-services-client` | `wms.master.read` | `MASTER_READ` |
| 나머지 **9개** | — | **없음**(명시적 빈 집합) |

🔵 `MASTER_READ` 는 **첫 실측이 찾아낸 반쪽 배선** 때문에 함께 넣었다 — write 만 주니 워크로드가
`POST` 로 창고를 만들고 **방금 만든 그 행을 읽는 데 403** 을 받았다(§③ 1차 매트릭스 3행). 한
방향만 뚫린 이음매는 이 저장소가 반복해 대가를 치른 모양이고, 읽기는 이미 준 쓰기보다 엄격히
작다. [[project_externalised_seam_with_no_counterpart]]

🔴 `MASTER_ADMIN` 은 **주지 않는다** — 티켓 Goal 은 "API 로 마스터를 만든다" 이고 deactivate 는
다른, 더 높은 결정이다.

## ③ AC-2 / AC-3 — 라이브 2×2 매트릭스 (iam + wms 실기동, auth-service 재빌드·재배포)

토큰 클레임 실측:

```
scope=["wms.master.write"]  roles=["MASTER_WRITE"]
scope=["wms.master.read"]   roles=["MASTER_READ"]      ← 같은 클라이언트·같은 시크릿
```

| # | 토큰 | 호출 | 기대 | **실측** |
|---|---|---|---|---|
| 1 | write-scope | `POST /api/v1/master/warehouses` | 201 | **201** ✅ **AC-2** |
| 2 | read-scope | `POST /api/v1/master/warehouses` | 403 | **403** ✅ **AC-3** |
| 3 | read-scope | `GET /api/v1/master/warehouses` | 200 | **200** |
| 4 | write-scope | `POST /{id}/deactivate` (`MASTER_ADMIN`) | 403 | **403** ✅ **AC-3** |

**왜 이 네 칸인가** — 양성 하나로는 *"열렸다"* 와 *"게이트가 사라졌다"* 를 구별할 수 없다.
2행은 **클라이언트·시크릿·테넌트·엔드포인트가 전부 같고 요청 scope 만 다르다** ⇒ 열린 것은
role 이지 호출자가 아니다. 4행은 **토큰조차 같고 role 술어만 다르다** ⇒ 게이트는 살아 있다.
3행은 2행의 403 이 "토큰이 죽었다" 가 아니라 **인가**임을 보인다(같은 토큰이 200 을 받는다).

응답의 `createdBy = wms-internal-services-client` — 워크로드가 행위자로 기록된다.

🔴 **400 은 판정이 아니다**(배경의 경고가 두 번 발화했다): `warehouseCode` 정규식(`^WH\d{2,3}$`)과
`version` 필수를 몰라 각각 `400 VALIDATION_ERROR` 가 났다. 빈 검증은 `@PreAuthorize` **이전**에
돈다 — 그 400 들은 인가에 대해 아무것도 말하지 않으므로 판정으로 쓰지 않았다.
🔴 재측정은 매번 **새 `Idempotency-Key`**(멱등 재생 함정).

## ④ 19개 서비스 회귀 (ADR § 결과 4)

- `OperatorRoleDerivationTest` **8/8 통과**, `wms_excludesAdminTier` 가
  `.doesNotContain(…, "MASTER_WRITE")` 를 **그대로** 강제한다 ⇒ `TASK-BE-433` 의 user-chosen
  결정은 뒤집히지 않았다(ADR 구속력 1).
- 도달 범위를 다시 쟀다 — `MASTER_WRITE`/`MASTER_READ` 를 술어로 쓰는 곳은 여전히 **wms
  master-service 하나**뿐이고, 부여된 토큰은 `tenant_id=wms` 라 다른 5개 프로젝트 엣지가
  테넌트에서 거부한다.
- **fan artist-service `ADMIN_ROLES` 는 열리지 않았다** — `WorkloadRoleCatalogTest` 가 *어떤*
  워크로드 클라이언트도 `ADMIN`/`OPERATOR`/`SUPER_ADMIN`/`FAN_OPERATOR` 를 **부여받을 수 없음**을
  단언한다(그 클라이언트가 제시 가능한 **모든 scope** 에 대해). 가드는 **발급자 쪽**에 둔다 —
  결정이 거기 있고, 매처는 바뀌지 않은 코드이기 때문이다.
- 테스트: auth-service 전체 · fan artist-service · ecommerce order-service · wms master-service
  **전부 green**.

## ⑤ 가드 bite — 세 번, 전부 물었고 전부 지목이 정확했다

| 주입 | 결과 |
|---|---|
| 카탈로그에서 seed 된 cc 클라이언트 1개 제거 | 완전성 테스트 **RED** (그 하나만) |
| `community-service-client` 에 `FAN_OPERATOR` 부여 | admin-tier 테스트 + 폭발반경 테스트 **RED** |
| **새 cc 클라이언트를 마이그레이션에 추가**(카탈로그엔 없음) | 완전성 + 비공허성 테스트 **RED** |

세 번째가 이 가드의 존재 이유다 — *내일 등록될 클라이언트*는 안전한 기본값을 받고 **아무도
결정하지 않은 클라이언트가 된다**. 복원 후 tree clean·rc=0 재확인.

🔵 비공허성은 **구조적**이다: 단언이 비어 있지 않은 카탈로그와의 **집합 동등**이라, 파서가 0건을
읽으면 통과가 아니라 실패한다(탐지식의 0건은 부재의 증거가 아니다).

## ⑥ ADR-061 이 거짓으로 만든 주석 **4곳**을 고쳤다

가드가 아니라 **산문**이 이 저장소에서 반복해 대가를 치르는 자리다. 넷 다 원문이 왜 그렇게
쓰였는지와 무엇이 바뀌었는지를 함께 남겼다:

1. `TenantClaimTokenCustomizer` — *"Omitted for client_credentials (a workload is not an identity —
   recursion guard)"*. 🔴 두 이유를 뭉쳐 놓아 **두 번째가 하중을 받고 있었다**. 재귀 가드는
   *account-service 조회*를 묶는 것이지 클레임을 묶지 않는다.
2. fan `artist-service SecurityConfig` — *"Two independent reasons, both measured"* 는 머신 토큰이
   **구조적으로** role 을 못 싣던 동안에만 전수였다. 이제 **세 번째 경로**가 존재하고, 그것이
   닫혀 있는 이유는 구조가 아니라 **결정**임을 적었다.
3. ecommerce `SystemClientSubjectValidator` — 판별자로 *"carries no roles claim"* 을 함께 적어
   두고 있었다. `sub` 만이 술어였고 그게 더 강하므로 그 문장을 지웠다.
4. scm `SupplierApplicationService` — *"`MASTER_WRITE` is granted to nobody — TASK-MONO-514"* 를
   인용해 자기 논거로 삼고 있었다. 인용된 사실이 오늘 바뀌었다.

## ⑦ 이 티켓이 답하지 **않은** 것

- **`INVENTORY_RESERVE`** — §③ 이 *"이 티켓에서 묶지 않는다"* 로 명시 제외했고 그대로 둔다.
  ADR-061 C 가 그것을 **가능하게** 만들었을 뿐 부여는 별도 결정이다.
- **fan artist-service 의 admin 매처 개방** — `TASK-MONO-522` 소유. 빈 기본값이 그때까지 닫아 둔다.

---

# Goal

WMS 마스터 데이터를 **API 로** 만들 수 있는 자격증명이 존재한다 — 또는 존재하지 않는 것이
의도라면 그 사실이 코드에 적혀 있고, 데모/운영이 그 전제 위에서 동작한다.

---

# Scope

## In Scope

- `MASTER_WRITE` 발급 경로 결정(운영자 엔타이틀먼트 확장 / 전용 역할 / 워크로드 role 클레임)
- 위 비대칭에 대한 근거를 코드에 남기기
- `OperatorRoleDerivation` 주석의 "master-data writes = ADMIN 티어" 진술과 실제 술어의 정합

## Out of Scope

- 데모 마스터 데이터 자체 — `infra/demo/wms-devseed.override.yml` 이 저장소의 기존
  Flyway dev 시드를 켜서 해결한다(`TASK-MONO-510`). 이 티켓은 **API 도달 가능성**이다

---

# 선택지 → **[`ADR-MONO-061`](../../docs/adr/ADR-MONO-061-workload-token-authorization-plane.md) 로 승격됨 (PROPOSED, 2026-08-13)**

§⑤ 의 세 안이 ADR 의 A/B/C 가 되고, 티켓 Goal 이 이미 허용하던 *"존재하지 않는 것이 의도"* 가
**D(v1 범위 밖 확정)** 로 명시 선택지가 됐다. ADR 은 여기에 실측 비용을 붙여 둔다
(cc 클라이언트 12 · `roles`→authority 서비스 19 / 6 프로젝트 · `MASTER_WRITE` 술어 24).

🟢 **`ADR-MONO-061 ACCEPTED — C` (2026-08-13, 소유자 정확형).** AC-1 은 이것으로 답해졌다:
**워크로드 토큰에 `roles` 클레임**. A·B·D 는 배제 — `OperatorRoleDerivation` 의 wms 집합에
`MASTER_WRITE` 는 계속 넣지 않고 `wms_excludesAdminTier` 테스트도 **유지**한다.
🔴 게이트가 실제로 한 번 물었다(직전 라운드의 *"③(추천)"* 은 글자의 출처가 에이전트 추천
라벨이라 넘기지 않았다) — 그 사실이 ADR § 결정 에 기록돼 있다.

🔴 **구현 순서가 구속력을 갖는다**: `jwt-standard-claims.md` 의 machine 인가 축 개정이
**먼저**다(계약이 스펙이다). cc 클라이언트 12개의 role 집합은 **기본값 빈 집합**(fail-closed).

⚠️ **이 ACCEPT 가 결정하지 않은 것**: fan artist-service 의 `ADMIN_ROLES` 매처를 워크로드
신원에 여는가 — rider 대조 결과 *"답하지 않고도 C 를 고를 수 있다"* 이므로 **미결**이고
`TASK-MONO-522` 가 명시적으로 답해 기록해야 한다. 빈 집합 기본값이 그때까지 자동으로 닫아 둔다.

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 완료(§①·§②). 운영자 403 을 **서로 다른 키 2회**로 재확인
      (타임스탬프 상이 ⇒ 멱등 재생 아님). 요구처 **1개 서비스 / 42개 술어**, 발급처
      **0건**. 🔴 발급처 후보 `PermissionCatalog` 는 **JWT 로 흐르지 않는 미끼**였다.
      🔵 워크로드 레그는 시크릿이 없어 **라이브 재측정 안 함** — 코드 경로로만 확인했고
      그렇게 적었다.
- [x] **AC-1 (결정) — 완료.** `ADR-MONO-061 ACCEPTED — C`(소유자 정확형, 2026-08-13) ⇒
      **워크로드 토큰에 `roles`**. A·B·D 배제. 구현은 § 구현 ①②. 아래 원문 유지:
- [ ] ~~**AC-1 (결정) — 🔴 사용자 결정 대기.**~~ 세 안 모두 ADR 급(§⑤). **2회차에서 비용을
      실측해 붙였다**: ① = `OperatorRoleDerivation` 상수 1 + 테스트 assertion 1(그러나
      BE-433 *user-chosen* 뒤집기) / ② = 새 발급 평면 + master-service **42개 술어** 범위 결정 /
      ③ = 변경 지점 **1곳**이지만 효과가 **cc 클라이언트 12 × 서비스 19 / 프로젝트 6** 에
      동시 도달, `INVENTORY_RESERVE` 를 푸는 **유일한** 안, 그리고 🔴 `ADR-MONO-059` ·
      `TASK-MONO-522` 와 **한 ADR 에서 같이 답해야** 한다. 아래 원문 유지:
      ① 운영자 엔타이틀먼트에 `MASTER_WRITE` 추가(다른 세 서비스와 대칭)
      ② 마스터 전용 운영 역할 신설
      ③ 워크로드 클라이언트 토큰에 role 클레임 부여(scope↔role 간극 자체를 메운다)
      역할 모델 변경이면 **ADR**
- [x] **AC-2 (도달 가능성)** — 완료(§③ 1행). 라이브 `POST /api/v1/master/warehouses` → **201**,
      `createdBy=wms-internal-services-client`. 토큰 발급만이 아니라 **호출이 통과**했다.
- [x] **AC-3 (음성 대조)** — 완료(§③ 2·4행). 대조군을 **두 축**으로 세웠다: ② 같은 클라이언트가
      **좁은 scope** 로 받은 토큰 → 403(호출자가 아니라 role 이 연다) · ④ **같은 토큰**이
      `MASTER_ADMIN` 술어에 → 403(게이트는 살아 있다). 3행이 그 403 들이 죽은 토큰이 아니라
      **인가**임을 보인다(같은 토큰 GET 200).
- [x] **AC-4 (주석 정합)** — 완료(§④). `OperatorRoleDerivation` 의 두 주석이 이제
      계약의 티어링(create/update=`MASTER_WRITE`)과 일치하고, 제외가 의도이며 어디서
      강제되는지를 가리킨다. **동작 변경 없음.**

---

# Related Specs

- `projects/iam-platform/apps/auth-service/.../OperatorRoleDerivation.java`
- `projects/wms-platform/apps/master-service/.../application/service/*Service.java` (`@PreAuthorize`)
- `infra/demo/wms-devseed.override.yml` (사유 원문 + 실측)

# Edge Cases

- wms 는 **데이터에 테넌트가 거의 없다** — `tenant_id` 컬럼을 가진 테이블은 5개 DB 통틀어
  `outbound_db.outbound_order` 하나뿐이다(실측). 역할을 넓히면 테넌트 격리로는 좁혀지지
  않는다는 뜻이므로, 권한 범위를 정할 때 이 사실을 전제로 삼아야 한다
- `INVENTORY_RESERVE` 도 같은 부류다 — 운영자 엔타이틀먼트가 주지 않아 출고 사가의
  예약 단계가 막힌다(실측). 같은 결정으로 함께 다룰지 정할 것

# Failure Scenarios

- **scope 를 추가하고 끝낸다** — 이름은 맞는데 여전히 403 이다. 인가는 role 로 한다
- **AC-2 없이 토큰 발급만 확인한다** — 이 결함이 정확히 그 모양이다

# Definition of Done

- [x] 결정 + ADR — [`ADR-MONO-061`](../../docs/adr/ADR-MONO-061-workload-token-authorization-plane.md) ACCEPTED — C
- [x] AC-2/AC-3 실측 증거 — § 구현 ③ 라이브 2×2 매트릭스
- [x] Ready for review
