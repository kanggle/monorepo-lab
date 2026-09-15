# Task ID

TASK-FE-101

# Title

다크 테마에서 «장바구니에 추가되었습니다.» 가 안 보인다 — Toast 가 배경만 밝게 고정하고 글자색은 테마를 따른다

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

# 배경

상품 상세·목록에서 장바구니에 담으면 `Toast`(`shared/ui/Toast.tsx`)가 «장바구니에 추가되었습니다.» 를 띄운다.
사용자 보고: **다크 테마에서 잘 안 보인다.**

## 원인 (코드로 확인)

```tsx
const STYLE_MAP = {
  success: { backgroundColor: '#f0fdf4', borderColor: '#22c55e' },  // 고정 연녹
  error:   { backgroundColor: '#fef2f2', borderColor: '#ef4444' },  // 고정 연분홍
};
// ...
color: 'var(--color-text)',  // 테마 토큰
```

- 배경은 **고정값**(라이트 전용), 글자색은 **테마 토큰**. 다크 테마(`:root[data-theme='dark']`)에서 `--color-text` 가
  `#ededed` 로 바뀌어 **밝은 배경 위 밝은 글자** — WCAG 대비 **약 1.1:1**(AA 기준 4.5:1).
- 같은 `Toast` 를 쓰는 곳: `AddToCartButton` · `ProductPurchasePanel` · `ProfileForm`(프로필 저장) — 성공·에러 둘 다 같은 결함.

## 형제 검사

web-store 에서 밝은 배경을 고정한 곳 전수(grep): `Toast` · `products/page.tsx`(검색 불가 안내) · `DemoBackendNoticeClient`
· 이미지 위 반투명 흰 오버레이들. 🔵 `products/page.tsx`·`DemoBackendNoticeClient` 는 **글자색도 고정**(`#856404`·`#92400e`)이라
다크에서도 읽힌다 ⇒ «배경 고정 + 글자 테마» 라는 **같은 결함은 `Toast` 하나**다. 오버레이는 글자 대비 문제가 아니다.

---

# Goal

라이트·다크 두 테마 모두에서 Toast 문구의 대비가 WCAG AA(4.5:1) 이상이다.

---

# Scope

## In Scope

- `globals.css` 에 `--color-success-surface`·`--color-success-border`·`--color-error-surface`·`--color-error-border` 토큰(라이트 값 =
  기존 색, 다크 값 = 어두운 초록·빨강 계열)
- `Toast.tsx` 가 고정 hex 대신 그 토큰을 쓴다
- 두 테마 대비를 토큰 실제 값으로 계산하는 시험 + 기존 hex 단언 정리

## Out of Scope

- Toast 의 위치·지속시간·애니메이션
- 다른 화면의 고정 색(위 형제 검사에서 결함 아님으로 판정)

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 원인을 코드로 특정하고, 같은 부류(배경 고정 + 글자 테마)가 web-store 에 더 있는지 전수 확인한다(위 § 형제 검사).
- [ ] **AC-1** — 다크 테마에서 `--color-text` 대 `--color-success-surface`·`--color-error-surface` 대비 ≥ 4.5:1, 라이트도 동일.
- [ ] **AC-2** — **대조군**: 결함이던 조합(다크 `--color-text` on 연녹 `#f0fdf4`)이 같은 판정기에서 기준 미달로 나온다.
- [ ] **AC-3** — `Toast.tsx` 에 고정 hex 가 없고 다섯 토큰을 `var()` 로 쓴다(소스 단언).
- [ ] **AC-4** — 라이트 테마의 토스트 색은 **기존과 같다**(라이트 토큰 값 = 기존 hex).
- [ ] **AC-5** — 게이트: web-store `tsc`·ESLint 로컬 · vitest·lint & build CI.
- [ ] **AC-6** — 라이브: 배포 뒤 `store.hubwang.com` 에서 `data-theme=dark` 일 때 `--color-success-surface` 계산값이 다크 값이다
      (토스트는 장바구니 조작이 필요하므로 토큰 계산값으로 잰다 — 가능하면 실제 토스트도 띄워 본다).

---

# Related Specs

- `projects/ecommerce-microservices-platform/apps/web-store/src/app/globals.css` (디자인 토큰 · 다크 테마 블록)
- `projects/ecommerce-microservices-platform/apps/web-store/src/shared/ui/Toast.tsx`

# Related Contracts

- 없음 (UI 표현만)

---

# Edge Cases

- 테마 전환 직후 토스트가 떠 있는 경우 — 토큰이라 전환과 함께 색이 바뀐다
- 에러 토스트(프로필 저장 실패 등) — 같은 결함이었으므로 같이 고친다

# Failure Scenarios

- **다크 표면만 추가하고 Toast 가 여전히 hex 를 쓴다** → 다크에서 그대로 안 보인다. AC-3 이 문다
- **대비 판정기가 늘 통과한다** → AC-2 대조군이 문다
- **라이트 색이 바뀐다** → 의도치 않은 시각 변경. AC-4

# Test Requirements

- `toast-dark-contrast.test.ts` — 두 테마 × 두 표면 대비 · 대조군 · 다크 블록 정의 · 소스 토큰 사용

# Definition of Done

- [ ] 수정 + 테스트
- [ ] 게이트 통과
- [ ] 라이브 확인(AC-6)

분석=Opus 5 / 구현 권장=Sonnet (단순 토큰화 — 이번엔 분석 흐름이 이어져 Opus 가 구현)
