# Task ID

TASK-MONO-372

# Title

`Service Type` 한 칸이 **어떤 규칙 파일을 로드할지** 정하는데, 그게 3개 서비스에서 거짓이다 — HARDSTOP-10 은 "선언됐는가" 만 보고 **참인지는 아무도 안 본다**

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- guard
- ci

---

# Dependency Markers

- **발굴 출처**: 2026-07-12 미티켓 3차원 스윕(선언↔진실 축).
- **같은 계열**: `MONO-345`(service map) · `MONO-352`(error registry) · `MONO-360`(gateway) · `MONO-363`/`369`(ADR index) · `MONO-371`(JWT 클레임). **`check-service-map-drift.sh` 가 형태 참조** — 디렉터리/코드의 진실 ↔ 문서의 선언, 양방향.
- **선행 흔적**: `TASK-MONO-277`(done) 이 **바로 이 표면**을 손봤으나 **doc-only** 였고, 그 AC 가 *"No existing service declaration becomes non-conforming"* 이라 단언했다 — **product-service 에 대해 그 단언은 거짓이다.** 선언을 코드와 대조한 적이 한 번도 없다.

---

# Goal

각 서비스의 `specs/services/<svc>/architecture.md` 는 `| Service Type | … |` 한 칸을 선언한다. 그 칸은 장식이 아니다:

> `platform/entrypoint.md:39` — *"Service-Type-Specific: Read **exactly one** file matching the target service's declared `Service Type`"*

**즉 그 한 칸이 이 서비스에 어떤 MUST 들이 적용될지를 고른다.** 그런데 `HARDSTOP-10` 은 **타입이 선언돼 있고 카탈로그에 존재하는가**만 본다. **그것이 참인지는 아무도 검사하지 않는다.**

## 착수 전 실측 (2026-07-12, `875e583cd`) — 코드가 권위

전 서비스의 선언 ↔ `src/main` 의 어노테이션을 양방향 대조:

| 서비스 | 선언 | 실제 | 드리프트 |
|---|---|---|---|
| ecommerce `product-service` | `rest-api` **(single)** | `@KafkaListener` **6개** (3 컨슈머 그룹, 7 토픽) | **`event-consumer` 누락** |
| ecommerce `notification-service` | `event-consumer` **(single)** | `@RestController` **4개** (엔드포인트 14개) | **`rest-api` 누락** |
| wms `inbound-service` | `rest-api` (primary) | `@KafkaListener` **3개** | **`event-consumer` 누락**(단, Composition 산문은 정직 — 아래 참조) |

**나머지 48개 선언은 통과한다.** 즉 가드는 **3줄 고치면 즉시 초록**이 되고, 계속 초록으로 남는다.

## 실제로 발현된 결과 (product-service)

선언이 `rest-api` 라서 **`platform/service-types/event-consumer.md` 가 이 서비스의 규칙 집합에 한 번도 들어간 적이 없다.** 그 파일의 MUST(멱등 · Retry/DLQ · `eventVersion` 분기 · 구독 토픽 선언)가 **라이브 컨슈머 6개에 적용된 적이 없다.**

- `architecture.md:116` 은 지금도 *"**Idempotency**: all **three** consumers dedupe on `event_id`"* 라고 적혀 있다 — **6개다.** spec 이 쓰인 뒤 추가된 컨슈머들이 규칙 밖에 있었다.
- `AccountStatusChangedSellerConsumer` 만 `event_id` dedupe 참조가 **0**이다(나머지 5개는 4~7).

**⚠️ 정직하게 — 이건 라이브 버그가 아니다.** 그 컨슈머를 읽어보면 **자연 멱등**이다(이미 SUSPENDED 면 `false` 반환 + DEBUG 로그, 부수효과 없음). **그것을 결함이라 부르면 phantom 이다.** 결함은 *"spec 이 3개라고 말하는데 6개고, 그 6개가 event-consumer 규칙의 심사를 한 번도 안 받았다"* 는 사실 자체다. 이 컨슈머의 멱등 전략은 **정당하지만 검토된 적이 없다.**

## notification-service (ecommerce) — 문서 두 개가 서로 반대말을 한다

