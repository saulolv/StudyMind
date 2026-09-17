package com.contentservice.events;

/**
 * The seam for everything this service publishes.
 *
 * <p>One method. The envelope fields required by
 * {@code contracts/events/v1/event-envelope.v1.schema.json} — {@code event_id}, {@code event_type},
 * {@code event_version}, {@code occurred_at}, {@code source_service}, {@code correlation_id} — are
 * all derived inside the implementation, so no caller constructs, or can get wrong, an envelope.
 */
public interface EventPublisher {

    void publish(EventPayload payload);
}
