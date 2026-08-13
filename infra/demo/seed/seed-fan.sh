#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-fan.sh — 팬 플랫폼 도메인 데이터 시드
# =============================================================================
# TASK-MONO-509 (MONO-506 의 S2 슬라이스).
#
# 세 단계다. 순서가 곧 도메인 규칙이다:
#
#   1) 직접-DB — 아티스트 · 그룹 · 팬덤  (**여기만** 남았다)
#   2) 아티스트 토큰(API) — ARTIST_POST 3종 가시성
#   3) 소비자 토큰(API) — 팔로우 · FAN_POST · 댓글 · 리액션 · 멤버십 구독
#
# 🔴 왜 1번만 아직 직접-DB 인가 (TASK-MONO-512 로 갱신, 2026-08-11)
# -----------------------------------------------------------------------------
# 원래 이유는 두 개였고 **하나는 해소, 하나는 성격이 바뀌었다**. 둘을 구별하지 않으면
# 남은 예외가 "아직 못 고친 것" 으로 읽히는데, 실제로는 **고치지 않기로 결정된 것**이다.
#
# (가) **팬 도메인에는 운영자 평면이 없다 — 그리고 앞으로도 열지 않는다.**
#      artist-service 의 `ADMIN_ROLES = {ADMIN, OPERATOR, SUPER_ADMIN, FAN_OPERATOR}`,
#      community-service 의 `ActorContext.isOperator()`, iam 의 `OperatorRoleDerivation`
#      `case "fan", "fan-platform"` — 셋 다 그대로 있고, **도달하는 테넌트는 여전히 0** 이다
#      (`tenant_domain_subscription` 에 `fan` 0/18행, `operator_tenant_assignment` 에
#      `fan-platform` 없음). 실측 그림도 그대로다:
#
#        POST /api/v1/artists         403 FORBIDDEN
#        POST /api/v1/artist-groups   403 FORBIDDEN
#        POST /api/v1/fandoms/{id}    403 FORBIDDEN
#
#      🔵 바뀐 것은 **그 결여의 지위**다. `ADR-MONO-059` 가 **A(아티스트에게 실제 계정)**
#      로 ACCEPTED 되면서 B(운영자 대리 저작)가 배제됐고, *"B2C_CONSUMER 테넌트를 운영자가
#      assume 하는 조합은 열지 않는다"* 가 구속력 있는 결정이 됐다. 구독 행이나 배정 행을
#      넣는 것은 이제 **미완의 작업이 아니라 결정에 반하는 작업**이다.
#      ⇒ 이 세 리소스는 **API 호출자가 아예 없다**. 그 공백을 다루는 것은 `TASK-MONO-522`
#      이고, 이 블록은 그 티켓이 답할 때까지 직접-DB 다. (`TASK-MONO-512` 를 사유로 들면
#      안 된다 — 그 티켓은 닫혔고, 닫히면서 이 블록을 **열지 않기로** 확정했다.)
#
# (나) ~~설령 (가) 가 고쳐져도 ARTIST_POST 는 API 로 못 넣는다~~ — **해소됐다.**
#      두 절반이 각각 랜딩했다:
#        · `TASK-FAN-BE-045` — `artists.account_id` 신설 + 항등 백필로 피드 조인
#          (`posts.author_account_id` ⋈ `follows.artist_account_id`)의 양 끝을 정의상 일치.
#        · `TASK-MONO-512` — 그 항등값을 **실재하는 IAM subject 로** 만들고(account-service
#          migration-dev `R__06` 이 **바로 그 id 로** 계정을 만든다) `ARTIST` 역할을 부여.
#      ⇒ 아티스트는 이제 **자기 계정으로 로그인해서** 자기 글을 쓴다. 아래 2번이 그것이다.
#
# 🔴 왜 데모 사용자가 게이팅 대상 글을 쓰면 안 되는가 — 요구는 그대로, 이제 **충족된다**
# -----------------------------------------------------------------------------
# `PostAccessGuard.ensureVisibilityAccessible` 와 `GetFeedUseCase.isLocked` 는 둘 다
# **`actor.owns(authorAccountId)` 면 무조건 통과**시킨다. 데모 계정이 MEMBERS_ONLY /
# PREMIUM 글의 작성자면 멤버십과 무관하게 전부 열려 가시성 시연이 **공허하게 초록**이 된다.
# 그래서 게이팅 대상 글은 반드시 **데모 계정이 아닌 주체**가 써야 하는데, 전에는 그것을
# "직접-DB 로 남의 id 를 박아 넣어서" 만족시켰다. 이제는 **진짜 다른 계정**(아티스트)이
# 로그인해서 쓴다 — 같은 성질을 우회가 아니라 구조로 얻는다.
# =============================================================================
set -uo pipefail
SEED_DOMAIN=fan
# shellcheck source=infra/demo/seed/lib.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib.sh"

