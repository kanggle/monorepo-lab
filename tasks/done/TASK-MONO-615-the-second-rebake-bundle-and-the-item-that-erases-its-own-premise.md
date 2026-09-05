# Task ID

TASK-MONO-615

# Title

⏳ **재굽기 번들 ②** — `TASK-MONO-610` 기동 창이 남긴 미결을 한 번의 재굽기로 정리한다.
🔴🔴 **그런데 항목 하나(V8)는 재굽기가 그 전제를 지운다 — 순서가 내용보다 먼저다.**

# Status

done

# Owner

monorepo

# Task Tags

- demo
- rebake
- adr-followup

---

# 🔎 어디서 왔나

`TASK-MONO-581`(**done**)이 「재굽기 한 번이 밀린 확인 여섯 개를 정리한다 — 나눠 쓰지 마라」
로 첫 번째 번들을 닫았고, 지금 도는 AMI(`6bc2a44e7`, 2026-08-29)가 그 산출물이다.
`TASK-MONO-610` 기동 창(2026-09-02 UTC)이 **그 뒤로 쌓인 미결**을 실측으로 확정했다.

🔴 **이 티켓은 「재굽기를 하자」가 아니다.** 재굽기 **전에** 반드시 끝나야 하는 것과,
재굽기가 **비로소** 가능하게 하는 것을 가르는 것이 본체다.

---

# 🔴🔴 먼저 — **재굽기가 스스로 지우는 전제**

`TASK-MONO-610` V8 은 *"`V0034` 행이 **기존 볼륨** 위에 실제로 들어갔는가"* 를 묻는다.
CI 는 **항상 신선 볼륨**이라 마이그레이션 순서·멱등성 결함에 영구히 초록이고, 그 결함은
기존 볼륨에서만 드러난다. 기동 창 실측:

```
flyway_schema_history 최대 version = 0033   (2026-08-29 15:54 적용)
→ V8 의 전제 「V0034 미만」이 성립하는 볼륨은 지금 이것 하나다
→ 그 볼륨은 EC2 부팅 2회를 견뎠다 (demo-down.sh 에 -v 가 없다)
```

🔴 **재굽은 AMI 로 인스턴스를 갈면 docker 볼륨이 첫 부팅에 새로 생긴다** ⇒ V8 은 그것이
금지한 **신선 볼륨 판정의 재탕**이 된다. **한 번 재굽으면 V8 은 영원히 못 잰다.**

⇒ **순서가 강제된다:**

| | 무엇 | 왜 |
|---|---|---|
| **0** | 🔴 **V8 을 살아 있는 볼륨 위에서 먼저 잰다** | 아래 § 항목 A |
| 1 | 코드 수정 4건을 main 에 랜딩 | 재굽기가 그것을 굽는다 |
| 2 | 재굽기 + 기동 창 | § 항목 C 를 정리 |

🔵 이것이 `TASK-MONO-581` 이 겪지 않은 형태다 — 581 의 여섯 항목은 서로 독립이었다.
[[feedback_measure_the_plans_premise_before_starting_the_phase]]

---

# 항목 A — 🔴 **재굽기 「전에」 해야 하는 것 (되돌릴 수 없다)**

## A1. V8 — 기존 볼륨 위의 마이그레이션 판정

**왜 저장소 갱신만으로 안 되나** (기동 창 실측): 마이그레이션은 호스트 파일이 아니라
**이미지 안**에 있다.

```
docker exec iam-auth-service-1 → /app/BOOT-INF/classes/db/migration/V0033__… (마지막)
image created = 2026-08-29T14:37:51Z · 바인드마운트 없음
```

⇒ **살아 있는 데모 호스트에서 `iam-auth-service` 이미지만 재빌드**하고 iam 스택만
재기동해 flyway 가 `V0034`·`V0035` 를 **그 볼륨**에 적용하는지 본다.

- 🔴 판정 전에 `flyway_schema_history` 최대가 **`V0034` 미만**임을 다시 확인한다
  (아니면 신선 볼륨 판정의 재탕이다).
- 🔴 **마이그레이션 파일을 grep 하지 마라** — 파일에 있는 것과 **행에 들어간 것**은 다른 축이다.
- 판정 SQL: `SELECT redirect_uris FROM oauth_clients WHERE client_id='platform-console-web';`
  → `https://console.hubwang.com/api/auth/callback` 이 **행에 있는가**.
- 🔵 같은 재빌드가 `V0035`(`store.hubwang.com`)도 넣으므로 **`TASK-MONO-612` AC-0 ②**
  (store 런타임 값)와 **store 로그인**의 선행도 함께 풀린다.
- 🔴 실패 시 이전 이미지 태그로 롤백 가능하지만 **IdP 가 몇 분 내려간다** — 소유자 승인 사안.

---

# 항목 B — **코드 수정 4건. 재굽기 전에 main 에 있어야 한다**

## B1. 🔴 V4 — 라우터가 `/connect` 를 auth-service 로 보내지 않는다

```
iam-oidc.rule = Host(iam.<도메인>) && (PathPrefix(/oauth2) || /login || /signup || /.well-known)
```

discovery 는 `end_session_endpoint: …/connect/logout` 을 **광고하는데** 그 경로가 목록에
없다 ⇒ 바깥에서 **404**. 컨테이너 직격은 **401**(엔드포인트는 존재한다).

- 자리: `infra/demo/iam-traefik.override.yml`
- 🔴 **가드가 필요하다**: 「discovery 가 광고하는 모든 엔드포인트의 경로 접두사가 라우터
  규칙에 포함되는가」. 지금 이 결함은 **아무것도 빨갛게 만들지 않는다** — `TASK-MONO-574`
  AC-2 가 「V4 공백」을 경고했고 그 공백이 정확히 이것이었다.
- 🔵 술어를 「`/connect` 가 있는가」로 쓰지 마라. **discovery 문서에서 파생**해야 다음
  엔드포인트가 추가될 때도 문다.
  [[feedback_a_guard_that_names_a_cause_needs_a_predicate_only_that_cause_trips]]

## B2. 🔴 `provision-demo-env.sh` 의 자리표시자가 팬 로그인을 죽인다

`fan-platform-web` 의 런타임 `OIDC_CLIENT_SECRET` = **`replace-with…`**.
`demo-boot.sh` → `provision-demo-env.sh` 가 `projects/*/.env.example` 을 그대로 복사하고,
compose 는 파일 옆의 `.env` 를 자동 로드해 **기본값 `${OIDC_CLIENT_SECRET:-fan-platform-dev}`
를 덮는다.**

- **대조군**: 같은 토큰 POST 를 `ecommerce-web-store-client:ecommerce-dev` 로 보내면
  **`invalid_grant` 400**(=인증 통과). 팬만 **`invalid_client` 401**.
- 증상은 `?error=Configuration` 이라 **어느 env 가 틀렸는지 말하지 않는다.**
- 🔴 **모집단을 세라** — `.env.example` 을 가진 프로젝트 전부에서 「예제 값이 compose
  기본값을 덮어 죽은 값이 되는」 키가 몇 개인지. 팬 하나만 고치면 형제가 낙오한다.
  [[feedback_grep_the_siblings_before_fixing_it_yourself]]
- 🔵 `TASK-MONO-550` 은 preflight 가드를 **정당하게** 충족시켰다. 그 정당한 조치가 바로
  이 자리에서 로그인을 죽였다 — 되돌리지 말고 **값의 출처**를 고쳐라.
  [[feedback_a_fallback_is_not_a_placeholder]] [[feedback_two_correct_exclusions_compose_into_a_hole]]

## B3. 🔴 가드 (w) 가 C2 아래서 위양성이고, 그 FAIL 이 (z18) 을 가린다

`verify-demo-wrapper.sh` (w) 의 술어 = *"JWKS 호스트가 이 서비스의 **docker 네트워크
alias** 인가"*. `IAM_PUBLIC_URL` 이 `https://auth.hubwang.com` 으로 뒤집히면 그 이름은
alias 가 아니라 **공개 DNS** 라 FAIL 한다. 런타임은 반증한다 —
`getent hosts auth.hubwang.com` = `216.150.16.129`, `curl JWKS` = **200 433B**, 오류 로그 0건.

- alias 는 도달 가능의 **충분조건이지 필요조건이 아니다.** C2 가 두 번째 경로를 만든다.
- 🔴 **완화하지 마라 — 넓혀라**: 「alias 이거나, **공개 DNS 로 해소되고 그 컨테이너가
  egress 를 갖는다**」로. 그리고 (w) 의 원래 근거(MONO-507: 해소 실패가 401 로 위장한다)는
  여전히 참이므로 **술어를 지우면 안 된다.**
- 🔴 이 FAIL 이 실행을 중단시켜 **`TASK-MONO-606` AC-4′ ②((z18))가 미도달**로 남았다.
  B3 없이는 그 칸을 잴 수 없다.
- 🔴 **남은 공백**: 「JVM 이 그 JWKS 로 토큰을 실제로 검증했다」는 아직 **미측정**이다
  (유효한 팬 토큰이 필요하고 그건 B2 에 막혀 있다). B2+B3 이 끝나면 그것부터 재라.

## B4. 🔴🔴 V5 — 두 번째 부팅이 스스로 뜨지 못한다 (**경합**)

```
14:23:21  iam-auth-service-1     Recreate
14:24:01  iam-gateway-service-1  Recreated
14:24:01  Container iam-kafka Waiting / iam-redis Waiting / iam-mysql Waiting
          → 두 컨테이너가 「Created」로 남음 (started=0001-01-01T00:00:00Z)
14:41:03  demo-stack.service: start operation timed out → failed
```

**기전**: EC2 부팅에서 `restart=unless-stopped` 컨테이너가 dockerd 와 함께 먼저 살아난다.
그 뒤 `docker compose up -d` 는 **Traefik 라벨에 `DEMO_DOMAIN` 이 박힌 서비스를 매 부팅
recreate** 하고(도메인이 부팅마다 바뀌므로 **항상** 해당) `depends_on: service_healthy` 를
기다린다. 방금 살아난 kafka/mysql/redis 가 아직 healthy 가 아니면 **recreate 된 앱이
시작되지 못한 채 남는다.**

- 🔵 **자원 고갈이 아니다**: 메모리 63GB 중 **31.6GB 여유** · 디스크 26%.
- 🔵 **손으로 `docker start` 하면 즉시 healthy** 가 되고 왕복도 성립한다 ⇒ 이미지·설정
  문제가 아니라 **순서 문제**다.
- 🔴 **1회차는 성공하고 2회차는 실패했다 ⇒ 결정론이 아니라 경합이다.** 단일 성공을
  성질로 승격시키지 마라. [[feedback_local_proves_behaviour_not_performance]]
- 🔴 **이것이 `ADR-MONO-069` 축 ②(「사람 손 없이 두 부팅을 건넌다」)를 막고 있다.**
  🔵 다만 원인이 issuer 이름과 **독립**이므로 **C1/C2 재지정 근거는 아니다** — 세 IP 를
  건너 같은 이름이 왕복을 성립시킨 것은 별도로 관측됐다.
- 후보(고르지 않았다 — 실측 후 결정): ⓐ 부팅 시 `demo-down.sh` 를 먼저 돌려 잔존
  컨테이너를 정리 ⓑ compose 의 의존 대기 타임아웃/조건 조정 ⓒ `restart` 정책을 데모
  프로파일에서 바꾼다 ⓓ 라벨에서 `DEMO_DOMAIN` 을 빼 recreate 자체를 없앤다.
  🔴 ⓓ 는 Traefik 라우팅의 근간이라 **범위가 크다** — 고르기 전에 `TASK-MONO-358` 계보를 읽어라.

---

# 항목 C — **재굽기가 「비로소」 정리하는 것**

| # | 무엇 | 지금 왜 못 닫나 |
|---|---|---|
| C1 | **`TASK-MONO-604` AC-4** — `web.ecommerce.<도메인>` 이 404 | 🔴 **지금의 404 는 억제가 아니라 stale label 이다**(604 § CORRECTION). 억제 선언보다 오래된 컨테이너는 `profiles:` 게이트 밖이고 `--remove-orphans` 가 안 지운다 ⇒ 그 컨테이너가 **애초에 없는** AMI 가 필요하다 |
| C2 | `auth-forwarder`·`backend-resolver` 가 호스트 저장소에 존재 | 기동 창에서는 `git reset --hard` 로 임시 충족시켰다 — **호스트 로컬 상태이지 AMI 가 아니다** |
| C3 | `IAM_PUBLIC_URL` 뒤집기가 **저장소 판본**으로 | 지금은 **호스트 로컬 한 줄**이다. main 의 `demo.env` 는 아직 `http://iam.${DEMO_DOMAIN}` |
| C4 | 호스트의 `projects/fan-platform/.env` 가 **새 `.env.example` 을 반영** | 🔴 B2 가 저장소를 고쳐도 호스트는 안 바뀐다 — `provision-demo-env.sh` 는 멱등이고 호출자는 `demo-boot.sh` 뿐(**부팅 시점**이지 굽는 시점이 아니다)이라, 지금 인스턴스의 `.env` 에는 자리표시자가 그대로 있다 |

🔴 **C3 을 main 에 랜딩하는 PR 은 B3(가드 (w))을 같은 PR 에 넣어야 한다** — 안 그러면
그 PR 이 `verify --live` 를 빨갛게 만든다. 한 변경이 가드를 무효화하면 **같은 PR 에서 갚는다.**

---

# Goal

`TASK-MONO-610` 기동 창이 남긴 미결을, **순서를 지켜** 한 번의 재굽기로 정리한다.

# Scope

- **포함**: A1(볼륨 위 V8) · B1–B4(코드 수정) · C1–C4(재굽기가 정리하는 것)
- **제외**: `ADR-MONO-069` 재지정 — B4 는 이름 축과 독립이므로 결정을 다시 열지 않는다
- **제외**: `TASK-MONO-612` AC-1(소유자 Vercel 지정) — 별 축

# Acceptance Criteria

## AC-0 — 🔴 **착수 시 재측정한다 (verify-then-act)**

1. `flyway_schema_history` 최대가 **여전히 `V0034` 미만**인가 — 아니면 **A1 은 이미
   불가능**하고 이 티켓의 순서 제약이 사라진다(그 사실을 적고 항목 A 를 STOP 으로 닫아라).
2. B1–B4 가 **아직 참인가** — 넷 다 재현 절차가 위에 있다. 하나라도 이미 고쳐졌으면
   그 항목을 지우지 말고 **「해소됨 + 무엇이 고쳤나」** 로 남겨라.
3. 🔴 **항목 간 상충을 다시 본다** — 이 티켓의 존재 이유가 「한 항목의 수단이 다른 항목의
   전제를 지운다」이므로, 새로 추가된 항목이 있으면 그 축을 먼저 검사한다.

