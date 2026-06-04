# Task ID

TASK-ERP-BE-009

# Title

**approval-service 부트스트랩 — 결재 워크플로 first increment (ERP v2 pillar, ADR-016 §D3 forward-declaration 집행).** ADR-MONO-016 §D3 가 v2 로 forward-declare 한 `approval-service` 를 read-model(ERP-BE-007) 선례대로 **first increment** 로 실행: 단일 Hexagonal `rest-api` 서비스(`apps/approval-service/`, `com.example.erp.approval`) — `ApprovalRequest` aggregate + 상태기계(**DRAFT → SUBMITTED → APPROVED \| REJECTED \| WITHDRAWN**, single-stage route) + 권한결재자 enforcement(E3, 자기결재 금지 I4) + 멱등 전이(E4) + 불변 감사(E8/A7) + masterdata subject ref-check(E1) + transactional outbox 이벤트(`erp.approval.*.v1`) + REST + 기본 inbox + IT. erp 의 **첫 실 도메인 로직(workflow state machine)** 서비스 — read-model 의 E5 read-only 와 대비.

# Status

review

# Owner

backend-engineer (erp approval-service bootstrap; ADR-016 §D3 amendment + architecture.md/contracts 이 spec PR 에 동반 — impl)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- deploy
- test

---

# Dependency Markers

- **realises**: ADR-MONO-016 §D3 amendment(2026-06-05, TASK-ERP-BE-009) — approval-service first increment forward-declaration 집행. erp.md E3(상태기계+권한결재자+자기결재금지)/E4(멱등전이+불변감사)/E1(참조무결성)/E6/E7/E8 + internal-system I4(SoD)/I1/I2 + audit-heavy A2/A3/A7/A10 + transactional T1~T5.
- **builds on**: TASK-ERP-BE-001 (masterdata-service Hexagonal 선례 + 참조 대상 master) + TASK-ERP-BE-007 (read-model first-increment 선례 + dual-type 패턴) + TASK-BE-336/337(erp.write 위임 scope + org_scope — approval write authz 도 동일 도메인 scope 로 커버).
- **cross-service (E1)**: `MasterDataPort` 가 masterdata-service REST 로 approval subject(department/employee) 존재·active 검증(SUBMITTED 전). masterdata-service 는 불변(approval 이 read-only 참조).
- **deploy**: root `settings.gradle` include `projects:erp-platform:apps:approval-service` + `.github/workflows/ci.yml` erp 필터에 approval-service 경로 backfill + erp `docker-compose.yml` 에 approval-service 블록 + `erp.local` path 라우팅(`PathPrefix(/api/erp/approval)`). **이 deploy 배선은 monorepo-level shared 경로(root settings.gradle/CI) 를 건드림** — impl PR 에서 atomic 하게 동반(cross-cutting bootstrap; masterdata TASK-MONO-119 선례).
- **v2-deferred (named)**: multi-stage routing(1~N) / 대결·위임(delegation) / IN_REVIEW intermediate / rich inbox 필터 / 콘솔 parity slice(별 PC-FE) / read-model 의 approval-fact 투영 / notification fan-out.

# Goal

erp 가 결재 워크플로의 핵심(상태기계 + 권한결재 + 멱등 + 감사 + 참조무결성 + 이벤트)을 실제로 수행하는 `approval-service` first increment 를 배포 가능 상태로 부트스트랩한다. masterdata(마스터) + read-model(통합조회) 에 이어 erp 의 **결재** bounded context 를 라이브화 — erp.md E3/E4 의 첫 실행.

# Scope

## In Scope

