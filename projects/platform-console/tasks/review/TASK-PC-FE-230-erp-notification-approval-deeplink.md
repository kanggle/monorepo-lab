# TASK-PC-FE-230 — ERP 결재 알림 딥링크 오라우팅 수정 + deepLink 계약 정합

**Status:** review
**Area:** platform-console / console-web (Phase A · 필수) · erp-platform / notification-service (Phase B · 선택, cross-project)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (Phase A 단독 — FE 라우팅 소규모 수정) / Opus 4.8 (Phase B 동반 시 — cross-project 계약+백엔드 정합) (분석=Opus 4.8 / 구현 권장=Sonnet, Phase B 포함 시 Opus)
**Origin:** 2026-07-08 ERP 기능↔콘솔 메뉴 배치 감사 — deepLink "미배선" 진단이 실제로는 **오라우팅 버그**로 확증됨(아래 검증 사실 참조).
**Impl (Phase A):** PR #2327 (`pc-fe-230-erp-deeplink`) — NotificationBell fallback → `/erp/approval?request=<sourceId>` + 결재함 페이지 `request` 프리선택 지원. 검증: tsc 0·lint 0·vitest 대상 37/37(전체 2462 pass + 무관 flake 1). Phase B(erp-platform `deepLinkFor`)는 선택 후속 미착수 — FE가 `deepLink` 우선 소비하도록 이미 배선돼 백엔드 랜딩 시 재작업 불필요.

---

## Goal

공용 셸의 `NotificationBell`이 ERP 결재 알림을 클릭할 때 **잘못된 페이지로 이동한다.** fallback이 `/erp?approval=<sourceId>`로 push하지만 `/erp`(마스터 페이지)는 `searchParams.asOf`만 읽고 `approval` 파라미터를 **무시**하므로, 운영자는 결재함(`/erp/approval`)이 아니라 **마스터 데이터 페이지에 떨어지고 결재건 id는 소실**된다. 이를 실제 결재함으로 정확히 딥링크하도록 수정한다.

부수적으로, ERP `deepLink` 계약 필드가 백엔드에서 하드코딩 `null`이라 셸의 라우팅 지식이 FE에 하드코딩된 fallback으로만 존재한다(계약 예시도 실제 라우트와 불일치). Phase B에서 이 라우팅 지식을 백엔드 계약으로 승격해 계약↔FE↔실제 라우트를 일치시킨다.

## 배경 사실 (검증됨 — 2026-07-08)

- **FE fallback 오라우팅**: `features/notifications/components/NotificationBell.tsx` `handleClick`(L85–97) — `n.deepLink` 있으면 `router.push(n.deepLink)`, 없으면 `isApprovalSource(n) && n.sourceId` 시 **`router.push('/erp?approval=' + encodeURIComponent(n.sourceId))`**. 하지만 `/erp` 마스터 페이지(`app/(console)/erp/page.tsx` L33–44)는 `searchParams?.asOf`만 소비 → `approval` 파라미터 무시, 마스터 슬라이스만 렌더. 실제 결재함은 **`/erp/approval`**(`app/(console)/erp/approval/page.tsx`, 현재 searchParams 미소비).
- **deepLink 필드는 전 계층에 이미 존재하나 항상 null**: 공유 계약(`platform/contracts/notification-inbox-contract.md` §1)·ERP 계약(`projects/erp-platform/specs/contracts/http/notification-api.md` L59, L72–75)·console-bff 집계기(item 필드 verbatim 통과)·`NotificationBell`(L91 `n.deepLink` 우선 소비)까지 배선 완료. 단 ERP 백엔드 `NotificationResponse.deepLinkFor(n)`가 **하드코딩 `return null`**(`projects/erp-platform/apps/notification-service/.../presentation/dto/NotificationResponse.java` L73–75) → NON_NULL 규약상 필드 자체가 생략됨. 슬라이스 테스트 `NotificationInboxControllerSliceTest.java` L87이 `$.data[0].deepLink` `.doesNotExist()`로 이 null을 고정.
- **계약 예시도 실제 라우트와 불일치**: notification-api.md sourceId 설명(L81–83)은 `GET /api/erp/approval/requests/{sourceId}`(백엔드 SoR)를 딥링크로 안내 — 이는 **API 경로**이지 콘솔 라우트가 아님. 셸이 push할 콘솔 라우트는 `/erp/approval`.
- **집계기는 erp-only(Phase-1)**: console-bff `NotificationAggregationUseCase.extractItems`가 도메인 item 맵을 verbatim 통과(필드 화이트리스트 없음) → 백엔드가 `deepLink`를 채우면 **console-bff 무변경**으로 셸에 도달. `NotificationInboxResponse.items`는 `List<Map<String,Object>>`.
- **sourceType 확장**: 알림 소스는 v1 `APPROVAL` 외에 위임(`DELEGATION_GRANTED/REVOKED`, `sourceType=DELEGATION`)도 존재(events `notification-subscriptions.md`). FE fallback은 `isApprovalSource`(APPROVAL 한정)만 라우팅 → **위임 알림은 현재 클릭해도 inert**. (단 notification-api.md L79는 "sourceType always APPROVAL in v1"로 기술 — 계약 본문 드리프트 가능성, Phase B에서 함께 확인.)

