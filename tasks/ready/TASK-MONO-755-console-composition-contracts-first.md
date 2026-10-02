# Task ID

TASK-MONO-755

# Title

`ADR-MONO-081` 단계 1 — 콘솔 합성의 **생산자를 console-web 으로**: 계약·스펙 먼저

# Status

ready

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

- [ ] **AC-1** — § 2.4.9 의 응답 봉투·오류 코드(`NO_ACTIVE_TENANT` · `TOKEN_INVALID` · `BAD_GATEWAY`)·카드별 `status` 정의가 **바이트 그대로**다(diff 에 그 줄이 없다). 바뀐 것은 생산자 서술뿐이다.
- [ ] **AC-2** — 계약이 `ADR-MONO-081` D2 의 불변식을 생산자에게 지운다: IAM 레그 = 운영자 토큰 · 나머지 = 도메인용 토큰 · 레그 하나 실패 = 그 카드만 열화 · **레그 401 = 응답 401** · 활성 테넌트 없음 = 호출 전 400.
- [ ] **AC-3** — `notification-inbox-contract.md` 의 집계기가 console-web 서버이고, 읽음 처리(`POST …/{sourceDomain}/{id}/read`)의 알 수 없는 도메인 = 404 규칙이 남아 있다.
- [ ] **AC-4** — `console-web/architecture.md` 에 «합성은 console-web 서버» 규칙 **하나**만 있다(§303–309 의 «bff leg 신설은 무이득» 문단이 일반 규칙으로 올라간다).
- [ ] **AC-5** — `TASK-MONO-751` 의 «console-bff ↔ fan gateway» 가 «console-web(같은 출처 라우트) ↔ fan gateway» 로 바뀌어 있다. 🔴 그 티켓이 그 사이 in-progress 면 고치지 말고 그 티켓 소유 세션에 남길 문장을 이 티켓 § 결과에 적는다.
- [ ] **AC-6** — 라이더 R1(지표 → 구조화 로그)이 계약의 관측 절에 반영돼 있다: 레그마다 `domain` · `status` · `latencyMs` 한 줄.

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
