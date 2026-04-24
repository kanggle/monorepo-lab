# 스킬 가이드

Claude가 구현 시 참조하는 기술 패턴 문서 목록.

## 스킬이란?

`.claude/skills/`에 정의된 구현 가이드. 에이전트가 코드를 작성할 때 따라야 할 패턴과 규칙을 담고 있다.

## 작성 완료된 스킬

| 스킬 | 파일 | 내용 |
|---|---|---|
| Spring Boot API | `backend/springboot-api.md` | REST API 구현 패턴 (Controller, Command/Result, 네이밍) |
| Backend 구현 워크플로우 | `backend/implementation-workflow.md` | 백엔드 구현 전체 절차 |
| Backend 테스트 | `backend/testing-backend.md` | 단위/슬라이스/통합 테스트 패턴 |
| Layered 아키텍처 | `backend/architecture/layered.md` | 레이어드 아키텍처 구현 |
| DDD 아키텍처 | `backend/architecture/ddd.md` | DDD 아키텍처 구현 |
| Hexagonal 아키텍처 | `backend/architecture/hexagonal.md` | 헥사고날 아키텍처 구현 |
| Clean 아키텍처 | `backend/architecture/clean.md` | 클린 아키텍처 구현 |
| Feature-Sliced Design | `frontend/architecture/feature-sliced-design.md` | Next.js FSD 패턴 |
| Layered by Feature | `frontend/architecture/layered-by-feature.md` | Next.js 기능별 레이어 패턴 |

## 미작성 스킬 (placeholder)

아래는 파일만 존재하고 내용은 아직 없음. 해당 영역은 specs만 참조.

| 카테고리 | 파일들 |
|---|---|
| Backend | `dto-mapping`, `exception-handling`, `validation`, `transaction-handling` |
| Frontend | `api-client`, `implementation-workflow`, `state-management`, `form-handling`, `loading-error-handling`, `testing-frontend` |
| Database | `schema-change-workflow`, `indexing`, `migration-strategy`, `transaction-boundary` |
| Messaging | `event-implementation`, `outbox-pattern`, `consumer-retry-dlq`, `idempotent-consumer` |
| Testing | `test-strategy`, `contract-test`, `e2e-test`, `fixture-management`, `testcontainers` |
| Search | `elasticsearch-index`, `elasticsearch-query`, `index-sync` |
| Infra | `docker-build`, `kubernetes-deploy`, `terraform-module`, `ci-cd` |

## 태스크 유형별 읽을 스킬

| 태스크 유형 | 읽을 스킬 |
|---|---|
| 백엔드 API 추가 | `springboot-api` + 해당 아키텍처 스킬 + `testing-backend` |
| 프론트엔드 화면 추가 | 해당 아키텍처 스킬 (`feature-sliced-design` 또는 `layered-by-feature`) |
| 이벤트 추가 | (미작성 — specs만 참조) |
| 통합 태스크 | 백엔드 스킬 + specs |

## 스킬 인덱스

전체 매핑: `.claude/skills/INDEX.md` 참조

---

## 완료된 스킬 상세

각 스킬의 핵심 패턴, 주요 규칙, 적용 서비스를 요약한다.

---

### Spring Boot API (`backend/springboot-api.md`)

**핵심 패턴**

- Controller는 HTTP 매핑만 담당: Request → Command 변환 후 Application Service 호출, Result → Response 변환
- Command/Result 레코드로 레이어 간 데이터 전달 (도메인 엔티티를 레이어 경계에 넘기지 않음)
- 인프라 계층이 계산한 값이 필요하면 인터페이스 반환 타입에 포함시킴 (application에서 infrastructure 유틸 직접 import 금지)
- HTTP 상태 코드 및 오류 응답 형식은 `specs/platform/error-handling.md` 준수
- 네이밍 규칙: `{UseCase}Command`, `{UseCase}Result`, `{UseCase}Request`, `{UseCase}Response`

**주요 규칙**

- Application 레이어에서 `infrastructure.*` 유틸을 직접 import 금지
- 도메인 엔티티는 레이어 경계를 넘지 않음
- 사전 조건: `specs/services/<service>/architecture.md` 반드시 선독

**적용 서비스**

