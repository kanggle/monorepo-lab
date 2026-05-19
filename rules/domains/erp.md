# Domain: erp

> **Activated when**: `PROJECT.md` declares `domain: erp`.

---

## Scope

Enterprise Resource Planning (전사 기간계) — 조직 마스터데이터·결재 워크플로·통합 조회 read model 을 다루는 사내 기간 업무 시스템. 핵심은 **마스터데이터의 무결성·결재 상태 전이의 추적가능성·통합 read model 의 책임 경계** — 마스터데이터는 단일 진실 원천으로 일관되게 유지되고, 모든 결재·마스터 변경은 불변 감사 기록을 남기며, 통합 조회는 도메인 비즈니스 로직을 소유하지 않고 다른 시스템이 소유한 사실을 합성만 한다.

이 도메인은 "조직의 기준정보(부서/직원/직급/비용센터/거래처 등)가 정확하고 일관적인가, 결재가 누구에 의해 어떤 상태로 전이되었는가, 그리고 흩어진 도메인 사실을 한 화면으로 통합 조회할 때 그 책임 경계가 분명한가"에 집중한다. 본 도메인은 **사내 임직원 전용**(외부 공개 트래픽 없음)이라는 internal-system 경계를 전제로 하며, 인증은 SSO, 인가는 권한 매트릭스, 모든 접근은 운영 추적 가능해야 한다.

전통 ERP 의 광의 범위(총계정원장·매입채무·조달·재고·HR 깊이)는 erp 의 *모듈 확장* 영역이다 — 본 도메인 룰은 마스터데이터·결재 워크플로·통합 read model 을 우선 강제하고, 회계/조달/재고/주문 같은 도메인 비즈니스 로직은 **각 도메인 시스템이 소유**한다는 책임 경계를 E5 에서 명시적으로 못박는다. erp 가 어떤 도메인 깊이를 자체 모듈로 들이는 경우 본 파일에 규칙을 추가한다.

---

## Bounded Contexts (표준)

erp 도메인 프로젝트가 일반적으로 가지는 bounded context 묶음. 실제 서비스 분할은 트래픽·소유권·조직 경계에 따라 달라진다.

| Bounded Context | 책임 |
|---|---|
| **Master Data** | 조직 기준정보(부서 계층·직원 조직속성·직급·비용센터·거래처) 의 생성·수정·폐기, 참조 무결성, 유효기간(effective-dated) 관리 |
| **Approval Workflow** | 결재 요청의 라우팅(단계/대결/위임), 상태기계(기안→상신→승인/반려/회수→완료), 결재함 |
| **Integrated Read Model** | 흩어진 도메인 사실을 합성한 통합 조회 view. **비즈니스 로직 미보유** — 원천 시스템의 사실을 read-only 로 투영만 |
| **Organization / Permission** | 조직-역할-권한 매트릭스, SSO 신원 ↔ 내부 권한 매핑, 데이터 접근 범위(소속/조직 단위) 결정 |
| **Audit / Operations** | 마스터 변경·결재 전이·권한 변경의 불변 감사 기록, 운영자 검토 큐(예외 결재·권한 이상·마스터 충돌), 정책 설정 |

각 context 는 자체 데이터 저장소를 가지며, context 간 통신은 **이벤트** 또는 **잘 정의된 내부 HTTP** 로만 이루어진다. 다른 도메인 시스템(조달/재고/주문/회계 등)과의 통합은 반드시 read-only 합성 또는 이벤트 구독 adapter layer 를 거치고, 도메인 사실을 erp 가 권위적으로 변경하지 않는다.

---

## Ubiquitous Language

