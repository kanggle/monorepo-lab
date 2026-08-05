# Task ID

TASK-MONO-511

# Title

IAM 계정 이벤트가 소비자 프로젝트에 **한 건도 도달하지 않는다** — 컨슈머 5개가 다른 Kafka 클러스터를 구독하고 있고, 그중 하나는 GDPR 익명화 의무다

# Status

ready

# Owner

platform

# Task Tags

- infra
- code
- test

---

# 배경 — `TASK-BE-575` 를 실측하다 나왔다

BE-575 는 "IAM 로그인 사용자에게 프로필이 없다" 였다. 원인을 찾다 보니 **user-service 의
`account.created` 컨슈머는 멀쩡했다.** 받는 것이 없었을 뿐이다.

## 실측 (2026-08-05, 데모 로컬 토폴로지)

브라우저로 실제 가입을 한 번 시켰다:

```
가입 전  iam-kafka       account.created  0:0  1:0  2:0
        ecommerce-kafka account.created  0:0
가입 후  iam-kafka       account.created  0:0  1:0  2:1   ← IAM 은 발행했다
        ecommerce-kafka account.created  0:0             ← 여기는 그대로다
        ecommerce user_profiles: 새 행 없음
```

`ecommerce-user-service` 의 `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` 는
`ecommerce_ecommerce-net` 의 `ecommerce-kafka` 로 해소된다. IAM 은 자기 프로젝트 compose 의
`iam-kafka` 로 발행한다. **두 클러스터 사이에 아무 다리가 없다.** ecommerce 쪽 토픽은
컨슈머가 붙으면서 auto-create 된 빈 토픽이라 존재 자체가 오해를 부른다 — 토픽이 있으니
배선이 된 것처럼 보인다.

## 죽은 컨슈머는 하나가 아니다 (전수)

| 서비스 | 토픽 | 하는 일 | 안 되면 |
|---|---|---|---|
| user-service | `account.created` | 프로필 온보딩 | BE-575 (지금은 pull-through 로 우회) |
| user-service | `account.deleted` | `withdrawProfile` / **`anonymizeProfile`** | **TASK-BE-258 GDPR 익명화 의무가 실행되지 않는다** |
| order-service | `account.deleted` | account-sync | 삭제된 계정의 주문 측 정리 누락 |
| product-service | `account.status.changed` | 판매자 상태 반영 | 정지된 판매자가 계속 노출 |
| notification-service | `account.created` | 온보딩 알림 | 가입 알림 없음 |

`account.deleted` 는 계약 문서(`account-lifecycle-subscriptions.md`)가 "live" 라고 적고 있고,
`user-api.md` 도 "cross-project deletion wiring is live" 라고 적어 왔다. **문서가 틀린 것이
아니라, 문서가 말하는 토폴로지가 이 compose 에 없다.**

## 의도된 토폴로지는 무엇인가

IAM 의 `specs/features/consumer-integration-guide.md` 는 소비자가 **IAM 과 같은 클러스터**에
있다고 전제한다 — "단일 Kafka 클러스터를 다수 테넌트가 공유하므로 자기 테넌트 외 이벤트는
skip"(§ 590/607). 즉 컨슈머 코드는 그 전제 위에서 옳게 쓰였고, 갈라진 것은 **배포 토폴로지**다.
프로젝트별 격리 compose 는 MONO-507 이 확인한 대로 의도된 격리이므로, 이 티켓은
"격리를 없애자" 가 아니라 **격리를 유지한 채 계정 이벤트만 건너오게 하는 방법을 정하는 것**이다.

---

# Goal

IAM 이 발행한 계정 생명주기 이벤트가 소비자 프로젝트의 컨슈머에 도달한다. 그리고 도달하지
않으면 **누군가 알아차린다** — 지금은 조용하다.

---

# Scope

## In Scope

- 토폴로지 결정 + **ADR** (아래 선택지)
- 결정된 방식의 배선 (compose / 설정 / 필요 시 릴레이)
- **도달을 검증하는 테스트** — 지금 전 계층이 초록인데 이벤트는 한 건도 안 왔다
- `account-lifecycle-subscriptions.md` 등 "live" 라고 적은 문서를 실제와 맞춘다

