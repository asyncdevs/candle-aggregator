package com.candle.api.config;

import com.candle.common.model.BidAskEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for the candle-api.
 *
 * The candle-api is primarily a consumer, but it needs a producer to forward
 * unprocessable messages to the dead-letter topic (raw-bidask-events.DLT).
 * This KafkaTemplate is consumed exclusively by the DeadLetterPublishingRecoverer
 * wired in KafkaConsumerConfig.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, BidAskEvent> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Do not add type headers — keeps the DLT payload format identical to
        // the source topic so consumers can replay without deserialisation changes.
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, BidAskEvent> dltKafkaTemplate(
            ProducerFactory<String, BidAskEvent> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }
}
