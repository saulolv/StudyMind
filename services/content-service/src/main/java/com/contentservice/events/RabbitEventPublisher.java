package com.contentservice.events;

import com.contentservice.events.wire.ContentDeletedPayload;
import com.contentservice.events.wire.ContentSubmittedPayload;
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
 * <p>The envelope is assembled as an explicit map rather than by reflecting over Java field names:
 * its fields are all derived here and none of them vary per event. The payload is not, because that
 * part of the wire does vary — it is built through the generated types in
 * {@code com.contentservice.events.wire}, which come from {@code contracts/events/v1/}. Renaming a
 * field in a schema therefore breaks this file rather than the Python workers reading the queue.
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
     * is given a routing key here. The {@code event_type} comes from the schema, not from this file.
     */
    private static Descriptor describe(EventPayload payload) {
        return switch (payload) {
            case ContentSubmitted ignored ->
                    new Descriptor(ContentSubmittedPayload.EVENT_TYPE, "content.submitted");
            case ContentDeleted ignored ->
                    new Descriptor(ContentDeletedPayload.EVENT_TYPE, "content.deleted");
        };
    }

    /**
     * The one place a domain event becomes wire. Each setter below is named after a field in the
     * schema that generated it, so a rename in a schema fails here at compile time rather than at
     * a consumer that receives a key it does not recognise.
     */
    private static Map<String, Object> body(EventPayload payload) {
        return switch (payload) {
            case ContentSubmitted event -> ContentSubmittedPayload.builder()
                    .contentId(event.contentId())
                    .userId(event.userId())
                    .type(event.type().name())
                    .storagePath(event.storagePath())
                    .fileName(event.fileName())
                    .sourceUrl(event.sourceUrl())
                    .build()
                    .toWireMap();
            case ContentDeleted event -> ContentDeletedPayload.builder()
                    .contentId(event.contentId())
                    .userId(event.userId())
                    .type(event.type().name())
                    .storagePath(event.storagePath())
                    .build()
                    .toWireMap();
        };
    }

    private static String currentCorrelationId() {
        String fromContext = MDC.get(CORRELATION_ID_KEY);
        return fromContext != null ? fromContext : UUID.randomUUID().toString();
    }

    private record Descriptor(String eventType, String routingKey) {
    }
}