- **Master Data (기준정보)** — 조직 운영의 단일 진실 원천이 되는 참조 데이터. 부서·직원(조직속성)·직급·비용센터·거래처 등. 다른 데이터가 이를 참조한다.
- **Effective Dating (유효기간)** — 마스터 레코드의 유효 시작/종료. 과거 시점 조회는 그 시점에 유효했던 값으로 재현 가능.
- **Reference Integrity (참조 무결성)** — 마스터 간 참조(직원→부서, 비용센터→부서 등)가 항상 유효한 대상을 가리켜야 함. 참조 중인 마스터는 임의 삭제 금지(폐기/비활성으로만).
- **Approval Request (결재 요청)** — 승인을 받아야 하는 업무 단위. 상태기계: `DRAFT → SUBMITTED → (IN_REVIEW →) APPROVED`, 분기 `REJECTED` / `WITHDRAWN`.
- **Approval Route (결재선)** — 결재 요청이 거치는 결재자 단계 순서(1~N단계). 대결/위임 규칙 포함.
- **Approver / Delegate (결재자/대결자)** — 특정 단계의 승인 권한자, 부재 시 위임받은 대결자.
- **Approval Inbox (결재함)** — 결재자가 처리해야 할 미결/기결 요청 목록.
- **Integrated Read Model (통합 read model)** — 여러 도메인 시스템의 사실을 합성한 read-only 조회 모델. erp 는 이를 **소유 변경하지 않는다**(투영만).
- **Source of Record (사실 원천)** — 어떤 도메인 사실의 권위적 소유 시스템. 통합 read model 의 각 필드는 정확히 하나의 source of record 를 가진다.
- **Permission Matrix (권한 매트릭스)** — 역할 × 자원 × 행위 × 데이터 범위(소속 조직 단위)로 접근을 결정하는 인가 모델.
- **SSO Identity** — 사내 단일 인증으로 발급된 신원. 내부 권한 매트릭스와 매핑되어 인가에 사용.
- **Data Scope (데이터 범위)** — 사용자가 접근 가능한 조직 단위 범위(본인/팀/부서/전사). 권한 매트릭스의 한 축.
- **Audit Trail** — 마스터 변경·결재 전이·권한 변경에 영향을 준 모든 연산의 actor + timestamp + before/after 불변 기록.

이 용어들은 코드·API·문서에서 일관되게 사용되어야 한다.

---

## Standard Error Codes

erp 도메인에서 공통으로 발생하는 에러는 [../../platform/error-handling.md](../../platform/error-handling.md) 의 전역 카탈로그에 등록되어야 한다. 본 도메인 특유의 코드:

### Master Data
- `MASTERDATA_NOT_FOUND` — 존재하지 않는 마스터 레코드
- `MASTERDATA_REFERENCE_VIOLATION` — 다른 마스터가 참조 중인 레코드의 삭제/무효화 시도
- `MASTERDATA_DUPLICATE_KEY` — 마스터 자연키(코드 등) 중복
- `MASTERDATA_EFFECTIVE_PERIOD_INVALID` — 유효기간 모순(종료 < 시작) 또는 기간 겹침 위반
- `MASTERDATA_PARENT_CYCLE` — 계층 마스터(부서 등)의 순환 참조

### Approval Workflow
- `APPROVAL_REQUEST_NOT_FOUND` — 존재하지 않는 결재 요청
- `APPROVAL_STATUS_TRANSITION_INVALID` — 허용되지 않는 결재 상태 전이
- `APPROVAL_NOT_AUTHORIZED_APPROVER` — 현재 단계의 결재 권한자가 아님
- `APPROVAL_ROUTE_INVALID` — 결재선 구성 오류(단계 누락·자기 결재 등)
- `APPROVAL_ALREADY_FINALIZED` — 이미 완료/반려된 결재의 재처리 시도

### Integrated Read Model / Authorization
- `READ_MODEL_SOURCE_UNAVAILABLE` — 통합 조회의 원천 시스템 응답 불가
- `PERMISSION_DENIED` — 권한 매트릭스상 자원/행위 접근 거부
- `DATA_SCOPE_FORBIDDEN` — 요청 데이터가 사용자 데이터 범위(소속 조직 단위) 밖
- `EXTERNAL_TRAFFIC_REJECTED` — 외부(비-SSO·비-내부망) 접근 시도 거부

