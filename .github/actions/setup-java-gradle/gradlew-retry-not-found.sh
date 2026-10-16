#!/usr/bin/env bash
# TASK-MONO-797. Run `./gradlew "$@"`; if — and only if — it failed because Gradle
# reported a dependency as NOT FOUND, say so out loud and run it once more with
# `--refresh-dependencies`.
#
# Why this exists (measured, not guessed — runs 38863691082 / 38883805350 / 38890453134):
#   1. At 06:53Z on 2026-10-16 Maven Central answered "not found" to a live request for
#      resilience4j-annotations-2.2.0.jar (stack frame:
#      ExternalResourceResolver$RemoteRepositoryAccess.resolveArtifact). The jar is there;
#      the answer was transient.
#   2. Gradle remembers a not-found answer in ~/.gradle/caches/modules-2/metadata-*
#      and does not ask again while that record is fresh.
#   3. gradle/actions/setup-gradle saves ~/.gradle/caches even when the build FAILED
#      (only cache-cleanup is skipped), and every caller of _platform-e2e.yml shares one
#      gradle-home key prefix (`e2e[<hash>]-<sha>` — the reusable job is always `e2e`).
#   4. So every later run restored the record and failed WITHOUT asking the repository
#      (stack frame: CachingModuleComponentRepository$LocateInCacheRepositoryAccess
#      .resolveArtifactFromCache), and re-saved it for the run after. ci.yml's lanes have
#      a different key hash, never restored the record, and stayed green — which is why
#      "same commit, ci.yml green, jar returns 200" all held at once.
#
# `--refresh-dependencies` makes Gradle re-ask the repository instead of trusting the
# record, and a success overwrites it in THIS job's Gradle home. It does not guarantee the
# next run restores a clean entry: the lanes share one key and the first to save wins, so
# a sibling that never resolves the artifact (wms) can save the record again. That case
# costs one refresh per affected lane and a visible ::warning:: per run, not a red lane.
# (On non-default branches setup-gradle is cache-read-only and saves nothing.)
#
# What this deliberately does NOT do:
#   - retry any other failure. Compile errors and test failures exit with their own code
#     after one run: a retry there would double the lane and could turn a flaky test green.
#   - hide the event. Every retry emits a ::warning:: (recovered) or ::error:: (still not
#     found) annotation plus a step-summary line naming which of the two origins it was.
#
# The predicate keys on Gradle's resolution exception CLASS names, which only appear with
# --stacktrace; the plain "Could not find" text also matches unrelated script errors
# ("Could not find method …"). So --stacktrace is required, and enforced below rather
# than assumed.
set -uo pipefail

case " $* " in
  *" --stacktrace "*) ;;
  *)
    echo "::error title=gradlew-retry-not-found::called without --stacktrace — the not-found predicate cannot see Gradle's exception class without it. Add --stacktrace to the caller."
    exit 2
    ;;
esac

log="$(mktemp "${RUNNER_TEMP:-/tmp}/gradlew-XXXXXX.log")"

./gradlew "$@" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
[ "$rc" -eq 0 ] && exit 0

if ! grep -qE 'org\.gradle\.internal\.resolve\.(ArtifactNotFoundException|ModuleVersionNotFoundException)' "$log"; then
  exit "$rc"
fi

if grep -q 'LocateInCacheRepositoryAccess' "$log"; then
  origin="a not-found record restored from the Actions Gradle cache (Gradle did not ask the repository)"
else
  origin="a live not-found answer from the repository"
fi
what="$(grep -oE 'Could not find [^ ]+' "$log" | sort -u | head -5 | sed -e 's/^Could not find //' -e 's/\.$//' | paste -sd ' ' -)"

echo "::warning title=Gradle dependency not found — retrying once with --refresh-dependencies::${what:-<unparsed>} — from ${origin}. See TASK-MONO-797."
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Gradle not-found retry (TASK-MONO-797)"
    echo "- not found: \`${what:-<unparsed>}\`"
    echo "- origin: ${origin}"
    echo "- first attempt rc=${rc}; retrying with \`--refresh-dependencies\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi

./gradlew "$@" --refresh-dependencies
rc2=$?
if [ "$rc2" -eq 0 ]; then
  echo "::warning title=Gradle not-found retry recovered::the refreshed resolution succeeded and this job's Gradle home no longer carries the not-found record. Whether the NEXT run restores a clean entry depends on which lane saves the shared gradle-home key first (a sibling lane that never needed the artifact still carries the record) — a repeat of this warning on the next run means exactly that, not a new outage."
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "- retry: **recovered** (rc=0)" >> "$GITHUB_STEP_SUMMARY"
else
  echo "::error title=Gradle dependency still not found after --refresh-dependencies::${what:-<unparsed>} — the repository says so now too: a wrong coordinate or a repository outage, not a stale cache record."
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && echo "- retry: **still failing** (rc=${rc2})" >> "$GITHUB_STEP_SUMMARY"
fi
exit "$rc2"