GW="http://fan-platform.${DEMO_DOMAIN}"

container_up fan-platform-gateway || { seed_log "게이트웨이 미기동 — 건너뜀"; exit 0; }
# 🔴 `/api/artists` 가 아니라 `/api/v1/artists` 다. 팬 게이트웨이는 `/api/v1/**` 만
# 받아 다운스트림 `/api/**` 로 rewrite 한다 — `/api/artists` 는 404 다.
wait_http "$GW/api/v1/artists" 240 || { seed_fail "게이트웨이가 240초 안에 응답하지 않습니다"; seed_summary; exit $?; }

TENANT="${DEMO_FAN_TENANT:-fan-platform}"
DEMO_SUB="${DEMO_FAN_SUB:-0199de70-0000-7000-8000-00000000fa02}"

# 고정 id — 2회차 실행이 같은 것을 또 만들지 않게 하는 근거다(직접-DB INSERT 가
# `WHERE NOT EXISTS` 로 자기 id 를 검사한다). 랜덤이면 멱등이 성립할 수 없다.
#
# 🔴 이 세 값은 **iam 의 계정 id 이기도 하다** (TASK-MONO-512). account-service
# migration-dev `R__06` 이 정확히 이 id 로 `accounts` 행을, auth-service `R__02` 가
# 같은 id 로 `credentials` 행을 만든다. 그래서 `artists.account_id`(= 엔티티 id, FAN-BE-045
# 의 항등 백필)가 **로그인 가능한 진짜 subject** 가 된다. 세 파일이 어긋나면 아무것도
# 실패하지 않고 피드만 조용히 빈다 ⇒ `FanArtistDemoSeedTest` 가 셋을 대조해 고정한다.
# 값을 바꾸려면 세 곳을 함께 바꿔야 한다.
ARTIST_A="0199de80-0000-7000-8000-00000000a001"   # 팔로우 대상 · 3종 가시성 글의 저자
ARTIST_B="0199de80-0000-7000-8000-00000000a002"   # 디렉터리가 한 줄이 아니게 하는 두 번째
ARTIST_C="0199de80-0000-7000-8000-00000000a003"   # 그룹 멤버 (artist_type=GROUP_MEMBER)
GROUP_1="0199de80-0000-7000-8000-00000000b001"

# 아티스트 로그인 — auth-service migration-dev R__02 의 이메일. 비밀번호는 데모와 같은
# `Demo1234!` 다(면접관이 타이핑하는 계정은 여전히 demo@demo.com 하나 — 이쪽은 시드만 쓴다).
ARTIST_A_EMAIL="${DEMO_FAN_ARTIST_A_EMAIL:-lumi@demo.com}"
ARTIST_B_EMAIL="${DEMO_FAN_ARTIST_B_EMAIL:-noah@demo.com}"

# 🔴 게시물 id 는 **더 이상 고정 리터럴이 아니다.** API 로 발행하므로 서버가 UUIDv7 을
# 만든다(`PublishPostUseCase`). 아래 2번이 발행한 뒤 (저자, 제목)으로 되찾아 채운다 —
# 댓글·리액션이 PUBLIC 글의 id 를 필요로 하기 때문이다.
POST_PUB=""

