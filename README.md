# Candle Aggregation Service

A production-ready microservices platform that ingests a real-time stream of bid/ask market data, aggregates it into OHLCV candlestick format, and exposes a TradingView-compatible history API.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Module Breakdown](#module-breakdown)
3. [Technology Stack](#technology-stack)
4. [Data Flow](#data-flow)
5. [API Reference](#api-reference)
6. [Running the Service](#running-the-service)
7. [Running Tests](#running-tests)
8. [Assumptions and Trade-offs](#assumptions-and-trade-offs)
9. [Bonus Features](#bonus-features)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                     Docker Compose                           │
│                                                              │
│  market-data-adapter ──► Kafka ──► candle-api               │
│       (producer)      (4 partitions)  (consumer + REST API)  │
│                                            │                 │
│                                       TimescaleDB            │
│                                     (ticks hypertable)       │
│                                                              │
│  Kafka ──► logger-service ──► Logstash ──► Elasticsearch     │
│              (archival)                        │             │
│                                            Kibana            │
└──────────────────────────────────────────────────────────────┘
```

The system is split into four independently deployable services communicating via Apache Kafka.

---

## Module Breakdown

### `common` (shared library)

Pure Java JAR — no Spring Boot repackaging. Defines all shared domain types and constants:

| Class | Purpose |
|---|---|
| `BidAskEvent` | Raw tick record (symbol, bid, ask, timestamp in ms). Includes `midPrice()` and `timestampSeconds()` helpers. |
| `Candle` | OHLCV record (time, symbol, interval, open, high, low, close, volume). |
| `CandleInterval` | Enum of supported intervals (1s, 5s, 1m, 15m, 1h). Provides `windowStart()` for timestamp alignment and `toPgInterval()` for SQL. |
| `KafkaTopics` | Central topic name constants. |
| `Symbols` | Supported trading pairs: BTC-USD, ETH-USD, XAU-USD, XAG-USD. |

### `market-data-adapter` (port 8081)

Simulates an exchange tick feed using a random walk price model. Runs on a 500 ms schedule and publishes one `BidAskEvent` per symbol per tick (8 ticks/sec total) to Kafka, partitioned by symbol.

Key classes: `PriceSimulator`, `BidAskEventProducer`

### `candle-api` (port 8082)

The core service. Consumes raw ticks from Kafka and stores them in TimescaleDB. Serves OHLCV candle history through a REST API in TradingView UDF format. Aggregation happens entirely at query time using `time_bucket()` — no pre-computation.

Key classes: `BidAskEventConsumer`, `TickRepository`, `CandleQueryService`, `HistoryController`

### `logger-service` (port 8083)

Independent Kafka consumer (group `logger-group`) that ships all ticks as structured JSON logs to Logstash → Elasticsearch → Kibana. Uses async appender so back-pressure never stalls processing threads.

Key class: `CandleEventConsumer`

---

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 21 (Project Loom — virtual threads enabled) |
| Framework | Spring Boot 3.2.3 |
| Messaging | Apache Kafka 7.6.0 (4 partitions, 1 topic) |
| Database | TimescaleDB 15 (PostgreSQL + time-series extensions) |
| Logging | ELK Stack — Elasticsearch 8.13, Logstash 8.13, Kibana 8.13 |
| Serialisation | Jackson 2.16.1 |
| Build | Maven (multi-module) |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers 1.19.6, Spring Kafka Test |
| Monitoring | Kafka UI, Spring Actuator |

---

## Data Flow

```
1. PriceSimulator  →  random walk bid/ask (±0.05%/tick, clamped ±10% from base price)
2. BidAskEventProducer  →  publishes BidAskEvent every 500 ms per symbol, keyed by symbol
3. Kafka  →  routes each symbol to its dedicated partition (ordering guaranteed per symbol)
4. BidAskEventConsumer (concurrency=4)  →  writes each tick as a row in the ticks hypertable
5. TimescaleDB stores:  (time TIMESTAMPTZ, symbol, bid, ask, mid_price)
6. GET /history  →  time_bucket() aggregates ticks into OHLCV candles at query time
7. HistoryController  →  converts candles to TradingView UDF parallel-array response
```

**Mid-price**: `(bid + ask) / 2.0` — standard practice for instruments where only bid/ask is available. Used as the representative price for all OHLC computation.

**Volume**: Synthetic — the count of ticks received within each bucket, not notional trade size.

---

## API Reference

### `GET /history`

Returns OHLCV candle history in TradingView UDF parallel-array format.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `symbol` | string | yes | Trading pair, e.g. `BTC-USD`, `ETH-USD`, `XAU-USD`, `XAG-USD` |
| `interval` | string | yes | Candle size: `1s`, `5s`, `1m`, `15m`, `1h` |
| `from` | long | yes | Range start — UNIX seconds, inclusive |
| `to` | long | yes | Range end — UNIX seconds, exclusive |

**Success response (data exists)**

```json
{
  "s": "ok",
  "t": [1620000000, 1620000060],
  "o": [29500.5,    29501.0],
  "h": [29510.0,    29505.0],
  "l": [29490.0,    29500.0],
  "c": [29505.0,    29502.0],
  "v": [10,         8]
}
```

**No data in range**

```json
{ "s": "no_data" }
```

**Validation error (HTTP 400)**

```json
{ "s": "error", "errmsg": "Unknown interval: 2x. Supported: [1s, 5s, 1m, 15m, 1h]" }
```

### Health

```
GET /actuator/health   →  includes Kafka consumer and PostgreSQL sub-indicators
GET /actuator/metrics
```

---

## Running the Service

### Prerequisites

- Docker and Docker Compose
- Java 21 SDK (for local builds only)
- Maven 3.9+ (for local builds only)

### Start everything with Docker Compose

```bash
# Build all modules
mvn clean package -DskipTests

# Launch all services (Zookeeper, Kafka, TimescaleDB, ELK, all app services)
docker compose up --build
```

Services come up in dependency order. Allow ~30 seconds for TimescaleDB to accept connections.

| Service | URL |
|---|---|
| History API | `http://localhost:8082/history?symbol=BTC-USD&interval=1m&from=0&to=9999999999` |
| Adapter health | `http://localhost:8081/actuator/health` |
| Candle-API health | `http://localhost:8082/actuator/health` |
| Logger health | `http://localhost:8083/actuator/health` |
| Kafka UI | `http://localhost:8080` |
| Kibana | `http://localhost:5601` |

### Example API call

```bash
# After ~5 seconds of data ingestion
FROM=$(date -d '5 minutes ago' +%s)
TO=$(date +%s)
curl "http://localhost:8082/history?symbol=BTC-USD&interval=1m&from=$FROM&to=$TO"
```

### Tear down

```bash
docker compose down -v   # -v removes volumes (clears the TimescaleDB data)
```

---

## Running Tests

The test suite is split into three tiers by speed and infrastructure requirements.

### Tier 1 — Unit tests (no infrastructure required)

All pure unit tests and Spring MVC slice tests. Run fast, require no external processes.

```bash
# All modules
mvn test

# Individual modules
mvn test -pl common
mvn test -pl market-data-adapter
mvn test -pl candle-api -Dgroups="!integration"
```

### Tier 2 — Integration tests (require Docker)

Tagged `@Tag("integration")`. Two test classes:

| Test class | Infrastructure | Scope |
|---|---|---|
| `TickRepositoryIntegrationTest` | Testcontainers (TimescaleDB image) | Real SQL — `INSERT`, `time_bucket()`, `first()`, `last()`, boundary conditions, multi-window bucketing |
| `HistoryApiIntegrationTest` | `@EmbeddedKafka` + H2 | Full Spring context — Kafka consumer wiring, REST layer, error handling |

```bash
# Run integration tests only (Docker must be running)
mvn verify -pl candle-api -Dgroups=integration

# Run all tests including integration
mvn verify
```

> **Note**: `TickRepositoryIntegrationTest` pulls `timescale/timescaledb:latest-pg15` on first run. Ensure Docker has internet access or pre-pull the image.

### Test class inventory

| Module | Test class | Type | Description |
|---|---|---|---|
| `common` | `CandleIntervalTest` | Unit | `windowStart` alignment for all intervals, `fromLabel` validation (valid, invalid, null, case-sensitive), `toPgInterval` SQL literals, `getDurationSeconds`, round-trip label check |
| `common` | `BidAskEventTest` | Unit | `midPrice` arithmetic, `timestampSeconds` truncation, record equality/inequality, `toString` |
| `common` | `CandleTest` | Unit | All accessors, record equality, OHLC invariants (high≥open, low≤close, etc.), flat candle |
| `market-data-adapter` | `PriceSimulatorTest` | Unit | bid < ask invariant (repeated 50×), price bounds ±10% (repeated 30× per symbol), 2/4 decimal rounding, thread safety (8 concurrent threads), unknown symbol fallback |
| `market-data-adapter` | `BidAskEventProducerTest` | Unit | Publishes exactly N events (one per symbol), correct topic, symbol as partition key, payload bid/ask values, event timestamp within call window, Kafka send failure does not propagate |
| `candle-api` | `HistoryResponseTest` | Unit | `ok()` with 1 and 3 candles, array alignment, null `errmsg`, empty list → `noData()`, `noData()` all nulls, `error()` status and message, record equality |
| `candle-api` | `CandleQueryServiceTest` | Unit | Delegation to repository, correct symbol/interval/time params passed, all 5 intervals resolve to correct enum, invalid/empty interval throws before repository is called |
| `candle-api` | `BidAskEventConsumerTest` | Unit | Single and multiple event forwarding, same-symbol repeated calls, repository exception propagation |
| `candle-api` | `HistoryControllerTest` | Slice (`@WebMvcTest`) | HTTP 200 with data, HTTP 200 no_data, all valid intervals, HTTP 400 for invalid interval/missing params/wrong type, response body format, `@JsonInclude(NON_NULL)` omits null fields |
| `candle-api` | `TickRepositoryIntegrationTest` | Integration (Docker) | Insert persistence, mid_price calculation, millisecond precision, multi-row insert, empty query, OHLCV aggregation (1 tick, 2 ticks, 3 ticks), multi-window bucketing, 5s interval, `from` inclusive / `to` exclusive, symbol isolation, ascending sort, interval label |
| `candle-api` | `HistoryApiIntegrationTest` | Integration (EmbeddedKafka) | Actuator health, history endpoint with mocked data, 400 on invalid interval, missing param, Kafka consumer delivers to repository (single and multi-event) |
| `logger-service` | `CandleEventConsumerTest` | Unit | No-throw guarantee for all symbols, exactly one log per `consume()` call, INFO level, all 7 structured fields present (`event_type`, `symbol`, `bid`, `ask`, `mid_price`, `event_ts_ms`, `event_ts_iso`), ISO-8601 timestamp format |
| `logger-service` | `LoggerServiceIntegrationTest` | Integration (EmbeddedKafka) | Actuator health, Kafka listener wired to consumer, single and multi-event delivery verified via `@SpyBean`, payload field matching |

---

## Assumptions and Trade-offs

### Aggregation at query time, not write time

Raw ticks are stored 1:1 in the hypertable. OHLCV is computed on-the-fly using `time_bucket()`, `first()`, and `last()` — TimescaleDB aggregate functions that respect chronological order.

**Pro**: Zero write-side complexity, no partial-candle state, instant support for any interval without schema changes, correct behaviour on late-arriving ticks.

**Con**: Higher read CPU per query. Mitigatable with TimescaleDB continuous aggregates if throughput grows.

### Mid-price for OHLC

No last-trade price is available from a bid/ask-only feed, so `(bid + ask) / 2.0` is stored and aggregated. This is standard practice in FX and crypto markets.

### Synthetic volume (tick count)

Volume = number of ticks per window. Without a matching engine, notional traded volume is unavailable.

### Kafka partition-per-symbol ordering

4 partitions, symbol as key → each partition receives ticks for exactly one symbol in arrival order. Consumer concurrency = 4 ensures no inter-symbol locking on the database while preserving intra-symbol write ordering.

### Stateless consumer

`candle-api` holds no in-memory window state. Ticks go straight to the DB. On restart the consumer replays from the last committed Kafka offset; no candle data is lost.

### No authentication

The `GET /history` endpoint is unauthenticated. In production, add an API gateway or Spring Security filter.

### No pre-created hypertable extension in schema.sql

The production `schema.sql` assumes the TimescaleDB extension is already active (as it is in the official Docker image). The integration test (`TickRepositoryIntegrationTest`) issues `CREATE EXTENSION IF NOT EXISTS timescaledb` explicitly before creating the table to make the test self-contained.

---

## Bonus Features

### ELK Stack — full observability pipeline

`logger-service` ships every tick as a structured JSON document to Logstash over TCP using `LogstashTcpSocketAppender` (async, `neverBlock=true`). Logstash routes to daily indices in Elasticsearch (`candle-logs-{service}-{YYYY.MM.dd}`). Kibana visualises tick rates, spread distributions, and symbol activity in real time.

### Kafka UI

Web console at `http://localhost:8080` for inspecting topics, partition offsets, consumer group lag, and individual message payloads — no CLI tools needed.

### Java 21 virtual threads (Project Loom)

`spring.threads.virtual.enabled=true` is set in `candle-api` and `market-data-adapter`. Spring Boot 3.2 transparently routes blocking Servlet, JDBC, and Kafka threads through virtual threads, increasing concurrency headroom with no code changes.

### Health checks in Docker Compose

All services define a `healthcheck` block that polls `/actuator/health`. Dependent services (`depends_on: condition: service_healthy`) wait for their upstream to pass before starting.

### Configurable tick interval

The simulator tick rate is externalised as `adapter.tick-interval-ms` (default 500 ms). Change it in `docker-compose.yml` or pass it as an environment variable to adjust simulated market data frequency without rebuilding.

### Extensible by design

- **New symbol**: add a constant to `Symbols.java` and a base price in `PriceSimulator`. No schema changes.
- **New interval**: add an enum constant to `CandleInterval` with the corresponding `toPgInterval()` case. No schema changes, no new topics or partitions.
