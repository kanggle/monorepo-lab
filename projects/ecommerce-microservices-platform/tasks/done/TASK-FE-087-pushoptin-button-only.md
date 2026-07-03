# TASK-FE-087 — 알림 설정 PushOptIn: 제목·설명 제거하고 버튼만 표시

- **Status**: done
- **DONE (2026-07-03, 3-dim verified — PR #2142 `0c3e86d7f`)**: state=MERGED + origin/main tip=`0c3e86d7f` 일치 + pre-merge failing=0(Frontend lint&build+unit[제목/설명 미표시 케이스]+E2E GREEN). PushOptIn 제목·설명 제거, 버튼만 푸시 토글 아래 표시.
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)
- **IMPLEMENTED (2026-07-03)**: `PushOptIn.tsx` — 상단 제목 `<p>이 브라우저에서 푸시 받기</p>` 와 지원 상태 설명 note("허용하면 주문·배송 알림을…" / "이 브라우저에서 푸시 알림을 받고 있습니다.") 제거. 감싸던 `<div>` 도 제거하고 **버튼만** 직접 렌더(버튼의 `marginTop` 제거 → 섹션 `marginTop space-4` 로 푸시 토글 아래 배치). 미지원/권한차단 안내 note·에러 note·버튼 색상(`--color-on-primary`, FE-086)·"푸시 수신 기기" 목록(FE-085, 버튼 아래 유지)은 그대로. 테스트: push-optin 에 제목/설명 미표시 2케이스(미구독/구독) 추가. 라이브 3001 hot-reload 확인. ⚠️로컬 vitest 불가(Node24↔vitest4)→CI Node20 권위.

## Goal

**TASK-FE-086** 로 푸시 채널 영역에 그룹화된 `PushOptIn` 을 더 간결하게 정리한다. 사용자 요청: 반복적인 제목("이 브라우저에서 푸시 받기")과 설명("허용하면 주문·배송 알림을 이 브라우저에서 실시간으로 받습니다.") 을 없애고, **"이 브라우저에서 푸시 받기" 버튼만** "푸시 알림을 받습니다"(푸시 토글) 바로 아래에 둔다. "푸시 수신 기기" 목록은 그 버튼 아래, 같은 푸시 영역 안에 유지.

## Scope

**In scope** (web-store only):

1. `src/features/notification/ui/PushOptIn.tsx` — (a) 최상단 semibold 제목 `<p>이 브라우저에서 푸시 받기</p>` 제거. (b) 지원+비차단 상태의 설명 note(구독 여부에 따른 "받고 있습니다"/"허용하면…") 제거 및 감싸던 `<div>` 제거 → 버튼만 직접 렌더. 버튼 자체의 상단 여백 정리(섹션 여백으로 토글 아래 배치).
2. `src/__tests__/push-optin.test.tsx` — 제목/설명 없이 버튼만 표시됨을 단언(미구독/구독 각 케이스에서 설명 문구 부재 확인).

**Out of scope**: 미지원·권한차단 안내 note(유지), 버튼 동작/색상(FE-086 유지), 푸시 영역 그룹화·게이팅(FE-086 유지), PushDeviceList(유지).

## Acceptance Criteria

- **AC-1 — 버튼만.** 지원되고 권한이 차단되지 않은 상태에서 PushOptIn 은 제목·설명 없이 "이 브라우저에서 푸시 받기"(또는 구독 시 "이 브라우저 구독 해지") 버튼만 표시한다.
- **AC-2 — 위치.** 버튼이 푸시 토글("푸시 알림을 받습니다") 바로 아래에 위치하고, 그 아래 "푸시 수신 기기" 목록이 이어진다(FE-086 영역 유지).
- **AC-3 — 엣지 보존.** 미지원 브라우저·권한 차단 상태의 안내 문구, 에러 메시지는 기존대로 표시된다.
- **AC-4 — 회귀 없음.** 버튼 클릭(구독/해지), 버튼 색상(`--color-on-primary`), busy 비활성화는 기존과 동일.
- **AC-5 — 게이트.** web-store 프런트 유닛(vitest) GREEN(push-optin/notification-settings 통과 + 신규 케이스). ⚠️ 로컬 vitest 불가(Node24↔vitest4) → CI Node20 권위.

## Related Specs

- TASK-FE-086 — 푸시 영역 그룹화. 본 task 는 그 안의 PushOptIn 표현을 버튼-only 로 축소.

## Related Contracts

- 없음(프레젠테이션 축소, API 무관).

## Edge Cases

- 미지원/권한차단: 안내 note 는 유지되어 버튼이 없는 이유를 사용자에게 알림(제거하면 빈 영역이 됨).
- 구독/미구독: 설명 note 는 제거하되 버튼 라벨("받기"/"구독 해지")로 상태를 계속 구분.

## Failure Scenarios

- `note` 헬퍼까지 삭제하면 미지원/차단 안내가 사라짐 → 헬퍼 유지, 설명 호출부만 제거.
- 버튼을 감싼 `<div>` 만 지우고 note 를 남기면 요청과 불일치 → note 호출과 div 를 함께 제거.
