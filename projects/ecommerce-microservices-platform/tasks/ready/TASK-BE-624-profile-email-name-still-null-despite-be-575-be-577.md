# Task ID

TASK-BE-624

# Status

ready

# Title

IAM 가입 계정의 마이페이지 「기본 정보」가 여전히 빈칸 — `TASK-BE-575`/`TASK-BE-577` 이후에도 라이브에서 이메일·이름이 null

# Owner

backend

# Task Tags

- code
- bug
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 원인 후보가 좁다(배포 반영 여부 확인 중심).

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창(데모 기능 점검표, 흐름 8 — 프로필 수정).
- 선행 읍을 것: `TASK-BE-575`(user-service 프로필 지연 프로비저닝, done) ·
  `TASK-BE-577`(SAS 액세스 토큰에 `email` 클레임 발행, done — AC-3 가 **새 계정**으로
  이메일이 프로필까지 닿는 것을 확인했다). 이 티켓은 그 두 티켓의 **결과가 라이브에서
  재현되지 않는 것**을 다룬다 — 중복이 아니라 그 claim 에 대한 재검증이다.

---

# 배경 — 23차 창 라이브 실측 (2026-10-06 UTC)

스토어 `/my/profile` 「기본 정보」가 이메일/이름 빈칸으로 렌더됐다. API 확인:

```
GET /api/bff/api/users/me  →  {"email":null,"name":null, ...}
```

해당 계정은 이번 창 06:12:55Z 에 **프로필 프로비저닝 경로로** 생성됐다(`TASK-BE-575`가
만든 그 경로 — `UserProfileProvisioner`). PATCH 로 nickname/phone 은 정상 동작한다
(`TASK-BE-575`/`577` 범위 밖의 것).

## 왜 이것이 놀라운가 — `TASK-BE-577` 이 같은 것을 라이브로 확인했다고 적었다

`TASK-BE-577`(DONE)의 AC-3 은 "**새 계정**으로" 라는 조건으로 다음을 실측했다:

```
sub=8302433b-…  token-claim "email":"be577-…@example.com"
...
GET http://ecommerce.local/api/users/me  →  200
       {"userId":"8302433b-…","email":"be577-…@example.com", …}
after : 1 row, email=be577-…@example.com, tenant_id=ecommerce
```

즉 "email scope 로 발급된 토큰 → 게이트웨이 `X-User-Email` 주입 → `UserProfileProvisioner`
→ 프로필에 이메일" 경로가 **그 창(2026-08-06, 로컬 iam+ecommerce 슬라이스)에서는 작동을
확인했다.** 그런데 23차 창(배포된 데모, 2026-10-06)의 새 계정은 그 결과를 보여주지 않는다.

