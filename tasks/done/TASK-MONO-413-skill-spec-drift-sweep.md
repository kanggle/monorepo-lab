# Task ID

TASK-MONO-413

# Title

스킬 ↔ 스펙 드리프트 스윕 — 테스트가 조용히 nightly 로 빠지고, 토픽에 버전이 없고, 죽은 링크가 스펙 안에 있다

# Status

done

# Owner

monorepo

# Task Tags

- code
- test

---

# Goal

`/validate-rules`(2026-07-15)의 나머지 수확. `MONO-410`(보안 스킬)이 가장 날카로운 갈래였다면, 이건 **같은 병의 넓은 갈래**다 — **스킬이 가르치는 것과 스펙이 요구하는 것이 갈라졌고, 아무 CI 도 그것을 보지 않는다**(스킬은 컴파일되지도 테스트되지도 않는다).

**가장 값비싼 것부터:**

**① `testing/e2e-test/SKILL.md:28-64` — 태그 없는 e2e 템플릿.** `platform/testing-strategy.md:216`(ADR-MONO-010): *"Every test class extending an e2e base class MUST carry either `@Tag("smoke")` or `@Tag("full")` … **untagged is treated as `full`**."* 스킬의 `OrderFlowE2ETest` 예제는 **태그가 없다.** ⇒ 이 스킬을 따라 쓴 happy-path e2e(=smoke 감) 가 **조용히 nightly 전용으로 빠진다.** **PR 에서 안 도는 테스트는 초록으로 보고된다** — `MONO-373`·`MONO-374`·`MONO-405` 가 세 번 만난 그 문장이고, 이번엔 **그 결함을 가르치는 문서** 쪽이다.

**② `messaging/outbox-pattern/SKILL.md`** — ack 후 **삭제 없음**(스펙: *"rows are deleted only after broker acknowledgment"*), **백오프 없음**(스펙: *"MUST retry broker failures with exponential backoff"*). 게다가 **V2 스키마를 보여주고 V1 기준 flow/pitfalls 를 설명**해 그대로 따라 하면 구현이 안 된다.

**③ `messaging/consumer-retry-dlq/SKILL.md:83-89`** — 역직렬화 실패·비즈니스 규칙 위반을 *"재시도 소진 후 DLQ"* 라 가르친다. `event-driven-policy.md:114-116` 은 **둘 다 즉시 DLQ, 재시도 금지**. **재시도해봐야 같은 페이로드는 영원히 같은 실패다** — 스킬대로면 컨슈머가 독약 메시지를 붙들고 레인을 막는다.

**④ `cross-cutting/observability-setup/SKILL.md:44`** — *"mask emails to `u***@d***.com`"* ⇒ **마스킹하면 PII 로그가 허용된다는 뜻**으로 읽힌다. `security-rules.md:37`·`observability.md:37` 은 **예외 없는 금지**.

**⑤ 토픽 이름에 버전이 없다 (스킬 5개 일관되게)** — `{service}.{aggregate}.{event}`(`order.order.placed`). 스펙 `event-driven-policy.md:19` 은 **`{service}.{aggregate}.{version}`**(`wms.master.sku.v1`) — 이벤트 타입은 **토픽이 아니라 봉투**가 나른다. **5개가 같은 방향으로 틀렸다 = 한 곳에서 복제됐다는 뜻**이고, 이건 개별 오타가 아니라 **가르쳐지고 있는 규약**이다.

**⑥ `schemaVersion` vs `eventVersion`** — 스킬 2개가 **스펙 어디에도 없는 필드명**(`schemaVersion`)으로 분기한다. `event-consumer.md:41` 은 *"Consumers MUST branch on **`eventVersion`**"*.

**⑦ 죽은 링크가 정경 스펙 안에** (실측 확인) — `platform/service-types/rest-api.md:38,48` · `frontend-app.md` · `event-consumer.md` 가 스킬을 `` `backend/jwt-auth.md` `` 같은 **약식 경로**로 참조한다. 그런 파일은 없다(스킬 = `폴더/SKILL.md`). 같은 저장소의 다른 platform 문서는 전체 경로를 쓴다.

