# Internal HTTP Contract: consumer site roles — grant / revoke (account-service)

> **TASK-MONO-752 (ADR-MONO-079 D5 · ADR-MONO-078 A § 4).** The first **writer** of
> `consumer_site_roles` over HTTP. A pool account (`accounts.tenant_id = 'consumer-pool'`) that
> becomes a member of an ecommerce seller gets the store site role `SELLER`; the seller's
> suspension / closure takes it away again. The store token's roles are `seed ∪ consumer_site_roles`
> of **that site only** (TASK-BE-615), so a role written here changes the store token and never the
> fan token.
>
> Caller today: ecommerce `product-service` (seller members). Data model:
> [`data-model.md` § consumer_site_roles](../../../services/account-service/data-model.md).

**Path prefix**: `/internal/tenants/{tenantId}/accounts/{accountId}/site-roles`
— `{tenantId}` is the **consumer site** (e.g. `ecommerce`), never `consumer-pool`; `{accountId}` is the pool account.

**Authentication / authorization**: identical to [account-internal-provisioning.md](./account-internal-provisioning.md)
— `Authorization: Bearer <IAM client_credentials JWT>` carrying `internal.invoke`, and the token's `tenant_id`
equals the path `{tenantId}` (ADR-MONO-076 tenant-scoped exchange — the IAM gateway enforces it on every
`/internal/tenants/{tenantId}/**` path). `X-Tenant-Id`, when present, must equal the path (`TenantScopeGuard`).
No new client registration or permission catalogue entry: the path is the existing routed tenant surface.

---

## Grantable roles — a closed list, per site

Only these `(site, role)` pairs can be written or removed here (`GrantableSiteRoles`):

| Site (`{tenantId}`) | Role | Why |
|---|---|---|
| `ecommerce` | `SELLER` | ADR-MONO-079 D5 — a person who is a member of a store seller |

🔴 Anything else is `400 SITE_ROLE_NOT_GRANTABLE`. The site role lands in a consumer token whose
gateway admits operator paths by role name, so an open role name would let a caller mint
`ECOMMERCE_OPERATOR` (or any other role) onto a shopper. Seed roles (`CUSTOMER`, `FAN`) are never
stored (data-model.md) and are not grantable either. Adding a pair is a decision, not a config change.

---

## PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:grant

Write `consumer_site_roles(accountId, {tenantId}, roleName)`. Idempotent: an already-held role is a
successful no-op (`changed=false`).

