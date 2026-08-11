#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed/seed-fan.sh — 팬 플랫폼 도메인 데이터 시드
# =============================================================================
# TASK-MONO-509 (MONO-506 의 S2 슬라이스).
#
# 두 단계다. 순서가 곧 도메인 규칙이다:
#
#   1) 직접-DB — 아티스트 · 그룹 · 팬덤 · ARTIST_POST 3종 가시성
#   2) 소비자 토큰(API) — 팔로우 · FAN_POST · 댓글 · 리액션 · 멤버십 구독
#
# 🔴 왜 1번이 API 가 아닌가 — 두 개의 독립된 이유가 있고 둘 다 실측했다
# -----------------------------------------------------------------------------
# (가) **팬 도메인에는 운영자 평면이 없다.** artist-service 의 SecurityConfig 는
#      `ADMIN_ROLES = {ADMIN, OPERATOR, SUPER_ADMIN, FAN_OPERATOR}` 로 쓰기를 열고,
#      community-service 의 `ActorContext.isOperator()` 도 `FAN_OPERATOR` 를 받고,
#      iam 의 `OperatorRoleDerivation` 에는 `case "fan", "fan-platform" -> FAN_OPERATOR`
#      arm 까지 있다. 그런데 **그 arm 에 도달하는 테넌트가 하나도 없다**:
#
#        · `demo-corp` 구독 = [ecommerce, wms, scm, erp, finance] — fan 없음
#          ⇒ assume 해도 roles 에 FAN_OPERATOR 가 없고, 게다가 팬 게이트웨이는
#            `required-tenant-id: fan-platform` 이라 **토큰 자체가 거절된다**
#            (실측 2026-08-05: `GET /api/v1/artists` → 403
#             `{"code":"TENANT_FORBIDDEN","message":"tenant_id 'demo-corp' is not allowed"}`)
#        · `fan-platform` 테넌트를 assume 하려 하면 IAM 이 거절한다
#          (실측: `{"error":"invalid_grant","error_description":"operator is not
#           assigned to the selected tenant"}` — R__seed_demo_operator.sql 에
#           `fan-platform` 배정 행이 없고, 있더라도 `tenant_domain_subscription`
#           에 (fan-platform, fan) 행이 없어 FAN_OPERATOR 는 파생되지 않는다)
#
#      즉 `FAN_OPERATOR` 는 **IdP 가 발급할 수 없는 역할**이다. 데모 사용자
#      토큰(roles=["FAN"])으로 실측한 결과도 같은 그림이다:
#
#        POST /api/v1/artists         403 FORBIDDEN
#        POST /api/v1/artist-groups   403 FORBIDDEN
#        POST /api/v1/fandoms/{id}    403 FORBIDDEN
#        POST /api/v1/community/posts 403 PERMISSION_DENIED
#                                     "ARTIST role required to publish ARTIST_POST"
#
#      → TASK-MONO-512 (AC-8).
#
# (나) **설령 (가) 가 고쳐져도 ARTIST_POST 는 API 로 못 넣는다.** 피드는
#      `posts.author_account_id IN (SELECT follows.artist_account_id ...)` 로 잇고,
#      프런트는 `<FollowButton artistAccountId={artist.id}>` 로 **아티스트 엔티티 id**
#      를 팔로우 대상으로 넘긴다(artists 테이블에 account_id 컬럼은 없다 — 실측).
#      그런데 `PublishPostUseCase` 는 `authorAccountId = actor.accountId()`(JWT sub)
#      로 고정한다. 어떤 실제 호출자도 자기 sub 이 아닌 id 로 글을 쓸 수 없으므로,
#      운영자든 ARTIST 역할 계정이든 그가 쓴 글은 **그 아티스트를 팔로우한 팬의
#      피드에 뜨지 않는다.** → TASK-FAN-BE-045 (AC-8).
#
# 🔴 왜 데모 사용자가 게이팅 대상 글을 쓰면 안 되는가
# -----------------------------------------------------------------------------
# `PostAccessGuard.ensureVisibilityAccessible` 와 `GetFeedUseCase.isLocked` 는 둘 다
# **`actor.owns(authorAccountId)` 면 무조건 통과**시킨다. 데모 계정이 MEMBERS_ONLY /
# PREMIUM 글의 작성자면 멤버십과 무관하게 전부 열려 AC-3 이 **공허하게 초록**이 된다.
# 그래서 게이팅 대상 글은 반드시 **데모 계정이 아닌 id**(= 아티스트 엔티티 id)로 쓴다.
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

