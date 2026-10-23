# Performance Metrics Contract

This contract defines the **one response shape** every domain overview's performance-metrics endpoint returns — the «실적» strip at the top of a console domain overview: the value of each metric over the current period-to-date, the same metric over a same-length previous window, and a daily series.

It is the **D2 · D3** deliverable of [ADR-MONO-084](../../docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md) (ACCEPTED). Per that ADR:

- **Only the shape and the windows are shared.** Which metrics a domain reports, and what each one counts (included states, the timestamp a row is attributed to, exclusions), is the **owning domain's contract** — each domain's HTTP contract names its keys and cites this file (ADR-MONO-084 D5).
- **The base path, authentication and tenancy stay domain-owned.** A domain serves the endpoint on its own operator-facing service, under its own path convention (e.g. `…/admin/…/metrics`), behind the same authorization as its other operator reads.
- **Each domain aggregates on its own side** (ADR-MONO-084 D4). The console does not count list pages to produce these numbers.

> **HARDSTOP-03 note.** This file lives under `platform/contracts/` (shared regulation) and is **project-agnostic**: it names no service, entity or metric key. Examples use placeholder keys.

Implementation: `libs/java-common` — `com.example.common.metrics.PerformanceMetrics` (the DTO) and `com.example.common.time.KstPeriodBounds` (`currentWindow` · `previousWindow` · `currentDates`). Console: `shared/lib/kst-period.ts` (`kstMetricsWindows`), `shared/api/performance-metrics.ts` (parser), `shared/ui/PerformanceStrip.tsx`.

---

## 1. Request

```
GET <domain-owned path>/metrics?period=week|month
```

| Parameter | Required | Values | Notes |
|---|---|---|---|
| `period` | yes | `week`, `month` (case-insensitive) | Anything else is a **400** in the domain's own error vocabulary. There is no `today`: a domain's existing «today» count stays where it is (ADR-MONO-084 OD-4). |

A domain MAY add its own filters (e.g. one location among several). Such a filter narrows every metric and every series row alike.

## 2. Windows (normative)

All windows are cut on the **KST (`Asia/Seoul`) calendar**, whatever zone the server, its database or its host runs in. Korea observes no daylight saving, so KST is a fixed `+09:00`.

| | `week` | `month` |
|---|---|---|
| `current.from` | this Monday 00:00 KST | the 1st of this month 00:00 KST |
| `current.to` | now | now |
| `previous.from` | the Monday before 00:00 KST | the 1st of the previous month 00:00 KST |
| `previous.to` | `previous.from` + (`current.to` − `current.from`) | the same, **but never later than `current.from`** |

- Windows are **half-open** `[from, to)`.
- The previous window is the **same elapsed length**, not the whole previous period: on the 23rd at 14:05 this month is compared with the previous month's 1st through 23rd 14:05. Comparing a part-month with a whole month would read as a fall every day of the month.
- When the previous month is shorter, its window stops at its end: on March 30 the previous window is February 1 → March 1.
- On the wire, `from` and `to` are ISO-8601 with the `+09:00` offset, **truncated to whole seconds** (`2026-10-23T14:05:00+09:00`). The query runs on the exact bounds; the truncation is display precision.
- A server must take these windows from `KstPeriodBounds` and **must not** cut its own KST or UTC days (ADR-MONO-084 D3).

The fixture [`fixtures/performance-metrics-windows.json`](fixtures/performance-metrics-windows.json) is the worked table of these rules. The Java implementation and the console both test against it, so the two cannot drift.

## 3. Response

**200**

```json
{
  "period": "month",
  "timezone": "Asia/Seoul",
  "current":  { "from": "2026-10-01T00:00:00+09:00", "to": "2026-10-23T14:05:00+09:00" },
  "previous": { "from": "2026-09-01T00:00:00+09:00", "to": "2026-09-23T14:05:00+09:00" },
  "metrics": [
    { "key": "<money-metric>", "unit": "money", "currency": "KRW", "current": "12340000", "previous": "11020000" },
    { "key": "<count-metric>", "unit": "count", "currency": null,  "current": "812",      "previous": "790" }
  ],
  "series": [
    { "date": "2026-10-01", "values": { "<money-metric>": "410000", "<count-metric>": "27" } },
    { "date": "2026-10-02", "values": { "<money-metric>": "0",      "<count-metric>": "0" } }
  ]
}
```

| Field | Type | Nullable | Description |
|---|---|---|---|
| `period` | string | no | Echo of the request: `week` or `month`. |
| `timezone` | string | no | The IANA zone the windows and dates are cut in. Today always `Asia/Seoul`; the field holds the place for a per-tenant zone (ADR-MONO-084 D3). |
| `current` / `previous` | object | no | `{ from, to }` — § 2. |
| `metrics` | array | no | One entry per metric, **in display order**. May be empty. |
| `metrics[].key` | string | no | Unique within the response. A domain reporting one metric per currency suffixes the key with `:` + the currency code (`<amount-metric>:USD`). |
| `metrics[].unit` | string | no | `count` or `money`. |
| `metrics[].currency` | string | yes | ISO-4217 for `money`; `null` for `count`. Never mixed: each money metric is one currency, and amounts in different currencies are **never added together or converted** (ADR-MONO-084 D2 · OD-3). |
| `metrics[].current` / `previous` | string | no | The value over each window, as an **integer string**. |
| `series` | array | no | One row per KST date of `current`, first to last, both ends included — never empty for a non-empty `metrics`. |
| `series[].date` | string | no | `YYYY-MM-DD`, a KST date. |
| `series[].values` | object | no | Metric key → integer string, for **every** key of `metrics`. A day with no activity is `"0"`, never absent. |

### Values are integer strings

Every value — counts and money alike — is a JSON **string** holding an integer: `"812"`, `"12340000"`. Money is in **minor units** of its currency (KRW: won; USD: cents). This is the console's money invariant (`console-integration-contract.md` § 2.4.7, «F5»): the console renders money from the string and never turns it into a floating-point number. Counts use the same form so that a client has one parse path, not two.

A value MAY be negative where the domain's definition allows it (for example a net figure). The domain's contract says so when it does.

### What a value means

The meaning of each key — the states it includes, the timestamp a row is attributed to, what it excludes, and whether a past day's value can still change (a late transition attributed to an earlier timestamp) — is **normative in the owning domain's contract**, one line per key. The principle shared by all of them (ADR-MONO-084 D5): **money is counted only once it is settled.**

## 4. Errors

| Status | When | Client behaviour (console) |
|---|---|---|
| 400 | `period` missing or unknown | — (a console bug) |
| 401 | no / invalid token | whole-session re-login |
| 403 | the operator may not read this domain's metrics | the strip says «권한 없음»; the rest of the overview renders |
| 5xx / timeout | anything else | the strip says «불러올 수 없음»; the rest of the overview renders |

A failed strip is **never shown as zero**: «0» reads as «nothing happened».

## 5. Verification obligations for a conforming endpoint

- Window tests with an injected `Clock`, not the host clock — at least one case across KST midnight and one across the end of a short month.
- A control group per metric: a row in an excluded state does not move the value; a row in an included state does.
- A tenant-isolation test.
- The console side reads the response through `shared/api/performance-metrics.ts`; a response that does not parse is treated as a failure, never as a partial success.
