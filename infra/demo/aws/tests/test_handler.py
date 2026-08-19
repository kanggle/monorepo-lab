"""월 예산 가드 테스트.

이 파일은 lambda/ 밖에 둔다 — terraform 의 archive_file 이 lambda/ 를 통째로
zip 으로 굽기 때문에, 안에 두면 테스트와 스텁이 배포 아티팩트에 섞여 들어간다.

왜 테스트하는가: monthly-budget 가드는 공개 /start 버튼과 청구서 사이에 서 있는
유일한 코드다. "코드가 맞아 보인다"는 근거가 못 된다 — 산술과 상태 전이를 실제로
돌려서, 특히 **가드가 열리는 방향으로 실패하지 않는지**(fail-open) 확인한다.

실행: python tests/test_handler.py     (boto3 불필요 — 스텁 주입)
"""
import datetime
import json
import os
import sys
import types
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "terraform", "lambda"))


# --- boto3 스텁 --------------------------------------------------------------
# handler 는 import 시점에 boto3.client() 를 호출하므로, import 전에 심어야 한다.
class _FakeParamNotFound(Exception):
    pass


class _FakeSSM:
    def __init__(self):
        self.store = {}
        self.sent = []  # send_command 호출 기록 (TASK-MONO-477)
        self.exceptions = types.SimpleNamespace(ParameterNotFound=_FakeParamNotFound)

    def put_parameter(self, Name, Value, Type, Overwrite):
        self.store[Name] = str(Value)

    def get_parameter(self, Name):
        if Name not in self.store:
            raise _FakeParamNotFound(Name)
        return {"Parameter": {"Value": self.store[Name]}}

    def send_command(self, InstanceIds, DocumentName, Comment, Parameters):
        self.sent.append({"instances": InstanceIds, "doc": DocumentName, "params": Parameters})
        return {"Command": {"CommandId": "cmd-fake"}}


class _FakeEC2:
    def __init__(self):
        self.state = "stopped"
        self.start_calls = 0
        self.stop_calls = 0
        # EC2 는 LaunchTime 을 tz-aware datetime 으로 준다. 테스트도 그 모양을 지킨다 —
        # handler 가 `.timestamp()` 를 부르므로 naive 로 두면 실제와 다른 값이 나온다.
        self.launch_time = datetime.datetime.fromtimestamp(0, datetime.timezone.utc)

    def describe_instances(self, InstanceIds):
        return {"Reservations": [{"Instances": [
            {
                "State": {"Name": self.state},
                "PublicIpAddress": "1.2.3.4",
                "LaunchTime": self.launch_time,
            }
        ]}]}

    def start_instances(self, InstanceIds):
        self.start_calls += 1
        self.state = "running"

    def stop_instances(self, InstanceIds):
        self.stop_calls += 1
        self.state = "stopping"


FAKE_SSM = _FakeSSM()
FAKE_EC2 = _FakeEC2()

fake_boto3 = types.ModuleType("boto3")
fake_boto3.client = lambda svc: FAKE_EC2 if svc == "ec2" else FAKE_SSM
sys.modules["boto3"] = fake_boto3

os.environ.update({
    "INSTANCE_ID": "i-test",
    "BEAT_PARAM": "/t/beat",
    "STARTED_PARAM": "/t/started",
    "USAGE_PARAM": "/t/usage",
    "HEALTH_PARAM": "/t/health",
    "IDLE_MINUTES": "20",
    "MAX_RUNTIME_MINUTES": "180",
    "MONTHLY_BUDGET_MINUTES": "60",  # 테스트는 1시간 예산
    # 🔴 `ALLOWED_ORIGIN` 은 **일부러 남겨 심는다**(TASK-MONO-557). 핸들러가 이걸 다시
    # 읽기 시작하면 `test_responses_carry_no_cors_headers` 가 물어야 하는데, 환경변수를
    # 지워 버리면 그 테스트는 *"값이 없어서"* 통과한다 — 행사된 적 없는 네거티브 테스트가
    # 되고, 정작 회귀는 못 본다. 눈에 띄는 값을 넣어 두면 새는 순간 그 문자열이 나온다.
    "ALLOWED_ORIGIN": "https://should-not-appear.example",
})