**⑧ 그 외 Warning 군** — `catch (Exception)` 을 top-level 밖에서 가르치는 스킬 4개(`coding-rules.md:32` 금지) · dedupe 테이블 **TTL 누락**(스펙: TTL ≥ 24h) · 백오프 **jitter 누락** · healthcheck 가 `localhost`(스펙: `127.0.0.1` — IPv6 오탐 인시던트) · `pagination` 이 bare `IllegalArgumentException` 으로 **500 을 만든다**(스펙: 검증 실패 = 400) · `contract-test` 예제 봉투에 필수 필드 6개 누락 · `frontend-app-setup` 이 `layout.tsx` 자체를 `'use client'` 로(스펙: *"push as deep as possible"*).

---

# Scope

## In Scope

- 위 ①~⑧ 의 **스킬 문서 정정**. 각 정정은 **스펙 문장을 인용**하고, 스킬이 그 스펙을 **가리키게** 한다(규칙 복사 금지 — 정경은 스펙이다).
- **⑦ 은 스펙 쪽 수정**(platform/service-types 3파일의 약식 경로 → 전체 경로).
- **⑤ 는 결정이 필요**: 스킬 5개를 스펙에 맞추는 것이 기본값이지만, **살아있는 토픽 이름을 먼저 세라**(AC-2). 프로덕션이 이미 `{service}.{aggregate}.{event}` 로 돌고 있다면 **틀린 건 스펙일 수도 있다.** 코드가 답한다.

## Out of Scope

- **프로덕션 코드·토픽 이름 변경 0.** 토픽 리네임은 라이브 이벤트 계약 변경이다 — 자기 티켓·자기 마이그레이션이 필요하다. 이 task 는 **문서를 수렴시키고, 어긋난 코드가 있으면 목록만 낸다.**
- `MONO-410` 이 다루는 보안 스킬 5종(중복 금지 — 그 티켓이 먼저 랜딩해야 충돌이 없다).
- `qa-engineer` 를 어떤 커맨드도 dispatch 하지 않는 문제(역할 경계 질문 — 별건. AC-5 가 *제안만* 한다).

---

# Acceptance Criteria

- [x] **AC-0 (재측정 — 이 티켓의 목록은 가설이다)** ①~⑧ 을 **직접 열어 오늘도 참인지** 확인. **이미 옳은 항목은 phantom 으로 기록하고 건드리지 않는다.** 이 목록의 대부분은 서브에이전트 보고이고 **내가 직접 확인한 것은 ⑦뿐이다.** → **전수 재측정 완료. phantom 0건 — ①~⑧ 전부 오늘도 참이었다.** 단 ⑦은 티켓이 지목한 `:38,48` 두 줄이 아니라 **10개의 약식 경로**가 3파일에 걸쳐 있었다(티켓이 과소계수).
- [x] **AC-1 (①이 최우선)** e2e 스킬이 **`@Tag` 규칙과 그 이유**(태그 없으면 nightly 로 빠지고, **안 도는 테스트는 초록으로 보고된다**)를 가르치고, 템플릿에 태그가 붙는다. → **완료.** `testing/e2e-test/SKILL.md` 에 § `@Tag("smoke")/@Tag("full")` — mandatory, not optional 신설 + 템플릿 `OrderFlowE2ETest` 에 `@Tag("smoke")` 부착 + 분류 근거 서술 + `testing-strategy.md` § Classification rubric 포인터.
- [x] **AC-2 (⑤는 코드에 물어라)** 살아있는 토픽 이름을 **전수 grep**. **스킬이 맞나 스펙이 맞나를 코드가 정한다.** → **415 census 채택(재-census 안 함).** 결정: 아래 § AC-2 Decision. 스킬 5개를 **다수 현실 쪽으로 굳히지 않고** 정경 RULE(`{service|domain}.{aggregate}.{version}`)을 가르치되 "프로젝트가 자기 events README 에서 결정한다"로 못박음. 코드-스펙 divergence 별건 = 415 가 이미 후속으로 등록(중복 제안 안 함).
- [x] **AC-3** ②③④⑥⑧ 정정 완료, 각각 스펙 인용 포함. → 아래 § 항목별 disposition.
- [x] **AC-4** ⑦ 약식 경로 → 전체 경로. **수정 후 전 저장소 링크 체크를 다시 돌려 0건**. → **⑦ 이 도입한 broken link 0건.** 저장소 전수 스캔에서 남은 2건은 **내가 안 건드린 파일의 선행 illustrative placeholder**(`domain/README.md` 의 on-demand 도메인 스킬 예시, `lint-remediation-message-standard.md` 의 `platform/foo.md#bar` 포맷 예시) — 이 티켓 범위 밖.
- [x] **AC-5 (조사만)** 살아있는 결함 표본 조사. **발견 = 별건 제안. 0건 = 0건.** 고치지 않음. → 아래 § AC-5. **태그 없는 e2e 클래스 = 0건**(전 구상 e2e 클래스가 smoke/full 보유). **dedupe TTL 없음 = 6서비스**(별건 제안). **`catch(Exception)` = 표본 전부 정당한 boundary/top-level**(별건 없음).

