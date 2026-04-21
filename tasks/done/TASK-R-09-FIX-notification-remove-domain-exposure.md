# Task ID

TASK-R-09-FIX

# Title

[FIX] notification-service controller의 도메인 엔티티 직접 노출 제거 (TASK-R-09 미완료)

# Status

review

# Owner

backend

# Task Tags

- fix
- refactor
- architecture

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

TASK-R-09의 구현이 불완전하다. 다음 두 가지 문제가 남아 있다:

1. `NotificationController`(14번째 줄)가 `com.example.notification.domain.model.Notification`을 직접 import하고 있다.
2. `NotificationQueryService.getNotifications()`가 `PageResult<Notification>` (도메인 엔티티)을 반환하며, 컨트롤러가 이를 직접 사용한다.
3. `NotificationListResponse`가 `Spring Data Page<Notification>`를 받는 메서드를 포함하고 있다.

application service가 도메인 엔티티를 result DTO로 변환하여 반환하도록 수정하고, controller에서 domain 패키지를 직접 import하지 않도록 해야 한다.

---

# Scope

## In Scope

- `NotificationQueryService.getNotifications()`가 `PageResult<ListNotificationsResult.NotificationSummary>` 또는 `ListNotificationsResult`를 반환하도록 변경
- `NotificationQueryService.getNotificationDetail()`이 `GetNotificationResult`를 반환하도록 변경 (현재 `Notification` 반환)
- `NotificationController`에서 `domain.model.Notification` import 제거
- `NotificationListResponse.from()`이 `PageResult<Notification>` 대신 result DTO를 받도록 변경
- `ListNotificationsResult`에서 `Spring Data Page<Notification>` 관련 메서드 제거
- 관련 테스트 수정

## Out of Scope

- API 응답 JSON 형식 변경
- 비즈니스 로직 변경
- 다른 서비스 변경

---

# Acceptance Criteria

- [ ] `NotificationController`에 `domain.model` 패키지 import가 없다
- [ ] `NotificationQueryService.getNotifications()`가 result DTO를 반환한다 (도메인 엔티티 아님)
- [ ] `NotificationQueryService.getNotificationDetail()`이 `GetNotificationResult`를 반환한다
- [ ] application service에서 도메인 엔티티를 result DTO로 변환한다
- [ ] `ListNotificationsResult`에 `Spring Data Page` 의존성이 없다
- [ ] API 응답 JSON 형식이 기존과 동일하다 (하위 호환)
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/services/notification-service/architecture.md`

---

# Related Contracts

- `specs/contracts/http/notification-api.md`

---

# Edge Cases

- result DTO 변환 시 필드 누락으로 API 응답 필드가 달라질 수 있음 -> 기존 응답 JSON과 대조하여 동일하게 유지
- `ListNotificationsResult`에 존재하는 `from(Page<Notification>)` 메서드를 제거할 때 다른 코드에서 참조하는 경우 -> 전체 검색 후 제거

---

# Failure Scenarios

- result DTO 변환 로직 누락으로 NullPointerException 발생 -> 단위 테스트로 변환 로직 검증
- API 응답 JSON 형식 변경으로 하위 호환 깨짐 -> 컨트롤러 슬라이스 테스트에서 JSON 검증

---

# Test Requirements

- `NotificationQueryService` 단위 테스트 (result DTO 반환 확인)
- `NotificationController` 슬라이스 테스트 (domain import 없음, JSON 응답 형식 확인)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
