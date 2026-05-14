# hardstop-detect.ps1 — auto-inject Hard Stop 4-block remediation messages
#
# PreToolUse hook for Edit + Write. Detects the 5 mechanically-detectable Hard
# Stop triggers (HARDSTOP-01 / -03 / -05 / -09 / -10) and emits a JSON
# {decision: "block", reason: <4-block stanza>} so the agent's next turn
# receives the standardised remediation message.
#
# Stanza body source = platform/hardstop-rules.md (single source of truth,
# mirrored as a catalog in CLAUDE.md § Hard Stop Rules — see TASK-MONO-099).
# Hook injects only the file path / line number into the parameterised template.
# Drift between hook output and the canonical platform/hardstop-rules.md stanza
# is currently a manual-sync responsibility; the fixtures under
# .claude/hooks/__tests__/ guard the stanza ID and 4-block shape but do not
# byte-compare body content (see TASK-MONO-099 § Out of Scope for the
# drift-detection follow-up).
#
# Reference: platform/lint-remediation-message-standard.md
# Provenance: TASK-MONO-060 (Phase 3 of OpenAI Harness gap A).

$reader    = New-Object System.IO.StreamReader([Console]::OpenStandardInput(), [System.Text.Encoding]::UTF8)
$inputJson = $reader.ReadToEnd()

function Write-Block {
    param([string]$Stanza)
    $result = @{
        decision = "block"
        reason   = $Stanza
    }
    $result | ConvertTo-Json -Compress
    exit 0
}

function Get-RepoRoot {
    param([string]$StartPath)
    if (-not $StartPath) { return $null }
    $dir = if (Test-Path $StartPath -PathType Container) { $StartPath } else { Split-Path -Parent $StartPath }
    while ($dir -and (Test-Path $dir)) {
        if (Test-Path (Join-Path $dir ".git") -PathType Container) { return $dir }
        if (Test-Path (Join-Path $dir "CLAUDE.md") -PathType Leaf -ErrorAction SilentlyContinue) {
            # Heuristic: a sibling CLAUDE.md at a level that also has tasks/INDEX.md is the monorepo root
            if (Test-Path (Join-Path $dir "tasks/INDEX.md") -PathType Leaf -ErrorAction SilentlyContinue) {
                return $dir
            }
        }
        $parent = Split-Path -Parent $dir
        if ($parent -eq $dir) { break }
        $dir = $parent
    }
    return $null
}

function Find-ProjectMdAncestor {
    param([string]$StartPath, [string]$RepoRoot)
    if (-not $StartPath) { return $null }
    $dir = if (Test-Path $StartPath -PathType Container) { $StartPath } else { Split-Path -Parent $StartPath }
    while ($dir) {
        if (Test-Path (Join-Path $dir "PROJECT.md") -PathType Leaf -ErrorAction SilentlyContinue) {
            return $dir
        }
        if ($RepoRoot -and ($dir -eq $RepoRoot)) { break }
        $parent = Split-Path -Parent $dir
        if (-not $parent -or $parent -eq $dir) { break }
        $dir = $parent
    }
    return $null
}

