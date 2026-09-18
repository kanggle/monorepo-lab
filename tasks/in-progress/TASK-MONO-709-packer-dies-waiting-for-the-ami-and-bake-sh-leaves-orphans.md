# Task ID

TASK-MONO-709

# Title

🔴 packer 가 **AMI 준비를 기다리다 `unexpected EOF`** 로 죽었다 — 이미지는 살았지만 태그·핀·정리가 전부 안 됐고, `bake.sh` 에는 그 상태를 구조할 경로가 없다

# Status

in-progress (2026-09-17 UTC — AC-0 ⚪ 원인 미상 · AC-1~AC-4 닫힘, 실전은 다음 굽기)

# Owner

monorepo

# Task Tags

- demo
- ami
- bake
- recovery

---

# Goal

2026-09-17 UTC 12차 굽기(승인 SHA `af0018aa6`)에서 `bash infra/demo/aws/packer/bake.sh --ref bake-<sha>` 가 **37분 55초**에 죽었다:

```
==> amazon-ebs.demo: Stopping the source instance...
==> amazon-ebs.demo: Creating AMI portfolio-demo-1789658583 from instance i-054b8d179af391052
==> amazon-ebs.demo: Attaching run tags to AMI...
==> amazon-ebs.demo: AMI: ami-02613b0378621b124
==> amazon-ebs.demo: Waiting for AMI to become ready...
Build 'amazon-ebs.demo' errored after 37 minutes 55 seconds: unexpected EOF
==> Builds finished but no artifacts were created.
[bake] ✖ packer build 가 rc=1 로 끝났습니다. 핀 파일은 **안 고칩니다.**
```

그 시점에 이미 끝나 있던 것: 2단계 HEAD 대조(`[ami] cloned HEAD=af0018aa6… expected=af0018aa6…`) · 인스턴스 안 정적 검증 **PASS** · `CreateImage` 등록. 안 된 것: **RepoCommit 태그**(태그는 ready 뒤에 붙는다) · `deployed-ami.env` 갱신 · **빌더 정리**.

남은 것(사람이 손으로 치웠다): 빌더 인스턴스 `i-054b8d179af391052`(c6i.4xlarge, stopped — EBS 과금) · 보안그룹 `sg-048932bb2ed941f91` · 키페어 `packer_…`. 그리고 AMI 는 **태그 없는 채** available 이 됐다 — `check-ami-generation.sh` 와 `ami-bundle-capability.sh` 는 핀 파일을 읽으므로 그 상태로는 «구운 세대» 를 아무도 모른다.

🔵 이번에는 소유자가 **«구조: 기다렸다 수동 태그»** 를 골라 태그 3종을 손으로 붙이고 provenance 를 `operator-record` 로 적어 살렸다(`#3905`). 🔴 그 절차가 **어디에도 적혀 있지 않다** — 다음 사람은 55분을 다시 태우거나, 더 나쁘게는 태그 없는 AMI 를 그냥 둔다.

---

# Scope

## 포함

- `bake.sh` 에 **구조 경로**: 굽기가 AMI 등록 이후에 죽었을 때 (1) 방금 만든 AMI 를 이름(`portfolio-demo-<timestamp>`)으로 찾아 (2) available 을 기다렸다가 (3) 태그를 붙이고 (4) 핀 파일을 쓰되 **provenance 를 `operator-record` 로** 적는다. 🔴 `ami-tag` 로 적으면 안 된다 — 태그를 우리가 붙였기 때문이다(그 구별이 핀 파일의 존재 이유다).
- 고아 정리: 빌더 인스턴스 · SG · 키페어를 **이름/태그로 찾아** 보고(삭제는 사람이 확인 후, 또는 `--cleanup` 플래그).
- 실패 모드 자체의 완화 여부 판단: `aws_polling` 설정 · packer 버전 · 타임아웃. 🔴 원인(«왜 EOF 였나»)을 모르면 완화가 아니라 추측이다.
- 절차 문서: `infra/demo/aws/README.md` 에 «굽기가 AMI 등록 뒤에 죽었을 때» 한 절.

## 제외

