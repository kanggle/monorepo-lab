"""월 예산 가드 테스트.

이 파일은 lambda/ 밖에 둔다 — terraform 의 archive_file 이 lambda/ 를 통째로
zip 으로 굽기 때문에, 안에 두면 테스트와 스텁이 배포 아티팩트에 섞여 들어간다.

왜 테스트하는가: monthly-budget 가드는 공개 /start 버튼과 청구서 사이에 서 있는
유일한 코드다. "코드가 맞아 보인다"는 근거가 못 된다 — 산술과 상태 전이를 실제로
돌려서, 특히 **가드가 열리는 방향으로 실패하지 않는지**(fail-open) 확인한다.

실행: python tests/test_handler.py     (boto3 불필요 — 스텁 주입)
"""
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
        self.exceptions = types.SimpleNamespace(ParameterNotFound=_FakeParamNotFound)

    def put_parameter(self, Name, Value, Type, Overwrite):
        self.store[Name] = str(Value)

    def get_parameter(self, Name):
        if Name not in self.store:
            raise _FakeParamNotFound(Name)
        return {"Parameter": {"Value": self.store[Name]}}


class _FakeEC2:
    def __init__(self):
        self.state = "stopped"
        self.start_calls = 0
        self.stop_calls = 0

    def describe_instances(self, InstanceIds):
        return {"Reservations": [{"Instances": [
            {"State": {"Name": self.state}, "PublicIpAddress": "1.2.3.4"}
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
    "IDLE_MINUTES": "20",
    "MAX_RUNTIME_MINUTES": "180",
    "MONTHLY_BUDGET_MINUTES": "60",  # 테스트는 1시간 예산
    "ALLOWED_ORIGIN": "*",
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


class BudgetGuardTest(unittest.TestCase):
    def setUp(self):
        FAKE_SSM.store.clear()
        FAKE_EC2.state = "stopped"
        FAKE_EC2.start_calls = 0
        FAKE_EC2.stop_calls = 0

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


if __name__ == "__main__":
    unittest.main(verbosity=2)
