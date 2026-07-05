# Task ID

TASK-PC-FE-195

# Title

운영자 관리 — 테넌트 배정 vs cross-org 파트너십 disambiguation 노트 + `/partnerships` 교차 링크 (PC-FE-187 파트너십 신설 반영, intra-org 배정과 혼동 방지)

# Status

review

# Owner

frontend

# Task Tags

- frontend
- platform-console
- ux

---

# Goal

PC-FE-187 로 **cross-org 파트너십**(다른 회사가 자기 테넌트를 운영하며 이 테넌트의 bounded slice 를 맡음) 화면이 신설되면서, 운영자 관리 화면의 **테넌트 배정**(intra-org — 운영자를 이 테넌트의 운영자로 편입) 과 **혼동 지점**이 생겼다. 특히 `AssignOperatorForm` 의 현재 카피가 "직원·협력업체 온보딩" 으로 두 개념을 **conflate** 한다("협력업체" 라는 표현이 있으나 실제로는 intra-org 배정 경로다). 운영자 화면에서 두 경로를 명확히 구분하고 파트너십 화면으로 교차 링크한다.

**(B) 미채택·백로그**: 운영자별 cross-org reach 가시화(운영자 행에 참여 파트너십·host reach 표시)는 신규 백엔드(operator 기준 파트너십 조회 엔드포인트) 필요 → 이 task 범위 밖, 백로그 후보로 남김.

# Scope

## In scope

- `src/features/operators/components/AssignOperatorForm.tsx`:
  - 설명 카피를 **intra-org 정확화**: "이 배정 = 운영자를 **이 테넌트의 운영자로 편입**(같은 조직 내 위임 온보딩)". "협력업체" 표현의 conflation 정리.
  - **disambiguation 노트 + 링크**: "다른 회사(협력사) 조직이 **자기 테넌트를 운영하며** 이 테넌트의 일부(도메인·역할 slice)를 맡게 하려면 → **파트너십** 화면" + `next/link` `<Link href="/partnerships">` (콘솔 실제 라우트). 관계-단위 오프보딩(파트너십 종료·participant 해제 시 접근 cascade 소멸) 한 줄 힌트.
- 테스트: `tests/unit/` 에 assign-form 노트/링크 존재 단언(기존 operators 테스트 스타일 미러) — 링크 `href="/partnerships"` + disambiguation 문구 렌더 확인. (신규 스위트 or 기존 operators UI 테스트 확장.)

## Out of scope

- 신규 백엔드·계약·API 변경 0 (순수 FE 카피+링크).
- **(B) 운영자별 cross-org reach 가시화** — operator 기준 파트너십/participation 조회 엔드포인트 신설 필요 → 별도 BE+FE task, **백로그 후보**(수요/여력 시).
- 파트너십 화면(PC-FE-187) 자체 변경.
- 토큰 모델 가이드(BE-481, repo 개발자 문서)를 콘솔 UI 에서 링크 — 부적절(콘솔 독자=운영자, repo md 아님). UI 링크는 `/partnerships` 실제 라우트만.
- 사이드바 nav 변경(파트너십 leaf 는 PC-FE-187 에서 이미 추가됨).

# Acceptance Criteria

- [ ] **AC-1** `AssignOperatorForm` 설명이 intra-org(이 테넌트 운영자 편입)로 정확화되고, "협력업체" conflation 이 정리됨.
- [ ] **AC-2** disambiguation 노트 렌더 — cross-org 협력사 파트너 운영은 파트너십 화면으로 안내 + `next/link` 로 `/partnerships` 링크(콘솔 실제 라우트). 관계-단위 오프보딩 힌트 포함.
- [ ] **AC-3** 노트는 assign form 컨텍스트(activeTenant 있을 때 표시되는 곳)에 위치 — 배정 액션 옆이라 혼동 방지에 유효.
- [ ] **AC-4** 테스트: 노트 문구 + `/partnerships` 링크 존재 단언. 기존 operators UI/nav 테스트 회귀 0.
- [ ] **AC-5** `pnpm lint` + `pnpm exec tsc --noEmit` + `pnpm vitest`(신규/영향 스위트) green. 회귀 0(Windows OperatorsScreen fetchMock flake 는 pre-existing — CI Linux 권위).
- [ ] **AC-6** 신규 백엔드 0. 순수 FE. CI 프런트 잡(vitest/E2E-smoke) green.

# Related Specs

- `docs/adr/ADR-MONO-045-cross-org-partner-delegation.md` (cross-org 파트너십 — 이 노트가 안내하는 개념), `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (intra-org 위임 — 테넌트 배정의 근거).
- `projects/platform-console/specs/services/console-web/architecture.md` (partnerships·operators feature 레지스트리 — BE-480 에서 갱신됨).

# Related Contracts

- 변경 없음(FE 카피+링크).

# Edge Cases

- `/partnerships` 라우트는 PC-FE-187 로 존재(operator token 게이트). 링크만 — 접근 권한(파트너십.manage)은 그 화면이 자체 게이트.
- assign form 은 activeTenant 있을 때만 표시(기존 게이팅) → 노트도 그 컨텍스트. 테넌트 미선택 시엔 배정 자체가 안 보이므로 혼동 없음.

# Failure Scenarios

- 순수 FE 카피/링크라 런타임 실패 표면 없음. 링크 깨짐만 주의(`/partnerships` 실재 — PC-FE-187).
- 기존 assign-form 테스트가 카피 문자열에 의존하면 갱신 필요.
