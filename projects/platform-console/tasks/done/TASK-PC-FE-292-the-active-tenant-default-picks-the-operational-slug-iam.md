# Task ID

TASK-PC-FE-292

# Title

활성 테넌트 기본값이 **운영용 슬러그 `iam`** 을 고른다 — 그래서 로그인 직후 모든 도메인 화면이 «세션이 만료되었습니다» 로 튕긴다

# Status

done

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
      - ⚪ **2026-09-16 착수 시 라이브는 못 읽었다** — 데모 인스턴스 정지. 소유자 결정(같은 날):
        **창은 구현 후 한 번만** 열어 이 칸과 AC-4 를 같은 창에서 잰다.
      - 🟢 **코드 측 전제는 착수 시 재확인했다**(`origin/main` `7b706d304`): `V0024__rename_gap_slug_to_iam.sql`
        UPDATE 그대로 · account-service `V0019` 의 «`gap` 은 의도적으로 안 심는다» 그대로 · 콜백
        `route.ts:223` 과 갱신 `session-refresh.ts:221` 둘 다 여전히 `'*'` 만 거름.
      - 🔵 **라이브 값이 바뀌었어도 고침은 무효가 되지 않는다** — 고친 술어는 토큰 `tenant_id` 를
        아예 입력으로 쓰지 않고 «레지스트리 선택지에 있는가» 로 판정하기 때문이다. 값이 바뀌었으면
        «왜 튕겼나» 의 **서사**만 낡는다.
- [x] 🔴 **«테넌트를 안 고른 로그인» 이 실제로 어느 화면까지 깨지는지 세라.** 오늘 실측된 것은
      ecommerce 뿐이다(사용자 보고 + 로그 1건). wms·scm·erp·finance 도 같은 리졸버(`getDomainFacingToken`)를
      쓰므로 **같이 깨질 것으로 예측**되지만 재지 않았다 — 예측을 실측으로 바꾸고 수를 적어라.
      - 🟢 **코드로 센 모집단**: `(console)` 아래 도메인 섹션 **6**(ecommerce·wms·scm·erp·finance·**ledger** —
        티켓이 몰랐던 여섯째) · `page.tsx` **46**(23·7·6·6·3·1), 전부 `getDomainFacingToken` 경유 클라이언트.
        그 중 «테넌트를 선택하세요» 게이트가 있는 섹션 **0**(그 문구가 있는 11곳은 전부 IAM·대시보드 화면).
        401 → `/login?error=session_expired` 리다이렉트 지점 약 30곳이 이 여섯 섹션과 그 상태 로더에 흩어져 있다.
      - ⚪ **«실제로 튕기는가» 의 라이브 실측은 ecommerce 1 뿐**(변함없음). 나머지 다섯은 **코드 경로가 같다**는
        데까지만 셌다 — AC-4 창에서 여섯 섹션을 같이 연다.

## AC-1 — 기본값을 못 정할 때 무엇을 하는가 (🔴 갈래를 고른 뒤 구현)

- [x] 🟢 **소유자 결정 2026-09-16: «마지막 선택 기억 + 단일 배정»** (아래 추천을 좁힌 것). 기록은
      아래 § «2026-09-16 착수 — AC-1 결정 기록».
- [x] 🔴 다음 중 하나를 고르고 **고른 이유와 버린 것**을 적는다.
      - **ⓐ 레지스트리의 배정 테넌트로 기본값** — 운영자가 한 테넌트만 배정받았으면 그것을 자동 선택.
        여럿이면 안 고른다. 🔵 계약서의 «테넌트 범위는 레지스트리가 정한다» 와 정렬된다.
        🔴 레지스트리 왕복이 콜백 경로에 하나 늘어난다.
      - **ⓑ 아무것도 기본값으로 두지 않는다** — `'*'` 운영자와 같은 취급. 화면은 기존 «상단
        스위처에서 테넌트를 선택한 뒤 다시 시도하세요» 게이트를 탄다(그 게이트는 이미 있다:
        `dashboards/overview` · `accounts` · `audit` · `operator-groups` …).
        🔴 **그러나 `/ecommerce`·`/wms` 등 도메인 섹션에 그 게이트가 있는지 확인하지 않았다** — 없으면
        그 화면들은 여전히 401 을 받는다(= 이 티켓이 안 고쳐진다). 세고 나서 고르라.
