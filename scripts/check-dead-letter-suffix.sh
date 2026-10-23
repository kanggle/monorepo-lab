#!/usr/bin/env bash
#
# check-dead-letter-suffix.sh — TASK-MONO-813 (ADR-MONO-085 D5)
#
# Fails when the fleet's dead-letter topic name stops being one name.
#
# ADR-MONO-085 (ACCEPTED — A) fixed the dead-letter topic of every source topic
# as `<topic>.DLT`, written out explicitly at every site. Before it, the name
# was split three ways and nothing noticed:
#
#   rule + skills          `.dlq`
#   ecommerce · iam · fan  `.dlq`   (13 recoverers, 50 listeners)
#   wms · erp · scm · fin  `.DLT`   (30 sites, 65 listeners)
#   pre-created topics     `.dlq`   (wms · iam — wms's were orphans: its
#                                    consumers sent to `.DLT`, which did not
#                                    exist until the broker auto-created it)
#
# That is how a wms consumer came to dead-letter onto a topic nobody had
# provisioned (33rd demo window). Changing the text of the rule alone would have
# left the split in the code (TASK-MONO-813 Failure Scenario 1), so this guard
# reads the four places the name has to agree:
#
#   R1  sender literals   `+ ".xxx"` after a topic in projects/*/apps/*/src/main
#                         Java — the custom destination resolvers.
#   R2  @RetryableTopic   every annotation declares `dltTopicSuffix`, and its
#                         value is `.DLT`. 🔴 Spring's default for
#                         @RetryableTopic is `-dlt`, NOT `.DLT` (the `.DLT`
#                         default belongs to DeadLetterPublishingRecoverer), so an
#                         annotation that omits the attribute silently invents a
#                         third name. That is why R2 checks presence, not only
#                         value.
#   R3  suffix settings   `dlt-suffix:` in application*.yml and the
#                         `${…dlt-suffix:X}` default in @Value — wms configures
#                         its resolvers this way.
#   R4  provisioning      every dead-letter topic a compose file or script
#                         creates (`"$t.xxx"`, `"$$t.xxx"`, `"${topic}.xxx"`).
#   R5  the rule itself   the three platform files state `.DLT` and no `.dlq`.
#   R6  partition         a resolver must not route the dead letter to the
#                         SOURCE partition number (ADR-MONO-085 D3): pre-created
#                         dead-letter topics have 1 partition while sources have
#                         3, so `record.partition()` aims records from partitions
#                         1 and 2 at partitions that do not exist. A file that
#                         overrides `createProducerRecord` (wms) drops the hint
#                         before sending and is exempt.
#
# The population is `git ls-files`, i.e. the INDEX. Stage before running this
# locally — an unstaged new consumer does not exist as far as this script is
# concerned (CLAUDE.md § Task Rules, "Stage before you run a repo guard").
#
# NON-VACUITY. A rename of the package layout, or a path filter typo, would
# shrink every population to zero and every rule would "agree". Each population
# has a floor; falling under it is a failure, not a pass. The floors are well
# under today's counts (measured 2026-10-24: 19 resolver sites, 24 annotations,
# 4 provisioning files) so that deleting a consumer does not turn this red, but
# losing a whole population does.
#
# Usage:
#   bash scripts/check-dead-letter-suffix.sh              # check the tree
#   bash scripts/check-dead-letter-suffix.sh --self-test  # prove each rule bites
#
# Exit 0 = one name everywhere. Exit 1 = a site disagrees (each one printed).

set -uo pipefail

EXPECTED='.DLT'
MIN_RESOLVERS=10
MIN_RETRYABLE=10
MIN_PROVISIONING=2

# Every rule reads its input as "path<TAB>content" on stdin and prints one
# violation per line, so --self-test can feed it fixtures instead of the tree.

# R1 — `+ ".xxx"` where xxx is any dead-letter-looking suffix.
rule_sender_literal() {
  awk -F'\t' -v want="$EXPECTED" '
    {
      line = $0; sub(/^[^\t]*\t/, "", line)
      if (line ~ /^[ \t]*(\*|\/\/)/) next
      s = line
      while (match(s, /\+[ \t]*"[.-](dlq|DLQ|dlt|DLT)"/)) {
        lit = substr(s, RSTART, RLENGTH); sub(/^\+[ \t]*"/, "", lit); sub(/"$/, "", lit)
        if (lit != want) printf "R1  %s  resolver appends \"%s\" (want \"%s\")\n", $1, lit, want
        s = substr(s, RSTART + RLENGTH)
      }
    }'
}

