#!/usr/bin/env bash
#
# check-shared-lib-jpa-scan.sh — TASK-MONO-406
#
# Enforces `platform/shared-library-policy.md` § "No context-wide annotations in a
# shared @AutoConfiguration".
#
# The defect it polices, stated once:
#
#   `libs/java-messaging` shipped an @AutoConfiguration that @Imported a
#   @Configuration carrying @EntityScan + @EnableJpaRepositories. An
#   auto-configuration runs in EVERY consumer without being asked, and those
#   annotations are not scoped to the library — they reconfigure the whole
#   consuming application:
#
#     * an explicit @EnableJpaRepositories anywhere makes Spring Boot's
#       JpaRepositoriesAutoConfiguration back off for the entire app, so every
#       consumer had to hand-declare its own or SILENTLY lose its repositories;
#     * the library's repository claimed the bean name `processedEventJpaRepository`
#       — the most natural name any service would pick — so a service that modelled
#       the same concept died with BeanDefinitionOverrideException;
#     * the library's @Entity was scanned into every consumer, so under
#       `ddl-auto: validate` services that never used it still had to create its
#       table or fail to boot.
#
#   Four contexts failed to load this way (TASK-BE-333, TASK-BE-432, TASK-BE-461,
#   TASK-BE-489). Each was patched locally with `exclude = ...`, and the auto-config
#   ended up retained *because* so many services excluded it.
#
# Why a script and not a test: this defect class is invisible to the compiler and to
# unit tests (a slice test never loads auto-configurations). Only a booting context
# sees it, and that needs Docker. This is a static, Docker-free check on the source.
#
# SCOPE — read this before widening the predicate:
#
#   It checks the AUTO-CONFIGURATION CLOSURE only: the classes listed in each lib's
#   META-INF/spring/*.AutoConfiguration.imports, plus everything those classes
#   @Import (transitively). Nothing else.
#
#   A library @Configuration that a consumer must explicitly component-scan is NOT in
#   scope and must NOT be flagged: the consumer opted in and knows what it enabled.
#   `libs/java-gateway`'s SecurityConfig (@EnableWebFluxSecurity) is exactly that — it
#   lives outside every consumer's base package, each gateway names
#   `com.example.apigateway` in its scan, and ecommerce deliberately does not. The
#   first draft of this guard flagged it, which would have made the guard RED on the
#   day it landed — and a guard that is red on arrival gets switched off (TASK-MONO-360).
#   The distinction between "auto" and "opt-in" IS the rule; do not flatten it.
#
set -euo pipefail

cd "$(dirname "$0")/.."

# Annotations whose effect is the whole consuming ApplicationContext. Inside an
# auto-configuration closure a library may declare none of them: it cannot know what
# its consumers scan. (@ConfigurationProperties / @ConditionalOn* / @Bean are scoped
# and fine.)
FORBIDDEN='EnableJpaRepositories|EntityScan|ComponentScan|EnableScheduling|EnableCaching|EnableAsync|EnableTransactionManagement|EnableWebSecurity|EnableWebFluxSecurity|EnableMethodSecurity'