- REST API를 제공하는 모든 Spring Boot 백엔드 서비스

---

### Backend 구현 워크플로우 (`backend/implementation-workflow.md`)

**핵심 패턴**

- 구현 전 스펙 읽기 → 구현 → 테스트 → 셀프 리뷰의 9단계 절차
- 레이어 위반 체크리스트: controller → repository 직접 호출 금지, domain → framework 의존 금지, application → infrastructure 유틸 직접 사용 금지
- 셀프 리뷰: 금지 의존성 없음, 필드명 계약 일치, 테스트 통과, 계약 변경 여부 확인

**주요 규칙**

- 스펙 및 계약 읽기 완료 전 구현 시작 금지
- API 또는 이벤트 형태가 변경되면 계약 파일을 먼저 업데이트
- 테스트는 `testing-backend` 스킬 + `specs/platform/testing-strategy.md` 병행 참조

**적용 서비스**

- 모든 Spring Boot 백엔드 서비스의 신규 기능 구현 태스크

---

### Backend 테스트 (`backend/testing-backend.md`)

**핵심 패턴**

- 단위 테스트: `@ExtendWith(MockitoExtension.class)`, Spring 컨텍스트 없음
- 컨트롤러 슬라이스 테스트: `@WebMvcTest` + `MockMvc`, `SecurityConfig`·`GlobalExceptionHandler` 반드시 import
- 통합 테스트: `@SpringBootTest` + `@Testcontainers`, 실제 PostgreSQL·Redis 사용 (H2 금지)
- 인프라 단위 테스트: Mockito로 template/ops 모킹
- 테스트 메서드명: `{scenario}_{condition}_{expectedResult}`
- `@DisplayName`은 한국어로 비즈니스 행동 기술

**주요 규칙**

- Mockito STRICT_STUBS: 사용되지 않는 stub은 `UnnecessaryStubbingException` 발생 → stub은 사용하는 테스트에만 선언
- 레코드 반환 메서드에 `willReturn(null)` 사용 금지
- 데이터 격리: `UUID.randomUUID()` 또는 고유 이메일로 테스트 간 충돌 방지
- `@Transactional` 롤백에 의존하지 말고 실제 정리 또는 고유 데이터 사용

**적용 서비스**

- 모든 Spring Boot 백엔드 서비스

---

### Layered 아키텍처 (`backend/architecture/layered.md`)

**핵심 패턴**

```
com.example.{service}/
├── presentation/          # Controller, Request/Response DTO
├── application/           # Service, Command, Result
├── domain/                # Entity, Repository 인터페이스, Domain Service
└── infrastructure/        # JPA 구현체, 외부 어댑터, Config
```

- Presentation: HTTP 매핑만, 비즈니스 로직 없음, `@Valid` 검증
- Application: `@Transactional` 경계, 도메인 인터페이스만 사용
- Domain: 비즈니스 불변식 강제, Spring 어노테이션 없음 (JPA 어노테이션 허용)
- Infrastructure: 도메인 인터페이스 구현, 프레임워크 의존성 소유

**주요 규칙**

- Controller → Repository 직접 호출 금지
- Application → Infrastructure 유틸 직접 import 금지
- Domain 엔티티에 `@Service`, `@Component` 등 Spring 스테레오타입 금지
- `@Transactional`은 Application Service에만

**적용 서비스**

- 단순한 도메인 구조의 Spring Boot 서비스 (레이어드 아키텍처 선언 서비스)

---

### DDD 아키텍처 (`backend/architecture/ddd.md`)

**핵심 패턴**

```
com.example.{service}/
├── interfaces/            # REST Controller, Inbound Event Handler
├── application/           # Application Service, Command, Result, Port(선택)
├── domain/
│   ├── {aggregate}/       # 애그리게이트 루트·엔티티·밸류오브젝트·Repository 인터페이스
│   ├── event/             # 도메인 이벤트
│   └── service/           # 도메인 서비스 (복수 애그리게이트 규칙)
└── infrastructure/        # 영속성, 이벤트 발행, Config
```

