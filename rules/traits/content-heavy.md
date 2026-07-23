# Trait: content-heavy

> **Activated when**: `PROJECT.md` includes `content-heavy` in `traits:`.

---

## Scope

콘텐츠 자체가 비즈니스 자산인 서비스에 적용된다. 상품 카탈로그, 기사·포스트, 리뷰·댓글, 배너·프로모션 이미지, 동영상, 미디어 에셋 등.

"콘텐츠의 양·품질·검색성·노출 방식"이 서비스 가치의 상당 부분을 차지한다. 쓰기(CMS) 경로보다 읽기(소비) 경로의 품질·속도·다변화가 결정적이다.

적용 범위:
- **필수 적용**: Product, Article, Post, Review, Banner, Media 같은 콘텐츠 aggregate
- **조건부**: 검색/자동완성 서비스 (콘텐츠 인덱싱이 지배적)
- **제외**: 순수 트랜잭션 서비스 (Order, Payment 등 — `transactional` trait만으로 충분)

전형적으로 `read-heavy` trait와 함께 선언된다.

---

## Mandatory Rules

### C1. 본문은 **언어·버전 관리** 기본

콘텐츠 텍스트는 `locale` + `version` 필드를 기본으로 가진다:
- `locale`: BCP 47 (`ko-KR`, `en-US`, …)
- `version`: 점증 정수 또는 semantic versioning
- 최신 버전만 노출하는 게 기본이지만, 과거 버전 아카이브는 보존
- 다국어 미지원 서비스라도 `locale: default`로 스키마는 남긴다 (나중 확장 대비)

### C2. 미디어는 **원본 ↔ 파생본 분리**

업로드된 원본 이미지/비디오와 변환된 파생본 (썸네일, 리사이즈, 트랜스코딩)은 별도 저장.

- 원본: 객체 스토리지 (S3/GCS) + 변경 불가 (immutable)
- 파생본: CDN에 푸시 또는 요청 시 동적 생성
- URL 규칙: 원본 경로에서 파생본 경로를 결정적으로 파생 (`product/123/original.jpg` → `product/123/thumb_300x300.jpg`)
- 파생본 캐시 무효화는 원본 버전 기반 (ETag, 해시 suffix)

원본 저장·버킷 네이밍·presigned URL 업로드 플로우·라이프사이클 규칙은 [`platform/object-storage-policy.md`](../../platform/object-storage-policy.md) 를 따른다.

### C3. 콘텐츠 인덱싱은 **쓰기 모델과 분리된 read projection**

원본 스키마(정규화, ACID, 수정 로그 적합)와 소비용 스키마(denormalize, search-friendly, 조회 빠름)를 분리. 업데이트는 이벤트 기반으로 전파.

- 쓰기 모델: OLTP DB (Postgres 등)
- 읽기 모델: 검색 엔진 (Elasticsearch, OpenSearch), 캐시 레이어, CDN-ready JSON
- 동기화: `content.updated` / `content.deleted` 이벤트 → read projection consumer
- 일관성: eventual — 최대 지연 SLA 명시 (권장 5~30s)
- 읽기 모델이 downtime 되더라도 쓰기는 계속 가능 (데이터 유실 없음; 재처리 가능)

### C4. 노출 정책 (publish / unpublish / schedule) 명시

콘텐츠는 단순 CRUD가 아니라 **노출 상태**를 가진다:

- `DRAFT`: 작성 중, 공개 API에 노출 안 됨
- `PUBLISHED`: 공개 노출
- `UNPUBLISHED`: 공개 노출 중단 (작성자/관리자가 다시 `PUBLISHED`로 전환 가능)
- `ARCHIVED`: 과거 기록, 소급 조회 시만 노출 (검색엔진 크롤러 제외)
- `SCHEDULED`: 미래 시점에 자동으로 `PUBLISHED` (cron 또는 publish 스케줄러)

전이 규칙은 콘텐츠 유형별 state machine으로 문서화. 비허용 전이 시도 → 422.

### C5. 운영자·작성자 권한 구분 + 편집 이력

콘텐츠 작성자와 관리자의 권한을 구분:

- 작성자: 본인 콘텐츠의 CRUD, 노출 상태는 `DRAFT`↔`PUBLISHED` 정도만
- 모더레이터/관리자: 모든 콘텐츠의 `UNPUBLISHED`/`ARCHIVED` 전환, 숨김 처리
- 편집 이력은 필수 보관 — 누가 언제 어떤 필드를 바꿨는지 (audit trail)
- `PUBLISHED`로 전환되는 순간 이력은 immutable (수정 가능해도 이력은 추가만 됨)

### C6. 검색·필터·정렬은 read-heavy와 공유 (cross-reference)

상품 검색, 기사 검색 등의 구체적 요구사항은 [`read-heavy`](read-heavy.md) trait의 R1 (Pagination), R3 (Caching), R5 (Search infrastructure) 참조. 중복 규정하지 않고 참조로 통합한다.