## Scope

### Phase A — FE 오라우팅 수정 (필수, platform-console 단독)

1. **`app/(console)/erp/approval/page.tsx`** — `searchParams?: Promise<{ request?: string }>`를 받아(마스터 페이지 `asOf` 패턴 미러) 특정 결재건 프리선택/스크롤/하이라이트를 지원. 결재함 화면 컴포넌트에 선택 결재건 id를 seed로 전달. 잘못된/미존재 id는 **무시하고 목록만 렌더**(크래시 없음).
2. **`features/notifications/components/NotificationBell.tsx`** — fallback을 **`/erp/approval?request=<sourceId>`**로 교체(현재의 dead `/erp?approval=` 제거). `n.deepLink` 우선 소비 로직은 유지(백엔드가 채우면 자동 우선).
3. **테스트**: `NotificationBell` 테스트에 (a) `deepLink` 존재 시 그 값으로 push, (b) 부재+APPROVAL 소스 시 `/erp/approval?request=<sourceId>`로 push, (c) 부재+비-APPROVAL 소스 시 inert 케이스 추가/갱신. 결재함 페이지 테스트에 `request` 파라미터 프리선택/무시(미존재 id) 케이스 추가.
4. **계약 정합(콘솔측)**: `projects/platform-console/specs/services/console-web/architecture.md` erp 라우트 트리에 `/erp/approval`의 `request` 쿼리 파라미터 소비를 명시. (셸 알림 딥링크 대상이 `/erp/approval`임을 기록.)

### Phase B — deepLink 계약 승격 (선택, cross-project: erp-platform + platform-console 원자적 PR)

5. **`NotificationResponse.deepLinkFor(n)`**(erp notification-service) — 하드코딩 `null`을 실제 파생으로 교체: `sourceType==APPROVAL` → `"/erp/approval?request=" + sourceId`, `sourceType==DELEGATION` → `"/erp/delegation"`(또는 위임 상세 라우트 확정 시 그 경로). **콘솔 라우트 문자열의 SoT는 콘솔**이므로, 파생 경로는 Phase A에서 확정한 실제 라우트와 정확히 일치해야 한다(dead-link 방지).
6. **슬라이스 테스트 `NotificationInboxControllerSliceTest.java`** — L87 `deepLink .doesNotExist()` 단언을 파생 경로 값 단언으로 교체(APPROVAL/DELEGATION 각각).
7. **ERP 계약 `notification-api.md`** — Common shape(L59)·deepLink 설명(L72–75)을 "null/ABSENT" → "APPROVAL/DELEGATION 소스에서 콘솔 라우트 파생"으로 갱신. sourceType 설명(L79 "always APPROVAL in v1")이 위임 소스 존재와 드리프트면 함께 정정.
8. **FE 정리(선택)**: 백엔드가 deepLink를 채우면 `NotificationBell`의 APPROVAL fallback은 안전망으로 유지하거나 제거 — 유지 권장(백엔드 미배포 환경 회복탄력성).

> **PR 분할**: Phase A(FE 단독)를 먼저 랜딩해 오라우팅 버그를 즉시 해소. Phase B는 별도 후속(cross-project 원자 PR)로 분리 가능 — 그 경우 이 태스크는 Phase A 완료 시 review 이동, Phase B는 원 태스크 ID를 참조하는 후속 태스크로.

## Out of Scope (의도적 유지)
- console-bff 집계기·`NotificationInboxResponse` 변경 — item 필드 verbatim 통과로 무변경(deepLink는 백엔드가 채우면 자동 도달).
- ecommerce/wms 알림의 집계기 배선 — 여전히 erp-only Phase-1 유지(별건).
- 결재함 페이지에 결재 write/상신/승인 액션 신설 — 이 태스크는 딥링크 진입 + 프리선택(읽기 항해)만.

