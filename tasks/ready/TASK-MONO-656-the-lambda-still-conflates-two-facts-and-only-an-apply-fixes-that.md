# Task ID

TASK-MONO-656

# Title

🔴 **론처는 고쳤지만 API 는 아직 두 사실을 한 값에 싣고 있다** — 남은 것은 `terraform apply` 하나이고, 그것은 소유자 결정이다

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- infra
- owner-decision

---

# Goal

`TASK-MONO-653` 이 갈라 놓은 `selected` / `requested` 를 **실제 람다에 배포**해서, `/bundles`
응답 자체가 두 사실을 구별하게 한다.

🔵 **방문자 증상은 이미 없다** — 론처가 자기 쪽에서 보정하고 있다(아래 § 왜 급하지 않은가).
남은 것은 **API 를 정직하게 만드는 것**이다.

---

# 🔴 왜 이 티켓이 따로 있나 — 「plan 까지 내고 STOP」의 나머지

`TASK-MONO-653` 의 AC-4 는 *"람다 쪽을 고쳤다면 그건 `terraform apply` 이고 **소유자 승인
사항**이다 — plan 까지 내고 STOP"* 이라고 적었고, 그대로 했다. 653 의 AC 는 **전부 닫혔다.**

🔴🔴 **그런데 그렇게 닫으면 apply 의무가 `done/` 과 함께 얼어붙는다.** `done/` 은 frozen 이고
다시 안 읽힌다 — 「소유자가 승인하면 할 일」이 저장소 어디에도 안 남는다. `CLAUDE.md` 가
이름 붙인 그 실패다: **산문에는 게이트가 없다.** 그래서 653 을 닫기 전에 이 티켓이 그 의무를
넘겨받는다.

🔵 선례: `TASK-MONO-645` 가 `635`·`638` 의 「스택이 떠야 잴 수 있는」 항목들을 같은 이유로
넘겨받았다.

---

# 🟢 이미 잰 것 (2026-09-10 UTC, `TASK-MONO-653` 에서)

## plan 은 이미 냈고 안전하다

```
Plan: 0 to add, 1 to change, 0 to destroy.

# aws_lambda_function.control will be updated in-place
  ~ source_code_hash = "/BMmrfwBuRNNKKgkUkYnlDQOLayUOvl0TcM+PIqx6sc="
                    -> "4UEHfnNxr0cQk9fNmwsHRk7ICjhN9Yw5SK5kx9ns6Zs="
```

🟢 **`0 to destroy` 이고 EC2 가 목록에 없다.** 그것이 중요한 이유는 `TASK-MONO-645` 가 적어 둔
사실 때문이다 — 이 인스턴스는 **루트 볼륨 하나뿐**이라 인스턴스 교체는 **DB 마이그레이션
이력 소멸**이다. 이 plan 에는 그 항목이 없다.

🔴 **그러나 이 plan 은 낡는다.** `source_code_hash` 는 그때의 zip 해시이고, 그 사이 다른
커밋이 `handler.py` 나 다른 리소스를 건드렸으면 **다른 계획이 나온다.** AC-0 이 그것을 막는다.

## 왜 급하지 않은가 — 론처가 이미 보정한다

`bundleStateOf()` 가 «`requested` + 인스턴스가 `running`·`pending` 이 아님» 을 `selected` 로
읽는다. 실측(2026-09-10T04:07Z, 실제 브라우저):

```
GET /status  → stopped        GET /bundles → 선택 9개 전부 requested (옛 람다)
페이지의 .bstart 3개          → 🟢 전부 활성, 라벨 「실시간 기능 시작」
```

🔵 그 보정은 **임시 코드가 아니다** — 「인스턴스가 `stopped` 인데 이 묶음이 기동 중」은 서버
버전과 무관하게 성립할 수 없으므로 apply 뒤에도 그대로 옳다. **지우지 마라.**

## 그래서 apply 가 고치는 것은 «화면» 이 아니라 «계약» 이다

🔴 지금 `/bundles` 를 읽는 다른 소비자(생기거나 이미 있는)는 `requested` 를 **여전히 두 뜻으로**
받는다. 론처 하나가 보정하고 있을 뿐이고, **그 보정은 론처 안에만 있다.**

---

# Scope

## 포함

- 🙋 **소유자 승인 확인** (AC-0)
- `terraform plan` **재실행** — 위 plan 을 재사용하지 않는다
- `terraform apply`
- apply 뒤 `/bundles` 가 실제로 `selected` 를 주는지 **라이브 확인**
- 🔵 그 상태에서 론처가 여전히 옳게 그리는지 확인(보정 경로를 안 타는 갈래)

## 제외

- 🔴 **론처의 `bundleStateOf` 보정 제거** — 위에 적었듯 항상 참인 규칙이다. 「이제 서버가
  `selected` 를 주니 필요 없다」는 **틀렸다**: 롤백·다른 리전·옛 캐시가 옛 값을 줄 수 있고,
  그때 그 줄이 없으면 버튼이 다시 죽는다.
- 🔴 **AMI 재굽기** — 다른 축이고 다른 승인이다(`TASK-MONO-645` 가 그 순서를 들고 있다).
- `TASK-MONO-647`(묶음 기동이 전체를 켠다) · `TASK-MONO-653`(닫혔다).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [ ] 🙋 **소유자가 `terraform apply` 를 승인했는가.** 승인 문장이 없으면 **STOP** —
      아무것도 안 하는 것이 올바른 구현이다. 🔴 「PR 을 진행해라」 같은 일반 지시는
      **이 승인이 아니다**(`CLAUDE.md` § Git — 파괴적/외부 행위는 건별 승인).
