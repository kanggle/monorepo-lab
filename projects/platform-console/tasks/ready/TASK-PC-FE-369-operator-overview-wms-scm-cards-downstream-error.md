# Task ID

TASK-PC-FE-369

# Title

운영자 통합 개요의 «WMS 출고» · «SCM 발주» 카드가 `DOWNSTREAM_ERROR` 이고 도메인 상태 요약이 «WMS 점검 불가» — 같은 `WMS_ADMIN_BASE_URL` 을 쓰는 `/wms` 화면은 정상, 서비스 로그엔 그 요청이 없다

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (원인 미확정 — 콘솔 서버 합성 · 배포 환경 변수 · 게이트웨이 경로 중 어디인지 측정부터, 콘솔 서버 로그를 못 보는 환경)
> 기안 2026-10-23 UTC — `TASK-PC-FE-364` AC-3 라이브(33차 창)의 ❌. 측정 먼저(AC-0).

# Dependency Markers

- 출처: `TASK-PC-FE-364` § 라이브 확인 (2026-10-23 UTC · 33차 창) — AC-3 ❌.
- 후속: 고친 뒤 `TASK-PC-FE-364` AC-3(권한 세트별 «권한 없음» 표 포함)을 다음 창에서 다시 잰다.
- 관련 · 직렬: `TASK-PC-FE-368`(ready — 같은 `shared/composition/` 합성에 지표 칸을 더한다). 둘이 같은 파일을 고치면 공유 파일 시리즈 = 한 worktree · 직렬.
- 관련: `TASK-PC-FE-364`(데이터 다리 정의) · `TASK-PC-FE-310`(도메인 상태 요약).
- 선행: 없음.

# Goal

Fix issue found in TASK-PC-FE-364. 33차 창(2026-10-23 UTC), 로그인(`demo-corp`) 운영자 통합 개요에서:

- «WMS 출고» · «SCM 발주» 카드가 «하위 서비스에서 오류가 발생했습니다 · 사유: DOWNSTREAM_ERROR»(두 번 로드, 수 분 간격, 같음).
- 도메인 상태 요약 «WMS 점검 불가».
- 나머지 IAM 0 · Finance 0 · ERP 2 · ecommerce 0 은 표시.
- 해당 서비스 로그(wms-admin-service · scm procurement)에 그 요청의 오류가 없다 → 요청이 서비스에 닿지 않은 것으로 보인다.
- 같은 `WMS_ADMIN_BASE_URL` 을 쓰는 `/wms` 화면은 정상.
- 콘솔 서버(Vercel) 로그는 볼 수 없었다(CLI 없음).

다리 정의(`apps/console-web/src/shared/composition/console-composition.ts`): 데이터 `:381-394` — wms `${WMS_ADMIN_BASE_URL}/dashboard/orders?status=RECEIVED&page=0&size=1` · scm `${SCM_GATEWAY_BASE_URL}/api/v1/procurement/po?status=DRAFT&page=0&size=1`(둘 다 `domainHeaders`), 상태 `:416-433` — `${originOf(base)}/actuator/health`(자격 없음, `originOf` = `:308`). 🔵 기안 때 코드 읽기(측정 아님): 실패 분류 `:192-248` 은 fetch 예외 · 2xx 인데 JSON 아님 · health 503 인데 health 문서 아님 · 401/403 밖의 모든 상태(404 포함)를 **같은** `DOWNSTREAM_ERROR` 로 접는다 — 화면 문구만으로는 «서비스가 오류를 냈다» 와 «요청이 서비스에 안 닿았다» 를 구분할 수 없다. 상태 다리는 base 의 origin 만 쓰므로 base 에 경로 접두가 있으면 데이터 다리와 다른 곳을 친다.

목표: 두 카드가 실제 값(또는 그 도메인의 정당한 «권한 없음»)을 그리고, 도메인 상태 요약이 WMS 를 실제 상태로 보인다.

# Scope

