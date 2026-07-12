# Task ID

TASK-MONO-377

# Title

`ADR-MONO-049` **ACCEPTED — 범위 A**(20개 서비스 전체). 그리고 범위를 정하려고 처음으로 **서비스별 전수**를 셌더니, 이번엔 카운트가 **내려갔다**

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

- **대상**: [`ADR-MONO-049`](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) — `PROPOSED` → **`ACCEPTED`**.
- **게이트**: `PROPOSED → ACCEPTED` 는 **사람의 게이트**다. 사용자가 *"ADR-MONO-049 ACCEPT (범위 A)"* 를 **명시적으로 선택**했다(선택지가 ADR 번호·전이·범위를 담고 있었고, *"아무것도 안 열고 대기"* 를 포함한 대안 중에서 골랐다). **에이전트 self-ACCEPT 아님.**
- **선행 (머지됨)**: `TASK-MONO-364`(ADR 초안) · `TASK-MONO-365`(§1.5 — `libs/java-security` 는 이미 존재) · `TASK-MONO-375`(§1.6 — 카운트가 세 번째로 낮다) · `TASK-MONO-363`(ADR 인덱스 가드).
- **후속**: `TASK-MONO-378` (D5-1). 나머지 D5-2~D5-8 은 **선행 단계가 랜딩한 뒤에** 티켓팅한다.

---

# Goal

`TASK-MONO-375` 가 §1.6 에서 물었다: **D5 를 20개 서비스 전체로 넓힐 것인가, 아니면 14개를 남기는 이유를 명시할 것인가.**

**답: 범위 A — 전체.** 근거는 §1.6 이 이미 적어둔 그대로다:

> `AllowedIssuersValidator` 의 **18개 사본이 정규화 후 전부 동일**하다. 6개만 옮기면 **이 ADR 이 방금 정경(canonical)이라 선언한 클래스의 동일 복사본 12개**가 남는다. **ADR 이 자기 논지를 자기에게 실행하는 것이다.**

finance+erp 에 선을 긋는 것은 코드의 성질이 아니라 **직전 task 가 어디에 서 있었는가의 흔적**이다.

## 그런데 범위를 정하려고 세어봤더니 — **아무도 20개를 열거한 적이 없었다**

§1.6 은 **프로젝트별 합계**만 줬다(erp 4 · fan 4 · wms 5 · finance 2 · scm 3 · iam 2). **서비스별 매트릭스는 이 task 가 처음 만들었고, 균일한 스윕이 아니었다:**

| 프로젝트 | `AllowedIssuersValidator` + `TenantClaimValidator` | `TenantClaimEnforcer` |
|---|---|---|
| erp | 4 | 4 |
| fan | 4 | 4 |
| finance | 2 | 2 |
| **scm** | **1** (procurement 만) | **3** |
| **wms** | **5** | **0** |
| **iam** | **2** | **0** |
| | **18** | **13** |

- **wms(5)·iam(2) 은 `TenantClaimEnforcer` 를 아예 안 갖는다** ⇒ servlet 모듈을 **영원히 배선하지 않는다**.
- **scm 의 demand-planning·inventory-visibility 는 Enforcer 만** 갖는다 ⇒ validator 이관이 **없다**.

**단계 목록을 프로젝트 리스트에서 유도했다면 이 둘을 다 틀렸을 것이다.**

## 그리고 추정 두 개가 더 틀렸다 — **이번엔 안전한 방향으로**

| | ADR 이 말한 것 | 실측 (`ae54af58d`) |
|---|---|---|
| **새** `java-security` 배선이 필요한 서비스 | §1.6: *"6 → 20"* | **1개** — `wms/admin-service` 가 20개 중 **유일하게** `build.gradle` 에 `libs:java-security` 가 없다 |
| `java-security-servlet` 배선 대상 | §1.6: *"6 → 20"* | **13개** — Enforcer 보유자만 |
| 20개의 프레임워크 | *(servlet 가정)* | **✅ 20개 전부 servlet.** reactive 서비스는 사본을 **하나도** 안 갖는다 |

⇒ **범위 A 는 ADR 자신의 최악 추정보다 싸다.** 삭제는 3배(18 → **49**)지만 **빌드 그래프 비용은 거의 안 움직인다**: 새 모듈 **1개**, `settings.gradle` **1줄**, `java-security-servlet` **13줄**, 새 `java-security` **1줄**.

> **이 혈통에서 카운트가 움직인 게 네 번째이고, 내려간 건 처음이다.** 내려간 이유는 단 하나 — **직전 문서에서 물려받지 않고 코드에 대고 쟀기 때문**이다. 그게 이 ADR 전체의 교훈이고, 여기서 한 번 더 성립했다.

---

# Scope

## In Scope

1. **`Status: PROPOSED → ACCEPTED`** + `History` 줄(`Date` 는 **안 움직인다** — PROPOSED 날짜다, MONO-369 규약).
2. **§1.7 신설** — 범위 결정 + 서비스별 전수 매트릭스 + 추정 3건 정정 + *"무엇이 정해졌고 무엇이 아닌가"*.
3. **D5 재작성** — 4단계(6서비스) → **8단계(20서비스)**. 단계는 **§1.7 의 매트릭스에서** 유도한다(프로젝트 리스트가 아니라).
4. **§D6 의 stale 선언 정정** — *"wms rate-limit keying — 사람 대기"* 는 **이미 해결됐다**(`TASK-MONO-368`/`370`). **ADR 의 non-goal 목록 자체가 stale declaration 이었다** — 이 ADR 이 논박하려는 바로 그 클래스.
5. **§4 에 정정 포인터**, **§7 로드맵**을 범위 A 로 교체.
6. **`docs/adr/INDEX.md`** Status 셀 `PROPOSED → ACCEPTED`.
7. **`TASK-MONO-378`**(D5-1) 만 `ready/` 에 생성.