# -----------------------------------------------------------------------------
# 1. 직접-DB — 아티스트 · 그룹 · 팬덤
# -----------------------------------------------------------------------------
# 시간은 **고정 리터럴**이다. NOW() 면 2회차 실행에서 값이 바뀌어 "행 수가 수렴한다" 를
# 넘어 "상태가 수렴한다" 가 깨진다. (게시물의 published_at 은 이제 이 규칙을 따르지
# 않는다 — 도메인이 발행 시각을 정하기 때문이고, 그 대가는 2번 머리에 적었다.)
if container_up fan-platform-postgres; then
  dbexec --why "아티스트 디렉터리는 **결정에 의해 영구히 직접-DB 다.** ADR-MONO-063 ACCEPTED — D1 (2026-08-13) 이 디렉터리(아티스트·그룹·팬덤)의 **쓰기 표면을 v1 제품 범위 밖으로 확정**했다 ⇒ 이 블록은 '아직 회수 못 한 우회' 가 아니라 **그 결정의 구현**이고, 잠정 표현은 여기서 제거됐다(이전 문구는 'TASK-MONO-522 소관' 이라는 미결 상태였다). POST /api/v1/artists|artist-groups|fandoms 는 hasAnyRole(ADMIN,OPERATOR,SUPER_ADMIN,FAN_OPERATOR) 이고 그 네 이름을 fan-platform 에서 드는 주체는 **사람도 기계도 없다**: FAN_OPERATOR 는 tenant_domain_subscription(*, 'fan') 에서 파생되는데 그 행이 전 테넌트 0/18 이고(fan-platform 은 operator_tenant_assignment 에도 없다 — 2026-08-13 재측정에서 셋 다 여전히 0), ADR-MONO-059 ACCEPTED-A 가 B(운영자 대리)를 배제하며 'B2C_CONSUMER 테넌트를 운영자가 assume 하는 조합은 열지 않는다' 를 구속력 있게 확정했고, ADR-MONO-061 이 만든 세 번째 길(워크로드 토큰이 roles 를 싣는다)도 D1 이 **닫았다**(어떤 cc 클라이언트도 admin-tier 를 받지 않는다 — auth-service WorkloadRoleCatalog + 그 테스트). 🔴 이 사유를 TASK-MONO-512 나 TASK-MONO-522 '소관' 으로 적으면 안 된다: 둘 다 닫혔고, 열지 않기로 **확정하면서** 닫혔다. 되돌리려면 ADR-MONO-063 을 개정해야 한다. 🔵 이 결정의 근거는 '호출자가 없다' 이므로, 콘솔에 fan 관리 화면이 생기는 날 그 근거가 사라지고 결정은 다시 열려야 한다" \
    fan-platform-postgres psql fanplatform_artist fanplatform <<SQL
-- account_id 는 **엔티티 id 와 동일**하게 넣는다(TASK-FAN-BE-045 V3 의 항등 백필과 같은 값).
-- 이유: 이 시드는 조인의 양쪽 모두에 아티스트 엔티티 id 를 쓴다 — follows 는 아래 API 호출로,
-- posts.author_account_id 도 이제 API 발행 결과로. 다른 값을 넣으면 팔로우 검증(FAN-BE-045
-- AC-6)이 이 시드 자신의 팔로우 호출을 거절한다.
-- 🔵 TASK-MONO-512 이후 이 값은 **실재하는 IAM subject 다** — account-service migration-dev
-- R__06 이 바로 이 id 로 계정을, auth-service R__02 가 자격증명을 만든다. 그래서 아래 2번이
-- 아티스트 **본인 로그인**으로 글을 발행할 수 있다. (재지정이 아니라 그 id 를 실재화한 이유는
-- R__06 헤더에 있다: 이미 시드된 데모 DB 의 follows/posts 가 옛 값을 들고 있기 때문이다.)
INSERT INTO artists (id, tenant_id, account_id, artist_type, status, stage_name, real_name, debut_date, agency, bio, created_at, updated_at, published_at, version)
SELECT '$ARTIST_A', '$TENANT', '$ARTIST_A', 'SOLO', 'PUBLISHED', '루미', '김하늘', DATE '2021-03-14', 'Aurora Entertainment',
       E'2021년 데뷔한 솔로 아티스트입니다. 어쿠스틱 기반의 자작곡을 주로 발표합니다.\n\n데모 데이터 — TASK-MONO-509',
       TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM artists WHERE id = '$ARTIST_A');

INSERT INTO artists (id, tenant_id, account_id, artist_type, status, stage_name, real_name, debut_date, agency, bio, created_at, updated_at, published_at, version)
SELECT '$ARTIST_B', '$TENANT', '$ARTIST_B', 'SOLO', 'PUBLISHED', '노아', '박서준', DATE '2019-08-01', 'Aurora Entertainment',
       E'프로듀서 겸 솔로 아티스트.\n\n데모 데이터 — TASK-MONO-509',
       TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM artists WHERE id = '$ARTIST_B');

INSERT INTO artists (id, tenant_id, account_id, artist_type, status, stage_name, real_name, debut_date, agency, bio, created_at, updated_at, published_at, version)
SELECT '$ARTIST_C', '$TENANT', '$ARTIST_C', 'GROUP_MEMBER', 'PUBLISHED', '세아', '이세아', DATE '2022-05-20', 'Aurora Entertainment',
       E'그룹 STELLAR 의 리더.\n\n데모 데이터 — TASK-MONO-509',
       TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM artists WHERE id = '$ARTIST_C');

INSERT INTO artist_groups (id, tenant_id, name, debut_date, agency, status, created_at, updated_at, version)
SELECT '$GROUP_1', '$TENANT', 'STELLAR', DATE '2022-05-20', 'Aurora Entertainment', 'ACTIVE',
       TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM artist_groups WHERE id = '$GROUP_1');

INSERT INTO group_memberships (group_id, artist_id, tenant_id, role, joined_at)
SELECT '$GROUP_1', '$ARTIST_C', '$TENANT', 'LEADER', TIMESTAMPTZ '2026-01-05 09:00:00+00'
WHERE NOT EXISTS (SELECT 1 FROM group_memberships WHERE group_id = '$GROUP_1' AND artist_id = '$ARTIST_C');