`event-consumer` **(single)** 로 선언하고 Identity 표에 *"Event publication | none (terminal consumer)"* 라 적었는데, **`@RestController` 4개 · 엔드포인트 14개**를 서빙한다. 게다가 **저장소가 그 서비스의 HTTP 계약서를 발행하고 있다**(`specs/contracts/http/notification-api.md`).

⇒ **한 손-유지 표면은 HTTP 계약을 발행하고, 다른 손-유지 표면은 그 서비스에 HTTP 표면이 없다고 선언한다.** `rest-api.md` 는 이 서비스에 로드된 적이 없다.

---

# Scope

## In Scope

1. **선언 3건 교정** — `product-service`(+`event-consumer`) · ecommerce `notification-service`(+`rest-api`) · wms `inbound-service`(+`event-consumer`). **Composition 섹션도 함께 갱신**(`platform/service-types/INDEX.md` 규약).
2. **product-service 의 stale 산문 교정** — `architecture.md:116` "all three consumers" → 실제 6개 + 각 컨슈머의 멱등 전략을 사실대로 기술(**`AccountStatusChangedSellerConsumer` 는 `event_id` dedupe 가 아니라 상태-전이 멱등**임을 명시. 코드를 문서에 맞추지 말 것 — § Out of Scope).
3. **가드** — `scripts/check-service-type-drift.sh` + CI 잡. **두 방향만**:
   - `src/main` 에 `@KafkaListener` 존재 ⇒ Service Type 칸에 `event-consumer` 포함
   - `src/main` 에 `@RestController` 존재 ⇒ 칸에 `rest-api`(또는 `identity-platform`) 포함
4. **CI 배선** — pure-positive 필터(negation 금지). 경로 = `projects/*/specs/services/*/architecture.md` + `projects/*/apps/*/src/main/**/*.java` + 스크립트. **`code-changed` 와 AND 금지** — 이 드리프트는 **architecture.md 한 줄 수정 = markdown-only 로도 도착한다**(MONO-360 이 실측한 실패 모드).

## Out of Scope

- **`@Scheduled` ⇒ `batch-job` 축 — 절대 만들지 말 것.** ~30개 서비스가 outbox relay/cleanup 용 `@Scheduled` 를 갖고 있고 **아무도 `batch-job` 을 선언하지 않으며 그게 옳다.** 이 축은 순수 노이즈 → 첫날 30개 RED.
- **역방향 `rest-api` ⇒ `@RestController` 필수 — 절대 만들지 말 것.** 게이트웨이 8개가 전부 `rest-api` 를 선언하고 `@RestController` 가 **0개**다(Spring Cloud Gateway 라우트). 프런트엔드는 자바가 아예 없다. → 첫날 RED.
- **코드를 문서에 맞추기** — 컨슈머를 지우거나 컨트롤러를 옮기지 않는다. **코드가 권위, 문서를 고친다.**
- **`AccountStatusChangedSellerConsumer` 에 `event_id` dedupe 추가** — 자연 멱등이므로 **불필요**하다. 넣으면 없던 테이블·경합·실패 모드를 새로 만든다. **문서가 사실을 말하게 하는 것으로 충분하다.**

---

# Acceptance Criteria

- [ ] **AC-1 — 선언 3건이 참이 된다.** Service Type 칸 + Composition 섹션 둘 다.
- [ ] **AC-2 — product-service 산문이 사실을 말한다.** 컨슈머 6개, 그리고 그중 하나는 `event_id` dedupe 가 **아닌** 멱등 전략을 쓴다는 것.
- [ ] **AC-3 — 가드**: `check-service-type-drift.sh` 가 위 **두 방향만** 검사한다. 현 트리 GREEN(3건 교정 후), **비-vacuous**(architecture.md 0개나 모듈 0개를 파싱하면 exit 2).
- [ ] **AC-4 — mutation, 전부 물어야 함** (**주입이 적용됐는지 먼저 확인**):
  1. product-service 칸에서 `event-consumer` 제거 → **RED**
  2. notification-service 칸에서 `rest-api` 제거 → **RED**
  3. `@KafkaListener` 가 없는 서비스 칸에 아무 타입이나 넣어도 **통과**(가드가 과잉 주장하지 않는지)
  4. **vacuity**: baseline exit=0.
