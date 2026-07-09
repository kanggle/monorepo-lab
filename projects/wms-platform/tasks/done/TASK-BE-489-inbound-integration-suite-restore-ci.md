# Task ID

TASK-BE-489

# Title

inbound-service 통합 테스트 스위트 복구 + CI 배선 (dormant PutawayLifecycleIntegrationTest)

# Status

done

# Owner

backend

# Task Tags

- code
- test
- ci

---

# Goal

`inbound-service` 의 통합 테스트 스위트(`PutawayLifecycleIntegrationTest`, 1 IT)는 **dormant** 다 — 컴파일은 되지만(`test` sourceSet → `:check` 가 빌드) 어떤 CI 레인에서도 실행되지 않았고, 컨텍스트조차 로드되지 않는다. TASK-MONO-335 가 자매 서비스 `inventory-service` 를 복구·CI 배선하면서 inbound 도 함께 다루려 했으나, inbound 는 SecurityConfig 수정만으로는 부족한 **다층 rot** 이 드러나 MONO-335 범위에서 **분리**됐다(inventory 만 배선, PR #2354).

이 task 는 inbound 통합 스위트를 복구해 CI 에 배선한다.

# 배경 — MONO-335 에서 확인된 inbound rot 층 (순차)

1. **컨텍스트 로드 #1 (SecurityConfig)**: `SecurityConfig` 의 `@Bean SecurityFilterChain securityFilterChain(HttpSecurity …)` 가 `@SpringBootTest(webEnvironment=NONE)` 에서 `HttpSecurity` 빈 부재로 로드 실패. 수정 = `@ConditionalOnWebApplication(SERVLET)` (inventory/outbound 의 BE-432/BE-334 패턴). **MONO-335 에서 시도했다가 inbound descope 시 되돌림** — 이 task 에서 재적용.
2. **컨텍스트 로드 #2 (OutboxAutoConfiguration)**: `InboundServiceApplication` 이 공유 `OutboxAutoConfiguration` 을 `exclude` 하지 않아, 라이브러리 `OutboxJpaConfig` 가 `ProcessedEventJpaEntity`(테이블 `processed_events`)를 entity-scan → inbound 에 해당 마이그레이션 없음 → `ddl-auto=validate` 실패. inbound 는 자체 outbox 스택(`InboundOutboxJpaEntity` + `@Scheduled` `OutboxPublisher`) + 자체 dedupe(`inbound_event_dedupe`) 사용, 공유 inbox 미사용. 수정 = `@SpringBootApplication(exclude = OutboxAutoConfiguration.class)` (inventory BE-432 / outbound BE-333 패턴). **주의**: inventory 의 BE-432 는 exclusion 과 함께 동반 config(예: `InventoryServiceCommonConfig` 의 KafkaTemplate/TopicResolver 등 outbox 발행에 필요한 빈)를 제공한다 — 단순 1줄 exclusion 이 발행 경로를 깨뜨릴 수 있으므로 inventory 의 전체 구성과 대조해 **동반 빈까지 맞춰야** 한다.
3. **기능 실패 (golden-path 이벤트 미방출)**: 컨텍스트 로드 후 `putawayLifecycle_publishesAllEvents` 가 `pollEventOnTopic`(45s await) 타임아웃 — 라이프사이클을 use-case 포트로 구동한 뒤 기대한 3개 이벤트(`inbound.putaway.instructed/completed`, `inbound.asn.closed`)가 Kafka 토픽에 도달하지 않음. #2 의 불완전 exclusion 부작용(outbox 발행 빈 누락)일 수도, 별개 기능 갭일 수도 있다 — **진단 필요**.

# Scope

## In Scope

- inbound `SecurityConfig` `@ConditionalOnWebApplication(SERVLET)` 재적용.
- inbound `InboundServiceApplication` 공유 `OutboxAutoConfiguration` exclude + **동반 빈 정합**(inventory 구성 대조; outbox→Kafka 발행 경로가 IT 에서 실제 동작하도록).
- golden-path 이벤트 미방출 **근본 원인 진단·수정**(발행 빈 누락 / 토픽·직렬화 불일치 / 라이프사이클 갭 중 무엇인지).
- append-only teardown: `@AfterEach` 의 `DELETE FROM putaway_confirmation` / `DELETE FROM inbound_outbox` 는 V7 W2 `BEFORE DELETE` 트리거에 막힘 → `TRUNCATE` 로 교체(inventory MONO-335 패턴).
- **CI 배선**: `inbound-service:integrationTest` 를 CI 에 추가. MONO-335 의 `wms-inventory-integration-tests` 잡에 합치거나(2모듈, `--no-parallel`) 별도 잡. 판단은 구현 시.
- 로컬 Windows Testcontainers 는 npipe 미감지로 skip → **CI Linux 가 권위**.

## Out of Scope

- inventory 복구/배선(MONO-335 완료).
- 이벤트 dedupe save/merge 버그(TASK-BE-488) — 별건(단, #2 의 dedupe/outbox 구성 이해가 겹칠 수 있음).

# Related Specs

> `platform/entrypoint.md` → `PROJECT.md`(domain=wms) → `rules/domains/wms.md` + `rules/traits/{transactional,integration-heavy}.md` → event-consumer / rest-api service-type.

- `specs/services/inbound-service/architecture.md`(putaway 라이프사이클·이벤트 발행)
- `specs/contracts/events/inbound-events.md`(instructed/completed/closed 이벤트 shape·토픽)

# Related Contracts

- 없음(테스트 복구 + 컨텍스트/발행 구성 수정, 계약 무변경 예상). golden-path 진단 결과 발행 갭이 계약 불일치면 계약 우선 갱신.

# Target Service

- `inbound-service`.

---

# Edge Cases

- `@ConditionalOnWebApplication` 적용 후 inbound `@WebMvcTest` 슬라이스는 servlet 컨텍스트라 조건 true → 보안 배선 유지(회귀 없음).
- OutboxAutoConfiguration exclude 후 자체 `@Scheduled` outbox 발행이 IT(NONE 웹환경)에서 동작하는지 확인(inventory IT 가 Kafka 이벤트를 검증·통과하므로 NONE 에서도 스케줄러 동작 가능).

# Failure Scenarios

- exclusion 이 발행 빈을 깨면 golden-path 가 계속 타임아웃 → inventory 의 동반 config 를 그대로 이식.
- 추가 층 rot 발견 → 이 task 내 수정 또는 재분리(단, inbound 는 IT 1개라 완결 목표).

---

# Notes

- 발견/분리 경위: TASK-MONO-335(2026-07-09). 사용자 방침 "스코프 신중히" + task 의 failure-scenario 계획("inbound 가 heavy rot 이면 descope, follow-up 발제")에 따라 분리.
- 구현 권장 모델: **Opus**(다층 컨텍스트/발행 구성 + 기능 진단).
