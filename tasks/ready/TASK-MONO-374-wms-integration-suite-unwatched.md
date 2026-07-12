# Task ID

TASK-MONO-374

# Title

wms integration 스위트는 **감시되지 않는다** — 확률적으로 깨지는데, wms 를 건드릴 때만 실행된다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- flake
- drift-guard
- reachability

---

# Goal

`TASK-MONO-370`(게이트웨이 키잉, wms `gateway-service` 모듈만 수정) 의 CI 에서 **wms Integration 잡이 RED** 였다. 재실행 GREEN. 그 자체는 흔한 일이지만, **확인하는 과정에서 나온 것이 문제다.**

## 실측 (2026-07-12, `875e583cd`)

```
main 최근 12 런에서 "Integration (master-service + notification-service + outbound-service)" 잡의 결론:
  93ec29f16   <job absent — path-gated skip>
  20b1c19ea   <job absent — path-gated skip>
  538c5be23   <job absent — path-gated skip>
  03a5aa609   <job absent — path-gated skip>
  e965ebac8   <job absent — path-gated skip>
  036ed081f   <job absent — path-gated skip>
  (12/12 전부)
```

**최근 12 런 중 한 번도 돌지 않았다.** paths-filter 로 게이팅돼 **wms 코드를 건드리는 PR 에서만** 실행된다.

## 왜 이게 문제인가

1. **그 스위트는 확률적으로 깨진다.** 370 에서 관측된 실패는 `SQLSTATE(08006)` / `SocketException: Closed by interrupt` / notification-service Hikari 커넥션 절단 — **`TASK-MONO-331` 이 `--no-parallel` 로 고쳤다고 기록된 경합 flake 의 지문 그대로다.** 즉 **그 수정은 불완전하거나, 다른 경합 축이 남아 있다.**
2. **그리고 아무도 안 본다.** wms 는 성숙한 프로젝트라 자주 안 바뀐다 ⇒ 잡이 거의 안 돈다 ⇒ **깨져도 몇 주 동안 아무도 모른다.** 다음에 wms 를 건드리는 사람이 **자기 diff 와 무관한 RED 를 만나** 원인을 찾느라 시간을 쓴다(370 이 정확히 그랬다).
3. **`main` 기준선이 없다.** "main 은 초록이다" 라고 말할 근거가 없다 — **한 번도 안 돌았으니까.** 370 에서 이 스위트가 **선존 파손**인지 flake 인지 판정하려 했을 때, 비교할 대상이 없었다.

**이것은 이 저장소가 계속 만나는 그 형태다: 실행되지 않는 검사는 초록으로 보고된다** (`TASK-MONO-359`/`360` 의 도달성 원칙). 다만 여기서는 **가드가 아니라 테스트 스위트**가 그 상태다.

---

# Scope

## In Scope

1. **경합 원인 재조사** — `MONO-331` 의 `--no-parallel` 이 무엇을 고쳤고 무엇이 남았는지. `08006 / Closed by interrupt` 는 **커넥션이 밖에서 끊긴 것**이지 앱 버그가 아니다: Testcontainers 컨테이너 종료 / Awaitility 타임아웃에 의한 스레드 interrupt / 4 모듈 동시 기동의 리소스 포화 중 무엇인지 특정한다.
2. **감시 경로 확보** — 둘 중 하나(또는 둘 다):
   - **TIME 트리거**: `nightly-e2e.yml`(이미 cron 보유)에 wms integration 을 얹는다. 그러면 **wms 를 아무도 안 건드려도 매일 밤 실행**된다. ⚠️ `ci.yml` 에 `schedule:` 추가는 **금물**(`dorny/paths-filter` 는 schedule 이벤트에 비교 base 가 없어 필터 의미 미정의 — MONO-359).
   - **결함의 성질을 단위테스트로 환원**: 확률적 IT 는 **CI-GREEN 이 증거가 되지 못한다.** 커넥션 절단에 대한 재시도/격리 성질을 결정론적 테스트로 단언할 수 있는지 본다.
3. **`nightly-e2e.yml` 서비스 추가 드리프트 주의** — 그 파일은 서비스 추가 시 **여러 곳을 동시에 갱신**해야 한다(과거 실측). 한 곳만 고치면 조용히 안 돈다.

## Out of Scope