## Out of Scope

- BE-575 의 pull-through 프로비저닝 — 이벤트가 복구돼도 그대로 둔다(멱등하게 공존하며,
  이벤트가 늦거나 유실돼도 화면이 열려야 한다)

---

# 선택지 (착수 시 ADR 필요)

| 안 | 방식 | 유의점 |
|---|---|---|
| A | IAM kafka 를 공유 네트워크에 노출하고 소비자가 **두 번째 컨슈머 팩토리**로 붙는다 | 가이드의 전제에 가장 가깝다. 소비자마다 브로커 주소가 늘고, 프로젝트 격리가 Kafka 한 축에서 뚫린다 |
| B | **릴레이**(MirrorMaker 또는 소형 브리지)로 `account.*` 만 각 프로젝트 클러스터에 복제 | 격리 유지. 운영 요소가 하나 는다. 토픽 화이트리스트 관리 필요 |
| C | 계정 이벤트를 이벤트가 아니라 **pull-through 계약**으로 정식화 | BE-575 가 이미 그렇게 하고 있다. 그러나 `account.deleted`(GDPR)는 pull 로 성립하지 않는다 — 소비자가 "언제" 물어봐야 하는지 모른다 |

> C 를 고르더라도 **`account.deleted` 만은 push 가 필요하다.** 익명화는 사용자가 다시 오지
> 않아도 일어나야 하는 일이다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 착수 시 위 오프셋 실측을 **다시 한다.** 그리고 죽은 컨슈머 표를
      전수로 다시 센다(이 티켓은 `account.*` 만 셌다 — 다른 프로젝트가 IAM 토픽을 구독하는지,
      역방향(ecommerce→IAM)도 같은 상태인지 확인할 것)
- [ ] **AC-1** — 가입 한 번에 소비자 클러스터의 `account.created` 오프셋이 증가하고,
      user-service 로그에 온보딩이 남는다
- [ ] **AC-2** — IAM 에서 계정을 삭제하면 ecommerce 프로필이 `WITHDRAWN` 이 되고,
      post-grace 이벤트로 PII 가 실제로 지워진다 (TASK-BE-258 의무의 **실행** 확인)
- [ ] **AC-3** — 도달이 끊기면 실패하는 검사가 있다. 컨슈머 lag 알람이든 e2e 든,
      **"토픽이 존재한다" 를 술어로 쓰지 않는다** — 빈 auto-created 토픽이 바로 그 함정이었다
- [ ] **AC-4** — 문서와 실제가 일치한다

---

# Related Specs

- `projects/iam-platform/specs/features/consumer-integration-guide.md`
- `projects/iam-platform/specs/contracts/events/account-events.md`
- `projects/ecommerce-microservices-platform/specs/contracts/events/account-lifecycle-subscriptions.md`
- `docs/adr/ADR-MONO-037-*` (계정 생명주기 반응), `ADR-MONO-040-*` (신원 이관)

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md`

---

# Edge Cases

- 소비자가 **다른 테넌트**의 이벤트를 받는다 — 가이드대로 `tenant_id` 필터가 필요하다
- 릴레이(B) 재시작 시 중복 전달 — 컨슈머는 이미 멱등이지만 재확인
- 소비자 프로젝트가 내려가 있는 동안 쌓인 이벤트 — retention 안에 소비되는가

# Failure Scenarios

- **A 를 골라 브로커만 열고 끝낸다** — 토픽은 보이는데 컨슈머 그룹이 다른 클러스터의
  오프셋을 들고 있어 조용히 안 읽는다. AC-1 이 그것을 잡아야 한다
- **"토픽이 있다" 를 도달 판정으로 쓴다** — 이 결함이 그동안 안 보인 이유가 정확히 그것이다

# Test Requirements

- 도달 e2e (가입 → 소비자 프로젝트에서 반응 확인)
- `account.deleted` 익명화 실행 확인

# Definition of Done

- [ ] ADR + 배선
- [ ] 도달 테스트
- [ ] 문서 정합
- [ ] Ready for review
