# Service Overview

## Service
`review-service`

## Responsibility
Owns product review and rating management, including review creation with purchase verification and average rating calculation.

## In Scope
- review CRUD (create, update, delete) with purchase verification
- review listing by product
- user review history query
- average rating calculation per product
- domain event publishing (ReviewCreated, ReviewUpdated, ReviewDeleted)

## Out of Scope
- product catalog management (owned by product-service)
- order processing (owned by order-service)
- search indexing of reviews (owned by search-service)

## Owned Data
- review (reviewId, userId, productId, rating, title, content, status, timestamps)
- average rating per product (materialized view or cache)

## Published Interfaces
- review HTTP APIs defined in `specs/contracts/http/review-api.md`
- review domain events defined in `specs/contracts/events/review-events.md`

## Dependent Systems
- persistence (relational database)
- messaging infrastructure (event publication)
- order-service (synchronous HTTP call for purchase verification)
