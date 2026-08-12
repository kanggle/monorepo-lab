# Task ID

TASK-MONO-523

# Title

`_platform-e2e.yml` 은 **한 프로젝트짜리 스택**만 표현할 수 있다 — 다른 프로젝트의 서비스를 e2e 트리오에 넣을 방법이 없다

# Status

done

# Owner

monorepo

# Task Tags

- ci
- shared-infra

---

# 배경

`TASK-FAN-INT-005` 의 AC-0 실측에서 드러났다. fan 의 live-trio e2e 에 iam(auth-service)을
넣어 워크로드 토큰을 **실제로 발급**받게 하려면 그 컨테이너 이미지가 CI 에서 빌드되어야
하는데, 재사용 워크플로가 서비스 하나하나를 **호출자의 `project-dir` 아래**로 하드코딩하고
있어 표현할 수가 없다.

`.github/workflows/_platform-e2e.yml` 원문(3곳 전부 같은 가정):

```bash
mv "$DOWNLOAD_PATH/$name/build/libs/$name.jar" \
   "$PROJECT_DIR/apps/$name/build/libs/$name.jar"     # ① jar 복원 경로
docker build -t "$image" \
  -f "$PROJECT_DIR/apps/$name/Dockerfile" \
  "$PROJECT_DIR/apps/$name/"                          # ② Dockerfile + 빌드 컨텍스트
XARGS="$XARGS -x ${GRADLE_PATH}:apps:${name}:bootJar" # ③ gradle 제외 태스크 경로
```

세 가정이 전부 `projects/<caller>/apps/<name>` 이다. 다른 프로젝트의 서비스는 셋 다 어긋난다.

## 🔴 그리고 "아티팩트에 jar 하나 더 얹으면 되지 않나" 는 **기존 3종을 깬다**

`actions/upload-artifact@v4` 는 **최장 공통 접두사를 잘라낸다.** 지금 fan 의 업로드 목록은
전부 `projects/fan-platform/apps/` 로 시작해서 아티팩트 루트가
`<service>/build/libs/<service>.jar` 가 되고, 위 ①이 정확히 그 모양을 읽는다.
여기에 `projects/iam-platform/apps/auth-service/...` 를 한 줄 추가하면 공통 접두사가
`projects/` 로 **짧아져** 루트가 `fan-platform/apps/<service>/...` 가 되고 — **기존 3종의
복원이 전부 깨진다.** 즉 "한 줄 추가" 로 보이는 변경이 침묵으로 레인을 부순다.
⇒ 교차 프로젝트 jar 는 **별도 아티팩트**여야 한다.

## 🔴 부수 실측 — 프로젝트마다 Dockerfile 의 빌드 컨텍스트가 다르다

| 서비스 | 컨텍스트 | 근거 |
|---|---|---|
| `projects/fan-platform/apps/artist-service` | **서비스 디렉터리** | `COPY build/libs/artist-service.jar` |
| `projects/iam-platform/apps/auth-service` | **프로젝트 루트** | `COPY apps/auth-service/build/libs/` |

지금 재사용 워크플로는 전자만 표현한다. 컨텍스트를 서비스별로 말할 수 없으면
iam 이미지는 `COPY` 단계에서 실패한다.

---

# Goal

`services` JSON 의 항목이 **자기 경로를 스스로 말할 수 있게** 한다. 미지정 시 동작은
지금과 **바이트 단위로 동일**하다(wms / scm / ecommerce 호출자 무수정).

---

# Scope

## In Scope

- `.github/workflows/_platform-e2e.yml`
  - `services` 항목의 **선택** 필드: `dir`(서비스 디렉터리, 리포 루트 기준) ·
    `context`(docker 빌드 컨텍스트) · `jar`(복원 대상 jar 의 스테이징 경로) ·
    `bootjar`(gradle 제외 태스크 전체 경로)
  - **선택** 입력 `extra-boot-jars-artifact` + `extra-download-path` — 두 번째
    `download-artifact` 스텝
