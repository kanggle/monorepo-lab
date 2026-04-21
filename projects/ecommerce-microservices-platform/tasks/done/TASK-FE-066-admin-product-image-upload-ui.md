# Task ID

TASK-FE-066

# Title

admin-dashboard 상품 등록/수정 폼 이미지 업로드 UI — 다중 업로드, 미리보기, 순서 변경, 삭제

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

# Goal

admin-dashboard 상품 등록/수정 화면에 이미지 관리 UI를 추가한다.
TASK-BE-125에서 구현된 presigned URL 기반 이미지 업로드 API를 연동하여
어드민이 상품별로 최대 10장의 이미지를 업로드·삭제·순서 변경·대표 지정할 수 있도록 한다.

작업 완료 후:
- 상품 등록/수정 폼에 이미지 섹션이 표시된다.
- 드래그앤드롭 또는 파일 선택으로 이미지를 업로드할 수 있다.
- 업로드 진행률이 표시된다.
- 이미지 미리보기, 순서 변경(드래그), 대표 이미지 지정, 삭제가 가능하다.
- 상품 상세 페이지에서 등록된 이미지가 표시된다.

# Scope

## In Scope

- 이미지 업로드 API 클라이언트 함수 추가 (presigned URL 발급, 등록, 수정, 삭제, 목록 조회)
- `ImageUploader` 컴포넌트 — 파일 선택 / 드래그앤드롭, 업로드 진행률
- `ImageGallery` 컴포넌트 — 미리보기 그리드, 순서 변경, 대표 지정, 삭제 버튼
- `useProductImages` 훅 — 이미지 CRUD 상태 관리
- `ProductForm.tsx` 수정 — 이미지 섹션 통합
- 상품 수정 화면 — 기존 이미지 로드 + 추가/삭제
- 클라이언트 사이드 검증: 파일 타입 (jpeg/png/webp), 최대 5MB, 최대 10장
- 에러 상태 UI: 업로드 실패, 용량 초과, 타입 불일치, 10장 초과
- 로딩/빈 상태 처리
- 컴포넌트 테스트

## Out of Scope

- 이미지 크롭/리사이즈 (클라이언트 사이드)
- 이미지 최적화/WebP 변환
- web-store 고객 UI의 이미지 갤러리 (별도 태스크)
- packages/ui 공통 컴포넌트 추출 (필요 시 후속 태스크)

# Acceptance Criteria

- [ ] 상품 등록 폼에 "상품 이미지" 섹션이 표시된다
- [ ] 파일 선택 버튼 또는 드래그앤드롭으로 이미지를 업로드할 수 있다
- [ ] 업로드 중 진행률 표시 (또는 로딩 스피너)
- [ ] 업로드 완료 후 미리보기가 그리드로 표시된다
- [ ] 이미지 클릭 또는 버튼으로 대표 이미지를 지정할 수 있다
- [ ] 각 이미지에 삭제 버튼이 있고, 클릭 시 확인 후 삭제된다
- [ ] 허용되지 않은 파일 타입 선택 시 에러 메시지 표시
- [ ] 5MB 초과 파일 선택 시 에러 메시지 표시
- [ ] 10장 초과 업로드 시도 시 에러 메시지 표시
- [ ] 상품 수정 화면 진입 시 기존 이미지가 로드되어 표시된다
- [ ] 대표 이미지 변경 시 상품 목록의 thumbnailUrl이 자동 갱신된다
- [ ] API 호출 실패 시 에러 토스트 표시
- [ ] 테스트가 추가되고 통과한다

# Related Specs

> **Before reading Related Specs**: Follow `specs/platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `specs/rules/common.md` plus any `specs/rules/domains/<domain>.md` and `specs/rules/traits/<trait>.md` matching the declared classification.

- `specs/platform/object-storage-policy.md` (presigned URL 흐름, allow-list)
- `specs/services/admin-dashboard/overview.md`

# Related Skills

- `.claude/skills/frontend/nextjs-page.md`

# Related Contracts

- `specs/contracts/http/product-api.md` (이미지 엔드포인트 6개)

# Target App

- `apps/admin-dashboard`

# Implementation Notes

### Presigned URL 업로드 흐름
1. 프론트에서 `POST /api/admin/products/{id}/images/upload-url` 호출 → `{ uploadUrl, objectKey, expiresAt }` 수신
2. 프론트에서 `uploadUrl`로 직접 PUT (fetch, XMLHttpRequest for progress)
3. 프론트에서 `POST /api/admin/products/{id}/images` 호출 → `{ objectKey, sortOrder, isPrimary }` 전송
4. 서버가 HEAD 검증 후 등록 확정 → 응답으로 `{ imageId, url, objectKey, sortOrder, isPrimary, uploadedAt }`

### 컴포넌트 구조
```
features/product-management/
  components/
    ImageUploader.tsx        — 파일 선택 / DnD / 진행률
    ImageGallery.tsx         — 미리보기 그리드 / 순서 변경 / 대표 / 삭제
    ImageItem.tsx            — 개별 이미지 카드
  hooks/
    use-product-images.ts    — CRUD 상태 관리
  api/
    product-image-api.ts     — API 함수
```

### 기술 제약
- Next.js App Router (admin-dashboard)
- fetch API 기반 (api-client 패키지)
- 업로드 진행률: XMLHttpRequest 또는 fetch + ReadableStream
- 순서 변경: HTML Drag and Drop API (외부 라이브러리 없이) 또는 간단한 up/down 버튼
- 스타일: 기존 admin-dashboard의 인라인 스타일 / CSS Modules 패턴 따르기

# Edge Cases

- 네트워크 중단으로 presigned URL PUT 실패 → 에러 메시지 + 재시도 안내
- presigned URL 만료 (5분) → 재발급 후 재시도
- 동시에 여러 이미지 업로드 (병렬 처리, 개별 진행률)
- 상품 저장 전 이미지 업로드 (이미지는 독립 API이므로 상품 ID 필요 → 수정 화면에서만 업로드 가능, 신규 등록 시 상품 생성 후 이미지 추가 안내)
- 대표 이미지 삭제 후 자동 대체 (서버에서 처리, UI는 목록 새로고침)
- 브라우저 뒤로가기/페이지 이탈 시 업로드 중 경고

# Failure Scenarios

- presigned URL 발급 실패 (503 STORAGE_UNAVAILABLE) → "스토리지 서비스 연결 실패" 에러
- 이미지 등록 실패 (404 MEDIA_NOT_FOUND) → "업로드한 파일을 찾을 수 없습니다" 에러
- 이미지 등록 실패 (400 MEDIA_VALIDATION_FAILED) → "지원하지 않는 파일 형식입니다" 에러
- 이미지 삭제 실패 → 토스트 에러 + 목록 유지
- 10장 초과 (422 IMAGE_LIMIT_EXCEEDED) → "이미지는 최대 10장까지 등록할 수 있습니다"

# Test Requirements

- ImageUploader 컴포넌트 테스트 (파일 선택, 검증, 업로드 트리거)
- ImageGallery 컴포넌트 테스트 (삭제, 대표 지정)
- useProductImages 훅 테스트 (상태 관리)
- ProductForm 이미지 섹션 통합 테스트

# Definition of Done

- [ ] UI implemented
- [ ] API integration completed
- [ ] Loading/error/empty states handled
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
