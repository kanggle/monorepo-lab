# Task ID

TASK-MONO-375

# Title

`ADR-MONO-049` 의 카운트는 **세 번째로 낮다** — 그대로 ACCEPT 하면 ADR 이 자기 논지를 자기에게 실행한다

# Status

done

# Owner

monorepo

# Task Tags

- adr
- measurement
- security
- shared-library

---

# Dependency Markers

- **대상**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — **`PROPOSED`. 이 task 는 문서를 정정할 뿐 승격하지 않는다.** `PROPOSED → ACCEPTED` 는 사람의 게이트다(정확형 intent 요구). **self-ACCEPT 금지.**
- **선행 (머지됨)**: `TASK-MONO-364`(ADR 초안) · `TASK-MONO-365`(§ 1.5 정정 — `libs/java-security` 는 이미 존재) · `TASK-MONO-363`(ADR 인덱스 가드 — 이 task 후에도 GREEN 이어야 한다).

---

# Goal

ADR-049 는 **자기 서두에서** 이렇게 적는다:

> *"`ADR-MONO-048` § 1.1, whose duplication count was low twice over (4 copies → the corrected 10 → the measured **18**)"*

**세 번째로 낮다. 그리고 이번엔 이 ADR 이 틀린 쪽이다.**

## 실측 (`origin/main` `1929c2627`, 2026-07-12)

| 클래스 | 복사본 | 라인 | 정규화 후 서로 다른 body |
|---|---|---|---|
| `AllowedIssuersValidator` | **18** | 760 | **1** — 18개 전부 동일 |
| `TenantClaimValidator` | **18** | 1,409 | **10** |
| `TenantClaimEnforcer` | **13** | 1,353 | **8** |
| | **49** | **3,522** | |

**6개 프로젝트 20개 서비스**(erp 4 · fan 4 · wms 5 · finance 2 · scm 3 · iam 2).

ADR § 1.1 은 **finance(2) + erp(4) = 6 서비스**만 세고, 그것을 *"the real shape"* 라 부른다. ⇒ **복사본의 37%, 서비스의 30%.**

wms/fan/scm/iam 의 `AllowedIssuersValidator` 는 finance 것과 **정규화 후 완전 일치** — 동명이인이 아니라 같은 클래스다.

## 그리고 § 1.2 fact 2 (*"Behavioural drift: zero"*) 는 **자기 범위 안에서만** 참이다

함대의 servlet tenant gate 는 **4가지 정책**을 구현한다:

| 프로젝트 | 와일드카드 `"*"` | `entitled_domains` |
|---|---|---|
| wms | ❌ | ✅ |
| fan | ✅ | ❌ |
| scm / finance / erp | ✅ | ✅ |
| iam | ❌ | ❌ (strict) |

⇒ **D4 의 전제가 거짓이다**: *"All six servlet `TenantClaimValidator` copies implement exactly …"* 는 finance/erp 에만 참이다.

## 🟢 그러나 **라이브 결함은 없다** — 과장하면 진짜 논거까지 죽는다

각 프로젝트의 **servlet 정책이 자기 게이트웨이 정책과 정확히 일치**한다(주석 제거 후 builder 호출 실측):

| 프로젝트 | 게이트웨이 | servlet | |
|---|---|---|---|
| wms | entitled, no wildcard | entitled, no wildcard | ✅ |
| fan | wildcard, no entitled | wildcard, no entitled | ✅ |
| scm / finance / erp | wildcard + entitled | wildcard + entitled | ✅ |

**이중방어의 두 겹이 모든 프로젝트에서 같은 말을 한다.** 18개 복사본의 발산은 **의도된 프로젝트별 정책**이지 부패가 아니다.

> ⚠️ 1차 탐지식은 `grep -c` 로 원문을 세어 **wms 게이트웨이를 오검출**했다 — 그 Javadoc 이 *"wildcard 를 거부한다"* 고 **설명**하기 때문이다. **주석을 걷어내고 실제 builder 호출만 세어야 한다.** (MONO-368 의 I4 오탐과 같은 클래스.)

---

# Scope

## In Scope

1. **§ 1.6 신설** — 재측정 + 4정책 표 + 게이트웨이 미러링 확인 + 9개 무테스트 복사본 열거.
2. **본문의 거짓 수치에 정정 포인터** — Decision driver · **D4**(12 edges → **26 edges**, 1정책 → 4정책) · **D5**(20 중 6만 통합 ⇒ **14 잔존**) · **§ 4/4.1**(모든 수치가 § 1.1 범위 한정).
3. **`docs/adr/INDEX.md`** 요약 행 갱신(18 → 49).

