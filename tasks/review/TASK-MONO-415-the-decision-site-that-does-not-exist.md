# Task ID

TASK-MONO-415

# Title

정경 스펙이 **5번 가리키는 결정 지점이 존재하지 않는다** — `specs/contracts/events/README.md` 는 7개 프로젝트 어디에도 없다

# Status

review

# Owner

monorepo

# Task Tags

- event
- adr

---

# Goal

`TASK-MONO-411` 이 이벤트 breaking-change 의 정경을 `platform/event-driven-policy.md` 로 확정했다. 그 파일을 정경으로 만들고 나서 보니, **그 파일이 자기가 결정하지 않는 것들을 프로젝트에게 넘기고 있고, 넘기는 주소가 없다.**

`event-driven-policy.md` 는 **`specs/contracts/events/README.md` 를 5번 가리킨다**:

| 줄 | 무엇을 그 파일에 위임하는가 |
|---|---|
| `:19` | **토픽 명명 규약** — `{domain}.{aggregate}.{version}` 인지 `{service}.…` 인지를 *"프로젝트가 선언한다"* |
| `:46` | **`eventType` 명명 규약** — *"Matches the naming convention **declared by the project in** `specs/contracts/events/README.md`"* |
| `:56` | **직렬화 선택**(JSON 기본, Avro/Protobuf 는 프로젝트가 고르면 *거기서 선언*) |
| `:68` | **스키마 레지스트리 채택 여부** |
| `:151` | **이벤트 계약 인덱스**(모든 이벤트 계약 + producer 목록) |

**그런데 그 파일은 7개 프로젝트 중 0개에 존재한다** (계약 파일 97개 전수 확인, `TASK-MONO-411`).

⇒ **다섯 개의 결정이 "프로젝트가 선언한다" 고 적힌 채, 선언될 자리가 없다.** 아무도 그 규약을 *어긴* 적이 없다 — **선언한 적이 없기 때문이다.** 그리고 지금 이벤트를 만드는 사람은 **토픽 이름을 자기 취향으로 정하고**, 그게 규약인지 우연인지 **누구도 판정할 수 없다.**

**이건 이 저장소가 반복해서 만난 그 축이다** — `MONO-404`(가드 규칙에 정경 홈이 없었다) · `PC-FE-241`(콘솔 컨벤션이 저장소 밖에만 있었다) · **`1곳에도 없는 규칙 = 없는 규칙`**. 다만 이번엔 더 나쁘다: **정경 문서가 존재하지 않는 파일을 다섯 번 인용하고 있어서, 규칙이 있는 것처럼 읽힌다.**

---

# Scope

## In Scope

1. **먼저 세라 (AC-1)** — 살아있는 토픽 이름·`eventType` 값·직렬화 방식을 **7개 프로젝트 전수**로 수집한다(`@KafkaListener`, 토픽 상수, producer, compose/config). **이 표가 이 티켓의 본체다** — 규약은 발명하는 게 아니라 **이미 실행 중인 것을 발견해 선언하는 것**이다.
2. **수렴 판정 (AC-2)** — 수집 결과가 (a) **이미 일관** → 그걸 그대로 `README.md` 로 선언 / (b) **갈라짐** → **갈라짐 자체를 기록**하고, 통일이 필요한지 별건으로 올린다. **이 티켓에서 라이브 토픽을 리네임하지 않는다**(이벤트 계약 변경 = 컨슈머 마이그레이션 = 자기 티켓).
3. **각 프로젝트에 `specs/contracts/events/README.md` 신설** — 5개 위임 항목(토픽 명명 · `eventType` 명명 · 직렬화 · 스키마 레지스트리 · 계약 인덱스)을 **실측에 근거해** 채운다. 프로젝트마다 다르면 다르게 적는다(그게 위임의 의미다).
4. **`event-driven-policy.md` 가 여전히 옳은지 확인** — 5개 포인터가 이제 실제 파일을 가리킨다.

## Out of Scope

