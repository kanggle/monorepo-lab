package com.example.product.application.service;

import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import com.example.product.domain.event.ProductImagesUpdatedPayload;
import com.example.product.domain.exception.ImageLimitExceededException;
import com.example.product.domain.exception.ImageNotFoundException;
import com.example.product.domain.exception.MediaNotFoundException;
import com.example.product.domain.exception.MediaValidationException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductImage;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.domain.port.PresignedUploadResult;
import com.example.product.domain.port.StorageClient;
import com.example.product.domain.repository.ProductImageRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.infrastructure.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageService 단위 테스트")
class ProductImageServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private StorageClient storageClient;

    @Mock
    private MediaUrlResolver mediaUrlResolver;

    @Mock
    private ProductEventPublisher productEventPublisher;

    private StorageProperties storageProperties;
    private EventPublishingHelper eventPublishingHelper;
    private ProductImageService productImageService;

    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        eventPublishingHelper = new EventPublishingHelper(productEventPublisher);
        productImageService = new ProductImageService(
                productRepository, productImageRepository, storageClient,
                mediaUrlResolver, storageProperties, eventPublishingHelper);

        productId = UUID.randomUUID();
        product = Product.create("테스트 상품", "설명", new Price(10000),
                null, List.of(ProductVariant.create("기본", new StockQuantity(10), new Price(0))));
    }

    // --- generateUploadUrl ---

    @Test
    @DisplayName("유효한 요청으로 presigned URL 발급 성공")
    void generateUploadUrl_validRequest_returnsResult() {
        given(productRepository.existsById(productId)).willReturn(true);
        PresignedUploadResult expectedResult = new PresignedUploadResult(
                "https://s3.example.com/presigned", "products/" + productId + "/0-abc.jpg", Instant.now().plusSeconds(900));
        given(storageClient.generatePresignedPutUrl(anyString(), anyString(), eq("image/jpeg"), eq(1024L)))
                .willReturn(expectedResult);

        PresignedUploadResult result = productImageService.generateUploadUrl(productId, "image/jpeg", 1024L);

        assertThat(result.uploadUrl()).isEqualTo("https://s3.example.com/presigned");
        assertThat(result.objectKey()).isNotNull();
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 상품으로 presigned URL 요청 시 ProductNotFoundException")
    void generateUploadUrl_productNotFound_throwsException() {
        given(productRepository.existsById(productId)).willReturn(false);

        assertThatThrownBy(() -> productImageService.generateUploadUrl(productId, "image/jpeg", 1024L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("허용되지 않은 contentType으로 요청 시 MediaValidationException")
    void generateUploadUrl_invalidContentType_throwsException() {
        given(productRepository.existsById(productId)).willReturn(true);

        assertThatThrownBy(() -> productImageService.generateUploadUrl(productId, "application/pdf", 1024L))
                .isInstanceOf(MediaValidationException.class);
    }

    @Test
    @DisplayName("5MB 초과 contentLength 요청 시 MediaValidationException")
    void generateUploadUrl_tooLargeContent_throwsException() {
        given(productRepository.existsById(productId)).willReturn(true);

        assertThatThrownBy(() -> productImageService.generateUploadUrl(productId, "image/jpeg", 6 * 1024 * 1024))
                .isInstanceOf(MediaValidationException.class);
    }

    // --- registerImage ---

    @Test
    @DisplayName("이미지 등록 성공 시 ProductImage를 반환한다")
    void registerImage_success_returnsImage() {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        ProductImage createdImage = ProductImage.create(productId, objectKey, 0, false);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productImageRepository.countByProductId(productId)).willReturn(0);
        given(storageClient.headObject(anyString(), eq(objectKey))).willReturn(true);
        given(productImageRepository.save(any(ProductImage.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(productImageRepository.findByProductIdOrderBySortOrder(productId))
                .willReturn(List.of(createdImage));

        ProductImage result = productImageService.registerImage(productId, objectKey, 0, false);

        assertThat(result).isNotNull();
        assertThat(result.getObjectKey()).isEqualTo(objectKey);
        verify(productImageRepository).save(any(ProductImage.class));
    }

    @Test
    @DisplayName("이미지 10장 초과 시 ImageLimitExceededException")
    void registerImage_limitExceeded_throwsException() {
        String objectKey = "products/" + productId + "/10-abc.jpg";
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productImageRepository.countByProductId(productId)).willReturn(10);

        assertThatThrownBy(() -> productImageService.registerImage(productId, objectKey, 0, false))
                .isInstanceOf(ImageLimitExceededException.class);
    }

    @Test
    @DisplayName("objectKey HEAD 검증 실패 시 MediaNotFoundException")
    void registerImage_headCheckFails_throwsException() {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productImageRepository.countByProductId(productId)).willReturn(0);
        given(storageClient.headObject(anyString(), eq(objectKey))).willReturn(false);

        assertThatThrownBy(() -> productImageService.registerImage(productId, objectKey, 0, false))
                .isInstanceOf(MediaNotFoundException.class);
    }

    @Test
    @DisplayName("다른 상품의 objectKey로 등록 시 MediaValidationException")
    void registerImage_wrongProductPrefix_throwsException() {
        UUID otherProductId = UUID.randomUUID();
        String objectKey = "products/" + otherProductId + "/0-abc.jpg";
        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> productImageService.registerImage(productId, objectKey, 0, false))
                .isInstanceOf(MediaValidationException.class);
    }

    @Test
    @DisplayName("isPrimary=true 등록 시 기존 primary가 demote된다")
    void registerImage_withPrimary_demotesExisting() {
        String objectKey = "products/" + productId + "/1-abc.jpg";
        ProductImage existingPrimary = ProductImage.create(productId, "products/" + productId + "/0-old.jpg", 0, true);
        ProductImage newImage = ProductImage.create(productId, objectKey, 1, true);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productImageRepository.countByProductId(productId)).willReturn(1);
        given(storageClient.headObject(anyString(), eq(objectKey))).willReturn(true);
        given(productImageRepository.findByProductIdOrderBySortOrder(productId))
                .willReturn(List.of(existingPrimary))  // demoteExistingPrimary
                .willReturn(List.of(newImage))          // updateProductThumbnailUrl
                .willReturn(List.of(newImage));          // publishImagesUpdatedEvent
        given(productImageRepository.save(any(ProductImage.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(mediaUrlResolver.resolve(objectKey)).willReturn("http://cdn/img.jpg");

        productImageService.registerImage(productId, objectKey, 1, true);

        assertThat(existingPrimary.isPrimary()).isFalse();
    }

    @Test
    @DisplayName("이미지 등록 시 ProductImagesUpdated 이벤트가 images 배열을 포함한다")
    void registerImage_success_publishesEventWithImages() {
        String objectKey = "products/" + productId + "/0-abc.jpg";
        ProductImage createdImage = ProductImage.create(productId, objectKey, 0, false);
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productImageRepository.countByProductId(productId)).willReturn(0);
        given(storageClient.headObject(anyString(), eq(objectKey))).willReturn(true);
        given(productImageRepository.save(any(ProductImage.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(productImageRepository.findByProductIdOrderBySortOrder(productId))
                .willReturn(List.of(createdImage))   // updateProductThumbnailUrl
                .willReturn(List.of(createdImage));   // publishImagesUpdatedEvent
        given(mediaUrlResolver.resolve(objectKey)).willReturn("http://cdn/img.jpg");

        productImageService.registerImage(productId, objectKey, 0, false);

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(productEventPublisher).publish(captor.capture());
        ProductEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("ProductImagesUpdated");
        ProductImagesUpdatedPayload payload = (ProductImagesUpdatedPayload) event.payload();
        assertThat(payload.images()).hasSize(1);
        assertThat(payload.images().get(0).objectKey()).isEqualTo(objectKey);
    }

    // --- deleteImage ---

    @Test
    @DisplayName("이미지 삭제 성공")
    void deleteImage_success() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.create(productId, "products/" + productId + "/0-abc.jpg", 0, false);
        given(productRepository.existsById(productId)).willReturn(true);
        given(productImageRepository.findById(imageId)).willReturn(Optional.of(image));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productImageRepository.findByProductIdOrderBySortOrder(productId)).willReturn(List.of());
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        productImageService.deleteImage(productId, imageId);

        verify(productImageRepository).delete(image);
        verify(storageClient).deleteObject(anyString(), eq(image.getObjectKey()));
    }

    @Test
    @DisplayName("존재하지 않는 이미지 삭제 시 ImageNotFoundException")
    void deleteImage_imageNotFound_throwsException() {
        UUID imageId = UUID.randomUUID();
        given(productRepository.existsById(productId)).willReturn(true);
        given(productImageRepository.findById(imageId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productImageService.deleteImage(productId, imageId))
                .isInstanceOf(ImageNotFoundException.class);
    }

    @Test
    @DisplayName("primary 이미지 삭제 시 최소 sortOrder 이미지가 promote된다")
    void deleteImage_primaryDeleted_promotesLowest() {
        UUID imageId = UUID.randomUUID();
        ProductImage primaryImage = ProductImage.create(productId, "products/" + productId + "/0-abc.jpg", 0, true);
        ProductImage secondImage = ProductImage.create(productId, "products/" + productId + "/1-def.jpg", 1, false);

        given(productRepository.existsById(productId)).willReturn(true);
        given(productImageRepository.findById(imageId)).willReturn(Optional.of(primaryImage));
        given(productImageRepository.findByProductIdOrderBySortOrder(productId))
                .willReturn(List.of(secondImage)) // after delete, for promote
                .willReturn(List.of(secondImage)) // for thumbnailUrl update
                .willReturn(List.of(secondImage)); // for publishImagesUpdatedEvent
        given(productImageRepository.save(any(ProductImage.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(mediaUrlResolver.resolve(secondImage.getObjectKey())).willReturn("http://cdn/img");

        productImageService.deleteImage(productId, imageId);

        assertThat(secondImage.isPrimary()).isTrue();
    }

    // --- getImages ---

    @Test
    @DisplayName("이미지 목록 조회 성공")
    void getImages_success() {
        ProductImage img1 = ProductImage.create(productId, "products/" + productId + "/0-abc.jpg", 0, true);
        ProductImage img2 = ProductImage.create(productId, "products/" + productId + "/1-def.jpg", 1, false);
        given(productRepository.existsById(productId)).willReturn(true);
        given(productImageRepository.findByProductIdOrderBySortOrder(productId)).willReturn(List.of(img1, img2));

        List<ProductImage> result = productImageService.getImages(productId);

        assertThat(result).hasSize(2);
    }

    // --- updateImage ---

    @Test
    @DisplayName("이미지 업데이트 성공")
    void updateImage_success() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.create(productId, "products/" + productId + "/0-abc.jpg", 0, false);
        given(productRepository.existsById(productId)).willReturn(true);
        given(productImageRepository.findById(imageId)).willReturn(Optional.of(image));
        given(productImageRepository.save(any(ProductImage.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(productImageRepository.findByProductIdOrderBySortOrder(productId)).willReturn(List.of(image));
        given(mediaUrlResolver.resolve(image.getObjectKey())).willReturn("http://cdn/img");

        ProductImage result = productImageService.updateImage(productId, imageId, 5, true);

        assertThat(result.getSortOrder()).isEqualTo(5);
        assertThat(result.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("이미 primary인 이미지에 isPrimary=true 요청 시 demote를 호출하지 않는다")
    void updateImage_alreadyPrimary_skipsDemote() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.create(productId, "products/" + productId + "/0-abc.jpg", 0, true);
        given(productRepository.existsById(productId)).willReturn(true);
        given(productImageRepository.findById(imageId)).willReturn(Optional.of(image));
        given(productImageRepository.save(any(ProductImage.class))).willAnswer(inv -> inv.getArgument(0));
        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(productImageRepository.findByProductIdOrderBySortOrder(productId)).willReturn(List.of(image));
        given(mediaUrlResolver.resolve(image.getObjectKey())).willReturn("http://cdn/img");

        ProductImage result = productImageService.updateImage(productId, imageId, null, true);

        // Should still be primary, no demote happened
        assertThat(result.isPrimary()).isTrue();
    }
}
