# Task ID

TASK-MONO-709

# Title

🔴 packer 가 **AMI 준비를 기다리다 `unexpected EOF`** 로 죽었다 — 이미지는 살았지만 태그·핀·정리가 전부 안 됐고, `bake.sh` 에는 그 상태를 구조할 경로가 없다

# Status

ready

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
- [ ] **AC-1 — 구조 경로.** 위 (1)~(4). 🔴 provenance 는 `operator-record`. `--dry-run` 으로 그 분기를 **굽기 없이** 시험할 수 있어야 한다(지금 스크립트의 규율과 같다).
- [ ] **AC-2 — 고아 보고.** 굽기 실패 후 남는 세 종류를 이름으로 나열한다(실측 이름 패턴 포함). 삭제는 기본값이 아니다.
- [ ] **AC-3 — bite.** `--dry-run` 자기시험: ① AMI 이미 있음 + 태그 없음 → 구조 분기를 탄다 ② 태그 있음 → 아무것도 안 한다 ③ AMI 없음 → 지금처럼 실패로 끝난다.
- [ ] **AC-4 — 문서.** `infra/demo/aws/README.md` 에 절차와 «왜 operator-record 인가» 를 적는다.

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
