# Trait: read-heavy

> **Activated when**: `PROJECT.md` includes `read-heavy` in `traits:`.

---

## Scope

조회 트래픽이 쓰기 트래픽을 수십 배 이상 압도하는 서비스에 적용된다. 상품 상세, 검색, 피드, 타임라인, 프로필, 리포트 조회 등.

"읽기 성능이 서비스 생존을 결정한다"는 인식 하에, 캐시 레이어·읽기 복제본·페이지네이션·검색 인프라가 설계의 중심이 된다.

적용 범위:
- **필수 적용**: 공개 API 중 조회 엔드포인트 (GET, 검색, 필터링, 페이지네이션)
- **조건부**: 관리자 대시보드 조회 (트래픽은 낮지만 big query가 많음)
- **제외**: 순수 쓰기 엔드포인트 (POST/PATCH/DELETE)

전형적으로 `content-heavy` trait와 함께 선언된다.

---

## Mandatory Rules

### R1. Pagination은 **cursor-based 우선**

리스트 조회 API는 대규모 데이터 대비:

- **우선**: cursor-based (opaque token) — `GET /items?cursor=...&limit=20`
  - 응답: `{ items: [...], nextCursor: "...", hasMore: true }`
  - cursor 인코딩: `(lastId, sortKey)` base64 또는 JWT-signed
  - 고성능, 깊은 페이지에도 일정 성능
- **허용**: offset-based (`?page=1&size=20`) — 작은 데이터셋, 관리자 화면, SEO 필요 시
- **금지**: 전체 데이터 반환 (`GET /items` 페이지네이션 없음)

`limit` 기본값 20, 최대 100. 초과 시 400 `VALIDATION_ERROR`.

### R2. 응답에 **ETag + Cache-Control**

조회 응답은 캐시 가능성을 명시:

- `ETag`: 리소스의 버전 해시. 클라이언트 `If-None-Match` → 304
- `Cache-Control`:
  - Public 콘텐츠: `public, max-age=300, stale-while-revalidate=60`
  - Private/인증 필요: `private, max-age=60`
  - 실시간: `no-store`
- `Last-Modified`: 보조, ETag가 없을 때만

### R3. 캐시 레이어 **명시적으로** (Redis / Memcached / CDN)

단순 "DB 조회"를 기본 가정하지 않는다:

- **뜨거운 데이터** (상품 상세, 인기 검색어 등): Redis / Memcached 인메모리 캐시, TTL 1~10분
- **정적 자산** (이미지, CSS, JS): CDN, TTL 1시간~30일
- **조회 쿼리 결과** (검색, 리스트): 선택적 — 키는 쿼리 전체 (정규화 필수)
- 캐시 무효화 전략 명시: TTL 기반 / 이벤트 기반 / 수동 flush API
- **stampede 방지**: probabilistic early expiration 또는 distributed lock

### R4. DB 복제본 활용 허용

읽기 전용 쿼리는 primary DB에서 분리 가능:

- Read replica로 라우팅 — replication lag 허용 (단, tolerance 명시 필요)
- 트랜잭션 필요한 경로는 primary로 (read-your-own-write)
- Hibernate / JPA 레벨에서는 `@Transactional(readOnly = true)` 선언

복제 지연이 문제되는 경로 (checkout, 결제 직후 등)는 primary 강제.

### R5. 검색·필터·정렬은 전용 인프라로

RDB의 `WHERE LIKE '%...%'` + `ORDER BY` 조합으로는 스케일 한계. 조건:

- 전문 검색: Elasticsearch / OpenSearch / Meilisearch
- 자동완성: 별도 prefix index 또는 Redis sorted set
- 복합 필터 (카테고리 × 가격 × 브랜드 등): 인덱스 설계 또는 검색 엔진
- 정렬 옵션은 사전 정의된 필드만 허용 (SQL injection + index 관리)
- 최대 결과 수 하드 리밋 (예: 10000건 넘으면 "refine your search" 메시지)

### R6. N+1 방지 **필수**

ORM 사용 시 N+1 쿼리 금지:

- JPA: fetch join, `@EntityGraph`, DTO projection
- 리스트 응답은 단일 쿼리 또는 한정된 쿼리 집합 (최대 2~3개)
- 관측: 개발 환경에서 SQL 로그 확인, CI에서 N+1 감지 플러그인 활용 (선택)
- API 문서에 "this endpoint issues N queries" 주석 남기기 (unavoidable 한 경우)

---

## Overrides

해당 없음. common rule과 충돌 없음.

---

## Anti-patterns

- Offset-based pagination을 수백만 건 테이블에 사용 — 깊은 페이지에서 O(N) 스캔
- 캐시 레이어 생략 + "DB 인덱스로 해결" — 트래픽 증가 시 DB 병목
- 캐시 TTL 없이 영구 저장 — stale 데이터 지속, 무효화 누락
- Read replica로 결제 확인 경로 조회 — lag으로 "결제했는데 주문 조회 안 됨" 사고
- SQL `LIKE '%keyword%'`로 검색 — 인덱스 활용 불가
- N+1 쿼리 방치 — 리스트 응답이 DB 커넥션 풀을 고갈
