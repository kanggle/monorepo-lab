# Task ID

TASK-PC-FE-303

# Status

review (2026-10-02 UTC — AC-7 은 머지 뒤 첫 nightly)

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

- [x] **AC-1** — 두 라우트가 `CONSOLE_BFF_URL` 을 읽지 않는다. 인박스 응답이 `notification-inbox-contract.md` 모양 그대로다.
- [x] **AC-2** — 🔴 대조군: 집계 도메인 하나가 실패해도 인박스는 200 이고 나머지 도메인 항목이 보인다(ADR-043 D5). 도메인이 `erp` 하나뿐이면 **시험에서 도메인 둘을 설정해** 이 성질을 잰다.
- [x] **AC-3** — 레그 401 → 응답 401(재로그인). 열화 200 이 아니다.
- [x] **AC-4** — 읽음 처리: 설정에 없는 `sourceDomain` → 404 이고 **하위 호출 0**. 있는 도메인은 그 도메인 알림 서비스로 한 번만 간다(재시도로 두 번 보내지 않는다 — 쓰기).
- [x] **AC-5** — 도메인별 자격이 console-bff `CredentialSelectionAdapter` 와 같다(헤더 단언).
- [x] **AC-6** — 라이더 R1: 레그 구조화 로그 한 줄. 라이더 R2: 레그 타임아웃(값은 `TASK-PC-FE-302` 가 잰 것을 쓴다).
- [ ] **AC-7** — 머지 뒤 다음 nightly 콘솔 e2e 결과 확인.
- [x] **AC-8** — 🔴 **머지 전에** 이 브랜치로 `nightly-e2e.yml` 과 `federation-hardening-e2e.yml` 을 `workflow_dispatch` 로 돌려 둘 다 초록임을 적는다. `TASK-PC-FE-302` 의 교훈: 합성을 옮기는 순간 두 e2e 하네스의 console-web 배선(도메인·알림 서비스 주소)이 모자라 nightly 가 깨졌다 — PR CI 66/0 은 그것을 보지 못한다(두 스위트는 PR 에서 돌지 않는다). 알림 인박스는 두 하네스 모두 console-bff 로 가고 있으니 console-web 에 erp 알림 주소가 필요하다.

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

# 결과 (2026-10-02 UTC)

## 무엇을 바꿨나

- `src/shared/composition/notification-inbox.ts` (신규) — console-bff `NotificationAggregationUseCase` 의 대응. 설정된 도메인(`CONSOLE_NOTIFICATION_DOMAINS`, 기본 `erp`)을 병렬로 읽고, 항목을 합쳐 `createdAt` 내림차순(없으면 뒤)으로 정렬, `sourceDomain` 이 없거나 빈 문자열일 때만 주입, `totalElements` 합산. 네트워크 원시 호출은 없다 — 라우트가 `sampleGate(` 뒤에서 자기 `fetch` 를 넘기고 그 자리에서 `resolveBackendUrl` 한다(302 와 같은 구조).
- 두 라우트 — `CONSOLE_BFF_URL` 을 더 읽지 않는다. 표본(sample) 코어 이름 `console-bff` 는 302 와 같은 이유로 유지.
- `env.ts` — `CONSOLE_NOTIFICATION_DOMAINS` (console-bff `consolebff.notifications.domains` 대응). 알려진 인박스가 없는 이름은 경고 후 건너뛴다.
- erp 레그 주소 = `${ERP_BASE_URL}/api/erp/notifications` — erp 콘솔 화면들이 쓰는 같은 베이스(계약 § 2.4.8: erp 게이트웨이가 `/api/erp/notifications/**` 를 notification-service 로 보낸다).

## AC 판정

