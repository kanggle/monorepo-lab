# Task ID

TASK-PC-FE-069

# Title

console-web `/erp` 운영 페이지의 read-model 조직뷰(org-view) leg를 best-effort로 강등 — read-model 장애가 권위 소스인 masterdata 마스터 읽기까지 통째로 degrade 시키지 않도록 (notification/approval/delegation leg와 동일 격리)

# Status

ready

# Owner

frontend (Opus 4.8 analysis / Sonnet 4.6 impl). console-web + console-integration-contract §2.4.8 1문장. No backend/producer change.

# Task Tags

- spec
- code
- test

---

# Dependency Markers

- **user request (2026-06-09 spec-review)** — `/erp` 페이지가 read-model 미배포/장애 시 전체 섹션 degrade("erp 운영 정보를 일시적으로 불러올 수 없습니다") 하던 동작을 점검한 결과 발견: 구현이 계약보다 엄격(과잉 결합).
- **relates**: TASK-PC-FE-049(read-model org-view 바인딩 도입), TASK-PC-FE-010(erp-ops 섹션), TASK-PC-FE-052(notification bell — 동일한 best-effort/shell-level degrade 선례).
- **계약 근거**: console-integration-contract §2.4.8 *Integrated read-model binding*(line 901-919)은 read-model을 **eventually-consistent 2차 프로젝션**으로 규정(미투영 참조=`null`+"동기화 중" 배지). org-view leg를 critical로 강제하는 조항은 **없음**. 병렬 바인딩 notification bell(line 959-968)은 명시적 best-effort.

# Goal

`getErpSectionState()`에서 `listEmployeeOrgViews`(read-model `/api/erp/read-model/employees`) leg를 hard `Promise.all`에서 빼내 `.catch(() => null)` best-effort로 이동 — masterdata 5개 마스터 읽기(권위 소스)만 섹션 degrade를 좌우하고, read-model 조직뷰는 그 카드만 비운다. 계약 §2.4.8에 이 resilience 경계를 1문장 명문화(notification 바인딩과 패리티).

# Scope

## In Scope

- **`projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.8 *Integrated read-model binding***: org-view leg의 resilience 경계 1항목 추가 — "org-view leg는 독립·best-effort; 503/timeout/network/기타 비-auth 에러는 **org-view 카드만** 비우고(empty + "동기화 중/일시 불가" affordance) masterdata 마스터 읽기나 다른 섹션을 degrade 시키지 않는다. notification 바인딩의 shell-level best-effort + approval/delegation leg의 `catch→null` 격리와 동일. masterdata 읽기 실패만 erp 섹션 전체를 degrade 한다. 단 어느 leg든 `401`은 공유 IAM 토큰이므로 whole-session 재로그인(per-leg degrade 아님)."
- **`src/features/erp-ops/api/erp-state.ts`**: `listEmployeeOrgViews(orgViewParams)`를 masterdata `Promise.all`(5 legs) 밖으로 이동 → approval/delegation처럼 `const employeeOrgViewsP = listEmployeeOrgViews(orgViewParams).catch(() => null)`로 선-기동(병렬 유지), masterdata `Promise.all` 성공 후 `[employeeOrgViews, approvalRequests, approvalInbox, delegationFacts]`를 함께 await. 주석(line 195-198의 "a failure on this leg alone degrades the whole section") 갱신.
- **`tests/unit/erp-state.test.ts`**: 신규 케이스 — read-model org-view leg만 503 실패(URL 분기 mock) → `state.degraded === false` + `state.employeeOrgViews === null` + masterdata 마스터(`departments` 등) non-null. 기존 happy-path(9 legs/asOf) + 503/403/401/429 케이스(masterdata 실패가 여전히 degrade/forbidden/redirect) 단언 유지.

## Out of Scope

- approval/delegation/notification leg 동작(이미 best-effort, 불변).
- read-model producer API, masterdata API, 라우팅(erp-gateway/Traefik path-prefix — 계약 line 916-919 불변).
- org-view 카드 UI 자체의 "동기화 중" 렌더 로직(ErpOpsScreen은 이미 `employeeOrgViews: null` 허용 — 타입상 `| null`).
- ERP 도메인 재개 여부(별건; 이 task는 resilience 정리만).

# Acceptance Criteria

- [ ] read-model org-view leg 단독 503/timeout/network → erp 섹션 **non-degraded**, masterdata 5개 마스터 정상 렌더, org-view 카드만 비움(`employeeOrgViews === null`).
- [ ] masterdata 읽기 503/timeout → 여전히 erp 섹션 전체 degrade(기존 동작 불변).
- [ ] 403(masterdata) → forbidden, 401(공유 토큰) → whole-session redirect(불변).
- [ ] happy-path: 9 legs 전부 발사 + asOf thread-through(org-view 포함) 불변.
- [ ] §2.4.8 계약에 org-view best-effort 경계 1문장 명문화(notification 패리티).
- [ ] `vitest` + `tsc --noEmit` green; scope = console-web + 계약 1문장.

# Related Specs

- `console-integration-contract.md` §2.4.8 (consumer 계약 — 본 task가 1문장 추가).

# Related Contracts

- **변경**: `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.8 *Integrated read-model binding* — resilience 경계 1항목 추가(producer API 무변경; consumer resilience 명문화).
- **불변**: erp `masterdata-api.md` / `read-model-api.md`(producer, 소비만).

# Target Service

- `platform-console` / `apps/console-web` — `features/erp-ops/api/erp-state.ts` + 계약 §2.4.8 + `tests/unit/erp-state.test.ts`.

# Architecture

- CQRS read-model은 권위 소스(masterdata)의 eventually-consistent 2차 프로젝션 → 그 가용성이 권위 데이터 표시를 막아선 안 된다(resilience 격리). 섹션 degrade 판정의 critical leg = masterdata 읽기로 한정; read-model/approval/notification/delegation은 모두 best-effort. 공유 IAM 토큰 401은 leg 무관 whole-session 재로그인(부분 authed state 없음).

# Edge Cases

- org-view 503 + masterdata 200 → 섹션 렌더, org-view 카드 empty(degrade 아님).
- org-view 401(토큰 만료) → `.catch→null`로 삼켜지나, masterdata leg가 동일 토큰으로 401 → hard `Promise.all`이 던져 redirect(공유 세션 — 격리 후에도 401 보장; 계약 line 166 주석 "a 401 would already be raised by the masterdata legs"와 동일 논리).
- read-model 빈 목록(이벤트 미투영) → 200 empty(degrade 아님, 정상).

# Failure Scenarios

- org-view를 best-effort로 옮겼는데 masterdata도 best-effort로 잘못 옮김 → masterdata 장애가 degrade를 못 일으킴(권위 소스 가림 은폐). AC가 "masterdata 503 → 여전히 degrade" 단언으로 가드.
- org-view promise를 try 블록 안에서 생성 → 직렬화(병렬 손실). 선-기동(try 밖 `const ...P = ....catch`)으로 병렬 유지(approval/delegation과 동일 패턴).
- 401 격리 회귀: org-view 401만 발생하고 masterdata가 우연히 통과하는 mock에선 redirect 안 됨 — 단 실서비스는 공유 토큰이라 동시 401(계약 보장). 테스트는 all-401 → redirect로 핀.

# Definition of Done

- [ ] erp-state.ts org-view leg best-effort 이동 + 주석 갱신
- [ ] §2.4.8 계약 1문장 명문화
- [ ] erp-state.test.ts 신규 격리 케이스 + 기존 케이스 green
- [ ] vitest + tsc --noEmit green; scope = console-web + 계약
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
