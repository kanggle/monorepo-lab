#!/usr/bin/env bash
# =============================================================================
# check-build-context-declarations.sh — Dockerfile 이 **요구**하는 외부 빌드 컨텍스트를
#   그 Dockerfile 을 빌드하는 **모든** compose 서비스가 **공급**하는가 (TASK-MONO-629)
# =============================================================================
# 하나의 계약이 **두 벌에 걸쳐** 있다:
#
#   Dockerfile   COPY --from=<X>            ← 요구 (X 가 스테이지도 이미지도 아닐 때)
#   compose      build.additional_contexts  ← 공급
#
# 🔴 안 주면 BuildKit 은 `<X>` 를 **이미지 이름으로 해석**한다. 그래서 증상이
#    *"pull access denied … docker.io/library/<X>:latest"* 로 나오고, **원인이 배선처럼
#    보이지 않는다.** 그 오독이 이 가드가 태어난 이유다.
#
#   bash scripts/check-build-context-declarations.sh [--root <dir>] [--self-test]
#
#   종료코드  0 = 전부 공급됨   1 = 누락   2 = 판정 불가
#
# -----------------------------------------------------------------------------
# 🔴🔴 왜 새 가드인가 — **`(z26)` 이 이미 이 축을 본다고 적혀 있는데 못 잡았다**
# -----------------------------------------------------------------------------
# `infra/demo/verify-demo-wrapper.sh` 의 `(z26)` 은 «컨텍스트 밖 워크스페이스 의존이
# 이미지 빌드에 전달되는가» 를 묻는다. 옳은 축인데 **두 곳에서 좁았다**:
#
#   ⑴ **첫 매치에서 멈춘다.** 그 프로젝트의 compose 들을 훑다가 선언을 하나 찾으면
#      `break` 한다 ⇒ 「**어떤** compose 가 주는가」를 물었는데 요구는
#      「**모든** compose 가 줘야 한다」이다. `platform-console/docker-compose.yml` 이
#      주고 있었으므로 `docker-compose.e2e.yml` 은 **한 번도 안 봤다.**
#   ⑵ **모집단이 그 프로젝트 디렉터리뿐이다.**
#      `tests/federation-hardening-e2e/docker/…` 는 `projects/platform-console/` 밖에서
#      같은 Dockerfile 을 빌드한다 ⇒ 시야에 **애초에 없었다.**
#
# 그래서 두 낙오가 동시에 살아 있었고, 실측으로 확인됐다(2026-09-05):
#   nightly `Platform Console E2E full-stack`  → 20여 런 연속 실패
#   scheduled `Federation Hardening E2E`       → 09-04 까지 초록, 09-05 부터 실패
#
# 🔵 이 파일은 **모집단을 저장소 전체로** 잡고 **매치되는 서비스를 전부** 본다.
#    (z26) 은 자기 축(«package.json 의 link:/file: 탈출») 을 계속 보되, 이 파일을
#    가리키도록 주석이 갱신됐다 — 구현을 두 벌로 만들면 하나만 고쳐진다.
#
# -----------------------------------------------------------------------------
# 무엇을 «요구» 로 세는가 — 🔴 셋을 구별해야 한다
# -----------------------------------------------------------------------------
# `COPY --from=<X>` 의 `<X>` 는 세 가지일 수 있다:
#   · 같은 파일의 **스테이지**   `FROM node:20 AS builder` → `COPY --from=builder`
#   · **이미지 참조**            `COPY --from=alpine:3.20` (`:` 또는 `/` 를 포함)
#   · **추가 빌드 컨텍스트**     ← 이것만 compose 가 줘야 한다
# 구별을 못 하면 스테이지를 **거짓 고발**한다. 판별 순서: 스테이지 → 이미지꼴 → 나머지.
# =============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELFTEST=0

while [ $# -gt 0 ]; do
  case "$1" in
    --root)      ROOT="$(cd "${2:-}" && pwd)"; shift 2 ;;
    --self-test) SELFTEST=1; shift ;;
    *) echo "알 수 없는 인자: $1" >&2; exit 2 ;;
  esac
