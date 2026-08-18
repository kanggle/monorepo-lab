# Task ID

TASK-MONO-551

# Title

데모 헬스 판정이 **두 방향으로** 틀렸다 — 끝난 작업을 죽었다고 하고, 죽은 호스트를 살았다고 한다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- guard

---

# 배경 — 2026-08-17(UTC) TASK-MONO-550 AC-3 라이브 실증 중 발견

`ami-0d7c781d63d46b489` 로 apply 한 첫 부팅에서 데모가 **자력으로 떴다**(MONO-550 의 고침이 동작).
그 실증 도중 `/domains` 를 판정 근거로 쓰다가, 그것이 **두 방향 모두로 틀린 답**을 준다는 것을 봤다.
두 결함은 같은 파일(`infra/demo/demo-status.sh` + 발행 경로)에 있고 **방향만 반대**다.

## 결함 A — 거짓 음성: 정상 종료한 일회성 작업을 "죽은 서비스"로 센다

`demo-status.sh` 의 술어(측정한 것):

```
total   = docker ps -a  (종료된 것 포함)
healthy = state=running 이고 (헬스체크 없음 | healthy)
```

⇒ **`Exited (0)` 인 init 작업은 `total` 에는 들어가고 `healthy` 에는 절대 못 들어간다.**

실측(전수):

| 안 도는 컨테이너 | 상태 |
|---|---|
| `ecommerce-minio-init` | `Exited (0)` |
| `wms-kafka-init` | `Exited (0)` |
| `iam-kafka-init` | `Exited (0)` |
| **unhealthy 상태의 실행 컨테이너** | **0건** |

⇒ iam `14/15` · wms `16/17` · ecommerce `33/34` = **전부 정상인데 영원히 `partial`**.
이 세 도메인은 **어떤 상태에서도 `up` 이 될 수 없다**. 런처 페이지는 면접관에게
**항상 노란 배지 3개**를 보여준다 — 포트폴리오 데모에서 이건 기능 결함과 같은 값을 한다.

🔴 이건 "정상 종료"와 "죽음"을 구분하지 않는 술어 문제다. `Exited (0)` 은 **성공**이다.

## 결함 B — 거짓 양성: 얼어붙은 스냅샷이 "현재 상태"로 읽힌다

같은 실증에서 **호스트 유저랜드가 통째로 멈췄다**(별건 = `TASK-MONO-552`). 그때 `/domains` 는:

```
{"iam": {"state":"partial","healthy":14,"total":15}, ... }   ← 99/102, 멀쩡해 보인다
```

**그 데이터는 12.8분 전 것이었다.**

| | |
|---|---|
| SSM 파라미터 `LastModifiedDate` | 21:40:35Z (Version **91** 에서 정지) |
| 조회 시각 | 21:53:25Z |
| 발행 주기 | **30초** (`demo-status.timer`) |

발행자가 죽으면 SSM 파라미터는 **마지막 값 그대로 남는다**. Lambda 는 그것을 읽어
**타임스탬프 없이** 반환한다 ⇒ *"방금 잰 값"* 과 *"13분 전 값"* 이 **바이트 단위로 구별 불가**다.

🔴🔴 나는 이 화면을 근거로 **"99/102 정상, 곧 테스트 가능"** 이라고 보고할 뻔했다.
실제로 그 순간 박스는 15분째 무응답이었고 HTTP 는 전멸이었다. 면접관도 같은 화면을 본다 —
**런처가 초록인데 아무 링크도 안 열리는 상태**가 이 결함의 사용자 표면이다.

## 왜 이 둘을 한 티켓에 묶는가

같은 술어의 두 방향이다. 한쪽만 고치면 나머지 반은 살아남고, 살아남은 거짓이
**더 자주 읽히는 쪽**일 수 있다. 그리고 A 를 고치면 `up` 이 흔해지므로 **B 의 위험이 커진다**
(초록이 기본값이 될수록 얼어붙은 초록이 안 띈다). 순서는 **B 먼저**.

