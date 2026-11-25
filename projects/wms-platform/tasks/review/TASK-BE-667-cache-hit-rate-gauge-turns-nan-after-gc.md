# Task ID

TASK-BE-667

# Title

admin-service `admin.query.cache.hit.rate` 는 «v1 에선 항상 0» 이 설계인데 GC 뒤 `NaN` 이 된다 — `meterRegistry.gauge(name, 0.0d)` 가 박싱된 `Double` 을 약한 참조로만 잡는다. 강한 참조로 등록해 설계대로 0 을 유지한다

# Status

review

# Owner

backend

# Task Tags

- observability
- admin-service

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku · Sonnet 5.5 (한 줄 + 테스트 1)
> 기안 2026-11-25 UTC. 출처: `TASK-BE-666` 증거 «기록만 ①» → 소유자 «진행».

# Dependency Markers

- 선행: `TASK-BE-048`(done) deviation #8 — 이 gauge 를 «v1 은 Redis 쿼리 캐시가 없으므로 항상 0, 그래도 이름을 고정하려고 등록한다» 로 **결정** 했다(`ProjectionMetrics` javadoc · 생성자 주석). 그 결정은 그대로 둔다.

# Goal

🔴 정정 먼저: `TASK-BE-666` 증거는 이 gauge 를 «값이 갱신될 수 없는 코드 결함» 이라 적었다. 틀린 진단이었다 — 상수 0 은 `TASK-BE-048` 의 **의도된 설계** 다(위 선행). 666 은 그 주석을 읽지 않고 적었다.

진짜 결함은 따로 있다. 2026-11-25 UTC 실측(일회용 테스트, 실제 `PrometheusMeterRegistry` 에 `ProjectionMetrics.java:61` 과 같은 한 줄 `r.gauge("admin.query.cache.hit.rate", 0.0d)` → `System.gc()`):

```
before:   admin_query_cache_hit_rate 0.0
after GC: admin_query_cache_hit_rate NaN
```

Micrometer 의 `MeterRegistry.gauge(String, T)` 는 관측 대상(여기선 박싱된 `Double`)을 **약한 참조** 로 잡는다. 아무도 그 `Double` 을 들고 있지 않으므로 첫 GC 에 사라지고, 그 뒤 값은 `NaN` 이다 — 설계가 약속한 «안정된 이름 · 값 0» 이 서비스가 뜬 지 얼마 안 돼 깨진다. 저장소에서 이 모양(`.gauge(이름, 리터럴 · new …)`)은 이 한 곳뿐이다(`git grep`).

# Scope

## In Scope

1. `ProjectionMetrics` 생성자: `Gauge.builder(CACHE_HIT_RATE_METRIC, () -> 0.0d).description(…).register(meterRegistry)` — `Supplier` 를 받는 빌더는 강한 참조로 잡는다(`Gauge.builder(String, Supplier)` 가 `strongReference(true)`). 주석의 «v1 항상 0» 설명은 유지하고, 왜 `registry.gauge(name, 0.0d)` 가 아닌지 한 줄.
2. 테스트 `ProjectionMetricsTest`(새로): 실제 GC 가 일어났음을 **확인한 뒤**(`WeakReference` 감시 객체가 비워질 때까지 `System.gc()` 반복, 상한 있음) gauge 값이 `0.0` 이다. 감시 객체가 끝내 안 비워지면 테스트는 **실패가 아니라 중단(assumption)** — GC 가 없었으면 아무것도 증명하지 않았으므로.

## Out of Scope

- 이 gauge 를 지우거나 실제 캐시 적중률을 구현하는 것 — `TASK-BE-048` 결정 · v2 Redis 캐시 몫.

# Acceptance Criteria

- [x] AC-1: 새 테스트 PASS. **bite**: 등록을 원래 한 줄로 되돌리면 같은 테스트가 실패(`NaN`).
- [x] AC-2: admin-service `test` 통과.
- [ ] AC-3: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `specs/services/admin-service/architecture.md` § Observability(`admin.query.cache.hit.rate`)

# Related Contracts

- 없음.

# Edge Cases

- 테스트 JVM 에서 `System.gc()` 가 무시될 수 있다(`-XX:+DisableExplicitGC`) → 감시 객체가 안 비워짐 → assumption 으로 건너뜀(거짓 초록 아님, 거짓 빨강 아님).

# Failure Scenarios

- GC 없이 값만 읽는 테스트 → 원래 코드도 통과(0.0) — 아무것도 안 잰다. AC-1 의 bite 가 이것을 막는다.

# Test Requirements

- 새 단위 테스트 + bite · admin-service test · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **변경**: `ProjectionMetrics` 생성자의 `meterRegistry.gauge(CACHE_HIT_RATE_METRIC, 0.0d)` → `Gauge.builder(CACHE_HIT_RATE_METRIC, () -> 0.0d).description(…).register(meterRegistry)` + 왜 앞의 형태가 아닌지 주석. «v1 항상 0»(TASK-BE-048 #8) 은 그대로. 새 테스트 `ProjectionMetricsTest`.
- **AC-1** — 새 테스트: `WeakReference` 감시 객체가 비워질 때까지 `System.gc()`(상한 50) → 비워지지 않으면 assumption 으로 건너뜀 → gauge 값 `0.0`. 고친 코드 1/1 PASS(skipped 0 — GC 가 실제로 일어났다). **bite**: 등록을 원래 한 줄로 되돌리면 같은 테스트 실패 — `[value after GC] expected: 0.0 but was: NaN` → 복원. 🔴 첫 bite 시도는 치환 정규식이 `description(...)` 안의 괄호에 걸려 **되돌리기가 안 된 채** 통과했다(되돌린 줄이 없음을 `grep` 으로 확인 후 줄 번호로 다시 되돌려 진짜 bite 를 얻었다) — «bite 가 통과했다» 는 그 자체로는 아무 증거도 아니다.
- **AC-2** — admin-service 전체 `test` rc=0 — **377 tests, 실패 0 · skip 0**.
- **AC-3** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `.mjs` 7/7. PR CI: 대기.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