## Out of Scope

- **`PROPOSED → ACCEPTED` 승격** — 사람의 게이트. **이 task 는 문서를 정정할 뿐이다.**
- **범위 결정 자체** — D5 를 20개 서비스로 넓힐 것인가, 아니면 14개를 남기는 이유를 명시할 것인가. **§ 3 이 결정할 일이고, § 3 은 아직 `PROPOSED` 다.** 이 task 는 **그 선택이 진짜 숫자를 보고 이뤄지게** 할 뿐이다.
- **구현** — ACCEPT 전 착수는 HARDSTOP-09.

---

# Acceptance Criteria

- [x] **AC-1** — 49/3,522/20서비스/6프로젝트가 실측으로 기록됐고, 동일 클래스임이 **정규화 body 비교**로 확인됐다.
- [x] **AC-2** — § 1.2 fact 2 의 범위 한정이 명시됐고, **4정책**이 표로 기록됐다.
- [x] **AC-3** — **라이브 결함 없음**(servlet ↔ gateway 미러링)이 확인·기록됐다. **과장하지 않았다.**
- [x] **AC-4** — D4/D5/§4/Decision-driver 에 정정 포인터가 붙어, **본문 어느 줄도 홀로 읽혀 거짓을 말하지 않는다.**
- [x] **AC-5** — ADR Status 가 **`PROPOSED` 그대로**다. `check-adr-index-drift.sh` **GREEN**(Status/Date 일치).
- [x] **AC-6** — 9개 무테스트 복사본이 **이름으로** 열거됐다(§ 1.3 의 `finance/ledger-service` 지목은 **참**으로 확인 — 3개 클래스 전부 보유, 테스트 0).

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` (§ 1.1 · § 1.2 · § 1.6 · D4 · D5 · § 4)
- `docs/adr/INDEX.md`
- `libs/java-gateway/.../security/TenantClaimValidator.java` — **이미 builder 로 파라미터화**(MONO-355). servlet 복사본들은 **게이트웨이가 파라미터화한 것을 하드코딩**했다.
- `scripts/check-adr-index-drift.sh` (MONO-363)

# Related Contracts

없다 — 문서 정정.

---

# Edge Cases

- **"49개니까 더 시급하다" 로 읽지 말 것** — 시급성은 변하지 않았다(**라이브 결함 0**). 바뀐 것은 **범위와 비용**이다. 그리고 `AllowedIssuersValidator` 18개가 **전부 동일**하다는 사실은, **6개만 통합하면 12개의 동일 복사본이 남는다**는 뜻이다 — 그게 D5 의 문제다.
- **D4 의 builder 는 여전히 옳다** — 4정책을 표현할 수 있는 **유일한** 형태이기 때문이다. 틀린 것은 메커니즘이 아니라 **위험 진술**(12 edges / 단일 정책)이다.

# Failure Scenarios

- **§ 1.6 만 붙이고 본문을 안 고친다** → D4·D5·§4 를 **홀로 읽는 사람**이 여전히 거짓을 읽는다. 이 저장소가 계속 만나는 stale-declaration 그 자체. Guard: AC-4.
- **ACCEPT 를 같이 해버린다** → 사람의 게이트를 에이전트가 통과시키는 것. **금지.** Guard: AC-5.
- **드리프트를 결함으로 과장한다** → § 1.2 가 스스로 경고한 것(*"the exaggeration would take the real argument down with it"*). 실제로 미러링은 정합했다. Guard: AC-3.

---

# Provenance

발굴 2026-07-12 — 사용자가 *"ADR-049 는 언제 하는 게 좋냐"* 고 물었다. 답하려고 **범위를 처음으로 실제로 재봤고**, 그 과정에서 카운트가 세 번째로 낮다는 것이 나왔다.

**패턴이 산술 문제가 아니라는 것이 요점이다: 매 패스가 *직전 task 가 서 있던 자리* 만 봤다.** ADR-048 은 게이트웨이 4개를 보고 있었고, MONO-361 은 finance/erp 를 보고 있었고, ADR-049 는 361 의 범위를 물려받으며 *"이게 전부인가"* 를 다시 묻지 않았다. § 1.5 는 *"집이 이미 있는가"* 를 물었지만 아무도 *"모집단이 이게 전부인가"* 를 묻지 않았다.

분석=Opus 4.8 / 구현 권장=—(문서 정정 완료. **다음 행동은 사람의 ACCEPT 판단**).
