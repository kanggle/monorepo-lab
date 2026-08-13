# ADR-MONO-061 — 워크로드(machine) 토큰의 인가 평면: scope 만 싣는 토큰이 role 로 지키는 표면에 도달할 수 있는가

**Status:** ACCEPTED
**Date:** 2026-08-13
**History:** PROPOSED 2026-08-13. **ACCEPT is a human gate — this record authorises no code by itself.** 승인은 소유자의 **정확형** 지시(`ADR-MONO-061 ACCEPTED — <A|B|C|D>`)를 요구하며, 일반적인 "진행"/"proceed"/"추천대로" 는 이것을 승인하지 않는다. 작성 에이전트는 자기 제안을 스스로 ACCEPT 할 수 없다. · **ACCEPTED 2026-08-13 — C** (소유자가 `ADR-MONO-061 ACCEPTED — C` 를 직접 타이핑). 🔴 **게이트가 실제로 한 번 물었다**: 직전 라운드에서 소유자가 `AskUserQuestion` 목록의 *"③(추천)"* 을 골랐고, 그 글자의 출처가 **작성 에이전트의 추천 라벨**이며 그 시점에 이 문서가 존재하지 않았으므로 넘기지 않고 PROPOSED 로 기록한 뒤 정확형을 요청했다. **self-ACCEPT 아님** · **§ 선택지 / § 추천 / § 결과 는 byte-unchanged**(finalise 이지 re-decide 아님). ⚠️ **fan artist-service 의 admin 매처를 여는 것은 이 ACCEPT 가 결정하지 않았다** — 아래 § 결정 참조.
**Decision driver:** `TASK-MONO-514` — `master-service` 의 쓰기 42개 술어 중 24개가 `MASTER_WRITE`/`MASTER_ADMIN` 을 요구하는데 **그 역할을 발급하는 경로가 저장소에 0건**이다. 워크로드 클라이언트는 `wms.master.write` **scope** 를 발급받지만, 인가는 **role** 로 하므로 이름이 맞는 scope 를 들고도 403 이다.
**Related:** `TASK-MONO-514`(발굴·실측) · `TASK-MONO-521`(폭발반경을 셀 수 있게 한 모집단 측정) · `TASK-MONO-522`(선택지 C 를 고르면 **같이 답해야 하는** 티켓) · `ADR-MONO-059`(fan 저작 신원 평면 — C 가 그 binding 을 건드린다) · `platform/contracts/jwt-standard-claims.md` · `platform/security-rules.md` · `TASK-BE-433`(선택지 A 가 뒤집는 user-chosen 결정)

---

## 실측 (2026-08-05 최초, 2026-08-06 재측정, 2026-08-13 모집단 확정)

**운영자 토큰** — 콘솔 로그인 → RFC 8693 assume `demo-corp`:

```
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE, INBOUND_READ, INBOUND_WRITE,
         INVENTORY_READ, INVENTORY_WRITE, MASTER_READ]      ← MASTER_WRITE 없음

GET  /api/v1/master/warehouses   200  totalElements=1
POST /api/v1/master/warehouses   403  FORBIDDEN  (서로 다른 Idempotency-Key 2회,
                                                  타임스탬프 상이 ⇒ 멱등 재생 아님)
```

**워크로드 클라이언트** (`wms-internal-services-client`, `client_credentials`):

```
scope=internal.invoke     → invalid_scope (등록돼 있지 않다)
scope=wms.master.write    → 발급됨. tenant_id=wms, roles 클레임 **없음**
POST /api/v1/master/warehouses  → 403 (동일)
```

⇒ **이름이 맞는 scope 가 존재한다는 사실은 그 scope 가 무언가를 연다는 증거가 아니다.**
`master-service` 의 `SecurityConfig` 는 권한을 `roles` 클레임에서만 만든다
(`setAuthoritiesClaimName("roles")` + `ROLE_` 접두). scope 는 어떤 authority 도 만들지 않는다.

**발급처 전수 = 0건.** `OperatorRoleDerivation` 도 `DelegatableRoleCatalog` 도 `MASTER_READ`
까지만 싣고, iam 마이그레이션에 `MASTER_WRITE` 를 주는 시드가 없다.
🔴 후보 하나가 미끼였다 — `wms/admin-service/PermissionCatalog` 는 `"MASTER_WRITE"` 를
**알지만 JWT 로 흐르지 않는다**(wms admin 은 할당을 `wms.admin.assignment.v1` 로 발행할 뿐이고
iam 은 구독하지 않는다).

---

## 이것은 wms 하나의 갭이 아니다 — **같은 뿌리에 두 번째 표면이 있다**

`inventory-service-api.md` 가 명시한다:

> `INVENTORY_RESERVE` is a **machine-to-machine** scope. **Human users do not hold it.**