---

## Forbidden Patterns

- 원본과 파생본을 같은 path / 버킷에 섞음 — 캐시 무효화 복잡해지고, 원본 손상 리스크
- 카탈로그 쓰기 모델에서 검색 쿼리 직접 수행 — LIKE 기반 검색이 DB를 태움
- 공개 API에서 `DRAFT` 콘텐츠도 리턴 — 미완성 콘텐츠 노출 사고
- 편집 이력 보존 안 함 — 분쟁 발생 시 입증 불가
- 미디어 URL을 랜덤 UUID로 생성 — CDN 캐시 활용 어려움; 결정적 경로 권장

---

## Required Artifacts

content-heavy trait 이 활성화된 프로젝트는 다음 산출물을 **필수**로 갖춘다:

1. **콘텐츠 노출 상태 다이어그램** — `DRAFT → PUBLISHED → UNPUBLISHED / ARCHIVED / SCHEDULED` 전이 규칙 (콘텐츠 유형별 state machine) (C4). 위치: `specs/services/<service>/state-machines/content-status.md`.
2. **미디어 저장 모델** — 원본 ↔ 파생본 분리, 버킷/경로 규칙(결정적 파생 경로), 캐시 무효화(버전/해시 suffix) (C2). [../../platform/object-storage-policy.md](../../platform/object-storage-policy.md) 준수. 위치: `specs/services/<service>/media-model.md`.
3. **읽기 projection + 동기화 SLA** — 쓰기 모델(OLTP) ↔ 읽기 모델(검색 엔진/캐시/CDN JSON), `content.updated`/`content.deleted` 이벤트 전파, 최대 지연 SLA(권장 5~30s) (C3). 위치: `specs/services/<service>/read-model.md`.
4. **locale / version 스키마** — 콘텐츠 텍스트의 `locale`(BCP 47) + `version`, 과거 버전 아카이브 정책 (C1). data model 에 포함.
5. **편집 이력 / audit trail** — 누가 언제 어떤 필드를 바꿨는지, `PUBLISHED` 이후 이력 immutability, 작성자 ↔ 모더레이터 권한 구분 (C5). 위치: `specs/services/<service>/data-model.md` 또는 `knowledge/architecture/content-audit.md`.

---

## Interaction with Common Rules

- [../../platform/object-storage-policy.md](../../platform/object-storage-policy.md) 의 버킷 네이밍·presigned URL 업로드·라이프사이클 규칙이 C2 (미디어 원본/파생본) 에 직접 적용된다.
- [../../platform/error-handling.md](../../platform/error-handling.md) 의 `STATE_TRANSITION_INVALID` (422) 를 C4 노출 상태 비허용 전이에, `STORAGE_UNAVAILABLE`/`MEDIA_NOT_FOUND`/`MEDIA_VALIDATION_FAILED` 를 미디어 경로에 사용한다.
- [read-heavy.md](read-heavy.md) **(함께 선언 권장)**: 검색·필터·정렬·페이지네이션·캐시 요구사항은 R1 (Pagination)·R3 (Caching)·R5 (Search infrastructure) 를 참조로 통합한다 (C6 — 중복 규정하지 않는다).
- [../../platform/architecture.md](../../platform/architecture.md) 의 서비스 경계 원칙에 따라 읽기 projection(C3)이 쓰기 모델과 분리된 소비 표면으로 존재한다.

---

## Checklist (Review Gate)

- [ ] 콘텐츠 텍스트가 `locale` + `version` 을 기본 스키마로 갖고 과거 버전을 보존하는가? (C1)
- [ ] 미디어 원본(immutable 객체 스토리지) ↔ 파생본(CDN/동적)이 분리되고 파생 경로가 결정적인가? (C2)
- [ ] 콘텐츠 인덱싱이 쓰기 모델과 분리된 read projection 이고 동기화 SLA 가 명시되며 읽기 모델 downtime 이 쓰기를 막지 않는가? (C3)
- [ ] 노출 상태(`DRAFT`/`PUBLISHED`/`UNPUBLISHED`/`ARCHIVED`/`SCHEDULED`)가 state machine 으로 문서화되고 비허용 전이가 422 로 거부되는가? (C4)
- [ ] 작성자 ↔ 모더레이터 권한이 구분되고 편집 이력이 보관되며 `PUBLISHED` 이후 이력이 immutable(추가만)인가? (C5)
- [ ] 검색·필터·정렬이 read-heavy R1/R3/R5 를 참조로 통합되어 있는가? (C6)
- [ ] 노출 상태 다이어그램·미디어 저장 모델·읽기 projection SLA·locale/version 스키마·편집 이력 문서가 존재하는가?
- [ ] 금지 패턴(원본/파생본 혼합, 쓰기 모델 직접 검색, `DRAFT` 공개 노출, 이력 미보존)이 존재하지 않는가?
