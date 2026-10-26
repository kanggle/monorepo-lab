# Task ID

TASK-BE-652

# Title

admin-service 의 조건 없는 `GET /api/v1/admin/settings` 가 500 — `AdminSettingJpaRepository.search` 의 `:keyPrefix IS NULL OR … LIKE CONCAT(:keyPrefix,'%')` 를 PostgreSQL 이 `character varying ~~ bytea` 로 거절한다(콘솔 WMS 운영설정 화면이 설정을 못 읽는다)

# Status

done

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

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-2 — 닫힘 (settings 절반).** 34차 창(2026-10-24 UTC): ✅ settings — `GET /api/v1/admin/settings` 를 필터 없이 불러 200, 설정 4행(`inventory.low_stock.default_threshold_qty` = 10 포함). 같은 방식의 `/users` 는 403 `FORBIDDEN`(demo-corp 토큰에 `WMS_ADMIN` 이 없다 — 500 이 아니고, 이 토큰으로는 잴 수 없다). 소유자는 콘솔 «WMS 운영설정» 화면을 따로 확인하지 않았다 — 화면 절반은 ⚪. `TASK-BE-644` AC-3 은 그 티켓에 ⚪ 로 적었다. (증거 o05)
- **AC-0 — 열린 채 유지.** AC-0 이 열려 있어 티켓은 `review` 에 남는다.
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 AC-2 settings 절반의 닫힘 기록이다.)

---

## CORRECTION — 35차 창 라이브 결과 (2026-10-25 UTC)

- **AC-2 ✅ — 화면 절반까지 닫힘.**
  - API 절반(다시): `GET /api/v1/admin/settings` 200, 4행(임계값 행 포함).
  - 화면 절반(소유자): 콘솔 WMS 운영설정이 «예약 TTL 24» 와 «저재고 기본 임계치 10» 을 오류 없이 나열한다. «프로젝션 상태 = 권한 없음».
  - 관찰(원인 미측정): 화면은 2행을 보이는데 API 는 4행을 돌려준다. 프로젝션 상태 «권한 없음» 은 `WMS_ADMIN` 없는 토큰 때문일 가능성이 높다 — 추론이다.
- **AC-0 — 열린 채 유지.** 이번 창에서 건드리지 않았다(① 실 DB IT 출력 · ③ 콘솔 쓰기 경로 부재 측정이 남아 있다).

⇒ review 유지 — 남은 것: AC-0 (①~③ 을 file:line · IT 출력으로 — 특히 ③ «콘솔 쓰기 경로 부재» 미측정). AC-1 · AC-2 는 닫힘.

---

## CORRECTION — AC-0 닫힘 (2026-10-26 UTC, 코드·CI 측정 · 라이브 불요)

- **AC-0 ✅ — ①~③ 전부 file:line · CI 출력으로.**
  - **① IT 출력 (CI run `39969962421`, head `874b09b4b` = 시험만 담은 커밋, 2026-10-23T14:28Z, conclusion `failure`)** — `gh run view 39969962421 --log-failed` 원문: `AdminListSearchIT > users_noFilters_and_eachFilterAlone() FAILED` · `InvalidDataAccessResourceUsageException at AdminListSearchIT.java:109` ← `SQLGrammarException` ← `PSQLException` · `83 tests completed, 1 failed`. settings 넷은 이 실행에서 초록(수정 전 코드) — 그래서 `SettingsListLiveShapeIT` 를 더했고 그 커밋 `501b5d582` 에서 settings 둘이 `SQLGrammarException` 빨강(85건 중 3건, AC-1 기록), 구현 `8b7ed5239` 뒤 fail 0. ⇒ 깨지는 조합 = `keyPrefix`/`q` 가 없는 호출(무필터 · 다른 필터만). 기존 시험이 못 잡은 이유는 위 구현 노트(목록 쿼리를 실 PG 로 부르는 IT 부재 · in-memory 저장소)와 «AC-0 보강»(정렬 없는 `PageRequest` · 따뜻한 해석 캐시).
  - **② 형제 — admin-service 밖까지 넓혔다.** wms `apps/**/src/main/**/*.java` 의 `LIKE … CONCAT(` 전수: admin-service 는 이미 `LikePatterns`/`RefSearch` 로 고쳐짐(주석만 남음). **master-service 에 같은 계열 6줄** — `adapter/out/persistence/JpaWarehouseRepository.java:24-26,44-46` · `JpaPartnerRepository.java:29-30` · `JpaSkuRepository.java:40-41` (`LOWER(..) LIKE LOWER(CONCAT('%', :q, '%'))`). 🔵 **모양이 다르다**: 모두 `(:q IS NULL OR :q = '' OR …)` — `:q = ''` 가 파라미터를 문자열로 타입 지정한다(깨진 admin 두 쿼리에는 이 비교가 없었다). 실 PG 증거: `WarehouseRepositoryImplTest.java:191` 이 Testcontainers `postgres:16-alpine`(`:40`)에서 `new WarehouseListCriteria(ACTIVE, null)` 로 `q=null` 검색을 부른다. ⚪ **Partner · Sku 의 `q=null` 실 PG 호출은 시험에서 찾지 못했다**(`PartnerRepositoryImplTest` · `SkuRepositoryImplTest` 는 Testcontainers 이나 null-q 검색 호출 0건) — 같은 `:q = ''` 모양이라 같은 결과로 추정하지만 **측정은 아니다**. 같은 500 이라는 증거가 없으므로 범위 = **이 티켓 밖, 별도 티켓 없음**(재발 시 이 줄이 출발점). 🔴 «Warehouse 한 줄 초록» 을 «master 전체 안전» 으로 승격하지 않는다.
  - **③ 콘솔 → admin 경로 · 쓰기 경로 부재.** 경로: `console-web/src/app/api/wms/settings/route.ts:20` `GET` 하나(파일에 `PUT`/`PATCH`/`POST` export 없음, `:15-18` «READ-ONLY: GET only … no proxy route for it exists here») → `features/wms-ops/api/wms-settings-api.ts:25-37` `listSettings` `{ method: 'GET', path: '/settings?…' }` → `callWmsAdmin`(admin-service `/api/v1/admin/settings`, § 5.1). 단건은 `:50-60` `getSetting` GET. **쓰기 부재 측정**: `features/wms-ops/**` 의 `/settings` 출현 10줄이 전부 GET 또는 주석, `src/**` 전체에서 settings 로 가는 `PUT|PATCH|POST|DELETE` 0건. ⇒ `TASK-BE-644` AC-3(콘솔에서 임계값 바꾸기)은 **콘솔로는 잴 수 없다** — 쓰기는 `PUT /api/v1/admin/settings/{key}`(§ 5.3, `WMS_ADMIN`)를 직접 부르는 것뿐이고, 콘솔 쓰기 화면은 이 티켓 Out of Scope 대로 별도 결정.
- **AC-1 ✅ · AC-2 ✅**(34 · 35차 CORRECTION) ⇒ **모든 AC 닫힘 → done.** 4차원: impl PR #4502 `state=MERGED` · squash `adae12ea4` ⊂ `origin/main` · 머지 시점 `statusCheckRollup` FAILURE 0 · 이 AC 절.
