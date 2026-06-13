package com.example.settlement;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lightweight bootstrap for {@code @WebMvcTest} slice tests — avoids the
 * {@code SettlementServiceApplication} {@code @EnableJpaRepositories}/{@code @EntityScan}
 * requiring an entityManagerFactory in a web slice context (mirrors
 * {@code TestOrderServiceApplication}).
 */
@SpringBootApplication
public class TestSettlementServiceApplication {
}
