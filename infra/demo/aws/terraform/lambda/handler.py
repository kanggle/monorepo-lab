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
없다 — 정적 사이트에 넣으면 그 토큰도 같이 공개된다. CORS(= API Gateway 의
`cors_configuration`)는 브라우저 정책일 뿐 서버를 지키지 못한다(curl 한 줄이면 우회).

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
HEALTH_PARAM = os.environ.get("HEALTH_PARAM", "/portfolio-demo/domains-health")
# 헬스 스냅샷이 이 나이를 넘으면 stale 로 본다 (TASK-MONO-551 결함 B).
# 발행 주기는 30초(demo-status.timer)이므로 3주기 = 90초. 한 번 놓친 발행으로 빨개지지
# 않으면서, 발행자가 죽은 것은 1분 반 안에 드러난다. 인스턴스 시계와 Lambda 시계가 다르지만
# 둘 다 UTC + chrony 이므로 오차는 이 임계보다 훨씬 작다.
#
# 🔵 terraform 에 env 로 심지 않는다 — 이 값은 사실 하나이고, `HEALTH_PARAM` 처럼 세 곳에
#    복제되면 한 곳만 고쳐진다(가드 (z) 가 존재하는 이유가 그것이다). 필요하면 Lambda 콘솔
#    env 로 덮을 수 있게 os.environ 은 열어 둔다.
HEALTH_STALE_AFTER_SECONDS = int(os.environ.get("HEALTH_STALE_AFTER_SECONDS", "90"))
IDLE_MINUTES = int(os.environ.get("IDLE_MINUTES", "20"))
MAX_MINUTES = int(os.environ.get("MAX_RUNTIME_MINUTES", "180"))
BUDGET_MINUTES = int(os.environ.get("MONTHLY_BUDGET_MINUTES", "600"))

# 🔴 CORS 는 여기서 다루지 않는다 — **API Gateway 의 `cors_configuration` 이 유일한 집**이다
# (TASK-MONO-557). 예전에는 이 파일도 `ALLOWED_ORIGIN` 을 읽어 `_resp()` 에 실었고, 그래서
# 같은 사실이 두 집을 갖고 있었다. 2026-08-18 실측이 그 구조가 이미 어긋나 있었음을 보였다:
#
#   · terraform 의 `var.allowed_origin` 은 `""` 였고, API Gateway 는 그 빈 값을 보고 폴백해
#     CloudFront 도메인을 허용했다(참조라서 정확).
#   · 그런데 같은 `""` 가 이 파일에는 **그대로** 왔다 — `os.environ.get` 은 키가 존재하면
#     기본값 `"*"` 를 쓰지 않으므로 `Access-Control-Allow-Origin: ""` 를 실었다.
#
# 라이브 3칸 대조군(허용 오리진 / 임의 오리진 / preflight)에서 응답에 나타난 값은 **전부
# API Gateway 쪽**이었고 이 파일의 빈 문자열은 어디에도 없었다 ⇒ 죽은 코드가 틀린 값을
# 들고 있었던 것이다. (a)를 걷어내는 변경이 오는 날 그 빈 문자열이 전면에 나서서 **모든
# 오리진을 차단**한다.
#
# 그래서 두 집 중 **일하지 않는 쪽을 지웠다.** 헤더를 여기서 또 실으면 API Gateway 가 넣는
# 것과 겹쳐 `Access-Control-Allow-Origin` 이 두 번 나갈 수 있고, 브라우저는 중복을 거부한다.
# 🔵 이 방향이 안전한 실패 쪽이기도 하다: 만약 API Gateway 의 CORS 설정이 사라지면 헤더가
# **아예 없어져 브라우저에서 즉시 깨진다** — `"*"` 로 폴백해 조용히 전부 허용하는 것보다 낫다.

# 데모 호스트에 저장소가 클론된 경로(packer 단계 2). demo-boot.sh/demo-down.sh 가 여기 있다.
REPO = "/opt/monorepo-lab"

