# Task ID

TASK-SCM-BE-059

# Title

공급사를 등록할 API 가 없다 — 조달의 전제 데이터를 코드로는 만들 수 없다

# Status

review

# Owner

scm-platform

# Task Tags

- backend
- api
- demo-gap

---

# 배경 — `TASK-MONO-510` 6회차가 시드를 쓰다 부딪혔다

데모 시드(`infra/demo/seed/seed-scm.sh`)가 발주를 만들려다 막혔다:

```
POST /api/v1/procurement/po  {supplierId: <신규>, …}  → SUPPLIER_NOT_FOUND
```

공급사를 먼저 만들어야 하는데 **만들 방법이 없다.**

## 실측 (추론 아님)

| 확인 | 결과 |
|---|---|
| 저장소 전 컨트롤러에서 suppliers 생성 매핑 | **0건** |
| 이 프로젝트의 e2e | `ProcurementDbFixtures.insertActiveSupplier` — **직접 DB INSERT** |
| 콘솔의 공급사 관리 화면 | **없다**(`console-nav-config.ts` 의 scm 리프 6개에 부재) |

⇒ 시드가 `dbexec --why "scm 에는 공급사 등록 API 가 없다 …"` 로 직접 DB 를 쓴다.
이 저장소의 시드 규약("넣을 수 있는 것은 실제 API 로")에서 **유일하게 우회가 정당화된
항목**이고, 그 사유가 코드에 남아 있다.

🔵 **이것을 "시드의 편법" 으로 읽지 말 것.** 우회가 아니라 **유일한 경로**다. 그리고
제품 자신의 e2e 도 같은 우회를 쓴다 — 즉 이 공백은 데모가 만든 것이 아니라 원래 있었다.

---

# ✅ 게이트 해제 — `ADR-SCM-001` **ACCEPTED (A, 자격증명은 v2 유보)** · 2026-08-07

> 이 티켓은 2026-08-07 에 HARDSTOP-09 로 PAUSE 됐다가 **같은 날 해제**됐다. 아래 PAUSE 기록은
> **왜 이 결정이 필요했는지**의 근거로 보존한다 — 지우면 다음 세션이 같은 조사를 반복한다.
> 해제 내용과 그것이 이 티켓을 어떻게 바꾸는지는 이 절 **끝**에 있다.

## (보존) 당시의 PAUSE 근거 — HARDSTOP-09

**이 티켓을 착수하려다 멈췄다. 위 배경은 맞지만 그 다음 문장이 근거 없는 도약이었다.**

착수 시 `Supplier.create(...)` 와 `SupplierRepository.save(...)` 가 **이미 있고**
없는 것은 인바운드 표면 하나뿐임을 확인했다 — 여기까지는 티켓대로였다. 그런데 스펙
세 곳이 서로 다른 말을 한다:

| 출처 | 말하는 것 |
|---|---|
| `specs/services/procurement-service/architecture.md` L27 · L51-52 | 공급사 마스터는 **범위 안**이다 — *"**Maintain** a v1 internal `suppliers` master with **AES-GCM-encrypted credentials** (S6). v2 will migrate this responsibility to `supplier-service`."* |
| `specs/contracts/http/procurement-api.md` | 공급사 엔드포인트 **0건** — 계약이 의도적으로 비워 둔 것으로 읽힌다 |
| `ProcurementDbFixtures` Javadoc | *"deliberate trade-off **recorded in the task spec § Failure Scenarios**"* |

🔴 **세 번째의 인용이 dangling 이다.** `TASK-SCM-INT-001` 의 § Failure Scenarios 를 열면
A(Docker fix 실패) · B(크로스프로젝트 소비가 wms 의존) · C(nightly 자원 비용) **셋뿐**이고
공급사 픽스처 얘기는 **한 줄도 없다.** 즉 "의도적 v1 결정" 이라는 주장은 **출처가 없다** —
추론이 주석 안에서 인용으로 굳었다.

⇒ **마스터는 범위 안인데 채울 길이 없고, 계약은 비어 있으며, 비어 있는 이유는 기록되지
않았다.** 이 상태에서는 엔드포인트를 추가하는 것도, 추가하지 않는 것도 어느 스펙의 승인도
받지 못한다.

