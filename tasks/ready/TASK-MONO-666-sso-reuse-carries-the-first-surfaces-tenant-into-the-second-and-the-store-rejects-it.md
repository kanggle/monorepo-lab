# Task ID

TASK-MONO-666

# Title

🔴🔴 **SSO 재사용이 «먼저 연 표면의 테넌트» 를 그대로 들고 가고, 스토어가 그것을 거부한다** — 워크스루가 약속한 *"하나의 자격증명으로 세 표면 전부"* 가 순서에 따라 깨진다 (`TASK-MONO-633` 이 라이브에서 확증)

# Status

ready

# Owner

monorepo

# Task Tags

- auth
- demo
- adr

---

# Goal

`TASK-MONO-633` 이 **확증한 결함**을 받아 **결정으로 보낸다.**

🔴 **이 티켓은 고치지 않는다.** 질문이 *"SSO 재사용 시 클라이언트별로 테넌트를 다시
해석해야 하는가"* 이고, 그것은 **인증 모델 변경**이라 `HARDSTOP-09` 다
⇒ 산출물은 **ADR PROPOSED** 이고, 갈래를 고르는 것은 소유자다.

---

# Background — 633 이 무엇을 확증했나 (2026-09-11 UTC 데모 창)

## 증상

콘솔이나 팬에 **먼저** 로그인한 방문자가 같은 브라우저로 스토어에 들어가면
`/login?error=account_type_mismatch` 로 튕긴다. 스토어에서 **바로** 로그인하면 통과한다.

## 실측 — B·A·B·A 교대 + A′, 다섯 패스 전부 일관

| 패스 | 진입 순서 | 최종 path | `/api/auth/session` 의 `accountId` | IAM 폼을 거쳤나 | 토큰 |
|---|---|---|---|---|---|
| **B** | 스토어에서 바로 | `/` | **`…ec01`** | 예 | — |
| **A** | 콘솔 → 스토어 | **`account_type_mismatch`** | **null** | **아니오** | `tenant_id=iam` `sub`=…`ad03` |
| **B** | 스토어에서 바로 | `/` | **`…ec01`** | 예 | — |
| **A** | 콘솔 → 스토어 | **`account_type_mismatch`** | **null** | **아니오** | `tenant_id=iam` `sub`=…`ad03` |
| **A′** | 팬 → 스토어 | **`account_type_mismatch`** | **null** | **아니오** | (복호 불가) |

🔴 **`sawIamForm=false` 가 이 측정의 유효성 조건이다** — A 패스가 IdP 폼을 거쳤다면
스코프 조회가 `ecommerce` 행에 히트해 통과했을 것이고, 그러면 H2 를 **관측할 수 없다.**
633 § Edge Cases 가 그 조건을 미리 적어 뒀고, 실측이 그것을 만족했다.

## 기전 — 런타임 행으로 확인

`demo@demo.com` 은 `auth_db.credentials` 에 **3행**이다:

| `tenant_id` | `account_id` |
|---|---|
| `ecommerce` | `…ec01` ← B 패스가 히트하는 행 |
| `fan-platform` | `…fa02` |
| `iam` | `…ad03` ← A 패스 토큰의 `sub` |

⇒ SSO 세션이 이미 있으면 스토어는 **새로 스코프를 고르지 않고** 기존 세션의 테넌트
(`iam` 또는 `fan-platform`)를 들고 가고, 스토어의 consumer 가드가 그것을 거부한다.

🔵 **`ADR-MONO-035` 의 cross-tenant 가드는 «설계대로» 동작하고 있다.** 고장난 것은
가드가 아니라 **«한 자격증명으로 세 표면» 이라는 약속과 그 가드가 함께 성립하지 않는다**
는 점이다. 그래서 코드 수정이 아니라 **결정**이 필요하다.

## 🔴 부수 발견 — 633 의 스코프 표가 데이터를 잘못 가리킨다

