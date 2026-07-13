"""On-demand demo control plane.

하나의 Lambda 가 두 종류의 이벤트를 처리한다:
  1) API Gateway (HTTP API v2) — /start /stop /status /heartbeat
  2) EventBridge 스케줄 — {"action": "idle-check"} 로 5분마다 호출되어
     heartbeat 끊김 / 최대 가동시간 초과 / 월 예산 소진 시 인스턴스를 stop.

상태(마지막 heartbeat, 기동 시각, 월 누적 사용량)는 SSM Parameter Store 에 둔다(무료).

-------------------------------------------------------------------------------
왜 월 예산 가드가 있는가 (MONTHLY_BUDGET_MINUTES)
-------------------------------------------------------------------------------
/start 는 공개 정적 사이트가 부르는 **인증 없는** 엔드포인트다. 토큰을 숨길 곳이
없다 — 정적 사이트에 넣으면 그 토큰도 같이 공개된다. CORS(ALLOWED_ORIGIN)는
브라우저 정책일 뿐 서버를 지키지 못한다(curl 한 줄이면 우회).

따라서 "URL 을 아는 누구나 인스턴스를 켤 수 있다"는 전제를 받아들이고, 대신
**지출의 상한**을 서버 쪽에 둔다. idle-stop 과 max-runtime 만으로는 부족하다:
/start 를 반복 호출하면 둘 다 계속 리셋되어 24/7 가동(월 $360)이 가능하다.

이 가드는 running 상태의 실제 경과 시간을 5분 틱으로 누적하고, 월 예산을 넘으면
(a) 즉시 stop 하고 (b) 이후 /start 를 429 로 거절한다. 매월 1일 자동 리셋.
"""
import json
import os
import time

import boto3

ec2 = boto3.client("ec2")
ssm = boto3.client("ssm")

INSTANCE_ID = os.environ["INSTANCE_ID"]
BEAT_PARAM = os.environ["BEAT_PARAM"]
STARTED_PARAM = os.environ["STARTED_PARAM"]
USAGE_PARAM = os.environ["USAGE_PARAM"]
IDLE_MINUTES = int(os.environ.get("IDLE_MINUTES", "20"))
MAX_MINUTES = int(os.environ.get("MAX_RUNTIME_MINUTES", "180"))
BUDGET_MINUTES = int(os.environ.get("MONTHLY_BUDGET_MINUTES", "600"))
ALLOWED_ORIGIN = os.environ.get("ALLOWED_ORIGIN", "*")

# EventBridge 틱 간격(5분)의 2배. 틱이 한 번 유실돼도 실제 경과분을 반영하되,
# 인스턴스가 오래 running 이었는데 Lambda 가 죽어 있던 구간을 과도하게 몰아서
# 계상하지 않도록 상한을 둔다.
MAX_TICK_SECONDS = 600


def _now():
    return int(time.time())


def _month(ts=None):
    return time.strftime("%Y-%m", time.gmtime(ts if ts is not None else _now()))


def _put(param, value):
    ssm.put_parameter(Name=param, Value=str(value), Type="String", Overwrite=True)


def _get(param, default=None):
    try:
        return ssm.get_parameter(Name=param)["Parameter"]["Value"]
    except ssm.exceptions.ParameterNotFound:
        return default


