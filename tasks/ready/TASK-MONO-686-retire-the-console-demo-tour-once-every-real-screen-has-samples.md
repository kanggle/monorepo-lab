# Task ID

TASK-MONO-686

# Title

⏳ 모든 실제 화면에 샘플이 찬 뒤 **콘솔 둘러보기(`/demo`)를 은퇴**시킨다 — `(demo)` 그룹 · `features/demo-tour` · `console-sample` 데이터셋 (`ADR-MONO-074` 실행 8/8)

# Status

ready

# Owner

monorepo

# Task Tags

- platform-console
- demo
- cleanup

---

# ⏳ SCHEDULED — DO NOT START before `TASK-PC-FE-282` ~ `TASK-PC-FE-288` 전부 `done/`

**AC-0 verify-then-act 게이트**: ① 일곱 티켓이 `projects/platform-console/tasks/done/` 에 있는가 ② 샘플 원장
(`shared/sample/coverage`)의 `pending` 이 **0** 인가 — 🔴 둘 다 **재서** 확인한다(INDEX 행이 아니라 파일과 원장). 하나라도
아니면 **STOP**. 🔴 원장이 0 이 아닌데 지우면 그 도메인의 방문자에게 «준비 중» 만 남는다(`ADR-MONO-074` § Roadmap 8 의 이유).

---

# Goal

`ADR-MONO-074` ACCEPTED(A · R1ⓐ · R2ⓐ · R3ⓐ) 의 마지막 단계. 익명 방문자가 실제 콘솔 화면을 샘플로 보게 된 뒤 남는
**두 번째 UI**(`/demo`)와 그것만 먹이던 데이터셋을 걷어 낸다.

루트 티켓인 이유: `infra/demo/public-data/**` 가 `projects/` 밖이다(`tasks/INDEX.md` § When to Use Root vs Project Tasks).

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/app/(demo)/**` · `src/features/demo-tour/**` 삭제
- `/demo` → `/dashboards/overview`, `/demo/<domain>` → 그 도메인의 실제 경로 **308**
- 둘러보기 전용 테스트 삭제: `tests/unit/demo-tour-*` (🔴 `demo-tour-console-guard-regression` 은 282 가 기대값을 바꿔 **살아 있는 가드**다 — 이름만 옮기고 지우지 않는다) · `e2e-smoke/demo-tour.spec.ts`
- `infra/demo/public-data` 의 `console-sample` 데이터셋 은퇴(픽스처 · 스냅샷 · 데이터셋 선언 · 소비자 마커)
- 저장소 안에서 콘솔 `/demo` 를 가리키는 링크·문구(론처 `infra/demo/aws/site/index.html` · README · `docs/portfolio.md` · `vercel-ignore.sh` 등) 갱신

## Out of Scope

- 팬·스토어 공개 봉투(`ADR-MONO-070` D1~D5) — 그대로
- 샘플 모드 자체 — 282 가 소유

---

# Acceptance Criteria

- [ ] **AC-0** 위 게이트.
- [ ] **AC-1** 삭제 전 **소비자 grep** — `demo-tour` · `console-sample` · `DEMO-PUBLIC-DATA-CONSUMER: console-web` · `'/demo` 를 저장소 전체에서 세고, 남는 참조가 0 이 될 때까지 표로 이 파일에 적는다(삭제가 남긴 것의 소비자).
- [ ] **AC-2** 308 매핑 테스트: `/demo` 와 삭제 시점 데이터셋의 **모든** 도메인 키가 실제 경로로 간다(🔴 목록을 손으로 적지 말고 삭제 직전 픽스처에서 뽑아 고정).
- [ ] **AC-3** `public-data` 패키지 테스트·발행 CLI 가 `console-sample` 없이 초록(데이터셋 목록을 세는 곳이 있으면 그 수도).
- [ ] **AC-4** 🔴 `scripts/` 에 추가·삭제가 생기면 **가드 전수** 실행(부분 선택 금지 — `CLAUDE.md` § Task Rules).
- [ ] **AC-5** `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(console-web) 각각 독립 `rc=0`.
- [ ] **AC-6** 머지 후 첫 `nightly-e2e.yml` 콘솔 스펙 1회 확인.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` § Roadmap 8
- `docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md` (D6 — 부분 대체됨)

# Related Contracts

- 없음(공개 봉투 계약에서 콘솔 데이터셋이 빠질 뿐 팬·스토어 계약 불변)

# Edge Cases

- 외부에 `/demo` 링크가 이미 퍼져 있다(포트폴리오·이력서) → 308 이 그 링크를 살린다. 지우지 않는다.
- `TASK-MONO-680` 이 바꾼 `/demo` 문구·`sample-actions.ts` 는 여기서 함께 사라진다(의도).

# Failure Scenarios

- 원장 `pending` 이 0 이 아닌데 착수 ⇒ AC-0 STOP.
- 데이터셋을 세는 가드가 수 불일치로 빨강 ⇒ AC-4 전수 실행에서 드러난다.

# Test Requirements

- AC-5 · AC-4

# Definition of Done

- [ ] `(demo)` · `features/demo-tour` · `console-sample` 0
- [ ] `/demo/**` 외부 링크가 실제 화면으로 간다

분석=Opus 5 / 구현 권장=Sonnet 5.