633 § 실측의 표는 *"`(ecommerce, demo@demo.com)` 행이 있다 ⇒ seed `[CUSTOMER]`"* 를
**`account_roles` 의 데이터인 것처럼** 적는다. 런타임은 이렇다:

```
account_db.account_roles  전체 12행 = fan-platform/ARTIST 6 + fan-platform/FAN 6
account_db.account_roles  demo@demo.com = 0행
```

⇒ `[CUSTOMER]` 부여는 **DB 행이 아니라 토큰 발급 시점의 코드 경로**다. H2 를 흔들지는
않지만(오히려 «가르는 것이 credentials 스코프 조회뿐» 임을 보여 준다), **표를 그대로 두면
다음 사람이 `account_roles` 를 찾다가 혼란한다.** AC-3 이 그것을 고친다.

---

# Scope

## In Scope

- **ADR PROPOSED** 작성 (`docs/adr/ADR-MONO-072-…`) — 갈래를 **나열**하고 소유자가 고른다
- 워크스루 `§ 0` 의 *"하나의 자격증명으로 세 표면 전부"* 문장 정정
- 633 의 스코프 표 정정(위 부수 발견)

## Out of Scope

- 🔴 **코드 수정** — `HARDSTOP-09`. ADR 이 ACCEPTED 되기 전에는 아무것도 안 고친다
- `ADR-MONO-035` 의 cross-tenant 가드 자체 재논의 — 그 결정은 ACCEPTED 이고, 이 티켓은
  **그 가드와 데모 약속이 공존할 수 있는가**를 묻는다
- 데모 시드 변경 — 갈래 중 하나일 수 있으므로 ADR 이 고른 뒤에 한다

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [ ] 🔴 **633 의 실측이 아직 유효한지 확인한다.** 이 티켓은 2026-09-11 창의 관측 위에
      서 있다. 그 사이 `auth-callbacks.ts` · `TenantClaimTokenCustomizer.java` ·
      `SavedRequestTenantResolver.java` · 시드 3종이 바뀌었으면 **재현부터 다시 한다.**
      🔵 재현에 창이 필요하다 — 🔴 **이 티켓만을 위해 데모를 켜지 마라.**
- [ ] 🔵 창 없이 할 수 있는 것: 위 네 파일의 diff 확인. 안 바뀌었으면 **관측을 상속해도
      된다**(그 판단과 근거를 적어라).

## AC-1 — ADR PROPOSED 를 쓴다 (🔴 고르지 않는다)

- [ ] `docs/adr/ADR-MONO-072-…` 를 **PROPOSED** 로 기안한다.
- [ ] 🔴 **최소 세 갈래를 적고, 각각이 «무엇을 포기하는지» 를 적어라.** 후보:
      - **ⓐ 클라이언트별 테넌트 재해석** — SSO 세션이 있어도 진입한 클라이언트의 테넌트로
        스코프를 다시 고른다. 포기하는 것: 「한 번 로그인하면 끝」의 단순함, 그리고
        cross-tenant 가드가 막으려던 **일부 시나리오**
      - **ⓑ 데모 계정을 표면마다 분리** — 워크스루가 표면별 계정을 안내한다. 포기하는 것:
        *"하나의 자격증명으로 세 표면 전부"* 라는 **데모의 세일즈 포인트 그 자체**
      - **ⓒ 약속을 고친다** — 코드는 그대로 두고 워크스루가 *"스토어를 쓰려면 스토어에서
        먼저 로그인하라"* 고 말한다. 포기하는 것: 방문자가 순서를 **모르면 여전히 밟는다**
      - 🔵 **ⓓ 아무것도 안 한다** 도 갈래다 — 그러면 **그 판단과 이유를 적어야** 한다
- [ ] 🔴 **내 추천을 결정으로 적지 마라.** 추천은 추천이라고 표시한다.
- [ ] 🔴 **각 갈래의 반경**을 적어라 — ⓐ 는 인증 모델 변경(가장 큼), ⓑ 는 시드+문서,
      ⓒ 는 문서만.

