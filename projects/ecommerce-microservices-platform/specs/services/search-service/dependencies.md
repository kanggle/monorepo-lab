# Service Dependencies

## Service
`search-service`

## Allowed Direct Dependencies
- Elasticsearch (search index store)
- Messaging infrastructure (event consumption)
- Shared technical libraries allowed by platform policy

## Allowed Service Interactions
- Consumes product events published by product-service (via event contracts)
- Exposes search query HTTP API to gateway-service (via HTTP contracts)

## Consumes From

| Source | Events | Purpose |
|---|---|---|
| product-service | ProductCreated, ProductUpdated, ProductDeleted, StockChanged | Product index synchronization |

## Publishes To
- None (search-service is a read-only query service, it does not publish domain events)

## Forbidden Dependencies
- direct database access to product-service or any other service
- importing product-service internal code or entity models
- calling product-service HTTP APIs to sync index (must use events only)
- owning product source-of-truth data

## Notes
search-service index data is derived and eventually consistent.
If index and product-service data diverge, product-service is the authoritative source.
All dependency changes must be reflected in related specs and contracts first.
