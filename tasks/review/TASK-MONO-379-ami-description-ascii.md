# Task ID

TASK-MONO-379

# Title

em dash 하나가 40분치 AMI 를 태우고 산출물까지 지웠다 — `packer validate` 는 통과했다

# Status

review

# Owner

monorepo

# Task Tags

- infra
- demo
- ci

---

# Goal

`infra/demo/aws/packer/demo-ami.pkr.hcl` 의 `ami_description` 에 **em dash(U+2014)** 가 들어 있다. EC2 의 `ModifyImageAttribute` 는 description 에 **0x7F 초과 바이트를 거부**한다.

그것만이면 사소한 오타다. **문제는 실패 시점과 실패 방식이다.**

```
==> amazon-ebs.demo: Creating AMI portfolio-demo-1783889064 from instance i-040100f258704905d
==> amazon-ebs.demo: AMI: ami-01b26cf9e9ae69632
==> amazon-ebs.demo: Modifying attributes on AMI (ami-01b26cf9e9ae69632)...
==> amazon-ebs.demo: Modifying: description
==> amazon-ebs.demo: Error modify AMI attributes: ... InvalidParameterValue:
      Character sets beyond ASCII are not supported.
==> amazon-ebs.demo: Deregistering the AMI and deleting associated snapshots because of cancellation, or error...
==> amazon-ebs.demo: Deregistered AMI id: ami-01b26cf9e9ae69632
==> amazon-ebs.demo: Deleted snapshot: snap-0edb142b02a5e62a2
```

**Packer 는 이미지를 다 구운 뒤에 그 속성을 설정하고, 거부를 빌드 실패로 취급해 방금 만든 AMI 와 스냅샷을 되돌려 지운다.** 40분과 산출물이 함께 사라진다. 재시도해도 같은 자리에서 같은 방식으로 죽는다.

**`packer validate` 는 통과한다.** 문법은 멀쩡하고, AWS 만이 거부하며, 그것도 **맨 마지막 한 줄에서** 거부한다.

---

# 왜 하필 지금 터졌나 — 승격본은 한 번도 빌드된 적이 없다

계정에 있는 AMI `ami-0a99246fd0ad65007`(2026-07-11)은 **정상적으로 구워졌다.** 그러니 이 결함은 원래 없었다.

`TASK-MONO-366` 이 scratchpad PoC 를 `infra/demo/aws/` 로 **승격**하면서 들어갔다 — 산문 습관대로 하이픈 자리에 em dash 를 썼다. 그리고 **승격본은 그 뒤로 한 번도 빌드되지 않았다.** 저장소가 *"이 코드로 데모 호스트를 재현할 수 있다"* 고 주장하는데, **그 주장은 검증된 적이 없었다.**

**이 저장소가 계속 잡아온 "선언 ↔ 진실" 드리프트이고, 이번엔 그 드리프트가 하필 *"재현 가능성"을 담당하는 아티팩트* 안에 있었다.** 366 이 고치려던 결함(*"AMI 가 저장소의 계약을 반영하지 않는다"*)과 정확히 같은 형태가, 366 이 만든 파일 안에서 재현됐다.

---

# 설계

## 1) 수정

`ami_description` 의 em dash → **ASCII 하이픈**. 한 글자다.

## 2) 가드 (o) — Packer 가 AWS 에 보내는 문자열이 ASCII 인가

`verify-demo-wrapper.sh` 에 추가한다. `ami_description` **과 `ami_name`** 을 본다(같은 API 가 같은 이유로 거부한다).

**🔴 파일 전체를 ASCII 로 검사하면 안 된다.** 이 저장소의 주석은 **거의 전부 한글**이다 — 전체 검사는 **첫날부터 RED** 이고, 첫날 RED 인 가드는 **꺼진다.** 그리고 꺼진 잡의 skip 은 **초록으로 보고된다**(`MONO-360` 이 실측한 실패 모드). **AWS 에 실제로 전송되는 두 필드만** 본다.

## 3) 가드의 도달 가능성

`.hcl` 은 `MONO-366` 이 `code-changed` 에 이미 추가했다(`.py`/`.tf`/`.hcl`/`.service`/`.html`). ⇒ **이 가드는 자기가 감시하는 파일에서 도달 가능하다.** 착수 시 재확인할 것 — 도달 불가능한 가드는 없는 가드다(`MONO-359`).

---

# Scope

## In Scope

- `infra/demo/aws/packer/demo-ami.pkr.hcl` — `ami_description` ASCII 화.
- `infra/demo/verify-demo-wrapper.sh` — 가드 (o).
- **AMI 재빌드 + `TASK-MONO-366` 의 무인 실기동 증명 완주** — 이 결함이 정확히 그것을 막고 있었다.

## Out of Scope

- Packer 가 실패 시 AMI 를 지우는 동작 자체 — Packer 의 정상 동작(부분 산출물을 남기지 않는다). 우리가 고칠 것은 **실패하지 않게 하는 것**이다.
- 다른 `.hcl`/`.tf` 파일의 비-ASCII 전수 조사 — AWS API 에 문자열로 전달되는 필드만 문제다. 필요하다고 판단되면 별도 task.

