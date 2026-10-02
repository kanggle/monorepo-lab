# Task ID

TASK-MONO-755

# Title

`ADR-MONO-081` 단계 1 — 콘솔 합성의 **생산자를 console-web 으로**: 계약·스펙 먼저

# Status

done (2026-10-02 UTC — 4차원 검증) ‖ 직전: review

# Owner

monorepo

# Task Tags

- contracts
- docs
- platform-console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (문서 — 선 모양은 바꾸지 않고 «누가 만드나» 만 옮긴다)

---

# Dependency Markers

- **선행**: `ADR-MONO-081` ACCEPTED — A (2026-10-02).
- **후속**: `TASK-PC-FE-302` · `TASK-PC-FE-303` 이 이 티켓의 계약 위에서 구현한다.
- **관련**: `TASK-MONO-751` — 그 티켓의 Related Contracts 줄을 이 티켓이 고친다(AC-5).

# Goal

콘솔의 교차 도메인 합성 세 가지(운영 개요 · 도메인 상태 · 알림 인박스)의 **생산자**를 계약·스펙에서 console-bff 에서 console-web 서버로 옮긴다. 응답의 선(wire) 모양은 한 글자도 바꾸지 않는다(`ADR-MONO-081` D1).

# Scope

## In Scope

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9(및 하위 절) — 생산자·호출 경로
- `platform/contracts/notification-inbox-contract.md` — 집계기의 자리
- `projects/platform-console/specs/services/console-web/architecture.md` — «도메인 안 = console-web, 도메인 사이 = console-bff» 두 규칙을 하나로(D3)
- `projects/platform-console/PROJECT.md` — Service Map 의 console-bff 행에 «은퇴 예정(ADR-081)» · `service_types` 는 **삭제 단계(757)에서** 바꾼다(지금 바꾸면 아직 있는 서비스와 분류가 어긋난다)
- `tasks/ready/TASK-MONO-751-*.md` Related Contracts 한 줄

## Out of Scope

- 코드 · `console-bff/architecture.md` 삭제(757) · `jwt-standard-claims.md` 의 console-bff 대상 언급(757)

# Acceptance Criteria

