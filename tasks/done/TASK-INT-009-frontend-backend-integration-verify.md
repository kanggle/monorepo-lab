# Task ID

TASK-INT-009

# Title

프론트엔드 ↔ 백엔드 통합 검증 — web-store, admin-dashboard API 연동 확인

# Status

done

# Owner

backend

# Task Tags

- deploy
- test

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

web-store와 admin-dashboard가 docker compose 환경에서 gateway-service를 통해 실제 백엔드 API와 정상 연동되는지 검증한다. CORS 설정, 프록시 경로, 인증 플로우가 올바르게 동작하는지 확인한다.

---

# Scope

## In Scope

### web-store 연동 검증
- 상품 목록 페이지가 실제 API 데이터를 렌더링하는지 확인
- 로그인/회원가입 플로우가 gateway를 통해 동작하는지 확인
- 주문 생성 플로우가 정상 동작하는지 확인
- API 에러 응답 시 프론트엔드 에러 핸들링 동작 확인

### admin-dashboard 연동 검증
- 상품 관리(CRUD)가 실제 API와 연동되는지 확인
- 주문 관리(목록, 상세, 취소)가 정상 동작하는지 확인
- 사용자 관리 페이지가 정상 동작하는지 확인

### 인프라 검증
- `NEXT_PUBLIC_API_URL` 빌드 ARG가 올바르게 주입되었는지 확인
- CORS 정책이 프론트엔드 요청을 허용하는지 확인
- gateway-service 라우팅이 정상 동작하는지 확인

## Out of Scope

- 자동화된 브라우저 테스트 (수동 검증 가이드 작성)
- 성능/부하 테스트

---

# Acceptance Criteria

- [ ] web-store가 gateway-service API를 통해 데이터를 정상 조회한다
- [ ] admin-dashboard가 gateway-service API를 통해 CRUD 동작한다
- [ ] 인증 토큰이 프론트엔드에서 올바르게 관리된다
- [ ] CORS 에러 없이 API 호출이 성공한다
- [ ] 통합 검증 가이드 문서가 작성된다 (`docs/integration-verification.md`)

---

# Related Specs

- `specs/platform/deployment-policy.md`
- `specs/platform/testing-strategy.md`

# Related Skills

_(없음)_

---

# Related Contracts

- `specs/contracts/` (전체 API 계약)

---

# Target Service

- `docs/integration-verification.md` (신규)
- `apps/web-store/` (설정 검증)
- `apps/admin-dashboard/` (설정 검증)
- `apps/gateway-service/` (CORS 설정 확인)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- gateway-service가 아직 기동 중일 때 프론트엔드 접속 시 에러 핸들링
- 토큰 만료 후 갱신 플로우가 프론트엔드에서 정상 동작하는지
- 백엔드 서비스 하나가 다운된 상태에서 프론트엔드 에러 표시

---

# Failure Scenarios

- CORS 설정 누락 시 gateway-service에 CORS 허용 설정 추가 필요
- `NEXT_PUBLIC_API_URL`이 Docker 내부 주소일 때 클라이언트 사이드에서 접근 불가 → 호스트 접근 가능한 URL로 변경 필요

---

# Test Requirements

- 수동 검증 체크리스트 기반

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