- 기존 호출자(wms / fan / scm)의 **무변경 확인** — 기본값이 지금 식과 같은지 실측

## Out of Scope

- 어떤 프로젝트가 어떤 서비스를 넣을지 — 그건 호출자(그 프로젝트의 티켓)가 정한다.
  이 티켓은 **표현 가능하게만** 만든다
- `nightly-e2e.yml` / `ci.yml` 의 개별 잡 내용 — 호출자 소유

---

# Acceptance Criteria

- [x] **AC-1 (기본값 무변경)** — `dir`/`context`/`jar`/`bootjar` 를 지정하지 않은 항목이
      만들어 내는 `mv` 경로 · `docker build -f/컨텍스트` · `-x …:bootJar` 문자열이
      **변경 전과 문자열 단위로 동일**함을 확인한다. 🔴 판정은 "wms 레인이 초록" 이 아니라
      **생성된 명령 문자열의 대조** — 초록은 다른 이유로도 나온다
- [x] **AC-2 (교차 프로젝트 표현 가능)** — 다른 프로젝트의 서비스 항목이
      ① 자기 Dockerfile ② 자기 빌드 컨텍스트 ③ 자기 gradle 태스크 경로
      ④ 자기 아티팩트에서 온 jar 로 빌드된다
- [x] **AC-3 (HARDSTOP-03)** — 재사용 워크플로의 **실행 라인**(주석 제거 후)에
      프로젝트 고유명이 **0건**이다. 프로젝트 지식은 전부 호출자의 JSON 에만 있다.
      🔴 술어를 "파일 전체 0건" 으로 잡으면 **틀린다**: 이 파일 헤더는 이미
      *"for wms / fan / scm platforms"* 라고 적고 있고 `description:` 예시도
      `e.g. projects/wms-platform` 이다. 구속하는 것은 **동작**이지 설명이 아니므로,
      금지되는 것은 `if [ "$name" = … ]` 류의 **프로젝트별 분기·경로**다
- [x] **AC-4 (`set -u` 안전)** — 스크립트는 `set -euo pipefail` 아래서 돈다. 새 선택 필드는
      `jq -r '… // ""'` 로 **빈 문자열 기본**을 취해 미지정 항목이 `null` 문자열을 만들지
      않게 한다. 🔴 `jq -r '.dir'` 는 미지정 시 리터럴 `null` 을 뱉는다 — 경로에 섞이면
      "그런 디렉터리 없음" 이 아니라 **엉뚱한 경로를 만들고 나중에 죽는다**

---

# Related Specs

- `.github/workflows/_platform-e2e.yml`
- `tasks/done/TASK-MONO-326-ci-workflow-dry-refactor.md` (이 재사용 워크플로를 만든 티켓)
- `projects/fan-platform/tasks/ready/TASK-FAN-INT-005-*` (유일한 소비자)

# Related Contracts

- 없음 — CI 배선만 바뀐다

# Edge Cases

- `boot-jars-mode: build`(nightly) 경로에서는 `mv` 스텝이 없다. 새 필드가 그 모드에서
  무해한지 확인할 것 — nightly 호출자는 `bootjar-tasks` 로 잡 안에서 빌드한다
- `-x <task>` 는 존재하지 않는 태스크면 gradle 이 즉시 실패한다. `bootjar` 를 잘못 적으면
  이미지 빌드가 아니라 **gradle 호출**에서 죽는다 — 증상 위치가 원인과 멀다

# Failure Scenarios

- 🔴 **기본값을 "거의 같게" 만든다** — 경로 하나가 미묘하게 달라도 wms/scm 레인이 조용히
  깨진다. AC-1 이 초록이 아니라 **문자열 대조**를 요구하는 이유다
- 🔴 **fan 전용으로 특수화한다** — `if [ "$name" = "auth-service" ]` 같은 분기는 HARDSTOP-03
  위반이다. 일반화가 아니면 이 티켓이 아니다