### Cross
- `OPERATION_NOT_PERMITTED` — 운영자/계정 권한 없음

---

## Integration Boundaries

### 외부 (플랫폼 경계 바깥)

- **SSO / 사내 IdP** — 임직원 단일 인증. erp 는 자체 비밀번호를 소유하지 않고 IdP 토큰을 검증한다.
- **다른 도메인 시스템 (read-only 합성·이벤트 구독)** — 조달/재고/주문/회계 등. erp 는 이들의 사실을 **read-only 로 합성**하거나 이벤트를 구독해 통합 read model 을 갱신하되, 그 도메인 사실을 권위적으로 변경하지 않는다.
- **알림 채널** — 결재 상신/승인/반려, 마스터 변경 통지, 권한 변경 알림.
- **사내망 경계** — 외부 공개 트래픽 없음. 게이트웨이/네트워크 정책에서 internal-only 를 강제한다.

### 내부 (같은 프로젝트 내 다른 서비스)

- gateway → 모든 service: SSO token 검증, 내부망 경계 강제, 권한 클레임 전파.
- approval → master data: 결재 대상이 참조하는 마스터(부서/비용센터 등) 유효성 확인.
- integrated read model ← 다른 도메인 시스템: 이벤트 구독 또는 read-only 조회로 투영 갱신(권위 변경 없음).
- permission → 모든 service: 역할·데이터 범위 결정.

### 내부 이벤트 카탈로그 (권장)

- `<prefix>.masterdata.created` / `.updated` / `.deactivated`
- `<prefix>.approval.submitted` / `.approved` / `.rejected` / `.withdrawn` / `.delegated`
- `<prefix>.permission.granted` / `.revoked` / `.role.changed`
- `<prefix>.readmodel.projection.updated` / `.source.unavailable`

---

## Mandatory Rules

### E1. 마스터데이터는 단일 진실 원천 + 참조 무결성
각 마스터(부서/직원/직급/비용센터/거래처 등)는 단일 권위 저장소를 가지며 자연키가 유일하다. 다른 마스터/레코드가 참조 중인 마스터는 물리 삭제하지 않고 폐기/비활성(논리 상태)으로만 전이한다. 참조 무결성 위반은 `MASTERDATA_REFERENCE_VIOLATION` 으로 거부한다. 계층 마스터는 순환 참조를 금지한다.

### E2. 마스터 변경은 유효기간 + 불변 감사 기록
마스터 레코드의 변경은 effective-dated 로 관리해 과거 시점 조회가 그 시점 값으로 재현 가능해야 한다. 모든 마스터 생성/수정/폐기는 actor + timestamp + before/after + 사유를 불변(append-only) 감사 저장소에 기록한다. 감사 기록의 사후 수정·삭제는 금지한다.

### E3. 결재 워크플로는 상태기계 + 권한 있는 결재자만 전이
결재 요청은 정의된 상태기계(`DRAFT → SUBMITTED → (IN_REVIEW →) APPROVED`, 분기 `REJECTED`/`WITHDRAWN`)로만 전이한다. 각 단계의 전이는 그 단계에 대해 권한이 있는 결재자(또는 위임받은 대결자)만 수행할 수 있고, 그 외에는 `APPROVAL_NOT_AUTHORIZED_APPROVER`. 자기 자신이 기안한 건의 자기 결재는 금지한다. 완료/반려된 결재는 immutable — 재처리는 새 요청으로만.

### E4. 결재 전이는 멱등 + 불변 감사 기록
결재 상태 전이(상신/승인/반려/회수/위임)는 멱등하게 처리한다 — 동일 전이의 중복 요청은 최초 결과를 반환하고 상태를 재전이시키지 않는다. 모든 전이는 actor(결재자) + timestamp + 단계 + before/after + 사유(반려 시 필수)를 불변 감사 저장소에 기록한다.

