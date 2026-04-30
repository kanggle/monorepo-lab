---
name: global-account-platform
domain: saas
traits: [transactional, regulated, audit-heavy, integration-heavy, multi-tenant]
service_types: [rest-api, event-consumer, frontend-app]
compliance: [gdpr, pipa]
data_sensitivity: pii-sensitive
scale_tier: startup
taxonomy_version: 0.1
---

# global-account-platform

## Purpose

글로벌 계정/인증/보안 플랫폼. Weverse Account류의 실전형 백엔드 시스템으로, 다수의 하위 제품이 공유하는 **계정 인프라 레이어**를 목표로 한다. 회원가입, 로그인/로그아웃, JWT 발급과 refresh token 회전, 계정 상태(active/locked/dormant/deleted) 전이, 로그인 이력, 비정상 로그인 탐지, 관리자 강제 작업, Kafka 기반 보안 이벤트 처리, Redis 기반 rate limiting과 일시 인증 상태를 포함한다.

이 프로젝트는 백엔드 포트폴리오로서 **프로덕션 지향 설계**를 추구한다. 튜토리얼식 단순화는 피하고, 마이크로서비스 경계·이벤트 기반 분리·관측성·운영 가능성을 실제 수준으로 구현하는 것을 목표로 한다.

플랫폼은 **여러 제품(tenant)에 계정·인증을 공급하는 공유 인프라**로 동작한다. 1차 소비자는 B2C 팬플랫폼(`fan-platform`)이며, 2차 소비자로 B2B 내부 시스템인 WMS(`wms`)를 포함한다. 향후 ERP·SCM·MES 등 추가 엔터프라이즈 제품도 동일한 계정 게이트웨이를 사용한다. 자세한 테넌트 모델은 [specs/features/multi-tenancy.md](specs/features/multi-tenancy.md) 참조.

> `compliance`, `scale_tier`, `data_sensitivity` 필드는 [.claude/config/](.claude/config/)와 [rules/taxonomy.md](rules/taxonomy.md)에 enum이 등록되지 않은 **정보성 필드**이다. 값의 의미는 본 문서의 서술을 기준으로 한다.

## Domain Rationale

`saas`를 선택한 이유:

- 이 프로젝트는 특정 산업(금융·의료·이커머스 등)에 귀속되지 않는 **가로축 SaaS 인프라**. 계정·권한·감사·관리자 기능이 제품의 본질.
- `developer-platform`(Auth0/Clerk 계열)도 후보였으나, 이 프로젝트는 외부 개발자에게 SDK를 제공하는 형태가 아니라 **내부 제품군이 공유하는 플랫폼 레이어**이므로 saas 쪽이 더 정확.
- `fintech`는 계정 구조는 정렬되지만 실제 금융 도메인이 아니라 의미 과장 우려.

Bounded context는 [rules/domains/saas.md](rules/domains/saas.md)의 표준 구분(Identity / Account / Access / Security Analytics / Audit / Admin)을 따른다.

## Trait Rationale

