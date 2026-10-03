# Task ID

TASK-MONO-749

# Title

`ADR-MONO-079` D3 — 상품 **`collection_ref`**(= 팬 아티스트 id) · 공개 스냅숏에 싣기 · 팬 아티스트 페이지가 그것으로 굿즈를 고른다

# Status

done

# Owner

monorepo

# Task Tags

- ecommerce
- fan-platform
- public-data

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (두 프로젝트 + 공개 스냅숏 생성기)

---

# Dependency Markers

- **선행**: `ADR-MONO-079` ACCEPTED — A
- **후속**: `TASK-MONO-739`(팬 굿즈 시드 — 이 필드 위에서 시드한다, 739 의 R4 예명 접두어 규칙을 대체)

# Goal

상품에 `collection_ref VARCHAR(64) NULL`(인덱스 `(tenant_id, collection_ref)`)을 두고, 값은 팬 아티스트 id(라이더 R1 기본값)다. 공개 스냅숏(`@demo/public-data` store)에 그 필드를 싣고, 팬 아티스트 페이지는 `collection_ref = <그 아티스트 id>` 인 상품만 카드로 보인다 — 익명 방문 «백엔드 호출 0» 불변식 유지(ADR-077).

# Scope

## In Scope

- product-service: 마이그레이션(postgres · h2 둘 다 — ADR-077 D2 의 시드 경로) · 도메인 · 상품 생성/수정 API 의 선택 필드 · 공개 상품 응답에 노출
- 콘솔 상품 편집 화면의 입력 칸(선택)
- 공개 스냅숏 생성기(`infra/demo/public-data`)가 필드를 싣는다
- 팬 웹: 아티스트 페이지가 스냅숏에서 그 아티스트의 굿즈를 고른다(739 가 카드 UI 를 만든다면 이 티켓은 선택 함수와 시험까지)
- 계약: 필드 의미(«팬 아티스트 id, FK 없음»)

## Out of Scope

- 굿즈 시드 데이터(`TASK-MONO-739`) · 그룹/소속사 단위 컬렉션(R1 밖)

# Acceptance Criteria

- [x] **AC-1** — `collection_ref` 가 있는 상품만 그 아티스트 페이지에 보이고, 다른 아티스트의 굿즈는 보이지 않는다(대조군). → `artist-goods.test.ts`(A↔B 양방향 · NULL 상품은 어느 페이지에도 없음) · bite 로 빨강 확인(§ 구현 기록)
- [x] **AC-2** — 필드가 없는 기존 상품은 무변경(NULL) — 스토어 화면 회귀 없음. → 백필 없음 · h2 시드 전부 NULL(`CollectionRefMigrationTwinTest`) · 등록/수정 대조군 · `store.json` 24개 전부 `collectionRef: null` · web-store `tsc` 0. ⚪ web-store vitest 는 이 호스트에서 기동 불가 → CI `web-store` 잡이 판정
- [x] **AC-3** — 공개 스냅숏 생성기 시험이 필드를 싣는다(스냅숏 원장/가드가 있으면 갱신). → `public-data.test.mjs` 2칸 추가 · `store.json` 생성기로 재생성(`--check` 가 물었다) · 상품 API 키 집합 원장(`ProductApiContractTest`) 갱신
- [x] **AC-4** — 팬 아티스트 페이지가 백엔드 호출 0 을 유지한다(기존 가드). → `public-pages.test.tsx`(fetch 0회) 포함 팬 웹 38파일 331 통과 · `check-client-graph-*` rc=0. 🔵 선택 함수는 인자만 읽는 순수 함수다(카드 렌더는 739)

# Related Specs

- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D3
- `docs/adr/ADR-MONO-077-fan-goods-sold-by-the-ecommerce-store.md` D1 · D2 · D5(D)

# Related Contracts

- product-service 상품 API · 공개 스냅숏 스키마

# Edge Cases

- 아티스트가 보관(ARCHIVED)되면 그 굿즈 카드는 팬에 안 보인다(팬이 보관 아티스트를 안 보이므로) — 상품은 스토어에 그대로.

# Failure Scenarios

1. postgres 만 바꾸고 h2(standalone)를 빠뜨려 데모/로컬 한쪽이 깨진다.

---

# 구현 기록 (2026-10-02 UTC)

## 무엇을 바꿨나

**이름(티켓·ADR 대조용)**: 컬럼 `products.collection_ref VARCHAR(64) NULL` · 인덱스 `idx_products_tenant_collection_ref (tenant_id, collection_ref)`(postgres) · API/JSON 필드 `collectionRef` · 공개 DTO `PublicProduct.collectionRef: string | null` · 팬 선택 함수 `artistGoods(fan, products, artistId)`.

