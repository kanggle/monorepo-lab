# Task ID

TASK-PC-FE-052

# Title

**콘솔 notification-bell slice — notification-service in-app inbox 소비 (ADR-016 §D3 notification 첫 증분의 콘솔 소비자).** TASK-ERP-BE-011 이 라이브화한 notification-service(approval 이벤트 fan-out → recipient-scoped in-app inbox)를 콘솔 shell 이 소비: 헤더에 알림 벨(unread badge) + 드롭다운(최근 알림 list, recipient-scoped) + 클릭 시 deep-link(approval 요청) + 멱등 mark-read. `approval(이벤트) → notification(영속) → 콘솔 벨(가시)` 루프를 사용자-가시 종단까지 완결. PC-FE-051 결재함의 알림 짝 — backend fan-out 을 운영 화면에서 demonstrable.

# Status

done

# Owner

frontend-engineer (platform-console console-web; TASK-ERP-BE-011 notification-api 라이브 — 이 task 는 콘솔 shell 벨 + proxy)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **선행 (cross-project, erp)**: TASK-ERP-BE-011 (notification-service first increment — `/api/erp/notifications` 라이브, main 머지됨 `7fd1b0eb`). 콘솔이 런타임 호출.
- **builds on**: TASK-PC-FE-051 (approval 결재함 — erp 도메인-facing 토큰 + same-origin proxy + 에러 graceful 선례) + TASK-PC-FE-041 (AccountMenu — shell 헤더 client dropdown 컨벤션 + outside-click/Escape) + TASK-PC-FE-049 (read-model degrade 패턴).
- **realises**: ADR-MONO-016 §D3 — notification-service 첫 증분(ERP-BE-011)의 콘솔 소비자. notification-api.md(3 endpoint: GET inbox / GET {id} / POST {id}/read) 를 콘솔 same-origin proxy 로 소비.
- **decision (user, 2026-06-05)**: 다음 작업 = 콘솔 notification-bell slice.

# Goal

운영자-admin 이 콘솔 어느 화면에서든 헤더 알림 벨로 자신에게 fan-out 된 결재 알림을 인지(unread 카운트)하고, 드롭다운에서 최근 알림을 열람·읽음 처리하며, 알림을 클릭해 원본 결재 요청으로 이동할 수 있다. notification-service 의 in-app inbox 가 콘솔 shell 에서 end-to-end demonstrable — `approval → notification → 콘솔 벨` 루프 가시화.

# Scope

## In Scope

- **proxy routes**(server-only, erp 도메인-facing 토큰; 기존 `app/api/erp/_proxy.ts` 에러매핑 재사용; approval inbox[PC-FE-051] read proxy 패턴):
  - `app/api/erp/notifications/route.ts` — **GET**(inbox; query `unread`/`page`/`size` 전달).
  - `app/api/erp/notifications/[id]/route.ts` — **GET**(단건; 타인 소유 → 404 `NOTIFICATION_NOT_FOUND` passthrough).
  - `app/api/erp/notifications/[id]/read/route.ts` — **POST**(멱등 mark-read; body 없음, Idempotency-Key 없음 — 자연 멱등).
- **신규 `features/notifications/` feature**(shell-level — v1 source 는 erp 이나 벨은 전역 헤더 affordance, v2 sourceType[MASTERDATA/PERMISSION] 가산 대비):
  - types(zod): `Notification`(NON_NULL **absent `readAt`** 파싱→optional, `read===false` 시 미설정) / list response(+PageMeta) / `sourceType`·`type` 자유문자 tolerant 파싱(미래 enum 비throw).
  - api client(server-only): `listNotifications`(unread 필터) / `getNotification` / `markNotificationRead`. erp 도메인-facing 토큰(`getDomainFacingToken`), `X-Tenant-Id` 미전송. 에러 → `ApiError`/`ErpUnavailableError`(§2.5 taxonomy).
  - keys + hooks(client): `useNotificationInbox`(unread-count 용 + 드롭다운 list) + `useMarkNotificationRead`(성공 시 inbox invalidate). **NO `refetchInterval`**(폴링 금지 — 기존 erp-ops 규율); 드롭다운 open 시 refetch.
  - components: `NotificationBell`(client) — 벨 아이콘 + unread badge(0 시 숨김) + 드롭다운(최근 N 알림, type/title/createdAt + unread dot; 행 클릭 → mark-read + `/erp?approval=<sourceId>` deep-link; 빈/에러/degrade graceful). AccountMenu 패턴(outside-click/Escape/`data-testid`).
  - `index.ts` export.
- **shell wiring**: `app/(console)/layout.tsx` 헤더 `gap-3` 그룹에 `<NotificationBell />` 추가(TenantSwitcher ↔ ThemeToggle 사이). 벨은 erp inbox 호출이 403/503/네트워크 실패 시 **shell 을 깨지 않고 조용히 비활성/빈 상태**로 degrade(integration-heavy resilience — 운영자가 erp 미권한일 수 있음).
- **tests**: `NotificationBell`(unread badge·드롭다운·mark-read·deep-link·빈/403/degrade graceful) + api client 단위(absent `readAt` 파싱·unread 쿼리·mark-read payload) + proxy route 단위(GET/POST 메서드 노출·에러매핑·server-only token·{id} 404 passthrough). console-web **vitest + tsc --noEmit + lint + build** GREEN(MONO-166 gate).

## Out of Scope

