# Task ID

TASK-ERP-BE-042

# Title

erp 아웃박스 릴레이가 **한 번도 돌지 않는다** — `@Scheduled` 는 있는데 `@EnableScheduling` 이 없어 read-model 프로젝션이 영구히 빈다

# Status

review

# Owner

erp-platform

# Task Tags

- bug
- infra
- test

---

# 배경 — `TASK-MONO-510` 이 데모 시드를 만들다 발견

erp 시드가 마스터 20건을 실제 API 로 만든 뒤 `/erp/orgview`(read-model 사원 프로젝션)를
기다렸는데 **120초 동안 0건**이었다. 상류를 소비자 쪽이 아니라 **브로커에서** 확인했다:

```
erp_db.masterdata_outbox        UNPUBLISHED 17 / PUBLISHED 0
erp_approval_db.approval_outbox UNPUBLISHED  1 / PUBLISHED 0
kafka-get-offsets erp.masterdata.*        전 토픽 :0:0   ← 한 건도 발행된 적이 없다
erp_read_model_db.*_proj                  전 테이블 0행
```

토픽은 존재한다(컨슈머가 만든다). **메시지가 0** 이다.

## 원인

`MasterdataOutboxPublisher` 는 `@Scheduled(fixedDelayString = "${masterdata.outbox.poll-ms:1000}")`
로 1초마다 돌아야 하고 `@ConditionalOnProperty(..., matchIfMissing = true)` 라 기본
활성이다. 그런데 **Spring 은 `@EnableScheduling` 없이는 `@Scheduled` 를 등록하지
않는다** — 예외도 경고도 없이 **조용히 아무 일도 하지 않는다.**

전수 확인(`grep -rl "@EnableScheduling"`):

| 프로젝트 | 보유 서비스 |
|---|---|
| wms-platform | admin · inbound · inventory · master · notification · outbound (6/6) |
| scm-platform | demand-planning · inventory-visibility · procurement |
| finance-platform | account · … |
| **erp-platform** | **notification-service 하나뿐** ← masterdata · approval 없음 |

🔴 즉 **형제 파리티 낙오**다. 아웃박스를 가진 두 서비스(masterdata · approval)가 정확히
빠져 있고, 아웃박스가 없는 서비스 하나만 갖고 있다.

## 무엇이 죽어 있는가

- `/erp/orgview` — 사원 조직도. 시드가 무엇을 넣어도 **빈다**
- `/erp/delegation` 의 read-model 위임 사실 뷰 (`delegation_fact_proj`)
- read-model-service 의 프로젝션 5종 전부 (`employee/department/job_grade/cost_center/approval_fact`)
- 즉 **read-model-service 는 배포돼 있고 healthy 인데 하는 일이 없다.**

🔵 `/erp/masters` 와 `/erp/approval` 목록은 프로듀서 DB 를 직접 읽으므로 **영향 없다**
(실측: 각각 3·4·3·3·3 / 3건). 그래서 이 결함은 "화면 일부만 비는" 형태로 보였고
프로젝션이라는 공통 원인이 드러나기 전까지 데이터 부족처럼 읽혔다.

# Goal

아웃박스 행이 Kafka 로 발행되고, read-model 프로젝션이 따라오며, **다시 멈추면 CI 가
빨개진다.**

# Scope

## In Scope

- `masterdata-service` · `approval-service` 의 스케줄링 활성화
- 그것을 검사하는 회귀 가드
- 이미 쌓인 미발행 행의 처리 확인(릴레이가 켜지면 백로그가 그대로 나가는가)

## Out of Scope

- 결재 상신 실패 → **`TASK-ERP-BE-041`**
- 결재함이 비는 문제 → **`TASK-MONO-515`**
- notification-service (이미 갖고 있다)

---

# 🟢 착수 (2026-08-06/07) — 완료. 그리고 이 결함 **아래에** 하나가 더 있었다

## AC-0 재현 — 세 수치 그대로

```
erp_db.masterdata_outbox        published_at IS NULL  16
erp_approval_db.approval_outbox published_at IS NULL   1
kafka end-offset  erp.masterdata.* / erp.approval.*   전 토픽 :0:0
erp_read_model_db.*_proj                              6종 전부 0행
```