# 도메인 화이트리스트 (TASK-MONO-477). SSM RunShellScript 로 넘기는 이름은 **반드시**
# 이 집합으로 검증한다 — 검증 없이 사용자 입력을 셸 명령에 넣으면 명령 주입이 된다.
# 출처는 projects.sh 의 COMPOSE 키. "full"/"demo-core" 는 프로파일, "all" 은 전체 종료.
DOMAINS = frozenset({"iam", "wms", "scm", "finance", "erp", "ecommerce", "fan", "console"})
START_NAMES = DOMAINS | {"full", "demo-core"}
STOP_NAMES = DOMAINS | {"all"}

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
        # CORS 헤더는 여기서 싣지 않는다 — API Gateway 가 유일한 집이다(위 § 참조).
        "headers": {"Content-Type": "application/json"},
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


# ---- 도메인별 선택 (TASK-MONO-477) -----------------------------------------

def _send(commands):
    """SSM RunShellScript 로 인스턴스에서 명령을 실행하고 CommandId 를 돌려준다."""
    r = ssm.send_command(
        InstanceIds=[INSTANCE_ID],
        DocumentName="AWS-RunShellScript",
        Comment="demo per-domain control",
        Parameters={"commands": commands, "executionTimeout": ["1800"]},
    )
    return r["Command"]["CommandId"]


def _body_name(event):
    """요청 본문(JSON)에서 name 을 꺼낸다. 없거나 파싱 실패면 ""."""
    raw = event.get("body") or ""
    try:
        data = json.loads(raw) if raw else {}
    except (ValueError, TypeError):
        data = {}
    name = data.get("name", "") if isinstance(data, dict) else ""
    return name.strip() if isinstance(name, str) else ""


def _parse_health(raw):
    """(domains, published_at) — 두 스냅샷 모양을 다 읽는다.

    새 모양: {"published_at": <epoch>, "domains": {...}}   ← demo-status-publish.sh
    옛 모양: {"iam": {...}, ...}                            ← 구 AMI / terraform 초기값 {}

    옛 모양에는 published_at 이 **없으므로 None** 을 돌려주고, 호출자가 그것을 stale 로
    떨어뜨린다. 부재를 신선함으로 읽지 않는다.
    """
    try:
        parsed = json.loads(raw) if raw else {}
    except (ValueError, TypeError):
        return {}, None
    if not isinstance(parsed, dict):
        return {}, None

    inner = parsed.get("domains")
    if isinstance(inner, dict):
        at = parsed.get("published_at")
        # bool 은 int 의 서브클래스다 — True 가 epoch 1 로 통과하면 안 된다.
        if isinstance(at, (int, float)) and not isinstance(at, bool):
            return inner, int(at)
        return inner, None
    return parsed, None


def domains():
    """인스턴스 상태 + 도메인별 헬스 스냅샷.

    스냅샷은 인스턴스의 demo-status.timer 가 SSM 에 발행한다(비동기). VM 이 running 이
    아니면 스냅샷은 stale 이므로 전부 down 으로 본다 — 손상/부재를 '전부 up' 으로 읽지 않는다.

    🔴 TASK-MONO-551 결함 B — **발행이 끊긴 것과 정상인 것이 구별 가능해야 한다.**
    발행자가 죽어도 SSM 파라미터는 마지막 값 그대로 남는다. 이 함수는 예전에 그것을
    타임스탬프 없이 반환했고, 그래서 12.8분 묵은 `99/102 정상` 이 *"지금 상태"* 로 읽혔다
    — 그 순간 호스트는 15분째 무응답이었다. 나는 이 화면을 근거로 "곧 테스트 가능" 이라고
    보고할 뻔했고, 면접관도 같은 화면을 본다: **런처는 초록인데 아무 링크도 안 열린다.**

    🔴 stale 일 때 도메인 상태를 **그대로 실어 보내지 않는다.** 상단에 플래그만 얹고 원래
    state 를 남기면, 그 플래그를 안 보는 소비자는 여전히 초록을 그린다 — 그리고 이 결함의
    사용자 표면은 정확히 그 배지다. 그래서 각 도메인의 `state` 자체를 `"stale"` 로 바꾼다.
    healthy/total 은 진단용으로 남긴다.
    """
    state, ip, _ = _state()
    if state != "running":
        # 인스턴스가 꺼져 있다는 사실은 state 가 이미 정확히 말한다. 발행이 없는 게
        # 정상인 구간이므로 stale 이라고 부르지 않는다 — 그건 다른 사실이다.
        return _resp({"state": state, "ip": ip, "domains": {},
                      "health_age_seconds": None, "health_stale": False})

    snap, published_at = _parse_health(_get(HEALTH_PARAM))
    age = None if published_at is None else max(0, _now() - published_at)
    # 시계 오차로 age 가 음수가 되는 쪽(인스턴스 시계가 앞섬)은 max(0,…) 이 흡수한다.
    # 임계는 발행 주기(30초)의 3배 — chrony 오차보다 충분히 크다.
    stale = age is None or age > HEALTH_STALE_AFTER_SECONDS
    if stale:
        snap = {k: (dict(v, state="stale") if isinstance(v, dict) else v)
                for k, v in snap.items()}
    return _resp({"state": state, "ip": ip, "domains": snap,
                  "health_age_seconds": age, "health_stale": stale})


