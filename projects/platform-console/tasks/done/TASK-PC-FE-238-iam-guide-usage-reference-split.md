# Task ID

TASK-PC-FE-238

# Title

IAM 가이드 재구성 — 「메뉴 기능 사용법」과 「레퍼런스(역할·권한 키·구독 도메인)」 분리 + 본문 평이화

# Status

done

# Owner

frontend

# Task Tags

- code
- test
- docs

---

# Dependency Markers

- **선행**: 없음. 순수 정적 화면(`/iam/guide`) — 데이터 페치·권한 게이트 없음.
- **관련**: `TASK-PC-FE-237`(ADR-047 org-hierarchy 콘솔) — `ORG_ADMIN` / `org.manage` 는 그 태스크가 화면을 만든 뒤 가이드에 반영한다(본 태스크 Out of Scope).
- **관련**: `TASK-PC-FE-236`(guide presentation 원자 shared 추출) — IAM 가이드는 당시 대상 외였다. 본 태스크에서 공용 `guide-primitives` 로 편입.

---

# Goal

`/iam/guide` 는 현재 **레퍼런스 성격의 카탈로그**(역할 6종의 종류·설명, 권한 키, 구독 도메인 → 도메인 롤 파생표)와 **운영 지식**(위임 체인, 온보딩 축, 화면 접근 규칙)이 한 흐름에 섞여 있고, 문장이 스펙 용어(`net-zero`, `disjoint`, `tri-state`, `D2 confinement`, `센티넬`)로 쓰여 처음 진입한 운영자가 읽기 어렵다. 또한 **IAM 메뉴 11개 중 3개**(운영자 관리·계정 운영·감사·보안)만 언급되어, 나머지 메뉴(테넌트·권한·권한 세트·도메인 구독·파트너십·운영자 그룹)의 사용법은 어디에도 없다.

완료 후 가이드는 세 부분으로 **분리**된다:

1. **개념** — 콘솔에 처음 들어온 사람이 알아야 할 최소한(계정 4개의 모자 · 권한 두 평면).
2. **메뉴 사용법** — IAM 관련 **11개 메뉴 전부**에 대해 "무엇을 하는 곳 / 할 수 있는 작업 / 필요 권한", 그리고 가장 흔한 운영 흐름(운영자 온보딩 3단계 + 도달 범위 3축).
3. **레퍼런스** — 찾아보는 표: 역할 6종 · 역할 × 메뉴 접근 매트릭스 · 권한 키 · 구독 도메인 → 도메인 롤.

본문은 스펙 용어를 평이한 한국어로 바꾸고(의미 보존), 중복 문단을 제거한다.

---

# Scope

## In Scope

- `src/features/iam-guide/data.ts` — 데이터 재구성:
  - **신규 `CONSOLE_MENUS`**: 11개 메뉴 × (라우트 · 목적 · 가능 작업 · 필요 권한 · 읽기전용/변경/준비중).
  - `SCREEN_ACCESS` 확장 — 3개 → **7개 라이브 표면**(운영자 관리 · 계정 운영 · 감사·보안 · 테넌트 · 권한/권한 세트 · 도메인 구독 · 파트너십). 매트릭스는 **행=메뉴, 열=역할** 로 전치(역할 6열 고정폭이 메뉴 7열보다 읽기 쉬움).
  - `PERMISSION_KEYS` 에 **`partnership.manage`** 추가(현재 누락 — `/partnerships` 는 이미 라이브).
  - `SEED_ROLES.TENANT_ADMIN.permissions` 에 `partnership.manage` 추가(`rbac.md` § Seed Matrix 와 일치).
  - 문구 평이화: `intent` · `desc` · `AUTH_PLANE_DISJOINT` 등에서 스펙 용어 제거/풀어쓰기.
- `src/features/iam-guide/components/IamGuideScreen.tsx` — 3부 구조로 재작성. 공용 `shared/ui/guide-primitives` 의 `Mono` · `NoteCard` 사용(로컬 `PermChip` 은 IAM 고유이므로 유지).
- `tests/unit/IamGuideScreen.test.tsx` — 신규 구조에 맞춰 갱신 + `CONSOLE_MENUS` 렌더 단언 추가. axe 유지.

## Out of Scope