- notification-service backend — TASK-ERP-BE-011(소비만).
- notification v2(외부 채널 Slack/SMTP / masterdata·permission·delegation 알림 / recipient preferences / digest / cross-recipient operator view / bulk mark-all-read) — backend v2 미구현이라 콘솔도 범위 밖.
- 결재 화면 자체(create/transition) — TASK-PC-FE-051(이 task 는 벨에서 deep-link 만; `/erp?approval=<id>` 수신은 erp 페이지가 기존 detail drawer 로 처리, 신규 라우팅 최소).

# Acceptance Criteria

- [ ] **AC-1** 콘솔 헤더에 알림 벨; unread > 0 시 badge 표시(=0 시 badge 숨김). 클릭 → 드롭다운에 recipient-scoped 최근 알림 list(type/title/시각 + unread dot).
- [ ] **AC-2** 알림 행 클릭 → mark-read(멱등) 호출 후 원본 결재로 deep-link(`sourceId` → `/erp?approval=<sourceId>`); inbox invalidate 로 badge/list 갱신.
- [ ] **AC-3** absent `readAt`(read===false) 정상 파싱·렌더(crash 0); unread 필터 쿼리 전달. mark-read 재호출 멱등(같은 readAt, 에러 0).
- [ ] **AC-4** degrade graceful: erp inbox 403(미권한)/503/timeout/network → 벨이 shell 을 깨지 않고 조용히 빈/비활성(에러 boundary 0). 비-erp 운영자도 콘솔 정상 사용.
- [ ] **AC-5** proxy: notifications=GET, {id}=GET, {id}/read=POST(다른 메서드 미노출); 토큰 server-only(도메인-facing erp 토큰)·`X-Tenant-Id` 미전송; 에러매핑 기존 erp proxy 일치; {id} 404 `NOTIFICATION_NOT_FOUND` passthrough.
- [ ] **AC-6** console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). 기존 erp-ops/operators/shell 테스트 회귀 0.

# Related Specs

- consume: `projects/erp-platform/specs/contracts/http/notification-api.md`(ERP-BE-011). ADR-MONO-016 §D3 notification 첫 증분. ADR-MONO-013/015/017(console model). console-integration-contract.md §2.4.8(erp binding — notification read surface blockquote 추가 + read-model/approval/notification 라이브 정합).

# Related Contracts

- consume: notification-api.md(3 endpoint). console-web 자체 proxy(same-origin; notifications GET / {id} GET / {id}/read POST).

# Edge Cases

- 비-erp 운영자(erp tenant/entitlement 없음): inbox 403 `TENANT_FORBIDDEN`/`PERMISSION_DENIED` → 벨 빈/비활성(에러 아님). 콘솔 정상.
- unread=0: badge 숨김; 드롭다운 "새 알림 없음".
- absent `readAt`: zod optional, unread dot 으로만 구분(readAt null-value 가정 금지).
- mark-read 더블클릭: 자연 멱등(같은 readAt, 누적 부작용 0 — Idempotency-Key 불필요).
- 타인 소유 알림 단건 조회: 404 `NOTIFICATION_NOT_FOUND`(존재 누설 회피) → 드롭다운은 자기 inbox 만 노출하므로 정상 경로엔 미발생.
- 미래 sourceType/type(MASTERDATA/PERMISSION): tolerant 파싱, 제네릭 라벨 — deep-link 는 APPROVAL 만(다른 source 는 라벨만).

# Failure Scenarios

- notification-service 불가(503/timeout): 벨 degrade(빈/비활성) — shell·타 섹션 무영향.
- mark-read 실패(404/네트워크): 드롭다운 inline 무시(다음 새로고침에 정합) — deep-link 는 진행(읽음 실패가 이동을 막지 않음).
- write proxy 토큰 클라 노출: server-only(`getServerEnv`/도메인-facing 토큰) 가드.
- 잘못된 메서드 proxy 호출: 405/미노출.

# Test Requirements

- 컴포넌트: `NotificationBell`(badge·드롭다운·mark-read+deep-link·빈·403/degrade graceful·outside-click/Escape), api client 단위(absent `readAt`/unread 쿼리/mark-read payload·server-only), proxy route 단위(메서드 노출·에러매핑·{id} 404 passthrough).
- console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). erp-ops/operators/shell 회귀 0.
- Local(선택): erp 전체 재배포(notification-service 포함) 후 라이브 — approval 제출→벨 알림 등장→클릭 read+deep-link 루프 확인.

# Definition of Done

- [ ] proxy(notifications GET / {id} GET / {id}/read POST) + `features/notifications`(types/api/keys/hooks/NotificationBell) + shell 헤더 wiring + degrade graceful.
- [ ] console-web vitest + tsc + lint + build GREEN(MONO-166); 회귀 0.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet 4.6 (UI slice — 기존 approval read proxy + AccountMenu dropdown 선례 재사용, 신규 상태기계/mutation 복잡도 없음; mark-read 자연 멱등, degrade graceful 이 핵심 정직성 요건). 사용자 "콘솔 notification-bell slice" 선택. 메타: approval→notification 백엔드 루프(ERP-BE-009/011)의 사용자-가시 종단 — backend fan-out 을 운영 화면 벨로 가시화. 선행 ERP-BE-011 머지됨(`7fd1b0eb`). 벨은 전역 shell affordance 이나 v1 source=erp inbox 라 비-erp 운영자에게 graceful degrade 필수(integration-heavy). [[project_platform_console_adr_013]]
