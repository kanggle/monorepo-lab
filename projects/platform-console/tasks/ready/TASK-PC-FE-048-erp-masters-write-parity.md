# Task ID

TASK-PC-FE-048

# Title

**erp 나머지 4개 마스터 write parity — 직원·직급·비용센터·거래처에 create/update/retire 추가.** 부서(PC-FE-046) 파일럿이 확립한 전 스택 청사진을 복제. producer write 엔드포인트·토큰 authz(erp.write+org_scope, 도메인 단위)는 이미 라이브 → 순수 console-web 프런트+proxy. 필드-설정 기반 generic `MasterWriteDialog` 로 4개 마스터를 DRY 하게 처리; FK(부서/직급/비용센터)는 raw UUID 가 아니라 드롭다운(PC-FE-047 교훈, 이미 로드된 섹션 목록 재사용). §2.4.8 read-only carve-out 을 5개 마스터 전체 write 로 일반화.

# Status

ready

# Owner

frontend-engineer (console-web only — project-internal; + spec §2.4.8 rescope, ADR-016 §D3.1 note)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test
- adr

---

# Dependency Markers

- **follows / generalizes**: TASK-PC-FE-046 (부서 write 파일럿 — 청사진), TASK-PC-FE-047 (참조 입력=드롭다운 교훈). TASK-BE-336/337 (write 권한 — erp.write scope + org_scope; **도메인 단위라 4개 마스터 자동 커버**, 추가 백엔드 0).
- **producer (불변)**: erp `masterdata-api.md` § Employee/JobGrade/CostCenter/BusinessPartner — 각 create(POST)/update(PATCH)/retire(POST .../retire) 이미 라이브.
- **decision (user, 2026-06-04)**: "erp 나머지 4개 마스터 write".

# Goal

운영자가 콘솔 ERP 운영에서 직원·직급·비용센터·거래처를 생성/수정/폐기한다(부서와 동일 confirm+reason(retire)+멱등키 패턴). 부서 포함 5개 마스터 전부 write 가능.

# Scope

## In Scope

- **api** `erp-api.ts` — create/update/retire fn × 4 마스터(callErp method/body/idempotencyKey 재사용). `types.ts` — 4 마스터 create/update input + zod body 스키마(+ 공유 `ErpRetireBodySchema`).
- **hooks** `use-erp-ops.ts` — create/update/retire 훅 × 4 + prefix invalidation.
- **proxy** 4 마스터 × {POST master(create), POST [id](→upstream PATCH update), POST [id]/retire} = 12 same-origin POST route.
- **ui** generic `MasterWriteDialog`(field-config 기반: text/number/date/static-select/dynamic-select(FK)/reason; confirm gate; retire reason; 멱등키; 에러 매핑) + 마스터별 field config. 4 List 에 writable gate + FK optionSources(부서/직급/비용센터 목록은 ErpOpsScreen 의 initial* 에서 thread). `index.ts` export.
- **spec** §2.4.8 read-only bullet(4개 마스터) → 5개 마스터 write 로 rescope(*Department write binding (PILOT)* → *Masterdata write binding*). ADR-016 §D3.1 note(파일럿 → 전 마스터 write). `erp-api.test.ts` rescoped 단언을 5개 마스터 write 허용으로 갱신.

## Out of Scope

- producer/contract(erp masterdata-api.md) — 불변(consume only). 백엔드/토큰 authz — 불변(이미 커버).
- 부서 move-parent 류 마스터별 특수 작업(부서 전용; 4개 마스터는 create/update/retire 만).
- FK full-list fetch(섹션 initial 목록 사용 — 파일럿 규모 충분).

# Acceptance Criteria

- [ ] **AC-1** 4개 마스터 각각 create/update/retire 가 콘솔에서 동작 — 올바른 same-origin POST → upstream(create POST / update PATCH / retire POST) + 멱등키; retire 는 reason(body) 필수.
- [ ] **AC-2** credential 불변(getDomainFacingToken, X-Operator-Reason 미생성 — 부서와 동일, test 단언).
- [ ] **AC-3** FK 입력(employee 의 부서/직급/비용센터, cost-center 의 부서)은 드롭다운(섹션 목록), partnerType 은 static select(CUSTOMER/SUPPLIER/BOTH).
- [ ] **AC-4** §2.4.8 read-only 단언이 5개 마스터 write 로 rescope(부서-만-write carve-out 제거); `erp-api.test.ts` proxy-walk/export 단언 갱신.
- [ ] **AC-5** 에러(409 DUPLICATE/REFERENCE_VIOLATION[job-grade/cost-center]/422 EFFECTIVE/403 PERMISSION) inline.
- [ ] **AC-6** console `pnpm test`/`tsc`/`lint`/`build` GREEN.

# Related Specs

- console-integration-contract §2.4.8 (read-only→5-master write rescope). ADR-016 §D3.1. producer erp `masterdata-api.md` § 4 masters(불변).

# Edge Cases

- employee: `MASTERDATA_REFERENCE_VIOLATION` retire 시 미발생(leaf), job-grade/cost-center 는 발생(active employee 참조).
- business-partner paymentTerms = 중첩 {termDays, method} — UI 옵션 입력(termDays number + method, 선택).
- FK 목록 0개(빈 섹션) → 드롭다운 "없음"만; 필수 아님(대부분 optional).

# Failure Scenarios

- 부서 write/read 회귀 — 부서 경로 불변 확인(부서는 자체 dialog 유지).
- read 순수성(GET) 단언 보존 — write 일반화가 read 호출에 누수 없어야.

# Test Requirements

- `erp-api.test.ts`: read-pure-GET 보존 + export/proxy-walk 단언을 5개 마스터 write 허용으로 갱신 + 4 마스터 write-call 단언(method/path/idempotency/credential).
- 신규/확장 component test: `MasterWriteDialog`(field 렌더 + retire reason gate + 멱등키 + FK dropdown) + 4 List writable gate.
- `pnpm test` + `tsc` + `lint` + `build`.
- Local: console-web 재빌드+재기동; 4 마스터 create/retire 라이브 스모크(+ producer DB 영속, 토큰은 erp.write+org_scope 이미 보유).

# Definition of Done

- [ ] 4 마스터 api/hooks/proxy + generic dialog + List gate + spec rescope + ADR note + tests.
- [ ] console `pnpm test`/`tsc`/`lint`/`build` GREEN.
- [ ] Local 재빌드+재기동 + 라이브 스모크.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 "erp 나머지 4개 마스터 write" 선택. 메타: 파일럿(부서)이 도메인 단위 authz(erp.write/org_scope)와 dialog/proxy/hook 패턴을 확립 → 나머지 마스터는 백엔드 0, 순수 프런트 복제. 필드-설정 generic dialog 로 4× 중복 제거; FK=드롭다운(PC-FE-047). [[project_platform_console_adr_013]]
