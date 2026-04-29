package com.candle.api;

import com.candle.api.repository.TickRepository;
import com.candle.common.config.KafkaTopics;
import com.candle.common.model.BidAskEvent;
import com.candle.common.model.Candle;

import java.math.BigDecimal;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-application integration test: HTTP layer + Kafka consumer wiring.
 *
 * Uses @EmbeddedKafka (no external broker) and @MockBean TickRepository
 * (no PostgreSQL/Docker needed). The test profile (application-test.yml)
 * wires an H2 datasource so the Spring context loads cleanly.
 *
 * For real TimescaleDB SQL validation see TickRepositoryIntegrationTest.
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
    "spring.kafka.consumer.group-id=it-candle-api-group",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext
@Tag("integration")
class HistoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TickRepository tickRepository;

    @Autowired
    private KafkaTemplate<String, BidAskEvent> testKafkaTemplate;

    // ── Health endpoint ───────────────────────────────────────────────────────

    @Test
    void actuatorHealth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── History endpoint ──────────────────────────────────────────────────────

    @Test
    void historyEndpoint_withMockedNoData_returnsNoDataResponse() throws Exception {
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong()))
            .thenReturn(List.of());

        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000600"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.s").value("no_data"));
    }

    @Test
    void historyEndpoint_withMockedCandles_returnsOkResponse() throws Exception {
        var candles = List.of(
            new Candle(1620000000L, "BTC-USD", "1m",
                new BigDecimal("29500"), new BigDecimal("29600"),
                new BigDecimal("29400"), new BigDecimal("29550"), 5L)
        );
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong()))
            .thenReturn(candles);

        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000600"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.s").value("ok"))
            .andExpect(jsonPath("$.t[0]").value(1620000000))
            .andExpect(jsonPath("$.o[0]").value(29500.0))
            .andExpect(jsonPath("$.h[0]").value(29600.0))
            .andExpect(jsonPath("$.l[0]").value(29400.0))
            .andExpect(jsonPath("$.c[0]").value(29550.0))
            .andExpect(jsonPath("$.v[0]").value(5));
    }

    @Test
    void historyEndpoint_invalidInterval_returns400WithErrorBody() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "INVALID")
                .param("from",     "1620000000")
                .param("to",       "1620000600"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.s").value("error"))
            .andExpect(jsonPath("$.errmsg").exists());
    }

    @Test
    void historyEndpoint_missingSymbol_returns400() throws Exception {
        mockMvc.perform(get("/history")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000600"))
            .andExpect(status().isBadRequest());
    }

    // ── Kafka consumer wiring ────────────────────────────────────────────────

    @Test
    void kafkaConsumer_receivesEvent_forwardsToRepository() {
        var event = new BidAskEvent("BTC-USD", new BigDecimal("29000"), new BigDecimal("29100"), System.currentTimeMillis());

        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "BTC-USD", event);

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(tickRepository, atLeastOnce()).insertTick(any(BidAskEvent.class)));
    }

    @Test
    void kafkaConsumer_multipleEvents_allForwardedToRepository() {
        long now = System.currentTimeMillis();
        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "BTC-USD",
            new BidAskEvent("BTC-USD", new BigDecimal("29000"), new BigDecimal("29100"), now));
        testKafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, "ETH-USD",
            new BidAskEvent("ETH-USD", new BigDecimal("3490"), new BigDecimal("3510"), now));

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                verify(tickRepository, atLeast(2)).insertTick(any(BidAskEvent.class)));
    }

    // ── Test-only Kafka producer ──────────────────────────────────────────────

    @TestConfiguration
    static class TestKafkaProducerConfig {

        @Bean
        public ProducerFactory<String, BidAskEvent> testProducerFactory(
                org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker) {

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
                ProducerFactory<String, BidAskEvent> testProducerFactory) {
            return new KafkaTemplate<>(testProducerFactory);
        }
    }
}
