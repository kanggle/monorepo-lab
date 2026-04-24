# Service Overview

## Service
`product-service`

## Responsibility
Owns the product catalog, including product definitions, variants, categories, pricing, and inventory stock levels.

## In Scope
- Product registration, modification, and deletion
- Product variant and option management (size, color, etc.)
- Category and tag management
- Pricing and discount rate management
- Inventory stock tracking and adjustment
- Product status management (on-sale, sold-out, hidden)
- Product-related event publishing

## Out of Scope
- Order processing and payment
- Search indexing (search-service consumes product events to maintain its own index)
- User authentication
- Review and rating management unless explicitly assigned

## Owned Data
- Product master data
- Product variant and option data
- Category hierarchy
- Pricing and discount configuration
- Inventory stock records

## Published Interfaces
- Product HTTP APIs defined in `specs/contracts/http/product-api.md`
- Product domain events defined in `specs/contracts/events/product-events.md`

## Dependent Systems
- Messaging infrastructure (event publishing)
- Persistence components
- Related services through approved contracts only
