# Task ID

TASK-BE-042-processed-events-table-missing

# Title

admin/auth-service — `processed_events` 테이블 Flyway 마이그레이션 누락 복구

# Status

ready

# Owner

backend

# Task Tags

- code
- db
- fix

# depends_on

- (없음)

---

# Goal

E2E 런타임 검증 도중 admin-service와 auth-service 양쪽에서 Hibernate `Schema-validation: missing table [processed_events]` 로 기동 실패가 확인됐다. JPA 엔티티는 존재하지만 Flyway 마이그레이션에 해당 테이블 DDL이 빠져 있다. libs/java-messaging의 inbox 패턴 엔티티로 보이며, 같은 문제가 account/security에도 잠재해 있을 수 있어 전수 점검이 필요하다.

---

# Scope

## In Scope

1. 각 서비스 src/main/java 아래 `ProcessedEvent` 엔티티 또는 inbox 관련 JPA 엔티티 전수 조사
2. 해당 엔티티가 참조하는 테이블의 Flyway 마이그레이션 존재 여부 확인
3. 누락된 서비스에 마이그레이션 추가 — 서비스별 최신 V 번호 다음 번호 할당
4. 테이블 스키마는 libs/java-messaging의 inbox 계약과 일치해야 함 (processed_events 표준 컬럼: event_id PK, topic, consumer_group, processed_at)
5. e2e 프로파일에서 admin/auth 기동 시 Hibernate schema-validation 통과 확인

## Out of Scope

- inbox 로직 동작 변경
- account/security에도 같은 결함이 있으면 동일 패턴으로 수정 포함, 그 외 부수 리팩토링 금지

---

# Acceptance Criteria

- [ ] admin-service, auth-service (및 필요 시 account/security)에 `processed_events` 테이블 마이그레이션 존재
- [ ] `docker compose -f docker-compose.e2e.yml up -d` 후 admin/auth 헬스체크 healthy
- [ ] `./gradlew :apps:admin-service:test :apps:auth-service:test` 통과

---

# Related Specs

- `platform/service-types/event-consumer.md` (inbox 패턴)
- `libs/java-messaging` shared inbox 규약

---

# Target Service

- `apps/admin-service`, `apps/auth-service` (+필요 시 account/security)

---

# Edge Cases

- 이미 같은 테이블명을 가진 서비스별 기존 마이그레이션이 있으면 스키마 diff 확인 후 통합

---

# Failure Scenarios

- 마이그레이션 순번 충돌 시 최신 버전 + 1 할당

---

# Test Requirements

- 기존 단위 테스트 회귀 없음
- 로컬 compose up 으로 schema-validation 통과 확인

---

# Definition of Done

- [ ] 마이그레이션 추가 + compose 기동 성공
- [ ] Ready for review
