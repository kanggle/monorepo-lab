# Task ID

TASK-PC-FE-082

# Title

ADR-MONO-031 Phase 1b(image) — console-web `features/ecommerce-ops` **product image** 흡수: ecommerce 상품 이미지 운영(목록/presigned 업로드/등록/수정/삭제)을 platform-console 상품 상세 화면 안으로 가져온다. console-web Route Handler → ecommerce gateway 직접 호출(ADR-017 D2.A, BFF write leg 없음), 도메인-facing IAM OIDC 토큰. presigned 업로드는 클라이언트가 ① upload-url 프록시 호출 → ② **브라우저→S3 직접 PUT(XHR 진행률)** → ③ register 프록시 호출. products(PC-FE-081)/orders(PC-FE-083) 패턴 미러. **이게 Phase 1b 마지막 facet → 완료 시 Phase 2(users) 진입.**

# Status

review

# Owner

frontend

# Task Tags

- platform-console
- console-integration
- adr-mono-031
- ecommerce
- console-web

---

# Dependency Markers

- **선행 (prerequisite, done)**:
  - `TASK-MONO-252` — ADR-031 ACCEPTED + 계약 § 2.4.10 (product/order CRUD 바인딩, **image #10-14 포함**). 본 태스크가 그 계약의 consumer-side(**image #10-14**) 구현.
  - `TASK-BE-366` — ecommerce 백엔드 `AdminProductImageController` operator-plane authz(`validateAdminRole` 제거, gateway `AccountTypeEnforcementFilter`+`TenantClaimValidator`+`WHERE tenant_id` 위임). **본 흡수의 차단 해소** — 콘솔 OPERATOR 가 이미지 list/upload-url/register/update/delete 호출 가능.
  - `TASK-PC-FE-081` — products 흡수(`features/ecommerce-ops` 골격: `_proxy.ts`, `products-api.ts` 의 `callEcommerce`/`parseEcommerceError` 패턴, eligibility, sidebar ecommerce 그룹, `ProductDetail`). 본 태스크가 그 product 상세에 **이미지 관리 섹션**을 임베드(같은 feature, 같은 auth/credential 모델 재사용).
- **근거 (ADR/계약)**: [ADR-MONO-031](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md) Phase 1b; `console-integration-contract.md` § 2.4.10 (#10 list / #11 presigned upload url / #12 register / #13 update / #14 delete).
- **선례 (미러)**: 본 feature 의 products/orders 슬라이스(`*-api.ts` 의 단일 하드닝 호출부, `_proxy.ts` mapper, eligibility, `force-dynamic` page). image 는 동형 + presigned XHR 업로드 플로우 추가.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (presigned 멀티스텝 XHR 직접 S3 업로드 + 진행률/에러 처리로 단순 미러보다 복잡; 나머지 CRUD 는 확립 패턴).

---

# Goal

ecommerce 상품 이미지 운영을 platform-console 상품 상세 화면 안에서 수행할 수 있게 한다(admin-dashboard 이미지 관리 화면의 콘솔 등가물). console-web 이 ecommerce gateway 의 product-image admin API(`/api/admin/products/{id}/images/**`)를 **Route Handler 로 직접 호출**(ADR-017 D2.A)하고, 도메인-facing IAM OIDC 토큰(`getDomainFacingToken()`)으로 인증한다(tenant scope 는 JWT claim 권위, X-Tenant-Id 헤더 없음). 범위 = 이미지 **목록 / presigned 업로드(파일 선택→S3 직접 PUT→등록) / sortOrder·isPrimary 수정 / 삭제**. presigned 업로드 URL 로의 실제 바이트 PUT 은 **브라우저→S3 직접**(presign 이 곧 인가; OIDC 토큰 미첨부) — 콘솔 서버를 경유하지 않는다(presigned URL 설계 목적). 모든 mutation 은 confirm-gate(삭제) 또는 명시 액션이며 producer 거부를 actionable inline 으로 표면화한다.

# Scope

**platform-console console-web 내부만**. ecommerce 백엔드 0-change(BE-366 으로 이미 operator-plane). BFF 0-change(write leg 없음, ADR-017 D2.A). products/orders 슬라이스 파일은 **`_proxy.ts`(image body 스키마 re-export 추가) + `ProductDetail.tsx`(이미지 섹션 임베드 1줄) + `index.ts`(image export 추가)만 additive 수정**; 나머지 image 파일은 신규.

## In scope

### A. feature (`src/features/ecommerce-ops/`)
- `api/image-types.ts` — Zod 스키마 + TS 타입. producer DTO 정합(아래 § 부록 형상): `ImageItemSchema`(imageId/objectKey/sortOrder/isPrimary/url/uploadedAt, `.passthrough()`), `ImageListSchema`(`{images:[]}`), `PresignedUrlResponseSchema`(uploadUrl/objectKey/expiresAt), `RegisterImageResponseSchema`. write body: `PresignedUrlBodySchema`(contentType min1 / contentLength int positive), `RegisterImageBodySchema`(objectKey min1 / sortOrder int>=0 / isPrimary bool), `UpdateImageBodySchema`(sortOrder int>=0 optional / isPrimary bool optional, refine ≥1 필드). 업로드 제약 상수(`IMAGE_ALLOWED_CONTENT_TYPES`, `IMAGE_MAX_BYTES`).
- `api/images-api.ts` — server 전용 ecommerce gateway 클라이언트(list/upload-url/register/update/delete). base URL `ECOMMERCE_ADMIN_BASE_URL`, path `/products/{id}/images{,/upload-url,/{imageId}}`. 도메인-facing 토큰. `products-api.ts`/`orders-api.ts` 와 **동형 단일 하드닝 호출부**를 image-api 안에 재현(products-api 미수정). flat 에러 봉투, 401/403→ApiError·404/400/422/409→ApiError inline·503/timeout/network→EcommerceUnavailableError.
- `hooks/use-ecommerce-images.ts` — TanStack Query: `useImages(productId)`(admin list #10) + `useCreateUploadUrl`/`useRegisterImage`/`useUpdateImage`/`useDeleteImage` mutation, `invalidateQueries`(images + product detail). presigned 바이트 PUT 헬퍼 `uploadToPresignedUrl(uploadUrl, file, onProgress)`(XHR, 진행률, 비-2xx→에러) — apiClient 우회(직접 S3 cross-origin).
- `components/ImageManager.tsx` — 상품 상세 임베드 섹션: 이미지 목록(썸네일·primary 배지·sortOrder), 파일 선택→업로드(진행률 바)→자동 register, set-primary, sortOrder 인라인 변경, 삭제(confirm). `ImageUploadField.tsx`(파일 picker + 진행률 + content-type/크기 클라이언트 검증) 분리로 가독성 확보. 기존 `ConfirmDialog` 재사용.
- `index.ts` — image export 추가(`ImageManager`, image 타입). 기존 products/orders export 유지.

### B. Route Handlers (`src/app/api/ecommerce/products/[id]/images/`)
- `route.ts` — `GET`(list #10) + `POST`(register #12). `runtime='nodejs'`, register 는 Zod `RegisterImageBodySchema` parse.
- `upload-url/route.ts` — `POST`(presigned url #11). Zod `PresignedUrlBodySchema` parse. **응답의 uploadUrl 은 그대로 클라이언트에 반환**(클라이언트가 S3 로 직접 PUT). 콘솔 서버는 바이트를 프록시하지 않는다.
- `[imageId]/route.ts` — `PATCH`(update #13, Zod `UpdateImageBodySchema`) + `DELETE`(delete #14, 204).
- 공유 `mapEcommerceError`/`badRequest`/`tryParse`/`newRequestId` 는 `_proxy.ts` 에서 import. image body 스키마는 `_proxy.ts` 에 re-export 추가.

### C. ProductDetail 임베드 (`src/features/ecommerce-ops/components/ProductDetail.tsx`)
- 변형 에디터/재고 섹션 옆에 `<ImageManager productId={data.id} />` 섹션 1개 추가(additive). 별도 라우트/사이드바 leaf **불필요**(이미지는 상품 상세 하위).

## Out of scope (후속)
- users/promotions/shippings/notifications — Phase 2~5(백엔드 tenant_id 선행).
- admin-dashboard 삭제 — Phase 6.
- 이미지 크롭/리사이즈/멀티 동시 업로드 큐 — 운영 화면 v1 은 단건 순차 업로드.
- S3/MinIO presign 서버 구현 — producer(BE) 소유, 본 태스크는 계약 소비만.

# Acceptance Criteria

- **AC-1**: 상품 상세에 이미지 관리 섹션 렌더 — admin list(`GET /admin/products/{id}/images`)로 이미지 목록(썸네일·primary·sortOrder) 표시. ecommerce 일시 장애 시 섹션만 degrade(상세 셸/변형/재고 섹션 유지).
- **AC-2**: presigned 업로드 3-스텝 동작 — ① `POST /api/ecommerce/products/{id}/images/upload-url`(프록시)로 `{uploadUrl,objectKey,expiresAt}` 획득 → ② **브라우저가 uploadUrl 로 직접 PUT**(파일 바이트 + Content-Type, XHR 진행률; OIDC 토큰/쿠키 미첨부) → ③ `POST /api/ecommerce/products/{id}/images`(프록시)로 `{objectKey,sortOrder,isPrimary}` register. 성공 시 목록 invalidate.
- **AC-3**: 수정(sortOrder/isPrimary, `PATCH .../{imageId}`) + 삭제(`DELETE .../{imageId}`, 204) 동작. 삭제 confirm-gated. producer 거부(404 IMAGE_NOT_FOUND, 400/422 VALIDATION, 409)를 actionable inline 표면화(crash 금지).
- **AC-4**: 모든 서버 호출이 Route Handler → ecommerce gateway 직접. 도메인-facing OIDC 토큰, `getOperatorToken()` 미사용(테스트로 핀), X-Tenant-Id 헤더 미부착(JWT claim 권위), Idempotency-Key 미부착(producer 미정의). presigned 바이트 PUT 은 콘솔 서버 미경유(직접 S3).
- **AC-5**: BFF 0-change; ecommerce 백엔드 0-change; **products/orders 슬라이스는 `_proxy.ts`(image 스키마 re-export)+`ProductDetail.tsx`(섹션 임베드)+`index.ts`(export) additive 외 0-change**.
- **AC-6 (빌드)**: console-web `pnpm lint` + `tsc --noEmit` + `vitest run` GREEN. 신규 단위 테스트(images-api: getDomainFacingToken 사용·getOperatorToken 미호출 핀·X-Tenant-Id 부재·flat 에러 매핑·list/register/update/delete path; images-proxy: body 검증·에러 매핑; presigned 플로우: upload-url→XHR PUT(mock)→register 순서 + 비-2xx PUT 에러 처리; types: passthrough 관용).
- **AC-7**: § 3 IAM-parity matrix 불변(additive domain scope, 카운트 16 유지). 계약 § 2.4.10 본문 추가 변경 없음(이미 #10-14 바인딩 존재).

# Related Specs / Code

- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 (#10 list / #11 upload-url / #12 register / #13 update / #14 delete).
- 선례(미러, 같은 feature): `src/features/ecommerce-ops/api/{products-api,orders-api,types}.ts`, `src/app/api/ecommerce/products/**`, `src/app/(console)/ecommerce/products/[id]/page.tsx`(상세 호스트).
- 백엔드(소비, 0-change): ecommerce `AdminProductImageController` `/api/admin/products/{id}/images/**`(BE-366 operator-plane), DTO `PresignedUrl{Request,Response}` / `RegisterImage{Request,Response}` / `UpdateImageRequest` / `ImageResponse` / `ImageListResponse`, `MediaUrlResolver`, product-service `GlobalExceptionHandler`(flat 봉투).

# Related Contracts

- `console-integration-contract.md` § 2.4.10 (#10-14 image endpoints). 신규 백엔드 계약 0(기존 admin API 소비).

# Edge Cases / Failure Scenarios

- **presigned PUT 메서드/CORS**: presigned URL 은 표준 S3 PUT(메서드 상수화). 브라우저→S3 cross-origin → CORS 는 producer/S3 설정 책임(본 태스크 범위 밖). PUT 비-2xx 시 register 단계 진입 금지 + inline 에러.
- **credential 누출**: 바이트 PUT 에 OIDC 토큰/쿠키/Authorization 절대 미첨부(presign 이 인가). upload-url/register/update/delete 프록시만 도메인-facing 토큰.
- **credential 혼용**: `getOperatorToken()` 사용 시 ADR-017 D4 위반 → `getDomainFacingToken()`. 테스트로 핀.
- **BFF write leg 유혹**: 이미지 mutation 을 BFF 로 라우팅하면 ADR-017 D2.A 위반 → Route Handler 직접.
- **고아 objectKey**: upload-url 만 받고 PUT 실패/중단 → register 미호출 → S3 에 미참조 객체 잔존 가능(producer GC 책임; 콘솔은 PUT 성공 후에만 register). register 실패 시 사용자에게 재시도 안내.
- **content-type/크기**: 클라이언트 1차 검증(`IMAGE_ALLOWED_CONTENT_TYPES`/`IMAGE_MAX_BYTES`) + producer 2차 검증(`@Positive contentLength`, `@NotBlank contentType`) 400. 클라이언트 검증은 UX 보조이지 신뢰경계 아님.
- **primary 단일성**: isPrimary=true 등록/수정 시 producer 가 기존 primary 해제(서버 권위) — 클라이언트는 낙관 갱신 대신 invalidate 후 재조회.
- **tenant 누출**: X-Tenant-Id 헤더 수동 부착 금지(JWT claim 권위) — image 는 product tenant_id 격리(BE side, BE-366).
- **producer DTO 불일치**: read `.passthrough()` 관용으로 미래 필드 무시; throw 금지.
- **eligibility/degrade**: 상품 상세가 이미 eligibility 게이트 통과한 뒤이므로 이미지 섹션은 그 안에서 동작; 이미지 list 일시 장애가 상세 전체를 깨면 안 됨(섹션만 degrade).

# Notes

## 부록 — producer DTO 형상(소비, 검증완료 2026-06-14, `AdminProductImageController` + DTO 직접 확인)

- **#10 list** `GET /admin/products/{id}/images` → `ImageListResponse`:
  `{ images: [{ imageId, objectKey, sortOrder(int), isPrimary(bool), url, uploadedAt(string) }] }`
- **#11 upload-url** `POST /admin/products/{id}/images/upload-url` body `PresignedUrlRequest { contentType(@NotBlank), contentLength(@Positive, long) }` → `PresignedUrlResponse { uploadUrl, objectKey, expiresAt(Instant) }`
- **#12 register** `POST /admin/products/{id}/images` body `RegisterImageRequest { objectKey(@NotBlank), sortOrder(@Min 0, int), isPrimary(bool) }` → **201** `RegisterImageResponse { imageId, objectKey, sortOrder, isPrimary, url, uploadedAt(Instant) }`
- **#13 update** `PATCH /admin/products/{id}/images/{imageId}` body `UpdateImageRequest { sortOrder(Integer, nullable), isPrimary(Boolean, nullable) }` → 200 `ImageResponse`
- **#14 delete** `DELETE /admin/products/{id}/images/{imageId}` → **204** No Content
- **에러 봉투**: flat `{ code, message, timestamp }`(product-service 공유 `ErrorResponse`, products 와 동일). presigned PUT(S3) 응답은 이 봉투가 아님(S3 XML/빈 바디) — 비-2xx 만 판정.
- **presigned 업로드**: `MediaUrlResolver`/`generateUploadUrl(productId, contentType, contentLength)` → `PresignedUploadResult{uploadUrl, objectKey, expiresAt}`. 바이트는 브라우저→`uploadUrl` 직접 PUT.

## 운영 메모

- 격리 worktree `wt-pc-fe-082`(브랜치 `task/pc-fe-082-ecommerce-ops-image-console-absorption`, base=origin/main `fdd076f2e`). 메인 체크아웃(다른 세션 pc-fe-079 점유) 미접촉.
- ⚠️ 동시 세션 활발. 머지 직전 root/platform-console `tasks/` PC-FE 최대값 재확인.
- 머지 후 close-chore: review→done Status 만, narrative 는 커밋. 3-dim 검증.
- ⚠️ 검증 node_modules junction 정리 순서: worktree 제거 **전** junction 단독 제거(`cmd /c rmdir "<wt>/.../node_modules"` `/s` 없이) → LinkType 빈값 + main node_modules 건재 확인 → 그 다음 `git worktree remove`. (memory `env_console_web_local_verify_needs_lint` catastrophic 함정.)
