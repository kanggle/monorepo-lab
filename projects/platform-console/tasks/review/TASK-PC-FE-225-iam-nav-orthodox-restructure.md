# Task ID

TASK-PC-FE-225

# Title

IAM nav 정석 재편성 (IAM 평면 / 고객-신원 평면 분리)

# Status

review

# Owner

frontend

# Task Tags

- code

---

# Goal

콘솔 사이드바를 **정석(orthodox) IAM taxonomy**로 재편성한다 — AWS IAM/GCP Cloud IAM처럼 **워크포스 평면(IAM)**과 **고객-신원 평면(Cognito/Identity Platform 대응)**을 분리한다.

완료 후 참이 되어야 하는 것: `IAM` 그룹은 **테넌트·운영자·운영자 그룹·권한·권한 세트**(+ 기존 개요/가이드/감사) 만을 담고, 소비자 「계정」(`accounts`)은 IAM 그룹에서 빠져 **신규 「고객 신원」 그룹**으로 이동한다. 신규 메뉴 4개(테넌트/운영자 그룹/권한/권한 세트)는 이 태스크에서 **스텁 라우트**만 생성하고, 각 화면의 실제 기능은 후속 태스크(TASK-PC-FE-226, TASK-PC-FE-227, TASK-PC-FE-228, ADR-MONO-046 실행 로드맵)가 채운다. **백엔드·기존 「계정」 route는 무변경**(위치만 이동).

---

# Scope

## In Scope

- `src/shared/ui/ConsoleSidebarNav.tsx`의 `GROUPS` 배열 재편성.
  - `IAM` 그룹에 4개 항목 추가: `테넌트`(`/tenants`), `운영자 그룹`(`/operator-groups`), `권한`(`/permissions`), `권한 세트`(`/permission-sets`). 기존 `운영자`(`/operators`) + 개요/가이드/감사는 그대로 유지.
  - `계정`(`/accounts`)을 `IAM` 그룹에서 **신규 「고객 신원」 그룹**으로 이동(신규 그룹 생성, `testid` 부여). route(`/accounts`)와 기능은 무변경 — nav 상 위치만 변경.
- 신규 스텁 페이지 4개: `src/app/(console)/tenants/page.tsx`, `src/app/(console)/operator-groups/page.tsx`, `src/app/(console)/permissions/page.tsx`, `src/app/(console)/permission-sets/page.tsx`. 각 페이지는 "구현 예정" 플레이스홀더(200 응답, 최소 레이아웃)만 렌더 — 실 데이터 연동은 후속 태스크.
- 각 신규 nav 항목/그룹에 안정적 `testid` 부여(기존 nav 게이팅 관례 — 권한 없는 메뉴 숨김/차단 패턴 답습).

## Out of Scope

- 4개 신규 화면(테넌트/운영자 그룹/권한/권한 세트)의 실제 목록·상세·CRUD 기능 — 각각 TASK-PC-FE-226(테넌트), TASK-PC-FE-227(권한), TASK-PC-FE-228(권한 세트), ADR-MONO-046 실행 로드맵(운영자 그룹)에서 구현.
- 「계정」 화면 자체의 기능·API·라우트 변경 — nav 배치만 이동, 화면·프록시·api 클라이언트는 완전 동일.
- 「고객 신원」 그룹의 리브랜딩(계정 화면 라벨·문구 변경 등) — v1은 nav 재배치만, 화면 내부 리브랜딩은 후속 스코프.
- 백엔드 변경 일체.

---

# Acceptance Criteria

- [ ] `IAM` 그룹 메뉴 구성 = 개요 / 가이드 / 운영자 / 운영자 그룹 / 테넌트 / 권한 / 권한 세트 / 감사(순서는 구현 시 확정, 기존 개요/가이드가 최상단 관례 유지).
- [ ] `계정`(`/accounts`)이 `IAM` 그룹에서 빠지고 신규 「고객 신원」 그룹에 위치.
- [ ] 신규 4개 메뉴 항목 + 신규 「고객 신원」 그룹 각각에 고유 `testid` 부여.
- [ ] 신규 스텁 라우트(`/tenants`, `/operator-groups`, `/permissions`, `/permission-sets`) 4개 모두 200 응답.
- [ ] 「계정」 화면의 기존 기능(목록/상세/필터 등)에 회귀 없음 — nav 위치만 변경되었음을 기존 vitest 스위트 통과로 확인.
- [ ] `pnpm lint` + `tsc --noEmit` + `vitest` 전부 GREEN (`[[env_console_web_local_verify_needs_lint]]` — lint 누락 시 no-unused-vars가 CI에서만 적발됨에 주의).