출고 사가의 예약 단계가 막히는 이유가 같다 — 계약이 *"기계만 든다"* 고 선언한 권한인데,
**기계 토큰이 권한을 실을 자리가 없다.** 운영자 엔타이틀먼트에 넣는 것은 계약 위반이므로,
이 두 표면의 공통 답은 *"머신 신원이 인가를 어떻게 표현하는가"* 하나다.

---

## 🔴 규정은 이미 **두 축**을 정해 두었다 — 그리고 이 갭은 그 사이에 있다

`platform/security-rules.md`: internal-only surface 는 *"subject allow-list **OR** required
scope"* — **둘 중 정확히 하나**를 요구한다. `platform/contracts/jwt-standard-claims.md`:
*"machine (`client_credentials`) tokens authorize on the `scope` axis"*.

저장소는 그 규정을 **지키고 있다**(TASK-MONO-521 실측):

| 부류 | 지점 | 축 |
|---|---|---|
| scope 판정기 | fan membership-service · artist-service | 필수 scope → `ROLE_INTERNAL` |
| subject allow-list | ecommerce order-service `SystemClientSubjectValidator` | `sub` 화이트리스트 |

⇒ **`/internal/**` 표면에는 답이 있다.** 답이 없는 것은 **도메인 API 표면**(`/api/v1/master/**`)
이다. 거긴 사람 역할 모델(`roles`)로 지켜지는데, 규정은 머신에 scope 축을 배정했다.
**두 축이 만나지 않는 지점이 이 ADR 의 주제다.**

---

## 모집단 — 선택지 C 의 크기를 정하는 숫자

| 축 | 수 | 측정 방법 |
|---|---|---|
| `client_credentials` 클라이언트 | **12** | 마이그레이션 INSERT 를 **문 단위** 파싱(줄 단위는 여러 줄 seed 를 놓친다). 🔵 대조군: 비-cc 4개가 정확히 브라우저 클라이언트로 떨어짐 |
| `roles` → authority 로 바꾸는 서비스 | **19 / 6개 프로젝트** | `libs/java-security-servlet` 의 `ActorClaims`(erp 4 · fan 4 · finance 2 · scm 4 · wms 5) + wms 5개의 자체 `setAuthoritiesClaimName("roles")` |
| `MASTER_WRITE` 를 요구하는 술어 | **24** (`MASTER_WRITE or MASTER_ADMIN` 12 + READ 포함 18 중 겹침) | `master-service` 1개 서비스 |

🔴 곁다리: `wms-user-flow-client` 가 **이름과 달리** cc 그랜트를 갖고 있다.

---

## 선택지

### A. 운영자 엔타이틀먼트에 `MASTER_WRITE` 추가

`OperatorRoleDerivation.WMS_OPERATOR_ROLES` 에 한 줄. 코드 변경이 가장 작다
(상수 1 + `OperatorRoleDerivationTest#wms_excludesAdminTier` assertion 1).

🔴 **그러나 이것은 대칭 복원이 아니라 결정 뒤집기다.** `TASK-BE-433` 본문:
*"Decision (user-chosen, Option A) … ADMIN-tier (…, master-data writes) is **deliberately
excluded**"*, AC-2: *"no `*_ADMIN` / `WMS_ADMIN` / **`MASTER_WRITE`** in the wms set"* —
그리고 테스트가 `doesNotContain("MASTER_WRITE")` 로 강제한다.

🔴 추가 리스크: **wms 는 데이터에 테넌트가 거의 없다** — `tenant_id` 컬럼을 가진 테이블이
5개 DB 통틀어 `outbound_db.outbound_order` **하나**뿐이다(실측). 운영자 역할을 넓히면
테넌트 격리로는 좁혀지지 않는다.

❌ `INVENTORY_RESERVE` 는 해결하지 못한다 — 계약이 사람에게 주는 것을 금지한다.

### B. 마스터 전용 운영 역할 신설

`MASTER_OPERATOR` 같은 역할을 새로 만들고 발급 평면(파생 or 부여)을 정한다. wms 안에 갇히고,
과거 결정을 뒤집지 않으며, 토큰 모델을 건드리지 않는다.

대가: 역할 모델에 역할이 하나 는다. 그리고 **master-service 의 42개 술어 중 어디까지 인정할지**
를 정해야 한다(READ 18 / WRITE 12 / ADMIN 12) — 술어를 바꿀지, 새 역할을 기존 술어에
매핑할지가 이 안의 실질 질문이다.

❌ `INVENTORY_RESERVE` 는 그대로 막힌다.

### C. 워크로드 토큰에 `roles` 클레임을 싣는다 (scope↔role 간극 자체를 메운다)

