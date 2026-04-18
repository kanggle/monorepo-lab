---
name: grpc-service-setup
description: Set up a `grpc-service` end-to-end
category: service-types
---

# Skill: gRPC Service Setup

Implementation orchestration for a `grpc-service`. Use only when `rest-api` is demonstrably insufficient.

Prerequisite: read `platform/service-types/grpc-service.md` before using this skill. No services in this monorepo currently use gRPC — this skill activates with the first one.

---

## Orchestration Order

1. **Justification** — document in `specs/services/<service>/architecture.md` why gRPC was chosen over REST (must satisfy at least 2 criteria from the spec)
2. **Proto** — author `.proto` files under `specs/contracts/grpc/<service>/v1/`
3. **Buf setup** — `buf.yaml`, `buf.gen.yaml`, lint and breaking-change checks in CI
4. **Architecture style** — pick from `backend/architecture/`
5. **Server skeleton** — see "Spring gRPC Wiring" below
6. **Auth interceptor** — JWT or mTLS-based identity, application service authorizes
7. **Error mapping** — domain exceptions → gRPC status codes
8. **Deadlines** — server respects client deadline, sets own deadline on outbound calls
9. **Observability** — OTel gRPC instrumentation, RPC metrics
10. **mTLS** — via service mesh (`infra/service-mesh/SKILL.md`) or direct TLS config
11. **Tests** — in-process server tests, contract tests, Buf breaking-change CI

---

## Proto Layout

```
specs/contracts/grpc/
  example-service/
    v1/
      order_service.proto
      order_messages.proto
    v2/
      order_service.proto
      order_messages.proto
buf.yaml
buf.gen.yaml
```

```protobuf
// specs/contracts/grpc/example-service/v1/order_service.proto
syntax = "proto3";
package orderservice.v1;
option java_package = "com.example.order.grpc.v1";
option java_multiple_files = true;

import "google/protobuf/timestamp.proto";

service OrderService {
  rpc PlaceOrder(PlaceOrderRequest) returns (PlaceOrderResponse);
  rpc GetOrder(GetOrderRequest) returns (Order);
  rpc StreamOrderEvents(StreamOrderEventsRequest) returns (stream OrderEvent);
}
```

Generated code is **never** committed. Regenerate at build time.

---

## Spring gRPC Wiring

Use `grpc-spring-boot-starter` (LogNet or Yidong fork, whichever the org standardizes on).

```java
@GrpcService
public class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final PlaceOrderUseCase placeOrderUseCase;

    @Override
    public void placeOrder(PlaceOrderRequest request, StreamObserver<PlaceOrderResponse> responseObserver) {
        try {
            PlaceOrderResult result = placeOrderUseCase.execute(toCommand(request));
            responseObserver.onNext(toResponse(result));
            responseObserver.onCompleted();
        } catch (DomainException e) {
            responseObserver.onError(toGrpcStatus(e).asRuntimeException());
        }
    }
}
```

Map domain exceptions to canonical statuses:

| Domain | gRPC Status |
|---|---|
| Validation failure | `INVALID_ARGUMENT` |
| Not found | `NOT_FOUND` |
| Auth missing | `UNAUTHENTICATED` |
| Forbidden | `PERMISSION_DENIED` |
| Conflict | `ABORTED` or `FAILED_PRECONDITION` |
| Rate limit | `RESOURCE_EXHAUSTED` |
| Unknown / 5xx | `INTERNAL` |

---

## Auth Interceptor

```java
@GrpcGlobalServerInterceptor
public class JwtServerInterceptor implements ServerInterceptor {
    public <Req, Res> ServerCall.Listener<Req> interceptCall(
            ServerCall<Req, Res> call, Metadata headers, ServerCallHandler<Req, Res> next) {
        String token = headers.get(Metadata.Key.of("authorization", ASCII_STRING_MARSHALLER));
        AuthUser user = jwtVerifier.verify(token);
        Context ctx = Context.current().withValue(AuthContext.USER_KEY, user);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
```

---

## Buf CI Checks

`.github/workflows/proto.yml`:

```yaml
jobs:
  buf:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: bufbuild/buf-setup-action@v1
      - run: buf lint
      - run: buf breaking --against ".git#branch=main"
```

---

## Self-Review Checklist

Verify against `platform/service-types/grpc-service.md` Acceptance section. Specifically:

- [ ] Justification documented
- [ ] Proto under `specs/contracts/grpc/`, generated code excluded from git
- [ ] Buf lint and breaking checks passing in CI
- [ ] JWT or mTLS interceptor wired
- [ ] Domain exceptions mapped to canonical gRPC statuses
- [ ] Deadlines respected (test by setting a client deadline of 100ms on a slow path)
- [ ] OTel traces visible end-to-end
- [ ] mTLS verified between caller and service in staging