- [x] 🔵 **비-고객 슬러그 목록을 어떻게 아는가**도 이 칸에서 정한다. → **목록을 두지 않는다**: 토큰 `tenant_id` 는
      입력이 아니고, «레지스트리 선택지에 있는가» 라는 관계로만 판정한다(코드에 `'iam'` 리터럴 0건). `iam` 을 상수로 박으면
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

### 🟢 2026-09-16 착수 — AC-1 결정 기록

**착수 실측이 추천안(«ⓐ + 항상 assume»)을 그대로는 못 쓰게 만들었다** — 소유자에게 이 셋을 먼저 보였다:

1. 데모 운영자 `demo-operator` 의 선택 가능 테넌트는 **2개**다(레지스트리 = 배정 ∪ 홈 =
   `demo-corp`·`ecommerce` — `ConsoleRegistryUseCase.java:78` · `R__seed_demo_operator.sql` 4절).
   ⓐ 의 «하나면 자동, 여럿이면 게이트» 는 **이 사건의 운영자를 게이트로 보낸다.**
2. 그런데 도메인 섹션엔 게이트가 **0** 이다(AC-0 둘째 칸) ⇒ ⓐ 그대로면 여전히 401.
3. 홈 테넌트(`demo-corp`)를 자동으로 고르는 안은 ecommerce 목록이 **전부 0건**(`TASK-BE-576`: 데이터가
   `ecommerce` 테넌트에 있다)이고, 레지스트리 응답엔 «어느 것이 홈인가» 필드가 **없다**(IAM 계약 변경 필요).

| 갈래 | demo-operator 의 로그인 | 비용 | 판정 |
|---|---|---|---|
| **🟢 마지막 선택 기억 + 단일 배정** | 첫 로그인만 게이트에서 한 번 고름 → 이후 자동 | 콘솔 안 · IAM 계약 무변경 | **소유자 선택** |
| ⓐ 그대로(단일 배정만 자동) | 매 로그인 게이트 | 콘솔 안 | 기각 — 매번 손으로 고르는 것은 소유자가 쓴 우회책과 같은 부담 |
| 홈 테넌트 자동(레지스트리에 `homeTenant` 추가) | demo-corp 로 열리지만 ecommerce 0건 | iam-platform 계약·코드 | 기각 — 프로젝트 교차 + «열렸는데 비었다» 는 또 다른 거짓 화면 |

**모든 갈래 공통으로 결정된 것**: 토큰 `tenant_id` 로 정하지 않는다 · 고른 테넌트는 **반드시 assume** ·
assumed 토큰이 없으면 도메인 섹션이 «테넌트를 먼저 선택하세요» 를 보인다.

🔵 **홈 테넌트도 assume 이 통과한다**(그래서 «항상 assume» 이 고객 운영자를 깨뜨리지 않는다):
`OperatorAssignmentCheckUseCase` 의 판정 범위 = 배정 행 ∪ 홈 테넌트.

## AC-2 — 고침

- [x] 콜백과 유휴 갱신 **두 곳 모두**에서 같은 술어를 쓴다(함수 하나, 호출 두 곳).
      → `src/shared/lib/active-tenant-default.ts` `establishDefaultTenant` — 호출은 `api/auth/callback/route.ts`
      와 `shared/lib/session-refresh.ts`(유휴 갱신의 «살아남은 테넌트 없음» 가지) 둘. `homeTenantFromAccessToken`
      은 호출자 0 이 되어 **삭제**(되살려 쓰는 길을 닫았다).
- [x] 활성 테넌트 쿠키가 서지 않는 경우에도 로그인 자체는 성공한다(운영자 토큰·세션 쿠키 불변).
      → 레지스트리 저하 · assume 거절 · 선택지 여럿, 세 경우 모두 콜백 307 + 운영자 쿠키 세워짐을 단언.
- [x] 테넌트를 고르면 기존과 동일하게 동작한다(assume 경로 무변경).
      → `/api/tenant` 의 허용 검사·교환·원자적 설정·실패 처리 불변(`tenant-switch.test.ts` 초록). 더해진 것 둘:
      성공 시 `console_last_tenant=<sub>|<tenant>`(30일, HttpOnly, 자격 아님) 기록 · 비우기(`tenant=''`)가 그것도 지움.
