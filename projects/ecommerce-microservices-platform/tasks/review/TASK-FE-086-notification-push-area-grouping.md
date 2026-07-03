# TASK-FE-086 — 알림 설정: 푸시 채널 영역 안에 옵트인/기기목록 그룹화 + 버튼 글자색 정합

- **Status**: review
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)
- **IMPLEMENTED (2026-07-03)**: `NotificationSettings.tsx` — 푸시 토글 바로 아래에 `pushEnabled && <div data-testid="push-area"><PushOptIn/><PushDeviceList/></div>` 로 그룹화(하단 개별 렌더 제거, `!isLoading && !error` 블록 안). `PushOptIn.tsx` — 버튼(미구독 상태) 글자색 `var(--color-white)`→`var(--color-on-primary)`(좌측 사이드바 active 색=`my/layout.tsx` 동일 토큰), 섹션 상단 `marginTop space-6`+`borderTop` 제거→`marginTop space-4`(토글 아래 자연 연결). 테스트: notification-settings 에 push-area 게이팅 2케이스(pushEnabled true/false), push-optin 에 버튼 색상 1케이스. 라이브 3001 hot-reload 확인. ⚠️로컬 vitest 불가(Node24↔vitest4)→CI Node20 권위.

## Goal

web-store 알림 설정(`/my/notifications/settings`)에서 "푸시" 채널이 활성화(`pushEnabled`)됐을 때, 흩어져 있던 **"이 브라우저에서 푸시 받기"(PushOptIn)** 와 **"푸시 수신 기기"(PushDeviceList)** 를 **푸시 채널 영역 안으로** 그룹화한다. 현재는 이메일/SMS/푸시 토글과 별개로 화면 하단에 나열돼 있어 "푸시" 채널과의 관계가 시각적으로 드러나지 않는다.

또한 "이 브라우저에서 푸시 받기" 버튼의 글자색을 **좌측 마이페이지 사이드바에서 선택된 메뉴 항목의 글자색과 동일**(`var(--color-on-primary)`)하게 맞춘다(현재 `var(--color-white)` 하드코딩 → 테마 토큰으로 정합).

## Scope

**In scope** (web-store only):

1. `src/features/notification/ui/NotificationSettings.tsx` — "푸시" 토글 바로 아래에, `pushEnabled === true` 일 때만 `PushOptIn` → `PushDeviceList` 순서로 렌더(둘을 "푸시 영역" 컨테이너로 묶음). 하단에 따로 있던 `<PushOptIn />` / `<PushDeviceList />` 제거. 로딩/에러 상태에서는 렌더 안 함(기존 `!isLoading && !error` 블록 안으로 이동).
2. `src/features/notification/ui/PushOptIn.tsx` — (a) "이 브라우저에서 푸시 받기" 버튼(미구독 상태)의 글자색을 `var(--color-white)` → `var(--color-on-primary)` 로 변경(좌측 메뉴 선택 항목과 동일 토큰). (b) 푸시 토글 바로 아래에 자연스럽게 붙도록 섹션 상단 여백/구분선 조정(중복 border 제거).

**Out of scope**: 푸시 구독/발송 로직, 백엔드, 이메일/SMS 토글, 기기 목록 데이터.

## Acceptance Criteria

- **AC-1 — 그룹화.** 푸시 토글("푸시 알림을 받습니다") 바로 아래에 "이 브라우저에서 푸시 받기", 그 아래 "푸시 수신 기기" 목록이 위치하여 하나의 푸시 영역으로 묶여 보인다.
- **AC-2 — 활성화 게이팅.** `pushEnabled` 가 false 이면 옵트인/기기목록이 표시되지 않고, true 일 때만 표시된다.
- **AC-3 — 버튼 글자색.** "이 브라우저에서 푸시 받기" 버튼 글자색이 `var(--color-on-primary)`(좌측 사이드바 선택 메뉴 글자색과 동일 토큰, 라이트/다크 테마 모두 정합)로 렌더된다.
- **AC-4 — 회귀 없음.** 이메일/SMS/푸시 토글, 저장 성공/실패 메시지, 로딩/에러 상태, 뒤로가기 링크는 기존과 동일하게 동작.
- **AC-5 — 게이트.** web-store 프런트 유닛(vitest) GREEN(기존 notification-settings / push-optin / push-device-list 테스트 통과, 필요 시 갱신). ⚠️ 로컬 web-store vitest 불가(Node24↔vitest4) → CI Node20 권위.

## Related Specs

- TASK-FE-085 / fix-001 — 기기 목록·실시간 갱신. 본 task 는 그 UI 배치를 푸시 채널 영역으로 정리.
- `apps/web-store/src/app/(store)/my/layout.tsx` — 좌측 사이드바 active 항목 색(`var(--color-on-primary)`) = 버튼 글자색 기준.

## Related Contracts

- 없음(프레젠테이션 배치/색상, API 무관).

## Edge Cases

- 푸시 미지원 브라우저: `pushEnabled` true 여도 PushOptIn 이 자체적으로 "지원하지 않습니다" 안내를 표시(기존 동작 유지).
- 다크 테마: `var(--color-on-primary)` 가 테마별로 바뀌므로 버튼 글자색도 사이드바 선택 색과 함께 정합.
- 푸시 토글을 끄면(pushEnabled=false) 옵트인/목록이 사라지지만, 브라우저 구독 자체(및 DB)는 유지(선호도 토글과 기기 구독은 별개 — FE-085 규칙).

## Failure Scenarios

- 옵트인/목록을 `pushEnabled` 대신 항상 렌더하면 AC-2 위반 → `!isLoading && !error && pushEnabled` 로 게이팅.
- 버튼 색을 리터럴(`#fff`)로 넣으면 다크 테마에서 사이드바와 불일치 → 반드시 `var(--color-on-primary)` 토큰 사용.
