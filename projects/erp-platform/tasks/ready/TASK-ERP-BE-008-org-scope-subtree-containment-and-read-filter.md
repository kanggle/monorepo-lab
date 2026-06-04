# Task ID

TASK-ERP-BE-008

# Title

**erp `org_scope` 소비 — masterdata subtree containment + read-model read 필터 (BE-337 `["*"]` 브리지의 v2 erp 측).** 운영자 토큰의 `org_scope` claim = 부서 **subtree-root** id 들(TASK-BE-338 이 membership 에서 도출). masterdata `RoleScopeAuthorizationAdapter` 가 root 를 **자기 부서트리로 descendant 확장**해 data-scope containment(현재 flat `contains` → subtree-aware); read-model `ReadAuthorizationGate`/query 가 동일 subtree 로 org-view **read 필터**. `"*"`/미설정 = 현행 동작(net-zero). ADR-MONO-020 D3 amendment(2026-06-05) + erp masterdata architecture.md E6 point 3 v2 실행.

# Status

ready

# Owner

backend-engineer (erp masterdata-service + read-model-service; ADR-MONO-020 D3 amendment + architecture E6 v2 이미 land — impl)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **consumes**: TASK-BE-338 (GAP — assume-tenant 토큰의 실제 `org_scope` claim = subtree-root). **net-zero 라 순서 무관**: BE-338 미land 시 erp 는 `["*"]`만 받아 현행대로(bypass); BE-338 land + 설정 시 이 task 의 subtree 처리가 narrowing 발현.
- **realises**: ADR-MONO-020 D3 amendment(2026-06-05) erp 측 + masterdata architecture.md E6 point 3 v2(이 spec PR) + read-model architecture.md § Multi-tenancy(이 spec PR).
- **modifies (BE-007/BE-001)**: masterdata `RoleScopeAuthorizationAdapter`(flat contains → subtree containment), read-model `ReadAuthorizationGate`/org-view query(read 필터 추가).
- **decision (user, 2026-06-05)**: per-operator org_scope, 멤버십 출처=operator_tenant_assignment.org_scope.

# Goal

운영자가 자신의 `org_scope` subtree 안의 부서만 **쓰고**(masterdata) **보도록**(read-model org-view) 한다 — 데이터-스코프가 write·read 양쪽 대칭. `org_scope=["*"]`/미설정이면 현행(전체) — net-zero. erp E6 데이터-스코프를 정석(membership-derived subtree)으로 완성.

# Scope

## In Scope

- **masterdata subtree expansion** `RoleScopeAuthorizationAdapter.evaluate` 의 data-scope 분기를 subtree-aware 로: `actor.dataScopeDepartmentIds()`(= org_scope, subtree-root id 들; `"*"` = platform bypass 불변)에 대해 target department 가 **어떤 root 의 descendant(자신 포함)인지** 판정. 부서 계층은 `DepartmentRepository`(parent_id) 로 walk(target→ancestor 체인에 root 가 있으면 in-scope; cycle-free 보장[E1 MASTERDATA_PARENT_CYCLE], depth-bound 방어). flat `contains` 는 정확매칭이라 subtree 못 잡던 것 교체.
  - 구현 선택: target 의 ancestor 체인을 올라가며 root-set 교집합(효율적, target당 1 walk) — descendant 전체 펼치기보다 권장.
  - `AuthorizationPort` 시그니처 유지(targetDepartmentId 단일) — 비-department 마스터의 targetDepartmentId 는 그 마스터의 owning department(현행 로직 유지, 그 dept 로 subtree 판정).
- **read-model read 필터** `ReadAuthorizationGate`(또는 query 계층)에 org_scope 추출 + org-view list/detail 을 운영자 org_scope subtree 내 employee 로 필터(employee.department 가 어떤 root 의 descendant). subtree walk 는 `department_proj.parent_id`(read-model 자체 projection). `"*"`/미설정 → 필터 없음(net-zero). detail 에서 scope 밖 employee 요청 → 404 또는 403(결정: **404 MASTERDATA_NOT_FOUND** — 존재를 누설 안 함, E6 데이터-스코프 누설 방지)와 일관. list 는 scope 내만 반환.
- **ActorContext / 토큰 파싱**: masterdata `ActorContextJwtAuthenticationConverter` 가 이미 org_scope 파싱(확인) — subtree-root 의미로 사용. read-model `ReadAuthorizationGate` 가 org_scope claim 추출 추가(현재 미사용).
- **tests**: masterdata(subtree containment matrix — root=in/descendant=in/sibling=out/ancestor-of-root=out/`"*"`=bypass/미설정=fail-closed; cycle-free walk) unit + IT(운영자 org_scope=[sales-root] 토큰 → sales subtree write 200 / 타 부서 403 DATA_SCOPE_FORBIDDEN / `"*"` 전체 200). read-model(org_scope 필터 unit + IT: scope 내 employee 만 list, scope 밖 detail 404). net-zero 회귀(기존 `["*"]`/미설정 테스트 GREEN).

## Out of Scope

