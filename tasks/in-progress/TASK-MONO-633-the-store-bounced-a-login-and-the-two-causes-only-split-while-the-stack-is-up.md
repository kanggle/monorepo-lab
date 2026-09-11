# Task ID

TASK-MONO-633

# Title

🔴 **스토어 로그인이 `account_type_mismatch` 로 튕겼다.** 소유자 답변(`demo@demo.com`)이 후보 둘 중 하나를 죽였고, 남은 것은 워크스루가 약속한 것을 깨는 **순서 의존 결함**이다 — 라이브가 할 일은 「어느 쪽인가」가 아니라 **「그 하나를 확증하는 것」** 으로 바뀌었다

# Status

in-progress

# Owner

monorepo

# Task Tags

- demo
- live-verification
- measurement
- auth

---

# Goal

2026-09-07 UTC 기동 창(EC2 `i-0394b45b62cdd1fc6`, launch `07:10:49Z` → stop `07:27:37Z`)
에서 소유자가 `https://store.hubwang.com` 로그인을 시도했고 결과가
`/login?error=account_type_mismatch` 였다.

코드 독해로 원인 후보를 둘로 좁혔고(§ 실측), **소유자 답변이 그중 하나를 죽였다**
(§ 소유자 답변). 남은 하나(**H2 — SSO 순서 축**)는 **결함**이다.

이 티켓은 그 확증을 **기동 창 한 번**으로 끝낸다.

🔴 **AC-0 ③ 이 이미 닫혔다** — 다시 묻지 마라. 답은 `demo@demo.com` 이고, 그 한 줄이
§ 소유자 답변의 소거 논증을 성립시킨다.

🔴 **재는 티켓이다. 고치지 않는다.** 결함으로 판정되면 별도 티켓으로 기안한다
(`TASK-MONO-632` 가 세운 관례).

---

## ⏳ 착수 게이트 — 소유자가 데모를 켤 때까지 시작하지 않는다

소유자 지시(2026-09-07): *"지금말고 킬 상황이 될때 진행"*. 인스턴스는
**r6i.2xlarge** 이고 `iam-kafka` healthy 까지 실측 **약 8~10분**이 든다 ⇒ 이 티켓만을
위해 켜지 말고, **다음에 데모가 켜지는 창에 얹어라.**

---

# 소유자 답변 (2026-09-07) — AC-0 ③ 이 닫혔다. H1 이 죽고 남은 것은 하나다

소유자 답: **`demo@demo.com`** 이었다.

그러면 **H1 은 죽는다.** 그리고 그 자리에서 끝나지 않는다 — 이 답은
[`CredentialAuthenticationProvider.resolveCredential`](../../projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/CredentialAuthenticationProvider.java)
과 합쳐져 **소거 논증**이 된다.

**스토어에서 실제로 폼 로그인을 했다면**, `resolveCredential` 이 낼 수 있는 결과는 둘뿐이다:

| 갈래 | 조건 | 결과 |
|---|---|---|
| 스코프 히트 | `(ecommerce, demo@demo.com)` 행이 있다 | principal tenant = `ecommerce` = platform ⇒ seed `[CUSTOMER]` ⇒ **통과** |
| 스코프 미스 | 그 행이 없다 | 폴백 `findAllByEmail` 이 **2행 이상**(`fan-platform`·`iam`)을 보고 `matches.size() > 1` ⇒ **fail-closed `BadCredentialsException`** ⇒ **로그인 폼 에러**, 역할 가드에 **도달조차 못 한다** |

🔴🔴 **어느 쪽도 `account_type_mismatch` 를 낼 수 없다.** 그런데 그 배너가 나왔다
⇒ **스토어에서 폼 로그인이 일어나지 않았다** ⇒ **SSO 세션 재사용 = H2.**

## 그래서 남은 질문은 「어느 가설인가」가 아니다

**H2 는 결함이다** — 워크스루 § 0 의 *"하나의 자격증명으로 세 표면 전부"* 가 **순서 의존**이
된다. 라이브 창의 일은 후보를 고르는 것이 아니라 **이 하나를 확증하고, 세션을 심은 클라이언트가
콘솔(`iam`)인지 팬(`fan-platform`)인지 가르는 것**이다(둘 다 같은 배너를 낸다).