# R2 — @RetryableTopic blocks. The block runs from the annotation line to the
# first line whose parentheses balance back to zero.
rule_retryable() {
  awk -F'\t' -v want="$EXPECTED" '
    function flush() {
      if (!open) return
      if (suffix == "") printf "R2  %s  @RetryableTopic has no dltTopicSuffix (Spring default is \"-dlt\", want \"%s\")\n", where, want
      else if (suffix != want) printf "R2  %s  dltTopicSuffix = \"%s\" (want \"%s\")\n", where, suffix, want
      open = 0
    }
    {
      path = $1; line = $0; sub(/^[^\t]*\t/, "", line)
      if (!open) {
        if (line ~ /^[ \t]*@RetryableTopic([ \t]*\(|[ \t]*$)/) {
          open = 1; depth = 0; suffix = ""; where = path
        } else next
      }
      if (match(line, /dltTopicSuffix[ \t]*=[ \t]*"[^"]*"/)) {
        v = substr(line, RSTART, RLENGTH); sub(/^[^"]*"/, "", v); sub(/"$/, "", v); suffix = v
      }
      n = split(line, ch, "")
      for (i = 1; i <= n; i++) { if (ch[i] == "(") depth++; else if (ch[i] == ")") depth-- }
      if (depth <= 0 && line ~ /\)/) flush()
      else if (depth <= 0 && line !~ /@RetryableTopic/) flush()
    }
    END { flush() }'
}

# R3 — `dlt-suffix: X` in yml, and `dlt-suffix:X}` as an @Value default.
rule_suffix_setting() {
  awk -F'\t' -v want="$EXPECTED" '
    {
      line = $0; sub(/^[^\t]*\t/, "", line)
      if (line ~ /^[ \t]*#/) next
      if (match(line, /dlt-suffix:[ \t]*[^ \t}"]+/)) {
        v = substr(line, RSTART, RLENGTH); sub(/^dlt-suffix:[ \t]*/, "", v)
        sub(/^\$\{[A-Z0-9_]+:/, "", v)
        if (v != want) printf "R3  %s  dlt-suffix = \"%s\" (want \"%s\")\n", $1, v, want
      }
    }'
}

# R4 — dead-letter topics created by provisioning scripts.
rule_provisioning() {
  awk -F'\t' -v want="$EXPECTED" '
    {
      line = $0; sub(/^[^\t]*\t/, "", line)
      if (line ~ /^[ \t]*#/) next
      s = line
      while (match(s, /\$\$?\{?[A-Za-z_]+\}?[.-](dlq|DLQ|dlt|DLT)"/)) {
        lit = substr(s, RSTART, RLENGTH); sub(/^\$\$?\{?[A-Za-z_]+\}?/, "", lit); sub(/"$/, "", lit)
        if (lit != want) printf "R4  %s  creates \"<topic>%s\" (want \"<topic>%s\")\n", $1, lit, want
        s = substr(s, RSTART + RLENGTH)
      }
    }'
}

# R6 — a TopicPartition built from the source partition, in a file that does
# not strip the partition before sending.
rule_partition() {
  awk -F'\t' '
    {
      path = $1; line = $0; sub(/^[^\t]*\t/, "", line)
      if (line ~ /createProducerRecord/) exempt[path] = 1
      if (line ~ /^[ \t]*(\*|\/\/)/) { prev[path] = ""; next }
      joined = prev[path] " " line
      if (joined ~ /new[ \t]+([a-z.]+\.)?TopicPartition\(/ && joined ~ /(\.DLT|\.dlq|dlqTopic|dltSuffix)/ && line ~ /record\.partition\(\)/) hit[path] = hit[path] ? hit[path] : FNR
      prev[path] = line
    }
    END { for (p in hit) if (!(p in exempt)) printf "R6  %s  dead letter routed to the source partition number (ADR-MONO-085 D3: pass -1)\n", p }'
}

# Feed "path<TAB>line" for each file in the population.
emit() {
  local f
  while IFS= read -r f; do
    [ -f "$f" ] || continue
    awk -v p="$f" '{ sub(/\r$/, ""); print p "\t" $0 }' "$f"
  done
}

self_test() {
  local fails=0 out
  expect() { # <name> <want-substring-or-empty> <rule> <fixture>
    out="$(printf '%s\n' "$4" | "$3")"
    if [ -z "$2" ]; then
      [ -z "$out" ] && echo "  ok    $1" || { echo "  FAIL  $1 — expected silence, got: $out"; fails=$((fails+1)); }
    else
      case "$out" in *"$2"*) echo "  ok    $1" ;; *) echo "  FAIL  $1 — expected '$2', got: '${out:-<nothing>}'"; fails=$((fails+1)) ;; esac
    fi
  }
  T=$'\t'
  echo "self-test:"
  expect "R1 bites .dlq"            'appends ".dlq"' rule_sender_literal "a.java${T}  return new TopicPartition(record.topic() + \".dlq\", -1);"
  expect "R1 passes .DLT"           ''               rule_sender_literal "a.java${T}  return new TopicPartition(record.topic() + \".DLT\", -1);"
  expect "R1 skips comments"        ''               rule_sender_literal "a.java${T} * record.topic() + \".dlq\" was the old name"
  expect "R2 bites missing suffix"  'no dltTopicSuffix' rule_retryable "$(printf 'a.java\t    @RetryableTopic(\na.java\t        attempts = "3",\na.java\t        backoff = @Backoff(delay = 1000))\na.java\t    @KafkaListener(topics = "t")')"
  expect "R2 bites wrong suffix"    'dltTopicSuffix = "-dlt"' rule_retryable "$(printf 'a.java\t    @RetryableTopic(\na.java\t        dltTopicSuffix = "-dlt",\na.java\t        attempts = "3")')"
  expect "R2 passes .DLT"           ''               rule_retryable "$(printf 'a.java\t    @RetryableTopic(\na.java\t        attempts = "3",\na.java\t        backoff = @Backoff(delay = 1000, multiplier = 2.0),\na.java\t        dltTopicSuffix = ".DLT",\na.java\t        autoCreateTopics = "false")\na.java\t    @KafkaListener(topics = "t")')"
  expect "R2 ignores javadoc"       ''               rule_retryable "a.java${T} * {@code @RetryableTopic} 3-retry + DLT"
  expect "R3 bites yml"             'dlt-suffix = ".dlq"' rule_suffix_setting "application.yml${T}    dlt-suffix: .dlq"
  expect "R3 bites @Value default"  'dlt-suffix = ".dlq"' rule_suffix_setting "a.java${T}  @Value(\"\${inbound.kafka.dlt-suffix:.dlq}\") String s"
  expect "R3 passes env default"    ''               rule_suffix_setting "application.yml${T}    dlt-suffix: \${ADMIN_PROJECTION_DLT_SUFFIX:.DLT}"
  expect "R4 bites compose \$\$t"   'creates "<topic>.dlq"' rule_provisioning "docker-compose.yml${T}          create_topic \"\$\$t.dlq\" \$\$DLQ_PARTITIONS"
  expect "R4 bites \${topic}"       'creates "<topic>.dlq"' rule_provisioning "x.sh${T}  create_topic \"\${topic}.dlq\" 1"
  expect "R4 passes .DLT"           ''               rule_provisioning "x.sh${T}  create_topic \"\${topic}.DLT\" 1"
  expect "R6 bites source partition" 'source partition' rule_partition "$(printf 'a.java\t    return new TopicPartition(record.topic() + ".DLT",\na.java\t            record.partition());')"
  expect "R6 passes -1"             ''               rule_partition "a.java${T}    return new TopicPartition(record.topic() + \".DLT\", -1);"
  expect "R6 exempts createProducerRecord" ''        rule_partition "$(printf 'a.java\t  (r, e) -> new TopicPartition(r.topic() + dltSuffix, record.partition() < 0 ? 0 : record.partition())\na.java\t  protected ProducerRecord<Object, Object> createProducerRecord(')"
  if [ "$fails" -gt 0 ]; then echo "self-test: $fails failed"; return 1; fi
  echo "self-test: all passed"
}

if [ "${1:-}" = "--self-test" ]; then self_test; exit $?; fi

cd "$(git rev-parse --show-toplevel)" || exit 1

java_main="$(git ls-files ':(glob)projects/*/apps/*/src/main/**/*.java')"
yml_main="$(git ls-files ':(glob)projects/*/apps/*/src/main/**/application*.yml' ':(glob)projects/*/apps/*/src/main/**/application*.yaml' ':(glob)projects/*/apps/*/src/main/**/application*.properties')"
provisioning="$(git ls-files ':(glob)projects/*/docker-compose*.yml' ':(glob)projects/*/scripts/*.sh' | xargs -r grep -l -E 'kafka-topics(\.sh)?|create_topic' 2>/dev/null)"

violations=""
add() { [ -n "$1" ] && violations="${violations}${1}"$'\n'; }

resolver_files="$(printf '%s\n' "$java_main" | xargs -r grep -l 'new DeadLetterPublishingRecoverer' 2>/dev/null)"
retryable_files="$(printf '%s\n' "$java_main" | xargs -r grep -l -E '^[[:space:]]*@RetryableTopic' 2>/dev/null)"
n_resolvers="$(printf '%s\n' "$resolver_files" | grep -c . || true)"
n_retryable="$(printf '%s\n' "$retryable_files" | xargs -r grep -h -c -E '^[[:space:]]*@RetryableTopic' 2>/dev/null | awk '{s+=$1} END{print s+0}')"
n_provisioning="$(printf '%s\n' "$provisioning" | grep -c . || true)"

add "$(printf '%s\n' "$resolver_files" | emit | rule_sender_literal)"
add "$(printf '%s\n' "$resolver_files" | emit | rule_partition)"
add "$(printf '%s\n' "$retryable_files" | emit | rule_retryable)"
add "$( { printf '%s\n' "$yml_main"; printf '%s\n' "$resolver_files"; } | emit | rule_suffix_setting)"
add "$(printf '%s\n' "$provisioning" | emit | rule_provisioning)"

# R5 — the rule text.
for f in platform/event-driven-policy.md platform/service-types/event-consumer.md platform/glossary.md; do
  if [ ! -f "$f" ]; then add "R5  $f  missing — this guard reads the dead-letter rule from it"; continue; fi
  grep -q -F "${EXPECTED}" "$f" || add "R5  $f  does not state the dead-letter name \"<topic>${EXPECTED}\""
  hits="$(grep -n -E '\.dlq\b' "$f" || true)"
  [ -n "$hits" ] && add "$(printf '%s\n' "$hits" | sed "s#^#R5  $f:#; s#\$#  (still names .dlq)#")"
done

floor_fail=""
[ "$n_resolvers" -lt "$MIN_RESOLVERS" ] && floor_fail="${floor_fail}  resolver files: ${n_resolvers} < ${MIN_RESOLVERS}"$'\n'
[ "$n_retryable" -lt "$MIN_RETRYABLE" ] && floor_fail="${floor_fail}  @RetryableTopic annotations: ${n_retryable} < ${MIN_RETRYABLE}"$'\n'
[ "$n_provisioning" -lt "$MIN_PROVISIONING" ] && floor_fail="${floor_fail}  provisioning files: ${n_provisioning} < ${MIN_PROVISIONING}"$'\n'

echo "check-dead-letter-suffix: read ${n_resolvers} resolver files · ${n_retryable} @RetryableTopic · ${n_provisioning} provisioning files · 3 rule files"

rc=0
if [ -n "$floor_fail" ]; then
  echo ""
  echo "❌ A population fell under its floor — this is NOT a pass. Nothing was compared:"
  printf '%s' "$floor_fail"
  echo "   Did a path move? The globs are at the top of this script."
  rc=1
fi

violations="$(printf '%s' "$violations" | grep -v '^$' | sort -u)"
if [ -n "$violations" ]; then
  echo ""
  echo "❌ The dead-letter topic is not one name. ADR-MONO-085 (ACCEPTED — A) says \"<topic>${EXPECTED}\", written out at every site:"
  printf '%s\n' "$violations" | sed 's/^/  /'
  echo ""
  echo "   Fix the site, not this script. A sender, its provisioning and the rule have to move together,"
  echo "   or a record lands on a topic nobody created (TASK-MONO-813)."
  rc=1
fi

[ "$rc" -eq 0 ] && echo "✅ one dead-letter name (\"<topic>${EXPECTED}\") across senders, provisioning and the rule."
exit "$rc"