---

# Goal

`/domains` 의 답이 **지금 그 호스트의 상태**를 뜻하게 만든다. 끝난 작업을 실패로 세지 않고,
발행이 끊긴 상태를 "정상"으로 보고하지 않는다.

# Scope

## In Scope

- **B (먼저)** — 헬스 스냅샷에 **발행 시각**을 싣고, Lambda 가 그 나이를 판정에 쓴다.
  - 발행자(`demo-status-publish.sh`)가 스냅샷에 `published_at`(epoch, UTC)을 넣는다.
  - `handler.py` 가 `age = now - published_at` 을 계산해 응답에 담고, **임계(예: 3× 발행주기)**
    를 넘으면 도메인 상태를 그대로 반환하지 않고 `stale` 로 표시한다.
  - 🔴 **`published_at` 이 아예 없는 스냅샷**(구 AMI, terraform 초기값 `{}`)도 `stale` 이어야
    한다 — *부재를 신선함으로 읽지 않는다*. 이게 이 결함의 핵심 축이다.
  - 사이트(`site/index.html`)가 `stale` 을 **초록이 아닌 것**으로 렌더한다.
- **A** — `demo-status.sh` 의 술어가 "성공적으로 끝난 일회성 작업"을 구분한다.
  구현자가 AC-0 에서 재확인 후 택일하고 근거를 적을 것:
  - (a) `state=exited && ExitCode=0` 을 **healthy 로 계상**한다(가장 작은 변경).
  - (b) 그런 컨테이너를 **`total` 에서 뺀다**(= 서비스가 아니므로 모집단이 아니다).
  - (c) compose 라벨(예: `demo.oneshot=true`)로 명시 분류한다.
  🔴 어느 쪽이든 **`Exited (137)`·`Exited (1)` 은 여전히 실패여야 한다** — 종료코드를 보지 않고
  "exited 는 다 봐준다" 로 가면 진짜 크래시를 초록으로 만든다(이 티켓이 만드는 최악의 결과).

## Out of Scope

- 호스트가 굳는 원인 자체 → **`TASK-MONO-552`**. 이 티켓은 *"굳은 것을 굳었다고 말하게"* 하는 것이다.
- 일회성 init 컨테이너를 없애는 것(compose 구조 변경). 그것들은 정당하게 존재한다.

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act).**
`origin/main` 에서 `demo-status.sh` · `demo-status-publish.sh` · `handler.py` · `site/index.html` 을
다시 읽는다. 위의 술어와 "안 도는 컨테이너 3건" 을 **다시 센다** — 인계된 숫자는 가설이다.

**AC-1 — 신선도(B).** 스냅샷에 발행 시각이 실리고, 발행이 끊기면 `/domains` 가 **다른 답**을 준다.
판정: 발행자 타이머를 멈춘 뒤 임계 시간이 지나면 응답이 `stale` 로 바뀐다.
🔴 **`published_at` 부재 스냅샷도 `stale`** 인지 별도 케이스로 확인한다.

**AC-2 — 일회성 작업(A).** 세 init 컨테이너가 `Exited (0)` 인 상태에서 iam·wms·ecommerce 가
`up` 으로 보고된다. **그리고 대조군**: 같은 컨테이너를 `Exited (1)` 로 만들면 `partial` 로 떨어진다.
대조군 없이 통과만 보면 "exited 를 전부 봐주는" 구현과 구별되지 않는다.

**AC-3 — 가드.** 저장소만 보고 AC-1·AC-2 의 명제를 지킨다. `verify-demo-wrapper.sh` **정적 구간**
(CI "Demo wrapper smoke" + packer 7단계가 실제로 돌린다). **bite 필수** — 각 고침을 되돌리면
빨개져야 하고, **주입이 실제로 들어갔는지 먼저 단언**한다(MONO-550 에서 sed 구분자 충돌로
주입 0건인데 PASS 가 나온 전례).
🔴 가드는 **자기 설명 문구에 걸리면 안 된다** — 스크립트 주석이 `Exited (0)` 이라는 문자열을
담게 되므로 줄머리 앵커나 실행 결과로 판정할 것.

