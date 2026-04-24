# 에이전트 가이드

Claude Code에서 사용할 수 있는 커스텀 에이전트 목록과 사용법.

## 에이전트 목록

| Agent | 역할 | 모델 | 언제 쓰나 |
|---|---|---|---|
| `coordinator` | 태스크 분배, 에이전트 팀 조율 | opus | 복합 태스크를 여러 에이전트에 위임할 때 |
| `architect` | 아키텍처 결정, 설계 리뷰 | opus | 아키텍처 판단이나 구조 리뷰가 필요할 때 |
| `backend-engineer` | Spring Boot 백엔드 구현 | opus | 백엔드 API, 서비스, 인프라 어댑터 구현 시 |
| `frontend-engineer` | Next.js 프론트엔드 구현 | opus | 페이지, 컴포넌트, 상태 관리 구현 시 |
| `code-reviewer` | 코드 리뷰 | sonnet | 구현된 코드의 품질/보안/성능 검증 시 |
| `qa-engineer` | 테스트 작성, 품질 검증 | sonnet | 테스트 작성 및 커버리지 검증 시 |
| `api-designer` | REST API 계약 설계 | sonnet | API 엔드포인트, 요청/응답 스키마 설계 시 |
| `event-architect` | 이벤트 계약 설계 | sonnet | 도메인 이벤트, 메시징 패턴 설계 시 |
| `database-designer` | DB 스키마, 마이그레이션 설계 | sonnet | 스키마 변경, 인덱스 최적화 시 |
| `devops-engineer` | 인프라, CI/CD, 배포 | sonnet | Docker, K8s, Terraform, 파이프라인 구성 시 |

## 사용법

### 특정 에이전트 지정

```
backend-engineer 에이전트로 구현해줘
@"code-reviewer (agent)" auth 서비스 리뷰해줘
```

### coordinator로 복합 태스크 위임

```
coordinator로 상품 목록 조회 태스크 구현해줘
```

coordinator가 자동으로 판단하여 하위 에이전트에 순차/병렬 위임.

### 세션 단위 에이전트 지정

```bash
claude --agent coordinator
```

## 모델 전략

- **opus**: 핵심 구현/판단 에이전트 (coordinator, architect, backend, frontend)
- **sonnet**: 보조/검증 에이전트 (reviewer, qa, designer 등) — 비용 효율

## 에이전트 상세

### coordinator

태스크를 분석하고 전문 에이전트에 위임하여 전체 진행을 조율하는 오케스트레이터.

**핵심 규칙:**
- `tasks/ready/`에 없는 태스크는 즉시 중단하고 보고
- 작업 의존 순서 준수: 계약 → 설계 → 구현 → 테스트
- 의존성이 없는 단계는 에이전트를 병렬 실행
- 스펙 미비 시 구현 시작 없이 중단하고 보고
- 에이전트 간 충돌 발생 시 `CLAUDE.md` 소스 우선순위 기준으로 해결

**참조 스킬:** `.claude/skills/INDEX.md` (위임 시 각 에이전트의 매칭 스킬 확인)

**참조 워크플로우:** CLAUDE.md Hard Stop Rules, 의존성 그래프 기반 순차/병렬 실행

**위임 관계:**
- 위임 대상: `api-designer`, `event-architect`, `database-designer`, `backend-engineer`, `frontend-engineer`, `qa-engineer`, `code-reviewer`, `devops-engineer`, `architect`
- 위임 수신: 없음 (최상위 오케스트레이터)

---

### architect

서비스 아키텍처 결정, 설계 리뷰, 기술적 트레이드오프 분석을 담당.

**핵심 규칙:**
- 모든 결정은 `specs/platform/architecture-decision-rule.md` 우선 준수
- 레이어 의존성 방향 위반 및 도메인 로직의 `libs/` 유출 여부 검증
- 아키텍처 결정 시 ADR을 `.claude/templates/adr-template.md` 형식으로 작성
- 문서화되지 않은 아키텍처 결정을 단독으로 내리지 않음
- 스펙 누락 또는 충돌 시 즉시 중단하고 보고

**참조 스킬:** `.claude/skills/` (아키텍처 관련 스킬), `knowledge/` 설계 참고 자료

