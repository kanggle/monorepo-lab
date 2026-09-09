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


# ===========================================================================
# 화면 묶음 선택 기동 (TASK-MONO-634 / ADR-MONO-071)
# ===========================================================================
class BundleSelectionTest(unittest.TestCase):
    """왜 이 클래스가 있는가.

    이 티켓이 고치는 결함은 **"선택했는데 전부 뜬다"** 이고, 그 결함은 어떤 에러도 내지
    않는다 — 96개 컨테이너가 전부 정상적으로 뜬다. 즉 **로그로는 절대 안 보인다.**
    그래서 판정은 "요청이 성공했나" 가 아니라 **"무엇이 저장되고 무엇이 실행됐나"** 여야
    한다. 아래 테스트가 전부 `FAKE_SSM.store` 와 `FAKE_SSM.sent` 를 본다.
    """

    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_SSM.sent.clear()
        FAKE_EC2.state = "stopped"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0
        FAKE_EC2.launch_time = launched(0)

    def req(self, payload):
        return {"body": json.dumps(payload)}

    def selection(self):
        return json.loads(FAKE_SSM.store[handler.SELECTION_PARAM])["bundles"]

    # -- 화이트리스트 / 주입 ------------------------------------------------
    def test_unknown_bundle_is_rejected_and_nothing_is_started(self):
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.bundle_start(self.req({"bundles": ["fan", "nope"]}))
        self.assertEqual(r["statusCode"], 400)
        self.assertEqual(FAKE_EC2.start_calls, 0)
        # 🔴 **부분 수용 금지** — 유효한 'fan' 도 저장되면 안 된다. 저장되면 방문자는
        #    "둘 다 켰다" 고 믿는데 하나만 뜨고, 안 뜬 쪽은 화면에서 "고장" 으로 보인다.
        self.assertNotIn(handler.SELECTION_PARAM, FAKE_SSM.store)

    def test_shell_metacharacters_never_reach_send_command(self):
        """🔴 이 값은 결국 SSM RunShellScript 의 명령줄이 된다. 화이트리스트가 주입 방어다."""
        for evil in ["fan; rm -rf /", "$(id)", "fan console", "../../etc/passwd", "FAN"]:
            with self.subTest(payload=evil):
                FAKE_SSM.sent.clear()
                with mock.patch.object(handler, "_now", return_value=T0):
                    r = handler.bundle_start(self.req({"bundles": [evil]}))
                self.assertEqual(r["statusCode"], 400, evil)
                self.assertEqual(FAKE_SSM.sent, [], evil)

    def test_bundle_names_match_projects_sh(self):
        """🔴🔴 같은 사실이 두 집에 있다(여기 + projects.sh). 갈라지면 조용히 틀린다.

        가드 (z32)가 CI 에서 같은 대조를 하지만, 여기서도 한다 — 가드는 셸이고 이쪽은
        파이썬이라 서로의 파싱 결함을 덮어 준다.
        """
        import re
        root = os.path.join(HERE, "..", "..", "..", "..")
        with open(os.path.join(root, "infra", "demo", "projects.sh"), encoding="utf-8") as fh:
            src = fh.read()

        def names_of(var):
            m = re.search(r"declare -A " + var + r"=\((.*?)\n\)", src, re.S)
            self.assertIsNotNone(m, var + " 를 projects.sh 에서 찾지 못했습니다")
            return set(re.findall(r"^\s*\[([a-z0-9-]+)\]=", m.group(1), re.M))

        self.assertEqual(names_of("BUNDLES"), set(handler.BUNDLES))
        self.assertEqual(names_of("BUNDLE_ADDONS"), set(handler.BUNDLE_ADDONS))
        # 🔴 대조군 — 위 두 단언이 **빈 집합끼리** 비교해서 통과하는 것을 막는다.
        self.assertGreaterEqual(len(handler.BUNDLES), 3)
        self.assertGreaterEqual(len(handler.BUNDLE_ADDONS), 1)

    # -- 최초 부팅부터 선택된다 ---------------------------------------------
    def test_cold_start_persists_selection_before_starting_the_instance(self):
        """🔴🔴 이 티켓의 본체. stopped 에서 'fan' 만 골라도 선택이 **저장된다.**"""
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.bundle_start(self.req({"bundles": ["fan"]}))
        self.assertEqual(r["statusCode"], 200)
        self.assertEqual(body(r)["state"], "starting")
        self.assertEqual(self.selection(), ["fan"])
        self.assertEqual(FAKE_EC2.start_calls, 1)
        # 🔴 stopped 에서는 SSM 명령을 **안 보낸다** — 보낼 수 없다(인스턴스가 꺼져 있다).
        #    부팅이 저장된 선택을 읽는 것이 이 설계의 요점이다.
        self.assertEqual(FAKE_SSM.sent, [])

    def test_cold_start_selection_never_contains_the_full_profile(self):
        """🔴 "팬만" 이 "전부" 로 번역되지 않는다 — 그것이 고치는 결함 자체다."""
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan"]}))
        stored = FAKE_SSM.store[handler.SELECTION_PARAM]
        self.assertNotIn("full", stored)
        self.assertNotIn("console", stored)
        self.assertNotIn("store", stored)

    # -- 동시 / 중복 / 기동 중 추가 -----------------------------------------
    def test_concurrent_requests_union_rather_than_overwrite(self):
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan"]}))
            FAKE_EC2.state = "running"
            handler.bundle_start(self.req({"bundles": ["store"]}))
            handler.bundle_start(self.req({"bundles": ["console"]}))
        self.assertEqual(self.selection(), ["console", "fan", "store"])

    def test_repeating_the_same_request_does_not_run_the_command_twice(self):
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan"]}))
            first = len(FAKE_SSM.sent)
            handler.bundle_start(self.req({"bundles": ["fan"]}))
        self.assertEqual(len(FAKE_SSM.sent), first, "같은 요청 반복이 중복 실행됐습니다")

    def test_request_while_booting_is_not_lost(self):
        """🔴 'pending' 중에 온 요청도 선택에 들어가야 한다 — 유실되면 그 화면은 안 뜬다."""
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan"]}))
            FAKE_EC2.state = "pending"
            r = handler.bundle_start(self.req({"bundles": ["store"]}))
        self.assertEqual(r["statusCode"], 200)
        self.assertIn("store", self.selection())

    def test_selection_is_kept_even_when_instance_is_stopping(self):
        FAKE_EC2.state = "stopping"
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.bundle_start(self.req({"bundles": ["fan"]}))
        self.assertEqual(r["statusCode"], 409)
        # 🔵 켜지 못했지만 **선택은 남는다** — 다시 누르면 그 선택으로 뜬다.
        self.assertEqual(self.selection(), ["fan"])

    def test_warm_start_sends_the_selection_sentinel_not_a_domain_list(self):
        """🔴 명령줄에 방문자 문자열이 안 들어간다 — 센티널 하나만 간다."""
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["console-wms"]}))
        cmds = FAKE_SSM.sent[-1]["params"]["commands"]
        self.assertEqual(cmds, ["bash /opt/monorepo-lab/infra/demo/demo-boot.sh selection"])

    # -- 종료: 공유 의존을 안 내린다 ----------------------------------------
    def test_bundle_stop_never_names_iam(self):
        """🔴🔴 iam 을 내리면 남아 있는 다른 묶음의 로그인이 조용히 무너진다."""
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan", "store"]}))
            FAKE_SSM.sent.clear()
            r = handler.bundle_stop(self.req({"bundles": ["fan"]}))
        self.assertEqual(r["statusCode"], 200)
        cmd = FAKE_SSM.sent[-1]["params"]["commands"][0]
        self.assertIn("demo-down.sh fan", cmd)
        self.assertNotIn("iam", cmd)
        self.assertEqual(self.selection(), ["store"])

    def test_bundle_stop_is_serialised_by_a_lock(self):
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan"]}))
            # 잠금이 잡혀 있는 상태를 만든다
            FAKE_SSM.store[handler.LOCK_PARAM] = json.dumps({"owner": "other", "until": T0 + 60})
            r = handler.bundle_stop(self.req({"bundles": ["fan"]}))
        self.assertEqual(r["statusCode"], 409)
        self.assertEqual(body(r)["error"], "locked")

    def test_expired_lock_does_not_wedge_the_demo_forever(self):
        """🔴 만료 없는 잠금은 Lambda 가 한 번 죽으면 데모를 영구히 잠근다."""
        FAKE_EC2.state = "running"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start(self.req({"bundles": ["fan"]}))
            FAKE_SSM.store[handler.LOCK_PARAM] = json.dumps({"owner": "dead", "until": T0 - 1})
            r = handler.bundle_stop(self.req({"bundles": ["fan"]}))
        self.assertEqual(r["statusCode"], 200)

    # -- 상태: running != 준비 완료 -----------------------------------------
    def _health(self, states, age=0):
        FAKE_SSM.store["/t/health"] = json.dumps({
            "published_at": T0 - age,
            "domains": {d: {"state": st, "healthy": 1, "total": 1} for d, st in states.items()},
        })

    def test_instance_running_is_not_the_same_as_bundle_ready(self):
        """🔴🔴 이 구별이 없으면 방문자가 링크를 눌러 404 를 보고 '고장' 으로 읽는다."""
        FAKE_EC2.state = "running"
        self._health({"iam": "up", "fan": "down"})
        with mock.patch.object(handler, "_now", return_value=T0):
            handler._write_selection({"fan"})
            b = body(handler.bundles())
        self.assertEqual(b["state"], "running")
        self.assertNotEqual(b["bundles"]["fan"]["state"], "ready")
        self.assertEqual(b["bundles"]["fan"]["state"], "booting")

    def test_bundle_is_ready_only_when_iam_is_up_too(self):
        FAKE_EC2.state = "running"
        self._health({"iam": "down", "fan": "up"})
        with mock.patch.object(handler, "_now", return_value=T0):
            b = body(handler.bundles())
        self.assertNotEqual(b["bundles"]["fan"]["state"], "ready", "iam 없이 준비 완료로 표시됐습니다")

        self._health({"iam": "up", "fan": "up"})
        with mock.patch.object(handler, "_now", return_value=T0):
            b = body(handler.bundles())
        self.assertEqual(b["bundles"]["fan"]["state"], "ready")

    def test_stale_health_is_never_reported_as_ready(self):
        """🔴 stale 일 때 up 을 믿으면 꺼진 스택을 초록으로 그린다(MONO-551 결함 B)."""
        FAKE_EC2.state = "running"
        self._health({"iam": "up", "fan": "up"}, age=handler.HEALTH_STALE_AFTER_SECONDS + 1)
        with mock.patch.object(handler, "_now", return_value=T0):
            b = body(handler.bundles())
        self.assertTrue(b["health_stale"])
        self.assertEqual(b["bundles"]["fan"]["state"], "unknown")

    def test_stopped_instance_distinguishes_selected_from_waiting(self):
        """🔴🔴 TASK-MONO-653 — 이 단언은 `requested` 였고, 그것이 결함이었다.

        `stopped` + 선택됨은 «저장된 선택에 있다» 이지 «지금 뜨는 중» 이 아니다. 론처는
        `requested` 를 후자로 그리고 버튼을 잠그므로, 한 번 고른 묶음의 시작 버튼이
        인스턴스가 멈춘 뒤 **영구히 죽었다.** 값을 갈라 `selected` 로 만들었다.
        """
        FAKE_EC2.state = "stopped"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler._write_selection({"fan"})
            b = body(handler.bundles())
        self.assertEqual(b["bundles"]["fan"]["state"], "selected")
        self.assertEqual(b["bundles"]["store"]["state"], "waiting")

    def test_pending_instance_is_requested_not_selected(self):
        """🔴🔴 대조군 — `pending` 을 `stopped` 와 같이 묶으면 안 된다.

        켜지는 중이면 「기동 중」이 **참**이므로 버튼은 잠긴 채여야 한다. 여기서 `selected`
        가 나오면 론처가 버튼을 열고, 방문자가 기동 중에 또 눌러 **중복 요청**이 된다 —
        이 티켓의 Failure 2 다. 이 칸이 없으면 «`stopped` 아니면 전부 `selected`» 라는
        더 단순하고 **틀린** 구현이 초록으로 통과한다.
        """
        FAKE_EC2.state = "pending"
        with mock.patch.object(handler, "_now", return_value=T0):
            handler._write_selection({"fan"})
            b = body(handler.bundles())
        self.assertEqual(b["bundles"]["fan"]["state"], "requested")
        # 선택 안 된 묶음은 인스턴스 상태와 무관하게 `waiting` 이다(대조군의 대조군).
        self.assertEqual(b["bundles"]["store"]["state"], "waiting")

    def test_running_instance_with_all_domains_down_is_requested(self):
        """🔵 `requested` 가 **남아 있어야 하는** 자리. 값을 가르면서 이쪽을 같이 지우면
        「인스턴스는 떴는데 이 묶음이 아직」을 표현할 값이 사라진다."""
        FAKE_EC2.state = "running"
        self._health({"iam": "down", "fan": "down"})
        with mock.patch.object(handler, "_now", return_value=T0):
            handler._write_selection({"fan"})
            b = body(handler.bundles())
        self.assertEqual(b["bundles"]["fan"]["state"], "requested")

    def test_selected_and_requested_are_never_the_same_value(self):
        """🔴🔴 이 티켓의 불변식 그 자체 — 두 사실은 **다른 값**이어야 한다.

        위 세 칸은 각각 하나의 상태를 고정한다. 이 칸은 그 셋을 한 문장으로 묶어,
        누군가 «두 값을 다시 하나로 합치는» 방향으로 되돌리면 여기서도 빨개지게 한다.
        """
        seen = {}
        for ec2 in ("stopped", "pending"):
            FAKE_EC2.state = ec2
            with mock.patch.object(handler, "_now", return_value=T0):
                handler._write_selection({"fan"})
                seen[ec2] = body(handler.bundles())["bundles"]["fan"]["state"]
        self.assertNotEqual(
            seen["stopped"], seen["pending"],
            "«선택됐지만 안 떴다» 와 «켜지는 중» 이 같은 값입니다 — "
            "론처는 둘을 구별할 수 없고, 어느 쪽으로 그려도 한쪽이 거짓이 됩니다: "
            + repr(seen))

    # -- 예산 / 라우팅 -------------------------------------------------------
    def test_budget_exhausted_refuses_bundle_start(self):
        FAKE_SSM.store["/t/usage"] = json.dumps(
            {"month": handler._month(T0), "seconds": BUDGET_SEC, "tick": 0})
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.bundle_start(self.req({"bundles": ["fan"]}))
        self.assertEqual(r["statusCode"], 429)
        self.assertEqual(FAKE_EC2.start_calls, 0)

    def test_bundle_start_path_is_not_swallowed_by_the_start_route(self):
        """🔴 '/bundle/start' 도 '/start' 로 끝난다 — 디스패치 순서가 load-bearing 이다.

        순서가 틀리면 `start()` 가 불려 **선택 없이 인스턴스만 켜진다.** 그 실패는 200 을
        내므로 로그로 안 보이고, 방문자는 고른 것과 다른 것이 뜬 화면을 본다.
        """
        with mock.patch.object(handler, "_now", return_value=T0):
            r = handler.handler(
                {"requestContext": {"http": {"method": "POST", "path": "/bundle/start"}},
                 "body": json.dumps({"bundles": ["fan"]})}, None)
        self.assertEqual(self.selection(), ["fan"], "start() 로 잘못 라우팅됐습니다")
        self.assertEqual(body(r)["state"], "starting")

    def test_bundles_route_is_reachable(self):
        r = handler.handler(
            {"requestContext": {"http": {"method": "GET", "path": "/bundles"}}}, None)
        self.assertEqual(r["statusCode"], 200)
        self.assertIn("bundles", body(r))


