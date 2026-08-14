package com.wms.admin.application.security;

/**
 * Resolves the current caller's {@link ReadScope} (TASK-BE-583 / ADR-MONO-065 § D1).
 *
 * <p>An application-layer port so the dashboards depend on the <em>question</em>
 * ("what may this caller see?") rather than on Spring Security. The production
 * implementation reads the signed JWT; tests bind a fake.
 */
public interface ReadScopeProvider {

    ReadScope current();
}
