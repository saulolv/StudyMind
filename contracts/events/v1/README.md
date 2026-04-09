# StudyMind Event Contracts (v1)

This folder defines RabbitMQ event schemas for the MVP.

## Conventions

- Versioned file name: `<event-name>.v1.schema.json`
- Schema version field: `event_version = "v1"`
- Base envelope fields are shared by all events:
  - `event_id` (UUID)
  - `event_type` (string)
  - `event_version` (string)
  - `occurred_at` (ISO-8601 UTC timestamp)
  - `source_service` (string)
  - `correlation_id` (UUID)
  - `payload` (object)

## Initial Events

- `ContentUploaded` -> emitted by `document-service`
- `VideoSubmitted` -> emitted by `video-service`
- `ChunksCreated` -> emitted by `processing-worker` or `transcription-worker`
- `ContentIndexed` -> emitted by `embedding-worker`
- `AnswerGenerated` -> emitted by `chat-llm-service` (optional analytics event)

## Validation

Use any JSON Schema validator compatible with Draft 2020-12.

