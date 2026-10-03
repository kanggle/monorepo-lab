# Task ID

TASK-MONO-748

# Title

`ADR-MONO-079` D1·D2 — 팬 **소속사 엔티티**(artist-service) · 아티스트/그룹 소속 · 소속사 ↔ 스토어 셀러 연결(0..1)

# Status

done

# Owner

monorepo

# Task Tags

- fan-platform
- data-migration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (자유 텍스트 → 엔티티 이전 · 다른 프로젝트 셀러 검증)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A (2026-10-02)
- **후속**: `TASK-MONO-750`(운영자 관리 경로) · `TASK-MONO-751`(콘솔 화면)

# Goal

ADR-079 D1·D2 를 구현한다. 지금 소속사는 `artists.agency` · `artist_groups.agency` 의 자유 텍스트다. 이를 artist-service 의 엔티티 `agencies` 로 만들고, 아티스트·그룹이 `agency_id` 로 소속하게 하며, 소속사가 굿즈를 파는 스토어 셀러를 0..1 개 가리키게 한다(라이더 R2 기본값).

# Scope

## In Scope

- 마이그레이션: `agencies(id, tenant_id, name, status, store_seller_id NULL, created_at, updated_at, version)` · `UNIQUE(tenant_id, name)` · `artists.agency_id` / `artist_groups.agency_id`(NULL 허용 FK)
- 이전: 기존 자유 텍스트 값이 같은 것끼리 소속사 행 하나로 묶고 `agency_id` 채움(공백·대소문자 정규화 규칙을 정해 적는다). 자유 텍스트 컬럼은 이번에 지우지 않는다
- 소속사 CRUD API(관리 경로 — 권한은 `TASK-MONO-750` 이 여는 운영자 경로를 쓴다; 그 전에는 지금의 관리 역할 게이트 그대로)
- 셀러 연결 쓰기 때 스토어 셀러 존재·상태 검증(없음 · `CLOSED` → 거절) — 프로젝트 간 조회 경로는 계약을 먼저 쓴다
- 공개 읽기(아티스트 상세의 소속사 이름) — 표시를 소속사 이름에서
- 데모 시드(`seed-fan.sh`)와 공개 스냅숏(`infra/demo/public-data`)의 소속사 반영

## Out of Scope

- 콘솔 화면(`TASK-MONO-751`) · 운영자 assume 경로(`TASK-MONO-750`) · 자유 텍스트 컬럼 제거(별도)

# Acceptance Criteria

- [ ] **AC-1** — 기존 볼륨 이전 시험: 자유 텍스트가 같은 아티스트·그룹이 같은 소속사 행을 가리키고, 비어 있으면 NULL. — `AgencyMigrationExistingVolumeIT` 작성. ⚪ 로컬 미실행 — CI integrationTest 가 첫 실행.
- [x] **AC-2** — 소속사 CRUD · 중복 이름 409 · 소속 변경 시험. — `AgencyServiceTest`(Crud 7 · Affiliation 6) · `AgencyControllerSliceTest` 초록(통합 `AgencyApiIntegrationTest` 는 CI).
- [ ] ~~**AC-3** — 셀러 연결: 존재하는 ACTIVE 셀러 → 저장 · 없는/`CLOSED` 셀러 → 거절 · 셀러 조회 실패 → 저장 안 함(fail-closed).~~ → **`TASK-MONO-759` 로 분리** (소유자 결정 2026-10-02 «분리 후 진행»). 규칙·포트·fail-closed 어댑터는 이 티켓에 있고, 실제 조회 경로만 759.
- [x] **AC-4** — 공개 페이지의 소속사 표시가 그대로 보인다(회귀 없음). — `AgencyDisplayTest` · `fan.json` 드리프트 0 · public-data 시험 초록.

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D1 · D2
- `projects/fan-platform/specs/services/artist-service/`

# Related Contracts

- artist-service HTTP 계약(소속사 API 추가) · 셀러 조회 계약(프로젝트 간)

# Edge Cases

- 같은 소속사를 다르게 쓴 자유 텍스트(«SM» / «SM Entertainment») — 자동 병합하지 않는다(정규화 규칙 밖은 별도 행).
- 소속사 없는 아티스트(솔로 무소속).

# Failure Scenarios

1. 이전이 일부 행만 채워 소속사 표시가 사라진다.
2. 셀러 조회 장애 때 검증 없이 저장해 존재하지 않는 셀러를 가리킨다.