**그리고 "그냥 만들면 되는" 일이 아니다** — 마스터는 S6 로 **암호화된 공급사 자격증명**을
보유하므로(`SupplierCredentialsEncryptor` 실재) 등록 API 는 곧 *"그 자격증명을 누가 어떤
경로로 넣는가"* 를 정하는 일이고, `PROJECT.md` 가 v2 `supplier-service` 에 배정한 **이관
경계를 선점**한다.

```
[VIOLATION] HARDSTOP-09: Task `TASK-SCM-BE-059` requires an architecture decision
(cross-service contract — v1 공급사 마스터가 쓰기 표면을 갖는가, 그리고 S6 암호화
자격증명을 어떤 권한 경로로 받는가) that is not documented in
`projects/scm-platform/specs/services/procurement-service/architecture.md` or any ADR.
[WHY] Architecture decisions made implicitly during implementation produce code that later
cannot be defended against "why was this chosen" review questions — and shape every
downstream task that builds on the same service. 여기서는 v2 `supplier-service` 이관
경계와 자격증명 취급을 선점한다.
[REMEDIATION] Choose one:
  2. 결정이 중대하므로(계약 표면 + 자격증명 + v2 이관 경계) ADR 에 기록하고 ACCEPTED 까지
     PAUSE — `projects/scm-platform/docs/adr/ADR-001-supplier-master-write-surface.md`
     를 이 PR 에서 **Proposed** 로 제출했다. A/B/C 와 각각의 대가가 그 안에 있다.
[REFERENCE] CLAUDE.md § Layer Rules + platform/architecture-decision-rule.md
```

## 해제 (2026-08-07) — 무엇이 정해졌고 이 티켓이 어떻게 바뀌는가

소유자가 **정확형 intent** 로 승인했다:

> **`ADR-SCM-001 ACCEPTED — A (자격증명은 v2 유보)`**

**A 채택** ⇒ v1 공급사 마스터는 *배포 산출물*이 아니라 **운영 대상**이고, 채우는 주체는
마이그레이션이 아니라 **운영자**다. `procurement-service` 에 운영자용 등록/조회 엔드포인트를
둔다. 아래 Goal·AC 가 전제하던 A 가 실제로 채택됐으므로 **큰 재작성은 필요 없다** — 다만
rider 가 범위를 한 군데 좁힌다.

