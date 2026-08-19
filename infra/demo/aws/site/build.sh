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
# 지금 CloudFront 판은 terraform 이 apply 때 자기 상태에서 `config.js` 를 렌더해 S3 에
# 넣는다. Vercel 에는 terraform 이 쓸 수 있는 자리가 없으므로, **같은 값을 빌드 환경변수로
# 받는다.** 리터럴을 다시 커밋해서 해결하지 말 것 — 그러면 원래 고침이 무효가 된다.
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

# terraform 의 `aws_s3_object.config` 와 **같은 모양**을 낸다. 두 배포 경로가 다른 모양을
# 내면 한쪽에서만 되는 상태가 만들어지고, 그건 진단이 가장 오래 걸리는 종류다.
printf 'window.DEMO_API_BASE = "%s";\n' "$DEMO_API_BASE" > "$OUT/config.js"

echo "[site/build] ✔ public/ 조립 완료 — DEMO_API_BASE=$DEMO_API_BASE"
