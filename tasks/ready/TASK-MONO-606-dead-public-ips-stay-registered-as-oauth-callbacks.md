# Task ID

TASK-MONO-606

# Title

더 이상 우리 것이 아닌 공인 IP 가 OAuth 콜백으로 **등록된 채 누적**된다. 줄어드는 경로가 없고, AMI 에 굳어서 배포된다.

# Status

ready

# Owner

monorepo

# Task Tags

- security
- demo
- iam

---

# 🔎 어떻게 나왔나 — `TASK-MONO-605` 의 **판정 밖**에서 나왔다

`TASK-MONO-605` 가 `TASK-BE-582` 의 「기존 볼륨」 판정을 위해 데모 루트 볼륨 스냅샷
(`snap-09449008990589c36`, 2026-08-29)을 복원해 `oauth_clients` 를 읽었다. 판정 자체는
**통과**였다. 그런데 그 행이 이렇게 생겼다:

```json
["http://localhost:3000/api/auth/callback/iam",
 "http://localhost:3002/api/auth/callback/iam",
 "http://fan-platform.local/api/auth/callback/iam",
 "http://web.fan-platform.local/api/auth/callback/iam",
 "http://fan-platform.54-181-1-212.sslip.io/api/auth/callback/iam",
 "http://web.fan-platform.54-181-1-212.sslip.io/api/auth/callback/iam",
 "http://fan-platform.43-200-129-91.sslip.io/api/auth/callback/iam",
 "http://web.fan-platform.43-200-129-91.sslip.io/api/auth/callback/iam",
 "http://fan-platform.54-116-51-195.sslip.io/api/auth/callback/iam",
 "http://web.fan-platform.54-116-51-195.sslip.io/api/auth/callback/iam"]
```

**서로 다른 공인 IP 세 개**가 콜백 주소로 남아 있다. 데모는 온디맨드라 IP 가 매번 바뀌므로
**그 셋 중 어느 것도 지금 우리 것이 아니다.**

## 기전 — 설계된 대로 동작한 결과다. 버그가 아니라 **빠진 절반**이다

`infra/demo/seed-demo-domain.sh` 는 `.local/` 을 포함한 등록 URI 를 찾아 현재 데모 도메인으로
치환한 사본을 **`JSON_MERGE_PRESERVE` 로 덧붙인다.** 원본을 지우지 않는 것은 **의도**이고,
그 헤더가 이유까지 적어 뒀다 — *"같은 DB 를 로컬 `*.local` 로도 쓸 수 있어야 한다."*

🔵 **그 판단은 옳다.** 문제는 **덧붙이기만 있고 걷어내기가 없다**는 것이다:

- 멱등성 가드(`WHERE ... NOT LIKE CONCAT('%', @dom, '%')`)는 **이번 도메인**에 대해서만 판정한다.
  부팅마다 `@dom` 이 다르므로 **매번 가드를 통과하고 매번 새로 붙는다.**
- 옛 도메인을 지우는 코드는 이 저장소 어디에도 없다.
- ⇒ **부팅 1회 = 클라이언트당 URI +2. 상한 없음.**

## 🔴 그리고 그 누적이 **AMI 에 굳어서 배포된다**

605 가 복원한 볼륨은 모든 파일이 **2026-08-22 각인**이었다(`wtmp`·`syslog`까지, 08-23 이후
파일 **0개**). 즉 위 sslip.io 3종은 **인스턴스가 살면서 쌓은 것이 아니라 AMI 굽는 과정에서
이미 DB 에 들어간 것**이고, 그 AMI 로 뜨는 **모든 인스턴스가 그 셋을 갖고 시작한다.**
⇒ 재굽기를 할 때마다 **직전 세대의 죽은 IP 들이 다음 세대로 상속된다.**

---

# Goal

죽은 공인 IP 가 `redirect_uri` / `post_logout_redirect_uri` 로 등록된 채 남지 않게 한다.
그리고 **그 상태가 실제로 무엇을 가능하게 하는지 먼저 재고** 조치 강도를 정한다.

🔴 **이 티켓은 «심각도» 를 이미 정해 두지 않는다.** 아래 AC-1 이 그것을 재는 자리다.

---

# Scope

**In:**

