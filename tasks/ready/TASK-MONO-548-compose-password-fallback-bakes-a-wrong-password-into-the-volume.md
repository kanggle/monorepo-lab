# Task ID

TASK-MONO-548

# Title

창고 스택 재시작 루프 — compose 의 `${VAR:-기본값}` 이 `.env` 없이도 **조용히 성공**해서 틀린 비밀번호를 볼륨에 굳힌다. 재기동으로는 절대 안 고쳐진다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

---

# 배경

`docs/guides/interview-demo-walkthrough.md` § 6 의 이 행도 **추적 티켓이 없다**(`—`).
§ 6 드리프트 가드는 추적 칸이 `—` 인 행을 **구조적으로 못 본다**(술어가 인용된 티켓의
상태만 본다) — 그래서 이 행은 어느 큐에도 없이 남아 있었다.

## 기전 (행이 적은 내용은 정확하다, 실측으로 확인)

`projects/wms-platform/docker-compose.yml` 은 DB 비밀번호를 **폴백 기본값과 함께** 읽는다:

```
POSTGRES_PASSWORD: ${POSTGRES_ROOT_PASSWORD:-postgres}
MASTER_DB_PASSWORD: ${MASTER_DB_PASSWORD:-master}
INBOUND_DB_PASSWORD: ${INBOUND_DB_PASSWORD:-inbound}
INVENTORY_DB_PASSWORD: ${INVENTORY_DB_PASSWORD:-inventory}
OUTBOUND_DB_PASSWORD: ${OUTBOUND_DB_PASSWORD:-outbound}
ADMIN_DB_PASSWORD: ${ADMIN_DB_PASSWORD:-admin}
NOTIFICATION_DB_PASSWORD: ${NOTIFICATION_DB_PASSWORD:-notification}
```

`.env` 에는 같은 키들이 `*-changeme-local` 값으로 들어 있다. 그래서 **`.env` 가 안 실린
채 postgres 볼륨이 초기화되면** 롤 비밀번호가 `inbound`/`master`/… 로 굳고, 앱은 `.env`
값으로 접속하다 `FATAL: password authentication failed` 로 죽는다.

🔴 **postgres init 은 빈 데이터 디렉터리에서만 돈다** — 이후 `.env` 를 제대로 실어도
비밀번호는 그대로다. 재기동·재배포로는 **절대** 안 고쳐진다.

🔴 **증상이 원인처럼 안 보인다**: 설정 오류가 아니라 **앱 컨테이너 크래시 루프**
(`Up 3초` 반복)로 나타나므로, 보는 사람은 앱을 의심한다. 진짜 신호는 **의존 대상**인
postgres 컨테이너 로그에 있다.

비파괴 복구(행에 기록됨, 데이터 보존):

```bash
docker exec <pg 컨테이너> psql -U postgres \
  -c "ALTER ROLE <롤> WITH PASSWORD '<.env 의 값>'"   # 해당 롤 전부
```

## 🔴 결함은 폴백 그 자체다

`:-기본값` 은 **누락을 실패가 아니라 성공으로 만든다.** `.env` 를 안 실은 실행이 즉시
멈추는 대신 *다른* 비밀번호로 정상 기동하고, 그 차이는 **볼륨에 영구히 각인된 뒤**
한참 뒤에 크래시로 나타난다. 이 저장소가 반복해서 기록한 모양이다 — 조용히 성공하는
경로가 나중에 다른 얼굴로 실패한다.

같은 폴백이 다른 프로젝트 compose 에도 있는지는 **세어 보고 시작할 것**(AC-0).

---

# Goal

`.env` 없이 이 스택을 올리는 실행이 **볼륨을 오염시키기 전에 멈춘다**. 이미 오염된
볼륨은 **원인을 이름으로 말해 주는** 진단을 얻는다.

---

# Scope

선택지(하나만 고를 필요 없음, AC-2 가 정한다):

1. **폴백 제거** — `${MASTER_DB_PASSWORD:?...}` 로 바꿔 미설정 시 compose 가 **즉시
   실패**하게 한다. 가장 직접적이지만 **다른 진입점(테스트·CI·스크립트)이 그 변수를
   안 실어 주고 있었다면 그쪽이 깨진다** — 그래서 AC-1 이 소비자를 먼저 센다.
2. **프리플라이트 가드** — `infra/demo/` 기동 경로에서 필수 변수 부재를 **기동 전에**
   잡는다. `verify-demo-wrapper.sh` 에 이미 가드 체계가 있으므로 자리는 있다.
3. **오염된 볼륨 진단** — 크래시 루프 시 `demo-status.sh` 가 postgres 인증 실패를
   지목하고 복구 명령을 출력한다. 🔵 이건 위 둘과 **배타적이지 않다**: 이미 오염된
   볼륨은 앞의 둘로 고쳐지지 않는다.

