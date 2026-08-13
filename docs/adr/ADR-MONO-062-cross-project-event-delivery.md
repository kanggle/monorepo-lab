# ADR-MONO-062 — 프로젝트별 Kafka 격리를 유지한 채 크로스프로젝트 이벤트를 도달시키는 방법

**Status:** ACCEPTED
**Date:** 2026-08-13
**History:** PROPOSED 2026-08-13. **ACCEPT is a human gate — this record authorises no code by itself.** 승인은 소유자의 **정확형** 지시(`ADR-MONO-062 ACCEPTED — <A|B|C>`)를 요구하며, 일반적인 "진행"/"proceed"/"추천대로" 는 이것을 승인하지 않는다. 작성 에이전트는 자기 제안을 스스로 ACCEPT 할 수 없다. · **ACCEPTED 2026-08-13 — B** (소유자가 `ADR-MONO-062 ACCEPTED — B` 를 직접 타이핑). 🔴 **게이트가 실제로 한 번 물었다**: 직전 라운드의 *"B(추천)"* 선택은 글자의 출처가 **작성 에이전트의 추천 라벨**이고 그 시점에 이 문서가 존재하지 않았으므로 넘기지 않았다. **self-ACCEPT 아님** · **§ 선택지 / § 추천 / § 결과 는 byte-unchanged**. 🔵 **rider 대조 결과: 없음** — 아래 § 결정.
**Decision driver:** `TASK-MONO-511` AC-0 — 크로스프로젝트 Kafka 컨슈머 **16 리스너 / 3계열**이 **한 건도** 이벤트를 받지 못한다. 그중 하나는 `TASK-BE-258` 의 **GDPR 익명화 의무**다.
**Related:** `TASK-MONO-511`(발굴·실측) · `TASK-BE-575`(pull-through 우회) · `TASK-BE-258`(GDPR 익명화) · `TASK-MONO-507`(프로젝트별 격리가 **의도**임을 확인) · `TASK-SCM-BE-003`(선택지 A 의 설정 계층을 이미 절반 만들어 둔 티켓) · `projects/iam-platform/specs/features/consumer-integration-guide.md`(같은 클러스터를 전제하는 문서) · `ADR-MONO-001`(로컬 네트워크 컨벤션)

---

## 실측 (2026-08-13 UTC, iam 전체 + ecommerce/wms 브로커)

**가입 한 번** (`POST /api/accounts/signup`, HTTP **201**, `accountId=3d51b618-…`):

```
                    가입 전                 가입 후
iam-kafka           account.created 0:0     account.created 0:1    ← 발행됐다
ecommerce-kafka     토픽 0개                토픽 0개               ← 그대로
wms-kafka           토픽 0개                토픽 0개
```

**경로 부재 매트릭스** — 오프셋보다 강한 술어:

```
  iam-kafka       -> ecommerce-kafka  DNS-FAIL     ecommerce-kafka -> wms-kafka        DNS-FAIL
  iam-kafka       -> wms-kafka        DNS-FAIL     wms-kafka       -> iam-kafka        DNS-FAIL
  ecommerce-kafka -> iam-kafka        DNS-FAIL     wms-kafka       -> ecommerce-kafka  DNS-FAIL
  대조군: 각 브로커 -> 자기 프로젝트 `kafka`                        RESOLVES (3/3)
```

네트워크 소속에 교집합이 없다(`iam_iam-net`+`iam_iam-e2e` / `ecommerce_ecommerce-net` /
`wms_wms-net`). `kafka` 는 프로젝트마다 다른 IP 로 해소된다(172.23.0.2 / 172.24.0.2 / 172.27.0.2).
공유되는 유일한 네트워크는 `traefik-net`(`external: true`)이고 **어느 브로커도 거기 붙어 있지
않다**.

🔵 **이번 회차가 발굴 티켓보다 깨끗하다.** 티켓은 `ecommerce-kafka` 의 **빈** `account.created`
를 보고 *"토픽 존재는 배선의 증거가 아니다"* 라고 적었는데, 컨슈머를 띄우지 않은 이번엔
**토픽이 아예 생기지 않았다** ⇒ 그 토픽이 **컨슈머의 산물**이었음이 한 실험 안에서 보인다.

---

## 🔴🔴 모집단은 티켓이 센 것의 **3배**다

| 계열 | 소비 서비스 / 리스너 | 토픽 | 상태 |
|---|---|---|---|
| **iam → ecommerce** (`account.*`) | user(2) · order(1) · product(1) · notification(1) = **5** | 3 | 다리 없음 |
| **wms → ecommerce** (`wms.*`) | order(1) · product(3) · shipping(2) = **6** | 4 | 다리 없음 |
| **wms → scm** (`wms.*`) | demand-planning(1) · inventory-visibility(3) · logistics(1) = **5** | 5 | 🔴 반쯤 지어짐(아래) |

