# Architecture Diagrams

이 문서는 시스템의 전체 아키텍처를 Mermaid 다이어그램으로 시각화합니다.

---

## 1. System Overview

전체 서비스 토폴로지와 통신 흐름입니다.

```mermaid
graph TB
    subgraph Clients
        WS[Web Store<br/>Next.js 15 :3000]
        AD[Admin Dashboard<br/>Next.js 15 :3001]
    end

    subgraph Gateway Layer
        GW[Gateway Service<br/>Spring Cloud Gateway :8080<br/>JWT Auth / Rate Limit]
    end

    subgraph Core Services
        AUTH[Auth Service<br/>Layered :8081]
        PRODUCT[Product Service<br/>DDD :8082]
        USER[User Service<br/>Layered :8084]
        ORDER[Order Service<br/>DDD :8086]
        PROMOTION[Promotion Service<br/>DDD :8092]
    end

    subgraph Integration Services
        PAYMENT[Payment Service<br/>Hexagonal :8087]
        SEARCH[Search Service<br/>Hexagonal :8085]
        NOTIFICATION[Notification Service<br/>Hexagonal :8093]
        SHIPPING[Shipping Service<br/>Layered :8090]
    end

    subgraph Support Services
        REVIEW[Review Service<br/>Layered :8091]
        BATCH[Batch Worker<br/>Layered :8088]
    end

    subgraph Infrastructure
        KAFKA[Apache Kafka 3.7]
        REDIS[Redis 7]
        ES[Elasticsearch 8.15]
    end

    subgraph Databases
        DB_AUTH[(auth_db)]
        DB_PRODUCT[(product_db)]
        DB_ORDER[(order_db)]
        DB_PAYMENT[(payment_db)]
        DB_USER[(user_db)]
        DB_SHIPPING[(shipping_db)]
        DB_REVIEW[(review_db)]
        DB_PROMOTION[(promotion_db)]
        DB_NOTIFICATION[(notification_db)]
        DB_BATCH[(batch_db)]
    end

    WS --> GW
    AD --> GW
    GW --> AUTH
    GW --> PRODUCT
    GW --> USER
    GW --> ORDER
    GW --> PAYMENT
    GW --> SEARCH
    GW --> SHIPPING
    GW --> REVIEW
    GW --> PROMOTION
    GW --> NOTIFICATION

    AUTH --> DB_AUTH
    AUTH --> REDIS
    PRODUCT --> DB_PRODUCT
    ORDER --> DB_ORDER
    PAYMENT --> DB_PAYMENT
    USER --> DB_USER
    SHIPPING --> DB_SHIPPING
    REVIEW --> DB_REVIEW
    PROMOTION --> DB_PROMOTION
    NOTIFICATION --> DB_NOTIFICATION
    BATCH --> DB_BATCH
    GW --> REDIS
    SEARCH --> ES

    ORDER --> KAFKA
    PAYMENT --> KAFKA
    PRODUCT --> KAFKA
    SHIPPING --> KAFKA
    PROMOTION --> KAFKA
    AUTH --> KAFKA
    KAFKA --> NOTIFICATION
    KAFKA --> SEARCH
    KAFKA --> BATCH

    PAYMENT -. Toss Payments .-> EXT_PG[External PG]
    NOTIFICATION -. Email/SMS/Push .-> EXT_MSG[External Channels]
```

---

## 2. Service Architecture Patterns

서비스 복잡도에 따라 3가지 아키텍처 패턴을 선택 적용합니다.

```mermaid
graph LR
    subgraph "DDD (복잡한 도메인)"
        DDD_API[API Layer] --> DDD_APP[Application Layer]
        DDD_APP --> DDD_DOMAIN[Domain Layer<br/>Aggregate / Entity<br/>Domain Event / Repository]
        DDD_DOMAIN --> DDD_INFRA[Infrastructure Layer]
    end

    subgraph "Hexagonal (외부 연동 중심)"
        HEX_IN[Inbound Port] --> HEX_CORE[Core Domain]
        HEX_CORE --> HEX_OUT[Outbound Port]
        HEX_ADAPTER_IN[Web Adapter] --> HEX_IN
        HEX_OUT --> HEX_ADAPTER_OUT[External Adapter]
    end

    subgraph "Layered (단순 CRUD)"
        LAY_CTRL[Controller] --> LAY_SVC[Service]
        LAY_SVC --> LAY_REPO[Repository]
        LAY_REPO --> LAY_DB[(Database)]
    end
```