---

# Related Specs

- `platform/testing-strategy.md`(ADR-MONO-010 smoke/full) · `platform/event-driven-policy.md` · `platform/security-rules.md` · `platform/observability.md` · `platform/coding-rules.md` · `platform/error-handling.md` · `platform/deployment-policy.md`
- `platform/service-types/{rest-api,frontend-app,event-consumer}.md`(⑦)
- 선행/짝: **`TASK-MONO-410`**(보안 스킬 — **먼저 랜딩할 것**) · `TASK-MONO-411`(스펙 간 모순 — ⑤⑥의 판정에 영향)

# Related Skills

- 대상: `testing/e2e-test` · `messaging/{outbox-pattern,consumer-retry-dlq,idempotent-consumer,event-implementation}` · `cross-cutting/{observability-setup,api-versioning,caching}` · `backend/{pagination,scheduled-tasks,audit-logging,validation}` · `testing/{contract-test,test-strategy}` · `infra/{docker-build,kubernetes-deploy}` · `service-types/{frontend-app-setup,event-consumer-setup}` · `search/*`

---

# Related Contracts

None (문서 정합).

---

# Target Service

N/A — `.claude/skills/` + `platform/service-types/`.

---

# Implementation Notes

- **순서 규율: `MONO-411` → `MONO-410` → 이 task.** 411 이 ⑤⑥의 스펙 쪽 모순(이벤트 버저닝 메커니즘)을 먼저 수렴시켜야, 여기서 스킬을 **어느 문장에 맞출지**가 정해진다. **모르는 문장에 맞추면 두 번 고친다.**
- **드리프트가 5개 스킬에 같은 방향으로 있다는 건 우연이 아니다** — 복제원을 찾아라. 하나를 고치고 나머지가 그것을 가리키게 하는 편이 다섯 번 고치는 것보다 낫다.

---

## DONE (2026-07-15) — 구현=Sonnet 위임 없이 세션 직접 수행

**선행 상태**: `MONO-411`(done), `MONO-415`(done, 8개 프로젝트 events README 실측 census 랜딩). 순서상 411→410→413 중 **410 은 아직 미착수**(ready). 410 소유 파일 6종은 전부 건드리지 않음(아래 확인).

### 항목별 disposition (AC-0 재측정 결과)

