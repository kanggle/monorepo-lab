# Task ID

TASK-MONO-376

# Title

**watch 신호가 발화했다 — `TASK-BE-504` 는 원인이 아니었다.** outbound-service 두 IT 가 무관한 diff 에서 **세 번째** RED. 이제 경합 축(레인 분할 / `--no-parallel`)으로 넘어간다

# Status

done

# Owner

monorepo

# Task Tags

- ci
- test

---

# Dependency Markers

- **선행 (그리고 이 task 를 미리 예고한 것)**: `projects/wms-platform/tasks/done/TASK-BE-504-outbound-it-precreate-listener-topics.md` — close 노트가 **문자 그대로** 이렇게 적어뒀다:

  > *"**watch 신호를 유지한다**: 같은 두 IT 가 **또** 무관한 diff 에서 RED 이면, 이 변경은 원인이 아니었다는 뜻이고 **경합 축**(레인 분할 / `--no-parallel` 확대, MONO-331 선례)으로 넘어가야 한다. 그때는 이 task 를 근거로 '이미 고쳤다' 고 결론짓지 말 것."*

  **그 신호가 도착했다. 이 task 가 그 후속이다.**
- **경합 축 선례**: `TASK-MONO-331` — 같은 레인의 DB-커넥션 절단 flake 를 **WMS caller `--no-parallel`** 로 고쳤다. memory `env_wms_notification_seed_cluster_ci_flake`.
- **판정 규율**: `TASK-BE-503` / memory `env_ci_flake_is_a_hypothesis_not_a_verdict` — *"flake=인프라" 는 가설이지 결론이 아니다.* **그 역도 참이다.** 그리고 **확률적 결함에 CI 1회 GREEN 은 증거가 아니다.**

---

# Goal

## 관측 이력 — 같은 서명, 세 번

