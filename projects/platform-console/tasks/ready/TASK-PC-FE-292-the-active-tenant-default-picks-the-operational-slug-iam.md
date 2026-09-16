# Task ID

TASK-PC-FE-292

# Title

활성 테넌트 기본값이 **운영용 슬러그 `iam`** 을 고른다 — 그래서 로그인 직후 모든 도메인 화면이 «세션이 만료되었습니다» 로 튕긴다

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

**Analysis model:** Opus 5 / **구현 권장:** Opus 5 (인증·테넌트 경계다. 문구 한 줄이 아니다)

---

# Required Sections (must exist)

- Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios

---

# Goal

**로그인 직후 테넌트를 고르지 않아도 도메인 화면이 열린다.** 오늘은 로그인만 하고 이커머스
메뉴를 누르면 `/login?error=session_expired` 로 튕긴다 — 세션은 멀쩡한데 화면이 «만료» 라고
말한다.

---

# 🔴🔴 요지 — 데이터는 맞다. **토큰이 엉뚱한 테넌트를 말한다**

2026-09-16 라이브 데모(인스턴스 `i-027d6b39694585491`, 도메인 `16-184-17-212.sslip.io`)에서
SSM 읽기 전용 진단으로 실측한 사슬:

| # | 실측 | 출처 |
|---|---|---|
| 1 | 콘솔 OIDC 클라이언트의 `tenant_id` = **`iam`** | 라이브 `auth_db.oauth_clients` · `V0024__rename_gap_slug_to_iam.sql` 가 `gap`→`iam` 으로 UPDATE |
| 2 | `iam` 은 **고객 테넌트가 아니다** — 계정 서비스가 **일부러** 안 심는다 | 라이브 `account_db.tenants` 12행에 없음 · `V0019__create_tenant_domain_subscription.sql` 주석 *"gap 은 의도적으로 안 심는다"* |
| 3 | 그래서 로그인 토큰이 `tenant_id=iam` 으로 발급되고 **`entitled_domains` 가 생략**된다 | auth-service WARN 다수: *"entitled_domains lookup failed for tenant_id=iam, omitting claim (fail-soft)"* + account-service `404 TENANT_NOT_FOUND: iam` |
| 4 | 콘솔이 **그 값을 활성 테넌트 기본값으로 삼는다** — `'*'` 만 거르고 `iam` 은 통과 | `shared/lib/jwt.ts:59-62` `homeTenantFromAccessToken` · `api/auth/callback/route.ts:223-229` |
| 5 | 활성 테넌트 쿠키만 서고 **assumed 토큰은 없다** ⇒ 도메인 호출이 **기본 토큰**으로 나간다 | `shared/lib/session.ts:222` `getDomainFacingToken = assumed ?? access` |
| 6 | 도메인 게이트웨이가 그 토큰을 거절 → **401** → 콘솔이 «세션 만료» 로 번역 | Traefik 접근 로그: Vercel IP `3.239.72.25` → `GET /api/admin/users?page=0&size=20` **401** · `98.82.133.175`·`13.220.191.99` 도 동일 |

🔵 **DB 는 정상이다**: `demo-corp` 는 ecommerce 포함 5도메인 구독 ACTIVE, 운영자(`demo-operator`)의
홈 테넌트는 `demo-corp` 이고 `operator_tenant_assignment` 에 `demo-corp`·`ecommerce` 두 행이 있다.
고칠 것은 데이터가 아니라 **«활성 테넌트를 무엇으로 기본값 삼는가»** 다.

## 🟢 2026-09-16 라이브 확증 — 소유자 실측 (이 사슬의 반증 기회였고, 통과했다)

소유자가 라이브 데모에서 직접 쟀다:

> *"로그인하고 바로 이커머스 메뉴를 누르면 안 되고, 계정을 다른 것으로 전환했다가 다시
> 안 되던 것으로 전환하면 된다."*

