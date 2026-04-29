package com.candle.api.config;

import com.candle.common.model.BidAskEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the candle-api.
 *
 * <h3>Dead-letter queue</h3>
 * A {@link DefaultErrorHandler} with {@link DeadLetterPublishingRecoverer} is
 * attached to the listener container factory. On consume failure:
 * <ol>
 *   <li>The message is retried up to {@value #MAX_ATTEMPTS} times with a
 *       {@value #RETRY_INTERVAL_MS} ms fixed backoff.</li>
 *   <li>If all retries fail, the message is published to
 *       {@code raw-bidask-events.DLT} and the consumer moves on — no tick
 *       is silently dropped and the consumer does not stall.</li>
 * </ol>
 *
 * <h3>Concurrency</h3>
 * concurrency = 4 — one listener thread per Kafka partition.
 * Each thread handles its own symbol set, keeping inserts conflict-free.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final long MAX_ATTEMPTS      = 3L;
    private static final long RETRY_INTERVAL_MS = 1_000L;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, BidAskEvent> bidAskConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new JsonDeserializer<>(BidAskEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BidAskEvent> kafkaListenerContainerFactory(
            KafkaTemplate<String, BidAskEvent> dltKafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(dltKafkaTemplate);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(RETRY_INTERVAL_MS, MAX_ATTEMPTS - 1)
        );

        ConcurrentKafkaListenerContainerFactory<String, BidAskEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bidAskConsumerFactory());
        factory.setConcurrency(4);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
