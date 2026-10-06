# Task ID

TASK-PC-FE-309

# Title

ERP 결재 목록·상세가 부서·직원·기안자·결재선을 UUID 그대로 찍는다 — `TASK-PC-FE-276`/`277` 의 census 둘 다 이 화면을 안 봤다

# Status

ready

# Owner

platform-console

# Task Tags

- erp-ops
- ui
- readability
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 16 — erp 결재 한 건 처리).
- 선행 읍을 것 — **이 티켓은 중복이 아니다, 이유를 적는다**:
  - `TASK-PC-FE-276`(DONE) 은 `/erp/masters` **8곳**만 고쳤다(`masterRefLabel`/`codeName`
    헬퍼 + `data-master-ref` 마커 + 회귀 가드를 그 자리에서 만들었다).
  - `TASK-PC-FE-277`(DONE) 은 다른 12개 도메인의 48(재집계 66)곳을 census 했지만
    **`features/erp-ops/` 를 통째로 제외**했다 — 이유는 "276 이 이미 고쳤다" 였는데, 276
    의 실제 범위는 `/erp/masters` 뿐이었다. 즉 **결재(`approval-*`) 화면은 276 의 범위에도
    277 의 census 모집단에도 들어간 적이 없다** — 이것은 두 티켓 중 하나가 "결함 아님"으로
    판정한 재방문이 아니라, 둘 다 들여다본 적이 없는 census 공백이다.

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

- `/erp/approvals` 목록: 대상(`대상`) 칸이 `부서 · 01a10fd2-742e-…` / `직원 · 01a10fd2-78b3-…`
  로 렌더된다.
- 결재 상세: 기안자가 `0199de70-…ad04`, 결재선/이력의 처리자(actor)가 `0199de70-…ad03` 로
  렌더된다.

둘 다 **다른 엔티티(부서/직원/운영자)를 가리키는 참조 칸**이다 — `TASK-PC-FE-277` 이
세운 판정 기준(자기 식별자 vs 참조, Edge Cases 참조)으로는 명백히 "참조" 쪽이고, 그
기준으로는 결함 후보다.

관련 파일(확인됨, `git ls-files`):

```
features/erp-ops/components/ApprovalScreen.tsx
features/erp-ops/components/ApprovalDetail.tsx
features/erp-ops/components/approval-common.tsx
features/erp-ops/api/approval-types.ts / approval-reads.ts
```

`shared/lib/master-ref-label.ts` (276 이 만들고 277 이 `erp-ops/lib/` → `shared/lib/` 로
옮긴 공용 헬퍼)가 이미 존재한다 — **새 포맷을 만들 필요가 없다.**

---

# Goal

ERP 결재 목록·상세의 참조 칸(부서·직원·기안자·결재선 처리자)이 UUID 대신 이름(또는
`CODE · 이름`)을 보여준다 — `/erp/masters` 와 같은 패턴으로.

---

# Scope

## In Scope

- `TASK-PC-FE-277` 과 같은 술어로, `features/erp-ops/components/Approval*.tsx` +
  `approval-common.tsx` 범위만 다시 센다(census, 추측하지 않는다).
- 각 참조 칸이 **자기 식별자인지 참조인지** 가른다(277 의 기준 그대로: 결재 자신의 id는
  자기 식별자, 부서/직원/기안자/처리자는 참조).
- 참조로 판정된 칸에 대해 **런타임 값**(시드/픽스처)으로 UUID 인지 읍을 수 있는 코드인지
  확인한다 — 콘솔 zod 는 전부 `z.string()` 이라 선언으로는 못 가른다(277 이 이미 실측).
- 결함으로 판정된 칸만 `shared/lib/master-ref-label.ts` 의 `masterRefLabel`/`codeName` 으로
  교체하고 `data-master-ref` 마커를 달아, `tests/unit/erp-master-ref-names.test.tsx`
  (276/277 의 회귀 가드)의 모집단을 넓힌다.

## Out of Scope

- 새 포맷/헬퍼 생성 — `masterRefLabel`/`codeName` 을 재사용한다.
- 새 가드 작성 — 기존 `erp-master-ref-names.test.tsx` 를 넓힌다.
- 결재 자신의 id(목록/상세에 그 결재 건을 식별하는 자리) — 운영자가 그 id 로 검색/지원
  요청을 받을 수 있으므로 이름으로 바꾸지 않는다.
- DTO 에 이름/코드가 없어 화면에 닿지 않는 자리의 백엔드 계약 변경 — 발견되면 후속 티켓
  (`TASK-PC-FE-277` 이 `TASK-MONO-659` 를 그렇게 분리한 것과 같은 패턴).

---

# Acceptance Criteria

- [ ] **AC-0 (census)** — `TASK-PC-FE-277` 의 술어로 `approval-*` 범위를 다시 세고, 몇
      곳이 있는지·그중 몇이 참조인지 표로 남긴다.
- [ ] **AC-1 (가르기)** — 참조로 판정된 칸마다, 그 이름의 출처가 **그 화면에 실제로
      닿는지** 확인한다(277 AC-1 이 겪은 함정: "시스템 어딘가에 이름이 있다"와 "이 화면이
      받는 props/스키마에 이름이 있다"는 다른 질문이다).
- [ ] **AC-2** — 이름이 닿는 칸만 `masterRefLabel`/`codeName` 으로 교체 + `data-master-ref`
      마커. 이름이 안 닿는 칸은 추측하지 않고 ⚪ + 후속 티켓으로 분리.
- [ ] **AC-3** — `erp-master-ref-names.test.tsx` 의 모집단이 늘어난다(새 가드를 짓지
      않는다). bite(한 곳을 되돌리면 빨강) 확인.
- [ ] **AC-4** — 결재 자신의 id 는 손대지 않았음을 확인(회귀 — 자기 식별자 보존).

---

# Related Specs

- `projects/erp-platform/specs/contracts/http/`(결재 목록/상세 DTO — 이름/코드 필드 유무 확인)
- `projects/platform-console/apps/console-web/src/features/erp-ops/lib/` 는 더 이상 쓰지
  않는다 — `shared/lib/master-ref-label.ts` 가 정본(`TASK-PC-FE-277` AC-0 이동).

# Related Contracts

- 없음(발견되면 Out of Scope 의 후속 티켓이 가져간다)

---

# Edge Cases

- 기안자/결재선 처리자는 **운영자**(이름 있음) 참조, 부서/직원은 **마스터데이터** 참조 —
  서로 다른 조회 경로일 수 있다.
- 결재 이력(history)의 처리자가 **이미 탈퇴/삭제된 운영자**일 경우 이름 조회가 실패할 수
  있다 — `TASK-PC-FE-277` 의 `이름 확인 불가` 폴백 패턴을 따른다(id 로 조용히 되돌아가지
  않는다).

# Failure Scenarios

- **새 라벨 포맷을 만든다** — `codeName`/`masterRefLabel` 이 이미 있다(277 Failure 4 와
  동일한 함정).
- **결재 자신의 id 를 이름으로 바꾼다** — 운영자가 그 id 로 검색/지원 요청을 받는 경로를
  깨뜰린다.
- **`data-master-ref` 마커를 안 단다** — 고쳐도 회귀 가드 밖이라 다음에 되돌아가도 안 보인다.
- **선언(zod 스키마)만 보고 UUID 여부를 판정한다** — 콘솔 zod 는 전부 `z.string()`,
  런타임 값을 확인해야 한다(277 이 실측으로 증명).
