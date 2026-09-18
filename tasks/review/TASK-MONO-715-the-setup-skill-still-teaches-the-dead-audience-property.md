# Task ID

TASK-MONO-715

# Title

🔴 게이트웨이 셋업 스킬이 **한 번도 읽히지 않는 속성 + `aud`=플랫폼 이름** 을 아직 가르친다 — `TASK-MONO-696` 이 고친 두 오해를 새 게이트웨이마다 재생산한다

# Status

review (2026-09-18 UTC — AC-0 ~ AC-3 닫힘 · 모집단이 2파일 6히트 → 3파일 19히트로 늘었다)

# Owner

monorepo

# Task Tags

- contract
- onboarding

---

> **분석 모델:** Opus 5 / **구현 권장:** Sonnet (문서 몇 줄. 난점은 분량이 아니라 **모집단** — 같은 오해가 몇 개 파일에 사는지 AC-0 이 먼저 센다)
>
> 📎 **출처**: `TASK-MONO-698` § AC-0 (g) 곁발견 + § AC-4 (c) ① 판단. 698 의 Scope 밖이라 별 티켓으로 떼어냈다.

# Goal

`TASK-MONO-696` 은 두 가지를 고쳤다: (i) 게이트웨이의 `spring…jwt.audiences` 속성은 **그 게이트웨이가 쓰지 않는 디코더**를 설정한다(= 죽은 속성), (ii) `aud` 는 **플랫폼 이름이 아니라 발급 client id** 다. 696 AC-4 는 죽은 속성 잔존을 **`projects/**` 안에서** 0 으로 만들었다.

🔴 **그런데 스킬이 그 둘을 계속 가르친다.** `.claude/skills/service-types/identity-platform-setup/SKILL.md:79`:

```yaml
audiences: ${GATEWAY_AUDIENCE}   # e.g., wms
```

한 줄에 두 오해가 다 있다 — 속성은 아무도 안 읽고, 예시값 `wms` 는 **플랫폼 이름**이다. 스킬은 «새 게이트웨이를 만들 때 읽는 문서» 이므로, 이 줄은 **다음 게이트웨이에 같은 결함을 심는 지시문**이다.

🔵 이 티켓이 끝나면: 스킬이 가르치는 것이 **계약서 rule 5 와 같은 것**이다(엣지가 **client id allowlist** 를 선언하고 교집합으로 검사한다).

# Scope

## In Scope