# 고정 id — 2회차 실행이 같은 것을 또 만들지 않게 하는 근거다(모든 INSERT 가
# `WHERE NOT EXISTS` 로 자기 id 를 검사한다). 랜덤이면 AC-5 가 성립할 수 없다.
ARTIST_A="0199de80-0000-7000-8000-00000000a001"   # 팔로우 대상 · 3종 가시성 글의 저자
ARTIST_B="0199de80-0000-7000-8000-00000000a002"   # 디렉터리가 한 줄이 아니게 하는 두 번째
ARTIST_C="0199de80-0000-7000-8000-00000000a003"   # 그룹 멤버 (artist_type=GROUP_MEMBER)
GROUP_1="0199de80-0000-7000-8000-00000000b001"
POST_PUB="0199de81-0000-7000-8000-00000000c001"
POST_MEM="0199de81-0000-7000-8000-00000000c002"
POST_PRE="0199de81-0000-7000-8000-00000000c003"
POST_B="0199de81-0000-7000-8000-00000000c004"

# -----------------------------------------------------------------------------
# 1. 직접-DB — 아티스트 · 그룹 · 팬덤 · ARTIST_POST
# -----------------------------------------------------------------------------
# 시간은 **고정 리터럴**이다. NOW() 면 2회차 실행에서 published_at 이 바뀌어 피드
# 정렬이 흔들리고, 무엇보다 "행 수가 수렴한다" 를 넘어 "상태가 수렴한다" 가 깨진다.
if container_up fan-platform-postgres; then
  dbexec --why "팬 도메인에는 발급 가능한 운영자 역할이 없다 — FAN_OPERATOR 를 파생시키는 tenant_domain_subscription(*, 'fan') 행이 존재하지 않고, demo-corp assume 토큰은 팬 게이트웨이의 required-tenant-id=fan-platform 에 막힌다(실측 403 TENANT_FORBIDDEN). POST /api/v1/artists 는 hasAnyRole(ADMIN,OPERATOR,SUPER_ADMIN,FAN_OPERATOR) 이므로 어느 쪽으로도 API 로 만들 수 없다 — 남은 차단 사유는 **역할 발급 경로의 부재 하나뿐**이고 그것이 TASK-MONO-512 다. TASK-FAN-BE-045 가 담당한 절반(아티스트에 계정이 없어 저자 id 를 낼 수 없음)은 artists.account_id 로 해소됐다" \
    fan-platform-postgres psql fanplatform_artist fanplatform <<SQL
-- account_id 는 **엔티티 id 와 동일**하게 넣는다(TASK-FAN-BE-045 V3 의 항등 백필과 같은 값).
-- 이유: 이 시드는 조인의 양쪽 모두에 아티스트 엔티티 id 를 쓴다 — follows 는 아래 API 호출로,
-- posts.author_account_id 는 직접-DB 로. 다른 값을 넣으면 팔로우 검증(AC-6)이 이 시드 자신의
-- 팔로우 호출을 거절한다. 🔴 이 값은 실재하는 IAM subject 가 아니다 — 아무도 이 계정으로
-- 로그인할 수 없고, 그래서 아래 ARTIST_POST 블록이 아직 직접-DB 인 것이다(TASK-MONO-512).
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

  # ARTIST_POST 3종 가시성 + post_status_history.
  #
  # 🔴 `PUBLISHED` 만으로는 부족하다 — `published_at` 이 NULL 이면 피드 정렬
  # (ORDER BY p.publishedAt DESC)에서 사라진다. 이 저장소의 대표 실패 모드다.
  #
  # history 행을 함께 넣는 이유: 도메인은 PUBLISH 시 반드시 이력을 남긴다
  # (PublishPostUseCase). 이력 없는 PUBLISHED 는 도메인이 만들 수 없는 상태이고,
  # 시드가 그런 상태를 보여주면 데모가 거짓말을 한다.
  dbexec --why "저자는 artists.account_id 여야 피드(posts.author_account_id ⋈ follows.artist_account_id)가 성립하고 TASK-FAN-BE-045 가 그 컬럼을 만들었지만, 발행하려면 그 계정으로 **로그인**해 ARTIST 역할을 든 토큰이 있어야 한다. 데모 아티스트의 account_id 는 항등 백필값이라 실재하는 IAM subject 가 아니고, ARTIST 역할 자체도 발급 경로가 없다 — 둘 다 TASK-MONO-512. 게다가 데모 계정이 저자가 되면 actor.owns() 로 게이팅이 통째로 우회돼 AC-3 이 공허해진다. ⇒ MONO-512 가 풀릴 때까지 직접-DB 유지(TASK-FAN-BE-045 AC-5 의 명시적 판정)" \
    fan-platform-postgres psql fanplatform_community fanplatform <<SQL
