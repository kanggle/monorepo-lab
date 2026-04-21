# Task ID

TASK-FE-066

# Title

위시리스트 버튼 silent fail 방지 — 백엔드 `wishlistItemId` 누락 시 사용자 피드백 및 fallback 처리

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# Goal

`WishlistButton`에서 `inWishlist=true`이지만 `wishlistItemId`를 획득할 수 없을 때 클릭이 조용히 무시되어 사용자가 "하트가 반응하지 않는" 증상을 겪는 문제를 방지한다. 백엔드 응답 회귀(예: 필드 누락)나 일시적 오류 시에도 사용자가 상태를 인지하고 복구할 수 있도록 방어 로직을 추가한다.

## 배경

`TASK-BE-124-fix-001` 이후 백엔드 `WishlistCheckResponse`는 `wishlistItemId`를 내려주지만, 운영 중 이미지/배포 누락으로 필드가 빠지는 회귀가 실제로 발생했다(kkangchang99@gmail.com 계정 "베이직 코튼 티셔츠", "슬림핏 데님 청바지" 상품에서 하트 클릭이 무반응). `WishlistButton.tsx:66-70`의 `if (itemId)` 가드가 아무 피드백 없이 return하여 사용자가 원인을 알 수 없었다.

# Scope

## In Scope

- `apps/web-store/src/features/wishlist/ui/WishlistButton.tsx` 수정
  - `inWishlist === true && itemId == null` 케이스 감지
  - 다음 중 한 가지 이상 처리:
    - 사용자에게 안내(alert 또는 토스트: "위시리스트 상태를 다시 불러오는 중입니다" 등)
    - `useWishlistCheck` query invalidate/refetch 트리거로 자가 복구 시도
    - 개발 환경에서 `console.warn` 로그
- `useCallback` 의존성 배열에 `checkData` 추가 (stale closure 방지)
- 관련 테스트 추가:
  - `inWishlist=true` + `wishlistItemId=undefined` 상황에서 버튼 클릭 시 사용자 피드백이 발생하는지
  - refetch가 트리거되는지

## Out of Scope

- 백엔드 계약 변경 (이미 `wishlistItemId` 필드 존재)
- `productId` 기반 DELETE API 신규 추가 (별도 논의 필요)
- 상품 상세/목록 외 화면의 위시리스트 UI 변경

# Acceptance Criteria

- [ ] `inWishlist=true` 이고 `wishlistItemId` 가 없을 때 하트 클릭 시 최소 하나의 사용자 피드백(토스트/alert 등)이 발생한다.
- [ ] 같은 상황에서 `wishlist check` query 가 refetch 된다.
- [ ] `useCallback` 의존성 누락이 해결되어 `checkData` 업데이트가 handler에 반영된다.
- [ ] 정상 경로(`itemId` 존재)의 동작에 회귀가 없다.
- [ ] 추가된 테스트가 통과한다.

# Related Specs

- `specs/features/wishlist/` (해당 feature spec)
- `specs/platform/testing-strategy.md`

# Related Contracts

- `packages/types/src/wishlist.ts` — `WishlistCheckResponse`
- `apps/user-service` WishlistController `/api/wishlists/me/check` (변경 없음, 소비 계약 확인 용도)

# Edge Cases

- check API 가 500/네트워크 오류로 실패 → `checkData=undefined` → `inWishlist=false` 로 취급되어 add 시도 → `ALREADY_IN_WISHLIST` 에러로 invalidate 발생(기존 동작 유지).
- check API 가 `inWishlist: true` 만 돌려주고 `wishlistItemId` 를 누락 → 본 태스크가 해결 대상.
- 응답 지연 중 사용자가 여러 번 클릭 → 중복 refetch/alert 방지(throttle 또는 `isPending/checkLoading` 가드 유지).
- 로그아웃 상태 → 기존 `/login` redirect 경로 유지.

# Failure Scenarios

- 피드백 UI 가 모달/alert 의존이면 자동화 테스트에서 window.alert mocking 필요.
- refetch 가 무한 루프를 돌지 않도록 트리거 조건을 click 시점으로 한정.
- stale closure 수정 시 addMutation/removeMutation 참조 변경으로 인한 렌더 증가 점검.

# Notes

- 원인 조사 히스토리: 2026-04-15 조사에서 user-service Docker 이미지가 `TASK-BE-124-fix-001` 이전 JAR 로 실행 중이었던 사실을 확인, 재빌드/재시작으로 즉각 복구함. 본 태스크는 동일 회귀 재발 시 사용자 경험 손상을 막기 위한 **프론트 방어 로직** 추가이며 백엔드 수정은 포함하지 않는다.
