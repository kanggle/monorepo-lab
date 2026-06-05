# Task ID

TASK-ERP-BE-013

# Title

**approval-service v2.1 — 대결/위임 (delegation/substitution) (ADR-016 §D3 approval forward-declaration 3번째·마지막 증분).** TASK-ERP-BE-012 가 라이브화한 다단계 결재선 위에 **위임 모델**을 얹는다: 결재자 A 가 부재 시 대결자 D 에게 표준 위임(`DelegationGrant`: A→D, 유효기간, 사유, ACTIVE|REVOKED)을 부여 → 활성 grant 가 있으면 D 가 A 대신 해당 단계를 승인/반려(audit `onBehalfOf=A`); `erp.approval.delegated.v1` 이벤트로 위임 사실 발행; 위임 변경은 **불변 감사 + 운영 조회**(erp.md E8/L131). 위임 통한 자기결재(D==기안자) 차단. **withdraw 는 기안자-only 불변**(위임은 결재자 의무만). 이로써 erp 결재 도메인(단계/대결/위임)이 완성된다.

# Status

ready

# Owner

backend-engineer (erp-platform approval-service; 분석=Opus 4.8 / 구현 권장=Opus — 권한위양 모델 + 전이시점 대결자 resolution + 신규 aggregate 생명주기 + 불변 감사 + 이벤트)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- test
- adr

---

# Dependency Markers