def _state():
    """(state, public_ip, launched_at) — launched_at 은 EC2 가 아는 기동 시각(epoch, 없으면 0).

    **왜 EC2 의 LaunchTime 을 함께 돌려주는가.** 유휴/최대가동 판정은 원래 SSM 의
    beat/started 파라미터만 봤다. 그런데 terraform 이 그 둘을 `value = "0"` 으로
    **미리 만들어 둔다** ⇒ `now - 0` ≈ 17억 초 ⇒ `apply` 직후 첫 idle-check(5분 이내)가
    **웜업 도중의 인스턴스를 즉시 정지시킨다.**

    handler 에는 `_get(BEAT_PARAM, now)` 라는 안전 기본값이 있었다. 하지만 그것은
    파라미터가 **없을 때만** 쓰이고, terraform 이 항상 만들어 두므로 **한 번도 도달할 수
    없는 가드**였다. 있는 것과 물 기회를 얻는 것은 다르다.

    피해는 정지에서 끝나지 않았다. 정지가 스택 웜업 한복판을 자르는 바람에 kafka 의
    KRaft 로그 디렉터리가 반쯤 쓰인 채로 남았고(`topic ID` 없는 log dir), 그 뒤 **모든**
    부팅에서 kafka 가 기동을 거부해 `demo-boot.sh` 가 중단됐다 — fan/console 은 순번이
    오지 않아 영영 뜨지 않았다. 정문을 열었더니 그 뒤가 무너져 있었다(TASK-MONO-389).

    LaunchTime 은 **누가 켰든**(terraform·콘솔·/start) 참인 유일한 사실이다.
    """
    r = ec2.describe_instances(InstanceIds=[INSTANCE_ID])
    reservations = r.get("Reservations") or []
    if not reservations or not reservations[0].get("Instances"):
        return "missing", None, 0
    inst = reservations[0]["Instances"][0]
    launched = inst.get("LaunchTime")
    return (
        inst["State"]["Name"],
        inst.get("PublicIpAddress"),
        int(launched.timestamp()) if launched is not None else 0,
    )


def _resp(body, code=200):
    return {
        "statusCode": code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": ALLOWED_ORIGIN,
            "Access-Control-Allow-Headers": "content-type",
            "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
        },
        "body": json.dumps(body, ensure_ascii=False),
    }


# ---- 월 사용량 -------------------------------------------------------------

def _usage():
    """{"month": "YYYY-MM", "seconds": int, "tick": int} — 손상/미존재 시 당월 0."""
    raw = _get(USAGE_PARAM)
    cur = _month()
    if not raw:
        return {"month": cur, "seconds": 0, "tick": 0}
    try:
        u = json.loads(raw)
        # 달이 바뀌면 리셋. tick 도 버린다(지난달 틱으로 이번달을 계상하지 않도록).
        if u.get("month") != cur:
            return {"month": cur, "seconds": 0, "tick": 0}
        return {
            "month": cur,
            "seconds": int(u.get("seconds", 0)),
            "tick": int(u.get("tick", 0)),
        }
    except (ValueError, TypeError):
        # 손상된 값이 "예산 무제한"으로 읽히면 안 된다 — 보수적으로 0 부터.
        return {"month": cur, "seconds": 0, "tick": 0}


def _save_usage(u):
    _put(USAGE_PARAM, json.dumps(u))


def _budget_exhausted(u):
    return u["seconds"] >= BUDGET_MINUTES * 60


# ---- API actions -----------------------------------------------------------

def start():
    state, _, _ = _state()
    if state == "missing":
        return _resp({"state": state, "message": "인스턴스를 찾을 수 없습니다"}, 503)

    u = _usage()
    if _budget_exhausted(u):
        used_min = u["seconds"] // 60
        return _resp(
            {
                "state": state,
                "error": "monthly-budget-exhausted",
                "message": f"이번 달 데모 가동 예산 소진 ({used_min}/{BUDGET_MINUTES}분). 다음 달 1일 리셋됩니다.",
                "used_minutes": used_min,
                "budget_minutes": BUDGET_MINUTES,
            },
            429,
        )

    if state == "stopping":
        return _resp({"state": state, "message": "이전 종료 진행 중 — 잠시 후 다시 시도"}, 409)
    if state == "stopped":
        ec2.start_instances(InstanceIds=[INSTANCE_ID])
        # 새 세션의 시작점. 이전 세션의 tick 이 남아 있으면 정지 구간이
        # running 으로 계상되므로 여기서 끊는다.
        u["tick"] = _now()
        _save_usage(u)

    # 이미 running/pending 이어도 heartbeat/started 는 갱신해 세션 연장
    _put(STARTED_PARAM, _now())
    _put(BEAT_PARAM, _now())
    # "약 10분" 은 실측이다(MONO-389, 데모 호스트 저널): 부팅 → `up complete` 9분 32초.
    # 예전엔 "약 2~4분" 이라 적혀 있었다 — 잰 적 없는 숫자이고, 그 시점엔 console 이
    # 아직 시작도 안 했다. 방문자를 정확히 포기할 시점에 포기시키는 문구였다.
    return _resp({"state": "starting", "message": "기동 시작 — 8개 프로젝트 웜업까지 약 10분"})


