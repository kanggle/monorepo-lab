# Service Overview

## Service
`notification-service`

## Responsibility
Owns notification delivery across multiple channels (email, SMS, push) and user notification preference management.

## In Scope
- notification delivery triggered by domain events
- notification history query per user
- notification template management (admin)
- user notification preference management (opt-in/out per channel)
- idempotent event processing (duplicate event detection via event_id)

## Out of Scope
- user profile management (owned by user-service)
- order processing (owned by order-service)
- payment processing (owned by payment-service)
- business logic beyond notification delivery

## Owned Data
- notification (notificationId, recipient, channel, subject, body, status, sent/failed timestamps)
- notification template (templateId, type, channel, subject template, body template)
- user notification preference (userId, channel opt-in/out settings)

## Published Interfaces
- notification HTTP APIs defined in `specs/contracts/http/notification-api.md`
- no domain events published

## Dependent Systems
- messaging infrastructure (consumes events from order-service, payment-service, shipping-service, auth-service)
- persistence (relational database)
- external delivery channels (email, SMS, push providers)
