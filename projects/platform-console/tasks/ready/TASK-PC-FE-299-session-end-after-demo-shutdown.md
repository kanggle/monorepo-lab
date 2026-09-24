# Task ID

TASK-PC-FE-299

# Title

데모 서버 종료 뒤 세션 잔존 표면 정리 — 캐시 노출 · bfcache · 원인 구분 메시지

# Status

ready

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.

---

# Goal

데모 백엔드가 꺼진 뒤(또는 세션 만료 뒤) 방문자가 인증된 화면의 잔존 데이터를 보거나(브라우저 캐시/bfcache), 원인이 다른데 같은 문구를 보는 문제를 정리한다. 기존 재로그인 배선(`refresh` 실패 → `clearFullSession` → `/login?error=session_expired`, 백엔드 401 → `RE_LOGIN_PATH`)은 **이미 있다** — 이 티켓은 그 배선이 놓친 표면(캐시 헤더 부재, bfcache, 원인 미구분, 클라 잔존 상태)만 다룬다.

# Scope

## In Scope

- **캐시 헤더**: 인증된 콘솔 HTML 응답에 `Cache-Control: no-store` (+ `private`)를 추가한다. 현재 `next.config.mjs:83-95` 에는 보안 헤더만 있고 캐시 지시자가 없다(2026-09-24 UTC 실측 확인 — 해당 파일에 `no-store`/`Cache-Control` 패턴 0건). `(console)` 레이아웃은 이미 `force-dynamic`(`layout.tsx:53`)이므로 정적 캐싱 문제는 아니지만, 브라우저/중간 캐시가 인증된 페이지를 저장하지 않도록 헤더를 명시한다. **정적 자산에는 적용하지 않는다** — 샘플 방문자 페이지(ADR-MONO-074, 로그인 불필요)에 적용하면 캐싱 이점을 해칠 수 있으므로, 인증이 필요한 라우트에만 한정하고 그 판단 근거를 구현 기록에 남긴다.
- **bfcache 대응**: `pageshow` 이벤트(`event.persisted === true`)를 감지해, 세션 쿠키가 없는 상태로 뒤로가기/앞으로가기로 복귀한 인증 페이지를 강제로 새로고침(또는 로그인으로 리다이렉트)한다.
- **원인 구분 로그인 메시지**: 로그인 페이지가 지금은 한 문구(「세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요.」, `login/page.tsx:31,81-86`)만 보여준다(2026-09-24 UTC 실측 확인). 데모 서버 종료로 인한 로그아웃일 때는 「서버가 종료되어 다시 로그인해야 합니다.」류 변형 문구를 보여준다 — **이 판단은 실제 신호(예: 데모 상태 엔드포인트)에서 파생해야 하며 추측 배지를 달지 않는다.**
- **클라이언트 잔존 상태 정리**: 강제 로그아웃 시 클라이언트에 남아 있을 수 있는 선택 테넌트, 메뉴 드릴 상태, 사용자 정보 등(어떤 저장소를 쓰든 — localStorage/sessionStorage/메모리 상태)을 점검하고, 남아 있는 것이 있으면 로그아웃 경로에서 지운다.
- **낡은 주석 정리**: `DemoBackendNotice.tsx:40-43` 의 "page.tsx 67개 중 익명으로 닿는 것이 /login 하나"(나머지 66개는 `(console)/layout.tsx` 의 `isAuthenticated()` 가드 뒤) 주석은 ADR-MONO-074 이후 **낡았다** — 2026-09-24 UTC 실측: 같은 레이아웃이 이제 `isSampleVisitor() || isAuthenticated()` 로 게이트하므로(:144-145), 샘플 방문자는 로그인 없이도 66개 화면에 닿을 수 있다. 이 주석을 현재 사실에 맞게 정정한다.

## Out of Scope

