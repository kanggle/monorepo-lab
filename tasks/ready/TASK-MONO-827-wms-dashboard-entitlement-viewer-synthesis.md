# Task ID

TASK-MONO-827

# Title

`ADR-MONO-083` 잔여 — WMS admin-service 대시보드가 **구독만으로** 열린다(`entitled_domains ∋ wms` → `ROLE_WMS_VIEWER` 합성). ERP(793 Q1) · Finance(794)가 지운 합성이 WMS 에만 남았다 — 지울지, 지운다면 대시보드를 어느 업무 역할에 묶을지

# Status

ready

# Owner

monorepo

# Task Tags

- code
- api
- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한 게이트 · 프리셋 · 호출자 전수 · 원자 PR)
> 기안 2026-11-09 UTC. 출처: 37차 창 `TASK-PC-FE-364` AC-3 측정(`TASK-MONO-672` § 37th window — results, Findings 2) → 소유자 «진행».
> 🔴 **누가 무엇을 보는지가 바뀐다** — AC-0 측정 뒤 소유자 결정(§ 질문) 없이는 구현하지 않는다.

# Dependency Markers

- 선행: 없음(`TASK-BE-631` · `TASK-BE-632` · `TASK-MONO-793` · `TASK-MONO-794` 는 done).
- 형제(같은 원칙의 앞선 적용): `TASK-MONO-793`(ERP — 소유자 결정 Q1 «합성 제거») · `TASK-MONO-794`(Finance — § AC-0 § 4 «`FINANCE_VIEWER` 합성 제거(793 Q1)»).
- 후속 후보: 콘솔 반영(권한 맵 · 개요 WMS 카드 · 샘플)은 793 · 794 처럼 «굽기 라이브 확인 뒤» 별도 콘솔 티켓.

# Goal

37차 창에서 **업무 권한 세트가 0개**인 운영자(`demo-corp`)의 교환 토큰이 WMS 개요 카드 출처(`GET /api/v1/admin/dashboard/orders`)에서 **200** 을 받았다 — 세트 8개 · 대조군 어느 행에서도 WMS 는 200 이었다(`TASK-MONO-672` § 37th window). 원인은 결함이 아니라 **남은 예외**다:

- wms admin-service `SecurityConfig.java:147-149` — 토큰의 서명된 `entitled_domains` 에 `wms` 가 있으면 `ROLE_WMS_VIEWER` 를 합성한다(ADR-MONO-019 § D5 · ADR-MONO-020 D4 · `TASK-MONO-162`). 대시보드 컨트롤러 9개가 `@PreAuthorize("hasRole('WMS_VIEWER')")` 다.
- ERP 는 같은 합성을 **소유자 결정 Q1** 로 지웠다(`TASK-MONO-793` — `read-model-service …/ReadAuthorizationGate.java:84-85` · `approval-service …/ActorContext.java:17-19`). Finance 도 지웠다(`TASK-MONO-794` — `ledger-service …/ActorContextJwtAuthenticationConverter.java:54-59`: «with it a read-only business set could never be told apart from "no set"»).
- `ADR-MONO-083` 의 AS-IS 표 WMS 행은 이 합성을 **적었지만**(«admin-service `WMS_VIEWER`(대시보드, `entitled_domains∋wms` 면 합성)») WMS 를 «✅ 업무 · 조회/변경 둘 다 있음» 으로 보고 단계를 두지 않았다 — 그래서 재검토되지 않았다.

결과: WMS 를 구독한 회사의 **모든** 운영자가 WMS 대시보드(주문 · 출하 · 처리량 · 지표 · 기준정보 · 조정 · 재고 · ASN · 알림 목록)를 본다. 세트를 하나도 안 준 운영자도, 다른 도메인 세트만 준 운영자도. 콘솔이 «업무 권한 없음» 을 그리는 근거(실효 권한 API)와 서버 판정이 WMS 에서만 어긋난다.

# Scope

## In Scope

- **AC-0 측정**(아래) → 소유자 결정 → 계약 먼저 → 구현.
- wms admin-service 의 합성 제거(결정이 «제거» 일 때)와 대시보드 9곳의 게이트를 결정된 업무 역할로.
- 결정에 따라 iam 카탈로그 · 시스템 프리셋(`V0052` 계열 새 마이그레이션) · «이전 호환» 세트.
- `ADR-MONO-083` 에 이 결정을 적는 짧은 절(WMS 행 정정 — «합성 제거» 또는 «예외로 유지 + 사유»).
- 같은 PR 안의 시험: 대조군(세트 없음 → 403 · 해당 업무 세트 → 200 · 다른 도메인 세트만 → 403 · `legacy-compat` → 이전과 같은 200 집합).