## 🔴 소거를 무너뜨릴 수 있는 단 하나 — 반드시 먼저 물어라

위 논증은 **폴백이 2행 이상을 본다**는 전제 위에 서 있다. 런타임 `credentials` 에
`demo@demo.com` 행이 **정확히 하나**뿐이고 그것이 `ecommerce` 가 아니라면(시드가 이 볼륨에
한 번도 안 돌았다는 뜻), 폴백이 그 한 행으로 **인증에 성공**하고 역할 가드가 튕긴다 —
**폼 로그인을 했는데도** 같은 배너가 나오는 유일한 경로다.

🔵 **판별은 라이브가 아니라 질문 한 줄**: **«스토어에서 IAM 로그인 폼에 비밀번호를 실제로
입력했는가»**
· **아니오(바로 튕겼다)** ⇒ **H2 확정**, AC-2 는 확인용으로 강등.
· **예(입력했다)** ⇒ H2 기각. 코드상 불가능한 조합이므로 **런타임 데이터가 시드와 다르다**는
뜻이고, **AC-2 가 주 검사로 승격**된다.

🔵 시드가 이 데모에 실제로 적용되는 경로인 것은 확인했다 — demo 스택의 iam 앱은
`projects/iam-platform/docker-compose.e2e.yml` 로 뜨고 그 파일이 `SPRING_PROFILES_ACTIVE: e2e`
를 박으며, `application-e2e.yml` 만이 `classpath:db/migration-dev` 를 flyway locations 에
넣는다. 🔴 **그래도 「적용 경로가 있다」는 「그 볼륨에 행이 있다」가 아니다** — 그 구분이 AC-2 다.

---

# 실측 (2026-09-07 UTC, 코드 + AWS + HTTP)

## 이 리다이렉트를 내는 자리는 하나뿐이다

[`auth-callbacks.ts:225-230`](../../projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth-callbacks.ts)
— `signInCallback` 이 `roles ∌ CUSTOMER` 일 때 그 문자열을 반환한다. 술어는
`roles.includes('CUSTOMER')` **하나뿐**이고 `account_type` 은 `ADR-MONO-032` D5 step 4 에서
이미 제거됐다. 🔴 **배너 문구(`operator 계정으로는 …`)는 술어가 아니다** — 고정 문구이며
왜 거부됐는지를 담지 않는다.

⇒ 이 페이지에 도달했다는 것은 **OIDC 왕복이 성공했다**는 뜻이다(프로필까지 받았다).
잰 것은 「로그인 실패」가 아니라 **「로그인 성공 + 역할 거부」** 다.

## `roles` 가 비는 경로가 둘이다

`roles` 의 출처는 저장된 `account_roles`, 없으면 seed 다
([`TenantClaimTokenCustomizer.java:786-822`](../../projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/TenantClaimTokenCustomizer.java)).
그리고 seed 는 `TASK-MONO-381` 이후 **주체 자신의 테넌트 == 클라이언트의 platform** 일
때만 발동한다(같은 파일 `seedFor`, 852-858행).

리포 전체에서 `INSERT INTO account_roles` 는 **팬 아티스트용 한 곳뿐**이다 ⇒ 데모의
consumer 계정은 `roles` 를 **전적으로 seed 에서만** 받는다.

### ~~H1 — 계정 축 (설계대로일 가능성)~~ → 🟢 **소거됨 (2026-09-07, § 소유자 답변)**

🔵 **아래는 지우지 않는다** — 이 표가 소거 논증의 **전제**(폴백이 몇 행을 보는가)를 공급하므로,
지우면 논증이 근거를 잃는다.

`credentials` 시드 전수:

| 이메일 | tenant_id | 스토어 로그인 예상 |
|---|---|---|
| `demo@demo.com` | `ecommerce` · `fan-platform` · `iam` (**3행**) | 스코프 조회가 `ecommerce` 행에 히트 → seed `[CUSTOMER]` → 통과 |
| `requester@demo.com` | **`iam` 1행** | cross-tenant 폴백이 인증 성공 → claim tenant `iam` ≠ platform `ecommerce` → **거부** |
| `lumi@` · `noah@` 등 아티스트 | **`fan-platform`** | 같은 이유로 **거부** |