🔴 판정은 **브로커**에서 냈다. "프로젝션이 0" 은 지연과 발행 부재 양쪽에 똑같이
부합하고, end-offset 0 만이 둘을 가른다.

## AC-5 모집단 재확인 — **정확히 2개, 더도 덜도 없다**

| 서비스 | `@Scheduled` 보유 파일 | `@EnableScheduling` |
|---|---|---|
| gateway-service | 0 | 0 |
| **masterdata-service** | **1** | **0** ← |
| **approval-service** | **1** | **0** ← |
| notification-service | 1 | 1 |
| read-model-service | 0 | 0 |

즉 **아웃박스를 가진 두 서비스가 정확히 빠졌고**, 갖고 있는 하나는 아웃박스가 **없는**
서비스였다. wms 6/6 · scm · finance 는 전부 보유.

## AC-1 활성화 — `OutboxConfig` 에 붙였다 (별도 `SchedulingConfig` 아님)

두 서비스에서 스케줄되는 일은 **아웃박스 릴레이 하나뿐**이므로 스위치를 그것이 켜는
대상 옆에 둔다 — 릴레이 배선을 읽는 사람이 놓칠 수 없다. notification-service 의
`SchedulingConfig` 도 같은 원리다(자기가 켜는 재시도 스케줄러 옆에 있다).

## AC-2 백로그 — **전량 발행**

```
masterdata_outbox  PUBLISHED 16 / UNPUBLISHED 0
approval_outbox    PUBLISHED  1 / UNPUBLISHED 0
end-offset  department 3 · employee 4 · jobgrade 3 · costcenter 3 · businesspartner 3
            delegated 1                                    ← 시드 수량과 정확히 일치
```

볼륨을 보존한 채(`demo-down.sh erp` 는 `-v` 가 아니다) 이미지만 교체해 재기동했으므로,
이것은 **기존 백로그가 실제로 나간 것**이지 새로 만든 데이터가 아니다.

## AC-3 가드 — 무는 것 확인

`OutboxRelayIsScheduledIntegrationTest` × 2(masterdata · approval), integration 레인.
① 스케줄러가 `publishPending` 을 **실제로 등록했는지**, ② 미발행 행이 **아무도 부르지
않았는데** 발행되는지. `@EnableScheduling` 을 되돌리자 **2/2 FAILED**, 메시지가 원인을
그대로 지목했다(*"no ScheduledAnnotationBeanPostProcessor … add @EnableScheduling"* /
*"the row is still unpublished"*).

🔴 **첫 판의 구조 단언이 틀린 술어였다.** `getRunnable() instanceof ScheduledMethodRunnable`
로 캐스팅했더니 **행위 단언은 통과하는데 구조 단언만 FAILED** 였다 — Spring 이 러너블을
감싼다. 즉 그 술어를 그대로 뒀다면 **릴레이가 멀쩡히 도는데 "등록 안 됨" 이라고 보고**
했을 것이다. 스케줄러 자신의 태스크 설명(`Task#toString`)으로 바꿨다.
**행위 단언이 있었기 때문에 그 오진이 즉시 드러났다** — 구조 단언 하나만 있었으면
"고쳤는데 가드가 빨갛네" 로 한참 헤맸을 자리다.

🔴 **가드는 이 저장소에서 `outbox.polling.enabled=true` 를 켜는 최초의 테스트다.**
`application-test.yml` 주석은 *"integration tests explicitly enable it via
`@TestPropertySource`"* 라고 적어 뒀는데, 두 서비스 테스트 전수에 **그런 테스트가 0건**
이었다. 초록 CI 와 "한 번도 안 돈 릴레이" 가 공존한 이유가 이것이다.
기존 `MasterdataOutboxPublisherTest` 는 퍼블리셔를 **직접 호출**한다 — 강제 계층을
우회하는 테스트는 없는 강제를 증명한다.

## AC-4 라이브 — 콘솔 BFF 원소 수