import handler  # noqa: E402


BUDGET_SEC = handler.BUDGET_MINUTES * 60  # 3600

# 가짜 시계의 앵커. 2027-01-15 언저리 — 달 중순이라 하루를 더해도 같은 달이다.
#
# 하나로 통일하는 것이 중요하다: handler._month() 는 내부에서 _now() 를 부르므로,
# _now 만 목킹하고 저장된 usage 의 month 는 실제 달로 두면 둘이 어긋나 **월 롤오버
# 리셋이 엉뚱하게 발동**한다(usage 가 0 으로 초기화되어 예산 초과가 감지되지 않는다).
# 실제로 이 테스트를 처음 돌렸을 때 3건이 그렇게 실패했다 — 핸들러가 아니라 테스트의
# 결함이었지만, 눈으로만 봤다면 "가드가 동작한다"고 넘겼을 자리다.
T0 = 1_800_000_000


def body(resp):
    return json.loads(resp["body"])


def launched(ts):
    return datetime.datetime.fromtimestamp(ts, datetime.timezone.utc)


class BudgetGuardTest(unittest.TestCase):
    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_EC2.state = "stopped"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0
        FAKE_EC2.launch_time = launched(0)

    def usage(self):
        return json.loads(FAKE_SSM.store["/t/usage"])

    def set_usage(self, seconds, tick=0, month=None):
        FAKE_SSM.store["/t/usage"] = json.dumps({
            "month": month or handler._month(T0),
            "seconds": seconds,
            "tick": tick,
        })

    def test_fake_clock_stays_within_one_month(self):
        """앵커 검증 — 이 테스트들이 쓰는 시간 범위가 달을 넘지 않아야 한다.

        넘으면 다른 테스트들이 '월 롤오버 리셋' 때문에 거짓 통과/실패한다.
        """
        self.assertEqual(handler._month(T0), handler._month(T0 + 86400))
        self.assertEqual(handler._month(T0), handler._month(T0 + 13 * 300))

    # -- 누적 ---------------------------------------------------------------
    def test_idle_check_accumulates_running_time(self):
        """running 인 동안 틱 간격이 누적된다."""
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.idle_check()            # 첫 틱: tick 만 심고 0 누적
        self.assertEqual(self.usage()["seconds"], 0)

        with mock.patch.object(handler, "_now", return_value=T0 + 300):
            FAKE_SSM.store["/t/beat"] = str(T0 + 300)
            FAKE_SSM.store["/t/started"] = str(T0)
            handler.idle_check()            # 두번째 틱: +300s
        self.assertEqual(self.usage()["seconds"], 300)

    def test_stopped_clears_tick_so_downtime_is_not_billed(self):
        """정지 구간이 다음 틱에 몰려 계상되면 안 된다."""
        self.set_usage(seconds=100, tick=T0)
        FAKE_EC2.state = "stopped"
        with mock.patch.object(handler, "_now", return_value=T0 + 86400):
            handler.idle_check()
        u = self.usage()
        self.assertEqual(u["tick"], 0, "정지 시 tick 이 끊겨야 한다")
        self.assertEqual(u["seconds"], 100, "정지 구간은 누적되지 않아야 한다")

    def test_tick_gap_is_clamped(self):
        """Lambda 가 오래 죽어 있었어도 한 틱에 몰아서 계상하지 않는다."""
        self.set_usage(seconds=0, tick=T0)
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0 + 86400):  # 하루
            FAKE_SSM.store["/t/beat"] = str(T0 + 86400)
            FAKE_SSM.store["/t/started"] = str(T0 + 86400)
            handler.idle_check()
        self.assertEqual(self.usage()["seconds"], handler.MAX_TICK_SECONDS)

    # -- 상한 집행 -----------------------------------------------------------
    def test_budget_exhaustion_stops_the_instance(self):
        FAKE_EC2.state = "running"
        self.set_usage(seconds=BUDGET_SEC, tick=0)
        with mock.patch.object(handler, "_now", return_value=T0):
            FAKE_SSM.store["/t/beat"] = str(T0)
            FAKE_SSM.store["/t/started"] = str(T0)
            r = handler.idle_check()
        self.assertTrue(r["stopped"])
        self.assertIn("monthly-budget", r["reason"])
        self.assertEqual(FAKE_EC2.stop_calls, 1)

    def test_start_is_refused_when_budget_exhausted(self):
        FAKE_EC2.state = "stopped"
        self.set_usage(seconds=BUDGET_SEC, tick=0)
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.start()
        self.assertEqual(resp["statusCode"], 429)
        self.assertEqual(body(resp)["error"], "monthly-budget-exhausted")
        self.assertEqual(FAKE_EC2.start_calls, 0, "예산 소진 시 인스턴스를 켜면 안 된다")

    def test_start_is_allowed_below_budget(self):
        FAKE_EC2.state = "stopped"
        self.set_usage(seconds=BUDGET_SEC - 60, tick=0)
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.start()
        self.assertEqual(resp["statusCode"], 200)
        self.assertEqual(FAKE_EC2.start_calls, 1)

    def test_repeated_start_cannot_evade_the_budget(self):
        """/start 반복은 idle/max 타이머를 리셋하지만 예산은 리셋하지 못한다.

        이 테스트가 가드의 존재 이유다 — idle-stop 과 max-runtime 만으로는
        /start 를 계속 부르는 것만으로 24/7 가동이 가능하다.
        """
        FAKE_EC2.state = "running"
        # 5분 틱 × 13회 = 65분 > 예산 60분
        for i in range(13):
            now = T0 + i * 300
            with mock.patch.object(handler, "_now", return_value=now):
                handler.start()                      # 매 틱마다 재기동 시도(공격자)
                FAKE_SSM.store["/t/beat"] = str(now)
                FAKE_SSM.store["/t/started"] = str(now)
                handler.idle_check()
        self.assertGreater(FAKE_EC2.stop_calls, 0, "예산을 넘겼는데 한 번도 안 멈췄다")
        # 이후 start 는 거절
        FAKE_EC2.state = "stopped"
        with mock.patch.object(handler, "_now", return_value=T0 + 13 * 300):
            self.assertEqual(handler.start()["statusCode"], 429)

    # -- fail-safe ----------------------------------------------------------
    def test_corrupt_usage_does_not_read_as_unlimited(self):
        """손상된 값이 '예산 무제한'으로 읽히면 가드가 열린 채 실패한다."""
        FAKE_SSM.store["/t/usage"] = "}{ not json"
        with mock.patch.object(handler, "_now", return_value=T0):
            u = handler._usage()
            self.assertEqual(u["month"], handler._month())
        self.assertEqual(u["seconds"], 0)
        self.assertFalse(handler._budget_exhausted(u))

    def test_month_rollover_resets(self):
        self.set_usage(seconds=BUDGET_SEC, tick=123, month="1999-01")
        with mock.patch.object(handler, "_now", return_value=T0):
            u = handler._usage()
        self.assertEqual(u["seconds"], 0)
        self.assertEqual(u["tick"], 0, "지난달 tick 으로 이번달을 계상하면 안 된다")

    def test_missing_usage_param_defaults_to_zero(self):
        with mock.patch.object(handler, "_now", return_value=T0):
            u = handler._usage()
        self.assertEqual(u["seconds"], 0)