근거: `auth-service/.../migration-dev/R__01_seed_demo_single_identity_credentials.sql:86-107`
· `R__seed_demo_second_operator_credential.sql:94-106` ·
`account-service/.../R__05_seed_demo_corp_tenant_and_consumer_accounts.sql:106-118` ·
[`CredentialAuthenticationProvider.java:54-60`](../../projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/CredentialAuthenticationProvider.java)
(한 테넌트에만 있는 이메일은 폴백이 **성공**시킨다 ⇒ 로그인은 되고 **그 다음** 가드에서 튕긴다).

H1 이 참이면 `ADR-MONO-035` 2026-07-13 개정이 적은 **cross-tenant 가드**가 의도대로
발화한 것이다 = **결함 아님**.

### H2 — SSO 순서 축 (결함일 가능성) 🔴🔴

`authorization_code` 경로에서 `tenant_id` 클레임은 **principal** 에서 읽고, `platform` 은
**클라이언트**에서 읽는다(같은 파일 348-361행). 그리고 principal 의 tenant 를 찍는
[`SavedRequestTenantResolver`](../../projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/security/SavedRequestTenantResolver.java)
는 **폼 로그인 시점에만** 돈다 — 저장된 `/oauth2/authorize` 요청의 `client_id` 를 읽어야
하기 때문이다.

⇒ 콘솔(`platform-console-web`, tenant `iam`)에 **먼저** 로그인한 브라우저로 스토어에
가면 `/oauth2/authorize` 가 **로그인 폼을 안 거치고**(SSO 세션 재사용) principal 의
login-time tenant 인 `iam` 이 그대로 클레임이 된다 ⇒ `seedFor('iam','ecommerce')` = `[]`
⇒ roles 없음 ⇒ **같은 배너**.

🔴 **H2 가 참이면 계정이 `demo@demo.com` 하나여도 발화한다** — 그러면 워크스루 § 0 의
*"하나의 자격증명으로 세 표면 전부"* 가 **순서 의존**이 되고, 이것은 문서가 약속한 것을
깨는 **데모 결함**이다.

🔵 **왜 지금까지 안 잡혔나 (가설)**: `TASK-MONO-632` 의 기동 창 #6 은 칸 0~3 을 전부
PASS 로 냈지만, 표면을 **각각** 찔렀지 «콘솔 로그인 → 스토어» 라는 **순서**를 재지 않았다.
🔴 이것은 추정이다 — AC-1 이 재기 전까지 632 의 결과를 반증으로 읽지 마라.

## 라이브가 왜 지금 불가능한지

```
curl -o /dev/null -w '%{http_code}' https://auth.hubwang.com/.well-known/openid-configuration
  → 503  ("데모 백엔드가 지금 꺼져 있습니다" — Vercel 포워더 폴백)
aws ec2 describe-instances --instance-ids i-0394b45b62cdd1fc6
  → State: stopped · Reason: "User initiated (2026-09-07 07:27:37 GMT)"
```

🔴 위 표는 **선언 파일(시드 마이그레이션)** 을 읽은 것이지 **런타임 행**이 아니다.
이 데모는 볼륨이 장수하므로 손으로 만든 계정이 있으면 표에 없다. 그 구분이 AC-2 다.

---

# Scope

## In Scope

- 기동 창 안에서 H1 · H2 를 **각각** 판정 (AC-1 · AC-2)
- 실제 발급 토큰의 `tenant_id` / `roles` 실측 (AC-3)
- 판정 결과에 따른 **기안**(고침이 아니라) (AC-4)

## Out of Scope