- 현재 라이브 데모 DB 에 남아 있는 죽은 도메인 **전수**(클라이언트별, 개수)
- 이 배선에서 그 등록이 **무엇을 가능하게 하는가**의 실측 (AC-1)
- `seed-demo-domain.sh` 에 «옛 데모 도메인 회수» 를 추가
- AMI 재굽기 경로에서 누적이 상속되지 않게 하는 지점

**Out:**

- `.local` / `localhost` 항목 제거 — **의도된 것**이고 로컬 개발이 쓴다. 건드리지 않는다
- `V0033` 등 마이그레이션 파일 수정 — 체크섬을 깬다. 런타임 시드의 문제다
- 데모 아키텍처를 고정 도메인으로 바꾸는 결정 — 그것은 `ADR-MONO-067` D4 / `TASK-MONO-576`

---

# Acceptance Criteria

## AC-0 — 전제 재확인 (verify-then-act)

1. **아직 참인가.** 현재 AMI(`ami-0caf015f7cd9144fd`)에서 뜬 인스턴스의 `oauth_clients` 에
   죽은 sslip.io 도메인이 몇 개 있는지 **행으로** 센다.
   🔴 **파일 grep 금지** — 이 축의 진실은 DB 에 있다(`TASK-BE-582` AC-4 가 세운 규칙).
   🔵 기동이 필요하면 그것은 소유자 승인 대상이다. **605 처럼 스냅샷으로도 잴 수 있다.**
2. 0 건이면 **STOP** — 그 사이 누군가 고쳤다는 뜻이고, 무엇이 고쳤는지부터 찾는다.

## AC-1 — **심각도를 재고 나서** 조치 강도를 정한다. 추론으로 정하지 않는다

아래 셋을 **실측**한다. 셋의 조합이 «위험» 과 «지저분» 을 가른다:

| # | 무엇 | 왜 이것이 축을 가르나 |
|---|---|---|
| ① | `fan-platform-user-flow-client` 가 **public 인가 confidential 인가** (`client_authentication_methods`) | public + 코드 가로채기면 secret 없이 교환된다 |
| ② | **PKCE 가 강제되는가** (`require_proof_key`) | 강제면 `code_verifier` 없이 교환 불가 ⇒ 공격 난도가 크게 다르다 |
| ③ | 그 IdP 에 **무엇이 들어 있는가** — 데모 시드 계정뿐인가, 실제 값이 있는가 | 「털려도 데모 계정」과 「실계정」은 다른 결정을 부른다 |

🔴 **판정을 «치명적» 으로도 «무해» 로도 미리 적지 마라.** 지금 아는 것은 *"그 IP 를 받은
사람이 authorization code 를 받을 수 있는 주소를 갖는다"* 까지이고, 거기서 실제로 세션이
탈취되는지는 ①②에 달렸다. [[feedback_a_verifiable_mechanism_is_not_the_cause]]

## AC-2 — 회수는 **보존해야 할 것을 명시적으로 지키면서** 한다

- 지울 것: **과거 데모 도메인**(현재 `DEMO_DOMAIN` 이 아닌 sslip.io 형태)
- 🔴 지키지 말아야 할 것을 지우면 로그인이 통째로 깨진다 — **`.local` · `localhost` ·
  `hubwang.com` 은 반드시 남는다.** 술어를 «sslip.io 이면서 현재 도메인이 아닌 것» 으로
  좁혀라. «http 이면 삭제» 같은 넓은 술어는 `.local` 을 같이 지운다.
- **양성 대조군**: 회수 후 현재 도메인 콜백으로 로그인이 되는지 확인한다.
  🔴 회수만 하고 «깨끗해졌다» 로 닫지 마라 — 이 저장소는 그 모양으로 데인 적이 있다.

## AC-3 — **재발하지 않게** 한다. 1회 청소는 조치가 아니다

- `seed-demo-domain.sh` 가 덧붙이기 **전에** 옛 도메인을 걷어내도록 고친다
  (덧붙이기와 걷어내기가 같은 트랜잭션에 있어야 부팅 중간에 죽어도 안전하다)
- 🔴 **AMI 재굽기 경로도 덮어야 한다** — 굽는 인스턴스에서도 시드가 돌고, 그 결과가
  이미지에 굳는다. 굽기 마지막에 회수가 한 번 더 돌지 않으면 **다음 세대가 상속한다**
