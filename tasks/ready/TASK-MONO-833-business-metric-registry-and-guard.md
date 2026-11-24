# Task ID

TASK-MONO-833

# Title

코드가 등록하는 business metric 중 어느 스펙에도 없는 20개를 각 서비스 스펙에 적고, 이름이 문자열로 박힌 metric 이 그 프로젝트 `specs/` 에 없으면 실패하는 가드

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- observability
- ci
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (metric 설명 — 코드를 읽고 쓰기) · 가드 술어·대조군은 Opus 검토
> 기안 2026-11-24 UTC. 출처: 소유자 «다음 할 일 추천» → 이 항목 → 소유자 «진행해».

# Dependency Markers

- 없음. (관련: `TASK-MONO-832` — 같은 모양의 «선언만 있고 게이트 없는 목록»; `scripts/check-error-code-registry.sh` — 같은 «sound, not complete» 술어의 선례.)

# Goal

`platform/observability.md` § Business Metrics 는 *«Each service must define its own business metrics in `specs/services/<service>/observability.md`»*, § Change Rule 은 *«New metrics … must be documented here before implementation»* 이라고 정한다. 아무것도 그것을 강제하지 않는다.

기안 중 실측(2026-11-24 UTC): `projects/*/apps/*/src/main` 에서 meter 를 등록하는 서비스 **32**, 이름이 문자열 리터럴인 metric **112** 중 **92** 는 그 프로젝트 `specs/` 어딘가에 있고 **20 개(8 서비스)** 는 리포 어디에도 없다(`.`→`_` 표기 · `_total` 뺀 이름으로도 0건):

| 서비스 | metric |
|---|---|
| ecommerce `order-service` | `order_backordered_total` · `order_stuck_detector_exhausted_total` · `order_stuck_detector_recovery_fired_total` |
| fan `community-service` | `community_feed_cache_hits_total` · `community_feed_cache_misses_total` |
| fan `membership-service` | `membership_auto_renewed_total` |
| iam `admin-service` | `admin.audit.org_node_scope_deny_failure` · `admin.audit.tenant_placement_deny_failure` |
| iam `security-service` | `kafka.consumer.lag` · `security_auto_lock_failures_total` · `security_auto_lock_retries_total` |
| scm `demand-planning-service` | `reorder_open_guard_hits_total` · `reorder_sweep_skipped_unmapped_total` |
| wms `admin-service` | `admin.outbox.pending.count` · `admin.outbox.publish.failure.total` · `admin.outbox.publish.success.total` |
| wms `notification-service` | `notification.outbox.publish.failure.total` · `notification.outbox.publish.success.total` · `notification.outbox.skipped.total` · `notification.routing.dedupe` |

그중 몇은 알림을 걸 종류다(복구 소진 · 자동 잠금 실패 · outbox 발행 실패). 문서에 없으면 운영자는 그것이 있는 줄 모른다.

# Scope

## In Scope

- 위 20개를 각 서비스 스펙에 적는다 — **그 서비스가 이미 metric 을 적어 두는 파일**(`observability.md` 가 있으면 거기, 없으면 metric 을 이미 적고 있는 `architecture.md` 등; 아무 데도 없으면 `specs/services/<service>/observability.md` 를 새로). 각 행: 이름 · 유형(Counter/Gauge/Timer) · 태그 · 언제 증가/무엇을 재는가 · (해당하면) 알림 의미. **코드를 읽고** 쓴다 — 이름에서 추측하지 않는다.
- 새 가드 `scripts/check-metric-name-registry.sh`:
  - 모집단 = `projects/<p>/apps/<s>/src/main/**/*.java` 의 meter 등록 호출에 **문자열 리터럴로** 넘어간 이름: `(Counter|Timer|Gauge|DistributionSummary|LongTaskTimer|FunctionCounter|FunctionTimer)\.builder("…")` · `.counter("…"` · `.timer("…"` · `.gauge("…"` · `.summary("…"`.
  - 술어 = 그 이름(또는 `.`→`_` 표기)이 **같은 프로젝트의** `projects/<p>/specs/**` 에 고정 문자열로 나타난다. 다른 프로젝트 스펙이나 `tasks/` 의 언급은 문서가 아니다.
  - **sound, not complete** — 상수 · 연결 · 필드로 만든 이름은 의도적으로 범위 밖(`check-error-code-registry.sh` 와 같은 결정과 같은 이유: 틀리게 무는 가드는 꺼진다). 헤더에 명시.
  - 빈 모집단 fail-closed. `--self-test` = 실제 트리 사본 변형(별도 프로세스 진입점 · 기준선 · 문서에서 이름 삭제 bite · 새 리터럴 metric bite · 다른 프로젝트 스펙에만 있는 이름은 bite · `tasks/` 에만 있는 이름은 bite · `.`→`_` 표기 통과 · 주석 속 호출 무시).
