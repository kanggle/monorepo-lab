# Common Rules Index

> **⚠️ 이 파일은 인덱스 전용입니다.**
> 규칙 문장을 여기에 작성하지 마십시오. 이 파일은 [platform/](../platform/) 아래의 canonical 규칙 파일을 가리키는 **포인터**일 뿐입니다.
> 중복이 발견되면 이 파일에서 삭제하고 원본 파일을 단일 진실 소스(SSOT)로 유지하십시오.

---

## Purpose

모든 프로젝트에 **항상 적용되는** 규칙 파일 목록. [README.md](README.md)의 resolution order에서 common 계층을 로드할 때 이 인덱스를 사용한다.

이 파일에 등록된 규칙은 [PROJECT.md](../PROJECT.md)의 domain/traits와 무관하게 기본 baseline으로 동작한다.

---

## Index

| # | 파일 | 범위 | 상태 |
|---|---|---|---|
| 1 | [../platform/architecture.md](../platform/architecture.md) | 플랫폼 전체 아키텍처(마이크로서비스 + K8s + HTTP/Events) 베이스라인 | canonical |
| 2 | [../platform/architecture-decision-rule.md](../platform/architecture-decision-rule.md) | 각 서비스의 내부 아키텍처 선언 의무(DDD / Hexagonal / Layered) | canonical |
| 3 | [../platform/service-boundaries.md](../platform/service-boundaries.md) | 서비스 간 책임 경계, 데이터 소유권, 호출 관계 규칙 | canonical |
| 4 | [../platform/dependency-rules.md](../platform/dependency-rules.md) | 모듈/서비스 의존성 방향 규칙 (gateway → services → libs) | canonical |
| 5 | [../platform/coding-rules.md](../platform/coding-rules.md) | Java 21/TypeScript strict 코딩 표준, 로깅, 예외, 검증 | canonical |
| 6 | [../platform/naming-conventions.md](../platform/naming-conventions.md) | PascalCase/camelCase/snake_case 및 패키지 네이밍 규칙 | canonical |
| 7 | [../platform/error-handling.md](../platform/error-handling.md) | JSON 에러 응답 포맷 및 HTTP 상태 코드 매핑 | canonical |
| 8 | [../platform/testing-strategy.md](../platform/testing-strategy.md) | Unit/Slice/Integration/Event 테스트 피라미드와 필수 범위 | canonical |
| 9 | [../platform/observability.md](../platform/observability.md) | 로그(SLF4J)·메트릭(Prometheus)·트레이싱(Jaeger) 규격 | canonical |
| 10 | [../platform/security-rules.md](../platform/security-rules.md) | JWT 인증/인가, 민감 데이터 처리, 비밀 관리 | canonical |
| 11 | [../platform/shared-library-policy.md](../platform/shared-library-policy.md) | `libs/` 사용 정책 — 기술적 재사용만 허용, 도메인 로직 금지 | canonical |
| 12 | [../platform/ownership-rule.md](../platform/ownership-rule.md) | 서비스·계약·스펙 소유권 및 변경 승인 책임 | canonical |
| 13 | [../platform/repository-structure.md](../platform/repository-structure.md) | 모노레포 디렉토리 레이아웃 고정 구조 | canonical |
| 14 | [../platform/versioning-policy.md](../platform/versioning-policy.md) | API/Event/Library 버전 관리 규칙 | canonical |

---

## Files Explicitly NOT in Common

다음 파일들은 common이 아니며, 해당 trait이 활성화된 프로젝트에서만 로드된다. 참조용으로 나열:

| 파일 | 활성화 조건 (trait) |
|---|---|
| [../platform/event-driven-policy.md](../platform/event-driven-policy.md) | trait에 의해 이벤트 발행/소비가 요구될 때 (예: `integration-heavy`, `real-time`) |
| [../platform/api-gateway-policy.md](../platform/api-gateway-policy.md) | 퍼블릭 API 노출이 있는 프로젝트(gateway 사용). 대부분의 외부 대면 서비스에 해당 |
| [../platform/deployment-policy.md](../platform/deployment-policy.md) | k8s 배포 환경 — 현재 저장소는 기본값이지만 로컬·서버리스 환경은 예외 |
| [../platform/refactoring-policy.md](../platform/refactoring-policy.md) | 리팩토링 작업을 수행할 때만 로드 |

이 매핑은 [../platform/entrypoint.md](../platform/entrypoint.md)의 Auxiliary 섹션과 [README.md](README.md)의 resolution order가 공동으로 관리한다.

---

## Change Protocol

- 새 common 규칙 파일을 [../platform/](../platform/) 아래 추가하면, 이 인덱스에 **같은 PR에서** 행을 추가한다.
- Common 규칙을 domain/trait 계층으로 "강등(demote)"하려면:
  1. 원본 파일을 그대로 유지하거나 rename 없이 남긴다 (이동/삭제 금지).
  2. 이 인덱스에서 행을 제거한다.
  3. [README.md](README.md)의 "Files Explicitly NOT in Common" 표에 추가한다.
  4. 해당 파일을 참조하는 [domains/](domains/) 또는 [traits/](traits/) 파일을 작성한다.
- 이 순서를 지키면 기존 `apps/`, `libs/`의 경로 참조가 깨지지 않는다.