---

# 구현 기록 (2026-10-02 UTC · 분석=Opus 5.5 / 구현=Opus 5.5)

> 🔵 **2026-10-02 정정 — 소유자 결정 «분리 후 진행»**: 끝난 부분을 이 티켓으로 머지하고, 셀러 조회의 실제 전송(AC-3)은 **`TASK-MONO-759`** 로 분리했다(⏳ 소유자의 전송 선택 대기). 그래서 이 티켓은 `review` 로 간다. 아래 「부분 완료」 문단은 분리 전 기록(보존).
>
> 🔴 **부분 완료 — `in-progress` 에 남긴다.** 소속사 엔티티 · 이전 · CRUD · 소속 변경 · 표시 · 시드 · 스냅숏은 끝났다.
> **셀러 조회의 프로젝트 간 전송(fan → store)만 Hard Stop** 이다(아래 § Hard Stop). 검증 **의미**(무엇을 받고 무엇을 거절하나)는
> 포트에 대고 구현·시험했고, 배포되는 어댑터는 «검증 불가» 로만 답한다 ⇒ 셀러 **연결은 전부 503 으로 거절**되고 **아무것도 저장되지 않는다**(fail-closed). 해제(null)는 된다.

## 무엇을 바꿨나

**계약·스펙 먼저** (`projects/fan-platform/specs/`)
- `contracts/http/artist-api.md` — § Agencies 신설(CRUD · 이름 정규화 규칙 · `agency` 필드의 의미 · 셀러 연결 표 · **프로젝트 간 조회의 호출자 쪽 계약**), 아티스트/그룹 `agencyId` 필드, `PATCH /api/artists/{id}/agency` · `PATCH /api/artist-groups/{id}/agency`, 오류 코드 6개, 게이트웨이 경로 표.
- `services/artist-service/data-model.md` (§ `agencies` · § V4 이전 규칙) · `architecture.md` (패키지 · 실패 모드) · `dependencies.md` (store 셀러 조회 = NOT WIRED) · `contracts/events/artist-events.md` (`changedFields: ["agencyId"]`).
- 🔵 **ecommerce 쪽 계약(`product-api.md`)은 손대지 않았다** — 거기 들어갈 내부 읽기 엔드포인트의 인증 방식이 바로 Hard Stop 의 결정이다.

**마이그레이션** — `apps/artist-service/src/main/resources/db/migration/artist/V4__agencies.sql`
- `agencies(id, tenant_id, name, status, store_seller_id NULL, created_at, updated_at, version)` · `uq_agencies_tenant_name UNIQUE(tenant_id, name)` · `uq_agencies_tenant_id_id UNIQUE(tenant_id, id)` · `ck_agencies_status (ACTIVE|ARCHIVED)`.
- `artists.agency_id` / `artist_groups.agency_id` VARCHAR(36) NULL + **복합 FK** `fk_artists_agency` / `fk_artist_groups_agency` = `(tenant_id, agency_id) → agencies(tenant_id, id)` (DB 가 남의 테넌트 소속사를 거부) + 인덱스 `idx_*_tenant_agency`.
- 이전: 두 테이블의 자유 텍스트를 정규화 키로 UNION → 테넌트별 소속사 1행 → `agency_id` 채움. 마지막 `DO` 블록이 «비어 있지 않은 텍스트인데 `agency_id` 가 NULL» 인 행이 하나라도 있으면 **RAISE**(Failure Scenario 1 을 부팅 실패로 만든다). 자유 텍스트 컬럼은 바이트 그대로 둔다.

**정규화 규칙** (V4 SQL 과 `Agency.normalizeName` 이 **같은** 규칙)
- ASCII 공백 `[ \t\n\r\f\v]` 연속 → 공백 하나, 앞뒤 공백 제거, **대소문자는 보존·구별**. 남는 게 없으면(`NULL`·`''`·`'   '`) → `agency_id NULL`.
- 대소문자를 접지 않은 이유: 이전은 사람이 쓰지 않은 동치를 만들면 안 된다(«SM»/«SM Entertainment» 를 안 합치는 것과 같은 이유). 접으면 표시 이름을 **하나 골라야** 해서 일부 행이 보던 철자가 조용히 바뀐다. 공백은 화면에서 안 보이므로 접어도 독자가 본 것이 안 바뀐다.