## Out of Scope

- 비밀번호 값 자체의 변경·시크릿 관리 도입.
- 볼륨을 지우는 복구 절차 — 행에 이미 **비파괴** 복구가 기록돼 있고 그쪽이 낫다.

---

# Acceptance Criteria

- [ ] **AC-0 (모집단 재계수)** — 같은 `${VAR:-기본값}` 폴백을 쓰는 **비밀번호/시크릿
      변수**를 저장소 전 compose 파일에서 **전수로 센다**. 🔴 wms 만 고치면 형제 프로젝트가
      같은 결함을 그대로 갖는다(이 저장소의 반복 패턴). 0건이면 0건이라고 적는다.
- [ ] **AC-1 (소비자 조사)** — 그 변수들을 **누가 실어 주는지** 확인한다: `demo-up.sh` ·
      CI 잡 · 로컬 개발 절차 · 테스트. 🔴 폴백을 지우면 **여기 안 실어 주던 경로가 즉시
      깨진다** — 추측하지 말고 각 진입점을 열어 볼 것.
- [ ] **AC-2 (결정 + 구현)** — 1/2/3 중 무엇을 하는지 근거와 함께 정하고 구현한다.
      **하지 않기로 한 것도 산출물**이며 사유를 적는다.
- [ ] **AC-3 (bite — 이 티켓의 핵심)** — `.env` 없이 기동을 시도해 **볼륨이 만들어지기
      전에 멈추는지** 확인한다. 🔴 *"에러가 났다"* 가 아니라 **어느 지점에서 멈췄는지**가
      판정이다 — postgres 가 초기화된 뒤 멈추면 이미 늦었다(그게 이 결함 전체다).
      🔴 **일회용 볼륨/프로젝트명으로 할 것** — 살아 있는 데모 볼륨을 쓰지 말 것.
- [ ] **AC-4 (대조군)** — `.env` 를 제대로 실은 기동이 **여전히 정상**임을 같은 절차로
      확인한다. 가드가 정상 경로까지 막으면 꺼진다.
- [ ] **AC-5 (오염된 볼륨 재현 + 복구 검증)** — 일회용 볼륨을 **일부러 오염시켜**
      크래시 루프를 재현하고, 행에 적힌 `ALTER ROLE` 복구가 **실제로 데이터를 보존한 채**
      복구하는지 확인한다. 🔵 문서에 적힌 복구 절차를 **한 번도 안 돌려 본 채** 남겨두지
      않는다.
- [ ] **AC-6 (행 갱신)** — 추적 기재는 **발행 PR 에서 이미 했다**(`—` → 이 티켓; 가드의
      인용 행이 40 → 42 로 늘었다). 여기서 할 일은 그 인용이 **여전히 붙어 있는지**와,
      이 티켓이 done 으로 갈 때 행이 그 사실을 반영하는지다. 🔵 인용이 붙었으므로
      이제부터는 드리프트 가드가 **대신 잡아 준다** — 안 고치면 CI 가 빨개진다.

---

# Related Specs

- `infra/demo/README.md` · `infra/demo/demo-up.sh` · `infra/demo/demo-status.sh`
- `infra/demo/verify-demo-wrapper.sh` — 기존 가드 체계 (a)~(y)
- `projects/wms-platform/docker-compose.yml` · `.env.example`

# Related Contracts

없음 — 로컬 기동 배선이며 API·이벤트 계약을 건드리지 않는다.

---

# Edge Cases

- **CI 는 이 compose 를 안 쓸 수 있다** — 그러면 폴백 제거의 영향면이 로컬뿐이다.
  안 쓴다는 것도 **확인해서 적을 것**.
- **`POSTGRES_ROOT_PASSWORD:-postgres` 는 성격이 다르다** — 슈퍼유저 롤이라 복구 명령
  자체가 이 자격으로 들어간다. 여기까지 어긋나면 복구 경로가 막힌다.
- **다른 프로젝트가 같은 폴백을 쓰면서 실제로 그 기본값에 의존 중**일 수 있다 — 그 경우
  제거는 마이그레이션이지 한 줄 수정이 아니다.

# Failure Scenarios

- **wms 만 고친다** → 형제 프로젝트가 같은 결함을 그대로 갖는다.
- **AC-3 을 "에러가 났다"로 통과시킨다** → 볼륨이 이미 초기화된 뒤 멈추는 가드는
  이 결함을 하나도 막지 못한다.
- **살아 있는 데모 볼륨으로 bite 한다** → 면접 데모 데이터를 스스로 오염시킨다.
