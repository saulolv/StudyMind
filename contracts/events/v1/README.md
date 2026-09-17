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

- `ContentSubmitted` -> emitted by `content-service` for both PDF and video, discriminated by `payload.type`
- `ContentDeleted` -> emitted by `content-service`
- `ChunksCreated` -> emitted by `processing-worker` or `transcription-worker`
- `ContentIndexed` -> emitted by `embedding-worker`
- `AnswerGenerated` -> emitted by `chat-llm-service` (optional analytics event)

## Routing

| Event | Exchange | Routing key |
| ----- | -------- | ----------- |
| `ContentSubmitted` | `studymind.events` | `content.submitted` |
| `ContentDeleted` | `studymind.events` | `content.deleted` |

A consumer that only handles PDFs binds `content.submitted` and filters on `payload.type`; the
routing key stays coarse so a single binding sees every submission.

## Validation

Use any JSON Schema validator compatible with Draft 2020-12.

`content-service` validates every envelope it publishes against these files in its own test suite
(`ContentSubmittedContractTest`), so the schemas are enforced rather than aspirational.

## Changes

`ContentUploaded` and `VideoSubmitted` were merged into `ContentSubmitted` when document-service and
video-service became `content-service`. The two events had the same shape apart from the source
field, and everything downstream (`ChunksCreated`, `ContentIndexed`) already used a single `type`
discriminator. Nothing consumed the old events yet, so they were removed rather than deprecated.
