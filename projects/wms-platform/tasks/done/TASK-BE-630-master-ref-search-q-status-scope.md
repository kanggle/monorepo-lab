# Task ID

TASK-BE-630

# Title

admin-service 기준정보 조회(`GET /dashboard/refs/{type}`)에 검색어 · 상태 · 범위 필터를 실제로 구현한다 — 지금은 전부 무시되고 전체 목록만 준다

# Status

done — 구현 완료(2026-10-12 UTC). AC-1~5 ✅ — 단 AC-2~4 의 실제 PostgreSQL 칸은 CI 통합 잡이 잰다(로컬 Docker 없음).

# Owner

wms-platform

# Task Tags

- admin-service
- read-model
- contract-drift

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 조회 쿼리 6개 + 컨트롤러 파라미터. 🔴 PostgreSQL 미타입 null(42P18) 과 LIKE 와일드카드 이스케이프를 같이 잡아야 한다.

---

# Dependency Markers

- 후속: `TASK-PC-FE-333`(콘솔 WMS 입고 · 재고 · 출고의 ID 입력칸을 코드 · 이름 선택으로) — 이 티켓의 검색이 **전제**다.
- 관련(같은 결함 부류): `TASK-BE-331` · `TASK-BE-332`(읽기 모델 검색의 PostgreSQL 미타입 null).

# Goal

소유자 요청(2026-10-12): 콘솔 WMS 화면에서 창고 · 공급처 · SKU · 위치 · 로트를 **ID(UUID)를 몰라도** 코드나 이름으로 찾을 수 있게 한다. 그 첫 단계로, 계약이 «admin-UI dropdowns / autocomplete» 용이라고 적은 기준정보 조회가 **검색을 실제로 하게** 만든다.

실측(코드):
- `MasterRefController.list` 는 `page` · `size` · `sort` 만 받고 6종 모두 `findAll(pageable)` 을 돌려준다.
- 계약 § 1.7 은 *"Query parameters vary by `{type}`"* 라고만 적고 하나도 정의하지 않았다.
- 콘솔 `/wms/master` 는 `q`(검색어) · `status` 를 보낸다(`types.ts` 주석: *"forwarded tolerantly … expected to be ignored server-side"*). ⇒ **검색어 칸이 아무 효과 없이 같은 목록을 돌려준다** — 오류가 없어 운영자는 «결과가 이것뿐» 으로 읽는다. ⚪ 라이브 화면 확인은 안 했다(코드 판독).

# Scope

## In

- 계약 `specs/contracts/http/admin-service-api.md` § 1.7 — 파라미터 표: `q`(코드 · 이름 부분 일치, 대소문자 무시, `% _` 문자 그대로) · `status` · `warehouseId`(zones · locations) · `skuId`(lots) · `partnerType`(partners, 반복 가능). 해당 없는 파라미터는 **무시**(거절 아님). 잘못된 UUID = `400 VALIDATION_ERROR`.
- `readmodel/master/*RefRepository` 6개에 `search(...)` JPQL. `RefSearch` — LIKE 패턴을 **Java 에서** 만든다(소문자 · 이스케이프 · 빈 값=null): JPQL 에서 `CONCAT('%', :q, '%')` 로 만들면 필터 없는 호출이 PostgreSQL 에 미타입 null 을 넘긴다(42P18, TASK-BE-331 부류). `IN` 목록은 비어서 바인딩되지 않게 플래그 + 자리표시자.
- `MasterRefController` — 파라미터를 받아 해당 타입의 `search` 로.
- 시험: `MasterRefControllerSliceTest`(저장소가 **무엇을 받았는지** 단언 6칸) · `RefSearchTest`(단위) · `MasterRefSearchIntegrationTest`(실제 PostgreSQL 7칸 — 전 필터 null · 코드/이름 대소문자 · `_` 문자 그대로 · status · 창고 범위 · SKU 범위 · 거래처 유형).

## Out

- 정렬 기본값 변경(`lastEventAt,desc` 유지 — 콘솔이 필요하면 `sort` 로 지정).
- 인덱스 — `scale_tier: startup`, 앞쪽 `%` 부분 일치는 B-tree 를 못 쓴다. 규모가 커지면 `pg_trgm` 을 별도 티켓으로.
- 다른 § 1 목록(입고 · 재고 · 출고)의 조회 조건은 그대로 **ID** 를 받는다 — 코드 → ID 변환은 콘솔이 이 검색으로 한다(`TASK-PC-FE-333`).

# Acceptance Criteria

- [x] **AC-1** — 계약 § 1.7 에 파라미터 표가 있고, 구현이 그 표대로 동작한다. 실측: 슬라이스 9/9(저장소가 받은 값 단언).
- [x] **AC-2** — `q` 는 코드와(이름이 있는 타입은) 이름에 대소문자 무시 부분 일치한다. `_` · `%` 는 문자 그대로 일치한다. 실측: `RefSearchTest` 5/5(패턴) + `MasterRefSearchIntegrationTest`(`warehouse_q_…` · `underscore_isLiteral_…`). ⏳ 통합 시험은 **CI 가 잰다** — 이 호스트는 Docker 데몬이 꺼져 있다(`docker version` → npipe 없음). CI 의 wms 통합 잡이 `admin-service:integrationTest` 를 돈다(ci.yml, `needs.changes.outputs.wms`).
- [x] **AC-3** — `warehouseId`(zones · locations) · `skuId`(lots) · `partnerType`(partners, 여럿 OR) · `status` 가 범위를 좁힌다. 해당 없는 파라미터는 무시된다. 실측: 슬라이스 4칸 + 통합 3칸(⏳ CI).
- [x] **AC-4** — 모든 필터가 비어 있는 호출이 PostgreSQL 에서 실패하지 않는다. 실측: 통합 `everySearch_allNullFilters_…`(⏳ CI).
- [x] **AC-5** — 잘못된 UUID 는 `400 VALIDATION_ERROR` 이고 저장소를 부르지 않는다. 실측: 슬라이스 `list_malformedUuid_returns400`.