### 패턴 적용 매핑

```mermaid
graph TB
    subgraph DDD Style
        ORDER_S[Order Service<br/>주문 라이프사이클, Saga]
        PRODUCT_S[Product Service<br/>상품 Aggregate, 재고]
        PROMOTION_S[Promotion Service<br/>프로모션 규칙, 쿠폰]
    end

    subgraph Hexagonal Style
        PAYMENT_S[Payment Service<br/>PG 연동 격리]
        SEARCH_S[Search Service<br/>ES 추상화]
        NOTIFICATION_S[Notification Service<br/>멀티채널 격리]
    end

    subgraph Layered Style
        AUTH_S[Auth Service]
        USER_S[User Service]
        SHIPPING_S[Shipping Service]
        REVIEW_S[Review Service]
        BATCH_S[Batch Worker]
    end

    style DDD Style fill:#e1f5fe
    style Hexagonal Style fill:#f3e5f5
    style Layered Style fill:#e8f5e9
```

---

## 3. Event Flow: Order Saga

주문 생성부터 배송까지의 이벤트 기반 Saga 흐름입니다.

```mermaid
sequenceDiagram
    actor Customer
    participant Order as Order Service
    participant Kafka as Kafka
    participant Payment as Payment Service
    participant Shipping as Shipping Service
    participant Notification as Notification Service

    Customer->>Order: POST /api/orders
    activate Order
    Order->>Order: 주문 생성 + Outbox 저장 (단일 TX)
    Order-->>Kafka: OrderPlaced
    deactivate Order

    Kafka-->>Payment: OrderPlaced
    activate Payment
    Payment->>Payment: 결제 처리 (Toss Payments)
    Payment-->>Kafka: PaymentCompleted
    deactivate Payment

    Kafka-->>Order: PaymentCompleted
    activate Order
    Order->>Order: 주문 확정 (CONFIRMED)
    Order-->>Kafka: OrderConfirmed
    deactivate Order

    Kafka-->>Shipping: OrderConfirmed
    activate Shipping
    Shipping->>Shipping: 배송 시작
    Shipping-->>Kafka: ShippingStatusChanged
    deactivate Shipping

    Kafka-->>Notification: OrderPlaced
    Kafka-->>Notification: PaymentCompleted
    Kafka-->>Notification: ShippingStatusChanged
    Notification->>Customer: 알림 발송 (Email/SMS/Push)
```

---

## 4. Event Flow: Product Search Indexing

상품 변경 이벤트가 검색 인덱스에 반영되는 흐름입니다.

```mermaid
sequenceDiagram
    actor Admin
    participant Product as Product Service
    participant Kafka as Kafka
    participant Search as Search Service
    participant ES as Elasticsearch

    Admin->>Product: POST /api/admin/products
    activate Product
    Product->>Product: 상품 저장 (PostgreSQL)
    Product-->>Kafka: ProductCreated
    deactivate Product

    Kafka-->>Search: ProductCreated
    activate Search
    Search->>ES: Index Document
    deactivate Search

    Admin->>Product: PATCH /api/admin/products/{id}
    activate Product
    Product->>Product: 상품 수정
    Product-->>Kafka: ProductUpdated
    deactivate Product

    Kafka-->>Search: ProductUpdated
    activate Search
    Search->>ES: Update Document
    deactivate Search
```

---

## 5. Infrastructure & Observability

