# Task ID

TASK-BE-647

# Title

inventory-service 의 저재고 기본 임계값 설정(`inventory.alert.low-stock.default-threshold`)이 어디에도 없다 — 재기동 직후 저재고 알림이 0 건이다(설정 변경 이벤트가 다시 올 때까지)

# Status

review

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (설정 한 줄 + 시험 — 단 기동 순서 판정)
> 기안 2026-10-22 UTC — `TASK-BE-644` AC-0 ④ 의 곁 발견. 소유자 결정 2026-10-22 KST: **«티켓 + 굽기 전 구현»** (데모는 굽기마다 재기동하므로 굽기 뒤 알림이 끊긴다). 서버 변경 ⇒ 배포는 **굽기**.

# Dependency Markers

- 관련: `TASK-BE-459`(done — 설정 변경 이벤트로 임계값을 라이브 갱신, «재기동하면 config 기본값» 을 전제) · `TASK-BE-644`(admin 표시 기준 — 같은 설정 키).

# Goal

- `apps/inventory-service/.../config/AlertConfig.java:44` — `@Value("${inventory.alert.low-stock.default-threshold:#{null}}") Integer defaultThreshold`.
- 이 프로퍼티를 넣는 `application*.yml` · env · compose 가 저장소에 없다(AC-0 이 전수 확인) ⇒ 재기동하면 기본값 `null` → `LowStockDetectionService.java:77-79` 가 알림을 내지 않는다.
- BE-459 의 라이브 갱신은 **설정이 바뀔 때만** 온다 — 재기동 뒤 아무도 설정을 안 바꾸면 알림은 계속 0.
- 판정 경계(`LowStockDetectionService.java:81` `<`) 는 그대로(소유자 결정 2026-10-22: admin 표시도 `<` 로 맞춘다 — `TASK-BE-644`).

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 프로퍼티를 넣는 곳 전수(yml 프로필 · env · compose · 데모 · 시험 설정) ② 기동 시 admin 의 현재 설정 값을 읽어 올 수 있는 길이 있는가(시작 시 1회 조회 · 컨슈머 오프셋 처음부터 · 없음) — 있으면 «config 기본값 10» 보다 정확하다 ③ 시험 프로필이 이 값을 넣고 있어 CI 가 결함을 못 본 것인가.
- 수선(최소안): `application.yml` 에 `inventory.alert.low-stock.default-threshold: 10`(스펙 `domain-model.md:221` 의 기본값과 같게). ② 가 값싸면 «기동 시 현재 설정 1회 조회» 를 소유자에게 제안.
- 시험: 프로퍼티 없이 기동한 컨텍스트에서 기본값 10 이 들어가는가 · 설정 변경 이벤트 뒤 그 값으로 바뀌는가(BE-459 비회귀).

## Out of Scope

- admin 표시 기준(`TASK-BE-644`).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~③ (아래 `## AC-0 측정`).
- [x] **AC-1** — 구현 + 시험 완료(로컬 `:inventory-service:test` rc=0) · **CI 의 wms 잡 초록은 PR 뒤 확인** — 그때 체크.
- [ ] **AC-2** — 굽기 뒤 라이브: 재기동된 데모에서 재고를 기준 아래로 내리면 저재고 알림이 생기는가(다음 데모 창).

# Related Specs

- `projects/wms-platform/specs/services/inventory-service/architecture.md` (`:254` — 설정 소비)
- `projects/wms-platform/specs/services/admin-service/domain-model.md` (`:221` — 기본값 10)

# Related Contracts

- `projects/wms-platform/specs/contracts/events/admin-events.md` (`:343` — 읽기)

# Edge Cases

- 설정 변경 이벤트가 기동 전에 이미 있었다(오프셋) — 재기동 뒤 다시 소비되는가(AC-0 ②).

# Failure Scenarios

1. 시험 프로필에만 값을 넣어 운영 프로필은 여전히 비어 있다 → AC-0 ① 전수 + «프로퍼티 없는 컨텍스트» 시험.

## AC-0 측정 (2026-10-22 UTC)

