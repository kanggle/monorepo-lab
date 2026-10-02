# Task ID

TASK-PC-FE-302

# Status

review (2026-10-02 UTC — AC-9 는 머지 뒤 첫 nightly)

# Title

`ADR-MONO-081` 단계 2 — **운영 개요 · 도메인 상태** 합성을 console-web 서버로

# Owner

platform-console

# Task Tags

- console-web
- integration
- resilience

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 레그 6개의 자격 선택·실패 격리·401 처리. 틀리면 만료 세션이 «일부 장애» 로 숨는다.

---

# Dependency Markers

- **선행**: `TASK-MONO-755`(계약) done.
- **후속**: `TASK-MONO-756`(e2e) · `TASK-MONO-757`(삭제).

# Goal

`app/api/console/dashboards/operator-overview/route.ts` · `domain-health/route.ts` 가 console-bff 로 프록시하는 대신 **console-web 서버에서 직접** 각 도메인 조회 API 를 불러 같은 응답 봉투를 만든다. console-bff 의 `OperatorOverviewCompositionUseCase` · `DomainHealthCompositionUseCase` 가 하던 일이다.

# Scope

## In Scope

- 두 라우트 + 합성 모듈(`features/` 아래 — 기존 도메인 안 개요 fan-out 과 같은 자리·모양)
- 레그별 자격: IAM = `getOperatorToken()`, 나머지 = `getDomainFacingToken()`(ADR-017 D4)
- 단위 시험 · 계약 고정 시험

## Out of Scope

- 알림 인박스(`TASK-PC-FE-303`) · console-bff 코드 삭제(`TASK-MONO-757`) · 화면 컴포넌트(선 모양이 같으므로 변경 0이어야 한다)

# Acceptance Criteria

- [x] **AC-1** — 두 라우트가 `CONSOLE_BFF_URL` 을 읽지 않는다. 응답 JSON 이 `specs/contracts/fixtures/operator-overview-leg-bodies.json` 기반 픽스처로 console-bff 응답과 **같은 모양**이다(계약 고정 시험).
- [x] **AC-2** — 🔴 **대조군 1**: 레그 하나(예: scm)가 타임아웃/5xx 면 응답 200, 그 카드만 열화, 나머지 카드는 실제 값.
- [x] **AC-3** — 🔴 **대조군 2**: 레그 하나가 401 이면 응답 401(`TOKEN_INVALID`) — 열화 카드 200 이 **아니다**.
- [x] **AC-4** — 활성 테넌트 없음 → 400 `NO_ACTIVE_TENANT`, **레그 호출 수 0**(목 호출 횟수 단언).
- [x] **AC-5** — 레그별 헤더 단언: IAM 레그는 운영자 토큰, 나머지는 도메인용 토큰. `X-Tenant-Id` 는 활성 테넌트.
- [x] **AC-6** — 라이더 R2: 회로 차단기 없음, 레그마다 타임아웃. 🔴 타임아웃 값은 **Vercel 함수 실행 한도를 재고 나서** 정하고, 잰 값과 출처를 이 파일 § 결과에 적는다(값을 지어내지 않는다 — 지금 `maxDuration` 설정 0건).
- [x] **AC-7** — 라이더 R1: 레그마다 구조화 로그 한 줄(`domain` · `status` · `latencyMs`), 토큰·PII 없음.
- [x] **AC-8** — 샘플 방문자(`sampleGate`)는 여전히 레그를 부르지 않는다(기존 시험 유지).
- [ ] **AC-9** — 🔴 콘솔 full-stack e2e 는 nightly 에서만 돈다 — 머지 뒤 다음 nightly 의 `Platform Console E2E full-stack` 결과를 확인해 적는다.

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md` D1·D2
- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/platform-console/specs/services/console-bff/architecture.md`(옮겨 올 동작의 출처)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 · § 2.4.9.2

# Edge Cases

- finance 기본 계정 id 없음 → finance 카드만 `MISSING_PREREQUISITE`.
- 운영자 권한이 좁아 한 레그가 403 → 그 카드만 «권한 없음»(401 과 다르다).
- 샘플 원장의 `core: 'console-bff'` 이름 — **바꾸지 않는다**(ADR-081 라이더 대조 — 바꾸면 원장 가드가 문다).

# Failure Scenarios

