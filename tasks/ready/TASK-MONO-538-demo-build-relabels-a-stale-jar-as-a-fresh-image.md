# Task ID

TASK-MONO-538

# Title

신선도 검사가 **자기가 인쇄한 명령의 절반**으로 꺼진다 — `DEMO_BUILD=1` 은 jar 를 다시 만들지 않는다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

---

# Goal

`TASK-MONO-533` 이 넣은 `infra/demo/check-image-freshness.sh` 는 낡은 이미지를 발견하면
**두 줄을 순서대로** 인쇄한다:

```
[freshness]   다시 굽기:  ./gradlew :projects:…:bootJar …
[freshness]   그 다음:    DEMO_BUILD=1 bash infra/demo/demo-up.sh …
```

이 순서는 load-bearing 인데 **강제하는 것이 없다.** 두 번째 줄만 실행하면:

- `demo-up.sh` 는 **컴파일하지 않는다**(그 파일 헤더가 명시한다 — `DEMO_BUILD=1` 로
  빌드하려면 *"먼저 `./gradlew <각 서비스>:bootJar`"*).
- 도커는 **디스크에 있는 옛 jar 를 그대로 COPY** 해 이미지를 다시 굽고 `Created` 를 **지금**
  으로 찍는다.
- 다음 실행부터 그 서비스는 `이미지 > 소스` 이므로 **영원히 "신선"** 으로 보고된다.

⇒ **래칫이 자기 계측기를 끈다.** 그리고 그것을 켜는 명령이 다름 아닌 이 검사 자신이
인쇄한 두 줄 중 하나다.

🔵 티켓 `TASK-MONO-533` 본문은 이미 *"이미지가 더 새롭다고 그 커밋을 담았다는 보장은
없다(더티 트리 빌드)"* 를 적었다. **새로운 사실은 방향이다** — 그 구멍을 내는 것이
사용자의 실수가 아니라 **이 검사 자신의 치료법**이라는 것.

---

# 🔵 이번 실행에서 실제 피해는 없었다 (그러나 우연이다)

2026-08-16 라이브 검증에서 `DEMO_BUILD=1 demo-up.sh scm` 이 하드 의존 `iam` 까지 **다시
구웠다.** 나는 iam 의 jar 를 다시 만들지 않았으므로 정확히 위 상황이었다.

실측 — iam 앱 5개의 jar mtime 대 소스 마지막 커밋:

| 서비스 | jar | 소스 마지막 커밋 |
|---|---|---|
| auth-service | 08-15 01:29Z | 08-14 00:42Z |
| account-service | 08-15 01:29Z | 08-12 20:51Z |
| admin-service | 08-15 01:29Z | 08-12 20:51Z |
| security-service | 08-15 01:29Z | 07-31 19:00Z |
| gateway-service | 08-15 01:29Z | 08-05 14:22Z |

전부 소스보다 새로웠다 ⇒ 낡은 바이너리가 세탁되지 않았다. **jar 가 하나라도 낡았다면
그 서비스는 오늘부터 영구 초록이 됐을 것이고, 아무도 몰랐을 것이다.**

🔴 부수 관측: **하드 의존이 함께 구워진다.** `demo-up.sh scm` 이 `iam` 도 `--build` 대상에
넣는다 — 사용자는 scm 만 다시 굽는다고 생각한다. 낡은 jar 를 세탁할 표면이 **요청한
도메인보다 넓다.**

---

# Scope

## In Scope

- **판정 축을 하나 더 붙인다** — 이미지 시각뿐 아니라 **jar 의 시각**도 소스 커밋과 대조한다.
  `이미지 ≥ 소스` 이지만 `jar < 소스` 이면 그것이 정확히 이 결함이고, 지금은 **초록으로
  보고된다.** 새 판정 칸을 만든다(예: `낡은 jar 로 구워짐`).
  🔴 이 축은 **낡음만 단언한다**(533 의 규율 유지) — jar 가 더 새롭다고 그 커밋을 담았다는
  보장은 없다. 역방향만 보장된다.
- `DEMO_BUILD=1` 경로에서, 굽기 **직전**에 같은 대조를 수행해 낡은 jar 가 있으면 **알린다.**
  🔴 **막지 않는다** — 533 이 정한 대로 이 검사는 자문이고 기동을 세우지 않는다
  (막으면 사람들이 `demo-up.sh` 를 우회한다).
- `check-image-freshness.sh` 의 안내 문구에 **왜 순서가 중요한지**를 한 줄로 적는다 —
  지금은 두 명령이 나란히 있을 뿐 *"두 번째만 하면 검사가 꺼진다"* 는 말이 없다.
- `demo-up.sh` 가 `DEMO_BUILD=1` 로 **어느 도메인을 굽는지** 이름을 대며 알린다(하드 의존
  포함). 지금은 요청한 도메인만 굽는다고 오해하기 쉽다.

## Out of Scope

- **자동 gradle 실행** — `demo-up.sh` 가 직접 `./gradlew` 를 부르면 기동이 수십 분이 되고,
  그것이 533 이 명시적으로 배제한 것이다(*"사람들이 우회한다"*).
