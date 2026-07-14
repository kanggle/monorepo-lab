# Task ID

TASK-MONO-411

# Title

정경 스펙 4쌍이 서로 다른 말을 한다 — 인용된 출처가 자기 자신과 어긋나는 자리 포함

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- api
- event

---

# Goal

`/validate-rules`(2026-07-15)가 **platform 스펙끼리 갈라진** 4쌍을 찾았다. 스펙은 SoT 다 — 갈라진 스펙은 **성실히 따를수록 틀리게 만든다**(`MONO-403`·`MONO-367` 이 같은 실패 모드로 대가를 치렀다).

**① `versioning-policy.md:9-13` 이 자기 자신과 모순** (실측 확인)
- bullet 1: *"URL path versioning: `/api/v{n}/{resource}`"*
- bullet 3: *"Version prefix is **omitted** in current contracts but must be added when a breaking change is introduced."*
- 그런데 **`rest-api.md:28` 은 이 파일을 근거로 *"the `/api/` prefix is **mandatory** per `platform/versioning-policy.md`"*** 라고 인용하고, `naming-conventions.md:64` 의 예시(`/api/v1/<resource>/refresh-token`)도 접두사를 쓴다. **인용된 출처가 인용문과 반대를 말한다.**

**② 이벤트 breaking-change 메커니즘이 두 스펙에서 다르다**
- `versioning-policy.md:35-39` — *"`{EventName}V{n}` … **Alternatively**, topic suffix (`<topic>.v2`) — either approach"* (**택1**, `eventVersion` 필드 언급 없음)
- `event-driven-policy.md:60-68,121-126` — *"Breaking changes **require**: 1. a new `eventVersion`, **and** 2. a parallel topic, 3. consumer migration deadline"* (**둘 다 필수**)
- **두 문장은 합성되지 않는다.** 구현자는 자기가 먼저 연 파일에 따라 다른 코드를 쓴다.

**③ `Idempotency-Key` 의 적용 범위**
- `platform/service-types/rest-api.md:41` — *"All non-idempotent endpoints … **MUST** accept an `Idempotency-Key` header"* (**모든 rest-api 서비스, 무조건**)
- `rules/traits/transactional.md` T1 + `error-handling.md:106-116` — **`transactional` trait 를 선언한 프로젝트에만** 활성화되는 규칙으로 서술.
- **SoT 우선순위상 `rules/traits/`(4층) > `platform/`(5층)** ⇒ **trait 게이트가 권위**이고 `rest-api.md` 의 무조건 MUST 가 고쳐질 쪽이다. 단, *"platform 베이스라인으로 격상"* 이 의도였다면 그렇게 **선언**하고 `error-handling.md` 의 분류를 함께 고쳐야 한다.

**④ JWT 서명 알고리즘**
- `platform/contracts/jwt-standard-claims.md:36` — *"Algorithm: **RSA** asymmetric"* (대안 없음)
- `platform/service-types/identity-platform.md:99-101` — *"RSA 2048 minimum, **or EdDSA (Ed25519)**"*
- 계약이 더 좁고 더 구속적이다. **둘 중 하나가 틀렸고, 지금은 어느 쪽을 믿을지가 파일 열람 순서에 달려 있다.**

---

# Scope

## In Scope

- **네 쌍을 각각 하나의 문장으로 수렴**시킨다. 매번 **어느 쪽이 권위인지 SoT 우선순위로 판정하고 그 근거를 PR 본문에 적는다.**
- ①은 **`versioning-policy.md` bullet 3 이 stale** 일 가능성이 높다(두 파일이 이미 반대로 실행 중). **다만 진짜로 접두사 없는 계약이 남아 있는지 grep 으로 확인하고 고쳐라** — 문장이 stale 인지, 아니면 현실이 갈라진 건지는 **코드가 답한다.**
- ③은 **결정이 필요**하다(trait 게이트 유지 vs platform 베이스라인 격상). **이 task 는 결정을 강요하지 않는다 — 현행 SoT 대로 trait 게이트를 정경으로 삼아 `rest-api.md` 를 정정하는 것이 기본값**이고, 격상을 원하면 **사람이 명시적으로 승인**해야 한다(ADR 필요 여부 포함).

## Out of Scope

