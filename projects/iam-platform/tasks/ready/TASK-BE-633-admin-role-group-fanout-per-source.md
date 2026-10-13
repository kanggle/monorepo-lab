# Task ID

TASK-BE-633

# Title

관리 역할 그룹 fan-out 의 **단일 출처** 결함 — G1 회수가 G2 가 준 같은 역할을 지운다 · 그룹 행 위 직접 역할 PATCH 의 조용한 출처 변환(추론) 확인

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (fan-out · PK · 회수 경로)
> 출처: `ADR-MONO-083` § Outstanding(ACCEPT PR 에서 기안 — «ACCEPT 후 기안» 을 산문에만 남기지 않는다).

# Dependency Markers

- **선행**: 없음(ADR-083 단계와 독립). `TASK-BE-631` 이 같은 문제를 도메인 세트에서 출처별 행으로 푼다 — 그 설계를 재사용할지 AC-0 에서 정한다.

# Goal

관리 역할(`admin_operator_roles`) 그룹 fan-out 은 행 하나에 `group_origin` 한 칸이라, 두 그룹이 같은 역할을 주면 먼저 만든 그룹이 행을 갖고 그 그룹 회수 시 다른 그룹 멤버의 권한이 사라진다(`GroupFanOutService.java:131-145` · `data-model.md:243` 이 v1 성질로 인정). 이것을 출처별로 안전하게 만든다.

# Scope

## In Scope

- AC-0 측정: G1 · G2 중복 회수 시험(지금 빨강을 먼저 확인) · 🔵 그룹 행 위 같은 테넌트 직접 역할 PATCH 가 `saveAll` merge 로 그룹 행을 감사 없이 직접 행으로 바꾸는지 시험으로 확인(지금은 코드 추론 — `PatchOperatorRoleUseCase.java:126-132` · `JpaAdminOperatorAdapter.java:373-381` · 엔티티에 `Persistable` 없음).
- 출처별 표현(출처 표 또는 PK 확장)과 마이그레이션 · fan-out · 회수 · 내보내기 · 784 의 마지막 관리자 가드 대조.
- 비-내보내기 경로(그룹 TENANT_ASSIGNMENT 회수 · 탈퇴 · 그룹 삭제)가 다른 테넌트의 직접 역할 행을 소속 없이 남기는지(784 D-1 불변식) 측정 · 필요하면 수선.

## Out of Scope

- 그룹을 상속 모델로 전환(ADR-046 D2-B — 별도 ADR).
- 도메인 세트(`TASK-BE-631`).

# Acceptance Criteria

- [ ] **AC-0** — 위 측정 셋의 결과(빨강/초록 · file:line) · 수선 설계 선택과 이유.
- [ ] **AC-1** — 🔴 대조군: G1 · G2 가 같은 역할 → G1 탈퇴 → 역할 유지 → G2 탈퇴 → 사라짐 · 직접 + 그룹 → 그룹 회수 뒤 직접 유지 · 직접 회수 뒤 그룹 유지.
- [ ] **AC-2** — 직접 PATCH 와 그룹 행 공존 시 감사에 출처 변화가 남거나 두 출처가 공존한다(조용한 변환 0).
- [ ] **AC-3** — 784 불변식: 비-`'*'` 역할 행의 테넌트 ∈ 소속 — 그룹 경로 회수 뒤에도 참.

# Related Specs

- `docs/adr/ADR-MONO-046-operator-group-model.md` D2 · D5 · `docs/adr/ADR-MONO-083-…md` D4 · § Outstanding
- `projects/iam-platform/specs/services/admin-service/data-model.md` · `rbac.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § groups (응답에 출처가 드러나는 칸이 바뀌면)

# Edge Cases

- 같은 역할을 직접 + 두 그룹이 주는 상태에서 그룹 삭제 — 남는 출처 둘.
- 마지막 관리자 가드(784)가 «다른 출처가 남아 있음» 을 보고 회수를 허용해야 하는 경우.

# Failure Scenarios

1. 기존 데이터 이전 없이 PK 만 바꾼다 — 이미 «먼저 쓴 그룹» 에 묶인 행의 다른 출처가 영영 기록되지 않는다(재-fan-out 필요).
2. 추론(PATCH merge)을 측정 없이 사실로 적고 고친다 — 없는 결함을 고치거나 있는 결함을 놓친다.
