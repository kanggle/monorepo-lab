# Task ID

TASK-ERP-BE-007

# Title

**erp `read-model-service` 첫 증분 — masterdata 변경 이벤트를 구독해 통합 employee org-view 를 투영하는 read-model 소비자 부트스트랩.** masterdata-service 의 outbox/producer 측은 이미 완성(5 마스터 × create/update/retire/move-parent → `erp.masterdata.*.changed.v1` 발행). 빠진 것은 **소비자** — 계약에 "v1 consumers = none", architecture.md 가 "read-model-service v2 will be the inbound consumer" 로 forward-declare. 이 task 가 그 소비자의 첫 증분을 짓는다: 4 토픽(department/employee/jobgrade/costcenter) 구독 + `processed_events` dedupe(T8) + 4 projection table(MySQL) + 읽기 시점 org-view 조합(부서경로+비용센터+직급) + read-only REST 2종. "기준정보를 바꾸면 통합 조회가 따라 움직인다" 의 수신 고리를 닫는다(E5 read-only 투영). 청사진 = scm `inventory-visibility-service`(rest-api+event-consumer, EventDedupe, no-outbox Cat C).

# Status

review

# Owner

backend-engineer (erp-platform project-internal; + read-model-service spec 3종 [architecture.md / read-model-api.md / read-model-subscriptions.md] + PROJECT.md Service Map/frontmatter + ADR-MONO-016 §D3 additive amendment + erp-masterdata-events.md consumer note)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- event
- test
- deploy
- adr

---

# Dependency Markers

