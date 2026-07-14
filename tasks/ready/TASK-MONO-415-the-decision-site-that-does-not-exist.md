# Task ID

TASK-MONO-415

# Title

정경 스펙이 **5번 가리키는 결정 지점이 존재하지 않는다** — `specs/contracts/events/README.md` 는 7개 프로젝트 어디에도 없다

# Status

ready

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

- [ ] **AC-0 (재측정)** `specs/contracts/events/README.md` 가 오늘도 **0개**인지 직접 확인(7개 프로젝트). 그리고 `event-driven-policy.md` 의 참조가 **정말 5개**인지 다시 센다 — **`TASK-MONO-411` 의 보고는 인계된 가설이다.**
- [ ] **AC-1 (전수 실측표)** 7개 프로젝트 × (토픽 이름 목록 · `eventType` 값 형태 · 직렬화 · 레지스트리 사용 여부). **표가 PR 본문에 실린다.** ⚠️ **`Glob` 이 절대경로에서 0건을 반환한 전례가 있다**(MONO-411 실측 중) — **아는 답에 먼저 돌려 자기검증**하고, 못 믿겠으면 `Get-ChildItem` 으로 교차 확인하라. **빈 출력은 부재가 아니다.**
- [ ] **AC-2 (수렴 판정)** 각 축이 **일관인지 갈라졌는지** 판정. **갈라졌으면 갈라짐을 적는다** — 억지로 하나로 쓰면 그 README 가 **거짓 선언**이 되고, 이 저장소는 거짓 선언에 여러 번 대가를 치렀다.
- [ ] **AC-3 (README 신설)** 7개 프로젝트에 `specs/contracts/events/README.md` 신설. 각 파일은 **실측에 근거**하고, 정경(`platform/event-driven-policy.md`)을 **가리키되 규칙 본문을 복사하지 않는다**.
- [ ] **AC-4 (포인터가 살아있는가)** `event-driven-policy.md` 의 5개 참조가 **실제 파일로 해소**된다. 링크 체크로 확인.
- [ ] **AC-5** 프로젝트별 README 는 **프로젝트 문서**다 — `platform/` 에 프로젝트 내용이 새지 않는다(HARDSTOP-03).

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

- [ ] 7개 프로젝트 README 신설(발행 없는 프로젝트는 명시적 부재 선언).
- [ ] AC-1 표 · AC-2 판정 PR 본문 게재.
- [ ] `event-driven-policy.md` 의 5개 포인터 전부 해소.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `TASK-MONO-411` 구현 중 발견. 411 이 `event-driven-policy.md` 를 이벤트 버저닝의 정경으로 확정한 **직후**, 그 정경이 5번 가리키는 결정 지점이 **저장소에 존재하지 않음**이 드러났다(계약 파일 97개 전수 확인). 사용자 승인으로 티켓팅(2026-07-15).

분석=Opus 4.8 / 구현 권장=**Sonnet**(전수 수집 + 표 작성이 본체. 단 AC-2 에서 갈라짐이 나오면 판단은 사람에게).