- 코드 수정 · 가드 변경 · seed 변경 — 결함 판정이 나오면 **별도 티켓**
- `docs/guides/interview-demo-walkthrough.md` 수정 — AC-4 의 산출물이 정하고, 그 자체는 별 티켓
- `ADR-MONO-035` 재논의 — cross-tenant 가드 결정은 ACCEPTED 이고 여기서 안 건드린다
- 이 티켓만을 위한 EC2 기동 (§ 착수 게이트)

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act)** — 착수 전에 셋을 확인하고, 하나라도 어긋나면 STOP 하고 이 티켓 본문부터 갱신한다.
  ① `curl -o /dev/null -w '%{http_code}' https://auth.hubwang.com/.well-known/openid-configuration` 이 **200** 인가(503 이면 스택이 아직 안 떴다 — 워크스루 § 7 기준 `iam-kafka` healthy 까지 약 8~10분).
  ② § 실측이 인용한 **네 파일이 그 사이 바뀌지 않았는가** (`auth-callbacks.ts` · `TenantClaimTokenCustomizer.java` · `SavedRequestTenantResolver.java` · 시드 3종). 바뀌었으면 인용 행번호부터 다시 잡는다 — 🔴 **행번호를 상속하지 마라.**
  ③ ✅ **닫혔다 (2026-09-07) — 답은 `demo@demo.com`.** 다시 묻지 마라. 이 답이 H1 을 죽였고 § 소유자 답변의 소거 논증을 성립시켰다.
  ④ 🔴 **아직 안 닫힌 질문이 하나 남았고, 그것도 라이브가 아니라 질문이다**: «스토어에서 IAM 로그인 폼에 **비밀번호를 실제로 입력했는가**». **아니오** ⇒ H2 확정(AC-2 는 확인용). **예** ⇒ H2 기각이고 **AC-2 가 주 검사로 승격**된다(§ 소유자 답변 마지막 절). 답이 없으면 두 갈래를 **둘 다** 열어 둔 채 창에 들어간다.
- [ ] **AC-1 (H2 — 순서 축, 결정적)** — 한 기동 창 안에서 **교대로** 잰다. 판정은 URL 이 아니라 **`/api/auth/session` 의 `accountId`** 로 한다(🔴 `/login` 은 세션 없어도 200 이다 — `TASK-MONO-622` 가 이미 그 함정을 이름 붙였다).
  · **B**: 새 시크릿 창 → 스토어에서 `demo@demo.com` 로그인 → 세션 `accountId` **有** 를 기대
  · **A**: 새 시크릿 창 → **콘솔 먼저** `demo@demo.com` 로그인 → 같은 창에서 스토어 진입 → `account_type_mismatch` 를 기대
  · **A′**(추가, 2026-09-07): 새 프로파일 → **팬 먼저** `demo@demo.com` 로그인 → 스토어 진입. 🔴 콘솔(`iam`)과 팬(`fan-platform`)은 **같은 배너를 낸다** — 소유자가 어느 쪽을 먼저 열었는지 모르므로 둘 다 재고, 가르는 것은 AC-3 의 `sub`/`tenant_id` 다.
  · 🔴 **B, A, B, A 로 교대**하고 **인터리브 안에서만** 비교한다. 두 패스가 갈리면 **H2 참**. 🔴 **안 갈리면 이제는 「H1 이다」가 아니라 「소거 논증이 틀렸다」** — § 소유자 답변이 H1 을 이미 죽였으므로, 그때 열어야 할 것은 AC-2 이지 H1 이 아니다.