🔵 **역방향(ecommerce → iam)은 0건**이다. ecommerce 의 `user.user.withdrawn` 컨슈머 2개는
자기 프로젝트 토픽을 읽는다.

**죽은 컨슈머가 무엇을 못 하고 있나**(iam→ecommerce 계열):

| 서비스 | 토픽 | 하는 일 | 안 되면 |
|---|---|---|---|
| user-service | `account.created` | 프로필 온보딩 | BE-575 (pull-through 로 우회 중) |
| user-service | `account.deleted` | `anonymizeProfile` | **TASK-BE-258 GDPR 익명화가 실행되지 않는다** |
| order-service | `account.deleted` | account-sync | 삭제 계정의 주문 측 정리 누락 |
| product-service | `account.status.changed` | 판매자 상태 반영 | 정지된 판매자가 계속 노출 |
| notification-service | `account.created` | 온보딩 알림 | 가입 알림 없음 |

---

## 🔴 wms→scm 에는 **이 문제를 위한 설정 계층이 이미 있고, 주석의 전제가 거짓이다**

`projects/scm-platform/docker-compose.yml:374`:

```yaml
# WMS_KAFKA_BOOTSTRAP: cross-project event source. In dev, same cluster.
KAFKA_BOOTSTRAP: ${WMS_KAFKA_BOOTSTRAP:-kafka:9092}
```

`TASK-SCM-BE-003` 이 *"cross-project source — 동일 클러스터일 수도 별도일 수도"* 라며 심어 둔
이음매다. 그런데 **"In dev, same cluster" 는 거짓**이고(scm 과 wms 는 각자 브로커),
`WMS_KAFKA_BOOTSTRAP` 은 **저장소 어디에서도 값이 할당되지 않는다**(유일한 등장이 위 주석,
scm `.env.example` 에도 없음 — 대조군으로 같은 탐지식이 그 파일의 `KAFKA_BOOTSTRAP` 은 찾는다).
⇒ 폴백이 항상 이겨서 scm 이 **자기 클러스터**를 읽는다.

🔴🔴 **그리고 이것이 선택지 하나를 지운다.** *"컨슈머별 bootstrap 을 파라미터화한다"* 는 A 의
**대안이 아니라 A 의 설정 계층**이다 — 주소를 정확히 적어도 네트워크가 분리돼 있으면 도달
자체가 불가하다(위 6/6 DNS-FAIL). scm 이 그것만 만들어 두고 한 번도 동작한 적이 없는 이유가
정확히 이것이다.

⇒ 실제 축은 셋뿐이다: **경로를 뚫거나(A) · 토픽을 복제하거나(B) · 이벤트를 쓰지 않거나(C)**.

---

## 🔴 문서 두 곳이 이 배선을 "live" 라고 적어 왔다

