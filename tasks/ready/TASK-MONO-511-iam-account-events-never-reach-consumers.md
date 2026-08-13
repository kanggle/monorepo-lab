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

# 🟢 착수 (2026-08-13 UTC) — AC-0 정적 절반 완료. **이 티켓은 자기 범위를 3배 작게 잡고 있었다**

## 🔴🔴 전수는 5가 아니라 **16 리스너 / 3계열**이다

AC-0 이 요구한 *"다른 프로젝트가 IAM 토픽을 구독하는지, 역방향도 같은 상태인지"* 를 재니
**같은 결함이 세 갈래로 있었다.** 티켓이 센 것은 그중 하나다:

| 계열 | 소비 서비스 / 리스너 | 토픽 | 다리 |
|---|---|---|---|
| **iam → ecommerce** (`account.*`) | user(2) · order(1) · product(1) · notification(1) = **5** | 3 | 없음 |
| **wms → ecommerce** (`wms.*`) | order(1) · product(3) · shipping(2) = **6** | 4 | 없음 |
| **wms → scm** (`wms.*`) | demand-planning(1) · inventory-visibility(3) · logistics(1) = **5** | 5 | 🔴 **반쯤 지어져 있다**(아래) |

🔵 **역방향(ecommerce → iam)은 0건이다.** ecommerce 의 `user.user.withdrawn` 컨슈머 2개는
ecommerce 자기 토픽을 읽는다. AC-0 의 그 질문에는 *"없다"* 가 답이고, 그것도 산출물이다.

## 🔴🔴 wms→scm 에는 **이 문제를 위한 파라미터가 이미 있고, 주석의 전제가 틀렸다**

`projects/scm-platform/docker-compose.yml:374`:

```yaml
# WMS_KAFKA_BOOTSTRAP: cross-project event source. In dev, same cluster.
KAFKA_BOOTSTRAP: ${WMS_KAFKA_BOOTSTRAP:-kafka:9092}
```

`TASK-SCM-BE-003` 이 *"cross-project source — 동일 클러스터일 수도 별도일 수도"* 라고 적으며
심어 둔 이음매다. 그런데:

- **"In dev, same cluster" 가 거짓이다** — scm 은 `scm-platform-kafka`(on `scm-platform-net`),
  wms 는 `wms-kafka`(on `wms-net`). 같은 클러스터인 적이 없다.
- `WMS_KAFKA_BOOTSTRAP` 은 **저장소 어디에서도 값이 할당되지 않는다.** 유일한 등장이 위 주석이고,
  scm 의 `.env.example` 에도 **없다**(대조군: 같은 탐지식이 그 파일의 `KAFKA_BOOTSTRAP=kafka:9092`
  는 찾는다). ⇒ 폴백이 항상 이겨서 **scm 이 자기 클러스터를 읽는다.**

⇒ [[project_externalised_seam_with_no_counterpart]] 의 그 모양이다 — **외부화된 이음매에 짝이 0건**.
그리고 이 이음매의 존재가 문제를 **더 안 보이게** 만들었다: 변수가 있으니 배선된 것처럼 읽힌다.

## 구조 판정 — 오프셋보다 강한 술어

AC-3 이 *"토픽이 존재한다를 술어로 쓰지 말라"* 고 요구한다. 오프셋보다 더 강한 술어가 있어서
그것부터 썼다 — **두 클러스터 사이에 네트워크 경로가 있는가**:

- 4개 프로젝트 compose 전부 자기 `name:` · 자기 `*-net` · 자기 `kafka` 컨테이너를 갖는다.
- **모든 브로커가 자기 프로젝트 net 에만 붙어 있다**(iam-net / ecommerce-net / wms-net /
  scm-platform-net). 공유되는 유일한 네트워크는 `traefik-net`(`external: true`)이고
  **어느 브로커도 거기 붙어 있지 않다.**
- 저장소 전체의 bootstrap 값은 전부 `kafka:9092` — 즉 **자기 net 안**으로만 해소된다.

🔴 이 술어는 auto-create 된 빈 토픽에 속지 않는다. 오프셋은 "이번엔 안 왔다" 를 보이지만,
경로 부재는 **올 수 없음**을 보인다.

🔵 **결정에 미치는 영향**: `traefik-net` 이라는 **이미 존재하는 공유 외부 네트워크**가 있으므로
선택지 A 의 비용이 티켓이 가정한 것보다 낮다. 다만 그것은 HTTP 라우팅용 네트워크이고
브로커를 거기 올리는 것은 격리 축을 바꾸는 일이라 **그 자체가 ADR 사안**이다.

## 라이브 재측정 (2026-08-13 UTC) — 결함은 그대로 있다

기동: iam 전체(`demo-up.sh iam`, base+e2e+traefik override) + ecommerce/wms **브로커만**
(앱 이미지는 굳이 빌드하지 않았다 — 판정에 필요한 것은 브로커다).

**① 가입 한 번 (`POST /api/accounts/signup`, `X-Tenant-Id: ecommerce`, HTTP **201**,
`accountId=3d51b618-0614-441d-b4e6-338a8e4529fe`)**

```
                    가입 전                가입 후
iam-kafka           account.created 0:0    account.created 0:1   ← 발행됐다
                                    1:0                    1:0
                                    2:0                    2:0
ecommerce-kafka     토픽 0개               토픽 0개               ← 그대로
wms-kafka           토픽 0개               토픽 0개
```

