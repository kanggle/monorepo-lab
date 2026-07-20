# TASK-BE-539 — `uq_notifications_event_id` 가 코드가 쓰는 모양과 다르다: 정상 이벤트가 DLQ 로 조용히 사라진다

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (인덱스 재정의 + 마이그레이션 + 다채널 전송 IT. 재고·금전은 아니지만 데이터 유실 경로다)

> `TASK-BE-538` **Edge 3**(NATURAL-KEY 실거동 확인) 수행 중 발견. 멱등성 감사에서 나왔지만 **멱등성 결함이 아니다** — 유니크 인덱스가 코드의 쓰기 모양과 어긋나 **중복이 아닌 최초 전송이 제약을 위반**한다.

---

## Goal

`notification-service` 의 이벤트 중복 제거 인덱스는 이렇게 선언돼 있다:

```sql
-- V4__add_unique_constraint_event_id.sql:3
CREATE UNIQUE INDEX uq_notifications_event_id ON notifications(event_id) WHERE event_id IS NOT NULL;
```

**`event_id` 하나당 행 하나**를 강제한다. 그런데 전송 코드는 **채널당 한 행**을 쓴다:

```java
// NotificationSendService.java:60-62
for (NotificationChannel channel : NotificationChannel.values()) {
    sendViaChannel(command, channel, preference, senderMap);
}

// NotificationSendService.java:88-89, :102 — 채널마다 호출된다
Notification notification = Notification.create(
        command.tenantId(), command.userId(), channel, renderedSubject, renderedBody, command.eventId());
...
notificationRepository.save(notification);
```

즉 **한 이벤트가 EMAIL 행 1개 + PUSH 행 1개를 같은 `event_id` 로 저장**한다. 두 행의 `event_id` 가 같으므로 두 번째 INSERT 가 `uq_notifications_event_id` 를 위반한다.

### 이것은 재전송이 아니라 최초 전송에서 터진다

`NotificationSendService.java:52` 의 `existsByEventId(eventId, tenantId)` 중복 제거는 **재전송에 대해서는 의도대로 동작한다.** 문제는 그 검사를 통과한 **정상 최초 전송**이다:

1. 커밋 시점 flush 에서 두 번째 채널 INSERT 가 위반 → `DataIntegrityViolationException`
2. 트랜잭션 전체 롤백 → `notifications` 에 아무 행도 커밋되지 않음
3. `KafkaConsumerConfig.java:29` `setMaxAttempts(3)` → 재시도 3회. **매 재시도마다 `existsByEventId` 는 여전히 empty**(롤백됐으므로) → 전체 루프 재실행 → 동일 실패
4. 3회 소진 후 `<topic>.dlq` 로 이동 → **알림이 발송 기록 없이 사라진다**

`DataIntegrityViolationException` 은 `KafkaConsumerConfig.java:32` 의 non-retryable 목록에 없어 재시도 예산을 전부 태운다.

### 발화 조건 (오늘 안 터지는 이유)

한 테넌트에 **같은 `TemplateType` 에 대해 EMAIL 템플릿과 PUSH 템플릿이 둘 다** 있어야 한다 — `NotificationSendService.java:79` 가 채널별로 템플릿을 찾고 없으면 건너뛰기 때문이다. 템플릿은 마이그레이션 시드가 아니라 **관리자가 생성**한다. 그래서 지금은 잠재적이고, **관리자가 한 타입에 두 채널 템플릿을 만드는 순간 발화**한다.

등록된 sender 는 `EmailNotificationSender`(EMAIL) 와 `WebPushSender`(PUSH) 둘이고, 기본 선호(`UserNotificationPreference.createDefault`)가 둘 다 켠다 — **AC-0 에서 재확인할 것.**

---

## Scope

### In Scope

1. 인덱스를 **코드가 실제로 쓰는 모양**으로 재정의: `(tenant_id, event_id, channel)`.
2. 새 Flyway 마이그레이션(`V8__*`) + `migration-h2` 대응본이 있으면 함께.
3. 다채널 동시 전송 IT — **현재 코드에서 RED 여야 한다**(Edge 1).
4. 재전송 중복 제거가 여전히 동작하는지 회귀 확인.

