# Flyway dev seeds in iam-platform — placement, and how to recover a poisoned database

`TASK-MONO-524`, 2026-08-12. This document exists because a dev-only seed broke
production-shaped startup for every developer with an existing volume, and
**nothing in CI could ever have caught it.**

---

## 1. The incident

```
iam-auth-service-1   Exited (1)

FlywayValidateException: Validate failed: Migrations have failed validation
  Detected resolved migration not applied to database: 0032.
```

`auth-service` had two dev seeds numbered `V9001` / `V9002`. Under the `e2e`
profile Flyway merges `db/migration` (production) and `db/migration-dev` into
**one version sequence**, so after those seeds ran, `9002` was the highest
**applied** version in `flyway_schema_history`.

On 2026-08-11 `#3270` added production `V0032`. Flyway resolved it, saw
`32 < 9002`, classified it as an **out-of-order** migration, and — with the
default `out-of-order: false` — refused. Spring failed the `flywayInitializer`
bean and the container exited.

## 2. Why no test caught it, and no test could

CI and any fresh `docker compose up` create an **empty volume**. Everything then
applies in ascending order and nothing is ever out of order. The defect is only
reachable from a database that *already applied* the high band — a developer's
local volume, or a demo host that preserves volumes.

> A migration-ordering verdict taken on a fresh volume proves nothing. If you
> need to reproduce this class of defect: migrate a fresh DB fully, then
> `DELETE FROM flyway_schema_history WHERE version='NNNN'` for the production
> migration you want to arrive "later", and restart.

That is why the regression guard ([`scripts/check-dev-seed-migration-band.sh`](../../../scripts/check-dev-seed-migration-band.sh))
checks **file placement**, not runtime behaviour.

## 3. Measured behaviour — four experiments on the poisoned volume

Run against the real `auth_db` volume, `0032` row deleted to reproduce "a new
production migration arrives". Nothing here is inferred.

| | condition | result |
|---|---|---|
| **A** | code as shipped 2026-08-11 | 🔴 `Exited` — `Detected resolved migration not applied to database: 0032` |
| **B** | `V9xxx` files removed (simulates the `R__` conversion) | 🔴 `Exited` — **still out-of-order**. Flyway did *not* complain about the now-fileless `9001`/`9002` |
| **C** | `spring.flyway.out-of-order: true` | ✅ `healthy`, `0032` applied |
| **D** | B + `ignore-migration-patterns: "*:missing"` | 🔴 `9001`/`9002` surface as "applied migration not resolved locally" |

Two findings decided the fix:

1. **The defect lives in the history table, not in the file listing.** B proves a
   file-side change alone does not heal an existing database. The `9xxx` *rows*
   are what hold the highest-applied watermark.
2. **A missing VERSIONED migration is silently tolerated; a missing REPEATABLE
   is not.** In B, Flyway ignored `9001`/`9002` because they are `future`
   (version above everything resolved) and the default `ignore-migration-patterns`
   is `*:future`. D confirms it: setting the property to `*:missing` *replaces*
   that default, and the two rows immediately surface.
   🔴 Consequence: **never rename an already-applied `R__` file.** Its description
   is its identity, and a rename fails validation with no such tolerance.

## 4. What was changed

- **`auth-service` and `account-service`**: the eight `V9xxx` dev seeds became
  `R__NN_…` repeatable migrations. A repeatable has no version, so it can neither
  collide with a production number (the problem `TASK-MONO-207` used the band to
  solve) nor be out of order (the problem the band created). It also always runs
  **after** every versioned migration — the order seed data wants anyway.
  All eight are `INSERT IGNORE` only, so re-running on checksum change is a no-op.
  The `NN_` prefix is the ordering contract: repeatables run in description order,
  and `account-service`'s `R__06` depends on the tenant `R__05` seeds.
- **`spring.flyway.out-of-order: true`** in each service's `application-e2e.yml`
  — the *only* profile that loads `migration-dev`. This is what lets an
  already-poisoned host boot (experiment C). It is a compatibility shim, not a
  policy change; see § 6 for its sunset.