| AC | 판정 | 근거 |
|---|---|---|
| AC-1 | ✅ | `tests/unit/notification-routes.test.ts` — `CONSOLE_BFF_URL` 을 심어 두고 호출 URL 이 erp 직행 하나뿐임을 단언. 응답을 `NotificationInboxResponseSchema` 로 파싱(벨이 쓰는 그 스키마) |
| AC-2 | ✅ | `tests/unit/shared/notification-inbox.test.ts` — **도메인 둘**(alpha·beta)로 beta 의 503·네트워크·읽을 수 없는 본문·403·타임아웃 각각에서 200 + alpha 항목 + `degradedDomains=[beta]` |
| AC-3 | ✅ | 모듈: beta 401 → `unauthorized`(alpha 정상이어도). 라우트: erp 401 → 401 `TOKEN_INVALID` |
| AC-4 | ✅ | 미설정 `sourceDomain`(`wms`) → 404 `NOTIFICATION_NOT_FOUND`, fetch 0. erp → POST 정확히 1회 — 200·503·네트워크·404·401·타임아웃 모두 1회 |
| AC-5 | ✅ | erp 레그 헤더 = `Authorization: Bearer <domain-facing token>`, `X-Tenant-Id`·`X-Operator-Token` 없음(활성 테넌트가 있어도). console-bff `CredentialSelectionAdapter` 의 ERP → `IamOidcAccessToken` 과 `ErpNotificationsReadAdapter` 의 «X-Tenant-Id 없음» 과 같다 |
| AC-6 | ✅ | R1: `console_composition_leg` 한 줄(route `notifications-inbox`/`notifications-read` · domain · status · reason · latencyMs · requestId, 토큰 없음 단언). R2: `LEG_TIMEOUT_MS`(4000, 302 실측값) 재사용 · 서킷브레이커 없음 · 읽음 처리 재시도 없음 |
| AC-7 | ⏳ | 머지 뒤 첫 nightly |
| AC-8 | ✅ | 아래 «AC-8 dispatch 실측» |

## bite (대조군)

주입 → 두 시험 파일 실행 → 백업 사본으로 복원(바이트 일치 확인). 기준선 36/36 통과.

| 주입 | 결과 |
|---|---|
| B1 도메인 401 을 열화로 삼킴(console-bff 의 옛 동작) | 2 실패 |
| B2 읽음 처리를 한 번 더 보냄 | 8 실패 |
| B3 한 도메인 실패가 전체를 실패시킴 | 7 실패 |

## 로컬 게이트

lint rc=0 · tsc rc=0 · vitest **328 파일 / 3,690 시험** rc=0 · `check-fetch-resolution.mjs` rc=0(미분류 0).

## 티켓과 다르게 한 것 — 1건

- 🔴 **Edge Case «활성 테넌트 없음 → 400, 하위 호출 0» 은 넣지 않았다.** 그 규칙은 대시보드 합성(§ 2.4.9)의 것이다. 알림 인박스 계약(`notification-inbox-contract.md` § 4)과 지금 생산자(console-bff — 옛 라우트 주석 «the BFF does NOT require it for the notification aggregator; absent is fine»)는 테넌트를 요구하지 않고, erp 레그는 `X-Tenant-Id` 를 보내지 않으므로 테넌트가 입력조차 아니다. 벨은 콘솔 셸 전체에 있으므로 400 을 넣으면 **선 모양이 바뀐다**(ADR-081 D1 위반). 스펙 > 티켓 규칙에 따라 계약을 따랐고, 시험 «no active tenant is NOT a 400» 이 이 판단을 고정한다.

## 하네스 배선 (AC-8 의 전제)

두 하네스 모두 console-web 에 `ERP_BASE_URL` 이 이미 있고 그 값이 console-bff 의 erp 주소와 **같다** — nightly `http://127.0.0.1:9`(둘 다), federation `http://erp-masterdata-service:8080`(둘 다). 따라서 인박스의 결과는 옮기기 전과 같다(erp 가 `degradedDomains` 에 들어간 200). 302 와 달리 **배선 추가가 필요 없다** — 그것이 맞는지를 dispatch 로 잰다.

## AC-8 dispatch 실측 (2026-10-02 UTC, 브랜치 `pc-fe-303-notifications` 커밋 `340108dea`)

| 워크플로 | 런 | 결과 |
|---|---|---|
| `federation-hardening-e2e.yml` | `37020371201` | ✅ success (boot jars · full-stack 둘 다) |
| `nightly-e2e.yml` | `37020365215` | 1차: **web-store 잡만 failure**, 나머지 14 잡 success(**Platform Console E2E full-stack 포함**) → 실패 잡만 재실행 → ✅ success |

🔴 1차 web-store 실패의 원인: `Build web-store (Next.js prod build)` 단계에서 `next/font` 가 `fonts.gstatic.com` 글꼴 파일을 못 받아 `TypeError: Cannot read properties of null (reading '1')` 로 빌드가 멈췄다(재시도 1/3 뒤). 이 PR 은 web-store 를 건드리지 않고, 같은 기준 커밋 `689594443` 의 main push nightly(`37017628507`)에서 그 잡은 success 였다. «외부 일시 장애» 는 가설이었고 **재실행 success 로 확인**했다 — 코드 변경 없이 같은 커밋으로.

