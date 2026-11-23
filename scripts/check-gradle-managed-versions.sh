#!/usr/bin/env bash
# =============================================================================
# check-gradle-managed-versions.sh — TASK-MONO-829
# =============================================================================
# Fails when a third-party coordinate whose version lives in `gradle.properties`
# is written with a literal version in a build file again.
#
# WHY THIS EXISTS
# ---------------
# Before TASK-MONO-829 the versions below were string literals repeated across
# dozens of build files. Bumping one meant editing every copy, and the copies
# that were missed are exactly the drift TASK-MONO-828 had to clean up
# (`nimbus-jose-jwt` at three versions, `wiremock` at two plus a retired
# coordinate). 829 moved each family to ONE property. Without this check the
# next module copied from an old example reintroduces a literal, and the drift
# starts again with nothing to notice it.
#
# RULES
# -----
#   R1  No build file (`git ls-files '*.gradle'`) declares a managed coordinate
#       with a literal version — it must reference the property.
#   R2  Every managed property is defined in `gradle.properties`.
#   R3  Every managed property is referenced by at least one build file
#       (an unreferenced property is a stale second copy of a version).
#
# NON-VACUITY
# -----------
# A path typo or a renamed file would shrink the population to nothing and every
# rule would "agree". So the build-file population and the number of property
# references each have a floor, set well under the measured counts (2026-11-24:
# 82 build files, 366 references) so that deleting a module does not turn this
# red but losing the population does.
#
# WHAT THIS DOES NOT COVER
# ------------------------
# - Coordinates outside the table below. Adding a family is a deliberate edit to
#   MANAGED and to `gradle.properties` together.
# - Versions resolved from a BOM (`spring-boot-dependencies`) — those have no
#   literal to drift.
# - Whether the property values are the right versions. That is a dependency
#   upgrade decision, not drift.
#
# The population is `git ls-files`, i.e. the INDEX. Stage before running this
# locally (CLAUDE.md § Task Rules, "Stage before you run a repo guard").
#
# Usage:
#   bash scripts/check-gradle-managed-versions.sh              # check the tree
#   bash scripts/check-gradle-managed-versions.sh --self-test  # prove each rule bites
# =============================================================================
set -uo pipefail

SELF="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
ROOT="${GMV_GUARD_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

MIN_BUILD_FILES=40
MIN_REFERENCES=150

# property|coordinate regex (group:artifact, ERE). One line per family.
MANAGED=(
  'testcontainersVersion|org\.testcontainers:[a-z0-9-]+'
  'testcontainersRedisVersion|com\.redis:testcontainers-redis'
  'okhttpVersion|com\.squareup\.okhttp3:(okhttp|mockwebserver)'
  'awaitilityVersion|org\.awaitility:awaitility'
  'logstashLogbackEncoderVersion|net\.logstash\.logback:logstash-logback-encoder'
  'nimbusJoseJwtVersion|com\.nimbusds:nimbus-jose-jwt'
  'resilience4jVersion|io\.github\.resilience4j:[a-z0-9-]+'
  'springdocVersion|org\.springdoc:[a-z0-9-]+'
  'lombokVersion|org\.projectlombok:lombok'
  'wiremockVersion|org\.wiremock:wiremock-standalone'
  'shedlockVersion|net\.javacrumbs\.shedlock:[a-z0-9-]+'
  'jjwtVersion|io\.jsonwebtoken:[a-z0-9-]+'
  'kafkaClientsVersion|org\.apache\.kafka:kafka-clients'
)

main() {
  local fail=0 entry prop coord files=() f hits refs total_refs=0

  while IFS= read -r f; do
    [ -n "$f" ] && files+=("$f")
  done < <(git -C "$ROOT" ls-files '*.gradle')

  echo "[gradle-managed-versions] build files=${#files[@]} (floor $MIN_BUILD_FILES)  root=$ROOT"
  if [ "${#files[@]}" -lt "$MIN_BUILD_FILES" ]; then
    echo "  x population: ${#files[@]} build files is under the floor $MIN_BUILD_FILES — the population was lost, not cleaned."
    return 1
  fi

  local props="$ROOT/gradle.properties"
  if [ ! -f "$props" ]; then
    echo "  x gradle.properties not found at $props"
    return 1
  fi

  for entry in "${MANAGED[@]}"; do
    prop="${entry%%|*}"
    coord="${entry#*|}"

    # R1 — literal version on a managed coordinate.
    hits="$(cd "$ROOT" && grep -nE "['\"]${coord}:[0-9][0-9A-Za-z.-]*['\"]" "${files[@]}" 2>/dev/null)"
    if [ -n "$hits" ]; then
      echo "  x R1 literal version for ${coord} — reference \${${prop}} instead:"
      printf '      %s\n' "$hits"
      fail=1
    fi

    # R2 — property defined.
    if ! grep -qE "^${prop}=[^[:space:]]+" "$props"; then
      echo "  x R2 ${prop} is not defined in gradle.properties"
      fail=1
    fi

    # R3 — property referenced.
    refs="$(cd "$ROOT" && grep -oF "\${${prop}}" "${files[@]}" 2>/dev/null | wc -l | tr -d ' ')"
    if [ "$refs" -eq 0 ]; then
      echo "  x R3 ${prop} is defined but no build file references it"
      fail=1
    fi
    total_refs=$((total_refs + refs))
  done

  echo "[gradle-managed-versions] property references=${total_refs} (floor $MIN_REFERENCES)"
  if [ "$total_refs" -lt "$MIN_REFERENCES" ]; then
    echo "  x population: ${total_refs} references is under the floor $MIN_REFERENCES"
    fail=1
  fi

  if [ "$fail" -eq 0 ]; then
    echo "[gradle-managed-versions] ok — ${#MANAGED[@]} families, every one a single property"
  fi
  return "$fail"
}

