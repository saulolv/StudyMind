package com.contentservice.events;

import java.time.Clock;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EventsConfiguration {

    @Bean
    TopicExchange studymindEventsExchange(@Value("${studymind.events.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
