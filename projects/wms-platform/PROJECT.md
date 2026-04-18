---
name: wms-platform
domain: wms
traits: [transactional, integration-heavy]
service_types: [rest-api, event-consumer]
compliance: []
data_sensitivity: internal
scale_tier: startup
taxonomy_version: 0.1
---

# wms-platform

## Purpose

창고 관리 시스템(Warehouse Management System). 입고·재고·출고 전 과정을 체계적으로 관리하는 백엔드 플랫폼으로, 물류 센터 운영에 필요한 **로케이션 기반 재고 추적**, **입출고 오더 처리**, **피킹/패킹/출하 워크플로**를 핵심으로 한다.

이 ��로젝트는 백엔드 포트폴리오로서 **프로덕션 지향 설계**를 추구한다. 튜토리얼식 단순화는 피하고, 마이크로서비스 경계·이벤트 기반 분리·관측성·운영 가능성을 실제 수준으로 구현하는 것을 목표로 한다.

## Domain Rationale

`wms`를 선택한 이유:

- 이 프로젝트는 물류 센터의 **입고 → 적치 → 보관 → 피킹 → 패킹 → 출하** 전 과정을 다루는 창고 관리 시스템이다.
- `logistics`(물류 전반)보다 범위가 좁고, `ecommerce`(상거래)와는 관심사가 다르다. WMS는 "물건이 창고 안에서 어떻게 움직이는가"에 집중한다.
- `mes`(제조 실행)는 생산 공정에 초점이 맞춰져 있어 이 프로젝트의 성격과 다르다.

Bounded context는 [../../rules/domains/wms.md](../../rules/domains/wms.md)의 표준 구분(Inbound / Inventory / Outbound / Master Data / Admin)을 따른다.

## Trait Rationale

- **transactional**: 재고 이동(입고 적치, 피킹 차감, 재고 조정)은 강한 일관성과 멱등성이 필수. 동시 피킹 시 재고 정합성, 입고 검수 후 적치 확정, 출하 확인 후 재고 차감 등 모든 핵심 경로에서 트랜잭션 보장이 요구된다. 적용 대상: `inventory-service`, `inbound-service`, `outbound-service`.
- **integration-heavy**: ERP 연동(입고 예정/출고 오더 수신), TMS 연동(출하 정보 전달), 바코드/RFID 스캐너 인터페이스, 외부 알림(슬랙/이메일) 등 외부 시스템과의 연동이 안정성의 핵심 변수. Circuit breaker, retry, DLQ, idempotent side-effect 패턴 반복 적용.

## Service Map (초기)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, 인증 검증, rate limiting |
| `inbound-service` | rest-api | 입고 예정(ASN) 관리, 검수, 적치 지시 |
| `inventory-service` | rest-api | 로케이션 기반 재고 관리, 재고 조정, 재고 이동, 실시간 재고 조회 |
| `outbound-service` | rest-api | 출고 오더, 피킹 지시/확인, 패킹, 출하 확인 |
| `master-service` | rest-api | 창고, 구역(zone), 로케이션, SKU 마스터 데이터 관리 |
| `notification-service` | event-consumer | Kafka 이벤트 소비, 알림 발송(슬랙/이메일), 이벤트 로그 |
| `admin-service` | rest-api | 대시보드, KPI 조회, 사용자/권한 관리 |

상세 아키텍처는 각 서비스의 `specs/services/<service>/architecture.md`에서 선언���다.

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **ecommerce** / **marketplace**: 이 플랫폼은 상거래가 아닌 **창고 내부 물류 관리**
- **real-time**: WebSocket 기반 실시간 UI 푸시는 초기 범위에서 제외. REST 폴링으로 충분
- **batch-heavy**: 대량 배치 처리(월말 재고 실사 등)는 존재할 수 있으나 아키텍처 드라이버가 아님
- **regulated**: 법적 컴플라이언스 요건이 중심이 아님. PII 없음
- **audit-heavy**: 재고 변동 이력은 추적하나, 법적 감사 수준의 불변 로그는 초기 범위 밖
- **multi-tenant**: 단일 물류 센터 가정. 멀티 테넌트 확장 시 trait 재분류
- **data-intensive**: 재고 데이터는 SKU당 구조적으로 작음. TB급 분석 플랫폼 아님
- **read-heavy**: 쓰기 경로(입출고/재고 이동)와 읽기 경로가 균형

이 경계가 바뀌면 이 PROJECT.md의 traits를 수정하고 해당 [../../rules/traits/](../../rules/traits/) 파일을 로딩 범위에 포함시킬 것.

## Overrides

현재 명시적 override ��음. 공통/도메인/특성 규칙을 모두 기본��대로 따른다.

예외가 필요한 경우 이 섹션에 다음 형식으로 기록:

```
- **rule**: rules/traits/<trait>.md#<rule-id>
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
```
