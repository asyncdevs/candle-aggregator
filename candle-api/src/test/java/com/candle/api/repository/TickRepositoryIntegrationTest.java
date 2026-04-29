package com.candle.api.repository;

import com.candle.common.config.CandleInterval;
import com.candle.common.model.BidAskEvent;
import com.candle.common.model.Candle;
import org.junit.jupiter.api.BeforeAll;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TickRepository against a real TimescaleDB instance.
 *
 * Requires Docker. Run with: mvn verify -Dgroups=integration
 * Excluded from the default unit-test phase (mvn test).
 *
 * Uses a standalone JdbcTemplate — no Spring context needed.
 */
@Testcontainers
@Tag("integration")
class TickRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> timescaledb =
        new PostgreSQLContainer<>(
            DockerImageName.parse("timescale/timescaledb:latest-pg15")
                .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("candles")
            .withUsername("candle")
            .withPassword("candle");

    private static JdbcTemplate jdbcTemplate;
    private TickRepository tickRepository;

    @BeforeAll
    static void initSchema() {
        var ds = DataSourceBuilder.create()
            .url(timescaledb.getJdbcUrl())
            .username(timescaledb.getUsername())
            .password(timescaledb.getPassword())
            .driverClassName("org.postgresql.Driver")
            .build();

        jdbcTemplate = new JdbcTemplate(ds);

        // Enable the TimescaleDB extension (pre-loaded in shared_preload_libraries by the image)
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS timescaledb");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ticks (
                time      TIMESTAMPTZ   NOT NULL,
                symbol    TEXT          NOT NULL,
                bid       NUMERIC(19,8) NOT NULL,
                ask       NUMERIC(19,8) NOT NULL,
                mid_price NUMERIC(19,8) NOT NULL
            )
            """);

        jdbcTemplate.execute(
            "SELECT create_hypertable('ticks', 'time', if_not_exists => TRUE)");

        jdbcTemplate.execute(
            "CREATE INDEX IF NOT EXISTS idx_ticks_symbol_time ON ticks (symbol, time DESC)");
    }

    @BeforeEach
    void resetData() {
        jdbcTemplate.execute("TRUNCATE ticks");
        tickRepository = new TickRepository(jdbcTemplate);
    }

    // ── insertTick ────────────────────────────────────────────────────────────

    @Test
    void insertTick_persistsExactlyOneRow() {
        tickRepository.insertTick(btcEvent(1620000000000L, "29000", "29100"));

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticks", Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void insertTick_storesMidPriceAsAverageOfBidAndAsk() {
        tickRepository.insertTick(btcEvent(1620000000000L, "29000", "29100"));

        BigDecimal mid = jdbcTemplate.queryForObject("SELECT mid_price FROM ticks", BigDecimal.class);
        assertThat(mid).isEqualByComparingTo(new BigDecimal("29050"));
    }

    @Test
    void insertTick_storesBidAndAskSeparately() {
        tickRepository.insertTick(new BidAskEvent("ETH-USD", bd("3490"), bd("3510"), 1620000001000L));

        var row = jdbcTemplate.queryForMap("SELECT bid, ask, symbol FROM ticks");
        assertThat((BigDecimal) row.get("bid")).isEqualByComparingTo(bd("3490"));
        assertThat((BigDecimal) row.get("ask")).isEqualByComparingTo(bd("3510"));
        assertThat((String) row.get("symbol")).isEqualTo("ETH-USD");
    }

    @Test
    void insertTick_preservesMillisecondTimestampPrecision() {
        // Timestamp at 500ms — should be stored with sub-second precision
        tickRepository.insertTick(btcEvent(1620000000500L, "29000", "29100"));

        // Verify the stored time is rounded to within 1 second of the input
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ticks WHERE time >= to_timestamp(1620000000) AND time < to_timestamp(1620000001)",
            Long.class);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void insertTick_multipleEvents_allPersisted() {
        tickRepository.insertTick(btcEvent(1620000000000L, "29000", "29100"));
        tickRepository.insertTick(new BidAskEvent("ETH-USD", bd("3490"),  bd("3510"),  1620000001000L));
        tickRepository.insertTick(new BidAskEvent("XAU-USD", bd("2295"),  bd("2305"),  1620000002000L));
        tickRepository.insertTick(new BidAskEvent("XAG-USD", bd("28.45"), bd("28.55"), 1620000003000L));

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticks", Long.class);
        assertThat(count).isEqualTo(4L);
    }

    // ── queryCandles — empty results ──────────────────────────────────────────

    @Test
    void queryCandles_noTicks_returnsEmptyList() {
        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);
        assertThat(candles).isEmpty();
    }

    @Test
    void queryCandles_wrongSymbol_returnsEmptyList() {
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100"));

        var candles = tickRepository.queryCandles(
            "ETH-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);
        assertThat(candles).isEmpty();
    }

    // ── queryCandles — aggregation ────────────────────────────────────────────

    @Test
    void queryCandles_singleTick_returnsSingleCandleWithFlatOHLC() {
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100")); // mid = 29050

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).hasSize(1);
        Candle c = candles.getFirst();
        assertThat(c.symbol()).isEqualTo("BTC-USD");
        assertThat(c.interval()).isEqualTo("1m");
        assertThat(c.open()).isEqualByComparingTo(c.close()); // single tick
        assertThat(c.high()).isEqualByComparingTo(c.low());   // single tick
        assertThat(c.volume()).isEqualTo(1L);
    }

    @Test
    void queryCandles_twoTicksSameWindow_correctOHLCV() {
        // mid prices: 29050 then 29250
        tickRepository.insertTick(btcEvent(1620000010000L, "29000", "29100")); // mid=29050, first
        tickRepository.insertTick(btcEvent(1620000040000L, "29200", "29300")); // mid=29250, last

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).hasSize(1);
        Candle c = candles.getFirst();
        assertThat(c.open()).isEqualByComparingTo(bd("29050"));  // first mid price
        assertThat(c.close()).isEqualByComparingTo(bd("29250")); // last mid price
        assertThat(c.high()).isEqualByComparingTo(bd("29250"));  // max mid price
        assertThat(c.low()).isEqualByComparingTo(bd("29050"));   // min mid price
        assertThat(c.volume()).isEqualTo(2L);
    }

    @Test
    void queryCandles_threeTicksSameWindow_highAndLowCorrect() {
        tickRepository.insertTick(btcEvent(1620000010000L, "29100", "29200")); // mid=29150
        tickRepository.insertTick(btcEvent(1620000020000L, "28900", "29000")); // mid=28950 ← lowest
        tickRepository.insertTick(btcEvent(1620000050000L, "29300", "29400")); // mid=29350 ← highest

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).hasSize(1);
        Candle c = candles.getFirst();
        assertThat(c.open()).isEqualByComparingTo(bd("29150"));  // first tick
        assertThat(c.close()).isEqualByComparingTo(bd("29350")); // last tick
        assertThat(c.high()).isEqualByComparingTo(bd("29350"));
        assertThat(c.low()).isEqualByComparingTo(bd("28950"));
        assertThat(c.volume()).isEqualTo(3L);
    }

    // ── queryCandles — multiple windows ──────────────────────────────────────

    @Test
    void queryCandles_ticksInDifferentWindows_returnsMultipleCandles() {
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100")); // minute 0
        tickRepository.insertTick(btcEvent(1620000090000L, "29200", "29300")); // minute 1

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000120L);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).time()).isEqualTo(1620000000L);
        assertThat(candles.get(1).time()).isEqualTo(1620000060L);
    }

    @Test
    void queryCandles_fiveSecondInterval_correctBuckets() {
        // ticks at t=0s, t=3s → bucket [0,5), tick at t=7s → bucket [5,10)
        tickRepository.insertTick(btcEvent(1620000000000L, "29000", "29100"));
        tickRepository.insertTick(btcEvent(1620000003000L, "29100", "29200"));
        tickRepository.insertTick(btcEvent(1620000007000L, "29200", "29300"));

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.FIVE_SECONDS, 1620000000L, 1620000010L);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).time()).isEqualTo(1620000000L);
        assertThat(candles.get(0).volume()).isEqualTo(2L);
        assertThat(candles.get(1).time()).isEqualTo(1620000005L);
        assertThat(candles.get(1).volume()).isEqualTo(1L);
    }

    // ── queryCandles — time boundary behaviour ────────────────────────────────

    @Test
    void queryCandles_fromBoundary_inclusive() {
        tickRepository.insertTick(btcEvent(1620000000000L, "29000", "29100")); // exactly at 'from'

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).hasSize(1);
    }

    @Test
    void queryCandles_toBoundary_exclusive() {
        tickRepository.insertTick(btcEvent(1620000060000L, "29000", "29100")); // exactly at 'to'

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).isEmpty();
    }

    @Test
    void queryCandles_tickBeforeFrom_excluded() {
        tickRepository.insertTick(btcEvent(1619999999000L, "29000", "29100")); // before range
        tickRepository.insertTick(btcEvent(1620000030000L, "29100", "29200")); // in range

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().volume()).isEqualTo(1L);
    }

    @Test
    void queryCandles_tickAfterTo_excluded() {
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100")); // in range
        tickRepository.insertTick(btcEvent(1620000600000L, "29100", "29200")); // after range

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(candles).hasSize(1);
    }

    // ── queryCandles — symbol isolation ──────────────────────────────────────

    @Test
    void queryCandles_filtersBySymbol_doesNotCrossContaminate() {
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100"));
        tickRepository.insertTick(new BidAskEvent("ETH-USD", bd("3490"), bd("3510"), 1620000030000L));

        var btc = tickRepository.queryCandles("BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);
        var eth = tickRepository.queryCandles("ETH-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000060L);

        assertThat(btc).hasSize(1);
        assertThat(btc.getFirst().symbol()).isEqualTo("BTC-USD");

        assertThat(eth).hasSize(1);
        assertThat(eth.getFirst().symbol()).isEqualTo("ETH-USD");
    }

    // ── queryCandles — ordering ───────────────────────────────────────────────

    @Test
    void queryCandles_sortedAscendingByBucketTime() {
        tickRepository.insertTick(btcEvent(1620000120000L, "29300", "29400")); // minute 2 — inserted first
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100")); // minute 0
        tickRepository.insertTick(btcEvent(1620000090000L, "29150", "29250")); // minute 1

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000180L);

        assertThat(candles).hasSize(3);
        assertThat(candles.get(0).time()).isLessThan(candles.get(1).time());
        assertThat(candles.get(1).time()).isLessThan(candles.get(2).time());
    }

    // ── queryCandles — candle fields ──────────────────────────────────────────

    @Test
    void queryCandles_candleHasCorrectIntervalLabel() {
        tickRepository.insertTick(btcEvent(1620000030000L, "29000", "29100"));

        var candles = tickRepository.queryCandles(
            "BTC-USD", CandleInterval.FIFTEEN_MIN, 1620000000L, 1620000900L);

        assertThat(candles).hasSize(1);
        assertThat(candles.getFirst().interval()).isEqualTo("15m");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static BidAskEvent btcEvent(long timestampMs, String bid, String ask) {
        return new BidAskEvent("BTC-USD", bd(bid), bd(ask), timestampMs);
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
