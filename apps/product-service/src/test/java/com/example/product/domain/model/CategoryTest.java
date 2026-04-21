package com.example.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Category 엔티티 테스트")
class CategoryTest {

    @Test
    @DisplayName("유효한 값으로 카테고리를 생성할 수 있다")
    void create_validInput_success() {
        Category category = Category.create("전자제품", null);

        assertThat(category.getId()).isNotNull();
        assertThat(category.getName()).isEqualTo("전자제품");
        assertThat(category.getParentId()).isNull();
    }

    @Test
    @DisplayName("부모 카테고리와 함께 생성할 수 있다")
    void create_withParent_success() {
        UUID parentId = UUID.randomUUID();
        Category category = Category.create("노트북", parentId);

        assertThat(category.getParentId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("최상위 카테고리인지 확인할 수 있다")
    void isRoot_noParent_returnsTrue() {
        Category root = Category.create("전자제품", null);
        assertThat(root.isRoot()).isTrue();
    }

    @Test
    @DisplayName("하위 카테고리는 루트가 아니다")
    void isRoot_withParent_returnsFalse() {
        UUID parentId = UUID.randomUUID();
        Category child = Category.create("노트북", parentId);
        assertThat(child.isRoot()).isFalse();
    }

    @Test
    @DisplayName("카테고리 이름이 비어있으면 예외가 발생한다")
    void create_blankName_throws() {
        assertThatThrownBy(() -> Category.create("", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category name must not be blank");
    }

    @Test
    @DisplayName("카테고리 이름이 null이면 예외가 발생한다")
    void create_nullName_throws() {
        assertThatThrownBy(() -> Category.create(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category name must not be blank");
    }

    @Test
    @DisplayName("카테고리 이름이 100자를 초과하면 예외가 발생한다")
    void create_nameTooLong_throws() {
        String longName = "a".repeat(101);
        assertThatThrownBy(() -> Category.create(longName, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Category name must not exceed 100 characters");
    }

    @Test
    @DisplayName("카테고리 이름 앞뒤 공백이 제거된다")
    void create_trimmedName_success() {
        Category category = Category.create("  전자제품  ", null);
        assertThat(category.getName()).isEqualTo("전자제품");
    }
}
