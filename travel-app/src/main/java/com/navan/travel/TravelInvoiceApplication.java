package com.navan.travel;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
public class TravelInvoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelInvoiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
