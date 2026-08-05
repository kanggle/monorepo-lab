# Task ID

TASK-BE-577

# Title

`email` scope 를 받은 액세스 토큰에 `email` 클레임이 없다 — 게이트웨이의 `X-User-Email` 배선이 전부 무음이고, 소비자 프로필의 이메일·이름이 영구히 빈다

# Status

review

# Owner

backend

# Task Tags

- code
- contract
- test

---

# 배경 — `TASK-BE-575` 를 실측하다 나왔다

ecommerce 게이트웨이는 `X-User-Email` 을 주입하도록 배선돼 있다:

```java
JwtHeaderMapping.skipIfNull("X-User-Email", JwtClaims::email)
```

`skipIfNull` 이므로 **클레임이 없으면 헤더가 아예 없다.** 그리고 클레임이 없다.

## 실측 (2026-08-05, `ecommerce-web-store-client`, scope 에 `email` 포함)

```json
{"tenant_id":"ecommerce","sub":"4d960709-…","aud":"ecommerce-web-store-client",
 "entitled_domains":["ecommerce","wms"],
 "scope":["ecommerce.consumer","openid","profile","tenant.read","email"],
 "roles":["CUSTOMER"],"iss":"http://iam.local","tenant_type":"B2C_CONSUMER", …}
```

`email` scope 는 **승인돼 토큰에 실려 있는데**, `email` 클레임 자체가 없다. 즉 scope 는
동의만 기록했고 클레임 매핑이 없다.

## 결과

BE-575 이후 IAM 신원은 첫 요청에 ecommerce 프로필을 자동 생성한다. 그런데 이메일을 알 방법이
없으므로 프로필은 `email=null, name=null` 로 태어나고, **그 두 필드를 채울 API 가 없다**
(`PATCH /api/users/me` 는 nickname/phone/profileImageUrl 만 받는다). 브라우저 실측:

```
/my/profile  →  "기본 정보 / 이메일 (빈칸) / 이름 (빈칸)"
```

`account.created` 이벤트도 PII 마스킹(`emailHash`)이라 그 경로로도 못 채운다 — 설계상
"OIDC 토큰에서 나중에 채운다"(ADR-MONO-037 P1/P5) 인데, **그 토큰에 값이 없다.**
지금 데모에서 이메일이 보이는 유일한 프로필은 시드가 직접-DB 로 넣은 행 하나뿐이다.

---

# 🟢 착수 실측 (2026-08-06, 로컬 `iam` + ecommerce 최소 슬라이스)

## AC-0 — 모집단을 다시 셌다

`email`/`profile` scope 를 선언하는 클라이언트는 **6개**다(auth_db `oauth_clients` 전수):

| client | tenant | 변경 전 (email scope 요청) |
|---|---|---|
| `ecommerce-web-store-client` | ecommerce | 클레임 **없음** |
| `ecommerce-admin-dashboard-client` | ecommerce | 클레임 **없음** |
| `fan-platform-user-flow-client` | fan-platform | 클레임 **없음** |
| `demo-spa-client` | fan-platform | 클레임 **없음** |
| `platform-console-web` | iam | 클레임 **없음** |
| `wms-user-flow-client` | wms | **계측 불가** — 아래 |

🔴 **6번째는 "없음" 으로 세지 않았다.** `wms` 테넌트에 자격증명이 **0개**라
로그인 자체가 성립하지 않는다(`demo@demo.com` 은 3개 테넌트에 있어 클라이언트-스코프
조회가 빗나가면 fail-closed 로 모호성 거부). **토큰을 못 받은 것과 "받았는데 클레임이
없다" 는 다른 사실이다** — 계측 스크립트가 이 둘을 다른 값으로 출력하게 했다.

## 🔵 "왜 지금까지 없었는가" — 의도적 제외가 아니었다

티켓이 진짜 질문이라고 지목한 항목의 답:

1. **`platform/contracts/jwt-standard-claims.md` 는 `email` 을 처음부터 갖고 있었고,
   심지어 `Required: **Yes**` 였다.** 예시 토큰(Example 1)도 `email` 과
   `X-User-Email` 주입을 그려 두었다.
2. **ADR-MONO-037 P1 은 그 반대가 아니라 근거였다** — 선택된 안 A 의 이유가
   "PII 는 fan-out 이벤트가 아니라 **OIDC 토큰(정당한 동의 채널)** 으로 흐른다" 이고,
   `account.created` 를 emailHash 로 마스킹한 것이 바로 그 결정의 다른 쪽 절반이다.
3. 소비 측도 이미 다 배선돼 있었다 — 게이트웨이의 `skipIfNull("X-User-Email", …)`,
   `UserProfileProvisioner(userId, email)`.

