# Task ID

TASK-PC-FE-313

# Status

done

# Title

사이드바 «조직 계층»·«테넌트» 를 «관리 ▸ IAM» 에서 «조직 설정» 그룹으로 옮긴다. «조직 설정» = 회사 구조·산 것·맺은 관계(조직 계층 → 테넌트 → 도메인 구독 → 파트너십), IAM = 누가 무엇을 할 수 있나

# Owner

platform-console

# Task Tags

- console-web
- frontend
- navigation

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 사이드바 설정 이동 · 가이드 표 순서 · 단위 시험. 도메인 로직 없음. (실제 구현: Opus 5.5)
>
> 🔵 **소유자 결정(2026-10-07 UTC):** 대화에서 «도메인 구독과 파트너십 메뉴는 IAM 안과 밖 중 어디가 어울리나» → «밖. 이미 «조직 설정» 에 있다. 오히려 IAM 안의 «조직 계층»·«테넌트» 가 기준과 어긋난다 — 같은 기준이면 그 둘도 «조직 설정» 으로» → «진행».

---

# Dependency Markers

- 선행/후속: 없음.
- 🔵 **번호 변경**: 처음엔 `TASK-PC-FE-312` 로 기안했으나, 다른 세션의 #4211 이 먼저 `main` 에 `TASK-PC-FE-312`(조직 계층 노드 상세의 테넌트 추가·옮기기·빼기, `ready/`)를 넣어 **313 으로 바꿨다**. 그 312 는 `/org-hierarchy` 화면 **안**을 바꾸고 사이드바는 건드리지 않는다 — 이 티켓과 겹치는 파일 없음(그 화면 주석 한 줄 제외).
- 🟡 같은 시기 `ready/` 의 루트 `TASK-MONO-773`(ADR-MONO-080 단일 «테넌트 생성» 입구)이 **같은 파일 `console-nav-config.ts`** 에 «테넌트 생성» 진입점을 더한다. 이 티켓이 먼저 머지되면 773 은 «조직 설정» 그룹 기준으로 진입점을 둔다(재배치만, 의미 충돌 없음). 동시에 열리면 직렬 머지.

# Background (착수 전 측정, `origin/main` `f7e274ed1`)

| 사실 | 근거 |
|---|---|
| «도메인 구독»·«파트너십» 은 이미 IAM 밖 «조직 설정» 그룹 | `apps/console-web/src/shared/ui/console-nav-config.ts:215-228`(착수 전) |
| «조직 계층»(`/org-hierarchy`)·«테넌트»(`/tenants`)는 «관리 ▸ IAM» 드릴의 자식 | 같은 파일 `:171-182`(착수 전) |
| 두 화면의 백엔드·라우트·게이트는 메뉴 위치와 무관 — 사이드바 자리만 바뀐다 | `permission-map.ts` 의 `/org-hierarchy`(`org.manage`) · `/tenants`(`tenant.manage`) 행 |
| 권한 지도 표·순서는 `GROUPS` 에서 파생 — 손으로 고칠 순서 없음. `area` 만 메뉴 그룹 이름표 | `permission-map.ts` `navLeaves()` · `resolvePermissionMap()` |
| IAM 가이드는 `areas={['iam','customer-identity','org']}` — `area` 를 `org` 로 바꿔도 가이드에서 사라지지 않는다 | `features/iam-guide/components/IamGuideScreen.tsx:91` |
| testid `nav-iam-tenants`·`nav-iam-org-hierarchy` 의 소비자 = 단위 시험 한 파일뿐. e2e(`*.spec.ts`)는 두 메뉴를 testid·라벨·URL 어느 것으로도 부르지 않는다 | 저장소 grep(`node_modules` 제외) |

# Goal

1. 사이드바 그룹 기준을 하나로: **«조직 설정» = 회사 구조·산 것·맺은 관계, «IAM» = 누가 무엇을 할 수 있나.**
2. 라우트·기능·권한 게이트는 그대로다. 사이드바 자리, 그 자리를 설명하는 가이드 표, 주석만 바뀐다.

# Scope

## In Scope

- `console-nav-config.ts`: IAM 드릴에서 «조직 계층»·«테넌트» 제거 → «조직 설정» 그룹 맨 앞에 `조직 계층 → 테넌트 → 도메인 구독 → 파트너십` 순서로. 그룹에 testid `nav-group-org-settings`. 옮긴 두 항목의 testid 를 그룹 이름에 맞춰 `nav-org-hierarchy`·`nav-tenants` 로(형제 `nav-subscriptions`·`nav-partnerships` 와 같은 꼴).
- `permission-map.ts`: 두 행을 «조직 설정» 절로 옮기고 `area: 'iam' → 'org'`.
- `features/iam-guide/data.ts`: `CONSOLE_MENUS`·`SCREEN_ACCESS` 에서 두 항목을 «계정 운영» 뒤, «도메인 구독» 앞으로(사이드바 순서).
- 주석: `app/(console)/tenants/page.tsx` · `app/(console)/org-hierarchy/page.tsx` · `features/tenants/components/TenantsScreen.tsx` 의 «IAM ▸ …» 경로 문구.
- 스펙: `specs/services/console-web/architecture.md` 라우트 트리에 `org-hierarchy/`·`tenants/` 행(«조직 설정» 그룹 표기).
- 단위 시험: `tests/unit/sidebar-iam-group.test.tsx`.

