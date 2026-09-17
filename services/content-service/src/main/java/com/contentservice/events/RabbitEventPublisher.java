package com.contentservice.events;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Production adapter at the {@link EventPublisher} seam.
 *
 * <p>The envelope is assembled as an explicit map rather than by reflecting over Java field names.
 * The wire contract lives in {@code contracts/events/v1/}, is consumed by Python workers, and must
 * not shift because someone renamed a record component.
 */
@Component
class RabbitEventPublisher implements EventPublisher {

    static final String CORRELATION_ID_KEY = "correlationId";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String exchange;
    private final String sourceService;

    RabbitEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${studymind.events.exchange}") String exchange,
            @Value("${spring.application.name}") String sourceService) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.exchange = exchange;
        this.sourceService = sourceService;
    }

    @Override
    public void publish(EventPayload payload) {
        Descriptor descriptor = describe(payload);
        String eventId = UUID.randomUUID().toString();
        String correlationId = currentCorrelationId();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_id", eventId);
        envelope.put("event_type", descriptor.eventType());
        envelope.put("event_version", "v1");
        envelope.put("occurred_at", DateTimeFormatter.ISO_INSTANT.format(clock.instant()));
        envelope.put("source_service", sourceService);
        envelope.put("correlation_id", correlationId);
        envelope.put("payload", body(payload));

        Message message = MessageBuilder.withBody(objectMapper.writeValueAsBytes(envelope))
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setMessageId(eventId)
                .setCorrelationId(correlationId)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();

        rabbitTemplate.send(exchange, descriptor.routingKey(), message);
    }

    /**
     * Exhaustive over the sealed {@link EventPayload}: a new event type will not compile until it
     * is given an {@code event_type} and a routing key here.
     */
    private static Descriptor describe(EventPayload payload) {
        return switch (payload) {
            case ContentSubmitted ignored -> new Descriptor("ContentSubmitted", "content.submitted");
            case ContentDeleted ignored -> new Descriptor("ContentDeleted", "content.deleted");
        };
    }

    private static Map<String, Object> body(EventPayload payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        switch (payload) {
            case ContentSubmitted event -> {
                body.put("content_id", event.contentId().toString());
                body.put("user_id", event.userId().toString());
                body.put("type", event.type().name());
                putIfPresent(body, "storage_path", event.storagePath());
                putIfPresent(body, "file_name", event.fileName());
                putIfPresent(body, "source_url", event.sourceUrl());
            }
            case ContentDeleted event -> {
                body.put("content_id", event.contentId().toString());
                body.put("user_id", event.userId().toString());
                body.put("type", event.type().name());
                putIfPresent(body, "storage_path", event.storagePath());
            }
        }
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    private static String currentCorrelationId() {
        String fromContext = MDC.get(CORRELATION_ID_KEY);
        return fromContext != null ? fromContext : UUID.randomUUID().toString();
    }

    private record Descriptor(String eventType, String routingKey) {
    }
}
