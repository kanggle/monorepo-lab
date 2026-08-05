# Task ID

TASK-BE-577

# Title

`email` scope 를 받은 액세스 토큰에 `email` 클레임이 없다 — 게이트웨이의 `X-User-Email` 배선이 전부 무음이고, 소비자 프로필의 이메일·이름이 영구히 빈다

# Status

ready

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

- [ ] **AC-0 (재측정)** — 착수 시 위 클레임 덤프를 다시 뜬다. 그리고 `email`/`profile` scope 를
      선언하는 **모든 클라이언트**를 전수로 세고, 각각의 토큰을 실제로 확인한다
      (이 티켓은 `ecommerce-web-store-client` 하나만 봤다 — 팬/콘솔은 확인 안 했다)
- [ ] **AC-1** — `email` scope 로 발급된 토큰에 `email` 클레임이 있다
- [ ] **AC-2** — `email` scope **없이** 발급된 토큰에는 없다
- [ ] **AC-3** — 그 토큰으로 처음 들어온 소비자의 ecommerce 프로필에 이메일이 들어간다
      (게이트웨이 → `X-User-Email` → `UserProfileProvisioner`, 배선은 이미 존재)
- [ ] **AC-4** — 계약 문서에 scope↔클레임 대응이 적힌다

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

- [ ] 구현 + 테스트
- [ ] 계약 문서 갱신
- [ ] Ready for review
