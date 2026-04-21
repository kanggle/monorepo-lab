# Service Overview

## Service
`user-service`

## Responsibility
Owns user profile data and shipping address management for authenticated users.

## In Scope
- user profile creation upon UserSignedUp event consumption
- user profile query and update
- shipping address CRUD (add, update, delete, list)
- default address designation
- user withdrawal (profile status transition to WITHDRAWN)
- user profile and withdrawal domain event publishing
- admin user list and detail query

## Out of Scope
- authentication and credential management (owned by auth-service)
- order processing (owned by order-service)
- payment processing (owned by payment-service)
- product catalog management (owned by product-service)

## Owned Data
- user profile (userId, email, name, nickname, phone, profileImageUrl, status)
- shipping addresses (label, recipientName, phone, zipCode, address1, address2, isDefault)

## Published Interfaces
- user HTTP APIs defined in `specs/contracts/http/user-api.md`
- user domain events: UserProfileUpdated, UserWithdrawn (defined in `specs/contracts/events/user-events.md`)

## Dependent Systems
- auth-service (consumes UserSignedUp event to create initial profile)
- messaging infrastructure (event consumption and publication)
- persistence (relational database)