## AC-2 — 워크스루 § 0 정정

- [ ] `docs/guides/interview-demo-walkthrough.md` § 0 의 *"하나의 자격증명으로 세 표면
      전부"* 는 **지금 거짓이다.** 🔴 ADR 이 고르기 전이라도 **거짓인 문장은 고친다** —
      정정의 내용은 «순서에 따라 달라진다» 는 사실이지 «어느 갈래를 택했다» 가 아니다.
- [ ] 🔵 `§ 6` 한계 원장에 **행을 더한다**(상태 이모지 + 이 티켓 인용). 🔴 § 6 의 규칙은
      「인용한 티켓이 `done/` 으로 가면 그 행도 고쳐라」이므로 닫을 때 함께 처리한다.
- [ ] 🔴 **`TASK-PC-FE-275`(콘솔 로그인 화면이 데모 계정을 말하지 않는다)와 같은 축인지
      먼저 확인**하고, 같으면 얹을지 판단한다(633 AC-4 가 지목).

## AC-3 — 633 의 스코프 표 정정

- [ ] 633 § 실측의 스코프 표에서 `[CUSTOMER]` 가 **`account_roles` 행이 아님**을 명시한다.
      🔵 633 이 아직 `in-progress` 면 본문에서 고치고, `done/` 이면 **여기 본문에 적는다**
      (frozen 을 고치지 않는다).

## AC-4 — 가드

- [ ] ⚪ **가드를 만들지 말지 판단하고 근거를 적어라.** 🔴 *"만들자"* 를 기본값으로 두지
      마라 — 이 결함은 **두 표면의 순서**라는 상태에서만 나오므로 단위 테스트로 못 문다.
      e2e 로 물려면 «콘솔 로그인 → 스토어 진입» 을 한 스펙에 넣어야 하고, 그것은
      `nightly-e2e.yml` 축이다(🔴 `ci.yml` 에 넣으면 도커·전체 스택이 필요해진다).
- [ ] 🔵 만들지 않기로 하면 **무엇이 이 회귀를 잡는가**를 적어라(아무것도 없으면 그 사실을).

---

# Related Specs