🔵 **이것이 위 5번 칸(«assumed 토큰이 없다»)의 직접 증거다.** 전환은 어느 쪽으로 가든
`/api/tenant` 의 assume-tenant 교환을 태워 **그 테넌트로 스코프된 토큰**을 새로 발급받는다
(`api/tenant/route.ts:160`, `maxAge = assumed.expiresIn`). 그래서 «되돌아와도» 되는 것이다 —
고친 것은 «어느 테넌트인가» 가 아니라 **«assumed 토큰이 존재하는가»** 이기 때문이다.
🔴 로그인 직후 상태는 활성 테넌트 쿠키가 `iam` 으로 서 있고 assumed 토큰은 **없다** ⇒
`getDomainFacingToken` 이 기본 토큰(= `tenant_id=iam`)을 내보낸다.

🔵 **우회책(사용자용, 임시)**: 스위처에서 아무 테넌트로 한 번 전환한다. 🔴 이것을 «해결» 로
읽지 마라 — 매 로그인마다 사람이 해야 하고, 30분 유휴 뒤 갱신에서 같은 상태로 돌아간다.

🔴 **계약서가 이미 반대로 적고 있다**: `console-integration-contract.md:3370` — *"Tenant scope:
**never derived from the IAM OIDC token**. IAM resolves operator tenant scope producer-side from
`admin_operators.tenant_id`"*. 그런데 콜백은 정확히 그 토큰의 `tenant_id` 로 활성 테넌트를 정한다.

## 🔴 왜 지금까지 안 보였나

`TASK-MONO-648` 전수 촬영은 **스크립트가 테넌트를 명시적으로 선택**했고(`assumeTenant()`),
`TASK-MONO-676`/`TASK-MONO-674` 의 관측도 테넌트를 고른 뒤였다. **«고르지 않은 채 로그인만 한
운영자»** 라는 상태를 잰 적이 없다.

---

# Scope

## In Scope

- `homeTenantFromAccessToken`(`shared/lib/jwt.ts`) 이 **비-고객 슬러그**를 기본값으로 돌려주지 않는다.
  오늘 `'*'` 하나만 제외하는데, `iam`(= 콘솔 클라이언트의 운영용 슬러그)도 같은 부류다.
- 🔴 **같은 사실이 두 곳에 있다 — 둘 다 고친다**: 콜백(`api/auth/callback/route.ts`)과
  유휴 갱신(`shared/lib/session-refresh.ts:211-229`, `TASK-MONO-674` 가 추가한 홈 테넌트 재기본값).
  한쪽만 고치면 «로그인은 고쳐지고 30분 뒤 되돌아가는» 결함이 남는다.
- 기본값을 못 정할 때 화면이 **무엇을 하는가**를 정한다(아래 AC-1 의 갈래).
- 회귀 테스트: «토큰의 `tenant_id` 가 `iam`» 인 입력에서 활성 테넌트 쿠키가 서지 않는다 + 도메인
  화면이 «테넌트를 고르세요» 로 간다(또는 선택된 갈래의 동작).

## Out of Scope

- **IAM 쪽 발급 규칙 변경**(운영자 토큰에 홈 테넌트를 싣기). 소유자가 2026-09-16 선택창에서
  **콘솔 축**을 골랐다 — 그 갈래는 모든 소비자에게 영향을 주는 발급 규칙 변경이라 ADR 급이다.
- 게이트웨이가 테넌트 거절을 **401 로 보고하는 결함** → **`TASK-BE-595`**(별건, 같은 증상의
  나머지 절반). 이 티켓을 고쳐도 그 결함은 남고, 그 결함만 고치면 이 티켓은 «권한 없음» 으로
  표시만 바뀔 뿐 화면은 여전히 안 열린다.
- `V0024` 의 `gap`→`iam` 개명 자체(되돌리지 않는다).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [ ] 🔴 **라이브 값을 다시 읽어라.** 이 티켓은 «콘솔 클라이언트 tenant_id = `iam`» 과 «account_db
      에 `iam` 없음» 위에 서 있다. 그 둘이 바뀌었으면 이 티켓의 수치가 전부 낡은 것이다.
      읽는 법(데모 창에서, 읽기 전용): `iam-mysql` 컨테이너에
      `SELECT client_id, tenant_id FROM auth_db.oauth_clients WHERE client_id='platform-console-web';`
      와 `SELECT tenant_id FROM account_db.tenants ORDER BY 1;` — 2026-09-16 값은 각각
      **`iam`** 과 **`iam` 없음**(고객 테넌트 12건)이었다. 🔴 스크래치패드의 진단 스크립트를
      찾지 마라 — 그것은 세션과 함께 사라진다.
