# Task ID

TASK-MONO-751

# Title

`ADR-MONO-079` D4-A — **콘솔 팬 화면**: 소속사 · 아티스트 · 그룹 관리 + BFF 라우트

# Status

ready

# Owner

monorepo

# Task Tags

- platform-console
- frontend

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (이커머스 상품·셀러 화면의 선례를 따르는 화면 작업)

---

# Dependency Markers

- **선행**: `TASK-MONO-748`(소속사 API) · `TASK-MONO-750`(운영자 관리 경로)

# Goal

콘솔에 팬 도메인 화면을 만든다 — 소속사 목록·생성·편집(셀러 연결 포함), 아티스트·그룹 목록·편집(소속 변경). 모양은 기존 `ecommerce/products/**` · `ecommerce/sellers/**` 화면과 BFF 프록시를 따른다.

# Scope

## In Scope

- console-web 라우트 · 기능 폴더 · BFF 라우트 · 콘솔 레지스트리의 `fan` 항목
- 단위 시험 · e2e(🔴 콘솔 전체 e2e 는 nightly 에서만 돈다 — 머지 뒤 다음 nightly 확인)

## Out of Scope

- 팬 커뮤니티 관리(대리 저작 금지 — 059)

# Acceptance Criteria

- [ ] **AC-1** — 플랫폼 운영자가 `fan-platform` 으로 전환해 소속사를 만들고 아티스트를 소속시킬 수 있다.
- [ ] **AC-2** — 소속사에 셀러를 연결하는 입력이 존재하는 셀러만 받는다(748 의 검증 오류를 화면에 표시).
- [ ] **AC-3** — 고객사 운영자에게 `fan` 항목이 보이지 않는다(R3).
- [ ] **AC-4** — 머지 뒤 다음 nightly e2e 결과 확인.

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D4-A
- `projects/platform-console/specs/`

# Related Contracts

- console-bff ↔ fan gateway(관리 경로)

# Edge Cases

- 소속사 삭제 시 소속 아티스트가 있으면 거절 또는 보관 — 748 의 규칙을 따른다.

# Failure Scenarios

1. 화면은 열리는데 전환 전 API 호출로 403 — 콘솔의 «전환 뒤에 묻는다» 규칙.