- [`docs/adr/ADR-MONO-035-operator-auth-unification-model.md`](../../docs/adr/ADR-MONO-035-operator-auth-unification-model.md) — cross-tenant 가드. **이 가드는 설계대로 동작한다**
- [`docs/adr/ADR-MONO-032-unified-identity-roles-model.md`](../../docs/adr/ADR-MONO-032-unified-identity-roles-model.md) — D5 step 4
- `projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth-callbacks.ts:227` — `/login?error=account_type_mismatch` 를 내는 자리
- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` — Consumer-role guard
- [`docs/guides/interview-demo-walkthrough.md`](../../docs/guides/interview-demo-walkthrough.md) § 0 · § 6 — 🔴 사람용 참조이지 소스오브트루스가 아니다
- `tasks/in-progress/TASK-MONO-633-…md` — **이 티켓의 근거.** 다섯 패스 실측 + 런타임 행

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `roles` · `tenant_id` · `sub`.
  🔴 **ⓐ 를 고르면 이 계약의 해석이 바뀐다**(같은 `sub` 가 클라이언트별로 다른 `tenant_id`
  를 갖는다) ⇒ ADR 이 그 영향을 적어야 한다.

---

# Edge Cases

- **재현이 안 된다** → 🔴 **닫지 말고** 633 의 다섯 패스 표를 들고 다시 연다. 그 표는
  `sawIamForm=false` 라는 유효성 조건까지 포함한 관측이다.
- **ADR 이 ⓒ(문서만)로 결정된다** → AC-2 가 그대로 산출물이 되고 코드 변경은 0 이다.
  🔵 그것도 결정이므로 **«아무것도 안 했다» 로 적지 않는다.**
- **`TASK-PC-FE-275` 와 합쳐진다** → 그러면 이 티켓의 AC-2 만 그쪽으로 가고 AC-1 은 남는다.

# Failure Scenarios

- **F1 — 고치고 싶어진다.** 🔴 `HARDSTOP-09`. 가드를 손대면 ACCEPTED 결정을 되돌리게 된다.
  → § Out of Scope + AC-1 이 기안으로만 나가게 한다.
- **F2 — 「데모 한정 문제」로 축소한다.** 🔴 기전은 데모 시드가 아니라 **SSO 세션 재사용**
  이다. 같은 신원이 여러 테넌트에 있는 배포라면 어디서나 난다.
- **F3 — 워크스루만 고치고 ADR 을 안 쓴다.** 그러면 **질문이 큐에서 사라진다**
  (`TASK-MONO-537` 이 그 모양으로 9일을 잃었다). → AC-1 이 ADR 을 강제한다.
- **F4 — 「추천 = 결정」으로 적는다.** 이 저장소가 반복해서 밟은 축. → AC-1 마지막 칸.

---

# Test Requirements

- 🔵 **이 티켓은 코드 변경이 없다** — 산출물은 ADR + 문서 정정이다.
- 🔴 테스트는 **ADR 이 ACCEPTED 된 뒤의 구현 티켓**이 진다. AC-4 가 그때 필요한 가드의
  **자리(nightly e2e)** 를 미리 지목해 둔다.

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Opus** — `HARDSTOP-09` 축이고 ADR 갈래의 반경 판단이 필요하다.
🔴 **AC-1 의 선택은 소유자**이고 대리 판단이 불가능하다.

---

# 🟢 AC-1 소유자 결정 (2026-09-11 UTC) — **ⓒ 약속을 고친다**

## 답 (소유자의 말 그대로)

> **「ⓒ 약속을 고친다」**

선택창으로 네 갈래(ⓐ 클라이언트별 테넌트 재해석 / ⓑ 표면별 계정 분리 / ⓒ 약속을 고친다 /
ⓓ 안 한다)를 각각이 **무엇을 포기하는지**와 함께 올렸고, 소유자가 **ⓒ** 를 골랐다.
🔵 내 추천도 ⓒ 였지만 **물어서 받았다** — `TASK-MONO-651` Failure 2 가 지목한 구분이다.

## 이 결정이 정하는 것

- **코드 변경 0.** `ADR-MONO-035` 의 cross-tenant 가드는 **그대로 둔다**(설계대로 동작한다).
- 산출물은 **문서**다: 워크스루 § 0 의 거짓 문장 정정 + § 6 한계 원장 행.
- `ADR-MONO-072` 는 **이 결정을 기록한 ADR** 이 된다 — 갈래 넷과 «무엇을 포기했는지» 를
  남겨야 다음 사람이 ⓐ 를 다시 제안할 때 **이미 검토됐다는 사실**을 안다.

## 🔴 이 결정이 «포기한 것» 을 명시한다 — 나중에 이것만 지워지면 안 된다

ⓒ 는 **증상을 없애지 않는다.** 순서를 모르는 방문자는 **여전히 밟는다.**
🔵 문서가 그 사실을 말하게 하는 것이 이 갈래의 전부이고, 그래서 § 6 한계 원장 행이
**선택 사항이 아니라 이 결정의 절반**이다.

🔵 ⓐ(클라이언트별 테넌트 재해석)가 «제품으로서 옳은» 답일 수 있다는 판단은 **버려지지
않는다** — ADR 에 갈래로 남고, 데모가 아니라 프로덕션 요구가 생기면 그때 다시 연다.

## ⇒ 남은 AC 와 그 집

- **AC-1** 🟢 이 절로 닫힘.
- **AC-2**(워크스루 § 0 + § 6) · **AC-3**(633 스코프 표 정정) · **AC-4**(가드 판단) 은
  **이 티켓이 그대로 진다** — ⓒ 를 골랐으므로 그 셋이 **구현의 전부**다.
- 🔴 `ADR-MONO-072` 작성은 AC-1 의 산출물이므로 **이 티켓 안에서** 한다.