class MaxRuntimeResetTest(unittest.TestCase):
    """🔴🔴 **반복 시작 요청이 최대 가동 시간을 리셋하던 결함** (TASK-MONO-634 발견).

    `start()` 는 상태와 무관하게 `_put(STARTED_PARAM, _now())` 를 했다. 그래서 `/start` 를
    주기적으로 부르면 `run_sec = now - started` 가 영원히 작게 유지되어 **max-runtime 가드가
    한 번도 물지 않았다.**

    🔵 이 결함이 눈에 안 띈 이유: 월 예산 가드가 지출 상한을 따로 지키고 있었다. 즉 이
    저장소는 **가드를 하나 더 만들면서 원래 가드는 고치지 않았고**, 새 가드가 피해를 가려
    주었다. handler.py 헤더가 그 시나리오를 이미 문장으로 적어 두고 있었다는 점이 특히
    나쁘다 — 알고 있었는데 그 축은 죽어 있었다.
    """

    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_SSM.sent.clear()
        FAKE_EC2.state = "stopped"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0
        FAKE_EC2.launch_time = launched(T0)

    def test_repeated_start_does_not_reset_the_max_runtime_clock(self):
        # T0 에 켠다.
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.start()
        self.assertEqual(FAKE_SSM.store["/t/started"], str(T0))

        # 4시간 동안 30분마다 /start 를 다시 누른다(= 옛 판이 시계를 리셋하던 경로).
        for minutes in range(30, 4 * 60 + 1, 30):
            t = T0 + minutes * 60
            with mock.patch.object(handler, "_now", return_value=t):
                handler.start()

        # 🔴 판정: `started` 가 **여전히 T0** 여야 한다.
        self.assertEqual(
            FAKE_SSM.store["/t/started"], str(T0),
            "반복 /start 가 최대 가동 시간 시계를 리셋했습니다 — max-runtime 가드가 죽습니다.")

        # 그리고 실제로 가드가 문다.
        t = T0 + 4 * 60 * 60
        with mock.patch.object(handler, "_now", return_value=t):
            out = handler.idle_check()
        self.assertTrue(out["stopped"])
        self.assertIn("max-runtime", out["reason"])

    def test_heartbeat_is_still_extended_by_repeated_start(self):
        """🔵 대조군 — beat 는 **연장되는 것이 의도**다. 둘을 같이 얼리면 유휴 판정이 깨진다."""
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.start()
        t = T0 + 3600
        with mock.patch.object(handler, "_now", return_value=t):
            handler.start()
        self.assertEqual(FAKE_SSM.store["/t/beat"], str(t))

    def test_bundle_start_has_the_same_property(self):
        with mock.patch.object(handler, "_now", return_value=T0):
            handler.bundle_start({"body": json.dumps({"bundles": ["fan"]})})
        FAKE_EC2.state = "running"
        for minutes in (60, 120, 180, 240):
            t = T0 + minutes * 60
            with mock.patch.object(handler, "_now", return_value=t):
                handler.bundle_start({"body": json.dumps({"bundles": ["fan"]})})
        self.assertEqual(FAKE_SSM.store["/t/started"], str(T0))
