# Task ID

TASK-BE-118

# Title

auth-service 가입 이벤트 재발행 내부 엔드포인트 — user-service user_profiles 누락 복구 수단 제공

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
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

---

# Goal

auth-service에 내부(운영자)용 엔드포인트 `POST /api/internal/users/republish-signup-events`를 추가한다. 호출 시 auth-service `users` 테이블을 순회하며 각 유저에 대해 `auth.user.signed-up` 이벤트를 재발행한다.

user-service의 `UserSignedUpConsumer` 및 `UserSignedUpHandler`는 이미 멱등이므로, 재발행된 이벤트 중 이미 `user_profiles` 행이 있는 유저는 자동 스킵되고 누락된 유저만 프로필이 생성된다.

배경: Kafka 컨슈머 실패 또는 브로커 일시 장애로 `auth.user.signed-up` 이벤트가 소실되어 `user_profiles`에 행이 없는 유저가 존재할 수 있다. 현 상황에서는 해당 유저가 배송지 등록 등 `user_profiles` FK를 참조하는 API를 호출하면 500 오류가 발생한다 (FK 위반). 본 태스크 완료 후: 운영자가 엔드포인트를 한 번 호출하는 것만으로 누락 복구가 가능해진다.

---

# Scope

## In Scope

- `AdminUserRepublishController`(presentation 계층) 생성 — `POST /api/internal/users/republish-signup-events`
- `UserSignupRepublishService`(application 계층) 생성 — users repo 조회 + `AuthEventPublisher.publish(UserSignedUp)` 루프
- 응답 DTO: 재발행된 총 건수, 성공/실패 건수 반환
- gateway-service 경로 허용 설정 검토 (내부 경로 `/api/internal/**` 보호 정책)
- 단위 테스트 + 통합 테스트
- 컨트랙트 문서 추가 (`specs/contracts/http/auth-service.md` 또는 동등 문서)

## Out of Scope

- 자동 스케줄 실행 (본 태스크는 수동 호출만)
- user-service 쪽 변경 (기존 컨슈머가 멱등)
- auth-service Transactional Outbox 도입 (별도 태스크로 분리)
- cross-service reconciliation 대시보드

---

# Acceptance Criteria

- [ ] `POST /api/internal/users/republish-signup-events` 호출 시 auth-service `users` 테이블의 모든 행에 대해 `UserSignedUp` 이벤트가 `AuthEventPublisher.publish`로 발행된다
- [ ] 응답 바디에 `totalUsers`, `publishedCount`, `failedCount` 가 포함된다
- [ ] 발행 중 일부 실패해도 나머지 유저는 계속 발행된다 (부분 성공 허용)
- [ ] gateway-service에서 `/api/internal/**` 경로는 외부 인터넷에서 접근 불가이거나 관리자 인증을 요구한다
- [ ] 통합 테스트: 100명 users 시드 후 엔드포인트 호출 → 100건 발행 확인 (in-memory 또는 테스트용 publisher mock)
- [ ] 문서: 운영 가이드 섹션에 복구 절차 1줄 추가

---

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification.

- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/gateway-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/auth.user.signed-up.md` (기존 이벤트 스키마 준수)
- `specs/contracts/http/auth-service.md` (새 내부 엔드포인트 추가)

---

# Target Service

- `auth-service`
- `gateway-service` (경로 보호 설정만)

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

수정 대상 계층:
- Presentation: `AdminUserRepublishController`
- Application: `UserSignupRepublishService`
- Infrastructure: 변경 없음 (기존 `SpringAuthEventPublisher` 및 Kafka 인프라 재사용)

---

# Implementation Notes

### 재발행 전략

- 단순 루프 — `userRepository.findAll()` → 각 유저 대해 `UserSignedUp` 이벤트 생성 후 `authEventPublisher.publish(event)`
- 대량 데이터 시나리오는 본 태스크 범위 밖 (Out of Scope) — 현재 portfolio 규모상 findAll 허용
- eventId는 UUID로 신규 생성, occurredAt은 `Instant.now()`
- 기존 `UserSignedUp` 도메인 이벤트 레코드/클래스를 재사용해야 함 (`apps/auth-service/src/main/java/com/example/auth/domain/event/UserSignedUp.java`)

### 보안

- `/api/internal/**` 경로는 gateway-service에서 외부 접근 차단 또는 ADMIN 롤 필수
- 현재 gateway 정책 확인 후 필요 시 업데이트

### 응답 스키마 예시

```json
{
  "totalUsers": 152,
  "publishedCount": 150,
  "failedCount": 2
}
```

---

# Edge Cases

- 유저 0명 → 빈 응답(totalUsers=0) 성공 반환
- 이벤트 발행 실패(Kafka down) → `SpringAuthEventPublisher`는 예외를 삼키므로 `failedCount`를 정확히 반영하려면 publish 결과를 구별할 수 있는 변형 필요 (예: 메트릭 또는 try/catch 분리)
- 이미 `user_profiles`가 존재하는 유저 → user-service 멱등 컨슈머가 skip (정상)
- `users.active = false` 유저 — 재발행 대상에 포함 (이력 보존 목적)

---

# Failure Scenarios

- Kafka 브로커 장애로 전체 발행 실패 → 응답에 failedCount == totalUsers, 500 아닌 200으로 반환 (부분 성공 패턴)
- DB 쿼리 실패 → 500 반환
- 동시 호출로 중복 발행 → user-service가 멱등 처리하므로 영향 없음

---

# Test Requirements

- 단위 테스트: `UserSignupRepublishService` — publisher mock으로 publish 호출 횟수 검증
- 컨트롤러 테스트: 요청/응답 스키마 검증
- 통합 테스트: 시드된 users에 대해 엔드포인트 호출 → 발행 이벤트 카운트 및 응답 검증
- 기존 auth-service 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated
- [ ] Specs updated if required
- [ ] Ready for review
