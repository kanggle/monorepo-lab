# Task ID

TASK-PC-FE-316

# Status

backlog

# Title

운영자 그룹 화면에서 이메일로 초대하고, 본인이 수락하면 운영자가 되면서 그 그룹에 바로 들어가게 한다 — 그리고 그때 IAM 메뉴에서 «운영자 그룹» 을 «운영자 관리» 위로 올린다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- operator-groups
- navigation

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 초대 · 권한 경계 · 수락 시점 fan-out 설계. 백엔드(iam admin-service) 변경을 동반할 수 있다.
>
> 🔵 **소유자 결정(2026-10-07 UTC):** 대화 «운영자 그룹에서 바로 이메일로 운영자를 등록할 수 있나» → «지금 규칙(TASK-MONO-334)으로는 가입 계정이 있어야 해서 안 된다 · ADR-MONO-080 D6 초대 방식 뒤에 된다» → «그때는 그룹이 사람을 들이는 입구가 되니 그룹을 운영자 위로 올리는 게 자연스럽지 않나» → «맞다, 이 티켓에서 함께» → «진행».

---

# ⏳ SCHEDULED — DO NOT START before ADR-MONO-080 D6 (운영자 초대) is merged

**AC-0 (verify-then-act gate):** 착수 전에 아래를 **열어서** 확인한다. 하나라도 거짓이면 `ready/` 로 옮기지 말고 이 파일에 측정만 적는다.

1. `TASK-MONO-770`~`TASK-MONO-774`(ADR-MONO-080 실행) 중 **운영자 초대**(D6: 관리자 이메일 초대 → 인증된 본인 로그인 수락 → 운영자 측면 생성)를 구현한 티켓이 `done/` 이고, 그 PR 이 `main` 에 있다.
2. admin-service 의 운영자 생성이 더는 «대상 테넌트에 가입 계정 필수»(`CreateOperatorUseCase.java` 의 `OperatorAccountNotFoundException` 분기, TASK-MONO-334)가 아니라 초대 수락으로 일어난다.
3. 초대가 1회용 · 만료 있음 · 토큰 원문 미저장(ADR-MONO-080 R4)이다.

---

# Dependency Markers

- 선행(차단): ADR-MONO-080 D6 실행 — `TASK-MONO-770`~`774` 중 운영자 초대 단계. 위 AC-0.
- 선행: `TASK-PC-FE-314`(그룹 멤버 선택기) — 이미 있는 운영자 고르기. 이 티켓은 그 옆에 «초대» 를 더한다(대체하지 않는다).
- 선행: `TASK-PC-FE-315`(권한 세트 ↔ 권한 순서).
- 관련: `docs/adr/ADR-MONO-046-operator-group-model.md`(그룹 fan-out · `group_origin` · no-escalation).

# Background (기안 시 측정, `origin/main` `5e213a8e7`)