- AC-0 의 인구조사 결과에 해당하는 스킬 문서의 audience 문장 교정
- 계약서(`platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5)로의 포인터 — **규칙을 스킬에 복제하지 않는다**(복제가 드리프트의 원인이다)

## Out of Scope

- 코드 변경 **전부**. 이 티켓은 문서만 고친다
- 게이트웨이/엣지의 실제 allowlist 도입 — `TASK-MONO-696`/`697`/`712`/`713`
- 스킬의 다른 절(키 관리, 토큰 수명 등) 개선

# Acceptance Criteria

- [x] **AC-0 — 모집단을 먼저 센다. 🔴 「한 줄」이라고 단정하지 않는다.** 698 § AC-0 (g) 가 이름 댄 것은 `:79` 하나지만, 이 티켓을 기안하며 `.claude/skills/**` 를 audience 어휘로 전수하니 **같은 오해가 더 보였다**(2026-09-18 UTC 실측, 2파일 6히트):
  - `service-types/identity-platform-setup/SKILL.md:79` — 죽은 속성 + `# e.g., wms`(플랫폼 이름)
  - 같은 파일 `:52` — `.audience().add(aud)` 옆 주석 *"aud (one platform per token)"* ⇒ **같은 플랫폼-이름 오해**
  - `backend/jwt-auth/SKILL.md:61,77,80,89` — `generateAccessToken(Account, String audience, …)` 에서 `account.rolesFor(audience)` ⇒ **`aud` 를 플랫폼 키로 쓰는 코드 예시**
  🔴 위 목록은 **내가 잰 것**이고 착수 시점의 것이 아니다 — AC-0 이 다시 센다. 🔵 그리고 `jwt-auth` 쪽은 **오해의 종류가 다를 수 있다**(파라미터 이름이 `audience` 인 것과 `aud` 클레임에 플랫폼 이름을 넣는 것은 다른 문제다) ⇒ **읽고 판정**한 뒤 대상에 넣을지 정한다. 대상에서 뺀 것도 이유를 적는다.
- [x] **AC-1 — 죽은 속성 제거.** `audiences: ${GATEWAY_AUDIENCE}` 예시를 지운다. 🔴 **다른 속성으로 «바꾸지» 않는다** — 696 이 실측한 대로 그 게이트웨이들은 이 속성이 설정하는 디코더를 **쓰지 않는다**. 대신 계약서 rule 5 를 가리키고, 「엣지는 client id allowlist 를 선언한다」는 **사실만** 적는다.
- [x] **AC-2 — 플랫폼 이름 오해 제거.** `# e.g., wms` 류의 예시값과 *"one platform per token"* 류의 주석을, **`aud` = 발급 client id** 로 고친다(계약서 `aud` 행의 문구를 따른다). 예시값은 계약서처럼 **플레이스홀더**(`<operator-console-client-id>` 등)를 쓴다 — 실제 client id 를 스킬에 박으면 그것이 다음 드리프트다.
- [x] **AC-3 — 재진술하지 않는다.** 스킬은 **포인터**여야 한다(`rest-api.md` § Versioning 이 쓰는 방식). 🔴 rule 5 의 본문을 스킬에 복사하면 **이번 개정이 스킬에 두 번째 집을 만든다** — 이 저장소가 여러 번 대가를 치른 부류(«한 사실이 두 절에 있으면 한쪽만 고쳐진다»).

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 · Standard Claims `aud` 행 · § Change log 2026-09-16 / 2026-09-18
- `platform/service-types/identity-platform.md` § Integration Rules 규칙 3
- `tasks/review/TASK-MONO-698-audience-behind-the-gateway-and-at-the-other-edges.md` § AC-0 (g) · § AC-4 (c) ①
- `tasks/review/TASK-MONO-696-the-gateway-audience-is-configured-and-never-checked.md` § AC-4

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`

# Target Service

- 없음 (문서 전용 — `.claude/skills/**`, 공유 경로 ⇒ **root task**)

# Edge Cases

- **스킬은 프로젝트 무관이어야 한다**(HARDSTOP-03). `# e.g., wms` 를 다른 프로젝트 이름으로 바꾸는 것은 **같은 위반의 다른 판**이다 — 플레이스홀더를 쓴다.
- **`jwt-auth/SKILL.md` 는 발급측 예시다** — 게이트웨이(검증측) 예시와 오해의 방향이 다를 수 있다. AC-0 이 판정한다.
- **스킬을 읽는 것은 에이전트다** — 사람 리뷰어가 «맥락상 알아듣는다» 는 근거가 되지 않는다. 문장 자체가 참이어야 한다.

# Failure Scenarios

- **`:79` 한 줄만 고치고 닫기** — 같은 파일 `:52` 와 `jwt-auth` 가 남아 오해가 계속 전파된다. AC-0 이 막는다.
- **규칙을 스킬에 복제** — 다음 계약서 개정 때 스킬만 낡는다. AC-3 이 막는다.
- **실제 client id 를 예시로 박기** — 프로젝트 특정 값이 공유 경로에 들어간다(HARDSTOP-03) + 값이 바뀌면 낡는다.

# Test Requirements

- 문서 전용이라 코드 테스트 없음
- 🔵 대신 **AC-0 의 인구조사 결과(파일·줄·판정)** 를 이 파일에 표로 남긴다 — 「한 줄인 줄 알았다」가 이 티켓의 출발점이었으므로
- 관련 문서 lint / 링크 가드가 있으면 rc 를 명시해 돌린다(파이프 금지)

# Definition of Done

- [x] AC-0 ~ AC-3
- [x] 스킬이 가르치는 audience 문장이 계약서 rule 5 와 **같은 것을 말한다**
- [x] 죽은 속성 예시가 `.claude/skills/**` 에 **0건**

---

# 🟢 AC-0 — 인구조사: 「2파일 6히트」가 아니라 **3파일 19히트**였다 (2026-09-18 UTC · 분석=Opus 5)

🔴 **티켓이 적어 둔 모집단이 틀렸고, AC-0 이 그것을 잡으라고 있는 칸이다.** 기안 당시 실측은
«2파일 6히트» 였는데, 착수 시점 트리에서 `.claude/skills/**` 를 audience 어휘로 다시 전수하니
**티켓이 이름조차 대지 않은 파일이 하나 더** 나왔다 — `backend/gateway-security/SKILL.md`.
🔵 그 파일이 빠진 이유는 짐작이 간다: 거기의 문장은 `audiences` 속성도 `# e.g., wms` 도 아니라
**산문**(*"reject a token minted for a different platform"*)이라, 속성명·예시값으로 찾으면 안 걸린다.

| # | 파일 : 줄 | 무엇이 틀렸나 | 판정 |
|---|---|---|---|
| 1 | `service-types/identity-platform-setup:21` | *"each token is `aud`-scoped to one platform"* | **대상** — 🔴 이 줄은 **검증 단계에서야** 나왔다(아래 § 검증) |
| 2 | 〃 `:39` | *"≥ 1 role on the requested `aud` platform"* | **대상** |
| 3 | 〃 `:40` | *"`aud`-scoped to one platform"* | **대상** |
| 4 | 〃 `:52` | `.audience().add(aud)` + *"(one platform per token)"* | **대상** |
| 5 | 〃 `:53` | `resolveRoles(account, aud)` — `aud` 를 플랫폼 키로 | **대상** |
| 6 | 〃 `:79` | `audiences: ${GATEWAY_AUDIENCE}   # e.g., wms` — 죽은 속성 + 플랫폼 이름 | **대상** (698 이 이름 댄 그 줄) |
| 7 | `backend/gateway-security:47` | *"reject a token minted for a **different platform**"* | **대상** 🔴 티켓이 못 본 파일 |
| 8 | 〃 `:157` | 필터 표에 *"Verify (JWKS/RS256, `iss`, `aud`)"* | **제외** — `aud` 를 검증 목록에 **넣기만** 하고 그것이 무엇인지 말하지 않는다. 참인 문장이라 고칠 것이 없다 |
| 9 | `backend/jwt-auth:61,77` | `generateAccessToken(…, String audience, …)` 파라미터 이름 | **대상**(부수적) |
| 10 | 〃 `:79` | *"A token is scoped to ONE platform (`aud`)"* | **대상** |
| 11 | 〃 `:80` | `account.rolesFor(audience)` — `aud` 를 플랫폼 키로 | **대상** |
| 12 | 〃 `:89` | `.audience().add(audience)` | **대상**(부수적) |
| 13 | 〃 `:122` | 클레임 표 행 **``aud`` \| **one** platform** | **대상** |
| 14 | 〃 `:184` | *"One token = one `aud`, carrying only that platform's roles"* | **대상** |
| 15 | 〃 `:200` | 안티패턴 표 *"Scope to the token's `aud`"* | **대상** |

## 🔴 `jwt-auth` 판정 — 티켓이 «오해의 종류가 다를 수 있다» 고 남긴 칸

**대상이 맞다.** 파라미터 이름이 `audience` 인 것만이면 명명 취향이라 넘겼겠지만, 이 스킬은
**클레임 자체를 그렇게 설명한다** — `:122` 의 표가 «`aud` = **one** platform» 이라고 단언하고,
`:184` 가 요약에서 되풀이하고, `:80` 이 그 오해로 **role 을 고르는 코드**를 보여 준다.
🔵 즉 발급측·검증측의 차이가 아니라 **같은 오해의 다른 자리**다. 파라미터 이름도 함께
`clientId` 로 고쳤다 — 이름이 `audience` 인 채로 값이 client id 이면 다음 사람이 다시 헷갈린다.

# 🟢 AC-1 — 죽은 속성 제거

`identity-platform-setup:79` 의 `audiences: …` 예시를 **지웠다**. 🔴 다른 속성으로 바꾸지 않았다 —
696 이 실측한 대로 그 게이트웨이들은 그 속성이 설정하는 디코더를 **쓰지 않는다**. 대신 그 자리에
**왜 없는지**를 적고 계약서 rule 5 를 가리킨다.

🔵 **삭제만 하지 않고 «없다는 사실»을 남긴 이유**: 빈 자리는 다음 사람에게 «누가 빠뜨렸나» 로
읽히고, 그러면 친절하게 다시 넣는다. 죽은 속성이 돌아오는 가장 그럴듯한 경로가 그것이다.

# 🟢 AC-2 — 플랫폼 이름 오해 제거

계약서 `aud` 행의 문구를 따랐다 — *"The client id of the registered client the token was issued to
… It is **not** a platform identifier"*. 예시값은 실제 client id 를 박지 않고 계약서와 같은
**플레이스홀더** 서술을 썼다(HARDSTOP-03: `# e.g., wms` 를 다른 프로젝트 이름으로 바꾸는 것은
같은 위반의 다른 판이다).

🔵 고친 문장들이 «한 토큰 = 한 플랫폼» 이라는 **원래 의도까지** 지우지 않도록 했다. 그 의도는
참이다(한 client 는 한 플랫폼에 속하므로 role 은 여전히 그 플랫폼 것만 실린다). 틀린 것은
**그 사실을 `aud` 클레임에 플랫폼 이름으로 적는 것**이고, 둘이 붙어 있어서 지금까지 같이 통했다.

# 🟢 AC-3 — 재진술하지 않는다

세 파일 모두 **포인터**다(`platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 ·
§ Standard Claims `aud`). rule 5 의 본문(교집합 의미·fail-closed·403·섀도 조건)을 스킬에 복사하지
않았다 — 복사하면 이번 개정이 스킬에 **두 번째 집**을 만들고, 다음 계약서 개정 때 스킬만 낡는다.
🔵 상대 경로가 세 파일 모두에서 실재하는지 확인했다(`test -f` 3/3 OK).

# 🟢 검증 — 그리고 검증이 **두 가지를 더 잡았다**

| 판정 | 결과 |
|---|---|
| 죽은 속성 예시가 `.claude/skills/**` 에 0건 (DoD) | 🟢 **0건** |
| 「`aud` = 플랫폼」 문장 잔존 | 🟢 **0건** |
| 계약서 링크 실재 | 🟢 3/3 |

🔴 **검증에서 잡힌 것 ①** — `identity-platform-setup:21` 에 **같은 오해가 하나 더** 있었다.
AC-0 의 표를 쓸 때 그 줄을 «단계 목록» 으로 읽고 넘겼는데, 문장 안에 *"each token is `aud`-scoped
to one platform"* 이 그대로 있었다. 🔵 인구조사는 **어휘로** 걸렀고 판정은 **눈으로** 했으므로,
어휘에 걸렸어도 눈이 넘기면 빠진다 — 그래서 완료 판정을 **다시 grep 으로** 돌린 것이 값을 했다.

🔴 **검증에서 잡힌 것 ②** — 내가 쓴 설명문이 **그 판정 자신에 걸렸다.** AC-1 을 적으면서 지운
줄을 그대로 인용했더니(`` `audiences: ${GATEWAY_AUDIENCE}` ``), *"죽은 속성 0건"* grep 이 **그
문단을 물었다.** 이 저장소가 이미 이름 붙인 부류다 — «판별자가 자기 설명 문구에 걸린다».
⇒ 인용을 **서술로** 바꾸고(속성명을 리터럴로 적지 않는다) 그 이유를 그 자리에 한 줄로 남겼다.
🔵 남기지 않으면 다음 사람이 «왜 인용을 안 했지» 하며 되돌린다.
