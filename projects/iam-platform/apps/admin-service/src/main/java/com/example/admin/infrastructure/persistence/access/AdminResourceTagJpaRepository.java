package com.example.admin.infrastructure.persistence.access;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * TASK-BE-355 (ADR-MONO-029) — reads the admin-local governance tags for a
 * non-operator resource (tenant / account) so the RESOURCE_TAG
 * {@code ResourceTagResolver}s can resolve a target's tags from trusted data.
 */
public interface AdminResourceTagJpaRepository
        extends JpaRepository<AdminResourceTagJpaEntity, AdminResourceTagJpaEntity.Pk> {

    /**
     * The raw {@code tags} string for {@code (resourceType, resourceId)} — a native
     * projection so only the column is read. Returns the (possibly {@code null} /
     * empty) tags when a row exists; {@link Optional#empty()} when it does not. Both
     * an absent row and a {@code NULL} column mean "the resource is untagged".
     */
    @Query(value = "SELECT tags FROM admin_resource_tags "
            + "WHERE resource_type = :resourceType AND resource_id = :resourceId",
            nativeQuery = true)
    Optional<String> findTags(@Param("resourceType") String resourceType,
                              @Param("resourceId") String resourceId);
}
