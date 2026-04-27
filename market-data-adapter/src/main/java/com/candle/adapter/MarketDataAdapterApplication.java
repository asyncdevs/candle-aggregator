package com.candle.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketDataAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketDataAdapterApplication.class, args);
    }
}