-- 팬덤은 아티스트당 1행이다(PK = artist_id) — 목록 엔드포인트가 없는 이유이기도 하다
-- (\`/api/fandoms\` 는 \`/{artistId}\` 만 있다; 목록 조회 404 는 결함이 아니다).
-- color_hex 는 CHECK '^#[0-9A-Fa-f]{6}\$' 를 만족해야 한다.
INSERT INTO fandoms (artist_id, tenant_id, fandom_name, color_hex, founded_at, slogan, created_at, updated_at, version)
SELECT '$ARTIST_A', '$TENANT', '루미나', '#7C3AED', DATE '2021-03-14', '언제나 같은 자리에서',
       TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM fandoms WHERE artist_id = '$ARTIST_A');

INSERT INTO fandoms (artist_id, tenant_id, fandom_name, color_hex, founded_at, slogan, created_at, updated_at, version)
SELECT '$ARTIST_B', '$TENANT', '노바', '#0EA5E9', DATE '2019-08-01', '밤하늘의 가장 밝은 별',
       TIMESTAMPTZ '2026-01-05 09:00:00+00', TIMESTAMPTZ '2026-01-05 09:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM fandoms WHERE artist_id = '$ARTIST_B');
SQL
  if [ $? -eq 0 ]; then seed_log "아티스트 3 · 그룹 1 · 팬덤 2 준비됨"; else seed_fail "아티스트/그룹/팬덤 INSERT 실패"; fi

  # 🔵 ARTIST_POST 는 여기 없다 — TASK-MONO-512 가 아래 2번(API)으로 옮겼다.
  # 직접-DB 시절 이 자리에는 `post_status_history` 를 손으로 넣는 블록도 함께 있었다
  # (도메인은 PUBLISH 시 반드시 이력을 남기므로, 이력 없는 PUBLISHED 는 도메인이 만들 수
  # 없는 상태이고 시드가 그런 상태를 보여주면 데모가 거짓말을 한다). API 로 옮기면서
  # 그 블록도 사라졌다 — 이력도, 아웃박스 이벤트도 이제 도메인이 만든다. **손으로
  # 재현하던 불변식이 재현 대상이 아니게 되는 것**이 이 이동의 진짜 이득이다.
else
  seed_fail "fan-platform-postgres 미기동 — 아티스트를 시드할 수 없습니다"
fi

# -----------------------------------------------------------------------------
# 2. 아티스트 토큰(API) — ARTIST_POST 3종 가시성        [TASK-MONO-512]
# -----------------------------------------------------------------------------
# 아티스트가 **자기 계정으로 로그인해서** 자기 글을 쓴다. 예전에는 이 블록이 직접-DB 였고
# 그 사유가 `--why` 에 적혀 있었다 — 사유가 해소됐으므로 면제도 함께 회수한다.
# 🔴 해소된 사유의 면제를 남기면 그 면제가 회귀를 가린다: 역할 발급이 나중에 깨져도
# 시드는 계속 초록일 것이고, 아무도 팬 도메인의 저작 경로가 죽은 것을 모른다.
#
# 🔵 시간이 고정 리터럴이 아니게 된 대가를 명시한다. 예전 published_at 은
# `TIMESTAMPTZ '2026-02-01 …'` 였고 지금은 발행 시각(NOW)이다. 피드 정렬
# (`ORDER BY publishedAt DESC`)이 흔들리지 않는 이유는 **발행 순서가 고정**이고 2회차
# 실행은 아래 탐지에서 걸러져 아예 다시 쓰지 않기 때문이다 — 절대값은 변하지만
# **상대 순서와 최종 상태는 수렴한다**. 바뀐 것은 "글이 2월에 올라온 것처럼 보인다" 뿐이다.

artist_token_for() { # <email> → stdout: access token (없으면 빈 문자열)
  user_token 'fan-platform-user-flow-client' "${OIDC_CLIENT_SECRET:-fan-platform-dev}" \
    "http://web.fan-platform.${DEMO_DOMAIN}/api/auth/callback/iam" \
    'openid profile email offline_access fan-platform.community.read fan-platform.community.write fan-platform.artist.read' \
    "$1"
}

# 🔴 토큰의 `roles` 를 직접 본다. 판정은 "로그인이 됐다" 가 아니라 **역할이 실렸는가** 다
# (TASK-MONO-512 AC-2). 계정과 자격증명만 있고 account_roles 행이 빠지면 로그인은 멀쩡히
# 되고 토큰도 나오는데 `RoleSeedPolicy` 기본값 [FAN] 만 실려 발행에서 403 이 난다 —
# 그때 증상은 "역할이 없다" 가 아니라 그냥 403 이라, 여기서 안 보면 원인이 세 겹 밑이다.
jwt_has_role() { # <token> <role>
  local payload="${1#*.}"; payload="${payload%%.*}"
  payload="$(printf '%s' "$payload" | tr '_-' '/+')"
  case $(( ${#payload} % 4 )) in 2) payload="$payload==";; 3) payload="$payload=";; esac
  printf '%s' "$payload" | openssl base64 -d -A 2>/dev/null | grep -q "\"$2\""
}

publish_artist_post() { # <라벨> <저자 account_id> <visibility> <제목> <본문>
  local label="$1" author="$2" vis="$3" title="$4" body="$5" n
  # 탐지는 dbquery(읽기 전용)다. 피드로는 탐지할 수 없다 — 피드는 팔로우 기반이고
  # 아티스트는 자기 자신을 팔로우하지 않으므로 자기 글이 자기 피드에 뜨지 않는다.
  n="$(dbquery fan-platform-postgres psql fanplatform_community fanplatform '' \
        "SELECT count(*) FROM posts WHERE tenant_id='$TENANT' AND author_account_id='$author' AND post_type='ARTIST_POST' AND title='$title'")"
  n="$(printf '%s' "${n:-}" | tr -d '[:space:]')"
  if [ -z "$n" ]; then
    seed_fail "$label — 존재 여부 조회가 아무 값도 내지 않았습니다(0건이 아니라 계측 실패다)"
    return 1
  fi
  if [ "$n" != "0" ]; then
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label"
    return 0
  fi
  api_create "$label" "$GW/api/v1/community/posts" \
    "{\"postType\":\"ARTIST_POST\",\"visibility\":\"$vis\",\"title\":\"$title\",\"body\":\"$body\"}"
}

# 아티스트로 로그인해 발행한다. 토큰의 sub 이 곧 `posts.author_account_id` 가 되므로
# (PublishPostUseCase 는 저자를 호출자로 고정한다) sub 이 아티스트 엔티티 id 와 다르면
# 그 글은 팔로워 피드에 영영 뜨지 않는다 — 발행 전에 확인한다.
seed_as_artist() { # <라벨> <email> <기대 account_id>
  local who="$1" email="$2" expected="$3" token sub
  token="$(artist_token_for "$email")"
  if [ -z "${token:-}" ]; then
    seed_fail "$who 토큰 발급 실패($email) — ARTIST_POST 를 시드하지 못했습니다. iam 의 migration-dev(auth R__02 · account R__06)가 로드됐는지 확인하십시오(e2e 프로파일 전용)"
    return 1
  fi
  sub="$(jwt_sub "$token")"
  if [ "$sub" != "$expected" ]; then
    seed_fail "$who 의 토큰 sub($sub) != 아티스트 엔티티 id($expected) — 이 계정으로 쓴 글은 팔로워 피드에 뜨지 않습니다(R__06 의 accounts.id 를 확인하십시오)"
    return 1
  fi
  if ! jwt_has_role "$token" ARTIST; then
    seed_fail "$who 의 토큰에 ARTIST 역할이 없습니다 — account_roles(fan-platform, $expected, 'ARTIST') 행을 확인하십시오(account-service R__06). 로그인은 됐으므로 자격증명 문제가 아닙니다"
    return 1
  fi
  SEED_TOKEN="$token"
  seed_log "$who 로그인 · ARTIST 역할 확인됨"
  return 0
}

if seed_as_artist '루미' "$ARTIST_A_EMAIL" "$ARTIST_A"; then
  publish_artist_post 'ARTIST_POST(PUBLIC · 루미)' "$ARTIST_A" PUBLIC \
    '새 싱글 「밤의 끝」 발매 안내' \
    '안녕하세요, 루미입니다.\n\n오랜만에 새 싱글로 인사드립니다. 「밤의 끝」은 지난 겨울에 쓴 곡이에요.\n모든 분들이 들으실 수 있도록 전체 공개로 올립니다.'
  publish_artist_post 'ARTIST_POST(MEMBERS_ONLY · 루미)' "$ARTIST_A" MEMBERS_ONLY \
    '멤버십 전용 — 작업실 이야기' \
    '멤버십 가입해 주신 분들께만 남깁니다.\n\n이번 앨범 작업을 하면서 세 번을 갈아엎었어요. 처음 데모와 지금 버전은 코드 진행부터 다릅니다.\n다음 주에는 미공개 데모 음원도 여기에 올릴게요.'
  publish_artist_post 'ARTIST_POST(PREMIUM · 루미)' "$ARTIST_A" PREMIUM \
    '프리미엄 전용 — 팬미팅 선예매 안내' \
    '프리미엄 멤버십 전용 안내입니다.\n\n3월 팬미팅 선예매 링크와 좌석 배치도를 먼저 공유드립니다. 일반 예매보다 48시간 빠릅니다.'
fi

if seed_as_artist '노아' "$ARTIST_B_EMAIL" "$ARTIST_B"; then
  publish_artist_post 'ARTIST_POST(PUBLIC · 노아)' "$ARTIST_B" PUBLIC \
    '프로듀싱 노트를 시작합니다' \
    '노아입니다. 앞으로 작업 과정을 짧게 기록해 두려 합니다.\n첫 글은 마이크 프리앰프 이야기부터.'
fi

# PUBLIC 글의 id 를 되찾는다 — 아래 댓글·리액션이 그 id 로 건다. 서버가 만든 UUIDv7 이라
# 리터럴로 알 수 없고, 2회차 실행(발행을 건너뛴 경우)에도 같은 방법으로 얻어야 한다.
POST_PUB="$(dbquery fan-platform-postgres psql fanplatform_community fanplatform '' \
  "SELECT id FROM posts WHERE tenant_id='$TENANT' AND author_account_id='$ARTIST_A' AND post_type='ARTIST_POST' AND visibility='PUBLIC' ORDER BY published_at LIMIT 1")"
POST_PUB="$(printf '%s' "${POST_PUB:-}" | tr -d '[:space:]')"
if [ -z "$POST_PUB" ]; then
  # 🔴 빈 값으로 진행하면 댓글 URL 이 `/posts//comments` 가 되고 404 를 "댓글 실패" 로
  # 보고한다 — 원인(글이 없다)과 증상(댓글이 안 달린다)이 갈라진다.
  seed_fail "PUBLIC ARTIST_POST 를 찾지 못했습니다 — 댓글·리액션을 건너뜁니다"
fi

# -----------------------------------------------------------------------------
# 3. 소비자 토큰 — "내" 데이터. 여기부터도 전부 API 다.
# -----------------------------------------------------------------------------
# 이 플로우가 성립한다는 것 자체가 면접관이 밟을 팬 웹 로그인 경로가 살아 있다는 증거다.
CONSUMER_TOKEN="$(user_token 'fan-platform-user-flow-client' "${OIDC_CLIENT_SECRET:-fan-platform-dev}" \
  "http://web.fan-platform.${DEMO_DOMAIN}/api/auth/callback/iam" \
  'openid profile email offline_access fan-platform.community.read fan-platform.community.write fan-platform.artist.read')"
