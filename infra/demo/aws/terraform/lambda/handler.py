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
    r = ec2.describe_instances(InstanceIds=[INSTANCE_ID])
    reservations = r.get("Reservations") or []
    if not reservations or not reservations[0].get("Instances"):
        return "missing", None
    inst = reservations[0]["Instances"][0]
    return inst["State"]["Name"], inst.get("PublicIpAddress")


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
    state, _ = _state()
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
    return _resp({"state": "starting", "message": "기동 시작 — 41서비스 웜업까지 약 2~4분"})


def stop():
    ec2.stop_instances(InstanceIds=[INSTANCE_ID])
    return _resp({"state": "stopping", "message": "종료 요청됨"})


def status():
    state, ip = _state()
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
    state, _ = _state()
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

    last_beat = int(_get(BEAT_PARAM, now))
    started = int(_get(STARTED_PARAM, now))
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