🔵 **이번 회차는 원 티켓보다 깨끗하다.** 원 티켓은 `ecommerce-kafka account.created 0:0` 을
봤는데, 그 토픽은 **컨슈머가 붙으면서 auto-create 된 것**이다. 이번엔 컨슈머를 안 띄웠으므로
**토픽 자체가 없다.** ⇒ *"토픽의 존재는 컨슈머가 만든 것이지 프로듀서와 무관하다"* 가
같은 실험 안에서 보인다. AC-3 이 그 술어를 금지한 이유가 이것이다.

**② 경로 부재 매트릭스 (오프셋보다 강한 술어)**

```
  iam-kafka        -> ecommerce-kafka  DNS-FAIL       ecommerce-kafka -> wms-kafka  DNS-FAIL
  iam-kafka        -> wms-kafka        DNS-FAIL       wms-kafka -> iam-kafka        DNS-FAIL
  ecommerce-kafka  -> iam-kafka        DNS-FAIL       wms-kafka -> ecommerce-kafka  DNS-FAIL
  대조군: 각 브로커 -> 자기 프로젝트 `kafka`          RESOLVES (3/3)
```

🔵 **대조군이 계측기를 검증한다** — 6/6 DNS-FAIL 이 탐지 실패가 아니라 실제 부재임을 3/3
RESOLVES 가 보증한다. 네트워크 소속도 교집합이 없다: `iam_iam-net`+`iam_iam-e2e` /
`ecommerce_ecommerce-net` / `wms_wms-net`. `kafka` 는 프로젝트마다 **다른 IP**로 해소된다
(172.23.0.2 / 172.24.0.2 / 172.27.0.2).

🔴 **wms→ecommerce · wms→scm 두 계열은 오프셋으로 재지 않았다** — 그쪽 앱을 띄우지 않았기
때문이다. 그 두 계열의 판정 근거는 **구조(경로 부재)뿐**이고, 그렇게 적는다. 다만 경로가
없으면 오프셋을 재 봐야 결과는 정해져 있다.

## ⏸ 남은 것

AC-1~AC-4 는 **AC-1(토폴로지 ADR) 결정 이후**다. 지금 상태에서 확정된 것은
*"세 계열 전부, 어떤 이벤트도 건너갈 수 없다"* 이고, 그것이 결정의 입력이다.

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

# 선택지 → **[`ADR-MONO-062`](../../docs/adr/ADR-MONO-062-cross-project-event-delivery.md) 로 승격됨 (PROPOSED, 2026-08-13)**

> 아래 원문은 보존한다. ADR 이 이 표를 **3계열 16 리스너** 위에서 다시 쓰고, 티켓이 별개 안으로
> 세던 *"컨슈머별 bootstrap 파라미터화"* 가 **A 의 대안이 아니라 A 의 설정 계층**임을 실측으로
> 지운다 — 주소를 정확히 적어도 경로가 없으면 도달 불가다(브로커 간 6/6 DNS-FAIL).
>
> 🟢 **`ADR-MONO-062 ACCEPTED — B` (2026-08-13, 소유자 정확형).** AC-1 은 이것으로 답해졌다:
> **릴레이로 필요한 토픽만 복제, 프로젝트 격리 유지.** A·C 는 배제 — 브로커 간 네트워크 경로를
> 뚫지 않고(`traefik-net` 에도 전용망에도 브로커를 올리지 않는다), 소비자 코드·설정은 그대로다.
> 🔴 **소비자에 두 번째 브로커 주소가 들어가면 그건 B 가 아니라 A 다.**
> 🔵 rider 대조 결과 **없음** — 구현체(MM2 vs 브리지)·토픽 목록·도달 검사 술어는 전부 구현 AC 다.

# 선택지 원문 (착수 시 ADR 필요)

| 안 | 방식 | 유의점 |
|---|---|---|
| A | IAM kafka 를 공유 네트워크에 노출하고 소비자가 **두 번째 컨슈머 팩토리**로 붙는다 | 가이드의 전제에 가장 가깝다. 소비자마다 브로커 주소가 늘고, 프로젝트 격리가 Kafka 한 축에서 뚫린다 |
| B | **릴레이**(MirrorMaker 또는 소형 브리지)로 `account.*` 만 각 프로젝트 클러스터에 복제 | 격리 유지. 운영 요소가 하나 는다. 토픽 화이트리스트 관리 필요 |
| C | 계정 이벤트를 이벤트가 아니라 **pull-through 계약**으로 정식화 | BE-575 가 이미 그렇게 하고 있다. 그러나 `account.deleted`(GDPR)는 pull 로 성립하지 않는다 — 소비자가 "언제" 물어봐야 하는지 모른다 |

> C 를 고르더라도 **`account.deleted` 만은 push 가 필요하다.** 익명화는 사용자가 다시 오지
> 않아도 일어나야 하는 일이다.

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — **완료.**
      🔴🔴 전수는 **5가 아니라 16 리스너 / 3계열**이었다(iam→ecommerce 5 · wms→ecommerce 6 ·
      wms→scm 5). 🔵 역방향(ecommerce→iam)은 **0건**. 🔴 wms→scm 에는
      `${WMS_KAFKA_BOOTSTRAP:-kafka:9092}` 이음매가 이미 있으나 **값이 어디에도 없고 주석의
      "In dev, same cluster" 가 거짓**이다.
      **라이브**: 가입 201 → iam `account.created` **0:0 → 0:1**, ecommerce-kafka **토픽 0개
      유지**. 경로 부재 매트릭스 **6/6 DNS-FAIL**(대조군 자기-`kafka` **3/3 RESOLVES**).
      🔵 컨슈머를 안 띄우니 ecommerce 쪽 토픽이 **아예 생기지 않아**, 원 티켓이 본 빈 토픽이
      컨슈머의 산물이었음이 같은 실험에서 보였다. 🔴 wms 두 계열은 **구조 판정만**(앱 미기동)
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
