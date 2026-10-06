# Task ID

TASK-MONO-763

# Title

데모 호스트가 소셜 로그인 키를 **SSM Parameter Store 에서 읽어** iam auth-service 에 넘긴다 — 키는 저장소에 두지 않고, 콜백 주소는 `IAM_PUBLIC_URL` 에서 파생한다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- infra
- iam-platform
- oauth-social

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (배선 — 부팅 스크립트 · compose · terraform 정책 한 줄씩)
>
> ⏳ **DO NOT MERGE before `TASK-BE-617`** — 날짜 조건이 아니다. 이 티켓이 머지되고 재굽기되면 키가 **배포**된다. `TASK-BE-617` 의 순서 규칙(2026-10-05): 실제 소셜 키는 617 머지 이후(또는 같은 PR)에만 배포 환경에 들어간다 — 617 전에는 소셜 가입이 사이트별 계정을 만들고, 그걸 합칠 묶기(`TASK-MONO-743`)는 구현 없이 닫혔다. 작업·리뷰는 먼저 해도 되고 **머지만** 617 뒤다(AC-0).

---

# 왜 루트 티켓인가

경로가 셋이다 — `infra/demo/`(부팅 스크립트 · `demo.env`) · `infra/demo/aws/terraform/`(인스턴스 역할 정책) · `projects/iam-platform/docker-compose.yml`(auth-service 환경변수 전달). 셋이 한 PR 이어야 «키는 읽는데 넘기지 않는다» 반쪽 상태가 main 에 안 생긴다.

# Dependency Markers

- **선행(머지 순서)**: `TASK-BE-617` — 위 DO NOT MERGE.
- 관계: `TASK-BE-623`(키 없는 제공자 버튼 숨기기)과 같은 재굽기에 실린다. 623 이 먼저 들어가면 이 티켓의 효과가 화면에 그대로 보인다(키를 넣은 제공자 버튼만 나타남).
- 출처: 2026-10-05 소유자 대화 — Google · Naver 키 발급 완료, SSM 에 저장(아래 실측).

# Goal

재굽기 + apply 뒤 데모에서, SSM 에 키가 있는 제공자로 소셜 로그인이 실제로 끝난다. 키가 없는 제공자는 지금처럼 기본값(`test-*`)이 남아 623 에 의해 버튼이 숨는다.

# 실측 (2026-10-05 UTC)

- SSM(`ap-northeast-2`) — 값은 출력하지 않고 이름·유형·형식만 확인:

  | 이름 | 유형 | 형식 확인 |
  |---|---|---|
  | `/portfolio-demo/oauth/google/client-id` | String | `….apps.googleusercontent.com` |
  | `/portfolio-demo/oauth/google/client-secret` | SecureString (`alias/aws/ssm`) | `GOCSPX-` 로 시작 · 35자 |
  | `/portfolio-demo/oauth/naver/client-id` | String | 20자 |
  | `/portfolio-demo/oauth/naver/client-secret` | SecureString (`alias/aws/ssm`) | 10자 |
  | (kakao · microsoft) | — | 아직 없음 |

  🔴 이름 칸에 Google client-id 가 들어간 **잘못된 SecureString 사본 하나**(`263781549944-…apps.googleusercontent.com`)가 남아 있다 — 값은 위 올바른 이름으로 복사했다. 삭제는 소유자 확인 대기.
- 제공자 콘솔에 등록한 콜백(소유자): `https://auth.hubwang.com/login/oauth/{google,naver}/callback` — `demo.env:73` `IAM_PUBLIC_URL=https://auth.hubwang.com` 과 같은 호스트.
- `projects/iam-platform/docker-compose.yml` 의 auth-service `environment:` 에 `OAUTH_*` 가 **하나도 없다** ⇒ 지금은 호스트 env 에 값이 있어도 컨테이너에 안 들어간다.
- 인스턴스 역할(`infra/demo/aws/terraform/main.tf:134-138`)은 `ssm:GetParameter` 를 `boot-selection` 파라미터 **하나에만** 준다 ⇒ 지금은 `/portfolio-demo/oauth/*` 를 못 읽는다.
- 부팅은 `demo-boot.sh` → `demo-selection.sh`(IMDS 로 리전 해소 후 `aws ssm get-parameter`, `:101`) → `demo-up.sh`(`demo.env` 를 `set -a; source`). 같은 IMDS·리전 해소를 재사용할 수 있다.

# Scope

## In Scope

- **terraform**: 인스턴스 역할에 `ssm:GetParameter` 를 `arn:aws:ssm:<region>:<account>:parameter/portfolio-demo/oauth/*` 로 **읽기만** 추가. (SecureString 이 `alias/aws/ssm` 이므로 별도 KMS 권한이 필요한지 AC-4 에서 실측한다 — 필요하면 그 키 하나에 `kms:Decrypt` 만.)
- **부팅**: `demo-up.sh` 전에 네 제공자 × (client-id, client-secret) 를 SSM 에서 읽어 `OAUTH_<P>_CLIENT_ID` / `OAUTH_<P>_CLIENT_SECRET` 로 export. 파라미터가 **없으면 export 하지 않는다**(기본값이 남아 623 이 버튼을 숨긴다) · 읽기 실패(권한·네트워크)는 부팅을 막지 않고 한 줄 경고. 🔴 값은 로그·`set -x`·상태 발행 어디에도 찍지 않는다.
- **compose**: auth-service `environment:` 에 8개 + 리디렉트 2종을 전달. 키는 `${OAUTH_GOOGLE_CLIENT_ID:-}` 처럼 **빈 기본값**(빈 문자열 → 623 이 미설정으로 본다). `verify-demo-wrapper.sh` (g) «미설정 compose 변수 0건» 이 이 형태를 어떻게 세는지 먼저 확인하고 맞춘다.
- **`demo.env`**(비밀 아님): `OAUTH_<P>_REDIRECT_URI=${IAM_PUBLIC_URL}/login/oauth/<p>/callback` · `OAUTH_<P>_ALLOWED_REDIRECT_URIS=` 같은 값(네 제공자). 기본값 `http://localhost:3000/oauth/callback` 이 데모에서 쓰이면 제공자가 `redirect_uri_mismatch` 로 거절한다.
- 정적 검증(`verify-demo-wrapper.sh` 등)에 «compose 가 OAUTH 키를 auth-service 에 전달한다» 칸 하나.