- [x] 🔵 **추가(추천안 3단계의 구현 자리)** — 도메인 섹션 6곳에 `layout.tsx` + `DomainTenantGate`:
      assumed 토큰이 없으면 섹션을 렌더하지 않고 «테넌트를 먼저 선택하세요»(`data-testid="domain-no-tenant"`).
      샘플 방문자는 통과(ADR-MONO-074).

## AC-3 — 회귀를 막는다

- [x] 🔴 «`tenant_id=iam` 토큰으로 로그인» 을 주는 단위 테스트를 **먼저** 쓰고 빨간 것을 확인한다.
      → 고치기 전 트리에서, 기존 export 만 쓰는 임시 파일(콜백 · 유휴 갱신 · «테넌트 쿠키는 assumed 토큰 없이
      서지 않는다» 3칸)을 돌렸다: **rc=1, 3/3 빨강**, 단언 문구 `expected 'iam' to be undefined` ×2 ·
      `expected true to be false` — 라이브 결함이 단위에서 그대로 재현. 그 임시 파일은 커밋하지 않았다 —
      새 모듈을 import 하는 본 테스트는 고치기 전 트리에선 단언이 아니라 **수집 실패**로 빨개지므로 red-first 증거가 못 된다.
      본 테스트: `tests/unit/active-tenant-default.test.ts`(18칸) · `tests/unit/domain-tenant-gate.test.tsx`(13칸).
- [x] bite: 고친 뒤 되돌리면 그 칸이 빨개진다(되돌림 rc 를 기록).
      → 커밋된 트리에 변형을 넣고 `git checkout HEAD --` 로 복원(작업분은 커밋돼 있었다):

      | 변형 | rc | 빨강 |
      |---|---|---|
      | 호출 지점 둘을 옛 규칙(토큰 `tenant_id` − `'*'`, assume 없음)으로 되돌림 | 1 | 10 / 31 (콜백 8 + 갱신 2) |
      | 게이트 술어를 항상 «불필요» 로 | 1 | 3 / 31 |
      | `ecommerce/layout.tsx` 삭제 | 1 | 1 / 31 |
      | 복원 | 0 | 0 / 31 |
- [x] 🔵 기존 «홈 테넌트 기본값» 테스트(`TASK-PC-FE-036` 계열)가 여전히 초록인지 확인 —
      **고객 테넌트 토큰의 동작은 net-zero 여야 한다.**
      → 🔴 **기전은 net-zero 가 아니고, 결과는 net-zero 다** — 결정이 바꾼 것이지 «빨개서 고친» 것이 아니다.
      `jwt.test.ts` 의 `homeTenantFromAccessToken` 5칸은 함수와 함께 삭제. `auth-idle-refresh.test.ts` 의
      «홈 테넌트 되살림» 칸은 결과(`demo-corp`)를 유지하고 기전 단언만 바꿨다(`maxAge 1800` → 세션 쿠키 +
      assumed 토큰). «고객 테넌트 토큰 + 선택지 1 → 같은 테넌트(`acme-corp`)» 칸을 새로 뒀다.

**검증 실행(워크트리, 2026-09-16)**: `tsc --noEmit` rc=0 · `next lint`(변경 파일) rc=0 ·
`scripts/check-fetch-resolution.mjs` rc=0 · 인증·테넌트·샘플 가드 관련 13 파일 **160/160** rc=0
(`sample-fetch-allowlist` 포함 — `fetchRegistry` 의 `sampleGate(` 선행 유지). 전체 스위트 1회차는
**3229/3245, 16 실패**: 1은 위 `auth-idle-refresh` 칸(고침), 15는 화면 테스트 9파일의 `Test timed out in 5000ms`
계열 — 같은 9파일을 단독 재실행하면 **다른** 6칸이 실패, main 트리 대조군은 1칸 실패, `--minWorkers=1 --maxWorkers=2`
로 재실행하면 **149/149 rc=0**. 9파일 중 변경 모듈을 import 하는 파일 0. ⇒ 병렬 부하 타임아웃으로 판정
(같은 시각 `TASK-BE-595` Gradle 빌드가 호스트를 같이 썼다). 🔴 **전체 스위트를 한 번에 초록으로 돌린 적은 없다** — 권위는 PR CI.

🔵 **e2e 영향 확인**: `tests/e2e` 스펙 중 도메인 섹션 화면을 **여는** 것 0(`overview-consolidation` 은
사이드바 href 만 단언). 로그인 픽스처는 `console_active_tenant` 만 심고 assumed 토큰은 안 심지만, 그 쿠키로
여는 화면은 `/dashboards/overview`(게이트 밖, 동작 불변)뿐.

