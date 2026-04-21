# Changelog

All notable changes to `web-store` will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-04-02

### Added
- UI 전면 개선 — Header, Footer, globals.css, 상품상세, 장바구니, 주문, 마이페이지
- 다중옵션 선택, 주소검색, 배송지선택, 즉시주문, 결제완료 플로우
- Google/Instagram 소셜 로그인 UI + OAuth 콜백

### Fixed
- 장바구니 상품링크, 결제 전환 깜빡임, 홈 레이아웃 수정

### Changed
- 이미지/폰트/캐싱/스켈레톤 성능 최적화
- FSD layer-violation 해소 — OrderDetail, Orders, Addresses, Profile 비즈니스 로직을 features 레이어로 이동
- 인증 가드 중복 패턴을 `useRequireAuth` 훅으로 추출
- Login/Signup 인증 리다이렉트 로직을 `useRedirectIfAuthenticated` 훅으로 추출
- ProfileForm 반복 입력 필드를 `ProfileFormField` 컴포넌트로 추출
- OrderDetailView 데이터 로딩/액션 로직을 `useOrderDetail` 훅으로 추출
- AuthCardLayout 추출로 인증 페이지 레이아웃 중복 제거
- CartItemRow quantityBtnStyle 상수 추출
- OrderDetailView 결제 섹션 중복 병합
- AddressList smallBtn 스타일 추출
- SignupForm 비밀번호 규칙을 데이터 배열로 변환
- CheckoutForm import 경로를 barrel export로 변경
- 전화번호 검증 로직을 shared/lib으로 추출
- 미사용 CartItemRow export 제거