## Out of Scope

- 백엔드 위치(구독을 IAM 밖 서비스로 빼는 일) — 대화에서 «결제가 붙을 때» 로 미룬 별개 설계.
- 화면 내용·게이트·라우트 변경.
- «테넌트 생성» 입구 통합 — `TASK-MONO-773`.

# Acceptance Criteria

- [x] **AC-1** — IAM 드릴의 링크가 정확히 `/iam/guide · /iam · /operators · /operator-groups · /permissions · /permission-sets · /audit` 7개이고 `/tenants`·`/org-hierarchy` 가 없다(testid 무관 — href 로 단언).
- [x] **AC-2** — «조직 설정» 그룹(`nav-group-org-settings`)이 1뎁스 평면 링크 `조직 계층 → 테넌트 → 도메인 구독 → 파트너십` 을 DOM 순서대로 렌더한다.
- [x] **AC-3** — `/tenants` · `/tenants/<id>`(상세) · `/org-hierarchy` 딥링크에서 IAM 드릴이 열리지 않고(IAM 은 접힌 버튼, `aria-current` 없음) 해당 항목 하나만 `aria-current="page"`.
- [x] **AC-4** — 권한 지도 드리프트 가드(`permission-map-drift.test.ts`) 초록 — 두 행이 고아/누락 없이 nav 순서로 조인된다.
- [x] **AC-5** — AC-1~AC-3 시험이 **옛 설정에서 빨강**(bite): `origin/main` 의 `console-nav-config.ts` 를 넣고 돌리면 이 티켓이 바꾼/더한 5개만 실패하고 나머지 13개는 통과, 새 설정을 되돌려 놓으면 18/18.
- [x] **AC-6** — console-web `tsc --noEmit` · `next lint` rc=0, vitest 전량 통과(아래 기록 참고).
- [ ] **AC-7** — 라이브: 다음 데모 창에서 사이드바 «조직 설정» 4항목 · IAM 7항목을 눈으로 확인. ⚪ 이 티켓 안에서는 못 잰다(로컬 스택 미기동).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` § Internal Structure Rule (라우트 트리)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (엔타이틀먼트 ↔ IAM 평면 분리 — «조직 설정» 그룹의 근거)
- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` (조직 계층 = 테넌트 위 그룹핑, AWS Organizations 대응)

# Related Contracts

- 없음 — API·이벤트 변경 없음.

# Edge Cases

- `/tenants/<id>` 상세 경로가 여전히 «테넌트» 를 밝히는가 → 접두 일치(`console-nav-matching.ts`)로 유지. AC-3 에 상세 경로 포함.
- IAM 가이드의 메뉴 표에서 두 화면이 사라지는가 → `area: 'org'` 도 IAM 가이드가 읽는 범위라 유지된다(Background).
- 옛 testid 를 외부(e2e·스크립트)가 쓰는가 → grep 0건. 쓰는 곳이 생기면 그 PR 이 새 testid 를 쓴다.

# Failure Scenarios

- 옛 testid 를 찾는 숨은 e2e 가 nightly 에서 빨강 → 머지 뒤 첫 `nightly-e2e.yml` 을 한 번 확인한다(CLAUDE.md «Post-merge nightly check»).
- `TASK-MONO-773` 이 옛 IAM 자리를 전제로 진입점을 넣는 경우 → 같은 파일 충돌로 드러나 직렬 머지에서 해소(Dependency Markers).

# Implementation Record (2026-10-07 UTC)

- 변경: `console-nav-config.ts` · `permission-map.ts` · `iam-guide/data.ts` · 주석 3곳 · `architecture.md` · `sidebar-iam-group.test.tsx`.
- 게이트(워크트리, pnpm `--frozen-lockfile` 설치 후): `tsc --noEmit` rc=0 · `next lint` rc=0(경고 0) · 대상 6파일 84/84.
- 전량 vitest: 338 파일 중 337 통과 · 3821 중 3818 통과 — 실패 3건은 전부 `LedgerOpsScreen.test.tsx` «계정 탭»(요소 미발견). 이 파일은 nav 를 import 하지 않고 **단독 재실행 53/53 통과** → 전량 병렬 부하에서만 나는 이 티켓 무관 실패로 판정. CI 전량 레인 결과로 다시 확인한다.
- bite(AC-5): 옛 설정 → `sidebar-iam-group.test.tsx` 5 실패 / 13 통과(rc=1), 새 설정 복원 → 통과.

## CORRECTION — close (2026-10-09 UTC, 4차원 검증)

- **AC-7 닫힘**: 소유자가 **데모 창**에서 사이드바 «조직 설정» 4항목 · IAM 7항목을 눈으로 확인했다(2026-10-09 UTC 대화 — 확인 범위 질문에 «321 가이드, 313 · 315 메뉴» · 장소 «데모 창»). 위 AC 목록의 `[ ]` 는 이 절이 닫는다.
- (a) #4214 `MERGED` 2026-10-07T10:43:27Z, squash `5e213a8e7`.
- (b) `5e213a8e7` 이 `origin/main` 에 있음.
- (c) 머지 시점 체크 SUCCESS 15 · SKIPPED 51 · FAILURE 0.
- (d) AC-1~7 전부 닫힘.
