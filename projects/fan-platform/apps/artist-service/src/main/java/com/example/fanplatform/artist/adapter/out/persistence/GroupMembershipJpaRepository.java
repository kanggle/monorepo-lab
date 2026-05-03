package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.fanplatform.artist.adapter.out.persistence.GroupMembershipJpaEntity.GroupMembershipKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface GroupMembershipJpaRepository
        extends JpaRepository<GroupMembershipJpaEntity, GroupMembershipKey> {

    @Query("""
            SELECT m FROM GroupMembershipJpaEntity m
            WHERE m.key.groupId = :groupId
              AND m.tenantId = :tenantId
              AND m.leftAt IS NULL
              AND m.role <> com.example.fanplatform.artist.domain.group.GroupRole.FORMER_MEMBER
            ORDER BY m.key.joinedAt ASC
            """)
    List<GroupMembershipJpaEntity> findActiveByGroupId(@Param("groupId") String groupId,
                                                       @Param("tenantId") String tenantId);

    @Query("""
            SELECT m FROM GroupMembershipJpaEntity m
            WHERE m.key.groupId = :groupId
              AND m.tenantId = :tenantId
            ORDER BY m.key.joinedAt ASC
            """)
    List<GroupMembershipJpaEntity> findAllByGroupId(@Param("groupId") String groupId,
                                                    @Param("tenantId") String tenantId);

    @Query("""
            SELECT m FROM GroupMembershipJpaEntity m
            WHERE m.key.groupId = :groupId
              AND m.key.artistId = :artistId
              AND m.tenantId = :tenantId
              AND m.leftAt IS NULL
              AND m.role <> com.example.fanplatform.artist.domain.group.GroupRole.FORMER_MEMBER
            """)
    Optional<GroupMembershipJpaEntity> findActive(@Param("groupId") String groupId,
                                                  @Param("artistId") String artistId,
                                                  @Param("tenantId") String tenantId);
}
