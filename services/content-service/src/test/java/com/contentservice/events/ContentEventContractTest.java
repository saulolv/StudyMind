package com.contentservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.contentservice.domain.ContentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * The published envelope is validated against the actual files in {@code contracts/events/v1}, not
 * against a shape copied into this test. If a schema changes and the publisher does not, this fails.
 */
class ContentEventContractTest {

    private static final String SCHEMA_BASE = "https://studymind/contracts/events/v1/";
    private static final Path CONTRACTS_DIR =
            Path.of("..", "..", "contracts", "events", "v1").toAbsolutePath().normalize();

    private static final UUID CONTENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final com.fasterxml.jackson.databind.ObjectMapper parser =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final EventPublisher publisher = new RabbitEventPublisher(
            rabbitTemplate,
            new tools.jackson.databind.ObjectMapper(),
            Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC),
            "studymind.events",
            "content-service");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void contractsDirectoryIsWhereThisTestExpectsIt() {
        assertThat(CONTRACTS_DIR.resolve("event-envelope.v1.schema.json")).exists();
    }

    @Test
    void pdfSubmissionSatisfiesTheContentSubmittedSchema() {
        publisher.publish(
                ContentSubmitted.pdf(CONTENT_ID, USER_ID, "raw/u/c/lecture.pdf", "lecture.pdf"));

        JsonNode envelope = captureBody("content.submitted");
        assertThat(validate("content-submitted.v1.schema.json", envelope)).isEmpty();
        assertThat(envelope.get("payload").get("type").asText()).isEqualTo("PDF");
        assertThat(envelope.get("payload").has("source_url")).isFalse();
    }

    @Test
    void videoSubmissionSatisfiesTheSameSchema() {
        publisher.publish(
                ContentSubmitted.video(CONTENT_ID, USER_ID, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

        JsonNode envelope = captureBody("content.submitted");
        assertThat(validate("content-submitted.v1.schema.json", envelope)).isEmpty();
        assertThat(envelope.get("payload").get("type").asText()).isEqualTo("VIDEO");
        assertThat(envelope.get("payload").has("storage_path")).isFalse();
        assertThat(envelope.get("payload").has("file_name")).isFalse();
    }

    @Test
    void deletionSatisfiesTheContentDeletedSchema() {
        publisher.publish(new ContentDeleted(CONTENT_ID, USER_ID, ContentType.PDF, "raw/u/c/lecture.pdf"));

        JsonNode envelope = captureBody("content.deleted");
        assertThat(validate("content-deleted.v1.schema.json", envelope)).isEmpty();
    }

    @Test
    void envelopeCarriesTheDerivedFieldsNoCallerSupplies() {
        MDC.put(RabbitEventPublisher.CORRELATION_ID_KEY, "44444444-4444-4444-4444-444444444444");

        publisher.publish(ContentSubmitted.video(CONTENT_ID, USER_ID, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

        JsonNode envelope = captureBody("content.submitted");
        assertThat(envelope.get("event_version").asText()).isEqualTo("v1");
        assertThat(envelope.get("event_type").asText()).isEqualTo("ContentSubmitted");
        assertThat(envelope.get("source_service").asText()).isEqualTo("content-service");
        assertThat(envelope.get("occurred_at").asText()).isEqualTo("2026-09-17T12:00:00Z");
        assertThat(envelope.get("correlation_id").asText())
                .isEqualTo("44444444-4444-4444-4444-444444444444");
        assertThat(UUID.fromString(envelope.get("event_id").asText())).isNotNull();
    }

    @Test
    void generatesACorrelationIdWhenTheRequestDidNotCarryOne() {
        publisher.publish(ContentSubmitted.video(CONTENT_ID, USER_ID, "https://www.youtube.com/watch?v=dQw4w9WgXcQ"));

        JsonNode envelope = captureBody("content.submitted");
        assertThat(UUID.fromString(envelope.get("correlation_id").asText())).isNotNull();
    }

    private JsonNode captureBody(String expectedRoutingKey) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("studymind.events"), eq(expectedRoutingKey), captor.capture());
        Message message = captor.getValue();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        try {
            return parser.readTree(message.getBody());
        } catch (Exception ex) {
            throw new AssertionError("Published body was not valid JSON", ex);
        }
    }

    private static Set<ValidationMessage> validate(String schemaFile, JsonNode envelope) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(mappers ->
                        mappers.mapPrefix(SCHEMA_BASE, CONTRACTS_DIR.toUri().toString())));
        JsonSchema schema = factory.getSchema(SchemaLocation.of(SCHEMA_BASE + schemaFile));
        return schema.validate(envelope);
    }
}
