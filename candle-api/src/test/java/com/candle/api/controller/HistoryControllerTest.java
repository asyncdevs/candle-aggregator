package com.candle.api.controller;

import com.candle.api.service.CandleQueryService;
import com.candle.common.model.Candle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandleQueryService candleQueryService;

    // ── Happy path — single candle ────────────────────────────────────────────

    @Test
    void getHistory_singleCandle_returns200WithOkStatus() throws Exception {
        when(candleQueryService.getCandles("BTC-USD", "1m", 1620000000L, 1620000060L))
            .thenReturn(List.of(btcCandle(1620000000L)));

        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000060"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.s").value("ok"))
            .andExpect(jsonPath("$.t[0]").value(1620000000))
            .andExpect(jsonPath("$.o[0]").value(29500.0))
            .andExpect(jsonPath("$.h[0]").value(29600.0))
            .andExpect(jsonPath("$.l[0]").value(29400.0))
            .andExpect(jsonPath("$.c[0]").value(29550.0))
            .andExpect(jsonPath("$.v[0]").value(10));
    }

    // ── Happy path — multiple candles ────────────────────────────────────────

    @Test
    void getHistory_multipleCandles_returnsParallelArraysInOrder() throws Exception {
        when(candleQueryService.getCandles(any(), any(), anyLong(), anyLong()))
            .thenReturn(List.of(
                new Candle(1620000000L, "BTC-USD", "1m", bd("100"), bd("110"), bd("90"),  bd("105"), 3L),
                new Candle(1620000060L, "BTC-USD", "1m", bd("105"), bd("115"), bd("100"), bd("112"), 5L)
            ));

        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000120"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.t[0]").value(1620000000))
            .andExpect(jsonPath("$.t[1]").value(1620000060))
            .andExpect(jsonPath("$.o[0]").value(100.0))
            .andExpect(jsonPath("$.o[1]").value(105.0))
            .andExpect(jsonPath("$.v[0]").value(3))
            .andExpect(jsonPath("$.v[1]").value(5));
    }

    // ── All supported intervals ──────────────────────────────────────────────

    @Test
    void getHistory_allValidIntervals_return200() throws Exception {
        when(candleQueryService.getCandles(any(), any(), anyLong(), anyLong()))
            .thenReturn(List.of());

        for (String interval : List.of("1s", "5s", "1m", "15m", "1h")) {
            mockMvc.perform(get("/history")
                    .param("symbol",   "BTC-USD")
                    .param("interval", interval)
                    .param("from",     "1620000000")
                    .param("to",       "1620003600"))
                .andExpect(status().isOk());
        }
    }

    // ── No data ───────────────────────────────────────────────────────────────

    @Test
    void getHistory_emptyResult_returnsNoDataStatus() throws Exception {
        when(candleQueryService.getCandles(any(), any(), anyLong(), anyLong()))
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
    void getHistory_noDataBody_isExactlyNoDataJson() throws Exception {
        when(candleQueryService.getCandles(any(), any(), anyLong(), anyLong()))
            .thenReturn(List.of());

        String body = mockMvc.perform(get("/history")
                .param("symbol",   "ETH-USD")
                .param("interval", "5s")
                .param("from",     "1620000000")
                .param("to",       "1620000005"))
            .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("{\"s\":\"no_data\"}");
    }

    // ── Validation errors → 400 ──────────────────────────────────────────────

    @Test
    void getHistory_invalidInterval_returns400WithErrorStatus() throws Exception {
        when(candleQueryService.getCandles(any(), eq("99x"), anyLong(), anyLong()))
            .thenThrow(new IllegalArgumentException("Unknown interval: 99x. Supported: [1s, 5s, 1m, 15m, 1h]"));

        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "99x")
                .param("from",     "1620000000")
                .param("to",       "1620000600"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.s").value("error"))
            .andExpect(jsonPath("$.errmsg").exists());
    }

    @Test
    void getHistory_missingSymbol_returns400() throws Exception {
        mockMvc.perform(get("/history")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000600"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_missingInterval_returns400() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol", "BTC-USD")
                .param("from",   "1620000000")
                .param("to",     "1620000600"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_missingFrom_returns400() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("to",       "1620000600"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_missingTo_returns400() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getHistory_fromIsNotALong_returns400() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "not-a-number")
                .param("to",       "1620000600"))
            .andExpect(status().isBadRequest());
    }

    // ── from >= to validation ────────────────────────────────────────────────

    @Test
    void getHistory_fromEqualsTo_returns400WithErrorStatus() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000000"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.s").value("error"))
            .andExpect(jsonPath("$.errmsg").exists());
    }

    @Test
    void getHistory_fromGreaterThanTo_returns400WithErrorStatus() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000600")
                .param("to",       "1620000000"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.s").value("error"))
            .andExpect(jsonPath("$.errmsg").value(
                org.hamcrest.Matchers.containsString("must be strictly less than")));
    }

    @Test
    void getHistory_fromGreaterThanTo_doesNotCallService() throws Exception {
        mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "9999999999")
                .param("to",       "1000000000"))
            .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(candleQueryService);
    }

    // ── Response format — no nulls in ok response ────────────────────────────

    @Test
    void getHistory_okResponse_doesNotContainNullFields() throws Exception {
        when(candleQueryService.getCandles(any(), any(), anyLong(), anyLong()))
            .thenReturn(List.of(btcCandle(1620000000L)));

        String body = mockMvc.perform(get("/history")
                .param("symbol",   "BTC-USD")
                .param("interval", "1m")
                .param("from",     "1620000000")
                .param("to",       "1620000060"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(":null");
        assertThat(body).contains("\"s\":\"ok\"");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Candle btcCandle(long time) {
        return new Candle(time, "BTC-USD", "1m", bd("29500"), bd("29600"), bd("29400"), bd("29550"), 10L);
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
