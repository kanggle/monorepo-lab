# Task ID

TASK-PC-FE-049

# Title

**콘솔 ERP "통합 조회" 카드 — read-model-service employee org-view 를 platform-console 에 렌더 (event-propagation payoff 가시화).** TASK-ERP-BE-007 이 read-model-service(통합 employee org-view: 직원+부서경로+비용센터+직급, masterdata 이벤트 구독 투영)를 라이브화했으나 **아무도 소비하지 않는다**. 이 task 가 콘솔 ERP 운영에 read-only "통합 조회" 카드를 붙여 "기준정보를 바꾸면 통합 화면이 따라 움직인다"를 운영자에게 가시화한다. **cross-project (1 atomic PR)**: ① erp-platform docker-compose 에 `erp.local` **path 기반 Traefik 라우팅**(`/api/erp/read-model/**` → read-model-service, 나머지 → masterdata-service) — 현재 read-model-service 는 Traefik 라벨 부재로 erp.local 도달 불가(BE-007 표면화) ② console-web read-model API client + 동일출처 proxy + org-view 카드.

# Status

done

# Owner

frontend-engineer (console-web 프런트 — 분석/라우팅/계약은 dispatcher) + cross-project erp-platform docker-compose 라우팅 + console-integration-contract §2.4.8 read surface note

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- deploy
- test

---

# Dependency Markers

- **consumes (불변)**: erp read-model-service `read-model-api.md` § `GET /api/erp/read-model/employees` [+ `/{id}`] (TASK-ERP-BE-007, 라이브). read API/계약 변경 0.
- **follows**: TASK-ERP-BE-007 (read-model-service 첫 증분 — org-view producer). TASK-PC-FE-010/046/048 (erp-ops 콘솔 섹션 + read/write 패턴 청사진).
- **surfaces (BE-007 갭)**: read-model-service 가 erp docker-compose 에서 Traefik 라벨 부재 → `erp.local` 캐치올(masterdata) 이 `/api/erp/read-model/**` 를 가로채 404. 이 task 가 path-기반 라우팅으로 해소(gateway-service v1-deferred 까지의 임시 직결 라우팅; gateway 활성 시 그쪽이 인수).
- **decision (user, 2026-06-04)**: 다음 작업 = "콘솔 통합 조회 카드".
- **follow-up (범위 밖)**: business-partner+풀 통합조회 read API 확장(ERP-BE v2) / per-operator org_scope read 필터 / gateway-service 활성 시 라우팅 인수.

# Goal

운영자가 콘솔 ERP 운영에서 **통합 조회 카드**로 직원 한 명의 소속 부서 경로 + 비용센터 + 직급을 한 화면에 본다(read-model-service org-view 소비). read-only(E5) — eventually-consistent 경고 + 미해소 참조 배지 표시. masterdata 변경 → read-model 투영 → 콘솔 가시화의 전파 루프를 사용자 눈에 완결.

# Scope

## In Scope

- **deploy (erp-platform, cross-project)** `projects/erp-platform/docker-compose.yml` — `erp.local` Traefik **path 기반 라우팅**: read-model-service 에 `traefik.http.routers.erp-readmodel.rule=Host(erp.local) && PathPrefix(/api/erp/read-model)` (+ 높은 priority) + service loadbalancer; masterdata 의 기존 `Host(erp.local)` 캐치올은 낮은 priority 로 유지(나머지 경로 default). `docker compose config -q` exit 0.
- **api (console-web)** `types.ts` — `EmployeeOrgView`(employee + `department:{id,code,name,path[]}|null` + `costCenter|null` + `jobGrade|null`) + list/detail response + zod(meta.warning/meta.unresolved tolerant). `erp-api.ts` — `listEmployeeOrgViews(params)` + `getEmployeeOrgView(id, params)` (callErp 재사용, path `/api/erp/read-model/employees`; `?asOf`/`page`/`size`/`departmentId` thread). read-only(write fn 0).
- **proxy (console-web)** `app/api/erp/read-model/employees/route.ts` (GET) + `[id]/route.ts` (GET) — `_proxy.ts` mapErpError + buildListParams/buildDetailParams 재사용. 동일출처, HttpOnly GAP OIDC 토큰 server-side 부착(erp-api.ts 기존 경로).
- **server-state (console-web)** `erp-state.ts` — `getErpSectionState` fan-out 에 `employeeOrgViews` 추가(Promise.all). state shape + EMPTY 갱신. erp page 가 `initialEmployeeOrgViews` thread.
- **ui (console-web)** `EmployeeOrgViewCard`(read-only): 직원 행별 부서경로(breadcrumb)+비용센터+직급; **eventually-consistent `meta.warning` 배너** + 미해소 참조(`meta.unresolved`)는 "동기화 중" 배지. ErpOpsScreen 에 카드 섹션 추가(write 아님, AsOfPicker E3 연동). `index.ts` export.
- **contract** `console-integration-contract.md` §2.4.8 — read-model org-view read surface note(read-only GET 2종, producer=read-model-api.md, eventually-consistent + meta.warning/unresolved; write 없음).
- **tests (console-web)** `erp-api`/proxy walk 에 read-model GET 2 route 추가(read-pure-GET 보존, credential 불변 단언) + `EmployeeOrgViewCard` component test(path breadcrumb 렌더, meta.warning 배너, unresolved 배지). `pnpm test`/`tsc`/`lint`/`build` GREEN.