class FreshlyLaunchedInstanceTest(unittest.TestCase):
    """idle-check 가 **갓 켜진 인스턴스를 웜업 도중에 죽이지 않는가.**

    이 테스트가 없는 동안 실제로 벌어진 일(TASK-MONO-389 에서 실측):
    terraform 이 beat/started 를 `value = "0"` 으로 만들어 두므로 `now - 0` ≈ 17억 초 ⇒
    `apply` 직후 첫 틱(5분 이내)이 인스턴스를 정지시킨다. 그 정지가 스택 웜업 한복판을
    잘라 kafka 의 KRaft 로그 디렉터리를 반쯤 쓴 채로 남겼고, 그 뒤 **모든** 부팅에서
    kafka 가 기동을 거부해 console 이 영영 뜨지 않았다.

    handler 에는 `_get(BEAT_PARAM, now)` 라는 안전 기본값이 **있었다.** 그러나 그것은
    파라미터가 **없을 때만** 쓰이고 terraform 이 항상 만들어 두므로 한 번도 도달하지
    못했다 — **가드가 존재하는 것과 물 기회를 얻는 것은 다른 명제다.**

    그래서 아래 4건은 대칭으로 간다: 오탐이 사라졌는가(1·2) **그리고 진짜 초과는 여전히
    무는가(3·4)**. 후자가 없으면 이 수정은 가드를 끈 것과 구별되지 않는다.
    """

    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_EC2.state = "running"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0
        FAKE_EC2.launch_time = launched(0)

    def test_terraform_sentinel_zero_does_not_stop_a_warming_instance(self):
        """terraform 이 심은 "0" 그대로, 2분 전에 켜진 인스턴스 → 끄면 안 된다."""
        FAKE_EC2.launch_time = launched(T0 - 120)
        FAKE_SSM.store["/t/beat"] = "0"
        FAKE_SSM.store["/t/started"] = "0"
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.idle_check()
        self.assertEqual(FAKE_EC2.stop_calls, 0, "갓 켜진(2분) 인스턴스를 꺼서는 안 된다")
        self.assertFalse(r["stopped"])
        # 두 시계가 **기동 시각**으로 잡혔다는 것까지 못박는다. stop_calls 만 보면
        # "우연히 안 껐다" 와 "올바른 기준점을 썼다" 를 구별하지 못한다.
        self.assertEqual(r["idle_sec"], 120)
        self.assertEqual(r["run_sec"], 120)

    def test_stale_beat_from_a_previous_boot_does_not_stop_it(self):
        """지난 세션의 하트비트(1시간 전)가 남아 있어도 방금 켜졌으면 살아남는다."""
        FAKE_EC2.launch_time = launched(T0 - 60)
        FAKE_SSM.store["/t/beat"] = str(T0 - 3600)
        FAKE_SSM.store["/t/started"] = str(T0 - 3600)
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.idle_check()
        self.assertEqual(FAKE_EC2.stop_calls, 0, "기동 시각이 옛 하트비트를 이겨야 한다")

    def test_idle_guard_still_bites_once_the_window_really_elapses(self):
        """켜진 지 1시간, 하트비트 없음 → idle 20m 초과로 **정지해야 한다.**"""
        FAKE_EC2.launch_time = launched(T0 - 3600)
        FAKE_SSM.store["/t/beat"] = "0"
        FAKE_SSM.store["/t/started"] = "0"
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.idle_check()
        self.assertEqual(FAKE_EC2.stop_calls, 1, "진짜 유휴는 여전히 물어야 한다")
        self.assertIn("idle", r["reason"])

    def test_max_runtime_guard_still_bites_while_beating(self):
        """하트비트가 계속 와도 4시간이면 max-runtime 3h 로 **정지해야 한다.**"""
        FAKE_EC2.launch_time = launched(T0 - 4 * 3600)
        FAKE_SSM.store["/t/beat"] = str(T0)          # 방금 하트비트
        FAKE_SSM.store["/t/started"] = "0"           # 센티널 — 기동 시각이 이긴다
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.idle_check()
        self.assertEqual(FAKE_EC2.stop_calls, 1, "세션 상한은 하트비트로 우회될 수 없다")
        self.assertIn("max-runtime", r["reason"])