- **service skeleton** `apps/approval-service/` Hexagonal(domain/application/infrastructure/presentation), package `com.example.erp.approval`, `ApprovalServiceApplication`, build.gradle(masterdata 미러: web/data-jpa/validation/actuator/security/oauth2-resource-server/kafka + MySQL/Flyway + java-common/web/messaging/observability/security). MySQL `erp_db`(masterdata 와 동일 인스턴스, approval_request/audit_log/outbox/processed_events 테이블 별도; Flyway).
- **domain**: `ApprovalRequest` aggregate(상태기계 DRAFT→SUBMITTED→APPROVED|REJECTED|WITHDRAWN; 전이 가드: 불법전이→`APPROVAL_STATUS_TRANSITION_INVALID`, 종결재처리→`APPROVAL_ALREADY_FINALIZED`) + `ApprovalRoute`(single-stage, approver 1) + `ApprovalStatus` enum + 권한결재자 규칙(submitter≠approver, E3/I4 SoD → `APPROVAL_NOT_AUTHORIZED_APPROVER`/`APPROVAL_ROUTE_INVALID`) + audit(append-only) + 도메인 예외(erp.md 5 에러코드).
- **application**: `ApprovalApplicationService`(@Transactional 경계) — create/submit/approve/reject/withdraw/list/detail/inbox use-case; Idempotency-Key 멱등(E4/T1, same-key 재시도=prior outcome); 전이+감사+outbox 원자(A7). outbound ports: `MasterDataPort`(subject ref-check E1), `AuthorizationPort`, `ClockPort`, `IdempotencyStore`, `ApprovalRequestRepository`, `AuditLogRepository`, `ApprovalEventPublisher`.
- **infrastructure**: JPA persistence(entity/repo/adapter, audit_log append-only, outbox) + `ApprovalOutboxPollingScheduler`(`libs/java-messaging`) + `MasterDataHttpAdapter`(masterdata-service REST ref-check) + security(SecurityConfig RS256 + entitlement-trust dual-accept + external-traffic rejection) + ActorContextResolver + config.
- **presentation**: `ApprovalRequestController`(`/api/erp/approval/requests` create/list/detail/submit/approve/reject/withdraw) + `ApprovalInboxController`(`/api/erp/approval/inbox` 기본) + GlobalExceptionHandler(5 approval 에러코드→HTTP) + DTO(NON_NULL absent 규약) + TenantClaimEnforcer + PublicPaths(actuator probe 만).
- **outbox events**: `erp.approval.{submitted,approved,rejected,withdrawn}.v1`(partition key=approvalRequestId; envelope=erp-masterdata-events.md 동형; 소비자=v2 notification/read-model, 이 increment 무소비).
- **deploy 배선**(atomic): root `settings.gradle` include + `ci.yml` erp 필터 backfill(pure-positive, MONO-074/075 negation 금지) + erp `docker-compose.yml` approval-service 블록 + `erp.local` `PathPrefix(/api/erp/approval)` 라우팅.
- **tests**: domain unit(상태기계 전이 매트릭스 — 합법/불법전이/종결재처리/자기결재/권한결재자; 멱등) + application unit(use-case + Idempotency + ref-check mock) + slice(REST + JPA) + **IT(@Tag integration, Testcontainers MySQL + WireMock GAP JWKS + WireMock masterdata)**: create→submit→approve happy path + reject/withdraw + 권한결재자 403 + 불법전이 409 + 종결재처리 409 + 멱등 재시도 + outbox row 발행 + audit append. H2 forbidden.

## Out of Scope

- multi-stage routing(1~N) / 대결·위임 / IN_REVIEW / rich inbox 필터 — approval-service v2.
- 콘솔 approval-inbox parity slice — 별 platform-console PC-FE task.
- read-model 의 approval-fact 투영 + notification fan-out — v2 (이 increment 는 이벤트만 발행, 무소비).
- masterdata-service 변경(approval 은 read-only 참조).

# Acceptance Criteria

- [ ] **AC-1** 상태기계: create→DRAFT; submit DRAFT→SUBMITTED(subject active + route valid + submitter≠approver); approve SUBMITTED→APPROVED(권한결재자만); reject/withdraw(reason 필수). 불법전이→409 `APPROVAL_STATUS_TRANSITION_INVALID`; 종결상태 재처리→409 `APPROVAL_ALREADY_FINALIZED`.
- [ ] **AC-2** 권한(E3/I4 SoD): 비권한자 approve/reject→403 `APPROVAL_NOT_AUTHORIZED_APPROVER`; 자기결재(submitter==approver)→`APPROVAL_ROUTE_INVALID`(submit 시 거부).
- [ ] **AC-3** 멱등(E4): 같은 Idempotency-Key 전이 재시도=동일 결과(중복 전이·중복 이벤트·중복 감사 없음).
- [ ] **AC-4** 참조무결성(E1): submit 전 `MasterDataPort` 가 subject(department/employee) 존재·active 검증; 미해소→거부(architecture.md 지정 코드).
- [ ] **AC-5** 감사+outbox 원자(A7): 각 전이가 append-only audit_log + outbox 이벤트를 동일 tx 로 기록. `erp.approval.{submitted,approved,rejected,withdrawn}.v1` 발행(IT 단언).
- [ ] **AC-6** internal-system 경계(E7/I1/I2): RS256 JWT + entitlement-trust dual-accept; 외부 트래픽 거부; public path=actuator probe 만.
- [ ] **AC-7** deploy: `./gradlew :projects:erp-platform:apps:approval-service:build` GREEN + service healthy 기동(컨텍스트 로드) + settings.gradle/CI/docker-compose 배선; `docker compose config -q` 통과.
- [ ] **AC-8** `:check` GREEN; IT(@Tag integration) CI Linux(Testcontainers MySQL + WireMock). H2 미사용.

