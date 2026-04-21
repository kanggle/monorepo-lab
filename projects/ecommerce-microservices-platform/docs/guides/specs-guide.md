# 스펙 가이드

프로젝트 스펙 구조와 읽는 순서.

## 스펙이란?

`specs/` 디렉토리에 있는 문서로, 프로젝트의 **source of truth**. 구현이 스펙과 충돌하면 스펙이 우선.

## 디렉토리 구조

```
specs/
├── platform/          # 플랫폼 공통 정책 (모든 서비스에 적용)
├── contracts/         # API 및 이벤트 계약
│   ├── http/          # REST API 계약
│   ├── events/        # 도메인 이벤트 계약
│   └── schemas/       # 공유 스키마
├── services/          # 서비스별 스펙
│   ├── auth-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── search-service/
│   ├── user-service/
│   ├── gateway-service/
│   ├── batch-worker/
│   ├── web-store/
│   └── admin-dashboard/
├── features/          # 기능 스펙
└── use-cases/         # 유스케이스
```

## 우선순위 (높은 순)

1. `specs/platform/` — 플랫폼 정책
2. `specs/contracts/` — API/이벤트 계약
3. `specs/services/` — 서비스 아키텍처
4. `specs/features/` — 기능 스펙
5. `specs/use-cases/` — 유스케이스

하위 문서가 상위와 충돌하면 상위를 따른다.

## 주요 플랫폼 스펙

| 파일 | 내용 |
|---|---|
| `entrypoint.md` | 스펙 읽기 시작점, 읽기 순서 정의 |
| `architecture.md` | 전체 시스템 아키텍처 |
| `architecture-decision-rule.md` | 아키텍처 결정 규칙 |
| `naming-conventions.md` | 네이밍 컨벤션 |
| `error-handling.md` | 에러 처리, HTTP 상태 코드 |
| `testing-strategy.md` | 테스트 전략, 필수 테스트 범위 |
| `coding-rules.md` | 코딩 규칙 |
| `security-rules.md` | 보안 규칙 |
| `dependency-rules.md` | 의존성 규칙 |
| `shared-library-policy.md` | 공유 라이브러리 정책 |
| `event-driven-policy.md` | 이벤트 기반 정책 |
| `observability.md` | 관측 가능성 (로깅, 메트릭, 트레이싱) |
| `api-gateway-policy.md` | API 게이트웨이 정책, gateway-service 규칙 |
| `deployment-policy.md` | 빌드, 패키징, 배포 정책 |
| `glossary.md` | 프로젝트 용어 정의 |
| `ownership-rule.md` | 서비스, 계약, 공유 자산 소유권 규칙 |
| `repository-structure.md` | 모노레포 디렉토리 구조, 소유권 |
| `service-boundaries.md` | 서비스 경계, 크로스 서비스 상호작용 규칙 |
| `versioning-policy.md` | API, 이벤트, 라이브러리 버전 관리 정책 |

## 서비스별 스펙 구성

각 서비스 디렉토리에는:
- `overview.md` — 서비스 목적과 책임
- `architecture.md` — 아키텍처 패턴, 레이어 구조
- `dependencies.md` — 의존 서비스, 라이브러리
- `observability.md` — 모니터링, 알림 설정

## 읽는 순서

`specs/platform/entrypoint.md`에 정의된 순서를 따른다.

---

## 주요 플랫폼 정책 요약

각 플랫폼 스펙의 핵심 규칙을 요약한다.

---

### 아키텍처 (architecture.md)

| 서비스 | 스타일 | 역할 |
|---|---|---|
| `gateway-service` | Layered | 외부 트래픽 라우팅, JWT 검증, rate limiting |
| `auth-service` | Layered | 인증, 토큰 발급/갱신, 세션 관리 |
| `user-service` | Layered | 사용자 프로필, 배송지 관리 |
| `product-service` | DDD | 상품 카탈로그, 변형, 재고, 가격 |
| `order-service` | DDD | 주문 생명주기, 애그리게이트, 도메인 이벤트 |
| `payment-service` | Hexagonal | 결제 처리, 외부 프로바이더 연동 |
| `search-service` | Hexagonal | 상품 검색 인덱스, 검색 쿼리 API |
| `batch-worker` | Layered | 스케줄드/배치 처리 |
| `web-store` | Feature-Sliced Design | 고객용 쇼핑몰 (Next.js) |
| `admin-dashboard` | Layered by Feature | 내부 운영 대시보드 (Next.js) |