**참조 워크플로우:** CLAUDE.md Required Workflow 1–3단계, `specs/platform/entrypoint.md` 스펙 읽기 순서

**위임 관계:**
- 위임 대상: 없음 (설계 문서 작성만 수행)
- 위임 수신: `coordinator`로부터 아키텍처 판단 요청

---

### backend-engineer

Spring Boot 백엔드 서비스 구현 전문가.

**핵심 규칙:**
- 레이어 의존성 방향 엄수: Controller → Application Service → Domain → Infrastructure
- H2 사용 금지 — 테스트에 실제 DB(Testcontainers) 사용
- 계약에 정의되지 않은 API 추가 금지
- Command/Result, Request/Response 네이밍 패턴 준수 (`specs/platform/naming-conventions.md`)
- 도메인 로직을 `libs/`로 이동 금지

**참조 스킬:**
- `backend/implementation-workflow`
- `backend/springboot-api`
- `backend/testing-backend`

**참조 워크플로우:** CLAUDE.md Required Workflow 1–3단계, `specs/services/<service>/architecture.md`, `specs/contracts/`

**위임 관계:**
- 위임 대상: 없음 (직접 구현)
- 위임 수신: `coordinator` 또는 `api-designer`/`database-designer` 완료 후 구현 착수

---

### frontend-engineer

Next.js (App Router) 프론트엔드 애플리케이션 구현 전문가.

**핵심 규칙:**
- Feature-Sliced Design 적용 시 features 간 직접 임포트 금지 — entity 또는 widget 경유
- 페이지에 비즈니스 로직 배치 금지 — feature의 model/lib 사용
- Server Component 기본, 인터랙티브 요소에만 `'use client'` 추가
- 계약에 정의되지 않은 API 호출 금지
- 서버 데이터를 전역 상태로 관리 금지 (TanStack Query 사용)

**참조 스킬:**
- `frontend/architecture/feature-sliced-design`
- `frontend/architecture/layered-by-feature`

**참조 워크플로우:** CLAUDE.md Required Workflow 1–3단계, `specs/services/<app>/architecture.md`, `specs/contracts/`

**위임 관계:**
- 위임 대상: 없음 (직접 구현)
- 위임 수신: `coordinator` 또는 `api-designer` 계약 완료 후 병렬 구현 가능

---

### code-reviewer

구현된 코드의 품질, 보안, 성능, 컨벤션 준수 여부를 검증하는 리뷰 전문가.

**핵심 규칙:**
- 읽기 전용 — 코드를 직접 수정하지 않음
- 발견 사항을 Critical / Warning / Suggestion 3단계로 분류 보고
- 스펙과 충돌하는 구현은 반드시 Critical로 보고
- 프로젝트 컨벤션 위반만 지적 — 개인 스타일 선호 강요 금지
- 스펙 범위 외 기능 요구 금지

**참조 스킬:** 없음 (체크리스트 기반 독립 운영)

**참조 워크플로우:** `specs/services/<service>/architecture.md`, `specs/contracts/`, `specs/platform/error-handling.md`, `specs/platform/naming-conventions.md`

**위임 관계:**
- 위임 대상: 없음 (최종 단계 검증)
- 위임 수신: `coordinator`로부터 구현 완료 후 리뷰 요청, 또는 직접 호출

---

### qa-engineer

테스트 작성, 테스트 스위트 실행, 품질 검증 전문가.

**핵심 규칙:**
- `specs/platform/testing-strategy.md` 준수
- H2 금지 — 통합 테스트에 Testcontainers(PostgreSQL, Redis) 사용
- 테스트 데이터 격리: 테스트별 UUID 또는 고유 이메일 사용
- 테스트 메서드 네이밍: `{scenario}_{condition}_{expectedResult}`, DisplayName은 한국어
- 비즈니스 로직 수정 금지 — 테스트 코드만 작성

**참조 스킬:** `backend/testing-backend`

**참조 워크플로우:** CLAUDE.md Required Workflow 1–3단계, `specs/platform/testing-strategy.md`

**위임 관계:**
- 위임 대상: 없음 (직접 테스트 작성)
- 위임 수신: `coordinator`로부터 구현 완료 후 테스트 요청

