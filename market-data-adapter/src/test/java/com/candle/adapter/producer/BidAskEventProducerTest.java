package com.candle.adapter.producer;

import com.candle.adapter.simulator.PriceSimulator;
import com.candle.common.config.KafkaTopics;
import com.candle.common.config.Symbols;
import com.candle.common.model.BidAskEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidAskEventProducerTest {

    @Mock
    private KafkaTemplate<String, BidAskEvent> kafkaTemplate;

    @Mock
    private PriceSimulator priceSimulator;

    private BidAskEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new BidAskEventProducer(kafkaTemplate, priceSimulator);
    }

    // ── Publication count ────────────────────────────────────────────────────

    @Test
    void publishTicks_publishesOneEventPerSymbol() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        producer.publishTicks();

        verify(kafkaTemplate, times(Symbols.ALL.size())).send(any(), any(), any());
    }

    @Test
    void publishTicks_noExtraPublications() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        producer.publishTicks();

        verifyNoMoreInteractions(kafkaTemplate);
    }

    // ── Topic and key ────────────────────────────────────────────────────────

    @Test
    void publishTicks_sendsToCorrectTopic() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        producer.publishTicks();

        verify(kafkaTemplate, times(Symbols.ALL.size()))
            .send(eq(KafkaTopics.RAW_BIDASK_EVENTS), any(), any());
    }

    @Test
    void publishTicks_usesSymbolAsPartitionKey() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        producer.publishTicks();

        for (String symbol : Symbols.ALL) {
            verify(kafkaTemplate).send(any(), eq(symbol), any(BidAskEvent.class));
        }
    }

    // ── Event payload ─────────────────────────────────────────────────────────

    @Test
    void publishTicks_btcEvent_hasCorrectBidAsk() {
        when(priceSimulator.nextBidAsk(Symbols.BTC_USD)).thenReturn(new double[]{64950.0, 65050.0});
        when(priceSimulator.nextBidAsk(Symbols.ETH_USD)).thenReturn(new double[]{3490.0, 3510.0});
        when(priceSimulator.nextBidAsk(Symbols.XAU_USD)).thenReturn(new double[]{2295.0, 2305.0});
        when(priceSimulator.nextBidAsk(Symbols.XAG_USD)).thenReturn(new double[]{28.48, 28.52});
        stubKafkaTemplate();

        producer.publishTicks();

        ArgumentCaptor<BidAskEvent> captor = ArgumentCaptor.forClass(BidAskEvent.class);
        verify(kafkaTemplate).send(any(), eq(Symbols.BTC_USD), captor.capture());

        BidAskEvent btcEvent = captor.getValue();
        assertThat(btcEvent.symbol()).isEqualTo(Symbols.BTC_USD);
        assertThat(btcEvent.bid()).isEqualTo(64950.0);
        assertThat(btcEvent.ask()).isEqualTo(65050.0);
    }

    @Test
    void publishTicks_eventSymbolMatchesPartitionKey() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        producer.publishTicks();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BidAskEvent> eventCaptor = ArgumentCaptor.forClass(BidAskEvent.class);
        verify(kafkaTemplate, times(Symbols.ALL.size()))
            .send(any(), keyCaptor.capture(), eventCaptor.capture());

        List<String> keys = keyCaptor.getAllValues();
        List<BidAskEvent> events = eventCaptor.getAllValues();
        for (int i = 0; i < keys.size(); i++) {
            assertThat(events.get(i).symbol()).isEqualTo(keys.get(i));
        }
    }

    @Test
    void publishTicks_eventTimestampIsRecent() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        long before = System.currentTimeMillis();
        producer.publishTicks();
        long after = System.currentTimeMillis();

        ArgumentCaptor<BidAskEvent> captor = ArgumentCaptor.forClass(BidAskEvent.class);
        verify(kafkaTemplate, atLeastOnce()).send(any(), any(), captor.capture());

        for (BidAskEvent event : captor.getAllValues()) {
            assertThat(event.timestamp())
                .as("timestamp should be within the call window")
                .isBetween(before, after);
        }
    }

    // ── Simulator delegation ──────────────────────────────────────────────────

    @Test
    void publishTicks_callsSimulatorForEachSymbol() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        stubKafkaTemplate();

        producer.publishTicks();

        for (String symbol : Symbols.ALL) {
            verify(priceSimulator).nextBidAsk(symbol);
        }
    }

    // ── Kafka send failure doesn't propagate ──────────────────────────────────

    @Test
    void publishTicks_kafkaSendFails_doesNotThrow() {
        when(priceSimulator.nextBidAsk(any())).thenReturn(new double[]{100.0, 101.0});
        CompletableFuture<SendResult<String, BidAskEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);

        // publishTicks logs the failure but must not propagate the exception to the scheduler
        producer.publishTicks();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void stubKafkaTemplate() {
        SendResult<String, BidAskEvent> result = mock(SendResult.class);
        when(kafkaTemplate.send(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(result));
    }
}
