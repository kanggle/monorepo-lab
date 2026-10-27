# Task ID

TASK-MONO-820

# Title

라이브 데모의 «실적» 그래프 · 운영자 개요 «이번 주 vs 지난 주» 가 하루치뿐이다 — 시드에 부팅 시각 기준 최근 4주 과거 행(SCM · ERP · IAM 로그인 · WMS)

# Status

review

# Owner

devops

# Task Tags

- demo
- seed
- data

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (서비스 4개의 스키마 · 읽기 모델 · 테넌트 · 시간대 정합 + 시드 정책 예외 문서화)
> Drafted 2026-10-28 UTC. 출처: 소유자 질문 «로그아웃 버전 데이터 말고 DB 에도 데이터 추가했어?» → 조사(아래 AC-0) → 소유자 결정 2026-10-28: **범위 = SCM · ERP · IAM(로그인/잠금) · WMS 4개**, **날짜 = 부팅 시각 기준 최근 4주**.

# Dependency Markers

- 관련: `TASK-PC-FE-374`·`378`·`380`(같은 목적의 **익명 샘플** 픽스처 — 이 티켓은 그 라이브판), `TASK-PC-FE-379`(운영자 개요 headline 막대), `TASK-MONO-806`~`812`(도메인별 «실적» 스트립), `TASK-MONO-718`(ecommerce 테넌트 분리 — 이 티켓이 ecommerce 를 빼는 이유 중 하나).
- **배포 의존:** 시드 스크립트 변경은 **다음 AMI 굽기 이후** 데모에 반영된다(아래 AC-0). 라이브 측정(AC-6)은 그 뒤 창 — `TASK-MONO-672` § Order 에 측정 항목으로 등록한다.
- 범위 밖으로 둔 둘(소유자 결정): finance(잔액·원장 정합) · ecommerce(정책 금지 + 테넌트) — 후속이 필요하면 별도 티켓.

# Goal

