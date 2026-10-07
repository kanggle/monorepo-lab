# Task ID

TASK-MONO-774

# Title

`ADR-MONO-080` D7 = **E1** — erp 직원 마스터가 IAM 계정을 안다(`employees.account_id`) · 결재함·자기결재·계약 E3 를 한 id 공간으로

# Status

ready

# Owner

monorepo

# Task Tags

- erp
- iam
- platform-console
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (교차 컨텍스트 참조 · 결재 권한 술어)

---

# Dependency Markers

- **선행**: 없음 — 단, AC-0 에서 «연결을 누가 쓰나» 를 **«D6 초대 수락의 부산물»** 로 고르면 `TASK-MONO-772` `done/` 이 선행이다.
- 관련: `TASK-PC-FE-311`(콘솔 «나 (현재 운영자)» 보정 — 이 티켓이 걷는다) · `TASK-PC-FE-309`

# Goal

«누가 결재자인가» 를 계약은 **직원**으로(`approval-api.md:253, 323-326`), 코드는 **계정 `sub`** 로(`ApprovalRequestJpaRepository.java:61-67`) 답한다. 직원 마스터에 계정 칸이 없고(`V1__init.sql:38-55`), 계약 E3(승인자 = 살아 있는 직원)를 코드가 지키지 않으며(`ApprovalApplicationService.java:122-146`), 자기결재는 다른 id 공간을 비교한다(`:92, 98`). 데모는 승인자에 운영자 `sub` 를 넣는 편법으로 결재함을 채운다(`seed-erp.sh:316-319`). E1 로 정리한다:

- `employees.account_id`(NULL 허용 · 테넌트 안 유니크 · FK 없음 — 쓰기 때 IAM 으로 검증)
- 결재함 술어 = «승인자 직원의 `account_id` = 내 `sub`» · 계약 `approverId` = 직원 id **유지**
- 상신 때 E3 집행(승인자 = 살아 있는 직원) · 자기결재 = 상신자의 직원(내 `sub` 로 푼 직원) vs 승인자 직원, **같은 id 공간**
- 상신자 · 이력 처리자 · 위임 피위임자 칸도 같은 모델(위임 한 행의 두 id 공간 `seed-erp.sh:393, 410`)

# Scope

## In Scope

- 계약 먼저: erp masterdata 직원 계약(`account_id` 필드 · 연결 쓰기 API) · `approval-api.md`(결재함 술어 서술 · E3 거절 코드 · 자기결재)
- erp masterdata: 마이그레이션 · 연결 쓰기(누가 쓰나 — AC-0) · IAM 계정 존재 검증
- erp approval: 결재함 · 상신 E3 · 자기결재 · 위임 · 이력 처리자
- 데모 시드 §6: 편법(승인자 = 운영자 `sub`) 대신 «운영자 계정과 연결된 직원» 을 승인자로 — 🔵 소유자 결정(2026-10-07)의 «시드가 모델을 몰래 정하지 않게» 는 모델이 정해졌으므로 이제 해소된다
- 콘솔: `TASK-PC-FE-311` 보정 걷기 · 직원 상세/목록에 «연결된 계정» 표시 · 연결 쓰기 화면(AC-0 결정에 따라)

## Out of Scope

- 다른 도메인(wms 작업자 · scm 담당자)의 사람 마스터 — ADR-080 Context 가 erp 만 쟀다. AC-0 에서 모집단만 세어 후속 티켓으로.

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정(위 file:line 전부) + 🔴 **소유자 결정: 연결을 누가 쓰나** — ⓐ 인사 담당(erp 권한) · ⓑ 본인 수락 · ⓒ D6 초대 수락의 부산물(772 선행) — 선택지와 추천을 내고 결정을 받는다. 다른 도메인 사람 마스터 모집단을 센다.
- [ ] **AC-1** — 🔴 «전» 상태를 먼저 단언: 계약대로 승인자 = 직원 id 로 상신하면 결재함이 0 이 된다(현재) → 구현 뒤 연결된 계정으로 결재함에 보인다.
- [ ] **AC-2** — E3: 살아 있지 않은 직원 · 존재하지 않는 직원을 승인자로 상신 → 거절.
- [ ] **AC-3** — 자기결재: 내 계정과 연결된 직원을 승인자로 → 거절(지금은 다른 id 공간이라 통과한다 — 그 «전» 도 단언).
- [ ] **AC-4** — 연결 없는 직원(`account_id` NULL)은 승인자로 지정될 수는 있으나 결재함에 나타날 사람이 없다는 것을 화면이 말한다(또는 상신 거절 — AC-0 에서 결정).
- [ ] **AC-5** — 데모 시드 재굽기 뒤 결재함이 편법 없이 채워지고, `TASK-PC-FE-311` 보정 코드가 제거돼도 화면이 이름을 보인다(라이브 ⚪ 가능).

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § 새 입력 · D7
- `docs/adr/ADR-MONO-060`(assume 토큰 `sub` = 계정 UUID)
- `projects/erp-platform/specs/contracts/http/approval-api.md` · `projects/erp-platform/specs/`(masterdata)

# Related Contracts

- erp `approval-api.md` · erp masterdata 직원 계약 · IAM 계정 조회(내부)

# Edge Cases

- 한 계정이 같은 테넌트의 직원 둘에 연결 — 테넌트 안 유니크로 막는다.
- 퇴사 직원(상태 비활성)과 그 계정 — 연결을 남기되 E3 가 막는다.
- 계정 삭제·잠금 — 연결은 남고 결재함에는 들어올 수 없다(로그인 불가).

# Failure Scenarios

1. 결재함 술어만 바꾸고 데모 시드를 안 바꾼다 — 데모 결재함이 0 이 된다.
2. 자기결재 비교를 한쪽만 직원으로 바꾼다 — 다른 id 공간 비교가 그대로 남는다.