- [ ] 🔴 **plan 을 다시 낸다.** 위에 적힌 `0 add / 1 change / 0 destroy` 를 **인용하지 마라** —
      그것은 2026-09-10 의 값이다. 다시 낸 plan 이 **여전히 `0 to destroy` 이고 EC2 를 안
      건드리는지** 확인하고, 아니면 **STOP 하고 무엇이 달라졌는지 적어라.**
- [ ] 🔵 상태·`tfvars` 는 **메인 체크아웃에만** 있다(gitignore). 워크트리에서 돌리려면 그
      트리에 브랜치의 `handler.py` 만 임시로 얹고 plan/apply 뒤 **복원**한다 —
      **자격증명을 복사하지 마라.** (653 이 쓴 방법 그대로.)

## AC-1 — apply

- [ ] `terraform apply` 를 낸다.
- [ ] 🔴 적용된 것이 **람다 하나뿐**임을 출력으로 확인한다. EC2 가 교체됐으면 그것은 사고이고
      즉시 `TASK-MONO-645` 의 ③(기존 볼륨 마이그레이션 판정)이 **영영 못 잴 상태**가 된다.

## AC-2 — 라이브 판정

- [ ] 🔴 **인스턴스가 `stopped` 인 상태에서** `/bundles` 를 읽어 선택된 묶음이 `selected` 를
      주는지 확인한다. `requested` 면 apply 가 안 먹은 것이다(zip 캐시·별칭 확인).
- [ ] 🔴 **대조군**: 그 응답에서 선택 **안 된** 묶음은 여전히 `waiting` 이어야 한다.
      전부 `selected` 면 화이트리스트가 아니라 상수를 돌려주고 있는 것이다.
- [ ] 🔵 **`pending` 갈래는 이 창에서 못 잰다** — 인스턴스를 켜야 보인다. 🔴 **이 티켓만을
      위해 EC2 를 켜지 마라.** 못 쟀으면 ⚪ 로 적고, 다음에 데모가 켜지는 창
      (`TASK-MONO-645`·`633`)에 얹어라. 🔵 단위 테스트
      (`test_pending_instance_is_requested_not_selected`)가 그 갈래를 이미 문다.

## AC-3 — 화면이 안 깨졌는가

- [ ] 서버가 `selected` 를 주기 시작한 뒤에도 론처의 버튼이 **여전히 활성**인지 확인한다
      (이제 보정 갈래를 안 타고 직행한다). 🔵 `(z39)` 의 `S1_SEL_STOPPED` 가 재던 상태다.
- [ ] 🔴 `bundleStateOf` 를 **지우지 마라** — § 제외 참조.

---

# Related Specs / Contracts

- `TASK-MONO-653` — 값을 가른 티켓(`done/`). plan 을 낸 곳이고 이 의무의 출처다
- `TASK-MONO-645` — 🔴 **인스턴스 교체 = 루트 볼륨 = DB 마이그 이력 소멸.** 이 티켓이
  `0 to destroy` 를 매번 확인해야 하는 이유
- `ADR-MONO-071` § D5 — 8단계 표(이미 갱신됨)
- `infra/demo/aws/terraform/lambda/handler.py` `_bundle_state()`
- `infra/demo/aws/site/index.html` `bundleStateOf` · `B_STARTABLE`
- `infra/demo/aws/tests/test_handler.py` `BundleSelectionTest` — 대조군 3칸

---

# Edge Cases

- **apply 했는데 `/bundles` 가 그대로다** — Lambda zip 캐시나 별칭/버전 문제다. 🔴 「배포됐다」의
  판정은 콘솔의 `last_modified` 가 아니라 **`/bundles` 응답**이다.
- **plan 이 1 change 가 아니다** — 그 사이 다른 커밋이 인프라를 건드렸다는 뜻이다. AC-0 에서
  STOP 하고 무엇이 늘었는지 먼저 적어라.
- **인스턴스가 `running` 인 창에 착수한다** — 그러면 AC-2 의 주 판정(`stopped` + `selected`)을
  못 낸다. 🔵 대신 `pending`/`running` 갈래를 잴 수 있으므로 **AC-2 의 ⚪ 를 그때 닫아라.**
- **선택이 비어 있다** — 전부 `waiting` 이라 `selected` 가 한 건도 안 나온다. 그 상태에서는
  판정이 **공허하다** — 선택을 만들려면 기동이 필요하므로 ⚪ 로 적고 창을 기다려라.

---

# Failure Scenarios

1. 🔴🔴 **낡은 plan 을 근거로 apply 한다.** 이 파일에 적힌 `0 add / 1 change / 0 destroy` 는
   2026-09-10 의 값이다. 그 사이 인프라가 늘었으면 **같은 명령이 다른 일을 한다** —
   최악은 EC2 교체이고 그때 잃는 것은 되돌릴 수 없다.
2. 🔴🔴 **승인 없이 apply 한다.** 「진행해라」는 이 승인이 아니다.
3. 🔴 **론처의 보정을 「이제 필요 없다」며 지운다.** 롤백 한 번에 버튼이 다시 죽는다.
4. 🔴 **`/bundles` 를 안 읽고 「apply 성공」으로 닫는다.** 배포의 판정은 응답이다.
5. **`pending` 갈래를 재려고 EC2 를 켠다.** 예산은 유한하고 그 갈래는 단위 테스트가 이미 문다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 승인만 있으면 절차는 짧고 기계적이다(plan 재실행 → 대조
→ apply → 응답 확인). 🔴 다만 **AC-0 의 두 게이트(승인·plan 재대조)는 건너뛰면 되돌릴 수 없는
쪽으로 실패**하므로, 짧다고 생략하지 마라.
