#!/usr/bin/env bash
#
# check-controller-slice-naming.sh — TASK-MONO-461
#
# Enforces platform/testing-strategy.md § Controller Slice Tests:
#   a CONTROLLER WEB-SLICE test MUST be named `*ControllerSliceTest.java`.
#
# A file is a controller web-slice IFF BOTH hold:
#   (1) name gate — the class/file name matches `*Controller*Test`, AND
#   (2) mechanism gate — the file drives the web layer through a MockMvc slice:
#       `@WebMvcTest`, `@WebFluxTest`, or `MockMvcBuilders.standaloneSetup(...)`.
# For such a file the name MUST end in `ControllerSliceTest`.
#
# Why the mechanism gate (predicate is the property, not a proxy — testing
# strategy § "the guard's predicate"): `@WebMvcTest` alone is NOT a controller
# slice — the fleet uses it for contract tests (`*ApiContractTest`), aspect /
# condition security slices, health slices, and `Test*ServiceApplication`
# configs. Those fail the NAME gate (no `Controller...Test`) and are excluded.
# Conversely a controller UNIT test (Mockito, direct `new FooController(...)`,
# no MockMvc) fails the MECHANISM gate and correctly keeps `{ClassName}Test`.
#
# § G8 — what this guard does NOT cover (known holes, stated so the next reader
# does not mistake silence for coverage):
#   - Controller slices whose name does not contain "Controller" (keyed on the
#     name, so invisible here). None known at authoring time.
#   - It does not verify the file actually is a slice beyond the mechanism
#     string match; a commented-out `@WebMvcTest` would false-positive. No such
#     case exists in-tree; accepted over a Java parser.
#
# § G1 trigger: the drift arrives as a `.java` test file (new/renamed). Gated on
# `code-changed` in ci.yml (`.java` is a code extension), so it always runs on
# the diff that can introduce it.
#
# Usage:
#   check-controller-slice-naming.sh            # scan the repo (CI)
#   check-controller-slice-naming.sh --selftest # prove the predicate bites (G3)
#
set -euo pipefail

CANON_SUFFIX='ControllerSliceTest'
MECH_RE='@WebMvcTest|@WebFluxTest|standaloneSetup'

# check_file <path> → prints ::error and returns 2 on violation,
#                     returns 1 if it is a slice-under-check (for counting),
#                     returns 0 if skipped (not a controller slice).
check_file() {
  local f="$1" b
  b="$(basename "$f" .java)"
  case "$b" in *Controller*Test) ;; *) return 0 ;; esac         # (1) name gate
  grep -Eq "$MECH_RE" "$f" || return 0                          # (2) mechanism gate
  case "$b" in *"$CANON_SUFFIX") return 1 ;; esac               # compliant
  echo "::error file=${f}::controller web-slice must be named *${CANON_SUFFIX}.java (found ${b}.java) — platform/testing-strategy.md § Controller Slice Tests / TASK-MONO-461"
  return 2
}

scan_repo() {
  local base="${1:-.}" viol=0 checked=0 rc
  while IFS= read -r f; do
    set +e; check_file "$base/$f"; rc=$?; set -e
    case "$rc" in
      2) viol=$((viol+1)); checked=$((checked+1)) ;;
      1) checked=$((checked+1)) ;;
    esac
  done < <(cd "$base" && git ls-files -- 'projects' | grep -E '/src/test/.*\.java$' || true)
  echo "check-controller-slice-naming: ${checked} controller web-slice(s) checked, ${viol} violation(s)."
  [ "$viol" -eq 0 ] || return 1
}

selftest() {
  local d rc fail=0
  d="$(mktemp -d)"
  trap 'rm -rf "$d"' RETURN
  # (a) VIOLATION — @WebMvcTest slice named *ControllerTest
  printf '@WebMvcTest(FooController.class)\nclass FooControllerTest {}\n' > "$d/FooControllerTest.java"
  # (b) VIOLATION — standaloneSetup slice named *ControllerWebMvcTest
  printf 'MockMvcBuilders.standaloneSetup(new BarController());\nclass BarControllerWebMvcTest {}\n' > "$d/BarControllerWebMvcTest.java"
  # (c) COMPLIANT — @WebMvcTest slice already *ControllerSliceTest
  printf '@WebMvcTest(BazController.class)\nclass BazControllerSliceTest {}\n' > "$d/BazControllerSliceTest.java"
  # (d) EXCLUDED — controller UNIT test (Mockito, no MockMvc)
  printf '@ExtendWith(MockitoExtension.class)\nclass QuxControllerTest {}\n' > "$d/QuxControllerTest.java"
  # (e) EXCLUDED — @WebMvcTest but NOT a controller name (contract test)
  printf '@WebMvcTest\nclass OrderApiContractTest {}\n' > "$d/OrderApiContractTest.java"

  set +e
  check_file "$d/FooControllerTest.java"          >/dev/null 2>&1; [ $? -eq 2 ] || { echo "selftest FAIL: (a) violation not caught"; fail=1; }
  check_file "$d/BarControllerWebMvcTest.java"     >/dev/null 2>&1; [ $? -eq 2 ] || { echo "selftest FAIL: (b) standaloneSetup violation not caught"; fail=1; }
  check_file "$d/BazControllerSliceTest.java"      >/dev/null 2>&1; [ $? -eq 1 ] || { echo "selftest FAIL: (c) compliant slice mis-flagged"; fail=1; }
  check_file "$d/QuxControllerTest.java"           >/dev/null 2>&1; [ $? -eq 0 ] || { echo "selftest FAIL: (d) unit test not excluded"; fail=1; }
  check_file "$d/OrderApiContractTest.java"        >/dev/null 2>&1; [ $? -eq 0 ] || { echo "selftest FAIL: (e) contract test not excluded"; fail=1; }
  set -e

  if [ "$fail" -eq 0 ]; then
    echo "check-controller-slice-naming --selftest: OK (violation bites; compliant/unit/contract excluded)."
  else
    echo "check-controller-slice-naming --selftest: FAILED"; return 1
  fi
}

if [ "${1:-}" = "--selftest" ]; then
  selftest
else
  scan_repo "$(git rev-parse --show-toplevel)"
fi