`TenantClaimTokenCustomizer#customizeForClientCredentials` **한 곳**. 각 cc 클라이언트에
그 클라이언트가 실제로 필요한 role 집합을 부여한다.

✅ **`INVENTORY_RESERVE` 까지 한 번에 푸는 유일한 안**이고, 계약이 *"machine-to-machine"* 이라
선언한 권한을 실제로 기계에 줄 수 있는 유일한 형태다.

🔴 **변경 지점과 도달 범위가 크게 다르다**: 클레임이 존재하는 순간 **19개 서비스 / 6개
프로젝트**가 머신 토큰에서 authority 를 만들기 시작한다. 빈 role 집합으로 시작해도, 그날부터
*"어느 클라이언트에 무엇을 넣으면 어디가 열리는가"* 가 **전 저장소 질문**이 된다.

🔴 **`ADR-MONO-059` 의 binding 과 맞물린다.** fan artist-service 의 쓰기 매처는
`hasAnyRole(ADMIN, OPERATOR, SUPER_ADMIN, FAN_OPERATOR)` 이고 그 체인은
`ActorContextJwtAuthenticationConverter`(위 19개 중 하나)를 쓴다. 그 매처가 **지금 머신 신원에
닫혀 있는 유일한 이유가 "머신 토큰은 role 을 안 싣는다"** 이다. C 는 그 전제를 없앤다 ⇒
`TASK-MONO-522` 의 Edge Case(*"관리 API 를 워크로드 신원으로 연다"*)가 **가능해지고**,
`ADR-MONO-059` 가 닫아 둔 평면에 문이 생기는지를 이 ADR 이 **명시적으로** 답해야 한다.

🔴 규정과의 관계: `jwt-standard-claims.md` 가 *"machine tokens authorize on the `scope` axis"*
라고 적고 있으므로, **C 는 그 문장을 개정하는 것**이다. 개정 없이 구현하면 계약과 코드가
어긋난다(이 저장소가 반복해 만난 그 모양).

### D. v1 범위 밖으로 확정하고 닫는다

`TASK-MONO-514` 의 Goal 이 명시적으로 허용하는 답이다 — *"또는 존재하지 않는 것이 의도라면
그 사실이 코드에 적혀 있고, 데모/운영이 그 전제 위에서 동작한다"*. AC-4(주석 정합)는
**이미 완료**돼 있으므로, 남는 일은 *"마스터 데이터는 Flyway 시드로만 들어간다"* 를 확정하고
`INVENTORY_RESERVE` 를 별도 티켓으로 분리하는 것뿐이다.

🔵 데모는 이 선택으로 **막히지 않는다** — 마스터 데이터는 dev 시드로 들어가고 콘솔 마스터
화면은 읽기가 된다(`GET` 200, 실측).

---

## 추천 — **C** (다만 이것은 제안이지 결정이 아니다)

두 표면(`MASTER_WRITE`, `INVENTORY_RESERVE`)이 **하나의 뿌리**를 갖고, 그 뿌리는
*"머신 신원이 인가를 표현할 자리가 없다"* 이다. A·B 는 두 표면 중 하나만 덮고, 그것도
**사람 평면을 넓혀서** 덮는다 — `INVENTORY_RESERVE` 계약이 정면으로 금지하는 방향이다.

🔴 **추천의 비용을 숨기지 않는다**: C 는 폭발반경이 가장 크고, 규정 문장 하나를 개정하며,
`ADR-MONO-059` 의 binding 을 다시 열어 심사하게 만든다. 그 세 가지를 감수할 가치가 있는지가
이 선택의 실질 질문이고, 그 판단은 소유자의 것이다.

🔵 **D 도 정당하다.** 포트폴리오 데모로서 *"마스터 데이터 생성은 v1 범위 밖"* 은 완전한 답이고,
지금 상태와의 차이는 **그 답이 적혀 있는가**뿐이다.

---

## 결과 (ACCEPTED 시)

**C 를 고르면** — `TASK-MONO-514` 가 다음을 수행한다:

1. `jwt-standard-claims.md` § machine token 인가 축 **개정**(구현보다 먼저 — 계약이 스펙이다).
2. `customizeForClientCredentials` 에 role 부여. **cc 클라이언트 12개 각각의 role 집합을
   명시적으로 정한다** — 기본값은 **빈 집합**(fail-closed).