1. `Promise.all` 로 묶어 레그 하나 실패가 화면 전체 502 가 된다(ADR-017 D5 위반).
2. 401 을 `allSettled` 의 rejected 로 함께 삼켜 열화 카드로 그린다.
3. 레그 타임아웃이 함수 한도보다 길어 함수가 먼저 죽고, 화면은 카드 열화 대신 전체 실패를 본다.

---

# 결과 (2026-10-02 UTC)

**바뀐 것**: `app/api/console/dashboards/{operator-overview,domain-health}/route.ts` 가 console-bff 로 프록시하는 대신 **새 모듈 `shared/composition/console-composition.ts`** 로 직접 합성한다. 그 모듈에는 네트워크 원시 호출이 없다 — 라우트가 `sampleGate(` **뒤에서** 자기 `fetch` 를 넘기고, 그 자리에서 `resolveBackendUrl` 로 데모 주소를 해석한다(샘플 방문자 가드 · `check-fetch-resolution.mjs` 둘 다 구조로 만족 — 면제 표시 없음).

| AC | 근거 |
|---|---|
| AC-1 | `CONSOLE_BFF_URL` 을 두 라우트가 더 읽지 않는다. `console-composition.test.ts` «producer bodies pass through verbatim» — 픽스처(`operator-overview-leg-bodies.json`) 여섯 본문이 카드 `data` 와 `toEqual`, 순서 고정. 라우트 시험이 응답을 **운영 스키마** `OperatorOverviewSchema` / `DomainHealthSchema` 로 파싱 |
| AC-2 | 대조군 1 — scm 5xx / 네트워크 / 읽을 수 없는 본문 → scm 만 `degraded`, 나머지 5 `ok`. 모두 내려가도 6장 봉투 |
| AC-3 | 대조군 2 — erp 401 → `{ unauthorized: true }` → 라우트 401 `TOKEN_INVALID`. 403 은 카드 `forbidden`(본문의 `TENANT_FORBIDDEN` 이면 그 이유) |
| AC-4 | 활성 테넌트 없음 → 400, **fetch 호출 0**(두 라우트 모두). 반쪽 세션 → 401, 호출 0 |
| AC-5 | IAM = `Bearer <운영자 토큰>` + `X-Tenant-Id` · 나머지 5 = `Bearer <도메인용 토큰>`, `X-Tenant-Id` 없음(각 도메인 화면 클라이언트와 같은 규칙). health 6 레그는 자격·테넌트 헤더 없음 |
| AC-6 | `LEG_TIMEOUT_MS = 4000`. **잰 것**: Vercel 문서 «Maximum Duration» 원문(2026-10-02 수집, 페이지 갱신 2026-08-24) — fluid compute 기준 모든 요금제 기본 300 s. **못 잰 것**: 이 프로젝트의 대시보드 재정의값(저장소에서 Vercel CLI·API 접근 없음, `maxDuration` 설정 0건). 그래서 Vercel 이 내놓았던 가장 보수적인 한도(10 s)에서도 버티게 골랐다. 회로 차단기 없음 — 시험이 `CIRCUIT_OPEN` 이 나오지 않음을 단언. 값과 근거를 계약 § 2.4.9.0 에 적었다 |
| AC-7 | 레그마다 `console_composition_leg` 한 줄(`route` · `domain` · `status` · `reason` · `latencyMs` · `requestId`). 시험이 로그 전체에 토큰·계정 id 가 없음을 단언 |
| AC-8 | 샘플 방문자 경로 그대로 — `sample-mode-proxies.test.ts` ① fetch 0, 샘플 core 이름 `console-bff` 유지(라이더 대조 — 바꾸면 원장 가드) |
| AC-9 | ⏳ 머지 뒤 첫 nightly `Platform Console E2E full-stack` |

**🔴 대조군이 무는지 확인(bite)**: ① 데이터 레그 401 을 `degraded` 로 바꾸면 «erp 401» 셀 1개 실패 ② 레그 하나라도 `degraded` 면 빈 봉투를 내게 바꾸면 7개 실패. 원본 복원 뒤 19/19.

**🔴 발견 — console-bff 의 health 503 처리가 계약과 달랐다.** Spring `/actuator/health` 는 DOWN/OUT_OF_SERVICE 일 때 HTTP **503** 이다. 계약 § 2.4.9.2 는 그것을 «성공한 health 문서 → `ok` 카드 + `data.status`» 로 그리라고 하는데, console-bff 는 `RestClient.retrieve()` 가 503 에서 던져 **`degraded / DOWNSTREAM_ERROR`** 로 그렸다(자기 보고한 DOWN 이 «닿지 않음» 으로 보임). 새 구현은 계약을 따른다: 503 본문이 health 문서(`status` 가 `UP|DOWN|OUT_OF_SERVICE|UNKNOWN` **문자열**)면 `ok`, 그 밖의 503(게이트웨이 오류 봉투 — `status` 가 숫자)은 `degraded`. 시험 셀 있음. 계약 § 2.4.9.0 에 기록.