if [ -z "${CONSUMER_TOKEN:-}" ]; then
  seed_fail "소비자 토큰 발급 실패 — 팔로우/글/댓글/리액션/구독을 시드하지 못했습니다"
  seed_summary; exit $?
fi
SEED_TOKEN="$CONSUMER_TOKEN"

# 🔴 시드가 쓰는 sub 이 실제 토큰의 sub 과 다르면 "내 것" 이 전부 남의 것이 된다.
TOKEN_SUB="$(jwt_sub "$CONSUMER_TOKEN")"
if [ "$TOKEN_SUB" != "$DEMO_SUB" ]; then
  seed_warn "토큰 sub($TOKEN_SUB) != DEMO_FAN_SUB($DEMO_SUB) — 토큰 쪽을 신뢰합니다"
  DEMO_SUB="$TOKEN_SUB"
fi

# 팔로우 — 피드는 팔로우 기반이다. 이게 없으면 게시물이 다 있어도 홈이 빈다.
# 서버가 중복을 409 로 거절하므로 api_create 로 충분하다.
api_create '팔로우(루미)' "$GW/api/v1/community/follows" "{\"artistAccountId\":\"$ARTIST_A\"}"
api_create '팔로우(노아)' "$GW/api/v1/community/follows" "{\"artistAccountId\":\"$ARTIST_B\"}"

