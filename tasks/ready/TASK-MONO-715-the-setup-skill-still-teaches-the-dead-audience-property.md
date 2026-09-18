# Task ID

TASK-MONO-715

# Title

🔴 게이트웨이 셋업 스킬이 **한 번도 읽히지 않는 속성 + `aud`=플랫폼 이름** 을 아직 가르친다 — `TASK-MONO-696` 이 고친 두 오해를 새 게이트웨이마다 재생산한다

# Status

ready

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

- [ ] **AC-0 — 모집단을 먼저 센다. 🔴 「한 줄」이라고 단정하지 않는다.** 698 § AC-0 (g) 가 이름 댄 것은 `:79` 하나지만, 이 티켓을 기안하며 `.claude/skills/**` 를 audience 어휘로 전수하니 **같은 오해가 더 보였다**(2026-09-18 UTC 실측, 2파일 6히트):
  - `service-types/identity-platform-setup/SKILL.md:79` — 죽은 속성 + `# e.g., wms`(플랫폼 이름)
  - 같은 파일 `:52` — `.audience().add(aud)` 옆 주석 *"aud (one platform per token)"* ⇒ **같은 플랫폼-이름 오해**
  - `backend/jwt-auth/SKILL.md:61,77,80,89` — `generateAccessToken(Account, String audience, …)` 에서 `account.rolesFor(audience)` ⇒ **`aud` 를 플랫폼 키로 쓰는 코드 예시**
  🔴 위 목록은 **내가 잰 것**이고 착수 시점의 것이 아니다 — AC-0 이 다시 센다. 🔵 그리고 `jwt-auth` 쪽은 **오해의 종류가 다를 수 있다**(파라미터 이름이 `audience` 인 것과 `aud` 클레임에 플랫폼 이름을 넣는 것은 다른 문제다) ⇒ **읽고 판정**한 뒤 대상에 넣을지 정한다. 대상에서 뺀 것도 이유를 적는다.
- [ ] **AC-1 — 죽은 속성 제거.** `audiences: ${GATEWAY_AUDIENCE}` 예시를 지운다. 🔴 **다른 속성으로 «바꾸지» 않는다** — 696 이 실측한 대로 그 게이트웨이들은 이 속성이 설정하는 디코더를 **쓰지 않는다**. 대신 계약서 rule 5 를 가리키고, 「엣지는 client id allowlist 를 선언한다」는 **사실만** 적는다.
- [ ] **AC-2 — 플랫폼 이름 오해 제거.** `# e.g., wms` 류의 예시값과 *"one platform per token"* 류의 주석을, **`aud` = 발급 client id** 로 고친다(계약서 `aud` 행의 문구를 따른다). 예시값은 계약서처럼 **플레이스홀더**(`<operator-console-client-id>` 등)를 쓴다 — 실제 client id 를 스킬에 박으면 그것이 다음 드리프트다.
- [ ] **AC-3 — 재진술하지 않는다.** 스킬은 **포인터**여야 한다(`rest-api.md` § Versioning 이 쓰는 방식). 🔴 rule 5 의 본문을 스킬에 복사하면 **이번 개정이 스킬에 두 번째 집을 만든다** — 이 저장소가 여러 번 대가를 치른 부류(«한 사실이 두 절에 있으면 한쪽만 고쳐진다»).

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

- [ ] AC-0 ~ AC-3
- [ ] 스킬이 가르치는 audience 문장이 계약서 rule 5 와 **같은 것을 말한다**
- [ ] 죽은 속성 예시가 `.claude/skills/**` 에 **0건**
