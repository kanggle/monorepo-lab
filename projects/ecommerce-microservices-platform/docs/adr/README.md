# Architecture Decision Records

이 프로젝트의 주요 기술·아키텍처 결정과 그 **이유**를 기록한다. 결정 자체보다 **왜 그 결정을 했는지**, 그리고 **버린 대안은 무엇이었는지**에 초점을 둔다.

| # | 제목 | 상태 |
|---|---|---|
| [ADR-001](ADR-001-microservices-over-monolith.md) | 단일 판매자 B2C에서 모놀리식이 아닌 MSA를 택한 이유 | Accepted |
| [ADR-002](ADR-002-saga-over-distributed-transaction.md) | Order + Payment + Inventory에 Saga + Outbox를 택한 이유 | Accepted |
| [ADR-003](ADR-003-frontend-architecture-dual-strategy.md) | web-store(FSD) / admin-dashboard(Layered-by-Feature) 이원화 | Accepted |
| [ADR-004](ADR-004-taxonomy-based-rule-system.md) | 분류(domain/trait) 기반 규칙 시스템을 도입한 이유 | Accepted |
| [ADR-005](ADR-005-korean-search-analyzer.md) | Elasticsearch 한국어 analyzer로 nori 채택 | Accepted |
| [ADR-006](ADR-006-at-least-once-delivery-policy.md) | At-Least-Once Delivery Policy (서비스별 outbox vs best-effort 결정) | Accepted |
| [ADR-007](ADR-007-logistics-aggregator-carrier-integration.md) | 택배 연동을 단일 택배사 직연동이 아닌 물류 중개 플랫폼(aggregator)으로 수렴한 이유 | Proposed |

## ADR 작성 원칙

- **한 장으로 수렴**. Context → Decision → Consequences.
- 고른 결정뿐 아니라 **버린 대안과 그 이유**를 함께 기록.
- 검증되기 전까지는 `Proposed`, 뒤집히면 `Superseded by ADR-XXX`.
