package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.Category;
import com.example.product.domain.repository.CategoryRepository;
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
        return jpaRepository.findById(category.getId())
                .map(entity -> {
                    entity.update(category);
                    return entity.toDomain();
                })
                .orElseGet(() -> jpaRepository.save(CategoryJpaEntity.from(category)).toDomain());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(UUID id) {
        return jpaRepository.findById(id).map(CategoryJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return jpaRepository.findAll().stream()
                .map(CategoryJpaEntity::toDomain)
                .toList();
    }
}