- `ci.yml` 잡 + `changes` 필터(`projects/*/apps/*/src/main/**/*.java` · `projects/*/specs/**` · 가드 자신 — pure-positive).
- `scripts/` 69 → 70: 가드 **전부** 재실행 + `check-ls-files-guard-count.sh` 산문 집.

## Out of Scope

- 92개 기존 문서의 위치 통일(전부 `observability.md` 로 옮기기) — 범위가 다르다. 이 티켓은 **없는 것**을 채운다.
- 상수로 만든 metric 이름의 문서 감사 — 가드가 보지 않는 곳이므로 «20 이상» 이라는 사실만 기록한다.
- 알림 규칙 신설(`platform/observability.md` § Alerting) — 문서화가 먼저다.
- `libs/` 의 meter — 공유 라이브러리는 프로젝트 스펙이 없다(별개 결정).

# Acceptance Criteria

- [ ] AC-0: 착수 직전 재측정 — 가드의 모집단 정규식으로 다시 센 «문서에 없는 리터럴 metric» 이 위 20개와 같은가. 다르면 그 목록으로 진행하고 차이를 기록.
- [ ] AC-1: 그 전부가 그 서비스 스펙에 이름 · 유형 · 태그 · 의미와 함께 있다. 각 행의 근거(등록하는 클래스 경로)를 PR 본문에.
- [ ] AC-2: 가드 rc=0 · `--self-test` 전부 PASS.
- [ ] AC-3: bite — 사본에서 문서의 한 이름을 지우면 rc=1 + 서비스·이름 출력, 복원 → rc=0.
- [ ] AC-4: `scripts/` 가드 전부(스테이지 뒤) — 비0 은 사유와 함께 · `check-ls-files-guard-count.sh` rc=0.
- [ ] AC-5: PR CI 에서 새 잡이 **실행되어** success.

# Related Specs

- `platform/observability.md` § Business Metrics · § Change Rule
- 각 서비스 `specs/services/<service>/` (observability.md · architecture.md)
- `platform/testing-strategy.md` § 가드 규칙

# Related Contracts

- 없음(metric 이름·태그는 바꾸지 않는다 — 문서만).

# Edge Cases

- `kafka.consumer.lag` 같은 일반 이름 — 같은 프로젝트의 다른 서비스 스펙에 적혀 있으면 가드는 통과시킨다(프로젝트 단위 술어). 그래도 AC-1 은 **등록하는 그 서비스의** 스펙에 적으라고 요구한다.
- 같은 이름을 여러 서비스가 등록 — 프로젝트 스펙 하나에 있으면 가드는 통과. AC-1 은 서비스마다.
- 테스트 코드의 meter — 모집단은 `src/main` 만.

# Failure Scenarios

- 술어가 `tasks/` · 다른 프로젝트까지 보면 → 티켓에 이름을 적는 것만으로 초록(이 티켓 자신이 20개 이름을 담고 있다!). self-test 의 «tasks/ 에만» case 가 잡는다.
- 정규식이 `.builder("` 를 meter 아닌 빌더에서 잡으면 → 첫날 빨강. 기준선 case + AC-0 의 목록 대조가 잡는다.
- 짧은 이름이 스펙의 무관한 문장에 우연히 걸려 통과 → 알려진 한계(고정 문자열). 이름 길이 하한을 두지 않는다 — 놓치는 쪽이지 틀리게 무는 쪽이 아니다.

# Test Requirements

- 가드 self-test · bite · 가드 전수 · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
