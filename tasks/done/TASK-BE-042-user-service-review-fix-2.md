# Task ID

TASK-BE-042

# Title

user-service 리뷰 수정 2차 — Kafka 발행 실패 처리, 불필요 이벤트 방지, 쿼리 최적화, 예외 위치 수정

# Status

done

# Owner

backend

# Task Tags

- code

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

TASK-BE-038 ~ TASK-BE-041 리뷰에서 발견된 잔여 이슈를 수정한다.

주요 수정 사항:
1. `KafkaUserProfileEventPublisher`에서 `KafkaTemplate.send()` 비동기 실패 처리 추가
2. `UserProfileService.updateProfile()`에서 실제 변경이 없는 경우 이벤트 발행 방지
3. `AddressService.deleteAddress()`에서 전체 주소 로드 대신 count 쿼리 사용
4. `AccessDeniedException`을 도메인 레이어에서 presentation 레이어로 이동

이 태스크 완료 후: Kafka 이벤트 발행 실패가 로깅되고, 변경 없는 프로필 업데이트 시 이벤트가 발행되지 않으며, 주소 삭제 시 불필요한 전체 로드가 제거된다.

---

# Scope

## In Scope

- `KafkaUserProfileEventPublisher.handleProfileUpdated()` — `kafkaTemplate.send()` 반환값 `CompletableFuture`에 실패 콜백 추가
- `UserProfileService.updateProfile()` — 변경 여부 확인 후 이벤트 발행
- `AddressService.deleteAddress()` — `findAllByUserId()` 대신 `countByUserId()` 사용
- `AccessDeniedException` — `domain.exception` → `presentation` 패키지로 이동, import 경로 수정

## Out of Scope

- 도메인 모델 JPA 어노테이션 분리 (별도 태스크)
- 새로운 API 추가
- 테스트 구조 변경

---

# Acceptance Criteria

- [ ] `KafkaTemplate.send()` 실패 시 에러 로그가 기록된다
- [ ] 프로필 수정 요청에서 실제 변경된 필드가 없으면 `UserProfileUpdated` 이벤트가 발행되지 않는다
- [ ] 주소 삭제 시 `findAllByUserId()` 대신 `countByUserId()`로 다른 주소 존재 여부를 확인한다
- [ ] `AccessDeniedException`이 presentation 레이어에 위치한다
- [ ] 기존 테스트가 모두 통과한다
- [ ] 수정 사항에 대한 테스트가 추가된다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/services/user-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/user-events.md` (UserProfileUpdated — 발행)

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

수정 대상 계층:
- Application: 이벤트 발행 조건 추가 (`UserProfileService`)
- Infrastructure: Kafka 발행 실패 처리 (`KafkaUserProfileEventPublisher`), 주소 삭제 쿼리 최적화 (`AddressService`)
- Presentation: `AccessDeniedException` 이동

---

# Implementation Notes

### Kafka 발행 실패 처리

- `kafkaTemplate.send()` 반환값인 `CompletableFuture`에 `.whenComplete()` 콜백 등록
- 실패 시 `log.error()`로 기록 (userId, topic, 에러 메시지)

### 변경 여부 확인

- `updateProfile()` 내에서 기존 값과 요청 값을 비교
- 모든 필드가 동일하면 이벤트를 발행하지 않음
- 비교 대상: nickname, phone, profileImageUrl

### 주소 삭제 쿼리 최적화

- `deleteAddress()`에서 `findAllByUserId(userId)` → `countByUserId(userId)` 변경
- 다른 주소 존재 여부는 `count > 1`로 판단

### AccessDeniedException 이동

- `com.example.user.domain.exception.AccessDeniedException` → `com.example.user.presentation.exception.AccessDeniedException`
- `AdminUserController`, `GlobalExceptionHandler`의 import 경로 수정

---

# Edge Cases

- 프로필 수정 시 같은 값으로 업데이트 요청 → 이벤트 미발행
- nickname=null로 요청 시 기존 값이 null이면 변경 없음으로 판단
- Kafka 브로커 일시 장애 시 → 실패 로그 기록, 프로필 수정은 정상 완료

---

# Failure Scenarios

- Kafka 브로커 완전 다운 → `send()` 실패 콜백에서 로그 기록
- `countByUserId()` DB 오류 → 기존과 동일하게 500 반환

---

# Test Requirements

- 단위 테스트: 변경 없는 프로필 수정 시 이벤트 미발행 검증
- 단위 테스트: `deleteAddress()`에서 count 쿼리 호출 검증
- Controller 테스트: `AccessDeniedException` import 변경 후 기존 테스트 통과 확인
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
