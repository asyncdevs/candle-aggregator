package com.candle.logger.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.candle.common.model.BidAskEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pure unit tests for CandleEventConsumer.
 *
 * Uses a Logback ListAppender to capture log events in-process — no Spring
 * context, no Kafka broker, no Logstash connection required.
 */
class CandleEventConsumerTest {

    private CandleEventConsumer consumer;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger consumerLogger;

    @BeforeEach
    void setUp() {
        consumer = new CandleEventConsumer();

        // Attach an in-memory Logback appender to capture log output
        consumerLogger = (Logger) LoggerFactory.getLogger(CandleEventConsumer.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        consumerLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        consumerLogger.detachAppender(listAppender);
        listAppender.stop();
    }

    // ── No exceptions ─────────────────────────────────────────────────────────

    @Test
    void consume_doesNotThrowOnValidEvent() {
        var event = new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L);
        assertThatCode(() -> consumer.consume(event)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "BTC-USD, 64950.0, 65050.0",
        "ETH-USD,  3490.0,  3510.0",
        "XAU-USD,  2295.0,  2305.0",
        "XAG-USD,    28.45,   28.55"
    })
    void consume_doesNotThrow_forAllSymbols(String symbol, String bid, String ask) {
        var event = new BidAskEvent(symbol, new BigDecimal(bid), new BigDecimal(ask), System.currentTimeMillis());
        assertThatCode(() -> consumer.consume(event)).doesNotThrowAnyException();
    }

    // ── Log level and message ────────────────────────────────────────────────

    @Test
    void consume_emitsExactlyOneLogEvent() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        assertThat(listAppender.list).hasSize(1);
    }

    @Test
    void consume_logLevelIsInfo() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void consume_logMessageContainsBidAskTickLabel() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        assertThat(listAppender.list.get(0).getMessage()).contains("[LOGGER] BidAsk tick received");
    }

    // ── Structured arguments contain correct values ───────────────────────────

    @Test
    void consume_structuredArgs_containSymbol() {
        consumer.consume(new BidAskEvent("ETH-USD", bd("3490.0"), bd("3510.0"), 1620000000000L));
        assertThat(allArgsAsString()).contains("symbol=ETH-USD");
    }

    @Test
    void consume_structuredArgs_containBidAndAsk() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        String args = allArgsAsString();
        assertThat(args).contains("bid=29000.0");
        assertThat(args).contains("ask=29100.0");
    }

    @Test
    void consume_structuredArgs_containMidPrice() {
        // mid = (29000.0 + 29100.0) / 2 = 29050.00000000 (scale 8)
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        assertThat(allArgsAsString()).contains("mid_price=29050.00000000");
    }

    @Test
    void consume_structuredArgs_containTimestampMs() {
        long ts = 1620000000000L;
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), ts));
        assertThat(allArgsAsString()).contains("event_ts_ms=" + ts);
    }

    @Test
    void consume_structuredArgs_containIsoTimestamp() {
        long ts = 1620000000000L;
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), ts));
        String expectedIso = Instant.ofEpochMilli(ts).toString();
        assertThat(allArgsAsString()).contains("event_ts_iso=" + expectedIso);
    }

    @Test
    void consume_structuredArgs_containEventType() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        assertThat(allArgsAsString()).contains("event_type=bidask_tick");
    }

    @Test
    void consume_structuredArgs_allSevenFieldsPresent() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        // event_type, symbol, bid, ask, mid_price, event_ts_ms, event_ts_iso
        assertThat(listAppender.list.get(0).getArgumentArray()).hasSize(7);
    }

    // ── Repeated calls each produce one log event ────────────────────────────

    @Test
    void consume_calledMultipleTimes_emitsOneLogPerCall() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        consumer.consume(new BidAskEvent("ETH-USD", bd("3490.0"),  bd("3510.0"),  1620000001000L));
        consumer.consume(new BidAskEvent("XAU-USD", bd("2295.0"),  bd("2305.0"),  1620000002000L));
        assertThat(listAppender.list).hasSize(3);
    }

    @Test
    void consume_differentSymbols_logsCorrectSymbolEachTime() {
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), 1620000000000L));
        consumer.consume(new BidAskEvent("ETH-USD", bd("3490.0"),  bd("3510.0"),  1620000001000L));

        String firstArgs  = argsAsString(listAppender.list.get(0));
        String secondArgs = argsAsString(listAppender.list.get(1));

        assertThat(firstArgs).contains("symbol=BTC-USD");
        assertThat(secondArgs).contains("symbol=ETH-USD");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void consume_zeroPrices_doesNotThrow() {
        assertThatCode(() ->
            consumer.consume(new BidAskEvent("BTC-USD", BigDecimal.ZERO, BigDecimal.ZERO, 0L))
        ).doesNotThrowAnyException();
    }

    @Test
    void consume_veryRecentTimestamp_isoFormatIsValid() {
        long now = System.currentTimeMillis();
        consumer.consume(new BidAskEvent("BTC-USD", bd("29000.0"), bd("29100.0"), now));

        String args = allArgsAsString();
        // ISO-8601 format ends in 'Z'
        assertThat(args).containsPattern("event_ts_iso=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }

    private String allArgsAsString() {
        return argsAsString(listAppender.list.get(0));
    }

    private String argsAsString(ILoggingEvent event) {
        return Arrays.stream(event.getArgumentArray())
            .map(Object::toString)
            .collect(Collectors.joining(", "));
    }
}
