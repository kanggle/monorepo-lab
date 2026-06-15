> **DEPRECATED — 2026-05-04 (TASK-BE-132). Historical reference only; see [README.md](README.md).**

# Service Dependencies

## Service
`auth-service`

## Allowed Direct Dependencies
- shared technical libraries allowed by platform policy
- own database
- own cache if required
- platform-approved security components

## Allowed Service Interactions
- through published HTTP contracts
- through published event contracts

## Consumes From
- None (auth-service is a source of authentication events, not a consumer of other domain events)

## Publishes To

| Target | Events | Purpose |
|---|---|---|
| user-service | UserSignedUp | Initial user profile creation |
| notification-service (future) | UserSignedUp | Welcome notification |

## Forbidden Dependencies
- direct database access to another service
- importing another service's internal code
- depending on another service's internal entity model
- bypassing gateway or platform communication rules where applicable

## Notes
All dependency changes that affect service boundaries must be reflected in related specs and contracts first.