# ADR-MONO-050 — REACHABILITY PROBE, DO NOT MERGE (TASK-MONO-363 AC-5)

**Status:** PROPOSED

**Date:** 2026-07-12

**Decision driver:** This file is not an ADR. It exists to reproduce, on real CI,
the exact diff shape by which ADR-index drift arrives: a new `docs/adr/ADR-MONO-*.md`
with no row added to `docs/adr/INDEX.md`, and nothing else in the diff — markdown only.

The `adr-index-drift` job must FAIL on this PR. If it is SKIPPED instead, the guard
is unreachable on the change it exists to police, and a skipped job reports green.

**Supersedes:** none.

**Related:** `TASK-MONO-363`, `TASK-MONO-360` (which established this measurement
procedure), `TASK-MONO-359` (guards must not only bite — they must get the chance to).

This PR is opened to be observed and closed, never merged.