# Resolve a simple class name to its source file under libs/*/src/main/java.
find_source() {
    grep -rlE "^[[:space:]]*(public[[:space:]]+)?(final[[:space:]]+)?(abstract[[:space:]]+)?(class|interface)[[:space:]]+$1\b" \
        --include="$1.java" libs/*/src/main/java 2>/dev/null || true
}

# --- Build the auto-configuration closure -----------------------------------------
queue=""
imports_files=$(ls libs/*/src/main/resources/META-INF/spring/*.AutoConfiguration.imports 2>/dev/null || true)

if [ -z "$imports_files" ]; then
    echo "OK — no shared library registers an auto-configuration; nothing to check."
    exit 0
fi

declared=0
for f in $imports_files; do
    while IFS= read -r fqcn; do
        fqcn="${fqcn%$'\r'}"   # the .imports files are CRLF on this repo's Windows checkouts;
                               # without this strip every class name ends in \r, every lookup
                               # misses, and the guard cheerfully reports OK having checked
                               # nothing. An empty detector is not an absence of defects.
        [ -z "$fqcn" ] && continue
        case "$fqcn" in \#*) continue ;; esac
        declared=$((declared + 1))
        simple="${fqcn##*.}"
        src=$(find_source "$simple")
        if [ -z "$src" ]; then
            echo "FAIL — cannot resolve auto-configuration '$fqcn' to a source file under"
            echo "       libs/*/src/main/java. The guard refuses to pass while blind."
            exit 1
        fi
        queue="$queue $src"
    done < "$f"
done

if [ "$declared" -eq 0 ]; then
    echo "FAIL — found AutoConfiguration.imports file(s) but parsed zero class names."
    echo "       That is a broken parser, not a clean repo. Refusing to report OK."
    exit 1
fi

closure=""
while [ -n "$(echo "$queue" | tr -d '[:space:]')" ]; do
    next=""
    for src in $queue; do
        case " $closure " in *" $src "*) continue ;; esac
        closure="$closure $src"
        # Follow @Import(A.class) / @Import({A.class, B.class}) one hop.
        for imported in $(grep -oE '@Import\(\{?[^)]*\)' "$src" 2>/dev/null \
                          | grep -oE '[A-Za-z_][A-Za-z0-9_]*\.class' \
                          | sed 's/\.class$//' || true); do
            isrc=$(find_source "$imported")
            [ -n "$isrc" ] && next="$next $isrc"
        done
    done
    queue="$next"
done

# --- Check the closure --------------------------------------------------------------
# Annotation position only (`^\s*@Name`), never the word: the class this rule replaced
# had a javadoc that spelled out "@EnableJpaRepositories" three times while explaining
# the trap. A plain word-grep would count those comments and report prose as a
# violation. Javadoc lines start with `*`, so they cannot match `^\s*@`.
violations=""
for src in $closure; do
    hit=$(grep -nE "^[[:space:]]*@(${FORBIDDEN})\b" "$src" 2>/dev/null || true)
    [ -n "$hit" ] && violations="${violations}${src}
${hit}
"
done

if [ -n "$violations" ]; then
    echo "FAIL — a shared library's auto-configuration closure declares a context-wide"
    echo "       Spring annotation."
    echo
    echo "$violations"
    echo "An @AutoConfiguration runs in EVERY consumer without being asked. These"
    echo "annotations are not scoped to the library — they reconfigure the whole"
    echo "consuming application: Spring Boot's matching auto-configuration backs off"
    echo "app-wide, and any entity/repository the library registers collides by bean"
    echo "name with the service's own."
    echo
    echo "Ship the contract, not the mapping: the library provides the port and the"
    echo "generic machinery; the service owns the @Entity, the table, the migration and"
    echo "the scan. A library JPA type that must be shared belongs in a"
    echo "@MappedSuperclass — resolved via the entity class hierarchy, so it needs no"
    echo "entity scan."
    echo
    echo "If the class is meant to be opt-in, take it OUT of AutoConfiguration.imports"
    echo "(and out of any @Import from an auto-config) and let consumers scan it"
    echo "explicitly — that is a different, legitimate pattern."
    echo
    echo "Rule: platform/shared-library-policy.md § 'No context-wide annotations in a"
    echo "shared @AutoConfiguration'. Worked incidents: TASK-BE-333, TASK-BE-432,"
    echo "TASK-BE-461, TASK-BE-489 -> TASK-MONO-406."
    exit 1
fi

n=$(echo "$closure" | wc -w)
echo "OK — no context-wide Spring annotation in any shared auto-configuration."
echo "     (auto-config closure: $n class(es) reached from AutoConfiguration.imports)"
