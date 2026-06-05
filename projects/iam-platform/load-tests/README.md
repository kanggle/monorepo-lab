# Load Tests (k6)

k6 scenarios that exercise the Global Account Platform stack against the gateway.

## Prerequisites

- Full docker-compose stack running: `docker compose up -d`
- All 5 services reachable via gateway (`http://localhost:8080` by default)
- Either k6 installed locally (https://k6.io/docs/get-started/installation/), or Docker available for the `grafana/k6` image

## Scenarios

| File | Purpose |
|---|---|
| `scenarios/auth-load-test.js` | login -> refresh -> logout loop, ramping VUs |
| `scenarios/signup-load-test.js` | constant-arrival-rate signup at target RPS |

## Run

Local k6:

```bash
k6 run load-tests/scenarios/auth-load-test.js
k6 run -e RPS=20 load-tests/scenarios/signup-load-test.js
```

Docker-based k6:

```bash
docker run --rm --network host \
  -v "$PWD/load-tests:/work" \
  -e BASE_URL=http://localhost:8080 \
  grafana/k6:0.50.0 run /work/scenarios/auth-load-test.js
```

Batch runner:

```bash
./load-tests/run-all.sh auth
```

## Environment variables

- `BASE_URL` — gateway base URL (default `http://localhost:8080`)
- `RPS` — target signup rate for `signup-load-test.js` (default `10`)

## Observing results

While a test runs, open Grafana at http://localhost:3000 and inspect:

- **Gateway Service Overview** — request rate / p99 latency / 429 rate
- **Auth Service Overview** — login success/failure, refresh rotation
- **Account Service Overview** — signup rate, outbox pending
- **System Overview** — error rate and latency heatmap across all services
