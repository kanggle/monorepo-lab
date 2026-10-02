# Task ID

TASK-PC-FE-302

# Status

ready

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

- [ ] **AC-1** — 두 라우트가 `CONSOLE_BFF_URL` 을 읽지 않는다. 응답 JSON 이 `specs/contracts/fixtures/operator-overview-leg-bodies.json` 기반 픽스처로 console-bff 응답과 **같은 모양**이다(계약 고정 시험).
- [ ] **AC-2** — 🔴 **대조군 1**: 레그 하나(예: scm)가 타임아웃/5xx 면 응답 200, 그 카드만 열화, 나머지 카드는 실제 값.
- [ ] **AC-3** — 🔴 **대조군 2**: 레그 하나가 401 이면 응답 401(`TOKEN_INVALID`) — 열화 카드 200 이 **아니다**.
- [ ] **AC-4** — 활성 테넌트 없음 → 400 `NO_ACTIVE_TENANT`, **레그 호출 수 0**(목 호출 횟수 단언).
- [ ] **AC-5** — 레그별 헤더 단언: IAM 레그는 운영자 토큰, 나머지는 도메인용 토큰. `X-Tenant-Id` 는 활성 테넌트.
- [ ] **AC-6** — 라이더 R2: 회로 차단기 없음, 레그마다 타임아웃. 🔴 타임아웃 값은 **Vercel 함수 실행 한도를 재고 나서** 정하고, 잰 값과 출처를 이 파일 § 결과에 적는다(값을 지어내지 않는다 — 지금 `maxDuration` 설정 0건).
- [ ] **AC-7** — 라이더 R1: 레그마다 구조화 로그 한 줄(`domain` · `status` · `latencyMs`), 토큰·PII 없음.
- [ ] **AC-8** — 샘플 방문자(`sampleGate`)는 여전히 레그를 부르지 않는다(기존 시험 유지).
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