# Related Specs

- `specs/services/approval-service/architecture.md`(이 spec PR) + `specs/contracts/http/approval-api.md` + `specs/contracts/events/erp-approval-events.md`(이 spec PR). ADR-MONO-016 §D3 amendment(2026-06-05). erp.md E1/E3/E4/E6/E7/E8 + internal-system I1/I2/I4 + audit-heavy A2/A3/A7/A10 + transactional T1~T5. ADR-MONO-005 saga(Category B). gap-integration.md(OAuth2 RS256 + tenant gate + entitlement-trust).

# Related Contracts

- `approval-api.md`(8 business endpoint + actuator) + `erp-approval-events.md`(4 outbox 토픽). consume: masterdata-api.md(subject ref-check, read-only GET). envelope=erp-masterdata-events.md 동형.

# Edge Cases

- 자기결재(submitter==approver): submit 시 `APPROVAL_ROUTE_INVALID`(I4 SoD).
- subject 가 retired master: ref-check 가 active 아님 판정→거부(E1; architecture.md 코드).
- 종결(APPROVED/REJECTED/WITHDRAWN) 후 모든 전이 command→`APPROVAL_ALREADY_FINALIZED`(E3 forbidden in-place 재처리).
- 멱등 키 재사용 다른 payload: architecture.md idempotency 규약(키 충돌 처리).
- outbox 발행 실패: 전이 tx 롤백(원자성 A7; at-least-once 후속 재발행).
- audit 기록 실패: fail-closed(A10) — 비즈니스 전이 차단.

# Failure Scenarios

- 상태기계 race(동시 approve): 낙관락(version)으로 한쪽 `APPROVAL_STATUS_TRANSITION_INVALID`/`ALREADY_FINALIZED`.
- masterdata-service 불가: ref-check 실패→submit 거부(추정 금지, E1/E5 정신); 명시 에러.
- 멱등 store 불가: fail-closed 또는 architecture.md 지정 degrade.
- outbox 미발행→소비자(v2) 누락: at-least-once + dedupe 전제(이 increment 무소비라 영향 0, 회귀-게이트는 outbox row 단언).

# Test Requirements

- domain: 상태기계 전이 매트릭스(전 셀) + 권한결재자 + 자기결재 + 멱등 단위. application: use-case + Idempotency + MasterDataPort mock. slice: REST(에러코드 매핑) + JPA. **IT**: Testcontainers MySQL + WireMock(GAP JWKS + masterdata) — happy path(create→submit→approve) + reject/withdraw + 403/409 + 멱등 재시도 + outbox row + audit append. H2 forbidden.
- `./gradlew :projects:erp-platform:apps:approval-service:check` GREEN. IT CI Linux. service healthy 기동 검증(JPQL/컨텍스트).

# Definition of Done

- [ ] approval-service Hexagonal skeleton + 상태기계 first vertical slice(create/submit/approve/reject/withdraw/list/detail/inbox).
- [ ] E1 ref-check + E3/I4 권한·SoD + E4 멱등 + E8/A7 감사·outbox 원자 + E7 internal-system 경계.
- [ ] settings.gradle/CI/docker-compose 배선(atomic) + healthy 기동.
- [ ] `:check` GREEN; IT CI Linux GREEN.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (복잡 도메인 — workflow 상태기계 + 권한·SoD + 멱등 전이 + cross-service 참조무결성 + 감사·outbox 원자성 + 신규 서비스 부트스트랩 배선). 사용자 "approval-service 부트스트랩" 선택. 메타: read-model(ERP-BE-007) first-increment 선례대로 ADR-016 §D3 forward-declaration 집행(새 architecture.md 가 HARDSTOP-09 충족). erp 첫 실 도메인 로직(E3/E4 상태기계) 서비스 — read-model E5 read-only 와 대비. v2(multi-stage/위임/IN_REVIEW/콘솔 parity) deferred. [[project_monorepo_template_strategy]] [[project_platform_console_adr_013]] [[feedback_spring_boot_diagnostic_patterns]]
