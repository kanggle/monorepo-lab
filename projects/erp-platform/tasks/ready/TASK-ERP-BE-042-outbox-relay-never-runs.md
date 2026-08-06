# Task ID

TASK-ERP-BE-042

# Title

erp 아웃박스 릴레이가 **한 번도 돌지 않는다** — `@Scheduled` 는 있는데 `@EnableScheduling` 이 없어 read-model 프로젝션이 영구히 빈다

# Status

ready

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

# Acceptance Criteria

- [ ] **AC-0 (재현)** — 착수 시 위 세 수치를 다시 잰다: 아웃박스 UNPUBLISHED 건수 ·
      `kafka-get-offsets` end-offset · `*_proj` 행수. 🔴 **소비자 지연과 발행 부재는
      다르다** — 반드시 **브로커 offset** 을 근거로 삼는다
- [ ] **AC-1 (활성화)** — masterdata-service · approval-service 가 스케줄링을 켠다.
      🔵 형제들이 쓰는 방식이 두 가지다(Application 클래스 어노테이션 / 별도
      `SchedulingConfig`). erp 안의 선례(notification-service `SchedulingConfig`)를 따라
      **한 가지로 통일**하고, 왜 그 쪽인지 한 줄 적는다
- [ ] **AC-2 (백로그)** — 이미 쌓인 미발행 행이 릴레이 기동 후 발행된다. 발행되지 않으면
      (예: 만료/파티션키 문제) 그 사실을 수치와 함께 적는다 — "안 된다" 도 유효한 결과다
- [ ] **AC-3 (회귀 가드)** — 🔴 **"어노테이션이 있는가" 를 grep 하는 가드는 만들지 마라.**
      그것은 대리지표다(어노테이션이 있어도 조건부 빈이 꺼져 있으면 안 돈다). 스프링
      컨텍스트를 띄워 **`ScheduledAnnotationBeanPostProcessor` 가 그 퍼블리셔의
      `publishPending` 을 실제로 등록했는지**를 단언하라. 두 서비스 각각.
      🔴 가드가 **무는지** 확인한다(활성화를 되돌리면 RED)
- [ ] **AC-4 (라이브 검증)** — `bash infra/demo/demo-up.sh iam erp console` 후
      `seed-erp.sh` 의 `⛔ 차단 … TASK-ERP-BE-042` 줄이 **사라지고** 사원 4/4 가
      프로젝션에 반영된다. 콘솔 `/erp/orgview` 의 BFF 원소 수로 확인한다(HTML 아님 —
      콘솔은 클라이언트 렌더라 SSR HTML grep 은 전 화면 0건을 내는 깨진 탐지기다)
- [ ] **AC-5 (모집단 재확인)** — erp 5개 앱 전수로 "`@Scheduled` 를 가졌는데 스케줄링이
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

- [ ] AC-0~AC-5 충족
- [ ] `./gradlew :projects:erp-platform:apps:masterdata-service:test :projects:erp-platform:apps:approval-service:test` GREEN
- [ ] Ready for review
