# scripts/

Repo-level automation. All scripts are re-runnable; none store state outside `$TMPDIR`.

---

## `sync-portfolio.sh`

Extracts each project under `projects/<name>/` into its own standalone GitHub repo with full git history preserved.

### Usage

```bash
./scripts/sync-portfolio.sh              # sync all configured projects
./scripts/sync-portfolio.sh wms-platform # sync a single project
./scripts/sync-portfolio.sh --dry-run    # show plan without executing
```

### How it works

1. Clones the monorepo into `$TMPDIR/portfolio-sync/<project>`.
2. Runs `git-filter-repo` inside a `python:3.11-alpine` container to:
   - Keep only shared-library paths (`libs/`, `platform/`, `rules/`, `.claude/`, gradle wrappers, etc.) + `projects/<name>/`
   - Hoist `projects/<name>/` to the repo root via `--path-rename`
3. Post-processes in the extracted clone:
   - Restores the monorepo root `build.gradle` (which `--path-rename` overwrote with the project's placeholder)
   - Rewrites `settings.gradle` subproject paths (`projects:<name>:apps:...` → `apps:...`) and `rootProject.name`
   - Commits the rewrites as a single `chore(portfolio)` commit
4. Force-pushes to the configured remote.

Docker is required (the script avoids a local Python install by using a throwaway container). Uses `MSYS_NO_PATHCONV=1` on Git Bash so Windows doesn't mangle the `/repo` mount path.

### History preservation

Full history preserved — every monorepo commit that touched a kept path is retained in the extracted repo. SHAs change (expected for any `filter-repo` run).

### Configured projects

Defined in the `PROJECT_REMOTES` associative array near the top of the script. To add a new project:

```bash
PROJECT_REMOTES["my-new-project"]="https://github.com/kanggle/my-new-project.git"
```

Ensure the target repo exists on GitHub first (`gh repo create kanggle/my-new-project --public --description "..."`).

### When to re-run

- After any milestone worth publishing (aggregate completion, CI hardening, contract update).
- Before updating a resume / LinkedIn link if the individual repo URL appears there.
- Monthly rhythm is typical for a portfolio project.

### Why force-push

The extraction rewrites history every run (commit SHAs change). Force-push is the only way to land rewritten history on the remote. This is safe for portfolio repos because nobody else is committing directly to them; any external stargazer / forker is not disrupted since they already have their own copy.