- GAP org_scope 출처/전파 — TASK-BE-338(소비만).
- 콘솔 org_scope 설정/표시 UI — follow-up(PC-FE).
- 비-department 마스터의 owning-department 도출 로직 변경(현행 유지).
- `'*'` sentinel 재설계.

# Acceptance Criteria

- [ ] **AC-1** masterdata: 운영자 `org_scope=[sales-root]` → sales-root + 그 descendant 부서 대상 write 200; sales 밖(타 root/형제/조상) 부서 대상 write 403 `DATA_SCOPE_FORBIDDEN`. (subtree containment via parent_id walk, 정확매칭 아님.)
- [ ] **AC-2** net-zero: `org_scope=["*"]` 또는 미설정 → 현행 동작(전체 허용 / fail-closed 미설정). 기존 `RoleScopeAuthorizationAdapterTest` + masterdata IT GREEN.
- [ ] **AC-3** read-model: 운영자 `org_scope=[sales-root]` → `/employees` list 가 sales subtree 소속 employee 만; scope 밖 employee `/employees/{id}` → 404 `MASTERDATA_NOT_FOUND`. `"*"`/미설정 → 전체(net-zero, BE-007 동작).
- [ ] **AC-4** subtree walk: cycle-free 종료(E1 parent-cycle invariant), depth-bound 방어; 미해소 부모(read-model 미투영) → 보수적(그 가지 미포함, 날조 없음 E5).
- [ ] **AC-5** write·read 데이터-스코프 **대칭**: 같은 org_scope 운영자가 쓰는 범위 = 보는 범위.
- [ ] **AC-6** `./gradlew :apps:masterdata-service:check :apps:read-model-service:check` GREEN. IT(@Tag integration) CI Linux(Testcontainers MySQL+Kafka).

# Related Specs

- masterdata architecture.md E6 point 3 v2(이 spec PR) + read-model architecture.md § Multi-tenancy(이 spec PR). ADR-MONO-020 D3 amendment(2026-06-05). erp E6(rules/domains/erp.md, fail-closed data-scope).

# Related Contracts

- consume: assume-tenant 토큰 `org_scope` claim(subtree-root, BE-338). read API(read-model-api.md)/masterdata-api.md 는 불변(데이터-스코프는 인가 내부, 계약 형상 무변).

# Edge Cases

- org_scope `[]`(명시적 zero, BE-338): 모든 targeted write 403 + read-model 빈 결과(fail-closed). `NULL`→'*'(BE-338)과 구분.
- target=root 자신: in-scope(자신 포함).
- subtree-root 가 retired 부서: 여전히 계층상 부모 — descendant 판정 유효(retire=논리, 트리 보존 E2).
- read-model 미투영 부서 참조 employee: department 미해소 → 보수적 제외(scope 판정 불가 → 미포함; 날조 금지). org-view 의 unresolved 배지와 일관.
- 비-department 마스터(직원/직급/비용센터/거래처) write: 현행 owning-department 도출로 subtree 판정(직급은 dept FK 없음 → targetDepartmentId null → data-scope 미적용, 현행 유지).

# Failure Scenarios

- org_scope 파싱 실패(오염): fail-closed(거부) — BE-338 이 '*'/유효배열만 주입하므로 정상 경로엔 없음.
- masterdata subtree walk 가 깊은 트리에서 성능: depth-bound + ancestor-walk(target당 1회) 로 O(depth). 데모 규모 무관.
- read-model 필터 누락 → scope 밖 누설(회귀). list/detail 양쪽 단언으로 게이트.

# Test Requirements

- masterdata: `RoleScopeAuthorizationAdapterTest` subtree matrix(root/descendant/sibling/ancestor/`"*"`/미설정/`[]`) + 부서트리 walk 단위 + IT(org_scope 토큰별 write 200/403). H2 forbidden.
- read-model: org_scope 필터 unit(subtree 판정) + `ReadAuthorizationGate`/query + IT(scope 내 list / scope 밖 detail 404 / `"*"` 전체). 
- `./gradlew :apps:masterdata-service:check :apps:read-model-service:check` GREEN. IT CI Linux.
- Local: erp 재배포 후 라이브 스모크 — org_scope 설정 운영자 토큰으로 subtree write/read 격리 확인(BE-338 + 이 task 양쪽 배포 시).

# Definition of Done

- [ ] masterdata subtree containment(flat→subtree) + read-model read 필터.
- [ ] masterdata+read-model `:check` GREEN; IT CI Linux GREEN.
- [ ] net-zero 회귀 확인(`["*"]`/미설정 기존 동작 불변).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (E6 데이터-스코프 정석 — 계층 subtree containment + read/write 대칭 + net-zero 회귀 규율). 사용자 "per-operator org_scope" 선택. 메타: BE-337 `["*"]` 브리지의 v2 erp 측 — GAP(BE-338)이 출처/전파, 이 task 가 erp 소비(subtree-root→descendant 확장). claim 은 root 만(GAP 이 erp 트리 모름), erp 가 자기 트리로 확장. net-zero(`"*"`/미설정) 라 BE-338 과 순서 무관. [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