### E5. 통합 read model 은 도메인 비즈니스 로직 미보유 — 책임 경계 준수
통합 조회 read model 은 다른 도메인 시스템(조달/재고/주문/회계 등)이 소유한 사실을 **read-only 로 합성·투영**만 한다. erp 는 그 도메인의 비즈니스 규칙을 재구현하거나 그 사실을 권위적으로 변경하지 않는다. 통합 read model 의 각 필드는 정확히 하나의 source of record 를 가지며, erp 는 그 원천을 신뢰 경계로 존중한다(원천 불가 시 `READ_MODEL_SOURCE_UNAVAILABLE`, 임의 추정/생성 금지).

### E6. 인가는 권한 매트릭스 + 데이터 범위 — fail-closed
모든 자원 접근은 역할 × 자원 × 행위 × 데이터 범위(소속 조직 단위) 권한 매트릭스로 인가한다. 권한 또는 데이터 범위가 불충분하면 `PERMISSION_DENIED` / `DATA_SCOPE_FORBIDDEN` 으로 거부한다(fail-open 금지). 인가 판단은 매 요청마다 수행하고, 권한이 모호하면 거부 쪽으로 닫는다.

### E7. internal-system 경계 — 외부 공개 트래픽 금지 + SSO 강제
서비스는 사내 임직원 전용이다. 인증은 SSO/사내 IdP 토큰 검증으로만 하고 자체 자격증명을 소유하지 않는다. 외부(비-내부망·비-SSO) 트래픽은 게이트웨이/네트워크 정책에서 거부(`EXTERNAL_TRAFFIC_REJECTED`)한다. 공개 자가가입·익명 접근 경로를 두지 않는다.

### E8. 권한·조직 변경은 운영 추적 가능 + 불변 기록
역할 부여/회수, 결재선/대결 위임, 데이터 범위 변경 등 인가에 영향을 주는 변경은 actor + timestamp + before/after + 사유를 불변 감사 저장소에 기록하고 운영 조회가 가능해야 한다. 권한 상승 경로는 항상 추적 가능해야 하며 무기록 변경 경로를 두지 않는다.

---

## Forbidden Patterns

- ❌ **참조 중인 마스터의 물리 삭제** (E1 위반) — 폐기/비활성 논리 전이만.
- ❌ **마스터 자연키 중복 / 계층 순환 참조 허용** (E1 위반).
- ❌ **마스터·결재·권한 변경의 감사 기록 누락 또는 사후 수정** (E2·E4·E8 위반).
- ❌ **권한 없는 사용자/단계의 결재 전이, 자기 결재** (E3 위반).
- ❌ **완료/반려 결재의 in-place 재처리** (E3 위반) — 새 요청으로만.
- ❌ **통합 read model 에서 도메인 비즈니스 로직 재구현 / 원천 사실의 권위적 변경** (E5 위반).
- ❌ **원천 시스템 불가 시 통합 조회 값을 임의 추정/생성** (E5 위반) — `READ_MODEL_SOURCE_UNAVAILABLE`.
- ❌ **인가 fail-open / 데이터 범위 무시 전사 조회** (E6 위반).
- ❌ **외부 공개 트래픽 허용 / 익명·자가가입 경로** (E7 위반) — internal-system 경계.
- ❌ **SSO 우회 자체 비밀번호 보관** (E7 위반).
- ❌ **무기록 권한 상승 경로** (E8 위반).

---

## Required Artifacts