- **consumes (producer 불변)**: masterdata-service `erp-masterdata-events.md` § `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1` — 이미 라이브 발행(TASK-ERP-BE-001 impl PR #650). producer/계약 변경 0.
- **forward-declared by**: ADR-MONO-016 §D3 (read-model-service v2), PROJECT.md Service Map v2, masterdata-service `architecture.md` § Dependencies("read-model-service v2 will be the inbound consumer") + Required Artifacts #4/#7, erp-masterdata-events.md § Consumer rules. → 신규 아키텍처 결정 아님(기존 forward-decl 실행). 단 신규 서비스 자체 architecture.md 는 신규(HARDSTOP-09 gate — 이 spec PR 가 충족).
- **blueprint**: scm `inventory-visibility-service` (rest-api+event-consumer read-model, EventDedupe, no-outbox ADR-MONO-005 Cat C — 정확한 동형). erp 차이 = MySQL(Postgres 아님) + E1~E8 + org-view projection + Kafka 브로커 compose 신설.
- **decision (user, 2026-06-04)**: erp 강화 방향 = "마스터 변경 이벤트 전파" → 소비자 형태 = "read-model-service 최소 수직슬라이스".
- **follow-up (별도 task, 범위 밖)**: ① platform-console "통합 조회" 카드(TASK-PC-FE — read API 소비) ② business-partner + 풀 통합조회 ③ per-operator `org_scope` 데이터-스코프 read 필터 ④ erp Integration CI job 에 read-model IT 편입(TASK-ERP-BE-004 가 신설한 "Integration (erp-platform, Testcontainers)" job 재사용).

# Goal

masterdata 변경 이벤트가 통합 read model 로 전파되어, 운영자가 직원 한 명의 **소속 부서 경로 + 비용센터 + 직급**을 한 번의 조회로 본다. masterdata-service 의 마스터 변경 → outbox → Kafka → read-model-service projection → 통합 org-view 의 end-to-end 루프를 닫는다. read model 은 도메인 로직 미보유(E5) — read-only 투영만.

# Scope

## In Scope

- **service skeleton** `apps/read-model-service/` — Spring Boot 3.4 (Servlet), Hexagonal, `build.gradle`(masterdata-service mirror: spring-kafka + MySQL Flyway + java-messaging/security/observability/web/common libs + Testcontainers mysql/kafka), `bootJar=read-model-service.jar`, settings.gradle include, `application.yml`(+`application-test.yml` outbox/Kafka 비활성 slice), `Dockerfile`(host-prebuilt jar COPY — GAP trap 인지).
- **event-consumer** 4 `@KafkaListener` (department/employee/jobgrade/costcenter `.changed.v1`), consumer group `erp-read-model-v1`, manual ACK, 3-retry + DLT, invalid-envelope→즉시 DLT. `ApplyMasterChangeUseCase` per `changeKind`(CREATED/UPDATED/PARENT_MOVED→upsert, RETIRED→mark+effective_to).
- **dedupe (T8)** `processed_events`(event_id PK) — 중복 eventId skip-without-mutation.
- **projection (Flyway V1, MySQL)** `department_proj` / `employee_proj` / `job_grade_proj` / `cost_center_proj` + `processed_events`. RETIRED=논리 보존(삭제 금지, E2).
- **read API (rest-api)** `GET /api/erp/read-model/employees`(paginated, `?asOf`/`departmentId` subtree/`status`) + `GET /employees/{id}` — 읽기 시점 org-view 조합(department path = `parent_id` 조상 walk; 미투영 참조 → `null` + `meta.unresolved`, 날조 금지 E5). `meta.warning="Eventually-consistent read-model"`.
- **security** OAuth2 RS(GAP JWKS RS256) + entitlement-trust dual-accept(`tenant_id ∈ {erp,*}` ∪ `entitled_domains ∋ erp`; decode validator + filter 양 독립 게이트) + READ gate(`erp.read` ∨ operator ∨ entitled, fail-closed). 변경 엔드포인트 0 → WRITE/`org_scope` 게이트 없음.
- **infra** erp `docker-compose.yml` 에 **Kafka 브로커 신설**(현재 부재 — masterdata relay 도 로컬 무위였음) + read-model-service 블록 활성 + masterdata-service `KAFKA_BOOTSTRAP_SERVERS` 배선. read-model-service 는 자체 MySQL DB `erp_read_model_db`.
- **spec** read-model-service `architecture.md` + `read-model-api.md` + `read-model-subscriptions.md`(이 spec PR 에 포함). PROJECT.md Service Map(read-model-service v2→v1.5 first increment 활성) + frontmatter `service_types: [rest-api, event-consumer]`(ADR-016 §D2 conditional) + §Out of Scope read-heavy 재검토 노트. ADR-MONO-016 §D3 additive amendment. erp-masterdata-events.md § Consumer rules / forward-consumers 에 read-model-service 첫 소비자 노트. masterdata-service architecture.md § Dependencies forward-ref 노트(additive).
- **tests** unit(domain org-view 조합 + 4 projection upsert/retire + dedupe; application use-case mocked-port) + slice(JPA + `@WebMvcTest` SecurityConfig + GlobalExceptionHandler + controller meta.warning/unresolved) + IT(`@Tag("integration")` Testcontainers MySQL+Kafka+JWKS: 4토픽 consume→read API org-view 조합 end-to-end / 중복 eventId skip / poison→DLT / out-of-order missing-ref→unresolved / RETIRED 보존 + asOf / cross-tenant 403 + entitled 2xx + no-scope 403 + no-token 401). H2 forbidden.

## Out of Scope

- producer/계약(erp-masterdata-events.md topic/payload) — 불변(consume only).
- business-partner 토픽 구독 + 풀 통합조회(approval/permission/notification 등 v2) — deferred.
- platform-console "통합 조회" 카드(read API 소비) — 별 TASK-PC-FE.
- per-operator `org_scope` 데이터-스코프 read 필터(membership-derived) — masterdata E6 point 3 v2 와 정렬해 deferred.
- approval-service / read-model 풀 surface / admin-service.

# Acceptance Criteria

- [ ] **AC-1** 4 토픽 각각 consume → 해당 projection upsert; CREATED/UPDATED/PARENT_MOVED=최신 upsert, RETIRED=`status=RETIRED`+`effective_to`(보존, 삭제 금지).
- [ ] **AC-2** `GET /employees/{id}` 가 employee + 부서경로(조상 walk) + 비용센터 + 직급을 읽기 시점 조합으로 반환; 4 토픽 발행 후 모든 참조 resolved (IT end-to-end).
- [ ] **AC-3** 멱등(T8): 동일 `eventId` 재전달 → `processed_events` skip, projection byte-identical. invalid envelope(null eventId/payload)→즉시 DLT; transient→3-retry→DLT.
- [ ] **AC-4** 미투영 참조(out-of-order): employee 가 미consume 부서 참조 → org-view 의 `department=null` + `meta.unresolved=["department"]`, 날조 0 (E5). 알 수 없는 employee id → 404 `MASTERDATA_NOT_FOUND`(projection miss≠날조 row).
- [ ] **AC-5** 보안: cross-tenant(`tenant_id=scm`, `entitled_domains∌erp`)→403 `TENANT_FORBIDDEN`; entitled cross-tenant(`entitled_domains∋erp`)→2xx(dual-accept); read scope 부재→403 `PERMISSION_DENIED`; no token→401. 변경 엔드포인트 0(read-only, E5).
- [ ] **AC-6** read-model 은 **이벤트 발행 0 / write-back 0 / outbox 없음**(E5 terminal). `audit_log` 없음(provenance=`processed_events`). grep 으로 outbox/publish 부재 확인.
- [ ] **AC-7** erp `docker-compose.yml` Kafka 브로커 신설 + read-model-service 블록 활성 + masterdata `KAFKA_BOOTSTRAP_SERVERS` 배선; `docker compose config -q` exit 0.
- [ ] **AC-8** `./gradlew :apps:read-model-service:check` GREEN(Docker-free: unit+slice). IT 는 `integrationTest`(Docker) — CI "Integration (erp-platform, Testcontainers)" Linux runner 에서 실행(로컬 Windows Docker host-dependent, 정직 flag).
- [ ] **AC-9** spec 3종 + PROJECT.md(frontmatter+Service Map) + ADR-016 §D3 amendment + erp-masterdata-events.md consumer note 정합. read-model-service 가 erp.md Required Artifacts #4(통합 read model 경계 맵: 모든 필드 source of record=masterdata-service) 충족.

# Related Specs

- **NEW** read-model-service `architecture.md` / contracts `http/read-model-api.md` / `events/read-model-subscriptions.md` (this spec PR).
- producer `erp-masterdata-events.md`(불변) + masterdata-service `architecture.md`(forward-ref additive).
- ADR-MONO-016 §D2/§D3 (read-model-service forward-decl + §D3 amendment). ADR-MONO-005 Cat C. ADR-MONO-019 §D5(entitlement-trust). ADR-MONO-012(Hexagonal canonical).
- `rules/domains/erp.md` E5(primary)/E1/E2/E6/E7. `rules/traits/{internal-system,transactional(T8)}.md`. `platform/service-types/{rest-api,event-consumer}.md`(dual-type).

# Related Contracts

- consume: `erp.masterdata.{department,employee,jobgrade,costcenter}.changed.v1` (erp-masterdata-events.md, 불변).
- expose: `GET /api/erp/read-model/employees` + `/{id}` (read-model-api.md, NEW).
- publish: 없음(E5 terminal).

# Edge Cases

- out-of-order: employee.changed 가 그 department.changed 보다 먼저 도착 → read 시 `department=null`+`meta.unresolved`(자연 해소: 후속 department.changed consume 후 resolved). 절대 날조 금지(E5).
- PARENT_MOVED: department_proj 의 `parent_id` upsert → 다음 read 의 org-view path 가 새 조상 반영(fan-out 재stamp 불요 — path 는 읽기 시점 walk).
- RETIRED department 를 참조하는 ACTIVE employee → org-view 는 RETIRED 부서를 path 에 그대로(보존, `?asOf` 재현). 운영 표시는 콘솔 몫.
- department path 깊이: producer 가 cycle 금지(masterdata parent-cycle invariant) → 조상 walk 종료 보장. 방어적 depth-bound.
- 동일 aggregate 동시 이벤트: per-aggregateId 파티션 순서 → 단일 consumer group 직렬화(T7 ordering-based, 동시 다중 writer 없음).

# Failure Scenarios

- Kafka 브로커 미기동(로컬): consumer 재시도; masterdata relay 도 PENDING 유지(outbox 보존) — 브로커 복구 시 자연 배수. compose 에 broker 신설로 해소.
- read-model DB 다운: read API 5xx(read-model 부재는 비정상)·consumer 처리 실패→retry→DLT. masterdata 권위 데이터는 무영향(독립 store).
- read-model 과 masterdata 불일치(eventual): 정상 — `meta.warning` 명시. 권위 조회는 masterdata-api GET.
- producer 토픽 침묵: projection 정체(stale). 읽기는 마지막 투영값 + warning. (이 증분엔 staleness sweep 없음 — inventory-visibility 와 달리 alert 미발행.)

# Test Requirements

- unit: `EmployeeOrgView` 조합(resolved/unresolved/path walk) + 4 projection upsert+retire-mark + `EventDedupeRecord`; `ApplyMasterChangeUseCase`(changeKind 별) + `QueryEmployeeOrgViewUseCase`(mocked port, STRICT_STUBS); 4 consumer mapper + validator unit + `TenantClaimEnforcerTest`.
- slice: JPA adapter slice + `@WebMvcTest`+SecurityConfig+GlobalExceptionHandler + controller meta.warning/unresolved 단언.
- IT(`@Tag("integration")`, Testcontainers MySQL+Kafka+JWKS, H2 forbidden): AC-1~AC-5 end-to-end (publish→consume→read API org-view; dedupe; DLT; unresolved; RETIRED+asOf; 보안 4분기).
- `./gradlew :apps:read-model-service:check`(Docker-free) GREEN. `integrationTest` CI Linux 실행. `docker compose config -q` exit 0.
- Local: erp 재배포(Kafka broker + masterdata relay + read-model-service) 후 라이브 스모크 — masterdata 로 부서/직원 생성 → read-model `/employees/{id}` org-view resolved 확인(GAP host-prebuilt jar trap: masterdata+read-model 양쪽 `bootJar` 선행).

# Definition of Done

- [ ] service skeleton + 4 consumer + dedupe + 4 projection(Flyway) + read API 2종 + security + Dockerfile + settings.gradle/build.gradle.
- [ ] erp docker-compose Kafka broker 신설 + read-model-service 활성 + masterdata relay 배선; `docker compose config -q` exit 0.
- [ ] spec 3종 + PROJECT.md(frontmatter `service_types:[rest-api,event-consumer]` + Service Map) + ADR-016 §D3 amendment + erp-masterdata-events.md consumer note + masterdata architecture.md forward-ref.
- [ ] unit+slice `:check` GREEN; IT CI Linux GREEN(로컬 Docker host-dependent 정직 flag).
- [ ] Local 재배포 + 라이브 스모크(masterdata 변경→read-model org-view resolved).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim: state=MERGED + origin/main tip=squash + pre-merge failing required=0). `git mv ready→review→done` re-stage 확인.

---

분석=Opus 4.8 / 구현 권장=Opus (신규 event-driven 서비스 부트스트랩 + Hexagonal + consumer 멱등/dedupe/DLT + 보안 dual-accept + CQRS read-model 경계 — 복합 도메인). 사용자 "마스터 변경 이벤트 전파 → read-model-service 최소 수직슬라이스" 선택. 메타: **producer 는 이미 완성(BE-001), 강화 지점은 비어 있던 소비자 고리** — read-model-service 가 forward-declared(ADR-016 §D3) 라 신규 ADR 불요, 신규 서비스 architecture.md 만 HARDSTOP-09 gate. 청사진=scm inventory-visibility-service(near-mechanical 적응, MySQL/E-rule/org-view 차이). erp INDEX 엄격 PR Separation Rule → spec PR(이 task ready 진입) → impl PR → close chore. [[project_platform_console_adr_013]] [[feedback_spring_boot_diagnostic_patterns]]
