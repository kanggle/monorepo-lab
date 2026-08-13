# Task ID

TASK-MONO-511

# Title

IAM 계정 이벤트가 소비자 프로젝트에 **한 건도 도달하지 않는다** — 컨슈머 5개가 다른 Kafka 클러스터를 구독하고 있고, 그중 하나는 GDPR 익명화 의무다

# Status

done

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

# 🟢 구현 (2026-08-13 UTC) — `ADR-MONO-062 ACCEPTED — B` 이행. AC-1~AC-4 완료

## ⓪ 🔴🔴 계열은 3개가 아니라 **5개**였다 — ADR 의 숫자까지 과소계수를 물려받았다

1회차가 *"16 리스너 / 3계열"* 로 티켓을 3배 키웠고, `ADR-MONO-062` 는 그 숫자를 그대로
받아 *"3계열 12토픽"* 을 구속력으로 적었다. **독립적으로 다시 세니 5계열 / 17라우트 /
20리스너다:**

| 계열 | 토픽 | 리스너 | 1회차·ADR 이 셌나 |
|---|---|---|---|
| iam → ecommerce | 3 | 5 | ✅ |
| wms → ecommerce | 5 | 6 | ✅ (토픽 수는 4로 적었다) |
| wms → scm | 5 | 5 | ✅ |
| **ecommerce → wms** | **2** | **2** | ❌ **아무도 안 셌다** |
| **scm → wms** | **2** | **2** | ❌ **아무도 안 셌다** |

🔵 1회차의 *"역방향은 0건"* 은 **`ecommerce→iam` 에 대해서만 참**이었다. 그 질문에 답하고
나서 `ecommerce→wms` 는 묻지 않았다 — **한 방향을 확인한 것이 모든 방향을 확인한 것으로
읽혔다.**

🔴 소유자가 정확형으로 **측정된 5계열 전부**를 골랐다(2026-08-13). ADR 본문의 "3계열
12토픽" 은 발굴 티켓의 과소계수에서 왔다는 사실을 `mm2.properties` 에 적어 뒀다.

**🔴🔴 판독기가 세 번 틀렸고, 세 번 다 내 술어였다** — 그래서 두 개의 독립 계측기로
교차검증했다(node 파서 / bash 가드, **양쪽 다 17라우트**):

1. **발행처 탐지가 전건 실패**(`owner=(none found)`) — 토픽명이 발행 서비스의 아웃박스
   퍼블리셔 **상수**로 살고 `.send("리터럴")` 이 아니었다. 🔴 그리고 wms 는 아예 **계산**한다
   (`"wms.master." + aggregate + ".v1"`) ⇒ 리터럴 grep 의 0건은 부재가 아니라 **계측기의
   한계**였다. 소유는 접두 규약으로 정하고 그렇게 적었다.
2. **scm 리스너를 통째로 놓쳤다** — scm 컨슈머는 전부 `topics = TOPIC`(상수)이라 리터럴만
   보는 판독기에는 **0건**으로 보였다. 그 상태로 "scm 은 크로스프로젝트가 없다" 로 끝낼 뻔했다.
3. **유효성 술어가 빨갛게 떴다**(어노테이션 108 vs 귀속 102) — 추적하니 wms
   notification-service `AlertConsumer` 의 6건이 `${prop}` 플레이스홀더였고, **wms→wms
   내부**라 결론은 안 바뀌었다. 🔵 안 붙여 놨으면 그 6건이 조용히 빠진 채 "일치" 를 봤다.

## ① 배선 — MirrorMaker 2, 한 프로세스, 4클러스터 5흐름

구현체 선택은 ADR 이 구현 AC 로 위임한 rider 다. **MM2**를 골랐다: 코드 0줄, 이미지는
**브로커가 이미 쓰는 `apache/kafka:3.7.0`**, 그리고 다중 클러스터가 원래 이 도구의 용도라
흐름 5개를 한 JVM 이 처리한다(브리지를 흐름마다 띄우면 데모 메모리 예산 `MONO-399` 를 그만큼
깎는다). 오프셋·재시도·재기동 중복은 이미 풀린 문제다.

