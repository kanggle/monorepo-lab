# Task ID

TASK-BE-031-fix-stale-gauges-and-dlq-test

# Title

security-service — kafka.consumer.lag 스테일 게이지 제거 + 실제 DeserializationException DLQ 경로 검증

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-031

---

# Goal

TASK-BE-031 리뷰에서 발견된 Critical 2건과 Warning 2건을 해소한다. Consumer 재밸런스 후 스테일 게이지로 인한 메모리 누수·오염된 메트릭 문제를 제거하고, `DlqRoutingIntegrationTest`가 실제 `ErrorHandlingDeserializer` 경로의 poison pill을 검증하도록 보강한다.

---

# Scope

## In Scope

### Fix 1 — 스테일 게이지 제거 (Critical)
- `apps/security-service/src/main/java/com/example/security/infrastructure/config/SecurityMetricsConfig.java:142`의 `lagGauges` 맵이 재밸런스 후에도 사라진 파티션의 `LagKey`를 영구 유지.
- `refreshConsumerLag()`를 다음 구조로 교체:
  1. 사이클 시작 시 현재 관측되는 `(topic, group, partition)` 집합 수집
  2. 기존 맵에 있으나 이번 사이클에 없는 키에 대해 `meterRegistry.remove(meter)` 호출 후 맵에서 제거
  3. 새 값을 `AtomicLong.set` 으로 갱신 또는 신규 등록
- `AtomicLong` 참조 GC를 막지 않도록 map 제거와 meter 제거를 함께 수행

### Fix 2 — 진짜 poison pill DLQ 검증 (Critical)
- `apps/security-service/src/test/java/com/example/security/integration/DlqRoutingIntegrationTest.java` 를 확장:
  - `ErrorHandlingDeserializer` 경로가 실제 프로덕션 consumer config에 주입되어 있는지 먼저 확인 (없으면 `KafkaConsumerConfig`에 추가)
  - 테스트는 현재 String deserializer 경로 외에, JSON 바인딩 실패를 강제해 `DeserializationException` 을 `DefaultErrorHandler`가 받아 `DeadLetterPublishingRecoverer`를 거쳐 `<topic>.dlq`로 보내는지 검증
  - 3회 retry 후 DLQ 도착 + 원본 consumer 컨테이너 정상 동작 지속 검증

### Fix 3 — `security_consumer_lag` 중복 메트릭 정리 (Warning)
- 기존 `security_consumer_lag`(단일 합계) 게이지를 deprecated 처리하거나 제거.
- 대시보드 쿼리가 있으면 `sum(kafka_consumer_lag{service="security-service"})`로 대체 가능함을 `platform/observability.md`(있다면)에 메모.

### Fix 4 — `computeDlqDepth()` 계산식 수정 (Warning)
- `SecurityMetricsConfig.java:169`의 DLQ depth를 `latest offset - current committed offset` 으로 정정.
- committed offset은 `AdminClient.listConsumerGroupOffsets()` 결과에서 추출. 컨슈머 그룹이 없을 때는 `0` 또는 `latest offset` 중 정책 결정 (정책: DLQ consumer 그룹이 없으면 depth=latest offset, 즉 모두 미처리로 간주).

### (선택) Suggestion
- `SecurityMetricsConfigTest`에 재밸런스 시뮬레이션 회귀 테스트 추가 (이전 사이클 키가 다음 사이클에서 제거되는지).
- `AdminClient`를 `@Bean` 주입으로 분리해 `computeDlqDepth` unit 테스트 가능하게.

## Out of Scope

- DLQ consumer / 자동 replay
- Grafana 대시보드 JSON

---

# Acceptance Criteria

- [ ] 재밸런스 시뮬레이션 테스트가 이전 사이클 파티션의 게이지 제거를 검증
- [ ] `DlqRoutingIntegrationTest`가 실제 `DeserializationException` 경로의 DLQ 라우팅을 검증
- [ ] `security_consumer_lag` 중복 제거 또는 deprecated 표기
- [ ] `computeDlqDepth`가 committed offset 기준으로 계산
- [ ] `/actuator/metrics/kafka.consumer.lag` 태그 값이 재밸런스 후 stale 값 유지 없음
- [ ] `./gradlew :apps:security-service:test` 통과

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `platform/service-types/event-consumer.md` (dlq_depth 의미)

# Related Contracts

- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/security-service`

---

# Edge Cases

- 파티션이 일시적으로 사라졌다 곧 재할당되는 경우 — 한 사이클 게이지 공백 허용 (재등록)
- DLQ consumer 그룹이 존재하지 않을 때 depth 정책

---

# Failure Scenarios

- meter 제거 중 예외 → 로그 후 다음 사이클에 재시도, 서비스 경로 영향 없음

---

# Test Requirements

- Unit: `SecurityMetricsConfigTest`에 재밸런스 시나리오 추가
- Integration: `DlqRoutingIntegrationTest` JSON 바인딩 실패 케이스로 확장

---

# Definition of Done

- [ ] 구현 완료
- [ ] 테스트 통과
- [ ] Ready for review