## AC-1 — A1 을 **재굽기 전에** 닫는다

- 이미지 재빌드 → iam 스택 재기동 → `flyway_schema_history` 에 `V0034`·`V0035` 가
  **행으로** 들어갔는가. 🔴 파일 grep 은 판정이 아니다.
- 🔴 실패해도 **재굽기로 넘어가지 마라** — 넘어가면 이 축은 영구히 닫힌다.

## AC-2 — B1–B4 를 main 에 랜딩한다

- B1·B3 은 **가드를 동반**한다(수정만 하고 가드를 안 만들면 조용히 되돌아간다).
- B2 는 **모집단 전수**를 먼저 센다.
- B4 는 **경합**이므로 「한 번 됐다」로 닫지 마라 — **부팅 2회 연속**이 기준이다.

## AC-3 — 재굽기 + 기동 창으로 C1–C4 을 닫는다

- C1 판정: `web.ecommerce.<새 도메인>` 이 404 이고, **그 404 가 stale label 이 아님**을
  같이 보인다(컨테이너가 **존재하지 않아야** 한다). 🔴 상태 코드만으로 판정하지 마라.
- C3 판정: 새 AMI 의 `demo.env` 가 저장소 판본이고, 호스트 로컬 수정이 **0건**이다.
- C4 판정: 호스트의 `projects/fan-platform/.env` 를 **파일에서 직접** 읽어 `OIDC_CLIENT_SECRET=fan-platform-dev` 임을 확인한다. 🔴 저장소의 `.env.example` 을 grep 하는 것은 판정이 아니다 — 그건 이미 고쳐졌고, 물어보는 것은 「런타임이 그걸 반영했는가」이다.
  그리고 그때 비로소 **팬 로그인 자체를** 재본다 — B2 는 측정된 기전에서 파생했을 뿐 로그인 성공을 본 것이 아니다.

## AC-4 — 🔴 이 티켓이 **안 고치는 것**을 적는다

- `TASK-MONO-586` 라이브 축 · `TASK-MONO-610` AC-4b 발효 — 둘 다 **Vercel 배포**가
  선행이고, 이 저장소의 네 Vercel 프로젝트 모두 *"판정자가 이 커밋은 그 앱을 안 건드렸다고
  보면 배포가 안 생긴다"* 는 구간을 갖는다(`kanggle-auth`·`kanggle-fan` 에서 **2회 관측**).
  🔴 **재굽기는 그것을 안 고친다.**

# Related Specs

- [`docs/adr/ADR-MONO-069`](../../docs/adr/) — 축 ②(사람 손 없이 두 부팅)
- [`docs/adr/ADR-MONO-067`](../../docs/adr/) — 단계 2·4
- `TASK-MONO-581` — 첫 번째 번들(선례) · `TASK-MONO-610` § 기동 창 실측 원장 — 이 티켓의 근거 전부
- `TASK-MONO-604` § CORRECTION · `TASK-MONO-606` § CORRECTION · `TASK-MONO-612` § AC-0 실측

# Related Contracts

없음.

# Edge Cases

- 🔴 A1 의 이미지 재빌드가 실패하면 **IdP 가 내려간 채로 남을 수 있다** — 이전 이미지
  태그를 먼저 확보하고 롤백 경로를 적어 둔 뒤 시작한다.
- 🔴 재굽기 뒤 `terraform.tfvars` 의 AMI 핀을 갱신하지 않으면 **구운 것을 아무도 안 가리킨다.**
- 🔴 인스턴스 교체는 **볼륨 소멸**을 뜻한다 — A1 이 끝나지 않았으면 그 순간 이 티켓은 실패다.

# Failure Scenarios

| 상황 | 잘못된 처리 | 옳은 처리 |
|---|---|---|
| A1 이 번거로워 재굽기부터 한다 | 「어차피 재굽으면 다 되니까」 | 🔴 **그 순간 V8 은 영원히 못 잰다.** 순서가 이 티켓의 본체다 |
| C1 을 상태 코드로 판정 | `404` 를 보고 「억제 성공」 | 🔴 컨테이너 **존재 여부**를 같이 본다. 기동 창에서 정확히 이렇게 오독했다 |
| B3 을 「가드 끄기」로 처리 | 상한 완화 / 예외 추가 | 🔴 술어를 **넓혀라**. (w) 의 원래 근거는 여전히 참이다 |
| B4 를 한 번의 성공으로 닫는다 | 「이번엔 떴다」 | 🔴 **경합**이다. 부팅 2회 연속이 기준이다 |

---

# ✅ B1 구현 (2026-09-02 UTC) — 라우터가 `/connect` 를 덮고, **discovery 에서 파생한** 가드가 지킨다

## 무엇을 고쳤나

`infra/demo/iam-traefik.override.yml` 의 `iam-oidc.rule` 에 `PathPrefix(/connect)` 를 더했다.
🔵 값은 한 조각이지만 **왜 손으로 열거하면 안 되는지**를 같은 자리에 적었다 — 이 파일의
기존 주석이 이미 `/signup` 으로 **같은 결함을 한 번 겪었다**고 기록하고 있었고(`TASK-MONO-380`),
`/connect` 는 **두 번째**다.

## 가드 — 두 칸, 서로 다른 축

| 칸 | 언제 도나 | 무엇을 잰다 | 왜 이 축인가 |
|---|---|---|---|
| **(z20)** | **정적**(게이트 앞 ⇒ CI 포함) | 라우터 ⊇ **핀** · `/.well-known` 별도 단언 | 🔴 라이브 IdP 가 없는 CI 에서도 **물어야** 한다. 라이브 전용 칸은 IdP 가 없으면 skip 이고 **skip 은 판정이 아니다** |
| **(z21)** | `--live` | 라이브 discovery == 핀 · 라이브 ⊆ 라우터 | 핀이 낡으면 (z20)의 초록은 「덮었다」가 아니라 **「덜 알고 있다」**이다. 그 축을 잰다 |

**핀**: `infra/demo/idp-advertised-path-prefixes.txt` — `scripts/required-check-names.txt`
선례를 따른다(**권위에서 받아 적고 손으로 타이핑하지 않는다**). 헤더에 재파생 명령과
출처(2026-09-02 기동 창)를 박았다.

🔴 **핀의 완전성은 주장하지 않는다.** 그 세션은 문서 1,587B 중 **앞 약 900B 만** 읽었으므로
목록은 「관측된 접두사 전부」이지 「문서가 광고하는 전부」가 아니다. **완전성은 (z21)이
라이브에서 강제한다** — 핀에 없는 접두사를 IdP 가 광고하면 **FAIL** 이고, 그때 핀을 다시
받아 적는다. 이 문장을 핀 헤더에도 적어 뒀다.

## 실측 — 가드가 무는 것을 증명했다

정적 스위트 전체 **rc=0**, (z20) 통과: `핀 2건(/connect /oauth2) ⊆ 라우터 5건 · /.well-known 별도 확인`.

**bite**(주입이 됐는지부터 단언하고, 사유마다 다른 술어가 물어야 한다):

| # | 주입 | 주입 확인 | 결과 |
|---|---|---|---|
| ① | 규칙에서 `/connect` 제거 | 규칙 내 잔존 **0** | ✅ `rc=1` — *"라우터가 안 덮습니다: /connect"* |
| ② | 규칙에서 `/.well-known` 제거 | 잔존 **0** | ✅ `rc=1` — 전용 문구가 나온다 |
| ③ | 핀을 주석만 남기고 비움 | 비주석 줄 **0** | 🔴→✅ **처음엔 조용히 죽었다**(아래) · 고친 뒤 *"핀이 비어 있습니다"* |
| ④ | 라우터 키 이름 변경 | 원래 키 잔존 **0** | ◑ **z20 에 도달 못 함** — 가드 **(p)** 가 먼저 물었다(아래) |
| ⑤ | 핀에 라우터가 안 덮는 접두사 추가 | 비주석 줄 2→**3** | ✅ `rc=1` — *"…안 덮습니다: /registration"* (핀→라우터 방향도 문다) |

### 🔴 bite ③ 이 **내 가드의 결함**을 잡았다 — 「멈추는데 이유를 안 말한다」

첫 판은 `z20_want="$(grep -vE '…' "$pin" | sort -u)"` 였다. 핀이 비면 `grep` 은 0건이라
**종료코드 1** 이고, `set -euo pipefail` 아래 명령치환 실패는 스크립트를 **아무 메시지 없이**
죽인다. 즉 «핀이 비어 있습니다» 라고 적어 둔 그 문장이 **영원히 안 나오는 자리**였다.
빌드는 멈추므로 「가드가 물었다」로 착각하기 쉽다. ⇒ 같은 형태 **5곳**에 `|| true` 를 넣고
0건 판정을 호출부로 옮겼다. 🔵 그 이유를 코드 옆 주석에도 남겼다.

### ◑ bite ④ 는 z20 에 **도달하지 못했다** — 첫 원인이 둘째를 가린다

라우터 키 이름을 바꾸자 **가드 (p)** 가 먼저 *"iam-traefik.override.yml 에 iam-oidc 라우터
규칙이 없습니다"* 로 죽었다. ⇒ z20 의 *"술어가 형태를 놓쳤습니다"* 분기는 이 주입으로는
**가려져 있다**. 🔵 지우지 않는다 — (p) 가 옮겨지거나 사라지면 그때 이 분기가 유일한
방어선이 된다. 🔴 그러나 **「bite 5/5」라고 적지 않는다**: 실제로 z20 이 판정한 것은 **4칸**이다.

**(z21) 추출기**는 IdP 없이도 증명했다 — 이번 창에서 **실제로 받은 discovery 바이트**에
파이프라인을 돌려 `/connect /oauth2` 를 얻었고, 음성 대조군(경로 없는 issuer 만) **0건**,
양성 대조군(새 접두사 `/registration` 섞기)에서 **잡혔다**.
🔴 그러나 **(z21) 자신이 라이브에서 도는 것은 아직 안 봤다** — IdP 가 필요하고 데모는 꺼져
있다. **다음 기동 창의 첫 확인 항목**이다.

### 🔴🔴 그리고 그 자체 검사가 **또 하나를 잡았다** — 없었으면 조용히 초록이었다

(z21s) 를 넣자마자 FAIL 했다: 기대 `/connect /oauth2` 인데 실제는 **`/`** 하나.
원인은 로직이 아니라 **파일에 쓰인 바이트**였다 — 삽입 경로가 백슬래시를 접어
`s|…|/\1|` 의 `\1` 이 **제어문자 0x01** 로 들어갔고, 줄 이음도 사라져 있었다.
🔴 **아무것도 「실패」하지 않는 결함이다**: 함수는 정상 종료하고 `/` 를 뱉으며, 라이브
IdP 가 없는 CI 에서는 (z21) 이 skip 이라 **누구도 그것을 보지 못한다.**
⇒ 이스케이프를 재해석하지 않는 경로로 다시 쓰고, 들어간 바이트를 **`cat -A` 로 확인**했다.
같은 접힘이 `printf '%s\n'` **5곳**, `$'\n'` **15곳**, `tr '\n'` **4곳**에도 있어 함께 복구했다.
🔵 교훈은 「자체 검사를 넣어라」가 아니라 **「자체 검사가 없었다면 이 가드는 태어날 때부터
공허했다」**이다. [[feedback_assert_the_injection_before_reading_the_bite]]

## 🔴 AC-0 재측정 — 이번에 할 수 있었던 것과 못 한 것

| # | AC-0 항목 | 상태 |
|---|---|---|
| ① | `flyway` 최대가 여전히 `V0034` 미만인가 | 🔴 **재측정 못 했다** — 데모가 꺼져 있다. 🔵 다만 그 볼륨은 정지 중 변하지 않고, 그 이미지에 `V0034` 가 **없으므로** 값이 오를 경로가 없다. **A1 착수 시점에 실제로 다시 재라** — 이 문단은 추론이지 측정이 아니다 |
| ② | B1–B4 가 아직 참인가 | B1 ✅ 참이었고 **이 PR 이 닫는다**. B2·B4 는 호스트가 필요해 미재측정, B3 은 C3(뒤집기 랜딩)과 같은 PR 로 간다 |
| ③ | 항목 간 상충 | B1 은 다른 항목의 전제를 건드리지 않는다(라우팅 축 단독). A1 의 순서 제약은 **그대로 유효**하다 |

---

# ✅ B2 구현 (2026-09-03 UTC) — 값이 틀린 게 아니라 **두 번 선언돼 있었다**

## 모집단 실측 — 티켓이 요구한 전수조사

「예제 값이 compose 기본값을 덮어 죽은 값이 되는 키가 몇 개인가」를 8개
`.env.example` × 그 프로젝트의 compose 전부에 대해 셌다.

| 축 | 수 |
|---|---|
| compose 가 `${KEY:-기본값}` 을 주는 키 | **75** |
| 그중 `.env.example` 이 **다른 값으로 덮는** 키 | **10** |
| 그중 실제로 **죽는** 키 | **1** |

🔵 나머지 9개가 왜 안 죽는지가 이 조사의 핵심이다 — 판별자는 **「상대편이 같은
`.env` 를 읽는가」**다.

- **자기정합 9건** (wms DB 비밀번호 7 · wms Grafana · ecommerce MinIO): 같은 변수가
  **서버 컨테이너와 소비자 양쪽**에 간다. 실측 확인 —
  `MASTER_DB_PASSWORD` 는 postgres 의 init 스크립트와 앱의 `DB_PASSWORD` 에 동시에,
  `MINIO_ROOT_PASSWORD` 는 minio 와 그 클라이언트에 동시에. 값이 무엇이든 양쪽이
  같으므로 산다. (단 **기존 볼륨**에는 옛 값이 각인돼 있고, 그것이 preflight 가드가
  막는 위험이다 — 이 조사는 그 축을 대체하지 않는다.)
- **교차정합 1건** (fan `OIDC_CLIENT_SECRET`): 상대편은 **IAM 의 시드 행**이고 그
  BCrypt 해시는 `.env` 로 바꿀 수 없다. 그래서 이것만 죽는다.

🔴 **「덮는다」를 술어로 쓰면 10건 중 9건이 위양성이다.** 가드를 그렇게 쓰면 안 된다.

## 기전 — 값이 아니라 **선언이 둘**이었다

`projects/fan-platform/.env.example` 이 같은 키를 두 번 선언하고 있었다.

```
 24 OIDC_CLIENT_ID=fan-platform-user-flow-client
 25 OIDC_CLIENT_SECRET=fan-platform-dev          ← 시드와 맞는 값
 …
 72 OIDC_CLIENT_ID=fan-platform-user-flow-client
 73 OIDC_CLIENT_SECRET=replace-with-secret-from-iam-seed   ← dotenv 는 이쪽이 이긴다
```

