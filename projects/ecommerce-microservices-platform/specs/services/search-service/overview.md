# Service Overview

## Service
`search-service`

## Responsibility
Owns the product search experience. Maintains a denormalized search index by consuming events from product-service and provides search query APIs to clients.

## In Scope
- Product search API (keyword, filter, sort, pagination)
- Search index management (index creation, update, deletion)
- Consuming product events to keep the index in sync
- Faceted filtering (category, price range, attributes)
- Search ranking and relevance configuration

## Out of Scope
- Product data ownership (product-service owns the source of truth)
- Order and payment processing
- User authentication
- Review ranking unless explicitly assigned

## Owned Data
- Search index data (derived from product-service events, not authoritative)
- Search configuration and ranking rules

## Published Interfaces
- Search query HTTP APIs defined in `specs/contracts/http/search-api.md`

## Dependent Systems
- product-service (via published event contracts: ProductCreated, ProductUpdated, ProductDeleted, StockChanged)
- Elasticsearch (primary search store)
- messaging infrastructure (event consumption)