🔴 **`IdentityReplicationPolicy` 가 없으면 전부 조용히 실패한다.** MM2 기본 정책은 대상에
`<source>.` 접두를 붙여 `iam.account.created` 를 만든다 — 소비자는 `account.created` 를
구독하므로 **토픽은 생기고 데이터는 흐르는데 아무도 안 읽는다.** 그건 이 티켓이 고치려는
결함과 **똑같이 생겼다.**

🔴 **이름을 보존하면 순환이 문제가 된다.** 실제로 양방향 쌍(`ecommerce↔wms`)이 있다. 안전한
이유는 화이트리스트가 **접두로 서로소**라서인데, 그건 우연이 아니라 **불변식**이므로 가드가
단언한다(주석은 다음 사람이 안 읽는다).

## ② 🔴🔴 B 의 진짜 난관은 복제가 아니라 **주소**였다

네 브로커가 **전부 자기를 `kafka:9092` 로 광고한다.** 네 네트워크에 동시에 붙는 릴레이에게
그 이름은 모호하고, **부트스트랩 주소를 컨테이너명으로 정확히 적어도 사라지지 않는다** —
클라이언트는 응답으로 받은 advertised listener 로 다시 접속하기 때문이다. 증상이 연결 실패가
아니라 **조용한 오배송**이라 특히 나쁘다.

⇒ 브로커마다 **전역 유일 이름으로 광고하는 `RELAY` 리스너**를 하나 더 단다
(`infra/demo/*-relay.override.yml`, 4개). 프로젝트 compose 는 **안 건드린다** — 릴레이는
데모 합성의 관심사이고, `*-identity.override.yml` 이 같은 이유로 존재한다.

**이것은 A 가 아니다** — 브로커는 한 곳도 안 움직이고, 공유망에 안 올라가고, 서로 여전히
못 본다. 소비자 설정은 한 글자도 안 바뀐다. 🔵 **그 격리를 실측했다**: `iam-kafka` 안에서
`iam-kafka:9095` 만 응답하고 나머지 셋은 **닿지 않는다.**

## ③ AC-1 · AC-3 — 라이브 도달 (증분이 술어, 존재가 아니다)

`infra/demo/relay/probe-relay.sh` — 5계열 전부 **양성 + 음성 대조**:

```
✓ iam → ecommerce   account.created                    2 → 3
✓ (음성) iam ↛ wms · scm                                0 유지
✓ wms → ecommerce   wms.inventory.adjusted.v1          0 → 1
✓ wms → scm         wms.inventory.adjusted.v1          0 → 1
✓ wms → ecommerce   wms.master.sku.v1                  1 → 2
✓ (음성) wms ↛ scm   wms.master.sku.v1                  0 유지   ← 화이트리스트에 없다
✓ ecommerce → wms   ecommerce.fulfillment.requested.v1 0 → 1
✓ scm → wms         scm.procurement.inbound-expected.v1 1 → 2
✓ (음성) 나머지 목적지 전부 0 유지
```

🔵 **`wms.master.sku.v1` 이 ecommerce 엔 가고 scm 엔 안 가는 칸이 가장 날카롭다** — 화이트
리스트가 **선택적**이지 통짜 다리가 아님을 보인다. 양성만 있으면 *"복제가 된다"* 와
*"전부 복제된다"* 를 구별할 수 없고, 후자는 ADR 이 지키려던 격리를 조용히 되돌린다.

**엔드투엔드**(실제 가입 → ecommerce DB):

```
가입 201  accountId=1b5ee08e-…
iam       account.created  2:0 → 2:1
ecommerce account.created  2:0 → 2:1        ← 같은 증분
ecommerce user_profiles     그 accountId 로 행 생성 (ACTIVE)
```

