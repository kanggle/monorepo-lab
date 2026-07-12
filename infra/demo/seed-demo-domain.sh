#!/usr/bin/env bash
# =============================================================================
# infra/demo/seed-demo-domain.sh — 데모 도메인을 OIDC 클라이언트에 등록한다
# =============================================================================
# TASK-MONO-358.
#
# 무엇을 고치는가
# -----------------------------------------------------------------------------
# OAuth2 `redirect_uri` 는 **정확 일치**로 검증된다(RFC 6749 §3.1.2.3 / Spring
# Authorization Server). 그런데 이 저장소의 브라우저용 클라이언트들은 콜백 URL 을
# **Flyway 마이그레이션에 리터럴로** 박아 둔다:
#
#   V0015  platform-console-web           ["http://console.local/api/auth/callback", …]
#   V0012  ecommerce-web-store-client     ["http://web.ecommerce.local/api/auth/callback/iam", …]
#   V0011  fan-platform-user-flow-client  ["http://fan-platform.local/api/auth/callback/iam", …]
#
# 온디맨드 데모는 부팅 때마다 **공인 IP 에서 파생된 도메인**(`43-200-71-219.sslip.io`)
# 위에 뜬다. 마이그레이션이 알 수 없는 값이다. 등록되지 않은 redirect_uri 로
# authorize 를 치면 auth-service 는 이렇게 답한다:
#
#     HTTP/1.1 401
#     {"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
#
# 메시지가 원인을 전혀 가리키지 않아(자격증명 문제가 아니다) 오진하기 쉽다. 등록된
# `console.local` 로 같은 요청을 치면 302 가 나오는 것으로 격리했다 — **차이는
# redirect_uri 하나뿐이었다.**
#
# 왜 마이그레이션이 아니라 데모 스크립트인가
# -----------------------------------------------------------------------------
# 도메인이 런타임에야 정해진다. 마이그레이션은 정적이다. 그리고 이건 **데모 토폴로지**
# 관심사지 제품 관심사가 아니다 — `demo.env` / `iam-traefik.override.yml` 과 같은
# 원칙(프로젝트 compose 는 그대로, 데모 배선은 데모 파일이 책임진다).
#
# 어떻게 (하드코딩 없는 치환)
# -----------------------------------------------------------------------------
# 클라이언트 목록을 박지 않는다. **`.local/` 을 포함한 모든 등록 URI 를 찾아
# `.${DEMO_DOMAIN}/` 로 치환한 사본을 덧붙인다.** 새 클라이언트가 `.local` 콜백을
# 들고 추가돼도 이 스크립트는 수정 없이 그것까지 커버한다.
#
#   http://console.local/api/auth/callback
#     → http://console.<DEMO_DOMAIN>/api/auth/callback        (덧붙임, 원본 유지)
#
# 원본을 지우지 않는 이유: 같은 DB 를 로컬 `*.local` 로도 쓸 수 있어야 한다.
#
# 멱등성: 이미 `.${DEMO_DOMAIN}/` 가 들어 있는 행은 건너뛴다(WHERE 절 가드). 반복
# 실행해도 배열이 자라지 않는다.
#
# post_logout_redirect_uris 도 같이 처리한다. 이 값은 `client_settings` JSON 안에
# Jackson default-typing 형태로 들어 있다(V0016/V0021 의 교훈):
#   "settings.client.post-logout-redirect-uris": ["java.util.ArrayList", ["http://…", …]]
# 실제 배열은 `[1]` 이다 — `[0]` 은 타입 태그 문자열이다.
#
# 사용법: demo-up.sh 가 마지막에 호출한다. 단독 실행도 가능.
#   DEMO_DOMAIN=1-2-3-4.sslip.io bash infra/demo/seed-demo-domain.sh
# =============================================================================
set -euo pipefail

DEMO_DOMAIN="${DEMO_DOMAIN:-local}"

# `local` 이면 할 일이 없다 — 마이그레이션이 이미 `.local` 을 등록해 뒀다.
if [ "$DEMO_DOMAIN" = "local" ]; then
  echo "[seed] DEMO_DOMAIN=local — 마이그레이션 시드 그대로 사용, 건너뜀"
  exit 0
fi

