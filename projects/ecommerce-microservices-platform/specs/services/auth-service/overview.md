# Service Overview

## Service
`auth-service`

## Responsibility
Owns authentication and authentication-related identity flows.

## In Scope
- signup (account registration and credential creation)
- login
- logout
- token refresh
- OAuth/social login (Google, Naver)
- authentication session handling
- authentication-related security coordination
- authentication audit logging (login attempts, token refresh, logout events)
- authentication domain event publishing
- concurrent session management (per-user session limit and inactivity timeout)
- user withdrawal handling (consuming UserWithdrawn event to deactivate account and revoke tokens)

## Out of Scope
- user profile management
- order processing
- payment processing
- unrelated organization policies unless explicitly assigned

## Owned Data
- authentication credentials
- refresh token or session-related state
- authentication audit log (login attempts, token events, session changes)
- per-user active session registry

## Published Interfaces
- authentication HTTP APIs defined in `specs/contracts/http/auth-api.md`
- authentication domain events defined in `specs/contracts/events/auth-events.md`

## Dependent Systems
- user-service (via published event contract: UserWithdrawn)
- messaging infrastructure (for event consuming and publishing)
- persistence (relational database for credentials and audit log)
- cache (Redis for refresh tokens and session state)