def stop():
    ec2.stop_instances(InstanceIds=[INSTANCE_ID])
    return _resp({"state": "stopping", "message": "종료 요청됨"})


def status():
    state, ip, _ = _state()
    u = _usage()
    return _resp(
        {
            "state": state,
            "ip": ip,
            "used_minutes": u["seconds"] // 60,
            "budget_minutes": BUDGET_MINUTES,
        }
    )


def heartbeat():
    _put(BEAT_PARAM, _now())
    return _resp({"ok": True})


# ---- Scheduled idle check --------------------------------------------------

def idle_check():
    state, _, launched_at = _state()
    now = _now()
    u = _usage()

    if state != "running":
        # 정지 구간이 다음 틱에 몰아서 계상되지 않도록 tick 을 끊는다.
        if u["tick"]:
            u["tick"] = 0
            _save_usage(u)
        return {"checked": True, "state": state, "used_minutes": u["seconds"] // 60}

    # running — 지난 틱 이후 경과분을 누적
    if u["tick"]:
        u["seconds"] += min(now - u["tick"], MAX_TICK_SECONDS)
    u["tick"] = now
    _save_usage(u)

    # 두 시계를 **인스턴스의 실제 기동 시각으로 하한한다.**
    #
    # terraform 은 beat/started 를 `value = "0"` 으로 만들어 둔다. 그대로 빼면
    # `now - 0` ≈ 17억 초라 유휴·최대가동 가드가 **둘 다** 즉시 물어, `apply` 직후
    # 웜업 중인 인스턴스를 5분 안에 꺼버린다(그리고 잘린 웜업이 kafka 로그 디렉터리를
    # 망가뜨려 이후 부팅을 전부 실패시킨다 — TASK-MONO-389 에서 실측).
    #
    # `max(param, launched_at)` 는 두 가지를 한 번에 정리한다:
    #   · 센티널 0        → 기동 시각이 이긴다 ⇒ 갓 켜진 인스턴스는 온전한 유휴 창을 얻는다
    #   · 이전 세션의 값  → 기동 시각이 이긴다 ⇒ 지난 부팅의 하트비트로 지금을 재지 않는다
    # 반대로 /start 가 방금 찍은 값은 기동 시각보다 나중이므로 그대로 이긴다(세션 연장 유지).
    anchor = launched_at or now
    last_beat = max(int(_get(BEAT_PARAM, now) or 0), anchor)
    started = max(int(_get(STARTED_PARAM, now) or 0), anchor)
    idle_sec = now - last_beat
    run_sec = now - started

    reason = None
    if _budget_exhausted(u):
        reason = f"monthly-budget {BUDGET_MINUTES}m 소진"
    elif run_sec > MAX_MINUTES * 60:
        reason = f"max-runtime {MAX_MINUTES}m 초과"
    elif idle_sec > IDLE_MINUTES * 60:
        reason = f"idle {IDLE_MINUTES}m 초과"

    if reason:
        ec2.stop_instances(InstanceIds=[INSTANCE_ID])
        u["tick"] = 0
        _save_usage(u)
        return {
            "stopped": True,
            "reason": reason,
            "idle_sec": idle_sec,
            "run_sec": run_sec,
            "used_minutes": u["seconds"] // 60,
        }

    return {
        "stopped": False,
        "idle_sec": idle_sec,
        "run_sec": run_sec,
        "used_minutes": u["seconds"] // 60,
    }


# ---- Dispatch --------------------------------------------------------------

def handler(event, context):
    if event.get("action") == "idle-check":
        return idle_check()

    http = event.get("requestContext", {}).get("http", {})
    method = http.get("method")
    path = http.get("path", "")

    if method == "OPTIONS":
        return _resp({"ok": True})
    if path.endswith("/start"):
        return start()
    if path.endswith("/stop"):
        return stop()
    if path.endswith("/status"):
        return status()
    if path.endswith("/heartbeat"):
        return heartbeat()
    return _resp({"error": "not found", "path": path}, 404)
