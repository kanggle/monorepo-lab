#!/usr/bin/env bash
# =============================================================================
# infra/demo/check-image-freshness.sh — 이미지가 코드보다 낡았는지 본다 (TASK-MONO-533)
# =============================================================================
# `demo-up.sh` 는 기본이 `DEMO_BUILD=0` 이라 **호스트에 이미 있는 `:latest` 를 그대로
# 쓴다.** 그 이미지가 코드보다 낡았어도 컨테이너는 **멀쩡히 뜬다** — 그래서 증상이
# 크래시가 아니라 *"이미 고친 결함이 화면에 그대로 보이는 것"* 이고, 다음 사람은 그
# 결함을 코드에서 **다시 발굴한다.**
#
# 실측(2026-08-15, 이 검사를 만들게 한 사건): `wms-outbound-service:latest` 가
# 02:00:30 에 빌드됐고 마지막 wms 앱 커밋(`TASK-BE-586`)은 03:17:59 였다 — **78분**
# 이르다. 그대로 띄웠다면 그 티켓이 만든 `PickingRequest` 생성 경로가 **없는 바이너리**로
# 데모가 돌았을 것이고, 화면은 그 티켓 이전과 똑같이 보였을 것이다.
#
# -----------------------------------------------------------------------------
# 왜 여기(기동 경로)인가 — `verify-demo-wrapper.sh` 가 아니라
# -----------------------------------------------------------------------------
# 래퍼는 **정적** 검증이고 CI 에서 돈다. CI 러너에는 데모 이미지가 **하나도 없다** ⇒
# 거기서 이 검사는 영원히 "판정 불가" 만 낸다. 늘 판정 불가인 검사는 아무도 안 읽고,
# 안 읽히는 검사는 없는 것보다 나쁘다(`TASK-MONO-360`).
# 이미지가 존재하고 그 사실이 **행동으로 이어지는** 가장 이른 시점은 기동 직전이다.
#
# -----------------------------------------------------------------------------
# 술어 (여기가 이 검사의 전부다)
# -----------------------------------------------------------------------------
# 🔴 비교 대상은 **저장소 HEAD 가 아니라 그 서비스의 소스를 마지막으로 건드린 커밋**이다.
#    HEAD 로 비교하면 **문서 커밋 하나로 40개 이미지가 전부 낡음**이 되고, 그런 검사는
#    첫날부터 시끄러워서 꺼진다.
#
# 🔴 **낡음만 단언한다.** 이미지가 커밋보다 새롭다고 그 커밋을 담았다는 보장은 없다
#    (더티 트리에서 구울 수 있다). 보장되는 것은 한 방향뿐이다 —
#    **이미지가 더 낡으면 그 커밋은 확실히 없다.** 그래서 "최신입니다" 는 말하지 않는다.
#
# 🔴 **축이 둘인 이유 — 이 검사는 자기 치료법의 절반으로 꺼질 수 있었다 (TASK-MONO-538).**
#    낡음을 찾으면 아래 두 줄을 순서대로 인쇄한다: ① `./gradlew …:bootJar` ②
#    `DEMO_BUILD=1 demo-up.sh …`. 그런데 **`demo-up.sh` 는 컴파일하지 않는다** ⇒ ②만
#    실행하면 도커가 **디스크의 옛 jar 를 그대로 COPY** 해 이미지를 다시 굽고 `Created` 를
#    지금으로 찍는다. 첫 축(이미지 vs 소스)은 그 순간부터 **영원히 초록**이고, 그 안의
#    바이너리는 여전히 옛것이다. 그래서 **jar 자신의 시각**을 두 번째 축으로 잰다:
#    `이미지 ≥ 소스` 인데 `jar < 소스` 이면 `옛-jar` 로 따로 센다.
#    🔵 실제로 이 결함을 발견한 경로: `DEMO_BUILD=1 demo-up.sh scm` 이 **하드 의존 iam 까지**
#    다시 구웠는데 iam 의 jar 은 만들지 않은 상태였다(그때는 우연히 jar 이 최신이라 피해가
#    없었다). 세탁 표면은 **요청한 도메인보다 넓다.**
#
# 🔴 **시각 축을 맞춘다.** `docker image inspect` 의 `Created` 는 RFC3339(UTC 오프셋
#    포함), `git log %ct` 는 epoch 이다. 둘 다 epoch 로 바꿔 비교한다 — 이 호스트는
#    UTC+9 라 안 맞추면 9시간짜리 거짓 경보/거짓 침묵이 난다.
#
# 🔵 **모집단은 "compose 가 띄우는 서비스 중 앱 모듈이 있는 것"** 이다. `apps/<svc>/
#    Dockerfile` 의 존재를 기준으로 하므로 postgres·kafka·grafana 같은 외부 이미지는
#    이름 블랙리스트 없이 자연히 빠진다(블랙리스트는 새 서비스가 늘 때 조용히 어긋난다).
#
# 🔵 **공유 `libs/` 는 서비스별로 귀속하지 않는다.** 어떤 서비스가 어떤 lib 에 의존하는지
#    알려면 gradle 을 파싱해야 하고, 전부 귀속시키면 lib 커밋 하나가 모든 이미지를
#    낡음으로 만든다(위의 첫날-RED 실패 모드). 대신 **행 하나로 따로 알린다** — 판정하지
#    않고 사실만 말한다.
#
# 판정 불가(이미지 없음 · docker 불통)는 **초록이 아니다.** 따로 세어 따로 출력한다.
#
# 종료 코드: 항상 0. 이것은 **고지**이지 게이트가 아니다 — 낡은 이미지로 데모를 켜는 것이
# 불가능해야 할 이유는 없고(때로는 그게 정확히 원하는 것이다), 기동을 막으면 사람들이
# `demo-up.sh` 자체를 우회한다.
#
# 사용:  bash infra/demo/check-image-freshness.sh <domain>...
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
# shellcheck source=infra/demo/projects.sh
source "$HERE/projects.sh"

