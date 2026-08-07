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
