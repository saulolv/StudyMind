package com.contentservice.events;

/**
 * Every event this service can emit.
 *
 * <p>Sealed on purpose: {@code RabbitEventPublisher} switches over this type to derive the
 * {@code event_type} and routing key, so adding an event without giving it a contract mapping
 * is a compile error rather than a runtime surprise.
 */
public sealed interface EventPayload permits ContentSubmitted, ContentDeleted {
}