- [ ] 🔴 **«테넌트를 안 고른 로그인» 이 실제로 어느 화면까지 깨지는지 세라.** 오늘 실측된 것은
      ecommerce 뿐이다(사용자 보고 + 로그 1건). wms·scm·erp·finance 도 같은 리졸버(`getDomainFacingToken`)를
      쓰므로 **같이 깨질 것으로 예측**되지만 재지 않았다 — 예측을 실측으로 바꾸고 수를 적어라.

## AC-1 — 기본값을 못 정할 때 무엇을 하는가 (🔴 갈래를 고른 뒤 구현)

- [ ] 🔴 다음 중 하나를 고르고 **고른 이유와 버린 것**을 적는다.
      - **ⓐ 레지스트리의 배정 테넌트로 기본값** — 운영자가 한 테넌트만 배정받았으면 그것을 자동 선택.
        여럿이면 안 고른다. 🔵 계약서의 «테넌트 범위는 레지스트리가 정한다» 와 정렬된다.
        🔴 레지스트리 왕복이 콜백 경로에 하나 늘어난다.
      - **ⓑ 아무것도 기본값으로 두지 않는다** — `'*'` 운영자와 같은 취급. 화면은 기존 «상단
        스위처에서 테넌트를 선택한 뒤 다시 시도하세요» 게이트를 탄다(그 게이트는 이미 있다:
        `dashboards/overview` · `accounts` · `audit` · `operator-groups` …).
        🔴 **그러나 `/ecommerce`·`/wms` 등 도메인 섹션에 그 게이트가 있는지 확인하지 않았다** — 없으면
        그 화면들은 여전히 401 을 받는다(= 이 티켓이 안 고쳐진다). 세고 나서 고르라.
- [ ] 🔵 **비-고객 슬러그 목록을 어떻게 아는가**도 이 칸에서 정한다. `iam` 을 상수로 박으면
      다음 개명(`V0024` 가 이미 한 번 했다)에서 조용히 깨진다. 대안: «레지스트리가 주는 선택 가능
      테넌트에 없으면 기본값으로 쓰지 않는다»(값 목록이 아니라 **관계**로 판정).

### 🔵 2026-09-16 세션 마감 — 갈래 분석과 추천 (🔴 이것은 **추천이지 소유자 결정이 아니다**)

**이 티켓의 진짜 뿌리는 «깨진 불변식» 이다.** 콜백이 주석으로 적어 둔 전제 —
*"홈 테넌트로 기본값을 두면 assume 없이도 기본 토큰으로 동작한다"*
(`api/auth/callback/route.ts:214-222`) — 가 **이 클라이언트에서 거짓**이다.
콘솔 클라이언트의 토큰이 말하는 `tenant_id` 는 «어느 클라이언트로 로그인했나»(`iam`)이지
«어느 고객사를 운영하나»(`demo-corp`)가 아니다.

⇒ 근본 해결은 둘 중 하나다: **전제를 참으로 만들거나(B)**, **전제에 기대기를 그만두거나(A)**.

| | **A — 콘솔이 «항상 assume»** | **B — IAM 이 토큰에 홈 테넌트를 싣는다** |
|---|---|---|
| 하는 일 | 활성 테넌트를 레지스트리(권위)에서 정하고, 그 테넌트로 **실제 assumed 토큰을 발급받는다**. 도메인 호출은 항상 assumed 토큰 | `admin_operators.tenant_id`(=`demo-corp`)를 `tenant_id` 클레임으로 주고 `entitled_domains` 도 채운다 |
| 범위 | 콘솔 안 | **발급 규칙** — 그 클라이언트의 모든 소비자 |
| 계약 정합 | 🔵 3370행(«테넌트 범위는 IAM OIDC 토큰에서 유도하지 않는다»)과 정렬 | 🔴 «운영자 테넌트 범위는 생산자(admin-service)가 정한다» 와 충돌 여지 |
| 대가 | 로그인 경로에 레지스트리 왕복 + assume 왕복 1회씩 | **ADR 급**, 폭발 반경이 크다 |
| 소유자 축 | 🟢 2026-09-16 결정(«콘솔 축»)과 같다 | 🔴 그 결정에서 기각된 쪽 |