- **코드 변경 0.** 이 task 는 스펙 간 모순을 없앤다. 스펙 수정으로 **살아있는 코드가 위반이 되는 것이 드러나면** 별건 티켓으로 제안한다(AC-4).
- 새 ADR 발행 — **먼저 정경 파일을 읽어라.** `MONO-406` 의 교훈: *"새 결정을 제안하기 전에 정경 정책부터 열 것 — 이미 결정된 것을 다시 결정하면 드리프트가 한 겹 더 쌓인다."* ③이 ADR 을 요구하는지는 `platform/architecture-decision-rule.md` 가 판정한다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** 4쌍의 인용문이 **오늘도 그 파일 그 줄에 있는지** 직접 확인. 이미 수렴된 쌍이 있으면 phantom 으로 기록하고 넘어간다.
- [ ] **AC-1** 네 쌍 각각 **하나의 문장**으로 수렴. 각 수정마다 **권위 근거(SoT 층)** 를 명시.
- [ ] **AC-2 (①은 코드에 물어라)** 접두사 없는 API 경로가 **실제로 남아 있는지** grep(`@RequestMapping`/`@GetMapping` 등, 계약 문서, 게이트웨이 라우트). **결과가 문장을 결정한다** — 없으면 bullet 3 을 지우고, 있으면 그 잔존이 의도인지 드리프트인지 적는다.
- [ ] **AC-3 (③은 사람 결정)** trait 게이트 유지가 기본값. 격상을 제안하려면 **PR 본문에 근거와 영향 범위**(어떤 프로젝트가 `transactional` 없이 rest-api 인가)를 적고 **사람 승인을 받는다.** 에이전트 self-decision 금지.
- [ ] **AC-4 (스펙을 고치면 코드가 위반이 될 수 있다)** 각 수렴 후, 그 문장이 **오늘의 코드와 어긋나는지** 조사만 하고 별건 제안. 특히 ④(EdDSA 를 실제로 쓰는 서비스가 있는가 — 있으면 계약을 고칠 쪽은 반대일 수 있다).
- [ ] **AC-5** `platform/` project-agnostic 유지(HARDSTOP-03) — 서비스명·도메인 엔티티 도입 0.

---

# Related Specs

- `platform/versioning-policy.md` · `platform/event-driven-policy.md` · `platform/naming-conventions.md`
- `platform/service-types/{rest-api,identity-platform}.md`
- `platform/contracts/jwt-standard-claims.md`
- `platform/error-handling.md` § Idempotency codes · `rules/traits/transactional.md`
- `CLAUDE.md § Source of Truth Priority` (**분쟁 판정의 정경**)
- `platform/architecture-decision-rule.md` (③이 ADR 게이트인지 판정)

# Related Skills

N/A.

---

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (④)

---

# Target Service

N/A — 공유 `platform/` 스펙.

---

# Implementation Notes

- **"둘 다 맞게 적기" 는 해결이 아니다** — 지금이 정확히 그 상태다(각 파일이 자기 말을 한다). **하나를 고르고, 다른 쪽은 그것을 가리키게 하라.** 정경=규칙 / 나머지=포인터.
- ②는 `event-driven-policy.md` 가 더 상세하고 `entrypoint.md` 의 `event` 태그가 그리로 라우팅한다 ⇒ **그쪽이 정경일 가능성이 높다.** 다만 topic-suffix 대안이 살아있는 관행이면 그걸 **event-driven-policy 에 흡수**하는 게 맞다 — 실제 토픽 이름을 세어 보고 결정하라.

---

# Edge Cases

- **stale 문장을 "정정" 하다 옳았던 기록을 깨뜨릴 수 있다**(`MONO-403` 의 🔵 교훈: *"지금 값과 다르다" ≠ "그때 틀렸다"*). DONE task·ADR 의 역사 본문은 **고치지 않는다** — 살아있는 규칙 표면만 고친다.

---

# Failure Scenarios

- **네 쌍 중 하나를 "합쳐서" 둘 다 만족시키려 한다** → 합성 불가능한 규칙(②)이 더 모호해진다. 완화 = AC-1(하나를 고르고 근거를 적는다).
- **에이전트가 ③을 스스로 결정** → 라이브 API 계약의 범위를 사람 없이 바꾼다. 완화 = AC-3.

---

# Test Requirements

- doc-only. CI GREEN(코드 잡 skip 이 정상임을 확인 — **SKIPPED 를 통과로 읽지 말 것**).

---

# Definition of Done

- [ ] 네 쌍 수렴 + 권위 근거 기록.
- [ ] AC-2/AC-4 코드 조사 결과 기록(0건이면 0건).
- [ ] ③ 사람 승인 여부 기록.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules`. ①은 **직접 실측 확인**, ②③④는 서브에이전트 보고(**PLAUSIBLE — 착수 시 재확인**).

분석=Opus 4.8 / 구현 권장=**Opus**(SoT 판정 + ADR 게이트 판단 + 계약 범위 결정. 문서 편집처럼 보이지만 판단이 본체다).
