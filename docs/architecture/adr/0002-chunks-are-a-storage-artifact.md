# ADR-0002: chunks live in Qdrant, not in a relational table

- **Status:** Accepted
- **Date:** 2026-09-16

## Context

Extraction turns one content into tens or hundreds of chunks: a passage of text, where it came from
(a page, or a start and end timestamp), and eventually a vector. Something has to hold them, and
there were three candidates.

**A `chunk` table in `content_db`.** The obvious relational answer, and wrong here for a specific
reason: content-service would then own a table it never reads and never writes. Every row would be
inserted by `processing-worker` and read by `chat-llm-service`, neither of which owns that database.
Either the workers reach into content-service's schema — which is the boundary violation this
system is organised to avoid — or content-service grows write endpoints that exist only to proxy
data it has no opinion about.

**A `chunk` table in a new chunk-service.** A service whose entire behaviour is "store what the
worker says, return what chat asks for". No invariant to protect, no decision to make. That is a
table with a REST API in front of it, and the cost of a deployable.

**Qdrant.** The chunks exist to be retrieved by similarity, filtered by user. That is Qdrant's
query. The text has to be returned with the hit anyway, or retrieval needs a second round trip to
whatever holds it.

## Decision

A chunk is a point in Qdrant, not a row anywhere. Each point carries its vector plus a payload:

| Field | Purpose |
| --- | --- |
| `user_id` | the tenant filter, applied before similarity |
| `content_id` | provenance, citations, and the key for deletion |
| `type` | `PDF` or `VIDEO` |
| `text` | returned with the hit, so retrieval is one call |
| `page` | PDF only |
| `start_seconds`, `end_seconds` | video only; what makes a timestamped answer possible |

The chunk ids travel through the events — `ChunksCreated` carries `chunk_ids` and `chunk_count`,
`ContentIndexed` carries `indexed_chunk_count` and the collection name — so the pipeline can be
followed and counted without a database to join against.

`content_db` keeps only the inventory: one row per content, with its status. `chat_db` stores the
`chunk_ids` an answer cited, not the chunk text, so re-indexing does not leave stale copies behind.

## Consequences

**What got better.** No service owns a table it does not use. Retrieval is one query that filters
and ranks in the same call. Deleting a content is one `content_id` filter delete in Qdrant, driven
by `ContentDeleted`.

**What got harder.**

- *Chunks are not durable source data.* Qdrant is a derived store: losing it means re-extracting
  every PDF and re-transcribing every video, which costs real money in Whisper calls. The raw PDFs
  survive in object storage, but transcripts do not.
- *Changing the embedding model means a full re-index*, and there is no cheaper path because the
  text is not held anywhere else.
- *There is no SQL over chunks.* Counting chunks per user, or auditing what was indexed, means
  querying Qdrant rather than joining a table.

The first of those is the one that will hurt. The mitigation, when it is needed, is to persist the
extracted text — not the vectors — to object storage next to the raw upload, so re-indexing is a
re-embed rather than a re-transcribe. That is a cheap addition later and is deliberately not done
now.

## What would make this wrong

- If chunks acquire relational behaviour: users editing them, annotating them, sharing them between
  contents. A point payload is a bad place for anything with its own lifecycle.
- If transcription costs make re-extraction unacceptable *before* the object-storage mitigation
  above is in place.
- If reporting over chunks becomes a product feature rather than an operational question.
