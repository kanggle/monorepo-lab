# Lint Remediation Message Standard

Canonical format for every Hard Stop and rule-violation emission across the shared rule surface (`CLAUDE.md`, `platform/`, `rules/`).

The standard exists because a rule-violation message has two audiences:
1. The **human reviewer** reading the session log after the fact.
2. The **agent's next turn** — the message is the only context the agent gets to choose what to do next.

A prose stop ("required architecture is missing — stop and report") satisfies audience (1) and starves audience (2). The 4-block standard below satisfies both: each block carries one explicit signal the agent can replay verbatim in its next tool call.

Reference: OpenAI Harness Engineering (Lopopolo, 2025) — "custom lint error messages = agent's next-turn context". Decision record: [`docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md`](../docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md).

---

# Template

Every violation emission MUST carry exactly these four blocks, in this order:

```
[VIOLATION] <rule_id>: <one-line condition that fired> at <file>:<line | section-anchor>
[WHY] <invariant the rule protects — one sentence; cite the named principle or prior incident>
[REMEDIATION] Choose one:
  1. <concrete corrective action with file paths>
  2. <alternative corrective action with file paths>
  3. <escalation path: open ADR / file ready/ task / ask owner>
[REFERENCE] <rule-file-path> §<section-anchor>
```

## Block authoring rules (binding)

### `[VIOLATION]`