🔴 **rider "자격증명은 v2 유보" 가 좁히는 것.** ADR 의 A 원문은 자격증명을 *"이 티켓 범위 밖
(별도 엔드포인트/**후속**)"* 이라고만 적어 후속의 **시점을 열어 두었다**. rider 가 그 시점을
v2 `supplier-service` 로 못박는다 ⇒ **v1 에는 자격증명 입력 경로를 만들지 않고, v1 후속
티켓으로도 파일하지 않는다.** 따라오는 것 셋:

1. **자격증명 미보유 공급사가 v1 의 정상 상태다**(예외가 아니다). ⇒ 새 **AC-7**.
2. 🔴 `architecture.md` L51-52(*"Maintain a v1 internal `suppliers` master with
   AES-GCM-encrypted credentials (S6)"*)와 **표면적으로 충돌한다.** 유보는 마스터가 자격증명을
   **보유할 능력**(`SupplierCredentialsEncryptor`)을 없애지 않는다 — 없는 것은 **그것을 채우는
   v1 경로**다. 이 구분을 명문으로 적지 않으면 조사가 세 번째로 반복된다. ⇒ **AC-0**.
3. `TASK-SCM-BE-060`(상신이 `supplier-mock` 의존)은 **여전히 별건**이다. C 가 예고했던
   "공급사도 못 만들고 상신도 못 함" 조합은 v1 의 공식 입장이 **아니다** — 공급사 쪽은 열린다.

🔵 **공통 정리 2건 중 하나는 이미 끝났다** — 픽스처 Javadoc 의 dangling 인용은 PR #3249 가
이미 ADR 참조로 교체했다. 남은 것은 `architecture.md` 명문화이고, 그것이 AC-0 이다.

---

# Goal

공급사를 **API 로** 등록할 수 있다. 그 결과 `seed-scm.sh` 의 `dbexec` 가 사라지고
`POST /api/v1/procurement/po` 가 자기 전제를 스스로 만들 수 있다.

# Scope

## In Scope

- 공급사 생성(그리고 최소한의 조회) 엔드포인트 — **최소 필드만**(name·status·계약기간)
- `specs/contracts/` 갱신 — **구현보다 먼저**
- `specs/services/procurement-service/architecture.md` 에 ADR-SCM-001 결정 명문화 (AC-0)
- 이 프로젝트 e2e 의 `ProcurementDbFixtures.insertActiveSupplier` 를 그 API 로 전환
- `infra/demo/seed/seed-scm.sh` 의 `dbexec` 제거

## Out of Scope

- 🔴 **공급사 자격증명(S6 AES-GCM) 입력 경로 — v2 `supplier-service` 로 유보**
  (`ADR-SCM-001` ACCEPT rider). v1 에 자격증명 엔드포인트/필드를 만들지 않고, **v1 후속
  티켓으로도 파일하지 않는다.** `SupplierCredentialsEncryptor` 는 건드리지 않는다 —
  없애는 것이 아니라 **채우는 경로를 두지 않는** 것이다
- 공급사 **관리 화면**(콘솔) — 별도 프런트 티켓
- 공급사 수정/비활성 라이프사이클 — 이 티켓은 생성·조회까지
- `TASK-SCM-BE-060`(발주 상신이 supplier-mock 에 의존) — 별개 결함

---

# Acceptance Criteria

- [x] **AC-0a (결정 게이트)** — ✅ **해제됨**. `ADR-SCM-001` 이 **A(자격증명 v2 유보)** 로
      ACCEPTED(2026-08-07, 소유자 정확형 intent). 공통 정리 (a) 픽스처 Javadoc 의 dangling
      인용 교체는 PR #3249 + 본 ACCEPT PR 이 완료
- [x] **AC-0b (결정 명문화) — 완료 (스펙 PR, 2026-08-08).** `architecture.md` L51-52 를
      정정했다. 🔴 **원문이 표면상 충돌한 게 아니라 그냥 사실이 아니었다**(아래 AC-7 실측):
      *"with AES-GCM-encrypted credentials (S6)"* 는 **스키마에만** 참이었다. 그래서 적은
      구분은 "보유 능력은 유지 / 채우는 v1 경로 없음" + **왜 그 문장이 두 번의 조사를
      낳았는지**까지다
- [x] **AC-1 (계약 우선) — 완료 (스펙 PR, 2026-08-08).** `procurement-api.md` 에
      `POST /suppliers` · `GET /suppliers` · `GET /suppliers/{id}` 를 기존 규약대로 추가.
      🔵 **새 에러코드 0** — `SUPPLIER_NOT_FOUND`(404)·`SUPPLIER_INACTIVE`(422)가 이미 표에
      있었다. 🔴 **AC-3 의 멱등을 계약이 두 갈래로 명시**한다: 같은 `Idempotency-Key`=201
      replay / **다른 키 + 같은 `code`=200 + 행 증가 없음**. 시드 재실행·CI 재기동이 키를
      잃는 경우가 실제 수렴 경로라서, 상태코드를 갈라 두지 않으면 판정이 불가능하다
- [x] **AC-2 (생성) — 완료 (구현 PR, 2026-08-08).** `POST /api/procurement/suppliers`
      (`SupplierController` → `SupplierApplicationService.register`). IT
      `SupplierRegistrationIntegrationTest#registeredSupplierIsUsableForPoDraft` 가
      등록한 id 로 `draft()` 가 `SUPPLIER_NOT_FOUND` 없이 통과함을 실측한다
- [x] **AC-3 (멱등) — 완료 (구현 PR, 2026-08-08).** 선행 마이그레이션
      `V6__suppliers_natural_key_code.sql`(`code` 컬럼 + `UNIQUE (tenant_id, code)`,
      백필 `UPPER(id)`)을 먼저 넣었다. 판정은 **행 수**로 한다 —
      `registerIsIdempotentOnCode` 가 두 번 호출 뒤 `(tenant, code)` 행 수 = 1 을 잰다.
      🔵 멱등이 **두 층**인 것이 설계다: `Idempotency-Key` 래퍼는 키를 **가진** 재시도를,
      자연키 검사는 키를 **잃은** 호출자(재실행 시드·새 CI 잡)를 덮는다. 후자가 없으면
      재실행이 V6 유니크에 걸려 409 가 되지 수렴이 되지 않는다
- [x] **AC-4 (테스트 전환) — 완료 (구현 PR, 2026-08-08).** 호출자 **6개 전수**
      (`AsnReceive`·`CrossTenantIsolation`·`InboundExpectedLoop`·`ProcurementHappyPath`·
      `SupplierAckWebhook`·`SupplierCircuitBreaker`)를
      `SupplierApiFixtures.registerActiveSupplier` 로 옮기고
      `ProcurementDbFixtures.insertActiveSupplier` 를 **삭제**했다(deprecate 아님 —
      같은 행에 이르는 길이 둘이면 픽스처와 제품이 조용히 갈라진다).
      `countAuditRows`(읽기 전용)는 남는다
- [~] **AC-5 (시드) — 코드 완료, 라이브 2회차 실행 미실시 (2026-08-08).**
      `dbexec` 는 사라졌고 `seed_supplier` 가 등록 API 를 호출한다. 🔴 **id 가 더 이상
      우리가 정하는 값이 아니다** — 예전엔 PK 에 `SUP-DEMO-01` 을 박아 이후 호출이 그
      문자열을 그대로 supplierId 로 썼지만, 이제 서버가 UUID 를 만들고 그 문자열은
      `code` 다. 그래서 응답에서 id 를 뽑아 이후 호출에 넘긴다(jq 없음 — lib.sh 규약).
      🔵 옛 `dbexec` 행이 남은 DB 에서는 V6 백필이 `code='SUP-DEMO-01'` 을 채우므로 이
      호출이 **그 행으로 수렴**한다(중복 생성 없음).
      ⚠️ **깨끗한 볼륨 1회차/2회차 실행은 이 PR 에서 하지 않았다.** 미검증분을 정확히
      적는다 — 아래 § 남은 검증
- [x] **AC-6 (권한) — 완료·실측 (구현 PR, 2026-08-08).** 게이트는
      `ActorContext.isOperator()`(= `OPERATOR|ADMIN|SUPER_ADMIN|SCM_OPERATOR`).
      🔴🔴 **`scm.write` 를 요구하지 않기로 한 것이 이 AC 의 실측 결과다.** assume-tenant
      토큰의 `scope` 는 `platform-console-web` 클라이언트의 **등록 스코프**
      (`openid profile email tenant.read erp.write` — auth-service V0015 + V0023)이고,
      `scm.write` 는 **별개의 `scm` 클라이언트**(V0013)에 달려 있다. 요구했다면 존재하는
      유일한 운영자 신원이 403 을 받았을 것 — wms `MASTER_WRITE` 와 정확히 같은 모양
      (`TASK-MONO-514`). 역할 쪽은 성립한다: `demo-corp` 의 `scm` 구독 ACTIVE(V9005) →
      `OperatorRoleDerivation` 이 `SCM_OPERATOR` 발급 → `isOperator()` 가 이미 수용.
      🔵 스코프 축은 **그런 호출자가 생기면** 더한다(erp `RoleScopeAuthorizationAdapter`
      WRITE 선례 `hasScope ∨ isOperator`) — scm `ActorContext` 엔 스코프 축 자체가 없다
- [x] **AC-7 (자격증명 미보유가 정상 상태) — 실측이 이 AC 를 없앴다 (2026-08-08).**
      🔴🔴 **이 AC 의 전제가 틀렸고, 그 전제를 쓴 ADR 은 내가 썼다.** ADR § Consequences 의
      *"자격증명 미보유 공급사에 대한 `SupplierAdapterPort` 의 동작을 정의해야 한다"* 는
      **어댑터가 자격증명을 소비한다는 미검증 가정** 위에 서 있었다. 전수로 재니:

      ```
      SupplierCredentialsEncryptor   프로젝트 전체 참조 = 자기 파일뿐  (호출부 0건)
      supplier_credentials 테이블    읽는 코드 0 · 쓰는 코드 0        (DDL 에만 존재)
      SupplierAdapterPort            submitPurchaseOrder(PurchaseOrder, String)
                                     — 자격증명 파라미터 자체가 없다
      RestSupplierAdapter            credential|secret|apiKey 참조 0건
      ```

      ⇒ **정의할 새 동작이 없다.** "자격증명 미보유" 는 새로 생기는 상태가 아니라 이 서비스에
      **지금까지 존재한 유일한 상태**다. 그래서 이 AC 는 *동작 정의*가 아니라 **그 사실을
      문서에 못 박기**로 축소되고, 그건 AC-0b 에서 끝났다.
      🔵 **rider 의 비용이 0 인 이유가 여기서 드러난다** — "자격증명은 v2 유보" 는 새 제약을
      거는 것이 아니라 **이미 그러했던 현실을 명문화**한 것이다.
      🔴 남는 실행 항목 하나: 구현이 자격증명 필드를 **받지 않는다**는 것을 유지 — 계약에
      명시해 뒀으니(AC-1) 요청 DTO 에 필드를 더하지 말 것

---

# 🔴 계약(AC-1)의 자기 정정 — 구현이 스펙 PR 의 오류 둘을 잡았다

AC-1 은 스펙 PR 에서 "완료" 로 닫혔지만, 구현하면서 **내가 쓴 계약** 두 곳이 틀린 것을
발견해 같은 PR 에서 고쳤다. 둘 다 *구현이 계약을 따르려 하자* 드러난 것이다:

1. **`GET /suppliers` 의 봉투 모양** — `data: [...]` + `meta` 에 페이지 카운터로 적어
   뒀는데, 이 서비스의 `ApiEnvelope`+`PageResponse` 조합이 **낼 수 없는 모양**이고 같은
   파일의 형제(`GET /po`)는 `data: {content, page, …}` 를 낸다. 그대로 구현했으면 한
   계약 파일 안의 두 목록 엔드포인트가 서로 다른 형식을 답했을 것이다 ⇒ 형제에 맞춰 정정.
2. **인가 절의 `+ scm.write`** — AC-6 실측이 이것을 뒤집었다(위 AC-6). 콘솔 클라이언트에
   그 스코프가 없다.

🔵 교훈은 "계약 우선" 이 틀렸다는 게 아니라 **계약도 형제와 대조해야 한다**는 것이다.
1번은 형제 엔드포인트를 열어 보기만 했으면 스펙 PR 에서 잡혔다.

---

# ⚠️ 남은 검증 (이 PR 이 재지 않은 것)

- **AC-5 라이브**: 깨끗한 볼륨에서 `demo-up.sh scm` → `seed-scm.sh` **2회** 실행,
  실패 0 + `suppliers` 행 1. 스크립트 로직이 아니라 **스택 기동**이 비용이라 미실시.
  🔵 위험의 큰 쪽(게이트웨이 라우팅 · 운영자 토큰 인가 · 멱등)은 CI 의 scm e2e 6개가
  같은 엔드포인트를 실제 HTTP 로 지나가며 덮는다. 남은 고유 위험은 **bash 파싱**
  (id 추출 sed/grep)과 **V6 백필이 옛 데모 행에 실제로 도는가** 둘이다
- **콘솔 화면**: 공급사 관리 화면은 여전히 없다(범위 밖, 별도 프런트 티켓). 즉 이 PR 로
  운영자는 **API 로는** 공급사를 만들 수 있지만 **화면으로는** 아직 못 만든다

---

# Related Specs

- `specs/contracts/` — 조달 계약(에러 봉투 규약의 출처)
- `specs/services/procurement-service/architecture.md`

# Related Contracts

- 조달 API 계약 — 공급사 참조가 이미 등장한다(`supplierId`)

# Edge Cases

- 이미 존재하는 코드로 생성 → 409 인가 200(멱등)인가. **계약이 먼저 정해야 한다**
  ✅ 정해졌다: **순차 재등록 = 200 수렴**(키를 잃은 호출자가 정상 경로다), **동시 등록의
  패자만 409**(유니크에 걸린 경쟁 결과이지 재등록의 답이 아니다). 계약에 갈라 적었다
- 발주가 참조 중인 공급사의 비활성 — 이 티켓 범위 밖이지만 계약이 문을 열어 두지 말 것

# Failure Scenarios

- **생성만 만들고 조회를 안 만든다** → 시드가 멱등을 판정할 수 없어 2회차에 행이 두 배가 된다
- **e2e 픽스처를 지우지 않고 API 만 추가** → 두 경로가 공존해 나중에 조용히 갈라진다

# Definition of Done

- [x] AC-0b · AC-1 · AC-2 · AC-3 · AC-4 · AC-6 · AC-7 (AC-0a 는 ACCEPT 로 해제 완료)
- [~] AC-5 — 코드 완료, 라이브 2회차 실행만 미실시 (§ 남은 검증)
- [ ] Ready for review