| # | 언제 | 어느 PR | 그 PR 의 diff 가 wms 를 건드렸나 |
|---|---|---|---|
| 1 | 2026-07-11 | `TASK-MONO-354` | ❌ 아니오 |
| 2 | 2026-07-12 | `TASK-MONO-362` (#2441) | ❌ 아니오 |
| 3 | **2026-07-12** | **`TASK-MONO-371` (#2464)** — **docs + ci.yml + bash 스크립트뿐, 자바 0줄** | ❌ **아니오** |

**실패 서명은 세 번 다 동일하다** (콘솔이 아니라 test-report XML 에서만 읽힌다):

```
FulfillmentRequestedConsumerIT   > java.lang.IllegalStateException at FulfillmentRequestedConsumerIT.java:221
InventoryReserveFailedConsumerIT > java.lang.IllegalStateException at InventoryReserveFailedConsumerIT.java:177
```

= `ContainerTestUtils.waitForAssignment(container, 1)` 의 **타임아웃**(`Expected 1 but got 0 partitions`).

## BE-504 가 무엇을 했고, 무엇을 못 했나

BE-504 는 **제거 가능한 churn 원인 하나**를 없앴다 — 리스너 12개가 한 그룹인데 토픽이 lazy auto-create 되며 리밸런스가 연쇄하던 것을, 컨텍스트 기동 **전** 일괄 생성으로 접었다. **그 변경 자체는 옳고 되돌릴 이유가 없다.**

그러나 BE-504 는 **CI 실패를 재현하지 못했고**(조용한 호스트에서 outbound 전체 26/26 GREEN), 그 사실을 task · PR · 코드 주석 **세 곳에 명시**했다. **그리고 세 번째 RED 가 그 정직함을 정당화했다 — 토픽 사전생성은 원인이 아니었다.**

**⇒ 남은 축은 하나다: 경합.**

## 왜 경합인가 (아직 반증되지 않은 유일한 가설)

- 이 레인은 **4개 모듈을 동시에** 돌린다(master + notification + outbound, Testcontainers).
- **같은 레인에 이미 문서화된 경합 flake 전과가 있다** — `MONO-331` 이 DB 커넥션 절단(`SQLSTATE 08006`)을 **WMS caller `--no-parallel`** 로 고쳤다.
- `MONO-358` 의 CI 에서도 이 레인이 **26분 무출력 → 30분 타임아웃**을 냈고, 직전 notification-service 에 `HikariPool … 08006` 이 찍혀 있었다.
- **조용한 호스트에서는 재현되지 않는다.** 이건 부하-의존 결함의 지문이다.

**⇒ 가설: `waitForAssignment` 가 포기하기 전에 컨슈머 그룹이 파티션을 배정받지 못한다 — 러너가 포화라서.** 이것도 **가설이다.** 아래 규율을 읽을 것.

---

# ⚠️ 착수 규율 (읽지 않고 시작하지 말 것)

**"재실행하면 초록이니 인프라겠지" 로 닫지 말 것.** `env_ci_flake_is_a_hypothesis_not_a_verdict` 가 금지하는 바로 그 추론이다.

**그 역도 금지된다** — BE-504 가 "테스트 로직 결함" 가설로 갔다가 **틀렸다.** 두 방향 모두 가설이다.

**증거는 다음 중 하나여야 한다**:

1. **재현** — 러너를 포화시킨 상태(동시 4모듈 + CPU/메모리 압박)에서 실패를 **의도적으로 재현**한다. 재현하면 인과가 확정된다.
2. **또는 반증 가능한 개입** — 경합을 제거하는 변경을 넣고, **그 변경을 되돌리면 실패가 돌아온다**는 것을 보인다.

세 번 RED 가 났다는 것은 **재현 확률이 낮지 않다**는 뜻이므로, **같은 커밋에서 레인만 N회 반복 실행**(≥10)이 실용적인 측정 도구다.

---

# Scope

## In Scope

- `.github/workflows/ci.yml` 의 WMS integration 레인 — **분할 또는 직렬화**.
  - **옵션 A: 레인 분할** — `outbound-service` 를 자기 잡으로 뺀다. 러너당 부하가 줄고, 실패해도 어느 모듈인지 즉시 보인다. 비용 = 러너 하나 추가.
  - **옵션 B: `--no-parallel` 확대** — `MONO-331` 이 WMS caller 에 적용한 것을 이 잡의 Gradle 호출에도 확대. 비용 = 레인이 느려진다.
  - **어느 쪽이든 § 착수 규율의 증거 요건을 만족해야 한다.**
- 실패 시 **test-report XML 아티팩트**가 올라오는지 확인 — 이 결함의 진짜 메시지는 **세 번 다 콘솔이 아니라 XML** 에 있었다.

## Out of Scope

- **`BE-504` 되돌리기** — 토픽 사전생성은 **churn 을 실제로 줄인다.** 원인이 아니었을 뿐 해롭지 않다. 되돌리면 리밸런스 연쇄가 돌아온다.
- **`waitForAssignment` 타임아웃 증가** — **증상 마스킹.** 러너가 더 느려지면 다시 터진다. BE-504 도 명시적으로 배제했다.
- **consumer group-id 랜덤화** — **BE-504 가 반증했다**(두 IT 는 컨텍스트 캐시 키가 같아 **하나의 캐시된 컨텍스트를 재사용**한다 — 컨텍스트가 둘일 수 없다). **다시 제안하지 말 것.**
- **두 IT 를 `@Disabled`** — 커버리지를 잃고, **꺼진 테스트는 skip 으로 초록 보고된다**(이 저장소가 반복해서 대가를 치른 것).

---

# Acceptance Criteria

- [ ] **AC-1 (인과 확정)** — § 착수 규율의 증거 요건 중 **하나를 만족**한다. **"재실행하니 초록" 은 AC 가 아니다.**
- [ ] **AC-2 (개입)** — 레인 분할 또는 `--no-parallel` 확대.
- [ ] **AC-3 (측정)** — 개입 후 같은 커밋에서 레인을 **≥10회 반복 실행**하여 실패율을 측정. **1회 GREEN 은 증거가 아니다.**
- [ ] **AC-4 (커버리지 무손실)** — 3개 모듈의 IT 전량이 계속 실행된다. **XML 리포트로 실행 건수 확인**(`BUILD SUCCESSFUL` 은 전건 SKIPPED 를 못 거른다).
- [ ] **AC-5 (정직성)** — 인과를 확정하지 못했다면 **그렇게 적는다.** BE-504 가 그렇게 했고, **그 정직함 덕에 이 task 가 존재한다.** 초록 CI 를 증거로 삼지 말 것.

---

# Related Specs

- `projects/wms-platform/tasks/done/TASK-BE-504-outbound-it-precreate-listener-topics.md` — **watch 신호를 남긴 close 노트**(이 task 의 존재 이유)
- `tasks/done/TASK-MONO-331-*.md` — 같은 레인의 경합 flake 선례(`--no-parallel`)
- `projects/wms-platform/tasks/done/TASK-BE-503-*.md` — *"flake=인프라" 는 가설이다* 규율
- `.github/workflows/ci.yml` — WMS integration 레인

# Related Contracts

없음 — CI 배선 + 테스트 하네스.

---

# Edge Cases

- **레인 분할 = 러너 하나 추가** — `MONO-359`/`330` 이 정리한 composite 를 재사용해 중복을 만들지 말 것.
- **`--no-parallel` = 레인이 느려진다** — 이미 4모듈이다. 타임아웃 예산 확인.
- **이 레인은 `MONO-331` 이 이미 한 번 손댔다** — **그때의 개입이 무엇이었고 왜 충분하지 않았는지 먼저 읽을 것.** 같은 처방을 두 번 쓰는 것일 수도, 범위가 달랐던 것일 수도 있다.

# Failure Scenarios

- **F1 — 재현하지 못한다.** 가능성 있다(BE-504 도 못 했다). 그때는 **AC-5 를 이행하라**: 개입은 넣되 *"인과를 확정하지 못했다"* 를 명시하고 **watch 신호를 다시 남겨라.** 네 번째가 될 수도 있다.
- **F2 — 개입 후 1회 GREEN 을 보고 닫는다** → **이 결함의 역사가 그대로 반복된다.** BE-504 도 머지 후 CI 가 초록이었다. Guard: AC-3.

---

# Test Requirements

- 개입 후 레인 **≥10회 반복 실행**, 실패율 기록.
- 3개 모듈 IT 전량 실행 확인(XML).

---

# Definition of Done

- [ ] AC-1 ~ AC-5.
- [ ] `tasks/INDEX.md` done entry — **인과를 확정했는지 여부를 명시**.

---

# Provenance

발굴 2026-07-12 — **`TASK-BE-504` 가 남긴 watch 신호의 발화.** `TASK-MONO-371`(#2464) 의 CI 에서 같은 두 IT 가 **세 번째로** RED. 그 PR 의 diff 는 **docs + ci.yml + bash 스크립트**뿐이고 **자바 0줄** — 인과가 원리적으로 불가능하다.

**이 task 가 존재한다는 사실 자체가 BE-504 의 정직함이 값을 했다는 증거다.** 만약 BE-504 가 머지 후의 초록 CI 를 보고 *"고쳤다"* 고 닫았다면, 지금 이 실패는 **원인 불명의 새 flake** 로 보였을 것이고, 이미 배제된 가설(group-id 랜덤화)을 누군가 다시 제안했을 것이다.

분석=Opus 4.8 / 구현 권장=**Opus**(인과 확정이 이 task 의 전부다. 개입 자체는 한 줄이지만 **증거 없이 넣으면 네 번째 RED 를 기다리게 된다**).