### 아키텍처 결정 규칙 (architecture-decision-rule.md)

- 모든 서비스는 `specs/services/<service>/architecture.md`에 아키텍처를 명시적으로 선언
- 선언된 아키텍처를 반드시 준수, 구현 중 암묵적 변경 금지
- 아키텍처 변경 시: spec 문서 업데이트 → ADR 기록 → 관련 문서 동기화 → 코드 변경 순서
- 선언된 아키텍처가 없으면 즉시 멈추고 블로킹 이슈 보고

### 네이밍 컨벤션 (naming-conventions.md)

- Java 클래스: PascalCase + 역할 suffix (`Controller`, `Service`, `Repository`, `Exception`, `Config` 등)
- 메서드: camelCase 동사 시작, boolean은 `is/has/exists` prefix
- API URL: `kebab-case` 경로, 컬렉션은 복수 명사
- JSON 필드: camelCase, 이벤트 엔벨로프: snake_case
- Redis 키: `{service}:{entity}:{identifier}`, 모든 키에 TTL 필수
- 태스크 ID: `TASK-{TYPE}-{NUMBER}`, 파일명: `TASK-{TYPE}-{NUMBER}-{kebab-case-title}.md`

### 에러 처리 (error-handling.md)

- 응답 형식: `{ code, message, timestamp }` 통일
- 에러 코드: `UPPER_SNAKE_CASE`, 이 문서에 먼저 등록 후 구현에 사용
- 스택 트레이스/내부 클래스명/SQL은 응답에 절대 포함 금지
- `GlobalExceptionHandler`로 모든 미처리 예외를 표준 형식으로 처리
- HTTP 상태 코드: 400/401/403/404/409/422/429/500/503 표준 매핑

### 테스트 전략 (testing-strategy.md)

- 4계층 필수: Unit → Controller Slice → Integration → Event
- H2/인메모리 대체 금지 — Testcontainers로 실제 DB/캐시 사용
- 테스트 메서드명: `{scenario}_{condition}_{expectedResult}`
- `@DisplayName`에 한국어 비즈니스 행동 기술
- BE 태스크는 Domain/Service Unit + Controller Slice + Integration 테스트 필수

### 코딩 규칙 (coding-rules.md)

- 백엔드: Java 21, 프론트엔드: TypeScript strict mode
- 빌드: Gradle / pnpm + Turborepo
- Java: records/sealed class/pattern matching 활용, 생성자 주입만 허용 (필드 주입 금지)
- 비즈니스 규칙 위반은 unchecked exception, `Exception` 직접 catch 금지
- DB: Flyway 마이그레이션, 배포된 마이그레이션 파일 수정 금지
- 도메인 엔티티 직접 노출 금지 (DTO 사용)
- 데드 코드/주석 처리 코드/TODO(태스크 ID 없는) 금지

### 보안 규칙 (security-rules.md)

- JWT는 `auth-service`만 발급, 게이트웨이가 모든 인바운드에서 검증
- 각 서비스는 자체 인가 로직 반드시 구현 (게이트웨이 인증에만 의존 금지)
- 외부 통신은 HTTPS만 허용
- 민감 데이터(비밀번호/토큰/카드번호/PII)는 로그에 절대 기록 금지
- 결제 민감 데이터는 `payment-service`만 보유

### 의존성 규칙 (dependency-rules.md)

- 서비스 간: HTTP 계약 또는 이벤트 메시징만 허용
- 내부 코드 임포트, DB 직접 접근, 순환 동기 의존 금지
- 프론트엔드는 반드시 `gateway-service`를 통해서만 백엔드 호출
- `libs/`/`packages/`는 `apps/`에 의존 불가, 순환 모듈 의존 금지

