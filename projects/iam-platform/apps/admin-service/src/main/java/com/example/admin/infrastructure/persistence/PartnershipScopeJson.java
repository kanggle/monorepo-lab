package com.example.admin.infrastructure.persistence;

import com.example.admin.domain.rbac.ScopeSet;

import java.util.List;

/**
 * TASK-BE-477 / ADR-MONO-045 — infrastructure JSON value type for the
 * {@code delegated_scope} / {@code participant_scope} MySQL {@code JSON} columns:
 * {@code {"domains": [...], "roles": [...]}}.
 *
 * <p>A mutable bean (no-arg ctor + getters/setters) so Hibernate's
 * {@code @JdbcTypeCode(SqlTypes.JSON)} Jackson mapper can (de)serialise it without a
 * {@code @JsonCreator} or parameter-name module. Kept OUT of the domain layer so the
 * framework-free {@link ScopeSet} value object carries no Jackson/Hibernate coupling;
 * conversion happens here via {@link #from(ScopeSet)} / {@link #toScopeSet(PartnershipScopeJson)}.
 */
public class PartnershipScopeJson {

    private List<String> domains;
    private List<String> roles;

    public PartnershipScopeJson() {
    }

    public PartnershipScopeJson(List<String> domains, List<String> roles) {
        this.domains = domains;
        this.roles = roles;
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    /** Domain {@link ScopeSet} → persistence JSON. {@code null} scope → {@code null}. */
    public static PartnershipScopeJson from(ScopeSet scope) {
        return scope == null ? null : new PartnershipScopeJson(scope.domains(), scope.roles());
    }

    /** Persistence JSON → normalised domain {@link ScopeSet}. {@code null} → {@code null}. */
    public static ScopeSet toScopeSet(PartnershipScopeJson json) {
        return json == null ? null : ScopeSet.of(json.domains, json.roles);
    }
}