- **transactional**: 계정 상태 전이(가입/잠금/삭제), refresh token 회전, 로그인 실패 카운트 결정 경로에서 강한 일관성과 멱등성 필요. Saga + idempotency + 상태 기계 패턴이 필수. 적용 대상: `auth-service`, `account-service`, `admin-service`, 조건부 `security-service`.
- **regulated**: 이메일, 전화, 생년월일, IP, 디바이스 ID 등 PII가 중심 자산. 패스워드 해시·2FA 시크릿 같은 sensitive PII도 다룸. GDPR/PIPA의 삭제·이식·보존 요건을 구조적으로 준수하도록 설계.
- **audit-heavy**: "누가 언제 이 계정에 접근했는가"는 보안·컴플라이언스의 핵심 질문. 로그인 시도·권한 변경·관리자 작업이 모두 불변 감사 로그에 기록되어야 함. 플랫폼의 본질적 요구사항으로 별도 옵션 아님.
- **integration-heavy**: 이메일/SMS 전송(가입 확인, 재설정, 비정상 로그인 알림), OAuth 제공자(Google/Apple/Kakao), 선택적 리스크 인텔리전스 API 등 외부 연동이 시스템 안정성의 핵심 변수. Circuit breaker, retry, DLQ, idempotent side-effect 패턴 반복 적용.
- **multi-tenant**: 단일 플랫폼이 다수의 제품(`fan-platform`, `wms`, 향후 `erp`/`scm`/`mes` 등)에 계정·인증을 공급한다. B2C 팬플랫폼과 B2B 내부 시스템(WMS)이 공존하므로 데이터는 `tenant_id` 컬럼 기반의 **row-level isolation**으로 격리한다. JWT에는 `tenant_id` claim이 포함되어 cross-tenant 요청을 거부할 수 있어야 하며, 테넌트별 역할 집합(B2C 사용자 역할 vs WMS의 WAREHOUSE_ADMIN/INBOUND_OPERATOR 등)을 독립적으로 관리한다. 격리 회귀 방지 테스트가 필수다.

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **marketplace** / **ecommerce**: 이 플랫폼은 상거래가 아닌 **계정 인프라**
- **real-time**: 서브초 반응성이 요구되지 않음. 초 단위 허용
- **read-heavy**: 쓰기 경로(로그인/상태 변경/감사)와 읽기 경로가 균형. 캐시는 최적화이지 아키텍처 드라이버가 아님
- ~~**multi-tenant**~~: WMS 연동 도입으로 **재분류됨** — `traits`에 `multi-tenant`로 승격(2026-04-29). 격리 전략·테넌트 모델은 [specs/features/multi-tenancy.md](specs/features/multi-tenancy.md) 참조
- **internal-system**: 외부 사용자 대상 (직원 전용 시스템 아님)
- **batch-heavy**: 배치 처리가 중심이 아님 (감사 로그 압축·보존 작업이 백그라운드로 존재할 수는 있음)
- **data-intensive**: 계정 데이터는 사용자당 구조적으로 작음. TB급 분석 플랫폼 아님
- **content-heavy**: 콘텐츠 자산이 없음
- **fan content / subscriptions / payments**: 팬 도메인 기능은 frozen demo consumer(`community-service`, `membership-service`)로만 존재. 신규 기능 확장은 PROJECT.md 스코프 재개방을 전제로 한다.

이 경계가 바뀌면 이 PROJECT.md의 traits를 수정하고 해당 [rules/traits/](rules/traits/) 파일을 로딩 범위에 포함시킬 것.

## Service Map (초기)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, JWT 검증, rate limiting, JWKS 캐시 |
| `auth-service` | rest-api | 로그인/로그아웃, JWT 발급, refresh token 회전·재사용 탐지, Redis 실패 카운트 |
| `account-service` | rest-api | 회원가입, 프로필, 계정 상태 기계 |
| `security-service` | event-consumer | Kafka 보안 이벤트 소비, 로그인 이력, 비정상 탐지, 소규모 read-only 감사 조회 HTTP 표면 |
| `admin-service` | rest-api | 운영자 lock/unlock, 강제 로그아웃, 감사 조회 프록시 |

상세 아키텍처는 각 서비스의 `specs/services/<service>/architecture.md`에서 선언된다.

### Tenant-aware 서비스 범위

| Service | 테넌트 인지 방식 |
|---|---|
| `gateway-service` | JWT `tenant_id` claim 검증 + 다운스트림으로 `X-Tenant-Id` 헤더 전파 |
| `auth-service` | 로그인 시 account-service에서 `tenant_id` 조회 → JWT payload에 claim 포함 |
| `account-service` | `accounts` 테이블에 `tenant_id` 컬럼 (필수). `domain/tenant/`로 테넌트 메타 관리 |
| `admin-service` | 운영 명령 시 대상 계정의 `tenant_id` 검증, 테넌트별 운영자 권한 분리 |
| `security-service` | 감사·보안 이벤트에 `tenant_id` 보존 |

WMS 같은 enterprise 소비자는 [specs/features/multi-tenancy.md](specs/features/multi-tenancy.md)에 정의된 internal provisioning API를 통해 사용자 생성/관리.

### Product-layer services (frozen)

| Service | Service Type | 상태 |
|---|---|---|
| `community-service` | rest-api | **FROZEN** — 플랫폼의 product-layer demo consumer. 신규 기능 태스크 발행 금지 |
| `membership-service` | rest-api | **FROZEN** — 플랫폼의 product-layer demo consumer. 신규 기능 태스크 발행 금지 |

두 서비스는 `account-service` 내부 API를 호출하여 플랫폼 레이어의 소비자로 동작하며, 포트폴리오의 integration 예시 역할만 수행한다. 스코프 재개방 없이 새 기능·태스크를 만들지 않는다.

## Overrides

현재 명시적 override 없음. 공통/도메인/특성 규칙을 모두 기본값대로 따른다.

예외가 필요한 경우 이 섹션에 다음 형식으로 기록:

```
- **rule**: rules/traits/<trait>.md#<rule-id>
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
```