**artist-service 코드**
- 도메인: `domain/agency/{Agency, AgencyId, AgencyStatus}` · `Artist`/`ArtistGroup` 에 `agencyId` + `changeAgency(id, name)` (이름을 자유 텍스트에도 미러 — 해제 때 옛 텍스트가 폴백으로 되살아나지 않게).
- 포트/서비스: `AgencyRepository` · `StoreSellerDirectory`(outbound) · `ManageAgencyUseCase` · `LinkAgencyStoreSellerUseCase` · `ChangeAgencyAffiliationUseCase` · `AgencyService` · `AgencySupport`. 기존 `ArtistManagementService` / `ArtistDirectoryService` / `ArtistGroupService` 는 등록·생성 시 `agencyId` 를 받고, 모든 읽기에서 **소속사 이름을 엔티티에서** 읽는다(디렉터리는 페이지당 1회 배치, 소속 없는 페이지는 0회).
- 웹: `AgencyController` — `POST /api/agencies` · `GET /api/agencies` · `GET /api/agencies/{id}` · `PATCH /api/agencies/{id}` (rename) · `PATCH /api/agencies/{id}/status` (ARCHIVED) · `PATCH /api/agencies/{id}/store-seller`; `PATCH /api/artists/{id}/agency` · `PATCH /api/artist-groups/{id}/agency`. 오류: `AGENCY_NOT_FOUND` 404 · `AGENCY_NAME_CONFLICT` 409 · `AGENCY_ARCHIVED` 422 · `STORE_SELLER_NOT_FOUND` 422 · `STORE_SELLER_CLOSED` 422 · `STORE_SELLER_LOOKUP_UNAVAILABLE` 503.
- 권한: `SecurityConfig` 에 `/api/agencies/**` 를 **기존 `ADMIN_ROLES` 게이트 그대로** 추가(새 역할·새 경로 없음 — 운영자 경로는 750). 읽기는 인증된 테넌트 구성원.
- 셀러 검증: `ACTIVE`·`SUSPENDED`·`PENDING_PROVISIONING` → 저장 · 없음 → 422 · `CLOSED` → 422 · 그 밖의 모든 것(예외·null·모르는 상태) → 503, **저장 안 함**. `SUSPENDED` 를 받는 근거: ADR-079 D2 가 거절하는 것은 정확히 «없거나 CLOSED» 이고 정지는 되돌릴 수 있다.
- 배포 어댑터 `adapter/out/store/UnwiredStoreSellerDirectory` — 항상 «검증 불가». 🔴 허용 스텁으로 바꾸면 그것이 곧 Failure Scenario 2 다.
- 이벤트: 아티스트 소속 변경 → `artist.updated.v1` `changedFields: ["agencyId"]`(그룹은 갱신 이벤트가 없어 미발행).

**게이트웨이** — `apps/gateway-service/src/main/resources/application.yml` 에 `artist-service-agencies` (`/api/v1/agencies/**` → `/api/agencies/**`).

**데모 시드 · 공개 스냅숏**
- `infra/demo/seed/seed-fan.sh` — 소속사 2행(`Aurora Entertainment` · `Nova Sound`, 새 볼륨용 고정 id `…c001`/`…c002`)을 **이름으로** `WHERE NOT EXISTS`, 아티스트 6 · 그룹 1 은 `agency_id` 를 **이름 서브쿼리**로 채운다(기존 볼륨에서는 V4 가 무작위 id 로 이미 만들었으므로 id 로 찾으면 어긋난다). 스토어 셀러 연결은 시드하지 않는다(데모 굿즈의 셀러는 스토어 `default` 이고 이 소속사의 셀러가 아니다).
- `infra/demo/public-data/fixtures/raw-backend-responses.mjs` — 백엔드의 새 모양(`agencyId`)을 픽스처에 반영. 변환기 허용 목록은 그대로(`agency` 이름만 공개) ⇒ `fan.json` **드리프트 0**(생성기 `--check`). 새 시험: `agencyId` 비공개 + 양성 대조군 + 표시 이름 불변.
- artist-service 에는 dev 시드(R__/V seed)가 없다(마이그레이션 디렉터리는 V1~V4 뿐) — 확인했다.

## 테스트

