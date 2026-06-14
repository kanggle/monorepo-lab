# Feature: Product Management

## Purpose

Provides product catalog management for the platform. Covers product registration, modification, deletion, variant/option management, inventory stock control, and product data synchronization to the search index.

## Related Services

| Service | Role |
|---|---|
| product-service | Primary owner — product CRUD, variant management, inventory stock adjustment, event publishing |
| search-service | Consumes product events to maintain denormalized search index |
| platform-console | Admin UI for product CRUD and inventory management |
| gateway-service | Request routing and admin role authorization |

## User Flows

### Product Registration (Admin)

1. Admin sends POST /api/admin/products with product details and variants
2. product-service validates input, creates product with variants
3. product-service publishes ProductCreated event
4. search-service consumes event and indexes the new product

### Product Update (Admin)

1. Admin sends PATCH /api/admin/products/{productId} with partial update
2. product-service applies changes (name, description, price, status)
3. product-service publishes ProductUpdated event
4. search-service consumes event and updates the search index

### Product Deletion (Admin)

1. Admin deletes product via admin API
2. product-service marks product as deleted
3. product-service publishes ProductDeleted event
4. search-service consumes event and removes product from index

### Stock Adjustment (Admin)

1. Admin sends PATCH /api/admin/products/{productId}/stock with variantId, quantity, reason
2. product-service adjusts stock level for the specified variant
3. product-service publishes StockChanged event
4. search-service updates stock in index; order-service reacts if reason is ORDER_RESERVED

### Product Listing (Public)

1. Client sends GET /api/products with optional filters (name, categoryId, status, pagination)
2. product-service returns paginated product list

### Product Detail (Public)

1. Client sends GET /api/products/{productId}
2. product-service returns product detail including all variants

## Business Rules

- Product statuses: ON_SALE, SOLD_OUT, HIDDEN
- Each product can have multiple variants (size, color, etc.) with individual stock and additional price
- Stock adjustments publish StockChanged event regardless of direction (increase/decrease)
- StockChanged reasons: RESTOCK, ORDER_RESERVED, ORDER_CANCELLED, ADMIN_ADJUSTMENT
- Admin endpoints require admin role (enforced via gateway X-User-Role header)
- Optimistic locking applied for concurrent product modifications

## Related Contracts

- HTTP: `specs/contracts/http/product-api.md`
- Events: `specs/contracts/events/product-events.md`

## Related Events

| Event | Publisher | Consumers |
|---|---|---|
| ProductCreated | product-service | search-service |
| ProductUpdated | product-service | search-service |
| ProductDeleted | product-service | search-service |
| StockChanged | product-service | search-service, order-service |
