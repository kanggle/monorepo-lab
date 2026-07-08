# TASK-ERP-BE-028 — notification `deepLink` 파생 (APPROVAL·DELEGATION 콘솔 라우트)

**Status:** done
**Area:** erp-platform / notification-service · **Type:** feature (contract-visible behavioural fill-in)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet (소규모 backend + 계약 정합) (분석=Opus 4.8 / 구현 권장=Sonnet)
**Origin:** TASK-PC-FE-230 Phase B — 2026-07-08 ERP 알림 딥링크 감사에서 식별. FE(PC-FE-230, done)는 이미 `deepLink` 우선 소비하도록 배선됐고, 백엔드가 채우면 자동 우선.
**Impl:** `NotificationResponse.deepLinkFor` = enum switch(APPROVAL→`/erp/approval?request=<sourceId>`, DELEGATION→`/erp/delegation`) + 클래스 javadoc 갱신. 슬라이스 테스트 APPROVAL 값 단언 + DELEGATION 파생 테스트 추가. 계약 notification-api.md deepLink/sourceType/type-enum + v2-deferred delegation 스테일 정정. 검증: `:notification-service:test` BUILD SUCCESSFUL(Docker-free 슬라이스). console-web 무변경(FE `deepLink` 우선 소비).

---

## Goal

erp `notification-service`의 `NotificationResponse.deepLinkFor(n)`이 하드코딩 `return null`이라 계약 § 1의 `deepLink` 필드가 항상 비어 있다(NON_NULL 규약상 생략). 결과:
- **APPROVAL 알림**: 셸 `NotificationBell`이 자체 fallback(`/erp/approval?request=<sourceId>`, PC-FE-230)으로 항해 — 동작하나 라우팅 지식이 FE에 하드코딩됨.
- **DELEGATION 알림**: FE fallback은 `sourceType==APPROVAL`만 처리 → **위임 알림 클릭 시 아무 데도 안 감(inert)**. 실질 갭.

`deepLinkFor`가 `sourceType`/`sourceId`에서 실제 콘솔 라우트를 파생하도록 채운다. 이로써 라우팅 지식이 백엔드 계약으로 승격되고, DELEGATION 알림도 항해 가능해진다. **console-web 변경 불필요**(FE가 `deepLink` 우선 소비 — PC-FE-230; console-bff 집계기는 item 필드 verbatim 통과).

## 배경 사실 (검증됨 2026-07-08)

- `SourceRef.SourceType` = **{APPROVAL, DELEGATION}** (`domain/notification/SourceRef.java`). `NotificationType` 6종(APPROVAL_SUBMITTED/APPROVED/REJECTED/WITHDRAWN + DELEGATION_GRANTED/REVOKED).
- **DELEGATION 알림 실제 생성됨**: `ApprovalDelegatedConsumer`(`erp.approval.delegated.v1`)·`ApprovalDelegationRevokedConsumer`(`erp.approval.delegation.revoked.v1`) → `NotificationFactory`가 `SourceRef.delegation(grantId)` 설정. 계약의 "sourceType always APPROVAL in v1"·type enum 4종 열거는 **stale**.
- `sourceId`: APPROVAL=`approvalRequestId`(예 `appr-1`), DELEGATION=`grantId`.
- 실제 콘솔 라우트(SoT=console): 결재함=`/erp/approval`(PC-FE-230이 `?request=<id>` 프리선택 지원), 위임=`/erp/delegation`.
- 슬라이스 테스트 `NotificationInboxControllerSliceTest.java` L87이 `$.data[0].deepLink` `.doesNotExist()`로 현 null을 고정.

## Scope

1. **`NotificationResponse.deepLinkFor(n)`** — `null` → 파생:
   - `APPROVAL` → `/erp/approval?request=<sourceId>`
   - `DELEGATION` → `/erp/delegation`
   - enum switch(2값 exhaustive). 클래스 javadoc의 "always null" 기술 갱신.