- [ ] **AC-2 (런타임 ≠ 선언 검사)** — `credentials` · `accounts` · `account_roles` 를 **실제로 조회**해 § 실측의 시드 표와 대조한다. 🔴 **1순위 질문은 딱 하나**: `demo@demo.com` 행이 **몇 개이고 어느 테넌트인가**. **3행(`ecommerce`·`fan-platform`·`iam`)이면** 소거 논증의 전제가 서고 H2 가 남는다. **`ecommerce` 행이 없고 나머지가 1행뿐이면** 폼 로그인으로도 같은 배너가 나올 수 있어 **H2 를 확증할 수 없다**(§ 소유자 답변 마지막 절). 시드에 없는 행이 있으면 그것이 곧 「런타임 ≠ 선언」의 실물이므로 반드시 적는다.
- [ ] **AC-3 (토큰)** — 거부가 재현된 그 로그인의 **실제 access token** 을 디코드해 `tenant_id` · `roles` · `sub` 를 적는다. 🔴 **판정은 선언이 아니라 토큰이다.** 이제 이 칸이 가르는 것은 H1/H2 가 아니라 **세션을 심은 클라이언트**다: `sub`=`…ad03` + `tenant_id=iam` ⇒ 콘솔발 · `sub`=`…fa02` + `tenant_id=fan-platform` ⇒ 팬발. 🔴 `sub`=`…ec01`(`tenant_id=ecommerce`)이 나오면서도 거부됐다면 **가드가 아니라 seed 게이트를 다시 읽어라** — 그 조합은 현재 코드로 설명되지 않는다.
- [ ] **AC-4 (기안 — 고침 아님)** — 판정에 따라 갈린다.
  · **H2 참** ⇒ 🔴 데모 결함(문서 약속 위반)으로 **별 티켓 기안**. 그 티켓의 질문은 «SSO 재사용 시 클라이언트별로 테넌트를 다시 해석해야 하는가» 이고, 그것은 **인증 모델 변경이라 `HARDSTOP-09`** 다 ⇒ 기안은 **ADR PROPOSED** 로 가야 하며 여기서 고르지 않는다.
  · ~~**H1 만 참**~~ ⇒ 🟢 **2026-09-07 에 소거됐다**(§ 소유자 답변). 이 갈래는 더 이상 없다. 문서 축(워크스루 § 0 에 «스토어에 쓸 수 있는 계정» 명시)은 **H2 기안 안에 포함**시킨다 — `TASK-PC-FE-275`(콘솔 로그인 화면이 데모 계정을 말하지 않는다)와 같은 축이므로 얹을지 먼저 확인한다.
  · **H2 도 재현 안 됨** ⇒ 🔴 후보가 소진됐다는 뜻이므로 **닫지 말고** AC-2 의 행 수 + AC-3 의 토큰을 들고 이 티켓을 다시 연다. 🔴 **그때 의심할 것은 코드가 아니라 § 소유자 답변의 소거 논증**이다(전제 = 폴백이 2행 이상을 본다).
- [ ] **AC-5 (기록)** — 결과를 `tasks/INDEX.md` 행과 이 파일에 적을 때 **무엇으로 쟀는지**를 같이 적는다(세션 `accountId` · 토큰 클레임 · DB 행). 🔴 «통과했다/거부됐다» 만 적으면 다음 사람이 다시 못 잰다.

---

# Related Specs