**Request**:
```json
{
  "roleName": "SELLER",
  "expectedEmail": "person@example.com",
  "operatorId": "product-service"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| `roleName` | string | Yes | ≤ 64 chars; must be a grantable pair with `{tenantId}` |
| `expectedEmail` | string | Yes | ≤ 320 chars. 🔴 **The email the caller's invitation was addressed to.** account-service compares it with the pool account's own email (case-insensitive, trimmed) and refuses on mismatch — the account email lives here, not in the caller |
| `operatorId` | string | No | ≤ 36 chars; stored as `granted_by` and in the audit row |

**Rules, in order** (the first that fails answers):

1. `{tenantId}` is a registered tenant → else `404 TENANT_NOT_FOUND`.
2. `({tenantId}, roleName)` is grantable and `{tenantId}` is a consumer site → else `400 SITE_ROLE_NOT_GRANTABLE`.
3. `{accountId}` is a `consumer-pool` account → else, if it is an account **of the site itself** (a site
   account not yet moved to the pool — it cannot hold `consumer_site_roles`), `409 SITE_ROLE_REQUIRES_POOL_ACCOUNT`;
   otherwise `404 ACCOUNT_NOT_FOUND`.
4. `expectedEmail` equals the account's email → else `403 SITE_ROLE_EMAIL_MISMATCH`. Nothing is written.
4b. 🔴 **The account's email is verified** (`accounts.email_verified_at` is set) → else `403 EMAIL_NOT_VERIFIED`.
   Nothing is written. (TASK-MONO-770 — ADR-MONO-080 D3 · rider R1, which amends ADR-MONO-079 D5.)
   Rule 4 alone proves only «the logged-in account *says* it is that address»; IAM never checked that the person
   owns it, so someone who signed up to the pool with *another person's* address could accept an invitation sent
   to that address. Rule 4b makes the email an identity before a company role is attached.
   - Checked **after** rule 4: a wrong address is the stronger, more specific answer.
   - Checked **before** the idempotency check below — an account that already holds the role (e.g. a member of a
     second seller) still has to be verified to be attached again. 🔵 It never revokes anything: an unverified
     account that already holds the role keeps it (ADR-MONO-080 D5 — verification is evidence at the moment of
     attaching, not a condition for keeping).
   - The predicate is the shared one (`VerifiedEmailRequirement`) that ADR-MONO-080 step 3 (`TASK-MONO-772`)
     reuses for operator-invitation acceptance — one home, not one copy per caller.
5. The account holds an **ACTIVE** membership of `{tenantId}` → else `409 SITE_MEMBERSHIP_REQUIRED`.
   🔵 **Membership rule (decided here): grant never creates a membership.** A membership is the site
   consent (ADR-MONO-078 D3 · `PUT …/consumer-members/{accountId}`); the person accepting is logged in to
   the store, which already requires an ACTIVE membership (no membership ⇒ no store token, TASK-BE-615).
   A missing / `LEFT` membership therefore means «not the logged-in store user» and is refused, not repaired.
   The FK `consumer_site_roles → consumer_site_memberships` would refuse the row anyway.

**Response 200 OK**:
```json
{
  "accountId": "0199de70-0000-7000-8000-000000000752",
  "tenantId": "ecommerce",
  "roles": ["SELLER"],
  "changed": true
}
```

`roles` = the account's complete `consumer_site_roles` on `{tenantId}` after the call (seed roles are not in it).

**Side effects** (only when `changed=true`): one `consumer_site_roles` row; one `account_status_history`
audit row (`tenant_id={tenantId}`, reason `OPERATOR_PROVISIONING_ROLES_REPLACE`,
`details={"action":"SITE_ROLE_GRANT","site":…,"role":…}`). **No outbox event** — `account.roles.changed`
describes `account_roles` of the account's own tenant; the next token issuance reads the new role directly.

**Errors**: 400 `VALIDATION_ERROR` · 400 `SITE_ROLE_NOT_GRANTABLE` · 403 `TENANT_SCOPE_DENIED` ·
403 `SITE_ROLE_EMAIL_MISMATCH` · 403 `EMAIL_NOT_VERIFIED` · 404 `TENANT_NOT_FOUND` · 404 `ACCOUNT_NOT_FOUND` ·
409 `SITE_ROLE_REQUIRES_POOL_ACCOUNT` · 409 `SITE_MEMBERSHIP_REQUIRED` · 401 `UNAUTHORIZED`

---

## PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:revoke

Delete `consumer_site_roles(accountId, {tenantId}, roleName)`. Idempotent: a role not held is a
successful no-op (`changed=false`).

**Request**:
```json
{ "roleName": "SELLER", "operatorId": "product-service" }
```

Rules 1–3 of grant apply (tenant · grantable pair · pool account). No email check, no verification check
(rule 4b) and no membership check: removing a role must work for any account that may hold it.

🔴 **Revoking a site role never touches the account or the membership.** The account stays `ACTIVE`
(no lock — ADR-MONO-079 D5: locking would also stop the person shopping and using the fan site), the
store membership stays `ACTIVE`, so the next store token is the seed alone (`["CUSTOMER"]`) and the fan
token is unchanged.

**Response 200 OK**: same shape as grant (`roles` after the call, `changed`).

**Side effects** (only when `changed=true`): the row is deleted; one audit row
(`details={"action":"SITE_ROLE_REVOKE",…}`). No outbox event.

**Errors**: 400 `VALIDATION_ERROR` · 400 `SITE_ROLE_NOT_GRANTABLE` · 403 `TENANT_SCOPE_DENIED` ·
404 `TENANT_NOT_FOUND` · 404 `ACCOUNT_NOT_FOUND` · 409 `SITE_ROLE_REQUIRES_POOL_ACCOUNT` · 401 `UNAUTHORIZED`

---

## What the caller owns (not this service)

- **Who** may hold the role — invitations, members, «another seller still needs this role» — is the
  caller's domain (product-service `seller_members`). This service only answers «is this the account the
  invitation named, and is it a pool member of this site».
- **Retry** — a failed revoke leaves the role in place; the caller keeps the member row in a state it can
  retry from (product-service: the member stays `ACTIVE` until a revoke succeeds).