```mermaid
graph TB
    subgraph Application Services
        SVC1[Auth Service]
        SVC2[Product Service]
        SVC3[Order Service]
        SVC_N[... 9 more services]
    end

    subgraph Observability Stack
        PROM[Prometheus<br/>:9090<br/>Metrics Collection]
        GRAFANA[Grafana<br/>:3100<br/>Dashboards]
        LOKI[Loki<br/>:3101<br/>Log Aggregation]
        PROMTAIL[Promtail<br/>Log Collector]
        JAEGER[Jaeger<br/>:16686<br/>Distributed Tracing]
        ALERT[AlertManager<br/>:9094<br/>Alert Routing]
    end

    SVC1 -- "/actuator/prometheus" --> PROM
    SVC2 -- "/actuator/prometheus" --> PROM
    SVC3 -- "/actuator/prometheus" --> PROM
    SVC_N -- "/actuator/prometheus" --> PROM

    SVC1 -- "OTLP traces" --> JAEGER
    SVC2 -- "OTLP traces" --> JAEGER
    SVC3 -- "OTLP traces" --> JAEGER

    PROMTAIL -- "Docker logs" --> LOKI
    PROM --> GRAFANA
    LOKI --> GRAFANA
    JAEGER --> GRAFANA
    PROM --> ALERT
```

---

## 6. Kubernetes Deployment Topology

```mermaid
graph TB
    subgraph "Kubernetes Cluster"
        subgraph "Ingress"
            ING[Ingress Controller<br/>+ Security Headers]
        end

        subgraph "Application Pods"
            GW_POD[Gateway<br/>+ PDB]
            AUTH_POD[Auth<br/>+ PDB]
            PRODUCT_POD[Product<br/>+ PDB]
            ORDER_POD[Order<br/>+ PDB]
            PAYMENT_POD[Payment<br/>+ PDB]
            USER_POD[User<br/>+ PDB]
            SEARCH_POD[Search<br/>+ PDB]
            BATCH_POD[Batch Worker]
            WS_POD[Web Store<br/>+ PDB]
            AD_POD[Admin Dashboard<br/>+ PDB]
        end

        subgraph "Network Policies"
            NP[Default Deny<br/>+ Per-Service Allow Rules]
        end

        subgraph "External Infrastructure"
            EXT_DB[(PostgreSQL × 10)]
            EXT_KAFKA[Kafka]
            EXT_REDIS[Redis]
            EXT_ES[Elasticsearch]
        end
    end

    ING --> GW_POD
    GW_POD --> AUTH_POD
    GW_POD --> PRODUCT_POD
    GW_POD --> ORDER_POD
    GW_POD --> PAYMENT_POD

    AUTH_POD --> EXT_DB
    PRODUCT_POD --> EXT_DB
    ORDER_POD --> EXT_DB
    ORDER_POD --> EXT_KAFKA

    NP -. "Zero Trust" .-> AUTH_POD
    NP -. "Zero Trust" .-> ORDER_POD
```

---

## 7. Database-per-Service

```mermaid
graph LR
    subgraph "Service Boundary"
        A[Auth Service] --> A_DB[(auth_db<br/>:5432)]
        B[Product Service] --> B_DB[(product_db<br/>:5433)]
        C[Order Service] --> C_DB[(order_db<br/>:5434)]
        D[Payment Service] --> D_DB[(payment_db<br/>:5435)]
        E[User Service] --> E_DB[(user_db<br/>:5437)]
        F[Batch Worker] --> F_DB[(batch_db<br/>:5436)]
        G[Shipping Service] --> G_DB[(shipping_db<br/>:5438)]
        H[Review Service] --> H_DB[(review_db<br/>:5439)]
        I[Promotion Service] --> I_DB[(promotion_db<br/>:5440)]
        J[Notification Service] --> J_DB[(notification_db<br/>:5441)]
    end

    K[Search Service] --> K_ES[(Elasticsearch<br/>:9200)]
    L[Gateway Service] --> L_REDIS[(Redis<br/>:6379)]

    style A_DB fill:#336791,color:#fff
    style B_DB fill:#336791,color:#fff
    style C_DB fill:#336791,color:#fff
    style D_DB fill:#336791,color:#fff
    style E_DB fill:#336791,color:#fff
    style F_DB fill:#336791,color:#fff
    style G_DB fill:#336791,color:#fff
    style H_DB fill:#336791,color:#fff
    style I_DB fill:#336791,color:#fff
    style J_DB fill:#336791,color:#fff
    style K_ES fill:#005571,color:#fff
    style L_REDIS fill:#DC382D,color:#fff
```