- 단위/슬라이스(신규 59): `AgencyTest` 17(정규화 규칙 · «SM»≠«SM Entertainment» · 대소문자 구별 · 불변식) · `AgencyServiceTest` 24(AC-2 CRUD 7 · 소속 변경 6 · **AC-3 셀러 11**) · `AgencyDisplayTest` 7(AC-4 엔티티 이름 우선 · 무소속 폴백 · 디렉터리 배치 · 등록 시 소속 · 배포 어댑터 fail-closed) · `AgencyControllerSliceTest` 11(201/403/409/422/503 봉투).
- 통합(`@Tag("integration")`, Testcontainers): `AgencyMigrationExistingVolumeIT` 3 — **AC-1 기존 볼륨**(V3 까지 적용 → 자유 텍스트 12행 기록 → V4: 공백 변형 3개 = 한 행 · 그룹도 같은 행 · 대소문자/«SM» 분리 · 빈 값 3종 NULL · 타 테넌트 분리 · 남은 행 0 · 자유 텍스트 보존) · 복합 FK 의 교차 테넌트 거부 · 새 DB. `AgencyApiIntegrationTest` 3 — CRUD·409(공백 변형 포함)·FAN 403 · 소속 → 상세 표시(AC-4) · 이름 변경 추종 · 이동/해제 · 미존재 404 · **AC-3 실 스키마**(ACTIVE 저장 · CLOSED/없음 422 · 조회 실패 503, 세 경우 모두 저장값 불변).

## 로컬 판정 (rc · 개수)

| 명령 | 결과 |
|---|---|
| `./gradlew :projects:fan-platform:apps:artist-service:test` | rc=0 · **228 tests, 0 fail, 0 skip** (전 169 + 신규 59) |
| `./gradlew :projects:fan-platform:apps:gateway-service:test` | rc=0 · 48 tests, 0 fail |
| `./gradlew :projects:fan-platform:apps:artist-service:check` | rc=0 |
| `./gradlew :projects:fan-platform:apps:artist-service:integrationTest --tests '*Agency*'` | rc=0 · **6 SKIPPED** ⚪ |
| `node --test infra/demo/public-data/tests/public-data.test.mjs` | rc=0 · 48 pass / 0 fail |
| `node infra/demo/public-data/bin/build-bundled-snapshots.mjs --check` | rc=0 · `fan.json` · `store.json` 드리프트 없음 |
| `bash -n infra/demo/seed/seed-fan.sh` | rc=0 |

- **bite**: `AgencyService.verifySeller` 의 `catch (StoreSellerLookupUnavailableException e) { throw e; }` 를 `return;`(fail-open)로 바꿈 → 주입 확인(grep 1) → `AgencyServiceTest` rc=1, 정확히 `🔴 lookup failure → 503 … NOT saved` 1건 FAILED → 원본을 **복사로** 되돌림(grep `BITE` 0) → 전체 228 재실행 초록.
- ⚪ **Testcontainers 6건 미측정** — 이 호스트에서 Docker 데몬이 꺼져 있다(`docker info` → `dockerDesktopLinuxEngine` 파이프 없음). `@Testcontainers(disabledWithoutDocker = true)` 로 SKIPPED. **AC-1 의 기존 볼륨 시험과 V4 SQL 자체(정규식 클래스 · `gen_random_uuid()` · DO 블록)는 로컬에서 한 번도 실행되지 않았다** — CI `artist-service:integrationTest` 레인이 판정한다.
- ⚪ 시드 SQL 의 실제 실행(데모 Postgres)도 미측정 — `bash -n` 만.

## Hard Stop — 셀러 조회의 프로젝트 간 전송

