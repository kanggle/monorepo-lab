# Task ID

TASK-MONO-514

# Title

WMS 마스터 데이터를 쓸 수 있는 자격증명이 이 플랫폼에 없다 — `MASTER_WRITE` 를 발급하는 경로가 없고, 워크로드 클라이언트는 scope 만 싣는다

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

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 완료(§①·§②). 운영자 403 을 **서로 다른 키 2회**로 재확인
      (타임스탬프 상이 ⇒ 멱등 재생 아님). 요구처 **1개 서비스 / 42개 술어**, 발급처
      **0건**. 🔴 발급처 후보 `PermissionCatalog` 는 **JWT 로 흐르지 않는 미끼**였다.
      🔵 워크로드 레그는 시크릿이 없어 **라이브 재측정 안 함** — 코드 경로로만 확인했고
      그렇게 적었다.
- [ ] **AC-1 (결정) — 🔴 사용자 결정 대기 (§⑤).** 착수 결과 **세 안이 모두 ADR 급**임이
      드러났다: ①은 `TASK-BE-433` 의 *user-chosen* 결정을 뒤집는 것이고 테스트가 지킨다.
      아래 원문 유지:
      ① 운영자 엔타이틀먼트에 `MASTER_WRITE` 추가(다른 세 서비스와 대칭)
      ② 마스터 전용 운영 역할 신설
      ③ 워크로드 클라이언트 토큰에 role 클레임 부여(scope↔role 간극 자체를 메운다)
      역할 모델 변경이면 **ADR**
- [ ] **AC-2 (도달 가능성)** — AC-1 결정 이후.  — 실제 호출자가 `POST /api/v1/master/warehouses` 로 201 을
      받는다. 토큰 발급 성공만으로는 부족하다(그것이 이 결함의 모양이다)
- [ ] **AC-3 (음성 대조)** — 그 자격증명이 **없는** 호출자는 여전히 403 이어야 한다.
      양성만으로는 "열렸다" 와 "게이트가 사라졌다" 를 구별할 수 없다
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

- [ ] 결정 + (필요시) ADR
- [ ] AC-2/AC-3 실측 증거
- [ ] Ready for review