🔴🔴 **이 회차의 판정이 원 티켓보다 강한 이유**: ecommerce 쪽에 **컨슈머를 한 개도 안 띄운
상태**에서 토픽이 생기고 채워졌다 ⇒ 그 토픽은 **컨슈머의 산물일 수 없다.** 원 티켓이 속았던
빈 auto-created 토픽과 정확히 반대되는 증거다.

## ④ AC-2 — GDPR 익명화가 **실제로 실행됐다**

`account.deleted` 2단계를 iam 클러스터에 넣고 ecommerce 프로필을 관찰:

```
before          ACTIVE    | probe@demo.com | MONO511 Probe
phase 1 (anonymized=false, grace)   iam 2→3 · ecommerce 2→3
                WITHDRAWN | probe@demo.com | MONO511 Probe   ← 상태만, PII 유지(정상)
phase 2 (anonymized=true, post-grace) iam 3→4 · ecommerce 3→4
                WITHDRAWN | <null>         | <null>          ← PII 실제 삭제
```

🔵 **IAM 의 삭제 API 는 안 탔다** — 깨져 있던 절반은 IAM 의 발행이 아니라 **도달**이었으므로,
릴레이→컨슈머→DB 라는 그 절반을 쟀다. 그렇게 적는다.

🔴 **첫 시도는 아무 반응이 없었고, 원인은 내 탐침이었다.** 삭제 이벤트에 `tenantId=ecommerce`
를 넣었는데 그 프로필의 테넌트는 `fan-platform` 이었다(가입이 `X-Tenant-Id` 헤더를 안 따랐다) ⇒
테넌트 스코프 조회가 정확히 아무것도 안 건드렸다. **의도치 않게 테넌트 격리의 음성 대조가
됐다** — 배선이 아니라 탐침을 고쳐야 했다.
🔵 곁다리 관측(이 티켓 범위 아님): `POST /api/accounts/signup` 이 `X-Tenant-Id: ecommerce` 를
무시하고 `fan-platform` 으로 계정을 만들었다. 확인만 하고 손대지 않았다.

## ⑤ AC-3 정적 가드 — 술어는 **집합 동등**

`scripts/check-cross-project-topic-relay.sh`: *코드의 리스너 집합* ≡ *릴레이 화이트리스트*,
**양방향**. 한쪽에만 있으면 둘 다 결함이다(영원히 조용한 리스너 / 아무도 안 읽는데 경계를
넘는 복제). 추가로 **순환 서로소**와 **override 리스너 드리프트**(compose 는 스칼라를 병합
하지 않고 치환하므로 base 값이 override 값의 접두여야 한다)를 단언한다.

**bite 5회, 전부 물었고 각자 자기 단언만 건드렸다**: 화이트리스트에서 토픽 제거 / 순환 주입 /
아무도 안 읽는 토픽 추가 / 흐름 `.enabled=false` / override 가 base 리스너를 잃음.
🔴 **첫 bite 는 "안 물었다" 로 보였는데 실은 sed 이스케이프가 틀려 주입이 안 됐던 것**이다 —
주입 여부를 먼저 확인하는 규율이 그대로 발화했다.

**🔴🔴 가드 자신의 술어가 세 번 틀렸다**: ① 이벤트 *타입* 문자열을 토픽으로 세어 없는 라우트
3건을 만들었다 ② `KAFKA_ADVERTISED_LISTENERS` 를 **자기 주석이 인용해 둔 문장에서** 읽어
"base 와 override 가 다르다: 둘 다 같은 값" 이라는 말이 안 되는 실패를 냈다 ③ `${prop:default}`
바인딩을 버려서 wms 로 들어오는 두 계열이 통째로 안 보였다. 그리고 ④ **5분 넘게 걸렸다**
(파일 1500개를 하나씩 grep) — 느린 가드는 언젠가 꺼지고, **꺼진 가드는 초록을 보고한다.**

## ⑥ AC-4 문서 정합 + 거짓 진술 제거