두 선언은 **서로 다른 절**에 있었다(백엔드 절 · `TASK-FAN-FE-001` 프런트 절).
그리고 73행 위의 주석은 아직도 *"이 클라이언트를 V0011 시드에 추가해야 sign-in 이
동작한다"* 는 **FOLLOW-UP** 이었다 — 그런데 V0011 은 이미 그 클라이언트를 시드하고
있고, 그 해시는 평문 `fan-platform-dev` 와 맞는다(시드 SQL 자신이 그렇게 적어 뒀다).
**후속 작업은 끝났는데 자리표시자가 회수되지 않았다.**
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]
[[feedback_retract_the_exemption_when_the_defect_is_fixed]]

## 고친 것

- `projects/fan-platform/.env.example` — 아래쪽 재선언을 **삭제**하고, 그 자리에
  「여기서 다시 선언하지 말 것 + 왜 죽었는지 + FOLLOW-UP 은 이미 완료」를 적었다.
  값을 `fan-platform-dev` 로 **고쳐 쓰지 않았다** — 그러면 재선언이 남아 다음에 또
  한쪽만 고쳐진다.

## 가드 — 정적 2칸

두 칸은 **서로 다른 것**을 문다. 하나만으로는 부족하다.

- **(z22) 재선언 금지** — `projects/*/.env.example` 에 같은 키가 두 번 나오면 FAIL.
  술어를 「fan 의 시크릿이 맞는가」로 쓰지 않았다. 다음에 **다른 파일 다른 키**에서
  나도 물어야 하기 때문이다. 실측 8파일 · 130키.
- **(z23) 시크릿 ↔ 시드 대조** — 재선언 없이 값 하나만 틀려도 로그인은 똑같이 죽고,
  증상(`?error=Configuration`)은 어느 env 가 틀렸는지 말하지 않는다. 그래서 값 자체를
  시드가 문서화한 평문 집합과 대조한다(시드는 BCrypt 해시만 저장하므로 **주석이 유일한
  기계가독 연결고리**다 — 형식이 바뀌면 하한이 문다).

🔵 **모집단을 증거로 걸러낸다.** `*CLIENT_SECRET` 8건 전부가 대상이 **아니다** — IAM
자신의 `.env.example` 에 있는 구글·카카오·MS 시크릿은 **상류 소셜 IdP 자격**이지 이
IdP 에 등록된 클라이언트가 아니다. 프로젝트 이름을 하드코딩해 빼지 않고, **형제
`*CLIENT_ID` 의 값이 시드에 등록된 client_id 인 것만** 센다. 결과: 8 → 3건 자동 제외,
**5건 검사 · 미커버 0건**(ecommerce 는 형제 ID 가 `.env.example` 이 아니라 compose
기본값에 있어서 거기까지 찾는다 — 안 찾았으면 그 한 칸이 조용히 미커버로 남았다).

실행 결과: `(z22) 재선언 0건 — 파일 8개 · 키 130개` / `(z23) 시크릿 5건이 시드 평문
6건 안에 있다 — 등록 client_id 16건 기준`.

## bite 6/6 — 각 칸이 **다른 메시지**로 문다

| # | 주입 | 주입단언 | 기대 | 결과 |
|---|---|---|---|---|
| 0 | 없음(대조군) | — | PASS | ✅ |
| 1 | 팬 재선언 블록을 되돌린다(**역사적 결함 재현**) | 선언 수 2 | (z22) FAIL | ✅ |
| 2 | scm 값만 `scm-wrong-secret` (**재선언 없음**) | 바뀐 값 1 · 재선언 0 | (z23) FAIL | ✅ |
| 3 | 시드 주석 형식 변경 → 평문 추출 불능 | 남은 주석 0건 | (z23) 하한 FAIL | ✅ |
| 4 | `*CLIENT_SECRET` → `*CLIENT_TOKEN` (규약 변경) | 남은 키 0건 | (z23) 하한 FAIL | ✅ |
| 5 | 복원 후 재대조군 | — | PASS | ✅ |

🔵 ②가 **(z22) 가 아니라 (z23)** 으로, ③과 ④가 **서로 다른 하한 메시지**로 떨어지는
것을 확인했다 — 세 실패가 한 문장으로 뭉치면 진단이 안 된다.

🔵 **하네스와 전체 실행은 서로 다른 것을 잰다.** bite 는 래퍼에서 그 구역을 **그대로
추출한**(재타이핑하지 않은) standalone 하네스로 돌렸고 — `set -euo pipefail` 복제를
단언한다 — **술어가 무는가**만 증명한다. **칸에 도달하는가**는 래퍼 전체 실행이
따로 증명했다. 하나로 둘 다 주장하지 않는다.

## 🔴 안 고친 것 / 남은 공백

1. **살아 있는 호스트의 `.env` 는 이 PR 로 안 바뀐다.** `provision-demo-env.sh` 는
   멱등이라 `.env` 가 있으면 건드리지 않고, 호출자는 `demo-boot.sh` **하나뿐**이다
   (굽는 시점이 아니라 **부팅 시점**). 지금 도는 인스턴스의
   `projects/fan-platform/.env` 에는 자리표시자가 그대로 있다. 발효 조건은 **인스턴스
   교체**(재굽기 → C 항목)이거나 그 파일 삭제 후 재부팅이다.
   [[feedback_declaration_files_are_not_the_runtime_state]]
   ⇒ **C 항목 검증에 추가**: 재굽기 후 호스트의 `projects/fan-platform/.env` 의
   `OIDC_CLIENT_SECRET` 이 `fan-platform-dev` 인가를 **파일에서 직접** 확인한다.
2. **팬 로그인이 실제로 되는지는 미측정이다.** 데모가 꺼져 있다. 이 수정은 측정된
   기전(자리표시자 → `invalid_client` 401, 올바른 시크릿을 쓴 ecommerce 대조군은
   `invalid_grant` 400 으로 인증 통과)에서 **파생**한 것이지, 로그인 성공을 본 것이
   아니다. 닫으려면 기동 창이 필요하다.
3. **`NEXTAUTH_SECRET=replace-with-32-bytes-of-random-data` 는 그대로 뒀다.**
   자리표시자 어휘를 갖지만 **죽지 않는다** — next-auth 는 임의 문자열을 받고, 상대편이
   없다(자기정합). 커밋된 예제에 진짜 난수를 넣으면 모든 호스트가 같은 값을 쓰게 되어
   나아지지도 않는다. 강한 자격은 별개 판단이고 그때 출처는 예제가 아니라 생성기다.

---

# ✅ B3+C3 구현 (2026-09-03 UTC) — 완화가 아니라 **좁은 면제**, 그리고 뒤집기를 저장소로

## 🔴 먼저 — 티켓에 적힌 기전이 틀렸다

B3 절은 *"`IAM_PUBLIC_URL` 이 뒤집히면 그 이름은 alias 가 아니라 공개 DNS 라 (w) 가
FAIL 한다"* 고 적고 있었다. 코드를 따라가 보니 그 경로가 **아니었다** —

```
demo.env:59   IAM_JWKS_URL=http://iam-auth-service:8081/oauth2/jwks   ← 뒤집기와 무관
demo.env:66   JWT_JWKS_URI=${IAM_JWKS_URL}                            ← 그대로 alias
demo.env:111  INTERNAL_JWT_JWK_SET_URI=${IAM_PUBLIC_URL}/oauth2/jwks  ← **이것이 진범**
```

그리고 그 변수를 받는 서비스는 `projects/fan-platform/docker-compose.yml:193` 하나다.
🔴 **모집단은 「백엔드 19개」가 아니라 1건이었다.** 기억에서 원인을 가져왔다면 술어를
훨씬 넓게 썼을 것이고, 그만큼 가드를 더 많이 죽였을 것이다.
[[feedback_a_verifiable_mechanism_is_not_the_cause]]

**재현**: 뒤집힌 `demo.env` 로 래퍼를 그대로 실행 → `rc=1`,
`(w) … fan:membership-service … JWKS 호스트 'auth.hubwang.com' 가 이 서비스의
네트워크 어디에도 없습니다`.

## 🔴 그리고 (w) 혼자가 아니었다

`(z12)` 는 `DEMO_DOMAIN=z12-probe.invalid` 로 렌더한 뒤 *"점 있는 호스트는 반드시
프로브에서 파생돼야 한다"* 를 요구한다. 고정 이름 `auth.hubwang.com` 은 여기서도 BAD 다.
**(w) 가 먼저 죽어 그 사실을 가리고 있었다** — 첫 실패가 뒤를 덮는 자리다.
[[feedback_why_a_guard_does_not_bite]]

## 넓힘의 모양 — 면제는 **정확히 한 호스트**

두 칸 모두 같은 규칙으로 파생한다: `demo.env` 자신의 `IAM_PUBLIC_URL` 이 **`https`** 일 때
그 **호스트 하나**. 저장소에 이름을 박지 않는다.

- 🔵 **불활성 성질**: 기본값 `http://iam.${DEMO_DOMAIN}` 아래서는 `https` 조건이 거짓이라
  면제가 **완전히 꺼진다**. 즉 이 가드 변경 자체는 오늘의 판정을 하나도 바꾸지 않고,
  켜지는 시점이 정확히 C3(뒤집기)이 랜딩될 때다. bite ⓪이 이걸 잰다(면제 **0건**).
- 🔴 비교는 **`=`** 다 — 접미사 매치가 아니다. bite ③④가 다른 공개 호스트와
  `auth.hubwang.com.attacker.tld` 를 각각 거부하는 것으로 확인했다.
- 🔴 **(w) 삼킴 바닥**: 면제가 리소스 서버 **전부**를 덮으면 FAIL 이다. 그러면 경로 A 가
  한 번도 실행되지 않은 것이고, 이 칸은 「이름이 해소되는가」를 더 이상 재지 않는다 —
  통과가 아니라 재설계 신호다. bite ⑥이 실제로 그 상태를 만들어 확인했다.

🔴 **정적으로 증명되는 것이 줄어든다.** 경로 A 는 「이름이 컨테이너 fabric 안에서
해소된다」를 증명했지만, 경로 B 는 「그 이름이 데모가 선언한 공개 IdP 다」까지만 증명한다.
**실제 도달성은 라이브 축의 몫**이고, 그 공백은 이 티켓 B3 § 남은 공백에 이미 이름이
적혀 있다(「JVM 이 그 JWKS 로 토큰을 실제로 검증했다」= 미측정). 여기서 메운 척하지
않는다. 코드 주석에도 그렇게 적었다.
[[feedback_two_correct_exclusions_compose_into_a_hole]]

🔴 **(z12) 의 면제는 크다** — 뒤집힌 상태에서 주입 82건 중 **43건**이 면제된다(issuer 계열이
전부 IdP 를 가리키므로 당연하다). 그래서 (z12) 에는 삼킴 바닥을 두지 **않았다** — 남은
가치는 「선언된 IdP 도 프로브 파생도 아닌 하드코딩 호스트」를 잡는 것이고 그건 43건이
면제돼도 살아 있다. 대신 **양성 대조군**(bite ⑤)으로 그 사실을 증명한다.

## bite 8/8

| # | 주입 | 기대 | 결과 |
|---|---|---|---|
| ⓪ | 뒤집기 **없음**(저장소 기본) | PASS · 면제 **0건** | ✅ |
| ① | 뒤집기 `https` | PASS · (w) 면제 **1** / (z12) **43** | ✅ |
| ② | `http://auth.hubwang.com` (스킴만 다름) | (w) FAIL | ✅ |
| ③ | `INTERNAL_JWT_JWK_SET_URI` 를 다른 공개 호스트로 | (w) FAIL | ✅ |
| ④ | `auth.hubwang.com.attacker.tld` (접미사 유사) | (w) FAIL | ✅ |
| ⑤ | issuer 하나만 틀림 | (w) 통과 · **(z12)** FAIL | ✅ |
| ⑥ | `IAM_JWKS_URL` 까지 공개 IdP 로 → 전부 면제 | (w) **삼킴 바닥** FAIL | ✅ |
| ⑦ | 복원 후 재대조군 | PASS | ✅ |

🔵 ⑤가 **(w) 는 통과하고 (z12) 가 문다**는 것이 두 칸이 서로 다른 축임을 보인다 —
하나로 합쳤으면 못 봤을 구별이다.

## 🔴 이번에도 내 검사 술어가 먼저 틀렸다

첫 bite 실행에서 ②③④⑤가 `WRONG-CELL` 로 나왔다. 가드는 옳게 물었고, **내 판정기가
「어느 칸이 물었나」를 못 읽은 것**이었다 — `(w)`·`(z12)` 의 옛 FAIL 메시지에 칸 태그가
없었기 때문이다(새로 넣은 삼킴-바닥 메시지만 태그가 있어 그것만 PASS 로 잡혔다).
⇒ **두 메시지에 `(w)`/`(z12)` 접두사를 붙였다.** 사람이 CI 로그를 읽을 때도 같은 비용을
내고 있었다. 소비자 grep 0건을 확인하고 바꿨다.
[[feedback_my_verification_predicate_is_the_likeliest_defect]]

## 🔴 그리고 내가 만든 측정 오류 하나 — 기록해 둔다

bite 가 `demo.env` 를 갈아 끼우는 동안 **전체 래퍼 실행이 같은 파일을 다시 source** 하고
있었다(그 실행은 `rc=0` 을 냈다). 겹쳐 돌린 것이 원인이고, **그 결과는 폐기**한 뒤
bite 종료 후 깨끗하게 다시 돌렸다. 하네스가 49초·전체가 7분이라 겹치고 싶어지는데,
**공유 파일을 만지는 두 측정은 겹치면 안 된다.**

## C3 — 뒤집기를 저장소 판본으로

`demo.env` 의 `IAM_PUBLIC_URL` 을 `https://auth.hubwang.com` 으로 랜딩했다. 🔴 값만
바꾸면 **바로 위 주석이 값을 반박한다** — 그 주석은 「왜 도메인 파생이어야 하는가」를
설명하고 있었다. 주석도 함께 고쳐 C2 의 근거로 바꿨다.

🔴 **이 PR 이 닫지 않는 것**: 호스트는 이미 로컬로 뒤집혀 있으므로(기동 창에서 소유자
승인) 이 변경의 발효 확인은 **재굽기 이후**다. C3 판정(「새 AMI 의 demo.env 가 저장소
판본이고 호스트 로컬 수정이 0건」)은 그때 잰다.

---

# 🟡 B4 착수 (2026-09-03 UTC) — **경합은 안 골랐다. 매달렸을 때 진단이 남게 했다**

## 🔴 이 항목은 닫히지 않았다