- [x] **AC-1** — § 2.4.9 의 응답 봉투·오류 코드(`NO_ACTIVE_TENANT` · `TOKEN_INVALID` · `BAD_GATEWAY`)·카드별 `status` 정의가 **바이트 그대로**다(diff 에 그 줄이 없다). 바뀐 것은 생산자 서술뿐이다.
- [x] **AC-2** — 계약이 `ADR-MONO-081` D2 의 불변식을 생산자에게 지운다: IAM 레그 = 운영자 토큰 · 나머지 = 도메인용 토큰 · 레그 하나 실패 = 그 카드만 열화 · **레그 401 = 응답 401** · 활성 테넌트 없음 = 호출 전 400.
- [x] **AC-3** — `notification-inbox-contract.md` 의 집계기가 console-web 서버이고, 읽음 처리(`POST …/{sourceDomain}/{id}/read`)의 알 수 없는 도메인 = 404 규칙이 남아 있다.
- [x] **AC-4** — `console-web/architecture.md` 에 «합성은 console-web 서버» 규칙 **하나**만 있다(§303–309 의 «bff leg 신설은 무이득» 문단이 일반 규칙으로 올라간다).
- [x] **AC-5** — `TASK-MONO-751` 의 «console-bff ↔ fan gateway» 가 «console-web(같은 출처 라우트) ↔ fan gateway» 로 바뀌어 있다. 🔴 그 티켓이 그 사이 in-progress 면 고치지 말고 그 티켓 소유 세션에 남길 문장을 이 티켓 § 결과에 적는다.
- [x] **AC-6** — 라이더 R1(지표 → 구조화 로그)이 계약의 관측 절에 반영돼 있다: 레그마다 `domain` · `status` · `latencyMs` 한 줄.

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`
- `docs/adr/ADR-MONO-017-platform-console-bff-architecture.md` D4·D5·D6
- `docs/adr/ADR-MONO-043-notification-architecture-unification.md` D2·D5

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9
- `platform/contracts/notification-inbox-contract.md`

# Edge Cases

- finance 기본 계정 id 가 없는 운영자 — finance 카드만 `MISSING_PREREQUISITE`(지금 규칙 유지).
- 샘플 방문자(`ADR-MONO-074`) — 레그를 부르지 않는다는 규칙이 계약에 그대로 있다.

# Failure Scenarios

1. 생산자를 옮기며 봉투 필드 이름을 «정리» 해서 화면과 샘플 데이터가 함께 깨진다.
2. 401 을 «레그 실패» 로 적어, 구현이 만료 세션을 열화 카드로 숨긴다.

---

# 결과 (2026-10-02 UTC)

방식: § 2.4.9 는 약 700줄이라 전부 다시 쓰면 선 모양을 건드릴 위험이 크다. 그래서 **목표 규칙을 한 절(§ 2.4.9.0)에 모으고**, console-bff 를 «일이 일어나는 자리» 로 적은 절마다 **⏳ console-bff-era** 표시를 달았다(17곳). 두 서술이 다르면 § 2.4.9.0 이 이긴다고 그 절 머리에 적었다. 표시된 절은 console-bff 가 실제로 서비스하는 동안(302·303 머지 전)은 **참**이므로 지금 지우면 현재 동작과 문서가 어긋난다 — 정리는 `TASK-MONO-757` Scope 에 넣었다.

- **AC-1** — `console-integration-contract.md` diff = **추가 116줄 · 삭제 0줄**(`git diff -U0 | grep '^-'` 0건). 응답 봉투·오류 코드·카드 `status` 정의 줄은 하나도 바뀌지 않았다.
- **AC-2** — § 2.4.9.0 «Invariants carried across the move» 표: IAM=운영자 토큰 · 나머지=도메인용 토큰 · 레그 하나 실패=그 카드만 · **데이터 레그 401=전체 401**(degraded 로 삼키지 않는다) · 403=forbidden · 활성 테넌트 없음=레그 호출 0 으로 400 · health 레그는 무자격·비성공=degraded.
- **AC-3** — `notification-inbox-contract.md` § 4: 집계기 자리=console-web 서버(머리 인용) · 항목 4 에서 회로 차단기 조항을 빼고 «401 = 열화가 아니라 401» 추가 · **항목 6 신설**: 알 수 없는 `sourceDomain` = 하위 호출 0 으로 404, 아는 도메인은 쓰기를 **정확히 한 번**(재시도 금지).
- **AC-4** — `console-web/architecture.md` «도메인 랜딩 운영 개요 스냅샷» 절: «BFF 는 cross-domain 합성용» 예외를 없애고 «모든 합성 = console-web 서버» 한 규칙으로 올렸다. 다른 자리의 대비 서술은 757.
- **AC-5** — `TASK-MONO-751` Related Contracts 줄 정정(그 티켓은 `ready/` — in-progress 아님, 원격 브랜치에도 751 작업 없음을 확인).
- **AC-6** — § 2.4.9.0 «Observability»: `bff_*` 지표 대신 레그마다 `console_composition_leg` 로그 한 줄(`route` · `domain` · `status` · `reason` · `latencyMs` · `requestId`, 토큰·PII 없음).

🔴 **조사 중 발견 — 302 의 범위를 바꾼다.** console-bff 의 scm 레그는 게이트웨이를 거치지 않고 `inventory-visibility-service` 를 **직접** 부른다(§ 2.4.9.1 scm-leg topology 노트 — scm 게이트웨이가 당시 엔타이틀먼트 이중 수용을 못 했다). 그 주소는 docker 네트워크에만 있어 **Vercel 에서 닿지 않는다.** 그래서 § 2.4.9.0 «Which address each leg uses» 는 레그가 **그 도메인 콘솔 화면이 이미 쓰는 console-web 서버 클라이언트**(scm = `SCM_GATEWAY_BASE_URL` + `/api/v1/inventory-visibility/snapshot`)를 쓰라고 정한다 — Vercel 에서 이미 동작하는 경로다. 게이트웨이 경로의 응답 본문이 서비스 경로와 같다는 보장은 없으므로 302 가 레그마다 픽스처 모양을 단언한다(302 AC-1 이 이미 그것을 요구한다).

🔵 `PROJECT.md` 의 `service_types: [frontend-app, rest-api]` 는 **그대로** 두었다 — console-bff 가 아직 떠 있는 동안 `rest-api` 를 빼면 분류가 실제와 어긋난다. Service Map 행에 «은퇴 예정» 과 그때 빠질 것을 적었다(757).

# 종결 (2026-10-02 UTC) — 4차원 검증

- (a) PR [#4116](https://github.com/kanggle/monorepo-lab/pull/4116) `state=MERGED` 2026-10-02T11:49:55Z (첫 PR #4113 은 #4110 과 `tasks/INDEX.md` 충돌로 CI 0건 → main 위로 다시 올린 새 브랜치로 대체, force-push 없음)
- (b) squash `69cdcb58c` = 머지 직후 `origin/main` tip
- (c) 머지 전 체크 66 · 실패 0
- (d) § Acceptance Criteria 를 열어 읽음 — AC-1~6 전부 `[x]`, § 결과에 각 AC 의 근거(AC-1 은 삭제 0줄 측정)