done

say() { echo "[build-ctx] $*"; }
SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

# =============================================================================
# --self-test — **주입 픽스처**로 판정 경로를 돌린다
# =============================================================================
# 🔴 커버리지를 «지금 저장소에 그런 파일이 있다» 에 기대지 않는다. 이 패턴을 아무도 안
#    쓰게 되는 날(요구 0건) 그 커버리지는 **조용히 사라진다** — 그리고 하한을 요구 건수에
#    걸면 이번엔 성공이 고장으로 읽힌다. 그래서 판정 경로는 **주입한 세계**에서 돈다.
# 🔴 그리고 **읽기 전에 주입을 단언한다**: 픽스처가 정말 그 상태인가.
if [ "$SELFTEST" -eq 1 ]; then
  say "▶ 자가검사 — 주입 픽스처로 다섯 칸"
  T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

  mk() { mkdir -p "$(dirname "$1")"; cat > "$1"; }

  # 앱 A — 스테이지·이미지참조·외부컨텍스트를 **한 파일에** 섞는다(판별 시험).
  mk "$T/projects/appA/Dockerfile" <<'EOF'
FROM node:20-alpine AS builder
COPY --from=builder /app /app
COPY --from=alpine:3.20 /etc/os-release /tmp/os-release
COPY --from=ctx-a . /vendor/a
EOF
  # 앱 B — 요구 하나.
  mk "$T/projects/appB/Dockerfile" <<'EOF'
FROM node:20-alpine
COPY --from=ctx-b . /vendor/b
EOF
  # 앱 C — 요구가 있는데 **아무 compose 도 안 빌드한다**(AC-4 칸).
  mk "$T/projects/appC/Dockerfile" <<'EOF'
FROM node:20-alpine
COPY --from=ctx-c . /vendor/c
EOF
  # 앱 D — 요구가 **없다**. 두 가지 일을 한다:
  #   ① 열거 하한(Dockerfile ≥ 3)을 픽스처가 늘 넘기게 한다. 🔴 이 하한이 없으면 열거가
  #      깨진 채 초록이 되지만, **하한이 픽스처를 죽이면 칸이 전부 rc=2 가 된다** — 실제로
  #      그랬다(첫 판은 다섯 칸이 전부 2 였고 사유는 판정이 아니라 하한이었다).
  #      하한을 내리지 않고 **모집단을 주입해서** 푼다.
  #   ② 음성 대조군: 요구가 없으므로 어떤 칸에서도 이름이 찍히면 안 된다.
  mk "$T/projects/appD/Dockerfile" <<'EOF'
FROM node:20-alpine AS base
COPY --from=base /app /app
EOF

  mk "$T/projects/docker-compose.yml" <<'EOF'
services:
  a:
    build:
      context: ./appA
      additional_contexts:
        ctx-a: ../vendor/a
      dockerfile: Dockerfile
  b:
    build:
      context: ./appB
      additional_contexts:
        ctx-b: ../vendor/b
      dockerfile: Dockerfile
EOF
  mk "$T/projects/docker-compose.e2e.yml" <<'EOF'
services:
  a:
    build:
      context: ./appA
      additional_contexts:
        ctx-a: ../vendor/a
      dockerfile: Dockerfile
EOF
  mk "$T/tests/docker-compose.fed.yml" <<'EOF'
services:
  a:
    build:
      context: ../projects/appA
      additional_contexts:
        ctx-a: ../vendor/a
      dockerfile: Dockerfile