## Out of Scope

- 콘솔 화면 · 권한 맵 · 샘플 — 별도 콘솔 티켓(793 · 794 와 같은 순서).
- WMS 의 다른 서비스(inventory · inbound · outbound · master)의 게이트 — 이미 업무 역할로 판정한다(`ADR-MONO-083` 표). 단 AC-0 이 그 서비스들에 같은 합성이 **없음** 을 재확인한다(`outbound-service …/CallerScope.java` · `SecurityContextCallerScopeProvider.java` 가 `entitled_domains` 를 읽는다 — 무엇에 쓰는지 판정).
- admin-service 의 쓰기 게이트(`WMS_ADMIN` · `WMS_OPERATOR`) — 합성이 건드리지 않는다(`SecurityConfig.java:139-146` 주석).

# Acceptance Criteria

- [ ] **AC-0** — 착수 시점 코드로 재측정하고 § AC-0 기록에 file:line 으로 적는다. 🔴 끝에 STOP 하고 § 질문을 소유자에게 올린다.
  1. 합성 지점과 `hasRole('WMS_VIEWER')` 게이트 전수(기안 시점 `api/dashboard/` 의 9 컨트롤러 — 알림 목록 포함 — 와, 같은 `AlertDashboardController` 의 확인(쓰기) `WMS_OPERATOR`).
  2. 각 대시보드가 **무엇을 보여 주는지**(주문 · 출하 · ASN · 재고 · 조정 · 기준정보 · 처리량 · 지표 · 알림)와 대응하는 WMS 업무 역할 후보(`OUTBOUND_READ` · `INBOUND_READ` · `INVENTORY_READ` · `MASTER_READ` …) 표.
  3. 시스템 프리셋 7개가 지금 주는 역할(기안 시점 `V0052__create_domain_permission_sets_and_backfill.sql:144-152`: `WMS_VIEWER` 이상을 주는 것은 `wms-ops-actions`(`WMS_OPERATOR`) · `wms-full` 뿐) — 🔴 **합성만 지우면 `wms-outbound-operate` · `wms-inventory-read` 등만 가진 운영자는 자기 업무 대시보드도 403** 이 된다. 이 사실이 질문 Q2 의 이유다.
  4. 호출자 전수: 콘솔(`features/wms-ops/**` · `shared/composition/operator-overview-*.ts` · 샘플 `fixtures/wms.ts`), 데모 시드(`infra/demo/seed/seed-wms.sh` · `lib-backfill.sh`), `scripts/console-demo/**`, CI(`.github/workflows/federation-hardening-e2e.yml` 등), 서비스 간 호출(워크로드 토큰) — 각 호출이 어떤 토큰 · 어떤 역할로 오는지와 제거 후 결과.
  5. 37차 측정 재현 방법(창 필요 여부): 슬라이스 시험으로 «세트 없음 + `entitled_domains=[wms]` → 지금 200» 을 먼저 고정한다(제거 전 빨강이 될 대조군).
- [ ] **AC-1** — 소유자 결정 수령 후 계약(`projects/wms-platform/specs/contracts/http/admin-service-api.md` 인가 절 · iam `admin-api.md` § 카탈로그/프리셋)부터 바꾸고 구현한다.
- [ ] **AC-2** — 대조군 시험(실 체인 · 실서명 JWT): 세트 없음 + 구독 → 대시보드 전 경로 403 · 결정된 업무 역할 → 그 대시보드 200 · 다른 도메인 세트만 → 403 · `legacy-compat` 보유자 → 이전과 같은 200 집합(이전 호환) · `'*'` 와일드카드 GET 은 결정대로. bite: 합성을 되살리면 «세트 없음 → 403» 이 빨강.
- [ ] **AC-3** — 데모 시드 · 콘솔 · CI 호출자가 깨지지 않는다(AC-0 ④ 의 각 행을 시험 또는 실행으로). 깨지는 행은 이 PR 에서 고치거나 소유자 결정으로 별도 티켓.
- [ ] **AC-4** — 배포 상태를 따로 적는다: 굽기 → 다음 창에서 `TASK-MONO-672` 의 세트별 표를 WMS 행까지 다시 잰다(37차 절차 — 창 안 단일 세트 운영자 · admin 토큰 교환).

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` — AS-IS 표 WMS 행 · D2 · D3
- `docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md` § D5 · `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` D4 (entitlement-trust)
- `projects/wms-platform/specs/services/admin-service/architecture.md`
- `tasks/done/TASK-MONO-793-*` § 소유자 결정 Q1 · `tasks/done/TASK-MONO-794-adr-083-p7-finance-fine-grained-authz.md` § AC-0 § 4

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md`
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 업무 권한 세트 · 시스템 프리셋
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1(개요 WMS 카드 — 콘솔 후속에서)

