# Service Type: gRPC Service

Normative requirements for any service whose `Service Type` is `grpc-service`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

A `grpc-service` exposes high-throughput, strongly-typed binary APIs to internal clients via gRPC. It is intended for internal service-to-service communication where REST overhead is unacceptable or strict typing is required.

This service type is **optional** and currently has no implementations in this monorepo. Use it only when REST is demonstrably insufficient.

---

# When to Choose gRPC Over REST

Choose `grpc-service` when **at least two** apply:
- Throughput > 5k RPS sustained per instance
- Strict schema enforcement is required (binary, not JSON-tolerant)
- Bidirectional or server-streaming is needed
- Client and server are both internal and can deploy together

Otherwise, use `rest-api`.

---

# Mandatory Requirements

## Proto Source of Truth
- All `.proto` files live under `specs/contracts/grpc/<service>/v<n>/`
- Proto files MUST follow the [Buf style guide](https://buf.build/docs/best-practices/style-guide/)
- Generated code MUST NOT be committed — generate at build time

## Versioning
- Use proto package version: `<service>.v1`, `<service>.v2`
- Breaking changes require a new version package; coexistence rules apply (`cross-cutting/api-versioning.md`)
- Run Buf breaking change detector in CI: `buf breaking --against ".git#branch=main"`

## Authentication
- All gRPC endpoints require an interceptor that validates JWT or mTLS identity
- Authorization decisions at the application service layer, not the interceptor

## Error Handling
- Use canonical gRPC status codes (`INVALID_ARGUMENT`, `NOT_FOUND`, `PERMISSION_DENIED`, `INTERNAL`, etc.)
- Attach `google.rpc.ErrorInfo` for machine-readable error details
- Never use `INTERNAL` for client errors; never use `OK` for failures

## Deadlines and Cancellation
- Server MUST respect client deadlines and cancel work when the deadline expires
- Server MUST set its own deadline on outbound calls
- Default deadline: 5 seconds for unary, 30 seconds for streaming

## Observability
- Use OTel gRPC instrumentation for traces
- Emit standard RPC metrics: `rpc_server_duration`, `rpc_server_request_count`, broken down by method and status code
- See `cross-cutting/observability-setup.md`

## Encryption
- mTLS REQUIRED for all production traffic
- Plaintext gRPC is forbidden outside local dev
- See `infra/service-mesh.md` for mesh-managed mTLS

---

# Allowed Patterns

- Unary, server-streaming, client-streaming, and bidirectional RPCs
- Interceptors for auth, logging, metrics, retries
- Coexistence with REST: a service may expose both, but `Service Type` is `grpc-service` if gRPC is the primary interface

---

# Forbidden Patterns

- Plaintext gRPC in production
- Generated code committed to git
- Breaking changes without a new version package
- Returning `OK` with an error payload

---

# Testing Requirements

- Unit tests for each service implementation method
- Integration tests using an in-process gRPC server
- Contract tests pinning request/response shape per version
- Buf breaking-change check in CI

---

# Default Skill Set

`service-types/grpc-service-setup`, matched architecture skill, `cross-cutting/api-versioning`, `cross-cutting/observability-setup`, `cross-cutting/security-hardening`, `backend/testing-backend`

---

# Acceptance for a New gRPC Service

- [ ] Proto under `specs/contracts/grpc/<service>/v1/` reviewed
- [ ] Buf style and breaking-change checks passing
- [ ] Auth interceptor wired and tested
- [ ] mTLS verified between caller and service
- [ ] Deadlines honored and tested
- [ ] OTel traces visible end-to-end