---

# Related Specs

없음 (FE nav 재편성 — 신규/변경 대상 스펙 없음. 4개 신규 메뉴의 실 기능 스펙은 각 후속 태스크가 별도로 식별).

# Related Contracts

없음 (신규 스텁 라우트는 API를 소비하지 않음. 후속 태스크가 각 실제 계약을 식별).

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- 이 태스크는 **콘솔 표면 배치(nav taxonomy)만** 다룬다. 정석 매핑 근거: `IAM`(워크포스 평면) = 테넌트(격리 경계, AWS 계정/GCP 프로젝트 대응) · 운영자(워크포스 신원, IAM User/IdC User 대응) · 운영자 그룹(IAM User Group/Google Group 대응, ADR-MONO-046 참고) · 권한(Action/Permission) · 권한 세트(IAM Policy/Role). 「고객 신원」(B2C 소비자 평면) = 계정(AWS Cognito/GCP Identity Platform 대응).
- 신규 스텁 페이지는 실 데이터·API 호출을 포함하지 않는다 — 후속 태스크가 각 화면을 채울 때 스텁을 대체한다. 스텁 부재로 인한 404를 이 태스크의 AC 실패로 취급한다(라우트 파일 누락 검증).
- 기존 nav 게이팅 관례(운영자 role/권한 기반 메뉴 노출·차단)를 신규 항목에도 동일 적용 — 신규 화면이 아직 기능을 갖추지 않았어도 게이팅 로직 자체는 이 태스크에서 배선.
- 「고객 신원」 그룹 표시 권한은 기존 계정 조회 권한 보유자 기준 그대로(신규 권한 키 도입 없음).

---

# Edge Cases

- 권한 없는 운영자에게 신규 IAM 메뉴 항목(테넌트/운영자 그룹/권한/권한 세트)이 노출되거나 차단되는 경우 — 기존 nav 게이팅 관례를 그대로 따름(신규 권한 키 도입 아님, 화면별 실제 게이팅은 각 후속 태스크에서 확정).
- 「고객 신원」 그룹 자체의 표시 여부 — 계정 조회 권한 보유자만 노출(기존 계정 화면 게이팅과 동일 기준).
- 딥링크(`/accounts` 직접 진입)가 nav 그룹 이동 후에도 정상 하이라이트되는지(그룹 소속 변경이 `parentKeyForPath`/`matchesRoute` 로직에 영향 없는지 확인).

---

# Failure Scenarios

- 신규 스텁 라우트 파일 누락 시 404 — 4개 페이지 파일 존재 여부를 AC로 명시 검증.
- 계정 route 이동 작업 중 실수로 route path 자체를 변경 — 기존 딥링크(`/accounts`)가 깨지지 않는지 반드시 확인(그룹 소속만 이동, path 불변).
- nav 배열 재편성 중 기존 `운영자`/`개요`/`가이드`/`감사` 항목 좌표(순서·testid)를 훼손 — 기존 vitest nav 테스트 회귀로 적발.

---

# Test Requirements

- component test: `ConsoleSidebarNav` 그룹 구성(IAM 8개 항목 + 고객 신원 1개 항목) 검증.
- page/flow test: 신규 스텁 라우트 4개 진입 시 200 + 플레이스홀더 렌더 확인.
- 회귀: 기존 계정 화면 vitest 스위트(목록/상세/필터) GREEN 유지.

---

# Definition of Done

- [ ] `ConsoleSidebarNav.tsx` `GROUPS` 재편성 완료(IAM 8항목 + 고객 신원 1항목)
- [ ] 신규 스텁 페이지 4개 구현
- [ ] 계정 화면 기능 회귀 없음 확인
- [ ] 테스트 추가 및 통과
- [ ] `pnpm lint` + `tsc` + `vitest` GREEN
- [ ] Ready for review
