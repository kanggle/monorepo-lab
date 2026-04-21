# Task ID

TASK-FE-029

# Title

admin-dashboard React key 수정 및 api-client 하드코딩 설정 외부화

# Status

review

# Owner

frontend

# Task Tags

- code, test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

TASK-INT-012 크로스 리뷰에서 발견된 이슈 수정. admin-dashboard의 DataTable 및 ProductForm에서 배열 인덱스를 React key로 사용하는 Critical 이슈를 수정하고, api-client 패키지의 하드코딩된 공개 경로 및 타임아웃 설정을 외부화한다.

---

# Scope

## In Scope

### admin-dashboard
- DataTable.tsx: `key={index}` 및 `key={i}` → 데이터의 고유 식별자를 key로 사용
- ProductForm.tsx: variant `key={index}` → 고유 식별자를 key로 사용

### api-client (packages/api-client)
- `client.ts`: `PUBLIC_PATHS` 하드코딩 → `ApiClientConfig`에서 설정 가능하도록 변경
- `client.ts`: `REFRESH_TIMEOUT_MS = 10000` 및 `timeout: 10000` → `ApiClientConfig`에서 설정 가능하도록 변경
- `auth.ts`: `AUTH_ERROR_MESSAGES` 한국어 메시지 → 메시지 키만 export, 실제 메시지는 앱에서 제공

## Out of Scope

- api-client 전체 재설계
- 다른 프론트엔드 버그 수정

---

# Acceptance Criteria

- [ ] DataTable에서 배열 인덱스 대신 안정적 식별자가 key로 사용된다
- [ ] ProductForm variant 목록에서 안정적 key가 사용된다
- [ ] api-client의 공개 경로 목록이 설정으로 주입 가능하다
- [ ] api-client의 타임아웃이 설정으로 주입 가능하다
- [ ] AUTH_ERROR_MESSAGES가 키만 export하거나 앱 레벨에서 오버라이드 가능하다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Target App

- admin-dashboard, packages/api-client

---

# Edge Cases

- DataTable에 고유 ID가 없는 데이터가 전달되는 경우 fallback key 전략 필요
- api-client 설정 누락 시 합리적인 기본값 유지

---

# Failure Scenarios

- key 변경으로 인한 불필요한 리렌더링 발생
- 타임아웃 설정 누락으로 요청 무한 대기

---

# Test Requirements

- DataTable key 안정성 테스트
- api-client 설정 주입 테스트
- 기존 테스트 통과 확인

---

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
