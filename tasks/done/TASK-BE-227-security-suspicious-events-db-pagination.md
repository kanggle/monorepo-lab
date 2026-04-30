# TASK-BE-227: security-service suspicious events DB 페이지네이션 수정

## Goal

`SuspiciousEventQueryController`가 현재 전체 레코드를 메모리에 올린 뒤 슬라이싱하는
방식(in-memory pagination)을 사용하고 있다. 같은 서비스의 `LoginHistoryQueryController`는
이미 DB 레벨 페이지네이션(`Pageable`)을 사용한다. `suspicious-events` 엔드포인트도
동일 패턴으로 수정한다.

## Scope

- `apps/security-service/src/main/java/com/example/security/infrastructure/persistence/SuspiciousEventJpaRepository.java` — `Page` 반환 메서드 추가
- `apps/security-service/src/main/java/com/example/security/query/SecurityQueryService.java` — `findSuspiciousEvents` 시그니처 변경
- `apps/security-service/src/main/java/com/example/security/query/internal/SuspiciousEventQueryController.java` — `Pageable` 사용으로 변경
- 관련 테스트 수정/추가

## Acceptance Criteria

- [ ] `GET /internal/security/suspicious-events`가 DB 레벨에서 페이지네이션 수행
- [ ] `ruleCode` 필터도 DB 쿼리에서 처리 (메모리 필터링 제거)
- [ ] 응답 구조 동일: `content`, `page`, `size`, `totalElements`, `totalPages`
- [ ] size > 100 시 100으로 캡 (LoginHistoryQueryController와 동일)
- [ ] `LoginHistoryQueryController`와 동일한 패턴 사용

## Related Specs

- `specs/contracts/http/security-query-api.md` (페이지네이션 응답 형식)
- `specs/services/security-service/architecture.md`

## Related Contracts

- `specs/contracts/http/security-query-api.md`

## Edge Cases

- ruleCode=null: 필터 없이 전체 조회
- ruleCode 유효하지 않은 값: 빈 content 반환 (DB 쿼리 결과 없음)
- page=0, size=0: size는 최소 1로 처리 또는 빈 응답

## Failure Scenarios

- DB 쿼리 실패: 기존 예외 핸들러가 처리