1. **계약 먼저** — `projects/ecommerce-microservices-platform/specs/contracts/http/product-api.md`: 공개/관리 목록·상세 응답, `POST`/`PATCH /api/admin/products` 요청에 `collectionRef` + 새 절 «collectionRef — fan artist collection»(팬 아티스트 id · FK 없음 · NULL = 컬렉션 없음 · PATCH 의미 = 부재/null 무변경, `""` 지우기 · 최대 64자 · 공개 필드인 이유). 공개 스냅숏 스키마의 정본 `infra/demo/public-data/src/datasets.ts` `PublicProduct.collectionRef` 에 같은 의미를 적었다. 콘솔 계약 § 2.4.10 은 «producer 필드는 재정의하지 않는다» 이므로 손대지 않았다.
2. **마이그레이션 두 트리** — postgres `db/migration/V20__add_product_collection_ref.sql`(컬럼 + `(tenant_id, collection_ref)` 인덱스) · h2 `db/migration-h2/V13__add_product_collection_ref.sql`(컬럼 + `(collection_ref)` 인덱스). 🔵 h2 인덱스가 다른 것은 정상이다 — h2 트리는 `products.tenant_id` 를 만든 적이 없다(V12 헤더와 같은 사정). 백필 없음.
3. **product-service** — `Product.collectionRef` + `updateCollectionRef`(trim · 공백=null · 64자 초과 거부) · `reconstitute` 12인자 오버로드(옛 11인자는 null 로 위임) · JPA 매핑 · 등록/수정 명령·요청 DTO(`@Size(max = 64)` → 400 `VALIDATION_ERROR`) · `UpdateProductService`(null=무변경) · `RegisterProductService` · 목록/상세 응답 DTO. 모든 옛 생성자는 그대로 두었다(호출부 무수정).
4. **콘솔** — `product-types.ts` 의 목록/상세/등록/수정 스키마에 `collectionRef`(🔴 Zod 객체는 모르는 키를 **조용히 버린다** — 스키마에 안 넣으면 프록시에서 사라진다) · `ProductForm` 「팬 아티스트 컬렉션」 입력(선택, 64자 검증) · 수정 시 값을 비우면 `""` 를 보낸다(`collectionRefForUpdate` — 썸네일처럼 `undefined` 를 보내면 옛 아티스트가 조용히 남는다).
5. **공개 스냅숏** — `toPublicProduct` 가 `collectionRef: strOrNull(raw.collectionRef)` 를 싣는다(🔴 `searchText` 에는 안 섞는다) → `node bin/build-bundled-snapshots.mjs` 로 `store.json` 재생성(24개 전부 `null`, 손으로 안 고침).
6. **팬 웹** — `features/public-browse/lib/select.ts` `artistGoods`: `fan.artists` 에 없는 id(보관·비공개)면 빈 배열, 아니면 `collectionRef === artistId`. 배럴에서 내보낸다. **카드 UI 는 `TASK-MONO-739`**(티켓 Scope 그대로).
7. **web-store 시험 픽스처 3곳** — `PublicProduct` 가 필드를 요구하게 되어 리터럴에 `collectionRef: null` 추가(동작 변경 0).
8. **`TASK-MONO-739` 에 한 줄** — 마이그레이션 번호가 `V21`/`V14` 로 밀렸다는 것과 `artistGoods` 를 쓰라는 것(§ 편차).

## 시험

- product-service: `ProductTest` +4 · `UpdateProductServiceTest` +1(설정 / 부재=무변경 대조군 / `""`=지우기) · `RegisterProductServiceTest` +1(값 / 부재=null 대조군) · `ProductControllerSliceTest` +4(목록·상세 노출 + NULL 대조군, 등록 전달, PATCH 65자 400 · `""` 전달) · `ProductApiContractTest` 키 집합 3곳 갱신 · **새 `CollectionRefMigrationTwinTest`**(Docker 없이 h2 트리를 **실제 적용**해 `information_schema` 로 `VARCHAR(64)`·nullable 확인 + 시드 상품 전부 NULL + 두 트리에 컬럼 추가 마이그레이션이 정확히 하나씩) · `ProductRepositoryIntegrationTest` +1(postgres V20 왕복 · NULL 대조군 · 지우기 · `pg_indexes` 로 인덱스 모양).
- public-data: `public-data.test.mjs` +2(실림/부재=null/빈 문자열=null/searchText 비오염, 번들 `store.json` 전 상품에 키).
- 콘솔: `ProductForm.test.tsx` +4 · `ecommerce-products-proxy.test.ts` +1.
- 팬: 새 `artist-goods.test.ts` 5칸(비공허성 · A↔B 양방향 대조군 · NULL 상품 · 보관 아티스트 고아 · 예명≠id).

