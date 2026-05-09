# search-service Observability

Defines business metrics specific to search-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `search_query_total` | Counter | Total search queries executed |
| `search_query_duration_seconds` | Histogram | Search query latency |
| `search_zero_results_total` | Counter | Total queries returning zero results |
| `search_index_sync_total` | Counter | Total index sync operations by event type (created, updated, deleted) |
| `search_index_sync_failure_total` | Counter | Total index sync failures |

---

# Change Rule

New search-service metrics must be documented here before implementation.