3. AC-2: 실제 호출자가 `POST /api/v1/master/warehouses` 로 **201** 을 받는 실측.
   AC-3: 그 자격증명이 **없는** 호출자는 여전히 403(양성만으로는 "열렸다" 와 "게이트가
   사라졌다" 를 구별할 수 없다).
4. 🔴 **19개 서비스 회귀** — 머신 토큰이 role 을 실은 뒤에도 각 서비스의 기존 인증 매트릭스가
   그대로여야 한다. 특히 fan artist-service 의 `ADMIN_ROLES` 매처가 **의도치 않게 열리지
   않았는지**를 단언으로 고정한다.
5. `TASK-MONO-522` 를 **이 ADR 과 함께** 답한다(또는 후속 ADR 로 명시 위임).

**A/B/D 를 고르면** — `TASK-MONO-522` 는 독립으로 남고, `INVENTORY_RESERVE` 는 (A·B·D 모두)
별도 티켓이 된다.

---

## 결정 — **C** (ACCEPTED 2026-08-13)

머신 신원은 **`roles` 클레임으로 인가를 표현한다.** scope 축은 남되(`/internal/**` 의 두
판정기는 그대로), 도메인 API 표면에 도달하려면 role 이 필요하다는 현실을 토큰 모델이
따라간다.

### 무엇이 구속력을 갖나

1. **A · B · D 는 배제된다.** 특히 A 는 `TASK-BE-433` 의 user-chosen 결정을 뒤집지 않는다 —
   `OperatorRoleDerivation` 의 wms 집합에 `MASTER_WRITE` 는 **계속 들어가지 않는다**.
   `OperatorRoleDerivationTest#wms_excludesAdminTier` 는 **그대로 유지**한다.
2. **`jwt-standard-claims.md` 의 machine 인가 축 진술을 개정한다.** 지금 문장은
   *"machine (`client_credentials`) tokens authorize on the `scope` axis"* 이고, C 는 그것을
   좁게 참이 아니게 만든다. **개정이 구현보다 먼저다**(계약이 스펙이다 — CLAUDE.md
   § Source of Truth Priority).
3. **기본값은 빈 role 집합이다.** cc 클라이언트 12개 각각에 대해 role 집합을 **명시적으로**
   정하고, 정하지 않은 클라이언트는 **아무 role 도 받지 않는다**. 이 fail-closed 기본값이
   "19개 서비스가 갑자기 열린다" 와 "필요한 곳만 열린다" 를 가르는 유일한 장치다.

### ⚠️ 이 ACCEPT 가 **결정하지 않은 것** — rider 대조 결과

규정대로 반사가 아니라 **대조**했다: *"이 질문에 답하지 않고도 C 를 고를 수 있는가?"*

| 질문 | 답 없이 C 를 고를 수 있나 | 처리 |
|---|---|---|
| fan artist-service 의 `ADMIN_ROLES` 매처를 워크로드 신원에 여는가 | **가능하다** (C 는 *능력*을 만들 뿐, 어떤 cc 클라이언트에 admin-tier role 을 줄지는 별개) | 🔴 **미결** — `TASK-MONO-522` 로 남는다 |
| 어느 cc 클라이언트에 어떤 role 을 주는가 | 가능하다 | **구현 AC** (`TASK-MONO-514`) |
| `jwt-standard-claims.md` 를 개정하는가 | **불가능** — C 를 고르는 것이 곧 그 문장을 참이 아니게 한다 | rider 아님, 위 § 무엇이 구속력을 갖나 2 |

🔴🔴 **따라서 이 ACCEPT 는 `ADR-MONO-059` 의 binding 을 건드리지 않는다.** 소유자는 plain `C`
를 골랐고, `C + fan admin 개방` 이 나란히 제시된 적이 없다. plain 선택은 그것을 여는 것으로도
닫는 것으로도 확정되지 않으므로, **`TASK-MONO-522` 가 명시적으로 답하고 그 답을 기록해야
한다** — 조용히 빠뜨리는 것은 답이 아니다. 그때까지 어떤 cc 클라이언트도
`ADMIN`/`OPERATOR`/`SUPER_ADMIN`/`FAN_OPERATOR` 를 받지 않는다(위 fail-closed 기본값이
그것을 자동으로 보장한다).

### ACCEPT 가 인가하는 것 / 하지 않는 것

인가한다: `TASK-MONO-514` 가 § 결과 의 1~5 를 수행하는 것. 인가하지 않는다: 그 밖의 role
부여, 522 의 개방, 다른 ADR 의 재해석. 로드맵 각 단계는 별도 task 다(HARDSTOP-09).

### 게이트 — 통과했지, 우회하지 않았다

직전 라운드의 *"③(추천)"* 선택을 ACCEPT 로 읽지 않고 멈춰 정확형을 요청했고,
`ADR-MONO-061 ACCEPTED — C` 가 소유자 타이핑으로 도착했다. 왕복 1회 비용 vs 아키텍처 결정의
귀속이 영구히 흐려지는 비용의 교환이며, **그 사실 자체를 여기 적어 둔다** — 안 적으면 다음
세션엔 "그냥 통과된 것" 과 구별되지 않는다.
