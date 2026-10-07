# Task ID

TASK-PC-FE-314

# Title

사이드바가 **권한·구독과 무관하게 거의 전부를 보인다** — 새 관리자는 눌러도 «권한 필요» 만 나오는 메뉴를 다수 본다. 플랫폼 전용 메뉴는 **숨기고**, 구독 안 한 도메인 메뉴는 **보이되 «구독 필요»** 로 표시한다

# Status

ready

# Owner

platform-console

# Task Tags

- console-web
- frontend
- navigation

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 권한 정보 출처(`GET /api/admin/me` · 레지스트리 · `permission-map.ts`)가 이미 있어 화면 계산 위주.
>
> 🔵 **소유자 결정(2026-10-07 UTC):** 대화에서 «콘솔에 처음 들어온 계정이 볼 수 있는 메뉴» → 섞은 방식 추천 → «이 방향으로 platform-console 티켓을 기안».

---

# Dependency Markers

- 선행: 없음. `TASK-PC-FE-313`(사이드바 «조직 계층»·«테넌트» 를 «조직 설정» 으로, #4214 머지 · review)은 **자리만** 옮겼다 — 이 티켓은 그 결과 위에서 노출 규칙을 바꾼다.
- 관련: `ADR-MONO-080` D9 = T1 → `TASK-MONO-773`(운영자 아닌 계정의 셸 = «가이드 · 테넌트 생성» 만). 773 이 그 상태를 더할 때 이 티켓의 계산 함수에 한 갈래를 더한다(이 티켓이 먼저 머지되면 773 이 재사용).

# Background (착수 전 측정, `origin/main` `9031eb4a8`)

| 사실 | 근거 |
|---|---|
| 사이드바가 숨기는 규칙은 **하나뿐** — 레지스트리 게이트(`productKey`)가 걸린 부모(팬 디렉터리)만 | `apps/console-web/src/shared/ui/console-nav-config.ts:91-104` (`visibleGroups`) · `:438` |
| 메뉴마다 필요한 권한이 **이미 표로 있다** — `gate: { kind: 'admin', permission }` 11 · `admin-per-card` 2 · `domain` 32 · `operator` 2 · `public` 3 | `apps/console-web/src/shared/guide/permission-map.ts` (`gate:` 전수) |
| 역할 × 권한 표가 **이미 있다** (rbac.md § Seed Matrix 사본, 드리프트 테스트 있음) | `permission-map.ts:46-54`(`RBAC_ROLES`) · `:70`(`RBAC_SEED_MATRIX`) · `tests/unit/permission-map-drift.test.ts` |
| «내 역할» 출처 — `GET /api/admin/me` 가 `roles[]` 를 준다. 콘솔이 이미 부른다 | `projects/iam-platform/specs/contracts/http/admin-api.md:898-917` · `apps/console-web/src/features/operators/api/operators-self-api.ts:72-87` |
| «활성 테넌트가 구독한 도메인» 출처 — 레이아웃이 매 화면 레지스트리(`getCatalog`)를 받는다(제품별 `tenants[]`) | `apps/console-web/src/app/(console)/layout.tsx:183-188` |
| 예: 새 B2B 관리자(`TENANT_ADMIN` · `TENANT_BILLING_ADMIN`)에게 `/tenants`(`tenant.manage`) · `/org-hierarchy`(`org.manage`) 가 보이고, 눌러도 «SUPER_ADMIN 전용» 안내 | `permission-map.ts` `/tenants` · `/org-hierarchy` 행 · `(console)/tenants/page.tsx:42,63` |

# Goal

| 메뉴 종류 | 처리 |
|---|---|
| `admin` 게이트 — 내 역할 중 **어느 것도** 그 권한을 갖지 않음(시드 표 기준) | **숨김** (예: 고객사 관리자에게 «테넌트» · «조직 계층») |
| `admin-per-card` 게이트 — 카드 권한 중 하나라도 가짐 | 보임 · 하나도 없으면 숨김 |
| `domain` 게이트 — 활성 테넌트가 그 도메인을 **구독하지 않음** | **보이되 «구독 필요» 배지**. 부모(예: WMS)에 한 번만 표시하고, 하위 화면은 지금의 안내 그대로 |
| `public` · `operator` | 지금 그대로 |

원칙: **숨김은 화면 편의일 뿐이고 권한 판정은 서버가 한다.** 숨긴 메뉴의 주소를 직접 쳐도 지금의 «권한 필요» 화면이 그대로 나온다.

# Scope

## In Scope

- `shared/ui/console-nav-config.ts` — `visibleGroups` 를 «레지스트리 게이트» 하나에서 **노출 계산 함수 하나**로 일반화. 입력 = 내 역할 · 활성 테넌트의 구독 도메인 · 레지스트리 제품 키. 순수 함수(프레임워크 import 없음, 기존 규율).
- 권한 판정은 `permission-map.ts` 의 `gate` 와 `RBAC_SEED_MATRIX` 를 **조인**해 쓴다 — 메뉴별 권한 목록을 새로 손으로 만들지 않는다(사본 금지).
- `(console)/layout.tsx` — `GET /api/admin/me` 의 `roles` 를 받아 사이드바에 넘긴다(레지스트리 호출처럼 동시 시작, 실패해도 셸은 안 깨짐).
- 사이드바 «구독 필요» 배지 + 그 부모를 눌렀을 때 «도메인 구독» 으로 가는 안내(배지 옆 링크 또는 툴팁 — AC-0 에서 정함).
- 단위 시험 + bite.

## Out of Scope

- 서버 권한 판정 · 각 화면의 «권한 필요» 안내(그대로)
- 샘플 방문자(ADR-MONO-074) — **지금처럼 전부 보인다**(샘플 셸은 역할이 없다 — 숨기면 포트폴리오 둘러보기가 망가진다)
- 운영자 아닌 계정의 셸(`TASK-MONO-773`)

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 위 Background 재측정(`origin/main` 이 움직였으면 숫자 갱신). 결정해 적는다: ① 시드 표에 **없는 역할**(커스텀 · 권한 세트)을 가진 운영자 — 기본은 **보임**(모르면 숨기지 않는다, 서버가 막는다) ② `/api/admin/me` 실패 시 — 기본은 **지금처럼 전부 보임**(실패가 메뉴를 지우면 장애가 «권한 없음» 으로 읽힌다) ③ 활성 테넌트가 없을 때 도메인 배지 — 붙이지 않음(테넌트를 고르라는 안내가 이미 있다) ④ 배지 → «도메인 구독» 안내 방식.
- [ ] **AC-1** — `TENANT_ADMIN` + `TENANT_BILLING_ADMIN`(새 B2B 관리자): 렌더된 사이드바에 «테넌트» · «조직 계층» **없음**, «도메인 구독» · «운영자 관리» · «파트너십» **있음**.
- [ ] **AC-2 (대조군)** — `SUPER_ADMIN`: 지금과 같은 메뉴 전부(회귀). `ORG_ADMIN`: «조직 계층» 있음.
- [ ] **AC-3** — 구독 0 인 활성 테넌트: 도메인 부모 메뉴가 **보이고** «구독 필요» 배지가 있다 · 구독한 도메인엔 배지 없음(대조군).
- [ ] **AC-4** — 숨긴 메뉴의 주소 직접 접근 → 지금의 «권한 필요» 화면 그대로(서버 판정 유지 — 기존 시험 재확인).
- [ ] **AC-5** — AC-0 ①② 의 «모르면 보임» 이 시험으로 고정된다(커스텀 역할 · `/me` 실패).
- [ ] **AC-6** — bite: 노출 계산에서 역할 조건을 지우면 AC-1 칸만 빨강.
- [ ] **AC-7** — `tsc` · `lint` · `vitest` 전체 rc=0. 🔴 콘솔 e2e 디렉터리(`tests/e2e`, `e2e-smoke`, 루트 `tests/federation-hardening-e2e`)에서 `nav-` testid 를 쓰는 스펙을 grep 해 **어떤 계정으로 어떤 메뉴를 찾는지** 대조한다 — 데모 시드 계정의 역할로 숨겨지는 메뉴를 찾는 스펙이 있으면 고친다. 머지 뒤 첫 `nightly-e2e.yml` 콘솔 잡 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/iam-platform/specs/services/admin-service/rbac.md` § Seed Matrix · § Permission Keys

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/me` (소비만, 변경 없음)

# Edge Cases

- 역할은 테넌트 범위로 붙는다(예: `TENANT_ADMIN @ A`) — 활성 테넌트가 B 이면 B 에선 그 권한이 없다. `/me` 의 `roles` 가 범위를 싣지 않으면 «어느 테넌트에서든 가진 권한» 으로 보고 보인다(서버가 막는다). AC-0 에서 응답 모양을 확인해 적는다.
- `RBAC_SEED_MATRIX` 는 rbac.md 의 사본이라 낡을 수 있다 — 드리프트 테스트가 키만 본다(파일 머리 주석 § 한계). 낡으면 «숨기지 말아야 할 것을 숨기는» 쪽으로 틀릴 수 있으므로 ①의 «모르면 보임» 과 함께 본다.
- 부모 아래 하위 항목이 전부 숨겨지면 부모도 숨긴다.

# Failure Scenarios

1. `/me` 실패 때 메뉴를 다 숨긴다 — 장애가 «권한 없음» 으로 보인다.
2. 구독 안 한 도메인을 숨긴다 — 새 관리자가 무엇을 켤 수 있는지 알 길이 없다(결정과 반대).
3. 메뉴별 권한 목록을 새로 손으로 만든다 — `permission-map.ts` 와 두 사본이 갈라진다.
4. 사이드바 testid 를 찾는 e2e 가 데모 계정 역할 때문에 조용히 깨진다 — nightly 전용이라 PR 에서 안 보인다.
