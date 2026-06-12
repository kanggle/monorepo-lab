package com.example.admin.infrastructure.persistence.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * TASK-BE-355 (ADR-MONO-029) — admin-local governance tags for a non-operator
 * resource (tenant / account), keyed by {@code (resource_type, resource_id)}.
 *
 * <p>The RESOURCE_TAG access condition reads {@link #getTags()} (a comma-separated
 * string, possibly {@code null}) for the targeted resource so the gate can be
 * evaluated at the single decision site. Tags are <b>trusted domain data</b>
 * (anti-spoof, § D2-C) — never sourced from the request. Seed / admin-SQL only;
 * there is no tag-set API (mirrors {@code admin_operators.tags}).
 */
@Entity
@Table(name = "admin_resource_tags")
@IdClass(AdminResourceTagJpaEntity.Pk.class)
public class AdminResourceTagJpaEntity {

    @Id
    @Column(name = "resource_type")
    private String resourceType;

    @Id
    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "tags")
    private String tags;

    protected AdminResourceTagJpaEntity() {
        // JPA
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    /** Comma-separated tags; may be {@code null} (an untagged resource). */
    public String getTags() {
        return tags;
    }

    /** Composite primary key {@code (resource_type, resource_id)}. */
    public static class Pk implements Serializable {
        private String resourceType;
        private String resourceId;

        public Pk() {
        }

        public Pk(String resourceType, String resourceId) {
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(resourceType, pk.resourceType)
                    && Objects.equals(resourceId, pk.resourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceType, resourceId);
        }
    }
}
