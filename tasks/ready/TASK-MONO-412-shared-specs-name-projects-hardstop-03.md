# Task ID

TASK-MONO-412

# Title

공유 스펙이 프로젝트 이름을 부른다 — HARDSTOP-03 이 잡아야 할 표면인데 아무도 안 보고 있었다

# Status

ready

# Owner

monorepo

# Task Tags

- chore
- adr

---

# Goal

`CLAUDE.md:28` 은 공유 계층(`platform/`, `rules/`, `.claude/`, `libs/`, …)이 **project-agnostic** 이어야 한다고 선언한다 — *"no service names, API paths, domain entities"* — 그리고 **HARDSTOP-03** 이 그것을 강제한다(`.claude/hooks/hardstop-detect.ps1` 이 자동 발화 대상으로 명시).

그런데 `/validate-rules`(2026-07-15) 가 **공유 스펙 안에서 프로젝트 이름을 부르는 표면 2곳**을 찾았다 (①은 실측 확인):

**① `platform/api-gateway-policy.md:103-113` — "Current fleet (2026-07-12)" 표**
```
| ecommerce | ip | t:<tenant>:acct:<sub> | ... (MONO-368) |
| wms / scm / fan / finance / erp | ip | acct:<sub> | conforms |
| iam | ip (login/signup) | acct:<sub> (refresh) | ... |
```
**7개 프로젝트 이름 + 각자의 키 전략 + 티켓 번호**가 공유 스펙 본문에 박혀 있다.

**② `platform/shared-library-policy.md:20`** — `libs/java-gateway` 카탈로그 행: *"gateway services only (**wms, scm, fan, ecommerce**). Not **iam** — its gateway is an independent implementation."*

**🔴 그런데 이 표가 *나쁘다* 는 게 이 티켓의 결론이 아니다.** 표는 **값지다** — `MONO-368/370` 이 실제로 치른 대가의 기록이고, *"No recorded deviations"* 라는 문장은 감시 가능한 사실이다. 진짜 질문은 **그 값진 것이 왜 규칙이 금지한 자리에 앉아 있는가** 이고, 답은 둘 중 하나다:

- **(A) 예외를 선언한다** — `platform/README.md § Editing Policy` 는 이미 **예외 카탈로그**를 갖고 있다(`architecture.md`/`glossary.md` = 예시용 서비스명 허용, `error-handling.md` = 도메인 에러 섹션 허용). **이 표가 정당하다면 그 카탈로그에 등재돼야 한다.** 지금은 규칙이 금지하고, 파일이 어기고, 예외는 선언되지 않은 **셋 다 참인 상태**다.
- **(B) 옮긴다** — fleet 인벤토리는 운영 사실이지 정책이 아니다 ⇒ 프로젝트 문서(또는 `docs/`)로.

**⚠️ 그리고 이 티켓의 진짜 발견은 세 번째다: HARDSTOP-03 훅이 이걸 안 잡았다.** 규칙이 있고, 자동 탐지기가 있고, 위반이 본문에 있는데 **신호가 0** 이었다. **탐지기가 못 보는 위반은 위반이 아닌 것처럼 보인다**(`MONO-402`·`MONO-405`·`MONO-407` 과 같은 축).

---

# Scope

## In Scope

1. **(A)/(B) 결정** — 두 표면 각각에 대해. **기본값은 (A)**: 표는 값지고, 지우면 `MONO-368/370` 의 기억이 사라진다. 예외를 **`platform/README.md § Editing Policy` 에 등재**하고 *왜* 예외인지(운영 인벤토리 = 감시 가능한 사실, 규칙이 아님) 적는다.
2. **HARDSTOP-03 훅의 술어를 조사한다(AC-3)** — 훅이 `platform/**` 본문의 프로젝트명을 **보는가, 안 보는가.** 안 본다면 그게 설계인지(문서는 대상 외) 구멍인지 판정하고 **결과를 기록**한다.
3. `platform/README.md § Editing Policy` 의 예외 카탈로그가 **오늘 실제로 예외인 파일 전부**를 담는지 확인 — 감사가 `abac-data-scope.md`·`access-conditions.md`(둘 다 도메인 채택 표를 갖고 자기 입으로 *"illustrate, not define"* 이라 선언)도 미등재라고 보고했다.

## Out of Scope