```
/api/erp/read-model/employees      0 → 4      ✅
/erp/orgview  페이지                24,630 → 31,548 bytes
seed-erp.sh   "⛔ 차단 … TASK-ERP-BE-042"  →  "프로젝션 사원 4/4 반영 확인"
              차단 3 → 2 (남은 둘은 BE-041)
```

🔵 **시드를 한 줄도 안 고치고** 저 줄이 뒤집혔다 — 지문 기반 차단 분류가 의도대로
"고쳐지면 저절로 되살아난다" 를 실제로 보였다.

## 🔴 그래서 **면제를 회수했다** (시드 변경 1건)

프로젝션 대기의 `⛔ 차단` 을 `seed_fail` 로 되돌렸다. 이 경로는 이제 실측으로 성립하므로,
앞으로 프로젝션이 안 따라오면 그것은 알려진 결함이 아니라 **회귀**다. 고쳐진 결함의
면제를 남겨 두면 **그 면제가 정확히 회귀를 가리는 장치**가 된다 — 차단 분류의 값은
"고쳐지는 즉시 회수된다" 는 데 있고, 회수하지 않으면 "조용히 건너뛰기" 와 같아진다.

🔵 **범위 판단**: `infra/demo/seed/seed-erp.sh` 는 `projects/erp-platform/` 밖이지만
CLAUDE.md 가 열거한 공유 경로(`libs/` `platform/` `rules/` `.claude/` `tasks/templates/`
`docs/guides/` 루트 빌드파일)에도 들어 있지 않다. 이 수정은 **이 티켓의 직접적 귀결**
이고(면제의 근거가 사라졌다), 분리하면 main 에 **거짓 면제**가 남는다. 그래서 함께
넣고 여기 명시한다.

## 🔴🔴 릴레이가 살아나자 **그 아래의 결함**이 드러났다 → `TASK-ERP-BE-043`

위임 이벤트의 첫 메시지가 곧바로 DLT 로 갔다:

```
InvalidEnvelopeException: Invalid delegation envelope
                          (missing eventId/aggregateId/payload/grantId)
erp.approval.delegated.v1      end-offset 1
erp.approval.delegated.v1.DLT  end-offset 2
delegation_fact_proj           0행
```

**대조군이 원인을 확정했다** — 같은 스택, 같은 순간, 두 프로듀서:
masterdata 봉투는 최상위 `tenantId`·`aggregateType`·`aggregateId` 를 싣고,
approval 봉투는 **`aggregateId` 도 `tenantId` 도 없다**(`partitionKey` 만 있다).
소비자는 둘 다 `aggregateId` 를 **필수**로 요구한다. ⇒ `erp.approval.*` **여섯 토픽이
같은 운명**이고, 지금 하나만 보이는 이유는 나머지 다섯을 낳는 상신이 **BE-041 로
막혀 있어** 이벤트가 아직 없기 때문이다(`submitted` end-offset 0).

🔵 이것은 **이 티켓이 만든 것이 아니라 드러낸 것**이다. 아무것도 발행되지 않는 동안
그 불일치는 관측될 수 없었다.

## 이번에 하지 **않은** 것

- **BE-043 수정** — 계약 방향 결정(프로듀서 vs 소비자)이 필요하고, 위임 매퍼에만 있는
  테넌트 관문 비대칭까지 얽힌다. 별도 티켓
- **DLT 2건 처리** — BE-043 AC-6
- **`jdk`/`@Scheduled` 외 다른 스케줄 경로 점검** — AC-5 전수가 2개로 닫혔으므로 불필요
- 🔴 **`docs/guides/interview-demo-walkthrough.md` 의 한계 표 갱신** — 그 표의 "`/erp/orgview` 는 시드와 무관하게 빈다(ERP-BE-042)" 행이 **이제 틀렸다**. 다만 `docs/guides/` 는 CLAUDE.md 가 열거한 **모노레포 레벨 경로**라 프로젝트 티켓이 손대지 않는다. 그 표는 `TASK-MONO-510` 의 AC("가이드 한계 표 갱신") 소유이므로 거기서 처리한다 — **스코프를 넘지 않으려고 미룬 것이지 누락이 아니다.**