- 애그리게이트 루트: `AbstractAggregateRoot<T>` 상속, protected 생성자, 정적 팩토리, 공개 setter 없음
- 상태 전환 시 선행 조건 검증 + `registerEvent()` 호출
- 도메인 이벤트: `@TransactionalEventListener(phase = AFTER_COMMIT)`으로 Kafka 발행
- 밸류 오브젝트: `@Embeddable`, 불변, 모든 연산은 새 인스턴스 반환, `equals`/`hashCode` 오버라이드
- Repository: 애그리게이트 루트 단위로 1개, 도메인이 필요한 연산만 정의

**주요 규칙**

- 비즈니스 규칙은 애그리게이트 루트 또는 도메인 서비스에만
- 다른 애그리게이트를 Application Service 없이 직접 수정 금지
- 도메인 이벤트는 Application Service에서 직접 발행하지 않고 `registerEvent()` 사용
- 도메인 모델이 DTO 클래스에 의존하면 안 됨

**적용 서비스**

- 복잡한 도메인 규칙을 가진 서비스 (DDD 아키텍처 선언 서비스)

---

### Hexagonal 아키텍처 (`backend/architecture/hexagonal.md`)

**핵심 패턴**

```
com.example.{service}/
├── adapter/
│   ├── in/                # REST Controller, 메시지 컨슈머 (인바운드 어댑터)
│   └── out/               # DB 어댑터, 외부 API, 이벤트 발행 (아웃바운드 어댑터)
│       └── persistence/   # JPA 엔티티, Spring Data, Mapper
├── application/
│   ├── port/
│   │   ├── in/            # 인바운드 포트 (유스케이스 인터페이스)
│   │   └── out/           # 아웃바운드 포트 (드리븐 인터페이스)
│   ├── service/           # 유스케이스 구현체
│   ├── command/
│   └── result/
├── domain/                # 순수 POJO 도메인 모델 (JPA 어노테이션 없음)
└── config/
```

- 도메인 모델은 순수 POJO — JPA 엔티티는 영속성 어댑터 내부에만 존재
- Mapper가 도메인 모델 ↔ JPA 엔티티 변환
- Controller는 인바운드 포트 인터페이스에만 의존 (서비스 클래스 직접 의존 금지)
- 아웃바운드 포트 시그니처에 벤더 SDK 타입 노출 금지

**주요 규칙**

- 포트 인터페이스는 반드시 `application/port/` 에 위치 (어댑터 패키지에 두지 않음)
- Application Service는 아웃바운드 포트 인터페이스에만 의존 (어댑터 클래스 직접 참조 금지)
- 도메인 모델에 `@Entity` 금지
- 비즈니스 규칙을 어댑터에 두지 않음

**적용 서비스**

- 외부 시스템 격리가 중요한 서비스 (헥사고날 아키텍처 선언 서비스)

---

### Clean 아키텍처 (`backend/architecture/clean.md`)

**핵심 패턴**

```
com.example.{service}/
├── domain/                # Enterprise 비즈니스 규칙 (순수 POJO)
├── usecase/
│   ├── port/
│   │   ├── in/            # 인풋 포트 (유스케이스 인터페이스, 1유스케이스 = 1인터페이스)
│   │   └── out/           # 아웃풋 포트
│   ├── interactor/        # 유스케이스 구현체 (Spring 어노테이션 없음)
│   ├── command/
│   └── result/
├── adapter/               # 인바운드(REST), 아웃바운드(영속성, 외부, 이벤트)
└── config/                # 빈 등록, 트랜잭션 설정
```

- 의존성 방향: `[Frameworks] → [Adapters] → [Use Cases] → [Domain]` (안쪽으로만)
- 인터랙터(Interactor): `@Service` 없는 순수 Java 클래스, `@Bean`으로 수동 등록
- 유스케이스 인터페이스 메서드명은 단일 `execute()`
- 트랜잭션: 인터랙터에 직접 `@Transactional` 금지 — Config에서 TransactionProxy 적용하거나 어댑터에 위임

**주요 규칙**

- 1개 인터랙터 = 1개 인풋 포트 (유스케이스 간 혼합 금지)
- 도메인 레이어와 유스케이스 레이어를 동일 패키지에 두지 않음
- 인터랙터에 Spring 어노테이션 추가 금지
- 아웃풋 포트가 JPA 엔티티 타입을 반환하면 안 됨

**헥사고날 vs 클린 아키텍처 주요 차이**

