# Task ID

TASK-PC-FE-327

# Title

콘솔 가이드 «권한 및 테스트 계정 › 역할별 권한» 표의 역할 열 머리에 역할 이름과 함께 **역할의 성격**(플랫폼 전체 관리자 · CS 1선 · CS 2선 · 보안팀 …)을 적는다

# Status

review

# Owner

platform-console

# Task Tags

- platform-console
- guide

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 정적 문구 한 벌 + 표 머리 렌더 변경.

---

# Dependency Markers

- 출처: 2026-10-09 UTC 소유자 대화 — «역할 뿐만 아니라 플랫폼 전체 관리자, CS 1선, CS 2선, 보안팀 처럼 각 역할에 성격도 적도록 수정».
- 관련: `TASK-PC-FE-298`(done — 전역 가이드 · 역할별 권한 표 신설) · `TASK-PC-FE-322`(done — 전역 가이드 쉬운 말 재작성; 화면 문구에 파일 경로 · 티켓 번호 금지 가드).
- 같은 PR 에 `TASK-PC-FE-325` close 동반(review → done, 소유자 데모 확인).

# Goal

역할별 권한 표의 열 머리가 `SUPER_ADMIN` · `SUPPORT_LOCK` 같은 코드 이름뿐이라, 읽는 사람이 «이게 누구 역할인가»를 알 수 없다. 각 열 머리에 코드 이름 아래 한 마디 성격을 붙인다.

| 역할 | 성격 |
|---|---|
| `SUPER_ADMIN` | 플랫폼 전체 관리자 |
| `SUPPORT_READONLY` | CS 1선 (조회만) |
| `SUPPORT_LOCK` | CS 2선 (계정 제어) |
| `SECURITY_ANALYST` | 보안팀 |
| `TENANT_ADMIN` | 고객사 관리자 |
| `TENANT_BILLING_ADMIN` | 고객사 구독 담당 |
| `ORG_ADMIN` | 고객사 조직(본사) 관리자 |

근거 = `rbac.md` § Seed Roles 의 «의도» 열(CS L1 · CS L2 · 보안팀 · 테넌트-scoped 위임관리자 · entitlement 관리자 · org-node-scoped company-wide 위임관리자).

# Scope

## In Scope

- `shared/guide/permission-map.ts` 에 `RBAC_ROLE_NATURE: Record<RbacRole, string>` — 역할 원장 사본(`RBAC_ROLES`) 바로 옆. `Record<RbacRole, …>` 라 역할이 늘면 채우기 전엔 타입 오류.
- `GlobalGuideScreen` 역할별 권한 표 열 머리 = 코드 이름 + 성격 두 줄.
- 시험: 모든 역할 열 머리가 이름과 성격을 함께 보인다 · 플랫폼 역할 넷은 소유자 문구(플랫폼 전체 관리자 · CS 1선 · CS 2선 · 보안팀)를 포함.

## Out of Scope

- IAM 가이드(`/iam/guide`)의 역할 카드 `koName`(«CS 상담원», «테넌트 위임관리자» 등) — 다른 화면 · 다른 문구 체계. 맞출지는 별도 판단.
- 역할 · 권한 자체(RBAC 시드) 변경.

# Acceptance Criteria

- **AC-1** ✅ 역할별 권한 표의 일곱 열 머리가 각각 역할 코드 + 성격을 보인다(`global-guide-rbac-role-<ROLE>` testid).
- **AC-2** ✅ 성격 문구의 단일 원장 = `RBAC_ROLE_NATURE`(타입이 일곱 역할 전부를 강제).
- **AC-3** ✅ bite: 화면에서 성격 줄 렌더를 지우면 새 시험 **정확히 1개** 빨강(14개 중 13 통과) → 복원.
- **AC-4** ✅ `tsc --noEmit` rc=0 · `pnpm lint` rc=0 · 전체 vitest 363 파일 / 4,122 시험 rc=0(쉬운 말 가드 — 화면 문구에 경로 · 티켓 번호 없음 — 포함 통과).
- **AC-5** ⚪ 라이브 데모 화면 확인 — 다음 데모 창.

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` § Seed Roles(:97-109) — 성격 문구의 근거.

# Related Contracts

- 없음 (정적 가이드 화면).

# Edge Cases

- 좁은 화면: 표는 이미 `overflow-x-auto` 컨테이너 안이라 열 머리가 두 줄이 돼도 가로 스크롤로 읽힌다.
- 스크린 리더: 성격은 같은 `<th>` 안의 텍스트라 열 머리 이름에 함께 읽힌다(«SUPPORT_LOCK CS 2선 (계정 제어)»).

# Failure Scenarios

- 역할이 추가되었는데 성격이 빠지는 경우 → `Record<RbacRole, string>` 타입 오류로 빌드가 막힌다.
- 성격 문구가 화면 쉬운 말 가드(파일 경로 · 티켓 번호 금지)에 걸리는 경우 → 전체 vitest 에 포함된 `GlobalGuideScreen` 가드가 잡는다(통과 확인).
