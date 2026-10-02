# Task ID

TASK-PC-FE-303

# Status

ready

# Title

`ADR-MONO-081` 단계 3 — **알림 인박스 + 읽음 처리**를 console-web 서버로

# Owner

platform-console

# Task Tags

- console-web
- notification
- integration

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 집계 도메인이 `["erp"]` 하나라 합성은 얇다. 다만 읽음 처리는 **쓰기**다.

---

# Dependency Markers

- **선행**: `TASK-MONO-755`(계약) done.
- **후속**: `TASK-MONO-756` · `TASK-MONO-757`.
- `TASK-PC-FE-302` 와 코드 순서 의존 없음 — 같은 레그 실패 규칙을 쓰므로 302 가 먼저 머지되면 그 헬퍼를 재사용한다.

# Goal

`app/api/console/notifications/inbox/route.ts` · `notifications/[sourceDomain]/[id]/read/route.ts` 가 console-bff 대신 console-web 서버에서 도메인 알림 서비스를 직접 부른다. console-bff `NotificationAggregationUseCase` 의 동작(설정된 도메인 목록 · 도메인별 자격 · 실패 격리 · 알 수 없는 도메인 404)을 옮긴다.

# Scope

## In Scope

- 두 라우트 + 집계 모듈 · 도메인 목록 설정(기본 `["erp"]` — console-bff `consolebff.notifications.domains` 의 대응)
- 단위 시험

## Out of Scope

- 벨 UI(선 모양이 같으므로 변경 0) · 알림 도메인 추가 · console-bff 삭제

# Acceptance Criteria

- [ ] **AC-1** — 두 라우트가 `CONSOLE_BFF_URL` 을 읽지 않는다. 인박스 응답이 `notification-inbox-contract.md` 모양 그대로다.
- [ ] **AC-2** — 🔴 대조군: 집계 도메인 하나가 실패해도 인박스는 200 이고 나머지 도메인 항목이 보인다(ADR-043 D5). 도메인이 `erp` 하나뿐이면 **시험에서 도메인 둘을 설정해** 이 성질을 잰다.
- [ ] **AC-3** — 레그 401 → 응답 401(재로그인). 열화 200 이 아니다.
- [ ] **AC-4** — 읽음 처리: 설정에 없는 `sourceDomain` → 404 이고 **하위 호출 0**. 있는 도메인은 그 도메인 알림 서비스로 한 번만 간다(재시도로 두 번 보내지 않는다 — 쓰기).
- [ ] **AC-5** — 도메인별 자격이 console-bff `CredentialSelectionAdapter` 와 같다(헤더 단언).
- [ ] **AC-6** — 라이더 R1: 레그 구조화 로그 한 줄. 라이더 R2: 레그 타임아웃(값은 `TASK-PC-FE-302` 가 잰 것을 쓴다).
- [ ] **AC-7** — 머지 뒤 다음 nightly 콘솔 e2e 결과 확인.

# Related Specs

- `docs/adr/ADR-MONO-081-console-composition-in-the-console-server.md`
- `docs/adr/ADR-MONO-043-notification-architecture-unification.md` D2·D5

# Related Contracts

- `platform/contracts/notification-inbox-contract.md`
- `projects/erp-platform/specs/contracts/http/notification-api.md`

# Edge Cases

- 활성 테넌트 없음 → 400, 하위 호출 0.
- 읽음 처리 대상 알림이 이미 읽힘 → 하위 서비스 응답을 그대로(멱등 여부는 하위 계약이 정한다).

# Failure Scenarios

1. 읽음 처리를 GET 레그와 같은 재시도 래퍼로 감싸 쓰기가 두 번 나간다.
2. 도메인 목록을 하드코딩해 «알 수 없는 도메인 404» 가 사라진다.
