# Task ID

TASK-BE-041

# Title

user-service 리뷰 수정 — 이벤트 트랜잭션 분리, DLQ 설정, Admin 권한 체크, 쿼리 최적화

# Status

review

# Owner

backend

# Task Tags

- code
- event

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

TASK-BE-038, TASK-BE-039, TASK-BE-040 리뷰에서 발견된 스펙 위반 및 버그를 수정한다.

주요 수정 사항:
1. UserProfileUpdated 이벤트 발행을 트랜잭션 커밋 후로 분리 (event-driven-policy 위반)
2. UserSignedUpConsumer에 DLQ 설정 추가 (event-driven-policy 위반)
3. AdminUserController에 admin role 검증 추가
4. 기본 주소 해제 쿼리 최적화
5. libs/java-messaging 활용
6. TASK-BE-038 Status 필드 및 INDEX.md 정합성 수정

이 태스크 완료 후: 이벤트 발행이 트랜잭션 커밋 후에 실행되고, 실패한 이벤트는 DLQ로 라우팅되며, Admin API에 역할 검증이 적용된다.

---

# Scope

## In Scope

- `UserProfileService.updateProfile()` 이벤트 발행을 `@TransactionalEventListener(phase = AFTER_COMMIT)` 방식으로 변경
- `UserSignedUpConsumer` Kafka DLQ 설정 (ErrorHandler + DLT 토픽)
- `AdminUserController`에 admin role 헤더 검증 추가 (X-User-Role 또는 gateway 정책 기반)
- `AddressService.unmarkCurrentDefault()` — 벌크 UPDATE 쿼리로 변경
- `libs/java-messaging` 공통 라이브러리 활용 여부 확인 및 적용
- TASK-BE-038 Status 필드 `ready` → `review` 수정
- `tasks/INDEX.md` — TASK-BE-040 위치 수정 (ready → review)

## Out of Scope

- 도메인 모델 JPA 어노테이션 분리 (구조적 대규모 변경, 별도 태스크로 검토)
- 새로운 API 추가
- UserWithdrawn 이벤트 구현

---

# Acceptance Criteria

- [ ] `UserProfileUpdated` 이벤트가 트랜잭션 커밋 후에만 발행된다
- [ ] 트랜잭션 롤백 시 이벤트가 발행되지 않는다
- [ ] `UserSignedUpConsumer`에 DLQ가 설정되어 역직렬화 실패 메시지가 DLT 토픽으로 라우팅된다
- [ ] `GET /api/admin/users` 요청 시 admin role이 없으면 403 반환
- [ ] `GET /api/admin/users/{userId}` 요청 시 admin role이 없으면 403 반환
- [ ] 기본 주소 해제 시 전체 주소를 로드하지 않고 벌크 UPDATE로 처리한다
- [ ] 기존 테스트가 모두 통과한다
- [ ] 신규 수정 사항에 대한 테스트가 추가된다
- [ ] TASK-BE-038 Status 필드가 `review`로 수정된다
- [ ] `tasks/INDEX.md`에서 TASK-BE-040이 review 섹션에 위치한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/platform/api-gateway-policy.md`
- `specs/platform/security-rules.md`
- `specs/platform/testing-strategy.md`
- `specs/services/user-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/user-events.md` (UserProfileUpdated — 발행)
- `specs/contracts/events/auth-events.md` (UserSignedUp — 소비)
- `specs/contracts/http/user-api.md`

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

수정 대상 계층:
- Application: 이벤트 발행 방식 변경 (`@TransactionalEventListener`)
- Infrastructure: DLQ 설정, Kafka 컨슈머 에러 핸들러, 벌크 쿼리
- Presentation: AdminUserController 권한 검증

---

# Implementation Notes

### 이벤트 발행 트랜잭션 분리

- `UserProfileService`에서 직접 `eventPublisher.publishProfileUpdated()` 호출 제거
- Spring `ApplicationEventPublisher`로 도메인 이벤트를 발행하고, `@TransactionalEventListener(phase = AFTER_COMMIT)`에서 Kafka 이벤트 전송
- 또는 트랜잭션 커밋 후 콜백(`TransactionSynchronization.afterCommit()`) 활용

### DLQ 설정

- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` 구성
- DLT 토픽명: `auth.user.signed-up.DLT`
- 재시도 횟수: 3회 (backoff 포함)

### Admin Role 검증

- Gateway가 `X-User-Role` 헤더를 주입하는 경우: 컨트롤러에서 헤더 검증
- Gateway 라우팅만으로 보호하는 경우: 서비스 레벨 방어 계층 추가 (defense-in-depth)

### 벌크 쿼리

- `AddressRepository`에 `unmarkDefaultByUserId(UUID userId)` 메서드 추가
- `@Modifying @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")`

---

# Edge Cases

- 이벤트 발행 실패 시 트랜잭션은 이미 커밋된 상태 → 재시도 또는 로그 경고 (데이터 유실 방지)
- DLQ로 라우팅된 메시지의 모니터링/알림 필요
- Admin role 헤더가 gateway에서 주입되지 않는 경우 → 기본 거부

---

# Failure Scenarios

- `@TransactionalEventListener` 내 Kafka 발행 실패 → 로그 경고, 재시도 메커니즘 고려
- DLQ 토픽 생성 실패 → 컨슈머 기동 실패 방지 (auto-create 설정)
- Admin role 검증 로직 오류 → 통합 테스트에서 조기 발견

---

# Test Requirements

- 단위 테스트: 이벤트 발행이 트랜잭션 커밋 후에만 실행되는지 검증
- 단위 테스트: Admin role 없이 Admin API 호출 시 403 반환 검증
- 통합 테스트: DLQ 라우팅 — 잘못된 메시지 전송 시 DLT 토픽 도착 확인
- 통합 테스트: 트랜잭션 롤백 시 이벤트 미발행 확인
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
