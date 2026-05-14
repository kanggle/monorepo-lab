# Task ID

TASK-BE-155

# Title

inventory / notification / admin `application.yml` + Dockerfile SERVER_PORT defaults — spec align (TASK-BE-153 surfaced follow-up 2/2)

# Status

done

# Owner

backend

# Task Tags

- code
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

TASK-BE-153 (commit `7e14a218`, 2026-05-14) impl PR 의 직속 follow-up 2/2 — code-side spec drift fix. BE-153 INDEX entry surfaced 2건 (inventory + admin port mismatch) 의 deep audit 시 **notification 도 drift 라는 사실 추가 surfacing** (commit body 의 본 task 본문).

**Spec source-of-truth** (2개 align):

1. `projects/wms-platform/infra/prometheus/prometheus.yml` (L23-77) — comment "Port assignments align with projects/wms-platform/apps/*/application.yml" 의 의도와 달리 prometheus 가 spec-following.
2. `projects/wms-platform/specs/services/gateway-service/architecture.md § Routes` (L82-86) — gateway routing 의 표준.

**Drift matrix**:

| Service | prometheus.yml | gateway arch.md spec | application.yml (code) | Dockerfile | 결정 |
|---|---|---|---|---|---|
| master | 8081 | 8081 | 8081 | 8081 | ✓ align |
| inbound | 8082 | 8082 | 8082 | 8082 | ✓ align |
| **inventory** | **8083** | **8083** | **8082** ❌ | **8082** ❌ | code → spec |
| outbound | 8084 | — | 8084 | 8084 | ✓ |
| **notification** | **8085** | — (event-consumer, no route) | **8086** ❌ | **8086** ❌ | code → spec |
| **admin** | **8086** | **8086** | **8087** ❌ | **8087** ❌ | code → spec |

**Collision side-effect**: inventory + inbound 둘 다 code default 8082 → host bootRun 동시 기동 불가능. notification (8086) + admin spec target (8086) 둘 다 8086 → 단일 service spec align 시 collision. **3 service 동시 변경 필수**.

provenance:
- TASK-BE-153 commit `7e14a218` body § "follow-up candidate" + INDEX entry (BE-153 surfaced 2건 + audit 시 notification 추가 surfacing).

---

# Scope

## In Scope

### A. inventory-service code → spec align

- `apps/inventory-service/src/main/resources/application.yml` SERVER_PORT `${SERVER_PORT:8082}` → `${SERVER_PORT:8083}`
- `apps/inventory-service/Dockerfile` EXPOSE `8082` → `8083`, HEALTHCHECK `http://localhost:8082` → `http://localhost:8083`

### B. notification-service code → spec align

- `apps/notification-service/src/main/resources/application.yml` SERVER_PORT `${SERVER_PORT:8086}` → `${SERVER_PORT:8085}`
- `apps/notification-service/Dockerfile` EXPOSE `8086` → `8085`, HEALTHCHECK `http://localhost:8086` → `http://localhost:8085`

### C. admin-service code → spec align

- `apps/admin-service/src/main/resources/application.yml` SERVER_PORT `${SERVER_PORT:8087}` → `${SERVER_PORT:8086}`
- `apps/admin-service/Dockerfile` EXPOSE `8087` → `8086`, HEALTHCHECK `http://localhost:8087` → `http://localhost:8086`

### D. README.md inventory `:8082` 표기 정정

- 2 occurrences (L70 architecture diagram + L338 bash quick-start comment) `:8082` → `:8083` (inventory port).

### E. lifecycle

- task ready → in-progress → done (single-PR closure pattern, BE-154 답습).
- INDEX 동기.

## Out of Scope

- **spec 측 변경 0** — prometheus.yml + gateway architecture.md 가 이미 정합 source. code side drift 만 fix.
- BE-153 yml 의 ADMIN_SERVICE_URI default 8086 — 이미 spec 답습 ✓ (변경 없음).
- BE-154 architecture.md edit — 별 task, 본 task 와 무관.
- 외부 dev workflow doc (`docs/guides/dev-tooling.md` 등) audit — 별 follow-up 후보.
- inbound port 변경 — inbound 는 모든 source align (코드 8082 = spec 8082 = prometheus 8082).

---

# Acceptance Criteria

- [ ] 3 application.yml SERVER_PORT default 정합 (inventory 8083, notification 8085, admin 8086).
- [ ] 3 Dockerfile EXPOSE + HEALTHCHECK 정합 (동일 port).
- [ ] README.md 의 inventory `:8082` 2곳 → `:8083`.
- [ ] gradle compile + unit test PASS (3 service: inventory + notification + admin).
- [ ] host bootRun 시 7 service 모두 동시 기동 가능 (collision 0): 8080/8081/8082/8083/8084/8085/8086.
- [ ] task lifecycle ready → done (single-PR closure).
- [ ] wms tasks/INDEX.md 동기.