# FAN_POST · 댓글 — **탐지에 API 를 쓸 수 없다.**
#
# 🔴 `api_create_unless` 를 쓰려다 두 번 막혔고, 둘 다 조용히 중복을 만드는 모양이다:
#   · FAN_POST 의 탐지 후보는 피드뿐인데 피드는 **팔로우 기반**이라 자기 글이 안 뜬다
#     (자기 자신을 팔로우하지 않는 한). 마커는 영원히 미스 → 매 실행마다 새 글.
#   · 댓글에는 **목록 엔드포인트가 아예 없다**(CommentController = POST + DELETE).
#     탐지 GET 이 404 를 내면 `api_create_unless` 는 그대로 생성으로 떨어진다.
# 그래서 탐지는 `dbquery`(읽기 전용 — `--why` 가 필요 없다. 아무것도 쓰지 않는다)로
# 한다. 쓰기는 그대로 API 다 — 게이트가 막는 것은 직접-DB **쓰기**이지 검증 조회가 아니다.
seed_create_unless_row() { # <라벨> <db> <count-sql> <url> <body>
  local label="$1" db="$2" sql="$3" url="$4" body="$5" n
  n="$(dbquery fan-platform-postgres psql "$db" fanplatform '' "$sql")"
  n="$(printf '%s' "${n:-}" | tr -d '[:space:]')"
  if [ -z "$n" ]; then
    seed_fail "$label — 존재 여부 조회가 아무 값도 내지 않았습니다(0건이 아니라 계측 실패다)"
    return 1
  fi
  if [ "$n" != "0" ]; then
    SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  $label"
    return 0
  fi
  api_create "$label" "$url" "$body"
}

