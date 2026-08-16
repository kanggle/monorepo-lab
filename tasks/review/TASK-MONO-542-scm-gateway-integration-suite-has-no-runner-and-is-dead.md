# Task ID

TASK-MONO-542

# Title

scm 게이트웨이 통합 스위트도 러너가 없고 **이미 죽어 있다** — 팬과 같은 결함, 같은 원인, 같은 불가시성 (진단은 끝났다)

# Status

review

# Owner

monorepo

# Task Tags

- ci
- testing

---

# 배경

`TASK-MONO-541` 의 **AC-4(모집단 재계수)** 가 발굴했다. 그 티켓은 *"게이트웨이 한 줄만
넣고 닫으면 같은 계열의 다음 낙오를 못 잡는다"* 는 이유로 전수 대조를 요구했고,
**그 대조가 실제로 두 번째를 찾았다.**

## 재계수 결과 (2026-08-17 실측)

`integrationTest` 를 **선언한** 모듈 **39** ↔ 어느 CI 잡에 **나열된** 모듈 **41**.

| 모듈 | 판정 |
|---|---|
| `projects/fan-platform/apps/gateway-service` | 🔴 진짜 낙오 — `TASK-MONO-541` 이 닫음 |
| **`projects/scm-platform/apps/gateway-service`** | 🔴 **진짜 낙오 — 이 티켓** |
| `projects/iam-platform` (루트) | 🔵 **계수 인공물** — `subprojects { }` 블록에서 선언하므로 5개 앱이 상속하고, 그 5개는 전부 나열돼 있다 |

🔵 세 번째 행을 남기는 이유: 차집합에 나타났지만 **결함이 아니다.** 그것을 적지 않으면
다음 사람이 같은 계수를 하고 같은 의심을 반복한다.

## 🔴 진단은 이미 끝났다 — 추측이 아니라 실측이다

이 티켓은 "돌려 보고 무슨 일이 생기는지 보자" 가 아니다. **로컬에서 이미 돌렸다**:

```
:projects:scm-platform:apps:gateway-service:integrationTest
  → files=5  tests=5  failures=5  errors=0  skipped=0
  → 'jwks is null' 6회
```

**5개 클래스 전부 `initializationError`** 이고, 원인은 팬과 **글자 그대로 같다**:

- `GatewayIntegrationBase` 가 `@TestInstance(PER_CLASS)` 인데 공유 인프라
  (`jwks` · `downstream` · Redis)를 **`@BeforeAll`** 에서 만든다.
- `@DynamicPropertySource` 의 supplier 는 **컨텍스트 생성 시점**에 평가되는데,
  `PER_CLASS` 에서는 테스트 인스턴스 생성(=컨텍스트 로드)이 `@BeforeAll` **보다 먼저**다.
- ⇒ supplier 가 볼 때 그 필드들은 아직 null 이다.

⇒ **형제 파리티 낙오가 둘**이고, 둘 다 *러너가 없어서* 썩은 채로 남았다.

🔵 `ci.yml` 의 팬 잡 주석이 이 인과를 이미 증언하고 있다 — 다른 팬 서비스들이 그 잡에
합류할 때 *"§19a (containers @BeforeAll → static block)"* 를 고쳤다고 적혀 있다.
**레인 합류가 그 버그를 드러내는 행위**였고, 두 게이트웨이만 합류하지 않았다.

---

# Goal

scm 게이트웨이의 통합 스위트가 **살아나고**, CI 에서 **실제로 실행된다**.

---

# Scope

1. **하네스** — `projects/scm-platform/apps/gateway-service/src/test/.../GatewayIntegrationBase.java`
   의 공유 인프라 기동을 `@BeforeAll` → **static 초기화 블록**으로 옮긴다.
   `@AfterAll` 의 managed stop 은 제거하고 정리는 Ryuk/JVM 종료에 맡긴다.
   🔵 **선례를 그대로 따르면 된다** — `TASK-FAN-BE-049` 가 팬 쪽에 같은 변경을 이미
   했고(squash `7b00d5ee3`), 그 파일의 클래스 주석이 이유를 담고 있다.
2. **CI** — `.github/workflows/ci.yml` 의 scm 통합 잡 `gradle-tasks` 에
   `:projects:scm-platform:apps:gateway-service:integrationTest` 를 추가.
3. `report-paths` 글롭이 게이트웨이 산출물을 집는지 **확인**한다(가정하지 말 것).

## Out of Scope

- 팬 게이트웨이 — `TASK-FAN-BE-049`(하네스) + `TASK-MONO-541`(레인)이 닫았다.
- 모집단 재계수 자체 — `TASK-MONO-541` AC-4 가 수행했고 결과가 위 표다. **다시 세지
  말고, 이 티켓이 끝난 뒤 차집합이 0 이 되는지만 확인**한다(AC-5).
- 다른 프로젝트 게이트웨이의 하네스 선제 수정 — 차집합에 없으므로 대상이 아니다.