---

# Acceptance Criteria

- [x] **AC-0 (재현)** — 착수 시 위 세 수치를 다시 잰다: 아웃박스 UNPUBLISHED 건수 ·
      `kafka-get-offsets` end-offset · `*_proj` 행수. 🔴 **소비자 지연과 발행 부재는
      다르다** — 반드시 **브로커 offset** 을 근거로 삼는다
- [x] **AC-1 (활성화)** — masterdata-service · approval-service 가 스케줄링을 켠다.
      🔵 형제들이 쓰는 방식이 두 가지다(Application 클래스 어노테이션 / 별도
      `SchedulingConfig`). erp 안의 선례(notification-service `SchedulingConfig`)를 따라
      **한 가지로 통일**하고, 왜 그 쪽인지 한 줄 적는다
- [x] **AC-2 (백로그)** — 이미 쌓인 미발행 행이 릴레이 기동 후 발행된다. 발행되지 않으면
      (예: 만료/파티션키 문제) 그 사실을 수치와 함께 적는다 — "안 된다" 도 유효한 결과다
- [x] **AC-3 (회귀 가드)** — 🔴 **"어노테이션이 있는가" 를 grep 하는 가드는 만들지 마라.**
      그것은 대리지표다(어노테이션이 있어도 조건부 빈이 꺼져 있으면 안 돈다). 스프링
      컨텍스트를 띄워 **`ScheduledAnnotationBeanPostProcessor` 가 그 퍼블리셔의
      `publishPending` 을 실제로 등록했는지**를 단언하라. 두 서비스 각각.
      🔴 가드가 **무는지** 확인한다(활성화를 되돌리면 RED)
- [x] **AC-4 (라이브 검증)** — `bash infra/demo/demo-up.sh iam erp console` 후
      `seed-erp.sh` 의 `⛔ 차단 … TASK-ERP-BE-042` 줄이 **사라지고** 사원 4/4 가
      프로젝션에 반영된다. 콘솔 `/erp/orgview` 의 BFF 원소 수로 확인한다(HTML 아님 —
      콘솔은 클라이언트 렌더라 SSR HTML grep 은 전 화면 0건을 내는 깨진 탐지기다)
- [x] **AC-5 (모집단 재확인)** — erp 5개 앱 전수로 "`@Scheduled` 를 가졌는데 스케줄링이
      꺼진 서비스" 를 다시 센다. 이 티켓이 지목한 2개가 전부인지, 더 있는지 수치로 적는다

# Related Specs

- `projects/erp-platform/specs/services/masterdata-service/architecture.md` § 아웃박스
- `projects/erp-platform/specs/services/read-model-service/architecture.md`
- `platform/testing-strategy.md`

# Related Contracts

- `projects/erp-platform/specs/contracts/events/masterdata-events.md`
- `projects/erp-platform/specs/contracts/events/approval-events.md`

# Edge Cases

- 릴레이가 켜지면 **백로그가 한꺼번에** 나간다 — 컨슈머의 멱등(`processed_events`)이
  실제로 무는지 확인할 것
- `outbox.polling.enabled` 는 `matchIfMissing=true` 이므로 켜져 있다고 **믿기 쉽다**.
  이 결함은 그 플래그와 무관한 층에서 죽었다 — 플래그를 근거로 삼지 말 것
- 슬라이스/단위 테스트는 `application-test.yml` 에서 릴레이를 끈다. 가드는 그 프로파일이
  아닌 곳에서 돌아야 한다

# Failure Scenarios

- **어노테이션만 추가하고 라이브 미확인** → 조건부 빈/프로파일 때문에 여전히 안 돌 수
  있다. AC-4 가 막는다
- **grep 가드로 닫기** → 다음에 조건이 하나 더 붙으면 가드는 초록인데 릴레이는 죽는다.
  AC-3 이 명시적으로 금지한다

# Definition of Done

- [x] AC-0~AC-5 충족
- [x] 두 서비스 `integrationTest` 의 새 가드 GREEN (물기 확인 완료: 되돌리면 2/2 FAILED)
- [x] Ready for review
