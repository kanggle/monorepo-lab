package com.example.observability.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

@AutoConfiguration
@ConditionalOnClass({RecordInterceptor.class, ObjectMapper.class})
public class KafkaEventContextAutoConfiguration {

    @Bean
    public EventContextRecordInterceptor eventContextRecordInterceptor(ObjectMapper objectMapper) {
        return new EventContextRecordInterceptor(objectMapper);
    }

    @Bean
    public static BeanPostProcessor kafkaListenerContainerFactoryPostProcessor(
            org.springframework.beans.factory.ObjectProvider<EventContextRecordInterceptor> interceptorProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                    EventContextRecordInterceptor interceptor = interceptorProvider.getIfAvailable();
                    if (interceptor != null) {
                        @SuppressWarnings("unchecked")
                        ConcurrentKafkaListenerContainerFactory<String, String> typed =
                                (ConcurrentKafkaListenerContainerFactory<String, String>) factory;
                        typed.setRecordInterceptor(interceptor);
                    }
                }
                return bean;
            }
        };
    }
}
