package com.example.product.application.service;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import com.example.product.domain.exception.IdempotencyKeyConflictException;
import com.example.product.domain.exception.IdempotencyKeyRequiredException;
import com.example.product.domain.exception.InvalidCategoryException;
import com.example.product.domain.model.Category;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductCreateRequest;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.CategoryRepository;
import com.example.product.domain.repository.ProductCreateRequestRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.application.port.ProductMetricPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterProductService 단위 테스트")
class RegisterProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private ProductMetricPort productMetrics;

    @Mock
    private ProductCreateRequestRepository productCreateRequestRepository;

    private EventPublishingHelper eventPublishingHelper;
    private RegisterProductService registerProductService;

    private RegisterProductCommand validCommand;

    @BeforeEach
    void setUp() {
        eventPublishingHelper = new EventPublishingHelper(productEventPublisher);
        // Real resolver: no explicit seller + no scope → tenant default seller.
        SellerOwnershipResolver sellerOwnershipResolver = new SellerOwnershipResolver();
        Clock clock = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC);
        registerProductService = new RegisterProductService(
                productRepository, categoryRepository, sellerRepository,
                sellerOwnershipResolver, eventPublishingHelper, productMetrics,
                productCreateRequestRepository, clock);
        // Not every test reaches the replay lookup (e.g. invalid-category throws
        // earlier) — lenient() so those do not trip STRICT_STUBS.
        lenient().when(productCreateRequestRepository.find(any(), any())).thenReturn(Optional.empty());

        validCommand = new RegisterProductCommand(
                "테스트 상품",
                "상품 설명",
                10000L,
                null,
                null,
                null,
                List.of(new VariantCommand("기본", 10, 0)),
                "idem-key-1");
    }

    @Test
    @DisplayName("명시/스코프 없으면 default seller 로 귀속 + default seller 시드 보장 (D8)")
    void register_noSellerNoScope_ownedByDefaultSeller() {
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        registerProductService.register(validCommand);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo(Seller.DEFAULT_SELLER_ID);
        // default 귀속 시 per-tenant default seller 존재 보장 (idempotent)
        verify(sellerRepository).ensureDefaultSeller();
    }

    @Test
    @DisplayName("request.sellerId 가 있으면 그 셀러로 귀속 (default seller 시드 불필요)")
    void register_explicitSeller_ownedByThatSeller() {
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        RegisterProductCommand command = new RegisterProductCommand(
                "셀러 상품", "설명", 10000L, null, null, "seller-a1",
                List.of(new VariantCommand("기본", 10, 0)), "idem-key-2");

        registerProductService.register(command);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSellerId()).isEqualTo("seller-a1");
        verify(sellerRepository, never()).ensureDefaultSeller();
    }

    @Test
    @DisplayName("상품 등록 성공 시 상품 ID를 반환한다")
    void register_success_returnsId() {
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        UUID id = registerProductService.register(validCommand);

        assertThat(id).isNotNull();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("등록 성공 시 ProductCreated 이벤트가 발행된다")
    void register_success_publishesEvent() {
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        registerProductService.register(validCommand);

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(productEventPublisher).publish(captor.capture());

        ProductEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("ProductCreated");
        assertThat(event.source()).isEqualTo("product-service");
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    @DisplayName("variants가 없으면 IllegalArgumentException이 발생한다")
    void register_noVariants_throwsException() {
        RegisterProductCommand commandWithNoVariants = new RegisterProductCommand(
                "상품명", "설명", 10000L, null, null, null, List.of(), "idem-key-3");

        assertThatThrownBy(() -> registerProductService.register(commandWithNoVariants))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
        verify(productEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("이벤트 발행 실패해도 상품은 등록된다")
    void register_eventPublishFails_productStillSaved() {
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Event publish failed")).when(productEventPublisher).publish(any());

        UUID id = registerProductService.register(validCommand);

        assertThat(id).isNotNull();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("존재하지 않는 categoryId로 등록 시 IllegalArgumentException 발생")
    void register_invalidCategoryId_throwsException() {
        UUID fakeCategoryId = UUID.randomUUID();
        RegisterProductCommand commandWithCategory = new RegisterProductCommand(
                "상품명", "설명", 10000L, fakeCategoryId, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "idem-key-4");

        given(categoryRepository.findById(fakeCategoryId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> registerProductService.register(commandWithCategory))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("Category not found");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("유효한 categoryId로 등록 성공")
    void register_validCategoryId_success() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.create("전자제품", null);
        RegisterProductCommand commandWithCategory = new RegisterProductCommand(
                "상품명", "설명", 10000L, categoryId, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "idem-key-5");

        given(categoryRepository.findById(categoryId)).willReturn(Optional.of(category));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        UUID id = registerProductService.register(commandWithCategory);

        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("price가 음수면 도메인에서 예외 발생")
    void register_negativePrice_throwsException() {
        RegisterProductCommand commandWithNegativePrice = new RegisterProductCommand(
                "상품명", "설명", -1000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "idem-key-6");

        assertThatThrownBy(() -> registerProductService.register(commandWithNegativePrice))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productRepository, never()).save(any());
    }

    // ── TASK-BE-536: Idempotency-Key guard ─────────────────────────────────────

    @Test
    @DisplayName("AC-0/F4 Idempotency-Key 없으면 IdempotencyKeyRequiredException, 상품 미생성")
    void register_missingIdempotencyKey_isRefused_noProductCreated() {
        RegisterProductCommand command = new RegisterProductCommand(
                "상품명", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), null);

        assertThatThrownBy(() -> registerProductService.register(command))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        verify(productRepository, never()).save(any());
        verify(productEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("AC-0/F4 blank Idempotency-Key 도 거부된다 (webhook-store 널 구멍을 복사하지 않는다)")
    void register_blankIdempotencyKey_isRefused() {
        RegisterProductCommand command = new RegisterProductCommand(
                "상품명", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "   ");

        assertThatThrownBy(() -> registerProductService.register(command))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC-1 같은 키 + 같은 상품명 재생 → 기존 productId 반환, 재생성/재발행 없음")
    void register_sameKeySameName_isReplay_noSecondProduct() {
        UUID existingId = UUID.randomUUID();
        given(productCreateRequestRepository.find("ecommerce", "idem-A"))
                .willReturn(Optional.of(ProductCreateRequest.reconstitute(
                        1L, "ecommerce", "idem-A", "테스트 상품", existingId, Instant.now())));

        RegisterProductCommand command = new RegisterProductCommand(
                "테스트 상품", "상품 설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "idem-A");

        UUID id = registerProductService.register(command);

        assertThat(id).isEqualTo(existingId);
        verify(productRepository, never()).save(any());
        verify(productEventPublisher, never()).publish(any());
        verify(productCreateRequestRepository, never()).insert(any());
    }

    @Test
    @DisplayName("같은 키 + 다른 상품명 재사용 → IdempotencyKeyConflictException, 상품 미생성")
    void register_sameKeyDifferentName_isConflict() {
        given(productCreateRequestRepository.find("ecommerce", "idem-A"))
                .willReturn(Optional.of(ProductCreateRequest.reconstitute(
                        1L, "ecommerce", "idem-A", "원래 상품", UUID.randomUUID(), Instant.now())));

        RegisterProductCommand command = new RegisterProductCommand(
                "다른 상품", "상품 설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "idem-A");

        assertThatThrownBy(() -> registerProductService.register(command))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC-4 동시성 — claim insert 가 유니크 제약 위반 → IdempotencyKeyConflictException, 상품 미생성")
    void register_concurrentDuplicate_claimInsertLosesRace_isConflict() {
        given(productCreateRequestRepository.insert(any()))
                .willThrow(new DataIntegrityViolationException("uq_product_create_request_key"));

        assertThatThrownBy(() -> registerProductService.register(validCommand))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(productRepository, never()).save(any());
        verify(productEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("AC-2 다른 키로 같은 상품명을 다시 등록 → 정상적으로 두 번째 상품이 생성된다")
    void register_differentKey_sameName_isGenuineSecondRegistration() {
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        RegisterProductCommand command = new RegisterProductCommand(
                "테스트 상품", "상품 설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "idem-B");

        UUID id = registerProductService.register(command);

        assertThat(id).isNotNull();
        verify(productRepository).save(any(Product.class));
    }
}