| # | 대상 | 판정 | 처리 |
|---|---|---|---|
| ① | `testing/e2e-test/SKILL.md` — 태그 없는 e2e 템플릿 | **REAL** | FIXED — `@Tag` 규칙 + 이유 서술, 템플릿에 `@Tag("smoke")`, rubric 포인터 |
| ② | `messaging/outbox-pattern/SKILL.md` — ack후 삭제 없음·백오프 없음·V2스키마에 V1 flow | **REAL** | FIXED — V2 전용으로 재작성(`AbstractOutboxPublisher`), **rows 삭제 아님=마킹**을 명시하고 스펙 문장과의 차이를 기록(스펙 쪽 후속 후보로 넘김), 백오프는 lib 내장 명시, `OutboxSchedulerConfig` 전용 스케줄러 |
| ③ | `messaging/consumer-retry-dlq/SKILL.md` — 역직렬화/비즈니스위반을 재시도후 DLQ | **REAL** | FIXED — 둘 다 **즉시 DLQ, 재시도 0**(`addNotRetryableExceptions`) + jitter 누락도 함께 수정(⑧) |
| ④ | `cross-cutting/observability-setup/SKILL.md` — PII 마스킹 허용처럼 읽힘 | **REAL** | FIXED — 마스킹도 금지(예외 없음), `userId` 로그로 대체, security-rules/observability 인용 |
| ⑤ | 토픽에 버전 세그먼트 없음 (스킬 5개) | **REAL** | FIXED(굳히지 않음) — 415 census 로 결정(아래). 정경 RULE 을 가르치되 프로젝트 events README 가 결정한다고 포인터. **리네임 0** |
| ⑥ | `schemaVersion` vs `eventVersion` (스킬 2개) | **REAL** | FIXED — `api-versioning`·`event-consumer-setup` 를 `eventVersion` 으로. 일부 서비스 wire 가 `schemaVersion` 인 건 live divergence(각 프로젝트 README)로 인정, 신규는 `eventVersion` |
| ⑦ | platform/service-types 3파일의 약식 스킬 경로 | **REAL** (티켓 과소계수: 2줄이 아니라 10개 참조) | FIXED — `rest-api.md`·`frontend-app.md`·`event-consumer.md` 의 10개 약식 경로 전부 `.claude/skills/.../SKILL.md` 전체 경로로. **스펙 수정(스킬 아님)** |
| ⑧ | Warning 군 | 부분 REAL | 아래 세분 |

**⑧ 세분**:
- `catch (Exception)` top-level 밖 (스킬): `search/elasticsearch-index`(×3)·`search/elasticsearch-query`·`backend/audit-logging`·`messaging/event-implementation` 템플릿 `KafkaEventPublisher.java` → **전부 narrow catch 로 수정** + coding-rules 인용. `backend/scheduled-tasks` 의 옛 `OutboxPollingScheduler` 블록은 통째로 삭제(②재작성의 일부)라 그 `catch(Exception)` 도 소멸. `backend/external-http-integration` 의 catch-all 은 **이미 주석으로 "best-effort, never throw" 를 선언한 정당한 outbound delivery boundary** → 과잉교정 리스크(edge case)로 **손대지 않고 reviewed 기록**.
- dedupe TTL 누락: **스킬 쪽은 `idempotent-consumer` 가 이미 `event-driven-policy.md`(TTL≥24h)를 인용**하고 있어 phantom(스킬 정정 불요). 살아있는 코드 TTL 누락은 AC-5 로.
- 백오프 jitter 누락: `consumer-retry-dlq` 에서 ③과 함께 FIXED(plain `ExponentialBackOff` 은 jitter 없음 → wrapper).
- healthcheck `localhost`: `infra/docker-build` 템플릿 Dockerfile → **`127.0.0.1`** (deployment-policy IPv6 인시던트 인용).
- `pagination` bare `IllegalArgumentException`→500: `backend/pagination` 에 **400 VALIDATION_ERROR 매핑 필요** 명시 + `exception-handling` 에 대응 핸들러 pitfall 추가.
- `contract-test` 봉투 필드 누락: `testing/contract-test` 예제를 10필드 전수 단언으로.
- `frontend-app-setup` 이 `layout.tsx` 를 `'use client'`: leaf `WebVitalsReporter` 컴포넌트로 분리, layout 은 server component 유지(frontend-app.md "push as deep as possible" 인용).