try {
    $data = $inputJson | ConvertFrom-Json
    $filePath   = ""
    $oldString  = ""
    $newString  = ""

    if ($data.tool_input) {
        if ($data.tool_input.file_path)   { $filePath  = $data.tool_input.file_path }
        if ($data.tool_input.old_string)  { $oldString = $data.tool_input.old_string }
        if ($data.tool_input.new_string)  { $newString = $data.tool_input.new_string }
        elseif ($data.tool_input.content) { $newString = $data.tool_input.content }
    }

    if (-not $filePath) { exit 0 }

    # ===== Allowlist =====
    #
    # portfolio-sync derivation workdirs (mirrors protect-main-branch.ps1):
    # scripts/sync-portfolio.sh rewrites extracted copies of each project.
    if ($filePath -match 'portfolio-sync') { exit 0 }
    if ($data.cwd -and $data.cwd -match 'portfolio-sync') { exit 0 }

    # Meta-config: edits to .claude/hooks/ itself MAY reference project names in
    # detection-pattern allow-lists. Skip self-fire.
    $relPath = $filePath -replace '\\', '/'
    if ($relPath -match '(?:^|/)\.claude/hooks/') { exit 0 }

    $repoRoot = Get-RepoRoot -StartPath $filePath
    if (-not $repoRoot) {
        # File is entirely outside any monorepo — hook has no jurisdiction. Fail open.
        # HARDSTOP-01 still fires below for files INSIDE a monorepo without a PROJECT.md ancestor.
        exit 0
    }

    # Compute path relative to repo root for clearer stanza messages
    $repoRootNorm = ($repoRoot -replace '\\', '/').TrimEnd('/')
    $relFromRoot = $relPath
    if ($relPath.StartsWith($repoRootNorm)) {
        $relFromRoot = $relPath.Substring($repoRootNorm.Length).TrimStart('/')
    }

    # Shared paths — files that must remain project-agnostic
    $sharedPathPattern = '^(platform|rules|\.claude|libs|tasks/templates|docs/guides|CLAUDE\.md|TEMPLATE\.md|README\.md|build\.gradle|settings\.gradle)(?:$|/)'
    $isSharedFile = $relFromRoot -match $sharedPathPattern

    # ===== HARDSTOP-05: edit on task file under tasks/in-progress/ or tasks/review/ =====
    # These are frozen per tasks/INDEX.md § Move Rules + projects/<name>/tasks/INDEX.md.
    #
    # Lifecycle Status-field moves are allowed: simulate each `<from>` -> `<to>` lifecycle swap
    # on old_string. If swapping the first occurrence yields exactly new_string, the edit is a
    # lifecycle move. Catches both single-token edits and contextual edits like
    # "# Status\n\nready" -> "# Status\n\nreview".
    $lifecycleTokens = @('ready', 'in-progress', 'review', 'done')
    $isLifecycleMove = $false
    if ($oldString -and $newString -and $oldString -ne $newString) {
        foreach ($from in $lifecycleTokens) {
            foreach ($to in $lifecycleTokens) {
                if ($from -eq $to) { continue }
                $idx = $oldString.IndexOf($from)
                if ($idx -ge 0) {
                    $simulated = $oldString.Substring(0, $idx) + $to + $oldString.Substring($idx + $from.Length)
                    if ($simulated -eq $newString) {
                        $isLifecycleMove = $true
                        break
                    }
                }
            }
            if ($isLifecycleMove) { break }
        }
    }

    if (-not $isLifecycleMove -and $relFromRoot -match '(?:^|/)tasks/(in-progress|review)/[^/]+\.md$') {
        $stage  = $matches[1]
        $taskId = (($relFromRoot -split '/') | Select-Object -Last 1) -replace '\.md$', ''
        $stanza = @"
[VIOLATION] HARDSTOP-05: Task ``$taskId`` is being edited under ``$relFromRoot`` which is in ``tasks/$stage/`` — frozen per lifecycle rules (only tasks in ``ready/`` may be implemented; ``in-progress`` / ``review`` / ``done`` files must not be modified except via lifecycle Status-field moves).
[WHY] Only tasks in ``ready/`` may be implemented; ``in-progress/`` / ``review/`` / ``done/`` tasks are frozen, and unfiled work bypasses lifecycle review. The ready-queue signal is the public surface external observers read to know what's available.
[REMEDIATION] Choose one:
  1. If the work is new, author the task file in the correct ``tasks/ready/`` (root ``tasks/ready/`` for monorepo-level work per ``tasks/INDEX.md``; ``projects/<name>/tasks/ready/`` for project-internal work) and land it via a spec PR before any impl commits.
  2. If the work is a fix to an already-merged task, create a new fix task in ``ready/`` referencing the original task ID in its Goal section (per ``tasks/INDEX.md`` § Review Rules).
  3. If unclear which lifecycle applies, consult ``tasks/INDEX.md`` § "When to Use Root vs Project Tasks" decision table.
[REFERENCE] CLAUDE.md § Task Rules + tasks/INDEX.md § Move Rules
"@
        Write-Block $stanza
    }

    # ===== HARDSTOP-01: no PROJECT.md walking up from a project-relative path =====
    # Skip for shared paths (these are repo-root-level and have no PROJECT.md by design).
    # Skip for paths outside both shared and projects/ (e.g. /tmp scratch).
    if (-not $isSharedFile) {
        $isUnderProjects = $relFromRoot -match '^projects/'
        if ($isUnderProjects) {
            # Resolve absolute path of the directory to check
            $absDir = Join-Path $repoRoot (Split-Path -Parent $relFromRoot)
            $projectMdDir = Find-ProjectMdAncestor -StartPath $absDir -RepoRoot $repoRoot
            if (-not $projectMdDir) {
                $stanza = @"
[VIOLATION] HARDSTOP-01: No ``PROJECT.md`` is locatable walking up from ``$relFromRoot`` to the repo root.
[WHY] Every implementation request must resolve to exactly one project so rule layers (domain + traits) can be loaded; without ``PROJECT.md`` the rule resolver has no anchor and would silently default — Identify the Target Project is a CLAUDE.md prerequisite, not a fallback.
[REMEDIATION] Choose one:
  1. Move the working location into an existing project: ``cd projects/<name>/`` where ``<name>`` matches the request scope (see ``docs/project-overview.md`` § 2.1 for the 5 active projects).
  2. If the request is monorepo-level (touching ``libs/``, ``platform/``, ``rules/``, ``.claude/``, ``tasks/templates/``, ``docs/guides/``, ``CLAUDE.md``, ``TEMPLATE.md``), reframe as a root task per ``tasks/INDEX.md`` § "When to Use Root vs Project Tasks" and operate from repo root.
  3. If a new project is genuinely needed, file a ``tasks/ready/TASK-MONO-XXX-bootstrap-<project>.md`` and pause; do not implement before the project skeleton lands.
[REFERENCE] CLAUDE.md § Identify the Target Project (Read First)
"@
                Write-Block $stanza
            }
        }
    }

    # ===== HARDSTOP-03: shared library file contains project-specific content =====
    # Fire when target is shared AND new_string references a project name as a path token
    # or as a code-fenced identifier. Per-line `<!-- hardstop-allow: <reason> -->` annotation
    # immediately above a line suppresses detection for that line.
    if ($isSharedFile -and $newString) {
        # Build project list from repo (cached only within this invocation)
        $projectsDir = Join-Path $repoRoot "projects"
        $projectNames = @()
        if (Test-Path $projectsDir -PathType Container) {
            $projectNames = Get-ChildItem -Path $projectsDir -Directory -ErrorAction SilentlyContinue |
                Where-Object { Test-Path (Join-Path $_.FullName "PROJECT.md") -PathType Leaf } |
                ForEach-Object { $_.Name }
        }
        # Also accept the short forms used colloquially
        $shortAliases = @{
            'ecommerce-microservices-platform' = @('ecommerce', 'ecom')
            'fan-platform'                     = @('fan-platform', 'fan')
            'global-account-platform'          = @('global-account-platform', 'gap')
            'scm-platform'                     = @('scm-platform', 'scm')
            'wms-platform'                     = @('wms-platform', 'wms')
        }
        $allTokens = @()
        foreach ($name in $projectNames) {
            $allTokens += $name
            if ($shortAliases.ContainsKey($name)) {
                $allTokens += $shortAliases[$name]
            }
        }
        $allTokens = $allTokens | Sort-Object -Unique

        $lines = $newString -split "`r?`n"
        $hit = $null
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            # Skip if previous line carries the allow annotation
            if ($i -gt 0 -and $lines[$i - 1] -match '<!--\s*hardstop-allow:') { continue }
            foreach ($tok in $allTokens) {
                # Path-token form: "projects/<tok>/" or "apps/<tok>/"
                if ($line -match "(?:projects|apps)/$([regex]::Escape($tok))/") {
                    $hit = @{ token = $tok; line = $i + 1; raw = $line.Trim() }
                    break
                }
            }
            if ($hit) { break }
        }
        if ($hit) {
            $tok    = $hit.token
            $lineNo = $hit.line
            $stanza = @"
[VIOLATION] HARDSTOP-03: Shared library file ``$relFromRoot`` references project ``$tok`` (path-token form) at line $lineNo.
[WHY] Shared paths (``platform/``, ``rules/``, ``.claude/``, ``libs/``, ``tasks/templates/``, ``docs/guides/``) must remain project-agnostic so every project can adopt them unchanged; mixing project-specific content here breaks the Library vs Project boundary that this rule library is built on.
[REMEDIATION] Choose one:
  1. Move the offending content back to the owning project under ``projects/$tok/`` (apps / specs / knowledge / docs as appropriate) and keep the shared file generic.
  2. If the content is genuinely cross-service / cross-project, propose promotion via ``docs/adr/ADR-MONO-XXX-<slug>.md`` proposing a generic abstraction, and PAUSE this task until the ADR is ACCEPTED.
  3. If the content is documentation noise (example / illustration), replace it with an abstract placeholder (``<service>``, ``<entity>``) per existing precedent — or add an ``<!-- hardstop-allow: <reason> -->`` annotation on the preceding line if the reference is intentional.
[REFERENCE] platform/shared-library-policy.md § Forbidden in Shared Libraries
"@
            Write-Block $stanza
        }
    }

    # ===== HARDSTOP-09 / HARDSTOP-10 =====
    # Detection scope: edits under projects/<name>/apps/<service>/src/main/ (HARDSTOP-09)
    # or edits to projects/<name>/specs/services/<service>/architecture.md (HARDSTOP-10).
    if ($relFromRoot -match '^projects/(?<proj>[^/]+)/apps/(?<svc>[^/]+)/src/main/') {
        $proj = $matches['proj']
        $svc  = $matches['svc']
        $archPath = Join-Path $repoRoot ("projects/$proj/specs/services/$svc/architecture.md")
        if (-not (Test-Path $archPath -PathType Leaf)) {
            $stanza = @"
[VIOLATION] HARDSTOP-09: Task implementation under ``projects/$proj/apps/$svc/src/main/`` requires a service architecture declaration, but ``projects/$proj/specs/services/$svc/architecture.md`` does not exist.
[WHY] Architecture decisions made implicitly during implementation produce code that later cannot be defended against "why was this chosen" review questions — and shape every downstream task that builds on the same service. The Architecture Decision Rule (``platform/architecture-decision-rule.md``) forbids choosing architecture during implementation.
[REMEDIATION] Choose one:
  1. Author / update ``projects/$proj/specs/services/$svc/architecture.md`` recording the decision (chosen style + rejected alternatives + reason) and land the spec change before any code commit.
  2. If the decision is significant (cross-service, irreversible, or shapes other services), record it in ``projects/$proj/docs/adr/ADR-<scope>-XXX-<slug>.md`` and PAUSE until ACCEPTED.
  3. If the decision is reversible and local (single class / single endpoint), implement with an inline comment citing the choice + one-line reason and file a follow-up ``tasks/ready/`` task to backfill the architecture.md update.
[REFERENCE] CLAUDE.md § Layer Rules + platform/architecture-decision-rule.md
"@
            Write-Block $stanza
        }
    }

    if ($relFromRoot -match '^projects/(?<proj>[^/]+)/specs/services/(?<svc>[^/]+)/architecture\.md$') {
        $proj = $matches['proj']
        $svc  = $matches['svc']
        # Resolve the post-edit content. For Write, $newString IS the full file content.
        # For Edit, simulate by replacing $oldString with $newString in the existing file (if any).
        $simContent = ""
        $absFile = Join-Path $repoRoot $relFromRoot
        if ($data.tool_name -eq 'Write' -or ($data.tool_input -and $data.tool_input.content)) {
            $simContent = $newString
        } elseif (Test-Path $absFile -PathType Leaf) {
            $existing = Get-Content -Raw -Path $absFile -ErrorAction SilentlyContinue
            if ($existing -and $oldString -and $newString -and $existing.Contains($oldString)) {
                $simContent = $existing.Replace($oldString, $newString)
            } elseif ($existing) {
                $simContent = $existing
            } else {
                $simContent = $newString
            }
        } else {
            $simContent = $newString
        }

        # Load valid Service Type catalog from platform/service-types/INDEX.md
        $stCatalog = @()
        $stIndexPath = Join-Path $repoRoot "platform/service-types/INDEX.md"
        if (Test-Path $stIndexPath -PathType Leaf) {
            $stIndex = Get-Content -Raw -Path $stIndexPath -ErrorAction SilentlyContinue
            if ($stIndex) {
                $stCatalog = [regex]::Matches($stIndex, '`([a-z][a-z0-9-]+)`') |
                    ForEach-Object { $_.Groups[1].Value } |
                    Where-Object { $_ -match '^(rest-api|event-consumer|batch-job|grpc-service|graphql-service|ml-pipeline|frontend-app|identity-platform)$' } |
                    Sort-Object -Unique
            }
        }
        # Fallback to known catalog if INDEX parse missed
        if (-not $stCatalog -or $stCatalog.Count -eq 0) {
            $stCatalog = @('rest-api', 'event-consumer', 'batch-job', 'grpc-service', 'graphql-service', 'ml-pipeline', 'frontend-app', 'identity-platform')
        }

        # Heuristic: architecture.md MUST contain a "Service Type" header (or `**Service Type**`) followed
        # within the next ~10 lines by one of the valid catalog values.
        $hasValidType = $false
        if ($simContent -match '(?im)(?:^#+\s*Service\s+Type|\*\*Service\s+Type\*\*\s*[:|])') {
            $matchedSection = $matches[0]
            # Look for any catalog value in the section trailing 600 chars
            $idx = $simContent.IndexOf($matchedSection, [StringComparison]::OrdinalIgnoreCase)
            if ($idx -ge 0) {
                $tail = $simContent.Substring($idx, [Math]::Min(600, $simContent.Length - $idx))
                foreach ($t in $stCatalog) {
                    if ($tail -match "\b$([regex]::Escape($t))\b") {
                        $hasValidType = $true
                        break
                    }
                }
            }
        }

        if (-not $hasValidType) {
            $stanza = @"
[VIOLATION] HARDSTOP-10: Service Type is undeclared or not in ``platform/service-types/INDEX.md`` catalog at ``$relFromRoot`` (post-edit content does not declare a recognised Service Type).
[WHY] Service Type is the orthogonal axis (independent of domain/trait) that determines which ``platform/service-types/<type>.md`` file is loaded per the Required Workflow — without it, the type-specific rule layer is empty and the session would proceed with no service-type guidance.
[REMEDIATION] Choose one:
  1. Declare the Service Type in ``$relFromRoot`` under the standard "Service Type" header, choosing one of: ``$(($stCatalog -join '`, `'))``.
  2. If the existing service-type catalog has no fit, open ``tasks/ready/TASK-MONO-XXX-add-service-type-<name>.md`` proposing the new type with a ``platform/service-types/<new-type>.md`` file + INDEX update; PAUSE until landed.
  3. If the service is genuinely typeless (e.g. a test fixture or non-service shared module), reframe — it should not be under ``specs/services/`` in the first place.
[REFERENCE] CLAUDE.md § Required Workflow step 7 + platform/service-types/INDEX.md
"@
            Write-Block $stanza
        }
    }

    # No trigger fired — allow the tool call silently.
    exit 0
}
catch {
    # Fail-open: any hook crash allows the tool call (matches existing hooks' posture).
    exit 0
}
