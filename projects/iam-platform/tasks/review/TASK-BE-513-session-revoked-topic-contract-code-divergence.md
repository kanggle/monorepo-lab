# Task ID

TASK-BE-513

# Title

`session.revoked`(계약) vs `auth.session.revoked`(코드) 토픽 불일치 — 계약이 선언한 소비 관계가 코드에 없음

# Status

review

# Owner

backend

# Task Tags

- spec
- code

---

# 🔴 발견 경위 (2026-07-16, TASK-BE-511 부수 발견)

`TASK-BE-511`(Kafka init 토픽 정합) 착수 중, 생산 토픽을 outbox 퍼블리셔에서 재열거하다 발견. **계약↔코드 토픽명 불일치**이며, 이름만이 아니라 **선언된 소비 관계가 코드에 존재하지 않는** 더 깊은 드리프트다. BE-511 은 compose init 목록만 다뤘고(생산되는 `auth.session.revoked` 초기화·미생산 `session.revoked` 제거), **불일치 자체의 해소는 이 티켓으로 분리**했다.

**⚠️ AC-0 재측정 필수** — 아래 관측은 2026-07-16 스냅샷(가설). 착수 시 코드·계약을 다시 세라(코드가 이긴다).

---

# Goal

세션 무효화 이벤트의 **토픽명·소비 관계를 계약과 코드 사이에서 정합**한다. 현재 상태:

## 관측된 드리프트 (스냅샷, 재검증 대상)

1. **계약 `session-events.md`**: Topic = **`session.revoked`**, 발행 주체 = auth-service, **Consumers = security-service(login_history) + 관측성**.
2. **코드**: `auth-service` 의 `AuthOutboxPublisher` 는 **`auth.session.revoked`** 를 생산(`TOPIC_SESSION_REVOKED = "auth.session.revoked"`; `publishAuthSessionRevoked` 다수 호출부). `session.revoked`(무접두)를 생산하는 코드는 **없음**.
3. **소비자**: BE-511 재열거는 `session.revoked` **와** `auth.session.revoked` **둘 다 `@KafkaListener` 소비자가 0** 임을 확인. 그런데 `session-events.md` 는 security-service 가 이를 소비해 login_history 에 revocation 이력을 남긴다고 **선언**한다 — 즉 **계약이 약속한 소비가 코드에 미구현**(login_history 는 실측상 `auth.login.*` 로 채워지고, session-revocation 이력 소비는 배선 안 됨).
4. **중복 선언 가능성**: `auth-events.md` 도 `auth.session.revoked`(+`auth.session.created`)를 선언. `session-events.md` 의 `session.revoked` 와 **같은 논리 이벤트가 두 이름으로** 선언돼 있을 수 있다 — 착수 시 두 계약의 관계를 확정할 것.

## 오늘 무해한 이유 / 왜 그래도 고치나

오늘 양쪽 토픽 다 in-repo 소비자가 0이라 **당장 기능 문제는 없다.** 그러나 이건 **잠재 함정**이다: 미래 소비자(내부 신규 서비스 또는 외부 consumer)가 `consumer-integration-guide.md`/`session-events.md` 를 읽고 **계약대로 `session.revoked` 를 구독하면 이벤트를 0건** 받는다(생산은 `auth.session.revoked` 로 흐르므로). 계약이 거짓을 말하는 상태 — CI 가 절대 못 잡는다(선언↔진실 축).

# 🔵 착수 시 결정 필요 (A vs B — 사람 판단)

CLAUDE.md 소스오브트루스는 **계약 > 코드**(layer 6–9 > 14)지만, 여기서는 코드가 이미 `auth.session.revoked` 를 생산하고 BE-511 이 그 토픽을 init 에 넣었다. 두 방향:

- **(A) 계약을 코드에 맞춤** — `session-events.md` 의 토픽을 `auth.session.revoked` 로 정정(또는 `auth-events.md` 로 병합해 중복 제거). 코드=de-facto 현실, in-repo 소비자 0이라 저위험. 문서 변경 중심.
- **(B) 코드를 계약에 맞춤** — 생산 토픽을 `session.revoked` 로 rename(+BE-511 이 넣은 init 명도 되돌림). 계약 권위를 존중하지만, **외부(비-repo) 소비자가 `auth.session.revoked` 를 구독 중일 위험**을 배제 못 함(포트폴리오 데모라 실외부 소비자는 없을 개연성이나 확인 필요).

**추가로 결정할 것**: `session-events.md`(session.revoked) 와 `auth-events.md`(auth.session.revoked) 가 같은 이벤트면 **하나로 병합**할지, 별개 의미(cross-cutting vs auth-domain)로 **둘 다 유지**할지.