# Related Specs

- `specs/services/admin-service/architecture.md`(Layered · CQRS 읽기 모델) · `domain-model.md § 5`(`*Ref` 행 모양).

# Related Contracts

- `specs/contracts/http/admin-service-api.md` § 1.7 — **이 티켓이 고친다**(추가 파라미터만, 응답 모양 무변경 → 기존 소비자 호환).

# Edge Cases

| 상황 | 기대 |
|---|---|
| `q` 공백뿐 | 필터 없음(전체) |
| `q=WH_01` | `WH_01` 만, `WHX01` 은 아님 |
| `skus?warehouseId=…` | `warehouseId` 무시, 200 |
| `partners?partnerType=` (빈 값) | 유형 필터 없음 |
| `locations?warehouseId=WH01` | 400 `VALIDATION_ERROR` |

# Failure Scenarios

1. LIKE 패턴을 JPQL `CONCAT` 으로 만든다 → 필터 없는 첫 호출이 PostgreSQL 42P18 로 500(TASK-BE-331 재발). 통합 시험 «전 필터 null» 칸이 문다.
2. `_` 를 이스케이프하지 않는다 → `WH_01` 검색에 `WHX01` 이 섞인다. 통합 시험이 문다.
3. 빈 `IN ()` 을 바인딩한다 → PostgreSQL 문법 오류. 자리표시자 + 플래그, 통합 시험의 «유형 필터 없음» 대조군이 문다.
4. 시험이 상태코드만 본다 → 이 결함(200 인데 필터 무시)을 못 본다. 슬라이스 시험은 저장소가 받은 값을 단언한다.

# Implementation Notes

- `./gradlew :projects:wms-platform:apps:admin-service:test --tests MasterRefControllerSliceTest --tests RefSearchTest` → BUILD SUCCESSFUL, JUnit XML: 슬라이스 **9/9** · 단위 **5/5**. 통합 시험 클래스는 `compileTestJava` 로 컴파일만 확인.
- **bite**: 체크포인트 커밋 뒤 컨트롤러에서 `q` 를 버리게(`like = null`, 수정 전 동작) 하자 슬라이스 **9 중 2 실패**(검색어를 보내는 두 칸) → `git checkout` 으로 복원, 트리 깨끗.
- 계약의 «잘못된 UUID = 400 VALIDATION_ERROR» 는 `GlobalExceptionHandler` 의 `MethodArgumentTypeMismatchException` 처리를 읽고 적었다. 오류 봉투는 `{"error":{"code":…}}` 라 시험 경로는 `$.error.code`.
- 저장소에 원래 있던 `findByWarehouseId` · `findBySkuId` 는 컨트롤러가 한 번도 안 불렀다(죽은 메서드) — 이 티켓 범위 밖이라 남겼다.

---

## CORRECTION

(2026-10-13 UTC, close chore) 위 Status 의 «AC-2~4 의 실제 PostgreSQL 칸은 CI 통합 잡이 잰다» 는 **더 이상 남은 일이 아니다** — 아래가 지금의 사실이다. 위의 기록은 그대로 둔다.

- **4차원 검증**: (a) #4313 `MERGED` · 머지 커밋 `8338dda8a` (b) `origin/main` 조상 (c) 머지 시점 실패 check **0**(16 pass — `Integration (master-service + notification-service + outbound-service, Testcontainers)` 포함) (d) AC-1~5 닫힘.
- **AC-2~4 실측(CI)**: 첫 CI 런에서 통합 시험 7칸 중 `warehouse_q_matchesCodeOrNameCaseInsensitively` **1칸 실패** — 원인은 쿼리가 아니라 **시험 데이터**: `"WH-OTHER-" + UUID` 가 45자라 `warehouse_code VARCHAR(40)` 에 안 들어갔고, JPA 가 다음 쿼리(`search`) 직전에 flush 하면서 그 줄에서 `DataException`. 나머지 6칸(전 필터 null · `_` 문자 그대로 · status · 창고 범위 · SKU 범위 · 거래처 유형)은 그 런에서 이미 통과. 코드를 8자로 줄인 커밋 뒤 재런에서 통합 잡 **pass**(7/7).
- ⚪ 데모 반영: wms admin-service 는 구운 AMI 에서 돈다 ⇒ 이 변경은 **다음 재굽기** 뒤에 데모에 닿는다. 그 전까지 콘솔은 `TASK-PC-FE-333` 의 클라이언트 필터로 데모 규모에서 옳다. 이 티켓의 AC 는 데모 반영을 요구하지 않는다.