B4 의 후보 ⓐ~ⓓ 는 이 티켓이 **「고르지 않았다 — 실측 후 결정」** 으로 남긴 것이고,
그 판정 기준은 **부팅 2회 연속**이다. 데모가 꺼져 있으므로 여기서 고르지 않았고,
**AC 는 열린 채로 둔다.** 아래는 다른 축이다.

## 티켓의 산술과 관측이 모순된다

`demo-up.sh` 의 AC-0 주석은 대기의 상한을 이렇게 적었다:

> 대기는 전부 `depends_on: condition: service_healthy` 가 하고, 그 한도는 **의존 대상
> 자신의 healthcheck** 다. iam-kafka 기준: interval 15s · timeout 10s · retries 10 ·
> start_period 30s

그 산술의 최악값은 **약 5분**이다. 그런데 V5 에서 관측된 것은:

```
14:24:01  Container iam-kafka Waiting / iam-redis Waiting / iam-mysql Waiting
14:41:03  demo-stack.service: start operation timed out → failed
```

**17분**이고, 그 사이 다음 도메인의 `[demo] up:` 줄이 **하나도 없다**.
⇒ **「대기는 healthcheck 가 묶어 준다」는 문장은 실측이 반증했다.** 왜 안 묶였는지는
아직 모른다 — 그것이 B4 본체이고 기동 창의 몫이다.

## 그 매달림이 왜 「진단 없음」으로 끝나는가

`demo-up.sh` 는 재시도 · 예산 배분 · 수렴 재측정 · 요약 · 상태 발행을 아주 공들여
설계해 뒀다. 그런데 **그 전부가 「`up -d` 호출이 돌아온다」를 전제**한다. 호출 하나가
매달리면 그중 **아무것도 실행되지 않고** systemd 가 SIGTERM 을 보낸다. 운영자에게 남는
것은 유닛의 `failed` 한 줄이고, 상태 발행이 없으니 방문자 화면도 침묵한다.

**V5 의 두 번째 부팅이 아무 진단도 못 남긴 이유가 이것이다.**

## 고친 것 — 호출당 상한 + 전역 마감

- `UP_CALL_TIMEOUT`(기본 420s) · `UP_TOTAL_BUDGET`(기본 1020s). 🔴 **둘 다 필요하다**:
  호출당만 두면 8도메인 × 상한이 `TimeoutStartSec=1200` 을 넘고, 전역만 두면 첫 도메인이
  전부를 먹는다.
- 종료코드 **124 를 「떠서 실패」와 구별**한다. 두 사유는 진단이 다르다 — 전자는 의존이
  `unhealthy` 로 끝난 것이고, 후자는 **그 healthcheck 가 대기를 묶지 못했다**는 뜻이다.
- 🔴 매달림은 **재측정 결과와 무관하게** 최종 요약에 남긴다. 끊은 뒤 재시도가 성공하면
  「늦게 수렴」 초록이 되고, 그러면 「한 번 매달렸다」가 사라진다 — 그게 B4 가 찾는
  유일한 신호다. (`TASK-MONO-559` 에서 이미 당한 모양: A 의 고침이 B 의 유일한 증상을
  지운다.) [[feedback_a_warning_can_be_a_proxy_for_a_bigger_problem]]
- `timeout` 이 없으면 **오늘과 같은 동작**으로 내려가되 소리를 낸다. 도구 하나 때문에
  부팅 전체를 죽이는 것은 오늘보다 나쁘다. 대신 가드가 「묶는가」를 따로 단언한다.

🔵 **이것은 경합의 고침이 아니다.** ⓐ~ⓓ 를 앞지르지 않는다. 바꾼 것은 **매달렸을 때
무슨 일이 일어나는가**이고, 목적은 **다음 창에서 B4 를 잴 수 있게 만드는 것**이다.

## 가드 (z13) 칸 (7) — 데모 없이 증명한다

기존 `(z13)` 하네스에 **매달림 대역**을 더했다(stub `docker` 가 `up` 호출에서만 잔다).
네 가지를 묻는다: ① 끊는가 ② 「기동 실패」와 **다른 말**로 남는가 ③ 뒤 도메인이 계속
시도되는가 ④ **초록인 채로도** 신호가 남는가.

실측: `대조군(빨리 실패) 6s → 매달림 9s (차 3s)` — 대역은 `sleep 30s × 시도 2회`다.
묶였으면 3s, 안 묶였으면 60s 이상이므로 분리가 명확하다.
그리고 칸 (6)에 **전역 예산 < TimeoutStartSec** 단언을 추가했다(1020s < 1200s) —
이 부등식이 깨지면 「묶었다」가 거짓이 된다: 스크립트가 자기 마감에 닿기 전에 systemd 가
먼저 SIGTERM 을 보낸다.

## 🔴 이 칸을 만들면서 내 판정 술어가 **세 번** 틀렸다

이 세션에서 반복된 패턴이라 적어 둔다 — **대상보다 내 판정기가 먼저 틀린다.**

1. **총 실행시간을 「끊었는가」의 대리지표로 썼다.** 40s 를 보고 「안 묶였다」로 읽었는데,
   실제로는 2s 에 두 번 다 끊고 있었다. `demo-up.sh` 는 up 루프 말고도 신선도·preflight·
   재측정·표면검사를 한다. [[feedback_measurement_needs_a_validity_predicate]]
2. **대조군을 「실패가 없는 판」으로 잡았다.** 실패가 없으면 수렴 재측정·표면 검사 경로
   자체를 안 타므로, 두 판의 차이가 「매달림」이 아니라 **「실패 경로의 유무」**가 된다.
   ⇒ 대조군을 **「같은 도메인이 빨리 실패하는 판」**으로 바꿨다.
   [[feedback_control_group_design_four_axes]]
3. **대역이 시험 대상 밖에서도 잤다.** stub 이 `compose -p iam` 이면 무조건 매달려
   신선도 검사의 `config` 호출까지 30s 를 먹었다. 차이 34s 는 매달림이 아니라 **대역의
   범위**였다. ⇒ 대역을 **`up` 호출에만** 걸도록 좁혔다.
   [[feedback_assert_the_injection_before_reading_the_bite]]

🔵 세 번 모두 **가드는 옳게 물었고 내가 이유를 잘못 읽었다.** 매번 「추론」이 아니라
**직접 측정**(고아 프로세스 실험 · `SURFACE_SLEEP` grep · 로그 덤프)으로 갈랐다.

## 🔴 남은 것 (이 항목이 닫으려면)

- **부팅 2회 연속**으로 경합을 재고, 그때 ⓐ~ⓓ 중 하나를 고른다.
- 그 창에서 **이 변경이 실제로 무엇을 남기는지** 본다: 매달림이 다시 나면 이번엔
  `⏱ 매달림:` 줄과 요약이 남아야 한다. **그것이 이 변경의 유일한 성공 기준**이고,
  「매달림이 사라졌다」가 아니다(이 변경은 경합을 고치지 않는다).

---

# 🟢 기동 창 (2026-09-03 UTC) — A1 이 닫혔고, B4 의 「경합」은 **결정론**이었다

인스턴스 `i-0c4721bdeb335885a` / AMI `ami-0caf015f7cd9144fd`(6차, 08-29).
이 창에서 **부팅 4회**를 썼다 — 2회는 손대지 않은 상태(B4 재현), 2회는 후보 ⓐ 시험.
호스트 저장소는 `a544c49`(#3596)로, **B1–B4 랜딩분이 아직 없는 판본**이다. 그래서 이 창의
A1·B4 측정은 전부 「고치기 전」 상태에서 나왔다.

## AC-0 재측정 — 추론이 아니라 실제로 다시 쟀다

| # | AC-0 항목 | 실측 |
|---|---|---|
| ① | `flyway_schema_history` 최대가 **`V0034` 미만**인가 | ✅ **참.** `auth_db` 36행 · 최대 `0033` · `installed_on=2026-08-29 15:54:04`. 🔵 B1 세션이 *"추론이지 측정이 아니다"* 라고 적어 둔 그 칸을 이 창에서 실제로 닫았다 — 그리고 추론이 맞았다 |
| ② | B1–B4 가 아직 참인가 | B1 은 main 에 랜딩됐고 **호스트에는 아직 없다**(호스트 `a544c49`). B2·C3·B4 도 같다. B4 는 아래에서 **재현했다** |
| ③ | 항목 간 상충 | A1 → 재굽기 순서 제약 **그대로 유효**. 새로 발견한 항목(아래 B4-ii)은 재굽기 전 main 랜딩이 필요하므로 항목 B 와 같은 축이다 |

🔴 다른 세 스키마도 함께 쟀다(같은 볼륨): `account_db` 34행/`0028` · `admin_db` 46행/`0045`
· `security_db` 11행/`0011`. 전부 `2026-08-29 15:5x` 각인이다 — 이 볼륨이 **08-29 이후
아무 마이그레이션도 받지 않았다**는 뜻이고, 그것이 A1 의 전제다.

---

## ✅ A1 / V8 — **기존 볼륨 위에서** 마이그레이션이 들어간다 (AC-1 PASS)

### 무엇을 했나

| 단계 | 실측 |
|---|---|
| 롤백 확보 | `docker tag iam-auth-service iam-auth-service:pre-615-a1` → `31c759347491` |
| 이미지 안의 마이그레이션 (전) | 마지막이 `V0033__add_fan_vercel_domain_redirect_uri.sql` · 이미지 생성 `2026-08-29T14:37:51Z` |
| 호스트 소스 | `src/main/resources/db/migration/` 에는 `V0034`·`V0035` 가 **있다**. `build/resources/main/` 에는 **없었다** ⇒ 저장소는 앞서 있고 **산출물이 뒤처져 있었다** |
| 재빌드 | `./gradlew :projects:iam-platform:apps:auth-service:bootJar` rc=0 → `auth-service.jar` 98,884,285B (02:45) |
| 이미지 재굽기 | `docker compose -p iam … build auth-service` rc=0 → `sha256:ef3708f2…` created `2026-09-03T02:46:53Z` |
| 재기동 | `docker compose -p iam … up -d` rc=0 (02:47:55) · auth-service **healthy 02:48:41** |

### 판정 — 파일이 아니라 **행**을 봤다

```
전: 36행 · MAX(version)=0033 · 2026-08-29 15:54:04
후: 38행 · MAX(version)=0035 · 2026-09-03 02:48:19
    rank 37 = 0034 add console vercel domain redirect uri   success=1
    rank 38 = 0035 add store vercel domain redirect uri     success=1
    success=0 인 행 0건 · rank 1..38 연속
```

Flyway 자신의 말(auth-service 로그):

```
Successfully validated 38 migrations (execution time 00:00.200s)
Current version of schema `auth_db`: 0033
outOfOrder mode is active. Migration of schema `auth_db` may not be reproducible.   ← WARN
Migrating schema `auth_db` to version "0034 - add console vercel domain redirect uri"
Migrating schema `auth_db` to version "0035 - add store vercel domain redirect uri"
Successfully applied 2 migrations to schema `auth_db`, now at version v0035
```

🔵 `outOfOrder` 경고는 **떴지만 아무것도 실패시키지 않았다.** 이 볼륨에는 rank 34–36 에
버전 없는 반복 마이그레이션(`R__`)이 이미 있고, 그 위에 `0034`·`0035` 가 얹혔다. 신선
볼륨에서는 이 배치 자체가 생기지 않으므로 **CI 는 이 조합을 영원히 못 본다** — 그것이
V8 이 물으려던 것이고, 답은 **결함 없음**이다.

### 술어는 부분문자열이 아니라 JSON 이다

🔴 `LIKE '%console.hubwang.com%'` 은 주석에만 그 문자열이 있어도 참이고 배열이 깨져도
참이다. `JSON_VALID` + `JSON_CONTAINS(JSON_QUOTE(…))` 로 쟀다:

| client_id | json_valid | has_console | has_store | n |
|---|---|---|---|---|
| `platform-console-web` | 1 | **1** | 0 | 4 |
| `ecommerce-web-store-client` | 1 | 0 | **1** | 5 |

🔵 **음성 대조군이 같은 표 안에 있다** — console 의 URI 가 store 클라이언트에 **없고** 그
반대도 그렇다. 한 클라이언트만 봤으면 「부분문자열이 우연히 맞았다」와 구별할 수 없었다.

### 🔵 이 한 번의 재빌드가 함께 푼 것

- **`TASK-MONO-612` AC-0 ②** — `store.hubwang.com` 콜백이 이제 **행에 있다**.
- **store / console 로그인의 선행** — 두 redirect_uri 가 등록됐다.
  🔴 다만 **로그인 자체는 아직 안 봤다**. 등록은 필요조건이지 충분조건이 아니다.

### 🔴 롤백은 쓰지 않았다

`iam-auth-service:pre-615-a1` 태그는 남겨 뒀다. IdP 는 **약 46초** 내려갔다
(02:47:55 up → 02:48:41 healthy).

---

## ✅ B4 — 후보 **ⓐ** 를 골랐다. 그리고 「경합」이라는 말이 실측과 안 맞았다

### 🔴🔴 먼저 — 티켓의 「1회 성공 / 1회 실패 ⇒ 결정론이 아니라 경합」이 좁았다

손대지 않은 판으로 **부팅 2회를 연속** 재현했다(둘 다 stop/start — 🔴 reboot 는 공인 IP 가
유지돼 `DEMO_DOMAIN` 이 안 바뀌고, 그러면 **recreate 자체가 안 일어나** 다른 것을 잰다):

| 부팅 | iam 결과 | 남은 `Created` | 유닛 |
|---|---|---|---|
| #1 (02:23:04) | 98s 뒤 `dependency failed to start: iam-kafka is unhealthy` → 재시도 1회 → 포기 | 3 | **timeout** 02:43:04 |
| #2 (02:50:32) | 210s 뒤 같은 실패 → 재시도 1회 → 포기 | 3 | **timeout** 03:10:32 |

남은 셋은 두 번 다 **똑같다**: `iam-auth-service-1` · `iam-gateway-service-1` · `iam-kafka-ui`
— `TASK-MONO-610` V5 의 지문 그대로다. ⇒ **경합은 맞지만 오늘의 부하에서는 확실하게 진다.**
610 의 1회 성공이 예외였지 실패가 예외가 아니다. [[feedback_local_proves_behaviour_not_performance]]

### 🔴🔴 기전은 「kafka 가 안 떴다」가 아니라 **「프로브가 못 끝났다」**였다

부팅 시 샘플러(5초 간격)를 심어 잡았다:

```
03:14:43 load=23.73  kafka=starting  run=97 created=3   hc=[… 0:…  137:03:13:23]
03:15:02 load=85.98  kafka=starting  run=95 created=3   hc=[… -1:03:14:28  -1:03:14:44]
03:15:25 load=126.35 kafka=starting  run=93 created=3
03:15:42 load=162.74 kafka=starting  run=88 created=3   hc=[… -1:03:15:13]
```

`iam-kafka` healthcheck = `kafka-broker-api-versions.sh` — **JVM 기동**이다.
평시 소요 **1.48 / 1.99 / 2.14 / 3.31s**. 부팅 폭풍에서는:

```
exit=137  03:13:20 → 03:13:23     ← 137 = 128+9 = SIGKILL. 도커가 timeout:10s 로 죽였다
exit=-1   03:14:17 → 03:14:28     10.7s
exit=-1   03:14:33 → 03:14:44     11.6s
exit=-1   03:14:59 → 03:15:13     13.2s
exit=-1   03:15:28 → 03:15:39     11.0s
```

🔵 **같은 시각 브로커 로그는 정상이었다** — 컨슈머 그룹 `security-service` 7 멤버 리밸런스를
02:27~02:28 에 처리하고 있었다. **실패한 것은 브로커가 아니라 프로브다.**
🔵 자원 고갈도 아니다(티켓의 관찰과 일치): 메모리·디스크 여유. 고갈된 것은 **CPU 스케줄링**이다.

⇒ 후보 **ⓑ(의존 대기 타임아웃/조건 조정)는 레버가 어긋나 있다.** 넓혀야 할 것은 compose 의
대기가 아니라 **프로브가 끝날 여유**이고, 그것을 만드는 가장 싼 방법은 폭풍을 없애는 것이다.

### 판정 — ⓐ, 부팅 2회 연속

`demo-boot.sh` 가 up **앞에** `demo-down.sh` 를 돌린다(🔴 `-v` 없음 = 볼륨 보존. A1 의 행이
살아 있어야 한다).

| 부팅 | 판 | iam | `Created` | 시드 실패 | 유닛 |
|---|---|---|---|---|---|
| #3 (03:14:19) | ⓐ | `up: iam` 03:17:28 → `up: wms` 03:17:38 = **10s · 재시도 0** | **0** | 6도메인 전부 **0** | `exit-code`(정상 종료) |
| #4 (03:31:38) | ⓐ | 03:34:22 → 03:34:32 = **10s · 재시도 0** | **0** | 6도메인 전부 **0** | `exit-code`(정상 종료) |

두 판 모두 `기동 실패` 0건 · `⚠ 재시도 배분` 0건 · `✔ HTTP 표면 2/2`.
남은 rc=1 의 **유일한** 사유는 `[drift] ✖ ecommerce-web-store → 43-202-166-3.sslip.io` — 즉 **C1**이다.

🔵 왜 ⓒ·ⓓ 가 아닌가: 둘 다 8개 프로젝트의 compose 를 건드려 **로컬과 CI 의 기동 의미까지**
바꾼다. 여기서 필요한 것은 「데모 호스트의 부팅」이라는 한 상황이고 ⓐ 는 그 한 자리에만 산다.
🔴 ⓐ 가 공짜는 아니다 — down 단계가 실측 **160~184s** 를 먹는다. 그것이 아래 항목을 낳았다.

### 🔴 `DEMO_BOOT_RESET` 게이트 — 이것이 없으면 ⓐ 는 사고다

`demo-boot.sh` 는 부팅 말고 **컨트롤 플레인**도 부른다(`handler.py` `domain_start` →
`demo-boot.sh <name>`), 그리고 그 화이트리스트 `START_NAMES` 에는 **`full`·`demo-core` 가
들어 있다**. 무조건 down 하면 방문자가 「전부 켜기」를 누른 순간 **떠 있는 데모를 통째로
내렸다 올린다.** 인자로는 부팅인지 알 수 없다 — 아는 것은 **호출자**이고 그것을 아는 것은
systemd 유닛뿐이다. 그래서 플래그가 유닛에서 온다. 가드 **(z24)**가 그 쌍을 묶는다
(유닛이 준다 · 스크립트가 그때만 내린다 · `handler.py` 는 언급하지 않는다 + 행동 bite 3칸).

---

## 🔴🔴 #3601 의 성공 기준을 잰 결과 — **「남지 않았다」, 그런데 이유가 다르다**

이 창의 질문은 *"다시 매달리면 `⏱ 매달림:` 줄과 요약이 남는가"* 였다. 답을 둘로 갈라야 한다.

**① 매달림은 재현되지 않았다.** 부팅 #1·#2 의 `up -d` 는 **매달리지 않았다** — 98s / 210s 만에
`dependency failed to start` 로 **돌아왔다**. 그래서 `⏱` 대역 자체가 발화하지 않았고,
#3601 의 호출당 상한은 이 창에서 **시험되지 않았다.** (「매달림이 사라졌다」로 읽으면 안 된다.
다른 사유로 실패한 것이다.)