- 🔴 **가드가 필요하다.** 이 결함은 «부팅마다 하나씩» 자라는 종류라 사람이 볼 때는 이미
  커져 있다. `verify-demo-wrapper.sh` 계열에 **「현재 도메인이 아닌 sslip.io 등록이 0인가」**
  칸을 넣어라 — 🔴 **양성 대조군(일부러 하나 주입 → 무는가)까지 있어야 그 칸이 산다.**
  [[feedback_why_a_guard_does_not_bite]] [[project_guard_design_requirements]]

## AC-4 — 다른 클라이언트도 같은 병인지 **세고 나서** 적는다

605 가 본 것은 `fan-platform-user-flow-client` 한 행이고, 같은 볼륨에서
`redirect_uris LIKE '%sslip.io%'` 인 클라이언트는 **4개**였다.
🔴 **「4개」는 그 스냅샷의 숫자다** — 착수 시점에 다시 세라. 그리고 클라이언트마다
콜백 **경로 모양이 다르다**(`/api/auth/callback` · `/…/gap` · `/…/iam` · `/callback`) ⇒
한 술어가 넷을 다 덮는다고 가정하지 마라. [[feedback_recount_population_dont_inherit_scope]]

---

# Related Specs

- `infra/demo/seed-demo-domain.sh` — 기전이 사는 곳 (헤더가 «원본을 지우지 않는다» 의 이유를 적는다)
- `tasks/review/TASK-MONO-605-…md` § 판정 밖에서 나온 것 — 이 티켓의 출처
- `projects/iam-platform/tasks/review/TASK-BE-582-…md` § AC-4 — 「행으로 판정한다」 규칙의 출처
- `docs/adr/ADR-MONO-067-…md` § D4 — 데모 도메인이 부팅마다 바뀌는 구조 자체의 결정

# Related Contracts

없음. OAuth2 `redirect_uri` 정확 일치는 RFC 6749 §3.1.2.3 / Spring Authorization Server 동작이며
이 저장소의 계약 파일이 아니다.

---

# Edge Cases

- 🔴 **현재 도메인을 「옛 것」으로 오판하면 지금 돌아가는 로그인이 깨진다.** 회수 술어는
  실행 시점의 `DEMO_DOMAIN` 을 반드시 참조해야 하고, `DEMO_DOMAIN` 이 비었거나 `local` 이면
  **아무것도 지우지 않고 종료**해야 한다(시드 스크립트의 기존 early-exit 와 같은 규칙).
- 🔴 `client_settings` 의 post-logout 목록은 **Jackson default-typing** 이라 배열이 `[1]` 에 있다
  (`[0]` 은 타입 태그 문자열). `[0]` 을 배열로 다루면 조용히 망가진다 — V0016/V0021 의 교훈.
- 🔴 `redirect_uris` 를 **텍스트로** 다루면 MySQL 의 JSON 재직렬화(공백·키 순서)와 싸우게 된다.
  파싱된 트리 위에서 다뤄라 — 시드 스크립트가 이미 그 이유로 `JSON_TABLE` 을 쓴다.
- 🔵 배열이 비면 그 클라이언트는 로그인 불가가 된다. **회수 후 원소 수 ≥ 1 을 단언**하라.
- 🔵 스냅샷으로 재는 길이 열려 있다 — `TASK-MONO-605` 가 t3.small 하나로 하는 절차를 남겼다.
  **데모 예산을 쓰지 않고** AC-0 을 닫을 수 있다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 술어가 너무 넓어 `.local` 까지 삭제 | 로컬 개발 로그인이 전부 깨진다 | AC-2 의 좁은 술어 + 보존 목록 명시 |
| 1회 청소만 하고 닫음 | 다음 부팅부터 다시 자란다 | AC-3 (시드 수정 + 굽기 경로 + 가드) |
| 가드를 넣었는데 안 문다 | 매주 초록인데 목록은 계속 자란다 | AC-3 의 **양성 대조군 주입** |
| 심각도를 추론으로 «치명적» 판정 | 데모 하나 때문에 과잉 조치 | AC-1 ①②③ 실측 후 결정 |
| 심각도를 추론으로 «무해» 판정 | 실제 취약점을 닫지 않고 넘어감 | 같음 — **양방향으로** 재라 |
| `fan` 한 행만 고침 | 나머지 3개 클라이언트가 남는다 | AC-4 (모집단 재계수) |