`ecommerce/specs/contracts/events/account-lifecycle-subscriptions.md` 와 `user-api.md` 가
*"cross-project deletion wiring is live"* 라고 진술한다. **문서가 틀린 것이 아니라, 문서가
말하는 토폴로지가 이 compose 에 없다.** iam 의 `consumer-integration-guide.md` 는 소비자가
**IAM 과 같은 클러스터**에 있다고 전제하고 쓰였고(§ *"단일 Kafka 클러스터를 다수 테넌트가
공유"*), 컨슈머 코드는 그 전제 위에서 **옳게** 쓰였다. 갈라진 것은 **배포 토폴로지**다.

그리고 `TASK-MONO-507` 이 프로젝트별 격리 compose 를 **의도된 격리**로 확인했다. 즉 두 개의
정당한 진술이 충돌하고 있고, 이 ADR 은 **어느 쪽을 굽힐지**를 정한다.

---

## 선택지

### A. 브로커를 공유 네트워크에 노출하고 소비자가 원본 클러스터를 직접 구독

`traefik-net`(이미 `external: true` 로 존재)에 브로커를 붙이거나 전용 공유망을 만들고,
소비자에 **두 번째 컨슈머 팩토리**를 준다. `WMS_KAFKA_BOOTSTRAP` 계열 변수가 그때 비로소
의미를 갖는다(= 이미 절반은 지어져 있다).

- ✅ iam 의 `consumer-integration-guide.md` 전제에 가장 가깝고, 추가 운영 요소가 0이다.
- ✅ 배선 비용 최소 — 공유 외부 네트워크가 이미 있다.
- 🔴 **프로젝트 격리가 Kafka 축에서 뚫린다.** `TASK-MONO-507` 이 의도로 확인한 성질을 되돌리는
  것이므로, 이 ADR 이 그 되돌림을 명시해야 한다.
- 🔴 `traefik-net` 은 **HTTP 라우팅용** 네트워크다. 브로커를 거기 올리면 그 망의 성격이 바뀐다
  (전용 `events-net` 을 새로 만드는 하위 변형이 있고, 그 선택은 A 의 rider 다).
- 🔴 소비자마다 브로커 주소가 늘고, 컨슈머 그룹 오프셋이 **원본 클러스터**에 산다.

### B. 릴레이(MirrorMaker 2 또는 소형 브리지)로 필요한 토픽만 복제

- ✅ **프로젝트 격리를 유지한다** — 브로커 간 네트워크 경로를 뚫지 않는 유일한 안.
- ✅ 소비자 코드/설정 변경 0 — 지금 그대로 `kafka:9092` 를 읽으면 된다.
- ✅ 화이트리스트가 **무엇이 프로젝트 경계를 넘는지의 명시적 목록**이 되어, 다음 사람이
  경계를 문서가 아니라 설정에서 읽는다.
- 🔴 운영 요소가 하나 는다(컨테이너 + 화이트리스트 관리). 데모 메모리 예산에 영향
  (`TASK-MONO-399` 가 다루는 축).
- 🔴 화이트리스트가 **3계열 12토픽**이다 — 발굴 티켓이 상정한 `account.*` 3개보다 크다.
- 🔴 재시작 시 중복 전달 — 컨슈머는 이미 멱등이지만 재확인 필요.

### C. 크로스프로젝트 이벤트를 포기하고 pull-through 계약으로 정식화

`TASK-BE-575` 가 이미 `account.created` 를 그렇게 우회하고 있다.

- ✅ 추가 인프라 0.
- 🔴 **`account.deleted`(GDPR)에 성립하지 않는다** — 익명화는 사용자가 다시 오지 않아도
  일어나야 하고, 소비자는 "언제 물어봐야 하는지" 를 모른다.
- 🔴 **`wms.*` 두 계열(11 리스너)에는 더 안 맞는다** — 재고 조정·출고 확정·입고 검수는
  폴링할 주체가 없다(주문 없이도 발생한다).
- ⇒ 16 리스너 중 **일부만** 덮는다. 나머지는 여전히 A 나 B 를 필요로 한다.

---

## 추천 — **B** (다만 이것은 제안이지 결정이 아니다)

세 축 중 **B 만이 두 개의 정당한 진술을 모두 지킨다**: `TASK-MONO-507` 의 "프로젝트 격리는
의도" 와 `consumer-integration-guide.md` 의 "소비자는 자기 클러스터에서 읽는다". A 는 앞을
굽히고, C 는 뒤를 굽히면서 16 리스너 중 일부만 덮는다.

🔴 **추천의 비용을 숨기지 않는다**: B 는 운영 요소를 하나 늘리고, 데모 호스트의 메모리 예산에
들어간다(`TASK-MONO-399` 가 아직 답하지 못한 축). 그 비용이 격리 유지의 값어치보다 큰지가
이 선택의 실질 질문이다.

🔵 **A 도 정당하다.** 특히 *"이 저장소는 하나의 데모 호스트에서 도는 포트폴리오"* 라는 관점에서
프로젝트 격리를 Kafka 축에서만 완화하는 것은 합리적 교환이고, 설정 계층이 이미 절반 지어져
있다는 점이 그 비용을 더 낮춘다.

---

## 결과 (ACCEPTED 시)

**B 를 고르면** — `TASK-MONO-511` 이 다음을 수행한다:

1. 릴레이 배선(MirrorMaker 2 vs 소형 브리지 선택은 **구현 AC**로 승격 — 이 ADR 은 *복제한다*를
   정하지 *무엇으로 복제하는지*는 정하지 않는다).
2. **화이트리스트 12토픽 3계열**을 명시(`account.*` 3 + `wms.*` 9). 🔴 계열 하나만 배선하면
   나머지 둘이 낙오한다 — 발굴 티켓이 그렇게 셌기 때문에 특히 위험하다.
3. AC-3: 도달이 끊기면 **실패하는 검사**. 🔴 *"토픽이 존재한다"* 를 술어로 쓰지 않는다 —
   빈 auto-created 토픽이 바로 그 함정이었다. 컨슈머 lag 또는 e2e 반응 확인.
4. AC-2: `account.deleted` 로 ecommerce 프로필이 `WITHDRAWN` 이 되고 PII 가 **실제로 지워지는지**
   (TASK-BE-258 의무의 *실행* 확인).
5. 문서 정합 — "live" 라고 적은 두 곳을 실제와 맞춘다.
6. 🔵 `WMS_KAFKA_BOOTSTRAP` 의 **거짓 주석 정리** — B 를 고르면 그 변수는 불필요해지므로,
   지우든 남기든 *"In dev, same cluster"* 라는 거짓 진술은 제거한다.

**A 를 고르면** — 위 3·4·5 는 동일하고, 1·2 대신 공유망 배선 + `WMS_KAFKA_BOOTSTRAP` 계열
변수 실제 설정이 들어간다. 🔴 그리고 **`traefik-net` 재사용 vs 전용 `events-net`** 이 미결
rider 로 남으므로 구현 AC 로 승격해야 한다.

**C 를 고르면** — 덮이지 않는 리스너(최소 `account.deleted` + `wms.*` 11)를 **명시적으로 열거**
하고, 각각을 "죽은 채로 둔다 / 제거한다 / 별도 안" 중 무엇으로 할지 함께 적는다.

---

## 결정 — **B** (ACCEPTED 2026-08-13)

**프로젝트별 Kafka 격리는 유지한다.** 프로젝트 경계를 넘어야 하는 토픽만 릴레이로 각 소비자
클러스터에 복제하고, 소비자는 지금처럼 자기 클러스터(`kafka:9092`)를 읽는다.

### 무엇이 구속력을 갖나

1. **A · C 는 배제된다.** 브로커 간 네트워크 경로를 뚫지 않는다 — `traefik-net` 에도, 새
   전용 망에도 브로커를 올리지 않는다. `TASK-MONO-507` 이 확인한 프로젝트 격리는 **Kafka
   축에서도 유지**된다.
2. **화이트리스트가 프로젝트 경계를 넘는 것의 유일한 출처다.** 3계열 12토픽
   (`account.*` 3 + `wms.*` 9). 🔴 계열 하나만 배선하고 끝내지 않는다 — 발굴 티켓이 한 계열만
   셌기 때문에 이 낙오가 특히 일어나기 쉽다.
3. **소비자 코드·설정은 바뀌지 않는다.** 이것이 B 의 값어치이므로, 구현이 소비자에
   두 번째 브로커 주소를 넣기 시작하면 그것은 B 가 아니라 A 다.
4. **`WMS_KAFKA_BOOTSTRAP` 의 거짓 주석은 제거한다.** B 아래에서 그 변수는 불필요해진다.
   변수를 지우든 남기든, *"In dev, same cluster"* 라는 **거짓 진술**은 남기지 않는다.

### 🔵 rider 대조 결과 — **없음** (점검했다는 사실도 산출물이다)

규정대로 반사가 아니라 대조했다: *"이 질문에 답하지 않고도 B 를 고를 수 있는가?"*

| 질문 | 답 없이 B 를 고를 수 있나 | 처리 |
|---|---|---|
| MirrorMaker 2 인가 소형 브리지인가 | **가능하다** — B 는 *복제한다*를 정하지 *무엇으로*를 정하지 않는다 | **구현 AC** (`TASK-MONO-511`) |
| 화이트리스트에 정확히 어떤 토픽이 들어가나 | 가능하다 (계열은 정해졌고 목록은 실측 산물) | **구현 AC** |
| 도달 검사를 무엇으로 하나 | 가능하다 | **구현 AC** — 🔴 단 *"토픽이 존재한다"* 를 술어로 쓰는 것은 이 ADR 이 금지한다 |

⇒ B 본문은 자기 안에 미결 질문을 남겨 두지 않는다(`ADR-MONO-060` 의 `act` 나 A 의
`traefik-net` vs 전용망과 **구조가 다르다**). plain `B` 선택으로 확정되지 않는 것이 없다.

### ACCEPT 가 인가하는 것 / 하지 않는 것

인가한다: `TASK-MONO-511` 이 § 결과 의 B 경로 1~6 을 수행하는 것. 인가하지 않는다: 브로커
네트워크 노출, 소비자에 두 번째 브로커 주소 추가, 화이트리스트 밖 토픽의 복제. 로드맵 각
단계는 별도 task 다(HARDSTOP-09).

### 게이트 — 통과했지, 우회하지 않았다

직전 라운드의 *"B(추천)"* 선택을 ACCEPT 로 읽지 않고 멈춰 정확형을 요청했고,
`ADR-MONO-062 ACCEPTED — B` 가 소유자 타이핑으로 도착했다. **그 사실을 여기 적어 둔다** —
안 적으면 다음 세션엔 "그냥 통과된 것" 과 구별되지 않는다.
