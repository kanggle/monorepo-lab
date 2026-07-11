# Task ID

TASK-MONO-354

# Title

레포 위생 + **거짓 문서 진술** 2건 — 커밋된 JVM 크래시 덤프 4개(1.06MB) 제거·gitignore, `PostVisibility` 가 "PREMIUM 게이트는 always-pass" 라고 **거짓 서술**, `access-conditions.md` 가 `TIME_WINDOW` 를 단서 없이 "implemented" 로 공표

# Status

ready

# Owner

monorepo

# Task Tags

- chore
- docs
- security

---

# Goal

미티켓 백로그 3차원 발굴의 잔여 S 3건. 서로 다른 영역이지만 **한 가지 성질을 공유**한다 — **문서/레포가 현실과 다른 말을 하고, 그 거짓이 반복적으로 비용을 물린다.**

## 1 — 커밋된 JVM 크래시 덤프 4개 (레포 오염)

```
libs/java-observability/hs_err_pid20280.log      92,923 B
libs/java-observability/hs_err_pid26436.log      92,224 B
libs/java-observability/replay_pid20280.log     396,269 B
libs/java-observability/replay_pid26436.log     479,923 B
                                        총 ≈ 1.06 MB
```

**진짜 크래시 덤프다** — 첫 줄이 `There is insufficient memory for the Java Runtime Environment to continue.` **최초 커밋 `e839bb26b "init wms-platform"` 에 딸려 들어왔고**, 레포 어디에서도 **참조되지 않으며**(전수 grep), `.gitignore` 도 이들을 막지 않는다.

`libs/java-observability` 는 **관측성 라이브러리** 디렉터리다. 그 안에 남의 JVM OOM 덤프가 4개 들어 있는 것은 그 자체로 오독을 부른다(픽스처인가? 재현 자료인가? — 둘 다 아니다).

**재발 방지가 본질**: 이 파일들은 이 Windows 호스트에서 **반복 발생하는 알려진 해저드의 지문**이다(JDT.LS OOM 캐스케이드 → `hs_err_pid*` / `replay_pid*` 가 레포에 흩뿌려짐). `.gitignore` 항목이 없으면 **다음 크래시 때 또 커밋된다.** 삭제만 하고 끝내면 안 된다.

## 2 — `PostVisibility` javadoc 이 보안 태세를 **거짓 서술**한다

`projects/fan-platform/apps/community-service/.../domain/post/PostVisibility.java:13-15`:

> `PREMIUM` — paid subscribers only. **v1 has no membership-service** (TASK-FAN-BE-002 § Out of Scope) so **the gate is effectively always-pass with a TODO + WARN log**; a follow-up task will integrate a real check.

**거짓이다.** 실제 시행 지점 `PostAccessGuard:21-23` 은 정반대를 말한다:

> `PREMIUM` — author + premium members verified by `MembershipChecker` (**TASK-FAN-BE-010 hard fail-close**; required tier `PREMIUM`). Tier hierarchy is resolved in membership-service.

즉 게이트는 **닫혀 있고 hard fail-close** 인데, **도메인 enum**(독자가 가장 먼저 닿는 파일)이 **열려 있다고 서술**한다. 후속 task(FAN-BE-010)가 실제 검사를 붙였으나 이 javadoc 만 갱신되지 않았다.

**이게 왜 단순 stale docstring 이 아닌가**: 이 문장은 **보안 태세에 대한 진술**이다. 백로그 발굴이 돌 때마다 "PREMIUM 게이트가 열려 있다" 는 **가짜 보안 갭**으로 재부상해 **매번 재조사 비용**을 물린다(본 세션 포함 최소 2회). 거짓 진술이 **반복적으로** 사람을 잘못된 조사로 유인하고 있다.

## 3 — `access-conditions.md` 가 `TIME_WINDOW` 를 단서 없이 "implemented" 로 공표

`platform/access-conditions.md:23`:

```
| `TIME_WINDOW` | request time + zone | request-time within an allowed local time-of-day / day-of-week window | **implemented** (`TimeWindowCondition`) — iam pilot composed with `SOURCE_IP` (ADR-028) |
```

**빠진 것**: `ADR-MONO-028 § D3` 이 **ACCEPTED 시점에 고정**한 제약 — *"**midnight-wrap stays deferred** to a fast-follow (same-day `start < end` only in the pilot)"*. 평가기(`TimeWindowCondition:100`)는 `s.isBefore(e)` 를 요구하고, 아니면 `valid=false` → `isSatisfiedBy` 가 **전면 거부**(fail-closed).

**코드는 옳다** — javadoc·ADR·`admin-service/application.yml:84-85` 주석이 전부 이 제약을 명시한다. **계약 표만** 단서를 빠뜨렸다. 계약을 근거로 야간 유지보수 창(`22:00`–`06:00`)을 설정하려는 사람은 그것이 **거부된다는 사실을 계약에서 알 수 없다**.

> **주의 — 과대평가 금지.** 이 항목은 본 발굴에서 처음에 "라이브 보안설정의 침묵 전면 차단" 으로 **1순위**에 올렸다가, javadoc·ADR·yml 을 끝까지 읽고 **의도적·문서화된 유예**임이 확인되어 **강등**된 건이다. 남는 결손은 **계약 표 한 행의 단서 누락**뿐이며, midnight-wrap 실제 구현은 **ADR sub-decision 을 되여는 일 = 사람 결정**이므로 본 task 범위 밖이다.

---

# Scope

## IN

**1. 크래시 덤프 (root)**
- `git rm` — `libs/java-observability/{hs_err_pid20280,hs_err_pid26436,replay_pid20280,replay_pid26436}.log`
- `.gitignore` — `hs_err_pid*.log` / `replay_pid*.log` 패턴 추가(재발 방지). 근거 주석 포함.