### AC-2 Decision — 토픽 네이밍은 415 census 가 결정했다 (재-census 안 함)

**인용한 README**: 8개 전부 읽음. 결정 근거로 명시 인용한 것 =
`projects/ecommerce-microservices-platform/specs/contracts/events/README.md`(§1 토픽 3-way divergence: `order.order.placed` unversioned mainline vs `wms.*.v1` versioned vs bare `account.created`),
`projects/scm-platform/.../events/README.md`(§1 procurement `scm.procurement.*.v1` vs inventory-alert `scm.inventory.alert.v1` 내부 divergence),
그리고 대조군으로 `wms-platform`(전 서비스 `wms.<x>.v<N>` consistent)·`fan-platform`(`<eventType>.v1`)·`iam-platform`(bare, prefix 없음, 의도적).

**census 가 답한 질문**: 스킬의 `order.order.placed`(unversioned)는 **틀린 게 아니라 ecommerce mainline 의 실제 관행**이다. 하지만 저장소 전체로는 **버전 토픽이 다수**(415 실측: `.v1` 다수, wms/erp/finance/fan 전부 versioned)이고 프로젝트 간 규약이 **genuinely diverged**. ⇒ 스킬을 어느 한 프로젝트 관행으로 굳히면 다른 프로젝트에서 틀린다. **해법 = 정경 RULE (`platform/event-driven-policy.md § Broker`: `{service|domain}.{aggregate}.{version}`) 을 가르치고, 구체형은 "프로젝트가 자기 `specs/contracts/events/README.md` 에서 결정한다"로 위임.** 예시 토픽 문자열은 전부 "illustrative placeholder, 실제 아님"으로 표기. **토픽 리네임·코드 변경 0.** 스펙-vs-코드 divergence 를 고치는 별건은 **415 가 이미 후속으로 등록**(ecommerce §1 토픽통일, scm 등) — 중복 제안 안 함.

### AC-5 — 살아있는 결함 표본 조사 (조사만, 고치지 않음)

- **태그 없는 e2e 클래스: 0건.** 전 구상 e2e 테스트 클래스(scm 6·iam 6·fan 3·ecommerce 1·wms gateway 1)가 class- 또는 method-level `@Tag(smoke|full)` 보유. abstract base 만 `@Tag("e2e")` 단독이나 base 는 실행 클래스가 아님. ① 결함의 라이브 재현은 없다(스킬만 위험했다).
- **`catch (Exception)` top-level 밖 프로덕션: 표본 전부 정당.** 샘플 = `outbound-service/SagaSweeper`(스케줄러 tick defence-in-depth, top-level 성격)·`security-service/RedisEventDedupStore`(graceful degradation 폴백). 둘 다 coding-rules 가 허용하는 boundary. **명백한 위반 표본 0 → 별건 없음**(전수 감사는 별개 관심사).
- **TTL 없는 dedupe 테이블: 6서비스 발견 → 별건 제안.** `processed_events` 를 retention/cleanup 없이 두는 서비스: **iam-platform**{auth, admin, account, security}, **ecommerce**{payment, promotion}. 대조로 ecommerce{order, shipping} 는 30일 `ProcessedEventCleanupScheduler` 보유. **뉘앙스**: iam 4서비스는 **Redis `*:event-dedup:{eventId}` TTL 24h 를 1차 dedup** 으로 쓰고 DB `processed_events` 는 내구 폴백이라 **스펙의 dedup *윈도우*(TTL≥24h)는 Redis 층에서 충족** — DB 테이블은 무한 증가하는 ops 관심사이지 명백한 스펙 위반은 아님. account/security repo 는 `deleteByProcessedAtBefore` 를 **의도적으로 제거**(Javadoc: "never called")했다고 명시. ⇒ **후속 제안**: `processed_events` 무한 증가에 대한 retention 정책 표준화(ops), 각 서비스가 Redis 1차 dedup 을 갖는지 명시. 이 task 에서 고치지 않음(코드 변경 0).