- **훅 수정.** `.claude/hooks/` 는 에이전트 하드블록이고, 술어를 넓히면 **오탐이 첫날 RED** 를 만들 수 있다(`MONO-360`: 꺼진 가드는 없는 가드보다 나쁘다). **이 task 는 훅의 현재 술어를 *알아내고 기록*할 뿐이다.** 고칠지는 그 기록 위에서 별건으로 결정한다.
- 다른 공유 파일 전수 감사 → AC-2 가 **세기만** 한다(고치지 않는다).

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** ①②의 인용이 오늘도 그 자리에 있는지 확인.
- [ ] **AC-1** 각 표면에 대해 (A) 예외 등재 또는 (B) 이동 중 하나를 실행하고, **왜 그쪽인지** PR 본문에 적는다. **표의 내용(MONO-368/370 의 기억)은 어느 쪽에서도 소실되지 않는다** — 옮기더라도 포인터를 남긴다.
- [ ] **AC-2 (모집단을 세라)** `platform/`·`rules/`·`libs/` 문서에서 **프로젝트명(`wms|scm|fan|iam|ecommerce|finance|erp|platform-console`)이 등장하는 파일을 전수 grep** 해 목록과 건수를 낸다. **각 건이 (a) 정당한 예시 (b) 미선언 예외 (c) 진짜 위반 중 무엇인지 분류만** 하고, 이 task 에서 고치는 것은 ①② 뿐이다. **0건이 아닌 것이 이미 답이다 — 몇 건인지가 다음 티켓의 크기를 정한다.**
- [ ] **AC-3 (탐지기에게 물어라)** `.claude/hooks/hardstop-detect.ps1` 의 HARDSTOP-03 술어를 읽고, **①을 입력으로 줬을 때 발화하는가**를 확인한다(읽기만 — 훅 수정 금지). **발화하지 않으면 그 사실을 적는다.** *"규칙이 있다" 와 "탐지기가 본다" 는 다른 문장이다.*
- [ ] **AC-4** `platform/README.md § Editing Policy` 의 예외 카탈로그가 AC-2 의 (a)(b) 분류와 **일치**한다(등재 누락 0).

---

# Related Specs

- `CLAUDE.md` § Repository Layout("Shared vs project boundary") + § Hard Stop Rules(HARDSTOP-03)
- `platform/hardstop-rules.md#hardstop-03`
- `platform/README.md § Editing Policy` (**예외 카탈로그 — 정경**)
- `platform/api-gateway-policy.md` · `platform/shared-library-policy.md`
- `tasks/done/TASK-MONO-368-*` / `TASK-MONO-370-*` (표가 기록하는 인시던트)

# Related Skills

N/A.

---

# Related Contracts

None.

---

# Target Service

N/A — 공유 `platform/`.

---

# Implementation Notes

- **값진 것을 지우지 마라.** *"No recorded deviations"* 와 *"`RATELIMIT_IP_ONLY_ALLOWLIST` 는 비어 있고 그대로 두라"* 는 **감시 가능한 사실**이다. 규칙 위반의 처방은 삭제가 아니라 **올바른 집을 주는 것**이다(`MONO-404` 가 가드 규칙에 정경 홈을 준 것과 같은 형태).
- **예외를 선언하는 것도 규칙을 지키는 것이다** — `error-handling.md` 의 도메인 레지스트리가 그 선례다.

---

# Edge Cases

- **AC-2 의 grep 이 주석·예시를 센다**(`grep -c` 가 Javadoc 을 세던 그 클래스). **세지 말고 읽어라** — 분류가 목적이다.
- `architecture.md`/`glossary.md` 의 "예시용 서비스명" 은 **이미 허용**돼 있다. 그것을 위반으로 세면 오탐이다.

---

# Failure Scenarios

- **표를 지워서 "규칙 준수" 를 달성** → `MONO-368/370` 의 대가가 저장소에서 사라진다. 완화 = AC-1(내용 보존 + 포인터).
- **훅 술어를 이 task 에서 넓힌다** → 오탐 → 가드가 꺼진다. 완화 = Out of Scope + AC-3(조사만).
- **AC-2 를 "고치는 것" 으로 확장** → 범위 폭발. 완화 = 분류만.

---

# Test Requirements

- doc-only. CI GREEN 확인.

---

# Definition of Done

- [ ] ①② 처리 + 근거 기록.
- [ ] AC-2 전수 분류표(건수 + (a)(b)(c)) PR 본문 게재.
- [ ] AC-3 훅 술어 발화 여부 기록.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules`. ①은 **직접 실측 확인**(표 본문 인용), ②는 서브에이전트 보고(PLAUSIBLE).

분석=Opus 4.8 / 구현 권장=**Sonnet**(판단 기준이 티켓에 다 적혀 있고, 남은 건 전수 분류와 예외 등재. 단 AC-1 의 (A)/(B) 결정이 애매하면 사람에게 물을 것).