**AC-4 — 재굽기 + 라이브.** `demo-status.sh`·발행자는 **baked 층**이다. 새 AMI 로 apply 한 뒤
`/domains` 가 9개 도메인을 `up` 으로 보고하는지, 그리고 발행자를 죽였을 때 `stale` 이 뜨는지
실물로 확인한다. ⚠️ `packer build`/`terraform apply` 는 **사용자 승인 대상**.

# Related Specs

- `infra/demo/demo-status.sh` — 술어의 주체(결함 A)
- `infra/demo/demo-status-publish.sh` · `demo-status.timer` — 발행 경로(결함 B)
- `infra/demo/aws/terraform/lambda/handler.py` — `/domains` 응답 조립
- `infra/demo/aws/terraform/site/index.html` — 면접관이 보는 표면
- `infra/demo/verify-demo-wrapper.sh` 가드 (z) — 발행 **배선**은 지키지만 **신선도**는 안 본다

# Related Contracts

없음 (인프라 전용).

# Edge Cases

- **부팅 직후** — 발행자는 `OnBootSec=60` 이라 첫 1분은 스냅샷이 없거나 낡았다. 그 창을 `stale`
  로 부르는 것은 **옳다**(아직 모른다는 뜻). "부팅 중"과 "얼어붙음"을 구분하고 싶으면 EC2
  `LaunchTime` 을 함께 보되, **모르는 것을 초록으로 만들지는 말 것.**
- **terraform 초기값 `{}`** — apply 직후 파라미터는 빈 오브젝트다. 이것도 `stale` 이어야 한다.
- **시계** — `published_at` 은 인스턴스 시계, `now` 는 Lambda 시계다. 둘 다 UTC 이고 chrony 가
  돌지만, 임계는 그 오차보다 충분히 크게(3× 주기 = 90초 이상) 잡을 것.
- **새 일회성 컨테이너가 늘어난다** — 목록을 손으로 나열하면 그 순간 드리프트가 시작된다
  (이 저장소가 이미 두 번 데인 실패 모드). 종료코드/라벨 같은 **성질**로 판정할 것.

# Failure Scenarios

- **A 만 고치고 닫는다** — `up` 이 흔해져서 얼어붙은 초록이 더 안 띈다. **B 를 먼저.**
- **`exited` 를 전부 healthy 로 센다** — 크래시 루프가 초록이 된다. 종료코드를 반드시 볼 것.
- **`stale` 을 사이트가 초록으로 렌더한다** — 백엔드만 고치고 표면을 안 고치면 면접관에게는
  아무것도 안 바뀐다(이 결함의 사용자 표면은 배지다).
- **로컬 초록을 근거로 닫는다** — 발행자·타이머는 AMI 안에서만 돈다. 재굽기 없이 닫으면
  `main` 이 초록인 것과 데모가 고쳐진 것이 다르다는 명제를 또 밟는다(MONO-399 가 가르쳤다).

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Opus** — bash 술어 + Lambda + 프런트 배지 + 가드 설계 +
  재굽기 꼬리. 특히 **대조군 설계**(`Exited (1)` 이 여전히 빨간가)가 이 티켓의 난이도다.
- 선행: 없음. 후속: `TASK-MONO-552`(호스트가 굳는 원인) 의 **판정 도구**가 이 티켓이다 —
  552 를 고쳤는지 확인하려면 먼저 "굳었다"를 말할 수 있어야 한다.
- 관련: `TASK-MONO-550`(이 결함을 노출시킨 실증), `TASK-MONO-477`(발행자 도입).