- `account-lifecycle-subscriptions.md` — § Delivery topology 신설(실측 + *"빈 토픽은 컨슈머의
  산물"* 함정 명시).
- `user-api.md` — *"cross-project deletion wiring is live"* 가 **컨슈머에 대해 참이고 도달에
  대해 거짓**이었음을 적었다.
- `consumer-integration-guide.md` — *"단일 Kafka 클러스터를 다수 테넌트가 공유하므로"* 개정.
  🔵 **규칙 자체는 불변이고 여전히 필수다**(릴레이가 이름을 보존하므로 여러 테넌트가 여전히
  한 스트림에 섞인다) — 바뀐 것은 *이유*뿐이다.
- `WMS_KAFKA_BOOTSTRAP` **3곳 제거**(주석 2곳이 *"same cluster in dev"* 로 거짓). 🔴 주석만
  고치지 않고 **변수를 없앴다** — B 아래에서 소비자를 남의 브로커로 돌리는 것은 곧 **A** 라,
  손잡이를 남겨 두면 잘못된 길이 env 하나 거리에 놓인다. 🔵 세 번째 자리는 **주석이 아예 없어서**
  가장 놓치기 쉬웠다.

## ⑦ 남는 한계 — 조용히 넘기지 않는다

릴레이는 네 프로젝트 네트워크에 external 로 붙으므로 **네 도메인이 다 떠야** 기동한다.
`demo-core` 는 scm 을 포함하지 않아 **기본 데모에서는 릴레이가 안 뜬다** ⇒ `demo-up.sh` 가
빠진 도메인 이름을 대며 경고한다. *"배선이 없는데 아무도 모른다"* 가 이 티켓의 결함이었으므로
그 상태가 침묵해서는 안 된다.

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
- [x] **AC-2** — 완료(§④). `account.deleted` 2단계 실측: grace(`anonymized=false`) →
      `ACTIVE→WITHDRAWN`(PII 유지, 정상) / post-grace(`true`) → **email·name 이 실제로
      NULL**. 오프셋이 릴레이 전달을 확인했다(iam 2→3→4 · ecommerce 2→3→4).
      🔵 IAM 삭제 API 는 안 탔다 — 깨져 있던 절반은 발행이 아니라 **도달**이었으므로
      릴레이→컨슈머→DB 를 쟀고, 그렇게 적는다. 🔴 첫 시도 무반응의 원인은 **내 탐침의
      테넌트**였다(의도치 않게 테넌트 격리의 음성 대조가 됐다).
- [x] **AC-3** — 완료(§③·§⑤). **둘 다** 만들었다: 정적
      `scripts/check-cross-project-topic-relay.sh`(리스너 집합 ≡ 화이트리스트 **양방향**
      + 순환 서로소 + override 드리프트, **bite 5/5**) · 라이브
      `infra/demo/relay/probe-relay.sh`(술어 = **목적지 레코드 수 증분**, 라우트마다
      **음성 대조** 동반). 🔴 어느 쪽도 *"토픽이 존재한다"* 를 술어로 쓰지 않는다.
- [x] **AC-4** — 완료(§⑥). `account-lifecycle-subscriptions.md` § Delivery topology 신설 · `user-api.md` 의 "live" 가 어느 절반에 대해 참이었는지 명시 · `consumer-integration-guide.md` 의 "단일 클러스터" 전제 개정(**규칙은 불변, 이유만 바뀐다**) · `WMS_KAFKA_BOOTSTRAP` **3곳 제거**(주석만 고치지 않고 변수를 없앴다 — 남기면 A 가 env 하나 거리에 놓인다).

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

- [x] ADR + 배선 — [`ADR-MONO-062`](../../docs/adr/ADR-MONO-062-cross-project-event-delivery.md) ACCEPTED — B, MirrorMaker 2 한 프로세스 / 4클러스터 / 5흐름 / 17라우트
- [x] 도달 테스트 — 정적 가드 + 라이브 탐침(5계열 양성 + 음성 대조 전부 통과)
- [x] 문서 정합
- [x] Ready for review
