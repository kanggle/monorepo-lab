# TASK-BE-235: fix — security-service e2e profile에 outbox.topicMapping 추가

## Goal

`docker-compose.e2e.yml` 환경에서 security-service 시작 시 `outbox.topicMapping`이 비어있다는 이유로 빈 검증이 실패한다.

```
Property: outbox.topicMapping
Value: "{}"
Reason: must not be empty
```

`auth-service/src/main/resources/application.yml`은 `outbox.topic-mapping`을 정의하고 있는 반면, security-service는 동일한 설정이 누락되어 있다. security-service가 발행하는 이벤트(`security.suspicious.detected`, `security.auto.lock.triggered`) 토픽 매핑을 application.yml에 추가한다.

## Scope

**In:**
- `apps/security-service/src/main/resources/application.yml` — `outbox.topic-mapping` 섹션 추가 (security-service가 발행하는 모든 이벤트에 대해)
- 발행하는 이벤트 목록 식별: `OutboxEvent` 발행 코드를 grep하여 정확한 토픽 매핑 도출

**Out:**
- security-service 도메인 코드 변경 없음
- 다른 서비스 설정 변경 없음

## Acceptance Criteria

- [ ] security-service가 e2e profile로 정상 기동 (`OutboxProperties` 검증 통과)
- [ ] 기동 시 발행되는 이벤트가 `docker-compose.e2e.yml`의 `kafka-init`이 생성한 토픽과 일치
- [ ] `./gradlew :apps:security-service:test` 통과
- [ ] e2e 환경에서 `docker compose -f docker-compose.e2e.yml up`으로 security-service 컨테이너가 healthy 상태 도달

## Related Specs

- `specs/services/security-service/architecture.md`
- `platform/event-driven-policy.md` (outbox 패턴 규약)

## Related Contracts

- `specs/contracts/events/security-events.md` (있을 경우 — security-service가 발행하는 이벤트 목록)

## Edge Cases

- 코드에서 발행하는 이벤트 타입이 `kafka-init`이 생성한 토픽 목록(`security.suspicious.detected`, `security.auto.lock.triggered`)과 다를 경우: kafka-init 또는 application.yml 중 어느 쪽이 spec과 정합한지 확인 후 정렬

## Failure Scenarios

- 토픽 매핑은 추가했으나 실제 토픽이 Kafka에 없으면 발행 시 fail-fast 설정에 따라 에러 → `kafka-init` 컨테이너의 토픽 목록 확인 필요
- application.yml에는 매핑이 있으나 application-prod.yml에서 다시 비어있게 override되는 경우: prod profile도 동시 점검