## AC-4 — 라이브 판정 (🔴 창이 필요하다)

- [ ] 데모 창에서 **로그인만 하고 테넌트를 고르지 않은 채** `/ecommerce` 를 연다 → 화면이 열리거나
      (ⓐ) «테넌트를 고르세요» 가 뜬다(ⓑ). **`/login?error=session_expired` 로 가지 않는다.**
      🔵 결정된 갈래에서의 기대: demo-operator 는 선택지 2 ⇒ **첫 로그인은 «테넌트를 먼저 선택하세요»**,
      스위처로 고른 뒤 로그아웃·재로그인 ⇒ **그 테넌트로 바로 열린다**(기억). 여섯 섹션 모두.
- [ ] 대조군: 테넌트를 고른 뒤 같은 화면 → 오늘과 동일하게 열린다.
- [ ] 창을 못 열면 이 칸을 ⚪ 로 두고 **갈 곳을 적어라**(`TASK-MONO-672`).
      ⚪ **2026-09-16 미측정** — 소유자 결정: 머지·배포 후 창을 **한 번** 열어 AC-0 첫 칸과 함께 잰다.
      결과는 close chore 의 `## CORRECTION` 에 싣는다(review/ 파일은 동결). 창을 못 열면 `TASK-MONO-672`.

## AC-5 — 계약서 정합

- [x] `console-integration-contract.md` 의 «홈 테넌트 기본값» 서술(§ 2.7 인근, 3384행의 갱신 경로
      포함)을 고친 동작과 **같은 말**로 맞춘다. 🔴 3370행(«토큰에서 유도하지 않는다»)과 지금 구현이
      어긋나 있었다는 사실 자체를 한 줄로 남긴다 — 다음 사람이 같은 곳에서 다시 헷갈리지 않도록.
      → § 2.6.1 갱신 경로 문장 교체 · § 2.7 «net-zero» 불릿을 **취소선 + 철회 사유**로 남김 · § 2.7 에
      «Default active tenant» 와 «Domain section gate» 두 불릿 추가(어긋남 한 줄 포함) · Switch/Clear 경로에
      `console_last_tenant` 반영.

## 🔴 티켓 본문과 달라진 것 (의도한 이탈)

1. **Implementation Notes «`getDomainFacingToken` 은 건드리지 않는다» 는 지켰고, 추천안 3단계
   «도메인 호출은 항상 assumed 토큰» 은 리졸버가 아니라 섹션 layout 게이트로 구현했다.** 둘이 정면으로
   어긋났기 때문이다. 리졸버의 폴백을 없애면 base 토큰을 심는 단위 테스트 약 100파일과 셸의 알림 벨
   (`notifications/inbox`, 모든 화면에 뜬다)까지 파급된다. 게이트는 «섹션이 폴백에 **닿지 않는다**» 를
   보장한다. 🔴 대가: 게이트 밖에서 `getDomainFacingToken` 을 부르는 곳(알림 벨 · 운영자 통합 개요 프록시)은
   테넌트가 없으면 여전히 base 토큰으로 나간다 — 개요 페이지는 원래 활성 테넌트 게이트가 있고, 벨은 401 을
   리다이렉트가 아니라 조용한 오류 상태로 받는다(`throwOnError: false`). 오늘과 같은 동작이다.
2. **섹션이 6개였다**(티켓은 다섯을 셌다) — `ledger` 추가.
3. **가이드 페이지도 게이트 뒤에 있다.** layout 은 섹션 안 클라이언트 이동 때 다시 렌더되지 않으므로,
   경로로 가이드만 빼면 «가이드 → 개요» 이동이 게이트를 우회한다. 일관성을 택했다.
4. **`console_last_tenant` 는 로그아웃이 지우지 않는다** — 자격이 아니고, 운영자 `sub` 로 키가 걸려 있어
   다른 운영자에게는 쓰이지 않으며, 읽을 때마다 레지스트리로 재검증한다.

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

## CORRECTION

**2026-09-16 UTC close chore — 4차원 검증 + 라이브 판정.** review 이동 때 ⚪ 였던 AC-0 첫 칸과 AC-4 가 데모 창
(2026-09-16 16:49Z 기동, 소유자 승인)에서 측정됐다.

