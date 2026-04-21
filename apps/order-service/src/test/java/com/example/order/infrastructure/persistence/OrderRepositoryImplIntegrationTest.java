package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.Order.OrderItemData;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderRepositoryImpl save() 최적화 통합 테스트.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 JPA dirty check 동작 및
 * 쿼리 발생 패턴을 Hibernate Statistics로 검증한다.</p>
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("OrderRepositoryImpl save() 최적화 통합 테스트")
class OrderRepositoryImplIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("신규 주문 저장 시 INSERT 쿼리가 실행되고 SELECT 없이 처리된다")
    void save_newOrder_executesInsertWithoutSelect() {
        // given: version이 null인 신규 주문 생성
        Order newOrder = createNewOrder();
        assertThat(newOrder.getVersion()).isNull();

        // when: 트랜잭션 내에서 저장하고 Statistics 측정
        Order saved = transactionTemplate.execute(status -> {
            statistics.clear();
            Order result = orderRepository.save(newOrder);
            entityManager.flush();
            return result;
        });

        // then: SELECT 쿼리 없이 INSERT만 실행됨
        long entityLoadCount = statistics.getEntityLoadCount();
        long entityInsertCount = statistics.getEntityInsertCount();

        assertThat(saved).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getOrderId()).isEqualTo(newOrder.getOrderId());
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);

        // 신규 저장 시 entity load(SELECT)가 발생하지 않아야 한다
        assertThat(entityLoadCount)
                .as("신규 주문 저장 시 SELECT(entity load)가 발생하지 않아야 한다")
                .isEqualTo(0);
        // INSERT는 주문 + 주문 아이템에 대해 발생
        assertThat(entityInsertCount)
                .as("INSERT 쿼리가 실행되어야 한다 (주문 1건 + 아이템 1건)")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("기존 주문 업데이트 시 findById SELECT 1회 후 dirty check UPDATE가 실행된다")
    void save_existingOrder_executesFindByIdSelectThenDirtyCheckUpdate() {
        // given: 먼저 신규 주문을 저장
        Order savedOrder = transactionTemplate.execute(status -> orderRepository.save(createNewOrder()));
        assertThat(savedOrder).isNotNull();
        assertThat(savedOrder.getVersion()).isNotNull();

        // 도메인 모델에서 상태 변경 (reconstitute로 version 포함하여 재구성)
        Order orderToUpdate = Order.reconstitute(
                savedOrder.getOrderId(),
                savedOrder.getUserId(),
                savedOrder.getItems(),
                savedOrder.getStatus(),
                savedOrder.getTotalPrice(),
                savedOrder.getShippingAddress(),
                savedOrder.getCreatedAt(),
                savedOrder.getUpdatedAt(),
                savedOrder.getPaymentId(),
                savedOrder.getPaidAt(),
                savedOrder.getRefundedAt(),
                savedOrder.getVersion()
        );
        orderToUpdate.confirm(Clock.systemUTC());

        // when: 업데이트 실행 시 Statistics 측정
        Order updated = transactionTemplate.execute(status -> {
            statistics.clear();
            Order result = orderRepository.save(orderToUpdate);
            entityManager.flush();
            return result;
        });

        // then: findById에 의한 SELECT 1회 + dirty check에 의한 UPDATE 실행
        long entityLoadCount = statistics.getEntityLoadCount();
        long entityUpdateCount = statistics.getEntityUpdateCount();

        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // version이 있는 주문 업데이트 시 findById로 1회 SELECT 발생
        assertThat(entityLoadCount)
                .as("기존 주문 업데이트 시 findById에 의한 entity load(SELECT)가 발생해야 한다")
                .isGreaterThanOrEqualTo(1);

        // dirty check에 의한 UPDATE 발생
        assertThat(entityUpdateCount)
                .as("dirty check에 의한 UPDATE 쿼리가 실행되어야 한다")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("@Version 기반 낙관적 락 충돌 시 OptimisticLockingFailureException이 발생한다")
    void save_versionConflict_throwsOptimisticLockingFailureException() {
        // given: 신규 주문 저장
        Order savedOrder = transactionTemplate.execute(status -> orderRepository.save(createNewOrder()));
        assertThat(savedOrder).isNotNull();

        // 첫 번째 업데이트 성공 (version 0 -> 1)
        Order firstUpdate = Order.reconstitute(
                savedOrder.getOrderId(),
                savedOrder.getUserId(),
                savedOrder.getItems(),
                savedOrder.getStatus(),
                savedOrder.getTotalPrice(),
                savedOrder.getShippingAddress(),
                savedOrder.getCreatedAt(),
                savedOrder.getUpdatedAt(),
                savedOrder.getPaymentId(),
                savedOrder.getPaidAt(),
                savedOrder.getRefundedAt(),
                savedOrder.getVersion()
        );
        firstUpdate.confirm(Clock.systemUTC());
        transactionTemplate.executeWithoutResult(status -> orderRepository.save(firstUpdate));

        // 두 번째 업데이트: 이전 version(stale)으로 시도 -> 충돌 발생
        Order staleUpdate = Order.reconstitute(
                savedOrder.getOrderId(),
                savedOrder.getUserId(),
                savedOrder.getItems(),
                OrderStatus.CANCELLED,
                savedOrder.getTotalPrice(),
                savedOrder.getShippingAddress(),
                savedOrder.getCreatedAt(),
                savedOrder.getUpdatedAt(),
                savedOrder.getPaymentId(),
                savedOrder.getPaidAt(),
                savedOrder.getRefundedAt(),
                savedOrder.getVersion() // stale version (이미 1로 증가됨)
        );

        // when & then: stale version으로 업데이트 시도 시 OptimisticLockingFailureException 발생
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    orderRepository.save(staleUpdate);
                    entityManager.flush();
                })
        ).isInstanceOf(JpaOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("saveAll — N건의 기존 주문 배치 업데이트 시 findAllById 1회 SELECT로 처리된다")
    void saveAll_multipleExistingOrders_executesOneSelectAndBatchUpdate() {
        // given: N건의 신규 주문을 먼저 저장
        int orderCount = 5;
        String userId = "withdrawal-user-" + UUID.randomUUID();

        List<Order> savedOrders = transactionTemplate.execute(status -> {
            List<Order> newOrders = new java.util.ArrayList<>();
            for (int i = 0; i < orderCount; i++) {
                Order order = createNewOrderForUser(userId);
                newOrders.add(orderRepository.save(order));
            }
            return newOrders;
        });

        assertThat(savedOrders).hasSize(orderCount);
        savedOrders.forEach(o -> assertThat(o.getVersion()).isNotNull());

        // 도메인 모델에서 모든 주문을 CANCELLED로 변경 (회원 탈퇴 시나리오)
        List<Order> ordersToCancel = savedOrders.stream()
                .map(saved -> {
                    Order order = Order.reconstitute(
                            saved.getOrderId(), saved.getUserId(), saved.getItems(),
                            saved.getStatus(), saved.getTotalPrice(), saved.getShippingAddress(),
                            saved.getCreatedAt(), saved.getUpdatedAt(),
                            saved.getPaymentId(), saved.getPaidAt(),
                            saved.getRefundedAt(), saved.getVersion()
                    );
                    order.cancel(Clock.systemUTC());
                    return order;
                })
                .toList();

        // when: saveAll로 배치 업데이트 실행 및 Statistics 측정
        List<Order> updatedOrders = transactionTemplate.execute(status -> {
            statistics.clear();
            List<Order> result = orderRepository.saveAll(ordersToCancel);
            entityManager.flush();
            return result;
        });

        // then: 모든 주문이 CANCELLED 상태로 업데이트됨
        assertThat(updatedOrders).hasSize(orderCount);
        updatedOrders.forEach(o ->
                assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED));

        // findAllById에 의한 SELECT가 주문 수(N)보다 적어야 한다 (N+1 방지 검증)
        // entityLoadCount는 주문 엔티티 + 관련 아이템 엔티티 로드 수를 포함하므로
        // 쿼리 준비 횟수(queryExecutionCount)로 검증: findAllById 1회 + saveAll flush
        long queryExecutionCount = statistics.getQueryExecutionCount();
        assertThat(queryExecutionCount)
                .as("findAllById 1회 SELECT로 N건을 조회해야 한다 (N+1 방지)")
                .isLessThan(orderCount);

        // DB에서 다시 읽어 실제 상태 확인
        for (Order updated : updatedOrders) {
            Order fromDb = transactionTemplate.execute(status ->
                    orderRepository.findById(updated.getOrderId()).orElseThrow());
            assertThat(fromDb.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Test
    @DisplayName("saveAll — 빈 리스트 입력 시 빈 리스트를 반환한다")
    void saveAll_emptyList_returnsEmptyList() {
        // when
        List<Order> result = transactionTemplate.execute(status ->
                orderRepository.saveAll(List.of()));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("saveAll — 전부 신규 주문(version == null)인 경우 findAllById 호출 없이 INSERT만 실행된다")
    void saveAll_allNewOrders_executesInsertOnly() {
        // given
        List<Order> newOrders = List.of(createNewOrder(), createNewOrder(), createNewOrder());

        // when
        List<Order> saved = transactionTemplate.execute(status -> {
            statistics.clear();
            List<Order> result = orderRepository.saveAll(newOrders);
            entityManager.flush();
            return result;
        });

        // then
        assertThat(saved).hasSize(3);
        saved.forEach(o -> {
            assertThat(o.getVersion()).isNotNull();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        });

        // 신규 주문만 있으므로 entity load(SELECT)가 발생하지 않아야 한다
        long entityLoadCount = statistics.getEntityLoadCount();
        assertThat(entityLoadCount)
                .as("신규 주문만 있을 때 entity load(SELECT)가 발생하지 않아야 한다")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("saveAll — DB에 존재하지 않는 주문 ID로 업데이트 시도 시 IllegalStateException이 발생한다")
    void saveAll_nonExistentOrderId_throwsIllegalStateException() {
        // given: version이 있지만 DB에 존재하지 않는 주문
        Order nonExistent = Order.reconstitute(
                "non-existent-" + UUID.randomUUID(), "user-1", List.of(),
                OrderStatus.CANCELLED, 0L,
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", null),
                java.time.Instant.now(), java.time.Instant.now(),
                null, null, null, 0L
        );

        // when & then
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status ->
                        orderRepository.saveAll(List.of(nonExistent)))
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found for update");
    }

    /**
     * 테스트용 신규 주문 생성 헬퍼.
     * 매 호출마다 고유한 userId와 orderId를 사용하여 테스트 격리를 보장한다.
     */
    private Order createNewOrder() {
        String userId = "test-user-" + UUID.randomUUID();
        ShippingAddress address = new ShippingAddress(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구", null
        );
        List<OrderItemData> items = List.of(
                new OrderItemData("product-1", "variant-1", "테스트 상품", "옵션A", 2, 10000)
        );
        return Order.create(userId, items, address, Clock.systemUTC());
    }

    /**
     * 특정 userId로 신규 주문을 생성하는 헬퍼.
     * 회원 탈퇴 배치 테스트에서 동일 사용자의 여러 주문을 생성할 때 사용한다.
     */
    private Order createNewOrderForUser(String userId) {
        ShippingAddress address = new ShippingAddress(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구", null
        );
        List<OrderItemData> items = List.of(
                new OrderItemData("product-1", "variant-1", "테스트 상품", "옵션A", 1, 15000)
        );
        return Order.create(userId, items, address, Clock.systemUTC());
    }
}
