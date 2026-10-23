# Task ID

TASK-BE-652

# Title

admin-service 의 조건 없는 `GET /api/v1/admin/settings` 가 500 — `AdminSettingJpaRepository.search` 의 `:keyPrefix IS NULL OR … LIKE CONCAT(:keyPrefix,'%')` 를 PostgreSQL 이 `character varying ~~ bytea` 로 거절한다(콘솔 WMS 운영설정 화면이 설정을 못 읽는다)

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (JPQL 한 개 + 같은 모양 형제 확인 + 실 DB IT)
> 기안 2026-10-23 UTC — `TASK-BE-644` AC-3 을 재려다 33차 창에서 발견. 서버 변경 ⇒ 배포는 **굽기**. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-BE-644` § 라이브 확인 (2026-10-23 UTC · 33차 창) — AC-3 ⚪(이 결함 때문에 설정을 못 읽고 못 바꿈).
- 후속: 이 티켓이 굽기에 실린 뒤 `TASK-BE-644` AC-3(설정 10 → 25 → «저재고만» 결과 변화)을 다시 잰다. 🔴 콘솔에 설정 **쓰기** 경로가 없다(창 관측) — 644 AC-3 을 재려면 API 로 `PUT /settings/{key}` 를 부를 길도 필요하다(AC-0 ③).
- 관련: `TASK-BE-637`(done — 같은 wms 의 «실 DB 에서만 나는» 타입 결함, 실 DB IT 로 잡음) · `TASK-BE-630`(done — admin `refs` 검색의 `:q IS NULL OR … LIKE` 형제).
- 선행: 없음.

# Goal

Fix issue found in TASK-BE-644. 콘솔 `GET /api/wms/settings` → admin-service 설정 목록이 **500**. 창 관측: `SettingRepositoryImpl.search` → `apps/admin-service/src/main/java/com/wms/admin/infra/persistence/AdminSettingJpaRepository.java:13-18`
(`WHERE (:keyPrefix IS NULL OR s.key LIKE CONCAT(:keyPrefix, '%')) AND (:scope IS NULL OR s.scope = :scope) AND (:warehouseId IS NULL OR s.warehouseId = :warehouseId)`) 에서 PostgreSQL `operator does not exist: character varying ~~ bytea`. 필터 없는 목록이 콘솔의 기본 호출이라 WMS 운영설정 화면이 설정을 하나도 못 읽는다.

굽기와 무관한 기존 결함이다 — `AdminSettingJpaRepository.java` 의 마지막 변경은 `38d525f3a`(#280, `TASK-BE-045`), `SettingRepositoryImpl.java` 는 `b47209acf`(#304). (창 기록은 «그 파일 마지막 변경 `b47209acf`» 라고 적었는데, 그것은 `SettingRepositoryImpl.java` 의 것이다 — 기안 때 `git log` 로 확인.)

목표: 필터가 없거나 일부만 있어도 200 + 계약 § 5.1 모양.

# Scope

## In Scope

- **AC-0 측정** (file:line · 실 DB):
  - ① 실 Postgres IT 로 500 재현 — 필터 없음 · `keyPrefix` 만 · `scope` 만 · `warehouseId` 만 — 어떤 조합이 깨지는가. 기존 시험이 왜 못 잡았는가(H2 · 슬라이스 · mock 저장소?).
  - ② 같은 모양 형제 전수: admin-service 의 `:x IS NULL OR … LIKE` — `infra/persistence/AdminUserJpaRepository.java:24`(같은 `CONCAT` 모양) · `readmodel/master/*RefRepository.java`(`LOWER(..) LIKE :q ESCAPE`) — 각각 필터 없는 호출이 실 DB 에서 서는가. wms 다른 서비스의 같은 모양도 grep.
  - ③ 콘솔 → admin 경로(`/api/wms/settings` → `/api/v1/admin/settings`)와 «콘솔에 설정 쓰기 경로가 없다» 는 관측을 file:line 으로 — 644 AC-3 을 무엇으로 잴지 적는다.
- 수선(AC-0 이 고른다): 널 파라미터의 타입을 명시하거나(`CAST(:keyPrefix AS string)` 류) 조건부로 조립(Specification) — 형제와 같은 해법으로.
- 시험: 실 Postgres IT(Testcontainers) — 위 네 조합 200 · 페이지 · 결과 모양.

## Out of Scope

- 콘솔 설정 **쓰기** 화면(필요하면 별도 콘솔 티켓 — AC-0 ③ 이 판단 재료).
- 설정 의미 변경(`TASK-BE-644` · `TASK-BE-647`).

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~③ 을 file:line · IT 출력으로. ② 에서 형제가 같은 500 이면 범위(이 티켓 / 별도)를 적는다.
- [ ] **AC-1** — 구현 + 실 DB 시험(시험만 담은 커밋으로 CI 빨강 먼저) · CI 의 wms 잡 초록.
- [ ] **AC-2** — 굽기 뒤 라이브: 콘솔 WMS 운영설정 화면 · `GET /api/wms/settings` 200 + 설정 행(`inventory.low_stock.default_threshold_qty` 포함). 같은 창에서 `TASK-BE-644` AC-3 을 잴 수 있으면 잰다.

# Related Specs

- `projects/wms-platform/specs/services/admin-service/architecture.md`
- `projects/wms-platform/specs/services/admin-service/domain-model.md` (설정)

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 5.1 `GET /api/v1/admin/settings`

# Edge Cases

- `keyPrefix` 에 `%` · `_` 가 들어오면 LIKE 와일드카드로 읽힌다 — 지금 동작을 적고, 바꾸려면 계약 먼저(형제 `refs` 는 `ESCAPE` 를 쓴다 — AC-0 ②).
- `scope` 는 enum — 널 비교가 같은 타입 추론 문제를 내는가(AC-0 ①).

# Failure Scenarios

1. H2 · mock 저장소 시험으로 초록 — PostgreSQL 에서만 나는 결함이다 → 실 DB IT.
2. 이 쿼리만 고치고 같은 모양 형제(`AdminUserJpaRepository`)를 남긴다 → AC-0 ②.
