# Service Types

This document defines the catalog of allowed service types in this monorepo and how to select one.

Every service declared under `specs/services/<service>/architecture.md` MUST declare exactly one `Service Type` from the catalog below. Implementation must follow the matching `platform/service-types/<type>.md` document.

---

# Catalog

| Service Type | Communication | Use When |
|---|---|---|
| `rest-api` | Synchronous HTTP request/response | The service exposes resources over HTTP for clients or other services |
| `event-consumer` | Asynchronous Kafka/event subscription | The service primarily reacts to domain events from other services |
| `batch-job` | Scheduled or one-shot execution | The service performs periodic work, ETL, or long-running computations |
| `grpc-service` | Synchronous gRPC | The service exposes high-throughput, strongly-typed binary APIs to internal clients |
| `graphql-service` | Synchronous GraphQL | The service aggregates data from multiple sources for clients with flexible query needs |
| `ml-pipeline` | Mixed (batch + serving) | The service trains, hosts, or serves ML models |
| `frontend-app` | Server-side rendering / SPA | The service is a Next.js application that serves UI to end users |
| `identity-platform` | OIDC / JWT issuance | The service issues and manages JWT tokens, exposes JWKS, and handles authentication for platform gateways |

---

# Selection Rules

1. A service may declare exactly **one** primary `Service Type`. Hybrid responsibilities (e.g., a REST service that also consumes events) keep `rest-api` as primary and document the secondary capability under "Integration Rules".
2. The `Service Type` is chosen at service inception and may not change without an architecture decision recorded in the service spec.
3. Adding a new `Service Type` to this catalog requires updating this INDEX, creating a matching `<type>.md`, and adding the matching skill under `.claude/skills/service-types/<type>-setup.md`.

---

# Reading Order

When reading specs for a task, follow `platform/entrypoint.md`. After reading Core platform specs, read the **Service-Type-Specific** spec for the target service's declared type (exactly one file).

---

# Conflict Rule

If a service-type spec conflicts with a higher-priority platform spec (architecture.md, security-rules.md, dependency-rules.md), the higher-priority spec wins. Service-type specs extend, never override.

---

# Hybrid Cases

| Scenario | Primary Type | Secondary Capability Documented As |
|---|---|---|
| REST service that also publishes events | `rest-api` | "publishes events" in Integration Rules |
| REST service that also consumes events | `rest-api` | "subscribes to <topic>" in Integration Rules |
| Batch job that also exposes a small admin REST endpoint | `batch-job` | "admin endpoints" in Integration Rules |
| gRPC service that also serves REST | `grpc-service` | "REST adapter" in Integration Rules |

If neither role is dominant, the service is too large — split it.
