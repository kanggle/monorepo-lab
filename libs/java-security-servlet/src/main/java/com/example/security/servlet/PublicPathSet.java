package com.example.security.servlet;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.Set;

/**
 * The {@code EXACT}/{@code PREFIXES} matching <strong>mechanism</strong> shared by every project's
 * {@code PublicPaths} class (ADR-MONO-058 § D5).
 *
 * <h2>Mechanism, not policy</h2>
 *
 * Every project's {@code PublicPaths} class was found, on audit, to carry the identical matching logic —
 * an exact-match {@code Set<String>}, a prefix {@code Set<String>} whose entries end in {@code /}, and an
 * {@code isPublic(String)}/{@code isPublic(HttpServletRequest)} pair that checks the former, then the
 * latter. The <em>data</em> — which paths are actually public — is service policy and does not move here:
 * each service still owns its own {@code PublicPaths} class, still supplies its own {@code EXACT}/
 * {@code PREFIXES} literals, and still decides what is public. This type holds no path string and no
 * import from any {@code projects/} module (`libs/java-security-servlet/build.gradle`'s own header
 * comment states the same discipline for {@link TenantClaimEnforcer}'s exemption predicate — the shared
 * library never reaches into a project's {@code PublicPaths} class).
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * public final class PublicPaths {
 *     public static final Set<String> EXACT = Set.of("/actuator/health", ...);
 *     public static final Set<String> PREFIXES = Set.of("/actuator/health/");
 *     private static final PublicPathSet MECHANISM = PublicPathSet.of(EXACT, PREFIXES);
 *
 *     private PublicPaths() {}
 *
 *     public static boolean isPublic(String path) { return MECHANISM.isPublic(path); }
 *     public static boolean isPublic(HttpServletRequest request) { return MECHANISM.isPublic(request); }
 * }
 * }</pre>
 */
public final class PublicPathSet {

    private final Set<String> exact;
    private final Set<String> prefixes;

    private PublicPathSet(Set<String> exact, Set<String> prefixes) {
        this.exact = Set.copyOf(Objects.requireNonNull(exact, "exact"));
        this.prefixes = Set.copyOf(Objects.requireNonNull(prefixes, "prefixes"));
        for (String prefix : this.prefixes) {
            if (!prefix.endsWith("/")) {
                throw new IllegalArgumentException("prefix must end with '/': " + prefix);
            }
        }
    }

    /**
     * Builds a {@code PublicPathSet} from the caller's own exact-match and prefix sets. Both sets are
     * defensively copied — the caller's own {@code EXACT}/{@code PREFIXES} constants remain the
     * source of truth its {@code SecurityConfig} reads directly; this instance never aliases them.
     *
     * @throws IllegalArgumentException if any {@code prefixes} entry does not end with {@code /}
     */
    public static PublicPathSet of(Set<String> exact, Set<String> prefixes) {
        return new PublicPathSet(exact, prefixes);
    }

    /** The exact-match path set, as supplied to {@link #of(Set, Set)}. */
    public Set<String> exact() {
        return exact;
    }

    /** The prefix path set, as supplied to {@link #of(Set, Set)}. Every entry ends with {@code /}. */
    public Set<String> prefixes() {
        return prefixes;
    }

    /** Returns true if {@code path} matches an exact entry or falls under a prefix entry. */
    public boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        if (exact.contains(path)) {
            return true;
        }
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Convenience overload for servlet filter/security-config usage. */
    public boolean isPublic(HttpServletRequest request) {
        return isPublic(request.getRequestURI());
    }
}