**② 그런데 요약은 — 매달렸더라도 — 나올 수 없는 자리에 있었다.** 이쪽이 본론이다.

```
boot #1  up 루프 종료 02:27:57 → [seed] 13분 → 02:43:04 SIGTERM (`[seed] --- fan ---` 중)
boot #2  up 루프 종료 02:56:29 → [seed] 14분 → 03:10:32 SIGTERM (`[seed] --- erp ---` 중)
실측 카운트(두 판): '늦게 수렴' 0 · 'HTTP 표면' 0 · '재시도 배분' 0 · '매달림' 0
```

`demo-up.sh` 의 최종 요약 블록은 **시드 뒤**에 있다(342행 `seed/seed.sh` → 511행부터 요약).
iam 이 안 뜬 판에서는 각 도메인 시드가 게이트웨이를 **240s 씩** 기다리므로 그 한 단계가
systemd 예산을 통째로 먹고, 요약은 **한 줄도 실행되지 않는다.**

🔴 그리고 그때도 (z13) 칸 (6)의 `UP_TOTAL_BUDGET(1020) < TimeoutStartSec(1200)` 은 **참이었다.**
⇒ **잰 값이 아니라 «센 항»이 틀렸다.** up 루프만 묶고 그 뒤를 무한히 두면, 앞의 보증은
뒤가 통째로 먹는다. 「전역 예산이 상한 아래」는 필요조건이지 충분조건이 아니다.

🔵 **#3601 이 만든 결함이 아니다.** 그 PR 의 문장(「매달림은 재측정과 무관하게 요약에 남긴다」)은
옳다. 틀린 것은 **「요약은 언제나 실행된다」는 암묵 전제**다. 같은 전제가 이미 한 번 깨진
적도 있다 — `TASK-MONO-552` AC-3 이 *"시드의 rc 를 안 받아 스크립트가 그 자리서 죽고 최종
블록이 통째로 날아간다"* 를 고쳤다. 그때 닫은 것은 **rc 경로**였고, 이번에 깨진 것은
**시간 경로**다. **같은 자리, 다른 문.**
[[feedback_two_correct_exclusions_compose_into_a_hole]] [[feedback_why_a_guard_does_not_bite]]

### 고친 것 — ⓐ 가 그 산술을 더 나쁘게 만들기 때문에 **같은 PR 에서 갚는다**

ⓐ 는 앞에 down 단계(160~184s)를 더한다. `TimeoutStartSec` 을 그대로 두면 ⓐ 는 경합을
고치면서 **요약을 다시 잃게 만든다** — 「한 항목의 수단이 다른 항목의 전제를 지운다」는,
이 티켓이 존재하는 바로 그 모양이다(AC-0 ③ 이 물어보라고 한 축).

- `demo-up.sh`: `POST_UP_BUDGET`(240) · `SUMMARY_RESERVE`(120) 선언, 시드·드리프트를
  `post_up_call` 로 묶고 **rc 124 를 「시드 실패」와 구별**해 `⏱ 시드 단계 마감:` 으로 남긴다.
- `demo-boot.sh`: `DEMO_DOWN_BUDGET`(300) — down 도 매달릴 수 있다. up 을 묶어 놓고 down 을
  안 묶으면 같은 결함이 한 칸 앞으로 옮겨간 것뿐이다.
- `demo-stack.service`: `TimeoutStartSec` **1200 → 1800**. 🔴 완화가 아니라 **재산정**이다 —
  1200 은 「부팅 = up + 시드」인 세계의 값이었고, 단계가 하나 늘었다.
- 가드 **(z13) 칸 (8)**: 주석이 아니라 **합**을 센다.
  `정리 300 + up 1020 + 시드 240 + 예비 120 = 1680 ≤ 1800`, 그리고 예비 하한 60s
  (예비가 0이면 부등식은 통과하면서 요약은 또 못 나온다).
  실측 대조: boot #3 **777s** · boot #4 **725s**.

---

## 🔴🔴 측정을 막고 있던 것 — 가드가 **없는 죄**를 고발하고 있었다 (main 의 결함)

`verify --live` 를 돌리자 **(k)** 에서 죽었다:

```
[verify] (k) 마이그레이션에 박힌 .local 콜백을 데모 시드가 전부 덮는가
  FAIL: demo-up.sh 가 seed-demo-domain.sh 를 호출하지 않습니다 — 시드가 실행되지 않으면 로그인은 401 입니다.
```

**호출은 `demo-up.sh` 334행에 멀쩡히 있었다.** 그리고 이 FAIL 은 실행을 끊어
뒤의 `--live` 칸 — 이 창이 재려던 **(z18)·(z21)** — 을 **미도달**로 남겼다.
🔴 B3 절이 *"(w) 의 FAIL 이 (z18)을 가린다"* 라고 적은 그 모양이고, B3 절 자신이
*"(w) 가 먼저 죽어 (z12)를 가렸다"* 를 또 적었다. **세 번째다.**

### 기전 — `A | grep -q PAT` 는 pipefail 아래에서 **매치했는데 실패**한다

`grep -q` 는 첫 매치에서 즉시 끝나며 읽는 쪽 파이프를 닫는다. 앞단(`sed`/`printf`)이
아직 쓸 것이 남아 있으면 그 write 가 EPIPE/SIGPIPE 로 죽어 **141** 을 내고,
이 파일의 `set -o pipefail` 이 파이프라인의 결과를 141 로 만든다.

🔴 **내 첫 확인 술어부터 틀렸다.** 호스트에서 `sed … | grep -n …` 를 돌려 「통과한다」를
보고 «전이 오류인가» 로 갔다. `-n` 은 입력을 끝까지 읽으므로 **SIGPIPE 가 안 난다** —
나는 문제의 술어가 아니라 **다른 술어**를 재고 있었다.
[[feedback_my_verification_predicate_is_the_likeliest_defect]]

진짜 술어를 그대로 반복한 실측(데모 호스트, GNU grep/sed):

| 모양 | 입력 | 실패 |
|---|---|---|
| `sed 578줄 \| grep -q` (매치 334행) | demo-up.sh | **6 / 40** (rc=141) |
| `printf 25,476B \| grep -q` (앞쪽 매치) | 문자열 | **295 / 300** |
| 같은 자리에서 `-q` 만 뺀다 | 같음 | **0 / 60** |

🔵 왜 지금까지 안 터졌나 — 파이프로 넘기는 값이 대개 작아서 한 번의 `write()` 가
파이프 버퍼에 다 들어가고 grep 이 끝나기 전에 앞단이 이미 종료했다. (k) 는 `sed` 가
**줄 단위로 흘리기 때문에** 항상 쓸 것이 남아 있고, 그래서 이 칸이 먼저 발화했다.
🔴 그러나 「지금까지 안 터졌다」는 안전의 근거가 아니다 — 25KB 실측이 **98%** 다.

### 고친 것

- `grepq() { grep "$@" >/dev/null; }` — 파이프 뒤 **54곳**을 이것으로 바꿨다.
  종료코드 의미는 같고(매치 0 → 1) 입력을 끝까지 읽는다.
- 가드 **(z25)**: 술어를 「(k) 가 통과하는가」로 쓰지 않는다 — 그건 한 증상일 뿐이다.
  **모양**을 문다(파이프 뒤 `grep -q` 0건) + 헬퍼 본문이 실제로 `-q` 를 안 쓰는지
  (이름만 바뀌는 것을 막는다) + **200KB 대역의 양성 대조군**(`grep -q` 는 죽고
  `grepq` 는 안 죽는다 — 대조군이 없으면 이 칸은 「환경이 관대해서」 초록일 수 있고
  그러면 금지가 근거를 잃는다).
  🔴 (1)의 술어는 **주석을 먼저 걷어낸다** — 이 파일의 상단 주석이 설명을 위해 그
  모양을 일부러 적고 있다. [[feedback_a_discriminator_can_match_its_own_documentation]]

### 🔴 그리고 그 다음 실행에서 **(z2) 가 내 변경을 물었다** — 옳게

(z24) 가 `command -v timeout` 을 정적 구간에 들여오자 (z2) 가 즉시:
*"packer 1단계가 timeout 패키지를 설치하지 않습니다"*. **문 것 자체는 옳다** — 정적
구간의 새 도구 요구를 자동으로 범위에 넣는 것이 (z2) 의 설계다. 틀린 것은 **분류**이고
(`timeout` 은 coreutils = essential, apt 목록에 없는 것이 정상), 그래서 도구→패키지
매핑에 **세 번째 부류(base 제공)** 를 뒀다. 🔵 면제를 「없어도 된다」로 두지 않고
**근거 자체를 단언**한다(`command -v timeout`) — 이 스크립트는 packer 7단계에서 AMI
안에서도 도므로, 그 단언은 정확히 AMI 안의 실재를 잰다.
[[feedback_retract_the_exemption_when_the_defect_is_fixed]]

---

## ✅ 라이브 칸 — `TASK-MONO-606` AC-4′ ②((z18)) 와 (z21) 첫 확인

가드 결함을 고친 뒤 `--live` 가 그 자리까지 도달했다(호스트 `DEMO_DOMAIN=15-164-181-233.sslip.io`).

| 칸 | 결과 |
|---|---|
| **(z18)** `--live` 죽은 sslip OAuth 콜백 | ✅ **0건 — 판정한 sslip URI 10건** ⇒ `TASK-MONO-606` **AC-4′ ② 닫힘** |
| **(z21)** `--live` discovery ↔ 핀 ↔ 라우터 | ✅ **일치 — 광고 접두사 2건(`/connect` `/oauth2`)** ⇒ B1 의 「라이브에서 도는 것은 아직 못 봤다」가 **닫혔다** |
| (z20) 정적 라우터 ⊇ 핀 | ✅ 핀 2건 ⊆ 라우터 5건 · `/.well-known` 별도 확인 |
| (z24) 부팅 리셋 게이트 | ✅ 플래그 有 down→up 순서 · 플래그 無 down **0회** · 매달린 정리 **2s** 만에 끊고 말한다 |
| (z25) 파이프 뒤 `grep -q` | ✅ 0건 · 200KB 대역에서 `grep -q` **10/10 실패** ↔ `grepq` **0/10** |
| (z13) 단계 예산 **합** | ✅ `정리300 + up1020 + 시드240 + 예비120 = 1680s ≤ 1800s` |
| (w) JWKS 호스트 해소 · (z12) issuer 파생 | ✅ (B3 의 좁은 면제가 뒤집힌 상태에서 실제로 통과) |