---

# Acceptance Criteria

- [x] **`ami_description` 이 ASCII 다** — `packer build` 가 마지막 단계에서 죽지 않는다.
- [x] **가드 (o) 가 실제로 문다 — mutation 필수.** 통과는 증거가 아니다:
      - O1 `ami_description` 에 em dash 재주입 → **FAIL**
      - O2 `ami_name` 에 비-ASCII → **FAIL** (같은 API, 같은 이유)
      - O3 **한글 주석은 무해**(오탐 0) — 이게 없으면 가드가 첫날 꺼진다
      - O5 vacuity: 정상 트리 **PASS**
- [x] **AMI 가 실제로 구워진다** — **`ami-051cd83db9a46eea2`**, `packer_exit=0`, AWS 에서 `available` + ASCII description 확인(deregister 되지 않음). **옛 AMI(`ami-0a99246fd0ad65007`)는 승격 전 사본으로 구운 것**이므로 이것이 **승격본으로 구운 최초의 AMI** 다.
- [ ] **`TASK-MONO-366` 의 미이행 AC 를 완주한다** — 새 AMI 로 `terraform apply` → `POST /start` → **SSM·SSH 접속 없이** 브라우저 OIDC 로그인 왕복.
      **⚠️ 이 AMI 로는 아직 못 한다** — 부팅은 완전히 성공했으나(§ `TASK-MONO-380` 관측) **`/signup` 이 404 라 로그인할 계정을 만들 수 없다.** 380 을 함께 담아 **한 번만 다시 굽는다.**
- [ ] CI GREEN.

---

# Edge Cases

- **`grep -P` 가 필요하다** — `[^\x00-\x7F]` 를 쓰려면 PCRE 다. GNU grep 은 되고 BSD grep 은 안 된다. CI 러너는 ubuntu 이므로 안전하지만, 로컬 macOS 를 고려한다면 `LC_ALL=C grep` + POSIX 클래스로 대체 가능.
- **`LC_ALL=C` 가 load-bearing** — 로케일에 따라 `grep` 이 멀티바이트를 문자로 해석해 매치를 놓칠 수 있다.
- **`ami_name` 도 같은 제약** — description 만 고치고 name 을 놓치면 다음 사람이 같은 40분을 태운다.

# Failure Scenarios

- **파일 전체를 ASCII 검사** → 한글 주석 때문에 **첫날 RED** → 가드를 끈다 → **꺼진 잡의 skip 이 초록으로 보고된다.** `MONO-360` 이 실측한 그 실패 모드. **오탐 0 이 무는 것만큼 중요하다.**
- **`ami_description` 만 고치고 가드를 안 붙임** → 다음 사람이 산문 습관대로 em dash 를 다시 쓰고, **40분 뒤에, 산출물이 파괴되면서** 알게 된다.
- **`packer validate` 를 검증으로 취급** → 통과한다. **정적 검사가 통과하는 것과 동작하는 것은 다른 명제다** — 이 저장소가 이미 배웠고, 또 당했다.

# Test Requirements

- 정적: `verify-demo-wrapper.sh` 가드 (a)~(o) PASS + **(o) mutation 4방향**.
- **실기동**: `packer build` 완주 → AMI id 존재 → `terraform apply` → **사람 손 0** 로그인 왕복(= `MONO-366` 의 핵심 AC).

# Definition of Done

- [ ] 위 AC 전부
- [ ] CI GREEN
- [ ] `tasks/INDEX.md` done entry
- [ ] **`TASK-MONO-366` 을 닫을 수 있게 된다** — 366 이 `review/` 에 열린 채 남아 있는 유일한 이유가 이 결함이었다.

---

# Provenance

2026-07-13, `TASK-MONO-366` 의 실기동 증명을 위해 사용자 승인 하에 `packer build` 를 돌리다가 드러났다. 빌드는 **40분을 완주해 이미지를 만들었고**, 마지막 메타데이터 한 줄에서 죽으며 **자기 산출물을 지웠다.**

**366 이 review 에서 드러낸 결함이므로 `tasks/INDEX.md` 규정(라인 93)에 따라 새 fix task 로 판다** — `review/` 의 task 는 다시 구현하지 않는다.

**계보**: 이 저장소가 반복해서 배우는 명제 — ***정적 검사가 통과하는 것과 동작하는 것은 다른 명제다.*** `MONO-358`(가드 둘이 통과하면서 물지 못했다) → `MONO-366`(가드가 자기가 감시하는 파일에서 도달 불가능했다) → `MONO-373`/`PC-FE-240`(e2e 가 백엔드를 부르지 않는 spec 하나를 돌리고 초록이었다) → **379(`packer validate` 통과, `packer build` 는 40분 뒤 산출물 파괴)**.

분석=Opus 4.8 / 구현 권장=Sonnet (수정은 한 글자, 가드는 열 줄 — **다만 가드의 오탐 0 조건(한글 주석)만은 놓치면 안 된다**).