- [`docs/adr/ADR-MONO-035-operator-auth-unification-model.md`](../../docs/adr/ADR-MONO-035-operator-auth-unification-model.md) — § Amendments 2026-07-13 (`TASK-MONO-381`): 이 가드는 **cross-tenant 가드이지 operator 가드가 아니다**. H1 의 근거.
- [`docs/adr/ADR-MONO-032-unified-identity-roles-model.md`](../../docs/adr/ADR-MONO-032-unified-identity-roles-model.md) — D5 step 4 (`account_type` 제거, `roles` 가 유일한 인가 축)
- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` — Consumer-role guard 절
- [`docs/guides/interview-demo-walkthrough.md`](../../docs/guides/interview-demo-walkthrough.md) § 0 — *"하나의 자격증명으로 세 표면 전부"* (H2 가 참이면 이 문장이 낡는다). 🔴 사람용 참조이지 소스오브트루스가 아니다.
- [`tasks/done/TASK-MONO-632-reachability-was-measured-the-features-were-not.md`](../done/TASK-MONO-632-reachability-was-measured-the-features-were-not.md) — 기동 창 #6. 이 티켓의 「안 잰 축」이 무엇인지의 기준선.

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `roles` · `tenant_id` · `sub`. **계약 변경 없음** (이 티켓은 재기만 한다).

---

# Edge Cases

- **콜드 JVM** — 기동 직후 첫 로그인은 토큰 교환이 타임아웃할 수 있다(워크스루 § 문제해결: `operator_exchange_timeout`). 🔴 그 실패는 **이 티켓의 배너와 다른 사건**이다. 에러 코드를 보고 갈라라.
- **SSO 세션 잔류** — AC-1 의 두 패스가 같은 브라우저 프로파일을 공유하면 **A 가 B 를 오염시킨다**. 매 패스 새 시크릿 창이 아니라 **매 패스 새 프로파일**이어야 한다.
- **`demo@demo.com` 이 3행이라는 사실이 H2 를 가릴 수 있다** — 스토어에서 폼 로그인을 새로 하면 `ecommerce` 행에 히트해 통과하므로, **폼을 거치는 순간 H2 는 관측되지 않는다**. AC-1 의 A 패스가 폼을 **안** 거치는지 확인해야 한다(콘솔 로그인 후 스토어 진입 시 IdP 화면이 스쳐 지나가면 그 패스는 무효).
- **다른 세션이 데모를 동시에 만지고 있을 수 있다** — 이 저장소는 동시 세션이 관측된 적이 있다. 기동 창을 잡기 전에 인스턴스 상태를 한 번 읽어라.

# Failure Scenarios

- **F1 — 배너 문구를 술어로 읽는다.** *"operator 계정으로는"* 은 고정 문구이고 술어는 `roles ∌ CUSTOMER` 다. 문구를 믿고 operator 만 조사하면 H2 를 통째로 놓친다. → AC-3 이 토큰으로 막는다.
- **F2 — 시드 표를 런타임으로 읽는다.** § 실측의 표는 **선언**이다. 장수 볼륨에 손으로 만든 행이 있으면 표 밖이다. → AC-2 가 실제 행을 조회한다.
- **F3 — 한 패스만 재고 결론 낸다.** A 만 재면 «스토어 로그인이 깨졌다», B 만 재면 «멀쩡하다» 가 나오고 **둘 다 틀린 일반화**다. → AC-1 이 교대를 강제한다.
- **F4 — 632 의 「PASS」를 H2 의 반증으로 쓴다.** 632 는 순서 축을 안 쟀다(이 티켓의 가설). 🔴 **안 잰 것은 초록이 아니다.** → AC-1 이 직접 잰다.
- **F5 — 판정 없이 고친다.** H1 이면 고칠 코드가 없다(설계대로). 먼저 고치면 ACCEPTED 결정을 되돌리게 된다. → § Out of Scope + AC-4 가 기안으로만 나가게 한다.
- **F6 — 이 티켓 때문에 인스턴스를 켠다.** 소유자가 «킬 상황에 진행» 이라고 지정했다. r6i.2xlarge 는 이 판정 하나의 값보다 비쌀 수 있다. → § 착수 게이트.

---

분석=Opus 5 / 구현 권장=**Sonnet** (기동 창 안의 조회 + 기록 — 판정 기준이 이 문서에 이미 다 적혀 있다). 🔴 단, **AC-4 가 H2 참으로 갈리면 그 다음은 `HARDSTOP-09` 라 Opus** 로 올라간다 — 그리고 2026-09-07 소거 이후 **그쪽이 기본 경로**다.

---

# 🟢🟢 수확 (2026-09-11 UTC · 데모 창) — **H2 참. 확증됐다.**

## AC-0 — 착수 게이트

| 칸 | 결과 |
|---|---|
| ① OIDC 디스커버리 200 | 🟢 `07:16:42Z` 부터 200. 🔴 다만 **그때 묶음은 전부 `booting`** 이었다 — 측정은 8/8 `ready`(`07:22:51Z`) 이후에 했다 |
| ② 인용 네 파일 불변 | 🟢 이 창에서 코드 변경 없음(측정 전용 창) |
| ③ 소유자 답변 | ✅ 이미 닫힘 (`demo@demo.com`) |
| ④ 스토어에서 **폼에 비밀번호를 실제로 입력했는가** | 🟢 **아래 A 패스가 이 질문을 실측으로 대체했다** — A 패스는 폼을 **안 거쳤고**(`sawIamForm=false`) 그래도 거부됐다 ⇒ H2 확정 갈래 |

## 🟢 AC-1 — H2 (순서 축). **B·A·B·A 교대 + A′, 다섯 패스 전부 일관**

🔴 매 패스 **새 브라우저 컨텍스트**(프로파일 공유 없음). 판정은 URL 이 아니라
**`/api/auth/session` 의 `accountId`**.

| 패스 | 진입 순서 | 최종 path | `accountId` | **IAM 폼을 거쳤나** | 토큰 |
|---|---|---|---|---|---|
| **B** | 스토어에서 바로 | `/` | **`…ec01`** | 예(정상) | — |
| **A** | 콘솔 → 스토어 | **`/login?error=account_type_mismatch`** | **null** | **아니오** | `console_access_token` `tenant_id=iam` `sub`=…`ad03` |
| **B** | 스토어에서 바로 | `/` | **`…ec01`** | 예 | — |
| **A** | 콘솔 → 스토어 | **`account_type_mismatch`** | **null** | **아니오** | `tenant_id=iam` `sub`=…`ad03` |
| **A′** | 팬 → 스토어 | **`account_type_mismatch`** | **null** | **아니오** | (복호 불가, 아래 ⚪) |

🔴🔴 **티켓이 건 유효성 조건이 충족됐다.** § Edge Cases 가 *"A 패스가 폼을 **안** 거치는지
확인해야 한다 — IdP 화면이 스쳐 지나가면 그 패스는 무효"* 라고 적었고, `sawIamForm=false`
가 그것을 단언한다. ⇒ **인터리브 안에서 B 와 A 가 갈린다 ⇒ H2 참.**

## 🟢 AC-3 — 토큰. 세션을 심은 클라이언트가 갈렸다

거부된 A 패스의 실제 토큰: **`sub`=…`ad03` · `tenant_id=iam`** ⇒ 티켓의 판별표대로
**콘솔발**이다(`…ad03`+`iam` = 콘솔). 🟢 `…ec01`(`ecommerce`)이 아니므로 *"가드가 아니라
seed 게이트를 다시 읽어라"* 갈래는 **발동하지 않는다.**

⚪ **A′(팬발)의 토큰은 못 읽었다.** 팬은 Auth.js 의 **암호화된 세션 쿠키**
(`__Secure-authjs.session-token`)를 쓰고 JWT 가 아니라 클레임을 복호할 수 없었다.
🔵 거부 자체는 재현됐고, `credentials` 테이블이 `fan-platform` 신원을 **`…fa02`** 로
확정하므로 티켓이 예상한 조합과 모순되지 않는다 — 다만 **토큰으로 확증한 것은 아니다.**
🔴 이 칸을 «쟀다» 로 적지 않는다.

## 🟢🟢 AC-2 — 런타임 행. 소거 논증의 전제가 섰고, **표 한 칸이 틀렸다**

`iam-mysql` 에서 직접 조회했다(🔵 IAM 은 postgres 가 아니라 **MySQL** 이다 —
`auth_db` · `account_db` · `admin_db`).

### 1순위 질문: `demo@demo.com` 이 몇 행이고 어느 테넌트인가 → **3행**

| `auth_db.credentials` | `tenant_id` | `account_id` |
|---|---|---|
| id 1 | **`ecommerce`** | `…ec01` |
| id 2 | **`fan-platform`** | `…fa02` |
| id 3 | **`iam`** | `…ad03` |

⇒ 🟢 **시드 «선언» 과 런타임이 정확히 일치한다**(손으로 만든 행 없음). 티켓이 *"3행이면
소거 논증의 전제가 서고 H2 가 남는다"* 라고 적은 조건이 **충족**됐다.
🔵 그리고 이 세 `account_id` 가 위 AC-1/AC-3 의 관측과 **정확히 맞물린다**:
B 패스의 `accountId`=`…ec01`, A 패스 토큰의 `sub`=`…ad03`.

### 🔴🔴 그러나 § 실측의 스코프 표는 **한 칸이 틀렸다**

표는 *"`(ecommerce, demo@demo.com)` 행이 있다 → principal tenant = ecommerce ⇒ seed
`[CUSTOMER]` ⇒ 통과"* 를 **데이터인 것처럼** 적는다. 런타임은 이렇다:

```
account_db.account_roles  전체 12행  =  fan-platform/ARTIST 6 + fan-platform/FAN 6
account_db.account_roles  demo@demo.com  =  0행
account_db.accounts       demo@demo.com  =  2행 (ecommerce, fan-platform) — iam 없음
account_db.accounts       id=…ad03       =  0행
```

🔴 **`demo@demo.com` 에 `account_roles` 행이 하나도 없는데 B 패스는 통과한다.**
⇒ `[CUSTOMER]` 부여는 **DB 행이 아니라 토큰 발급 시점의 코드 경로**다.
🔵 이것은 H2 를 흔들지 않는다 — 오히려 **가르는 것이 «스코프 조회가 어느 credentials 행에
히트하느냐» 뿐**임을 보여 주므로 H2 의 기전과 일치한다. 🔴 다만 **표를 그대로 두면
다음 사람이 `account_roles` 를 찾아보고 없어서 혼란한다.**

🔵 `iam` credential 이 가리키는 `…ad03` 은 `account_db.accounts` 에 없고 **`admin_db`**
(`admin_operators` · `admin_operator_roles` · `operator_tenant_assignment` …)에 산다 —
운영자와 소비자 계정을 가른 설계이고 **결함이 아니다.**

## 🔴 AC-4 — 기안 (고침 아님). **H2 참 ⇒ `HARDSTOP-09` 경로**

§ AC-4 가 *"H2 참 ⇒ 별 티켓 기안, 그 질문은 «SSO 재사용 시 클라이언트별로 테넌트를 다시
해석해야 하는가» 이고 인증 모델 변경이라 **`HARDSTOP-09`** ⇒ **ADR PROPOSED**"* 라고
적었다. 그 기안은 이 창의 산출물로 남긴다(별도 커밋).

🔵 문서 축(워크스루 § 0 의 *"하나의 자격증명으로 세 표면 전부"*)은 **지금 낡았다** —
콘솔이나 팬에 먼저 로그인한 방문자는 스토어에서 **튕긴다.** 그 정정은 기안 안에 포함한다.

## AC-5 — 무엇으로 쟀는지

- 세션: `/api/auth/session` 의 `accountId` (스토어 오리진에서 `fetch`)
- 토큰: 쿠키 `console_access_token` 의 JWT payload (`sub` 끝 4자 · `tenant_id`)
- DB: `iam-mysql` 에 SSM 으로 직접 질의(`auth_db.credentials` · `account_db.accounts` ·
  `account_db.account_roles`)
- 유효성: `sawIamForm` (A 패스가 IdP 폼을 거쳤는지)

## 🟢 AC-4 닫힘 — 기안했다 (`TASK-MONO-666`)

🔴 *"산문으로 「나중에」라고 쓰지 마라 — 큐가 아닌 곳에 적은 의무는 사라진다"* 가 이 AC 의
조항이다. 그래서 **창 안에서 큐에 실제 파일로** 넣었다:

> **`TASK-MONO-666`** — *"SSO 재사용이 «먼저 연 표면의 테넌트» 를 그대로 들고 가고,
> 스토어가 그것을 거부한다"* (`tasks/ready/`)

그 티켓이 지는 것:
- **AC-1** `ADR-MONO-072` 를 **PROPOSED** 로 기안(🔴 `HARDSTOP-09` 라 고르지 않는다).
  갈래 넷을 «무엇을 포기하는지» 와 함께 적게 했다 — ⓐ클라이언트별 테넌트 재해석 /
  ⓑ표면별 계정 분리 / ⓒ약속을 고친다 / ⓓ아무것도 안 한다.
- **AC-2** 워크스루 § 0 의 *"하나의 자격증명으로 세 표면 전부"* 정정 + § 6 한계 원장 행.
  🔵 `TASK-PC-FE-275` 와 같은 축인지 먼저 확인하도록 적었다(이 AC 가 지목한 그대로).
- **AC-3** 위 § AC-2 의 **스코프 표 정정**(`[CUSTOMER]` 가 `account_roles` 행이 아니라는 것).
- **AC-4** 가드를 만들지 말지의 **판단**과 근거(🔴 이 결함은 «두 표면의 순서» 라는 상태에서만
  나므로 단위 테스트로 못 물고, e2e 로 물면 `nightly-e2e.yml` 축이다).

🔵 **이 티켓 자신은 «재는 티켓» 이므로 여기서 끝난다** — 남은 열린 칸은 AC-3 의
⚪(A′ 토큰 복호 불가) 하나이고, 그것은 «못 쟀다 + 왜» 로 닫힌 형태다.
