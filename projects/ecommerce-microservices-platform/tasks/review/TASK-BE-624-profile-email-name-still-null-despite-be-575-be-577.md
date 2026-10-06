# Task ID

TASK-BE-624

# Status

review

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

# 🟢 결과 (2026-10-06 UTC) — 원인은 세 후보 중 하나가 아니라 **ⓓ: BE-575 · BE-577 ·
TASK-MONO-511 세 개의 개별로는 옳은 수정이 만든 레이스 컨디션**

## 작업 조건 — 이 창에서는 라이브 재측정이 안 된다

이 워크트리에는 데모 스택(IAM/ecommerce 배포, DB, 게이트웨이)에 대한 접근이 없다. 그래서
AC-0/AC-1 은 **코드/티켓 기록으로 재구성**했고, 추측이 아니라 각 파일의 file:line 증거로
좁혔다.

## ⓐ(배포 미반영) 배제 — AC-1

과제 지시가 이미 전제를 준다: "AMI built from `d44dd0d61`, which includes BE-575/577".
저장소 쪽에서도 `TASK-BE-575`
(`projects/ecommerce-microservices-platform/tasks/done/TASK-BE-575-*.md`)와 `TASK-BE-577`
(`projects/iam-platform/tasks/done/TASK-BE-577-*.md`, **iam-platform 소유** — 이 티켓이
`TASK-BE-577` 을 ecommerce 쪽 선행으로 적었지만 실제로는 iam-platform 프로젝트의 완료
티켓이다) 둘 다 `done` 이고 이 워크트리의 베이스(`main`)에 머지돼 있다. ⇒ **ⓐ 배제.**

## ⓑ(토큰에 email 클레임 없음) 배제 — BE-577 AC-0~AC-3 가 이미 5개 클라이언트에서 양방향
확인

`TASK-BE-577`(iam-platform, done)이 `ecommerce-web-store-client` 를 포함한 `email` scope
선언 클라이언트 5개 전수에서 "scope 있으면 클레임 있고, 없으면 없다"를 라이브로 확인했고
(`projects/iam-platform/tasks/done/TASK-BE-577-access-token-carries-no-email-claim.md` AC-1/
AC-2), 게이트웨이 쪽 소비도 코드로 확인된다(아래). 과제 설명이 준 조건도 "IAM-email-signup"
(소셜 아님)이므로 scope 동의 누락 가능성도 낮다. ⇒ **ⓑ 배제 후보로 낮춤**(라이브 토큰
덤프로 100% 배제하진 못했지만, 아래 ⓓ 가 코드로 직접 재현되는 더 강한 설명이다).

## ⓒ(가입 경로가 다르다·소셜) 배제 — 과제 지시가 명시

과제 지시: "a new IAM-email-signup account". 소셜 로그인(`TASK-BE-617` 계열)이 아니다.
⇒ **ⓒ 배제.**

## ⓓ — 코드로 재현되는 레이스: 이벤트 경로가 이제 pull-through 보다 먼저 도착한다

세 조각이 각각 독립적으로 옳았고, **조합이 구멍을 만들었다**:

1. **`TASK-BE-575`**(done, 2026-08-05) — 이벤트 소비자(`account.created`)가 당시
   토폴로지 단절로 죽어 있어서(`ecommerce-kafka` 가 발행을 못 받음), **pull-through**
   (`UserProfileProvisioningFilter` → `UserProfileProvisioner.ensureProvisioned`)를
   첫 요청 시점의 대체 경로로 추가했다. 이 티켓은 "이벤트 경로가 복구돼도 그대로 둔다 —
   **멱등하게 공존**하며, 이벤트가 **늦거나 유실돼도** 화면이 열려야 한다"고 Out of
   Scope 에 적었다(`TASK-BE-575` Out of Scope). 전제는 **이벤트가 느리거나 안 온다**
   였다.
2. **`TASK-MONO-511`**(done, 2026-08-13 — BE-575/577 보다 **나중**) — iam→ecommerce
   Kafka 릴레이(MirrorMaker 2)를 깔아 그 죽어 있던 이벤트 경로를 **살렸다.** 이제
   `user-service` 의
   `AccountCreatedConsumer`(`.../infrastructure/event/AccountCreatedConsumer.java:34`,
   `@KafkaListener(topics = "account.created", groupId = "user-service")`,
   `@Profile("!standalone")` — 데모 배포에서 활성)가 **상시 폴링**하며 IAM 가입 즉시
   거의 바로 소비한다.