🔴 **AC-1 의 ⓑ(기본값 없이 선택 요구)만으로는 근본이 아니다** — 배정 테넌트가 하나뿐인
운영자도 **매 로그인마다 손으로 전환**해야 한다. 그것은 소유자가 2026-09-16 에 쓴 **우회책
그대로**이지 고침이 아니다.

🔵 **추천(착수 전 승인 필요)**: AC-1 을 **「ⓐ + 항상 assume」** 으로 좁힌다.

**그 추천안대로 갈 때의 구현 단계**

1. 활성 테넌트를 **토큰의 `tenant_id` 에서 유도하지 않는다**(`homeTenantFromAccessToken` 의 역할 축소/폐지).
2. 레지스트리의 배정 테넌트가 **하나면 즉시 assume**(`/api/tenant` 와 같은 교환으로 assumed 토큰 발급), **여럿이면** 기존 «상단 스위처에서 테넌트를 선택하세요» 게이트.
3. 도메인 호출은 **항상 assumed 토큰** — 이 클라이언트에서 기본 토큰 폴백은 의미가 없다
   (`getDomainFacingToken` 의 폴백이 «기본 토큰은 이미 홈 테넌트 스코프» 라는 전제 위에 있었다).
4. 유휴 갱신(`session-refresh.ts`)도 **같은 함수**를 쓴다(호출 지점 둘).
5. 🔵 AC-1 의 «비-고객 슬러그를 어떻게 아는가» 는 ⓐ에서 자동으로 풀린다 — 값 목록(`iam`)이 아니라
   **«레지스트리가 주는 선택지에 있는가»** 라는 관계로 판정하게 되기 때문이다.

**다음 세션 착수 순서**

1. **AC-0 재측정**(아래 조회법) — 전제가 살아 있는지부터.
2. **AC-1 을 소유자에게 묻는다** — 위 추천을 제시하되 🔴 **결정으로 적지 마라**.
3. 구현 + 회귀 테스트(AC-2·AC-3).
4. 자매 `TASK-BE-595`(401→403)도 같이 — 독립이지만 둘 다 고쳐야 사용자에게 정직한 화면이 된다.
5. **AC-4 는 데모 창에서만 난다.**

🔴 **창 제약**: 데모 인스턴스는 2026-09-16 에 **정지**했다(그 시점 예산 912/1200분 사용).
켜는 법 = 데모 론처 제어 API `POST /start`(주소는 `hubwang.com/config.js` 가 공개).
🔴 기동은 **요금이 걸린 동작**이라 소유자 승인이 필요하다.
🔴 **에이전트의 `aws ssm send-command` 는 이 호스트 분류기가 막았다**(2026-09-16, 소유자 명시
승인에도 차단 — 2026-09-11 엔 통과였다). 라이브 진단이 필요하면 **자립 실행형 `.ps1`**
(절대경로 · `--instance-ids` 박음 · 결과를 파일로도 저장)을 만들어 소유자에게 **한 줄 명령**으로 건네라.

## AC-2 — 고침

- [ ] 콜백과 유휴 갱신 **두 곳 모두**에서 같은 술어를 쓴다(함수 하나, 호출 두 곳).
- [ ] 활성 테넌트 쿠키가 서지 않는 경우에도 로그인 자체는 성공한다(운영자 토큰·세션 쿠키 불변).
- [ ] 테넌트를 고르면 기존과 동일하게 동작한다(assume 경로 무변경).

## AC-3 — 회귀를 막는다

- [ ] 🔴 «`tenant_id=iam` 토큰으로 로그인» 을 주는 단위 테스트를 **먼저** 쓰고 빨간 것을 확인한다.
- [ ] bite: 고친 뒤 되돌리면 그 칸이 빨개진다(되돌림 rc 를 기록).
- [ ] 🔵 기존 «홈 테넌트 기본값» 테스트(`TASK-PC-FE-036` 계열)가 여전히 초록인지 확인 —
      **고객 테넌트 토큰의 동작은 net-zero 여야 한다.**

