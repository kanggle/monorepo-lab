# Task ID

TASK-PC-FE-307

# Status

ready

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
> ⏳ **소유자 우선순위 결정 대기** — 착수 순서는 소유자가 정한다. 콘솔(Vercel) 변경이라 재굽기는 필요 없다.

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

- [ ] **AC-0** — 착수 시 file:line 재측정: 배너 판정(`OverviewDegradeBanner.tsx`)·서버 경고(`console-composition.ts`)·계약 § 2.4.9 문장·기존 시험(`tests/unit/shared/console-composition.test.ts:159` 의 `console_composition_all_down` 단언 등). 「전부 거절」 의 정의(사유 `TENANT_FORBIDDEN`·`PERMISSION_DENIED`·`MISSING_PREREQUISITE` 를 모두 `forbidden` 상태로 묶는가)를 카드 타입에서 확인해 적는다. 서버 이벤트를 나눌지 결정하고(계약 변경이면 계약 먼저) 그 이유를 적는다.
- [ ] **AC-1** — 6장 전부 `forbidden` → 권한 문구 배너가 렌더되고 «일시적으로 불러올 수 없습니다»·«다시 시도» 는 **없다**(렌더된 DOM 단언).
- [ ] **AC-2 (대조군)** — 6장 전부 `degraded` → 지금 배너 그대로. `forbidden` 과 `degraded` 가 섞인 전부-비정상 → 지금 배너(장애가 하나라도 있으면 재시도가 의미 있다). 하나라도 `ok` → 배너 없음.
- [ ] **AC-3** — bite: 새 분기를 지우면(전부 거절도 장애 배너) AC-1 칸만 빨강.
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
