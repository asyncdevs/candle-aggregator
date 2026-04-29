package com.candle.logger;

import com.candle.common.config.KafkaTopics;
import com.candle.common.model.BidAskEvent;

import java.math.BigDecimal;
import com.candle.logger.consumer.CandleEventConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the logger-service: full Spring context + embedded Kafka.
 *
 * Verifies that the Kafka listener is wired correctly and that the consumer
 * processes published messages without requiring a real broker or Logstash.
 *
 * @SpyBean wraps the real CandleEventConsumer so Mockito can verify invocations
 * while the actual logging code still executes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(
    partitions = 4,
    topics     = {KafkaTopics.RAW_BIDASK_EVENTS}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=it-logger-group",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@Tag("integration")
class LoggerServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private CandleEventConsumer consumer;

    @Autowired
    private KafkaTemplate<String, BidAskEvent> testKafkaTemplate;

    // ── Health endpoint ───────────────────────────────────────────────────────

    @Test
    void actuatorHealth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── Kafka consumer wiring ─────────────────────────────────────────────────

    @Test
    void consumer_receivesPublishedEvent_callsConsume() {
        var event = new BidAskEvent("BTC-USD", new BigDecimal("29000"), new BigDecimal("29100"), System.currentTimeMillis());

        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "BTC-USD", event);

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(consumer, atLeastOnce()).consume(any(BidAskEvent.class)));
    }

    @Test
    void consumer_receivesMultipleEvents_consumeCalledForEach() {
        long now = System.currentTimeMillis();
        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "BTC-USD",
            new BidAskEvent("BTC-USD", new BigDecimal("29000"), new BigDecimal("29100"), now));
        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "ETH-USD",
            new BidAskEvent("ETH-USD", new BigDecimal("3490"),  new BigDecimal("3510"),  now));
        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "XAU-USD",
            new BidAskEvent("XAU-USD", new BigDecimal("2295"),  new BigDecimal("2305"),  now));

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(consumer, atLeast(3)).consume(any(BidAskEvent.class)));
    }

    @Test
    void consumer_receivedEvent_containsCorrectSymbol() {
        var event = new BidAskEvent("XAG-USD", new BigDecimal("28.45"), new BigDecimal("28.55"), System.currentTimeMillis());

        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "XAG-USD", event);

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(consumer, atLeastOnce()).consume(
                    org.mockito.ArgumentMatchers.argThat(e ->
                        "XAG-USD".equals(e.symbol())
                        && e.bid().compareTo(new BigDecimal("28.45")) == 0
                        && e.ask().compareTo(new BigDecimal("28.55")) == 0)));
    }

    // ── Test-only Kafka producer ──────────────────────────────────────────────

    @TestConfiguration
    static class TestKafkaProducerConfig {

        @Bean
        public ProducerFactory<String, BidAskEvent> loggerTestProducerFactory(
                EmbeddedKafkaBroker embeddedKafkaBroker) {

            Map<String, Object> cfg = new HashMap<>();
            cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                embeddedKafkaBroker.getBrokersAsString());
            cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            cfg.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
            return new DefaultKafkaProducerFactory<>(cfg);
        }

        @Bean
        public KafkaTemplate<String, BidAskEvent> testKafkaTemplate(
                ProducerFactory<String, BidAskEvent> loggerTestProducerFactory) {
            return new KafkaTemplate<>(loggerTestProducerFactory);
        }
    }
}