- **토픽 리네임·`eventType` 변경·직렬화 전환 = 코드 변경 0.** 발견하고 선언한다. 통일이 필요하면 별건 + ADR 게이트(`event-driven-policy.md § Contract Rule` 이 breaking change 절차를 규정한다).
- `TASK-MONO-413` 의 스킬 정정(토픽에 버전 세그먼트 없는 스킬 5개) — **이 티켓의 AC-1 표가 413 의 AC-2 에 답을 준다.** 순서: **415 → 413**(또는 413 이 415 의 표를 인용). 스킬을 먼저 굳히면 **모르는 규약에 맞추는 것**이다.
- 이벤트 계약 인덱스의 *자동 생성*(가드/스크립트) — 매력적이지만 별건. **첫날 RED 인 가드는 꺼진다**(`MONO-360`).

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** `specs/contracts/events/README.md` 가 오늘도 **0개**인지 직접 확인(7개 프로젝트). 그리고 `event-driven-policy.md` 의 참조가 **정말 5개**인지 다시 센다 — **`TASK-MONO-411` 의 보고는 인계된 가설이다.**
  - 참조 개수: **5개** 확인(`:19` topic naming, `:46` eventType naming, `:56` serialization, `:68` schema registry, `:151` contract index) — 티켓 수치와 일치.
  - README 개수: **0/8** 확인 — **단, 모집단 자체가 7이 아니라 8이다.** `projects/` 는 `ecommerce-microservices-platform, erp-platform, fan-platform, finance-platform, iam-platform, platform-console, scm-platform, wms-platform` 8개 전부 `PROJECT.md` 를 갖는 유효 프로젝트. 티켓 본문·Related Specs·Definition of Done 이 반복해서 "7개"라 적은 것은 **인계된 가설이었고 재측정으로 반증됨**(모집단을 다시 세라 규율). 8개 전부에 README 신설로 처리함.
- [x] **AC-1 (전수 실측표)** 8개 프로젝트(재측정된 모집단) × (토픽 이름 목록 · `eventType` 값 형태 · 직렬화 · 레지스트리 사용 여부). 표는 아래 Implementation Notes 및 PR 본문에 게재.
- [x] **AC-2 (수렴 판정)** 각 축 CONSISTENT/DIVERGED 판정 완료 — 아래 Implementation Notes.
- [x] **AC-3 (README 신설)** 8개 프로젝트(재측정된 모집단) 전부에 `specs/contracts/events/README.md` 신설. 각 파일은 실측 근거 + 정경 포인터(규칙 본문 미복사).
- [x] **AC-4 (포인터가 살아있는가)** `event-driven-policy.md` 의 5개 참조 문자열이 이제 8개 프로젝트 전부에서 실제 파일로 해소됨(node fs.existsSync + bash `-f` 이중 확인, 상대링크 resolve 확인).
- [x] **AC-5** 8개 README 전부 `projects/<name>/specs/contracts/events/` 아래 — `platform/` 무변경 확인(`git status` — platform/ diff 없음).

---

# Related Specs

- `platform/event-driven-policy.md` (**정경 — `MONO-411` 이 확정**; L19·46·56·68·151 이 위임 지점)
- `tasks/done/TASK-MONO-411-platform-specs-contradict-each-other.md` (이 발견의 출처)
- `tasks/ready/TASK-MONO-413-skill-spec-drift-sweep.md` (**AC-2 가 이 티켓의 표를 필요로 한다**)
- `platform/service-types/event-consumer.md`

# Related Skills

- `.claude/skills/messaging/*`(413 이 다룬다 — 여기서 건드리지 말 것)

---

# Related Contracts

- 각 프로젝트 `specs/contracts/events/*`(읽기 — 인덱스 작성용)

---

# Target Service

N/A — 7개 프로젝트의 `specs/contracts/events/`.

---

# Implementation Notes

- **규약은 발명하지 말고 발견하라.** 이 README 들의 가치는 *"우리가 앞으로 이렇게 하자"* 가 아니라 *"우리가 이미 이렇게 하고 있다"* 를 **검증 가능하게 적어 두는 것**이다. 그래야 다음 사람이 규약과 우연을 구별할 수 있다.
- **프로젝트마다 달라도 된다** — `event-driven-policy.md` 가 명시적으로 **프로젝트에 위임**한 항목들이다. 통일이 목표였다면 위임하지 않았을 것이다. **다름을 기록하는 것과 드리프트를 방치하는 것은 다르다.**

## AC-0 재측정 결과 — 모집단이 티켓 숫자와 다르다