- **기동 차단(fail-closed)** — 위와 같은 이유.
- AWS AMI 층 — `TASK-MONO-399` AC-6 소유.
- CI — 매번 새로 빌드하므로 이 결함 클래스가 없다.

---

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act)** — 결함을 **재현**한다: 어떤 서비스의 jar 를 소스보다 낡게
      만든 뒤(예: 소스만 touch/커밋) `DEMO_BUILD=1` 로 그 도메인을 굽고, **수정 전 검사가
      그것을 "신선" 으로 보고하는지** 확인한다. 🔴 **이 대조군 칸이 없으면 AC-1 은
      아무것도 증명하지 못한다** — 무엇을 못 보던 것인지가 판정의 전부다.
- [ ] **AC-1 (문다)** — 같은 상태에서 수정 후 검사가 **새 판정 칸으로 잡는다.**
      복원 후에는 다시 조용하다(정상 상태에서 안 짖는다).
- [ ] **AC-2 (모집단)** — 새 축이 **서비스별 표**로 보고된다. 대표 1건 금지.
      `libs/` 는 533 과 같이 **귀속 불가 한 줄**로 남긴다(gradle 의존을 읽지 않는다).
- [ ] **AC-3 (판정 불가 ≠ 초록)** — jar 가 아예 없는 경우(한 번도 빌드 안 함)는 **낡음이
      아니라 판정 불가**로 분리해 센다. 533 이 세운 규칙 그대로.
- [ ] **AC-4** — 종료 코드는 **여전히 0**(자문). `verify-demo-wrapper.sh` ·
      `check-index-queue-drift` OK.

---

# Related Specs

- `tasks/done/TASK-MONO-533-local-demo-runs-images-older-than-the-code.md` (이 검사의 도입)
- `tasks/ready/TASK-MONO-399-demo-full-memory-budget-unenforced.md` (AMI 층의 같은 클래스)
- `infra/demo/check-image-freshness.sh` · `infra/demo/demo-up.sh`

# Related Skills

N/A — 셸 스크립트.

# Related Contracts

None.

# Target Service

N/A — `infra/demo/` (공유 데모 하네스).

# Architecture

N/A.

---

# Implementation Notes

- jar 위치는 `projects/<project>/apps/<svc>/build/libs/<svc>.jar` 다(Dockerfile 들이 그
  경로를 COPY 한다). `*-plain.jar` 는 제외해야 한다.
- 시각 축은 533 과 같이 **epoch 로 정규화**한다(이 호스트는 UTC+9 — 안 맞추면 9시간짜리
  거짓 경보/거짓 침묵).
- 소스 기준은 533 과 **같은 술어**를 재사용하라: 저장소 HEAD 가 아니라 **그 서비스의 소스를
  마지막으로 건드린 커밋**. HEAD 로 비교하면 문서 커밋 하나로 전부 낡음이 되고, 첫날 RED 인
  가드는 꺼진다(`TASK-MONO-360`).

---

# Edge Cases

- **더티 트리 빌드** — 커밋되지 않은 수정으로 구운 jar 는 소스 커밋보다 새롭지만 그 커밋을
  담지 않는다. 이 축도 여전히 **낡음만** 말할 수 있다. 문서에 그대로 적을 것.
- 이미지는 있는데 jar 가 지워진 경우(빌드 산출물 청소) → 낡음이 아니라 **판정 불가**.
- `libs/` 변경은 어느 서비스의 jar 를 낡게 만드는지 이 검사가 모른다 — 533 과 같은 한계이고
  같은 방식(귀속 불가 한 줄)으로 정직하게 남긴다.

---

# Failure Scenarios

- 새 축을 **차단**으로 만들면 데모 기동이 막히고 사람들이 스크립트를 우회한다 ⇒ 검사 전체가
  무력화된다(533 이 이미 이 함정을 피해 설계했다).
- 대조군(AC-0) 없이 구현하면 *"수정 후에도 초록"* 을 통과로 읽는다 — 이 저장소가 반복해서
  당한 실패 모드다(`TASK-MONO-534` AC-2 가 술어를 네 번 만에 맞췄다).
- jar 시각을 `git log` 대신 파일 mtime **끼리** 비교하면 체크아웃 순서에 따라 뒤집힌다.
  기준은 **소스 커밋 시각**이다.

---

# Test Requirements

- AC-0 재현 + AC-1 bite/복원 각 1회.
- `bash -n infra/demo/check-image-freshness.sh`.

---

# Definition of Done

- [ ] AC-0~AC-4 닫힘.
- [ ] `check-image-freshness.sh` 안내 문구에 순서의 이유 기록.
- [ ] `tasks/INDEX.md` done entry(close chore 시).

---

# Provenance

2026-08-16 라이브 검증 중 발굴. 네 도메인을 다시 구우려고 `DEMO_BUILD=1` 을 쓰다가, 그것이
하드 의존 `iam` 까지 **jar 재빌드 없이** 다시 굽는 것을 보고 이 경로를 확인했다. 이번에는
iam jar 이 모두 최신이라 피해가 없었다 — **우연이 결함을 가렸다.**

분석=Opus 5(1M) / 구현 권장=**Sonnet** (판정 축 하나 추가 + 대조군).