EOF

  # ---- 칸 ① 정렬된 세계 — 단, appC 가 AC-4 를 발화시키므로 먼저 그것부터 확인한다 ----
  # 🔴 주입 단언: appC 는 요구가 있고, 어떤 compose 도 그것을 안 빌드한다.
  grep -q 'from=ctx-c' "$T/projects/appC/Dockerfile" || { say "✖ 주입 실패: appC 에 요구가 없습니다"; exit 2; }
  grep -rq 'appC' "$T"/projects/docker-compose*.yml "$T"/tests/*.yml 2>/dev/null \
    && { say "✖ 주입 실패: 어떤 compose 가 appC 를 빌드합니다 — 칸④가 무의미해집니다"; exit 2; }
  bash "$SELF" --root "$T" >/dev/null 2>&1; c4=$?
  say "   칸④ 대상 0건(빌드하는 compose 없음) → rc=$c4  (기대 2)"

  # appC 를 치우면 나머지 칸을 잴 수 있다. 🔴 치웠다는 것을 단언한다.
  rm -rf "$T/projects/appC"
  [ ! -e "$T/projects/appC/Dockerfile" ] || { say "✖ 픽스처 정리 실패"; exit 2; }
  bash "$SELF" --root "$T" >/dev/null 2>&1; c1=$?
  say "   칸① 정렬된 세계 → rc=$c1  (기대 0 — 스테이지 'builder' 와 이미지 'alpine:3.20' 을 고발하지 않아야 한다)"

  # ---- 칸 ② 도착경로 ⑴: Dockerfile 이 요구를 늘린다 ----
  before="$(grep -c 'from=' "$T/projects/appB/Dockerfile")"
  printf 'COPY --from=ctx-b2 . /vendor/b2\n' >> "$T/projects/appB/Dockerfile"
  after="$(grep -c 'from=' "$T/projects/appB/Dockerfile")"
  [ "$after" -eq $((before + 1)) ] || { say "✖ 주입 실패: 요구가 안 늘었습니다 ($before → $after)"; exit 2; }
  grep -q 'ctx-b2' "$T/projects/docker-compose.yml" && { say "✖ 주입 실패: compose 가 이미 ctx-b2 를 줍니다"; exit 2; }
  bash "$SELF" --root "$T" >/dev/null 2>&1; c2=$?
  say "   칸② Dockerfile 이 요구를 늘림(compose 미추종) → rc=$c2  (기대 1)"
  sed -i '/ctx-b2/d' "$T/projects/appB/Dockerfile"
  grep -q 'ctx-b2' "$T/projects/appB/Dockerfile" && { say "✖ 되돌리기 실패"; exit 2; }

  # ---- 칸 ③ 도착경로 ⑵: **새 compose** 가 기존 Dockerfile 을 빌드한다 ----
  # 🔴 이것이 (z26) 이 못 본 축이다: 선언을 이미 «어딘가» 가 하고 있어도, 새로 생긴
  #    쪽이 안 하면 그쪽 빌드가 죽는다.
  mk "$T/tests/docker-compose.newcomer.yml" <<'EOF'
services:
  a:
    build:
      context: ../projects/appA
      dockerfile: Dockerfile
EOF
  [ -f "$T/tests/docker-compose.newcomer.yml" ] || { say "✖ 주입 실패: 새 compose 가 없습니다"; exit 2; }
  grep -q 'additional_contexts' "$T/tests/docker-compose.newcomer.yml" && { say "✖ 주입 실패: 새 compose 가 이미 줍니다"; exit 2; }
  grep -q 'additional_contexts' "$T/projects/docker-compose.yml" || { say "✖ 대조군 붕괴: 원래 compose 는 여전히 줘야 합니다"; exit 2; }
  bash "$SELF" --root "$T" >/dev/null 2>&1; c3=$?
  say "   칸③ 새 compose 가 안 줌(다른 compose 는 주는 중) → rc=$c3  (기대 1)"
  rm -f "$T/tests/docker-compose.newcomer.yml"

  # ---- 칸 ⑤ 축약형 build: <path> 도 본다 ----
  mk "$T/tests/docker-compose.short.yml" <<'EOF'
services:
  a:
    build: ../projects/appA
EOF
  bash "$SELF" --root "$T" >/dev/null 2>&1; c5=$?
  say "   칸⑤ 축약형 build 는 컨텍스트를 못 주므로 위반 → rc=$c5  (기대 1)"
  rm -f "$T/tests/docker-compose.short.yml"

  say "── 자가검사 결과: ①=$c1 ②=$c2 ③=$c3 ④=$c4 ⑤=$c5   (기대 0 / 1 / 1 / 2 / 1)"
  if [ "$c1" -ne 0 ] || [ "$c2" -ne 1 ] || [ "$c3" -ne 1 ] || [ "$c4" -ne 2 ] || [ "$c5" -ne 1 ]; then
    say "✖ 판정자가 다섯 칸을 가르지 못했습니다."
    say "  · ①≠0 → 스테이지나 이미지 참조를 **거짓 고발**하고 있습니다(판별 순서를 보세요)."
    say "  · ②≠1 → Dockerfile 이 요구를 늘리는 도착 경로를 못 봅니다."
    say "  · ③≠1 → **첫 매치에서 멈추고** 있습니다 — (z26) 이 정확히 그래서 못 잡았습니다."
    say "  · ④≠2 → «대상 0건» 을 통과로 접고 있습니다."
    say "  · ⑤≠1 → 축약형 build 를 아예 안 보고 있습니다."
    exit 2
  fi
  say "✔ 판정자가 다섯 칸을 갈랐습니다 (정렬 / 요구증가 / 새 compose / 대상0 / 축약형)."
  exit 0
fi

# 🔴 (z26) 이 실측한 교훈을 그대로 따른다: 열거를 `git ls-files` 로 하지 않는다.
#    ① 스테이지 전 파일이 안 보이고(가짜 «파일 없음»), ② 데모 호스트에서 root 로 돌면
#    git 이 dubious ownership 으로 죽어 **0줄**을 낸다 — 그 0 은 «위반 없음» 과 같은 모양이다.
enumerate() { # <name-pattern>
  find "$ROOT" \
    -name node_modules -prune -o -name .next -prune -o -name .git -prune -o \
    -name build -prune -o -name dist -prune -o -name target -prune -o \
    -name "$1" -print 2>/dev/null | sort
}

# ---------------------------------------------------------------------------
# 1) 요구: Dockerfile 이 필요로 하는 외부 컨텍스트
# ---------------------------------------------------------------------------
# 🔴 trap 이 참조하는 변수를 **먼저** 선언한다. `set -u` 아래서 미할당 변수를 trap 이
#    읽으면 "unbound variable" 로 죽는데, 그 죽음은 **판정 직후**에 나서 rc 를 덮어쓴다
#    (자가검사가 이 결함을 잡았다 — 칸 하나가 rc=2 로 보고됐고 사유는 판정과 무관했다).
BLD_FILE=""
REQ_FILE="$(mktemp)"; trap 'rm -f "$REQ_FILE" "$BLD_FILE" 2>/dev/null' EXIT
df_scanned=0
while IFS= read -r df; do
  [ -n "$df" ] || continue
  df_scanned=$((df_scanned + 1))
  awk -v F="$(cd "$(dirname "$df")" && pwd)/$(basename "$df")" '
    BEGIN{ IGNORECASE=1 }
    /^[[:space:]]*FROM[[:space:]]+/ {
      for (i=1; i<NF; i++) if (tolower($i)=="as") { st[tolower($(i+1))]=1 }
    }
    /^[[:space:]]*COPY[[:space:]]+--from=/ {
      line=$0
      sub(/^[[:space:]]*COPY[[:space:]]+--from=/, "", line)
      sub(/[[:space:]].*$/, "", line)
      names[line]=1
    }
    END{
      for (n in names) {
        if (tolower(n) in st) continue          # 스테이지 — 요구 아님
        if (n ~ /[:\/]/)      continue          # 이미지 참조꼴 — 요구 아님
        if (n ~ /^[0-9]+$/)   continue          # 스테이지 인덱스(COPY --from=0)
        print F "\t" n
      }
    }
  ' "$df" >> "$REQ_FILE"
done < <(enumerate 'Dockerfile')

n_req="$(wc -l < "$REQ_FILE" | tr -d ' ')"
say "Dockerfile ${df_scanned}개 스캔 · 외부 컨텍스트 요구 ${n_req}건"

# 🔴 하한은 «요구 건수» 가 아니라 «스캔한 파일 수» 에 건다. 요구는 정당하게 0 이 될 수
#    있지만(아무도 이 패턴을 안 쓰게 되는 날), **스캔이 0 이면 계측기가 고장난 것**이다.
#    [모집단이 줄어드는 축에 하한을 걸면 성공이 고장으로 읽힌다 — 이 저장소가 여러 번 밟았다]
if [ "$df_scanned" -lt 3 ]; then
  say "✖ Dockerfile 을 ${df_scanned}개밖에 못 찾았습니다 — 열거가 깨졌습니다 ⇒ 판정 불가"
  exit 2
fi

# ---------------------------------------------------------------------------
# 2) 공급: compose 의 build 블록
# ---------------------------------------------------------------------------
# 🔴 YAML 파서를 안 쓴다(러너 외 환경 호환 — `(z26)`·verify-demo-wrapper 가 같은 이유로
#    jq 를 피한다). 대신 **`build:` 블록만** 들여쓰기로 잘라 읽는다. 이 축에 필요한 키는
#    셋뿐이고(`context` · `dockerfile` · `additional_contexts`), 그 셋은 앵커·별칭을
#    쓰지 않는다(저장소 전수 확인: 앵커는 `logging:` 에만 있다).
# 🔴 `build:` 가 **문자열 축약형**(`build: ./path`)일 수도 있다. 그 형태는
#    additional_contexts 를 가질 수 없으므로, 요구가 있으면 **그대로 위반**이다.
BLD_FILE="$(mktemp)"
cm_scanned=0
while IFS= read -r cf; do
  [ -n "$cf" ] || continue
  case "$(basename "$cf")" in docker-compose*.yml|docker-compose*.yaml) : ;; *) continue ;; esac
  cm_scanned=$((cm_scanned + 1))
  cdir="$(cd "$(dirname "$cf")" && pwd)"
  awk -v CF="$cf" -v CDIR="$cdir" '
    function flush() {
      if (inb) {
        printf "%s\t%s\t%s\t%s\t%s\n", CF, CDIR, svc, (ctx=="" ? "." : ctx), (dfl=="" ? "Dockerfile" : dfl) > "/dev/stdout"
        for (k in acx) printf "%s\t%s\tACX\t%s\n", CF, svc, k > "/dev/stdout"
      }
      inb=0; ctx=""; dfl=""; delete acx; inacx=0
    }
    function indent(s,   i) { match(s, /^[ ]*/); return RLENGTH }
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*$/ { next }
    {
      ind = indent($0)
      line = $0
      sub(/^[ ]*/, "", line)
      sub(/[[:space:]]*$/, "", line)

      if (inb && ind <= bind) flush()

      # 서비스 이름(들여쓰기 2칸 기준의 맵 키). 정확한 깊이를 강제하지 않고 «build 보다
      # 얕은 키» 를 서비스로 본다 — 그 파일들이 전부 services: 아래 2칸이다.
      if (ind <= 2 && line ~ /^[A-Za-z0-9_.-]+:[[:space:]]*$/) {
        cand = line; sub(/:.*$/, "", cand)
        if (cand != "services" && cand != "volumes" && cand != "networks" && cand != "x-logging" && cand != "secrets" && cand != "configs")
          svc = cand
        next
      }

      if (line ~ /^build:[[:space:]]*$/) { inb=1; bind=ind; ctx=""; dfl=""; delete acx; inacx=0; next }
      if (line ~ /^build:[[:space:]]*[^[:space:]]/) {         # 축약형 build: <path>
        v = line; sub(/^build:[[:space:]]*/, "", v)
        printf "%s\t%s\t%s\t%s\t%s\n", CF, CDIR, svc, v, "Dockerfile" > "/dev/stdout"
        next
      }

      if (!inb) next

      if (line ~ /^additional_contexts:[[:space:]]*$/) { inacx=1; aind=ind; next }
      if (inacx) {
        if (ind > aind) {
          k = line
          if (k ~ /^-[[:space:]]*/) { sub(/^-[[:space:]]*/, "", k); sub(/=.*$/, "", k) }
          else                      { sub(/:.*$/, "", k) }
          gsub(/[[:space:]]/, "", k)
          if (k != "") acx[k]=1
          next
        }
        inacx=0
      }
      if (line ~ /^context:[[:space:]]*/)    { v=line; sub(/^context:[[:space:]]*/, "", v);    ctx=v; next }
      if (line ~ /^dockerfile:[[:space:]]*/) { v=line; sub(/^dockerfile:[[:space:]]*/, "", v); dfl=v; next }
    }
    END{ flush() }
  ' "$cf" >> "$BLD_FILE"
