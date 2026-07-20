# TASK-BE-545 — Correct the disproven "UserWithdrawn consume-side DLQ outage" claim that landed on main via TASK-BE-533

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (a correctness/record correction whose whole point is not to inherit the disproven claim — measure, don't assume)

> **This replaces the original TASK-BE-545, which was itself built on the false premise.** The
> original ticket ("audit whether real users are stuck withdrawn-but-not-cancelled from the
> `UserWithdrawnEvent` deserialization bug") told an operator to go audit a DLQ backlog that, on
> measurement, was never produced. Auditing a non-existent backlog is the harm; this ticket removes
> the claim instead of acting on it.

---

## Goal

`TASK-BE-533` (PR #2798, merged `9ba5a0534`) landed — alongside its real deliverables (notification
metrics, restored alerts, runbooks) — a commit adding `@JsonIgnoreProperties(ignoreUnknown = true)`
to `order-service`'s `UserWithdrawnEvent`, justified by this claim (in the record's Javadoc, the
`tasks/INDEX.md` note, and a new follow-up ticket):

> "The real envelope always carries `tenant_id`. Without `@JsonIgnoreProperties`, every real message
> threw `UnrecognizedPropertyException` and was routed straight to `user.user.withdrawn.dlq` — every
> withdrawal silently failed to cancel the user's active orders."

**That claim is false, and was measured false.** The evidence for it was a unit test using a bare
`new ObjectMapper()`, which enables `FAIL_ON_UNKNOWN_PROPERTIES`. The consumer
(`UserWithdrawnEventConsumer`) is injected with **Spring Boot's auto-configured `ObjectMapper`**,
on which that feature is **disabled by default**. Measured against the running
`OrderServiceApplication` context (Testcontainers boot, the actual injected bean):

```
FAIL_ON_UNKNOWN_PROPERTIES = false
REAL CONTEXT MAPPER: ACCEPTED the tenant_id envelope
```

So the real envelope always deserialised, with or without the annotation. No real `UserWithdrawn`
message was rejected; nothing was routed to `user.user.withdrawn.dlq`. This is a textbook
fixture-does-not-match-production case ([[env_test_fixture_impossible_input_proves_nothing]]): a
green/red obtained under a mapper the production path never uses.

The `@JsonIgnoreProperties` annotation itself is worth keeping — it makes `UserWithdrawnEvent`
uniform with its siblings (`PaymentRefundedEvent`, `AccountDeletedEvent`) and hardens it against a
future global `spring.jackson.deserialization.fail-on-unknown-properties=true`. Only the
**justification** was wrong. This ticket keeps the annotation and corrects the record.

## Scope

**In scope:**

1. **`UserWithdrawnEvent` Javadoc** — replace the "production DLQ outage" narrative with the true
   one: consistency + defense-in-depth, plus an explicit note that under the shipped Spring mapper
   the envelope was always accepted (correcting, by name, the TASK-BE-533 claim). *(Done in this
   ticket's implementation commit.)*
2. **The regression test** (`UserWithdrawnEventDeserializationTest`) — the test that asserted the
   annotation "fixes DLQ routing" used `new ObjectMapper()` and proved nothing about production.
   Replace it with two tests that tell the truth: (a) a production-like mapper (feature off) accepts
   the envelope *regardless* of the annotation — the evidence the outage claim is false; (b) a
   strict mapper (feature on) accepts it *only because of* the annotation — the guard's real
   predicate, mutation-verified to fail iff the annotation is removed.
3. **`tasks/INDEX.md`** — correct the TASK-BE-533 done-note's consume-side-outage sentence, and
   land this ticket's row in place of the audit one.

**Out of scope:**

- Reverting the `@JsonIgnoreProperties` annotation. It is correct on its own merits; removing it
  would be its own unjustified change.
- Re-opening or amending anything in TASK-BE-533's genuine deliverables (metrics, alerts, runbooks)
  — they were verified independently and are unaffected by this correction.
- A real DLQ/backlog audit. There is no backlog to audit — that was the disproven premise. If a
  future, *separately evidenced* consume-side failure is found, it gets its own ticket with its own
  measurement.

## Acceptance Criteria

- **AC-0 (gate — re-measure; do not inherit either story)** — Independently re-confirm at start,
  against the real injected bean (not a hand-rolled `ObjectMapper`), that the order-service
  application-context `ObjectMapper` has `FAIL_ON_UNKNOWN_PROPERTIES` disabled and accepts the full
  `tenant_id` envelope without the annotation. If — and only if — this comes back the other way (the
  context mapper is strict, or a Kafka `JsonDeserializer` with strict handling sits in the consume
  path), then the original claim was *right* and this ticket stops and reports that instead. The
  code wins over both notes.
- **AC-1** — `UserWithdrawnEvent`'s Javadoc no longer asserts a production DLQ outage; it states the
  consistency/defense-in-depth rationale and explicitly corrects the TASK-BE-533 claim with the
  measured fact.
- **AC-2** — The regression test asserts the two true properties above. The annotation-removed
  mutation turns the **strict-mapper** test RED and leaves the **default-mapper** test GREEN
  (predicate matches what the annotation actually does). State the mutation result in the PR body.
- **AC-3** — `tasks/INDEX.md`'s TASK-BE-533 note no longer contains the false consume-side-outage
  sentence.
- **AC-4** — order-service tests build and are GREEN; the correction introduces no behavior change
  (the annotation stays; only the story and the test's mapper change).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/contracts/events/user-events.md` (§ Event
  Envelope — `tenant_id` always present; the additive-field forward-compat rule the annotation honours)

## Related Contracts

- `UserWithdrawn` (`user.user.withdrawn`) — no contract change. This ticket corrects a claim about
  how one consumer deserialises the existing contract; the wire format is untouched.

## Edge Cases

- **The annotation genuinely does something — under a strict mapper.** Do not over-correct into
  "the annotation is pointless." It is a real (if latent) guard; the correction is that it guards a
  *hypothetical global config*, not a *current outage*. The strict-mapper test pins exactly that.
- **A Kafka `JsonDeserializer` in the consume path could change the answer.** The consumer takes a
  `@Payload String` and calls `objectMapper.readValue` itself, so the injected bean is the only
  mapper in play — but AC-0 must confirm this rather than assume it, because a value
  `JsonDeserializer` with `FAIL_ON_UNKNOWN` set would be a different story.

## Failure Scenarios

- **F1 — "correcting" by inheriting the opposite claim without re-measuring.** This ticket's own
  premise (the outage is false) is a hypothesis until AC-0 re-confirms it against the real bean.
  Same discipline the original ticket failed to apply. [[feedback_recount_population_dont_inherit_scope]]
- **F2 — leaving the false sentence somewhere it wasn't grepped.** The claim landed in three places
  (record Javadoc, INDEX note, the original ticket body). Grep for `user.user.withdrawn.dlq` /
  "every real message" / "consume side" across the project before closing, so no copy survives.
  [[feedback_deletion_leaves_survivors_grep_the_consumers]]
- **F3 — a green test that still proves nothing.** A regression test that passes with and without
  the annotation (as the original did under the default mapper) is not a guard. AC-2's mutation
  requirement is what makes the predicate real. [[feedback_guard_predicate_wrong_verify_the_artifact]]