# ===========================================================================
# 라우터는 등호로 가른다 — `$default` 의 선행 조건 (TASK-MONO-644)
# ===========================================================================
class RouterIsExactTest(unittest.TestCase):
    """🔴🔴 왜 이 클래스가 생겼는가.

    644 는 «없는 경로의 404 에 CORS 헤더가 없어 론처가 그 404 를 못 본다» 를 고친다.
    HTTP API(v2) 에는 REST API(v1) 의 `gateway_response` 손잡이가 없으므로 남은 길은
    **`$default` 라우트를 더해 람다가 404 를 내게 하는 것** 하나뿐이다 — 그래야 라우트가
    매치되어 `cors_configuration` 이 적용된다.

    그런데 그 변경은 **방벽을 하나 없앤다.** `$default` 이전에는 «어떤 (메서드, 경로) 가
    람다에 도달하는가» 를 게이트웨이의 라우트 목록이 정했다. 그 뒤로는 전부 도달하고,
    그러면 옛 `path.endswith("/start")` 사슬이 `POST /아무거나/start` 를 `start()` 로
    보낸다 — CORS 를 고치려던 변경이 **인증 없는 기동 경로를 여는** 변경이 된다.

    🔴 아래 시험은 **핸들러를 직접 부른다.** 게이트웨이를 거치는 시험은 이 축을 못 잰다:
    거기서는 라우트 목록이 여전히 막아 주므로 사슬이 틀려도 초록이고, 그 초록은
    «라우터가 안전하다» 가 아니라 «아직 `$default` 를 안 넣었다» 는 뜻이다.
    """

    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_SSM.sent.clear()
        FAKE_EC2.state = "stopped"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0
        FAKE_EC2.launch_time = launched(0)

    def call(self, method, path, payload=None):
        ev = {"requestContext": {"http": {"method": method, "path": path}}}
        if payload is not None:
            ev["body"] = json.dumps(payload)
        with mock.patch.object(handler, "_now", return_value=T0):
            return handler.handler(ev, None)

    # -- 물 기회 (positive control) ------------------------------------------
    def test_the_real_route_does_start_the_instance(self):
        """🔴 아래 bite 들이 «아무것도 안 켜졌다» 로 통과하지 않게 하는 바닥.

        이 칸이 없으면 `start()` 가 어떤 이유로든 죽어 있을 때 모든 bite 가 조용히
        통과한다 — 그 초록은 «라우터가 막았다» 가 아니라 «켤 수 있는 게 없었다» 다.
        """
        r = self.call("POST", "/start")
        self.assertEqual(r["statusCode"], 200)
        self.assertEqual(FAKE_EC2.start_calls, 1, "정상 라우트가 인스턴스를 안 켰습니다")

    # -- bite: 접미사가 기동 경로에 닿으면 안 된다 ----------------------------
    def test_suffix_paths_never_reach_start(self):
        """🔴🔴 이 티켓의 bite. 옛 `endswith` 사슬에서는 **전부 `start()` 에 도달한다.**"""
        for path in ("/x/start", "/evil/start", "/api/v2/start", "//start", "/bundle/x/start"):
            with self.subTest(path=path):
                FAKE_EC2.start_calls = 0
                r = self.call("POST", path)
                self.assertEqual(
                    r["statusCode"], 404,
                    f"{path} 가 404 가 아닙니다 — 라우터가 접미사로 갈리고 있습니다",
                )
                self.assertEqual(
                    FAKE_EC2.start_calls, 0,
                    f"{path} 가 start() 에 도달해 EC2 를 켰습니다 — "
                    "$default 아래에서 이것은 인증 없는 기동 경로입니다",
                )

    def test_suffix_paths_never_reach_the_other_control_routes(self):
        """`/start` 만의 문제가 아니다 — 사슬의 **모든** 가지가 같은 성질을 갖고 있었다."""
        cases = [
            ("POST", "/x/stop", "stop_calls"),
            ("POST", "/x/bundle/start", None),
            ("POST", "/x/domain/start", None),
            ("POST", "/x/heartbeat", None),
            ("GET", "/x/status", None),
            ("GET", "/x/bundles", None),
            ("GET", "/x/domains", None),
        ]
        for method, path, counter in cases:
            with self.subTest(path=path):
                FAKE_EC2.stop_calls = 0
                r = self.call(method, path, {"bundles": ["fan"]})
                self.assertEqual(r["statusCode"], 404, f"{method} {path} 가 404 가 아닙니다")
                if counter:
                    self.assertEqual(getattr(FAKE_EC2, counter), 0)
        self.assertEqual(FAKE_SSM.sent, [], "접미사 경로가 SSM 명령을 보냈습니다")

    # -- bite: 메서드도 표의 일부다 -------------------------------------------
    def test_method_is_part_of_the_route(self):
        """🔴 `$default` 는 **메서드 필터도** 없앤다.

        라우트 목록은 `"POST /start"` 였다 — 경로와 메서드를 **함께** 걸렀다. `$default`
        아래에서 표가 경로만 보면 `GET /start` 가 EC2 를 켠다. 링크 하나, 이미지 태그
        하나, 프리페치 한 번이면 충분하다.
        """
        for method, path in (("GET", "/start"), ("GET", "/stop"), ("GET", "/heartbeat"),
                             ("POST", "/status"), ("POST", "/bundles"), ("POST", "/domains")):
            with self.subTest(method=method, path=path):
                FAKE_EC2.start_calls = 0
                FAKE_EC2.stop_calls = 0
                r = self.call(method, path)
                self.assertEqual(r["statusCode"], 404, f"{method} {path} 가 통과했습니다")
                self.assertEqual(FAKE_EC2.start_calls, 0)
                self.assertEqual(FAKE_EC2.stop_calls, 0)

    # -- 정규화는 하되 관대하지 않게 ------------------------------------------
    def test_trailing_slash_is_the_same_route(self):
        r = self.call("GET", "/status/")
        self.assertEqual(r["statusCode"], 200)

    def test_normalisation_does_not_invent_new_spellings(self):
        """🔵 대조군 — 정규화가 «표를 우회하는 철자» 를 만들어 주지 않는가."""
        for path in ("/STATUS", "/status/../start", "/status%2f", " /status"):
            with self.subTest(path=path):
                self.assertEqual(self.call("GET", path)["statusCode"], 404)

    def test_unknown_path_is_not_reflected_in_the_body(self):
        """`$default` 아래에서 경로는 임의의 외부 입력이다 — 되비추지 않는다."""
        r = self.call("GET", "/<script>alert(1)</script>")
        self.assertEqual(r["statusCode"], 404)
        self.assertNotIn("script", json.dumps(body(r)))