**2. `PostVisibility` javadoc (fan-platform)**
- `PREMIUM` 서술을 실제 동작(`PostAccessGuard` = **hard fail-close**, `MembershipChecker` 검증, FAN-BE-010)에 맞춘다.
- 거짓 문장 삭제로 끝내지 말고 **왜 이 오해가 반복됐는지** 를 남긴다 — 다음 발굴이 또 "보안 갭" 으로 재조사하지 않도록.
- **코드 무수정**(동작은 이미 옳다).

**3. `access-conditions.md` (root)**
- `TIME_WINDOW` 행에 **same-day 제약 + midnight-wrap 유예(ADR-028 § D3)** 를 명시. fail-closed 임을 적는다.
- **코드 무수정.**

## OUT (의도적 제외 — 근거 포함)

- **midnight-wrap 실제 구현 금지.** ADR-028 § D3 이 ACCEPTED 시점에 **"deferred" 로 FIX** 했다. 되여는 것은 ADR 변경 = **사람 결정**(MONO-347/350 과 동일 성격). 계약에 단서를 다는 것과 유예를 뒤집는 것은 다른 일이다.
- **`libs/java-security` 에 로거 추가 금지** — `TimeWindowCondition` 이 오설정 시 조용히 fail-closed 되는 것을 WARN 으로 알리고 싶은 유혹이 있으나, `java-security` 는 **의도적으로 slf4j 의존이 없는 경량 라이브러리**다. 의존성 추가는 별건이고, 오설정 경고는 이미 **설정 지점**(`application.yml:84-85` 주석)에 있다.
- **git history 재작성 금지.** 덤프를 과거 커밋에서 지우려면 history rewrite 가 필요하다 — 포트폴리오 레포의 전 SHA 가 바뀌고 협업 중인 동시 세션의 브랜치가 전부 깨진다. **HEAD 에서 제거 + 재발 방지**로 충분하다(파일 크기 1MB 로 clone 부담이 유의미하지 않다).
- FAN-BE-010 이후의 membership 계층 로직 — 무관.

---

# Acceptance Criteria

- **AC-1** — `git ls-files | grep -E 'hs_err_pid|replay_pid'` 가 **0건**.
- **AC-2 (재발 방지 — mutation-check)** — `.gitignore` 가 실제로 문다. 덤프 파일을 하나 만들어 `git status --short` 에 **나타나지 않음**을 확인한다. **패턴을 추가하고 통과한다고 믿지 말고 주입해서 확인한다**(가드는 물어야 가드다).
- **AC-3** — `PostVisibility` javadoc 의 `PREMIUM` 서술이 `PostAccessGuard` 및 실제 `MembershipChecker` 배선과 **일치**한다. "always-pass" / "follow-up task will integrate a real check" 같은 **거짓 문장이 트리에 남아 있지 않다**(grep 확인).
- **AC-4** — `access-conditions.md` 의 `TIME_WINDOW` 행이 same-day 제약과 midnight-wrap 유예(ADR-028 § D3)를 명시하고, 위반 시 **fail-closed** 임을 적는다.
- **AC-5 (코드 무수정)** — Java `src/main` diff **0줄**. 세 항목 모두 **문서·위생**이며 동작을 바꾸지 않는다.
- **AC-6** — `./gradlew :projects:fan-platform:apps:community-service:test` GREEN(javadoc 변경이 컴파일을 깨지 않음).

---

# Related Specs

- `platform/access-conditions.md` · `docs/adr/ADR-MONO-028-time-window-access-condition.md` (§ D3)
- `projects/fan-platform/tasks/done/` — `TASK-FAN-BE-002`(v1 스코프, 거짓 javadoc 의 출처) · `TASK-FAN-BE-010`(hard fail-close 를 붙인 후속)

# Related Contracts

- 없음 (관측 가능한 API 동작 무변경)

---

# Edge Cases

- **`.gitignore` 패턴이 너무 넓으면 안 된다.** `*.log` 로 막으면 의도적으로 커밋된 로그 픽스처가 있을 경우 함께 사라진다. `hs_err_pid*.log` / `replay_pid*.log` 로 **정확히** 겨눈다(HotSpot 가 생성하는 이름).
- **덤프가 `libs/java-observability` 에 있다는 사실 자체가 오독을 부른다** — 관측성 라이브러리라 "샘플 덤프인가?" 로 읽힐 수 있다. 삭제 커밋 메시지에 **최초 커밋에 딸려온 사고**임을 명시한다.
- **`PostVisibility` 의 `MEMBERS_ONLY` 서술은 옳다** — 거짓은 `PREMIUM` 문단뿐이다. 문단 전체를 다시 쓰다가 옳은 서술까지 흔들지 말 것.

# Failure Scenarios

- **덤프만 지우고 `.gitignore` 를 빼먹는다** → 이 호스트에서 JDT.LS OOM 이 재발하는 순간 **또 커밋된다**(알려진 반복 해저드). AC-2 가 이걸 막는다.
- **`TIME_WINDOW` 단서를 다는 대신 midnight-wrap 을 "고친다"** → ADR 이 명시적으로 유예한 결정을 에이전트가 뒤집는 것. **금지**(Scope § OUT).
- **`PostVisibility` 를 "stale docstring" 으로 가볍게 처리** → 이건 **보안 태세 거짓 진술**이고, 그 대가는 이미 두 번 지불됐다(발굴 재조사). 왜 오해가 반복됐는지를 남기지 않으면 세 번째가 온다.