### 머지 4차원
- (a) impl PR [#3862](https://github.com/kanggle/monorepo-lab/pull/3862) `state=MERGED` 2026-09-16T12:44:14Z, 머지 커밋 `f95ef11a4`.
- (b) `f95ef11a4` 는 `origin/main` 의 조상.
- (c) 머지 시점 롤업: SUCCESS 10 · SKIPPED 48 · **실패 0 · 진행 중 3**(`Build & Test` · 프런트 unit · 프런트 E2E smoke — 필수 아님).
  🔴 소유자가 CI 완료 전에 머지했다. 셋의 결과: Build & Test·E2E smoke success, 프런트 unit **failure** — 실패 칸은 ecommerce
  web-store `DemoBackendNotice.test.tsx` 1칸(이 PR 무관 파일, 직전 헤드 `b340ad086` 에선 통과)이고 console-web 단계는 그 실패로
  skipped 였다. 같은 런 재실행(35097495783)에서 **ecommerce·fan·console-web unit·typecheck·lint 전 단계 success**.
- 🔴🔴 **그리고 이 머지가 `main` nightly 를 빨갛게 했다**(콘솔 full-stack e2e `overview-consolidation.spec.ts:75`) — 이 티켓의
  «e2e 영향 없음» 판정은 **틀렸다**(도메인 화면을 여는 스펙을 `goto` 문자열로만 셌고, 그 스펙은 **클릭**으로 들어간다).
  `TASK-PC-FE-293`(#3871) 이 복구했고 push 런 35106378961 에서 초록.
- (d) AC 대조 — 아래.

### AC-0 첫 칸 — 라이브 값 (SSM 읽기 전용, command `b6fcdd6d-ee4b-4d93-932c-56ae8e04b8df`, 16:58:13Z, 소유자 실행)
| 조회 | 2026-09-16 기록 | 라이브 |
|---|---|---|
| `auth_db.oauth_clients` `platform-console-web` 의 `tenant_id` | `iam` | **`iam`** |
| `account_db.tenants` | 12건, `iam` 없음 | **12건 전부 ACTIVE, `iam` 없음** (acme-corp · demo-corp · ecommerce · erp · fan-platform · finance · globex-corp · initech-corp · ip-pilot-corp · scm · umbrella-corp · wms) |
| `demo-operator` 홈 / 배정 | demo-corp / demo-corp·ecommerce | **demo-corp / demo-corp·ecommerce** |

⇒ 전제가 그대로 살아 있다. 티켓 수치는 낡지 않았다.

### AC-4 — 라이브 판정 (`console.hubwang.com`, Vercel 배포 `729aa7eed` = 292·293 포함, 헤드리스 chromium, 공개 데모 계정)
| 단계 | 관측 |
|---|---|
| **1. 로그인만, 선택 안 함** | 착지 `/dashboards/overview` · base 토큰 `tenant_id` 클레임 **`iam`** · 활성 테넌트 없음 · assumed 토큰 없음 · 6 섹션(`/ecommerce` `/wms` `/scm` `/erp` `/finance` `/ledger`) **전부 게이트(`domain-no-tenant`) · 전부 `session_expired` 아님** |
| **2. 대조군 — `POST /api/tenant {ecommerce}`** | **200** · assumed 토큰 `tenant_id`=`ecommerce` · `console_last_tenant`=`<sub>\|ecommerce` · 6 섹션 **전부 게이트 없음**(`E-Commerce 개요` · `WMS 개요` · `SCM 개요` · `ERP 개요` · `Finance 개요` · `Finance Ledger 운영`) |
| **3. 새 브라우저 재로그인(기억 쿠키만 유지)** | 선택 없이 활성 테넌트 `ecommerce` · assumed 토큰 `tenant_id`=`ecommerce` · 6 섹션 **전부 바로 열림** |

- AC-4 첫 칸 ✅ — 기대(선택지 2 ⇒ 첫 로그인은 게이트)대로이고 `/login?error=session_expired` 0/6.
- AC-4 둘째 칸 ✅ — 고른 뒤 6/6 열림. 🔵 결정된 «기억» 동작도 6/6.
- AC-4 셋째 칸 — 창을 열었으므로 해당 없음.
- ⚪ **이 판정이 안 잰 것**: 섹션이 **렌더됐다**(게이트 통과·제목)까지다. 각 화면 안의 데이터 로드·인라인 권한 오류는 보지 않았다.