| 항목 | Hexagonal | Clean |
|---|---|---|
| 핵심 개념 | 포트 & 어댑터 (경계 격리) | 유스케이스 (비즈니스 케이스) |
| Application Service | 여러 유스케이스를 하나의 서비스에 | 인터랙터 1개 = 유스케이스 1개 |
| 프레임워크 의존 | `@Service` 허용 | 인터랙터에 Spring 어노테이션 없음 |
| 빈 등록 | `@Service` 자동 등록 | `@Configuration`에서 수동 등록 |

**적용 서비스**

- 유스케이스 독립성과 테스트 격리가 중요한 서비스 (클린 아키텍처 선언 서비스)

---

### Feature-Sliced Design (`frontend/architecture/feature-sliced-design.md`)

**핵심 패턴**

```
src/
├── app/              # Next.js App Router (라우트만, 비즈니스 로직 없음)
├── widgets/          # 복수 feature 조합 컴포넌트
├── features/         # 자립형 기능 모듈
│   └── cart/
│       ├── ui/       # 컴포넌트
│       ├── model/    # 상태(Zustand), 타입, 훅
│       ├── api/      # API 호출 함수/훅
│       ├── lib/      # 순수 비즈니스 로직
│       └── index.ts  # 공개 API
├── entities/         # 공유 도메인 타입 및 기본 UI
└── shared/           # 프레임워크 독립적 유틸, UI 프리미티브
```

- 피처 간 직접 import 금지 — 공유 타입은 `entities/`, 조합은 `widgets/`, 네비게이션은 URL/Router 사용
- 각 피처는 `index.ts`를 통해 공개 API만 노출 (내부 파일 직접 import 금지)
- Entity UI는 순수하게 (기능별 행동 없음) — 피처가 Entity UI에 행동을 합성
- Server Component 기본, 클라이언트 상호작용이 필요할 때만 `'use client'`
- 서버 데이터는 TanStack Query/SWR 사용 (전역 상태로 관리 금지)

**주요 규칙**

- 피처가 다른 피처를 import 금지
- 비즈니스 로직을 `app/` 페이지 컴포넌트에 두지 않음
- 두 개 이상의 피처에서 사용하는 컴포넌트만 `shared/ui/`로 이동
- 서버 컴포넌트에서 API 호출은 `api/` 함수 직접 사용, 클라이언트 컴포넌트에서는 훅 사용

**적용 서비스**

- Next.js 스토어프론트 앱 (FSD 아키텍처 선언 서비스)

---

### Layered by Feature (`frontend/architecture/layered-by-feature.md`)

**핵심 패턴**

```
src/
├── app/              # Next.js App Router (페이지 조합만, 모두 'use client')
├── features/         # 관리 도메인별 기능 모듈
│   └── product-management/
│       ├── components/   # List, Detail, Form 컴포넌트
│       ├── hooks/        # 쿼리 훅, 뮤테이션 훅 (TanStack Query)
│       ├── api/          # API 호출 함수 객체 (apiClient 사용)
│       ├── types/        # 피처 전용 타입
│       └── index.ts      # 공개 API
└── shared/           # 공통 UI (DataTable, PageLayout, FormField 등), 훅, 유틸
```

- CRUD 패턴: List(필터+페이지네이션), Detail(삭제 확인), Form(생성·수정 공용)
- 필터·페이지네이션 상태는 URL(`useSearchParams`)로 관리
- 뮤테이션 성공 후 `queryClient.invalidateQueries()`로 캐시 무효화
- 관리자 페이지는 모두 CSR (`'use client'`)

**주요 규칙**

- 피처 간 직접 import 금지 — 공유 타입은 `@repo/types`, 공유 컴포넌트는 `shared/`
- API 호출을 컴포넌트에서 직접 하지 않고 훅을 통해 사용
- 두 개 이상의 피처에서 필요한 컴포넌트만 `shared/`로 이동
- Form 상태는 React Hook Form 사용 (`useState` 대신)
- `DataTable`, `PageLayout`, `FormField` 등 공통 UI는 `shared/ui/`에서 제공

**적용 서비스**

- Next.js 어드민 앱 (Layered by Feature 아키텍처 선언 서비스)
