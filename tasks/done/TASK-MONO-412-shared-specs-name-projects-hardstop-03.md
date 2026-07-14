# Task ID

TASK-MONO-412

# Title

공유 스펙이 프로젝트 이름을 부른다 — HARDSTOP-03 이 잡아야 할 표면인데 아무도 안 보고 있었다

# Status

done

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

- [x] **AC-0 (재측정)** ①②의 인용이 오늘도 그 자리에 있는지 확인.
- [x] **AC-1** 각 표면에 대해 (A) 예외 등재 또는 (B) 이동 중 하나를 실행하고, **왜 그쪽인지** PR 본문에 적는다. **표의 내용(MONO-368/370 의 기억)은 어느 쪽에서도 소실되지 않는다** — 옮기더라도 포인터를 남긴다.
- [x] **AC-2 (모집단을 세라)** `platform/`·`rules/`·`libs/` 문서에서 **프로젝트명(`wms|scm|fan|iam|ecommerce|finance|erp|platform-console`)이 등장하는 파일을 전수 grep** 해 목록과 건수를 낸다. **각 건이 (a) 정당한 예시 (b) 미선언 예외 (c) 진짜 위반 중 무엇인지 분류만** 하고, 이 task 에서 고치는 것은 ①② 뿐이다. **0건이 아닌 것이 이미 답이다 — 몇 건인지가 다음 티켓의 크기를 정한다.**
- [x] **AC-3 (탐지기에게 물어라)** `.claude/hooks/hardstop-detect.ps1` 의 HARDSTOP-03 술어를 읽고, **①을 입력으로 줬을 때 발화하는가**를 확인한다(읽기만 — 훅 수정 금지). **발화하지 않으면 그 사실을 적는다.** *"규칙이 있다" 와 "탐지기가 본다" 는 다른 문장이다.*
- [x] **AC-4** `platform/README.md § Editing Policy` 의 예외 카탈로그가 AC-2 의 (a)(b) 분류와 **일치**한다(등재 누락 0) — **①②의 범위 내에서.** AC-2 가 추가로 찾은 다른 (b)-후보(abac-data-scope.md, access-conditions.md 등)는 Out-of-Scope 규정("다른 공유 파일 전수 감사는 세기만 한다")에 따라 이 task 에서 등재하지 않고 Implementation Notes 에 다음 티켓 크기 산정용으로 기록한다.

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

## AC-0 재측정 결과

- ① `platform/api-gateway-policy.md` — "Current fleet (2026-07-12)" 표, 인용된 3행 + "No recorded deviations" 단락 **오늘도 그 자리(현재 103–113행)에 있음. Phantom 아님.**
- ② `platform/shared-library-policy.md:20` — `libs/java-gateway` 행의 *"gateway services only (wms, scm, fan, ecommerce). Not iam — its gateway is an independent implementation (ADR-MONO-048 § D2)"* **오늘도 그 자리(현재 20행)에 그대로 있음. 서브에이전트 보고(②)도 실측으로 확인 — phantom 아님.**

## AC-1 — (A)/(B) 결정: 둘 다 (A) 예외 등재

기본값(A)을 그대로 채택했다 — 두 표면 모두 **삭제하면 안 되는 실측/운영 사실**이고(①=MONO-368/370 이 치른 실제 대가, ②=ADR-MONO-048 §D1/D2 의 reactive/servlet 분리 근거), 다른 문서로 옮길 이유(B)가 없다: 둘 다 정확히 그 정책 문서의 본론(rate-limit key shape / shared-lib catalog)에 붙어 있어야 다음에 그 정책을 여는 사람이 사실과 규칙을 한 자리에서 본다. 옮겼다면 포인터가 필요했겠지만, (A)를 선택했으므로 내용 손실은 없다(원문 그대로 두고 예외만 선언).

