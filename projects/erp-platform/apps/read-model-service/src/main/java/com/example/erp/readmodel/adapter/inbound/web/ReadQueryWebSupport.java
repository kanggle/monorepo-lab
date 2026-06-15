package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.presentation.security.OrgScope;
import com.example.erp.readmodel.presentation.security.ReadAuthorizationGate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Shared read-query controller plumbing (extracted dedup — net-zero). Holds the
 * paging validation bound and the {@code org_scope} claim → root-id mapping that
 * the org-view / approval-fact / delegation-fact controllers all repeat.
 */
final class ReadQueryWebSupport {

    /** Maximum page size accepted by every read-model list endpoint. */
    static final int MAX_SIZE = 100;

    private ReadQueryWebSupport() {
    }

    /**
     * Validates the paging parameters. Preserves the exact bounds + the
     * {@link IllegalArgumentException} messages from the in-lined copies (400
     * {@code VALIDATION_ERROR}).
     */
    static void validatePaging(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    /**
     * Maps the operator's {@code org_scope} claim to the use-case filter argument:
     * platform/absent scope → {@code null} (no narrowing, net-zero); a bounded
     * scope → an immutable copy of its subtree-root ids.
     */
    static List<String> orgScopeRootIds(ReadAuthorizationGate readGate, Jwt jwt) {
        OrgScope orgScope = readGate.orgScope(jwt);
        return orgScope.isPlatform() ? null : List.copyOf(orgScope.roots());
    }
}