# Definition of Done

- [x] 네 개 선택 필드(`dir`/`context`/`jar`/`bootjar`) + 두 개 선택 입력
      (`extra-boot-jars-artifact`/`extra-download-path`)
- [x] AC-1 문자열 대조 결과 기재 — § 실측 결과
- [x] AC-3 grep 0건(실행 라인 기준)
- [x] `TASK-FAN-INT-005` 와 **한 PR** 로 원자적 랜딩 — PR #3278
- [x] Ready for review

---

# 실측 결과

## AC-1 — 기존 호출자 3종, 생성 문자열 **byte-identical**

변경 전/후의 도출식을 나란히 실행해 `mkdir` · `mv` · `docker build -f`+컨텍스트 ·
`XARGS` · `DARGS` 를 전부 뽑아 diff:

```
AC-1 wms: IDENTICAL (8 lines compared)
AC-1 fan: IDENTICAL (11 lines compared)
AC-1 scm: IDENTICAL (17 lines compared)
```

합 **36개 문자열**. 🔴 하네스가 워크플로와 다른 코드를 재타이핑했으면 이 초록은 공허하므로,
하네스의 도출 8줄이 `_platform-e2e.yml` 의 실제 8줄과 같은지 **따로 대조**했다(동일).
🔵 기본 `context` 를 `"$dir/"` 로 둔 것도 이 단언 때문이다 — 원문이
`"$PROJECT_DIR/apps/$name/"` 로 **후행 슬래시**를 달고 있었다.

## AC-2 — 교차 프로젝트 항목이 만들어 내는 문자열

```
mv|artifact-staging-fan-iam/auth-service.jar|projects/iam-platform/apps/auth-service/build/libs/auth-service.jar
docker build|-t|iam-auth-service:e2e|-f|projects/iam-platform/apps/auth-service/Dockerfile|projects/iam-platform/
XARGS| … -x :projects:iam-platform:apps:auth-service:bootJar
```

🔴 `jar` 경로에 `build/libs/` 가 **없는** 것이 의도다 — `upload-artifact@v4` 는 **단일 파일**
아티팩트를 그 파일의 디렉터리 기준으로 루팅하므로 스테이징 루트에 `auth-service.jar` 하나만
놓인다(fan 의 3-파일 아티팩트와 모양이 다르다).

## AC-3 — 실행 라인의 프로젝트 고유명

주석 제거 후 매칭 **2건**이고 **둘 다 변경 전부터 있던 `description:` 예시**
(`e.g. projects/wms-platform`)다. 새로 추가한 실행 라인의 고유명 **0건**,
`if [ "$name" = … ]` 류 분기 **0건**.

## AC-4 — `null` 누출 없음

교차 프로젝트 항목과 기본 항목을 섞어 돌린 출력에서 리터럴 `null` **0건**.

## 라이브 확인

PR #3278 의 `E2E (fan-platform v1 live-trio smoke)` 레인이 **iam 이미지를 실제로 빌드하고
기동해** 초록(4/4 PASSED). wms / scm 레인도 같은 런에서 초록 — 재사용 워크플로 변경이
기존 호출자를 깨지 않았다는 **행위 측 확인**(AC-1 의 문자열 대조가 구조 측 확인).

---

🔵 **왜 root 큐인가** — `_platform-e2e.yml` 은 wms / fan / scm 세 프로젝트가 공유하고,
지금까지 이 파일을 건드린 티켓은 전부 root(MONO-326 / 330 / 374)다. 반면 `ci.yml` 의
*자기 잡 블록*은 프로젝트 티켓이 건드려 온 선례가 있다(TASK-FAN-INT-001 이 fan 잡을
만들었다). 그 경계를 그대로 따른다 — 재사용 워크플로=root(이 티켓),
fan 잡 블록=`TASK-FAN-INT-005`.

분석=Opus 5 / 구현 권장=**Sonnet** — 일반화 자체는 기계적이다. 판단은 AC-1 대조에 있다.