### Out of Scope

- `existsByEventId` 의 테넌트 스코프 자체는 건드리지 않는다 — 인덱스에 `tenant_id` 를 넣으면 선체크와 제약의 범위가 **일치**하게 되므로 그것으로 해소된다.
- 선체크↔제약 범위 불일치의 *다른* 사례는 `TASK-BE-540`.
- DLQ 적재 알림/모니터링 부재는 별건 — 이 task 는 DLQ 로 가는 원인을 없앤다.
- `DataIntegrityViolationException` → 409 핸들러 부재는 `TASK-BE-542`.

---

## Acceptance Criteria

- **AC-0 (gate — 발화 조건 재측정)** — 착수 시 다음을 **직접 다시 확인**한다. 아래 서술은 출처가 아니라 가설이다:
  (a) `NotificationChannel` 의 값 개수와 각 채널에 등록된 sender 빈;
  (b) `UserNotificationPreference.createDefault` 가 실제로 켜는 채널;
  (c) `uq_notifications_event_id` 를 V4 이후 어떤 마이그레이션도 재정의하지 않았다는 것(V5~V7 확인);
  (d) 프로덕션/데모 DB 에 한 타입 2채널 템플릿이 이미 존재하는지.
  **(d) 가 참이면 이 결함은 잠재가 아니라 이미 발화 중**이며 티켓 우선순위가 올라간다.
- **AC-1** — 다채널(EMAIL+PUSH) 템플릿이 모두 있는 상태에서 이벤트 1건을 소비하면 `notifications` 에 **채널당 1행씩 커밋**되고 예외가 없다.
- **AC-2 (guard)** — AC-1 의 IT 를 **인덱스 수정 전 코드에 대고 먼저 실행해 RED 를 확인**한다. RED 를 못 보면 테스트가 발화 조건을 재현하지 못한 것이다(Edge 1).
- **AC-3** — 동일 `event_id` 재전송은 여전히 `NotificationSendService.java:52` 에서 걸러져 **추가 행이 생기지 않는다.**
- **AC-4** — 같은 `event_id` 가 **다른 테넌트**에서 오면 각 테넌트가 자기 행을 갖는다(새 인덱스가 `tenant_id` 를 포함하므로).
- **AC-5** — 마이그레이션이 기존 데이터에 대해 적용 가능하다. 기존 `notifications` 에 중복 `event_id` 가 있을 수 없으므로(옛 인덱스가 막았으므로) 인덱스 교체는 무손실 — **그러나 이를 가정하지 말고 마이그레이션에 검증 쿼리나 주석 근거를 남긴다.**

---

## Related Specs

- `apps/notification-service/src/main/resources/db/migration/V4__add_unique_constraint_event_id.sql` — 대상 인덱스
- `apps/notification-service/src/main/resources/db/migration/V5__add_tenant_id.sql` — `tenant_id` 를 추가했으나 **유니크 인덱스는 손대지 않았다**(비유니크 보조 인덱스만 생성, :20). 이 결함의 직접 원인
- `specs/services/notification-service/architecture.md` — Service Type / 테스트 요구
- `tasks/ready/TASK-BE-538-adr-002-d3-wording-adjudication.md` § Edge 3 — 이 티켓의 출처

## Related Contracts

- 없음 — 인덱스·저장 계층 변경이고 외부 API 형태는 불변. **단 AC-4 로 테넌트 격리 거동이 바뀌므로**(현재는 교차 테넌트 동일 `event_id` 도 충돌) 계약 문서에 중복 제거 범위가 명시돼 있다면 갱신 여부를 확인한다.

---

## Edge Cases

