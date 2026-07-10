package com.example.account.infrastructure.persistence;

import com.example.account.domain.orgnode.OrgNode;
import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.repository.OrgNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrgNodeRepositoryImpl implements OrgNodeRepository {

    private final OrgNodeJpaRepository jpaRepository;

    @Override
    public Optional<OrgNode> findById(OrgNodeId id) {
        return jpaRepository.findById(id.value()).map(OrgNodeJpaEntity::toDomain);
    }

    @Override
    public List<OrgNode> findAll() {
        return jpaRepository.findAllOrdered().stream().map(OrgNodeJpaEntity::toDomain).toList();
    }

    @Override
    public List<OrgNode> findByParentId(OrgNodeId parentId) {
        return jpaRepository.findByParentIdOrderByIdAsc(parentId.value()).stream()
                .map(OrgNodeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean hasChildren(OrgNodeId id) {
        return jpaRepository.existsByParentId(id.value());
    }

    @Override
    public OrgNode save(OrgNode node) {
        return jpaRepository.save(OrgNodeJpaEntity.fromDomain(node)).toDomain();
    }

    @Override
    public void deleteById(OrgNodeId id) {
        jpaRepository.deleteById(id.value());
    }
}
