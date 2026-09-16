# Task ID

TASK-PC-FE-293

# Title

e2e 로그인 픽스처가 **테넌트 쿠키만 심고 assume 은 안 한다** — `TASK-PC-FE-292` 의 섹션 게이트 뒤에서 `main` nightly 가 빨갛다

# Status

review

# Owner

frontend

# Task Tags

- test
- infra

**Analysis model:** Opus 5 / **구현 권장:** Opus 5 (판정 권위가 로컬에 없는 e2e 스택이다 — dispatch 런으로만 닫힌다)

---

# Required Sections (must exist)

- Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios

---

# Goal

**`TASK-PC-FE-292` 가 빨갛게 만든 `main` nightly 를 되돌린다.** 콘솔 full-stack e2e 가 292 머지 커밋
`f95ef11a4` 부터 실패한다(직전 `15dbd9108` 초록 · 자동 이슈 #3867).

소유자 결정(2026-09-16): **① 픽스처를 실제 동작에 맞춘다** — 제품의 게이트는 그대로 두고, 픽스처가
운영과 다른 상태(assumed 토큰 없는 활성 테넌트 쿠키)를 만들던 것을 고친다. (기각: ② 게이트가 `'*'`
운영자를 통과시킨다 — 토큰 `tenant_id` 판정이 다시 생긴다.)

---

# 요지 — 실측

| # | 사실 | 출처 |
|---|---|---|
| 1 | 실패 칸 = `overview-consolidation.spec.ts:75` — ERP «마스터» 링크 **클릭** → `/erp/masters` → `heading 'ERP 마스터'` 5s 안에 못 찾음 | nightly 런 35097574407 |
| 2 | e2e 운영자 `e2e-super-admin` 은 `tenant_id='*'`; 픽스처는 `console_active_tenant=fan-platform` **쿠키만** 심는다 | `fixtures/seed.sql` · `fixtures/login.ts` Step 7 |
| 3 | 292 전: 도메인 호출이 base 토큰(`'*'`)으로 나가 화면이 렌더(ERP 백엔드가 스택에 없어 degraded 지만 제목은 뜸). 292 후: assumed 토큰이 없으니 섹션 게이트가 «테넌트를 먼저 선택하세요» | `widgets/domain-tenant-gate` |
| 4 | 🔴 **픽스처만 바꾸면 안 된다**: 이 compose 의 `auth-service` 에 `ADMIN_SERVICE_URL` 이 없어 기본값 `http://localhost:8084` 로 간다 ⇒ assume 교환의 배정 검사(fail-closed)가 **모든 테넌트에서** 거절 → `/api/tenant` 403 | `docker-compose.e2e.yml` auth-service env · `auth-service application.yml:148` · admin-service `server.port: 8085` |
| 5 | `fan-platform` 은 비-dev 마이그레이션(`account-service V0009`)이 심는 ACTIVE 테넌트 ⇒ `'*'` 운영자의 레지스트리 선택지에 **항상** 있다 | `V0009__create_tenants.sql` · `ConsoleRegistryUseCase` |
| 6 | 이 스택에서 `POST /api/tenant` 가 성공한 적은 **없다**(그것을 부르던 federation 스펙은 `TASK-PC-FE-248` 이 삭제, CI 에서 돈 적 없음) | `git log -S "api/tenant" -- tests/e2e` |

🔴 **왜 292 가 못 봤나**: «도메인 화면을 여는 e2e 스펙» 을 `goto('/erp…')` 문자열로 셌다. 이 스펙은
**클릭**으로 들어간다 — 모집단을 URL 리터럴로 고른 것이 틀렸다.

---

# Scope

## In Scope

- `projects/platform-console/docker-compose.e2e.yml`: `auth-service` 에 `ADMIN_SERVICE_URL: http://admin-service:8085`.
- `tests/e2e/fixtures/login.ts`: 로그인 뒤 쿠키를 심지 않고 **실제 `POST /api/tenant`** 로 테넌트를 고른다.
  실패하면 **상태·본문을 담아 던진다**(403 이 «권한 문제» 로 위장해 스펙 안에서 엉뚱하게 터지지 않게).
- 판정: 브랜치 dispatch nightly 의 콘솔 full-stack 잡 초록.

## Out of Scope

- 섹션 게이트 · 기본값 규칙(292) 변경 — 결정 ①.
- `iam-platform/docker-compose.e2e.yml` 의 같은 결손(`iam-platform/tasks/INDEX.md` 가 이미 기록) — 그 프로젝트 몫.

---

# Acceptance Criteria

## AC-0 — 전제

- [x] 실패가 292 에서 시작했음을 런으로 고정: `15dbd9108` 초록 · `f95ef11a4` 빨강, 실패 칸 1(`:75`).
      → nightly 런 35096744015(`15dbd9108`) success · 35097574407(`f95ef11a4`) failure, 콘솔 full-stack 잡의
      실패 칸 1 = `overview-consolidation.spec.ts:75`(`heading 'ERP 마스터'` not found, 5000ms). 다음 커밋
      `2563bba1f` 런도 failure.

## AC-1 — 고침

- [x] compose `auth-service` 에 `ADMIN_SERVICE_URL`. → `http://admin-service:8085`.
- [x] 픽스처가 `POST /api/tenant {tenant}` 로 고르고, 2xx 가 아니면 상태+본문으로 던진다. `activeTenant=null` 가지는 유지(고르지 않음).
      → `context.request.post`(컨텍스트 쿠키 공유 → storageState 에 assumed 토큰이 실린다) + 성공 시 `[e2e login] … → 200` 로그.
- [x] 쿠키 직접 주입 코드 제거(운영과 다른 상태를 만드는 유일한 자리).
- [x] 🔵 **착수 뒤 추가(1차 dispatch 가 드러냄)** — `operators-profile.spec.ts` 가 실제 스위치로 `finance` 를 고르고,
      `seed-finance.sql` 의 계좌·잔액 행 `tenant_id` 를 `'*'` → `'finance'`. 아래 AC-2 표 참조.

## AC-2 — 판정 (🔴 권위는 dispatch 런)

- [x] 브랜치 dispatch nightly: 콘솔 full-stack 잡 **success**, 스펙 2개 전부 통과. 런 id 기록.

      | 런 | 커밋 | 콘솔 잡 | 결과 |
      |---|---|---|---|
      | 35101604812 | `9a4515417` (compose + 픽스처) | failure | 7 통과 · 1 실패 — `overview-consolidation` **초록**, 대신 `operators-profile.spec.ts:39` finance 카드 `forbidden`(기대 `ok`) |
      | **35103541564** | `40db5f5b2` (+ finance 테넌트 선택 · 시드 행 `'finance'`) | **success** | **8 통과 · 0 실패** |

      🔴 1차 실패의 원인: 오버뷰의 finance 레그가 이제 **선택한 테넌트(`fan-platform`)의 assumed 토큰**으로 나간다.
      finance 는 `tenant_id` `'*'` 또는 `'finance'` 만 받고 행을 그 클레임으로 읽는데, 시드는 `'*'` 와일드카드
      base 토큰 전제였다 — 즉 이 스펙은 **테넌트를 고른 운영자가 절대 안 타는 경로**에서만 초록이었다.
      `finance` 테넌트는 비-dev 마이그레이션이 보장한다(account-service `V0017` ACTIVE · `V0019` finance 자기구독).
      `operators-admin-profile.spec.ts` 는 IAM 운영자 목록만 보므로 `fan-platform` 유지.
- [x] 🔴 그 런의 globalSetup 로그에서 `/api/tenant` 가 **200** 이었음을 확인(«스펙이 우연히 통과» 와 구별).
      → 두 런 모두 `[e2e login] POST /api/tenant {tenant: fan-platform} → 200`(globalSetup + 스펙 안 재로그인 1회). 이
      스택에서 `/api/tenant` 가 성공한 **첫 기록**이다(compose 수정 전엔 배정 검사가 `localhost:8084` 로 가 fail-closed).
      `operators-profile` 의 `finance` 스위치는 `expect(switched.ok())` 로 단언 — 통과.
- [ ] 머지 후 `main` 의 nightly 한 번 초록 확인. 🔴 #3867 이 **브랜치 dispatch 초록으로 닫힐 수 있다**(`TASK-MONO-692`) — 닫힘을 «main 초록» 의 증거로 읽지 않는다.
      ⚪ 머지 전이라 미측정 — close chore 에서 잰다. (2026-09-16 dispatch 2회 뒤에도 #3867 은 OPEN.)

## 🔴 티켓 본문과 달라진 것

1. **finance 시드 행의 테넌트를 바꿨다**(Scope 에 없었다). 1차 dispatch 가 드러낸 같은 결함 부류 — 하네스가
   와일드카드 base 토큰 전제로 만들어져 있었다 — 라서 이 티켓에서 닫았다. 제품 코드 변경 0.
2. **로컬 판정 없음** — Docker 가 없는 호스트라 e2e 스택을 못 띄웠다. 로컬에서 잰 것은 두 파일의 `tsc` rc=0 뿐.

---

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.7 (Default active tenant · Domain section gate)
- `TASK-PC-FE-292` — 게이트를 넣은 티켓(이 티켓이 그 회귀를 닫는다)
- `TASK-PC-FE-289` — 같은 픽스처의 직전 수리(트레이싱)
- `TASK-MONO-692` — 빨간 nightly 이슈가 아무 ref 의 초록으로 닫힘

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` § Assume-Tenant Exchange (소비만)

# Target App

- `projects/platform-console/apps/console-web` (e2e 하네스) · `projects/platform-console/docker-compose.e2e.yml`

# Edge Cases

| 상황 | 기대 |
|---|---|
| `/api/tenant` 403 (배정 검사 실패) | 픽스처가 상태+본문으로 즉시 실패 — globalSetup 에서 멈춘다 |
| 레지스트리 저하 503 | 같음 |
| `activeTenant=null` 호출자 | 고르지 않는다(현재 호출자 0) |

# Failure Scenarios

1. **픽스처만 고치고 compose 를 안 고친다** → 모든 선택이 403 → globalSetup 실패(전 스펙 빨강).
2. **실패를 삼킨다** → 게이트 화면이 뜬 채 스펙이 5s 타임아웃으로 터지고, 원인이 «요소 없음» 으로 위장.
3. **dispatch 초록이 #3867 을 닫은 것을 main 복구로 읽는다** → `main` 이 아직 빨간데 닫힌다.