로그인 콘솔(데모 스택, 테넌트 `demo-corp`)에서 SCM · ERP · IAM · WMS 개요의 «실적» 일별 그래프가 최근 4주에 걸친 막대를 보이고, 운영자 개요 카드 SCM(발주 금액) · ERP(승인) · IAM(로그인 실패) · WMS(출고 수량)의 «지난 주» 막대가 0 이 아니다. 데이터는 매 부팅 시 시드가 **부팅 시각 기준** 날짜로 수렴(idempotent)시킨다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 조사 — 2026-10-28 UTC, 조사 에이전트 보고; 🔴 착수 시 file:line 재확인):**
  - 여섯 지표 모두 **요청 시 행 단위 시각으로 KST 일 버킷을 자바에서 계산** — 미리 집계된 일 테이블을 읽지 않는다(예: `OrderJpaRepository.java:204-206`, `SecurityMetricsQueryService.java:36-37`). ⇒ 과거 시각의 행만 있으면 그래프에 나타난다. 저장은 올바른 UTC 순간이면 된다.
  - **SCM** `GET /api/v1/procurement/po/metrics`(`PurchaseOrderMetricsService.java:68-102`): `purchase_orders`(+ `purchase_order_lines`) · `po_status_history`(DB `scm_procurement`, 컨테이너 `scm-platform-postgres`). 일 컬럼 `submitted_at`, `status != CANCELED`, 입고 = PO 별 첫 `to_status='RECEIVED'` 이력의 `occurred_at`. 원본 테이블. 고유 `(tenant_id, po_number)`, NOT NULL `supplier_id, buyer_account_id, status, total_amount, currency, created_at, updated_at`(`V1__init.sql:42-64`). 테넌트 `demo-corp`(`seed-scm.sh:86`).
  - **ERP** `GET /api/erp/approval/metrics`(`ApprovalMetricsService.java:52-92`): `approval_request`(MySQL `erp_approval_db`, `erp-platform-mysql`). 일 컬럼 `submitted_at` · `finalized_at`(status APPROVED/REJECTED). NOT NULL `id, tenant_id, subject_type, subject_id, title, approver_id, submitter_id, status, created_at, updated_at`(`V1__init.sql:18-37`) + `current_stage_index`/`total_stages`(`V2`). 상세 화면 정합에는 `approval_route_stage`(`V2…:29-38`) · `approval_action` 행도. 상신자 ≠ 결재자. DATETIME(6), Hibernate `jdbc.time_zone: UTC`(`approval-service/application.yml:20,34`). 테넌트 `demo-corp`(`seed-erp.sh:139`).
  - **IAM** `GET /api/admin/metrics`(`AdminMetricsUseCase.java:64-108`): admin-service 는 데이터가 없고 security-service(`login_history` · `account_lock_history`, `iam-mysql` security_db)와 account-service 를 부른다. 일 컬럼 `occurred_at`, outcome SUCCESS/FAILURE, 테넌트 = `tenant_id` 또는 `site_tenant_id`(로그인) · `tenant_id`(잠금)(`SecurityMetricsQueryService.java:39-47`). 🔴 **UPDATE · DELETE 를 트리거가 막는다**(`V0002`; `V0010` 은 PII 갱신만 허용) ⇒ 행은 지울 수도 날짜를 옮길 수도 없다 — `event_id`(UNIQUE) 키 + `INSERT IGNORE`. `newAccount`(가짜 `accounts`)는 **넣지 않는다**(자격 없는 로그인 가능 신원). 현재 `seed-iam.sh` 없음(«iam 은 Flyway 가 전부 심는다», `seed.sh:48`).
  - **WMS** `GET /api/v1/admin/dashboard/metrics`(`WarehouseMetricsService.java:51-79`): **읽기 모델** `admin_putaway_fact`(일 컬럼 `completed_at`) · `admin_shipment_summary`(`shipped_at`)(admin_db, `wms-postgres`) — Kafka 소비자만 쓴다(`InboundProjectionService.java:244-268`, `OutboundProjectionService.java:175-233`). 🔴 원본 DB(inbound/outbound)에 넣으면 **그래프에 안 보인다**(이벤트 경유, 토픽 보존 7일). 읽기 모델에 직접: `admin_putaway_fact(event_id PK, warehouse_id, completed_at, qty_received)`(`V6…:29-35`), `admin_shipment_summary(shipment_id PK, order_id, warehouse_id, shipped_at, total_qty, last_event_at, …)`(`V2__init_readmodel.sql:248-260`). `/dashboard/throughput` 은 `admin_throughput_*_daily` 를 읽으므로 같이 맞춘다 — 출고는 `admin_rebuild_outbound_daily_kst()`(`V6…:61-83`), 입고는 재구성 함수 없음. 테넌트 필터 없음(`WarehouseMetricsService.java:29`).
  - **시드 규칙**(`infra/demo/seed/README.md`): API 로 되는 것은 API 로(`:14`); 직접 DB 는 «금지가 아니라 유료» — `dbexec --why "<막힌 것, 검증 가능>"` 필수, 없으면 거절(`:20-33`, `lib.sh:278-284`); 도메인 규칙 우회 · 불가능 상태 금지(`:129-131`); **시각 경계는 고정 리터럴**(`:157`) — 이 티켓이 예외를 만든다(아래 2); 매 부팅 재실행 · 수렴(`:77`, `:118`, `:137-146`). `dbexec` = `docker exec -i <container> psql|mysql …`, stdin SQL(`lib.sh:285-291`). 가드 (y) `verify-demo-wrapper.sh:1504-1517` 가 시드의 직접 `docker exec … psql/mysql` 을 막는다 — `dbexec` 만 허용.
  - **굽기:** AMI 는 이미지 + `repo_ref` 클론을 굽고(`demo-ami.pkr.hcl:238`), 시드는 **부팅 때** `demo-up.sh:422` → `seed/seed.sh` 로 돈다(구울 때 시드 없음 `:28-33`, 부팅 때 git pull 없음). ⇒ 데이터는 구워지지 않지만 **시드 변경은 재굽기 뒤에만** 반영. 볼륨은 정지/시작에도 남는다. 시드는 이미 13~14분(`demo-up.sh:160-177`) — 대량 SQL 은 싸고 API 반복은 비싸다.