## AC-4 — 라이브 판정 (🔴 창이 필요하다)

- [ ] 데모 창에서 **로그인만 하고 테넌트를 고르지 않은 채** `/ecommerce` 를 연다 → 화면이 열리거나
      (ⓐ) «테넌트를 고르세요» 가 뜬다(ⓑ). **`/login?error=session_expired` 로 가지 않는다.**
- [ ] 대조군: 테넌트를 고른 뒤 같은 화면 → 오늘과 동일하게 열린다.
- [ ] 창을 못 열면 이 칸을 ⚪ 로 두고 **갈 곳을 적어라**(`TASK-MONO-672`).

## AC-5 — 계약서 정합

- [ ] `console-integration-contract.md` 의 «홈 테넌트 기본값» 서술(§ 2.7 인근, 3384행의 갱신 경로
      포함)을 고친 동작과 **같은 말**로 맞춘다. 🔴 3370행(«토큰에서 유도하지 않는다»)과 지금 구현이
      어긋나 있었다는 사실 자체를 한 줄로 남긴다 — 다음 사람이 같은 곳에서 다시 헷갈리지 않도록.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` → 선언된 domain/trait.

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.2 · § 2.6 · § 2.7 (3370행 · 3384행)
- `ADR-MONO-020` D4 (assume-tenant 자격 재스코프) · `ADR-MONO-002` (`'*'` 플랫폼 센티널)
- `TASK-PC-FE-036` — 홈 테넌트 기본값을 도입한 티켓(이 티켓이 그 술어를 좁힌다)
- `TASK-MONO-674` — 유휴 갱신의 홈 테넌트 **재**기본값(두 번째 호출 지점)

# Related Skills

- `.claude/skills/INDEX.md` 참조

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md`
- `projects/iam-platform/specs/contracts/http/console-registry-api.md` (선택 가능 테넌트의 권위)

---

# Target App

- `projects/platform-console/apps/console-web`

---

# Implementation Notes

- 판정 지점은 **두 곳**: `src/app/api/auth/callback/route.ts:223` · `src/shared/lib/session-refresh.ts:221`.
  술어는 `src/shared/lib/jwt.ts:59` 하나로 모은다.
- `getDomainFacingToken`(`src/shared/lib/session.ts:222`)은 **건드리지 않는다** — 그 폴백은 설계다
  (고객 테넌트 토큰이면 기본 토큰이 이미 옳게 스코프돼 있다).
- 🔴 `'*'` 처리와 **같은 모양**으로 넣어라. 새 개념을 만들면 세 번째 슬러그가 생길 때 또 갈라진다.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 배정 테넌트가 **여럿**인 운영자 | 자동 선택하지 않는다(어느 것을 고를지는 사람의 결정) |
| 배정 테넌트가 **하나** | ⓐ면 그것으로 기본값, ⓑ면 여전히 선택 요구 |
| `'*'` 플랫폼 운영자 | 오늘과 동일(기본값 없음) |
| 레지스트리가 저하(degraded) | 기본값을 정하지 못해도 **로그인은 성공**해야 한다 |
| 30분 유휴 뒤 갱신 | 갱신 경로도 같은 술어를 쓴다(`TASK-MONO-674` 의 재기본값) |

# Failure Scenarios

1. **콜백만 고치고 갱신을 안 고친다** → 로그인 직후는 되고 30분 뒤 같은 증상이 돌아온다.
2. **`iam` 을 상수로 박는다** → 다음 슬러그 개명에서 조용히 깨진다(`V0024` 가 이미 한 번 했다).
3. **ⓑ 를 골랐는데 도메인 섹션에 «테넌트를 고르세요» 게이트가 없다** → 화면은 여전히 401 을 받고
   증상이 그대로다(AC-1 이 그것을 세라고 요구한다).
4. **`TASK-BE-595` 를 안 고친다** → 이 티켓이 닫혀도, 다른 이유로 테넌트가 거절될 때 여전히
   «세션 만료» 라는 거짓말이 뜬다.
