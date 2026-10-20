# TASK-MONO-802 — on the Windows host `terraform plan` runs `ami-bundle-capability.sh` under a bash that has no git, computes `BUNDLE_SELECTION_CAPABLE=unknown`, and an apply that was meant to change something else silently turns bundle boot off

- **Status**: ready
- **Type**: fix (demo infrastructure — `infra/demo/aws/terraform`)
- **Created**: 2026-10-20 UTC
- **Scope class**: monorepo-level (`infra/demo/` is shared demo infrastructure)

> **Analysis model:** Opus 5.5 / **recommended implementation:** Sonnet (one `data.external` + a guard; AC-0 decides which)

# Origin

32nd demo window (`TASK-MONO-672` § 32nd window — harvest, 2026-10-20 UTC). The owner asked to raise the monthly budget to 2400 during the window. The agent ran, from the Git Bash tool, `terraform plan -var monthly_budget_minutes=2400` and read the plan with a grep for `MONTHLY_BUDGET` only. The plan had **two** changed lines:

```
~ "BUNDLE_SELECTION_CAPABLE" = "yes" -> "unknown"
~ "MONTHLY_BUDGET_MINUTES"   = "1800" -> "2400"
```

The apply went through, and the very next `POST /bundle/start` answered `bundle-boot-not-deployed` («데모 서버 이미지의 세대를 확인하지 못했습니다»). `terraform state show data.external.ami_bundle_capability` read `capable = "unknown"`, `reason = "git 저장소 밖에서 실행됐습니다"` — `ami-bundle-capability.sh:94` (`git -C "$GITROOT" rev-parse --git-dir` failed). The same script run directly from Git Bash answered `capable=yes` (`e44142d34` descends from `9f0fcd2d6`).

`where bash` on this host lists `C:\Program Files\Git\usr\bin\bash.exe`, **`C:\Windows\System32\bash.exe`** (WSL) and a WindowsApps stub. `main.tf:366-368` runs `program = ["bash", "${path.module}/../ami-bundle-capability.sh", "--json"]` — a bare `bash`, resolved by `terraform.exe`'s own PATH lookup. Re-running the plan from PowerShell with `C:\Program Files\Git\bin` first on `PATH` produced `"unknown" -> "yes"` as the **only** change; that apply restored bundle boot (verified: `/bundles` `bundle_boot_supported: true`). Inference (not measured): the failing run resolved WSL bash, whose `git` (if any) cannot read the Windows checkout. Ten minutes of a live window were spent on it.

# Goal

A plan run on this host either computes the real capability or **fails loudly** — it must never quietly turn a `yes` into `unknown` and hand that to an apply that was about something else.

# Scope

- **AC-0 (measure first)**: which `bash` `terraform.exe` resolves from the Git Bash tool vs PowerShell (log `uname -a` / `command -v git` from inside the program, throwaway); whether WSL is the cause; how the owner normally runs terraform on this host (PowerShell? Git Bash?) — read `infra/demo/aws/README.md` and past windows in `TASK-MONO-672`.
- Fix (AC-0 picks): (a) the script, when it cannot see the repository, **exits non-zero** so `data.external` fails the plan instead of returning `unknown` (🔴 check the contract of `unknown` first — the Lambda treats it as «refuse bundle boot», which is the safe side for a *missing* checkout; failing the plan may be better only for the plan path); or (b) pin the interpreter / document the required shell; or (c) a pre-apply check that refuses an apply which flips `BUNDLE_SELECTION_CAPABLE` from `yes`.
- Runbook line: «read the whole plan diff, not a grep» — where the demo runbook tells the owner/agent how to apply.

## Out of scope

- The capability rule itself (`9f0fcd2d6` threshold).
- The budget value (owner decision 2026-10-20: 2400 — `variables.tf` default updated with this ticket's origin PR).

# Acceptance Criteria

- [ ] **AC-0** — the resolved interpreter on each shell, the cause, and the chosen fix, recorded.
- [ ] **AC-1** — the failing shell no longer yields a silent `unknown` (plan fails, or computes `yes`), shown by running it.
- [ ] **AC-2** — the existing capability tests (`infra/demo/aws/tests`, `(z40)` bite) still pass; a test or guard for the new behaviour.
- [ ] **AC-3** — the runbook says how to run plan/apply on this host and to read the full diff.

# Related Specs

- `infra/demo/aws/README.md` · `infra/demo/aws/ami-bundle-capability.sh` header

# Related Contracts

- Control Lambda `POST /bundle/start` 409 `bundle-boot-not-deployed` (`lambda/handler.py:570-590`)

# Edge Cases

- A CI or Linux runner has only one bash — the fix must not break it.
- `unknown` must stay the answer for a genuinely missing/shallow clone in the Lambda path.

# Failure Scenarios

1. Make the script return `yes` when it cannot tell — the opposite failure (the guard exists so an old AMI is never offered bundle boot).
2. Fix only the docs — the next apply from the wrong shell flips it again.