1. **🔴 수정 전 RED 를 못 보고 넘어가는 것** — AC-2. 테스트가 "EMAIL+PUSH 템플릿 둘 다 존재" 를 실제로 세팅하지 못하면 초록으로 지나가고 아무것도 증명하지 않는다. **가드는 물 기회가 있어야 가드다.**
2. **채널이 3개 이상으로 늘 때** — `NotificationChannel.values()` 는 열거 전체를 돈다. sender 가 없으면(`:74`) 건너뛰므로 지금은 2행이지만, SMS sender 가 추가되면 3행이 된다. 새 인덱스는 `channel` 을 포함하므로 자동으로 견딘다 — **이 성질을 테스트로 고정할 것.**
3. **롤백이 중복 제거를 무력화하는 자기강화 루프** — 재시도마다 `existsByEventId` 가 empty 인 이유가 롤백이라는 점. 인덱스를 고치면 사라지지만, **같은 구조(선체크가 같은 트랜잭션의 커밋에 의존)가 다른 컨슈머에도 있는지** 확인해 볼 것.
4. **`migration-h2` 병행 디렉터리** — `product-service` 에는 `migration-h2` 가 따로 있다. notification 에도 있는지 확인하고 있으면 함께 갱신하지 않으면 로컬/CI 스키마가 갈라진다.

---

## Failure Scenarios

- **F1 — 인덱스를 그냥 지우고 끝낸다.** 그러면 재전송 중복 제거의 DB 백스톱이 사라진다. `existsByEventId` 선체크는 경합에서 뚫린다(같은 트랜잭션 커밋 전이므로). **제약을 없애는 게 아니라 올바른 모양으로 바꾸는 것**이 목표다.
- **F2 — `channel` 만 넣고 `tenant_id` 를 빠뜨린다.** 그러면 선체크(테넌트 스코프)와 제약(전역)의 범위 불일치가 남아 교차 테넌트 동일 `event_id` 에서 여전히 터진다. `TASK-BE-540` 과 같은 결함을 남기는 것.
- **F3 — AC-0 (d) 를 건너뛴다.** 이미 발화 중인지 모른 채 "잠재적 결함" 으로 취급하면 우선순위를 잘못 매긴다. DLQ 에 쌓인 알림은 조용하다 — 아무도 신고하지 않는다.

---

## Test Requirements

- **IT (Testcontainers, CI Linux 가 권위)**: EMAIL+PUSH 템플릿 시드 → 이벤트 1건 소비 → `notifications` 2행 커밋 확인. 수정 전 RED 확인 필수(AC-2).
- **IT**: 동일 `event_id` 재전송 → 행 수 불변(AC-3).
- **IT**: 두 테넌트가 같은 `event_id` → 각 2행, 총 4행(AC-4).
- 로컬 Windows 는 Testcontainers FLAKY — **CI 가 권위.**

---

## Definition of Done

- [ ] AC-0 발화 조건 4항 재측정 (특히 (d) 이미 발화 여부)
- [ ] 수정 전 IT RED 확인 (AC-2)
- [ ] `V8__*` 마이그레이션으로 인덱스를 `(tenant_id, event_id, channel)` 로 재정의
- [ ] `migration-h2` 병행본 확인 및 갱신
- [ ] AC-1/3/4 IT GREEN (CI Linux)
- [ ] 계약 문서에 중복 제거 범위 서술이 있으면 갱신

---

## Notes

- **분량**: small~medium. 변경은 마이그레이션 1개 + 테스트지만, **발화 조건 재현이 실질**이다.
- **dependency**: `선행` = 없음(독립). `형제` = `TASK-BE-540`(선체크↔제약 범위 불일치의 다른 사례) · `TASK-BE-542`(DIVE→409 미배선).
- **출처**: `TASK-BE-538` Edge 3 — *"unique 제약이 있다"* 와 *"중복이 실제로 거부된다"* 는 다르다는 명제를 재려다, **제약이 코드의 쓰기 모양 자체와 어긋난** 제3의 경우를 찾았다.
- **이 task 가 방어하는 실패 모드**: **인덱스는 코드가 쓰는 모양에 대한 주장이다.** 그 주장이 틀리면 잘못된 데이터를 막는 게 아니라 **옳은 데이터를 막는다.** 그리고 그 실패는 500 이 아니라 DLQ 로 가서 조용하다. [[env_empty_detector_output_is_not_absence]] [[project_guard_reachability_not_just_bite]]
