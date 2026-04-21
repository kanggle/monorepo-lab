# Service Type: GraphQL Service

Normative requirements for any service whose `Service Type` is `graphql-service`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

A `graphql-service` aggregates data from multiple internal sources into a single GraphQL endpoint. It is intended for clients (mobile, web) that benefit from flexible query shapes and reduced round-trips.

This service type is **optional** and currently has no implementations in this monorepo. Adopt it only when at least one of the following is true:
- Clients suffer from N+1 REST round-trips for a known view
- Multiple clients need different projections of overlapping data
- A federated graph is desired across multiple owning teams

---

# Mandatory Requirements

## Schema First
- The GraphQL schema lives under `specs/contracts/graphql/<service>/schema.graphqls`
- Schema changes follow contract change rules (CLAUDE.md Contract Rule)
- Use schema-first code generation (DGS, graphql-codegen, or equivalent) — do not derive schema from runtime resolvers

## Schema Ownership
- A field's owning team is declared via a `@owner(team: "...")` directive
- Federated subgraphs use Apollo Federation conventions if multi-team
- Unowned fields are forbidden

## Query Complexity Limits
- Enforce a query complexity limit at the gateway layer
- Default: max depth 7, max complexity 1000
- Reject anonymous introspection in production

## N+1 Defense
- Every list resolver MUST use a DataLoader to batch downstream calls
- Adding a resolver without a DataLoader for nested fields is forbidden
- Expose per-resolver metrics to detect regression

## Authentication and Authorization
- All queries require a valid JWT bearer token unless explicitly public
- Field-level authorization via directives (`@auth(role: ADMIN)`) or resolver guards
- Authorization decisions at the application service layer, not the resolver wiring

## Error Handling
- Use the GraphQL `errors` array with `extensions.code` for machine-readable codes
- Never return partial data with sensitive fields nulled silently — log a clear partial-failure entry
- Map domain exceptions to schema-defined error codes, not raw exception names

## Observability
- Per-resolver metrics: `graphql_resolver_duration_seconds`, `graphql_resolver_errors_total`
- Per-operation metrics: `graphql_operation_duration_seconds{operation_name=...}`
- Trace each resolver as a span

## Persisted Queries (Recommended)
- For mobile clients, use persisted queries to prevent arbitrary query shapes in production
- Persist hashes in the gateway; reject unknown hashes from public clients

---

# Allowed Patterns

- Query, Mutation, Subscription operations
- DataLoader for batching downstream calls
- Federation across multiple subgraphs
- Caching at the resolver level (cache key includes parent + args)
- Coexistence with REST contracts (graphql aggregates over REST contracts)

---

# Forbidden Patterns

- Resolver that issues per-row queries without DataLoader
- Public introspection in production
- Unbounded list arguments without pagination
- Mutations that bypass the application service layer
- Implicit nullability (always declare `!`)

---

# Testing Requirements

- Schema validation in CI (`graphql-cli validate-schema`)
- Resolver unit tests for each query/mutation
- Integration tests using a real GraphQL server with Testcontainers downstream
- Performance test: query depth 5 with 100 children must stay within p95 SLO

---

# Default Skill Set

`service-types/graphql-service-setup`, matched architecture skill, `cross-cutting/api-versioning`, `cross-cutting/caching`, `cross-cutting/observability-setup`, `cross-cutting/security-hardening`, `backend/testing-backend`

---

# Acceptance for a New GraphQL Service

- [ ] Schema under `specs/contracts/graphql/<service>/schema.graphqls` reviewed
- [ ] Query complexity limits enforced
- [ ] DataLoader covers every list resolver
- [ ] Persisted queries enabled for public clients
- [ ] Per-resolver and per-operation metrics emitted
- [ ] Federation ownership directives present (if federated)