- `platform/README.md` § Editing Policy "⚠️ 제한적 편집 허용" 목록에 세 번째 불릿 추가: **"운영 인벤토리 (project-agnostic 예외, TASK-MONO-412)"** — ①②를 명시적으로 등재하고, 각각이 규칙이 아니라 감시 가능한 사실을 기록하는 이유를 적음.
- `platform/api-gateway-policy.md` "Current fleet" 표 바로 위에 한 줄 포인터 추가(예외 선언 위치 + TASK-MONO-412 참조).
- `platform/shared-library-policy.md` Catalog 표 바로 위에 동일한 형태의 한 줄 포인터 추가.
- 원문 표/행은 **한 글자도 삭제·이동하지 않았다** — MONO-368/370 의 기억은 그대로 원래 자리에 있다.

## AC-2 — 모집단 분류 (platform/ · rules/ · libs/, 토큰: wms|scm|fan|iam|ecommerce|finance|erp|platform-console)

`libs/**/*.md` = **0건**(0 도 결과 — libs 문서에는 프로젝트명이 전혀 등장하지 않는다). 나머지는 `platform/` + `rules/` 히트를 파일 단위로 읽고 분류(세는 게 아니라 읽었다):

| 파일 | 건수(대략) | 분류 | 근거 |
|---|---|---|---|
| `platform/api-gateway-policy.md` (Current fleet 표 + 주변 단락) | 4 | **(b)→(A)로 해소** | 이 task 의 대상 ①. README 예외 카탈로그에 신규 등재 완료. |
| `platform/shared-library-policy.md:20` (java-gateway 행) | 1 | **(b)→(A)로 해소** | 이 task 의 대상 ②. 동일하게 등재 완료. |
| `platform/glossary.md` (L5, L106) | 2 | **(a) 정당한 예시 — 이미 선언됨** | README 가 이미 "architecture.md·glossary.md 의 예시 서비스 이름"을 명시적으로 허용. |
| `platform/error-handling.md` (`[domain: wms]` 등 섹션 헤더 다수 + 도메인별 코드 표) | ~60+ | **(a) 정당한 예시 — 이미 선언됨** | README 가 "error-handling.md 의 도메인별 에러 코드 섹션"을 명시적으로 허용(도메인마다 자기 섹션에 자기 이름을 다는 것이 설계). |
| `rules/domains/wms.md`, `ecommerce.md`, `erp.md`, `scm.md`, `fan-platform.md`, `fintech.md` (도메인 파일 본문 전체) | 다수(파일당 5–20) | **(a) 설계 그 자체 — 카탈로그 대상 아님** | `rules/README.md`가 이 디렉터리 자체를 domain-axis 규칙 라이브러리로 정의한다(`PROJECT.md`의 `domain:` 값과 정확히 1개 매칭). 도메인명은 "프로젝트 이름"이 아니라 재사용 가능한 **taxonomy 카테고리**(여러 미래 프로젝트가 같은 domain 을 선언할 수 있음) — HARDSTOP-03/예외 카탈로그의 대상이 되는 "특정 프로젝트를 지목하는 산문"과 다른 층위. |
| `rules/taxonomy.md` (도메인 카탈로그 섹션 제목들) | 다수 | **(a) 설계 그 자체** | 위와 동일 논리 — 41-domain 카탈로그의 정의부. |
| `platform/object-storage-policy.md` ("IAM-scoped credentials", "IAM role") | 3 | **(a) — 오탐(동음이의)** | 이건 프로젝트 `iam`이 아니라 **AWS IAM**(Identity and Access Management) 서비스를 가리킨다. 토큰 매칭의 구조적 한계(사람이 읽어야 구분됨) — 이번 감사가 세지 않고 읽어야 하는 이유의 실제 사례. |
| `rules/traits/integration-heavy.md` ("TMS/ERP") | 1 | **(a) — 일반 업계 용어** | 여기서 ERP는 프로젝트가 아니라 일반적인 "Enterprise Resource Planning" 외부 시스템 카테고리를 가리킴(도메인 파일들의 "상류 ERP" 서술과 동일 패턴). |
| `platform/event-driven-policy.md:19` (topic naming 예시 `wms.master.sku.v1`) | 1 | **(a) 정당한 예시(미등재)** | architecture.md/glossary.md 선례와 같은 종류의 "e.g." 일러스트레이션이지만 README 목록엔 아직 없음 — 이번 task 범위 밖(다음 티켓 후보). |
| `platform/testing-strategy.md` (L79 wms 테스트 클래스 예시, L240 iam 인시던트 인용) | 2 | **(a) 일러스트/인시던트 인용(미등재)** | api-gateway-policy.md 의 "(TASK-MONO-368 — ecommerce…)"와 같은 장르 — 티켓 번호로 역사적 근거를 인용. 리포 전역에 반복되는 패턴(별도 축, 아래 참고). |
| `platform/lint-remediation-message-standard.md:75` (`WmsOutboundOrder` / `projects/wms-platform/apps/outbound-service/` 리터럴 경로 예시) | 1 | **(b) 미선언 예외 — 경계 사례** | REMEDIATION 예시 문장이지만 **path-token 형태(`projects/wms-platform/`)를 문자 그대로 포함** — HARDSTOP-03 술어가 실제로 잡는 정확한 모양(§ AC-3). 오늘은 기존 커밋 콘텐츠라 발화하지 않지만, 이 줄을 포함한 새 Edit/Write 가 오면 **발화할 것**. 이번 task 범위 밖(다음 티켓에서 `<service>`/`<entity>` 플레이스홀더로 교체 검토). |
| `platform/abac-data-scope.md` (erp/wms 채택 표) | ~6 | **(b) 미선언 예외 — 티켓이 이미 인지** | 티켓 본문이 명시: "감사가 abac-data-scope.md·access-conditions.md 도 미등재라고 보고했다." Scope-out — 다음 티켓 크기에 포함. |
| `platform/access-conditions.md` (iam pilot 서술) | ~5 | **(b) 미선언 예외 — 티켓이 이미 인지** | 위와 동일. |
| `platform/contracts/notification-inbox-contract.md` (erp/ecommerce/wms/fan 4개 표 + 서술) | ~8 | **(b) 미선언 예외** | fleet 표와 같은 성격의 "per-domain 실측 인벤토리"(ADR-MONO-043). Scope-out. |
| `platform/contracts/jwt-standard-claims.md` (aud/roles 예시 다수: wms/scm/fan/erp/ecommerce/mes) | ~30+ | **경계 사례 — (a)에 가깝지만 미등재** | Worked Example 1–4 가 계약을 이해시키기 위한 구체 예시라는 점에서 architecture.md 선례와 유사. 다만 분량이 많고 "어떤 프로젝트가 어떤 aud 값을 쓰는지"를 사실상 등록하고 있어 (b) 로 볼 여지도 있음 — **다음 티켓에서 사람 판단 필요.** |
| `platform/service-boundaries.md` (L74 "ERP, payment providers…") | 1 | **(a) 일반 용어** | 외부 시스템 카테고리 나열 — 특정 프로젝트 아님. |
| `platform/service-types/identity-platform.md` (wms/ecommerce/erp/mes/scm/fan-platform 예시) | ~6 | **(a) 정당한 예시(미등재)** | service-type 문서가 자신의 규칙(aud 분리, TTL 차등)을 설명하기 위한 예시 나열 — architecture.md 선례와 동종. |

