# platform/ — Reusable Platform Specs

이 디렉터리는 **템플릿 인프라**입니다. 여기 있는 파일들은 프로젝트 특정이 아닌 **기술 수준 공통 규칙**을 담고 있으며, 모든 프로젝트에 그대로 재사용됩니다.

> 🛑 이 디렉터리는 `specs/`(프로젝트 콘텐츠)와 분리되어 있습니다. 프로젝트 특화 내용은 여기에 쓰지 마세요.

---

## What Lives Here

| 파일 | 범위 |
|---|---|
| [entrypoint.md](entrypoint.md) | 스펙 읽기 순서 — 모든 구현 작업의 시작점 |
| [architecture.md](architecture.md) | 플랫폼 전체 아키텍처 베이스라인 |
| [architecture-decision-rule.md](architecture-decision-rule.md) | 각 서비스의 내부 아키텍처 선언 의무 |
| [service-boundaries.md](service-boundaries.md) | 서비스 간 책임 경계와 호출 관계 규칙 |
| [dependency-rules.md](dependency-rules.md) | 모듈/서비스 의존성 방향 규칙 |
| [coding-rules.md](coding-rules.md) | Java/TypeScript 코딩 표준 |
| [naming-conventions.md](naming-conventions.md) | 식별자·패키지·파일 네이밍 규칙 |
| [error-handling.md](error-handling.md) | JSON 에러 포맷, HTTP 상태 매핑, 표준 에러 코드 |
| [testing-strategy.md](testing-strategy.md) | 테스트 피라미드와 필수 범위 |
| [observability.md](observability.md) | 로그·메트릭·트레이싱 규격 |
| [security-rules.md](security-rules.md) | JWT·비밀 관리·민감 데이터 처리 |
| [shared-library-policy.md](shared-library-policy.md) | `libs/` 사용 정책 — 기술 재사용만 허용 |
| [ownership-rule.md](ownership-rule.md) | 서비스·계약·스펙 소유권 |
| [repository-structure.md](repository-structure.md) | 모노레포 디렉터리 레이아웃 |
| [versioning-policy.md](versioning-policy.md) | API/Event/Library 버전 관리 |
| [event-driven-policy.md](event-driven-policy.md) | 이벤트 발행·소비 패턴, DLQ·재시도 정책 |
| [api-gateway-policy.md](api-gateway-policy.md) | 퍼블릭 API 노출·라우팅 규칙 |
| [deployment-policy.md](deployment-policy.md) | k8s 배포 환경 정책 |
| [refactoring-policy.md](refactoring-policy.md) | 리팩토링 작업 기준 |
| [glossary.md](glossary.md) | 플랫폼 공통 용어집 |
| [service-types/](service-types/) | service-type별 파일 (rest-api, event-consumer, batch-job, grpc-service, graphql-service, ml-pipeline, frontend-app) |

---

## Editing Policy

### ❌ 개별 프로젝트에서 편집 금지

- **원칙 수준 규칙**: architecture.md, testing-strategy.md, shared-library-policy.md, security-rules.md, dependency-rules.md, naming-conventions.md, coding-rules.md 등
- **서비스 타입 정의**: service-types/ 하위 모든 파일

개선이 필요하면 **템플릿 저장소를 먼저 수정**한 뒤 다른 프로젝트에 back-port. 개별 프로젝트에서 편집하면 template drift가 즉시 발생합니다.

### ⚠️ 제한적 편집 허용

- **예시 섹션**: error-handling.md의 도메인별 에러 코드 섹션은 프로젝트 도메인에 맞게 교체 가능
- **도메인 예시**: architecture.md·glossary.md의 예시 서비스 이름은 프로젝트에 맞게 참고 가능

편집 시에는 [../TEMPLATE.md](../TEMPLATE.md)의 back-porting 절차를 따를 것.

---

## Reading Order

모든 구현 작업은 [entrypoint.md](entrypoint.md)에서 시작합니다. entrypoint가 정의하는 3개 레이어:

1. **Core** — 모든 작업에서 항상 로드
2. **Service-Type-Specific** — 대상 서비스의 `architecture.md`에서 선언한 Service Type에 해당하는 하나의 파일
3. **Auxiliary** — 태스크 태그(api/event/deploy/test/adr 등)에 따라 조건부로 로드

---

## Related

- [../rules/](../rules/) — taxonomy 기반 domain/trait 규칙 (조건부 로드, 프로젝트 선언에 따라 활성화)
- [../rules/common.md](../rules/common.md) — 이 디렉터리의 14개 canonical 파일을 가리키는 인덱스
- [../CLAUDE.md](../CLAUDE.md) — AI 에이전트·개발자 최소 운영 규칙
- [../TEMPLATE.md](../TEMPLATE.md) — 템플릿 사용 가이드 (template vs project content 경계, 새 프로젝트 시작 절차)