`projects/` 는 8개(`ecommerce-microservices-platform, erp-platform, fan-platform, finance-platform, iam-platform, platform-console, scm-platform, wms-platform`) 전부 `PROJECT.md` 를 가진 유효 프로젝트다. 티켓 본문·AC·DoD 는 반복해서 "7개 프로젝트"라 적었다 — **재측정으로 반증**(이 저장소의 "모집단을 다시 세라" 규율과 동일 패턴). 8개 전부 처리했다. `event-driven-policy.md` 의 참조 개수는 5개로 티켓 수치와 일치(반증 아님).

## AC-1 전수 실측표 (8개 프로젝트 — Glob 0건 자기검증 후 코드 교차확인 완료)

| 프로젝트 | 토픽 명명 | `eventType` 형태 | 직렬화 | 스키마 레지스트리 | 축별 수렴 판정 |
|---|---|---|---|---|---|
| **ecommerce-microservices-platform** | **DIVERGED** — 3가지 공존: 주류 `<context>.<aggregate>.<event>` 무버전(`order.order.placed`), settlement/ACL `.v1` 버전 포함(`settlement.period.closed.v1`, `ecommerce.fulfillment.requested.v1`, wms 소비 `wms.master.sku.v1`), IAM기원 flat 무prefix(`account.created`) | 주류 PascalCase(`OrderPlaced`) 1개 예외(settlement dot+version) | JSON(Jackson), Avro/Proto 0건 | 미사용 | 토픽·eventType 모두 **DIVERGED**; envelope 도 3-way DIVERGED(snake_case 주류/wms camelCase/IAM flat, 참고용) |
| **erp-platform** | **CONSISTENT** — `erp.<domain>.<fact>.v1`(`erp.approval.submitted.v1`) | **CONSISTENT** — dot-separated, 토픽 minus `.v1` | JSON(Jackson) | 미사용 | 전 축 CONSISTENT; envelope 는 스펙(9필드) vs 코드(7필드) 불일치 발견(참고용, 코드가 실측 권위) |
| **fan-platform** | **CONSISTENT** — `<eventType>.v1`(`fan.membership.activated.v1`) | **CONSISTENT** — dot-separated | JSON(Jackson) | 미사용 | 전 축 CONSISTENT; artist/community 이벤트는 발행되나 **소비자 0개**(스펙에 "(planned)"로 정직하게 기록됨) |
| **finance-platform** | **CONSISTENT** — `finance.<domain>.<action>.v1` | **CONSISTENT** — dot-separated | JSON(Jackson) | 미사용 | 토픽·eventType·직렬화·레지스트리 CONSISTENT; **envelope 는 DIVERGED**(account-service 7필드 vs ledger-service 8필드 — 실사용 중, 강제통일 안 함) |
| **iam-platform** | **CONSISTENT** — bare(무prefix), 토픽=eventType 문자열 그대로(`account.created`) | **CONSISTENT** — dot-separated | JSON(Jackson), payload=JsonNode | 미사용 | 전 축 CONSISTENT; `session-events.md` 가 코드에 없는 `session.revoked` 를 기술(stale spec, 실제는 `auth.session.revoked`) — 후속 문서수정 필요, 이번 스코프 아님 |
| **scm-platform** | **DIVERGED** — procurement `scm.procurement.<agg>.<fact>.v1`(named const, outbox) vs inventory-alert 단일 공유토픽 `scm.inventory.alert.v1`(문자열 concat, no outbox) + wms 소비 토픽(3rd party 컨벤션) | **DIVERGED** — procurement 는 `scm.` prefix + 상수, inventory-alert 는 prefix 없이 런타임 concat | JSON(Jackson) | 미사용 | 토픽·eventType·delivery guarantee 모두 **DIVERGED**(강제통일 안 함, 후속 ADR 제안) |
| **wms-platform** | **CONSISTENT** — `wms.<service>.<family>.v<N>`(`wms.master.warehouse.v1`) | **거의 CONSISTENT** — dot-separated, 1개 명시적 예외(`inventory.low-stock-detected` hyphenated, 스펙에도 의도된 예외로 기록) | JSON(Jackson) | 미사용 | 전 축 CONSISTENT(7개 중 가장 깨끗함) |
| **platform-console** | N/A | N/A | N/A | N/A | **도메인 이벤트 발행/구독 없음** — ADR-MONO-013/017 확정 stateless BFF, Kafka 코드 0건(모든 "kafka" 매치가 부재를 명시하는 주석). 명시적 부재 선언으로 처리(빈 파일 아님). |

