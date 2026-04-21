# Service Dependencies

## Service
`batch-worker`

## Allowed Direct Dependencies
- shared technical libraries allowed by platform policy
- own database (job execution history only)
- messaging infrastructure (Kafka — event consumption and publication)

## Allowed Service Interactions
- through published HTTP contracts (read-only)
- through published event contracts

## Consumes From

| Source | Contract | Purpose |
|---|---|---|
| product-service | `product-api.md` | Source data for Elasticsearch index consistency check |
| search-service | `search-api.md` | Index data for Elasticsearch index consistency check |

## Publishes To
- None at current scope (may publish batch completion events in the future — contracts must be defined in `specs/contracts/events/` before implementation)

## Forbidden Dependencies
- direct database access to another service (per `service-boundaries.md`)
- importing another service's internal code
- depending on another service's internal entity model
- owning primary domain state (batch-worker operates on other services' data via published contracts only)
- calling non-public endpoints

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.
