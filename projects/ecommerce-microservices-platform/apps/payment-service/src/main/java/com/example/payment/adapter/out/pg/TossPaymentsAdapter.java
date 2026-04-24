package com.example.payment.adapter.out.pg;

import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
@Profile("!standalone")
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsAdapter implements PaymentGatewayPort {

    private final RestClient restClient;

    public TossPaymentsAdapter(TossPaymentsProperties properties, RestClient.Builder restClientBuilder) {
        String encodedKey = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + encodedKey)
                .build();
    }

    @Override
    public PaymentGatewayConfirmResult confirmPayment(String paymentKey, String orderId, long amount) {
        log.info("Toss Payments confirm request: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String paymentMethod = response != null && response.has("method")
                    ? response.get("method").asText() : null;
            String receiptUrl = response != null && response.has("receipt") && response.get("receipt").has("url")
                    ? response.get("receipt").get("url").asText() : null;

            log.info("Toss Payments confirm success: paymentKey={}, method={}", paymentKey, paymentMethod);
            return new PaymentGatewayConfirmResult(paymentMethod, receiptUrl);
        } catch (RestClientResponseException e) {
            log.error("Toss Payments confirm failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
    }

    @Override
    public void cancelPayment(String paymentKey, String cancelReason) {
        log.info("Toss Payments cancel request: paymentKey={}, reason={}", paymentKey, cancelReason);

        Map<String, String> body = Map.of("cancelReason", cancelReason);

        try {
            restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss Payments cancel success: paymentKey={}", paymentKey);
        } catch (RestClientResponseException e) {
            log.error("Toss Payments cancel failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("Cancel failed: status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
    }
}