- **선행 (같은 서비스)**: TASK-ERP-BE-012 (approval v2.0 다단계 + IN_REVIEW, main 머지됨 `b749c1f9`) + TASK-ERP-BE-009 (단일단계 first increment). 이 task 가 per-stage 결재자 인가에 대결자 수락을 추가.
- **realises**: ADR-MONO-016 §D3 approval forward-declaration 의 **3번째·마지막 증분** — ERP-BE-009 가 deferred 한 "대결/위임 (delegation/substitution) + `erp.approval.delegated` 이벤트". rules/domains/erp.md L40(대결자=부재 시 위임받은 사람)/L116(단계 전이는 결재자 또는 위임받은 대결자만)/L131(결재선·대결 위임=인가-영향 변경, 불변 감사+운영조회) 실현.
- **decision (user, 2026-06-05)**: 다음 작업 = approval v2.1 대결/위임(#1, BE-012 직후 sequencing한 증분).

# Goal

결재자가 부재 기간 동안 대결자에게 승인 권한을 위임할 수 있고, 활성 위임이 있으면 대결자가 해당 결재자 대신 다단계 결재선의 그 단계를 승인/반려할 수 있다. 위임 부여·회수는 불변 감사되고 운영 조회 가능하며, 대결 승인은 누가 누구를 대신했는지(`onBehalfOf`) 추적된다. erp 결재 bounded context 의 단계/대결/위임 전체가 실 도메인 로직으로 완성.

# Scope

## In Scope

- **domain (pure + aggregate)**:
  - 신규 `DelegationGrant` aggregate `(id, tenant_id, delegator_id[A], delegate_id[D], valid_from, valid_to?(open-ended 허용), reason, status[ACTIVE|REVOKED], created_at, created_by, revoked_at?, revoked_by?)`. 생성 검증: **자기위임(A==D) → `DELEGATION_INVALID`**; `valid_to < valid_from` → `DELEGATION_INVALID`. revoke: ACTIVE→REVOKED(이미 REVOKED → 멱등 또는 409, 멱등 선호). `isActiveAt(now)` = status=ACTIVE ∧ now ∈ [valid_from, valid_to ?? +∞]. 순수 검증 + JPA 엔티티(도메인↔framework 예외).
  - 위임 해소 `DelegationResolver`(domain/application) — `resolveActingApprover(stageApproverId, actorId, tenantId, now)`: actor==stageApprover → 직접; 아니면 active grant `stageApprover→actor` 조회(`DelegationGrantRepository`, 아웃바운드 포트) → 있으면 대결(onBehalfOf=stageApprover) / 없으면 미인가.
- **application + transition 통합**:
  - `approve`/`reject` 시 현 단계 결재자 A 에 대해 resolution → actor 가 A 또는 활성 대결자가 아니면 `APPROVAL_NOT_AUTHORIZED_APPROVER`. **위임 통한 자기결재 차단**: 효력 actor(=대결자 D)가 요청의 submitter 면 거부(`APPROVAL_ROUTE_INVALID` cause `self_approval_via_delegation` 또는 `APPROVAL_NOT_AUTHORIZED_APPROVER`). aggregate 는 `onBehalfOf`(=A) + actor(=D) 를 받아 audit 에 둘 다 기록; 현 단계 결재자 == onBehalfOf 불변 검사(T4/SoD 보존). withdraw 무변경(기안자-only, 위임 불가).
  - `DelegationApplicationService`(또는 approval service 확장): create/revoke/list 위임 grant — 각 변경 = grant 상태 + **불변 감사 row(actor+timestamp+before/after+reason, L131)** + (create 시) outbox `erp.approval.delegated.v1` 단일 Tx(A7). 멱등(create=Idempotency-Key).
- **persistence + migration**: 신규 `delegation_grant` 테이블 + JPA entity/repo(아웃바운드 포트) + active-grant 조회(`delegator_id, delegate_id, status, valid_from, valid_to` 인덱스). Flyway `V3__delegation.sql`(테이블 + 인덱스; 기존 데이터 무영향 — 순수 추가). `approval_audit_log`/action 의 `actor`/`onBehalfOf` 기록(BE-012 의 stage 처럼 nullable 가산 가능).
- **events (가산·하위호환)**: **신규 토픽 `erp.approval.delegated.v1`**(grant create 시; payload: grantId, delegatorId, delegateId, validFrom, validTo?, reason, actor, tenantId, occurredAt; aggregateType=`DelegationGrant`, aggregateId=grantId). **producer-only forward interface — read-model(BE-010)/notification(BE-011) 미구독이라 소비자 무영향**(catalog L101 명명 토픽; 신규 토픽은 기존 토픽 계약 불변). 전이 이벤트(approved/rejected)에 **`actingForApproverId` 가산**(NON_NULL; 대결 시 onBehalfOf=A, 본인 승인 시 absent) — 기존 소비자 unknown-field tolerant.
- **REST (additive)**: `POST /api/erp/approval/delegations`(create A→D, Idempotency-Key) + `POST /api/erp/approval/delegations/{id}/revoke`(reason) + `GET /api/erp/approval/delegations`(내 위임: as delegator + as delegate, scope-aware). 인가: `erp.write`(create/revoke 본인 위임만 — A 또는 admin) / `erp.read`(list). 모든 mutation Idempotency-Key.
- **contracts (additive, spec-first)**: approval-api.md(delegation 3 endpoint + 전이/detail `actingForApproverId`) + erp-approval-events.md(`erp.approval.delegated.v1` payload + 전이 `actingForApproverId`) + architecture.md(§v2.1 amendment: DelegationGrant aggregate·resolution·REST·events·audit + Out-of-Scope reconcile) + ADR-016 §D3 amendment blockquote(ERP-BE-013) + **error-handling.md**(신규 코드 `DELEGATION_INVALID`[422 자기위임/잘못된 기간], `DELEGATION_NOT_FOUND`[404 revoke 대상 없음] erp 섹션 등록 — 사용 전 등록 규칙).
- **tests**: domain(`DelegationGrant` create/revoke/isActiveAt·자기위임·잘못된 기간; `DelegationResolver` 직접/대결/미인가/만료/REVOKED) + application(create→delegated 이벤트·revoke 감사·멱등; 전이 대결 수락·onBehalfOf audit·위임 자기결재 차단) + **Testcontainers IT**(A→D grant → D 가 A 단계 approve[대결, onBehalfOf=A audit, actingForApproverId 이벤트] / grant 없는 타인 approve→403 / 만료·REVOKED grant→403 / 위임 자기결재 차단 / delegated 이벤트 발행 / withdraw 위임 불가). `:check`(Docker-free) GREEN + CI "Integration (erp-platform, Testcontainers)" GREEN(권위).

## Out of Scope

- **per-request / per-route 위임**(특정 요청·결재선만 대결) — v2.2. 이 증분은 standing windowed grant(A 가 결재자인 모든 단계 커버).
- **자동 부재 감지**(휴가/OOO 연동 자동 위임) — v2.2.
- **transitive/chained 위임**(D 가 다시 재위임) — 금지(v2.2 검토). grant 는 1-hop.
- **단계전진 알림 fan-out**(IN_REVIEW 전진 → 다음 단계 결재자 통지) — 별 증분(BE-012 가 분리). 이 task 와 무관.
- **delegated 이벤트 소비자**(notification 의 "위임받음" 통지, read-model 의 위임 투영) — 별 증분(이 task 는 producer-only forward interface).
- **콘솔 위임 UI** — 별 PC-FE 후속(backend 라이브 후).
- `delegated` revoke 이벤트 — v2.1 은 create 만 발행, revoke 는 감사만(별 토픽 불요).

# Acceptance Criteria

- [ ] **AC-1** 위임 grant: A 가 `POST /delegations`(A→D, 기간, 사유) 생성 → ACTIVE + 불변 감사 row + `erp.approval.delegated.v1` 발행. `POST /delegations/{id}/revoke` → REVOKED + 감사. `GET /delegations` → 내 위임(delegator/delegate) 조회. 자기위임/잘못된 기간 → 422 `DELEGATION_INVALID`; 없는 grant revoke → 404 `DELEGATION_NOT_FOUND`.
- [ ] **AC-2** 전이 대결: 다단계 요청에서 현 단계 결재자 A 가 부재, active grant A→D 존재 → **D 가 approve/reject** 수행 가능(상태기계·단계전진 BE-012 그대로); audit 에 actor=D + onBehalfOf=A 기록; 전이 이벤트 payload `actingForApproverId=A`. 본인(A) 승인 시 `actingForApproverId` absent.
- [ ] **AC-3** 미인가/만료/REVOKED: grant 없는 타인 approve → 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`; 만료(now > valid_to)·REVOKED grant → 동일 403. 위임 통한 자기결재(D==submitter) → 거부.
- [ ] **AC-4** withdraw 무변경(기안자-only, 위임 불가). 기존 BE-012 다단계/IN_REVIEW + BE-009 단일단계 전이 회귀 0.
- [ ] **AC-5** 이벤트: `erp.approval.delegated.v1` 신규 토픽 producer-only — read-model(BE-010)/notification(BE-011) **무변경 동작**(미구독); 기존 4 토픽 계약 불변; 전이 `actingForApproverId` 가산(unknown-field tolerant). 위임 변경 단일 Tx(grant+audit+(create시)outbox, A7).
- [ ] **AC-6** `:approval-service:check` GREEN + CI "Integration (erp-platform, Testcontainers)" GREEN. spec-first: 계약 4(approval-api/erp-approval-events/architecture/error-handling) + ADR amendment 머지 후 impl.

# Related Specs

- extend: `approval-service/architecture.md`(§v2.1 amendment — DelegationGrant + resolution + REST + events + audit; Out-of-Scope: 대결/위임 realised, per-request/auto-absence/transitive deferred). ADR-MONO-016 §D3(3번째 amendment). `rules/domains/erp.md`(L40/L116/L131 — 대결자·위임 인가-영향 변경 불변 감사 실현).

# Related Contracts

- update(additive): approval-api.md(delegation 3 endpoint + actingForApproverId) + erp-approval-events.md(delegated.v1 + actingForApproverId). error-handling.md(DELEGATION_INVALID/DELEGATION_NOT_FOUND erp 섹션 등록). 소비자(read-model/notification subscriptions) 무변경.

# Edge Cases

- 자기위임(A==D): 생성 422 `DELEGATION_INVALID`.
- 잘못된 기간(valid_to<valid_from): 422 `DELEGATION_INVALID`.
- 만료 grant(now>valid_to)·REVOKED grant 로 대결 시도: 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`(active 아님).
- 위임 통한 자기결재(D==요청 submitter): 거부(SoD — 위임으로 우회 불가).
- 다단계: 위임은 A 가 결재자인 **현 단계**에만 적용; A 가 stage 2 결재자면 stage 2 pending 시에만 D 대결 가능(이전/이후 단계 무관).
- 중복 grant(A→D 이미 ACTIVE): 멱등(Idempotency-Key) 또는 두 번째 무시; active 하나만 유효.
- revoke 멱등: 이미 REVOKED → 멱등 200(또는 409, 멱등 선호).
- delegated 이벤트: grant create 만; revoke 는 감사만(토픽 없음).

# Failure Scenarios

- delegation_grant 조회 불가(DB): 전이 시 fail-closed(대결 불인정 → 403, 우회 없음).
- 동시 같은 단계 approve(결재자+대결자): 낙관락 → 하나 win, 다른 409 `CONCURRENT_MODIFICATION`.
- 위임 감사 append 실패: grant 변경 Tx 전체 실패(audit-fail-closed A10).
- outbox(delegated) publish 실패: row PENDING, 다음 tick 재시도; grant 는 이미 커밋(멱등 무영향).

# Test Requirements

- domain: `DelegationGrantTest`(create/revoke/isActiveAt·자기위임·기간·멱등 revoke), `DelegationResolverTest`(직접 결재자/유효 대결/만료/REVOKED/타인 → 인가 결정).
- application: `DelegationApplicationServiceTest`(create→delegated 이벤트·불변 감사·Idempotency; revoke 감사), `ApprovalApplicationServiceTest` 확장(대결 approve onBehalfOf audit + actingForApproverId 이벤트; 위임 자기결재 차단; grant 없는 타인 403).
- IT(Testcontainers): A→D grant create(delegated.v1 발행 + 감사) → 다단계 요청 D 대결 approve(onBehalfOf audit, actingForApproverId 이벤트, 단계전진 BE-012 보존) / grant 없는 403 / 만료·REVOKED 403 / 자기결재 차단 / withdraw 위임 불가 / 기존 BE-009·BE-012 전이 회귀.
- `:approval-service:check`(Docker-free) GREEN; CI erp Integration(Testcontainers) GREEN = 권위. `--rerun-tasks` 1회(ctor 변경 stale cache 회피).

# Definition of Done

- [ ] DelegationGrant(aggregate+생명주기) + DelegationResolver + 전이 통합(onBehalfOf audit) + REST 3 + delegated 이벤트 + actingForApproverId 가산 + Flyway V3 + error-handling 등록.
- [ ] `:approval-service:check` + CI erp Integration(Testcontainers) GREEN; BE-009/BE-012 전이 회귀 0; 소비자(read-model/notification) 무변경.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (권한위양 모델: DelegationGrant aggregate 생명주기 + 전이시점 대결자 resolution + onBehalfOf 감사 추적 + 위임-통한-자기결재 SoD 차단 + 신규 토픽 producer + 불변 감사 L131 + BE-009/BE-012 전이 회귀 무손상). 사용자 "approval v2.1 대결/위임(#1)" 선택 — BE-012 직후 sequencing한 증분. **이로써 ADR-016 §D3 approval forward-declaration(단계/대결/위임) 3-증분 완성**(BE-009 단일단계 → BE-012 다단계+IN_REVIEW → BE-013 대결/위임). 신규 토픽=producer-only forward interface(소비자 무변경). per-request/자동부재/transitive 위임 = v2.2 deferred. [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]]
