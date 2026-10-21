# TASK-MONO-802 — on the Windows host `terraform plan` runs `ami-bundle-capability.sh` under a bash that has no git, computes `BUNDLE_SELECTION_CAPABLE=unknown`, and an apply that was meant to change something else silently turns bundle boot off

- **Status**: done
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

- [x] **AC-0** — the resolved interpreter on each shell, the cause, and the chosen fix, recorded. → § AC-0 measurement (2026-10-21 UTC). 🔴 The «WSL bash has no git» premise is wrong — see there.
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

---

## AC-0 measurement (2026-10-21 UTC · no window · no terraform run · analysis=Opus 5.5)

🔴 **The premise «the WSL bash has no git» is wrong (measured).** WSL Ubuntu-22.04 has git 2.34.1 and yields `capable=yes` in the main checkout. The incident's reason string («git 저장소 밖에서 실행됐습니다») reproduced in exactly one combination: **WSL bash run inside a git worktree**.

| Shell | `bash` resolved | Script result |
|---|---|---|
| PowerShell, default PATH | `C:\Windows\System32\bash.exe` = WSL (Ubuntu-22.04 default distro, bash 5.1.16, `/usr/bin/git` 2.34.1). Default PATH has `Git\cmd` only, no Git `bin` | main checkout `yes` (measured) |
| PowerShell, Git `bin;usr\bin` prepended | `C:\Program Files\Git\bin\bash.exe` (MINGW64, bash 5.2.26, git 2.45.1) | `yes` (measured) |
| Git Bash tool | native PATH starts `~\bin`, `Git\mingw64\bin`, `Git\usr\bin` → first `bash.exe` = `Git\usr\bin\bash.exe` (measured by walking PATH from node.exe) | `yes` |

- terraform v1.15.8 · external provider v2.4.1. Go `exec.LookPath` walks PATH in order and hands an absolute path to CreateProcess, so the «System32 first» CreateProcess rule does not apply (inferred). ⇒ from Git Bash the WSL launcher cannot win — the Origin's «ran from the Bash tool → WSL» does not match this measurement.
- **Cause (file:line)**: `ami-bundle-capability.sh:94` `git -C "$GITROOT" rev-parse --git-dir` fails → reason «git 저장소 밖»; `:110-114` always `exit 0` in `--json` mode, so `unknown` leaves as **success** — an intended contract (`:57-60` header · `main.tf:363-365`). `main.tf:366-368` `program = ["bash", …]` = first bash on PATH. `main.tf:395` → Lambda env `BUNDLE_SELECTION_CAPABLE`; `handler.py:93` reads `unknown` as refusal.
- **Reproduced mechanism (measured)**: a worktree's `.git` is a file `gitdir: C:/Users/.../monorepo-lab/.git/worktrees/<name>`; WSL git cannot read that Windows path — `fatal: not a git repository: /mnt/c/.../<wt>/C:/Users/...` (rc=128) → `{"capable":"unknown",…,"reason":"git 저장소 밖에서 실행됐습니다"}` rc=0. Git bash in the same worktree: normal.
- ⚪ Not settled: state lives only in the main checkout and the incident plan showed two diff lines, so «it ran in a worktree» is not proven either. Remaining candidates (neither measurable now): ① a `-state`/`-chdir` combination from a worktree ② the default WSL distro was then a git-less one (rancher-desktop / docker-desktop; today it is Ubuntu).
- Common defect: depending on which bash PATH yields first, the script can be blind to the repository, and `unknown` then leaves silently as success. Pinning the interpreter alone does not close every path.

### Owner decision (2026-10-21 UTC) — fix = terraform `postcondition`

- `data "external" "ami_bundle_capability"` gets `lifecycle { postcondition { condition = self.result.capable != "unknown" || var.allow_unknown_bundle_capability; error_message = <carries reason> } }` — the plan fails loudly. `no` is a real verdict and passes. One escape var (default false) for a deliberately repo-less / shallow run.
- Script contract and `(z40)` («json rc=0 in all five states», `verify-demo-wrapper.sh:4252`, `:4257-4261`, `:4496`) stay. Add diagnostic flat-string fields `shell` (`uname -s`) and `git` (`command -v git`) to the JSON so the next error names its cause.
- Rejected: (a) script exits non-zero on `unknown` (breaks z40 + header contract) · (b) `var.bash_path` (no default fits Linux/CI and Windows; does not cover the worktree/distro paths) · (c) replace bash (git ancestry cannot be computed by terraform functions).
- AC-2: `(z40)` gains a wiring grep for the postcondition + one bite cell (removing the condition must trip). AC-3: README (`infra/demo/aws/README.md:49` names no shell today) — on PowerShell prepend Git `bin` to PATH · run from the main checkout · read every `~`/`+`/`-` line of the plan.
- Files: `infra/demo/aws/terraform/main.tf` · `variables.tf` · `infra/demo/verify-demo-wrapper.sh` · `infra/demo/aws/README.md` · `ami-bundle-capability.sh` (diagnostic fields). No `scripts/` change (no guard-count ripple). Implementation = Sonnet. AC-1 = run `terraform plan` from PowerShell default PATH in a worktree and see it fail (owner/session runs terraform).