done < <(enumerate 'docker-compose*')

say "compose ${cm_scanned}개 스캔"
if [ "$cm_scanned" -lt 3 ]; then
  say "✖ compose 파일을 ${cm_scanned}개밖에 못 찾았습니다 — 열거가 깨졌습니다 ⇒ 판정 불가"
  exit 2
fi

# ---------------------------------------------------------------------------
# 3) 대조
# ---------------------------------------------------------------------------
bad=""; undecidable=""; checked=0; matched_total=0
while IFS=$'\t' read -r df name; do
  [ -n "$df" ] || continue
  matched=0
  while IFS=$'\t' read -r cf cdir svc ctx dfl; do
    [ "$cf" = "" ] && continue
    [ "$svc" = "ACX" ] && continue
    resolved="$(realpath -m "$cdir/$ctx/$dfl" 2>/dev/null)"
    [ "$resolved" = "$df" ] || continue
    matched=$((matched + 1)); checked=$((checked + 1))
    # 이 서비스의 build 블록이 그 이름을 선언했는가
    if ! grep -qF "$(printf '%s\t%s\tACX\t%s' "$cf" "$svc" "$name")" "$BLD_FILE"; then
      bad="$bad   ${df#$ROOT/} 이(가) '--from=$name' 를 요구하는데 ${cf#$ROOT/} 의 서비스 '$svc' 가 안 줍니다"$'\n'
    fi
  done < <(awk -F'\t' 'NF==5' "$BLD_FILE")
  matched_total=$((matched_total + matched))
  # 🔴 **조용한 0 금지.** 요구가 있는데 그것을 빌드하는 compose 서비스를 하나도 못 찾으면
  #    통과가 아니라 판정 불가다 — 이 가드의 가장 그럴듯한 결함이 «해석기가 대상을 못 찾아
  #    볼 것이 없어 초록» 이다.
  if [ "$matched" -eq 0 ]; then
    undecidable="$undecidable   ${df#$ROOT/} ('--from=$name' 요구) 를 빌드하는 compose 서비스를 **하나도** 못 찾았습니다"$'\n'
  fi
