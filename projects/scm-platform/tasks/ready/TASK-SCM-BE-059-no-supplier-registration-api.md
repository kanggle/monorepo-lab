# Task ID

TASK-SCM-BE-059

# Title

공급사를 등록할 API 가 없다 — 조달의 전제 데이터를 코드로는 만들 수 없다

# Status

ready

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

# ⛔ PAUSE — HARDSTOP-09 (2026-08-07, 착수 시 스펙 확인에서 발견)

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

**해제 조건**: `ADR-SCM-001 ACCEPTED` + A/B/C 중 선택.

🔵 **아래 Goal·AC 는 A 안을 전제하고 쓰여 있다** — 그 전제가 ADR 의 결론을 선취하고
있었다. 선택된 안에 맞춰 다시 쓸 것. C(부재를 명시적 결정으로 승격)를 고르면 이 티켓은
**closed-as-decided** 이고, 그때도 픽스처의 dangling 인용 교체와 architecture.md 명문화는
해야 한다(안 하면 이 조사가 또 반복된다).

---

# Goal

공급사를 **API 로** 등록할 수 있다. 그 결과 `seed-scm.sh` 의 `dbexec` 가 사라지고
`POST /api/v1/procurement/po` 가 자기 전제를 스스로 만들 수 있다.

# Scope

## In Scope

- 공급사 생성(그리고 최소한의 조회) 엔드포인트
- `specs/contracts/` 갱신 — **구현보다 먼저**
- 이 프로젝트 e2e 의 `ProcurementDbFixtures.insertActiveSupplier` 를 그 API 로 전환
- `infra/demo/seed/seed-scm.sh` 의 `dbexec` 제거

## Out of Scope

- 공급사 **관리 화면**(콘솔) — 별도 프런트 티켓
- 공급사 수정/비활성 라이프사이클 — 이 티켓은 생성·조회까지
- `TASK-SCM-BE-060`(발주 상신이 supplier-mock 에 의존) — 별개 결함

---

# Acceptance Criteria

- [ ] **AC-0 (결정 — 선행 게이트)** — `ADR-SCM-001` 의 A/B/C 중 하나가 **ACCEPTED** 되어야
      아래 AC 를 착수할 수 있다. 🔴 제안자가 자기 ADR 을 승인할 수 없고, 맨 "진행" 은
      승인이 아니다(`platform/architecture-decision-rule.md § The ACCEPTED Gate`).
      🔵 **어느 안이든 공통으로 해야 하는 것 둘**: (a) `ProcurementDbFixtures` Javadoc 의
      **없는 절을 가리키는 인용** 교체 (b) `architecture.md` 에 결정 명문화
- [ ] **AC-1 (계약 우선)** — `specs/contracts/` 에 공급사 생성/조회 계약이 있고,
      필드·에러코드가 기존 조달 계약의 규약(flat `{code,message,details?,timestamp}`)을 따른다
- [ ] **AC-2 (생성)** — 인증된 운영자 토큰으로 공급사를 생성할 수 있고, 그 id 로
      `POST /api/v1/procurement/po` 가 `SUPPLIER_NOT_FOUND` 없이 통과한다
- [ ] **AC-3 (멱등)** — 같은 자연키(코드)로 두 번 호출하면 행이 하나다.
      🔴 **판정은 로그 라벨이 아니라 행 수로** 한다 — 멱등 replay 도 2xx 를 내므로
      라벨만 보면 "생성" 이 두 번 찍힌다(`seed-scm.sh` 가 실제로 그렇게 오독했다)
- [ ] **AC-4 (테스트 전환)** — `ProcurementDbFixtures.insertActiveSupplier` 를 쓰던
      e2e 가 새 API 를 쓴다. 🔵 픽스처를 **지우기만 하지 말 것** — 남은 호출자를
      전수 grep 해서 옮긴다
- [ ] **AC-5 (시드)** — `seed-scm.sh` 의 공급사 `dbexec` 가 사라지고, 깨끗한 볼륨에서
      1회차/2회차 모두 실패 0 으로 수렴한다
- [ ] **AC-6 (권한)** — 어떤 역할이 생성할 수 있는지 명시하고, 데모 운영자 토큰
      (`assume demo-corp` → `SCM_OPERATOR`)이 그것을 **실제로 갖는지** 실측한다.
      🔴 wms 마스터 쓰기가 정확히 여기서 막혔다(`MASTER_WRITE` 를 아무도 못 받는다 —
      `TASK-MONO-514`) — 같은 함정을 반복하지 말 것

# Related Specs

- `specs/contracts/` — 조달 계약(에러 봉투 규약의 출처)
- `specs/services/procurement-service/architecture.md`

# Related Contracts

- 조달 API 계약 — 공급사 참조가 이미 등장한다(`supplierId`)

# Edge Cases

- 이미 존재하는 코드로 생성 → 409 인가 200(멱등)인가. **계약이 먼저 정해야 한다**
- 발주가 참조 중인 공급사의 비활성 — 이 티켓 범위 밖이지만 계약이 문을 열어 두지 말 것

# Failure Scenarios

- **생성만 만들고 조회를 안 만든다** → 시드가 멱등을 판정할 수 없어 2회차에 행이 두 배가 된다
- **e2e 픽스처를 지우지 않고 API 만 추가** → 두 경로가 공존해 나중에 조용히 갈라진다

# Definition of Done

- [ ] AC-1~AC-6 전부
- [ ] Ready for review
