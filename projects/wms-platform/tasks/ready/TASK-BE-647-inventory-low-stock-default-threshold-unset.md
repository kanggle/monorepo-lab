# Task ID

TASK-BE-647

# Title

inventory-service 의 저재고 기본 임계값 설정(`inventory.alert.low-stock.default-threshold`)이 어디에도 없다 — 재기동 직후 저재고 알림이 0 건이다(설정 변경 이벤트가 다시 올 때까지)

# Status

ready

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

- [ ] **AC-0** — 위 ①~③.
- [ ] **AC-1** — 구현 + 시험 · CI 의 wms 잡 초록.
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
