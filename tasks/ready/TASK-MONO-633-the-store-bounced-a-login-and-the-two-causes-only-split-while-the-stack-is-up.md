# Task ID

TASK-MONO-633

# Title

🔴 **스토어 로그인이 `account_type_mismatch` 로 튕겼다 — 원인 후보가 둘인데, 둘을 가르는 것이 코드가 아니라 런타임 상태다.** 하나는 「설계대로」이고 다른 하나는 워크스루가 약속한 것을 깨는 **순서 의존 결함**이다

# Status

ready

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

코드 독해로 **원인 후보를 둘로 좁혔다**(§ 실측). 그러나 **둘을 가르는 값은 코드에 없다** —
어느 credential 로 로그인했는가, 그리고 **그 브라우저에 IAM SSO 세션이 이미 있었는가**
는 런타임 상태이고, IdP 가 떠 있어야만 읽힌다.

이 티켓은 그 판정을 **기동 창 한 번**으로 끝낸다.

🔴 **재는 티켓이다. 고치지 않는다.** 결함으로 판정되면 별도 티켓으로 기안한다
(`TASK-MONO-632` 가 세운 관례).

---

## ⏳ 착수 게이트 — 소유자가 데모를 켤 때까지 시작하지 않는다

소유자 지시(2026-09-07): *"지금말고 킬 상황이 될때 진행"*. 인스턴스는
**r6i.2xlarge** 이고 `iam-kafka` healthy 까지 실측 **약 8~10분**이 든다 ⇒ 이 티켓만을
위해 켜지 말고, **다음에 데모가 켜지는 창에 얹어라.**

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

### H1 — 계정 축 (설계대로일 가능성)

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
  ③ 🔴 **소유자에게 「그때 어느 이메일을 넣었는가」를 묻는다.** 이 한 줄이 H1 을 즉시 가르고, 안 물으면 AC-2 가 census 로 부풀어 오른다.
- [ ] **AC-1 (H2 — 순서 축, 결정적)** — 한 기동 창 안에서 **교대로** 잰다. 판정은 URL 이 아니라 **`/api/auth/session` 의 `accountId`** 로 한다(🔴 `/login` 은 세션 없어도 200 이다 — `TASK-MONO-622` 가 이미 그 함정을 이름 붙였다).
  · **B**: 새 시크릿 창 → 스토어에서 `demo@demo.com` 로그인 → 세션 `accountId` **有** 를 기대
  · **A**: 새 시크릿 창 → **콘솔 먼저** `demo@demo.com` 로그인 → 같은 창에서 스토어 진입 → `account_type_mismatch` 를 기대
  · 🔴 **B, A, B, A 로 교대**하고 **인터리브 안에서만** 비교한다. 두 패스가 갈리면 **H2 참**. 안 갈리면 H2 는 기각이고 원인은 H1 뿐이다.
- [ ] **AC-2 (H1 — 계정 축)** — `credentials` · `accounts` · `account_roles` 를 **실제로 조회**해 § 실측의 시드 표와 대조한다. 🔴 판정 대상은 **소유자가 실제로 넣은 이메일의 행**이고(AC-0 ③), 표 전체 재확인은 그 다음이다. 시드에 없는 행이 있으면 그것이 곧 「런타임 ≠ 선언」의 실물이므로 반드시 적는다.
- [ ] **AC-3 (토큰)** — 거부가 재현된 그 로그인의 **실제 access token** 을 디코드해 `tenant_id` · `roles` · `sub` 를 적는다. 🔴 **판정은 선언이 아니라 토큰이다** — H1 이든 H2 든 예측하는 클레임 값이 서로 다르므로(H1: `sub` 가 `…ad04`/아티스트 · H2: `sub` 가 `…ad03` 이고 `tenant_id=iam`), 이 한 칸이 두 가설을 **독립적으로** 확인한다.
- [ ] **AC-4 (기안 — 고침 아님)** — 판정에 따라 갈린다.
  · **H2 참** ⇒ 🔴 데모 결함(문서 약속 위반)으로 **별 티켓 기안**. 그 티켓의 질문은 «SSO 재사용 시 클라이언트별로 테넌트를 다시 해석해야 하는가» 이고, 그것은 **인증 모델 변경이라 `HARDSTOP-09`** 다 ⇒ 기안은 **ADR PROPOSED** 로 가야 하며 여기서 고르지 않는다.
  · **H1 만 참** ⇒ 설계대로다. 남는 빈틈은 **문서**뿐 — 워크스루 § 0 에 «스토어에 쓸 수 있는 계정» 을 명시하는 별 티켓(경량). `TASK-PC-FE-275`(콘솔 로그인 화면이 데모 계정을 말하지 않는다)와 **같은 축**이므로 그 티켓에 얹을지 먼저 확인한다.
  · **둘 다 거짓** ⇒ 🔴 원인 후보가 소진됐다는 뜻이므로 **닫지 말고** AC-3 의 토큰 값을 들고 이 티켓을 다시 연다.
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

분석=Opus 5 / 구현 권장=**Sonnet** (기동 창 안의 조회 + 기록 — 판정 기준이 이 문서에 이미 다 적혀 있다). 🔴 단, **AC-4 가 H2 참으로 갈리면 그 다음은 `HARDSTOP-09` 라 Opus** 로 올라간다.