3. **`AccountCreatedHandler.handle`**(`.../application/service/AccountCreatedHandler.java:28`)
   은 항상 `provisioner.ensureProvisioned(accountId, null)` — 이벤트 페이로드는
   PII 마스킹이라 **email 을 절대 안 가진다**(의도된 설계, 바꿀 대상 아님).
4. **`UserProfileProvisioner.ensureProvisioned`**(수정 전,
   `.../application/service/UserProfileProvisioner.java:95-104` 부근)은 `findByUserId`
   가 행을 찾으면 **무조건 no-op** 으로 리턴했다. 즉 "두 경로 중 먼저 온 쪽이 이기고
   나머지는 no-op" 인데, **두 경로가 만드는 값이 다르다** — 이벤트는 항상
   `email=null`, pull-through 는 그 요청의 `X-User-Email`(게이트웨이가 토큰
   `email` 클레임에서 주입, `GatewayIdentityConfig.java:84`)을 들고 있다.
   "whichever arrives first wins with the other becoming a no-op" 이라는 그 클래스
   자신의 javadoc 전제(수정 전)는 **BE-575 시점엔 참이었다**(이벤트가 죽어 있어
   pull-through 만 돌았으므로 둘이 달라질 일이 없었다) — `TASK-MONO-511` 이 이벤트를
   살리면서 **처음으로 거짓이 됐다.**

## 왜 "항상" 인가 — 레이스가 아니라 거의 결정적이다

살아있는 Kafka 컨슈머는 폴링 중이라 발행 후 지연이 통상 밀리초~저지연이다. 반대로
pull-through 가 도는 "첫 인증 요청"은 **브라우저가 IAM 가입 → OAuth 콜백 → 세션 수립 →
스토어의 첫 `GET /api/users/me` 호출**을 전부 거친 뒤에야 나간다 — 여러 네트워크 왕복과
리다이렉트가 들어간다. 그래서 이벤트 경로가 거의 항상 먼저 도착해 `email=null` 행을
먼저 만들고, 뒤따라오는 pull-through 호출은 "이미 있음" 으로 no-op 이 되어 **진짜
이메일이 조용히 버려진다.** 과제가 보고한 "새 계정이 매번 null" 이라는 결정적 증상과
일치한다.

## 수정 — `UserProfileProvisioner`/`UserProfile` 에 **1회 백필**을 추가했다

- `UserProfile.assignEmail(String email)` 신설 — 이미 email 이 있거나 `WITHDRAWN`
  (GDPR 익명화됨)인 프로필은 **건드리지 않는다**(PII 를 덮어쓰거나 삭제를 되돌리지
  않는다는 기존 불변식을 그대로 지킨다).
- `UserProfileProvisioner.ensureProvisioned` — 행이 이미 있을 때 바로 리턴하는 대신
  `backfillEmailIfMissing(existing, email)` 을 호출한다. email 이 없거나 공백이면
  여전히 완전한 no-op(기존 동작·기존 테스트 전부 보존). 유효한 email 이 들어오고
  기존 행의 email 이 비어 있을 때만 `assignEmail` + `save`.
- 사이드 이펙트 없음: 이벤트 경로가 계속 `null` 을 넘기는 호출은 그대로 no-op 이고,
  이미 email 이 채워진 행에 다른 값이 들어와도 덮어쓰지 않는다(레이스의 반대 방향도
  막는다 — 동시에 두 pull-through 요청이 다른 email 후보를 들고 와도 첫 저장이
  이긴다).

**영향 범위는 ecommerce 단독이다** — iam-platform 변경 없음, 계약(`user-api.md`) 변경
없음(응답 바디/상태코드 불변, 내부 프로비저닝 동작만 수정).

## 테스트

- `UserProfileTest`(단위) — `assignEmail` 4건: 백필됨 / 이미 있으면 안 덮임 / WITHDRAWN
  이면 안 채워짐 / 형식 오류면 예외.
