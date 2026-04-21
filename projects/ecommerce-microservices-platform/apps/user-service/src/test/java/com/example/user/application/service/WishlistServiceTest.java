package com.example.user.application.service;

import com.example.user.application.command.AddWishlistItemCommand;
import com.example.user.application.result.AddWishlistItemResult;
import com.example.user.application.result.WishlistCheckResult;
import com.example.user.application.result.WishlistPageResult;
import com.example.user.domain.exception.AlreadyInWishlistException;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.domain.exception.WishlistAccessDeniedException;
import com.example.user.domain.exception.WishlistItemNotFoundException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.user.domain.model.WishlistItem;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.repository.WishlistItemRepository;
import com.example.user.domain.service.ProductInfoProvider;
import com.example.user.domain.service.ProductInfoProvider.ProductInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishlistService 단위 테스트")
class WishlistServiceTest {

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ProductInfoProvider productInfoProvider;

    @InjectMocks
    private WishlistService wishlistService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Nested
    @DisplayName("addItem")
    class AddItem {

        @Test
        @DisplayName("위시리스트에 상품을 추가하면 결과를 반환한다")
        void addItem_validCommand_returnsResult() {
            var command = new AddWishlistItemCommand(USER_ID, PRODUCT_ID);
            given(userProfileRepository.existsByUserId(USER_ID)).willReturn(true);
            given(wishlistItemRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(false);
            given(wishlistItemRepository.save(any(WishlistItem.class))).willAnswer(inv -> inv.getArgument(0));

            AddWishlistItemResult result = wishlistService.addItem(command);

            assertThat(result.wishlistItemId()).isNotNull();
            assertThat(result.productId()).isEqualTo(PRODUCT_ID);
            then(wishlistItemRepository).should().save(any(WishlistItem.class));
        }

        @Test
        @DisplayName("이미 위시리스트에 있는 상품을 추가하면 AlreadyInWishlistException이 발생한다")
        void addItem_duplicate_throwsAlreadyInWishlist() {
            var command = new AddWishlistItemCommand(USER_ID, PRODUCT_ID);
            given(userProfileRepository.existsByUserId(USER_ID)).willReturn(true);
            given(wishlistItemRepository.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(true);

            assertThatThrownBy(() -> wishlistService.addItem(command))
                    .isInstanceOf(AlreadyInWishlistException.class);

            then(wishlistItemRepository).should().existsByUserIdAndProductId(USER_ID, PRODUCT_ID);
        }

        @Test
        @DisplayName("user_profiles에 행이 없으면 UserProfileNotFoundException이 발생한다")
        void addItem_userProfileMissing_throwsUserProfileNotFound() {
            var command = new AddWishlistItemCommand(USER_ID, PRODUCT_ID);
            given(userProfileRepository.existsByUserId(USER_ID)).willReturn(false);

            assertThatThrownBy(() -> wishlistService.addItem(command))
                    .isInstanceOf(UserProfileNotFoundException.class);

            then(wishlistItemRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("getWishlist")
    class GetWishlist {

        @Test
        @DisplayName("위시리스트 목록을 조회하면 상품 정보와 함께 반환한다")
        void getWishlist_withItems_returnsPageWithProductInfo() {
            WishlistItem item = WishlistItem.create(USER_ID, PRODUCT_ID);
            var pageResult = new PageResult<>(List.of(item), 0, 20, 1L, 1);
            given(wishlistItemRepository.findAllByUserId(any(UUID.class), any(PageQuery.class)))
                    .willReturn(pageResult);

            var productInfo = new ProductInfo(PRODUCT_ID, "테스트상품", 15000, "ACTIVE");
            given(productInfoProvider.getProductInfos(anySet()))
                    .willReturn(Map.of(PRODUCT_ID, productInfo));

            WishlistPageResult result = wishlistService.getWishlist(USER_ID, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).productName()).isEqualTo("테스트상품");
            assertThat(result.content().get(0).productPrice()).isEqualTo(15000);
            assertThat(result.content().get(0).productStatus()).isEqualTo("ACTIVE");
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("상품 정보를 가져올 수 없는 경우 DELETED 상태로 반환한다")
        void getWishlist_productNotFound_returnsDELETED() {
            WishlistItem item = WishlistItem.create(USER_ID, PRODUCT_ID);
            var pageResult = new PageResult<>(List.of(item), 0, 20, 1L, 1);
            given(wishlistItemRepository.findAllByUserId(any(UUID.class), any(PageQuery.class)))
                    .willReturn(pageResult);
            given(productInfoProvider.getProductInfos(anySet())).willReturn(Map.of());

            WishlistPageResult result = wishlistService.getWishlist(USER_ID, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).productName()).isNull();
            assertThat(result.content().get(0).productStatus()).isEqualTo("DELETED");
        }

        @Test
        @DisplayName("빈 위시리스트를 조회하면 빈 목록을 반환한다")
        void getWishlist_empty_returnsEmptyPage() {
            var pageResult = new PageResult<WishlistItem>(List.of(), 0, 20, 0L, 0);
            given(wishlistItemRepository.findAllByUserId(any(UUID.class), any(PageQuery.class)))
                    .willReturn(pageResult);

            WishlistPageResult result = wishlistService.getWishlist(USER_ID, 0, 20);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }

        @Test
        @DisplayName("page가 음수이면 0으로 보정한다")
        void getWishlist_negativePage_correctedToZero() {
            var pageResult = new PageResult<WishlistItem>(List.of(), 0, 20, 0L, 0);
            given(wishlistItemRepository.findAllByUserId(any(UUID.class), any(PageQuery.class)))
                    .willReturn(pageResult);

            WishlistPageResult result = wishlistService.getWishlist(USER_ID, -1, 20);

            assertThat(result.page()).isZero();
        }

        @Test
        @DisplayName("size가 100을 초과하면 100으로 보정한다")
        void getWishlist_sizeOver100_correctedTo100() {
            var pageResult = new PageResult<WishlistItem>(List.of(), 0, 100, 0L, 0);
            given(wishlistItemRepository.findAllByUserId(any(UUID.class), any(PageQuery.class)))
                    .willReturn(pageResult);

            WishlistPageResult result = wishlistService.getWishlist(USER_ID, 0, 200);

            assertThat(result.size()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItem {

        @Test
        @DisplayName("자신의 위시리스트 항목을 삭제한다")
        void removeItem_ownItem_deletesSuccessfully() {
            UUID wishlistItemId = UUID.randomUUID();
            WishlistItem item = WishlistItem.reconstitute(wishlistItemId, USER_ID, PRODUCT_ID, java.time.Instant.now());
            given(wishlistItemRepository.findById(wishlistItemId)).willReturn(Optional.of(item));

            wishlistService.removeItem(USER_ID, wishlistItemId);

            then(wishlistItemRepository).should().delete(item);
        }

        @Test
        @DisplayName("존재하지 않는 위시리스트 항목을 삭제하면 WishlistItemNotFoundException이 발생한다")
        void removeItem_nonExisting_throwsNotFound() {
            UUID wishlistItemId = UUID.randomUUID();
            given(wishlistItemRepository.findById(wishlistItemId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> wishlistService.removeItem(USER_ID, wishlistItemId))
                    .isInstanceOf(WishlistItemNotFoundException.class);
        }

        @Test
        @DisplayName("다른 사용자의 위시리스트 항목을 삭제하면 WishlistAccessDeniedException이 발생한다")
        void removeItem_otherUsersItem_throwsAccessDenied() {
            UUID wishlistItemId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            WishlistItem item = WishlistItem.reconstitute(wishlistItemId, otherUserId, PRODUCT_ID, java.time.Instant.now());
            given(wishlistItemRepository.findById(wishlistItemId)).willReturn(Optional.of(item));

            assertThatThrownBy(() -> wishlistService.removeItem(USER_ID, wishlistItemId))
                    .isInstanceOf(WishlistAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("checkItem")
    class CheckItem {

        @Test
        @DisplayName("위시리스트에 있는 상품을 체크하면 true와 wishlistItemId를 반환한다")
        void checkItem_exists_returnsTrue() {
            UUID wishlistItemId = UUID.randomUUID();
            WishlistItem item = WishlistItem.reconstitute(wishlistItemId, USER_ID, PRODUCT_ID, java.time.Instant.now());
            given(wishlistItemRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(Optional.of(item));

            WishlistCheckResult result = wishlistService.checkItem(USER_ID, PRODUCT_ID);

            assertThat(result.productId()).isEqualTo(PRODUCT_ID);
            assertThat(result.inWishlist()).isTrue();
            assertThat(result.wishlistItemId()).isEqualTo(wishlistItemId);
        }

        @Test
        @DisplayName("위시리스트에 없는 상품을 체크하면 false와 null wishlistItemId를 반환한다")
        void checkItem_notExists_returnsFalse() {
            given(wishlistItemRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).willReturn(Optional.empty());

            WishlistCheckResult result = wishlistService.checkItem(USER_ID, PRODUCT_ID);

            assertThat(result.productId()).isEqualTo(PRODUCT_ID);
            assertThat(result.inWishlist()).isFalse();
            assertThat(result.wishlistItemId()).isNull();
        }
    }
}