🔴 `name` 은 **원래부터 설계상 빈칸**이다(`TASK-BE-577` 이 명시: "name 은 여전히 빈다 —
소스도 소비자도 없다"). 이것은 결함이 아니라 알려진 한계다 — 이 티켓의 AC 는 `email`
에만 걸고, `name` 은 Edge Case 로만 적는다.

## 원인 후보 (착수 시 가려야 함 — 추측으로 적지 않는다)

1. 데모 배포가 `TASK-BE-577`(및/또는 `TASK-BE-575`)의 머지분을 **실제로 담은 이미지/
   설정으로 떠 있는지** 확인되지 않았다 — 23차 AMI/서비스 배포 시점과 그 PR 들의 머지
   시점을 대조할 것.
2. 게이트웨이의 `X-User-Email` 주입(`skipIfNull`)이 라이브 환경 설정(예: OAuth 클라이언트의
   `email` scope 승인 여부, 토큰에 실제로 `email` 클레임이 있는지)에서 끝났는지.
3. 이 창의 계정이 **IAM 가입** 경로였는지(소셜 로그인 가입이면 `TASK-BE-617` 계열의 전역
   소비자 계정 설계가 끼어들어 다른 프로비저닝 경로를 탔을 수 있다 — 마침 23차 창은 소셜
   로그인 키 배포(`TASK-MONO-763`)와 같은 창이다).

---

# Goal

데모에서 새로 가입한 IAM 신원의 ecommerce 프로필이 **이메일**을 갖는다(`TASK-BE-577` 이
로컬에서 확인한 것과 같은 결과가 라이브에서도 재현된다).

---

# Scope

## In Scope

- 착수 시 **재측정**부터 — 이 계정이 거친 정확한 가입 경로(이메일 가입 vs 소셜)와, 그
  토큰에 `email` 클레임이 실제로 있는지를 라이브에서 직접 확인한다.
- `TASK-BE-575`/`577` 의 머지분이 23차 창의 데모 배포에 실제로 포함돼 있는지 대조한다
  (배포/배선 문제라면 이 티켓이 아니라 재배포로 끝날 수 있다 — 그 경우도 **원인**으로
  기록하고 닫는다).
- 배선은 맞는데도 여전히 비면, `UserProfileProvisioner`/`GatewayIdentityConfig` 를 그
  계정의 실제 가입 경로(소셜이면 `TASK-BE-617` 계열) 기준으로 다시 추적한다.

## Out of Scope

- `name` 필드를 채우는 것 — 소스가 없다는 것이 이미 `TASK-BE-577` 의 기록된 결정이다.
  필요해지면 별도 티켓(그 티켓도 그렇게 적어 뒀다).
- 소셜 로그인 설계(`TASK-BE-617`) 자체의 변경 — 그 경로가 원인이라면 이 티켓은 그 경로가
  이메일을 어떻게 다루는지 **확인만** 하고, 설계 변경은 그 티켓의 몫이다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 이번 창의 그 계정(또는 새로 만든 테스트 계정)의 가입 경로·토큰
      클레임·프로필 행을 다시 확인한다.
- [ ] **AC-1** — 23차 창 데모 배포가 `TASK-BE-575`/`577` 의 머지 커밋을 포함하는지
      확인한다(배포판 바이너리/커밋 SHA 대조).
- [ ] **AC-2** — 원인을 하나로 좁힌다: ⓐ 배포가 그 수정을 안 담았다 ⓑ 토큰에 `email`
      클레임이 실제로 없다(어느 단계에서 빠지는지) ⓒ 가입 경로가 `TASK-BE-577` 이 확인한
      경로와 다르다(예: 소셜) ⓓ 그 외.
- [ ] **AC-3** — 원인이 ⓐ(배포 반영 문제)면 재배포만으로 해소되는지 확인하고 기록 — 코드
      변경 없이 닫을 수 있다.
- [ ] **AC-4** — 원인이 코드/배선 쪽(ⓑ·ⓒ)이면 수정하고, 새 계정으로 `email` 이 채워짐을
      라이브로 재확인한다.

---

# Related Specs

- `projects/ecommerce-microservices-platform/specs/services/user-service/`
- `projects/ecommerce-microservices-platform/apps/user-service/.../UserProfileProvisioner.java`
- `projects/ecommerce-microservices-platform/apps/gateway-service/.../GatewayIdentityConfig.java`

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/user-api.md`

---

# Edge Cases

- `name` 은 알려진 한계(소스 없음, `TASK-BE-577` 기록) — 이 티켓의 판정에 포함하지 않는다.
- 소셜 가입 계정은 제공자가 이메일을 안 줄 수도 있다(`TASK-BE-577` Edge Case) — 그 경우
  빈 이메일은 결함이 아니라 데이터 부재다. AC-2 가 가입 경로를 먼저 가리는 이유다.

# Failure Scenarios

- **원인을 확인하지 않고 코드를 고친다** — 배포 반영 문제(ⓐ)라면 코드 변경이 아무 효과가
  없고, 다음 재굽기까지 "고쳤다"는 거짓 보고가 된다.
- **소셜 가입 계정으로 재현을 시도해 "역시 안 된다"고 결론짓는다** — 소셜 경로는 이메일이
  제공자에 의존하므로(Edge Case), IAM 이메일 가입으로 먼저 확인해야 한다.
