package com.lisovskyi.core_service.registry_event.config;

import com.lisovskyi.core_service.registry_event.dto.RegistryDocumentFoundEvent;
import org.apache.kafka.common.KafkaException;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import static com.lisovskyi.core_service.registry_event.RegistryEventConstants.MAX_RETRY_ATTEMPTS;
import static com.lisovskyi.core_service.registry_event.RegistryEventConstants.RETRY_BACKOFF_MS;

/**
 * "Plain" налаштування (bootstrap-servers, group-id, (де)серіалізатори, offset-reset,
 * ack-mode) живуть у application.yaml (spring.kafka.*) - Boot сам будує з них
 * ConsumerFactory/ProducerFactory і базовий ConcurrentKafkaListenerContainerFactoryConfigurer.
 * Тут лишається виключно те, що властивостями не виразити - програмна DLQ-логіка.
 */
@Configuration
public class RegistryKafkaConsumerConfig {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RegistryDocumentFoundEvent> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory,
            CommonErrorHandler registryEventErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, RegistryDocumentFoundEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        // Застосовує все, що вже налаштоване через spring.kafka.* в application.yaml
        // (deserializers, group-id, ack-mode тощо) - той самий шлях, яким Boot сам
        // збирає свій автоконфігурований фабричний бін, просто без дублювання коду.
        // Raw-type cast навмисно: сигнатура ConcurrentKafkaListenerContainerFactoryConfigurer
        // фіксована під <Object, Object>, а generics в Java інваріантні - без цього
        // приведення взагалі не компілюється, хоч типи сумісні (стирання типів на рівні
        // байткоду робить це безпечним рантайм-ефектом).
        configurer.configure((ConcurrentKafkaListenerContainerFactory) factory, kafkaConsumerFactory);
        factory.setCommonErrorHandler(registryEventErrorHandler);
        return factory;
    }

    @Bean
    public CommonErrorHandler registryEventErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        // Непередбачена помилка обробки (не дублікат - той case ловиться в самому
        // RegistryEventListener) -> MAX_RETRY_ATTEMPTS спроб із паузою RETRY_BACKOFF_MS,
        // після чого повідомлення йде в <топік>.DLT (дефолтна конвенція
        // DeadLetterPublishingRecoverer) замість того, щоб блокувати партицію назавжди.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
        FixedBackOff backOff = new FixedBackOff(RETRY_BACKOFF_MS, MAX_RETRY_ATTEMPTS - 1L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Помилки серіалізації (ErrorHandlingDeserializer перехоплює їх сам) не має сенсу
        // повторювати - зіпсоване повідомлення однаково не прочитається інакше наступного
        // разу, тому одразу в DLQ, без витрачання MAX_RETRY_ATTEMPTS спроб.
        errorHandler.addNotRetryableExceptions(KafkaException.class);
        return errorHandler;
    }
}