# Edge Cases

- **조회 전용 업무 세트가 대시보드를 못 여는 회귀**: Q2 를 정하지 않고 합성만 지우면 `wms-inventory-read` 운영자의 재고 대시보드가 403 — 793 · 794 가 «업무 역할 READ 로 판정» 을 같은 PR 에 넣은 이유.
- **알림 목록(`GET …/alerts`)**: 조회는 `WMS_VIEWER`, 확인(쓰기)은 `WMS_OPERATOR`(`wms-ops-actions`). 조회 게이트를 어느 업무 역할로 둘지 — 확인 권한자가 목록을 못 보는 조합이 생기지 않게.
- **처리량 · 지표(`throughput` · `metrics`)**: 출고 · 입고 · 재고를 함께 집계한다 — 단일 업무 역할에 대응하지 않는다. «WMS 업무 역할 아무거나(READ 이상)» 같은 합집합 게이트가 후보.
- **테넌트 미선택(기본 토큰 — 역할 0) 운영자**: 793 · 794 와 같이 403 수용인지 확인.
- **워크로드 토큰**: 서비스 간 호출이 대시보드를 읽고 있으면 범위(`wms.read` 등) 갈래가 필요할 수 있다 — AC-0 ④.

# Failure Scenarios

1. 합성만 지우고 대시보드 게이트를 `WMS_VIEWER` 로 둔다 → 조회 업무 세트 운영자 전원이 WMS 대시보드 403(위 Edge Case 1).
2. 대시보드 게이트를 넓은 합집합으로 두면서 «다른 도메인 세트만» 대조군을 빼먹는다 → 다른 도메인 역할 문자열이 우연히 통과하는 게이트를 못 잡는다.
3. 데모 시드 · CI 의 WMS 대시보드 읽기가 `legacy-compat` 외 토큰으로 오는데 AC-0 ④ 를 건너뛴다 → 머지 초록 · nightly · 창에서 빨강.
4. 37차 측정을 «결함» 으로 읽어 소유자 결정 없이 고친다 — 이것은 ADR-019/020 이 의도한 동작이고, 바꾸는 것은 권한 정책 결정이다.

---

## 질문 (AC-0 뒤 소유자에게 — 기안 시점 추천 · AC-0 이 바꿀 수 있다)

| Q | 질문 | 추천 | 대안 |
|---|---|---|---|
| **Q1** | WMS admin-service 의 구독 합성 `ROLE_WMS_VIEWER` 를 지우나 | **지운다** — 793 Q1 · 794 와 같은 원칙(조회 전용 세트와 «세트 없음» 을 서버가 구분해야 콘솔의 «업무 권한 없음» 과 서버 판정이 맞는다) | 유지 + `ADR-MONO-083` 에 «WMS 대시보드는 구독 단위 공개» 예외와 사유를 적는다(코드 무변경) |
| **Q2** | (Q1 = 지운다면) 대시보드 9곳을 어느 업무 역할로 판정하나 | **대시보드별 대응 READ**(주문 · 출하 = `OUTBOUND_READ ∨ WRITE` · ASN = `INBOUND_*` · 재고 · 조정 = `INVENTORY_*` · 기준정보 = `MASTER_READ` · 처리량 · 지표 · 알림 목록 = WMS 업무 역할 아무거나) + `WMS_OPERATOR` 이상은 지금처럼 전부 | (나) 대시보드 전부 «WMS 업무 역할 아무거나» 하나 — 단순, 세분 없음 · (다) 프리셋 READ 세트들에 `WMS_VIEWER` 를 넣는다 — 서비스 코드는 합성 제거만, 세분 없음 |
| **Q3** | `'*'` 와일드카드(플랫폼 SUPER_ADMIN) 토큰의 대시보드 GET | Finance 의 `ROLE_FINANCE_SUPERADMIN_READ` 처럼 GET 만 유지(AC-0 이 지금 동작을 잰 뒤) | 막는다 |
| 순서 | 콘솔 반영 | 별도 콘솔 티켓, 굽기 라이브 뒤(793 · 794 와 같음) | — |
