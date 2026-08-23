# Architecture Decision Rule

This document defines how service architecture must be selected and documented.

---

# Purpose

Different services may use different internal architectures.

Architecture must not be chosen arbitrarily during implementation.

The architecture for each service must be declared in:

`specs/services/<service>/architecture.md`

---

# Mandatory Rule

- Every service must declare its architecture explicitly.
- AI agents and developers must follow the declared architecture.
- Do not change service architecture implicitly during implementation.
- If the declared architecture is missing, emit the Hard Stop stanza below (per [`lint-remediation-message-standard.md`](lint-remediation-message-standard.md)) and halt implementation tool calls until a remediation option is chosen:

```
[VIOLATION] ARCH-RULE-01: Service architecture is not declared in `<project>/specs/services/<service>/architecture.md`.
[WHY] Architecture chosen during implementation cannot be defended against future "why was this chosen" review questions and shapes every downstream task on the service; the rule library forbids implicit architecture decisions.
[REMEDIATION] Choose one:
  1. Author the architecture spec under `<project>/specs/services/<service>/architecture.md` declaring the chosen style (Hexagonal / Layered / Clean / …) with rejected alternatives + reason; land the spec change before any code commit.
  2. If the decision is cross-service or irreversible, record it in `<project>/docs/adr/ADR-<scope>-XXX-<slug>.md` and PAUSE until ACCEPTED.
  3. If the decision is reversible and local, file a `tasks/ready/` follow-up task to backfill the architecture.md and reference its task ID in an inline code comment.
[REFERENCE] platform/architecture-decision-rule.md § Mandatory Rule + platform/hardstop-rules.md § HARDSTOP-09
```

---

# The ACCEPTED Gate — what actually lifts a PAUSE

Where this document (and an ADR's own lifecycle) says *"PAUSE until ACCEPTED"*, the pause is lifted **only**
by an explicit human decision that **names the ADR** — for example `ADR-<scope>-0NN ACCEPTED`.

- A bare **"진행" / "proceed" / "go ahead" / "OK"** does **NOT** accept an ADR, even when it replies directly
  to the message that proposed it, and even when the intent seems obvious from context. Approval to *continue
  the conversation* is not approval of *the architecture decision*. The two are routinely conflated because
  they arrive in the same breath.
- An agent **MUST NOT self-ACCEPT** an ADR it proposed. Authoring the proposal and ratifying it are different
  roles; an agent occupying both makes the gate decorative. This holds no matter how confident the agent is,
  and no matter how thoroughly it argued the alternatives.
- Until the exact-form intent arrives, an ADR stays **PROPOSED** and dependent implementation stays paused.
  Record the proposal, state plainly what is blocked on it, and stop.

**Why the exact form.** The gate exists to make an architecture decision *attributable* — someone chose this,
on the record, knowing it was a decision. A gate that any affirmative noise can open is not a gate; it
launders an agent's own preference into an accepted decision, which is exactly what § Prohibited Decisions
forbids.

## The three requirements do not substitute for each other

An accepting message must carry **all three**: the **ADR's name**, the word **`ACCEPTED`**, and — when the ADR
offers options — the **option letter, actually designated**. Check them **separately**. Two of three passing is
not "close enough"; it is the shape every near-miss below takes.

The two cases above (bare "진행", self-ACCEPT) are the easy ones. The dangerous shape is the message that is
*almost* right — where the temptation to wave it through is highest, and where waving it through is
indistinguishable from the laundering that § Prohibited Decisions forbids. All four below actually happened.

| Form | Why it looks like it passes | Verdict | Do this |
|---|---|---|---|
| ① **Placeholder letter** — `ADR-X-0NN ACCEPTED — <A\|B\|C>` (or a blank, or "추천대로") | Names the ADR, says `ACCEPTED` | **Not designated.** A template placeholder echoed back is not a choice — and a "recommended: A" beside it is *the agent's own*. | Stop; re-ask. |
| ② **Letter routed through a recommendation** — "decided per the recommendation, D" | The owner typed `D`; the verb is "decided" | **Source is the agent.** If "the recommendation" points at the implementer preference inside the ADR, accepting it is formally identical to self-ACCEPT. | Stop; re-ask. |
| ③ **Letter with no ADR named** — a bare `B` | Owner-typed, not agent-sourced, may even follow visible deliberation | **Names nothing.** The gate's whole purpose is attribution; one character attributes nothing. | Stop; re-ask. |
| ④ **Plain `A` offered beside `A + rider`** | Reads as a clean, decisive pick | **Does not settle the rider** when A's own body names it as an open question. | Record as open; promote to an AC. |

**Partial invalidity does not stop everything.** When one message carries several ADRs and only some are
malformed, ACCEPT and proceed with the valid ones, then re-ask about the rest. Halting all of them is its own
error.

**Prevention — most of these round-trips are the agent's fault.** When asking an owner to decide, hand them
the **exact form as a copy-pasteable line**: `ADR-<scope>-0NN ACCEPTED — <letter>`. Case ③ arose because the
agent asked for *"just one letter — A / B / C"*: it knew the gate requires the ADR's name and then requested a
format the gate does not accept. The owner answered exactly as asked.

**Record that the gate was exercised.** Write into the ADR's `History` that it stopped and re-asked — and, if
the cause was the agent's own request format, that too. Without this, a gate that *challenged* something is
indistinguishable from a gate that was never tested.

## Riders — a plain choice is not a rejection of the rider

When an ADR's own body names a sub-question as unresolved, choosing that option **plainly** settles neither
way. Reading it in either direction is the error.

- **Rider absent from the reply** → record it as **still open** and promote it to an acceptance criterion on
  the implementing task, phrased so the owner can reverse it in one line. Silently dropping it is not an
  answer.
- **Rider actually supplied** (e.g. `ACCEPTED — A (credentials deferred to v2)`) → leave the option's body
  **byte-unchanged** and write separately, under § Decision, what the rider narrows. A rider pins a timing or
  scope the option left open; it does not re-decide the option.
- **Check for a rider by comparison, not reflex — there may not be one.** The test: *can this option be chosen
  without answering that question?* If it cannot, the question is inside the choice and there is no rider.
  **Record that the check was performed and what it found** — "none" is also an output.
- **A predicate with no wiring is not a rider; it is an implementation AC.** If the ADR fixed a rule but not
  where it runs, promote it — an unwired ratchet is a comment, not code.

## What this section is not

These are prose rules with **no automated guard**, deliberately. What they judge is the shape of a message a
human sent, which is not a thing that can be counted in the repository. Do not add a detector over ADR files
as a stand-in for it — a proxy here would report on the wrong population.

---

# Selection Guidelines

For guidance on when to use each architecture style, consult the matching skill under `.claude/skills/backend/architecture/<style>/SKILL.md` (e.g. `hexagonal/`, `layered/`, `clean/`).

---

# Prohibited Decisions

Do not choose architecture based on:

- personal preference
- familiarity only
- temporary convenience
- copying another service without spec support

---

# Change Rule

If service architecture must change:

1. update `specs/services/<service>/architecture.md`
2. record the reason in ADR if the impact is significant
3. update related task/spec documents first
4. only then implement code changes

---

# Implementation Rule

Service implementation must follow the architecture declared in the service spec, even if another architecture would also be valid in theory.