INSERT INTO posts (id, tenant_id, author_account_id, post_type, visibility, status, title, body, published_at, created_at, updated_at, version)
SELECT '$POST_PUB', '$TENANT', '$ARTIST_A', 'ARTIST_POST', 'PUBLIC', 'PUBLISHED',
       '새 싱글 「밤의 끝」 발매 안내',
       E'안녕하세요, 루미입니다.\n\n오랜만에 새 싱글로 인사드립니다. 「밤의 끝」은 지난 겨울에 쓴 곡이에요.\n모든 분들이 들으실 수 있도록 전체 공개로 올립니다.',
       TIMESTAMPTZ '2026-02-01 10:00:00+00', TIMESTAMPTZ '2026-02-01 10:00:00+00', TIMESTAMPTZ '2026-02-01 10:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM posts WHERE id = '$POST_PUB');

INSERT INTO posts (id, tenant_id, author_account_id, post_type, visibility, status, title, body, published_at, created_at, updated_at, version)
SELECT '$POST_MEM', '$TENANT', '$ARTIST_A', 'ARTIST_POST', 'MEMBERS_ONLY', 'PUBLISHED',
       '멤버십 전용 — 작업실 이야기',
       E'멤버십 가입해 주신 분들께만 남깁니다.\n\n이번 앨범 작업을 하면서 세 번을 갈아엎었어요. 처음 데모와 지금 버전은 코드 진행부터 다릅니다.\n다음 주에는 미공개 데모 음원도 여기에 올릴게요.',
       TIMESTAMPTZ '2026-02-05 10:00:00+00', TIMESTAMPTZ '2026-02-05 10:00:00+00', TIMESTAMPTZ '2026-02-05 10:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM posts WHERE id = '$POST_MEM');

INSERT INTO posts (id, tenant_id, author_account_id, post_type, visibility, status, title, body, published_at, created_at, updated_at, version)
SELECT '$POST_PRE', '$TENANT', '$ARTIST_A', 'ARTIST_POST', 'PREMIUM', 'PUBLISHED',
       '프리미엄 전용 — 팬미팅 선예매 안내',
       E'프리미엄 멤버십 전용 안내입니다.\n\n3월 팬미팅 선예매 링크와 좌석 배치도를 먼저 공유드립니다. 일반 예매보다 48시간 빠릅니다.',
       TIMESTAMPTZ '2026-02-10 10:00:00+00', TIMESTAMPTZ '2026-02-10 10:00:00+00', TIMESTAMPTZ '2026-02-10 10:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM posts WHERE id = '$POST_PRE');

INSERT INTO posts (id, tenant_id, author_account_id, post_type, visibility, status, title, body, published_at, created_at, updated_at, version)
SELECT '$POST_B', '$TENANT', '$ARTIST_B', 'ARTIST_POST', 'PUBLIC', 'PUBLISHED',
       '프로듀싱 노트를 시작합니다',
       E'노아입니다. 앞으로 작업 과정을 짧게 기록해 두려 합니다.\n첫 글은 마이크 프리앰프 이야기부터.',
       TIMESTAMPTZ '2026-02-03 10:00:00+00', TIMESTAMPTZ '2026-02-03 10:00:00+00', TIMESTAMPTZ '2026-02-03 10:00:00+00', 0
WHERE NOT EXISTS (SELECT 1 FROM posts WHERE id = '$POST_B');

INSERT INTO post_status_history (post_id, tenant_id, from_status, to_status, actor_type, actor_account_id, occurred_at)
SELECT p.id, p.tenant_id, 'DRAFT', 'PUBLISHED', 'AUTHOR', p.author_account_id, p.published_at
FROM posts p
WHERE p.id IN ('$POST_PUB', '$POST_MEM', '$POST_PRE', '$POST_B')
  AND NOT EXISTS (SELECT 1 FROM post_status_history h WHERE h.post_id = p.id);
SQL
  if [ $? -eq 0 ]; then seed_log "ARTIST_POST 4 (PUBLIC 2 · MEMBERS_ONLY 1 · PREMIUM 1) 준비됨"; else seed_fail "ARTIST_POST INSERT 실패"; fi
else
  seed_fail "fan-platform-postgres 미기동 — 아티스트/게시물을 시드할 수 없습니다"
fi

# -----------------------------------------------------------------------------
# 2. 소비자 토큰 — "내" 데이터. 여기부터는 전부 API 다.
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
# 막혀 있는 것이 맞다(그 사실이 곧 AC-3 의 사전 상태다).
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
