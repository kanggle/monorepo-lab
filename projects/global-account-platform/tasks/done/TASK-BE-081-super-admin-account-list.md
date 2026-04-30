# Task ID

TASK-BE-081

# Title

feat: SUPER_ADMIN 전체 계정 목록 조회 (페이지네이션 포함)

# Status

ready

# Owner

fullstack

# Task Tags

- feature
- admin
- rbac
- frontend

# depends_on

없음

---

# Goal

`SUPER_ADMIN` 역할을 가진 운영자가 계정 검색 페이지 진입 시 이메일 입력 없이 전체 계정 목록을 페이지네이션 표 형태로 볼 수 있도록 구현한다.

현재 `GET /api/admin/accounts?email=xxx` 엔드포인트는 이메일이 없으면 빈 배열을 반환한다. 이 태스크는 `SUPER_ADMIN`에 한해 이메일 파라미터 없이 호출 시 전체 계정 목록(페이지)을 반환하도록 확장한다.

---

# Scope

## In Scope

### A. 스펙 및 컨트랙트 업데이트

- `specs/services/admin-service/rbac.md`:
  - `account.read` 권한 키를 Seed Matrix에 추가 (`SUPER_ADMIN` ✅, `SUPPORT_READONLY` ✅, 나머지 ❌)
  - 이미 `Permission.ACCOUNT_READ = "account.read"` 코드에 존재하나 스펙에 누락됨
- `specs/contracts/http/admin-api.md`:
  - Permission key catalog에 `account.read` 추가
  - `GET /api/admin/accounts` 섹션에 email 없는 경우 동작 (SUPER_ADMIN 전체 목록, 페이지네이션) 명시
  - 응답 스키마에 `page`, `size`, `totalPages` 필드 추가
- `specs/contracts/http/internal/admin-to-account.md`:
  - `GET /internal/accounts` (email 없음) → 전체 계정 페이지네이션 응답 명시

### B. account-service 백엔드

- `AccountSearchController.search()`:
  - `email` 파라미터가 없을 경우 → `Pageable`로 전체 계정 목록 반환
  - `page` (default 0), `size` (default 20, max 100) 파라미터 추가
  - 응답: `AccountSearchResponse(content, totalElements)` 유지, `page`/`size`/`totalPages` 추가
- `AccountSearchResponse` DTO에 `page`, `size`, `totalPages` 필드 추가

### C. admin-service 백엔드

- `AccountServiceClient`:
  - `search(String email)` → email이 null/blank이면 `listAll(int page, int size)` 경로로 분기, 또는 기존 메서드 시그니처 변경
  - 또는 별도 `listAll(int page, int size)` 메서드 추가
- `AccountAdminController.search()`:
  - email 없음 + `account.read` 권한 보유 시 → `accountServiceClient.listAll(page, size)` 호출
  - email 없음 + `account.read` 권한 없음 → 기존과 동일하게 빈 배열 반환
  - `page`, `size` 쿼리 파라미터 추가 (기존 동작에 영향 없음)
- `AccountServiceClient.AccountSearchResponse`에 `page`, `size`, `totalPages` 필드 추가
- admin-service Flyway 마이그레이션:
  - 새 파일 `V0011__seed_account_read_permission.sql`: `SUPER_ADMIN`, `SUPPORT_READONLY`에 `account.read` INSERT IGNORE

### D. 프론트엔드

- `AccountsPage` (서버 컴포넌트):
  - `requireOperatorSession`으로 세션 조회 후 `isSuperAdmin` 여부를 `AccountSearchForm`에 prop으로 전달
- `useAccountList` 훅 신규 작성:
  - `GET /api/admin/accounts?page=N&size=20` 호출 (email 없음)
  - `enabled` 조건: `isSuperAdmin === true`
  - 응답 스키마: `content`, `totalElements`, `page`, `size`, `totalPages`
- `AccountSearchForm`:
  - `isSuperAdmin?: boolean` prop 추가
  - `isSuperAdmin === true`이면 페이지 진입 시 `useAccountList`로 전체 목록 표시
  - 이메일 입력 후 검색하면 기존 `useAccountSearch` 동작으로 전환
  - 페이지네이션 UI: 이전/다음 버튼 (totalPages 기반)

## Out of Scope

- 정렬(sort) 기능
- 계정 상태 필터
- SUPER_ADMIN 외 다른 역할의 전체 목록 조회
- 실시간 검색(debounce)

---

# Acceptance Criteria

- [ ] `SUPER_ADMIN` 계정으로 `/accounts` 진입 시 이메일 입력 없이 전체 계정 목록이 표로 표시됨
- [ ] 표에 ID, 이메일, 상태, 가입일, 상세 링크 컬럼이 표시됨
- [ ] 페이지네이션: 이전/다음 버튼으로 이동 가능
- [ ] `SUPER_ADMIN`이 이메일 입력 후 검색하면 검색 결과로 전환됨
- [ ] `SUPER_ADMIN`이 아닌 역할은 이메일 입력 없이 검색하면 빈 화면 유지 (기존 동작 보존)
- [ ] `rbac.md` Seed Matrix에 `account.read` 추가됨
- [ ] `admin-api.md` permission key catalog에 `account.read` 추가됨
- [ ] `V0011__seed_account_read_permission.sql` Flyway 마이그레이션 적용됨
- [ ] `GET /internal/accounts` (email 없음) → 페이지네이션 응답 반환
- [ ] `GET /api/admin/accounts` (email 없음, SUPER_ADMIN) → 전체 목록 페이지 반환

---

# Related Specs

- `specs/features/admin-operations.md`
- `specs/services/admin-service/rbac.md`

---

# Related Contracts

- `specs/contracts/http/admin-api.md` — `GET /api/admin/accounts` (변경)
- `specs/contracts/http/internal/admin-to-account.md` — `GET /internal/accounts` (변경)

---

# Target Service

- `apps/account-service`
- `apps/admin-service`
- `apps/admin-web`

---

# Architecture

기존 `AccountSearchController` → `AccountAdminController` → `AccountServiceClient` 경로 재사용.
`email` 파라미터 유무로 분기: 없으면 `findAll(Pageable)`, 있으면 기존 `findByEmail`.
SUPER_ADMIN 여부 판단은 서버 컴포넌트(`AccountsPage`)에서 세션 roles로 결정하며, 클라이언트 훅에 `isSuperAdmin` 플래그로 전달.

---

# Edge Cases

- `size` > 100 요청 → 400 VALIDATION_ERROR
- account-service 장애 시 → 기존 circuit breaker / retry 동작 그대로 적용
- JWT roles에 `SUPER_ADMIN`이 없는 운영자가 email 없이 호출 → 빈 목록 반환 (403 아님, 읽기 권한 미보유로 조용히 빈 결과)
- 계정이 0건인 환경(초기 dev) → "결과가 없습니다." 메시지 표시

---

# Failure Scenarios

- `account.read` 권한 미부여 운영자가 email 없이 호출 → `AccountAdminController`에서 분기 처리, 빈 배열 반환
- account-service가 페이지네이션 파라미터 무시하고 전체 반환 → `size` 기본값 20으로 제한, JPA `Pageable` 강제 적용

---

# Test Requirements

- `AccountSearchController`: email=null 시 페이지네이션 응답 반환 단위 테스트
- `AccountAdminController`: `SUPER_ADMIN` vs 비-SUPER_ADMIN 분기 단위 테스트
- Frontend: `useAccountList` 훅 — MSW mock으로 페이지네이션 응답 확인