⇒ **결정도 문서도 소비자도 전부 있었고, 발행만 없었다.** 그래서 이 변경에 새
ADR 은 필요 없다(HARDSTOP-09 미해당). 계약 준수이지 새 노출 결정이 아니다.

## 🔴🔴 가드가 못 본 이유는 성능이 아니라 **방향**이다

`scripts/check-jwt-claims-registry.sh` 는 **minted → registered** 한 방향만 본다.
그 헤더가 "The reverse direction (table → code)" 를 검사하지 않는다고 **스스로
적어 두었다**(SAS 프레임워크 기본 클레임 때문에 day-one RED 가 되어 꺼질 것이므로 —
합당한 이유다). **문서가 요구하고 코드가 발행하지 않는 클레임**은 정확히 그 사각지대에
있었다. 이 티켓이 고친 것은 코드이고, 표는 `Yes` → `Conditional` 로 정정했다
(워크로드 토큰에는 `Yes` 가 참인 적이 없었고, 요청 안 한 클라이언트에도 참이 아니다).

## AC-1 / AC-2 — 라이브 5/5, 양방향

auth-service 재배포 후 같은 스윕:

```
CLIENT                             TENANT        WITH-email  NO-email
ecommerce-web-store-client         ecommerce     present     absent
ecommerce-admin-dashboard-client   ecommerce     present     absent
fan-platform-user-flow-client      fan-platform  present     absent
demo-spa-client                    fan-platform  present     absent
platform-console-web               iam           present     absent
```

전체 클레임 덤프(변경 후, web-store):

```json
{"tenant_id":"ecommerce","sub":"0199de70-…-ec01","roles":["CUSTOMER"],
 "iss":"http://iam.local","tenant_type":"B2C_CONSUMER",
 "aud":"ecommerce-web-store-client","entitled_domains":["ecommerce","wms"],
 "scope":["ecommerce.consumer","openid","profile","tenant.read","email"],
 "email":"demo@demo.com"}
```

기존 클레임(`tenant_id`/`sub`/`roles`/`entitled_domains`)은 그대로 — net-zero.

## AC-3 — 클레임이 프로필까지 닿는다

**새 계정**으로 했다(기존 계정은 프로필 행이 이미 있어 프로비저닝이 no-op 이고,
"원래 있던 값" 과 "이번에 들어간 값" 을 구별할 수 없다).

```
sub=8302433b-…  token-claim "email":"be577-…@example.com"
before: user_profiles rows for sub = 0
GET http://ecommerce.local/api/users/me  →  200
       {"userId":"8302433b-…","email":"be577-…@example.com", …}
after : 1 row, email=be577-…@example.com, tenant_id=ecommerce
```

🔵 **음성 대조는 변경 전 베이스라인이 이미 제공했다** — 착수 시점
`user_profiles` 5행 중 **pull-through 로 태어난 4행이 전부 빈 email**, 이메일을 가진
유일한 행은 시드가 직접-DB 로 넣은 것이었다. 같은 경로가 클레임 없이는 빈 값을,
클레임과 함께는 값을 낳는다.

🔴 `name` 은 여전히 빈다 — **소스도 소비자도 없다**: `credentials` 에 표시
이름 컬럼이 없고, 어느 게이트웨이도 `X-User-Name` 을 매핑하지 않는다. Scope 의
"`profile` scope 의 이름 클레임" 은 **의도적으로 넣지 않았고** 그 이유를 계약에 적었다.

## 결정 기록

- **scope 게이트** — 동의가 이 채널을 정당하게 만드는 근거이므로, 무조건 발행하면
  동의만 사라지고 PII 만 남는다(AC-2 가 이것을 잡는다).
- **빈 문자열 대신 생략** — 소비 측이 `skipIfNull` 이라 빈 값은 "헤더 있음 + 값
  없음" 으로 전달돼 **빈 이메일 프로필**을 만든다. 생략보다 나쁘다.
- **principal name 이 아니라 `details` 맵에서 읽는다** — 둘은 오늘 같은 문자열이지만
  principal name 은 컴파일 에러 없이 바뀔 수 있는 관례다. 그러면 **다른 식별자가
  `email` 클레임으로 조용히 발행된다**. `PrincipalDetailKeys` 가 존재하는 이유가
  정확히 그것이라 새 키를 거기에 뒀다(양쪽 producer 모두 발행 — 폼 로그인과 소셜
  로그인이 같은 계정에 다른 토큰을 주면 안 된다).
- **id_token 에도 실린다(의도)** — Out of Scope 의 "id_token 경로(별도 판단)" 에
  대한 판단: **제외하는 쪽이 추가 조건을 필요로 한다.** `email` scope 를 요청한
  클라이언트에게 OIDC Core 가 정의한 바로 그 자리에서 빼는 것이 오히려 놀라운 동작이다.