- 재로그인 배선 자체(`refresh` route, `re-login.ts`, `session-refresh.ts`)의 로직 변경 — 이미 동작하는 부분은 건드리지 않는다.
- 데모 상태를 판별하는 새 백엔드 엔드포인트 신설 — 이미 있는 신호(`resolveDemoBackendState()` 류)를 재사용하는 것을 우선하고, 정말 없으면 그 사실을 AC에 기록한다(신설이 필요하면 별도 백엔드 티켓으로 분리).

# Acceptance Criteria

- [ ] **AC-1** — 인증된 콘솔 HTML 응답에 `Cache-Control: no-store, private`(또는 동등)가 있고, 정적 자산·샘플 방문자 페이지에는 적용되지 않는다(각각 헤더 확인).
- [ ] **AC-2** — 유효한 세션으로 페이지를 새로고침해도 로그아웃되지 않는다(회귀 없음 확인 — 이 AC가 가장 깨지기 쉬운 부분이다).
- [ ] **AC-3** — 세션 쿠키가 없는 상태로 bfcache 복귀(뒤로가기)했을 때 인증 페이지가 잔존 데이터를 보여주지 않고 새로고침/리다이렉트된다(재현 스텝 + 확인 방법 기록).
- [ ] **AC-4** — 데모 종료로 인한 로그아웃과 일반 세션 만료가 **실제 신호로 구분**되어 다른 문구를 보여준다(추측이 아님을 확인 — 신호 소스를 명시).
- [ ] **AC-5** — 강제 로그아웃 후 클라이언트에 남아 있던 테넌트/드릴 상태/사용자 정보가 지워진다(재현 스텝).
- [ ] **AC-6** — `DemoBackendNotice.tsx` 의 낡은 주석이 ADR-MONO-074 이후 현재 사실(샘플 방문자도 66개 화면에 닿을 수 있음)에 맞게 갱신됐다.
- [ ] **AC-7** — 유닛 테스트 + typecheck + build 통과. 브라우저로 AC-2·AC-3 재현.

# Related Specs

- `projects/platform-console/apps/console-web/src/app/api/auth/refresh/route.ts`(:50,71,160,171)
- `projects/platform-console/apps/console-web/src/shared/lib/re-login.ts`(:48-54)
- `projects/platform-console/apps/console-web/src/app/(console)/layout.tsx`(:53 force-dynamic, :144-145 isSampleVisitor/isAuthenticated)
- `projects/platform-console/apps/console-web/src/app/(auth)/login/page.tsx`(:31 세션 만료 문구)
- `projects/platform-console/apps/console-web/src/widgets/demo-notice/DemoBackendNotice.tsx`
- `projects/platform-console/apps/console-web/next.config.mjs`(:83-95 보안 헤더)
- `projects/platform-console/apps/console-web/src/__tests__/relogin-marker.test.ts`
- ADR-MONO-074 (샘플 방문자 모드)
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

---

# Related Contracts

- 없음(프런트엔드 헤더/클라이언트 상태 변경, API 계약 무변경).

---

# Target App

- `apps/console-web`

---

# Edge Cases

- 데모가 "기동 중" 상태에서 세션이 만료된 경우 — 「서버가 종료되어…」 문구가 오도할 수 있다(서버는 켜지는 중이지 종료된 게 아니다). 상태별 문구 3종(만료/데모 종료/데모 기동 중)을 구분할지, 2종으로 단순화할지 구현자가 판단하고 근거를 남긴다.
- 샘플 방문자(비로그인)는 애초에 세션이 없으므로 이 티켓의 caching/bfcache 로직이 그 경로를 방해하지 않아야 한다.

# Failure Scenarios

- `no-store` 를 레이아웃 전체에 걸어 샘플 방문자 페이지까지 캐시 불능이 되면 ADR-MONO-074 가 의도한 정적 콘텐츠 가벼움이 깨진다.
- 유효 세션 새로고침이 로그아웃되는 회귀(AC-2) — 캐시 헤더/bfcache 로직이 과잉 반응하면 발생한다.
- 데모 종료 여부 판정을 실제 신호 없이 타이머·추측으로 만들면 AC-4가 요구하는 "실제 신호로 파생" 을 위반한다.