권장 출발점: **(A)** + 중복이면 `auth-events.md` 로 수렴(문서 정합, net-zero 런타임). 단 착수 전 AC-0 로 "정말 소비자 0" 재확인(내부·외부·관측성 파이프라인).

# Scope

## In Scope
- `specs/contracts/events/session-events.md` 와 `auth-events.md` 의 세션 무효화 이벤트 선언 정합(택한 방향대로).
- (B 선택 시에만) `auth-service` 생산 토픽 상수 + `TASK-BE-511` 이 넣은 compose init 명 rename.
- `session-events.md` 의 **소비 관계 서술 정정**: security-service login_history 가 실제로 session-revocation 을 소비하지 않는다면 그 문장을 현실에 맞춤(또는 그 소비를 구현하기로 결정하면 별도 후속으로 분리 — 이 티켓의 코어는 토픽명/선언 정합이지 미구현 소비 배선 추가가 아님).
- `consumer-integration-guide.md` 등 세션 이벤트를 참조하는 문서의 토픽명 정합.

## Out of Scope
- security-service 가 session-revocation 을 실제로 소비해 login_history 에 기록하는 **신규 기능 구현**(원한다면 별도 티켓 — 이건 "계약이 약속한 미구현 소비"이지 이번 정합의 대상 아님).
- `auth.session.created` 등 다른 세션 이벤트(불일치 없으면 무접촉).
- `TASK-BE-511` 이 이미 정리한 compose init 목록(방향 B 가 아니면 무변경).

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: (a) `auth-service` 가 실제로 생산하는 세션 무효화 토픽명 재확인(outbox 상수+호출부), (b) `session.revoked`/`auth.session.revoked` 의 in-repo 소비자(`@KafkaListener`)·관측성 파이프라인 전수 재grep — **정말 0인지**, (c) `session-events.md` ↔ `auth-events.md` 가 같은 이벤트인지 확정. **하나라도 스냅샷과 다르면 코드/실측이 이긴다.**
- [ ] **AC-1 (사람 결정)**: A vs B + 중복 병합 여부.
- [ ] 계약과 코드가 **같은 토픽명**을 말한다(선택 방향대로). 미래 소비자가 계약대로 구독하면 실제 생산 토픽에 도달.
- [ ] `session-events.md` 의 소비 관계 서술이 **현실과 일치**(미구현 소비를 사실인 양 선언하지 않음).
- [ ] 세션 이벤트 토픽명을 참조하는 모든 문서 정합(`consumer-integration-guide.md` 등).
- [ ] (B 선택 시) `:apps:auth-service:test` + `E2E full (iam docker-compose)` CI GREEN; init 명 정합.

# Related Specs / Contracts

- `specs/contracts/events/session-events.md` (`session.revoked` 선언 — 정합 대상)
- `specs/contracts/events/auth-events.md` (`auth.session.revoked` 선언 — 중복 여부 확인)
- `specs/features/session-management.md` (세션 무효화 트리거)
- `specs/features/consumer-integration-guide.md` (신규 소비자가 구독할 토픽 목록)
- (B 선택 시) `apps/auth-service` 의 `AuthOutboxPublisher` + `projects/iam-platform/docker-compose*.yml` 의 kafka-init

# Target Service

- `auth-service` (생산자, B 선택 시) + 계약 문서

# Edge Cases

- 외부(비-repo) 소비자가 `auth.session.revoked` 를 구독 중이면 B(코드 rename)는 그들을 깨뜨린다 — AC-0 (b)에서 **관측성/외부 파이프라인까지** 확인.
- `session-events.md` 와 `auth-events.md` 병합 시 envelope/payload 필드가 미묘하게 다를 수 있음(`revokedJtis`/`revokeReason` vs auth-events 의 세션 필드) — 승격/병합은 무손실이 아니다, 대조 필수.
- BE-511 이 compose 주석에 이미 이 불일치를 문서화했으므로, 방향 확정 후 그 주석도 갱신.

# Failure Scenarios

- 토픽명만 바꾸고 소비 관계 서술을 방치하면, 계약이 여전히 "security-service 가 소비한다"는 거짓을 유지(선언↔진실 갭 미해소).
- 스냅샷 "소비자 0"을 재확인 없이 믿고 B 로 rename → 실제 외부 소비자 파손.
- 중복(session.revoked/auth.session.revoked)을 못 보고 한쪽만 고치면 드리프트 잔존.

# Definition of Done

- [ ] AC-0 재측정 + A/B·병합 결정
- [ ] 계약·코드·문서 토픽명 정합, 소비 관계 서술 현실화
- [ ] (B 선택 시) CI GREEN
- [ ] Ready for review
