# Task ID

TASK-MONO-794

# Title

`ADR-MONO-083` P7 — Finance 세분 서버 인가: 계좌 · 원장의 조회/변경 역할 · `FINANCE_OPERATOR` · 일반 `OPERATOR/ADMIN` 은 진입 역할로 · iam 카탈로그 확장

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

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (필터 체인 권한 · 원장 고위험 쓰기 · 원자 PR)
> 🔴 **cross-project 원자 PR**: finance 인가 + iam 카탈로그 · «이전 호환» Finance 세트 갱신.

# Dependency Markers

- **선행**: `TASK-BE-631` · `TASK-BE-632`.
- 형제: `TASK-MONO-792` · `793` · `795`.

# Goal

Finance 쓰기는 `SCOPE_finance.write ∨ OPERATOR/ADMIN/SUPER_ADMIN/FINANCE_OPERATOR` 하나로 열리고(`account-service …/SecurityConfig.java:116-124` · `ledger-service …/SecurityConfig.java:118-126`), 원장은 분개 · 환율 덮어쓰기 · 기간 마감 · 대사 해소가 같은 권한이다. 계좌 · 원장을 조회/변경으로 나눈다.

# Scope

## In Scope

- 계약 먼저: finance 서비스 명세 · 계약(🔵 이번 조사에서 finance 명세를 열지 않았다 — AC-0).
- 두 서비스 필터 체인 · 앱 단 판정 갱신. account 의 KYC · 충전 «운영자 전용» 앱 단 검사는 새 역할 축에 맞춰 유지.
- 🔴 원장의 기간 마감 · 환율 덮어쓰기: «원장 변경» 에 포함할지, 관리 등급으로 떼어 **지금처럼 운영자에게 줄지 · 아무에게도 안 줄지** — ADR-083 D2(지금 발급 안 하는 등급은 자동 부여 금지)에 따라 AC-0 에서 측정 후 소유자 결정으로 올린다.
- iam 카탈로그 · «이전 호환» Finance 세트 · 시스템 프리셋.
- `SCOPE_finance.write` — 콘솔 클라이언트에 등록되지 않았음을 재확인하고, 등록 시 같은 우회가 된다는 가드(시험 또는 명세 문장)를 둔다.

## Out of Scope

- Finance 데이터 범위(테넌트 행 격리만 — 무변경).

# Acceptance Criteria

- [ ] **AC-0** — 재측정: finance 명세 · 코드 판정 대조 · 원장 고위험 쓰기 목록 · 업무 구분안(OD-6) · 원장 고위험 쓰기 처리 소유자 결정.
- [ ] **AC-1** — 🔴 대조군: 계좌 세트만 → 원장 변경 403 · 계좌 200 / 조회 세트 → 변경 403 / 진입 역할만 → 업무 변경 403.
- [ ] **AC-2** — 이전 호환 대조군(기존 운영자 같은 API 집합 200).
- [ ] **AC-3** — 배포 상태를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D2 · D3 · § 도메인 업무 구분 제안(Finance 행)
- `projects/finance-platform/specs/services/*/architecture.md`

# Related Contracts

- `projects/finance-platform/specs/contracts/http/*` · iam `admin-api.md` § 카탈로그

# Edge Cases

- `FINANCE_VIEWER`(구독 합성 READ) — 세분 조회 세트를 무력화하는지 AC-0 측정(ERP 와 같은 질문).
- 와일드카드 GET 전용 권한 — 무변경.

# Failure Scenarios

1. 일반 `OPERATOR` / `ADMIN` 역할 문자열을 쓰기 권한에 남긴다 — 다른 경로로 그 문자열을 받은 토큰이 전부 연다.
2. 원장 고위험 쓰기를 소유자 결정 없이 «원장 변경» 세트에 넣는다 — 관리 등급의 조용한 부여.