- **wms 코드 수정** — 앱 결함이라는 증거가 아직 없다. **먼저 원인을 특정**할 것. (`env_ci_flake_is_a_hypothesis_not_a_verdict`: *"flake=인프라" 는 가설이지 결론이 아니다.* 4xx 였다면 테스트 코드 결함이지만, `08006` 은 커넥션 계층이다.)
- 다른 프로젝트의 integration 잡 — **같은 질문을 다른 프로젝트에도 물을 가치는 있지만**(scm/fan/finance/erp 도 path-gated 인가?), 이 task 는 wms 로 한정한다. § Failure Scenarios 참조.

---

# Acceptance Criteria

- [ ] **AC-1 (도달성 실측)** — 조치 **전**에, wms integration 잡이 최근 N 런에서 몇 번 돌았는지 **측정해 기록**한다(위 § Goal 의 표를 재현). 조치 **후**, wms 를 건드리지 않는 커밋에서도 실행됨을 **실제 런으로 확인**한다. *(주장하지 말고 관측할 것 — MONO-360 의 규율.)*
- [ ] **AC-2** — 실패 원인이 **특정**되었다(리소스 포화 / 컨테이너 조기 종료 / Awaitility interrupt 중 무엇인지). 특정 못 하면 **그 사실을 적고** 무엇을 배제했는지 남긴다.
- [ ] **AC-3** — 감시 경로가 생겼고, **그것이 실제로 실패를 잡는지 mutation 으로 확인**한다(예: IT 하나를 의도적으로 깨뜨린 측정용 브랜치에서 시계 트리거가 RED).
- [ ] **AC-4 (무신호 ≠ 합격)** — 시계 잡이 **아무것도 실행하지 않고 exit 0** 할 수 없어야 한다. 커버리지 0 = FAIL (MONO-359 의 `--require-coverage` 규율).
- [ ] **AC-5** — 다른 5개 프로젝트의 integration 잡도 같은 상태인지 **조사만** 하고 결과를 기록한다(수정은 별건).

---

# Related Specs

- `.github/workflows/ci.yml` — wms integration 잡 + paths-filter
- `.github/workflows/nightly-e2e.yml` — 기존 cron 트리거
- `TASK-MONO-331` — `--no-parallel` 로 고쳤다고 기록된 그 경합
- `TASK-MONO-359` / `TASK-MONO-360` — 도달성 원칙(diff 를 안 남기는 결함 → TIME 트리거 / 실행 안 된 검사는 초록)

# Related Contracts

없다.

---

# Edge Cases

- **매일 밤 도는 flaky 스위트는 매일 밤 RED 를 만든다** — 그러면 **가드가 꺼진다**(MONO-360 의 *"첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 것보다 나쁘다"*). ⇒ **감시를 붙이기 전에 § AC-2 의 원인 특정이 선행**돼야 한다. 순서를 뒤집지 말 것.
- **재실행 GREEN 을 "flake 확정" 으로 읽지 말 것** — 370 에서 재실행이 초록이었지만, 그것이 증명한 것은 *"이 커밋이 항상 깨지지는 않는다"* 뿐이다. **인과 경로가 0 이라는 사실**(diff 가 `gateway-service` 모듈뿐, `outbound-service` 는 게이트웨이 의존 없음)이 머지 판단의 근거였지 재실행이 아니었다.

# Failure Scenarios

- **감시만 붙이고 원인을 안 고친다** → 매일 밤 RED → 알림 피로 → 잡을 끄거나 무시 → **처음보다 나쁜 상태**(끈 잡은 초록으로 보고된다). Guard: AC-2 가 AC-3 보다 먼저.
- **wms 만 보고 끝낸다** → 같은 구조(성숙한 프로젝트 + path-gated integration)가 scm/fan 에도 있으면 **거기서 똑같이 재발**한다. Guard: AC-5.

---

# Provenance

발굴 2026-07-12 — `TASK-MONO-370` CI 에서 wms Integration 이 RED 였고, **그것이 내 변경 탓인지 판정하려다** 발견했다. 판정에는 성공했다(인과 경로 0: diff 가 `gateway-service` 5개 모듈뿐이고 `outbound-service` 는 게이트웨이 의존이 없다). **문제는 판정 과정에서 비교할 `main` 기준선이 없다는 것이었다** — 그 잡이 최근 12 런에서 **한 번도 안 돌았기 때문이다.**

**"실행되지 않는 검사는 초록으로 보고된다" 를 이 저장소는 가드에 대해 배웠다(359/360). 같은 명제가 테스트 스위트에도 성립한다는 것은 아직 처리하지 않았다.**

분석=Opus 4.8 / 구현 권장=**Opus** (원인 특정이 본체 — 감시를 먼저 붙이면 매일 밤 RED 로 자멸한다).