**요약**: platform/rules 전수 중 **직접 고친 것은 ①②(2건, 5줄) 뿐**. 나머지는 (a) 정당한 예시/이미 선언됨/일반 용어/도메인-축 설계가 다수이나, **(b) 미선언 예외 후보가 최소 5개 파일**(`lint-remediation-message-standard.md`, `abac-data-scope.md`, `access-conditions.md`, `notification-inbox-contract.md`, 경계선상의 `jwt-standard-claims.md`) 남아있다 — **다음 티켓의 크기**는 이 5개 파일의 카탈로그 등재 여부(사람 판단 필요, 특히 jwt-standard-claims.md)다.

## AC-3 — HARDSTOP-03 술어가 실제로 보는 것

`.claude/hooks/hardstop-detect.ps1` (L188–247)을 읽었다(수정 안 함). 술어는:

1. 대상 파일이 shared path(`^(platform|rules|\.claude|libs|tasks/templates|docs/guides|CLAUDE\.md|TEMPLATE\.md|build\.gradle|settings\.gradle)`)여야 발동.
2. `projects/<name>/PROJECT.md` 를 가진 디렉터리에서 프로젝트명 + 짧은 별칭(shortAliases: ecommerce/ecom, fan-platform/fan, iam-platform/iam, scm-platform/scm, wms-platform/wms) 목록을 만든다.
3. **정확히 `(?:projects|apps)/<token>/` 형태의 path-token 문자열만** 새 내용(`new_string`, Edit/Write 의 사후 콘텐츠)에서 찾는다. **표의 셀 값, 콤마로 나열된 산문, `|` 로 구분된 프로젝트명 나열은 이 패턴에 절대 안 걸린다** — 정규식이 `projects/` 또는 `apps/` 리터럴 접두어를 요구하기 때문.

