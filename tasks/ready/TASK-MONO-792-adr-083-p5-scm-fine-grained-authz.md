# Task ID

TASK-MONO-792

# Title

`ADR-MONO-083` P5 — SCM 세분 서버 인가: 발주 · 수요계획 · 물류 · 재고 가시성의 조회/변경 역할 · `SCM_OPERATOR` 는 업무 API 를 열지 않는 진입 역할로 · iam 카탈로그 확장

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

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (서버 인가 신설 · 카탈로그 · 이전 호환 세트 동시 갱신 — 원자 PR)
> 🔴 **cross-project 원자 PR**: scm 서비스 인가 + iam 카탈로그 · «이전 호환» SCM 세트 갱신이 한 PR 이어야 한다. 갈라지면 기존 운영자가 그 사이에 SCM 업무 0 이 되거나 세분이 무력화된다.

# Dependency Markers

- **선행**: `TASK-BE-631`(카탈로그 · 세트) · `TASK-BE-632`(발급 전환 — 이 단계의 역할은 세트로만 토큰에 실린다).
- 같은 모양의 형제: `TASK-MONO-793`(ERP) · `794`(Finance) · `795`(E-commerce).

# Goal

지금 SCM 4 서비스는 `authenticated()` 만 검사한다(`demand-planning-service …/SecurityConfig.java:45` · `logistics-service …/SecurityConfig.java:39` · `inventory-visibility` `:63-64` · `procurement` `:58`). 업무 구분(ADR-083 § 도메인 업무 구분 제안, OD-6 기본값)대로 서버 인가를 넣어 «SCM 조회 세트» · «발주 운영 세트» 가 서버에서 실제로 구분되게 한다.

# Scope

## In Scope

- 계약 먼저: `projects/scm-platform/specs/services/*/architecture.md` · `specs/contracts/http/*` 에 역할 · 판정 표.
- 서비스 인가: procurement · demand-planning · logistics(조회/변경) · inventory-visibility(조회). 이름은 계약에서 확정(예: `SCM_PROCUREMENT_READ/WRITE`). 기존 procurement 의 상태 기계 판정(발주 확정 = OPERATOR)과 공급사 등록 `requireOperator()` 는 **함께 유지** — 역할 판정과 겹쳐도 넓히지 않는다.
- `SCM_OPERATOR` = 진입 역할: 게이트웨이 통과 · 화면 진입용, 업무 API 를 단독으로 열지 않는다. 업무 역할이 하나라도 있으면 서버(auth)가 파생.
- iam: 카탈로그에 SCM 업무 역할 추가 · «이전 호환» SCM 세트를 새 역할 전체로 갱신(기존 운영자 = 지금과 같은 업무 범위) · 시스템 프리셋 «SCM 조회» 등.
- 시험: 각 서비스 컨트롤러 슬라이스 · 통합.

## Out of Scope

- 관리 등급(ADMIN) SCM 역할 신설(ADR-083 D2 — 별도 설계).
- SCM 데이터 범위(조직 · `org_scope`) — 지금 없음, 이 ADR 밖.

# Acceptance Criteria

- [ ] **AC-0** — 재측정: SCM 4 서비스의 판정 · 컨트롤러 전수(엔드포인트 × 조회/변경) · procurement 상태 기계 · `ActorContext.isOperator()` 의 역할 집합 · 업무 구분안을 소유자 OD-6 기본값과 대조(바꿀 점이 있으면 착수 전 소유자에게).
- [ ] **AC-1** — 🔴 대조군(실패 쪽 먼저): 수요계획 세트만 → 발주 변경 API 403 · 수요계획 200 / 조회 세트 → GET 200 · 변경 403 / `SCM_OPERATOR` 만(업무 역할 0) → 업무 API 403.
- [ ] **AC-2** — 이전 호환: 백필된 기존 운영자가 이 PR 전후로 같은 API 집합에 200(대조군).
- [ ] **AC-3** — WMS 세트만 가진 운영자의 SCM 직접 API 403 · SCM 세트만 가진 운영자의 WMS 직접 API 403.
- [ ] **AC-4** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다 · 라이브 없으면 ⚪ 와 이유.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3 · § 도메인 업무 구분 제안
- `projects/scm-platform/PROJECT.md` · `specs/services/*/architecture.md`

# Related Contracts

- `projects/scm-platform/specs/contracts/http/*` · `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그

# Edge Cases

- 공급사 웹훅(HMAC, JWT 없음) — 역할 판정 대상 아님, 무변경.
- `/internal/**`(네트워크 신뢰) — 무변경.

# Failure Scenarios

1. 게이트웨이에만 역할 판정을 넣는다 — 서비스 직접 호출이 우회한다.
2. 카탈로그만 넓히고 서비스 판정을 안 넣는다 — 세트 경계가 화면에만 있다(ADR-083 § 새로 생기는 위험 1).
3. 이전 호환 세트 갱신을 다른 PR 로 — 기존 운영자 업무 중단.