- **1. 구현(도메인별, `infra/demo/seed/`):** 각 도메인 시드에 «최근 4주 과거 행» 단계를 `dbexec --why` 로 추가(IAM 은 새 `seed-iam.sh` + `seed.sh` 디스패치 등록). 날짜는 **부팅 시각 기준** `now() - N days` 계열로 계산하되 **행 id 는 날짜(KST 일)에서 결정적으로 유도**해 같은 날 재실행은 중복 없이 수렴(`ON CONFLICT DO NOTHING` / `INSERT IGNORE` / `WHERE NOT EXISTS`). 각 도메인 하루 0~수 건, 증감이 섞이게(완전 균일 금지 — `TASK-PC-FE-380` 교훈: 같은 값은 막대가 같아 보인다). 오늘(이번 주 창)의 기존 API 행과 겹치지 않게 **어제까지**만.
  - WMS: `admin_putaway_fact` · `admin_shipment_summary` 에 넣고 `admin_rebuild_outbound_daily_kst()` 호출, 입고 일 카운터는 같은 SQL 로 맞추거나 «재구성 함수 없음 → 직접 증분» 을 `--why` 에 적는다.
  - IAM: `login_history`(SUCCESS 다수 · FAILURE 소수) · `account_lock_history` 소수, 시드된 데모 계정에 귀속. 지울 수 없으므로 **부팅마다 새 날짜 행이 누적**된다 — 양(하루 수 건 × 4주)을 정하고 README 에 적는다.
- **2. 정책 문서:** `infra/demo/seed/README.md` 에 «시각 경계는 고정 리터럴» 의 **예외** 절 — 대상(과거 이력 행), 이유(그래프 창이 «오늘 기준 최근 N일»), 수렴 규칙(날짜 유도 id), IAM 누적 성질. 각 `dbexec --why` 문구가 검증 가능한 «막힌 것» 을 적는다(예: «과거 시각으로 PO 를 상신하는 API 가 없다»).
- **3. 시험(CI):** 새/바뀐 시드 SQL 이 문법적으로 맞고(`bash -n` 스윕은 자동), 날짜 유도 id 가 결정적이며 같은 입력 두 번 = 같은 행 집합임을 셸/SQL 단위로 보이는 시험 또는 가드 셀. 시드를 파싱하는 기존 시험(`ScmInboundExpectedDemoSeedShapeDltTest` 등 — 수정하는 파일을 읽는 것)이 초록. 새 시험이 시드를 읽으면 `build.gradle` 입력 + ci 경로 필터 등록(`scripts/check-outside-module-input-filter-coverage.sh`).
- **4. 측정 등록:** `TASK-MONO-672` § Order 에 «다음 굽기 뒤 창 — 820 AC-6» 항목.

## Out of Scope

- finance(직접 INSERT 는 `balances` · 원장 투영과 어긋남) · ecommerce(시드가 주문 직접 쓰기를 금지 + `EcommerceOrderDemoSeedTest` 고정 + `demo-corp` 세션은 ecommerce 테넌트 주문 0) — 소유자 결정으로 제외.
- IAM `newAccount`(가짜 계정).
- 시드 외 경로(Flyway 데모 프로필 시드) 로의 이전 — 검토만 하고 이번엔 `dbexec` 로 한다(소유자 결정 범위 안에서; 바꾸려면 재결정).
- 콘솔 코드 변경.

# Acceptance Criteria