- `<rule_id>` is required and uses one of two forms:
  - **`HARDSTOP-NN`** (NN = 01–10) — for the 10 Hard Stop triggers numbered in [`CLAUDE.md § Hard Stop Rules`](../CLAUDE.md#hard-stop-rules).
  - **`<source-shortname>-NN`** — for non-blocking warnings, where `<source-shortname>` matches the short name of the originating rule file (e.g. `SHARED-LIB`, `ARCH-RULE`, `EVENT-DRIVEN`, `NAMING`, `TESTING`).
- `<file>:<line | section-anchor>` MUST be unambiguous — either a real file path with a line number, or a file path with a section anchor (`platform/foo.md#bar`).
- One-line condition is imperative or declarative present tense, never future ("would fail").

### `[WHY]`

- One sentence (rare exceptions: two short sentences for compound invariants).
- States the invariant being protected, not the symptom. Good: "Shared libraries must remain project-agnostic so any project can adopt them unchanged." Bad: "This file imports a service-specific class."
- If the rule traces to a named principle (e.g. "Library vs Project boundary" from `CLAUDE.md`) or a prior incident (e.g. "PORT_PREFIX scaling crisis, ADR-MONO-001"), cite it inline. Citation gives future readers — and the agent's compaction layer — a stable anchor.

### `[REMEDIATION]`

- MUST list **at least two options OR one option + one escalation path**. Single-option lists are forbidden — the value of the standard is its choice surface; without alternatives the agent has nothing to pick between.
- Each option is imperative ("Add ...", "Remove ...", "Move ...", "File a ready/ task ...") and cites concrete file paths or commands.
- Vague phrasing is forbidden. The following are NOT acceptable option phrasings:
  - "review the spec"
  - "investigate the issue"
  - "consult the team"
  - "consider refactoring"
- The escalation path option (where present) is the explicit fallback when the in-band remediations are not applicable. Common forms:
  - "Open an ADR under `docs/adr/` proposing a new <decision> and pause this task until ACCEPTED."
  - "File a new ready/ task `tasks/ready/TASK-<scope>-XXX-<slug>.md` capturing the unresolved decision."
  - "Ask the project owner (`<project>/PROJECT.md` owner field) and pause."
- Numbered (`1.`, `2.`, `3.`) — not bulleted. The numbering lets a human or agent reference "remediation option 2" unambiguously in a follow-up message.

### `[REFERENCE]`

- Required, single line, exactly one citation.
- MUST resolve to an existing section anchor in the named file. Broken anchors fail review.
- For Hard Stop stanzas, the canonical reference is `CLAUDE.md § Hard Stop Rules` plus the specific stanza header. For non-blocking warnings, point to the originating rule file.
- Cross-references to detail files (e.g. `platform/shared-library-policy.md § Forbidden in Shared Libraries`) are encouraged for emissions whose `[WHY]` benefits from the longer rule body.

---

# Worked example — Hard Stop

```
[VIOLATION] HARDSTOP-03: Shared library file `libs/java-messaging/src/main/java/.../WmsOutboxRow.java` references service-specific type `WmsOutboundOrder` at line 42.
[WHY] Shared libraries must remain project-agnostic so any project can adopt them unchanged; mixing service-specific types into libs/ breaks the Library vs Project boundary enforced as a Hard Stop in CLAUDE.md.
[REMEDIATION] Choose one:
  1. Move the offending type back to the owning service: relocate the `WmsOutboundOrder` reference to `projects/wms-platform/apps/outbound-service/` and keep `libs/java-messaging/` generic.
  2. If the type is genuinely cross-service, propose promotion: open `docs/adr/ADR-MONO-XXX-<slug>.md` proposing a generic abstraction in `libs/java-messaging/`, and PAUSE this task until the ADR is ACCEPTED.
[REFERENCE] platform/shared-library-policy.md § Forbidden in Shared Libraries
```

# Worked example — non-blocking warning

```
[VIOLATION] NAMING-04: Class name `userMgr` in `apps/admin-service/src/main/java/.../UserMgr.java` uses lowercase-leading abbreviated form at line 1.
[WHY] Project-wide naming convention requires PascalCase + full nouns (no abbreviated forms) so identifiers remain searchable across the monorepo.
[REMEDIATION] Choose one:
  1. Rename to `UserManagementService` (or other PascalCase full-noun form) using IDE rename refactoring; update all 14 referencing sites flagged by the linter.
  2. If the abbreviated form is justified (e.g. preserved external API surface), add an `// NAMING-04 exempt: <one-sentence reason>` annotation directly above the class declaration, and file a `tasks/ready/` task documenting the exception for spec drift review.
[REFERENCE] platform/naming-conventions.md § Classes and Interfaces
```

---

# Emission contracts

The standard applies in three emission contexts. The table below states the contract for each.

| Context | Who emits | Format requirement | Forward action |
|---|---|---|---|
| **Hard Stop trigger fires** | Agent / human reviewer reading `CLAUDE.md` Hard Stop Rules | MUST emit 4-block stanza. The session MUST halt implementation tool calls (Edit / Write / Bash mutation) on the affected scope until a remediation option is chosen. | Pick a remediation option (1 / 2 / …) and execute it, OR escalate per the listed escalation path. |
| **Non-blocking rule warning** | Agent / skill noticing a soft rule mismatch (e.g. spec drift, missing test class for a new module) | SHOULD emit 4-block stanza. Implementation may continue if the warning is non-blocking, but the message MUST be surfaced to the user so the chosen remediation is auditable. | Either fix in-band before commit, or file the deferral as a `tasks/ready/` follow-up. |
| **Skill finding (`validate-rules`, `audit-memory`, future similar)** | User-level / plugin-supplied skill | RECOMMENDED 4-block stanza per finding (advisory — skill bodies are out of scope of this monorepo's spec). | Skill-specific. See note below. |

## Skill body alignment — out of scope

`validate-rules` and `audit-memory` are user-level / plugin-supplied skills not present in this repo's [`.claude/skills/`](../.claude/skills/). The standard documents the *recommended* output format so these skills' outputs slot into the same shape as agent-emitted Hard Stop messages, but the actual skill body alignment is a separate PR landing in the plugin repository.

This decision is recorded in [`docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md`](../docs/adr/ADR-MONO-006-lint-remediation-as-agent-context.md) § D3.

---

# Multiple simultaneous violations

A session that hits more than one Hard Stop / warning condition at the same time MUST emit **one stanza per fired condition**, not a merged stanza. Each stanza carries its own `[VIOLATION]` / `[WHY]` / `[REMEDIATION]` / `[REFERENCE]` blocks.

Rationale: the remediation options for distinct conditions are rarely compatible — `HARDSTOP-03` (shared-lib boundary) and `HARDSTOP-05` (task not in ready/) require different actions, so collapsing them would force the agent to invent a synthesis the standard does not authorize.

---

# Forward look — Phase 3 hook automation

The standard alone delivers **format consistency** when an emitter (agent / human / skill) follows it. It does **not** yet make the formatted message *active context* on the agent's next turn — that requires a PreToolUse or Stop hook in `.claude/hooks/` that auto-detects the trigger condition and injects the standardized message into the prompt.

Phase 3 is split into a separate `tasks/ready/` candidate (`TASK-MONO-060` — to be filed). Phase 1+2 ROI is **partial** until Phase 3 lands.

The OpenAI Harness Engineering report (the source of this pattern) explicitly describes the mechanism as "custom lint error messages = agent's next-turn context" — the value emerges when the message is *injected*, not just *available*. The standard is a prerequisite for that injection to be useful, but is not by itself sufficient.

---

# Change protocol

- Adding a new Hard Stop trigger to `CLAUDE.md` MUST also add a new `HARDSTOP-NN` stanza in this format (NN extends the existing 1–10 sequence).
- Renaming or restructuring sections referenced by `[REFERENCE]` blocks MUST grep this file for affected anchors and update them in the same PR.
- Promoting a non-blocking warning to a Hard Stop: update both this file (template change `<source-shortname>-NN` → `HARDSTOP-NN`) and the originating rule file in the same PR.
