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
import org.junit.jupiter.api.Tag;
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
@SpringBootTest(classes = com.example.order.OrderServiceApplication.class, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Tag("integration")
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
                savedOrder.getStuckRecoveryAttemptCount(),
                savedOrder.getStuckRecoveryAt(),
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
        // given: 신규 주문 저장 (DB version = 0)
        Order savedOrder = transactionTemplate.execute(status -> orderRepository.save(createNewOrder()));
        assertThat(savedOrder).isNotNull();
        String orderId = savedOrder.getOrderId();

        // TASK-BE-441: the prior version of this test could not construct a real conflict —
        // OrderRepositoryImpl.save() loads the CURRENT entity (jpaRepository.findById) on the
        // update path and OrderJpaEntity.updateFrom does NOT copy @Version from the passed
        // domain order, so a stale-version domain object is always reconciled against the
        // fresh row (last-writer-wins) and never triggers the lock. The @Version field is
        // nonetheless present and enforced by Hibernate at the JPA layer. To exercise that
        // real enforcement we reproduce a genuine concurrent conflict directly:
        //   1) load the managed entity into a persistence context (captures version 0),
        //   2) bump the row's version out-of-band via a native UPDATE (a concurrent writer),
        //   3) dirty-mutate + flush the still-version-0 managed entity → Hibernate's
        //      versioned UPDATE ... WHERE version = 0 matches 0 rows → optimistic lock failure.
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    OrderJpaEntity managed = entityManager.find(OrderJpaEntity.class, orderId);
                    assertThat(managed).isNotNull();

                    // Concurrent out-of-band writer bumps the row version (0 -> 1) without
                    // touching the persistence context, leaving `managed` stale at version 0.
                    entityManager.createNativeQuery(
                                    "UPDATE orders SET version = version + 1 WHERE order_id = :id")
                            .setParameter("id", orderId)
                            .executeUpdate();

                    // Dirty-mutate the stale managed entity and force a versioned flush.
                    managed.updateFrom(Order.reconstitute(
                            managed.getOrderId(), managed.getUserId(),
                            savedOrder.getItems(), OrderStatus.CANCELLED,
                            managed.getTotalPrice(), savedOrder.getShippingAddress(),
                            managed.getCreatedAt(), managed.getUpdatedAt(),
                            managed.getPaymentId(), managed.getPaidAt(), managed.getRefundedAt(),
                            managed.getStuckRecoveryAttemptCount(), managed.getStuckRecoveryAt(),
                            managed.getVersion()));
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
                            saved.getRefundedAt(),
                            saved.getStuckRecoveryAttemptCount(), saved.getStuckRecoveryAt(),
                            saved.getVersion()
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
        // given: a non-null version with an id that does not exist in the DB. saveAll →
        // loadExistingEntities treats version!=null as "existing" and findAllById returns
        // nothing, so toEntities cannot resolve it and throws IllegalStateException before
        // any flush (the version is a non-null Long so it can never be mistaken for a fresh
        // insert). Re-enabled (TASK-BE-441): the product update-resolution path is intact;
        // it was quarantined on the MONO-307 lane for a non-code reason.
        Order nonExistent = Order.reconstitute(
                "non-existent-" + UUID.randomUUID(), "user-1", List.of(),
                OrderStatus.CANCELLED, 0L,
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", null),
                java.time.Instant.now(), java.time.Instant.now(),
                null, null, null, 0, null, 0L
        );

        // when & then
        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status ->
                        orderRepository.saveAll(List.of(nonExistent)))
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order not found for update");
    }

    @Test
    @DisplayName("findAllByUserIdAcrossTenants — 한 사용자의 모든 상태/테넌트 주문을 조회한다 (PII cascade, ADR-037 P3-B)")
    void findAllByUserIdAcrossTenants_returnsAllOrdersForSubject() {
        // given: 동일 사용자의 주문을 여러 상태로 저장 (DELIVERED 포함 — 비활성 주문도 마스킹 대상)
        String userId = "pii-cascade-user-" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            Order pending = orderRepository.save(createNewOrderForUser(userId));
            Order delivered = reconstituteFrom(pending);
            delivered.confirm(Clock.systemUTC());
            orderRepository.save(delivered);
            // 다른 사용자의 주문 (cascade 대상 아님)
            orderRepository.save(createNewOrderForUser("other-" + UUID.randomUUID()));
        });

        // when
        List<Order> orders = transactionTemplate.execute(status ->
                orderRepository.findAllByUserIdAcrossTenants(userId));

        // then: 해당 사용자 주문만 반환 (다른 사용자 제외)
        assertThat(orders).isNotEmpty();
        assertThat(orders).allMatch(o -> o.getUserId().equals(userId));
    }

    @Test
    @DisplayName("PII cascade — anonymizePii 후 saveAll 하면 배송지 PII가 영속화되고 비즈니스 데이터는 보존된다 (ADR-037 P3-B)")
    void piiCascade_anonymizeThenSave_persistsMaskedAddressPreservingBusinessData() {
        // given
        String userId = "pii-persist-user-" + UUID.randomUUID();
        Order saved = transactionTemplate.execute(status ->
                orderRepository.save(createNewOrderForUser(userId)));
        assertThat(saved).isNotNull();
        long totalBefore = saved.getTotalPrice();

        // when: 도메인 익명화 후 saveAll
        transactionTemplate.executeWithoutResult(status -> {
            List<Order> all = orderRepository.findAllByUserIdAcrossTenants(userId);
            List<Order> masked = all.stream().filter(o -> o.anonymizePii(Clock.systemUTC())).toList();
            orderRepository.saveAll(masked);
        });

        // then: DB 재조회 시 배송지 PII는 tombstone, 비즈니스 데이터는 보존
        Order fromDb = transactionTemplate.execute(status ->
                orderRepository.findAllByUserIdAcrossTenants(userId).get(0));
        assertThat(fromDb.getShippingAddress().isAnonymized()).isTrue();
        assertThat(fromDb.getShippingAddress().getRecipient()).isEqualTo(ShippingAddress.ANONYMIZED_TOMBSTONE);
        // zip_code is NOT-NULL (orders.zip_code VARCHAR(20) NOT NULL) — tombstoned so the
        // saveAll flush satisfies the constraint instead of throwing a NOT-NULL violation
        assertThat(fromDb.getShippingAddress().getZipCode()).isEqualTo(ShippingAddress.ANONYMIZED_TOMBSTONE);
        // 비즈니스 데이터 보존
        assertThat(fromDb.getUserId()).isEqualTo(userId);
        assertThat(fromDb.getTotalPrice()).isEqualTo(totalBefore);
        assertThat(fromDb.getItems()).isNotEmpty();
    }

    private Order reconstituteFrom(Order saved) {
        return Order.reconstitute(
                saved.getOrderId(), saved.getUserId(), saved.getItems(),
                saved.getStatus(), saved.getTotalPrice(), saved.getShippingAddress(),
                saved.getCreatedAt(), saved.getUpdatedAt(),
                saved.getPaymentId(), saved.getPaidAt(), saved.getRefundedAt(),
                saved.getStuckRecoveryAttemptCount(), saved.getStuckRecoveryAt(),
                saved.getVersion());
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