- **AC-0** 위 사실을 착수 시점 코드로 file:line 재확인하고, 어긋난 것이 있으면 이 절에 정정 기록 후 진행(어긋남이 범위를 바꾸면 STOP · 보고).
- **AC-1** SCM · ERP · IAM · WMS 시드가 부팅 시각 기준 지난 28일(어제까지) 과거 행을 `dbexec --why` 로 넣고, 같은 날 두 번 돌려도 행 수가 같다(수렴 — 시험 또는 로컬 두 번 실행 증거).
- **AC-2** 각 도메인 metrics 응답(`period=month`)이 그 테넌트에서 0 아닌 날 ≥ 10, `period=week` 의 `previous` 가 해당 headline 지표(`poAmount:*` · `approved` · `loginFailure` · `qtyShipped`)에서 0 이 아니다 — 로컬 데모 스택(가능하면) 또는 SQL 로 계산한 기대값으로.
- **AC-3** WMS `/dashboard/throughput` 의 일 카운터가 metrics 와 같은 날에 같은 값(출고 재구성 · 입고 증분).
- **AC-4** README 예외 절이 있고 각 `--why` 가 검증 가능한 사유를 적는다. 가드 (y) · `bash -n` 스윕 · 시드 파싱 시험 초록, CI 초록.
- **AC-5** IAM 누적량이 README 에 적힌 상한 안이다(부팅 n 회 → 행 수 식).
- **AC-6 (라이브, 다음 굽기 뒤 창)** 로그인 콘솔 `demo-corp`: SCM · ERP · IAM · WMS 개요 «실적» 월간 막대 ≥ 10일, 운영자 개요 네 카드의 «지난 주» 막대 폭 > 0. 못 재면 ⚪ + 사유. `TASK-MONO-672` 에 등록.

# Related Specs

- `infra/demo/seed/README.md`(정책 — 갱신 대상)
- `platform/contracts/performance-metrics-contract.md`(지표 정의 — 읽기 전용)

# Related Contracts

- 각 도메인 metrics 엔드포인트 계약 — 읽기 전용(변경 없음).

# Edge Cases

- 부팅이 KST 자정 직후 → «어제까지» 경계를 KST 로 계산(시드 셸은 UTC 일 수 있다 — 명시적으로 `Asia/Seoul`).
- 오래 켜 둔 볼륨 재부팅 → 새 날짜 행만 추가되고 옛 행은 그래프 창 밖으로 밀린다(SCM · ERP · WMS 는 수렴, IAM 은 누적).
- order-service 와 달리 이 네 서비스의 시간대: ERP · IAM MySQL DATETIME(6) = UTC 벽시계, SCM · WMS Postgres TIMESTAMPTZ — 컨테이너 TZ 를 착수 시 라이브로 확인(조사에서 미확인).
- WMS 읽기 모델 행이 원본(outbound/inbound) 에 대응 행이 없다 — 상세 링크가 원본을 찾는 화면이 있으면 404 가 날 수 있다 → 착수 시 화면 경로 확인, 필요하면 해당 링크가 없는 id 체계로.

# Failure Scenarios

- WMS 원본 DB 에 넣는다 → 그래프에 안 보임(이벤트 경유).
- 날짜를 고정 리터럴로 박는다 → 몇 주 뒤 창 밖으로 밀려 다시 빈다.
- IAM 행을 «지우고 다시» 하려 한다 → 트리거가 막아 시드 실패(부팅 실패).
- 모든 날 같은 값 → 막대가 같아 보인다(`TASK-PC-FE-380`).
- `--why` 없는 직접 `docker exec psql` → 가드 (y) 빨강.

# Implementation Notes (2026-10-28 UTC)