def domain_start(event):
    name = _body_name(event)
    if name not in START_NAMES:
        return _resp({"error": "invalid-domain", "name": name, "valid": sorted(START_NAMES)}, 400)
    state, _, _ = _state()
    if state != "running":
        return _resp({"state": state,
                      "message": "먼저 데모를 시작하세요 (인스턴스가 켜져 있어야 도메인을 올릴 수 있습니다)"}, 409)
    u = _usage()
    if _budget_exhausted(u):
        return _resp({"error": "monthly-budget-exhausted",
                      "message": f"이번 달 예산 소진 ({u['seconds'] // 60}/{BUDGET_MINUTES}분)"}, 429)
    # demo-boot.sh 를 경유한다 — 인스턴스가 IMDS 로 DEMO_DOMAIN 을 파생한 뒤 demo-up.sh <name>
    # 을 부른다. demo-up.sh 를 직접 부르면 DEMO_DOMAIN=local 로 떠 라우터가 *.local 이 되고
    # 방문자가 도달할 수 없다(MONO-358). name 은 START_NAMES 로 검증됐으므로 주입 안전.
    cmd = _send([f"bash {REPO}/infra/demo/demo-boot.sh {name}"])
    return _resp({"state": "running", "domain": name, "action": "start",
                  "command_id": cmd, "message": f"{name} 기동 요청됨 — 웜업까지 잠시 걸립니다"})


def domain_stop(event):
    name = _body_name(event)
    if name not in STOP_NAMES:
        return _resp({"error": "invalid-domain", "name": name, "valid": sorted(STOP_NAMES)}, 400)
    state, _, _ = _state()
    if state != "running":
        return _resp({"state": state, "message": "인스턴스가 켜져 있지 않습니다"}, 409)
    # "all" = 전체 종료(demo-down.sh 무인자 → traefik 포함). 그 외 = 부분 종료(잔존 가드 적용).
    down = f"bash {REPO}/infra/demo/demo-down.sh" + ("" if name == "all" else f" {name}")
    cmd = _send([down])
    return _resp({"state": "running", "domain": name, "action": "stop",
                  "command_id": cmd, "message": f"{name} 종료 요청됨"})


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
    # 도메인 라우트를 먼저 본다 — "/domain/start" 는 "/start" 로도 끝나므로 순서가 load-bearing.
    if path.endswith("/domains"):
        return domains()
    if path.endswith("/domain/start"):
        return domain_start(event)
    if path.endswith("/domain/stop"):
        return domain_stop(event)
    if path.endswith("/start"):
        return start()
    if path.endswith("/stop"):
        return stop()
    if path.endswith("/status"):
        return status()
    if path.endswith("/heartbeat"):
        return heartbeat()
    return _resp({"error": "not found", "path": path}, 404)