| 사실 | 근거 |
|---|---|
| 운영자 생성은 그 이메일로 **대상 테넌트에 가입한 계정**이 있어야 한다(없으면 `422 OPERATOR_ACCOUNT_NOT_FOUND`, `'*'` 만 면제) | `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/application/CreateOperatorUseCase.java:86-108` |
| ADR-MONO-080(ACCEPTED 2026-10-07, 갈래 A) D6 이 그 규칙을 «초대 → 인증된 본인 로그인 수락 → 운영자 측면 생성» 으로 바꾼다. 초대받는 사람은 회사 테넌트 계정이 필요 없고 개인(풀) 계정이면 된다 | `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § D6 · D3 · R4 |
| 그룹 멤버는 **이미 존재하는 운영자**만 — 멤버 추가 = `operatorId` | `features/operator-groups/components/GroupMemberDialog.tsx` · ADR-MONO-046 |
| 그룹 멤버 추가/제거는 그룹 grant 를 개인 행으로 fan-out / `group_origin` 행 회수 | ADR-MONO-046 D4 (`:80-85`) |
| 그룹 권한은 초대한 사람이 가진 범위를 넘을 수 없다 | ADR-MONO-046 D3 (`:76`) |

# Goal

1. 그룹 상세에서 **이메일로 초대**한다. 받는 사람이 수락하면 운영자 측면이 생기고, **같은 순간 그 그룹 멤버가 되어** 그룹 grant 가 fan-out 된다.
2. 수락 전에는 아무 권한도 없다. 초대가 만료·취소되면 그룹에는 아무도 들어가지 않는다.
3. 그룹이 사람을 들이는 기본 입구가 되므로 IAM 메뉴에서 **«운영자 그룹» 을 «운영자 관리» 위로** 올린다: `가이드 · 개요 · 운영자 그룹 · 운영자 관리 · 권한 세트 · 권한 · 감사·보안`.

# Scope

## In Scope

- 방식(대화 추천): **초대에 그룹 정보를 담는다** — 운영자 초대가 «수락 시 이 그룹에 추가» 를 들고 간다. ADR-MONO-046 의 그룹 모델(멤버 = 존재하는 운영자)은 바꾸지 않는다.
  - 대안 «수락 대기 멤버» 상태를 그룹에 두는 안은 ADR-046 개정이 필요해 택하지 않는다. 착수 때 초대 구현의 실제 모양이 이 방식을 막으면 멈추고 보고.
- 그룹 상세: «이메일로 초대» 동작 + 대기 중 초대 목록(취소 포함).
- 수락 시: 운영자 측면 생성 → 그룹 멤버 추가(기존 fan-out 경로 재사용) — 한 트랜잭션 또는 실패 시 둘 다 없던 일.
- 권한: 초대 생성은 **운영자 초대 권한**(D6 이 정한 키, 아마 `operator.manage`)과 `group.manage` 를 **둘 다** 서버가 검사한다. 그룹 권한이 운영자 생성 권한을 대신하지 않는다.
- 메뉴: `console-nav-config.ts` 에서 «운영자 그룹» ↔ «운영자 관리» · IAM 가이드 표 순서 · `sidebar-iam-group.test.tsx`.
- «그룹이 기본 입구» 는 **운영 방침**이다(운영자 화면의 개인 초대 길도 남는다) — 그 방침 채택을 이 티켓 Background 에 기록.

## Out of Scope

- ADR-MONO-080 D6 자체(운영자 초대) — 선행 티켓.
- 그룹 멤버십을 권한 평가 축으로 바꾸는 일(ADR-046 D2 변경) — 하지 않는다.

# Acceptance Criteria

- [ ] **AC-0** — 위 SCHEDULED 게이트 셋을 열어서 확인하고 결과를 적는다.
- [ ] **AC-1** — 그룹에서 이메일 초대 → 그 이메일을 **인증한** 본인이 로그인해 수락 → 운영자 측면 + 그룹 멤버 + 그룹 grant fan-out 이 모두 생긴다(통합 시험).
- [ ] **AC-2** — 🔴 대조군: 인증 안 된 계정 · 다른 이메일 계정은 수락 못 한다(ADR-080 D3 대조군을 그룹 초대에도). 만료·취소된 초대는 수락 못 하고 그룹에 아무도 안 들어간다.
- [ ] **AC-3** — 초대한 사람이 가진 범위를 넘는 그룹 grant 는 수락 시점에도 fan-out 되지 않는다(no-escalation — 초대 시점과 수락 시점 사이에 초대자의 권한이 줄어든 경우 포함).
- [ ] **AC-4** — `group.manage` 만 있고 운영자 초대 권한이 없는 행위자는 초대를 만들 수 없다(서버 403).
- [ ] **AC-5** — IAM 드릴 순서 `/iam/guide · /iam · /operator-groups · /operators · /permission-sets · /permissions · /audit` + bite.
- [ ] **AC-6** — 라이브 데모 창에서 실제 이메일 초대 → 수락 → 그룹 멤버 확인.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` § D3 · D6 · R4
- `docs/adr/ADR-MONO-046-operator-group-model.md` § D3 · D4
- `projects/iam-platform/specs/services/admin-service/rbac.md`
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` — 운영자 초대 계약(D6 티켓이 정의) + 그룹 정보를 싣는 확장(이 티켓, **계약 먼저**).

# Edge Cases

- 초대 수락 시점에 그룹이 지워졌다 → 운영자 측면만 생기고 그룹 추가는 건너뛴다(또는 수락 거절) — 착수 때 결정해 계약에 적는다.
- 같은 이메일을 두 그룹에서 초대 → 수락 하나로 두 그룹 모두? 초대마다 따로? — 착수 때 결정.
- 이미 그 테넌트 운영자인 사람을 이메일로 초대 → 초대 대신 TASK-PC-FE-314 선택기로 안내.

# Failure Scenarios

- 이메일 일치만으로 수락이 되어 남의 이메일로 가입한 사람이 회사 권한을 받는다 → AC-2 대조군.
- 그룹 권한이 운영자 생성 권한을 대신한다 → AC-4.
- 초대 기능 없이 메뉴만 먼저 올려 «그룹 화면을 먼저 열어도 사람을 들일 수 없는» 기간이 생긴다 → 메뉴 이동은 이 티켓 안에서만(AC-5).
