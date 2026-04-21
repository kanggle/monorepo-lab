package com.example.product.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Category {

    private UUID id;
    private String name;
    private UUID parentId;

    public static Category create(String name, UUID parentId) {
        validateName(name);
        Category category = new Category();
        category.id = UUID.randomUUID();
        category.name = name.trim();
        category.parentId = parentId;
        return category;
    }

    public static Category reconstitute(UUID id, String name, UUID parentId) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Category name must not be blank");
        Category category = new Category();
        category.id = id;
        category.name = name;
        category.parentId = parentId;
        return category;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        if (name.trim().length() > 100) {
            throw new IllegalArgumentException("Category name must not exceed 100 characters");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category c)) return false;
        return id != null && id.equals(c.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