[ "$#" -gt 0 ] || { echo "usage: check-image-freshness.sh <domain>..." >&2; exit 0; }

stale=0 fresh=0 undecidable=0 laundered=0
stale_lines=() und_lines=() rebuild_mods=() laundered_lines=() laundered_mods=()
oldest_image_epoch=""

for slug in "$@"; do
  [ -n "${COMPOSE[$slug]+x}" ] || continue
  # 프로젝트 디렉터리 = COMPOSE 첫 파일의 부모 (projects/<name>/…)
  first="${COMPOSE[$slug]%% *}"
  projdir="$ROOT/$(dirname "$first")"
  case "$projdir" in */projects/*) ;; *) continue ;; esac

  mapfile -t ARGS < <(compose_args "$slug")
  # 🔴 이미지 이름을 `<프로젝트>-<서비스>` 로 **파생하지 않는다.** 대부분은 그 규칙이지만
  # compose 에 명시적 `image:` 가 있는 서비스가 있고(예: console-web =
  # `platform-console/console-web:local`), 파생 이름은 그 경우 **존재하지 않는 이미지를
  # 찾다가 "판정 불가"를 만든다** — 실제로 첫 판에서 그렇게 냈다. compose 가 말하는 것을 읽는다.
  mapfile -t PAIRS < <(docker compose -p "$slug" "${ARGS[@]}" config 2>/dev/null | awk '
    /^services:/ { insvc=1; next }
    insvc && /^[a-zA-Z]/ { insvc=0 }
    !insvc { next }
    /^  [A-Za-z0-9_.-]+:[[:space:]]*$/ { if (svc != "") print svc "	" img; svc=$1; sub(/:$/,"",svc); img=""; next }
    svc != "" && /^    image:[[:space:]]/ { img=$2 }
    END { if (svc != "") print svc "	" img }')
  if [ "${#PAIRS[@]}" -eq 0 ]; then
    undecidable=$((undecidable+1))
    und_lines+=("$slug: compose 가 서비스 목록을 내지 않음 (docker 불통?) — 이 도메인은 판정하지 못했다")
    continue
  fi

  for pair in "${PAIRS[@]}"; do
    svc="${pair%%$'	'*}"; declared="${pair#*$'	'}"
    moddir="$projdir/apps/$svc"
    [ -f "$moddir/Dockerfile" ] || continue     # 앱 모듈이 아닌 서비스 = 모집단 밖

    img="${declared:-${slug}-${svc}}"
    created="$(docker image inspect "$img" --format '{{.Created}}' 2>/dev/null)"
    if [ -z "$created" ]; then
      undecidable=$((undecidable+1))
      und_lines+=("$img: 이미지가 없다 — 첫 기동이거나 이름 규칙이 어긋났다(판정 불가, 초록 아님)")
      continue
    fi
    img_epoch="$(date -d "$created" +%s 2>/dev/null)"
    if [ -z "$img_epoch" ]; then
      undecidable=$((undecidable+1))
      und_lines+=("$img: Created 를 해석하지 못했다('$created') — 판정 불가")
      continue
    fi
    [ -z "$oldest_image_epoch" ] || [ "$img_epoch" -lt "$oldest_image_epoch" ] && oldest_image_epoch="$img_epoch"

    src_epoch="$(git -C "$ROOT" log -1 --format=%ct -- "$moddir" 2>/dev/null)"
    if [ -z "$src_epoch" ]; then
      undecidable=$((undecidable+1))
      und_lines+=("$img: '$moddir' 를 건드린 커밋이 없다 — 판정 불가")
      continue
    fi

    if [ "$img_epoch" -lt "$src_epoch" ]; then
      stale=$((stale+1))
      stale_lines+=("$(printf '%-40s 이미지 %s  <  소스 %s' "$img" \
        "$(date -u -d "@$img_epoch" '+%m-%d %H:%MZ')" "$(date -u -d "@$src_epoch" '+%m-%d %H:%MZ')")")
      # 🔵 조치까지 적는다. *"낡았습니다"* 만으로는 읽는 사람이 **어느 모듈을** 구워야
      # 하는지 모른다 — gradle 경로는 여기서 이미 알고 있다(프로젝트 디렉터리 + 서비스명).
      rebuild_mods+=(":projects:$(basename "$projdir"):apps:${svc}:bootJar")
    else
      # ── 두 번째 축: **이미지는 새로운데 jar 가 낡았는가** (TASK-MONO-538) ──────────
      # `demo-up.sh` 는 **컴파일하지 않는다**(그 파일 헤더가 명시한다). 그래서
      # `DEMO_BUILD=1` 만 실행하면 도커가 **디스크의 옛 jar 를 그대로 COPY** 해 이미지를
      # 다시 굽고 `Created` 를 지금으로 찍는다 ⇒ 위 첫 축은 그 순간부터 **영원히 초록**이다.
      #
      # 🔴 즉 이 검사는 **자기가 인쇄한 두 줄 중 두 번째만 실행하면 꺼진다.** 래칫이 자기
      # 계측기를 끄는 모양이고, 그것을 볼 수 있는 유일한 자리가 jar 자신의 시각이다.
      #
      # 🔴 여기서도 **낡음만 단언한다** — jar 가 소스보다 새롭다고 그 커밋을 담았다는
      # 보장은 없다(더티 트리 빌드). 역방향만 보장된다.
      # 🔵 jar 가 아예 없으면 낡음이 아니라 **판정 불가**다(빌드 산출물을 청소했을 수 있다).
      jar="$(ls "$moddir"/build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1)"
      if [ -z "$jar" ]; then
        fresh=$((fresh+1))
        undecidable=$((undecidable+1))
        und_lines+=("$img: 이미지는 소스보다 새롭지만 **jar 가 없다** — 이 이미지가 무엇으로 구워졌는지 판정 불가")
      else
        jar_epoch="$(date -r "$jar" +%s 2>/dev/null)"
        if [ -z "$jar_epoch" ]; then
          fresh=$((fresh+1))
          undecidable=$((undecidable+1))
          und_lines+=("$img: jar 시각을 읽지 못했다('$jar') — 판정 불가")
        elif [ "$jar_epoch" -lt "$src_epoch" ]; then
          laundered=$((laundered+1))
          laundered_lines+=("$(printf '%-40s 이미지 %s (새로움)  ·  jar %s  <  소스 %s' "$img" \
            "$(date -u -d "@$img_epoch" '+%m-%d %H:%MZ')" \
            "$(date -u -d "@$jar_epoch" '+%m-%d %H:%MZ')" \
            "$(date -u -d "@$src_epoch" '+%m-%d %H:%MZ')")")
          laundered_mods+=(":projects:$(basename "$projdir"):apps:${svc}:bootJar")
        else
          fresh=$((fresh+1))
        fi
      fi
    fi
  done
done

if [ "$stale" -gt 0 ]; then
  echo "[freshness] 🔴 코드보다 낡은 이미지 ${stale}개 — 그대로 띄우면 **고친 결함이 그대로 보인다**"
  for l in "${stale_lines[@]}"; do echo "[freshness]   $l"; done
  echo "[freshness]   ① 먼저:    ./gradlew ${rebuild_mods[*]}"
  echo "[freshness]   ② 그 다음:  DEMO_BUILD=1 bash infra/demo/demo-up.sh $*"
  # 🔴 순서가 load-bearing 이다 — ② 만 하면 `demo-up.sh` 는 컴파일하지 않으므로 **옛 jar 로**
  #    이미지가 다시 구워지고, 그 순간부터 이 검사는 그 서비스를 영원히 "신선" 으로 본다.
  echo "[freshness]   ⚠ 순서를 지킬 것 — ②만 실행하면 **옛 jar 로 이미지만 새로 구워져** 이 검사가 그 서비스를 영영 못 잡는다"
fi
if [ "$laundered" -gt 0 ]; then
  echo "[freshness] 🔴 이미지는 새로운데 **jar 가 코드보다 낡은** 서비스 ${laundered}개 — 새 이미지 안에 옛 바이너리가 들어 있다"
  for l in "${laundered_lines[@]}"; do echo "[freshness]   $l"; done
  echo "[freshness]   다시 굽기:  ./gradlew ${laundered_mods[*]}  →  DEMO_BUILD=1 bash infra/demo/demo-up.sh $*"
fi
if [ "$undecidable" -gt 0 ]; then
  echo "[freshness] ⚠ 판정 불가 ${undecidable}건 (초록이 아니다 — 재지 못한 것이다)"
  for l in "${und_lines[@]}"; do echo "[freshness]   $l"; done
fi

# 공유 라이브러리 — 귀속하지 않고 사실만
if [ -n "$oldest_image_epoch" ]; then
  libs_epoch="$(git -C "$ROOT" log -1 --format=%ct -- "$ROOT/libs" 2>/dev/null)"
  if [ -n "$libs_epoch" ] && [ "$libs_epoch" -gt "$oldest_image_epoch" ]; then
    echo "[freshness] ⚠ 공유 \`libs/\` 가 가장 낡은 이미지보다 새롭다($(date -u -d "@$libs_epoch" '+%m-%d %H:%MZ')) — **어느 서비스가 영향받는지 이 검사는 모른다**(gradle 의존을 안 읽는다)"
  fi
fi

# 🔴 요약은 **언제나** 낸다. 첫 판은 판정 불가가 있으면 요약을 삼켰고, 그래서 "몇 개가
# 실제로 신선했는지" 를 아무도 알 수 없었다 — 판정 불가만 보이면 검사가 아무것도 못 한
# 것처럼 읽힌다.
echo "[freshness] 요약 — 신선 ${fresh} · 낡음 ${stale} · 옛-jar ${laundered} · 판정 불가 ${undecidable} (낡음만 판정한다 — '최신'을 뜻하지는 않는다)"
exit 0
