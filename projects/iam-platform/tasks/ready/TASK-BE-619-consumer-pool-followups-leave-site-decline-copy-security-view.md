# Task ID

TASK-BE-619

# Status

ready

# Title

전역 소비자 계정 후속 셋 — 사이트 탈퇴 vs 계정 삭제 · 동의 거절 문구 · 콘솔 보안 이벤트 조회 (`TASK-BE-616` 에서 인계)

# Owner

iam-platform

# Task Tags

- account-service
- web-store
- platform-console
- follow-up

---

> **분석 모델:** Opus 5.5 / **구현 권장:** 항목 1 = Opus(삭제·탈퇴 의미 결정) · 항목 2·3 = Sonnet 5

---

# Dependency Markers

- **출처**: `TASK-BE-616` (done) § 검토 · § CORRECTION — 그 티켓이 «후속» 으로 남긴 세 의무. 이 티켓이 그 집이다.
- **선행**: 없음(616 머지 `2e7e2951f` 로 플래그가 켜져 있다).

# Goal

`TASK-BE-616` 이 플래그를 켜면서 드러났지만 그 범위 밖이라 남긴 세 가지를 닫는다. 서로 독립이라 항목별로 PR 을 나눠도 된다.

# Scope

## In Scope

1. **사이트 탈퇴 vs 계정 삭제** — 지금 사이트 운영자의 GDPR 삭제·사용자 `/me` DELETE 는 **풀 계정 하나**를 지워 모든 소비자 사이트에서 사라진다(616 이 계약 § 5 로 넓힌 결과, 데이터 주체 삭제 요청으로는 맞다). 그런데 «이 사이트만 그만 쓰기»(멤버십 `LEFT`) 경로가 없다. 사용자·운영자 각각에게 어떤 동작이 «탈퇴» 이고 어떤 동작이 «계정 삭제» 인지 정하고(🔴 소유자 결정이 필요할 수 있다 — 화면 문구·법적 의미), 멤버십 `LEFT` 경로를 만든다.
2. **동의 거절 문구** — 풀 계정이 사이트 첫 방문 동의를 거절하면 IAM 이 `error=access_denied` 로 돌려보낸다. NextAuth v5 가 그것을 `AccessDenied` 로 바꾸면 web-store `apps/web-store/src/features/auth/ui/LoginForm.tsx` → `normalizeErrorCode` 가 `role_denied` 로 묶어 «operator 계정으로는 web-store 에 접근할 수 없습니다…» 를 보인다(미측정). 측정 → 맞는 문구.
3. **콘솔 보안 이벤트 조회** — 풀 계정의 보안 이벤트는 `tenant_id=consumer-pool` 로 기록된다. 콘솔에서 사이트 테넌트(`ecommerce` · `fan-platform`)로 걸러 보면 보이지 않는다.

## Out of Scope

- 풀 모델 자체의 변경

# Acceptance Criteria

- [ ] **AC-1 (항목 1)** — «사이트 탈퇴» 와 «계정 삭제» 의 동작이 표로 정해져 있고(사용자 / 사이트 운영자 / 플랫폼 관리자 각각), 소유자 결정이 필요한 칸은 결정을 받아 기록했다. 멤버십 `LEFT` 경로가 있고, `LEFT` 뒤 그 사이트 토큰이 발급되지 않으며(615 의 멤버십 검사) 다른 사이트는 영향이 없다 — 대조 시험.
- [ ] **AC-2 (항목 2)** — 거절 시 web-store 가 실제로 받는 `?error=` 값을 **측정**하고 기록한다(nightly full-stack e2e `apps/web-store/e2e/consent-decline.spec.ts`, `e2e/account-type-guard.spec.ts` 를 본뜸 — 팬 전용 풀 시드 계정이 필요). 측정값에 맞는 문구로 고친다. 측정 전에 문구를 고치지 않는다.
- [ ] **AC-3 (항목 3)** — 콘솔 보안 이벤트 화면이 사이트 테넌트로 볼 때 그 사이트 멤버 풀 계정의 이벤트를 포함하거나, 포함하지 않는다면 그 사실을 화면이 말한다(조용히 빈 목록 금지). 선택과 근거를 기록.

# Related Specs

- `projects/iam-platform/specs/features/multi-tenancy.md` § 소비자 계정 풀 § 5
- `projects/iam-platform/tasks/done/TASK-BE-616-first-visit-site-consent.md` § 검토

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/auth-to-account.md` (consumer-members)

# Edge Cases

- 모든 사이트에서 `LEFT` 한 풀 계정 — 계정은 살아 있는데 쓸 사이트가 없다. 다시 동의하면 돌아올 수 있나(616 은 `LEFT` 를 동의로 다시 열지 않는다).

# Failure Scenarios

1. «사이트 탈퇴» 버튼이 실제로는 풀 계정을 지워 다른 사이트 데이터까지 사라진다.
2. 측정 없이 거절 문구를 «고쳐» 다른 오류 코드의 문구를 깨뜨린다.

---

## 추가 관찰 (2026-10-02 UTC, 18차 창 — `TASK-MONO-744` 라이브 판정)

- ④ **동의 화면 부제**가 그 사이트의 로그인 문구를 재사용한다 — 스토어로 가는 동의 화면에 «쇼핑을 계속하려면 IAM 계정으로 로그인하세요.» 가 뜬다(이미 로그인한 사람에게 «로그인하세요»). 동의 화면 전용 부제가 필요한지 정한다. 증거: 세션 스크래치 `live18/shots/m3-03-store-first-visit.png`.
- ⑤ 가입 직후 자동 로그인이 아니라 IAM 로그인 화면이 비밀번호를 다시 묻는다(기존 동작 — «가입이 완료되었습니다. 로그인해 주세요.»). 의도된 동작이면 그대로 두고 적는다.