class DomainControlTest(unittest.TestCase):
    """도메인별 선택 (TASK-MONO-477).

    가장 중요한 것은 **명령 주입 방지**다: /domain/{start,stop} 이 받은 name 을 SSM
    RunShellScript 로 넘기므로, 화이트리스트 밖 입력은 명령을 보내기 전에 400 으로 막아야
    한다. 그리고 대칭으로 — 정상 이름은 올바른 스크립트(start=demo-boot 경유로 도메인 파생)
    를 부르는지, VM 이 꺼져 있으면 거절하는지, 예산 소진을 상속하는지 확인한다.
    """

    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_SSM.sent.clear()
        FAKE_EC2.state = "running"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0
        FAKE_EC2.launch_time = launched(0)

    def _evt(self, name):
        return {"body": json.dumps({"name": name})}

    def _fresh_usage(self):
        FAKE_SSM.store["/t/usage"] = json.dumps(
            {"month": handler._month(T0), "seconds": 0, "tick": 0})

    def test_domain_start_sends_command_via_boot(self):
        self._fresh_usage()
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domain_start(self._evt("fan"))
        self.assertEqual(resp["statusCode"], 200)
        self.assertEqual(len(FAKE_SSM.sent), 1)
        cmd = FAKE_SSM.sent[0]["params"]["commands"][0]
        # demo-boot.sh 경유여야 한다 — 그래야 인스턴스가 DEMO_DOMAIN 을 파생한다.
        self.assertIn("demo-boot.sh fan", cmd)

    def test_domain_start_rejects_unknown_name_without_sending(self):
        resp = handler.domain_start(self._evt("bogus; rm -rf /"))
        self.assertEqual(resp["statusCode"], 400)
        self.assertEqual(len(FAKE_SSM.sent), 0, "검증 실패 시 명령을 보내면 안 된다(주입 방지)")

    def test_domain_start_requires_running_vm(self):
        FAKE_EC2.state = "stopped"
        resp = handler.domain_start(self._evt("fan"))
        self.assertEqual(resp["statusCode"], 409)
        self.assertEqual(len(FAKE_SSM.sent), 0)

    def test_domain_start_refused_when_budget_exhausted(self):
        FAKE_SSM.store["/t/usage"] = json.dumps(
            {"month": handler._month(T0), "seconds": BUDGET_SEC, "tick": 0})
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domain_start(self._evt("fan"))
        self.assertEqual(resp["statusCode"], 429)
        self.assertEqual(len(FAKE_SSM.sent), 0)

    def test_domain_stop_partial_passes_domain_arg(self):
        resp = handler.domain_stop(self._evt("console"))
        self.assertEqual(resp["statusCode"], 200)
        self.assertIn("demo-down.sh console", FAKE_SSM.sent[0]["params"]["commands"][0])

    def test_domain_stop_all_downs_everything(self):
        resp = handler.domain_stop(self._evt("all"))
        self.assertEqual(resp["statusCode"], 200)
        # "all" = 무인자 demo-down.sh (traefik 포함 전체 종료)
        self.assertTrue(
            FAKE_SSM.sent[0]["params"]["commands"][0].rstrip().endswith("demo-down.sh"))

    # ---- 헬스 스냅샷 신선도 (TASK-MONO-551 결함 B) ---------------------------
    #
    # 발행자가 죽어도 SSM 파라미터는 마지막 값 그대로 남는다. 예전 domains() 는 그것을
    # 타임스탬프 없이 반환했으므로 *"방금 잰 값"* 과 *"13분 전 값"* 이 **바이트 단위로
    # 구별 불가**였다 — 실측된 12.8분 묵은 `99/102 정상` 이 그렇게 읽혔고, 그 순간
    # 호스트는 15분째 무응답이었다.
    #
    # 🔴 대조군이 이 묶음의 본체다. "전부 stale 로 만든다" 는 구현도 아래 stale 케이스를
    #    전부 통과시키므로, **신선한 스냅샷이 up 으로 남는지**를 반드시 함께 본다.
    #    (이 클래스의 첫 버전은 평평한 스냅샷을 up 으로 단언했다 — 그 단언이 곧 결함이었다.)

    def _publish(self, at, **domains):
        """발행자가 쓰는 것과 같은 모양으로 스냅샷을 심는다."""
        FAKE_SSM.store["/t/health"] = json.dumps(
            {"published_at": at, "domains": domains})

    def test_domains_reads_fresh_snapshot_when_running(self):
        # 대조군 — 방금 발행된 스냅샷은 그대로 up 이어야 한다.
        self._publish(T0, iam={"state": "up", "healthy": 5, "total": 5})
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domains()
        self.assertEqual(resp["statusCode"], 200)
        self.assertEqual(body(resp)["domains"]["iam"]["state"], "up")
        self.assertFalse(body(resp)["health_stale"])
        self.assertEqual(body(resp)["health_age_seconds"], 0)

    def test_domains_snapshot_older_than_threshold_is_stale(self):
        self._publish(T0, iam={"state": "up", "healthy": 5, "total": 5})
        # 실측된 얼어붙음은 12.8분(768초)이었다. 그 값 그대로 재현한다.
        with mock.patch.object(handler, "_now", return_value=T0 + 768):
            resp = handler.domains()
        b = body(resp)
        self.assertTrue(b["health_stale"])
        self.assertEqual(b["health_age_seconds"], 768)
        self.assertEqual(b["domains"]["iam"]["state"], "stale",
                         "플래그만 얹고 up 을 남기면 그 플래그를 안 보는 소비자는 초록을 그린다")
        # healthy/total 은 진단용으로 남긴다 — 지우면 왜 stale 인지 볼 수 없다.
        self.assertEqual(b["domains"]["iam"]["healthy"], 5)

    def test_domains_just_at_threshold_is_not_stale(self):
        # 경계 — 한 번 놓친 발행으로 빨개지면 그 판정은 곧 무시된다.
        self._publish(T0, iam={"state": "up", "healthy": 5, "total": 5})
        with mock.patch.object(handler, "_now",
                               return_value=T0 + handler.HEALTH_STALE_AFTER_SECONDS):
            resp = handler.domains()
        self.assertFalse(body(resp)["health_stale"])
        self.assertEqual(body(resp)["domains"]["iam"]["state"], "up")

    def test_domains_snapshot_without_published_at_is_stale(self):
        """🔴 이 티켓의 핵심 축 — **부재를 신선함으로 읽지 않는다.**

        구 AMI 가 쓴 평평한 스냅샷에는 published_at 이 없다. 없는 것을 '0초 전' 으로
        읽으면 재굽기 전의 데모가 영원히 초록으로 보고된다.
        """
        FAKE_SSM.store["/t/health"] = json.dumps(
            {"iam": {"state": "up", "healthy": 5, "total": 5}})
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domains()
        b = body(resp)
        self.assertTrue(b["health_stale"])
        self.assertIsNone(b["health_age_seconds"])
        self.assertEqual(b["domains"]["iam"]["state"], "stale")

    def test_domains_terraform_initial_empty_object_is_stale(self):
        # apply 직후 파라미터는 `{}` 다. '도메인이 없다' 가 아니라 '아직 모른다' 이다.
        FAKE_SSM.store["/t/health"] = "{}"
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domains()
        self.assertTrue(body(resp)["health_stale"])
        self.assertEqual(body(resp)["domains"], {})

    def test_domains_published_at_true_is_not_an_epoch(self):
        # bool 은 int 의 서브클래스다 — 타입을 명시적으로 거르지 않으면 True 가 epoch 1 로
        # 통과한다. 그러면 우연히 stale 이 되지만, 우연히 맞는 것은 맞는 것이 아니다.
        FAKE_SSM.store["/t/health"] = json.dumps(
            {"published_at": True, "domains": {"iam": {"state": "up"}}})
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domains()
        self.assertIsNone(body(resp)["health_age_seconds"])
        self.assertTrue(body(resp)["health_stale"])

    def test_domains_clock_skew_forward_does_not_yield_negative_age(self):
        # 인스턴스 시계가 Lambda 보다 앞서면 age 가 음수가 된다. 응답에 말이 안 되는
        # 숫자가 실리고, 임계 비교의 의미도 흐려진다.
        self._publish(T0 + 5, iam={"state": "up", "healthy": 5, "total": 5})
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domains()
        self.assertEqual(body(resp)["health_age_seconds"], 0)
        self.assertFalse(body(resp)["health_stale"])

    def test_domains_hides_stale_snapshot_when_stopped(self):
        FAKE_EC2.state = "stopped"
        self._publish(T0, iam={"state": "up", "healthy": 5, "total": 5})
        resp = handler.domains()
        self.assertEqual(body(resp)["domains"], {}, "VM 이 꺼졌으면 스냅샷은 stale — 전부 감춘다")

    def test_domains_corrupt_snapshot_is_empty_not_crash(self):
        FAKE_SSM.store["/t/health"] = "}{ not json"
        with mock.patch.object(handler, "_now", return_value=T0):
            resp = handler.domains()
        self.assertEqual(body(resp)["domains"], {})
        self.assertTrue(body(resp)["health_stale"],
                        "파싱 실패는 '도메인이 없다' 가 아니라 '모른다' 다")


