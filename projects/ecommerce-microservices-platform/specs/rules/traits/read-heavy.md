# Trait: read-heavy

> **Activated when**: `PROJECT.md` includes `read-heavy` in `traits:`.

---

## Scope

읽기(조회) 트래픽이 쓰기의 수십~수백 배에 달하고, 읽기 성능이 사용자 경험을 직접 좌우하는 서비스에 적용된다.

ecommerce-microservices-platform 기준 적용 범위:

- 필수: [apps/product-service/](../../../apps/product-service/), [apps/search-service/](../../../apps/search-service/), [apps/review-service/](../../../apps/review-service/), [apps/web-store/](../../../apps/web-store/) (상품 조회·검색 경로)
- 조건부: [apps/promotion-service/](../../../apps/promotion-service/) (프로모션 조회 많은 경우), [apps/user-service/](../../../apps/user-service/) (프로필 조회)
- 제외: 순수 쓰기 경로(결제 commit, 주문 생성 자체)

---

## Mandatory Rules

### R1. 리스트 엔드포인트는 반드시 페이지네이션
모든 목록 반환 API는 `page`, `size`(또는 `cursor`, `limit`) 파라미터를 요구하며, 응답은 `{ content, page, size, totalElements }` 포맷을 따른다. `LIMIT` 없는 전체 조회 금지. 기본 size 상한은 100.

### R2. 커서 기반 페이지네이션 우선
대규모 데이터(10만건 이상 예상) 또는 실시간 변하는 리스트는 offset 페이지네이션 대신 **커서 기반(cursor)** 을 사용한다. offset은 깊은 페이지에서 성능이 급락하고 중복·누락 문제가 있다.

### R3. 읽기 전용 복제본(Read Replica) 활용
조회 쿼리는 가능한 한 **읽기 복제본**으로 라우팅한다. 단, 트랜잭션 직후 read-your-writes가 필요한 경로는 예외 — 이 경우 primary로 명시적 라우팅.

### R4. 다계층 캐시 전략 필수
읽기 경로는 다음 계층 중 최소 2개를 가진다:

- CDN (정적/준정적 콘텐츠, 공개 API 응답)
- API Gateway 또는 애플리케이션 캐시 (Redis 등)
- DB 쿼리 캐시 또는 머터리얼라이즈드 뷰

각 계층의 TTL과 무효화 트리거는 문서화되어야 한다.

### R5. 캐시 무효화는 이벤트 기반
데이터 변경 시 관련 캐시 엔트리의 invalidation은 **도메인 이벤트 또는 CDC(Change Data Capture)** 로 트리거한다. TTL만 의존하는 설계는 stale data 윈도우가 커지므로 단독 사용 금지.

### R6. N+1 쿼리 금지
서비스 레이어에서 리스트를 반환할 때 연관 데이터 로딩은 배치(batch fetch) 또는 조인으로 처리한다. ORM의 lazy loading으로 인한 N+1 쿼리는 코드 리뷰에서 차단.

### R7. 인덱스 없는 WHERE 조건 금지
대용량 테이블에 대한 쿼리는 WHERE/ORDER BY/GROUP BY 조건 전부가 인덱스로 커버되어야 한다. 인덱스 부재 시 쿼리는 EXPLAIN 검토 후에만 허용.

### R8. 응답 크기 제한
단일 응답은 일반 API 기준 최대 1MB, 상한 초과 시 필드 선택(sparse fieldsets) 또는 분할 반환으로 축소한다. 불필요한 큰 필드(긴 본문, 임베디드 리스트)는 별도 엔드포인트로 분리.

### R9. 읽기 레이턴시 SLO 명시
읽기 경로는 서비스 수준 목표(SLO)를 명시한다. 예: "p95 < 200ms, p99 < 500ms". 이 목표가 메트릭으로 측정되고 알림이 설정되어야 한다.

---

## Forbidden Patterns

- ❌ **페이지네이션 없는 리스트 반환**
- ❌ **Offset 페이지네이션 + 실시간 변하는 리스트 조합**
- ❌ **ORM lazy loading으로 인한 N+1 쿼리**
- ❌ **TTL만으로 캐시 일관성 유지** (이벤트 기반 무효화 없음)
- ❌ **전체 컬럼 SELECT *** 대용량 테이블 대상
- ❌ **인덱스 힌트 없는 full table scan 허용**
- ❌ **읽기 경로에서 동기적 외부 API 호출** (캐싱 없이)

---

## Required Artifacts

1. **페이지네이션 규약 문서** — offset vs cursor 선택 기준, 상한. 위치: `specs/contracts/http/` 또는 서비스별
2. **캐시 전략 문서** — 계층·TTL·무효화 트리거. 위치: `specs/services/<service>/cache-strategy.md`
3. **인덱스 설계 문서** — 대용량 테이블의 인덱스 목록과 쿼리 매핑. DB migration 스크립트와 동기
4. **SLO 선언** — 읽기 엔드포인트별 p95/p99 목표. 위치: `specs/services/<service>/observability.md`
5. **읽기 복제본 라우팅 규칙** — primary vs replica 라우팅 조건. 코드 레벨 또는 설정 파일

---

## Interaction with Common Rules

- [../../platform/testing-strategy.md](../../platform/testing-strategy.md)의 Performance/Load 테스트 레이어가 **선택**이 아닌 **필수**가 된다. 주요 읽기 엔드포인트는 로드 테스트 스크립트를 보유해야 한다 (참고: [load-tests/](../../../load-tests/)).
- [../../platform/observability.md](../../platform/observability.md)에 다음 메트릭을 추가: 엔드포인트별 p50/p95/p99, 캐시 hit rate, DB replica lag, 쿼리 실행 시간 분포.
- [../../platform/error-handling.md](../../platform/error-handling.md)의 `INVALID_PAGINATION`, `PAGE_SIZE_EXCEEDED` 등 페이지네이션 관련 오류 코드를 사용.

---

## Checklist (Review Gate)

- [ ] 모든 리스트 API가 페이지네이션을 강제하는가? (R1)
- [ ] 대용량/실시간 리스트가 커서 기반인가? (R2)
- [ ] 읽기 쿼리가 replica로 라우팅되는가? (R3)
- [ ] 다계층 캐시(최소 2개)가 구성되어 있는가? (R4)
- [ ] 캐시 무효화가 이벤트 기반인가? (R5)
- [ ] N+1 쿼리가 없는가? (R6)
- [ ] 대용량 테이블의 WHERE/ORDER BY/GROUP BY가 인덱스로 커버되는가? (R7)
- [ ] 응답 크기 상한이 지켜지는가? (R8)
- [ ] 읽기 SLO가 명시되고 메트릭·알림이 설정되었는가? (R9)
- [ ] 로드 테스트 스크립트가 주요 엔드포인트에 존재하는가?