- **① 프로퍼티를 넣는 곳 전수**: 저장소 전체 grep(`default-threshold` · `INVENTORY_ALERT_LOW`) 결과 **소비처는 `AlertConfig.java:44` 하나뿐**, 값을 넣는 곳은 **0 곳** — `inventory-service` 의 `application.yml` · `application-dev.yml` · `application-integration.yml` · `application-standalone.yml` 어디에도 없고, env 이름 `INVENTORY_ALERT_LOW_STOCK_*` 도 어떤 compose/문서에도 없다. 구동 경로(`projects/wms-platform/docker-compose.e2e.yml:159-` · `tests/fulfillment-demo/docker-compose.ecommerce-fulfillment.yml:93-`) 는 `SPRING_PROFILES_ACTIVE` 를 **지정하지 않아** 기본 프로필(`application.yml`)만 읽는다 ⇒ 기본 프로필에 넣으면 데모·운영 모두 닿는다. (`infra/demo` 에는 inventory-service 서비스 정의가 없음.)
- **② 기동 시 admin 현재 설정을 읽는 길**: 지금은 **없다**. `InMemoryLowStockThresholdAdapter.java:20-22` 가 «시작 시 HTTP 조회가 필요한데 WMS 에 서비스 간 HTTP 인증이 없다» 로 보류를 명시. 대안인 컨슈머 오프셋 처음부터 재생은 `application.yml:45` `auto-offset-reset: earliest` 가 **그룹 신규일 때만** 적용(커밋된 오프셋이 있으면 재생 안 됨)이라 재기동엔 값싸지 않다. 기록만 — 소유자에게 «시작 시 1회 조회» 는 별도 제안 사항.
- **③ 시험 프로필이 값을 넣어 결함을 가렸나**: **아니다.** `src/test/resources` 자체가 없고 시험 어디에서도 이 프로퍼티를 넣지 않는다. 다만 컨텍스트에서 **기본값을 단언하는 시험이 없었다**(`ApplicationContextSmokeTest` 는 빈 존재만 확인, 통합 시험은 `setDefaultThreshold(20)` 등을 직접 호출) ⇒ CI 가 볼 수 없던 구조.

## 구현 (2026-10-22 UTC · Sonnet)

- `apps/inventory-service/src/main/resources/application.yml` — `inventory.alert.low-stock.default-threshold: 10` + 주석(스펙 기본값 `admin-service/domain-model.md:221`, BE-459 이벤트가 여전히 라이브 덮어씀).
- `ApplicationContextSmokeTest.lowStockDefaultThresholdResolvesToTenFromBaseConfig` — 시험 쪽 프로퍼티 오버라이드 없이 실제 base config 에서 묶인 값이 10 인지 단언(yml 줄이 없으면 `Optional.empty` 로 **구성상 실패**). 이어 `updateDefaultThreshold(25)` 후 25 로 바뀜을 확인(BE-459 비회귀의 컨텍스트 수준 확인; 이벤트 경로 자체는 기존 `AdminSettingsConsumerTest` · `AdminSettingsConsumerIntegrationTest` 가 덮는다).
- 로컬: `./gradlew :projects:wms-platform:apps:inventory-service:test` rc=0 (tests=259, failures=0, errors=0, skipped=0). Testcontainers IT 는 CI 전용.
- AC-2(굽기 뒤 라이브)는 열려 있음.

## 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- PR #4479(head `6f7438245`) 체크 pending 0 · fail 0 · 설정 파일 변경이라 브랜치 nightly run `39817975871` **success** → AC-1 닫힘.
- `TASK-BE-643` · `TASK-BE-644` 머지 뒤 main 병합 — `INDEX.md` 충돌 2곳(ready = `(empty)` · in-progress = BE-647) 해소. 곁: review 칸에 남아 있던 `(empty)` 표시(643 · 644 를 review 로 옮길 때 지우지 않음)를 지웠다.
- 열림: **AC-2**(굽기 뒤 라이브 — 재기동된 데모에서 재고를 기준 아래로 내리면 저재고 알림이 생기는가).

## 라이브 확인 (2026-10-23 UTC · 33차 창)

- 배포 상태: 굽기 `283fd39cf` → AMI `ami-02b3ba00a3b2ff622`(51분 36초 · 태그 RepoCommit 일치) · 핀 PR #4498(`c5e3ddad3`) · 인스턴스 `i-0f39aec49ddbaa899`. impl 머지 `946c82b91`(#4479) 는 `283fd39cf` 의 조상.
- **AC-2 ⚪ 측정 못 함 (이유)** — 기준 10 아래로 내려간 SKU 가 없다(가용 99) · `/api/wms/alerts` 0 건. 재고를 깎는 조작은 하지 않았다 — 재고를 기준 아래로 내리는 조작이 있는 창에서 다시 잰다.
- ⇒ 이 티켓은 `review` 에 남는다(체크 칸은 동결 규칙으로 고치지 않음).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-2 — 34차 창 결과 (열린 채 유지).** 34차 창(2026-10-24 UTC): ⚪ 가용 재고 최소값이 85 이고 10 아래인 SKU 가 없어 알림도 0건이다. 기준(10) 자체는 `TASK-BE-652` AC-2 에서 설정 행으로 확인됐다.

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

- **AC-2 ✅.** 35차 창(굽기 `54b79233c`, 신선 볼륨 — 막 기동한 인스턴스): `SKU-APPLE-001` 재고 조정 −10 → available 5 (201).
  - inventory-service 로그: `inventory.low-stock-detected emitted inventoryId=d58d1efa… available=5 threshold=10`.
  - `GET /api/v1/admin/dashboard/alerts` 에 `LOW_STOCK` 행: `thresholdQty 10` · `actualQty 5`.
  - 막 기동한 인스턴스가 임계값 10 으로 알림을 냈다 — 이 티켓이 막으려던 «재기동 직후 알림 0건» 이 일어나지 않았다.
- 볼륨 잔여: 알림 행은 소유자 결정으로 지웠다(`TASK-MONO-672` § 35th 참조). 재고는 +80 으로 되돌렸다(available 85).

⇒ 판정: 모든 AC 닫힘 — 닫기 chore 대상
