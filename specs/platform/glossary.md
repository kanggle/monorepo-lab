# Glossary

Defines key terms used across specs, tasks, and documentation in this project.

---

# Authentication & Authorization

| Term | Definition |
|---|---|
| **Access Token** | Short-lived JWT issued by `auth-service` on login. Used to authenticate API requests. |
| **Refresh Token** | Long-lived opaque token used to obtain a new access token without re-authentication. |
| **JWT** | JSON Web Token. A signed token carrying identity claims. |
| **Bearer Token** | HTTP authentication scheme: `Authorization: Bearer <token>`. |
| **Revoked Token** | A refresh token that has been explicitly invalidated (e.g. via logout). |
| **Authentication** | Verifying the identity of a caller (who are you?). |
| **Authorization** | Verifying what an authenticated caller is allowed to do (what can you do?). |

---

# Domain Terms

| Term | Definition |
|---|---|
| **User** | A registered account in the system. Identified by UUID. Owns email, hashed password, and name. |
| **Order** | A customer's request to purchase one or more products. Owned by `order-service`. |
| **Payment** | A financial transaction associated with an order. Owned by `payment-service`. |
| **Product** | A purchasable item with price, stock, and description. Owned by `product-service`. |
| **Inventory** | The available quantity of a product. Updated on order placement and cancellation. |

---

# Architecture Terms

| Term | Definition |
|---|---|
| **Service** | An independently deployable unit with its own database and domain. |
| **Gateway** | The single entry point for all external traffic. Handles auth, routing, rate limiting. |
| **Contract** | A published interface definition (HTTP or event) that producers and consumers must follow. |
| **Spec** | A specification document defining rules, policies, or behavior. Source of truth. |
| **Task** | An execution unit for implementing a defined piece of work. Lifecycle: backlog → ready → in-progress → review → done. |
| **ADR** | Architecture Decision Record. Documents significant architecture decisions and their rationale. |
| **Layered Architecture** | Internal service structure with layers: presentation, application, domain, infrastructure. |
| **DDD (Domain-Driven Design)** | Architecture style centered on complex domain models, aggregates, and bounded contexts. Used when business rules are central. |
| **Hexagonal Architecture** | Architecture style (also called Ports and Adapters) that isolates business logic from external systems via port interfaces and adapter implementations. |

---

# Infrastructure Terms

| Term | Definition |
|---|---|
| **Testcontainers** | Java library that spins up real Docker containers (PostgreSQL, Redis, Kafka) during integration tests. |
| **Flyway** | Database migration tool. Applies versioned SQL scripts (`V{n}__description.sql`) in order. |
| **Redis** | In-memory key-value store. Used for refresh token storage with TTL-based expiry. |
| **Kafka** | Distributed event streaming platform. Used for async communication between services. |
| **Monorepo** | Single repository containing multiple services and libraries. Managed with Gradle + Turborepo. |

---

# Status Terms

| Term | Definition |
|---|---|
| **backlog** | Task is defined but not yet ready for implementation. |
| **ready** | Task is fully specified and ready to be implemented. |
| **in-progress** | Implementation is actively underway. |
| **review** | Implementation is complete and awaiting review. |
| **done** | Review approved and task is complete. |
| **archive** | Task is done and no further changes are expected. |

---

# Change Rule

Changes to glossary terms must be documented here before applying across specs.