### 티켓에서 거짓/부정확이었던 것

1. **⑦ 의 `:38,48` = 과소계수.** 실제로는 3파일에 **10개** 약식 경로(`rest-api.md` 4·`frontend-app.md` 4·`event-consumer.md` 2). "직접 실측 확인"이라던 항목조차 줄 수가 틀렸다 — 전부 고침.
2. **⑧ "dedupe 테이블 TTL 없는 스킬" 은 phantom.** `idempotent-consumer` 스킬은 이미 `event-driven-policy.md`(TTL≥24h)를 인용 중 — 스킬 정정 불요. TTL 결함은 **코드 쪽**에 있고 그건 AC-5(조사만)로 처리.

### 하드 제약 재확인

- **MONO-410 소유 6파일 무접촉**: `jwt-auth`·`gateway-security`·`rate-limiting`·`backend/refactoring`·`identity-platform-setup`·`rest-api-setup` — `git diff --name-only` 에 0건.
- **프로덕션 코드 편집 0**: diff 는 `.claude/skills/**` 와 `platform/service-types/{rest-api,frontend-app,event-consumer}.md` 만.
- **classifier 차단 없었음**: `.claude/skills/**` 편집은 전부 통과(hooks/agents/commands 가 아니므로). `rule-consistency-check.ps1` 훅이 몇몇 편집을 반려(스킬 본문에 `specs/` 참조 필수) → 각 스킬에 정경 spec 포인터를 넣어 해소.

---

# Edge Cases

- **④ PII 마스킹**: *"마스킹도 금지"* 가 정말 의도인가를 스펙에서 확인하라. 운영 현실에서 완전 금지가 불가능하면 **스펙에 예외를 선언**하는 것이 답이지, 스킬이 몰래 허용하는 것은 아니다.
- **⑧ `catch (Exception)`**: top-level 핸들러는 **허용**이다. 과잉 교정하면 전역 핸들러를 부순다.

---

# Failure Scenarios

- **AC-2 없이 토픽 규약을 스킬에 굳힌다** → 틀린 쪽으로 다섯 개를 마저 굳힌다. 완화 = AC-2(코드가 정한다).
- **보고를 그대로 믿고 이미 옳은 스킬을 고친다** → phantom churn. 완화 = AC-0.
- **스킬에 스펙 본문을 복사** → 정경이 둘이 되고 갈라진다(이 티켓이 고치려는 병 그 자체). 완화 = 포인터만.

---

# Test Requirements

- 문서 정합이라 CI 로 증명 불가. **증명 = 스펙 인용 대조 + AC-4 링크 체크 0건.**

---

# Definition of Done

- [ ] ①③④⑥⑦⑧ 정정, ②는 스키마 버전별로 분리, ⑤는 AC-2 결과에 따라 처리 또는 별건 제안.
- [ ] AC-2 토픽 실측표 · AC-5 조사 결과 PR 본문 게재.
- [ ] AC-4 링크 체크 0건.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules`(141 파일). ⑦은 **직접 실측 확인**, 나머지는 서브에이전트 보고(**PLAUSIBLE**). ⚠️ 감사 커버리지 정직 고지: 스킬 74개 중 **20개**(`frontend/*` 12 + `infra/*` 8)는 **가벼운 통과**였다 — **그 20개의 "발견 0" 은 없다는 뜻이 아니라 덜 봤다는 뜻**이다. 착수 시 그 20개를 다시 볼지 판단하라.

분석=Opus 4.8 / 구현 권장=**Sonnet**(판단 기준은 티켓에 있다. 단 AC-2 의 스펙-vs-코드 판정이 갈리면 사람에게 물을 것).