## 로컬 판정

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:ecommerce-microservices-platform:apps:product-service:test` | 0 | XML 집계 **395 tests / 0 failures / 0 errors / 0 skipped** |
| `node --test tests/public-data.test.mjs` (infra/demo/public-data) | 0 | 45/45 |
| `node bin/build-bundled-snapshots.mjs --check` | 0 | (재생성 전에는 rc=1 «store.json 드리프트» — 가드가 물었다) |
| console-web `npx vitest run` | 0 | **325 files / 3639 tests** |
| console-web `npx tsc --noEmit` · `npx next lint` | 0 · 0 | |
| fan-platform-web `npx vitest run` | 0 | **38 files / 331 tests**(`public-pages.test.tsx` fetch-0 포함) |
| fan-platform-web `npx tsc --noEmit` · `npx next lint --dir src` | 0 · 0 | |
| web-store `npx tsc --noEmit` | 0 | |
| `check-seed-catalogue-parity.sh` · `check-flyway-version-collision.sh` · `check-flyway-unresolvable-placeholder.sh` · `check-dev-seed-migration-band.sh` · `check-public-domains.sh` · `check-service-type-drift.sh` · `check-package-script-targets.sh` | 전부 0 | |
| `check-client-graph-backend-origins.mjs` · `check-client-graph-server-only.mjs` · `check-stage-inherits-its-contexts.mjs` | 전부 0 | |

**bite** — ① 팬 선택 함수의 술어를 `collectionRef !== null` 로 바꾸자 `artist-goods.test.ts` **3 failed / 2 passed**(AC-1 양방향 · 보관 고아 · 예명≠id) → 복원 후 5/5. ② `toPublicProduct` 에서 `collectionRef` 줄을 지우자 `public-data.test.mjs` **44/45**(AC-3 칸 빨강) + `--check` rc=1(드리프트) → 복원 후 45/45 · rc=0. ③ (부수) 응답 DTO 에 필드를 더하자 기존 `ProductApiContractTest` 키 집합 원장이 걸리는 자리라 함께 갱신했다 — 이 원장은 `Object.keys` 류라 필드명 grep 으로는 안 보인다.

**⚪ 못 잰 것**
- `ProductRepositoryIntegrationTest`(Testcontainers postgres — V20 적용 · 인덱스) — 이 호스트의 Docker 데몬이 꺼져 있다(`docker info` → `dockerDesktopLinuxEngine` 파이프 없음). CI `ecommerce-integration-tests` 잡이 판정한다. 🔵 h2 절반은 Docker 없이 위 쌍둥이 시험이 실제로 적용해 쟀다.
- **기존 볼륨**(V19 까지 적용된 postgres)에 V20 적용 — 못 쟀다. V20 은 `ADD COLUMN`(nullable, 기본값 없음) + `CREATE INDEX` 뿐이라 데이터 의존이 없다.
- web-store vitest — 이 호스트 Node 24 에서 vitest 4 가 기동하지 않는다(`ERR_PACKAGE_IMPORT_NOT_DEFINED #module-evaluator`). `tsc` 만 쟀고 시험은 CI 가 판정한다.
- 데모 실 백엔드 — 재굽기 전까지 데모 DB 에는 컬럼이 없다(739 와 같은 사정, 재굽기는 소유자 작업). 공개 열람은 번들 저장본이라 영향 없다.

## 편차

- **마이그레이션 번호**: `ADR-MONO-077` D2 와 `TASK-MONO-739` 가 `V20__seed_artist_goods`/`V13__seed_artist_goods` 를 예약해 두었는데, ADR-079 가 749 를 739 의 **선행**으로 두었으므로 749 가 다음 빈 번호 `V20`/`V13` 을 썼다. 739 는 `V21`/`V14` 로 쓴다 — 739 티켓에 적었다. ACCEPTED ADR 본문은 고치지 않았다(파일명은 D2 의 결정이 아니라 예시이고, 결정 «새 마이그레이션 · 적용된 V8/V19 수정 금지» 는 그대로 지켜진다).
- **h2 인덱스 모양**: 티켓의 `(tenant_id, collection_ref)` 는 postgres 에만 있다 — h2 트리에 `products.tenant_id` 가 없어서다(기존 드리프트, 이 티켓 범위 밖).
- **이벤트 페이로드**(`ProductCreated`/`ProductUpdated`)에는 싣지 않았다 — 소비자가 없고 티켓 범위(API · 공개 스냅숏)에 없다.
- **팬 아티스트 페이지 렌더**: 이 티켓은 선택 함수와 시험까지다(카드는 739 — 티켓 Scope 의 단서 그대로).

---

## CORRECTION (2026-10-03 UTC) — 4차원 종결

- (a) #4115 `MERGED` · (b) 스쿼시 `a8eb3d9c7` 가 `origin/main` 에 포함 · (c) 머지 시점 rollup 실패 0/68.
- (d) AC-1~4 전부 본문 근거로 닫힘(체크 4/4). ⚪ 이던 Testcontainers(`ProductRepositoryIntegrationTest` V20 왕복)는 CI `Integration (ecommerce A/B/C)` 가 실제로 돌아 SUCCESS — 건너뜀 아님.
