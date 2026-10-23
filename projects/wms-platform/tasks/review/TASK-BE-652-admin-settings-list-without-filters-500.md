# Task ID

TASK-BE-652

# Title

admin-service 의 조건 없는 `GET /api/v1/admin/settings` 가 500 — `AdminSettingJpaRepository.search` 의 `:keyPrefix IS NULL OR … LIKE CONCAT(:keyPrefix,'%')` 를 PostgreSQL 이 `character varying ~~ bytea` 로 거절한다(콘솔 WMS 운영설정 화면이 설정을 못 읽는다)

# Status

review

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
- [x] **AC-1** — 구현 + 실 DB 시험(시험만 담은 커밋으로 CI 빨강 먼저) · CI 의 wms 잡 초록. ✅ 2026-10-24 UTC (메인 세션) — PR #4502. 시험만 담은 커밋 `874b09b4b` 의 CI run `39969962421` 에서 `users_noFilters_and_eachFilterAlone` 만 빨강이었다(PSQLException). 🔴 그때 settings 시험 넷은 **수정 전 코드에서도 초록** → 라이브를 재현하지 못했다. 그래서 콘솔 호출 모양(정렬된 `size=20` + 쿼리 해석 캐시를 비운 첫 실행)의 `SettingsListLiveShapeIT` 를 `501b5d582` 로 더했다. 그 커밋에서는 settings 둘도 `SQLGrammarException` 빨강이었다(85건 중 3건 실패). 구현 `8b7ed5239` 뒤 head 체크 67개 pending 0 · fail 0. AC-0 ③ 의 «콘솔 쓰기 경로 부재» 는 여전히 미측정.
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

# 구현 노트 (2026-10-23 UTC)

## AC-0 (코드 측정 — 실 DB IT 출력은 CI 전용: 이 호스트에 Docker 없음)

- ① 깨지는 쿼리: `infra/persistence/AdminSettingJpaRepository.java:15` `:keyPrefix IS NULL OR s.key LIKE CONCAT(:keyPrefix, '%')`. `keyPrefix` 가 null 이면 `CONCAT` 안의 null 이 bytea 로 바인딩돼 `varchar ~~ bytea`. 따라서 **`keyPrefix` 가 없는 모든 호출**(무필터 · `scope` 만 · `warehouseId` 만)이 500 이고, `keyPrefix` 가 있으면 200 이다(추정 — IT `AdminListSearchIT` 의 네 조합이 CI 에서 확인). `:scope` · `:warehouseId`(enum · UUID)의 널 비교(`:x IS NULL OR …`)는 형제 `readmodel/**` 쿼리가 CI IT 에서 이미 서므로 같은 문제 없음(`MasterRefSearchIntegrationTest.everySearch_allNullFilters_…`).
- ② 같은 모양 형제(admin-service 전수 grep `IS NULL OR|CONCAT`): **`infra/persistence/AdminUserJpaRepository.java:24-26`** `:q IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) …` — 같은 `CONCAT` 모양 ⇒ 무필터 `GET /users` 도 같은 500 이었을 것(같은 티켓에서 수선). 나머지 `AdminRoleJpaRepository:20` · `AdminAssignmentJpaRepository:56-59`(`CONCAT` 없음, 평범한 `IS NULL`) 와 `readmodel/**`(`RefSearch.likePattern` 으로 패턴을 자바에서 조립 · 시간 경계는 `CAST(:p AS string)`)는 이미 고쳐진 형제. wms 다른 서비스는 grep 하지 않음(범위 밖) — 필요하면 별도.
- 왜 못 잡았나: 이 두 쿼리를 도는 시험이 모두 mock/in-memory 저장소(`InMemorySettingRepository`) 또는 컨트롤러 슬라이스였고 실 PostgreSQL 에서 목록 쿼리를 부르는 IT 가 없었다. TASK-BE-630 의 IT 는 `readmodel/master` 만 덮었다.
- ③ (콘솔 경로 · 쓰기 경로 없음 관측)은 이번 구현에서 측정하지 않았다 — 열려 있음(644 AC-3 선행).

## 수선

- 형제의 해법 복사: `readmodel/master/RefSearch.likePattern`(TASK-BE-630) — LIKE 패턴을 JPQL 이 아니라 자바에서 조립하고 JPQL 은 `:p IS NULL OR x LIKE :p` 로 둔다. `infra/persistence/LikePatterns` 신설(`prefix` · `lowerContains`), `SettingRepositoryImpl` · `UserRepositoryImpl` 이 호출.
- 의미 불변: `%` `_` 이스케이프를 **넣지 않았다**(수선 전과 같이 와일드카드로 읽힘). 바꾸려면 계약 먼저. 계약 변경 없음.

## 검증

- 로컬: `./gradlew :projects:wms-platform:apps:admin-service:test --offline` 초록(`LikePatternsTest` 포함). `AdminListSearchIT` 는 컴파일만 — Docker 없음 ⇒ 실행은 CI(`integrationTest`). 시험 커밋(`874b09b4b`)이 수선 전 CI 에서 빨강이어야 한다는 것은 **이 호스트에서 확인하지 못함**.
- AC-2(굽기 뒤 라이브) 는 열려 있음.

## AC-0 보강 — 라이브 호출과 첫 IT 의 차이 (CI 실측: PR #4502 run 39969962421)

- 사실(CI): 첫 시험 커밋 `874b09b4b` 에서 `AdminListSearchIT` 는 `users_*` 1건만 빨강이고 `settings_*` 4건은 **수선 전 코드에서도 초록**이었다 — 그 settings 시험은 라이브 500 을 재현하지 못했다.
- 라이브 호출 경로(코드 확인): 콘솔 `listSettings` (`console-web/src/features/wms-ops/api/wms-settings-api.ts:25-37`) 는 `keyPrefix`/`scope`/`warehouseId` 를 비면 보내지 않고 `page=0&size=20`(`wms-client.ts:145-157`, `WMS_DEFAULT_PAGE_SIZE=20` `types.ts:331`)만 보낸다 → `SettingsController.list:46-56` 이 `PageableSupport.pageable`(`PageableSupport.java:15-23`)로 **정렬된** `PageRequest`(`Sort.by(ASC,"key")`) 를 만든다.
- 첫 IT 와의 차이(코드 측정): ① IT 는 정렬 없는 `PageRequest.of(0,200)` 를 직접 저장소에 넘겼다(컨트롤러 · 정렬 없음). ② IT 는 같은 Spring 컨텍스트에서 `keyPrefix` 있는 호출이 먼저 실행됐을 수 있다(Hibernate 쿼리 해석 캐시가 따뜻함). 라이브는 재기동 뒤 첫 호출이 곧 무필터 호출이다.
- 배제한 차이: DB 버전(IT `postgres:16-alpine` = `docker-compose.yml:51`), Hibernate 속성(`application.yml:13-20` 이 IT·라이브 동일, `application-test.yml` 은 `ddl-auto` 만), 시드 행 4개라 `size=20` 에서 count 쿼리는 양쪽 모두 안 돈다.
- 후속 시험: `SettingsListLiveShapeIT`(컨트롤러 `list(null,null,null,0,20,"key,asc")` + 정렬된 Pageable 저장소 호출, 매 시험 전 `getQueryEngine().getInterpretationCache().close()`). 🔴 **이 호스트에서 실행하지 못했다(Docker 없음)** — 위 ①②중 어느 쪽이 원인인지는 CI 빨강으로만 가려진다. 둘 다 초록이면 차이는 아직 못 찾은 것이다.
