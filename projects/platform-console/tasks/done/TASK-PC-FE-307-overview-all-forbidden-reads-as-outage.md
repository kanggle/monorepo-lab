# Task ID

TASK-PC-FE-307

# Status

done

# Title

운영 개요의 카드가 **전부 `forbidden`** 일 때(잘못된 계정·테넌트) 상단 배너가 «모든 도메인의 개요 정보를 일시적으로 불러올 수 없습니다 … 잠시 후 다시 시도» 라고 말한다 — 원인은 권한인데 장애로 읽힌다. 배너가 «전부 거절» 과 «전부 열화» 를 구별한다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- copy

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 판정 함수 하나(`isAllDown`)를 둘로 나누고 문구 한 벌 + 시험. 서버 로그(`console_composition_all_down`)를 같이 나눌지가 유일한 설계 질문이다.
>
> 🔵 **소유자 결정(2026-10-04 UTC): 새 티켓 3건 중 첫 번째로 착수.** (원문: ⏳ 소유자 우선순위 결정 대기 — 착수 순서는 소유자가 정한다.) 콘솔(Vercel) 변경이라 재굽기는 필요 없다.

---

# Dependency Markers

- 출처: 20차 AMI 창(2026-10-04 UTC · `i-0c4859442f56d70e0` · `ami-0d78d476824493d77` · `f0927bcd0`), 15:34:00 UTC. `TASK-MONO-758` AC-2 를 재려고 `platform@demo.com`(팬 전용으로 묶인 데모 플랫폼 운영자 — `TASK-MONO-751` § CORRECTION 2026-10-03)의 세션으로 `/dashboards/overview` 를 열었다 → 카드 전부 `forbidden` + 상단 배너 «모든 도메인의 개요 정보를 일시적으로 불러올 수 없습니다.» · Vercel 로그 `console_composition_all_down`. 758 은 그 시도를 **잘못된 계정**으로 판정에서 뺐다(758 20차 절).
- 선행/후속: 없음.

# Goal

카드 6장이 전부 `ok` 가 아닐 때, 배너는 그 이유의 **종류**를 말한다:
- 전부 `degraded`(또는 `degraded` 가 하나라도 섞임) → 지금 문구 그대로(«일시적으로 불러올 수 없습니다 … 잠시 후 다시 시도»).
- 전부 `forbidden` → 장애 문구가 아니라 권한 문구(예: «이 계정·테넌트로는 볼 수 있는 도메인 개요가 없습니다 — 테넌트를 바꾸거나 권한을 확인하세요»). 「다시 시도」 를 권하지 않는다(다시 해도 같다).

# Scope

## In Scope

- `apps/console-web/src/features/operator-overview/components/OverviewDegradeBanner.tsx` — `isAllDown`(`cards.every(c => c.status !== 'ok')`, `:19-22`)을 «전부 거절» / «그 밖의 전부-비정상» 으로 나눈다. 배너 문구·testid 결정.
- (설계 판단) 서버 `shared/composition/console-composition.ts:288-294` 의 `console_composition_all_down` 경고를 전부-거절일 때도 같은 이름으로 낼지, 다른 이벤트(예: `console_composition_all_forbidden`, info 레벨)로 낼지 — 계약 `specs/contracts/console-integration-contract.md` § 2.4.9 «Aggregation degrade discipline»(`:2348-2352`)이 이 이벤트 이름을 적고 있으므로, 바꾸면 **계약 먼저**.
- 단위 시험 + bite.

## Out of Scope

- 카드별 `forbidden` 표시(각 카드의 사유 문구) — 그대로.
- 합성 HTTP 상태(전부-비정상도 200 — ADR-MONO-017 D5.A) — 그대로.
- 데모 플랫폼 운영자(`platform@demo.com`)가 운영 개요 메뉴에 닿는 것 자체를 막을지 — 별도 판단(필요하면 후속 티켓).

# Acceptance Criteria