done < "$REQ_FILE"

if [ -n "$undecidable" ]; then
  say "✖ **판정 불가** — 대상을 못 찾았습니다(0건은 통과가 아닙니다):"
  printf '%s' "$undecidable"
  say "  → build.context / dockerfile 해석이 깨졌거나, 그 이미지를 compose 가 아닌 곳에서만"
  say "     빌드합니다. 후자라면 이 가드는 그 축을 못 봅니다 — 예외를 여기 명시적으로 넣으세요."
  exit 2
fi

if [ -n "$bad" ]; then
  say "✖ **누락** — Dockerfile 이 요구하는 컨텍스트를 안 주는 compose 서비스가 있습니다:"
  printf '%s' "$bad"
  say "  → 증상은 «모듈을 못 찾음» 이 아니라 **\"pull access denied … docker.io/library/<이름>:latest\"** 입니다."
  say "     BuildKit 이 그 이름을 이미지로 해석하기 때문이고, 그래서 원인이 배선처럼 보이지 않습니다."
  say "  → 그 서비스의 build 블록에 additional_contexts 를 넣으세요. 🔴 경로는 **그 compose 파일 기준**입니다"
  say "     (형제의 '../../' 를 복사하지 마세요 — 디렉터리 깊이가 다르면 값도 다릅니다)."
  exit 1
fi

say "✔ 요구 ${n_req}건이 그것을 빌드하는 compose 서비스 ${checked}칸에서 전부 공급됩니다."
exit 0