## Out of Scope

- **구현** — 코드 0줄. 이 task 는 **결정 기록**이다.
- **D5-2~D5-8 티켓팅** — **선행이 랜딩한 뒤에.** 8단계는 전부 `libs/` 와 `settings.gradle` 을 건드리므로 **구조적으로 직렬**이다. `ready/` 에 8개를 쌓으면 **착수 불가능한 task 8개**가 되고, 동시 세션 둘이 집어서 충돌한다. (`project_shared_file_task_series_single_worktree_serialize`)
- **iam 게이트웨이** — §D6, `TASK-MONO-365` 가 감사해 방어 가능한 설계로 판정. ⚠️ **iam 전체를 제외하는 것으로 읽지 말 것**: iam 의 **servlet 서비스**(community·membership)는 **범위 안**이다(D5-8, 사본 4개).

---

# Acceptance Criteria

- [x] **AC-1** — ADR `Status` 가 `ACCEPTED`. `Date` 는 **2026-07-12 그대로**(전이일이 아니라 제안일). `History` 줄이 전이를 기록한다.
- [x] **AC-2** — `check-adr-index-drift.sh` **GREEN** (53 ADR, Status·Date 전부 일치).
- [x] **AC-3** — §1.7 이 **서비스별** 매트릭스를 담는다(프로젝트별 합계가 아니라). wms·iam 의 Enforcer 부재와 scm 2개의 validator 부재가 **명시**된다.
- [x] **AC-4** — D5 의 8단계가 **§1.7 매트릭스에서 유도**됐다: wms·iam 은 servlet 모듈을 배선하지 않고, scm 2개는 validator 를 이관하지 않는다. 사본 합계가 **49** 로 떨어진다(6+12+5+12+10+4).
- [x] **AC-5** — 본문 어느 줄도 **홀로 읽혀 거짓을 말하지 않는다**: §4 의 finance/erp 한정 수치에 정정 포인터, §D6 의 wms rate-limit 항목에 해결 표시, §7 로드맵 교체.
- [x] **AC-6** — `ready/` 에 **D5-1 하나만** 생성. 나머지는 §7 로드맵에만 존재한다.

---

# Related Specs

- `docs/adr/ADR-MONO-049-framework-neutral-security-library.md` (§1.6 · **§1.7** · D5 · D6 · §4 · §7)
- `docs/adr/INDEX.md`
- `platform/shared-library-policy.md` § Change Rule (HARDSTOP-09 게이트의 근거)
- `libs/java-security` (이미 존재 — `settings.gradle:17`) · `libs/java-gateway`
- `scripts/check-adr-index-drift.sh`

# Related Contracts

없다 — 결정 기록.

---

# Edge Cases

- **"49개니까 더 시급하다" 로 읽지 말 것.** 시급성은 안 변했다 — **라이브 결함 0**(각 프로젝트의 servlet 정책이 자기 게이트웨이와 정확히 미러링한다, `TASK-MONO-375` AC-3). 바뀐 것은 **범위와 비용**이다.
- **D5-1 은 servlet 서비스를 하나도 안 건드린다.** 게이트웨이 6개는 이미 `java-security` 에 의존하므로 **새 배선 0**. 그래서 첫 단계가 가장 안전하고, **게이트웨이 6개 스위트가 곧 증명**이다.
- **정책 4형태를 사본으로 보존하지 말 것.** MONO-355 의 builder 로 **표현**한다 — 그게 4형태를 표현 가능하게 만드는 유일한 형태다(§D4).

# Failure Scenarios

- **범위 A 를 승인해놓고 D5-3/D5-4 만 하고 멈춘다** → §1.6 이 경고한 바로 그 상태(동일 사본 12개 잔존). Guard: §7 이 8단계를 명시하고 각 단계가 사본 수를 못 박는다.
- **8개 task 를 한꺼번에 `ready/` 에 넣는다** → 전부 `libs/` 를 건드려 **직렬**인데 큐는 병렬로 보인다 → 동시 세션 둘이 집어서 충돌. Guard: AC-6.
- **iam 을 통째로 제외한다** — §D6 의 *"iam 게이트웨이"* 를 *"iam"* 으로 읽으면 사본 4개가 조용히 남는다. Guard: §1.7·§7 이 **게이트웨이 ≠ servlet 서비스**를 명시한다.

---

# Provenance

발굴 2026-07-13 — 사용자가 큐가 빈 상태에서 *"무엇을 열까"* 를 묻자 **범위 A 를 선택**했다.

**그리고 결정을 기록하려고 범위를 실제로 재봤더니, 아무도 20개를 열거한 적이 없다는 것이 나왔다** — §1.6 은 프로젝트별 합계까지만 갔고, 거기서 멈춘 이유는 그것으로 *"6 of 20"* 이라는 논증이 성립했기 때문이다. **논증에 충분한 만큼만 세고 멈춘 것이다.** 그 다음 줄(단계 목록)을 쓰려면 서비스별이 필요했고, 세어보니 **비균일**이었다.

**`TASK-MONO-375` 의 교훈이 여기서 한 번 더, 그러나 반대 부호로 반복됐다**: 물려받은 추정 두 개(*"6 → 20"* 배선)가 **과대**였다. 카운트는 세 번 낮았고 이번엔 **높았다**. 방향이 문제가 아니다 — **재지 않은 것이 문제다.**

분석=Opus 4.8 / 구현 권장=— (결정 기록 완료. **다음 행동 = `TASK-MONO-378` (D5-1)**).