## Out of Scope

- read-model-service producer/read API/계약 — 불변(consume only).
- business-partner / 풀 통합조회 / per-operator org_scope read 필터 — ERP-BE v2.
- write/mutation(통합 조회는 read-only, E5). gateway-service 활성(라우팅 임시 직결 유지).

# Acceptance Criteria

- [ ] **AC-1** erp docker-compose path 라우팅: `erp.local/api/erp/read-model/**` → read-model-service, 나머지 `erp.local/**` → masterdata-service. `docker compose config -q` exit 0. (라우팅 priority 로 PathPrefix 우선.)
- [ ] **AC-2** 콘솔 ERP 운영에 "통합 조회" 카드: 직원 행별 부서경로 breadcrumb + 비용센터 + 직급 렌더(read-model org-view 소비).
- [ ] **AC-3** read-only: read-model proxy/route 는 GET 만(POST/PATCH 0). credential = 기존 GAP OIDC server-side(erp-api.ts 경로, X-Operator-Reason 미생성). test 단언.
- [ ] **AC-4** eventually-consistent: `meta.warning` 배너 표시; 미해소 참조(`meta.unresolved` / 필드 null)는 "동기화 중" 배지(crash 없음, 날조 없음).
- [ ] **AC-5** `?asOf` E3 thread-through(AsOfPicker 연동). page/size/departmentId thread.
- [ ] **AC-6** 에러: 401→whole-session re-login, 403→inline(scope), 404/400/422→inline, 503/timeout→erp 섹션만 degrade(기존 mapErpError 재사용). 통합 조회만의 신규 에러 분기 없음.
- [ ] **AC-7** console `pnpm test`/`tsc`/`lint`/`build` GREEN.

# Related Specs

- erp `read-model-api.md`(불변, consume) + `read-model-service/architecture.md`(org-view shape). console-integration-contract §2.4.8(read-model read surface note 추가). ADR-MONO-013/015(parity slice). ADR-MONO-016 §D3(read-model-service).

# Related Contracts

- consume: `GET /api/erp/read-model/employees` + `/{id}` (read-model-api.md, 불변).
- expose: 동일출처 proxy `/api/erp/read-model/employees` [+ `/{id}`] (GET only).

# Edge Cases

- org-view 미해소 참조(employee 가 미투영 부서 참조): 필드 null + meta.unresolved → "동기화 중" 배지(읽기, 후속 이벤트로 자연 해소). 날조 금지.
- read-model 빈 projection(이벤트 미수신): 빈 카드 + eventually-consistent 안내(404 아님 — list 는 빈 배열).
- asOf 과거: read-model 첫 증분은 최신 revision 투영(RETIRED 보존) — producer 의 asOf 의미 그대로 thread.
- erp.local 라우팅 우선순위: PathPrefix 라우터가 캐치올보다 먼저 매칭되도록 priority 명시(Traefik 기본은 rule 길이 기반이나 명시 priority 로 확정).

# Failure Scenarios

- 라우팅 오설정 → read-model 호출이 masterdata 로 가서 404 → 카드 "조회 불가". 라이브 스모크로 확인.
- read-model-service 다운 → 503 → erp 섹션만 degrade(다른 섹션·콘솔 shell 무사, mapErpError).
- masterdata 라우팅 회귀 → 기존 5 마스터 카드 깨짐. 캐치올 보존 + path 라우터만 추가로 회귀 방지(기존 마스터 경로 불변 단언).

# Test Requirements

- `erp-api.test.ts`/proxy walk: read-model GET 2 route 추가, read-pure-GET 보존, credential 불변(getDomainFacingToken 미사용=기존 GAP OIDC), write fn 0 단언.
- `EmployeeOrgViewCard` component test: 부서경로 breadcrumb 렌더 + meta.warning 배너 + unresolved 배지 + 빈 projection.
- `pnpm test` + `tsc` + `lint` + `build`.
- Local: erp 재배포(path 라우팅) + console 재배포 후 라이브 스모크 — masterdata 로 부서/직원 생성 → 콘솔 통합 조회 카드에 org-view resolved 표시(전파 루프 가시 확인).

# Definition of Done

- [ ] erp docker-compose path 라우팅 + console-web read-model client/proxy/state/card + contract note + tests.
- [ ] console `pnpm test`/`tsc`/`lint`/`build` GREEN; `docker compose config -q` exit 0.
- [ ] Local 재배포 + 라이브 스모크(masterdata 변경 → 콘솔 통합 조회 org-view).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (프런트 통합 — read-model API 소비 + UI 카드; 무거운 도메인 로직 없음. 라우팅/계약/검증은 dispatcher). 사용자 "콘솔 통합 조회 카드" 선택. 메타: BE-007 이 백엔드로 닫은 전파 루프를 사용자에게 가시화 — 한 사이클 완결. **cross-project 핵심 = read-model-service 가 erp.local 도달 불가(Traefik 라벨 부재)** → path 라우팅이 카드의 전제. [[project_platform_console_adr_013]]
