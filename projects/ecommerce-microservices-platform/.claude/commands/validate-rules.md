---
name: validate-rules
description: Scan all rule files (CLAUDE.md, specs, skills, agents, commands) for inconsistencies
---

# validate-rules

Scan all rule files for inconsistencies, conflicts, duplications, and missing references.

## Usage

```
/validate-rules                                            # full scan of all rule files
/validate-rules --focus=<type>                             # scan only a specific type
```

Focus values: `specs`, `skills`, `agents`, `commands`

Examples:

```
/validate-rules
/validate-rules --focus=agents
/validate-rules --focus=skills
```

## Procedure

### Phase 1: Collect all rule files

1. Read `CLAUDE.md`
2. List and read all files in:
   - `specs/platform/`
   - `.claude/skills/**/SKILL.md` (each skill is a folder containing `SKILL.md`, per INDEX.md)
   - `.claude/agents/`
   - `.claude/commands/`

### Phase 2: Cross-reference checks

#### 2-1. Spec Consistency
- [ ] No two platform specs define the same rule differently
- [ ] Cross-references between specs point to existing files
- [ ] `CLAUDE.md` rules do not conflict with platform specs

#### 2-2. Skill → Spec Alignment
- [ ] Every skill references at least one spec as prerequisite
- [ ] Skill patterns match the rules in referenced specs
- [ ] Skill code examples follow `coding-rules.md` and `naming-conventions.md`
- [ ] No skill contradicts its referenced spec

#### 2-3. Agent → Skill/Spec Alignment
- [ ] Agent `skills:` field references existing skill files
- [ ] Agent checklist items align with referenced specs
- [ ] Agent `Does NOT` section does not conflict with workflow steps
- [ ] No two agents have overlapping responsibilities without clear boundaries

#### 2-4. Command → Agent/Skill/Spec Alignment
- [ ] Command procedures reference existing specs and skills
- [ ] Command agent prompt templates are consistent with agent definitions
- [ ] No two commands do the same thing (functional duplication)
- [ ] Command names follow a consistent naming pattern
- [ ] Composite commands (e.g., `process-tasks`) that reference other commands' procedures remain consistent with those commands' current rules (subagent_type, isolation, merge order, etc.)

#### 2-5. Reference Integrity
- [ ] All `specs/` paths referenced in skills, agents, and commands exist
- [ ] All `.claude/skills/**/SKILL.md` paths referenced in agents exist
- [ ] `skills/INDEX.md` lists every `SKILL.md` present under `.claude/skills/`
- [ ] No orphaned skill folders (contain `SKILL.md` but not listed in INDEX)
- [ ] Every skill folder contains exactly one `SKILL.md`; no stray `.md` files under `.claude/skills/` except `INDEX.md` (drift = Critical)
- [ ] Every `SKILL.md` begins with YAML frontmatter containing `name`, `description`, `category` (missing field = Critical)

#### 2-6. Service Type and Metadata Drift

This section was added to support the `service-types` catalog and agent capability metadata. All checks are read-only — `validate-rules` reports drift but never blocks via hooks.

- [ ] `.claude/skills/**/SKILL.md` file set equals the path set listed in `skills/INDEX.md` "Available Skills" table (drift = Critical)
- [ ] `specs/platform/service-types/*.md` file set equals the catalog listed in `specs/platform/service-types/INDEX.md` (drift = Critical)
- [ ] Every `specs/services/<service>/architecture.md` declares a `Service Type` and the value is one of the catalog entries (missing or invalid = Critical)
- [ ] Every entry in `skills/INDEX.md` "Default Skill Sets by Task Type" table resolves to an existing skill file (missing = Critical)
- [ ] Every `.claude/agents/*.md` frontmatter contains all of `capabilities`, `languages`, `domains`, `service_types` fields (missing field = Warning)
- [ ] Every `service_types` value in agent frontmatter is one of the `specs/platform/service-types/INDEX.md` catalog entries or the literal `all` (invalid value = Critical)
- [ ] Every skill referenced from a service-type spec's "Default Skill Set" exists under `.claude/skills/` (missing = Critical)

### Phase 3: Report

Output the validation report:

```
## Rule Validation Report

### Critical (must fix)
- [file] description of conflict or inconsistency

### Warning (should fix)
- [file] description of issue

### Info (consider)
- [file] suggestion for improvement

### Summary
- Files scanned: N
- Critical: N
- Warning: N
- Info: N
- Status: PASS / FAIL
```

## Rules

- Read-only — do not modify any files
- Report all issues found, do not stop at the first one
- Classify severity: Critical (contradictions, missing refs) > Warning (duplications, weak refs) > Info (naming, suggestions)
- When reporting a conflict between two documents, always specify which document is authoritative per CLAUDE.md Source of Truth Priority (higher-priority document wins, lower-priority document should be corrected)
- Proceed without asking confirmation questions
