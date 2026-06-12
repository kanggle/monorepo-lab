# Task ID

TASK-MONO-234

# Title

`.claude/skills/backend/external-http-integration/` 신설 — 실 outbound HTTP 어댑터(Slack/carrier/FCM/email) + inbound webhook 수신 패턴을 3개 도메인(fan/ecommerce/erp) 반복 구현에서 정규 플레이북으로 추출. skills INDEX.md 등록 포함.

# Status

ready

# Owner

backend / shared (Opus 4.8 analysis / Sonnet 4.6 impl 가능 — 문서성 skill). monorepo-level shared (`.claude/skills/`). No project code change.

# Task Tags

- docs
- skill

---

# Dependency Markers

- **relates (source 구현, 추출 대상)**:
  - fan-platform FAN-BE-016/017 — `HttpEmailChannelAdapter` + `HttpFcmPushChannelAdapter` (메모리 `project_fan_platform_v1_complete`).
  - ecommerce shipping BE-293 (carrier outbound pull `CarrierTrackingPort`) + BE-294 (carrier inbound webhook, HMAC·deliveryId 멱등·`ShippingForwardAdvancer`) (메모리 `project_ecommerce_wms_fulfillment_integration`).
  - erp notification ERP-BE-020 — `SlackWebhookChannelAdapter` + `ExternalNotificationProperties` + DeliveryRetryScheduler (메모리 `project_monorepo_template_strategy`).
- **relates (인접 skill, 상호참조)**: `backend/scheduled-tasks`, `messaging/consumer-retry-dlq`, `testing/testcontainers`, `backend/gateway-security`.
- **`.claude/` self-mod 제약**: 에이전트는 `.claude/` edit+commit 가능 여부가 classifier 에 의존(hooks/agents/commands 는 하드블록). skills 는 enumeration 밖이나, 막히면 사용자 셸 커밋. 메모리 `env_classifier_claude_self_mod_block`.

# Goal

3개 도메인에서 동일하게 반복된 "실 외부 HTTP 통합" 구현 지식을 단일 호출형 skill 로 정규화한다. 카탈로그에 전용 skill 이 없어 매번 메모리/기존 구현을 역참조하던 것을, task-type 으로 호출 가능한 플레이북(`backend/external-http-integration/SKILL.md`)으로 만들고 `.claude/skills/INDEX.md` 에 등록한다.

# Scope

## In Scope

- **신규 `.claude/skills/backend/external-http-integration/SKILL.md`** — 기존 skill house-style(frontmatter `name`/`description`/`category` → 패턴 섹션 → options 표 → `## Rules` → `## Common Pitfalls`)에 맞춰 작성. 내용 축:
  - Outbound 어댑터 코어 패턴: application 층 outbound port(`DeliveryOutcome` 결과형) + infrastructure 어댑터를 `@ConditionalOnProperty(mode)` 로 선택, default = noop(`matchIfMissing=true`) → net-zero.
  - 공유 RestClient 빌드: `ResilienceClientFactory.buildRestClient(baseUrl, connectMs, readMs)` (libs/java-common) — `new RestTemplate()` 금지.
  - best-effort/never-throw 계약(호출 use-case `@Transactional` fan-out 롤백 방지) + green-wash-safe(2xx 에서만 delivered).
  - 내구 retry 를 위한 트랜잭션 분리(consume tx 는 PENDING 행만, HTTP I/O 는 scheduler 자기 tx) → `scheduled-tasks`/`consumer-retry-dlq` 상호참조.
  - Inbound webhook 수신: HMAC-SHA256 raw-body 서명검증(blank secret = fail-closed) + provider-id 멱등 dedup + forward-only ordinal 전진. gateway permitAll·bearer-less.
  - 테스트: MockWebServer(2xx/5xx/connection-refused/route/서명-reject), 신규 의존성 0.
  - Configuration Options 표 + Rules + Common Pitfalls 표.
- **`.claude/skills/INDEX.md`** — `## Available Skills` 표에 행 추가:
  `| Real outbound HTTP adapter + inbound webhook ingestion | `backend/external-http-integration/SKILL.md` |`
  (`backend/scheduled-tasks` 인근, backend 섹션 내). `## Default Skill Sets by Task Type` 에 항목 추가:
  `| Add external channel / webhook | `external-http-integration` + `scheduled-tasks` + `consumer-retry-dlq` + `testing-backend` |`

## Out of Scope

- 기존 source 구현(fan/ecom/erp) 코드 변경 없음 — skill 은 그들에서 *추출*만.
- 새 라이브러리/의존성 추가 없음.
- `messaging/*`·`backend/scheduled-tasks` 등 인접 skill 본문 수정 없음(상호참조만).
- 프로젝트 코드·spec·contract 변경 없음.

# Acceptance Criteria

- [ ] `.claude/skills/backend/external-http-integration/SKILL.md` 존재, frontmatter(`name: external-http-integration`, `category: backend`) + 패턴 섹션 + Rules + Common Pitfalls 표 포함.
- [ ] outbound(port+조건부 어댑터+noop net-zero+ResilienceClientFactory+never-throw+2xx-only) 와 inbound(HMAC fail-closed+멱등+forward-only) 양방향을 모두 다룸.
- [ ] `.claude/skills/INDEX.md` `## Available Skills` 표 + `## Default Skill Sets by Task Type` 표에 신규 항목 등록.
- [ ] 기존 skill(`scheduled-tasks`/`consumer-retry-dlq`/`testcontainers`/`gateway-security`)과 내용 중복 없이 상호참조(중복 금지).
- [ ] 마크다운 lint OK(헤딩 계층·표 정렬·코드펜스 닫힘).

# Related Specs

- 없음 (skill 카탈로그 자체가 SoT; `platform/` 규정 변경 없음).

# Related Contracts

- 변경 없음.

# Target Service

- 없음 (repo-root `.claude/skills/` 공유 경로).

# Architecture

- Skill 은 Source-of-Truth 우선순위 11층(스펙/ADR 하위, knowledge 상위)의 에이전트용 호출형 플레이북이다. 시스템 사실(무엇)은 스펙/ADR 에, 재사용 절차(어떻게)는 skill 에 둔다는 3분할(spec/skill/memory)에서 본 task 는 메모리에 산재한 "HTTP 외부통합 어댑터 패턴"(fan/ecom/erp 3회 반복, 안정화됨)을 skill 로 승급시키는 것. 메모리 엔트리는 경험 로그로 남고, skill 이 정규 절차가 된다.

# Edge Cases

- INDEX 표 정렬·기존 행과 카테고리 그룹핑 어긋남 → backend 섹션 내 인접 배치로 회귀 없음.
- `.claude/` 에이전트 커밋이 classifier 에 막힐 경우 → 파일은 작성하되 commit 은 사용자 셸(또는 사용자에게 패치 전달). impl 단계에서 분기.

# Failure Scenarios

- skill 이 기존 `scheduled-tasks`/`consumer-retry-dlq` 내용을 복붙 → 중복. AC 가 "상호참조, 중복 금지" 명시.
- outbound 만 다루고 inbound webhook(BE-294) 누락 → 반쪽 추출. AC 가 양방향 명시.
- INDEX 미등록 → skill 이 호출 경로에 안 잡힘(디스커버리 실패). AC 가 INDEX 양 표 등록 명시.

# Definition of Done

- [ ] `SKILL.md` 신설(양방향 + Rules + Pitfalls)
- [ ] `INDEX.md` 양 표 등록
- [ ] 인접 skill 상호참조(중복 0)
- [ ] 마크다운 lint OK
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