## Implementation record (2026-10-21 UTC)

- `infra/demo/aws/terraform/main.tf:366-392` — `data.external.ami_bundle_capability` gains `lifecycle { postcondition { condition = self.result.capable != "unknown" || var.allow_unknown_bundle_capability; error_message = <<-EOT … EOT } }`. **`error_message` DOES reference `self`** (reason, shell, git): measured on terraform v1.15.8 with a scratch `terraform_data` config (builtin provider, outside the repo, no AWS) — apply failed printing `REASON=<self.output.reason>`. Not measured on 1.6 (`required_version >= 1.6` unchanged; if a 1.6 user hits a parse error there, bump `required_version`). The message states the reason + shell + git + the three fix steps (Git `bin` first on PATH · main checkout, not a worktree · `bash infra/demo/aws/ami-bundle-capability.sh --json`) + the escape var.
- `infra/demo/aws/terraform/variables.tf:211-216` — `allow_unknown_bundle_capability` (bool, default false).
- `infra/demo/aws/ami-bundle-capability.sh` (json block) — flat-string `shell` (`uname -s`) and `git` (`command -v git`, quotes/backslashes stripped) added; rc=0 contract unchanged. Sample on this host: `"shell":"MINGW64_NT-10.0-26200","git":"/mingw64/bin/git"`.
- `infra/demo/verify-demo-wrapper.sh` (z40): `shell`/`git` key assertion after the five `z40_expect` cells (~:4263); `z40_post_ok` wiring check + `variables.tf` var check (~:4412-4427); bite-5 (~:4522-4533) rewrites the condition to `true` and requires `z40_post_ok` to fail; `ok` line updated.
- `infra/demo/aws/README.md` step 2: PowerShell PATH prefix, main checkout, read every `~`/`+`/`-` line, escape var.
- Verification: `bash infra/demo/verify-demo-wrapper.sh --require-coverage` (the nightly invocation; ci.yml uses `--live`, not run) → log in scratchpad `z40-run1.log`, `rc=0`, `(z40) ok` line carries the new text, bite-5 inside the run passed. Independent mutation of main.tf (condition → `true`): `z40_post_ok` real=0, mutated=1. `python -m unittest infra/demo/aws/tests/test_handler.py` 126 OK. `terraform fmt -check` rc=0. `terraform validate`/init of the real config NOT run.
- **AC-1 command (coordinator, PowerShell default PATH, from a worktree)**: `cd <any worktree>\infra\demo\aws\terraform; terraform plan` — expected: `Error: Resource postcondition failed` on `data.external.ami_bundle_capability` with 사유/진단 lines (if WSL bash is resolved; if it resolves to `yes` instead, the cause is not reproduced there). Then from the main checkout with Git `bin;usr\bin` prepended: plan must show no `BUNDLE_SELECTION_CAPABLE` change.
- AC status at review: AC-0 closed · **AC-1 OPEN** (command above) · AC-2 closed (this record) · AC-3 closed (README). The checkboxes above were not edited because `review/` is frozen.

---

## CORRECTION (2026-10-21 UTC — AC-1 measured · all ACs closed · done)

The `[ ]` boxes for AC-1 · AC-2 · AC-3 above are the review-time record; all four ACs are closed now.

- **AC-1 ✅ (measured by the coordinating session after merge, owner-approved run)** on main `5ad7eaaa8`:
  - **Failing shell** — a detached worktree, PowerShell default PATH (`Get-Command bash` → `C:\WINDOWS\system32\bash.exe` = WSL), `terraform plan -input=false -lock=false` (the main checkout's `.terraform` · lock · tfvars copied in, no state; worktree deleted afterwards): **rc=1** · `Error: Resource postcondition failed` · `on main.tf line 379, in data "external" "ami_bundle_capability"` · `사유: git 저장소 밖에서 실행됐습니다` · `진단: shell=Linux git=/usr/bin/git`. The silent `unknown` is now a loud failure, and the diagnostic line names the AC-0 mechanism (WSL bash with git present, blind to the worktree's `.git` file).
  - **Correct shell** — main checkout, Git `bin;usr\bin` prepended (`bash` → `C:\Program Files\Git\bin\bash.exe`): **rc=0**, **`No changes. Your infrastructure matches the configuration.`** Every line of the plan was read — no `~`/`+`/`-` line at all, so `BUNDLE_SELECTION_CAPABLE` stays as deployed.
- **AC-2 ✅** — § Implementation record (z40 shell/git keys, postcondition wiring check, bite-5; `verify-demo-wrapper.sh --require-coverage` rc=0; handler tests 126 OK). PR #4441 CI: SUCCESS 9 · SKIPPED 58 · failure 0; branch nightly run 39573773365 success.
- **AC-3 ✅** — `infra/demo/aws/README.md` step 2 runbook.
- 4-dim: (a) #4441 `state=MERGED` · (b) `5ad7eaaa8` = `origin/main` tip at close · (c) failure 0 at merge · (d) AC-0 … AC-3 closed (above). Owner approved the postcondition `error_message` wording before merge (2026-10-21 UTC).