# =============================================================================
# --self-test — synthetic tree; each rule must bite, and the clean copy must pass
# =============================================================================
self_test() {
  local rc=0 i

  _mk() {
    local d e p c; d="$(mktemp -d)"
    mkdir -p "$d/scripts"
    cp "$SELF" "$d/scripts/"
    : > "$d/gradle.properties"
    for e in "${MANAGED[@]}"; do
      p="${e%%|*}"
      echo "${p}=1.0.0" >> "$d/gradle.properties"
    done
    # Enough build files and references to clear both floors.
    for i in $(seq 1 "$MIN_BUILD_FILES"); do
      mkdir -p "$d/m$i"
      {
        echo "dependencies {"
        for e in "${MANAGED[@]}"; do
          p="${e%%|*}"
          echo "    implementation \"x.example:lib:\${${p}}\""
        done
        echo "}"
      } > "$d/m$i/build.gradle"
    done
    git -C "$d" init -q
    git -C "$d" config user.email t@l; git -C "$d" config user.name t
    git -C "$d" add -A >/dev/null; git -C "$d" commit -qm base
    echo "$d"
  }
  _run() { GMV_GUARD_ROOT="$1" bash "$SELF" >/dev/null 2>&1; echo $?; }
  _expect() {
    local what="$1" want="$2" got="$3"
    if [ "$got" = "$want" ]; then echo "  ok: $what (rc=$got)"
    else echo "  x  $what — rc=$got, expected $want"; rc=1; fi
  }

  echo "[gradle-managed-versions] --self-test"
  local t

  t="$(_mk)"; _expect "clean synthetic tree passes" 0 "$(_run "$t")"; rm -rf "$t"

  # R1 — a literal version comes back.
  t="$(_mk)"
  printf "    testImplementation 'org.awaitility:awaitility:4.2.2'\n" >> "$t/m1/build.gradle"
  if ! grep -qF "awaitility:4.2.2" "$t/m1/build.gradle"; then
    echo "  x R1 injection failed — this cell tested nothing"; rc=1
  else
    git -C "$t" commit -qam mutate
    _expect "R1 literal managed version bites" 1 "$(_run "$t")"
  fi
  rm -rf "$t"

  # R1 control — the property form is not mistaken for a literal.
  t="$(_mk)"
  printf '    testImplementation "org.awaitility:awaitility:${awaitilityVersion}"\n' >> "$t/m1/build.gradle"
  git -C "$t" commit -qam mutate
  _expect "R1 control: property reference does not bite" 0 "$(_run "$t")"
  rm -rf "$t"

  # R2 — property removed from gradle.properties.
  t="$(_mk)"
  sed -i '/^jjwtVersion=/d' "$t/gradle.properties"
  if grep -q '^jjwtVersion=' "$t/gradle.properties"; then
    echo "  x R2 injection failed — this cell tested nothing"; rc=1
  else
    git -C "$t" commit -qam mutate
    _expect "R2 undefined property bites" 1 "$(_run "$t")"
  fi
  rm -rf "$t"

  # R3 — property no longer referenced anywhere.
  t="$(_mk)"
  for i in $(seq 1 "$MIN_BUILD_FILES"); do sed -i '/kafkaClientsVersion/d' "$t/m$i/build.gradle"; done
  git -C "$t" commit -qam mutate
  _expect "R3 unreferenced property bites" 1 "$(_run "$t")"
  rm -rf "$t"

  # Floor — the population disappears.
  t="$(_mk)"
  git -C "$t" rm -q -r -- 'm*' >/dev/null 2>&1; git -C "$t" commit -qm mutate >/dev/null 2>&1
  if [ "$(git -C "$t" ls-files '*.gradle' | wc -l)" -ne 0 ]; then
    echo "  x floor injection failed — build files still tracked, this cell tested nothing"; rc=1
  else
    _expect "floor: lost population bites" 1 "$(_run "$t")"
  fi
  rm -rf "$t"

  [ "$rc" -eq 0 ] && echo "[gradle-managed-versions] --self-test ok" || echo "[gradle-managed-versions] --self-test FAILED"
  return "$rc"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test; exit $?
fi
main; exit $?
