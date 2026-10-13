# Task ID

TASK-MONO-793

# Title

`ADR-MONO-083` P6 — ERP 세분 서버 인가: 기준정보 · 결재의 조회/변경 역할 · `ERP_OPERATOR` 는 진입 역할로 · iam 카탈로그 확장

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (앱 단 인가 포트 · 직무 분리 가드와의 관계 · 원자 PR)
> 🔴 **cross-project 원자 PR**: erp 인가 + iam 카탈로그 · «이전 호환» ERP 세트 갱신.

# Dependency Markers

- **선행**: `TASK-BE-631` · `TASK-BE-632`(🔴 P3 가 운영자 토큰의 `erp.write` 를 이미 걷었어야 한다 — 남아 있으면 이 단계의 세분이 범위로 우회된다).
- 형제: `TASK-MONO-792` · `794` · `795`.

# Goal

ERP 쓰기 판정은 `erp.write` 범위 ∨ 운영자 역할 하나다(`masterdata-service …/RoleScopeAuthorizationAdapter.java:80-96` · `approval-service …/JwtBackedAuthorizationAdapter.java:32-35`). 기준정보 · 결재를 조회/변경으로 나눈 역할을 앱 단 `AuthorizationPort` 에 넣는다.

# Scope

## In Scope

- 계약 먼저: erp 서비스 명세 · 계약에 역할 · 판정 표.
- `RoleScopeAuthorizationAdapter` · `JwtBackedAuthorizationAdapter` · `ReadAuthorizationGate` — 업무 역할 판정. 결재 자격(직무 분리, `ApprovalRoute`)은 역할과 **별개로 유지**. `org_scope` 부서 범위 판정 무변경.
- `ERP_OPERATOR` = 진입 역할(업무 API 단독 개방 없음). 워크로드 `erp.write`(내부 서비스 클라이언트)는 무변경.
- iam 카탈로그 · «이전 호환» ERP 세트 · 시스템 프리셋.

## Out of Scope

- 관리 등급 신설 · `entitled_domains` 읽기 합성(기존 READ 확장 규칙)의 재설계 — AC-0 에서 «구독만으로 READ» 가 세분 조회 세트를 무력화하는지 측정만 하고, 무력화하면 이 티켓 범위로 끌어온다.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: 쓰기 엔드포인트 전수 · 🔴 `entitled_domains∋erp` 가 역할 없이 READ 를 여는 규칙(`RoleScopeAuthorizationAdapter.java:80-96`)이 «조회 세트 없음 → 조회 403» 을 깨는지 · 업무 구분안 대조(OD-6).
- [ ] **AC-1** — 🔴 대조군: 결재 세트만 → 기준정보 변경 403 · 결재 200 / 조회 세트 → 변경 403 / `ERP_OPERATOR` 만 → 업무 변경 403 / 결재 자격(직무 분리) 거절은 역할과 무관하게 유지.
- [ ] **AC-2** — 이전 호환 대조군 · 부서 범위(`org_scope`) 판정 불변.
- [ ] **AC-3** — 배포 상태를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3
- `projects/erp-platform/specs/services/*/architecture.md` (`TASK-ERP-BE-021` 역할 전용 모델)

# Related Contracts

- `projects/erp-platform/specs/contracts/http/*` · iam `admin-api.md` § 카탈로그

# Edge Cases

- 플랫폼 와일드카드(`tenant_id='*'`) GET — 지금 규칙 유지.
- 읽기 모델 서비스의 «`org_scope` 없음 = 좁히지 않음» 비대칭 — 무변경.

# Failure Scenarios

1. `erp.write` 가 운영자 토큰에 남은 채 착수 — 세분이 범위로 우회된다.
2. 구독 합성 READ 를 그대로 두고 «조회 세트» 를 만든다 — 세트 없이도 조회가 된다.