## In Scope

- **AC-0 측정** (file:line · 실측):
  - ① 다리 URL 을 실제 배포 값으로 풀어 적는다(Vercel Production env 의 `WMS_ADMIN_BASE_URL` · `SCM_GATEWAY_BASE_URL` — 비밀이 아닌지 확인 후, 아니면 모양만) · `/wms` 화면이 부르는 URL · 헤더 · 토큰과 무엇이 다른가.
  - ② 같은 요청을 콘솔 서버 밖에서 재현(같은 URL · 같은 헤더 모양 · 운영자 토큰) — 게이트웨이 · Traefik 앞에서 무엇을 받는가(상태 · 본문 · `Content-Type`). `/actuator/health` 가 그 origin 에서 무엇을 받는가.
  - ③ 위 `:192-248` 분류 중 어느 갈래였는가를 보이게 할 수단 — 콘솔 서버 로그가 없는 환경에서 원인을 남길 길(`logLeg` 의 출력 위치 · 응답 `reason` 세분 · span).
  - ④ 시험 · e2e(federation · nightly)가 이 두 다리를 실제 URL 로 돌렸는가 — 그쪽은 초록인데 라이브가 빨간 이유.
- 수선: AC-0 이 정한다(URL 조립 · env · 게이트웨이 경로 · 오류 분류). 계약(`console-integration-contract.md` § 2.4.9.1 · § 2.4.9.2)이 바뀌면 계약 먼저. 사용자에게 보이는 문구가 바뀌면 소유자 승인.

## Out of Scope

- 카드 정의 · 문구(`TASK-PC-FE-364`) · 실적 지표 칸(`TASK-PC-FE-368`).
- 서버 쪽 변경 — AC-0 에서 서버 · 게이트웨이 · 데모 배선 결함으로 판명되면 그 프로젝트에 별도 티켓.

# Acceptance Criteria

- [ ] **AC-0** — 위 ①~④ 를 file:line · 실측 출력으로. 원인이 콘솔 밖(게이트웨이 · 서버 · 데모 배선)이면 STOP 하고 해당 프로젝트 티켓을 기안한다.
- [ ] **AC-1** — 수선 + 시험(원인을 재현하는 고정값 · 실패 분류) · `tsc --noEmit` · `next lint` · vitest(`--maxWorkers=4 --minWorkers=2`) · Client graph 가드 둘 · 큐 가드 셋(스테이지 뒤) rc=0. SSR · 합성이 바뀌면 머지 전 브랜치 nightly 콘솔 잡 · federation e2e.
- [ ] **AC-2** — 배포(Vercel Production 기록) · 라이브: 로그인(`demo-corp`) 개요의 «WMS 출고» · «SCM 발주» 카드가 값을 그리고 도메인 상태 요약에 «WMS 점검 불가» 가 없다(다음 데모 창 — wms · scm 묶음이 켜져 있을 때).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`
- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (데이터 다리) · § 2.4.9.2 (상태 다리)
- wms `projects/wms-platform/specs/contracts/http/admin-service-api.md` (`/dashboard/orders`) · scm procurement (`/api/v1/procurement/po`)

# Edge Cases

- 묶음(bundle)이 꺼진 도메인은 정당한 저하다 — 이 결함과 구분돼야 한다(30차 창의 카드 `DOWNSTREAM_ERROR` 는 `ecommerce` 묶음이 꺼진 경우였다 — `TASK-MONO-672` § Order 3). 33차 창은 9묶음 전부 ready 였다.
- 운영자가 그 도메인 권한이 없을 때의 «권한 없음» 은 바뀌면 안 된다.

# Failure Scenarios

1. 단위 시험의 URL 모의값으로 초록 — 실제 배포 env 에서만 나는 결함일 수 있다 → AC-0 ① · ②.
2. 묶음이 꺼진 창에서 «재현» 하고 원인으로 오인 → 창의 묶음 상태를 같이 적는다.