- 굽기 자체의 속도·비용(다른 축).
- AMI 세대 정리(등록해제·스냅샷 삭제)는 지금 규율 그대로.

---

# Acceptance Criteria

- [ ] **AC-0 — 원인을 아는 만큼만 적는다.** packer 버전 · `unexpected EOF` 가 어느 단계의 API 호출인지 · 같은 실패가 이 저장소 이력에 또 있었는지(9차·10차 굽기 기록 대조). 🔴 모르면 «모른다» 로 적고 구조 경로만 만든다 — 완화를 지어내지 마라.
- [x] **AC-1 — 구조 경로.** 위 (1)~(4). 🔴 provenance 는 `operator-record`. `--dry-run` 으로 그 분기를 **굽기 없이** 시험할 수 있어야 한다(지금 스크립트의 규율과 같다).
- [x] **AC-2 — 고아 보고.** 굽기 실패 후 남는 세 종류를 이름으로 나열한다(실측 이름 패턴 포함). 삭제는 기본값이 아니다.
- [x] **AC-3 — bite.** `--dry-run` 자기시험: ① AMI 이미 있음 + 태그 없음 → 구조 분기를 탄다 ② 태그 있음 → 아무것도 안 한다 ③ AMI 없음 → 지금처럼 실패로 끝난다.
- [x] **AC-4 — 문서.** `infra/demo/aws/README.md` 에 절차와 «왜 operator-record 인가» 를 적는다.

---

# Related Specs

- `infra/demo/aws/packer/bake.sh` · `infra/demo/aws/deployed-ami.env`(provenance 정의) · `infra/demo/aws/check-ami-generation.sh`
- `tasks/done/TASK-MONO-628-*` — RepoCommit 태그와 provenance 를 만든 티켓
- `#3905` — 12차 핀(이번 구조의 기록)

# Related Contracts

- 없음 — 인프라 스크립트.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 죽은 시점이 AMI 등록 **전** | 구조할 대상이 없다 — 지금처럼 실패로 끝난다(AC-3 ③) |
| 같은 이름의 AMI 가 둘 | 🔴 이름은 `{{timestamp}}` 라 충돌이 사실상 없지만, 둘이면 **멈춘다**(고르지 않는다) |
| AMI 가 `failed` 로 끝남 | 태그를 붙이지 않는다 — 등록해제 안내만 한다 |

# Failure Scenarios

1. **구조 경로가 `ami-tag` 로 핀을 쓴다** → 「이미지가 스스로 한 말」과 「사람이 적은 말」이 섞인다. 이 파일 전체가 그 구별을 위해 있다.
2. **고아를 자동으로 지운다** → 아직 조사 중인 빌더 인스턴스를 지울 수 있다(로그·디스크가 원인 조사의 유일한 증거일 때가 있다).

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet 5** (셸 한 파일 + 문서. 🔴 실제 굽기로는 시험하지 않는다 — `--dry-run` 자기시험)

---

# 구현 기록 — 2026-09-17 UTC (분석·구현=Opus 5)

## AC-0 — 원인: ⚪ **모른다고 적는다**

- packer 로그의 마지막 줄은 `Waiting for AMI to become ready...` → `unexpected EOF`(37분 55초). 그 EOF 가 어느 API 호출/플러그인 채널에서 났는지는 로그에 없다.
- 🔴 **완화를 지어내지 않았다** — `aws_polling` 을 늘린다거나 packer 를 올린다는 처방은 원인을 모르는 채 나오는 «그럴듯한» 값이다. 이 티켓이 한 것은 **그 실패가 남기는 상태를 구조 가능하게** 만든 것이다.
- 이 저장소 이력에서 같은 실패는 못 찾았다(9차는 `python: command not found` 로 12분 55초에, 그건 다른 모양).

## AC-1 — 구조 경로 (`bake.sh`)

