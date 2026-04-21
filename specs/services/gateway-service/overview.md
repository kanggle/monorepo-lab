# Service Overview

## Service
`gateway-service`

## Responsibility
Serves as the single entry point for all client requests, handling request routing, JWT authentication, rate limiting, and cross-cutting concerns.

## In Scope
- request routing to downstream backend services
- JWT token validation and user identity injection (X-User-Id, X-User-Email, X-User-Role headers)
- per-IP rate limiting with Redis-backed token bucket
- CORS configuration
- request/response logging
- health check endpoint exposure

## Out of Scope
- business logic of any kind (owned by downstream services)
- token issuance or refresh (owned by auth-service)
- persistent data ownership (stateless gateway)
- direct database access

## Owned Data
- None (stateless — rate limiting state is stored in Redis as transient data)

## Published Interfaces
- Gateway routing endpoints (proxies to downstream service HTTP APIs)
- Actuator health and metrics endpoints

## Dependent Systems
- auth-service (JWT secret for token validation)
- Redis (rate limiting state)
- all downstream backend services (routing targets)
