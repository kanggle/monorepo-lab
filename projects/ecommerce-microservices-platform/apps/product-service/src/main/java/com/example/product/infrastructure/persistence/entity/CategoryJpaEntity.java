package com.example.product.infrastructure.persistence.entity;

import com.example.product.domain.model.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryJpaEntity implements Persistable<UUID> {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public static CategoryJpaEntity from(Category category) {
        CategoryJpaEntity entity = new CategoryJpaEntity();
        entity.id = category.getId();
        entity.name = category.getName();
        entity.parentId = category.getParentId();
        entity.isNew = true;
        return entity;
    }

    public void update(Category category) {
        this.name = category.getName();
        this.parentId = category.getParentId();
    }

    public Category toDomain() {
        return Category.reconstitute(id, name, parentId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CategoryJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