```
[VIOLATION] HARDSTOP-09: Task `TASK-MONO-748` requires an architecture decision (cross-service contract — fan artist-service → ecommerce store seller lookup: transport + authentication) that is not documented in `projects/fan-platform/specs/services/artist-service/architecture.md` or any ADR (ADR-MONO-079 D2 says «값 검증은 쓰기 때 스토어의 셀러 조회로» but not how fan reaches the store).
[WHY] There is no existing pattern to reuse: (1) the store's seller data lives in ecommerce product-service, which validates no JWT at all — it trusts the ecommerce gateway's X-Tenant-Id / X-User-Role headers, and its only seller read is the operator-plane GET /api/admin/sellers/{sellerId} (X-User-Role == ECOMMERCE_OPERATOR); product-service has no /internal/** surface. (2) artist-service holds no IdP client (it is a resource server only; the only fan workload client is community-service-client, whose scope/tenant are its own). (3) A workload token for the store tenant needs a NEW client_credentials registration (auth-service Flyway seed) + a seller-read scope + an assume-tenant entry in WorkloadTenantCatalog — an IdP registration and a permission-catalog change, which TASK-MONO-726 needed explicit owner approval for. Inventing any of these here would decide cross-project auth implicitly.
[REMEDIATION] Choose one:
  1. Owner decides the transport (e.g. "artist-service-client cc registration + `store.seller.read` scope + assume-tenant `ecommerce` + a product-service `/internal/sellers/{id}` read with its own JWT chain", or "go through the ecommerce gateway with a workload role"), recorded as an ADR-079 rider or a new ADR; then a follow-up ticket wires `StoreSellerDirectory` with an HTTP adapter (the caller-side contract is already in artist-api.md § Store seller verification).
  2. Accept a non-live source for verification (e.g. the store public snapshot) — rejected here by default because it cannot see CLOSED sellers reliably and is not a write-time lookup.
  3. Keep the shipped fail-closed adapter (every link → 503) until (1) lands — this is the current state.
[REFERENCE] CLAUDE.md § Layer Rules + platform/architecture-decision-rule.md · ADR-MONO-079 D2 · projects/ecommerce-microservices-platform/apps/product-service/.../AdminSellerController.java · projects/iam-platform/apps/auth-service/.../WorkloadTenantCatalog.java
```

## AC 상태

- [ ] **AC-1** — 시험 작성 완료(`AgencyMigrationExistingVolumeIT`). ⚪ 로컬 미실행 — CI integrationTest 가 첫 실행(작성≠통과, CI 초록을 보고 닫는다).
- [x] **AC-2** — CRUD · 중복 409(정규화 후) · 소속 변경: 단위·슬라이스 초록, 통합 시험 작성(⚪ CI).
- [ ] **AC-3 → `TASK-MONO-759` 로 분리** (소유자 결정 2026-10-02 «분리 후 진행»). 규칙·포트·fail-closed 어댑터는 이 티켓에 있고, 실제 조회 경로만 759. 🔴 **운영 동작(머지 후)**: `PATCH /api/agencies/{id}/store-seller` 에 값을 주면 **항상 503 `STORE_SELLER_LOOKUP_UNAVAILABLE`, 저장 0** · `null`(해제)은 200. — 분리 전 기록: 검증 의미(ACTIVE 저장 · 없음/CLOSED 거절 · 조회 실패 시 저장 안 함)는 포트 기준 초록 + bite. 🔴 **실제 스토어에 묻는 경로가 없다** — Hard Stop 해소 전에는 «존재하는 ACTIVE 셀러 → 저장» 이 운영에서 성립하지 않는다(503).
- [x] **AC-4** — 표시 키·값 불변(`fan.json` 드리프트 0, 단위 시험), 통합 시험 작성(⚪ CI).

---

## CORRECTION (2026-10-03 UTC) — 4차원 종결

- (a) #4121 `MERGED` · (b) 스쿼시 `ccec68c71` 가 `origin/main` 에 포함 · (c) 머지 시점 rollup 실패 0/69.
- (d) AC-2·4 본문 근거로 닫힘. **AC-1 정정**: 본문의 ⚪(«CI integrationTest 가 첫 실행») 는 CI 에서 해소됐다 — `Integration (fan-platform)` SUCCESS, 잡 로그에 `Successfully applied 3 migrations` → `Successfully applied 1 migration` 쌍이 두 번(14:32:44→45, 14:32:48) — `AgencyMigrationExistingVolumeIT` 의 «V3 까지 → V4 만» 모양이다(V4 `agencies` 적용 줄 4회). 선택은 이름이 아니라 `@Tag("integration")` 이고 그 클래스에 태그가 있다.
- **AC-3**: 소유자 결정(2026-10-02 «분리 후 진행»)으로 `TASK-MONO-759` 로 넘어갔다 — 이 티켓을 닫아도 의무는 759 에 산다(ready, 경로 결정 게이트).
- 같은 PR 에 CI `Error code registry` 빨강을 고친 커밋(`b9453007c`, 코드 6개 등록)이 포함돼 머지됐다 — 머지 시점 rollup 실패 0.