---

### api-designer

REST API 계약을 설계하고 `specs/contracts/`에 문서화하는 전문가.

**핵심 규칙:**
- RESTful 리소스 중심 URL 설계
- 요청/응답 필드명은 계약에 정확히 정의 — 구현체가 반드시 일치해야 함
- `specs/platform/naming-conventions.md` 네이밍 규칙 준수
- `specs/platform/error-handling.md` 에러 응답 형식 준수
- 기존 계약의 breaking change는 사전 합의 없이 변경 금지

**참조 스킬:** 없음 (스펙 직접 참조)

**참조 워크플로우:** `specs/platform/versioning-policy.md`, `specs/platform/error-handling.md`, `specs/platform/naming-conventions.md`

**위임 관계:**
- 위임 대상: 없음 (설계 문서 작성만 수행)
- 위임 수신: `coordinator`로부터 API 계약 설계 요청; 완료 후 `backend-engineer`, `frontend-engineer` 구현 착수

---

### event-architect

도메인 이벤트와 메시징 패턴을 설계하고 `specs/contracts/`에 이벤트 계약을 문서화하는 전문가.

**핵심 규칙:**
- 이벤트 네이밍: `{Aggregate}.{PastTenseVerb}` 형식 (예: `Order.Created`)
- 이벤트 스키마 필수 필드: `event_id`, `event_type`, `occurred_at`, `source`, `payload`
- Outbox 패턴, 멱등 소비자, DLQ, 지수 백오프 재시도 설계 포함
- 이벤트 페이로드 스키마 소유 — DB 테이블 스키마는 `database-designer` 소유
- 기존 이벤트 계약의 breaking change는 사전 합의 없이 변경 금지

**참조 스킬:** 없음 (스펙 직접 참조)

**참조 워크플로우:** `specs/features/`, `specs/use-cases/`, 기존 이벤트 계약 패턴

**위임 관계:**
- 위임 대상: 없음 (설계 문서 작성만 수행); DB 테이블 DDL은 `database-designer`에 위임
- 위임 수신: `coordinator`로부터 이벤트 계약 설계 요청; 완료 후 `backend-engineer` 구현 착수

---

### database-designer

DB 스키마 설계, 마이그레이션 전략, 인덱스 최적화 전문가.

**핵심 규칙:**
- 테이블명 snake_case 복수형, 컬럼명 snake_case
- PK는 `id` (UUID 또는 BIGINT — 서비스 정책 따름), 타임스탬프는 `timestamptz`
- 롤백 가능한 마이그레이션 선호 — 데이터 파괴적 변경은 단계적 수행
- 복합 인덱스 컬럼 순서: 선택도 높은 컬럼 우선
- Outbox 테이블 DDL 소유 — 이벤트 페이로드 구조는 `event-architect` 소유

**참조 스킬:** 없음 (database 스킬 미작성 — `specs/platform/` 정책 직접 참조)

**참조 워크플로우:** `specs/services/<service>/architecture.md`, `specs/platform/` DB 관련 정책

**위임 관계:**
- 위임 대상: 없음 (직접 스키마 설계)
- 위임 수신: `coordinator`로부터 스키마 설계 요청; 완료 후 `backend-engineer` 구현 착수

---

### devops-engineer

인프라 구성, 컨테이너화, CI/CD 파이프라인, 배포 전략 전문가.

**핵심 규칙:**
- 시크릿을 코드에 하드코딩 금지
- 프로덕션에 직접 변경 적용 금지 — 모든 인프라 변경은 IaC(Terraform)를 통해서만
- 배포 전제 조건: 테스트 통과 필수
- 승인 없이 프로덕션 배포 실행 금지
- 스펙 누락 또는 충돌 시 즉시 중단하고 보고

**참조 스킬:** 없음 (스펙 직접 참조)

**참조 워크플로우:** `specs/platform/deployment-policy.md`, CLAUDE.md Hard Stop Rules

**위임 관계:**
- 위임 대상: 없음 (직접 인프라 구성)
- 위임 수신: `coordinator`로부터 인프라/배포 구성 요청

---

## 상세 정의

각 에이전트의 전체 시스템 프롬프트: `.claude/agents/<name>.md` 참조
