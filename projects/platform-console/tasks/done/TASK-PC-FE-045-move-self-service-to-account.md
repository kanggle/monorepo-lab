# Task ID

TASK-PC-FE-045

# Title

Move **셀프서비스(내 비밀번호 변경 + 내 프로필)를 운영자 관리(`/operators`) → 계정 설정(`/account`)** 으로 이전. 운영자 관리는 *남 관리(생성/역할/상태/타 운영자 프로필)* 전용으로 정리하고, 계정 설정은 읽기 전용 → *내 셀프서비스* 화면으로 승격. API/엔드포인트(`me/password`·`me/profile`) 불변.

# Status

done

# Owner

frontend-engineer (console-web only — project-internal)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **follows**: TASK-PC-FE-041(계정 메뉴 ⋮ → 아이디/계정 설정/로그아웃 + 읽기 전용 `/account` 페이지 신설), TASK-PC-FE-004(운영자 관리 화면 — 셀프서비스 폼을 여기에 처음 얹음).
- **root cause (UX 일관성)**: 셀프서비스 변경 엔드포인트가 operator 네임스페이스(`/api/admin/operators/me/*`)에 있어 PC-FE-004 가 `OperatorsScreen` 에 내 비밀번호·내 프로필 폼을 *남 관리*와 함께 얹음. PC-FE-041 이 계정 설정 페이지를 만들었으나 읽기 전용 + 운영자 관리로 *역방향 링크*만 둠 → "계정 설정 메뉴인데 비밀번호는 운영자 관리에서" 라는 역설. AWS/GCP 식 분리(계정 설정=내 것, IAM/Users=남 관리)와 어긋남.
- **decision (user, 2026-06-04)**: 진행 — 셀프서비스를 계정 설정으로 이동.
- **불변**: `me/password`·`me/profile` 프록시/라우트/contract/엔드포인트, admin-set-**타** 운영자 프로필(`useSetOperatorProfile` 의 per-row "프로파일 편집" 다이얼로그 = 남 관리)은 운영자 관리에 잔류.

# Goal

⋮ → 계정 설정(`/account`)에서 **내 비밀번호 변경 + 내 기본 finance 계정(프로필)** 을 직접 수행한다. 운영자 관리(`/operators`)에는 더 이상 내 비밀번호/내 프로필 폼이 없고 *남 관리* 기능(생성·역할·상태·타 운영자 프로필 편집·목록)만 남는다.

# Scope

## In Scope

- 신규 client 컴포넌트 `features/operators/components/AccountSelfService.tsx` — `useChangeOwnPassword` + `useUpdateOwnProfile` 훅 + 에러 매핑 + `ChangePasswordForm` + `MyProfileForm` 렌더(현 OperatorsScreen 에서 추출). prop: `initialDefaultAccountId`.
- `app/(console)/account/page.tsx` — 읽기 전용 → 셀프서비스 승격: `getCatalog()` 로 finance `operatorContext.defaultAccountId` 산출(operators page 에서 이전) + `<AccountSelfService initialDefaultAccountId=… />` 렌더 + 기존 "프로필 편집 → 운영자 관리로 이동" 역방향 블록 제거. 신원 read-only dl 은 유지.
- `app/(console)/operators/page.tsx` — `initialDefaultAccountId` 산출/전달 제거(`getCatalog` 는 `tenantOptions`/`isPlatformOperator` 위해 유지). `OperatorsScreen` 에 `initialDefaultAccountId` prop 미전달.
- `OperatorsScreen.tsx` — `ChangePasswordForm`/`MyProfileForm` 렌더 + `useChangeOwnPassword`/`useUpdateOwnProfile`/`pwError`/`updateProfileError`/`initialDefaultAccountId` prop 제거. create/roles/status/타-프로필-다이얼로그(`useSetOperatorProfile`)/목록은 유지.
- 테스트: `OperatorsScreen.test.tsx`(셀프 폼 부재 단언으로 갱신) + 신규 account 셀프서비스 테스트(폼 존재 + onSubmit 배선) + e2e `operators-profile.spec.ts` 가 **self** 프로필을 보면 `/account` 로 타깃 이전.

## Out of Scope

- API/프록시/contract/엔드포인트 변경(전부 불변).
- `ChangePasswordForm`/`MyProfileForm` 컴포넌트 내부(이동만, 수정 없음).
- admin-set-타-운영자 프로필(남 관리 — 운영자 관리 잔류).

# Acceptance Criteria

- [x] **AC-1** `/account`(계정 설정)에서 내 비밀번호 변경 + 내 기본 finance 계정 변경이 동작(폼 렌더 + `me/password`·`me/profile` 호출).
- [x] **AC-2** `/operators`(운영자 관리)에 내 비밀번호/내 프로필 폼이 더 이상 없음(남 관리 기능만 잔류; 타-운영자 프로필 편집 다이얼로그는 유지).
- [x] **AC-3** `/account` 의 역방향 "운영자 관리로 이동" 셀프서비스 안내 블록 제거(신원 read-only dl 유지).
- [x] **AC-4** console `pnpm test` GREEN(OperatorsScreen 셀프-폼 단언 갱신 + account 신규), `tsc`/`lint`/`build` GREEN.

# Related Specs

- `console-integration-contract.md` § 2.4.3(operators self-service `me/password`·`me/profile`). ADR-MONO-013/015(console UI). PC-FE-041(account page).

# Edge Cases

- 활성 테넌트 미선택 시에도 계정 설정의 신원/비밀번호 변경은 동작(claims/`me/*` 는 테넌트 무관). 단 finance 기본계정 초기값은 `getCatalog`(operator-scoped)에 의존 — registry 실패 시 빈 입력 + active set 흐름(기존 동작 유지).
- 타 운영자 프로필 편집(per-row)은 운영자 관리에 그대로 — self 와 혼동 없음.

# Failure Scenarios

- account 페이지가 client 훅을 직접 못 씀(서버 컴포넌트) → `AccountSelfService` client 래퍼로 격리(`'use client'`). 누락 시 빌드 에러로 표면화.

# Test Requirements

- `OperatorsScreen.test.tsx`: 셀프 폼 부재 단언으로 갱신(create/list/roles/status/타-프로필 다이얼로그 유지 단언 보존). account 셀프서비스 단위 테스트(ChangePasswordForm/MyProfileForm 렌더 + 호출). `pnpm test` + `tsc` + `lint` + `build`.
- e2e `operators-profile.spec.ts`: self 프로필 시나리오를 `/account` 로 이전(남 관리 시나리오는 `/operators` 유지).
- Local: console-web 재빌드+재기동; ⋮ → 계정 설정에서 비밀번호/프로필 변경 동작 + 운영자 관리에서 셀프 폼 사라짐 확인.

# Definition of Done

- [x] AccountSelfService 신규 + account page 승격 + operators page/OperatorsScreen 정리.
- [x] console `pnpm test`/`tsc`/`lint`/`build` GREEN.
- [x] Local 재빌드+재기동; 계정 설정 셀프서비스 동작 + 운영자 관리 셀프 폼 부재 확인.
- [x] Task md + `projects/platform-console/tasks/INDEX.md` 갱신.
- [x] Reviewed + merged (impl PR #1077 squash `092ee931`, 3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 "운영자관리에 내 비밀번호변경이 있는 이유는?"(2026-06-04) → UX 일관성 정돈. 메타: 셀프서비스(내 것)와 관리(남 것)는 분리된 화면이어야(AWS/GCP 패턴) — 엔드포인트 네임스페이스(operator/me/*)가 UI 배치를 잘못 유도했던 케이스. API 불변, UI routing-hierarchy only.