## Out of Scope

- 소셜 → 풀(`TASK-BE-617`) · 버튼 숨기기(`TASK-BE-623`).
- 운영 환경 비밀 관리(데모만).
- 키 값의 교체·폐기 절차(소유자 콘솔 몫).

# Acceptance Criteria

- [x] **AC-0 (머지 게이트)** — `TASK-BE-617` 이 `origin/main` 에 머지됐다(또는 이 PR 에 함께 있다). 아니면 머지하지 않는다.
- [x] **AC-1** — 정적: compose 렌더(`docker compose config`)에서 auth-service 가 `OAUTH_*_CLIENT_ID/SECRET/REDIRECT_URI/ALLOWED_REDIRECT_URIS` 를 받는다 · `demo.env` 만으로 렌더하면 키는 빈 문자열, 리디렉트는 `https://auth.hubwang.com/login/oauth/<p>/callback`.
- [x] **AC-2** — 부팅 스크립트 단위 시험(가짜 `aws`): 파라미터 있음 → export · 없음 → export 안 함 · 읽기 실패 → 경고 한 줄 + 계속. 🔴 대조군: 출력 어디에도 가짜 비밀 문자열이 안 나온다(grep 0).
- [ ] **AC-3** — `terraform plan`: 인스턴스 역할 정책 in-place 변경만(인스턴스 교체는 재굽기 때). 소유자 apply.
- [ ] **AC-4 (라이브, 재굽기 뒤 창)** — 인스턴스 안에서 `aws ssm get-parameter --with-decryption` 이 권한 오류 없이 성공(값 출력 금지 — 길이만) · auth-service 컨테이너 env 에 `OAUTH_GOOGLE_CLIENT_ID` 길이 > 0 · 로그인 화면에 Google · Naver 버튼만(623 함께) · **Google 로 가입 → 로그인 끝까지**(소유자 계정) · 팬 → 스토어 이동 시 재로그인 없음(617).

# Related Specs

- `projects/iam-platform/specs/features/oauth-social-login.md`
- `TEMPLATE.md` § Local Network Convention(데모 호스트명)

# Related Contracts

- 없음(배포 배선. API·이벤트 무변경).

# Edge Cases

- Naver 앱은 «개발 중» — 콘솔 «멤버 관리»에 등록된 계정만 로그인된다. AC-4 는 등록 계정으로 잰다. 공개 검수는 이 티켓 뒤(소유자 판단).
- Kakao · Microsoft 키가 나중에 SSM 에 추가되면 **재부팅만으로** 반영된다(재굽기 불필요) — 부팅 때 읽으므로. 이 성질을 README 에 적는다.
- 데모는 평소 꺼져 있다 — 제공자 검수자가 꺼진 데모를 볼 수 있다(검수 신청 때 안내 필요).

# Failure Scenarios

1. **키를 `demo.env` 에 적는다** — 저장소에 비밀이 커밋된다.
2. **compose 에 전달을 안 넣는다** — 호스트 env 에 값이 있어도 컨테이너는 기본값 `test-*` 를 본다(지금 상태).
3. **리디렉트 URI 를 안 바꾼다** — 기본값 `http://localhost:3000/oauth/callback` 으로 가서 `redirect_uri_mismatch`.
4. **617 전에 머지·재굽기** — 순서 규칙 위반: 사이트별 소셜 계정이 생길 틈.
5. **부팅 스크립트가 값을 찍는다** — `set -x` · 오류 메시지 · `/status` 발행에 비밀이 샌다(AC-2 대조군).
</content>
</invoke>

---

## review 이관 (2026-10-06 UTC)

- impl PR #4167 · 스쿼시 `ddf39df00` · 머지 시점 실패 0 (39 SUCCESS).
- AC-0 ✅ — `TASK-BE-617`(`71b66135c`) → `TASK-BE-623`(`85b4c9263`) → 이 티켓(`ddf39df00`) 순서로 머지(순서 규칙 지킴).
- AC-1 ✅ (`docker compose config` 렌더 + `verify-demo-wrapper.sh` 정적 PASS · (z43) 신설) · AC-2 ✅ (`test-fetch-oauth-secrets.sh` — 있음/없음/실패 + 가짜 비밀 grep 0).
- ⏳ AC-3 `terraform plan`(메인 체크아웃) → 소유자 apply · AC-4 라이브(재굽기 뒤 창).
- 🔵 구현 편차(옳음): auth-service 는 `projects/iam-platform/docker-compose.yml` 이 아니라 `infra/demo/iam-traefik.override.yml` 에 정의돼 있어 키 전달을 거기에 넣었다. KMS 권한은 넣지 않았다 — AC-4 에서 `alias/aws/ssm` 복호화가 역할 권한만으로 되는지 실측.