---

# Related Specs

- `projects/wms-platform/infra/prometheus/prometheus.yml` (port spec source 1)
- `projects/wms-platform/specs/services/gateway-service/architecture.md § Routes` (port spec source 2)
- `projects/wms-platform/apps/inventory-service/src/main/resources/application.yml` (target file)
- `projects/wms-platform/apps/notification-service/src/main/resources/application.yml` (target file)
- `projects/wms-platform/apps/admin-service/src/main/resources/application.yml` (target file)
- `projects/wms-platform/apps/inventory-service/Dockerfile`
- `projects/wms-platform/apps/notification-service/Dockerfile`
- `projects/wms-platform/apps/admin-service/Dockerfile`
- `projects/wms-platform/README.md` (inventory diagram + quick-start comment)
- `projects/wms-platform/tasks/done/TASK-BE-153-gateway-service-routes-backfill.md` (provenance, surfaced follow-up)
- `projects/wms-platform/tasks/done/TASK-BE-154-gateway-service-type-composition-and-paragraph-fix.md` (sibling, 1/2 closure)

---

# Related Contracts

본 task = code default + Dockerfile + README edit only. HTTP/event contract 자체 변경 0.

---

# Target Service

3 service (inventory + notification + admin) 의 `application.yml` + `Dockerfile` + 1 README.md.

---

# Architecture

본 task = **code → spec align** (drift fix code side). spec edit 0.

```
Before (code drift):
  inventory   :8082 ← collision with inbound :8082
  notification:8086 ← collision with admin spec :8086 if admin moves to spec
  admin       :8087 ← spec mismatch (spec :8086)

After (spec align):
  master       :8081 (unchanged)
  inbound      :8082 (unchanged)
  inventory    :8083 ← code aligned to spec
  outbound     :8084 (unchanged)
  notification :8085 ← code aligned to spec, frees 8086 slot
  admin        :8086 ← code aligned to spec, no collision (notification moved)
  gateway      :8080 (unchanged)
```

---

# Implementation Notes

## 3 service simultaneous change 필수 이유

- inventory 단독 8082 → 8083: inbound collision 해소 ✓
- admin 단독 8087 → 8086: notification (8086) 과 신규 collision ❌
- notification 단독 8086 → 8085: 별 문제 없음 ✓
- admin + notification 함께: collision 0 ✓

→ 3 service 동시 변경 = collision-free 전이의 유일한 path.

## test 영향

- inventory + notification + admin 의 `src/test/` 디렉토리 grep `808[0-9]` = **0 matches** (사전 audit). port 가 random or env 기반.
- SpringBootTest WebEnvironment.RANDOM_PORT 가 일반적 — port hardcode 없음.

## D4 churn impact

- 7 file edit (6 code + 1 README).
- production behavior 변경 0 — port default 변경만 (env var override 가능).
- ADR-MONO-003a § D1.1 인접 (project-internal infrastructure spec drift fix). D4 OVERRIDE 자연 적용 (wms-only).

---

# Edge Cases

- inventory bootRun 시 SERVER_PORT env var 가 8082 override 면 그대로 동작 (default 변경만, override path 무영향).
- Dockerfile EXPOSE 는 documentation only (Docker network port mapping 시 host:container 명시 별도). HEALTHCHECK 가 localhost:<port> 호출 → application.yml port 와 align 필수.
- README.md 의 inventory `:8082` 표기는 dev workflow 안내용 — 정합 안 하면 dev 실행 시 confusion.

---

# Failure Scenarios

- gradle compile 시 port hardcoded 발견 시 → 본 task scope 확장 (별 file edit) or follow-up task.
- host bootRun 7 service 시도 시 동시 기동 fail 시 → 미발견 collision audit.
- Dockerfile EXPOSE/HEALTHCHECK 미동기 시 → docker image 의 healthcheck loop 무한 fail. application.yml + Dockerfile pair-edit 필수.

---

# Test Requirements

- 3 service gradle compile + unit test PASS.
- production code 자체 변경 0 (yml default + Dockerfile + markdown only).
- CI = path-filter (TASK-MONO-074/075) → 3 service trigger 자연 검증.

---

# Definition of Done

### Impl

- [ ] AC 완료.
- [ ] task lifecycle ready → done (single-PR closure).

### CI verification

- [ ] inventory + notification + admin path-filter trigger SUCCESS.
- [ ] 회귀 0.

---

# Provenance

- TASK-BE-153 commit `7e14a218` (2026-05-14) § follow-up candidate 2/2 (inventory + admin 표기) — 본 task 의 deep audit 시 notification drift 추가 surfacing (prometheus.yml 8085 vs code 8086).
- BE-154 sibling closure (gateway-service architecture.md hook fix, 2026-05-14) = follow-up 1/2.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (substantial — 7 file 동시 edit, 3 service test verification, collision-free transition 검증).