### 🔴 핀의 완전성 — B1 이 주장하지 **않았던** 것이 이제 측정됐다

B1 세션은 *"그 세션은 문서 1,587B 중 앞 약 900B 만 읽었으므로 목록은 「관측된 접두사
전부」이지 「문서가 광고하는 전부」가 아니다"* 라고 적고, 완전성을 (z21)에 맡겼다.
이번에 **문서 전체**를 받아 확인했다 — 경로를 가진 엔드포인트는 8개이고
(`authorize` `device_authorization` `token` `jwks` `userinfo` `revoke` `introspect` → `/oauth2`,
`logout` → `/connect`), 접두사 집합은 **정확히 `{/oauth2, /connect}`** 다. **핀은 완전하다.**

### 🔴 B1 의 라이브 증명 — 404 → **401**

```
http://iam.<domain>/connect/logout            → 401   (이전: 404)
http://iam.<domain>/oauth2/jwks               → 200
http://iam.<domain>/.well-known/openid-configuration → 200
```
401 = 「엔드포인트는 있고 인증이 없다」 — 티켓이 *"컨테이너 직격은 401"* 이라고 적은 그 값이
이제 **바깥에서** 나온다. 그리고 discovery 의 `issuer` 는 `https://auth.hubwang.com` 이다(C3 발효).

### 🔴 그러나 `--live` 를 **끝까지** 돌린 것은 아니다 — 이름을 적어 둔다

(z21) 다음 칸 **(f) `--live`** 가 죽었다:

```
Container scm-platform-redis Creating
Error response from daemon: Conflict. The container name "/scm-platform-redis" is already in use
```

(f) 는 `-p verify-live-scm` 으로 scm 의 redis 를 따로 띄워 「같은 서비스 키가 별도 -p 로
공존하는가」를 본다. 그런데 그 compose 는 `container_name: scm-platform-redis` 를 **고정**하고
있고 그 이름은 떠 있는 데모의 scm 스택이 이미 갖고 있다 ⇒ **(f) 는 데모가 떠 있는 호스트에서
구조적으로 못 돈다.**

🔵 내 변경과 무관하다(compose 도 (f) 도 안 건드렸다). 🔴 그러나 *"`--live` 는 살아 있는
데모를 잰다"* 는 이름과 어긋나므로 **미측정 칸으로 이름을 적는다** — 이 창의 `--live` 판정은
**(f) 앞까지**다. 침묵하면 다음 사람은 rc=1 을 보고 내 변경을 의심한다.
[[feedback_a_census_measures_where_you_looked_not_what_exists]]

---

## 🟡 항목 C — 재굽기 전 **기준선을 실측으로 박아 뒀다** (판정은 재굽기 뒤)

재굽기가 인스턴스를 교체하면 이 값들은 사라진다. 「전」을 안 재고 「후」만 재면 그 판정은
자기 가설을 증명하는 모양이 된다.

| # | 재굽기 **전** 실측 (2026-09-03, `i-0c4721bdeb335885a` / `ami-0caf015f7cd9144fd`) |
|---|---|
| **C1** | `ecommerce-web-store` **존재하고 running** · 생성 `2026-09-02 13:04:34` · `project=ecommerce` · 라우터 라벨 `Host(web.ecommerce.43-202-166-3.sslip.io)` |
| **C2** | `infra/demo/{auth-forwarder,backend-resolver}` 가 호스트 저장소에 **있다** — 🔵 다만 이제 그것은 **main 판본**이다(호스트를 main 으로 리셋했다). AMI 가 갖는지는 별개 축 |
| **C3** | `git status` **clean** — 🔴 그러나 이것은 *내가* 리셋한 결과지 AMI 의 성질이 아니다. 진짜 판정은 새 AMI 의 첫 부팅에서 |
| **C4** | 호스트 `projects/fan-platform/.env` 에 **재선언이 그대로**: 25행 `fan-platform-dev` / **73행 `replace-with-secret-from-iam-seed`**(dotenv 는 이쪽이 이긴다) · mtime **2026-08-29 15:51:45** = 이 AMI 의 첫 부팅 이후 **한 번도 안 바뀌었다** |

### 🔴🔴 C1 이 티켓보다 강해졌다 — `--remove-orphans` **로도** 안 지워진다

티켓은 *"억제 선언보다 오래된 컨테이너는 `profiles:` 게이트 밖이고 `--remove-orphans` 가
안 지운다"* 라고 적었다. 이번 창에서 그것을 **실행으로** 확인했다: 후보 ⓐ 가 부팅마다
`demo-down.sh`(= `down --remove-orphans`)를 **전 도메인에** 돌렸는데도

```
부팅 #3 후: [drift] ✖  ecommerce-web-store (-p ecommerce) → 43-202-166-3.sslip.io
부팅 #4 후: [drift] ✖  ecommerce-web-store (-p ecommerce) → 43-202-166-3.sslip.io
```

**두 번 다 살아남았다.** 기전: compose 는 `web-store` 를 *모르는* 서비스가 아니라
**프로파일이 꺼진 아는 서비스**로 본다 ⇒ orphan 이 아니라서 `--remove-orphans` 대상이
아니고, 프로파일이 꺼져 있어서 `down` 대상도 아니다. **두 기전 어느 쪽에도 안 걸린다.**
⇒ C1 의 처방(「그 컨테이너가 **애초에 없는** AMI」)이 옳다는 것이 더 강하게 확인됐다.

🔵 그리고 이것이 부팅 #3·#4 에서 유닛이 `rc=1` 로 끝난 **유일한** 사유다. C1 이 닫히면
ⓐ 판의 부팅은 완전히 초록이 된다.

### C4 는 B2 가 예고한 그대로다

B2 § 남은 공백 1 이 *"`provision-demo-env.sh` 는 멱등이라 `.env` 가 있으면 건드리지 않고,
호출자는 `demo-boot.sh` 하나뿐(부팅 시점이지 굽는 시점이 아니다)"* 이라고 적었다.
부팅 로그가 그것을 그대로 말한다: `[provision-env] 요약 — 생성 0 · 기존유지 8`.
mtime 이 08-29 인 것이 **네 번의 부팅을 건너 안 바뀌었다**는 직접 증거다.
[[feedback_declaration_files_are_not_the_runtime_state]]

---

## 🔴🔴 재굽기가 **실패했다** — 그리고 C2 는 티켓이 적은 것보다 크다

7차 굽기(`main` = `6ad63cb09`)는 10분 53초에 죽었다. **내 변경 때문이 아니다.**

```
#78 [fan-platform-web builder 8/9] RUN pnpm --filter fan-platform-web build
#78 ./src/shared/config/demo-backend.ts
#78 Module not found: Can't resolve '@demo/backend-resolver'
#78 Import trace: ./src/widgets/demo-notice/DemoBackendNotice.tsx → ./src/app/(main)/layout.tsx
Build 'amazon-ebs.demo' errored after 10 minutes 53 seconds
```

### 티켓의 C2 는 「AMI 가 그 파일을 갖고 있지 않다」였다. 실측은 더 세다

**AMI 를 **구울 수 없다.** `fan-platform-web/package.json` 은

```
"@demo/backend-resolver": "link:../../../../infra/demo/backend-resolver"
```

인데 그 경로는 이 이미지의 빌드 컨텍스트(`projects/fan-platform`) **밖**이다.
⇒ C2 는 「호스트 로컬 상태를 AMI 로 옮기면 된다」가 아니라 **「그 이관 자체가 막혀 있다」**였다.

### 🔴 install 은 통과한다 — 그래서 install 로그로는 안 보인다

```
#69 pnpm install --frozen-lockfile
#69 13.73 Progress: resolved 576, downloaded 576, added 576
#69 14.15 Done in 13.3s          ← 성공한다
#78  ... next build → Module not found
```

`link:` 는 대상이 없어도 **심링크를 만들고 끝난다**. 죽는 것은 다음 단계다.
⇒ **「install 초록」은 워크스페이스가 온전하다는 증거가 아니다.**
[[env_pnpm_file_vs_link_changes_the_realpath]]

### 🔴🔴 왜 아무도 못 봤나 — 각각 옳은 두 제외가 합쳐져 구멍이 됐다

| 경로 | 이 결함을 만나나 | 왜 |
|---|---|---|
| CI `Frontend lint & build` | ❌ | `pnpm --filter fan-platform-web build` 를 **러너에서** 돈다(ci.yml:2589) — 저장소 루트라 링크가 해소된다 |
| CI 이미지 빌드 잡 | ❌ | **fan-platform-web 이미지를 굽는 CI 잡이 없다** |
| 형제 `web-store` 의 데모 이미지 | ❌ | 데모에서 **억제**돼 있다(ADR-MONO-067 단계 2) ⇒ 굽기 대상이 아니다 |
| `web-store` 의 Vercel 빌드 | ❌ | 저장소 루트에서 빌드한다 |
| **AMI 굽기** | ✅ | **여기 하나뿐** |