FAN_POST_TITLE='첫 콘서트 후기'
seed_create_unless_row "FAN_POST($FAN_POST_TITLE)" fanplatform_community \
  "SELECT count(*) FROM posts WHERE tenant_id='$TENANT' AND author_account_id='$DEMO_SUB' AND post_type='FAN_POST' AND title='$FAN_POST_TITLE'" \
  "$GW/api/v1/community/posts" \
  "{\"postType\":\"FAN_POST\",\"visibility\":\"PUBLIC\",\"title\":\"$FAN_POST_TITLE\",\"body\":\"어제 다녀왔습니다. 앙코르 무대가 정말 좋았어요. 다음에도 꼭 갈 겁니다.\"}"

# 댓글 · 리액션 — PUBLIC 글에만 단다. MEMBERS_ONLY/PREMIUM 은 구독 전이라 막혀 있고,
# 막혀 있는 것이 맞다(그 사실이 곧 가시성 시연의 사전 상태다).
#
# 🔴 `$POST_PUB` 는 이제 2번이 DB 에서 되찾은 값이다(리터럴이 아니다). 비어 있으면
# 건너뛴다 — 빈 값으로 URL 을 만들면 `/posts//comments` 가 404 를 내고, 그것을
# "댓글 실패" 로 보고하는 순간 원인(2번이 글을 못 만들었다)이 증상 뒤로 숨는다.
if [ -n "$POST_PUB" ]; then
  COMMENT_BODY='새 싱글 너무 좋아요. 밤에 듣기 딱입니다.'
  seed_create_unless_row '댓글(PUBLIC 글)' fanplatform_community \
    "SELECT count(*) FROM comments WHERE tenant_id='$TENANT' AND post_id='$POST_PUB' AND author_account_id='$DEMO_SUB' AND deleted_at IS NULL" \
    "$GW/api/v1/community/posts/$POST_PUB/comments" "{\"body\":\"$COMMENT_BODY\"}"

  # 리액션은 PUT upsert 라 본디 멱등이다.
  if http PUT "$GW/api/v1/community/posts/$POST_PUB/reactions" '{"reactionType":"LOVE"}'; then
    seed_log "리액션 LOVE (PUBLIC 글)"
  else
    seed_fail "리액션 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:160}"
  fi
else
  seed_warn "PUBLIC ARTIST_POST 가 없어 댓글·리액션을 건너뜁니다(위 실패가 원인입니다)"
fi

# 멤버십 구독 — MEMBERS_ONLY.
#
# 🔴 **PREMIUM 을 구독하지 않는 것이 요점이다.** PREMIUM 이면 세 글이 전부 열려
# 게이팅을 검증할 수 없다. MEMBERS_ONLY 여야 PUBLIC ✓ / MEMBERS_ONLY ✓ / PREMIUM ✗
# 로 **갈린다**(AC-3). 데모에서 업그레이드를 시연할 여지도 남는다.
#
# 🔴 Idempotency-Key 를 **완전히 고정하면 안 된다** — 이것도 실측으로 갈렸다.
# `SubscribeUseCase` 는 같은 키 + 같은 페이로드를 만나면 **저장된 결과를 재생**한다
# (재인증도, 새 행도 없다). 그래서 한 번 해지된 뒤 같은 키로 다시 구독하면 응답은
# 201 인데 돌아오는 것은 **그 해지된 멤버십**이다 — 시드는 초록, 게이트는 닫힌 채.
# 그렇다고 매번 새 키면 2회차 실행이 구독을 또 만든다.
#
# 그래서 키를 **지금까지 보유한 멤버십 개수**로 세대화한다. 이 분기는 애초에
# "유효한 구독이 없을 때" 만 도달하고, 그때 개수는 새 구독이 필요한 시점에만 늘어난다
# ⇒ 같은 상태에서는 같은 키(멱등), 새 세대가 필요할 때만 새 키.
# paymentId 는 목 PG(기본 프로파일 — `portone` 프로파일이 아닐 때)가 승인한다.
#
# 🔴 술어는 `"tier":"MEMBERS_ONLY"` 가 **아니다.** 첫 판이 그랬는데, 해지된 멤버십도
# 목록에 남으므로(`status=CANCELED`, `active=false`) 한 번 해지하면 시드가 영원히
# "이미 있음" 으로 건너뛰어 게이트가 닫힌 채 굳는다(실측). 물어야 할 것은 티어의
# **존재**가 아니라 **지금 유효한 구독이 있는가** 다. `{` 로 잘라 멤버십 객체 하나를
# 한 줄로 만든 뒤 `"active":true` 를 본다(객체 안에 중첩 객체가 없다 — 실측).
# PREMIUM 을 이미 들고 있어도 이 술어가 참이므로 등급을 낮추지 않는다.
if http GET "$GW/api/v1/memberships" \
   && printf '%s' "$SEED_LAST_BODY" | tr '{' '\n' | grep -q '"active":true'; then
  SEED_EXISTING=$((SEED_EXISTING + 1)); seed_log "존재  멤버십(유효한 구독 보유)"