- **AC-0 재확인 — 정정 5건(나머지 일치):** ① IAM 귀속: 데모 운영자 demo@ · requester@ 의 콘솔 자격증명은 tenant `iam`(auth `R__01_seed_demo_single_identity_credentials.sql` · `R__seed_demo_second_operator_credential.sql`)이라 demo-corp 지표에 안 잡힌다 → demo-corp 의 유일한 실재 계정 `newhire@demo-corp.example`(`0199de70-0000-7000-8000-00000000ad07`, account `R__07`)에 귀속, 가짜 계정 0. ② WMS 입고 재구성 함수는 **있다**(`admin_rebuild_inbound_daily_kst()`, `V6…:106-152`) — 단 사실 < 카운터인 볼륨에서 `RAISE EXCEPTION` 이라 부팅 경로에선 부르지 않고 «새로 들어간 사실만» CTE `RETURNING` 으로 증분. ③ `admin_shipment_summary.tenant_id` 열 존재(V3) — 목록은 거르고 지표는 안 거른다. ④ **ERP 시드 결함 발견·수선(범위 확장, 같은 원인):** 결재 목록은 정렬 없는 `PageRequest.of(page,size)` 인데 `seed-erp.sh` 가 `?size=100` 한 번 조회로 «제목 탐지» 와 §9 «대기 행 수 = 결재함 원소 수» 를 했다 — 백필로 100건을 넘으면 중복 생성 · 거짓 실패 → `erp_requests_all`(전 페이지 + id 중복 제거), 목록을 못 읽으면 POST 대신 `seed_fail`. ⑤ **시간 예산:** 시드 전체가 `demo-up.sh:190` `POST_UP_BUDGET=240s` 하나(순서 iam → wms → scm → finance → erp) — 백필을 각 시드 끝이 아니라 필요한 id 가 준비되는 가장 이른 자리에 둠(scm §0b · erp §4c · wms §1c), IAM Flyway 대기 60s.
- **구현:** 신규 `infra/demo/seed/lib-backfill.sh`(생성기 + `--self-test`) · `infra/demo/seed/seed-iam.sh`; 수정 `seed-scm.sh` §0b · `seed-erp.sh` §4c + `erp_requests_all` · `seed-wms.sh` §1c · `seed.sh`(주석 — 디스패치는 파일 존재로 자동) · `README.md`(§ 시각 경계 — 예외) · `verify-demo-wrapper.sh` (y2). 창 = KST(+9h 고정 산술) 어제까지 28일, 행 내용 = 그 행의 KST 날짜 + 그날 순번만으로 결정, 마지막 사건까지 어제 이전인 행만, headline 일별 ≥ 1 · 날마다 다름 · 주간 증감 혼재. UUID 꼴 id `00000820-<코드>-YYYY-MMDD-<순번12>`(36자 — `login_history.event_id VARCHAR(36)` 에 맞음, 메인 세션이 생성 줄로 확인). 넣은 뒤 창 안 행 수를 계획 수와 대조해 로그(생성 / 존재 +0 / ⚠ / ✗).
  - SCM: `purchase_orders` + `purchase_order_lines` + `po_status_history`(`PoStatusMachine` 허용 경로만), 하루 1~4건, 상태 SUBMITTED · ACKNOWLEDGED · CONFIRMED · RECEIVED(다음 날 SYSTEM) · CANCELED(대조군), `po_number` = `PO-BF-YYYYMMDD-NN`, `origin` OPERATOR, 목적지 컬럼 NULL(V4 CHECK 허용) — 메인 세션이 Flyway V1 · V2 · V4 · V5 와 컬럼·제약 대조. 아웃박스 우회(의도 — 옛 PO 가 wms inbound-expected 를 만들지 않음).
  - ERP: `approval_request` + `approval_route_stage` + `approval_action`, 하루 1~3건(순번 0 = APPROVED), 상신자 이운영 · 결재자 김본부 사원, UTC DATETIME(6). 감사 로그 · 아웃박스 행 없음.
  - IAM: `login_history`(SUCCESS 2~7 · FAILURE 1~3/일) · `account_lock_history`(약 9일에 1회), `INSERT IGNORE` on `event_id`. **누적 상한(README):** 하루 ≤ 11행, 총량 ≤ 11 × (28 + 첫~마지막 부팅 사이 날 수).
  - WMS: `admin_putaway_fact` · `admin_shipment_summary` 하루 1~3건씩 두 창고, 출고 카운터 `admin_rebuild_outbound_daily_kst()` · 입고 카운터 새 사실 증분. 콘솔 출하 행은 링크가 없어(`WmsRecentShipments.tsx` · `WmsShipmentsDataTable.tsx`) 원본 없는 id 로 404 에 닿는 경로 없음.