**플랫폼 전체 교차축 판정**: 직렬화(JSON, 전 8개 CONSISTENT) · 스키마 레지스트리(미사용, 전 8개 CONSISTENT — 부재 자체가 일관)는 프로젝트 간에도 수렴. 토픽 명명·eventType 명명은 프로젝트 간에도 **의도된 DIVERGED**(정경이 프로젝트에 위임한 축 그대로 — `{domain}.{aggregate}.{version}` OR `{service}.{aggregate}.{version}` 중 택1 + PascalCase OR dot-separated 중 택1, 정경이 이미 이 다양성을 명시적으로 허용).

## Out of scope 로 넘긴 후속 항목 (코드 변경 0, 발견만)

1. ecommerce: 토픽 명명 3-way divergence + envelope 3-way divergence — 통일 필요시 별도 ADR 게이트 티켓.
2. erp/finance: 스펙 파일이 옛 envelope 필드 수(9)를 기술, 코드는 7(erp)/7·8(finance) — 스펙 문서 보정 후속 필요.
3. finance: account-service 7필드 vs ledger-service 8필드 envelope divergence — 통일 여부는 컨슈머 마이그레이션 수반, 별도 ADR.
4. iam: `session-events.md` 가 코드에 없는 이벤트를 기술(stale) — 문서 재조정 후속.
5. scm: inventory-alert 계열이 procurement 계열과 명명·delivery guarantee 모두 다름 — 통일 필요성 판단은 후속 ADR.
6. `TASK-MONO-413`(스킬 정정)은 이 표를 인용해 진행 가능 — 순서 규율(415 → 413) 충족.

---

# Edge Cases

- **이벤트를 발행하지 않는 프로젝트**가 있을 수 있다(예: 콘솔). 그러면 README 는 *"이 프로젝트는 도메인 이벤트를 발행하지 않는다"* 라고 적는다 — **빈 파일이 아니라 명시적 부재 선언.** 0건도 결과다.
- **`eventType` 이 봉투 안에만 있고 토픽엔 없는 구조**(정경이 그렇게 규정) — 수집할 때 토픽과 eventType 을 **섞지 마라.**

---

# Failure Scenarios

- **실측 없이 "이상적인" 규약을 적는다** → README 가 코드와 어긋난 채 정경이 되고, 다음 사람이 그걸 믿는다. 완화 = AC-1/AC-2.
- **갈라진 것을 하나로 억지 통일** → 라이브 컨슈머가 깨지거나, README 가 거짓말을 한다. 완화 = AC-2(갈라짐을 적는다) + Out of Scope(리네임 금지).
- **413 을 먼저 한다** → 스킬을 **아직 없는 규약**에 맞추게 된다. 완화 = 순서 규율(415 → 413).

---

# Test Requirements

- doc-only. **증명 = AC-1 실측표 + AC-4 링크 해소.**

---

# Definition of Done

- [x] ~~7개~~ **8개**(재측정, AC-0) 프로젝트 README 신설(발행 없는 프로젝트 `platform-console` 은 명시적 부재 선언).
- [x] AC-1 표 · AC-2 판정 PR 본문 게재.
- [x] `event-driven-policy.md` 의 5개 포인터 전부 해소.
- [ ] `tasks/INDEX.md` done entry(close chore) — review 단계 진입, close chore 는 별도(리뷰 통과 후).

---

# Provenance

2026-07-15 `TASK-MONO-411` 구현 중 발견. 411 이 `event-driven-policy.md` 를 이벤트 버저닝의 정경으로 확정한 **직후**, 그 정경이 5번 가리키는 결정 지점이 **저장소에 존재하지 않음**이 드러났다(계약 파일 97개 전수 확인). 사용자 승인으로 티켓팅(2026-07-15).

분석=Opus 4.8 / 구현 권장=**Sonnet**(전수 수집 + 표 작성이 본체. 단 AC-2 에서 갈라짐이 나오면 판단은 사람에게).