## Acceptance Criteria
- **AC-1** (Phase A) ERP 결재 알림 클릭 → **`/erp/approval`** 로 이동(마스터 `/erp` 아님)하고 `sourceId` 결재건이 프리선택/하이라이트됨. 미존재/불량 id → 목록만 렌더(크래시 없음).
- **AC-2** (Phase A) `NotificationBell`은 `n.deepLink`가 존재하면 그 값으로 push하고, 부재+APPROVAL 소스에서만 `/erp/approval?request=<sourceId>` fallback을 사용. 비-APPROVAL·id 없음 → inert(mark-read만).
- **AC-3** (Phase A) `pnpm lint` + `tsc --noEmit` + `vitest`(notifications + erp approval 화면 신규 테스트 포함) green. **`pnpm lint` 필수**(no-unused-vars가 tsc/vitest에 안 잡힘 — `[[env_console_web_local_verify_needs_lint]]`).
- **AC-4** (Phase B, 착수 시) `NotificationResponse.deepLinkFor`가 APPROVAL→`/erp/approval?request=<sourceId>`, DELEGATION→위임 라우트를 파생, 슬라이스 테스트가 실제 값 단언으로 갱신, `:notification-service:test` green. 파생 경로가 Phase A 확정 콘솔 라우트와 문자열 일치(dead-link 부재 검증).
- **AC-5** (Phase B, 착수 시) notification-api.md deepLink/ sourceType 기술이 실제 동작과 정합(null/ABSENT 문구 제거, 위임 소스 반영).

## Edge Cases
- `sourceId`가 없거나 알림 소스가 비-APPROVAL(위임 등) → Phase A는 위임 라우트 미확정 시 inert 유지(회귀 없음). Phase B에서 DELEGATION 라우팅 추가.
- 결재함 페이지에 `request=<id>`가 있으나 해당 결재건이 목록/권한 밖 → 조용히 무시하고 전체 목록 렌더(에러 배너 금지).
- 백엔드 deepLink와 FE fallback이 **다른 경로**를 가리키면 알림별로 목적지 불일치 → AC-4의 문자열 일치 검증으로 가드.
- deepLink 미배포(Phase B 미완) 환경 → FE fallback이 여전히 올바른 `/erp/approval`을 가리켜 정상 동작(회복탄력성).

## Failure Scenarios
- Phase A에서 fallback만 고치고 결재함 페이지 `request` 파라미터 소비를 빠뜨림 → `/erp/approval`엔 도달하나 특정 결재건 프리선택 실패(부분 수정). 결재함 페이지 테스트가 프리선택 단언으로 가드.
- Phase B deepLink 파생 경로 오타/오정렬(`/erp/approvals/{id}` 등 미존재 라우트) → dead-link. AC-4 문자열 일치 + FE fallback 안전망이 이중 가드.
- 슬라이스 테스트를 `.doesNotExist()`로 방치한 채 deepLinkFor만 변경 → `:notification-service:test` RED(계약 단언 불일치). AC-4가 테스트 동반 갱신을 강제.

## Related Specs
- `projects/platform-console/specs/services/console-web/architecture.md` (erp 라우트 트리 — `/erp/approval` `request` 파라미터 추가)
- `platform/contracts/notification-inbox-contract.md` §1 (deepLink 공유 계약 — 이미 존재, 소비만)
- `projects/erp-platform/specs/contracts/http/notification-api.md` L59·L72–83 (Phase B 갱신 대상)

## Related Contracts
- 셸 소비: `features/notifications/api/notification-types.ts` L64 `deepLink: z.string().optional()`(이미 존재)
- 집계기(무변경 확인용): `projects/platform-console/apps/console-bff/.../application/usecase/NotificationAggregationUseCase.java` `extractItems`
- events: `projects/erp-platform/specs/contracts/events/notification-subscriptions.md`(APPROVAL 4종 + DELEGATION 2종, sourceId=approvalRequestId)

## Related
- 원본 갭 발견: 2026-07-08 ERP 기능↔콘솔 메뉴 배치 감사. deepLink "미배선"이 실측 결과 **오라우팅**(`/erp?approval=` → 파라미터 무시하는 마스터 페이지)으로 확증 — module-liveness+live-consumer+grep 3중 검증.
- 관련 컨벤션: `[[proj_console_ecommerce_detail_conventions]]`(상세/헤더), `[[env_console_web_local_verify_needs_lint]]`(lint 필수).
- ERP 알림 배선 선행(전부 done): TASK-ERP-BE-011(notification bootstrap)·BE-027(inbox shape conformance, deepLink 필드 도입). 이 태스크는 그 additive 필드를 실제로 채운다.