- [x] **AC-0** — 착수 시 file:line 재측정: 배너 판정(`OverviewDegradeBanner.tsx`)·서버 경고(`console-composition.ts`)·계약 § 2.4.9 문장·기존 시험(`tests/unit/shared/console-composition.test.ts:159` 의 `console_composition_all_down` 단언 등). 「전부 거절」 의 정의(사유 `TENANT_FORBIDDEN`·`PERMISSION_DENIED`·`MISSING_PREREQUISITE` 를 모두 `forbidden` 상태로 묶는가)를 카드 타입에서 확인해 적는다. 서버 이벤트를 나눌지 결정하고(계약 변경이면 계약 먼저) 그 이유를 적는다.
- [x] **AC-1** — 6장 전부 `forbidden` → 권한 문구 배너가 렌더되고 «일시적으로 불러올 수 없습니다»·«다시 시도» 는 **없다**(렌더된 DOM 단언).
- [x] **AC-2 (대조군)** — 6장 전부 `degraded` → 지금 배너 그대로. `forbidden` 과 `degraded` 가 섞인 전부-비정상 → 지금 배너(장애가 하나라도 있으면 재시도가 의미 있다). 하나라도 `ok` → 배너 없음.
- [x] **AC-3** — bite: 새 분기를 지우면(전부 거절도 장애 배너) AC-1 칸만 빨강.
- [ ] **AC-4 (라이브, ⚪)** — 다음 데모 창: `platform@demo.com` 로 `/dashboards/overview` → 권한 문구 배너 · `demo@demo.com`/`demo-corp` 에서 도메인 하나를 내려도 장애 배너는 안 뜬다(하나라도 ok).

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9 («Aggregation degrade discipline» · «`403` discipline (per-leg)»)
- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`
- `docs/adr/` ADR-MONO-017 D5.A (전부-비정상도 200)

# Related Contracts

- `console-integration-contract.md` § 2.4.9 — 서버 이벤트 이름을 바꾸거나 더할 때만 문장 수정(외부 API 변경 없음)

# Edge Cases

- `MISSING_PREREQUISITE`(finance 기본 계정 미설정)는 `forbidden` 상태로 오지만 의미는 «설정 필요» 다 — 전부 거절 문구가 그 경우에도 맞는지 AC-0 에서 확인한다.
- 카드 0장(`cards.length === 0`) — 지금처럼 배너 없음.
- 샘플 모드(익명 방문자) — 샘플 원장은 전부 `ok` 이므로 영향 없음(확인만).

# Failure Scenarios

1. **문구만 바꾸고 판정은 그대로** — 섞인 경우(일부 `degraded`)에도 권한 문구가 떠서 진짜 장애를 숨긴다(AC-2 가 막는다).
2. **서버 이벤트 이름을 계약 없이 바꾼다** — 계약 § 2.4.9 가 낡고, 로그 기반 관측이 조용히 0 이 된다.
3. **«다시 시도» 버튼만 숨기고 배너는 장애 문구** — 원인 오독이 그대로 남는다.

---

# Implementation Notes (2026-10-04 UTC)

> 분석=Opus 5.5 / 구현=Opus 5.5.

## AC-0 — 재측정 (착수 시, `c680a40fd`)

| 칸 | file:line | 읽은 것 |
|---|---|---|
| 배너 판정 | `apps/console-web/src/features/operator-overview/components/OverviewDegradeBanner.tsx:19-22` | `isAllDown = cards.length > 0 && every(status !== 'ok')` — 티켓의 `:19-22` 그대로 |
| 서버 경고 | `apps/console-web/src/shared/composition/console-composition.ts:288-294` | 같은 술어로 `logger.warn('console_composition_all_down', { route, requestId })` — 카드 상태는 안 싣는다 |
| 계약 | `specs/contracts/console-integration-contract.md:2348-2352` | «All-down still returns 200 … logs one `console_composition_all_down` warning» |
| 기존 시험 | `apps/console-web/tests/unit/shared/console-composition.test.ts:159` | 전부-비정상 → 그 이벤트 1건 단언 |
| 「전부 거절」 의 정의 | `apps/console-web/src/features/operator-overview/api/operator-overview-types.ts:51,63-67,94-98` | 상태는 `ok`/`degraded`/`forbidden` 셋. `PERMISSION_DENIED`·`TENANT_FORBIDDEN`·`MISSING_PREREQUISITE` **셋 다 `forbidden` 상태**로 온다 ⇒ 「전부 거절」 = `every(status === 'forbidden')`, 사유는 보지 않는다 |

- **`MISSING_PREREQUISITE` (Edge Case)** — 만드는 곳은 finance 레그 하나뿐이다(`console-composition.ts:348`). 그러니 「전부 거절」 이면 나머지 다섯 장은 반드시 권한·테넌트 사유다. 새 문구는 «각 카드의 사유를 확인하고, 테넌트를 바꾸거나 운영자 권한을 확인하세요» 라서 이 경우에도 맞다. finance 카드의 «사전 설정» 힌트(`DomainCardStates.tsx:86`)는 카드에 그대로 남는다.
- **샘플 모드 (Edge Case)** — 샘플 원장의 운영 개요는 6장 전부 `ok`(`src/shared/sample/fixtures/dashboards.ts:174-179`) ⇒ 어느 배너도 안 뜬다. 바뀐 것 없음.
- **카드 0장** — `isAllForbidden([])` = false(시험 단언) · 기존 `isAllDown([])` = false 그대로.

### 서버 이벤트 — **나누지 않는다** (계약 무변경)

1. 계약 문장의 «all-down» 은 «카드 6장 전부 비-`ok`» 다. 전부 거절도 그 정의에 들어가므로, 지금의 로그는 거짓이 아니다.
2. 이 티켓의 결함은 **사람이 읽는 배너 문구**다. 서버 로그는 운영자에게 안 보인다. 이름을 바꾸면 실패 시나리오 2(로그 기반 관측이 조용히 0)를 우리가 직접 만들게 된다.
3. 🔴 **알려진 한계**: 그래서 Vercel 로그의 `console_composition_all_down` 한 줄만으로는 «장애» 와 «잘못된 계정» 을 구별할 수 없다(이벤트에 카드 상태가 없다 — 20차 창 15:34Z 가 바로 그 경우였다). 구별이 필요해지면 이벤트 이름은 그대로 두고 필드(예: `allForbidden`)를 **추가**하는 후속 티켓으로 한다. 추가 필드라 계약의 이벤트 이름 문장은 그대로 남는다.

## 구현

- `OverviewDegradeBanner.tsx` — `isAllForbidden(cards)` 추가(barrel `index.ts` 에도 export). 전부 거절이면 다른 배너를 낸다. testid 는 `operator-overview-all-forbidden`, 문구는 «이 계정과 테넌트로 볼 수 있는 도메인 개요가 없습니다.» / «모든 도메인이 이 운영자의 개요 조회를 허용하지 않았습니다. 각 카드의 사유를 확인하고, 테넌트를 바꾸거나 운영자 권한을 확인하세요.»이고, **재시도 버튼은 없다**(`retry={null}`). 그 밖의 전부-비정상(섞인 경우 포함)은 기존 배너·testid·재시도를 **글자 그대로** 유지한다.
- 공유 셸 `shared/ui/DegradeBanner.tsx` 무수정 — `retry: ReactNode` 라 `null` 을 받는다.
- e2e 영향 없음: `operator-overview-all-degraded` 는 e2e 스펙에서 0건, 단위 시험에서만 쓰인다.

## 검증 (worktree `mlab-pcfe307`, `pnpm install --frozen-lockfile`)

- `tests/unit/features/operator-overview/OverviewDegradeBanner.test.tsx` — 신규 5칸: `isAllForbidden` 헬퍼, AC-1(권한 배너 · «일시적으로 불러올 수 없습니다»·«다시 시도» 텍스트 없음 · `operator-overview-retry-banner` 없음), AC-2 세 칸(전부 `degraded` → 장애 배너+재시도 · `forbidden`+`degraded` 혼합 → 장애 배너 · `ok` 한 장 → 배너 없음). 기존 6칸은 무수정.
- **AC-3 bite**: 분기 조건을 `false && isAllForbidden(...)` 로 바꿔 돌린 결과 **1 failed / 10 passed**였고, 실패한 칸은 AC-1 하나였다. 원본은 scratchpad 백업에서 복원했고 `cmp` 일치를 확인했다.
- `tsc --noEmit` rc=0 · `pnpm lint` rc=0(«No ESLint warnings or errors») · `vitest run` 전체 **337 파일 / 3804 통과**, rc=0.
- ⚪ **AC-4** — 라이브라서 다음 데모 창에서 잰다. review 에서 닫히지 않는 칸이다.

---

## CORRECTION (2026-10-05 UTC) — 21차 창 판정 (i-0aa3180ae21de4445 · ami-0a7b20c97325be01d · 678b6d003) — AC-4 닫힘

> 분석=Opus 5.5. 덧붙이기만 한다. 콘솔은 Vercel 이라 21차 AMI 와 무관하고, 판정은 소유자 브라우저 관찰이다.

| 시각(UTC) | 계정 · 상태 | 관찰 |
|---|---|---|
| 06:59:02 | `demo@demo.com` · `demo-corp` · ERP 묶음 **꺼짐** | ERP 카드 `DOWNSTREAM_ERROR` · 나머지 ok/forbidden(MISSING_PREREQUISITE) — **상단 배너 없음**(하나라도 ok) ✅ AC-4 둘째 칸 |
| 07:00:55 | `platform@demo.com` · ERP 꺼짐 | 거절 5 + ERP `DOWNSTREAM_ERROR` 1 = 섞인 전부-비정상 → **기존 장애 배너**(«일시적으로 불러올 수 없습니다» + 다시 시도) ✅ AC-2 대조군의 라이브 판 |
| 07:05:51 | `platform@demo.com` · `console-erp` 를 추가로 켬(07:03:26 → ready 07:05:13) | 6장 전부 `forbidden`(PERMISSION_DENIED · TENANT_FORBIDDEN ×4 · MISSING_PREREQUISITE) → **«이 계정과 테넌트로 볼 수 있는 도메인 개요가 없습니다.»** 권한 배너 · «다시 시도»·«일시적으로» 없음 ✅ AC-4 첫째 칸 |

### 4차원 (close chore)

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4153` | `state=MERGED` · mergeCommit `1427ef425` |
| (b) origin/main 조상 | 참 |
| (c) 머지 시점 실패 체크 | 65 중 **FAILURE 0** |
| (d) `# Acceptance Criteria` | AC-0 ~ AC-3 `[x]` · **AC-4 = 이 절에서 닫힘** (동사 «권한 문구 배너 · 하나라도 ok 면 장애 배너 안 뜸» = 위 표) |

⇒ **`review/` → `done/`.**