- **A regression guard** so a new high-band seed cannot arrive again (§ 7).

### Both services, not just the broken one

`account-service` had **not** crashed. Its production timeline was at `V0028`,
already applied, so nothing was pending. It was exactly one migration from the
same failure — the position `auth_db` occupied on 2026-08-10. Fixing only the
service that had already broken would have scheduled the sibling's outage.

| service | dev seed band | production max | before | after |
|---|---|---|---|---|
| `auth-service` | `V9001`–`V9002` | `V0032` | 🔴 crash-looping | ✅ `R__01`/`R__02` |
| `account-service` | `V9001`–`V9006` | `V0028` | 🟡 breaks on `V0029` | ✅ `R__01`–`R__06` |
| `admin-service` | — (see § 5) | `V0045` | ✅ | untouched |

## 5. Why `admin-service` was NOT changed

`admin-service` also keeps versioned dev seeds — `V0014`, `V0023`, `V0028` in
`db/migration-dev`. They are safe, and the reason is worth stating so the
asymmetry is not re-investigated as a defect:

They are **interleaved into reserved gaps inside the production sequence**
(production has no `V0014`, `V0023` or `V0028`), and production has since reached
`V0045`. A dev seed *below* the production watermark can never make a later
production migration out of order. The high band can, and does.

The guard encodes exactly that difference as its predicate, so `admin-service`
passes on **measurement** rather than on an exception entry.

`admin-service` also has one `R__seed_demo_operator.sql`, whose header argued
against the high band before this incident happened:

> a dev seed placed in a high band (the V9000+ trick account-service uses) …
> would break **every developer's existing local database** the day someone adds
> V0046.

That prediction was correct, and it was the sibling service that paid it.

---

## 6. Recovering a database that already applied the band

**You do not have to delete the volume.** Deleting it costs you every demo row
you have seeded — operators, tenants, approvals — and it is not necessary.

With this change merged, an existing database boots on its own: `out-of-order:
true` lets the pending production migrations apply. If you want the database
back in a genuinely clean state (and the shim in § 4 retired), remove the
watermark rows:

```sql
-- auth_db / account_db, dev + demo hosts only. Safe: the seeds these rows point
-- at are now R__ repeatables that re-apply themselves on the next startup, and
-- every statement in them is INSERT IGNORE.
DELETE FROM flyway_schema_history WHERE CAST(version AS UNSIGNED) >= 9000;
```

After that the highest applied version is the production one again, ordering is
a real invariant for that database, and `out-of-order: true` is a no-op.

**Sunset condition for `out-of-order: true`:** once no database in circulation
carries a `version >= 9000` row, the two `application-e2e.yml` entries can be
deleted. Until then they are the difference between "the demo host boots" and
"the demo host crash-loops with a message that names the wrong file".

### If you see a *different* failure after editing a seed

```
Detected applied migration not resolved locally: <description>.
```

That is a **repeatable** whose file was renamed or deleted. Flyway does not
tolerate this (§ 3, finding 2). Restore the filename, or run `flyway repair`.

---

## 7. The guard

[`scripts/check-dev-seed-migration-band.sh`](../../../scripts/check-dev-seed-migration-band.sh),
wired as the `dev-seed-migration-band` job in `.github/workflows/ci.yml`.

Predicate: for every `<svc>/db/migration-dev/V<n>__*.sql`, `n` must be `<=` the
highest version in `<svc>/db/migration/`. `R__` files are out of scope by
construction. Zero discovered `migration-dev` directories is a **failure**, not a
pass — a glob that stops matching must not read as "everything is fine".

Verified to bite (2026-08-12):

| negative | result |
|---|---|
| new `V9007__` in `auth-service/migration-dev`, **not staged** | 🔴 RED (reachability: the guard reads untracked files too) |
| `Vabc__` unparseable version | 🔴 RED (fail-closed, not skipped) |
| repo with no `migration-dev` at all | 🔴 RED (0 findings ≠ pass) |
| `V0030__` in `auth-service/migration-dev` (inside range) | ✅ GREEN — the predicate is a **range** check, not "no dev seeds" |
| control: none of the above | ✅ GREEN |