class RouteTableMatchesTerraformTest(unittest.TestCase):
    """🔴🔴 배선 — 핸들러의 표와 게이트웨이의 라우트 목록은 **같은 쌍**이어야 한다.

    두 곳이 어긋나면 증상이 조용하다: terraform 에만 있는 쌍은 «라우트는 있는데 404»,
    핸들러에만 있는 쌍은 `$default` 가 생긴 뒤에야 도달하는 **문서에 없는 경로**가 된다.
    어느 쪽도 에러를 내지 않는다.
    """

    ROUTES_RE = (
        r'resource\s+"aws_apigatewayv2_route"\s+"routes"\s*\{'
        r'.*?for_each\s*=\s*toset\(\[(.*?)\]\)'
    )

    def setUp(self):
        import re
        self.re = re
        path = os.path.join(HERE, "..", "terraform", "main.tf")
        self.assertTrue(os.path.isfile(path), f"main.tf 를 못 찾았습니다: {path}")
        with open(path, encoding="utf-8") as fh:
            self.tf = fh.read()

    def terraform_routes(self):
        m = self.re.search(self.ROUTES_RE, self.tf, self.re.S)
        self.assertIsNotNone(
            m, "main.tf 에서 aws_apigatewayv2_route.routes 의 for_each 를 못 읽었습니다 — "
               "리소스 이름이나 모양이 바뀌었으면 이 시험을 **먼저** 고치세요. "
               "못 읽은 채 통과하면 이 칸은 아무것도 대조하지 않습니다.")
        found = self.re.findall(r'"([A-Z]+ /[^"]*)"', m.group(1))
        return {tuple(s.split(" ", 1)) for s in found}

    def test_the_two_tables_are_the_same_set(self):
        tf_routes = self.terraform_routes()
        self.assertGreaterEqual(
            len(tf_routes), 10,
            f"terraform 에서 {len(tf_routes)}개만 읽었습니다 — 파서가 목록을 놓쳤습니다. "
            "빈(또는 얇은) 집합끼리는 **서로 동의하므로** 이 대조가 공허해집니다.")
        self.assertEqual(
            tf_routes, set(handler._ROUTES),
            "핸들러의 _ROUTES 와 main.tf 의 라우트 목록이 다릅니다.\n"
            f"  terraform 에만: {sorted(tf_routes - set(handler._ROUTES))}\n"
            f"  handler 에만  : {sorted(set(handler._ROUTES) - tf_routes)}")

    def test_the_default_route_exists(self):
        """🔴 `$default` 가 없으면 없는 경로의 404 는 게이트웨이가 내고, **CORS 가 안 붙는다.**

        그 404 는 브라우저에서 `net::ERR_FAILED` 로 도착하므로 론처의 `r.status === 404`
        분기는 **도달 불가능**하다 — 644 가 고치려는 바로 그 상태다.
        """
        self.assertRegex(
            self.tf,
            r'resource\s+"aws_apigatewayv2_route"\s+"default"\s*\{[^}]*route_key\s*=\s*"\$default"',
            "main.tf 에 $default 라우트가 없습니다 — 없는 경로의 4xx 에 CORS 가 안 붙습니다.")


if __name__ == "__main__":
    unittest.main(verbosity=2)