**①을 입력으로 주면 발화하는가 — 발화하지 않는다.** "Current fleet" 표의 `wms / scm / fan / finance / erp`, `iam` 같은 셀은 path-token 형태가 아니므로 정규식에 안 걸린다. ②의 `libs/java-gateway` 행("wms, scm, fan, ecommerce")도 동일 이유로 미발화.

**이게 "구멍"인가, "설계"인가 — 설계에 가깝다.** 이 술어가 bare-word 매칭(예: 그냥 `\bwms\b`)으로 넓혀졌다면 `rules/domains/wms.md`·`taxonomy.md`·모든 도메인 파일·이 문서 자신의 예시들까지 전부 오탐이 됐을 것이다 — `MONO-360`("이름만 보고 추측하는 predicate 가 `allowSuperAdminWildcard` 를 `all` 로 오인")과 같은 실패 계열. 술어는 **"프로젝트 디렉터리를 가리키는 경로 리터럴"**이라는, 오탐률이 낮은 좁은 신호만 잡도록 의도적으로 스코프를 좁힌 것으로 읽힌다. 다만 그 대가로 **prose/table 형태의 실제 위반(①②, 그리고 위 (b) 후보들)은 구조적으로 안 보인다** — "규칙이 있다"와 "탐지기가 본다"는 확실히 다른 문장이었다. **이 task 는 술어를 넓히지 않았다**(Out of Scope + 오탐 위험 — `platform/lint-remediation-message-standard.md:75` 처럼 이미 path-token 형태를 포함한 정당한 문서 예시가 존재해, 섣불리 넓히면 그 파일부터 오탐이 난다).

## 티켓에서 판명된 거짓/부정확 사항

- 없음. Goal 의 두 인용 모두 오늘 실측 확인됨(①은 재확인, ②는 서브에이전트 보고를 실측으로 승격). Provenance 섹션의 "① 직접 실측, ② PLAUSIBLE" 구분은 정확했다.

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

- [x] ①② 처리 + 근거 기록.
- [x] AC-2 전수 분류표(건수 + (a)(b)(c)) PR 본문 게재.
- [x] AC-3 훅 술어 발화 여부 기록.
- [ ] `tasks/INDEX.md` done entry(close chore) — 별건 close-chore 로 처리(review → done).

---

# Provenance

2026-07-15 `/validate-rules`. ①은 **직접 실측 확인**(표 본문 인용), ②는 서브에이전트 보고(PLAUSIBLE).

분석=Opus 4.8 / 구현 권장=**Sonnet**(판단 기준이 티켓에 다 적혀 있고, 남은 건 전수 분류와 예외 등재. 단 AC-1 의 (A)/(B) 결정이 애매하면 사람에게 물을 것).
