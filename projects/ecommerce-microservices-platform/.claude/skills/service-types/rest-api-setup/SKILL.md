---
name: rest-api-setup
description: Set up a `rest-api` service end-to-end
category: service-types
---

# Skill: REST API Service Setup

Implementation orchestration for a `rest-api` service. Composes existing skills into a setup workflow.

Prerequisite: read `specs/platform/service-types/rest-api.md` before using this skill. This skill is the orchestration layer; concrete patterns live in the referenced skills.

---

## Orchestration Order

1. **Contract** — author or update `specs/contracts/http/<service>-api.md` (use `/design-api`)
2. **Architecture style** — pick from `backend/architecture/{layered,clean,ddd,hexagonal}.md` and declare in `specs/services/<service>/architecture.md`
3. **API skeleton** — `backend/springboot-api/SKILL.md`
4. **Validation** — `backend/validation/SKILL.md`
5. **DTO mapping** — `backend/dto-mapping/SKILL.md`
6. **Persistence** — `database/schema-change-workflow/SKILL.md`, `database/transaction-boundary/SKILL.md`
7. **Transactions** — `backend/transaction-handling/SKILL.md`
8. **Auth** — `backend/jwt-auth/SKILL.md`, `backend/gateway-security/SKILL.md`
9. **Pagination** — `backend/pagination/SKILL.md` (every list endpoint)
10. **Idempotency** — see "Idempotency" below
11. **Versioning** — `cross-cutting/api-versioning/SKILL.md`
12. **Error handling** — `backend/exception-handling/SKILL.md`
13. **Observability** — `cross-cutting/observability-setup/SKILL.md`, `backend/observability-metrics/SKILL.md`
14. **Tests** — `backend/testing-backend/SKILL.md`, `testing/contract-test/SKILL.md`, `testing/e2e-test/SKILL.md`
15. **Gateway route** — register in `gateway-service`

---

## Idempotency Pattern

Mutating endpoints (POST creating resources, PUT/PATCH state transitions) accept `Idempotency-Key`:

```java
@PostMapping("/orders")
public ResponseEntity<OrderResponse> placeOrder(
    @RequestHeader("Idempotency-Key") String key,
    @Valid @RequestBody PlaceOrderRequest request,
    @AuthenticationPrincipal AuthUser user) {

    return idempotencyService.executeOnce(key, () ->
        orderService.placeOrder(request.toCommand(user.id()))
    );
}
```

`IdempotencyService` stores `(key, hash(request), response)` in Redis with 24h TTL. Repeated calls return the stored response.

---

## Gateway Registration

After implementing the service, register the route in `gateway-service` config:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/v1/orders/**
          filters:
            - StripPrefix=0
            - JwtAuthFilter
            - AuditLogFilter
```

The service must NEVER be reachable from outside the cluster except through the gateway.

---

## Self-Review Checklist

Before submitting for review, verify against `specs/platform/service-types/rest-api.md` Acceptance section. Specifically:

- [ ] Every endpoint matches the HTTP contract exactly (no extra fields, no missing fields)
- [ ] Every list endpoint paginates
- [ ] Every mutating endpoint accepts and honors `Idempotency-Key`
- [ ] Authorization decision lives in the application service, not the controller
- [ ] No stack traces in error responses
- [ ] Metrics, logs, traces verified end-to-end against a real call
- [ ] Gateway route registered and tested
