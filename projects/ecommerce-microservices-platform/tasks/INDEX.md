# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-FE-XXX`: frontend
- `TASK-INT-XXX`: integration

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.


## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
- Do not modify a task file after it moves to `review/` or `done/`.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

| ID | Title | Service | Tags |
|---|---|---|---|
| TASK-BE-077 | 쿠폰/프로모션 시스템 — 할인 쿠폰 발급, 사용, 프로모션 관리 기능 | promotion-service (신규) | code, api, event |
| TASK-BE-078 | 알림 서비스 — 이메일/SMS/푸시 알림 발송 기능 | notification-service (신규) | code, api, event |
| TASK-BE-079 | 리뷰/평점 시스템 — 상품 리뷰 작성, 평점 관리, 평균 평점 집계 | review-service (신규) | code, api, event |
| TASK-BE-080 | 위시리스트/찜 기능 — 사용자별 관심 상품 목록 관리 | user-service | code, api |
| TASK-BE-081 | 배송 추적 서비스 — 주문 배송 상태 관리 및 추적 | shipping-service (신규) | code, api, event |
## ready

_(없음)_

## in-progress

_(없음)_

## review

_(없음)_

## done

| ID | Title | Service | Tags |
|---|---|---|---|
| TASK-FE-066 | admin-dashboard 상품 등록/수정 폼 이미지 업로드 UI — 다중 업로드, 미리보기, 순서 변경, 삭제 | admin-dashboard | code, api |
| TASK-BE-125-fix-001 | TASK-BE-125 리뷰 수정 — 계약 위반 13건 + 최적화 4건 | product-service, search-service | code, api, event |
| TASK-BE-125 | product-service 상품 이미지 업로드/삭제 API + ProductImagesUpdated 이벤트 | product-service, search-service | code, api, event |
| TASK-INT-022-fix-001 | TASK-INT-022 리뷰 수정 — MinIO /tmp 마운트, .env.example, infra NSP PSA, minio-init 리소스 제한 | infra | deploy, code |
| TASK-INT-022 | 상품 이미지 객체 스토리지 인프라 구성 — MinIO(local/dev) + S3(staging/prod) 배선 | infra | deploy, code |
| TASK-FE-065-fix-001 | TASK-FE-065 리뷰 fix: 테스트 파일 3개 widgets import 경로 kebab-case 갱신 | web-store | fix, naming, test |
| TASK-FE-065 | web-store widgets 디렉토리 kebab-case 네이밍 적용 | web-store | code, naming |
| TASK-BE-124-fix-001 | TASK-BE-124 리뷰 지적 수정 — wishlistItemId 미구현 전면 보완 | user-service | code, test |
| TASK-BE-124 | GET /api/wishlists/me/check 응답에 wishlistItemId 추가 — 콘트랙트와 구현 불일치 수정 | user-service | code, test |
| TASK-FE-064-fix-001 | TASK-FE-064 리뷰 이슈 수정: 로그아웃 카트 클리어 테스트 누락 및 토큰 만료 시 카트 즉시 삭제 | web-store | code, test |
| TASK-BE-123 | TASK-BE-122 리뷰 fix: DATA_INTEGRITY_VIOLATION 에러 코드 등록 및 wishlist-api 컨트랙트 갱신 | user-service | code |
| TASK-FE-063-fix-001 | TASK-FE-063 리뷰 fix: 대시보드 위젯 테스트 3종 추가 및 집계 한계 경고 표시 | admin-dashboard | code, test |
| TASK-FE-063 | admin-dashboard 홈 화면 1순위 위젯 구현 — 주문/매출/재고 KPI 및 최근 주문 | admin-dashboard | code, api, test |
| TASK-FE-062-fix-001 | TASK-FE-062 리뷰 fix: cross-feature import 제거 + 에러/로딩 fallback 테스트 추가 | admin-dashboard | code, test |
| TASK-FE-062 | admin-dashboard 주문관리 목록/상세에 주문자 이메일 표시 | admin-dashboard | code, test |
| TASK-BE-119-fix-003 | TASK-BE-118-fix-001 리뷰 fix: RepublishSignupEventsIntegrationTest AdminAccountSeeder 충돌 수정 | auth-service | code, test |
| TASK-BE-119-fix-002 | TASK-BE-119 리뷰 fix: 통합 테스트 파일명 컨벤션 + Kafka producer acks/idempotence 설정 추가 | auth-service | code, test |
| TASK-BE-119 | auth-service AuthEvent → Kafka 발행 브리지 구현 — 인메모리 이벤트를 실제 Kafka로 전송 | auth-service | code, event, test |
| TASK-BE-118-fix-002 | TASK-BE-118-fix-001 리뷰 수정 — SecurityConfig TODO 주석에 연결된 태스크 ID 추가 | auth-service | code |
| TASK-BE-118-fix-001 | TASK-BE-118 리뷰 수정 — 재발행 엔드포인트 통합 테스트 누락 추가 및 보안/로깅 보완 | auth-service | code, test |
| TASK-BE-118 | auth-service 가입 이벤트 재발행 내부 엔드포인트 — user-service user_profiles 누락 복구 수단 제공 | auth-service | code, api, event, test |
| TASK-BE-116-fix-001 | TASK-BE-116 리뷰 수정 — payment-service AmountMismatchException HTTP 상태 코드 422 → 400 수정 | payment-service | code, api |
| TASK-BE-114 | auth-service Google OAuth 2.0 로그인 구현 — Authorization Code Flow, 사용자 find-or-create, JWT 발급 | auth-service | code, api, test |
| TASK-BE-098 | order-service API 컨트랙트 불일치 수정 — POST 요청 필드 차이 및 GET 목록 status 필터 미구현 | order-service | code, api, test |
| TASK-BE-097 | gateway-service 통합 테스트 user-service 라우트 누락 수정 | gateway-service | code, test |
| TASK-BE-096 | gateway-service rate_limited 메트릭 실제 연동 — RequestRateLimiter 429 응답 시 gateway_rate_limited_total 기록 | gateway-service | code, test |
| TASK-BE-095 | gateway-service 외부 클라이언트 X-User-Id / X-User-Email 헤더 스푸핑 방어 — 모든 경로에서 수신 헤더 제거 | gateway-service | code, test |
| TASK-BE-105 | order-service ErrorResponse 컨트랙트 위반 수정 — timestamp 필드 제거 및 IllegalArgumentException 핸들러 추가 | order-service | code, api, test |
| TASK-FE-061 | web-store product API mock 폴백 제거 — non-UUID mock id가 쓰기 API로 흘러 들어가는 경로 차단 | web-store | code, test |
| TASK-BE-116 | 전 서비스 GlobalExceptionHandler HttpMessageNotReadableException 핸들러 추가 — 잘못된 JSON/UUID 본문 시 500 → 400 VALIDATION_ERROR | auth-service, user-service, product-service, order-service, payment-service, shipping-service, review-service, promotion-service, notification-service, search-service | code, api, test |
| TASK-FE-047 | TASK-FE-040에서 발견된 LoginForm oauth_failed 에러 메시지 테스트 누락 수정 | admin-dashboard | code, test |
| TASK-BE-113-fix-001 | TASK-BE-113 리뷰 수정 — order-service 회원 탈퇴 시 주문 취소 배치 저장 실제 구현 | order-service | code, event, test |
| TASK-BE-113 | order-service 회원 탈퇴 시 주문 취소 배치 저장 최적화 | order-service | code, event |
| TASK-BE-109 | order-service 이벤트 발행 Transactional Outbox 패턴 적용 | order-service | code, event |
| TASK-BE-110 | order-service 이벤트 컨슈머 event_id 기반 중복 처리 구현 | order-service | code, event |
| TASK-BE-103 | order-service OrderMetrics 취소 reason 매핑 누락 수정 — user_withdrawn 카운터 추가 | order-service | code, test |
| TASK-BE-099 | order-service Application 레이어 Infrastructure 직접 의존 제거 — OrderMetrics, KafkaTemplate 포트 인터페이스 분리 | order-service | code, test |
| TASK-BE-099-fix-001 | TASK-BE-099 리뷰 수정 — OrderEventPublisher 포트 인터페이스 및 SpringOrderEventPublisher 구현체 추가 | order-service | code, test |
| TASK-BE-092 | gateway-service 스펙 헤더 불일치 수정 — X-User-Role vs X-User-Email 명확화 | gateway-service | code |
| TASK-BE-093 | gateway-service 미사용 RateLimiter Bean 및 Properties 제거 | gateway-service | code, test |
| TASK-BE-094 | gateway-service public 라우트 요청 라우팅 메트릭 누락 수정 | gateway-service | code, test |
| TASK-BE-091 | gateway-service RequestLoggingFilter resolveTargetService 중복 제거 — RouteService 위임 | gateway-service | code, test |
| TASK-INT-021 | Kubernetes 보안 강화 — SecurityContext, NetworkPolicy, secrets 관리 개선 | infra | deploy, code |
| TASK-INT-021-fix-002 | TASK-INT-021 리뷰 수정 2차 — Ingress server-snippets 대체, batch-worker NetworkPolicy kubelet probe, web-store/admin-dashboard SSR egress | infra | deploy, code |
| TASK-INT-021-fix-001 | TASK-INT-021 리뷰 수정 — default-deny Egress, seccompProfile, automountServiceAccountToken, PSS 레이블, Ingress 보안 헤더, PDB, 검증 스크립트 보완 | infra | deploy, code |
| TASK-BE-088 | search-service, payment-service Kafka DLQ 설정 추가 — DeadLetterPublishingRecoverer 적용 | search-service, payment-service | code, event, test |
| TASK-BE-088-fix-001 | TASK-BE-088 리뷰 수정 — payment-service catch 블록 특정 예외 변경 및 DLQ 라우팅 테스트 보강 | search-service, payment-service | code, event, test |
| TASK-FE-030 | isApiError 타입 가드 및 ERROR_MESSAGES 상수 공유 모듈 통합 — web-store 5곳 중복 제거 | web-store, packages/types | code, test |
| TASK-FE-031 | web-store, admin-dashboard 중복 공유 UI 컴포넌트 @repo/ui 통합 — LoadingSpinner, EmptyState, ErrorMessage | web-store, admin-dashboard, packages/ui | code, test |
| TASK-FE-032 | 대형 프론트엔드 컴포넌트 분해 — ProductForm(352줄), AddressForm(339줄), AddressList(263줄) | admin-dashboard, web-store | code, test |
| TASK-FE-033 | 프론트엔드 인라인 스타일 정리 — 스타일 객체 추출 및 CSS Modules 전환 | admin-dashboard, web-store | code, test |
| TASK-BE-084 | auth-service, user-service 이메일 검증 중복 제거 — 서비스별 Email Value Object 추출 | auth-service, user-service | code, test |
| TASK-BE-084-fix-001 | TASK-BE-084 리뷰 수정 — Email VO 정규식 불일치, 정규화 불일치, 최대 길이 검증 누락 | auth-service, user-service | code, test |
| TASK-INT-020 | E2E 테스트 커버리지 확대 — 핵심 비즈니스 흐름 전체 시나리오 추가 | infra | test, deploy |
| TASK-INT-019 | 모니터링 알림 규칙 세분화 — Prometheus AlertManager 알림 정책 구성 | infra | deploy, code |
| TASK-INT-018 | 성능/부하 테스트 구성 — 전체 서비스 부하 테스트 스크립트 및 기준 수립 | infra | test, deploy |
| TASK-INT-017 | specs/use-cases/ 유즈케이스 스펙 정의 — 핵심 사용자 시나리오 문서화 | all | code |
| TASK-FE-029 | admin-dashboard React key 수정 및 api-client 하드코딩 설정 외부화 | admin-dashboard, api-client | code, test |
| TASK-BE-076 | product-service 통합 테스트 Kafka 설정 수정 | product-service | code, test |
| TASK-BE-075 | LoginRateLimitFilter 인터페이스 사용 수정 | auth-service | code |
| TASK-BE-074 | gateway-service CORS ConfigMap 누락 수정 | gateway-service | code |
| TASK-BE-073 | auth-service application→infrastructure 직접 의존 제거 및 gateway CORS 외부화 | auth-service, gateway-service | code |
| TASK-BE-072 | payment-service, batch-worker 도메인 모델 프레임워크 의존성 분리 | payment-service, batch-worker | code |
| TASK-BE-071 | payment-service 이벤트 envelope snake_case 수정 및 토픽명 설정 외부화 | payment-service | code, event |
| TASK-BE-069 | order-service DTO 네이밍 수정 및 gateway-service 서비스 레이어 도입 | order-service, gateway-service | code, test |
| TASK-BE-068 | search-service Elasticsearch 응답 null 안전성 및 예외 처리 개선 | search-service | code, test |
| TASK-FE-028 | 프론트엔드 타입 안전성 강화 및 에러 바운더리 추가 | web-store, admin-dashboard | code, test |
| TASK-BE-067 | product-service 패키지명 수정 — interfaces → presentation | product-service | code, test |
| TASK-BE-066 | 전 서비스 이벤트 소비 실패 처리 개선 — 예외 로깅 강화, 메트릭 추가 | all | code, event, test |
| TASK-BE-065 | 전 서비스 입력 검증 강화 — @Valid 누락, 금액/헤더 검증 보완 | all | code, test |
| TASK-INT-013 | Docker 보안 강화 — Java 서비스 non-root 사용자, OTEL JAR 체크섬 검증 | infra | deploy, code |
| TASK-BE-064 | batch-worker 아키텍처 완성 — 빈 설정 클래스 제거, 도메인 검증 추가 | batch-worker | code, test |
| TASK-BE-063 | product-service soft-delete 쿼리 버그 수정 — findById에서 삭제된 상품 조회 가능 | product-service | code, test |
| TASK-BE-062 | auth-service, user-service PII 로그 노출 제거 | auth-service, user-service | code |
| TASK-BE-061 | 전체 서비스 하드코딩 시크릿 제거 — DB 크레덴셜, JWT 시크릿 설정 외부화 | all | code |
| TASK-INT-016 | specs/features/ 기능 스펙 정의 — 구현 완료된 핵심 기능별 feature 스펙 문서화 | all | code |
| TASK-INT-015 | 누락된 서비스 overview.md 스펙 작성 — user-service, gateway-service, batch-worker, admin-dashboard | all | code |
| TASK-INT-015-fix-001 | admin-dashboard overview.md 포맷 수정 — `## Application` → `## Service` | admin-dashboard | fix |
| TASK-INT-014 | 프론트엔드 Docker 이미지 빌드 및 docker-compose 통합 | infra | deploy, code |
| TASK-INT-013 | Docker 보안 강화 — Java 서비스 non-root 사용자, OTEL JAR 체크섬 검증 | infra | deploy, code |
| TASK-INT-011 | 모니터링 대시보드 — Grafana 프로비저닝 및 Prometheus 메트릭 시각화 | infra | deploy, code |
| TASK-INT-010 | Kubernetes 매니페스트 — 프로덕션 배포 Deployment, Service, Ingress 구성 | infra | deploy |
| TASK-INT-009 | 프론트엔드 ↔ 백엔드 통합 검증 — web-store, admin-dashboard API 연동 확인 | infra | deploy, test |
| TASK-INT-008 | CI/CD 파이프라인 구성 — GitHub Actions 빌드, 테스트, 이미지 빌드 워크플로우 | infra | deploy, code |
| TASK-INT-007 | E2E 통합 테스트 — docker compose 환경 전체 흐름 검증 스크립트 | infra | deploy, test |
| TASK-BE-070 | 전 서비스 한국어 로그 메시지 영어 통일 — 운영 일관성 확보 | all | code |
| TASK-BE-070-fix | TASK-BE-070 리뷰 수정 — order-service, product-service 잔여 한국어 에러 메시지 영어 전환 | order-service, product-service | code |
| TASK-INT-012 | 코드 품질 크로스 리뷰 — 전체 서비스 기술 부채 점검 및 개선 태스크 도출 | all | code |
| TASK-INT-006-fix | TASK-INT-006 리뷰 이슈 수정 — Dockerfile 빌드 실패, restart 정책 누락, NEXT_PUBLIC 환경변수 | infra | deploy, code |
| TASK-INT-006 | docker-compose 리소스 제한 및 프론트엔드 헬스체크 추가 | infra | deploy |
| TASK-BE-060 | product-service Metrics Counter 메모리 누수 테스트 추가 | product-service | code, test |
| TASK-BE-057-fix | 이벤트 발행 실패 메트릭 수정 | all | code, event |
| TASK-BE-057 | 전 서비스 이벤트 발행 실패 모니터링 — 실패 메트릭 및 로그 레벨 통일 | all | code, event, test |
| TASK-BE-056 | search-service ProductUpdated 재고 초기화 버그 수정 및 컨슈머 테스트 추가 | search-service | code, event, test |
| TASK-BE-055 | auth-service 클라이언트 IP 해석 로직 통일 — Rate Limit 우회 방지 | auth-service | code, test |
| TASK-BE-054 | order-service, gateway-service Metrics Counter 등록 메모리 누수 수정 | order-service, gateway-service | code, test |
| TASK-BE-053 | order-service, product-service 동시성 제어 — Optimistic Locking 적용 | order-service, product-service | code, test |
| TASK-FE-026 | web-store, admin-dashboard 인증 컨텍스트 및 API 설정 중복 코드 공통 패키지 추출 | web-store, admin-dashboard | code, test |
| TASK-FE-027 | web-store 메모리 누수 수정 및 에러 핸들링 개선 | web-store | code, test |
| TASK-BE-059 | gateway-service 테스트 컴파일 오류 수정 및 Rate Limiter 설정 외부화 | gateway-service | code, test |
| TASK-BE-058 | user-service 주소 관리 동시성 수정 및 입력 검증 강화 | user-service | code, test |
| TASK-FE-023 | admin-dashboard 사용자 관리 기능 구현 — 사용자 목록, 상세 조회 | admin-dashboard | code, api |
| TASK-FE-024 | TASK-FE-021 리뷰 수정 — 토스트 알림 미구현 및 메시지 auto-dismiss 누락 | web-store | code, test |
| TASK-FE-025 | TASK-FE-022 리뷰 수정 — 빈 상태 UI 중복 렌더링 수정 | web-store | code, test |
| TASK-FE-022 | web-store 배송지 관리 UI 구현 — 목록 조회, 추가, 수정, 삭제 | web-store | code, api |
| TASK-FE-021 | web-store 사용자 프로필 페이지 구현 — 프로필 조회 및 수정 | web-store | code, api |
| TASK-BE-052 | TASK-BE-051 리뷰 수정 — invalidate 보조 인덱스 제거 누락 및 Redis 키 spec 반영 | auth-service | code |
| TASK-BE-051 | TASK-BE-050 리뷰 수정 — 탈퇴 사용자 토큰 무효화 누락 보완 | auth-service | code, event |
| TASK-BE-050 | auth-service UserWithdrawn 이벤트 소비 — 탈퇴 사용자 인증 정보 무효화 | auth-service | code, event |
| TASK-BE-049 | batch-worker 부트스트랩 — 프로젝트 구조, DB 스키마, Spring Batch 설정, 초기 잡 구성 | batch-worker | code |
| TASK-BE-048 | TASK-BE-045 리뷰 수정 — 예외 처리 패턴 통일, 로그 언어 일관성 | order-service | code, event |
| TASK-BE-047 | TASK-BE-043 리뷰 수정 — 서비스 네이밍, CANCELLED 주문 예외 처리, 테스트 보완 | order-service | code, event |
| TASK-BE-046 | order-service Kafka DLQ 설정 추가 — 전 컨슈머 DeadLetterPublishingRecoverer 적용 | order-service | code, event |
| TASK-BE-045 | order-service UserWithdrawn 이벤트 소비 — 탈퇴 사용자 활성 주문 취소 처리 | order-service | code, event |
| TASK-BE-044 | order-service PaymentRefunded 이벤트 소비 — 환불 완료 시 주문 환불 상태 반영 | order-service | code, event |
| TASK-BE-043 | order-service PaymentCompleted 이벤트 소비 — 결제 완료 시 주문 결제 상태 반영 | order-service | code, event |
| TASK-BE-042 | user-service 리뷰 수정 2차 — Kafka 발행 실패 처리, 불필요 이벤트 방지, 쿼리 최적화, 예외 위치 수정 | user-service | code |
| TASK-BE-041 | user-service 리뷰 수정 — 이벤트 트랜잭션 분리, DLQ 설정, Admin 권한 체크, 쿼리 최적화 | user-service | code, event |
| TASK-BE-040 | 배송 주소 관리 API — 목록 조회, 추가, 수정, 삭제 | user-service | code, api |
| TASK-BE-039 | 사용자 프로필 조회/수정 API + UserProfileUpdated 이벤트 발행 | user-service | code, api, event |
| TASK-BE-038 | user-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델, UserSignedUp 이벤트 소비 | user-service | code, event |
| TASK-FE-020 | 주문 목록 status 필터 미동작 수정 — 계약 업데이트 및 클라이언트 필터 연동 | admin-dashboard | code, api |
| TASK-FE-019 | admin-dashboard 주문 관리 기능 구현 — 주문 목록, 상세, 취소 | admin-dashboard | code, api |
| TASK-FE-018 | FilterBar searchValue prop 동기화 버그 수정 | admin-dashboard | code, test |
| TASK-FE-017 | 상품 목록 name 필터 구현 — API 계약 업데이트 및 프론트엔드 연동 | admin-dashboard | code, api |
| TASK-FE-016 | TASK-FE-015 리뷰 수정 — 버그 수정, 타입 안전성, 누락 테스트 추가 | admin-dashboard | code, test |
| TASK-FE-015 | admin-dashboard 기반 구성 및 상품 관리 기능 구현 | admin-dashboard | code, api |
| TASK-FE-014 | 주문 상세 페이지 결제 정보 표시 | web-store | code, api |
| TASK-FE-013 | TASK-FE-011 주문 플로우 누락 테스트 추가 | web-store | code, test |
| TASK-FE-012 | TASK-FE-011 주문 플로우 구현 수정 — FSD 위반, 페이지네이션, 계약 불일치, 코드 품질 | web-store | code, api |
| TASK-FE-011 | web-store 주문 플로우 구현 — 주문 생성, 주문 내역, 주문 상세 | web-store | code, api |
| TASK-FE-010 | web-store 장바구니 기능 구현 — 클라이언트 상태 관리 및 UI | web-store | code |
| TASK-FE-009 | web-store 인증 플로우 구현 — 로그인, 회원가입, 세션 관리 | web-store | code, api |
| TASK-FE-008 | api-client 테스트 보완 — 토큰 갱신 재시도 및 엣지 케이스 | frontend | code, test |
| TASK-FE-007 | web-store 컴포넌트 및 페이지 테스트 추가 | web-store | code, test |
| TASK-FE-006 | @repo/api-client 및 @repo/types 테스트 추가 | frontend | code, test |
| TASK-FE-005 | web-store 가격 필터 버그 및 UX 개선 수정 | web-store | code |
| TASK-FE-004 | api-client Payment API X-User-Id 헤더 누락 수정 (불필요 — Gateway 자동 주입) | frontend | code, api |
| TASK-FE-003 | web-store 상품 목록 및 검색 페이지 구현 | web-store | code, api |
| TASK-FE-002 | @repo/api-client 및 @repo/types 구현 — Gateway API 연동 기반 | frontend | code, api |
| TASK-FE-001 | 프론트엔드 모노레포 부트스트랩 — Turborepo + 공유 패키지 초기 구성 | frontend | code |
| TASK-BE-037 | 전 서비스 비즈니스 메트릭 구현 — Micrometer Counter/Histogram 등록 | all | code |
| TASK-BE-036 | docker-compose OpenTelemetry Java Agent 설정 — 트레이싱 전파 및 Jaeger 연동 | infra | code, infra |
| TASK-BE-035 | auth-service 비즈니스 메트릭 구현 — Micrometer Counter 6종 | auth-service | code |
| TASK-BE-034 | 전 서비스 Observability 적용 — 트레이싱, 메트릭 노출, Health Check 표준화 | all | code, infra |
| TASK-BE-033 | libs/java-observability 공통 라이브러리 — 구조화 로깅, MDC 필터, Micrometer 공통 설정 | libs | code |
| TASK-BE-032 | docker-compose 전체 구성 — 인프라 + 전체 서비스 컨테이너 | infra | code, infra |
| TASK-BE-031 | gateway-service 부트스트랩 — Spring Cloud Gateway, JWT 검증 필터, 라우팅, Rate Limiting | gateway-service | code, api |
| TASK-INT-005 | payment-service Kafka 전환 — 컨슈머 @KafkaListener, 프로듀서 KafkaTemplate | payment-service | code, kafka |
| TASK-INT-004 | order-service Kafka 전환 — 프로듀서/컨슈머 전환 | order-service | code, kafka |
| TASK-INT-003 | search-service Kafka 컨슈머 전환 — @KafkaListener + 이벤트 envelope | search-service | code, kafka |
| TASK-INT-002 | product-service Kafka 프로듀서 전환 — KafkaProductEventPublisher | product-service | code, kafka |
| TASK-INT-001 | Kafka 인프라 구성 — docker-compose, build.gradle, application.yml | infra | code, kafka |
| TASK-BE-030 | 결제 조회 API + OrderCancelled 소비 — 환불 처리 + PaymentRefunded 이벤트 | payment-service | code, api, event |
| TASK-BE-029 | payment-service OrderPlaced 이벤트 소비 — 결제 생성 + 처리 + PaymentCompleted 이벤트 | payment-service | code, event |
| TASK-BE-028 | payment-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델 | payment-service | code |
| TASK-BE-027 | order-service StockChanged 이벤트 소비 — ORDER_RESERVED 시 PENDING → CONFIRMED 전이 | order-service | code, event |
| TASK-BE-026 | 주문 취소 API — POST /api/orders/{orderId}/cancel + OrderCancelled 이벤트 | order-service | code, api, event |
| TASK-BE-025 | 주문 조회 API — GET /api/orders, GET /api/orders/{orderId} | order-service | code, api |
| TASK-BE-024 | 주문 생성 API — POST /api/orders + OrderPlaced 이벤트 | order-service | code, api, event |
| TASK-BE-023 | order-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델 | order-service | code |
| TASK-BE-022 | 상품 검색 API — GET /api/search/products (키워드, 필터, 팩싯) | search-service | code, api |
| TASK-BE-021 | 상품 이벤트 소비 및 인덱스 동기화 — ProductCreated/Updated/Deleted/StockChanged | search-service | code, event |
| TASK-BE-020 | search-service 부트스트랩 — 프로젝트 구조, Elasticsearch 설정, 도메인 모델 | search-service | code |
| TASK-BE-019 | 재고 조정 API + StockChanged 이벤트 | product-service | code, api, event |
| TASK-BE-018 | 상품 수정 + 삭제 API + ProductUpdated/ProductDeleted 이벤트 | product-service | code, api, event |
| TASK-BE-017 | 상품 등록 + 조회 API + ProductCreated 이벤트 | product-service | code, api, event |
| TASK-BE-016 | product-service 부트스트랩 — 프로젝트 구조, DB, 도메인 모델 | product-service | code |
| TASK-BE-015 | auth-service 동시 세션 제한 및 비활성 타임아웃 — 사용자당 최대 세션 수 제한, 비활성 세션 자동 만료 | auth-service | code |
| TASK-BE-014 | auth-service 도메인 이벤트 발행 — UserSignedUp, UserLoggedIn, UserLoggedOut, TokenRefreshed, LoginFailed | auth-service | code, event |
| TASK-BE-013 | auth-service 감사 로그 구현 — 로그인 시도, 토큰 갱신, 로그아웃 이력 기록 | auth-service | code, api |
| TASK-BE-012 | auth-service Token Rotation 원자성 보강 및 SHA-256 해시 중복 제거 | auth-service | code |
| TASK-BE-011 | auth-service 누락 테스트 추가 — LoginRateLimitFilter, RedisAccessTokenBlocklist, JsonAuthenticationEntryPoint, DTO 검증 | auth-service | code, test |
| TASK-BE-010 | auth-service 보안 및 에러 처리 수정 — Authorization 헤더 검증, AccessDeniedException 처리, 에러 코드 등록 | auth-service | code, api |
| TASK-BE-009 | auth-service refresh API 계약 수정 — 토큰 로테이션 응답 명시 | auth-service | code, api |
| TASK-BE-008 | auth-service 계약 준수 및 DB 최적화 — 타임스탬프 타입 수정, 중복 인덱스 제거, 트랜잭션 설정 | auth-service | code, api |
| TASK-BE-007 | auth-service 보안 및 데이터 정합성 수정 — 비밀번호 최대 길이 제한 및 Redis 원자적 연산 | auth-service | code |
| TASK-BE-006 | auth-service 레이어 의존성 위반 수정 — application→presentation DTO 제거 및 infrastructure config 추상화 | auth-service | code |
| TASK-BE-005 | auth-service refresh API 계약 준수 및 아키텍처 개선 | auth-service | code, api |
| TASK-BE-004 | auth-service 보안 버그 수정 — DUMMY_HASH, JWT secret 검증 | auth-service | code |
| TASK-BE-003 | 토큰 갱신 + 로그아웃 API | auth-service | code, api |
| TASK-BE-002 | 회원가입 + 로그인 + JWT 발급 API | auth-service | code, api |
| TASK-BE-001 | auth-service 부트스트랩 — 프로젝트 구조, DB/Redis, 도메인 모델 | auth-service | code |