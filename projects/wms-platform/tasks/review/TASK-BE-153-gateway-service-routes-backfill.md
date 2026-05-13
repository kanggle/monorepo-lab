# Task ID

TASK-BE-153

# Title

`gateway-service` application.yml + application-standalone.yml — 5 service routes 등록 (inbound/inventory/outbound/notification/admin) — TASK-BE-152 audit § #10 finding closure

# Status

review

# Owner

backend

# Task Tags

- code
- gateway
- infra
- adr-followup
- bugfix
- spec-drift

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

TASK-BE-152 (2026-05-14, commit `ec2bb73c`) inventory-service Open Items audit § "#10 gateway route" portfolio-wide gap finding closure.

**정찰 결과 (commit `6fe554e5` 시점, 2026-05-14 본 세션)**:

[`projects/wms-platform/apps/gateway-service/src/main/resources/application.yml`](../../apps/gateway-service/src/main/resources/application.yml) L54-65 의 `spring.cloud.gateway.routes` 가 **master-service 만 등록** — inbound / inventory / outbound / notification / admin **5 service 모두 미등록**. [`application-standalone.yml`](../../apps/gateway-service/src/main/resources/application-standalone.yml) 도 동일 gap.

대조군 (cross-project audit):
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/resources/application.yml` = 정상 routes 등록.
- `projects/fan-platform/apps/gateway-service/src/main/resources/application.yml` = 정상 routes 등록 (community-service path rewrite).
- `projects/global-account-platform/apps/gateway-service/src/main/resources/application.yml` = 정상 routes 등록 (OIDC/OAuth2 endpoints).
- **wms-platform = wms-only gap** (portfolio-wide audit 결과 확정).

**Hostname routing 과의 책임 분담**: `docker-compose.yml` L6 "gateway-service is reached via http://wms.local/" 명시 — TASK-MONO-024 의 Traefik hostname routing (cross-project 분리: wms.local / ecommerce.local 등). 그러나 hostname routing 은 cross-project layer, gateway routes 는 intra-project routing layer — **두 layer 모두 작동 의도**. 따라서 hostname routing 존재가 gateway routes 부재의 justification 안 됨.

**실 production impact (현재)**:
- wms portfolio 의 5 service 가 gateway 우회 직접 ports 호출 가능성 (host 환경 측 ports 노출).
- 또는 외부 사용자 가 gateway URL 접근 시 master-service 만 reachable, 다른 5 service 는 404.
- portfolio 의 "production gateway" 깊이 증명 측면에서 약점.

본 task = **5 service routes 등록 + filters / rate-limit 설정 + spec align**. master-service route pattern 답습 (id / uri / predicates / filters).

**본 세션 (spec author only)**: ready 상태 entry 만, fix (impl) = 다음 세션.

provenance:
- TASK-BE-152 audit § "#10 gateway route ❌ MISSING — portfolio-wide gap: gateway-service/application.yml 이 master-service routes 만 등록..."
- 정찰 commit `6fe554e5` (TASK-MONO-090 close 직후, 본 세션 16 commit 누적 시점).
- Cross-project audit 결과: wms-only gap 확정.

---

# Scope

## In Scope

### A. `application.yml` 의 `spring.cloud.gateway.routes` 5 service entry 추가

master-service route (L55-65) pattern 답습. 각 service 별:

```yaml
- id: <service>-service
  uri: ${<SERVICE>_SERVICE_URI:http://localhost:<port>}
  predicates:
    - Path=/api/v1/<service>/**
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: <rate>
        redis-rate-limiter.burstCapacity: <burst>
        redis-rate-limiter.requestedTokens: 1
        key-resolver: "#{@clientIpKeyResolver}"
```

**5 service base path 결정** (각 service 의 `specs/contracts/http/<service>-api.md` base path 답습):
- inbound-service → `/api/v1/inbound/**` (또는 `/api/inbound/**` 등 — spec 답습 결정)
- inventory-service → `/api/v1/inventory/**`
- outbound-service → `/api/v1/outbound/**`
- notification-service → `/api/v1/notification/**`
- admin-service → `/api/v1/admin/**`

**Rate-limit policy 결정** (master-service replenishRate=100/burst=200 답습 vs service 별 customize). 본 task 의 impl 단계 결정.

**URI env var naming**: `INBOUND_SERVICE_URI` / `INVENTORY_SERVICE_URI` 등 (master-service `MASTER_SERVICE_URI` 답습).

### B. `application-standalone.yml` 동기

standalone yml 도 동일 5 service routes 추가 (각 환경의 URI default 차이만).

### C. spec 갱신 (필요 시)

- `specs/services/gateway-service/architecture.md` (있다면) 의 routing 표 갱신.
- 각 service 의 `specs/contracts/http/<service>-api.md` 의 base path 와 align (path 결정 source-of-truth).
- BE-152 audit body 의 § #10 entry (이미 closed task 라 본문 직접 수정 불가, option C-1 INDEX outcome 으로 archival).

### D. CI 검증

- application.yml 변경이 `wms` flag trigger → wms 의 PR-time + integration jobs 자연 검증.
- gateway-service integration test (있다면) 의 routes 검증 추가 가능 (별 follow-up).

## Out of Scope

- **Portfolio-wide gateway routes audit** — 다른 3 project (ecommerce/fan/gap) 정상 확정, wms-only gap. 별 task 불필요.
- **Traefik hostname routing 변경** — cross-project layer, 본 task 와 무관.
- **gateway-service architecture.md 갱신** — file 자체 존재 확인 후 결정. impl 단계.
- **5 service 의 각자 implementation (controllers, etc.)** — routes 등록만 하고, 실 controllers 가 spec 과 align 되는지는 spec 측 audit (별 task 후보).

---

# Acceptance Criteria

### Impl PR (다음 세션)

- [ ] `application.yml` 의 `spring.cloud.gateway.routes` 5 service entry 추가 (master 기존 entry 보존).
- [ ] `application-standalone.yml` 동기.
- [ ] 5 base path = spec `contracts/http/<service>-api.md` 와 align.
- [ ] Rate-limit policy 결정 + 적용 (master 답습 vs customize).
- [ ] URI env var 5개 추가 (`INBOUND_SERVICE_URI` 등).
- [ ] CI = `wms` flag trigger 자연 검증 (`wms gateway-master e2e-tests` job).
- [ ] task lifecycle ready → review.
- [ ] [`wms tasks/INDEX.md`](../INDEX.md) 동기.

### Close chore PR

- [ ] task Status review → done.
- [ ] git mv tasks/review → tasks/done.
- [ ] [`wms tasks/INDEX.md`](../INDEX.md) ## review 제거, ## done append 1-line outcome.

---

# Related Specs

- [`projects/wms-platform/apps/gateway-service/src/main/resources/application.yml`](../../apps/gateway-service/src/main/resources/application.yml) (target file)
- [`projects/wms-platform/apps/gateway-service/src/main/resources/application-standalone.yml`](../../apps/gateway-service/src/main/resources/application-standalone.yml) (target file)
- [`projects/wms-platform/specs/contracts/http/inbound-service-api.md`](../../specs/contracts/http/inbound-service-api.md) (base path source)
- [`projects/wms-platform/specs/contracts/http/inventory-service-api.md`](../../specs/contracts/http/inventory-service-api.md)
- [`projects/wms-platform/specs/contracts/http/outbound-service-api.md`](../../specs/contracts/http/outbound-service-api.md)
- [`projects/wms-platform/specs/contracts/http/notification-service-api.md`](../../specs/contracts/http/notification-service-api.md) (있다면)
- [`projects/wms-platform/specs/contracts/http/admin-service-api.md`](../../specs/contracts/http/admin-service-api.md) (있다면)
- [`projects/wms-platform/tasks/done/TASK-BE-152-inventory-service-open-items-audit-and-list-correction.md`](../done/TASK-BE-152-inventory-service-open-items-audit-and-list-correction.md) (audit source, § #10 finding)

---

# Related Contracts

본 task = gateway routing 확장. HTTP API contract 자체 변경 0 — 단 `gateway URL prefix` (예: `/api/v1/inbound/**`) 가 spec 의 base path 와 byte-level align 되어야 함.

---

# Target Service

`gateway-service` (wms-platform). 2 file (application.yml + application-standalone.yml) edit + 5 service routes.

---

# Architecture

### Layer 분담

```
Cross-project layer (hostname):
  wms.local        → wms gateway-service
  ecommerce.local  → ecommerce gateway-service
  fan.local        → fan gateway-service
  gap.local        → gap gateway-service
  (TASK-MONO-024 Traefik hostname routing)

Intra-project layer (path) — BE-152 § #10 gap target:
  wms.local/api/v1/master/**         → master-service          ← 현재 유일 등록
  wms.local/api/v1/inbound/**        → inbound-service         ← 본 task
  wms.local/api/v1/inventory/**      → inventory-service       ← 본 task
  wms.local/api/v1/outbound/**       → outbound-service        ← 본 task
  wms.local/api/v1/notification/**   → notification-service    ← 본 task
  wms.local/api/v1/admin/**          → admin-service           ← 본 task
```

두 layer 모두 작동 의도. 본 task = intra-project layer 의 5 service 등록.

---

# Implementation Notes

## master-service route (L55-65) pattern 답습

```yaml
- id: master-service
  uri: ${MASTER_SERVICE_URI:http://localhost:8081}
  predicates:
    - Path=/api/v1/master/**
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 100
        redis-rate-limiter.burstCapacity: 200
        redis-rate-limiter.requestedTokens: 1
        key-resolver: "#{@clientIpKeyResolver}"
```

본 task 의 5 service 도 동일 shape. 차이점:
- `id`: `<service>-service`.
- `uri`: `${<SERVICE>_SERVICE_URI:http://localhost:<port>}`. wms 5 service port = master 8081 + inbound 8082 + inventory 8083 + outbound 8084 + notification 8085 + admin 8086 (가정; impl 시 docker-compose 정확 확인).
- `Path`: `/api/v1/<service>/**` (각 service base path).
- `filters`: master 답습 = replenish 100 / burst 200. 단, 특정 service 가 더 strict / lenient 필요 시 customize (impl 시 결정).

## Rate-limit policy 결정 후보 (impl 시)

- **Option 1**: master 답습 (모든 service 100/200). 단순성.
- **Option 2**: service 별 customize (예: outbound 200/400 — shipment 트래픽 burst). 정확성.
- **Option 3**: shared default-filter (`default-filters` section) + service 별 override. 통일성.

본 task 의 impl 단계 결정.

## URI env var naming

master-service `MASTER_SERVICE_URI` 답습:
- `INBOUND_SERVICE_URI` (default `http://localhost:8082`)
- `INVENTORY_SERVICE_URI` (default `http://localhost:8083`)
- `OUTBOUND_SERVICE_URI` (default `http://localhost:8084`)
- `NOTIFICATION_SERVICE_URI` (default `http://localhost:8085`)
- `ADMIN_SERVICE_URI` (default `http://localhost:8086`)

각 default port = impl 시 docker-compose.yml + 각 service application.yml 확인.

## ADR / spec audit chain

- BE-152 audit § #10 (closed, 본 task 의 spec source).
- spec body 직접 수정 안 함 (option C-1 audit-only), INDEX outcome + 본 task body 가 source-of-truth.

## D4 churn impact

- 2 file `projects/wms-platform/apps/gateway-service/` touch.
- ~50 line addition (5 service × ~10 line).
- ADR-MONO-003a § D1.1 인접 (project-internal infrastructure 확장). D4 OVERRIDE 자연 적용 (wms-only).

---

# Edge Cases

- 5 service 의 base path 가 spec 에서 변경된 경우 (예: `/api/v2/...` 또는 path rewrite) → spec align 필요. 본 task impl 시 spec 정확 확인.
- 일부 service (예: admin-service) 가 internal-only path (`/internal/**`) 사용 시 → gateway 등록 제외 후보. impl 시 spec 결정.
- master-service 와의 `default-filters` 상속 = Spring Cloud Gateway 의 자연 동작. 명시 customize 시 override.
- Rate-limit policy 결정에 따라 redis dependency 증가 — wms 의 Redis 가 5 service 의 rate-limit 부담 가능 검증.

---

# Failure Scenarios

- 5 service routes 추가 후 wms gateway-master e2e-tests fail → master route 와의 정합성 확인 (path precedence 등).
- URI env var default 가 docker-compose 환경과 mismatch → service URI 실측 확인 후 fix.
- Rate-limit replenish / burst 가 traffic 패턴 wrong → tuning iteration.

---

# Test Requirements

- wms gateway-master e2e-tests (PR-time smoke) 회귀 0 (master route + 새 5 service routes 공존).
- 가능하면 새 e2e test class 추가: 5 service gateway routing 검증 (각 path 가 정확한 service 로 forwarding). 별 follow-up task 후보.
- production code = code-bearing config 변경 (yml 만, java 변경 0).

---

# Definition of Done

### Impl PR

- [ ] AC 완료.
- [ ] task lifecycle ready → review.

### CI verification

- [ ] wms PR-time + integration jobs SUCCESS.

### Close chore PR

- [ ] review → done, [`wms tasks/INDEX.md`](../INDEX.md) 동기.

---

# Provenance

- TASK-BE-152 audit § "#10 gateway route ❌ MISSING" finding (commit `ec2bb73c`, 2026-05-14).
- 정찰 commit `6fe554e5` (본 세션 16 commit 누적 시점) — wms-only gap 확정 (cross-project audit: ecommerce/fan/gap 모두 정상).
- 본 세션 (spec author only) — fix impl 다음 세션.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (substantial: 5 service routes 등록 + rate-limit policy 결정 + 2 file 동기 + e2e 검증).
