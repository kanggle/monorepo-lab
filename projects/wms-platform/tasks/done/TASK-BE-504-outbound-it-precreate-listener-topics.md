# Task ID

TASK-BE-504

# Title

outbound-service IT: 리스너 토픽 12개를 컨텍스트 기동 **전에** 일괄 생성해 기동 리밸런스 연쇄를 접는다 — **CI 실패를 고친다는 증명은 없다**(재현 불가). watch 신호 유지.

# Status

done

# Owner

wms-platform

# Task Tags

- test
- ci

---

# Goal

`FulfillmentRequestedConsumerIT` · `InventoryReserveFailedConsumerIT` 가 **wms 코드를 0줄 건드리지 않는 PR** 에서 두 번(2026-07-11 `TASK-MONO-354` PR, 2026-07-12 `TASK-MONO-362` PR #2441) RED 였다. MONO-354 close 노트가 *"같은 두 IT 가 무관한 diff 에서 재차 RED 시 착수"* 를 **미리 못박아 뒀고**, 그 신호가 도착했다.

## 진짜 메시지 — 콘솔이 아니라 test-report XML 에 있었다

```
java.lang.IllegalStateException: Expected 1 but got 0 partitions
```

`ContainerTestUtils.waitForAssignment(container, 1)` 의 **타임아웃**이다. Gradle 콘솔은 `IllegalStateException at ...java:221` 까지만 찍고 메시지를 생략한다.

## 조사 — 가설 2개를 세웠고 **둘 다 틀렸다**

| 가설 | 왜 틀렸나 |
|---|---|
| ① 두 Spring 컨텍스트가 같은 consumer group 에 조인 (`notification-service` 는 group-id 를 랜덤화하는데 outbound 는 안 한다) | **두 IT 는 `@MockitoBean`·`@TestPropertySource` 가 없어 컨텍스트 키가 동일** → Spring 이 **캐시된 컨텍스트 하나를 재사용**한다. 컨텍스트가 둘일 수 없다. group-id 랜덤화는 이 결함을 고치지 못한다. |
| ② 리스너 12개 중 10개가 **존재하지 않는 토픽**을 구독한다 | 로컬 로그에서 `wms.master.zone.v1-0` 등이 **실제로 배정**된다. Kafka 가 auto-create 하므로 토픽은 존재한다. |

**확인된 사실만 남기면**:

1. 실패는 `waitForAssignment` 타임아웃(`0 partitions`)이다.
2. CI 로그에 리밸런스 폭풍이 있다(`Request joining group due to: group is already rebalancing`).
3. **조용한 호스트에서는 재현되지 않는다** — outbound `integrationTest` 전체가 **26/26 GREEN**(로컬, 2026-07-12).
4. 이 CI 레인(master + notification + outbound, 4모듈 동시)에는 **이미 문서화된 경합 flake 클래스**가 있다(`TASK-MONO-331` 이 WMS caller 를 `--no-parallel` 로 고쳤다).

→ **증거는 "테스트 로직 결함" 을 지지하지 않는다.** 억지로 결함이라 부르는 것은 BE-503 의 교훈을 **반대 방향으로** 어기는 것이다.

## 그래도 제거할 수 있는 churn 원인 하나 (= 이 task 의 In Scope)

리스너 12개는 **모두 한 그룹**(`outbound-service`)이고, 각 토픽은 컨텍스트 기동 시 **lazy auto-create** 된다. 토픽이 하나씩 나타날 때마다 그룹의 구독 메타데이터가 바뀌어 **리밸런스가 연쇄**한다(`metadata.max.age.ms=2000` 이 이를 가속한다 — 이 값은 `Initializer` 가 discovery 를 위해 의도적으로 넣은 것이라 되돌리지 않는다).

조용한 호스트에선 이 연쇄가 순식간에 수렴해 아무도 못 느낀다. **경합 러너에선 `waitForAssignment` 가 포기하기 전에 수렴하지 못할 수 있다.**

→ **토픽 12개를 컨텍스트 기동 전에 일괄 생성**하면, **첫 컨슈머가 조인하는 시점에 구독 메타데이터가 이미 최종**이라 그 연쇄가 **리밸런스 1회로 접힌다.**

**이것은 타임아웃을 늘리는 것이 아니다** — 증상을 기다리는 대신 churn 의 원인을 없앤다.

---

# ⚠️ 이 task 가 **증명하지 못하는 것** (읽지 않고 닫지 말 것)

**CI 실패를 고친다는 증명이 없다.** 재현하지 못했기 때문이다(§ Goal 3).

→ **이 변경 후 CI 가 초록이어도 그것은 증거가 아니다.** 원래도 대부분의 런에서 초록이었다. 확률적 실패에 대해 "1회 GREEN" 은 아무것도 말해주지 않는다(`env_ci_flake_is_a_hypothesis_not_a_verdict` 규율 4).

→ **watch 신호를 유지한다**: 같은 두 IT 가 **또** 무관한 diff 에서 RED 이면, 이 변경은 원인이 아니었다는 뜻이고 **경합 축**(레인 분할 / `--no-parallel` 확대, MONO-331 선례)으로 넘어가야 한다. 그때는 이 task 를 근거로 "이미 고쳤다" 고 결론짓지 말 것.

---

# Scope

## In Scope

- `OutboundServiceIntegrationBase` — `LISTENER_TOPICS`(12개) 상수 + `KAFKA.start()` 직후 **static 블록에서 일괄 생성**(Spring 컨텍스트 기동 전).
- **`ListenerTopicsPrecreatedIT` 신설** — 그 목록이 **실제 리스너 구독 집합과 정확히 일치**함을 단언(양방향). 손으로 유지하는 목록에 아무 검사도 없으면 **그게 다음 드리프트**다.

## Out of Scope

- **`metadata.max.age.ms=2000` 되돌리기** — `Initializer` 가 per-test 토픽 생성의 discovery 를 위해 **의도적으로** 넣었다(주석에 근거 있음). 건드리면 다른 IT 가 깨진다.
- **consumer group-id 랜덤화** — 가설 ①이 틀렸으므로 **효과가 없다**. 하지 말 것(하면 "고쳤다" 는 착시만 남는다).
- **`waitForAssignment` 타임아웃 증가** — 증상 마스킹. 인프라 경합이 진짜 원인이라면 그건 **레인 축**에서 다뤄야 한다(§ 위 watch 신호).
- 두 IT 의 per-test `createTopic()` — 이제 사실상 no-op(멱등, `TopicExistsException` 흡수)이지만 **제거하지 않는다**. 그 자체로 해가 없고, 지우면 이 task 의 변경과 그 IT 들의 독립성이 얽힌다.
- 프로덕션 설정(`application.yml` 의 `group-id`) — 테스트 문제지 프로덕션 문제가 아니다.

---

# Acceptance Criteria

- [ ] **AC-1** `OutboundServiceIntegrationBase` 가 Spring 컨텍스트 기동 **전에** 리스너 토픽 전량을 생성한다.
- [ ] **AC-2 (목록이 드리프트하지 못한다)** `ListenerTopicsPrecreatedIT` 가 `LISTENER_TOPICS` ↔ 실제 `@KafkaListener` 구독 집합을 **양방향** 대조한다. **mutation 필수**: 목록에서 토픽 하나를 빼면 그 토픽을 지목하며 FAIL.
- [ ] **AC-3 (회귀 0)** outbound `integrationTest` 전체 GREEN — **XML 리포트로 실행 건수 확인**(`BUILD SUCCESSFUL` 은 전건 SKIPPED 를 못 거른다).
- [ ] **AC-4 (정직성)** 이 task 와 코드 주석이 **"CI 실패를 고친다는 증명은 없다"** 를 명시한다. 다음 사람이 초록 CI 를 보고 "해결됨" 이라 결론짓지 않도록.

---

# Related Specs

- `tasks/done/TASK-MONO-354-*.md` (watch 신호를 남긴 close 노트)
- `tasks/done/TASK-MONO-331-*.md` / memory `env_wms_notification_seed_cluster_ci_flake` (같은 레인의 경합 flake 선례 — `--no-parallel`)
- `tasks/done/TASK-BE-503-*.md` / memory `env_ci_flake_is_a_hypothesis_not_a_verdict` (**"flake=인프라" 는 가설이지 결론이 아니다** — 그리고 그 역도 참이다: **"flake=결함" 도 가설이다**)

# Related Contracts

None.

---

# Target Service

`wms-platform` / `outbound-service` (테스트 하네스만)

---

# Edge Cases

- **`LISTENER_TOPICS` 를 손으로 유지하면 새 `@KafkaListener` 추가 시 조용히 스테일**해지고, 그 토픽만 다시 lazy auto-create 되어 **churn 이 그룹 전체로 복귀**한다 → AC-2 가드가 이걸 막는다(mutation 확인 필수).
- **양방향 대조여야 한다** — 누락은 churn 을 되돌리고, 잔존(리스너가 사라졌는데 목록에 남음)은 **아무도 소비하지 않는 토픽을 만드는 죽은 설정**이 된다.

---

# Failure Scenarios

- **F1 — 이 변경이 CI 실패의 원인이 아니었다.** 가장 가능성 높은 시나리오이며 **이미 예상하고 있다**(재현 못 했으므로). 완화: watch 신호 유지 + § "증명하지 못하는 것" 을 task 와 코드에 못박음. **초록 CI 를 증거로 삼지 말 것.**
- **F2 — 토픽 사전생성이 다른 IT 를 깬다**(예: 특정 IT 가 "토픽이 없는 상태" 를 전제). 완화: AC-3 전체 스위트 실행.

---

# Test Requirements

- `ListenerTopicsPrecreatedIT` PASS + **mutation FAIL**(목록에서 토픽 제거).
- outbound `integrationTest` 전체 GREEN, **XML 집계로 실행 건수 확인**.

---

# Definition of Done

- [ ] AC-1 ~ AC-4.
- [ ] `projects/wms-platform/tasks/INDEX.md` done entry — **watch 신호가 살아 있음을 기록**.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-362` PR #2441 의 CI 에서 두 IT 가 **또** RED(무관 diff). MONO-354 가 남긴 착수 신호가 도착. XML 을 열어 진짜 메시지를 확보하고 두 가설을 세웠으나 **둘 다 반증**했고, **재현에도 실패**했다. 남은 것은 *제거할 수 있는 churn 원인 하나*뿐이며, **그것이 원인이라는 증명은 없다** — 그 사실을 숨기지 않는 것이 이 task 의 절반이다.

분석·구현=Opus 4.8.
