# Architecture Selection Guide

Design reference for choosing the right architecture style per service.

This document provides judgment guidance — the mandatory rules are in `specs/platform/architecture-decision-rule.md`.

---

## Layered Architecture

Use Layered Architecture when:

- the service is mostly CRUD-oriented
- domain rules are simple
- transaction flow is straightforward
- external integrations are limited
- maintainability is more important than advanced isolation

Typical fit:

- auth-service
- user-service
- admin-oriented services
- internal management services

---

## DDD-style Architecture

Use DDD-style Architecture when:

- the domain model is complex
- business rules are central
- aggregate boundaries matter
- invariants must be protected carefully
- the service contains rich domain behavior

Typical fit:

- order-service
- inventory-service
- pricing-service
- promotion-service

---

## Hexagonal Architecture

Use Hexagonal Architecture when:

- the service depends heavily on external systems
- testability and adapter isolation are critical
- input/output boundaries are important
- multiple integrations or runtime interfaces exist
- business logic should stay independent from frameworks

Typical fit:

- payment-service
- notification-service
- external integration services
- gateway-adjacent orchestration components

---

## Clean Architecture

Use Clean Architecture when:

- each use case must be independently testable and deployable
- the service has many distinct use cases with different complexity
- strict framework independence is required at every layer
- use-case-level isolation is more important than port/adapter boundary isolation
- the team wants explicit one-use-case-per-class organization

Typical fit:

- notification-service
- workflow-oriented services
- services with many independent operations (e.g., batch-worker with diverse jobs)
- services where use cases vary significantly in complexity

### Clean Architecture vs Hexagonal

Both isolate business logic from frameworks. Choose based on the primary concern:

- **Hexagonal**: boundary isolation is the priority (many external systems, adapter swapping)
- **Clean**: use-case independence is the priority (many diverse operations, per-use-case testing)
