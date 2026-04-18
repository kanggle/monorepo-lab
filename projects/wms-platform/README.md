# wms-platform

창고 관리 시스템(Warehouse Management System). 백엔드 포트폴리오 프로젝트로, 물류 센터의 입고·재고·출고 전 과정을 spec-driven · task-driven 방식으로 구축한다.

핵심 기능: 입고 예정(ASN) 관리, 검수·적치, 로케이션 기반 재고 추적, 재고 조정·이동, 출고 오더 처리, 피킹·패킹·출하, 마스터 데이터 관리, 대시보드·KPI.

스택: Java 21 · Spring Boot 3 · Gradle multi-module · JPA + QueryDSL · MySQL · Redis · Kafka · Docker Compose · Prometheus/Grafana/Loki.

---

## Principles

- [PROJECT.md](PROJECT.md)가 프로젝트의 domain과 trait 분류를 선언한다 (taxonomy 기반 규칙 시스템)
- `specs/`가 공식 source of truth이다
- 작업은 `tasks/`를 통해 수행한다
- `tasks/ready/`에 있는 태스크만 구현 가능하다
- [CLAUDE.md](../../CLAUDE.md)가 최소 운영 규칙을 정의한다
- 서비스 내부 구조는 전역적으로 고정되지 않는다. 각 서비스의 아키텍처는 [specs/services/<service>/architecture.md](specs/services/)에 선언된다

---

## Document Roles

### `PROJECT.md`
프로젝트의 분류 — `domain`(하나)과 `traits`(다수)를 선언한다. 이 값에 따라 [rules/](../../rules/) 아래 어떤 규칙 계층이 활성화되는지가 결정된다.

### `CLAUDE.md`
AI 에이전트와 개발자가 따라야 할 최소 운영 규칙. "Project Classification (Read First)" 섹션이 규칙 계층 해결 순서를 정의한다.

### `specs/`
공식 프로젝트 규칙·컨트랙트·서비스 정의·기능 정의·교차 서비스 흐름.
- [platform/](../../platform/) — 모든 프로젝트에 공통된 기술 수준 규칙 (아키텍처·코딩·보안·테스트·관측성 등)
- [rules/](../../rules/) — taxonomy 기반 조건부 규칙 (`common.md` 인덱스, `domains/<domain>.md`, `traits/<trait>.md`)
- `specs/contracts/` — HTTP 및 이벤트 컨트랙트
- `specs/services/` — 서비스별 아키텍처·개요·경계
- `specs/features/`, `specs/use-cases/` — 기능 수준·유스케이스 스펙

### `.claude/skills/`
구현 가이드, 작업 패턴, 체크리스트.

### `knowledge/`
설계 판단·트레이드오프·모범 사례 참고 자료.

### `tasks/`
생명주기 상태로 관리되는 실행 가능한 작업 단위.

### `docs/`
사람을 위한 온보딩, 운영 가이드, 런북.

---

## Repository Structure

    wms-platform/
    ├── README.md
    ├── CLAUDE.md
    ├── PROJECT.md           ← 프로젝트 분류 (domain + traits)
    ├── .claude/
    ├── apps/                ← 서비스 구현
    │   ├── gateway-service
    │   ├── inbound-service
    │   ├── inventory-service
    │   ├── outbound-service
    │   ├── master-service
    │   ├── notification-service
    │   └── admin-service
    ├── libs/                ← 공유 기술 라이브러리
    ├── specs/
    │   ├── contracts/
    │   ├── services/
    │   ├── features/
    │   └── use-cases/
    ├── platform/            ← 기술 수준 공통 규칙
    ├── rules/               ← taxonomy 기반 규칙
    ├── knowledge/
    ├── tasks/
    ├── docs/
    ├── infra/               ← Prometheus / Grafana / Loki 설정
    └── scripts/

---

## Implementation Rule

기존 코드에서 시작하지 않는다. 다음 순서로 읽는다:

1. [CLAUDE.md](../../CLAUDE.md)
2. [PROJECT.md](PROJECT.md) — 이어서 [platform/entrypoint.md](../../platform/entrypoint.md)의 **Step 0**에 따라 활성 규칙 계층([rules/common.md](../../rules/common.md), [rules/domains/wms.md](../../rules/domains/wms.md), [rules/traits/](../../rules/traits/) 아래 선언된 trait 파일)을 로드
3. 대상 태스크 (`tasks/ready/`)
4. [platform/entrypoint.md](../../platform/entrypoint.md) — Core, Service-Type-Specific, Auxiliary 레이어
5. 관련 플랫폼 스펙
6. 대상 서비스 스펙
7. 관련 컨트랙트
8. 관련 기능 스펙과 유스케이스
9. [.claude/skills/](../../.claude/skills/)
10. [knowledge/](knowledge/) (필요 시)

[PROJECT.md](PROJECT.md)에 선언되지 않았거나 미확인 `domain`/`trait` 값은 [CLAUDE.md](../../CLAUDE.md)에 따라 Hard Stop.

---

## Service Architecture Rule

서비스 내부 구조는 의도적으로 전역 표준화하지 않는다.

각 서비스는 오직 자신의 `specs/services/<service>/architecture.md`에 선언된 아키텍처만을 따른다. 서비스마다 다른 아키텍처를 사용할 수 있다.

---

## Shared Library Rule

`libs/`는 재사용 가능한 기술·공통 코드 전용이다.

다음을 둘 수 없다:
- 서비스 특화 도메인 로직
- 서비스 소유의 비즈니스 규칙
- 서비스 특화 엔터티
- 서비스 사유 오케스트레이션 로직

참조: [platform/shared-library-policy.md](../../platform/shared-library-policy.md)

---

## Task Lifecycle

`backlog → ready → in-progress → review → done → archive`

`ready/`에 있는 태스크만 구현할 수 있다. 상세는 [tasks/INDEX.md](tasks/INDEX.md) 참조.

---

## Notes

- 스펙이 없거나, 불명확하거나, 충돌하면 중단하고 보고한다.
- 컨트랙트 변경이 필요하면 컨트랙트를 먼저 갱신한다.
- 서비스 아키텍처 변경이 필요하면 서비스 스펙을 먼저 갱신한다.
