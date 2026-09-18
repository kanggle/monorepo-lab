# Task ID

TASK-MONO-706

# Title

🔴 신선 볼륨의 데모에서 **`wms.outbound.shipping.confirmed.v1.DLT` 에 레코드 1건** — 출고 확정 이벤트 하나를 어떤 소비자가 처리하지 못했다

# Status

ready

# Owner

monorepo

# Task Tags

- wms
- kafka
- dlt
- demo-seed

---

# Goal

2026-09-17 UTC 둘째 데모 창(AMI `af0018aa6`, 인스턴스 교체 = **신선 볼륨**, 시드만 돈 상태)에서 `TASK-MONO-675` AC-1 을 재려고 인스턴스 안에서 읽기 전용 Kafka 조회를 했는데, 범위 밖에서 이것이 보였다:

```
wms.outbound.shipping.confirmed.v1.DLT:0:0
wms.outbound.shipping.confirmed.v1.DLT:1:1
wms.outbound.shipping.confirmed.v1.DLT:2:0
```

⇒ 부팅 후 약 27분(창 시작 16:34:55Z, 조회 ≈17:03Z) 안에 **출고 확정 이벤트 1건이 소비에 실패해 DLT 로 갔다.** 같은 조회에서 `wms-admin-service` 그룹의 `wms.outbound.shipping.confirmed.v1` 파티션 1 은 `CURRENT-OFFSET 1 · LAG 0` 이었다(admin 은 소비했다) ⇒ 실패한 쪽은 **다른 그룹**이다(후보: 같은 조회의 그룹 목록 `wms-outbound-service` · `wms-inbound-service` · `wms-notification-service` · `wms-inbound-scm-expected-v1` · `wms-inventory-service`, 그리고 다른 프로젝트의 소비자 — 🔴 추론으로 고르지 마라).

🔴 이 한 건이 `TASK-MONO-667` 이 2026-09-11 창에서 본 **출고 사가 `STUCK_RECOVERY_FAILED`** 와 같은 뿌리인지는 모른다 — 연결은 AC-0 이 관측으로 한다. 신선 볼륨에서 시드만으로 DLT 가 생긴다는 것은 «매 창마다 재현된다» 일 수 있고, 그러면 데모 화면(출고 · 재고)에도 흔적이 있을 수 있다.

---

# Scope

## 포함

- DLT 레코드의 **헤더**(원 토픽 · 예외 클래스 · 메시지 · 실패한 소비자 그룹)를 읽어 실패 지점을 지목.
- 그 이벤트가 시드의 어느 출고에서 왔는지, 같은 조건이 매 신선 부팅마다 재현되는지.
- 결함이면 고친다(계약/스펙 먼저) · 의도(예: 데모에 없는 하류 서비스)면 소유자에게 표시 방식을 묻는다.

## 제외

- master DLQ(`wms.master.*.v1.dlq`) 오프셋 — `TASK-MONO-675` AC-1 이 들고 있다.
- 출고 사가 전반의 재설계.

---

# Acceptance Criteria

- [ ] **AC-0 — 실패 지점을 관측으로 지목.** 창에서(읽기 전용) `kafka-console-consumer.sh --topic wms.outbound.shipping.confirmed.v1.DLT --from-beginning --property print.headers=true --max-messages 5` 로 헤더(`kafka_dlt-original-consumer-group` · `kafka_dlt-exception-fqcn` · `kafka_dlt-exception-message` 등)를 읽는다. 🔴 페이로드에 개인정보가 없는지 먼저 보고, 티켓에는 헤더와 키 목록만 적는다. 🔴 명령은 소유자가 실행한다(SSM 은 에이전트에게 막혀 있다). 창이 없으면 ⚪ + `TASK-MONO-672`.
- [ ] **AC-1 — 재현성.** 다음 신선 부팅에서도 같은 DLT 레코드가 생기는가(오프셋 다시 조회). 한 번뿐이면 경합, 매번이면 시드/코드.
- [ ] **AC-2 — 결함/의도 판정 → (결함이면) 고친다 + bite, (의도면) 소유자에게 묻는다.** 로컬 재현(IT)이 되면 그것으로 bite.
- [ ] **AC-3 — 창 판정.** 고친 AMI 의 신선 부팅에서 그 DLT 끝 오프셋이 전 파티션 0 이고, 출고 화면에 `STUCK_*` 가 없는지 본다. 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# Related Specs

- `projects/wms-platform/specs/contracts/events/` — outbound shipping confirmed 이벤트 계약(AC-0 에서 파일 특정)
- `tasks/in-progress/TASK-MONO-675-the-denormalised-codes-are-null-because-the-ref-tables-are-empty.md` § 창 실측(2026-09-17 둘째 창) — 발견 기록
- `tasks/in-progress/TASK-MONO-667-*` — 2026-09-11 출고 `STUCK_RECOVERY_FAILED` 관측

# Related Contracts

- wms outbound 이벤트 계약 · 소비자 쪽 재시도/DLT 정책. 🔴 바꾸면 계약 먼저.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 실패 소비자가 데모에 안 뜬 묶음의 서비스 | «의도» 후보 — 그래도 DLT 가 쌓이는 것을 받아들일지는 소유자 결정 |
| 레코드가 이미 보존기간(24h)으로 사라짐 | 다음 신선 부팅에서 재현(AC-1)으로 대신 |
| 여러 건으로 늘어남 | 부팅 후 경과 시간과 함께 기록 — 주기 작업이 원인일 수 있다 |

# Failure Scenarios

1. **admin 그룹의 LAG 0 을 보고 «문제 없음» 으로 닫는다** → DLT 는 다른 그룹의 실패다.
2. **667 의 `STUCK_RECOVERY_FAILED` 와 같은 뿌리라고 추론으로 묶는다** → AC-0 의 헤더가 판정이다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus 5** (이벤트 소비 실패 원인 지목 · 사가 연관 가능성)
