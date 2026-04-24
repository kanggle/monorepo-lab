# Feature: Product Search

## Purpose

Provides full-text product search with filtering, sorting, faceted navigation, and relevance ranking. Maintains a denormalized search index synchronized from product-service via domain events.

## Related Services

| Service | Role |
|---|---|
| search-service | Primary owner — search index management, query API, event consumption |
| product-service | Source of truth for product data; publishes product events |
| web-store | Customer-facing search UI with keyword input, filters, and faceted navigation |
| gateway-service | Request routing |
| batch-worker | Elasticsearch index consistency check (periodic sync verification) |

## User Flows

### Product Search

1. Client sends GET /api/search/products with query parameters
2. search-service executes Elasticsearch query with keyword matching, filters, and sorting
3. Returns paginated results with relevance scores and facets (categories, price ranges)

### Index Synchronization (Event-Driven)

1. product-service publishes ProductCreated → search-service indexes new product
2. product-service publishes ProductUpdated → search-service updates indexed fields
3. product-service publishes ProductDeleted → search-service removes product from index
4. product-service publishes StockChanged → search-service updates stock in index

### Index Consistency Check (Batch)

1. batch-worker periodically runs Elasticsearch index consistency check
2. Verifies product data in search index matches product-service source data
3. Logs discrepancies for investigation

## Business Rules

- Search query (`q`) is required, minimum 1 character
- Default status filter: ON_SALE (only shows products currently on sale)
- Sort options: relevance (default), price_asc, price_desc, newest
- Page size maximum: 100
- Search results are eventually consistent — may lag behind product-service by a short delay
- Facets provide aggregated counts for categories and price ranges
- Relevance score is informational only
- All event consumers handle duplicates idempotently

## Related Contracts

- HTTP: `specs/contracts/http/search-api.md`
- Events consumed: `specs/contracts/events/product-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| ProductCreated | product-service | search-service |
| ProductUpdated | product-service | search-service |
| ProductDeleted | product-service | search-service |
| StockChanged | product-service | search-service |