class CorsHasOneHome(unittest.TestCase):
    """TASK-MONO-557 — CORS 의 집은 API Gateway 하나다.

    예전에는 이 핸들러도 `ALLOWED_ORIGIN` 을 읽어 헤더를 실었고, 같은 사실이 두 집을
    갖고 있었다. 2026-08-18 실측이 그 두 집이 **이미 어긋나 있었음**을 보였다: terraform
    의 `var.allowed_origin` 이 `""` 였는데 API Gateway 는 그걸 폴백으로 해소한 반면,
    `os.environ.get` 은 키가 존재하므로 기본값 `"*"` 를 쓰지 않고 `""` 를 그대로 실었다
    (`Access-Control-Allow-Origin: ""`). 라이브 응답에 나타난 값은 전부 API Gateway 쪽
    이었으므로, 그 헤더는 **틀린 값을 든 죽은 코드**였다.

    두 곳에서 실으면 헤더가 중복되어 브라우저가 거부하기도 한다. 그래서 일하지 않는
    쪽을 지웠고, 이 테스트가 그게 돌아오지 못하게 한다.
    """

    def _all_responses(self):
        """헤더를 내는 경로를 **모아서** 본다 — 한 곳만 보면 나머지로 샌다."""
        FAKE_EC2.state = "stopped"
        FAKE_SSM.store.clear()
        with mock.patch.object(handler, "_now", return_value=T0):
            return {
                "status": handler.status(),
                "domains": handler.domains(),
            }

    def test_responses_carry_no_cors_headers(self):
        for name, resp in self._all_responses().items():
            with self.subTest(response=name):
                keys = [k for k in resp["headers"] if k.lower().startswith("access-control-")]
                self.assertEqual(
                    keys, [],
                    f"{name} 이 CORS 헤더를 실었습니다: {keys}. "
                    "CORS 의 집은 API Gateway 의 cors_configuration 하나입니다 "
                    "(TASK-MONO-557) — 두 곳에서 실으면 값이 갈라지고 헤더가 중복됩니다.",
                )

    def test_the_env_var_is_actually_present(self):
        """🔴 위 테스트가 *행사되는지* 를 단언한다.

        `ALLOWED_ORIGIN` 을 픽스처에서 지우면 위 테스트는 값이 없어서 통과하고, 그건
        네거티브 테스트가 한 번도 행사되지 않는 상태다. 이 저장소가 반복해서 밟은 축이라
        주입 자체를 단언한다.
        """
        self.assertEqual(os.environ.get("ALLOWED_ORIGIN"), "https://should-not-appear.example")

    def test_content_type_is_still_there(self):
        """대조군 — 헤더를 통째로 지운 구현과 구별한다."""
        for name, resp in self._all_responses().items():
            with self.subTest(response=name):
                self.assertEqual(resp["headers"].get("Content-Type"), "application/json")


if __name__ == "__main__":
    unittest.main(verbosity=2)