MYSQL_CONTAINER="${IAM_MYSQL_CONTAINER:-iam-mysql}"
DB_USER="${AUTH_DB_USERNAME:-auth_user}"
DB_PASS="${AUTH_DB_PASSWORD:-auth_pass}"
DB_NAME="${AUTH_DB_NAME:-auth_db}"

# auth-service 의 Flyway 가 oauth_clients 를 만들고 시드할 때까지 기다린다.
# (demo-up.sh 는 `up -d` 로 끝나므로 여기 도달 시점에 아직 마이그레이션 중일 수 있다.)
echo "[seed] auth_db.oauth_clients 시드 대기 (DEMO_DOMAIN=$DEMO_DOMAIN)"
for i in $(seq 1 60); do
  n=$(docker exec "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -B \
        -e "SELECT COUNT(*) FROM oauth_clients WHERE client_id='platform-console-web';" 2>/dev/null || echo 0)
  [ "${n:-0}" = "1" ] && break
  [ "$i" = "60" ] && { echo "[seed] !!! oauth_clients 시드가 5분 내에 나타나지 않음" >&2; exit 1; }
  sleep 5
done

# JSON_TABLE 로 배열을 펼쳐 `.local/` 인 것만 골라 치환본을 만들고, 원본 배열에
# JSON_MERGE_PRESERVE 로 덧붙인다. 문자열 REPLACE 를 JSON 텍스트 전체에 걸지 않는
# 이유: MySQL 이 JSON 을 재직렬화하며 공백/키순서를 정규화하므로 텍스트 조작은
# 깨지기 쉽다(V0016 의 교훈). 파싱된 트리 위에서만 다룬다.
docker exec -i "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" <<SQL
SET @dom = '.${DEMO_DOMAIN}/';

UPDATE oauth_clients c
JOIN (
  SELECT c2.client_id,
         JSON_ARRAYAGG(REPLACE(jt.uri, '.local/', @dom)) AS extra
  FROM oauth_clients c2,
       JSON_TABLE(c2.redirect_uris, '\$[*]' COLUMNS (uri VARCHAR(512) PATH '\$')) jt
  WHERE jt.uri LIKE '%.local/%'
  GROUP BY c2.client_id
) x ON x.client_id = c.client_id
SET c.redirect_uris = JSON_MERGE_PRESERVE(c.redirect_uris, x.extra)
WHERE CAST(c.redirect_uris AS CHAR) NOT LIKE CONCAT('%', @dom, '%');

UPDATE oauth_clients c
JOIN (
  SELECT c2.client_id,
         JSON_ARRAYAGG(REPLACE(jt.uri, '.local/', @dom)) AS extra
  FROM oauth_clients c2,
       JSON_TABLE(
         JSON_EXTRACT(c2.client_settings, '\$."settings.client.post-logout-redirect-uris"[1]'),
         '\$[*]' COLUMNS (uri VARCHAR(512) PATH '\$')
       ) jt
  WHERE jt.uri LIKE '%.local/%'
  GROUP BY c2.client_id
) x ON x.client_id = c.client_id
SET c.client_settings = JSON_SET(
      c.client_settings,
      '\$."settings.client.post-logout-redirect-uris"[1]',
      JSON_MERGE_PRESERVE(
        JSON_EXTRACT(c.client_settings, '\$."settings.client.post-logout-redirect-uris"[1]'),
        x.extra))
WHERE CAST(c.client_settings AS CHAR) NOT LIKE CONCAT('%', @dom, '%');
SQL

echo "[seed] 등록된 redirect_uri:"
docker exec "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -B \
  -e "SELECT client_id, redirect_uris FROM oauth_clients WHERE JSON_LENGTH(redirect_uris) > 0 ORDER BY client_id;" \
  | sed 's/^/[seed]   /'

# 아무 클라이언트도 데모 도메인을 얻지 못했다면 조용히 성공한 척하지 않는다.
hit=$(docker exec "$MYSQL_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -B \
        -e "SELECT COUNT(*) FROM oauth_clients WHERE CAST(redirect_uris AS CHAR) LIKE '%${DEMO_DOMAIN}%';")
if [ "${hit:-0}" -eq 0 ]; then
  echo "[seed] !!! 치환된 redirect_uri 가 하나도 없다 — 로그인은 실패한다" >&2
  exit 1
fi
echo "[seed] OK — $hit 개 클라이언트가 $DEMO_DOMAIN 콜백을 갖는다"