`link:` 는 `c2df17060`(#3586, `TASK-MONO-614`)에서 들어왔고 마지막 굽기는 `6bc2a44e7`(08-29)다.
**이번이 그 뒤 첫 굽기**였고 그래서 지금 터졌다. 🔴 `TASK-MONO-614` 티켓 본문에
`docker`·`image`·`Dockerfile`·`AMI` 는 **0회** 등장한다 — 기각된 축이 아니라 **고려되지 않은 축**이다.
[[feedback_two_correct_exclusions_compose_into_a_hole]]

### 고친 것 — 결정을 건드리지 않는 최소 모양

해석기의 **위치도**(저장소 루트 `infra/demo/`) **단일 구현 규칙**도(ADR-MONO-068 § D6 = B2)
그대로 두고, package.json·lockfile 도 안 건드린다(그러면 Vercel 빌드까지 흔든다). 바꾼 것은
**이미지가 그 패키지를 보게 하는 것** 하나다:

- compose: `additional_contexts: { demo-backend-resolver: ../../infra/demo/backend-resolver }`
- Dockerfile: `pnpm install` **앞**에 `COPY --from=demo-backend-resolver . /infra/demo/backend-resolver`

🔵 목적지 `/infra/...` 는 임의가 아니라 **lockfile 이 정한다**: pnpm 은 `link:` 를 선언한
package.json 의 디렉터리 기준으로 풀고, 이미지에서 `/app/<app>` 에서 네 번 올라가면 `/` 다
(루트의 부모는 루트) ⇒ `/infra/demo/backend-resolver`. 저장소에서는 같은 상대경로가 저장소
루트를 가리키므로 **깊이가 일치한다.** 🔵 러너 이미지에는 안 들어간다 —
`transpilePackages` 가 TS 소스를 번들에 컴파일해 넣는다.

🔴 **형제도 같이 고쳤다.** `web-store` 는 같은 `link:` 를 갖고 같은 모양으로 죽는다 — 오늘은
억제돼 있어 안 굽힐 뿐이다. 한쪽만 고치면 억제가 풀리는 날 그대로 낙오한다.
[[feedback_grep_the_siblings_before_fixing_it_yourself]]

### 실측 — 같은 호스트에서 A/B (대조군을 먼저 돌렸다)

```
NEGATIVE CONTROL  origin/main (7ec0527)  → rc=1  "Module not found: Can't resolve '@demo/backend-resolver'"
TREATMENT         브랜치                  → rc=0  "Image fan-platform/fan-platform-web:local Built" · 해당 오류 0건
```

🔵 대조군이 없었으면 「이 호스트에서는 원래 잘 빌드된다」와 구별할 수 없었다.

### 가드 (z26) — 술어는 **모양**이지 「fan 이 resolver 를 갖는가」가 아니다

> 프로젝트 밖을 가리키는 `link:`/`file:` 의존이 있으면, 그 프로젝트 compose 가 같은 대상을
> `additional_contexts` 로 넘기고, 그 앱 Dockerfile 이 그 이름을 `COPY --from=` 으로 받아야 한다.

bite 4/4 (주입 확인 포함), 그리고 **두 주입이 서로 다른 문장으로** 떨어진다:

| # | 주입 | 결과 |
|---|---|---|
| ⓪ | 없음 | ✅ ok — 탈출 의존 **2건** 전부 전달 · package.json **11개** 스캔 |
| ① | compose 의 `additional_contexts` 제거 | ✅ FAIL — *"compose 에 additional_contexts 없음"* |
| ② | Dockerfile 의 `COPY --from` 만 제거 | ✅ FAIL — *"compose 는 '…' 를 넘기는데 Dockerfile 이 COPY --from=… 로 안 받음"* |
| ③ | 복원 | ✅ ok (트리 변경 0건 확인) |

### 🔴 그리고 이 칸의 **하한이 내 계측기를 잡았다**

첫 판은 열거를 `git ls-files '*package.json'` 로 했다. 데모 호스트에서 이 스크립트는
**root** 로 도는데 저장소는 ubuntu 소유라 git 이 *"detected dubious ownership"* 로 죽어
**0줄**을 냈다. 하한이 없었다면 술어는 그 0을 **「탈출 의존 없음」**으로 읽고 **고장난 채
영원히 초록**이었을 것이다 — ⓪부터 ③까지 전부 통과하는 모양으로.

🔵 하한을 **「탈출 의존 수」가 아니라 「스캔한 파일 수」**에 건 것이 이 칸을 살렸다. 탈출
의존은 정당하게 0이 될 수 있지만(해석기를 프로젝트 안으로 옮기면), 스캔 0은 언제나 고장이다.
⇒ 열거를 git 에서 떼어 `find` 로 바꿨다 — 소유권에도, **스테이지 여부에도** 안 걸린다.
[[feedback_a_non_vacuity_floor_under_a_draining_population]] [[env_a_guard_reading_git_ls_files_is_blind_to_unstaged_work]]

🔵 `realpath` 요구도 **선언**했다 — 안 하면 (z2)가 그것을 범위에 넣지 않고, AMI 에 없으면
packer 7단계에서야 죽는다(그게 (z2)의 존재 이유다). (z2)의 base 제공 부류에 함께 넣었다.

---

# ✅ 재굽기 7차 + 기동 창 #2 — C1·C2·C3·C4 판정 (2026-09-03 UTC)

`ami-03144d1436a69bbfb`(`portfolio-demo-1788413688`, main `adcf4c22c`) ·
인스턴스 `i-059942a457386d186` · `DEMO_DOMAIN=15-165-11-142.sslip.io`.

## 🟢 부팅 #5 — 이 창에서 **처음으로** 유닛이 성공했다

```
Result=success   ExecMainStatus=0   ActiveState=active
06:38:10 → 06:49:40  (690s,  TimeoutStartSec 1800)
[boot] 잔존 스택 정리 (DEMO_BOOT_RESET=1) …    ← ⓐ 가 새 AMI 에서 돈다
[drift] 라벨 일치 — 실행 중인 컨테이너에 … 아닌 sslip 호스트명이 없습니다
[demo] ✔ HTTP 표면 2/2: console=307 web.fan-platform=307
Created 0 · running 99 · 시드 6도메인 실패 0 (생성 wms 3·scm 11·finance 7·erp 20·ecommerce 12·fan 9)
```

🔵 새 인스턴스라 잔존 컨테이너가 없어 ⓐ 의 down 은 사실상 no-op 다 — 이 부팅이 증명하는
것은 「ⓐ 가 신선 부팅을 깨지 않는다」이지 「경합을 고쳤다」가 아니다. 후자는 부팅 #3·#4 가
이미 쟀다.

## ✅ C1 — 404 **이고** 컨테이너가 존재하지 않는다 (AC-3 요구 그대로)

| 축 | 실측 |
|---|---|
| 상태코드 | `web.ecommerce.15-165-11-142.sslip.io` → **404** |
| 🔴 **존재** | `docker ps -a --filter name=web-store` → **0건** |
| 양성 대조군 | `console` → **307** · `web.fan-platform` → **307** (억제 안 된 화면은 열린다) |
| 라벨 드리프트 | 다른 sslip 호스트명을 가진 컨테이너 **0건** · `[drift] 라벨 일치` |

🔴 **상태코드만으로 판정하지 않았다** — 기동 창 #1 에서 정확히 그 오독이 있었고, 그때
살아남은 `ecommerce-web-store`(2026-09-02 생성 · 라벨 `43-202-166-3.sslip.io`)는
**`down --remove-orphans` 를 두 부팅 연속 견뎠다**. 이제 그 컨테이너는 **없다**.
⇒ `TASK-MONO-604` AC-4 가 요구한 상태에 도달했다.

## ✅ C2 — 파일이 아니라 **번들**로 확인했다

- `infra/demo/{auth-forwarder,backend-resolver}` 가 AMI 에 존재 ✅
- 🔴 그러나 「파일이 있다」는 판정이 아니다. 러너 이미지 **안**을 봤다:
  `not-demo`(이 해석기에만 있는 문자열 — 미니파이는 식별자는 바꿔도 **문자열 리터럴은
  안 바꾼다**)가 `/app/web/fan-platform-web/.next/server/chunks/839.js` 에 있다.
- 🔵 **음성 대조군**: 존재하지 않는 센티넬 문자열 → **0 파일**(grep 이 아무거나 잡는 게
  아님을 증명). 형제 리터럴 `unavailable` 4파일 · `running` 8파일.

🔴 내 첫 술어는 `createDemoBackendResolver` 를 찾는 것이었고 **0건**이 나왔다. 프로덕션
빌드는 **식별자를 미니파이**하므로 그 술어는 애초에 성립할 수 없었다 — 「없다」가 아니라
**「그렇게는 못 잰다」**였다.

## 🟡 C3 — `demo.env` 는 저장소 판본, 내용 수정 0건. 다만 `git status` 는 0줄이 아니다

```
$ git status --short
 M infra/demo/demo-boot.sh          ← 이것 하나
$ git diff --numstat -- infra/demo/demo-boot.sh
0       0       infra/demo/demo-boot.sh
$ git diff --summary
 mode change 100644 => 100755 infra/demo/demo-boot.sh
demo.env:73  IAM_PUBLIC_URL=https://auth.hubwang.com      ← 저장소 판본과 동일
```

🔵 **내용 diff 는 0/0 이다.** 유일한 차이는 **실행 비트**이고, 이것은 AMI 굽기가 남기는
자국이다(기동 창 #1 의 옛 인스턴스에서도 같은 한 줄이 나왔다 — 재현 가능한 성질이지
호스트에서 누가 고친 흔적이 아니다).

🔴 그래서 AC-3 의 문장(**「호스트 로컬 수정이 0건」**)을 **문자 그대로는 충족하지 않는다**.
`git status` 는 1줄이다. 두 가지가 참이다:
- 판정이 묻는 것(**뒤집기가 저장소에서 왔는가 · 손으로 고친 값이 없는가**)은 **충족**된다.
- 술어(`git status` 0줄)는 **모드 변경을 내용 변경과 구별하지 못한다.**

⇒ 「0건이다」로 적지 않고 이렇게 남긴다. 다음 사람이 이 한 줄을 보고 드리프트로 오독하거나,
반대로 술어를 느슨하게 고쳐 **진짜 내용 변경까지 통과시키는** 것을 막아야 한다.
🔵 술어를 고칠 거라면 방향은 `git diff --numstat` 이 0 인가 + `--summary` 가 mode change
뿐인가이지, `git status` 를 지우는 것이 아니다.

## ✅ C4 — 파일 · 런타임 · **그리고 로그인 자체**

```
projects/fan-platform/.env:25   OIDC_CLIENT_SECRET=fan-platform-dev
mtime 2026-09-03 06:38:12                  ← 이 부팅에서 새로 만들어졌다
73행의 재선언은 **없다**                    ← B2 가 지운 그것
docker inspect fan-platform-web → OIDC_CLIENT_SECRET=fan-platform-dev   ← 런타임이 읽은 값
```

토큰 엔드포인트 지문이 **뒤집혔다**:

| 시점 | 응답 | 뜻 |
|---|---|---|
| B2 진단 당시 | `invalid_client` **401** | 시크릿이 틀렸다 |
| 지금 | `invalid_grant` **400** | **자격은 통과**, 코드만 가짜(대조군 ecommerce 와 같은 값) |

### 🔴 그리고 티켓이 요구한 「팬 로그인 **자체**」를 왕복으로 확인했다

```
next-auth /api/auth/signin/iam
  → 302 https://auth.hubwang.com/oauth2/authorize?…            (C3 뒤집기 = 공개 HTTPS)
  → 302 /login  →  200 4,247B (폼 · _csrf 96자 · username 필드)
  → POST demo@demo.com / Demo1234!  → 302 …/oauth2/authorize?…&continue
  → 콜백 → 200  http://web.fan-platform.15-165-11-142.sslip.io/
GET /api/auth/session →
  {"user":{"email":"demo@demo.com"},"accountId":"0199de70-0000-7000-8000-00000000fa02",
   "tenantId":"fan-platform","roles":["FAN"]}
```

⇒ B2 § 남은 공백 2 (*"팬 로그인이 실제로 되는지는 미측정이다 … 닫으려면 기동 창이 필요하다"*)
가 **닫혔다.** 🔵 그리고 여섯 도메인 시드가 전부 `실패 0` 인 것이 같은 사실의 독립 증거다 —
시드는 실제로 로그인 폼을 통과해 토큰을 받는다.

### 🔴🔴 그 왕복을 재는 동안 내 술어가 **없는 결함을 만들어 냈다**

`curl` 로 authorize 를 치자 **401 `WWW-Authenticate: Bearer`, 0 bytes** 가 나왔다. 나는
① 공개 포워더 ② 데모 호스트 직격 ③ **컨테이너 직격(Traefik 밖)** 세 경로를 비교해 셋 다
같은 401 임을 확인했고, 이어서 클라이언트 4개(fan·console·ecommerce·wms)가 **전부 401**
임을 확인했다. 「포워더도 Traefik 도 C3 도 아니고 IdP 전역이다」— 증거가 아주 그럴듯했다.

**전부 내 헤더를 재고 있었다.** Spring Security 는 엔트리포인트를 **콘텐츠 협상**으로
고른다. `Accept: */*` 면 리소스서버 엔트리포인트가 401 을 주고, 브라우저가 보내는
`Accept: text/html…` 이면 `/login` 으로 302 한다. 같은 URL을 헤더만 바꿔 다시 치자
**200 /login** 이 나왔고 왕복이 끝까지 갔다.

🔵 답은 저장소 안에 이미 있었다 — `infra/demo/seed/lib.sh:159` 의 로그인 헬퍼가
`-H 'Accept: text/html'` 을 **명시적으로** 붙이고 있다. 조사 전에 그 파일을 열었어야 했다.
🔴 「클라이언트 4개 전부 401」은 **모집단 조사처럼 보였지만** 네 번 모두 같은 내 헤더를
잰 것이라 **정보량이 0**이었다 — 표본을 늘려도 공통 결함이 나와 함께 늘어나면 그 조사는
증거가 아니다. 그대로 적었으면 **없는 IdP 결함**을 티켓에 랜딩했을 것이다.
[[feedback_my_verification_predicate_is_the_likeliest_defect]]
[[feedback_a_verifiable_mechanism_is_not_the_cause]]
[[feedback_grep_the_siblings_before_fixing_it_yourself]]

## 이 창이 **안 닫은** 것

- **AC-4 의 Vercel 축** — `TASK-MONO-586` 라이브 · `TASK-MONO-610` AC-4b. 재굽기는 그것을
  안 고친다(티켓이 이미 그렇게 적었고 이번에도 그대로다).
- **매달림(rc=124) 경로** — 이 창 전체(부팅 5회)에서 한 번도 재현되지 않았다. `#3601` 의
  호출당 상한은 **여전히 미시험**이다. 🔴 「고쳐졌다」가 아니라 **「아직 안 걸렸다」**이다.
- **`--live` 전체 통과** — (f) 가 `container_name: scm-platform-redis` 고정 때문에 떠 있는
  데모 호스트에서 구조적으로 못 돈다. 이 창의 `--live` 판정은 **(f) 앞까지**다.
- **C3 술어** — 위 § C3 참조. 판정은 충족되지만 `git status` 는 1줄이고, 술어가 mode 를
  content 와 구별하지 못한다는 사실을 남긴다.

---

# ⚪ ⓐ 매달림(rc=124) — 기동 창 #3 (2026-09-03 UTC) 에서도 **미측정**

`TASK-MONO-616` 이 이 칸을 매니페스트 **칸 6** 으로 들고 갔다. 판정은 **미측정**이고,
**사유가 「부팅에서 안 걸렸다」에서 「원리적으로 못 쟀다」로 바뀌었다.**

| | 창 #2 (09-03 오전) | **창 #3 (09-03 오후)** |
|---|---|---|
| 사유 | 부팅 **5회**에서 한 번도 재현되지 않음 | 🔴 **부팅 로그에 접근할 수 없음** |
| 근거 | ⓐ 적용 후 690~777s 정상 종료 | 컨트롤 플레인이 노출하는 것은 `/start` `/stop` `/status` `/heartbeat` **넷뿐** — 로그 엔드포인트가 없다 |
| 처분 | 다음 창의 입력 | 소유자가 **호스트 로그를 제공하지 않기로 지정** ⇒ 다음 창의 입력으로 유지 |

⇒ `#3601` 의 **호출당 상한은 여전히 미시험**이다. 🔴 **「고쳐졌다」가 아니라 「아직 안
걸렸다」이고, 이번에는 「볼 수도 없었다」가 더해졌다.** 단일 표본을 성질로 승격시키지 마라.

🔵 **다음 창을 위한 요구사항이 하나 확정됐다**: 이 칸은 **호스트 접근(SSM/ssh) 또는
`journalctl -u demo-stack` 출력**이 있어야만 판정된다. 창을 여는 것만으로는 안 된다 —
**창에 무엇이 딸려 와야 하는지**까지 매니페스트에 적어야 한다.

---

# ⚪ ⓐ **닫는다 — 「측정 불가」가 이 칸의 답이다** (2026-09-04, 소유자 재확인)

🔴 **이 칸을 「다음 창의 입력」으로 계속 넘기는 것을 여기서 멈춘다.** 소유자에게 다시
물었고(2026-09-04), **호스트 로그를 제공하지 않는다는 지정이 유효**하다는 답을 받았다.

| | |
|---|---|
| 판정 | ⚪ **미측정 — 그리고 측정 불가(확정)** |
| 사유 | 컨트롤 플레인이 노출하는 것은 `/start` `/stop` `/status` `/heartbeat` **넷뿐**이고, 호스트 접근은 **소유자가 제공하지 않기로 지정**했다(2026-09-03 최초 · **2026-09-04 재확인**) |
| 왜 이것이 «답» 인가 | 이 AC 가 요구한 것은 *"매달림이 재현되면 `⏱ 매달림:` 줄과 요약이 남는가"* 이고, 그 관측 지점이 **원리적으로 닫혀 있다**는 것은 추측이 아니라 **실측된 사실**이다. ⚪ 가 「측정 불가를 기록함」이면 그것은 그 AC 의 **답**이다 — `TASK-MONO-574` AC-2 가 남긴 역방향 선례 |

## 🔴 그러나 「닫는다」가 「고쳐졌다」는 아니다 — 두 문장을 분리해 남긴다

1. **`#3601` 의 호출당 상한은 여전히 미시험이다.** 부팅 **5회**(창 #2) + 창 #3 에서 한 번도
   발화하지 않았다. 🔴 **단일 표본을 성질로 승격시키지 마라** — 「안 걸렸다」는 「안 걸린다」가
   아니다.
2. **그 미시험 상태를 관측할 수단이 없다.** 그러므로 이 저장소는 그 상한에 대해
   **「모른다」를 아는 상태**로 간다. 그것이 최선이고, 「초록」으로 적는 것은 거짓이다.

## 🔵 이 칸을 다시 열 수 있는 조건 — **날짜가 아니라 사건이다**

⏳ 아래 **셋 중 하나**가 참이 되면 그때 새 티켓으로 다시 연다. 🔴 그 전에는 매니페스트에
적지 마라 — 닫힌 칸을 창마다 다시 세면 소화율 분모가 거짓이 된다.

| # | 조건 | 그때 무엇이 가능해지나 |
|---|---|---|
| A | 소유자가 SSM/ssh 접근을 제공한다 | `journalctl -u demo-stack` 직독 |
| B | 컨트롤 플레인에 로그 엔드포인트가 생긴다 | `/logs` 로 부팅 로그 회수 |
| C | **매달림이 관측 가능한 표면에서 재현된다** | 🔵 부팅이 정상 시간(690~777s)을 **크게** 넘겨 `/status` 폴링으로도 보이는 경우 — 이때는 로그 없이도 사건 자체가 증거다 |

🔴 **C 를 매 창의 「공짜 관측」으로 남긴다** — 부팅 소요 시간은 `/status` 폴링으로 **이미
재고 있으므로 추가 비용이 0** 이다. 창 #4 매니페스트가 이것을 그렇게 적는다
(`TASK-MONO-621` — **칸이 아니라 부수 관측**).

## 🔵 이 창이 부수적으로 재확인한 것 (전부 창 #2 판정과 일치)

| 축 | 창 #3 관측 |
|---|---|
| C1 — `web.ecommerce` 억제 | **404** — 🔵 그리고 console·`web.fan-platform` 이 **307 로 올라온 시점에도** 404 (부팅 초기의 전면 404 와 구별되는 순간) |
| B1 / V4 — `/connect` 라우팅 | `/connect/logout` → **401** (404 아님) |
| C3 — 뒤집기 발효 | 데모 호스트 직격 discovery 의 `issuer` = `https://auth.hubwang.com` |
| B2 / C4 — 팬 로그인 | `fan.hubwang.com` 왕복이 **세션까지** 성립(`roles:["FAN"]`) |
| 부팅 | 표면 2/2 (`console=307 web.fan-platform=307`) |

## 🔴 C3 술어 — **이 창의 칸이 아니었다** (그리고 그대로 열려 있다)

§ C3 이 남긴 *"술어(`git status` 0줄)가 mode change 를 content 와 구별하지 못한다"* 는
**라이브가 필요 없는 문서·술어 작업**이라 `TASK-MONO-616` 매니페스트가 «창 밖»으로 분류했다.
🔵 방향은 그 절이 이미 적었다 — `git diff --numstat` 이 0 인가 + `--summary` 가 mode change
뿐인가이지, `git status` 를 지우는 것이 아니다. 🔴 이번 창은 호스트 접근이 없어 그 한 줄의
**재현 확인조차 못 했다** — 같은 사유(로그·셸 부재)다.

---

# 🔴🔴 ⓐ 의 닫음을 **철회한다** — 그 전제가 틀렸다 (2026-09-04, 같은 날)

위 § ⓐ 닫는다 는 **몇 시간 만에 뒤집힌다.** 내가 소유자에게 제시한 전제가 틀렸기 때문이다.

## 무엇이 틀렸나

나는 이렇게 적었다:

> 컨트롤 플레인이 노출하는 것은 `/start` `/stop` `/status` `/heartbeat` **넷뿐**이고,
> 호스트 접근은 소유자가 제공하지 않기로 지정했다 ⇒ 관측 지점이 **원리적으로 닫혀 있다**.

🔴 **첫 문장은 참인데 결론이 거짓이다. 호스트 접근은 컨트롤 플레인을 거치지 않는다.**

| 증거 | 값 |
|---|---|
| 내 AWS 자격 | `portfolio-demo-deployer` 에 **`AmazonSSMFullAccess`** 부착(실측) |
| 인스턴스가 SSM 관리 대상인가 | ✅ **컨트롤 플레인 자신이** `ssm.send_command(DocumentName="AWS-RunShellScript")` 로 `/domain/start` 를 구현한다(`handler.py:241`) |
| 확인 | `aws ssm describe-instance-information` rc=0 |

⇒ `journalctl -u demo-stack` 은 **처음부터 읽을 수 있었다.**

## 🔴 이것이 왜 나쁜 종류의 오류인가

**「없다」를 「내가 본 창구에 없다」로 판정했다.** 컨트롤 플레인의 라우트 목록은 사실이었고,
그 사실에서 **접근 경로 전체의 부재**를 유도한 것이 비약이다 — 부재 판정을 **대리지표**로
했다. 그리고 그 잘못된 전제를 **소유자에게 이지선다로 올려** 결정을 받아냈다:
선택지 자체가 *"창을 더 연다고 바뀌지 않는다"* 를 기정사실로 적고 있었다.
🔴 **소유자는 내가 준 두 갈래 안에서만 고를 수 있다** — 갈래를 잘못 그리면 답도 잘못된다.
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]
[[feedback_blocked_decision_check_the_shared_premise]]

## 🔵 그런데 이 칸이 되살아난 이유는 **내가 「다시 열 조건」을 사건으로 적었기 때문**이다

닫으면서 조건 **A = 「소유자가 SSM/ssh 접근을 제공한다」** 를 적어 뒀고, 그 조건이
**이미 충족돼 있었다**는 것이 드러나자 칸이 자동으로 되살아났다.
🔴 **날짜로 적었으면("3개월 뒤 재검토") 이 정정은 그 칸을 못 살렸다.**
⇒ 닫을 때 «무엇이 참이 되면 다시 여는가» 를 적는 규율이 여기서 값을 했다.

## 처분

| | |
|---|---|
| ⓐ 의 상태 | ⚪ 미측정 — **열려 있음**(닫음 철회) |
| 이번 창 | `TASK-MONO-621` 매니페스트의 **칸 7** 로 들어간다 |
| 🔴 PASS 기준 | **안 바뀐다.** 매달림은 확률적이라 미재현이면 **미측정(사유: 부팅 N회 미재현)** 이고 「고쳐졌다」가 아니다. 바뀐 것은 사유가 **「볼 수 없었다」 → 「봤는데 안 걸렸다」** 로 옮겨가는 것뿐 |
| 소유자 지정 | 🙋 **SSM 을 읽기 전용으로 사용**(2026-09-04) — 조회 명령만 |

---

# ⚪ ⓐ — 기동 창 #4 (2026-09-04 UTC): **미측정, 그러나 사유가 또 바뀌었다**

`TASK-MONO-621` 매니페스트 **칸 7**. SSM 읽기 전용 접근으로 **로그를 실제로 읽었다.**

| | 창 #2 | 창 #3 | **창 #4** |
|---|---|---|---|
| 사유 | 부팅 5회 미재현 | 🔴 **부팅 로그에 접근할 수 없음** | 🔵 **봤는데 안 걸렸다** |
| 근거 | 690~777s 정상 종료 | 컨트롤 플레인에 로그 엔드포인트 없음 | `journalctl -u demo-stack \| grep 매달림` = **0건** · 부팅 **713초**(정상대역) |

⇒ 판정 **⚪ 미측정 (사유: 부팅 1회에서 미재현)**.

🔵 **세 창 만에 사유가 「관측 불가」에서 「관측했으나 미발생」으로 옮겨갔다.** 그것이 SSM
정정의 값이다 — 이제 이 칸은 **부팅 횟수만 있으면 닫힐 수 있다**.
🔴 **그러나 여전히 「고쳐졌다」가 아니다.** `#3601` 의 호출당 상한은 **미시험**이고,
단일 부팅의 정상 종료를 성질로 승격시키지 않는다.

🔴 **다음 창에 필요한 것이 바뀌었다**: 「호스트 접근」이 아니라 **「부팅 횟수」**다.
매달림은 확률적이고 창 #2 는 5회에서도 못 잡았다 ⇒ 이 칸만을 위해 부팅을 늘리는 것이
예산에서 정당한지는 🙋 **소유자 판단**이다(부팅 1회 ≈ 12~20분).

---

# 🟢 B3 § 남은 공백 — **닫혔다** (기동 창 #4)

B3 은 이렇게 적어 뒀다:

> 🔴 **남은 공백**: 「JVM 이 그 JWKS 로 토큰을 실제로 검증했다」는 아직 **미측정**이다
> (유효한 팬 토큰이 필요하고 그건 B2 에 막혀 있다). B2+B3 이 끝나면 그것부터 재라.

B2 는 창 #2 에서 닫혔고 B3 은 랜딩됐다 ⇒ **막혀 있지 않았다.** 창 #4 의 시드가 그것을 잰다:

```
[seed:fan] 생성  FAN_POST(첫 콘서트 후기) · 댓글(PUBLIC 글) · 리액션 LOVE(PUBLIC 글)
[seed:fan] 생성  멤버십(MEMBERS_ONLY, 1개월, key=demo-seed-fan-membership-gen0)
[seed:fan] WELCOME 알림 도착 확인 (이벤트 경로 정상)
[seed:fan] 요약 — 생성 9 · 기존 0 · 실패 0
```

시드는 **실제 토큰으로 게이트웨이를 통과해** 팬 서비스에 9개를 썼고 이벤트 경로까지
확인했다. 🔵 리소스 서버가 그 토큰을 **JWKS 로 검증하지 않았다면 하나도 안 써졌다** —
B3 이 넓힌 `(w)`/`(z12)` 면제가 가리키던 바로 그 경로가 **런타임에 성립**한다.

🔴 **주의**: 이것은 `TASK-MONO-621` 칸 5(팬 **화면**)와 **다른 명제**다. 칸 5 는 FAIL
했지만(Vercel 이 백엔드에 못 닿는다) **백엔드 쪽 검증은 성립**한다. 한 칸의 FAIL 이
흡수된 항목까지 FAIL 로 만들지 않는다.

---

# 🟢 C3 술어 — **닫혔다** (2026-09-05 UTC, 창 불필요)

§ C3 이 남긴 결함: *"술어(`git status` 0줄)가 mode change 를 content 와 구별하지 못한다."*
그리고 § *"이 창의 칸이 아니었다 (그리고 그대로 열려 있다)"* 가 그것을 **라이브 불필요**로
분류했다. 여기서 닫는다.

## 🔴 먼저 — 그 술어가 **어디에도 없었다**

착수해서 찾아보니 `git status` 로 호스트 드리프트를 재는 코드가 **저장소에 0건**이다.
술어는 **이 티켓의 산문에만** 있었고, 기동 창 운영자가 손으로 쳤다.

⇒ **산문은 게이트가 아니다.** `TASK-MONO-622` 가 같은 날 랜딩한 교훈 그대로다 —
거기서는 규칙이 **위반한 메시지 여섯 줄 위**의 주석에 있었는데도 어겨졌다.
그래서 「문구를 고친다」가 아니라 **실행 가능한 술어로 옮긴다**.

## 구현 — `infra/demo/check-host-drift.sh`

§ C3 이 적어 둔 방향 그대로다(*"`git diff --numstat` 이 0 인가 + `--summary` 가
mode change 뿐인가이지, `git status` 를 지우는 것이 아니다"*).

| 무엇을 드리프트로 세나 | |
|---|---|
| 추적 파일의 **내용** 변경 (`numstat` 이 0/0 이 아님) | 🔴 **DIRTY** |
| **미추적** 파일 (호스트에서 누가 새로 만든 것) | 🔴 **DIRTY** |
| **mode 변경만** (AMI 굽기 자국, 두 세대에서 재현) | 🟢 **면제** — 🔴 다만 **이름을 찍는다**(조용한 면제는 「원래 그런 것」으로 읽힌다) |
| `.gitignore` 된 것 (런타임 산출물) | 🟢 안 센다 |

🔴 **종료 코드 1과 2를 가른다**: `0` CLEAN · `1` DIRTY · **`2` UNKNOWN**(git 없음 ·
저장소 아님 · git 실패). **「변경 0건」과 「못 쟀다」를 같은 초록으로 만들지 않는다** —
그러면 이 스크립트가 무의미해진다.

## 🔴🔴 self-test 가 **두 번** 내 결함을 잡았다 — 둘 다 술어가 아니라 **주입**이었다

**① Windows 에서 `chmod +x` 는 mode change 를 안 만든다.**
`core.filemode` 가 기본 false 라 아무 diff 도 안 생긴다 — 실측상 `filemode=true` 로 켜도
이 호스트에선 diff 0 이었다. 🔴 그래서 「mode 변경만 → CLEAN」 칸이 **주입 없이 통과**했다.
술어가 옳아서가 아니라 **잴 것이 없어서** 초록이었다(= 공허한 초록).
⇒ 워크트리 대신 **인덱스**를 바꾼다(`git update-index --chmod=+x`) — 리눅스·Windows 동일.
[[feedback_assert_the_injection_before_reading_the_bite]]

**② 그 수정이 「내용+mode 동시」 칸을 (2)의 중복으로 만들었다.**
`update-index --chmod` 은 **워크트리 내용도 인덱스에 올린다.** 내용을 먼저 바꾸고 chmod
하면 내용 절반이 스테이지돼 사라진다 — 그 칸이 **CLEAN 을 냈다.**
⇒ **chmod 먼저, 내용 나중.** 그리고 **복합 주입은 절반마다 단언**한다(mode 절반과 content
절반을 따로 확인). 🔵 한쪽만 확인하면 나머지가 조용히 빠지고, 그 칸은 이웃 칸의 중복이 된다.

🔵 **두 번 다 「주입확인」 줄이 잡았다.** 없었으면 ①은 공허한 초록으로, ②는
「술어가 mode 면제로 내용을 가린다」는 **잘못된 진단**으로 갔을 것이다.

## self-test 7칸 (매 실행)

| 칸 | 기대 |
|---|---|
| 대조군 — 손 안 댄 체크아웃 | CLEAN |
| **mode 변경만** | CLEAN + 면제 파일 이름 출력 |
| 내용 변경 | DIRTY |
| **내용 + mode 동시** | DIRTY — 🔴 mode 면제가 내용 변경을 **가리면 안 된다** |
| 미추적 파일 | DIRTY |
| `.gitignore` 된 변경 | CLEAN |
| 저장소 아님 | **UNKNOWN**(rc=2) — 🔴 부재는 초록이 아니다 |

🔵 픽스처 문자열이 아니라 **임시 git 저장소를 실제로 만들어** 건다. 손으로 지어낸 diff
문자열은 git 의 실제 출력보다 관대해서 초록이 공허해진다.

## 가드 (z30) — 🔵 **러너를 붙였다**

`verify-demo-wrapper.sh` 정적 구간에 칸 **(z30)**: 파일 존재 · `bash -n` · **self-test 실행** ·
**통과 칸 수 하한 7**. 🔴 하한이 있는 이유 — self-test 가 «아무 칸도 안 돌고» rc=0 이면
그것은 「술어가 옳다」가 아니라 **「아무것도 안 쟀다」**이고 둘은 rc=0 으로 구별되지 않는다.

🔵 **누가 부르는가를 명시했다**: CI 는 **술어 자신**을 돌리고, **값은 기동 창에서** 잰다
(호스트 체크아웃은 데모 호스트에만 존재하므로 자동 실행이 원리적으로 불가능하다).
둘을 섞지 않는다.

## 🔴 이 절이 닫지 **않는** 것

- **ⓐ(rc=124)** — ⚪ 「측정 불가」로 남는다. 창 #4·#5 의 관측(*"봤는데 안 걸렸다"* ·
  `매달림` 로그 0건)은 **표본이 는 것**이지 「고쳐졌다」가 아니다.
- **`--live` 의 (f) 이후** — 구조적. AC-4 가 「안 고치는 것」으로 이미 적었다.

## ⇒ 이 티켓의 남은 AC

**없다.** § *"이 창이 안 닫은 것"* 네 항목 중 셋은 그 뒤에 해소됐다 —
AC-4 의 Vercel 축(`586`·`610` **둘 다 `done`**) · ⓐ(⚪ 로 기록) · `--live` (f) 이후
(AC-4 의 「안 고치는 것」) — 그리고 넷째(C3 술어)를 이 절이 닫는다.
