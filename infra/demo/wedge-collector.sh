#!/bin/sh
# TASK-MONO-552 AC-0 수집기.
#
# 설계 전제: **호스트가 굳으면 물어볼 수 없다**(1·2차 실증에서 두 번 확인). 그러므로
# 이 스크립트는 (a) 부팅 초반에 심어지고 (b) 굳는 동안에도 계속 쓰고 (c) 루트 EBS 에
# 남아 stop/start 를 견뎌야 한다.
#
# 계측기 선택:
#   · /proc/pressure/{memory,io,cpu}  ← 결정적. 커널이 멈춤 시간을 **자원별로 귀속**한다.
#     지금까지의 "읽기 IOPS 가 폭증했으니 thrashing 일 것" 은 추론이었다. PSI 는 측정이다.
#     full avg10 이 memory 에서 높고 io 에서 낮으면 메모리, 반대면 I/O.
#   · /proc/vmstat pgmajfault  ← major fault 누적. thrashing 이면 단조 급증한다.
#   · free -m                  ← available 이 0 으로 수렴하는가.
#   · 스케줄 지연               ← 기대 간격 대비 실제 간격. **안에서 본 멈춤의 크기**다.
#                                 이게 없으면 "로그가 끊겼다" 와 "느려졌다" 를 구분 못 한다.
#
# 일부러 넣지 않은 것: `docker stats`(데몬이 먼저 굳는다 — 계측기가 관측 대상에 의존하면
# 관측 대상이 죽을 때 계측기도 죽는다), `ps` 매 틱(비싸다 — 2분마다만).

OUT=/var/log/wedge-collector.log
INTERVAL=20

log() { printf '%s\n' "$*" >> "$OUT"; }

log "=== collector start $(date -u +%FT%TZ) boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null) ==="
log "=== interval=${INTERVAL}s  ncpu=$(nproc 2>/dev/null)  memtotal=$(awk '/MemTotal/{print $2}' /proc/meminfo) kB ==="

prev_epoch=$(date -u +%s)
n=0
while :; do
  n=$((n+1))
  now=$(date -u +%s)
  lag=$(( now - prev_epoch - INTERVAL ))   # 양수 = 커널이 우리를 제때 못 깨웠다
  prev_epoch=$now

  # free
  mem=$(awk '/^MemTotal:/{t=$2} /^MemAvailable:/{a=$2} /^Cached:/{c=$2} /^SwapFree:/{sf=$2}
             END{printf "total=%dM avail=%dM cached=%dM swapfree=%dM", t/1024, a/1024, c/1024, sf/1024}' /proc/meminfo)

  # PSI — 결정적 계측기.
  # 🔴 avg10 만 찍으면 안 된다: 소수점 둘째 자리 반올림이라 **작은 압력이 0.00 으로 사라진다**
  # (2026-08-18 1차 측정에서 25분간 mem_full=0.00 을 보고 "압력 없음" 으로 결론 낼 뻔했다).
  # `total=` 은 단조 증가하는 마이크로초 카운터라 반올림되지 않는다 — **차분이 진짜 신호**다.
  pm=$(awk '/^some/{s=$2; st=$5} /^full/{f=$2; ft=$5} END{printf "mem_some=%s mem_full=%s mem_someT=%s mem_fullT=%s", s, f, st, ft}' /proc/pressure/memory 2>/dev/null)
  pi=$(awk '/^some/{s=$2; st=$5} /^full/{f=$2; ft=$5} END{printf "io_some=%s io_full=%s io_someT=%s io_fullT=%s", s, f, st, ft}' /proc/pressure/io 2>/dev/null)
  pc=$(awk '/^some/{s=$2; st=$5} END{printf "cpu_some=%s cpu_someT=%s", s, st}' /proc/pressure/cpu 2>/dev/null)

  # major fault 누적 + 스왑 인/아웃
  vm=$(awk '/^pgmajfault /{mf=$2} /^pswpin /{si=$2} /^pswpout /{so=$2}
            END{printf "pgmajfault=%s pswpin=%s pswpout=%s", mf, si, so}' /proc/vmstat)

  load=$(cut -d' ' -f1-3 /proc/loadavg)
  procs=$(awk '{print $4}' /proc/loadavg)

  log "[$n] $(date -u +%H:%M:%SZ) lag=${lag}s load=$load runnable=$procs | $mem | $pm $pi $pc | $vm"

  # 2분마다 RSS 상위 8개 — 누가 먹고 있는지
  if [ $(( n % 6 )) -eq 1 ]; then
    log "    top-rss: $(ps -eo rss=,comm= --sort=-rss 2>/dev/null | head -8 | awk '{printf "%s=%dM ", $2, $1/1024}')"
  fi

  sleep "$INTERVAL"
done
