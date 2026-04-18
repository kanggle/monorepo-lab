---
name: indexing
description: Database index design and optimization
category: database
---

# Skill: Database Indexing

Patterns for index design and optimization in PostgreSQL.

Prerequisite: read `platform/coding-rules.md` (Database section) before using this skill.

---

## When to Add an Index

| Query Pattern | Index Type |
|---|---|
| Exact match (`WHERE email = ?`) | B-tree (default) |
| Range query (`WHERE created_at > ?`) | B-tree |
| Composite filter (`WHERE user_id = ? AND status = ?`) | Composite B-tree |
| Uniqueness constraint | Unique index |
| Foreign key column | B-tree |
| Full-text search | Use Elasticsearch instead |

---

## Index Naming Convention

Pattern: `idx_{table}_{columns}` or `uq_{table}_{columns}` for unique.

```sql
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_orders_user_status ON orders (user_id, status);
CREATE UNIQUE INDEX uq_payments_order_id ON payments (order_id);
CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);
```

---

## Composite Index Column Order

Put the most selective (highest cardinality) column first, unless the query always filters on a specific column.

```sql
-- Good: user_id is always present in queries, status filters further
CREATE INDEX idx_orders_user_status ON orders (user_id, status);

-- Bad: status has low cardinality, user_id is more selective
CREATE INDEX idx_orders_status_user ON orders (status, user_id);
```

**Rule:** A composite index on `(A, B)` supports queries on `A` alone, but NOT queries on `B` alone.

---

## Common Index Patterns (Generic Examples)

| Pattern | Example | Purpose |
|---|---|---|
| Unique on natural key | `uq_<table>_<column>` (e.g., `uq_users_email`) | Enforce business uniqueness |
| FK lookup | `idx_<child>_<parent>_id` (e.g., `idx_refresh_tokens_user_id`) | Reverse navigation + join |
| Compound query | `idx_<table>_<col1>_<col2>` (e.g., `idx_orders_user_status`) | Multi-column WHERE/ORDER BY |
| Outbox polling | `idx_<outbox>_status_created` | Poll pending events in FIFO order |
| Idempotency | `uq_<table>_<request-id>` | Dedup on repeated writes |

Per-project indexes should be declared in that project's `specs/services/<service>/` or migration files, not in this shared skill file.

---

## Adding Indexes in Migrations

```sql
-- V6__add_order_indexes.sql
CREATE INDEX idx_orders_user_status ON orders (user_id, status);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
```

For large tables in production, use `CONCURRENTLY`:

```sql
CREATE INDEX CONCURRENTLY idx_orders_created_at ON orders (created_at);
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| No index on foreign key columns | Always index FK columns — JOINs and cascades use them |
| Over-indexing (index on every column) | Only index columns used in WHERE, JOIN, ORDER BY |
| Wrong composite column order | Put highest-selectivity column first |
| Missing index on outbox `(status, created_at)` | Required for efficient outbox polling |
