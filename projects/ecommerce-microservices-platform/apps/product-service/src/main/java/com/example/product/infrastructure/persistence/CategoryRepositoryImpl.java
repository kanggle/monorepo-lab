package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.Category;
import com.example.product.domain.repository.CategoryRepository;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.persistence.entity.CategoryJpaEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;

    CategoryRepositoryImpl(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Category save(Category category) {
        String tenantId = TenantContext.currentTenant();
        return jpaRepository.findByIdAndTenantId(category.getId(), tenantId)
                .map(entity -> {
                    entity.update(category);
                    return entity.toDomain();
                })
                .orElseGet(() -> jpaRepository.save(CategoryJpaEntity.from(category, tenantId)).toDomain());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(UUID id) {
        return jpaRepository.findByIdAndTenantId(id, TenantContext.currentTenant())
                .map(CategoryJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return jpaRepository.findAllByTenantId(TenantContext.currentTenant()).stream()
                .map(CategoryJpaEntity::toDomain)
                .toList();
    }
}