- **`ORG_ADMIN` · `org.manage`** — `rbac.md` 에는 있으나 (a) 어떤 운영자에도 미배정된 inert 역할이고 (b) 콘솔 화면(`/org-hierarchy`)이 아직 없다. `TASK-PC-FE-237` 이 화면을 만들 때 함께 반영한다. `data.ts` 상단 주석에 **의도적 누락**임을 명시해 조용한 드리프트를 막는다.
- 라우트 분할(`/iam/guide/usage` 등) — 형제 도메인 가이드와 동일하게 단일 정적 화면 유지.
- 라이브 화면(`/operators` 등) 자체의 동작 변경.
- `rbac.md` 수정.

---

# Acceptance Criteria

- [ ] **AC-1** 가이드가 「개념 → 메뉴 사용법 → 레퍼런스」 3부로 분리되어 렌더된다. 역할 카드·권한 키 표·구독 도메인 표는 전부 **레퍼런스** 안에만 존재한다(사용법 절에는 카탈로그를 반복하지 않는다).
- [ ] **AC-2** `CONSOLE_MENUS` 11개 메뉴가 라우트·가능 작업·필요 권한과 함께 렌더된다. 준비 중(`/operator-groups`)은 "준비 중"으로 구분 표시된다.
- [ ] **AC-3** 접근 매트릭스가 7개 표면 × 6개 역할로 확장되고, 각 셀이 `rbac.md` § Seed Matrix 와 일치한다. 특히 (a) `/tenants` 는 SUPER_ADMIN 만, (b) `/partnerships` 는 TENANT_ADMIN 만(**SUPER_ADMIN 불가**), (c) `/subscriptions` 는 SUPER_ADMIN + TENANT_BILLING_ADMIN, (d) `/accounts` 는 SUPPORT_LOCK 불가(`account.read` 미보유).
- [ ] **AC-4** `partnership.manage` 가 권한 키 표와 TENANT_ADMIN 카드에 나타난다.
- [ ] **AC-5** 본문에 `net-zero` · `disjoint` · `tri-state` · `D2 confinement` · `센티넬` 등 스펙 전용 용어가 노출되지 않는다(코드 주석에는 유지 가능).
- [ ] **AC-6** `pnpm lint` 0 + `tsc --noEmit` 0 + `vitest run` 전체 GREEN. axe 위반 0.

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (**권위** — Permission Keys · Seed Roles · Seed Matrix)
- `projects/iam-platform/docs/guides/operator-auth-token-model.md` § 6 (계정 4개의 모자 · 토큰 축)
- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (D2 confinement · D3 no-escalation)
- `docs/adr/ADR-MONO-035` (관리 평면 ⟂ 운영 평면)
- `docs/adr/ADR-MONO-045-cross-org-partnership.md` (`partnership.manage`)

# Related Contracts

- 없음(정적 화면 — API 호출 없음).

---

# Target Service

- `platform-console` (`console-web`)

---

# Edge Cases

- 매트릭스 전치 후 `data-testid="iam-guide-cell-{role}-{href}"` 규약을 유지해야 기존 셀 단언이 살아남는다.
- `/permissions` 와 `/permission-sets` 는 **같은** 게이트(`operator.manage`)·같은 응답을 다르게 프레이밍한 화면 — 매트릭스에서 한 행으로 합치되 사용법에서는 두 메뉴로 나눠 설명한다.
- `/accounts` 의 「내보내기」는 `audit.read`, 「GDPR 삭제」는 `account.lock` 으로 게이트된다(직관과 어긋남 — 사용법에 명시).
- `/operator-groups` 는 인증만 통과하면 누구나 열 수 있는 정적 스텁 → 매트릭스에 넣지 않는다(권한 축이 없음).

---

# Failure Scenarios

- 매트릭스 확장 중 셀을 `rbac.md` 와 어긋나게 입력 → 운영자가 권한을 오해. Guard: AC-3 스팟체크 테스트가 4개 비직관 셀을 고정.
- `SUPER_ADMIN` 이 파트너십을 다룰 수 있다고 잘못 표기 → 실제로는 403. Guard: AC-3 (b).
- 문구 평이화 중 "상한(ceiling)"·"파생"처럼 의미가 뒤집히면 안 되는 단어를 뭉갬 → 오해. Guard: 의미 보존 리뷰 + AC-5 는 **용어 제거**가 아니라 **풀어쓰기**임을 명시.
- `pnpm lint` 생략 → `no-unused-vars` 로 CI 프런트 RED(tsc/vitest 가 못 잡음). Guard: AC-6.