- `UserProfileProvisionerTest`(단위, Mockito STRICT_STUBS) — 백필 3건(채움·안 덮임·형식
  오류 시 save 안 함) + 기존 5건 전부 보존(그중 no-op 테스트는 이름을 "email 도 안
  들어오면" 으로 명확히 했다 — 동작은 그대로).
- Testcontainers IT(`UserProfileProvisioningIntegrationTest` 등)는 **이 호스트에
  Docker 가 없어 실행 불가**(`docker info` rc=1) — CI(`integrationTest` Gradle task,
  `ecommerce-integration-tests` job)에 위임.
- `./gradlew :projects:ecommerce-microservices-platform:apps:user-service:test` →
  **BUILD SUCCESSFUL** (rc=0).

## 남은 것 — 라이브 재확인은 재굽기 이후

이 수정이 데모에 반영되려면 **AMI 재굽기**가 필요하다(이 머지만으로는 라이브가 안
바뀐다). AC-4 의 라이브 재확인은 그래서 여기서 체크하지 않는다 — 오케스트레이터가
다음 재굽기 창에서: 신규 IAM 이메일 가입 계정 → `GET /api/users/me` 의 `email` 이
채워지는지 측정할 것.

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

- [x] **AC-0 (재측정)** — 이번 창의 그 계정(또는 새로 만든 테스트 계정)의 가입 경로·토큰
      클레임·프로필 행을 다시 확인한다.
      → 라이브 재측정은 이 창(워크트리)에서 할 수 없다(IAM/ecommerce 데모 스택·DB 접근
      없음). 대신 **코드로 재구성**했다 — 아래 「결과」. 가입 경로는 과제 설명이 명시한
      대로 **IAM 이메일 가입**(소셜 아님)이었다고 전제했고, ⓒ(가입 경로가 BE-577 이 확인한
      것과 다르다)는 배제된다.
- [x] **AC-1** — 23차 창 데모 배포가 `TASK-BE-575`/`577` 의 머지 커밋을 포함하는지
      확인한다(배포판 바이너리/커밋 SHA 대조).
      → 과제 지시 자체가 "AMI built from `d44dd0d61`, which includes BE-575/577" 이라고
      전제를 준다. 저장소 쪽에서도 `TASK-BE-575`/`577` 이 `done` 이고 `main` 에 머지돼
      있다(이 워크트리의 베이스가 그 커밋들을 포함). ⇒ **ⓐ(배포 미반영)는 배제.**
- [x] **AC-2** — 원인을 하나로 좁힌다: ⓐ 배포가 그 수정을 안 담았다 ⓑ 토큰에 `email`
      클레임이 실제로 없다(어느 단계에서 빠지는지) ⓒ 가입 경로가 `TASK-BE-577` 이 확인한
      경로와 다르다(예: 소셜) ⓓ 그 외.
      → **ⓓ.** 세 후보 다 아니다 — 아래 「결과」가 code-path 증거로 좁힌 것은 **BE-575 ·
      BE-577 · `TASK-MONO-511` 세 개의 개별로는 옳은 수정이 조합되어 만든 레이스 컨디션**
      이다.
- [x] **AC-3** — 원인이 ⓐ(배포 반영 문제)면 재배포만으로 해소되는지 확인하고 기록 — 코드
      변경 없이 닫을 수 있다.
      → 해당 없음(ⓐ 아님).
- [ ] **AC-4** — 원인이 코드/배선 쪽(ⓑ·ⓒ)이면 수정하고, 새 계정으로 `email` 이 채워짐을
      라이브로 재확인한다.
      → **코드 수정 절반만 여기서 닫는다** — `UserProfileProvisioner`/`UserProfile`
      (아래 「결과」), 단위 테스트로 고정. **라이브 재확인은 이 창에서 할 수 없다**(데모
      스택·DB 접근 없음, 그리고 수정이 데모에 반영되려면 AMI 재굽기가 필요하다) — ⚪
      미체크로 남긴다. 🔴 **오케스트레이터가 다음 재굽기 창에서 측정할 것**: 신규 IAM
      이메일 가입 계정으로 `GET /api/users/me` 의 `email` 이 채워지는지.

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
