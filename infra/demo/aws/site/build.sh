#!/usr/bin/env bash
# =============================================================================
# infra/demo/aws/site/build.sh — Vercel 빌드 (TASK-MONO-557)
# =============================================================================
# 론처 페이지를 `public/` 로 조립한다. 하는 일은 두 가지뿐이다:
#   1. index.html 복사
#   2. `config.js` 를 **환경변수에서** 생성
#
# -----------------------------------------------------------------------------
# 🔴 왜 API 주소를 커밋하지 않는가
# -----------------------------------------------------------------------------
# `index.html` 에는 예전에 API 주소가 리터럴로 박혀 있었고, 재생성마다 썩었다
# (그 파일의 주석이 그것을 *"결함 2"* 라고 이름 붙여 두었다 — TASK-MONO-389).
# 예전에는 CloudFront 판이 있었고, terraform 이 apply 때 **자기 상태에서** `config.js` 를
# 렌더해 S3 에 넣었다. Vercel 에는 terraform 이 쓸 자리가 없으므로 이쪽은 **같은 값을 빌드
# 환경변수로** 받는다. 리터럴을 다시 커밋해서 해결하지 말 것 — 원래 고침이 무효가 된다.
#
# 🔴 **TASK-MONO-579 이후 이 파일이 유일한 생산자다**(CloudFront/S3 사본 폐기, ADR-MONO-067 D3).
#    그래서 잃은 성질이 하나 있다: terraform 판은 주소를 **자기 상태에서** 만들었으므로
#    *"배포된 페이지가 자기 API 와 어긋나는 것"* 이 표현 불가능했다. 환경변수 판에는 그
#    보장이 없다 — `DEMO_API_BASE` 가 **없으면** 아래에서 빌드를 죽이지만, **낡았으면**
#    아무도 모른다. API 를 재생성하면 Vercel 의 그 변수를 사람이 고쳐야 한다.
#
# -----------------------------------------------------------------------------
# 🔴 값이 없으면 조용히 넘어가지 않는다
# -----------------------------------------------------------------------------
# 빈 `config.js` 를 내보내면 Vercel 빌드는 성공하고 페이지는 200 을 주며 **아무 버튼도
# 동작하지 않는다**. 그 상태는 "배포됐다" 로 보고되므로 아무도 안 본다. 여기서 죽는 편이
# 낫다. (페이지 자신도 `window.DEMO_API_BASE` 부재에 크게 실패하도록 짜여 있다 —
# `index.html:85-91`. 이 스크립트는 그 가드의 빌드 측 짝이다.)
#
# -----------------------------------------------------------------------------
# 🔴 왜 `vercel.json` 에 `installCommand` 가 있는가 — 그리고 왜 그 설명이 **여기** 있는가
# -----------------------------------------------------------------------------
# 없으면 Vercel 이 저장소 **루트의 pnpm-lock.yaml** 을 찾아 올라가 monorepo 전체에
# `pnpm install` 을 시도한다(2026-08-19 실측: 빌드 로그가 `Detected pnpm-lock.yaml 9` 로
# 시작했다). 이 페이지는 의존성이 **0개**인 정적 HTML 한 장이다. Root Directory 를 site/ 로
# 지정해도 패키지 매니저 **탐지는 상위로 올라간다.**
#
# 🔴🔴 그 설명을 `vercel.json` 안에 `"//installCommand"` 키로 넣었다가 **배포를 깼다.**
# JSON 에는 주석이 없고, **Vercel 의 vercel.json 은 스키마가 엄격해 모르는 최상위 키를
# 거부한다.** 시각이 갈랐다: `45bdca743` **성공**(07:54Z) → 그 키를 넣은 뒤 `ea9d5e79c`
# (08:18Z)·`2679b8e41`(08:27Z) **연속 실패**.
# 🔵 그리고 사이트는 마지막 성공 배포가 계속 서빙해서 **겉으로는 멀쩡했다** —
# **배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다.** URL 만 찔러서는 안 보인다.
# ⇒ **설정 파일에 설명을 끼워 넣지 말 것.** 설명의 집은 이 스크립트다.
#
# -----------------------------------------------------------------------------
# Vercel 배선 — `kanggle-portfolio` (TASK-MONO-562)
# -----------------------------------------------------------------------------
#   Vercel 프로젝트   kanggle-portfolio  (= 방문자가 여는 정문. CORS `allowed_origins` 가
#                     이 오리진 하나만 허용한다 — CloudFront 사본은 버튼이 막힌다)
#   Root Directory    infra/demo/aws/site  (이 디렉터리)
#   ignoreCommand     scripts/vercel-should-build.sh 에 이 디렉터리 pathspec 을 넘긴다
#
# 저장소에 Vercel 프로젝트가 **둘**이라(여기 + `kanggle-fan`) 커밋 하나가 배포 **둘**을 굽고,
# 문서 전용 PR 도 예외가 아니었다 ⇒ 무료 플랜 한도. 판정 규약과 fail-open 설계는
# `scripts/vercel-should-build.sh` 헤더에 있다.
#
# -----------------------------------------------------------------------------
# 🔴🔴 `build-info.json` — 배포가 낡았다는 사실이 보이게 하는 유일한 수단
# -----------------------------------------------------------------------------
# rate limit 이든 스키마 오류든, **배포가 실패해도 사이트는 마지막 성공 판을 계속 서빙한다.**
# 그 상태에서 URL 은 200 이고 우리 쪽 증거는 "머지됨 + main 초록" 이라 **아무도 안 본다.**
# 2026-08-21 실측: 론처가 `TASK-MONO-561` 판(08-19)을 서빙하는 동안 `TASK-MONO-560` 은
# 머지된 지 하루가 지나도 방문자에게 도달하지 않았다.
#
# ⇒ 서빙 중인 페이지가 **자기가 어느 커밋에서 나왔는지** 말하게 한다.
# 🔴 `config.js` 가 아니라 **별도 파일**에 쓰는 이유: 원래는 `config.js` 가 terraform 의
# `aws_s3_object.config` 와 **바이트 모양이 같아야** 했다(두 배포 경로가 다른 모양을 내면
# 한쪽에서만 되는 상태가 만들어진다). 🔵 `TASK-MONO-579` 로 배포 경로가 하나가 되어 그
# 불변식은 사라졌지만, **분리는 그대로 둔다** — `config.js` 는 브라우저가 매번 읽는 설정이고
# `build-info.json` 은 판정자만 읽는 메타다. 한 파일에 섞으면 캐시 정책이 하나로 묶인다.
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/public"