| 무엇 | 어디 |
|---|---|
| 실패 뒤 자동 구조 | `packer build` rc≠0 이면 `rescue "$((BUILD_STARTED-120))" "$SHA"` — 성공하면 exit 0, 아니면 예전처럼 die |
| 이미 죽은 굽기 구조 | `--rescue-only --ref <branch>` (굽지 않는다. 기준 epoch = 가장 최근 `portfolio-demo-*` 이름) |
| 대상 찾기 | `portfolio-demo-*` self AMI 중 **이름의 `{{timestamp}}` epoch 이 빌드 시작 이후**인 것 — 우리가 재는 시각이 아니라 packer 가 박은 값 |
| 기다림 | `aws ec2 wait image-available` — `pending` 에서 태그를 붙이면 조용히 실패할 수 있다 |
| 태그 | `Name` · `Project` · `RepoCommit`(AMI + 스냅샷). 🔴 **붙인 뒤 되읽어** 같은 값인지 확인하고, 아니면 핀을 안 쓴다 |
| 핀 | `write_pin ... operator-record` — 🔴 `ami-tag` 가 아니다(사람이 붙인 태그다) |
| 손대지 않는 경우 | 후보 여럿 · **다른 커밋 태그** · `failed` 상태 ⇒ 고르지 않고 멈춘다(사유와 후보를 찍는다) |

🔵 **정상 경로도 같은 `write_pin()` 을 쓰도록 바꿨다** — 두 경로가 각자 sed 를 갖고 있으면 한쪽만 고쳐진다(이 저장소의 «한 사실이 두 집을 갖는다» 축).

## AC-2 — 고아 보고 (지우지 않는다)

`report_orphans()` 가 **이름 패턴으로** 찾아 나열한다: `key-name=packer_*` 인 인스턴스(종료 상태 제외) · `group-name=packer_*` 보안그룹 · `packer_*` 키페어. 정리 **순서**(인스턴스 → SG → 키페어)도 함께 찍는다 — 인스턴스가 살아 있으면 SG 삭제가 거부된다(2026-09-17 에 실제로 그 순서로 지웠다).
🔴 자동 삭제하지 않는 이유: 그 빌더의 디스크가 «왜 죽었나» 의 유일한 증거일 수 있다.

## AC-3 — bite (`--self-test`, AWS 없이)

`bash infra/demo/aws/packer/bake.sh --self-test` → **7/7 (판정 4종)**, rc=0.

| 칸 | 세계 | 기대 |
|---|---|---|
| ① `ami-without-tag-available` | 2026-09-17 의 그 상태 | `rescue` |
| ①' `ami-without-tag-pending` | 아직 pending | `rescue` |
| ② `ami-already-tagged` | 정상 경로가 이미 끝남 | `already-tagged`(다시 안 붙인다) |
| ③ `no-ami-at-all` | 등록 전에 죽음 | `none`(오늘과 같은 실패) |
| ④ `two-candidates` | 후보 둘 | `ambiguous` |
| ⑤ `tagged-with-other-commit` | 남의 커밋 태그 | `ambiguous` |
| ⑥ `ami-failed-state` | AMI `failed` | `none` |

🔴 **첫 판이 «7칸을 6/6» 이라고 찍었다** — 칸 수를 손으로 적었기 때문이다. 이제 세어서 찍고(`$ran`), 판정 종류가 3가지 미만이면 «픽스처가 공허하다» 로 멈춘다.
🔵 CI 가 PR 마다 돌린다 — `ci.yml` 의 `Bake wrapper dry-run` 옆에 `Bake wrapper self-test (rescue verdict — no AWS)` 스텝을 더했다. `--dry-run` 도 그대로 돈다(구조 경로 안내 3줄이 늘었다).

## AC-4 — 문서

`infra/demo/aws/README.md` § **«굽기가 AMI 등록 뒤에 죽었을 때»** — 무엇이 끝나 있고 무엇이 안 됐는지, `--rescue-only` 사용법, 🔴 왜 provenance 가 `ami-tag` 가 아닌지, 손대지 않는 경우, 고아 정리 **순서**. 굽기 명령 옆에도 포인터 한 줄.

## ⏳ 남은 것

**실전 판정은 다음 굽기**다 — 그때 굽기가 정상으로 끝나면 이 경로는 안 탄다(그것이 정상이다). 🔴 그래서 이 티켓은 «다음 굽기가 실패하면» 이 아니라 **자기시험 + 문서**로 닫는다. 실제 구조가 한 번 돌면 그 기록을 여기 덧붙인다.
