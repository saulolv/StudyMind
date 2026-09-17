package com.contentservice.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;

class EventsConfigurationTest {

    private final EventsConfiguration configuration = new EventsConfiguration();

    /**
     * Durable and not auto-delete: events outlive the publisher, so a restart must not drop the
     * exchange the Python workers are bound to.
     */
    @Test
    void declaresADurableTopicExchange() {
        TopicExchange exchange = configuration.studymindEventsExchange("studymind.events");

        assertThat(exchange.getName()).isEqualTo("studymind.events");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    /** {@code occurred_at} is UTC in the contract, so the default clock has to be too. */
    @Test
    void defaultsToAUtcClock() {
        Clock clock = configuration.clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
