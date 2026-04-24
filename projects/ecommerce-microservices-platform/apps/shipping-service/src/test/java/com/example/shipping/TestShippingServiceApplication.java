package com.example.shipping;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WebMvcTest 슬라이스 테스트용 경량 부트스트랩 클래스.
 * ShippingServiceApplication의 @EnableJpaRepositories와 @EntityScan이
 * @WebMvcTest 컨텍스트에서 entityManagerFactory를 요구하는 문제를 우회한다.
 */
@SpringBootApplication
public class TestShippingServiceApplication {
}