2. **슬라이스 테스트** — L87 `.doesNotExist()` → `.value("/erp/approval?request=appr-1")`. DELEGATION 파생 테스트 추가(`SourceRef.delegation("grant-9")` → `/erp/delegation`).
3. **계약 `notification-api.md`** — deepLink JSON 예시(L59)·bullet(L72-75)를 "파생됨"으로 갱신, sourceType bullet(L79-80) "always APPROVAL in v1" 정정, common-shape `type` enum(L56)에 DELEGATION_GRANTED/REVOKED 추가. v2-deferred 스테일 노트(L255-257) 관련 시 정정.

## Out of Scope (의도적 유지)
- console-web 변경 — FE가 `deepLink` 우선 소비(PC-FE-230 done). fallback은 백엔드 미배포 환경 안전망으로 유지.
- console-bff 집계기 — item 필드 verbatim 통과, 무변경.
- `/erp/delegation`에 특정 grant 프리선택 파라미터 — 위임 페이지가 미지원(현재), 평문 라우트로 충분(결재함과 달리 위임은 애초 라우팅이 0이었음 → 평문도 strict 개선). 후속 판단.
- `deepLink` sourceId URL-encode — sourceId는 시스템 id(안전 문자셋)라 평문 결합. 특수문자 유입 시 후속.

## Acceptance Criteria
- **AC-1** `deepLinkFor`가 APPROVAL→`/erp/approval?request=<sourceId>`, DELEGATION→`/erp/delegation` 파생. 응답 JSON `deepLink`에 노출(NON_NULL — 이제 non-null이라 항상 present).
- **AC-2** 슬라이스 테스트가 APPROVAL·DELEGATION 각각 실제 파생 값 단언(`.doesNotExist()` 제거).
- **AC-3** 계약 notification-api.md의 deepLink/sourceType/type-enum 기술이 실제 동작과 정합(null/ABSENT·"always APPROVAL" 문구 제거).
- **AC-4** `:projects:erp-platform:apps:notification-service:test` green (Docker-free 슬라이스).
- **AC-5** 파생 경로가 실제 console 라우트와 문자열 일치(`/erp/approval?request=`·`/erp/delegation` — PC-FE-230/menu와 대조).

## Edge Cases
- 미래 `SourceType` 값 추가 → exhaustive switch 컴파일 에러로 강제 인지(의도적: 새 소스는 라우트를 의식적으로 정의해야 함).
- sourceId 빈 문자열/특이값 → 시스템 생성 id라 비현실적; 평문 결합(방어 인코딩은 out of scope).

## Failure Scenarios
- 파생 경로 오타/미존재 라우트(`/erp/approvals/{id}` 등) → dead-link. AC-5 문자열 일치 + FE fallback 안전망이 이중 가드.
- 슬라이스 테스트를 `.doesNotExist()`로 방치 → `:test` RED(계약 단언 불일치). AC-2가 동반 갱신 강제.
- DELEGATION 케이스 누락(APPROVAL만 파생) → 위임 알림 여전히 inert. AC-1이 양 소스 커버 강제.

## Related Specs / Contracts
- `projects/erp-platform/specs/contracts/http/notification-api.md` (deepLink/sourceType/type — 갱신 대상)
- `projects/erp-platform/specs/contracts/events/notification-subscriptions.md` (APPROVAL 4종 + DELEGATION 2종 소스)
- console 소비 선례(무변경): platform-console `features/notifications` (`deepLink` 우선 소비, PC-FE-230 done)

## Related
- 선행 FE: TASK-PC-FE-230(done, #2327) — NotificationBell `deepLink` 우선 + 결재함 `?request=` 프리선택.
- 알림 배선 선행(done): TASK-ERP-BE-011/014/016(notification bootstrap + delegation consumers)·BE-027(inbox shape — deepLink 필드 도입).