- **assume-tenant 에는 싣지 않는다** — 그 토큰의 질문은 "어느 테넌트로 행위하는가"
  이고, base 토큰이 이미 갖고 있으며, 읽는 소비자가 없다.

## 🔴 옆에 남아 있던 거짓 문장 2곳을 함께 고쳤다

`UserProfileProvisioner` javadoc 과 ecommerce `user-api.md` 가 "SAS 액세스 토큰은
`email` 클레임을 싣지 않는다(실측)" 을 **사실로 적어 두고 있었다.** 방금 그것을
뒤집었으므로 그대로 두면 저장소가 거짓을 말한다(코드 동작 변경은 없음 — 주석/문서).

## 남은 것 / 후속

- `wms` 테넌트 자격증명 부재로 `wms-user-flow-client` 는 **미측정** — 결함이 아니라
  데모에 그 테넌트 사용자가 없다. wms 사용자 플로우를 실제로 쓰게 되면 그때 측정.
- `profile` scope → 이름 클레임은 소스(자격증명 저장소의 표시 이름)부터 없다.
  필요해지면 별도 티켓.

---

# Goal

`email` scope 로 발급된 액세스 토큰이 `email` 클레임을 싣는다. 그 결과 소비자 프로필이
자기 이메일을 갖는다.

---

# Scope

## In Scope

- SAS 토큰 커스터마이저에 `email`(그리고 `profile` scope 의 이름 클레임) 매핑 추가
- `openid`/`profile`/`email` scope 와 클레임의 대응을 계약 문서에 명시
- 테스트: scope 가 있을 때 클레임이 있고, **없을 때는 없다**(과다 노출 방지)

## Out of Scope

- ecommerce 쪽 변경 — 게이트웨이 매핑과 프로비저닝은 이미 값을 받을 준비가 돼 있다
- id_token 경로 (별도 판단)

---

# 유의점

- **PII 최소화와 정면으로 만난다.** `account.created` 를 emailHash 로 마스킹한 것은 의도된
  결정이다(ADR-MONO-037). 액세스 토큰은 그와 다른 매체이고 scope 로 동의를 받지만,
  토큰이 로그·트레이스에 남는 경로가 있는지 확인하고 결정을 ADR 에 남길 것
- `roles` / `entitled_domains` 를 넣는 커스터마이저가 이미 있다 — 새 기전이 아니라 그 자리에
  한 줄이다. **그러므로 "왜 지금까지 없었는가" 가 진짜 질문이다**(의도적 제외였는지 확인)

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 착수 시 위 클레임 덤프를 다시 뜬다. 그리고 `email`/`profile` scope 를
      선언하는 **모든 클라이언트**를 전수로 세고, 각각의 토큰을 실제로 확인한다
      (이 티켓은 `ecommerce-web-store-client` 하나만 봤다 — 팬/콘솔은 확인 안 했다)
- [x] **AC-1** — `email` scope 로 발급된 토큰에 `email` 클레임이 있다
- [x] **AC-2** — `email` scope **없이** 발급된 토큰에는 없다
- [x] **AC-3** — 그 토큰으로 처음 들어온 소비자의 ecommerce 프로필에 이메일이 들어간다
      (게이트웨이 → `X-User-Email` → `UserProfileProvisioner`, 배선은 이미 존재)
- [x] **AC-4** — 계약 문서에 scope↔클레임 대응이 적힌다

---

# Related Specs

- `projects/iam-platform/specs/contracts/http/` (토큰 발급)
- `docs/adr/ADR-MONO-037-*` — PII 마스킹 결정
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../GatewayIdentityConfig.java`
- `projects/ecommerce-microservices-platform/apps/user-service/.../UserProfileProvisioner.java`

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md` (대비되는 마스킹 결정)

---

# Edge Cases

- 소셜 로그인 계정에 이메일이 없다(제공자가 안 준 경우)
- 이메일 변경 후 기존 토큰 — 클레임은 발급 시점 값이다
- 같은 이메일이 여러 테넌트에 존재 — 프로필은 `user_id` 로 갈리므로 무관하지만 확인할 것

# Failure Scenarios

- **클레임만 넣고 프로필 반영을 확인하지 않는다** — 게이트웨이가 `skipIfNull` 이라 값이
  빈 문자열이면 헤더는 나가지만 프로필은 최소 프로필로 내려앉는다. AC-3 이 그것을 잡는다

# Test Requirements

- 토큰 클레임 단위/통합 테스트 (있을 때 / 없을 때)
- 프로필 반영 확인(AC-3)

# Definition of Done

- [x] 구현 + 테스트
- [x] 계약 문서 갱신
- [x] Ready for review