- [ ] **AC-5 — 오탐 0.** 51개 선언 전부 GREEN. **게이트웨이 8개(RC=0, `rest-api` 선언) 와 `@Scheduled` 30개가 RED 를 내지 않는다** — 이게 이 가드의 가장 큰 오탐 위험이다.
- [ ] **AC-6 — 도달 가능성**: 필터가 `code-changed` 와 **AND 되지 않는다**(architecture.md 한 줄 = markdown-only 로 도착).
- [ ] **AC-7** — 코드 변경 0 → `./gradlew check` 무영향.

---

# Related Specs

- `platform/entrypoint.md` L39 — Service Type 이 규칙 로딩을 고른다는 규정
- `platform/service-types/INDEX.md` — 타입 카탈로그 + Composition 규약
- `platform/hardstop-rules.md` HARDSTOP-10 — "선언됐는가" 만 검사(참인지는 안 봄)
- `scripts/check-service-map-drift.sh` (MONO-345) — 가드 참조 구현
- `tasks/done/TASK-MONO-277-*.md` — 이 표면을 doc-only 로 손보며 *"기존 선언 중 non-conforming 없음"* 이라 **잘못 단언**한 선행 task

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/notification-api.md` — **HTTP 표면이 없다고 선언된 서비스의 HTTP 계약서**(이 모순이 발견의 실마리).

---

# Edge Cases

- **wms `inbound-service` 는 "정직한" 드리프트다** — 칸에는 `event-consumer` 가 없지만 **Composition 산문이 event-consumer 경로를 명시하고 `event-consumer.md` 를 읽으라고 지시한다.** 산문은 정직하고 칸만 불완전하다. **가드는 칸을 보므로 이걸 문다 — 그게 맞다**(칸이 규칙 로더가 읽는 유일한 것이므로). 단 close 노트에 이 뉘앙스를 남길 것.
- **`identity-platform` 타입** — iam 의 auth/account/admin 은 `rest-api` 가 아니라 이 타입이다. `@RestController` 검사에서 허용해야 한다.
- **`src/main` 없는 서비스** — 프런트엔드(web-store, console-web …)는 자바 모듈이 아니다. 스킵.

# Failure Scenarios

- **F1 — `@Scheduled` 축을 넣는다** → 첫날 30개 RED → 가드가 꺼진다. Guard: § Out of Scope + AC-5.
- **F2 — 역방향(`rest-api` ⇒ RC 필수)을 넣는다** → 게이트웨이 8개 RED. Guard: § Out of Scope + AC-5.
- **F3 — `AccountStatusChangedSellerConsumer` 에 dedupe 를 "고쳐" 넣는다** → 자연 멱등인 코드에 불필요한 테이블·경합을 도입. Guard: § Out of Scope.
- **F4 — `code-changed` 와 AND** → architecture.md 한 줄 수정(= 이 드리프트의 도착 형태)에서 가드가 꺼진 채 초록. Guard: AC-6.

---

# Test Requirements

- `bash scripts/check-service-type-drift.sh` — 3건 교정 후 GREEN(비-vacuous), mutation 3방향.

---

# Definition of Done

- [ ] AC-1 ~ AC-7.
- [ ] `tasks/INDEX.md` done entry — **wms inbound-service 의 "정직한 드리프트" 뉘앙스 기록**.

---

# Provenance

발굴 2026-07-12 — 미티켓 3차원 스윕(선언↔진실 축).

**왜 오래 살아남았나**: 런타임은 멀쩡하다. 컨슈머는 돌고 컨트롤러는 응답한다. **틀린 것은 "이 서비스에 어떤 규칙이 적용되는가" 뿐이고, 그건 아무 테스트도 실행하지 않는 문장이다.** 비용은 **다음에 이 서비스를 건드리는 사람**이 낸다 — 그는 로드되지 않은 MUST 를 알 방법이 없다.

**스윕이 이걸 "멱등 결함" 으로 몰 뻔했다** — dedupe 0인 컨슈머를 발견하고 결함이라 보고했으나, **실제로 읽어보니 자연 멱등**이었다. 그대로 티켓팅했으면 phantom 이었다(`project_untickected_backlog_sweep_2026_07_11` 의 교훈이 그대로 재현).

분석=Opus 4.8 / 구현 권장=Opus(오탐 축 배제 판단이 이 task 의 절반이다 — `@Scheduled`·게이트웨이 역방향을 넣는 순간 가드가 죽는다).