---

# Acceptance Criteria

- [ ] **AC-0 (전제 재확인)** — 착수 시 ① scm 통합 잡에 게이트웨이가 **여전히 없고**
      ② `GatewayIntegrationBase` 가 아직 `@BeforeAll` 로 초기화하는지 확인한다.
      둘 중 하나라도 아니면 **STOP** 후 재측정.
- [ ] **AC-1** — 하네스 수정 후 로컬에서 스위트가 **실제로 실행된다**:
      `tests ≥ 5` · `failures=0` · `errors=0` · **`skipped=0`** 을 XML 아티팩트에서
      네 값 모두 읽어 적는다. 🔴 **`BUILD SUCCESSFUL` 은 판정이 아니다** — `MONO-541`
      선행 작업에서 `rc=0` 인데 `skipped=10` 인 회차가 실제로 나왔다(Docker 미검출 →
      `disabledWithoutDocker` 전량 스킵).
- [ ] **AC-2** — scm 통합 잡이 게이트웨이를 포함하고, **그 잡의 아티팩트**에서 같은 네
      값을 읽어 적는다(로컬 초록은 권위가 아니다 — 로컬 npipe 는 flaky ~1 pass/3).
- [ ] **AC-3 (bite)** — 라우트 재작성 정규식을 일부러 깨서 **그 잡이 빨개지는지** 확인하고
      되돌린다. 🔴 초록만 보면 *"잡이 추가됐다"* 와 *"잡이 그 스위트를 본다"* 가 구별되지
      않는다.
- [ ] **AC-4 (하네스 수정이 옳은 것을 고쳤는가)** — 수정 **전** 실패가
      `initializationError` / `jwks is null` 이었고, 수정 **후** 그 실패가 사라지는 것을
      대조로 남긴다. 🔵 수정 전 수치는 이 티켓 본문에 이미 있다(5/5 실패).
- [ ] **AC-5 (모집단이 닫혔는가)** — `TASK-MONO-541` AC-4 의 계수를 **다시 돌려**
      `DECLARED - LISTED` 차집합이 **`projects/iam-platform`(계수 인공물) 하나만** 남는지
      확인한다. 다른 것이 남으면 그것도 티켓으로.
- [ ] **AC-6** — 잡 시간 증가를 적는다(Redis 컨테이너 1개 추가).

---

# Related Specs

- `.github/workflows/ci.yml` — scm 통합 잡 (변경 대상)
- `projects/scm-platform/apps/gateway-service/build.gradle` — `integrationTest` 태스크

# Related Contracts

없음 — 테스트 하네스 + CI 배선이며 API·이벤트 계약을 건드리지 않는다.

---

# Edge Cases

- 🔴 **하네스를 고치면 *다른* 갭이 드러날 수 있다.** 이 저장소의 반복 관측이 정확히
  그것이다 — 서비스별 첫 CI 실행이 자기만의 하네스 갭(Redis 미컨테이너 · 스텁 미주입 ·
  스키마 드리프트)을 노출한다. `jwks is null` 이 사라진 뒤 **다음 실패가 나오는 것은
  정상**이고, 그것을 이 티켓에서 고칠지 별건으로 뺄지는 **성격을 보고** 정한다
  (하네스면 여기, 제품 결함이면 별건).
- scm 게이트웨이 스위트는 팬(1개 클래스 10 테스트)과 달리 **5개 클래스**(bootstrap ·
  health · prometheus 격리 · rate limit · route rewrite)다. 컨텍스트 캐시 재사용이
  걸리므로 **managed stop 제거가 팬보다 더 중요**하다.
- `report-paths` 글롭 — 아티팩트가 실제로 올라왔는지 **다운로드해서** 확인한다.
  리포트가 없으면 AC-2 를 잴 수단이 사라지고, 그 상태는 잡 초록과 구별되지 않는다.
- 경로 필터 — scm 통합 잡의 게이팅(`needs.changes.outputs.scm` 등)에 게이트웨이만 건드린
  PR 이 걸리는지 확인할 것.

# Failure Scenarios

- **CI 레인만 추가한다** → 잡이 즉시 빨개진다(5/5 실패). 하네스가 먼저다.
- **하네스만 고친다** → 로컬에서만 초록이고 러너가 없어 **다시 썩는다**. 이 티켓이
  존재하는 이유가 그것이다.
- **잡 초록으로 끝낸다** → 전량 스킵이 초록으로 보고된다. AC-1/AC-2 의 `skipped=0` 이
  그것을 잡는 유일한 단언이다.
- **bite 를 생략한다** → 잡이 그 스위트를 실제로 보는지 모른 채 닫힌다.
- **팬 수정을 복사만 하고 5개 클래스 차이를 안 본다** → 컨텍스트 캐시 ↔ 컨테이너 생애주기
  문제가 두 번째 클래스부터 나온다(팬은 클래스가 하나라 그 축이 드러나지 않았다).