if [ -z "${DEMO_API_BASE:-}" ]; then
  cat >&2 <<'EOF'
[site/build] ✗ DEMO_API_BASE 가 설정되지 않았습니다.

  이 값은 제어 API 의 베이스 URL 입니다(예: https://xxxx.execute-api.<region>.amazonaws.com).
  terraform 출력에서 가져오세요:

      terraform -chdir=infra/demo/aws/terraform output -raw api_base_url

  Vercel: Project Settings → Environment Variables → DEMO_API_BASE

  🔴 리터럴을 index.html 에 박아 우회하지 마세요 — 재생성마다 썩습니다(결함 2).
EOF
  exit 1
fi

# 🔴 값 검증. 빈 문자열만 막으면 오타(`http://`, 끝 슬래시, 따옴표 포함)가 그대로 통과해
# 브라우저에서만 깨진다 — 그 실패는 "API 가 죽었다" 처럼 보인다.
case "$DEMO_API_BASE" in
  https://*) ;;
  *) echo "[site/build] ✗ DEMO_API_BASE 는 https:// 로 시작해야 합니다: '$DEMO_API_BASE'" >&2; exit 1 ;;
esac
case "$DEMO_API_BASE" in
  */) echo "[site/build] ✗ DEMO_API_BASE 끝의 슬래시를 빼세요 (페이지가 경로를 이어 붙입니다): '$DEMO_API_BASE'" >&2; exit 1 ;;
esac

rm -rf "$OUT"
mkdir -p "$OUT"
cp "$HERE/index.html" "$OUT/index.html"

# 🔵 이 한 줄의 모양은 예전 terraform 판(`aws_s3_object.config`)과 맞춰 둔 것이다. 그 사본은
# `TASK-MONO-579` 로 폐기됐고 지금은 여기가 유일한 생산자다 — 모양을 바꿀 이유는 없으므로
# 그대로 둔다(`index.html` 이 `window.DEMO_API_BASE` 를 읽는 계약은 변하지 않았다).
printf 'window.DEMO_API_BASE = "%s";\n' "$DEMO_API_BASE" > "$OUT/config.js"

# --- build-info.json — 이 배포가 어느 커밋에서 나왔는가 (TASK-MONO-562 AC-3) ----
# Vercel 이 주는 값을 쓰고, 없으면(로컬 실행) git 에 묻는다. 🔴 둘 다 없으면 `unknown` 을
# 쓰되 **빌드를 죽이지는 않는다** — 신선도 판정은 `check-launcher-fresh.sh` 가 하고, 그쪽이
# `unknown` 을 **통과가 아니라 판정 불가**로 다룬다. 여기서 죽이면 SHA 를 모르는 정당한
# 환경(로컬 미리보기)에서 페이지를 못 만든다.
BUILD_SHA="${VERCEL_GIT_COMMIT_SHA:-}"
if [ -z "$BUILD_SHA" ]; then
  BUILD_SHA="$(git -C "$HERE" rev-parse HEAD 2>/dev/null || echo unknown)"
fi
BUILD_REF="${VERCEL_GIT_COMMIT_REF:-$(git -C "$HERE" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)}"
INDEX_MD5="$(md5sum "$OUT/index.html" 2>/dev/null | cut -d' ' -f1)"
[ -n "$INDEX_MD5" ] || INDEX_MD5=unknown

printf '{"commit":"%s","ref":"%s","index_md5":"%s"}\n' \
  "$BUILD_SHA" "$BUILD_REF" "$INDEX_MD5" > "$OUT/build-info.json"

echo "[site/build] ✔ public/ 조립 완료 — DEMO_API_BASE=$DEMO_API_BASE"
echo "[site/build] ✔ build-info.json — commit=$BUILD_SHA ref=$BUILD_REF index_md5=$INDEX_MD5"
