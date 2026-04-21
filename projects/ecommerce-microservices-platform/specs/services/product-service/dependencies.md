# Service Dependencies

## Service
`product-service`

## Allowed Direct Dependencies
- shared technical libraries allowed by platform policy
- own database (product and inventory data)
- messaging infrastructure (event publishing only)

## Allowed Service Interactions
- through published HTTP contracts
- through published event contracts

## Consumes From
- None at launch (product-service is a source, not a consumer of other domain services)

## Publishes To

| Target | Events | Purpose |
|---|---|---|
| search-service | ProductCreated, ProductUpdated, ProductDeleted, StockChanged | Product index synchronization |
| order-service | StockChanged | Stock reservation (if event-driven) |

## Forbidden Dependencies
- direct database access to another service
- importing another service's internal code
- depending on another service's internal entity model
- calling search-service directly (search-service consumes events autonomously)

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