else
  # 🔴 `grep -c` 는 **줄** 수다 — 응답이 한 줄이라 멤버십이 몇 개든 1 이 나온다.
  # 세어야 하는 것은 출현 횟수이므로 `-o` + `wc -l` 이어야 한다.
  MEMBERSHIP_GEN="$(printf '%s' "$SEED_LAST_BODY" | grep -o '"membershipId"' | wc -l | tr -d '[:space:]')"
  MEMBERSHIP_GEN="${MEMBERSHIP_GEN:-0}"
  # `gen` 접두사는 장식이 아니다 — 세대 번호만 쓰면 과거에 다른 규칙으로 쓰인 같은
  # 모양의 키와 충돌해 그 옛 결과를 재생한다(개발 중 실측: 고정 키
  # `…-membership-1` 이 세대 1 과 겹쳐 해지된 멤버십이 201 로 돌아왔다).
  MEMBERSHIP_KEY="demo-seed-fan-membership-gen${MEMBERSHIP_GEN}"
  if http POST "$GW/api/v1/memberships" \
       "{\"tier\":\"MEMBERS_ONLY\",\"planMonths\":1,\"paymentId\":\"$MEMBERSHIP_KEY\"}" \
       -H "Idempotency-Key: $MEMBERSHIP_KEY"; then
    # 🔴 2xx 를 결과로 믿지 않는다. `SubscribeUseCase` 는 같은 키를 만나면 **저장된
    # 멤버십을 그대로 재생**하므로, 이미 해지된 것을 되돌려 받고도 응답은 201 이다.
    # 물어야 할 것은 "요청이 성공했나" 가 아니라 **"지금 유효한 구독이 있나"** 다.
    if http GET "$GW/api/v1/memberships" \
       && printf '%s' "$SEED_LAST_BODY" | tr '{' '\n' | grep -q '"active":true'; then
      SEED_CREATED=$((SEED_CREATED + 1)); seed_log "생성  멤버십(MEMBERS_ONLY, 1개월, key=$MEMBERSHIP_KEY)"
    else
      seed_fail "멤버십 구독이 2xx 를 냈지만 유효한 구독이 생기지 않았습니다(키 $MEMBERSHIP_KEY 가 과거 결과를 재생했을 수 있습니다) — 멤버십 전용 화면이 잠긴 채 남습니다"
    fi
  else
    seed_fail "멤버십 구독 실패 — HTTP $SEED_LAST_STATUS ${SEED_LAST_BODY:0:200}"
  fi
fi

# 알림은 **이벤트로** 온다(membership.subscribed → notification-service → WELCOME).
# 직접 넣지 않는다 — 넣으면 컨슈머가 죽어 있어도 화면이 차서 배선 결함을 가린다.
# 대신 도착을 기다리고, 안 오면 그 사실을 남긴다.
for _ in $(seq 1 12); do
  http GET "$GW/api/v1/notifications" && \
    printf '%s' "$SEED_LAST_BODY" | grep -q '"type":"WELCOME"' && break
  sleep 5
done
if printf '%s' "$SEED_LAST_BODY" | grep -q '"type":"WELCOME"'; then
  seed_log "WELCOME 알림 도착 확인 (이벤트 경로 정상)"
else
  seed_warn "60초 안에 WELCOME 알림이 오지 않았습니다 — 알림 화면이 빕니다(membership→notification 이벤트 경로를 의심하라)"
fi
SEED_TOKEN=""

seed_summary
