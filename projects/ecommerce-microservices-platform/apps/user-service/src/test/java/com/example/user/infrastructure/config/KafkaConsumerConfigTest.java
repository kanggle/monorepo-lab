package com.example.user.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaConsumerConfig 단위 테스트")
class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @Test
    @DisplayName("kafkaErrorHandler 빈이 DefaultErrorHandler 인스턴스를 반환한다")
    void kafkaErrorHandler_returnsDefaultErrorHandler() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        CommonErrorHandler errorHandler = config.kafkaErrorHandler(kafkaTemplate);

        assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    @DisplayName("ExponentialBackOff가 표준 설정(1s base, 2x multiplier, 30s max, 3회)으로 구성된다")
    void kafkaErrorHandler_exponentialBackOffConfigured() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxAttempts(3);

        assertThat(backOff.getInitialInterval()).isEqualTo(1000L);
        assertThat(backOff.getMultiplier()).isEqualTo(2.0);
        assertThat(backOff.getMaxInterval()).isEqualTo(30000L);
        assertThat(backOff.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("JsonProcessingException이 not-retryable 예외로 등록되어 있다")
    void kafkaErrorHandler_jsonProcessingExceptionIsNotRetryable() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler errorHandler = (DefaultErrorHandler) config.kafkaErrorHandler(kafkaTemplate);
        BinaryExceptionClassifier classifier = extractClassifier(errorHandler);

        assertThat(classifier.classify(new JsonProcessingException("test") {})).isFalse();
    }

    @Test
    @DisplayName("Transient 예외(RuntimeException)는 retryable로 분류된다")
    void kafkaErrorHandler_runtimeExceptionIsRetryable() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler errorHandler = (DefaultErrorHandler) config.kafkaErrorHandler(kafkaTemplate);
        BinaryExceptionClassifier classifier = extractClassifier(errorHandler);

        assertThat(classifier.classify(new RuntimeException("transient error"))).isTrue();
    }

    private BinaryExceptionClassifier extractClassifier(DefaultErrorHandler errorHandler) {
        Class<?> clazz = errorHandler.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("classifier");
                field.setAccessible(true);
                return (BinaryExceptionClassifier) field.get(errorHandler);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access classifier field", e);
            }
        }
        throw new RuntimeException("classifier field not found in class hierarchy");
    }
}