### 공유 라이브러리 정책 (shared-library-policy.md)

- 기술적 공통 유틸리티만 포함 — 서비스 특화 도메인 로직 포함 금지
- 추가 전 4가지 체크: 2개 이상 서비스 사용, 기술/공통 성격, 단일 서비스 모델 미의존, 중복 감소 vs 결합도 비교
- 도메인 소유권이 재사용 편의보다 우선

### 이벤트 기반 정책 (event-driven-policy.md)

- 이벤트는 과거 사실, 발행 후 불변
- 토픽명: `{service}.{entity}.{event}` (kebab-case)
- 엔벨로프 필수 필드: `event_id(UUID)`, `event_type`, `occurred_at`, `source`, `payload`
- 컨슈머: 멱등 처리 필수, `event_id`로 중복 제거, 모든 컨슈머 그룹에 DLQ 설정
- 재시도: 최대 3회 지수 백오프 (1s→2s→4s, 최대 30s), 즉시 DLQ 대상: 역직렬화 실패/비즈니스 규칙 위반
- 프로듀서: 트랜잭션 커밋 후 이벤트 발행 (아웃박스 패턴), DB 트랜잭션 내부 발행 금지

### API 게이트웨이 정책 (api-gateway-policy.md)

- 모든 외부 요청의 단일 진입점, 서비스 외부 직접 노출 금지
- 유효 JWT 검증 후 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더 주입
- 퍼블릭 라우트: signup, login, refresh, GET /api/products/**, GET /api/search/**, health
- Rate limit: IP당 분당 100건, 인증 엔드포인트는 IP당 분당 10건

### 관측 가능성 (observability.md)

- 3대 기둥: 구조화 로깅(JSON), 메트릭(Micrometer→Prometheus→Grafana), 트레이싱(OpenTelemetry→Jaeger)
- 로그 필수 필드: timestamp, level, service, traceId, message
- HTTP 요청에 trace 헤더(`traceparent`, `tracestate`) 전파 필수
- 알림 기준: 에러율 5% 초과(5분), P99 지연 2초 초과, 헬스체크 실패, DB 커넥션 풀 소진

### 배포 정책 (deployment-policy.md)

- Docker 이미지: `eclipse-temurin:21-jre-alpine` 기반, 태그: `{service-name}:{git-sha}`
- 환경: local → dev(develop 머지 시 자동) → staging(main 머지 시 자동) → production(수동 승인)
- 환경별 설정은 환경 변수 주입, 이미지에 설정/시크릿 번들 금지
- Kubernetes 롤링 업데이트, 헬스체크 실패 시 자동 롤백

### 서비스 경계 (service-boundaries.md)

- 각 서비스는 자신의 데이터/비즈니스 규칙/인터페이스를 독점 소유
- 타 서비스 DB 직접 접근 금지, 동기 HTTP는 계약 기반만 허용
- HTTP 의존: gateway → 모든 서비스, order-service → product-service (재고 검증)만 서비스 간 직접 허용
- `batch-worker`는 비공개 엔드포인트 호출 불가, `gateway-service`는 비즈니스 로직/데이터 소유 금지

### 버전 관리 (versioning-policy.md)

- HTTP API: URL 경로 버전 `/api/v{n}/{resource}`, 현재 기본 v1
- 비파괴적 변경(선택 필드 추가, 신규 엔드포인트)은 버전 업 불필요
- 파괴적 변경(필드 제거/이름변경/타입변경)은 반드시 새 버전
- 이벤트: 파괴적 변경 시 `{EventName}V{n}` 형식, 마이그레이션 기간 중 동시 발행
- 공유 라이브러리: SemVer, Flyway: 순차 정수 버전

### 소유권 규칙 (ownership-rule.md)

- 서비스는 자신의 도메인 규칙과 퍼시스턴스 모델을 독점 소유
- HTTP 계약 소유: API를 노출하는 서비스, 이벤트 계약 소유: 프로듀서
- 서비스별 규칙은 `specs/services/<service>/`, 크로스 커팅 규칙은 `specs/platform/`

---

## 서비스 책임 요약

| 서비스 | 책임 |
|---|---|
| `gateway-service` | 모든 외부 요청의 단일 진입점. JWT 검증, 라우팅, rate limiting, CORS, 요청 로깅 |
| `auth-service` | 회원가입, 로그인, 로그아웃, 토큰 발급/갱신, 세션 관리, 인증 감사 로그 |
| `user-service` | UserSignedUp 이벤트 기반 프로필 자동 생성, 프로필/배송지 CRUD, 회원 탈퇴 |
| `product-service` | 상품 등록/수정/삭제, 옵션/변형 관리, 카테고리, 가격, 재고 추적, 상품 이벤트 발행 |
| `order-service` | 주문 생성~배송~완료/취소 전체 주문 생명주기를 DDD 애그리게이트로 관리 |
| `payment-service` | OrderPlaced/OrderCancelled 이벤트 수신으로 결제/환불 처리 (현재 시뮬레이션 모드) |
| `search-service` | 상품 이벤트 소비로 Elasticsearch 인덱스 유지, 키워드/필터/정렬/패싯 검색 API |
| `batch-worker` | 만료 세션 정리, 미처리 주문 자동 취소, 일별 매출 집계, Elasticsearch 정합성 검증 |
| `admin-dashboard` | 관리자 전용 상품/주문/사용자 관리 내부 운영 대시보드 (Next.js) |
| `web-store` | 고객용 쇼핑몰 프론트엔드 (Next.js) |

---

## 기능 스펙 요약

| 기능 | 설명 |
|---|---|
| `authentication` | 회원가입→로그인→토큰 갱신→로그아웃 전체 인증 흐름, JWT/세션 관리 비즈니스 규칙 |
| `user-management` | UserSignedUp 이벤트 기반 프로필 자동 생성, 프로필/배송지 CRUD, 회원 탈퇴 및 연쇄 처리 |
| `product-management` | 상품 등록/수정/삭제/재고 조정, search-service 인덱스 이벤트 기반 동기화 |
| `product-search` | Elasticsearch 기반 전문 검색(키워드, 필터, 정렬, 패싯), 이벤트 기반 인덱스 자동 동기화 |
| `order-processing` | 주문 생성→결제→확정→배송→완료/취소 전체 상태 흐름, payment/product/user 서비스 간 이벤트 연동 |
| `payment-processing` | OrderPlaced/OrderCancelled 이벤트 구독으로 결제/환불 이벤트 드리븐 처리 |

---

## 유스케이스 요약

| 유스케이스 | 설명 |
|---|---|
| `signup-and-login` | 회원가입(UC-1), 로그인(UC-2), 토큰 갱신(UC-3), 로그아웃(UC-4) — 이메일 중복, 자격증명 불일치, rate limit 등 예외 포함 |
| `user-profile-and-address` | 프로필 조회/수정(UC-1~2), 배송지 CRUD(UC-3~6), 회원 탈퇴(UC-7) — 최대 10개, 기본 배송지 삭제 불가 등 제약 |
| `product-browse-and-search` | 상품 목록(UC-1), 상세(UC-2), 전문 검색(UC-3), 인덱스 동기화(UC-4) — 비인증 사용자 접근 가능 |
| `cart-and-order` | 주문 생성(UC-1), 목록/상세 조회(UC-2~3), 주문 취소(UC-4), 탈퇴 시 자동 취소(UC-5) |
| `payment-and-refund` | 시스템 이벤트 기반 결제(UC-1), 주문 취소 시 환불(UC-2), 결제 내역 조회(UC-3) |
| `admin-management` | 상품 등록(UC-1), 수정(UC-2), 재고 조정(UC-3), 사용자 목록(UC-4), 사용자 상세(UC-5) — 관리자 전용 |