- **`--why`(네 도메인)** — 각 «과거 시각으로 X 하는 API 가 없다 + 지표가 어느 열을 KST 일로 자르는가 + 수렴 방식» 형식으로 시드에 원문(SCM `PurchaseOrderApplicationService` 서버 시계 · ERP `ApprovalApplicationService` · IAM Kafka 소비자 전용 + tenant `iam` 사정 · WMS Kafka 투영 전용 · 토픽 7일).
- **검증(로컬, Docker 데몬 없음 — 실제 DB 미실행):** `lib-backfill.sh --self-test` rc=0 44칸(메인 세션 재실행 PASS) — 달력(윤년, 800일 왕복) · KST 자정 경계 · 같은 부팅 시각 → 다른 프로세스에서도 SQL 바이트 동일 · 3일 뒤 부팅도 겹치는 날 행 동일 · 모든 INSERT 에 수렴 절 · 계획 수 = INSERT 수 · headline 매일 ≥ 1 · 증감 혼재 · 빈/자기결재 인자 거절. bite 변형 6종(RANDOM · 오늘 포함 · 증분을 전체 표로 · IAM 실패 상수화 · 부팅 날짜 의존 번호 · 수렴 절 제거) 전부 rc=1. DB 스텁 하네스: 1회 생성 → 2회 +0 → 3일 뒤 새 날짜만, KST 자정 직후 부팅 동일. `bash -n` 변경 sh 전부 rc=0(메인 세션 재실행). 자바 `ScmInboundExpectedDemoSeedShapeDltTest` 4/4 · `EcommerceSeedParityTest` 5/5(`--offline --rerun-tasks`). scripts/ 가드 7개 rc=0(scripts/ 파일 추가 없음, 새 Gradle 입력 없음). 전체 `verify-demo-wrapper.sh` 는 Docker 필요 — (y)+(y2) 구간만 rc=0.
- 🔴 **측정 조건(AC-2 · AC-6):** `period=month` 는 **달력월** — «0 아닌 날 ≥ 10» 은 매월 11일 이후에만 성립한다. 지난달 창(약 59일 전까지)은 28일 백필로 대부분 비어 있다. 지난 주 비교 창은 같은 경과 시각에서 끊겨 월요일 01시(IAM · WMS) / 07시(SCM · ERP) KST 이전엔 «지난 주» 가 0 일 수 있다.
- **AC-1 ~ AC-5 상태:** 로컬 증거로 닫을 수 있는 칸(생성기 결정성 · 수렴 · README · 가드)은 위 증거, **실제 DB 실행 · 행 수 불변 · 화면 값은 다음 굽기 뒤 창**(`TASK-MONO-672` § Order 21 에 등록). ⇒ review 에 둔다.
- **남은 위험:** IAM 행은 지울 수 없어 생성기를 나중에 바꾸면 옛 판 행이 남는다(사후 대조 ⚠). 백필 ACKNOWLEDGED PO 를 콘솔에서 확정하면 실제 아웃박스가 나간다(API 시드 PO 와 같은 동작). ERP 목록 무정렬 페이지 경계에서 «빠짐» 은 못 막는다(겹침은 id 중복 제거로 막음). `dbexec` mysql 경로는 stderr 를 버려 ERP/IAM 실패 원인은 SQL 을 손으로 다시 흘려야 보인다.
