package com.example.security.access;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * ADR-MONO-029 — the {@code RESOURCE_TAG} member of the ADR-MONO-026 closed
 * access-condition enum: gate an already-authorised action by the <b>targeted
 * resource's tags</b>.
 *
 * <p><b>The 3rd / final condition type</b> (sibling to {@link SourceIpCondition} /
 * {@link TimeWindowCondition}), completing the closed enum. Added as a code change
 * (a new evaluator class + tests), never runtime registration (ADR-026 § D1). See
 * {@code platform/access-conditions.md}.
 *
 * <p><b>Input = a resource attribute, not request context.</b> Unlike
 * {@code SOURCE_IP} (a request header) and {@code TIME_WINDOW} (the request clock),
 * this evaluator's input is the target resource's tag set — domain data the
 * consuming domain resolves (ADR-029 § D2). The evaluator itself is
 * framework-agnostic (raw {@link Set}s of strings).
 *
 * <p><b>Two modes (a single negation lives within the type, ADR-026 § D1 — never a
 * combinator):</b>
 * <ul>
 *   <li>{@link #forbidden(Collection) deny-if-present} — denies when the resource
 *       <b>carries</b> any forbidden tag (e.g. a {@code protected} operator's
 *       mutation is denied). ADR-026 § D4's "deny mutating actions on rows tagged
 *       confidential".</li>
 *   <li>{@link #required(Collection) require} — allows only when the resource
 *       carries <b>all</b> required tags.</li>
 * </ul>
 *
 * <p><b>Semantics (the three invariants every access condition shares):</b>
 * <ul>
 *   <li><b>Restriction-only</b> — gates an already RBAC-/tenant-/data-scope-passed
 *       action; never grants.</li>
 *   <li><b>Fail-safe</b> — a {@code null} resource tag set (the resolver could not
 *       determine the resource's tags) yields {@code false} (deny), never allow. An
 *       <i>empty</i> set is NOT null — it means "the resource is known to carry no
 *       tags" and is allowed under deny-if-present (no forbidden tag present).</li>
 *   <li><b>Net-zero / opt-in</b> — when no tag is declared ({@link #isConfigured()}
 *       false) there is no gate; {@link #isSatisfiedBy(Set)} returns {@code true}
 *       for every input, so an unconfigured endpoint behaves exactly as before.</li>
 * </ul>
 *
 * <p>Tags are matched case-insensitively (both config and resource tags are
 * trimmed + lower-cased); blank entries are dropped.
 */
public final class ResourceTagCondition {

    private final boolean configured;
    private final boolean denyIfPresent;
    private final Set<String> tags;

    private ResourceTagCondition(boolean configured, boolean denyIfPresent, Set<String> tags) {
        this.configured = configured;
        this.denyIfPresent = denyIfPresent;
        this.tags = tags;
    }

    /**
     * Build a <b>deny-if-present</b> condition: an action is denied when the target
     * resource carries ANY of {@code forbiddenTags}.
     *
     * @param forbiddenTags the tags that block the action (e.g. {@code ["protected"]});
     *                      {@code null} / blank entries dropped. Empty ⇒ net-zero.
     */
    public static ResourceTagCondition forbidden(Collection<String> forbiddenTags) {
        Set<String> norm = normalize(forbiddenTags);
        return new ResourceTagCondition(!norm.isEmpty(), true, norm);
    }

    /**
     * Build a <b>require</b> condition: an action is allowed only when the target
     * resource carries ALL of {@code requiredTags}.
     *
     * @param requiredTags the tags the resource must carry; {@code null} / blank
     *                     entries dropped. Empty ⇒ net-zero.
     */
    public static ResourceTagCondition required(Collection<String> requiredTags) {
        Set<String> norm = normalize(requiredTags);
        return new ResourceTagCondition(!norm.isEmpty(), false, norm);
    }

    /**
     * {@code true} iff the domain declared at least one tag — i.e. the gate is
     * active. When {@code false} the condition is net-zero; callers MUST
     * short-circuit on this before denying.
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Whether {@code resourceTags} satisfies the condition. {@code true} when the
     * condition is unconfigured (net-zero); otherwise, for deny-if-present,
     * {@code true} iff the resource carries none of the forbidden tags; for require,
     * {@code true} iff it carries all required tags. A {@code null} {@code
     * resourceTags} is a fail-safe deny ({@code false}); an empty set means "no
     * tags" (allowed under deny-if-present, denied under require).
     */
    public boolean isSatisfiedBy(Set<String> resourceTags) {
        if (!configured) {
            return true;
        }
        if (resourceTags == null) {
            return false; // fail-safe: unresolved tags deny
        }
        Set<String> norm = normalize(resourceTags);
        if (denyIfPresent) {
            for (String forbidden : tags) {
                if (norm.contains(forbidden)) {
                    return false;
                }
            }
            return true;
        }
        return norm.containsAll(tags);
    }

    /** Trim + lower-case (locale-root) each entry, dropping null/blank. */
    private static Set<String> normalize(Collection<String> raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