1. **마스터데이터 모델 + 참조 무결성 맵** — 마스터 종류, 자연키, 마스터 간 참조 방향, 유효기간 정책. 위치: `specs/services/<masterdata-service>/data-model.md` 또는 `knowledge/architecture/masterdata-model.md`.
2. **결재 상태 다이어그램** — `DRAFT → SUBMITTED → (IN_REVIEW →) APPROVED` (+ `REJECTED` / `WITHDRAWN`), 결재선/대결/위임 규칙. 위치: `specs/services/<approval-or-masterdata-service>/state-machines/approval-status.md`.
3. **권한 매트릭스 모델** — 역할 × 자원 × 행위 × 데이터 범위, SSO 신원 ↔ 내부 권한 매핑. 위치: `specs/services/<permission-or-masterdata-service>/authorization-model.md` 또는 `knowledge/architecture/permission-matrix.md`.
4. **통합 read model 책임 경계 맵** — 각 통합 조회 필드의 source of record, 투영 갱신 방식(이벤트/조회), erp 가 변경하지 않는 경계. 위치: `specs/services/<read-model-service>/data-model.md` 또는 `knowledge/architecture/context-map.md`.
5. **internal-system 경계 정책** — SSO 검증, 내부망 강제, 외부 트래픽 거부 지점. 위치: `specs/services/<gateway-or-service>/security.md` 또는 `knowledge/architecture/internal-boundary.md`.
6. **에러 코드 등록** — 위 Standard Error Codes 가 [../../platform/error-handling.md](../../platform/error-handling.md) 에 존재.
7. **Bounded context 맵** — 위 contexts 의 데이터 소유와 통신 방향 도식. 위치: `specs/services/` 전반 또는 `knowledge/architecture/context-map.md`.

> **Library 경계**: 본 파일에는 구체 service 명을 직접 적지 않는다 — `<masterdata-service>` 등 placeholder 또는 bounded context 이름 사용. 실제 service 명은 각 프로젝트의 `PROJECT.md` Service Map 과 `specs/services/` 가 담당.

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md) 의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/error-handling.md](../../platform/error-handling.md) 에 위 Standard Error Codes 가 등록되어야 한다.
- [../traits/transactional.md](../traits/transactional.md) 의 트랜잭션·멱등성·상태기계 규칙이 E3·E4 (결재 전이) 와 E1 (마스터 변경 원자성) 에 직접 적용 (선언 시).
- [../traits/internal-system.md](../traits/internal-system.md) 의 RBAC·SSO·운영 추적·외부 노출 금지 규칙이 E6·E7·E8 에 적용 (선언 시).
- [../traits/audit-heavy.md](../traits/audit-heavy.md) 의 불변 감사 저장소·조회 API·보존 기간 규칙이 E2·E4·E8 에 적용 (선언 시).
- `integration-heavy` 미선언이라도 통합 read model 의 원천 시스템 호출은 read-only 경계 + 원천 불가 시 추정 금지를 E5 가 도메인 자체로 강제한다 (통합 조회가 도메인 책임 경계의 직접 위험 지점이기 때문).

---

## Checklist (Review Gate)

- [ ] 각 마스터가 단일 권위 저장소 + 유일 자연키를 가지며 참조 중 레코드가 물리 삭제되지 않는가? (E1)
- [ ] 마스터 변경이 effective-dated 이고 불변 감사 기록(actor/time/before/after)을 남기는가? (E2)
- [ ] 결재가 정의된 상태기계로만 전이되고 권한 있는 결재자만 전이하며 자기 결재가 차단되는가? (E3)
- [ ] 결재 전이가 멱등하고 모든 전이가 불변 감사 기록을 남기는가? (E4)
- [ ] 통합 read model 이 도메인 비즈니스 로직을 보유하지 않고 원천 사실을 read-only 투영만 하는가? 각 필드가 단일 source of record 를 가지는가? (E5)
- [ ] 모든 접근이 권한 매트릭스 + 데이터 범위로 fail-closed 인가되는가? (E6)
- [ ] 외부 공개 트래픽이 거부되고 SSO 가 강제되며 자가가입/익명 경로가 없는가? (E7)
- [ ] 권한·조직·위임 변경이 운영 추적 가능한 불변 기록을 남기고 무기록 권한 상승 경로가 없는가? (E8)
- [ ] 마스터 모델 + 결재 상태 다이어그램 + 권한 매트릭스 + 통합 read model 경계 + internal 경계 문서가 존재하는가?
- [ ] 표준 에러 코드가 플랫폼 카탈로그에 등록되어 있는가?
- [ ] 다른 도메인 시스템 통합이 read-only 합성/이벤트 구독 adapter 로 분리되어 도메인 코어에 침투하지 않는가?