**바뀐 시험**: `operator-overview-proxy-header.test.ts` · `domain-health-proxy.test.ts` 삭제(단언할 BFF 홉이 없다) → `dashboard-composition-routes.test.ts` 로 대체. `sample-mode-proxies.test.ts` ② 는 «console-bff 에 닿는다» 대신 «도메인 레그에 닿는다». `sample-fetch-allowlist.test.ts` 는 설명 문구만.

**로컬 게이트**: `pnpm lint` rc=0 · `tsc --noEmit` rc=0 · 전체 vitest **325 파일 / 3,648 시험** 통과(해석 위치를 옮기기 전 판) · 옮긴 뒤 영향 시험 4 파일 49 통과 · `check-fetch-resolution.mjs` rc=0(36 사이트 전부 분류).

**미측정**: 실제 Vercel·데모에서의 동작(→ `TASK-MONO-758`), 게이트웨이 경유 scm 본문이 픽스처와 같은 모양인지의 **실물** 확인(콘솔 scm 화면 스키마가 같은 `{data, meta}` 를 읽는 것까지만 확인 — 실물은 758).

## 결과 보강 — 🔴 단계 순서가 틀렸다, `TASK-MONO-756` 을 이 PR 로 흡수 (2026-10-02 UTC)

PR CI 66/0 초록 뒤 머지 전 e2e 점검(`CLAUDE.md` «nightly 전용 스위트 grep»)에서, **합성을 옮기는 순간** nightly 전용 두 스택이 깨진다는 것이 보였다. `ADR-MONO-081` D4 는 «console-bff 를 **지우기** 전에 e2e 를 고친다» 로 적었지만 실제 경계는 **옮기기**였다.

| 스택 | 깨지는 이유 | 고친 것 |
|---|---|---|
| platform-console e2e(`nightly-e2e.yml`) | console-web 에 `FINANCE_BASE_URL` 없음 → `operators-profile` 의 «finance 카드 ok» 실패 | `docker-compose.e2e.yml` console-web 에 console-bff 가 쓰던 값 이관(wms/scm/erp 는 즉시 거절 주소) |
| federation e2e | console-web 에 도메인 주소 없음 · scm 은 게이트웨이 경로인데 하네스에 scm 게이트웨이 없음 → 엔타이틀먼트 스펙의 scm/erp `forbidden` 이 `degraded` | `scm-gateway-service` 추가(빌드·업로드·복원·기동·health 대기) + console-web 주소 |
| federation 트레이스 스펙 | console-bff span 이 없어진다 | 같은 불변식을 console-web → 생산자로 재진술. 합성 레그를 OTel span(`console.composition.leg`, `composition.domain`/`route`)으로 감쌈 + 단위 시험 |

**머지 전 실측(이 브랜치로 dispatch)**:
- `nightly-e2e.yml` [37007698800](https://github.com/kanggle/monorepo-lab/actions/runs/37007698800) — **success**(`Platform Console E2E full-stack` 포함 전 잡).
- `federation-hardening-e2e.yml` [37007694908](https://github.com/kanggle/monorepo-lab/actions/runs/37007694908) — **failure** at «Wait for scm-gateway-service health»: 게이트웨이의 기동 JWKS 확인(30 s)이 auth-service 연결 거부로 끝나 스스로 종료. 이 스택에서 기동 확인을 하는 서비스는 게이트웨이뿐. → `service_healthy` 의존 · 확인 120 s · `on-failure:3`.
- 재실행 [37009462411](https://github.com/kanggle/monorepo-lab/actions/runs/37009462411) — **success, Playwright 20 passed**. 트레이스 보고: trace `40457fb4…` 하나에 레그 span 6(iam·wms·scm·finance·erp·ecommerce) + 생산자 4(admin-service · finance · scm-gateway · erp-masterdata). 🔵 admin-service 는 console-bff 시절 트리에 합류하지 않던 생산자다.

🔵 AC-9(머지 뒤 첫 nightly)는 그대로 남긴다 — dispatch 는 같은 커밋의 같은 워크플로지만 «main 위에서» 는 아니다.
