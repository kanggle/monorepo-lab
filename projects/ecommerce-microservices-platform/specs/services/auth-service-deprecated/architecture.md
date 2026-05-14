# auth-service — Architecture

This document declares the internal architecture of `auth-service` (DEPRECATED).
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

> **Status**: DEPRECATED — ecommerce GAP cutover 시리즈 (2026-05-04, TASK-MONO-027/FE-067/BE-132) 로 GAP IdP 표준 OIDC consumer 전환. 본 spec 은 standalone v1 frozen 정책 보존용 historical reference.

---

## Identity

| Field | Value |
|---|---|
| Service name | `auth-service` (deprecated) |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **Layered Architecture** |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | (deprecated) self-hosted auth — GAP IdP 로 대체됨 |
| Deployable unit | `apps/auth-service/` (settings.gradle 제외, source 보존) |
| Data store | (deprecated) PostgreSQL |
| Event publication | (deprecated) |
| Event consumption | none |

### Service Type Composition

`auth-service` was a single-type `rest-api` service per
`platform/service-types/INDEX.md`. **DEPRECATED 2026-05-04** — ecommerce GAP
cutover 시리즈로 GAP IdP 표준 OIDC consumer 전환 (TASK-MONO-027/FE-067/BE-132).
standalone v1 시연 보존 목적으로 source 보존, monorepo settings.gradle 에서
제외. 적용되는 규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).

---

## Why This Architecture
This service is primarily CRUD-oriented and request/response-driven.

Business rules are important but relatively straightforward compared to domain-heavy services.

Maintainability, clarity, and fast operational development are prioritized.

## Internal Structure Rule
This service uses a layered internal structure.

Recommended internal layers:
- presentation
- application
- domain
- infrastructure

Package organization may follow package-by-layer or package-by-feature if the layered dependency rule is preserved.

## Allowed Dependencies
- presentation -> application
- application -> domain
- application -> infrastructure (via domain-defined interfaces only; concrete implementations are injected by the framework)
- infrastructure -> domain
- infrastructure -> framework and external libraries

## Forbidden Dependencies
- presentation must not access persistence directly
- presentation must not contain business rules
- domain must not depend on web framework code
- domain must not depend on controller/request classes
- repositories must not be called directly from controllers
- application must not import infrastructure utility classes directly (e.g., hashing, encoding utilities)
- application must access infrastructure behavior only through domain-layer interfaces or their return types

## Boundary Rules
- controllers handle HTTP mapping, validation entry, and response conversion
- application layer coordinates use-cases and transactions
- domain contains core business rules and entities
- infrastructure handles persistence, security integration, and framework adapters

## Integration Rules
- HTTP behavior must follow published contracts
- events, if any, must follow published event contracts
- persistence rules must follow service ownership boundaries
- shared libraries may be used only under shared-library policy

## Testing Expectations
Required emphasis:
- controller/API tests
- application service tests
- repository integration tests
- security-related tests where applicable

## Change Rule
Any architectural change to this service must be documented here first before implementation.