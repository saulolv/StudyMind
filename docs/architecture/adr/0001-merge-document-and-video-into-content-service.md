# ADR-0001: document-service and video-service are one content-service

- **Status:** Accepted
- **Date:** 2026-09-16
- **Supersedes:** the original three-service split in the first architecture sketch

## Context

The first design had a `document-service` for PDF uploads and a `video-service` for YouTube
submissions. They were separate deployables with separate databases and separate event names.

Writing them revealed that they were the same service twice:

- **The same resource.** Both owned "a thing a user submitted, which has a status and is being
  processed asynchronously". The columns differed by three nullable fields.
- **The same internal endpoint.** Both carried `/internal/{id}/ownership`, answering the same
  question for the same caller with the same semantics.
- **The same event, named twice.** `ContentUploaded` and `VideoSubmitted` had identical shapes apart
  from `storage_path`/`file_name` versus `source_url`.
- **A downstream that had already merged them.** `ChunksCreated` and `ContentIndexed` — the events
  the workers and chat actually consume — already carried a single `type` field with values `PDF`
  and `VIDEO`. The split existed only upstream of the point where the system stopped caring.

Two services whose only difference is which of three nullable fields is populated are not two
bounded contexts. They are one context with two adapters.

## Decision

One `content-service` owning a single `content` resource, with the source of a content expressed as
a sealed type rather than as a deployment boundary:

```java
public sealed interface ContentSource {
    record PdfUpload(String fileName, byte[] bytes, String declaredContentType) implements ContentSource {}
    record YouTubeLink(URI url) implements ContentSource {}
}
```

`ContentIngestion.ingest(userId, source)` is the seam. `POST /contents` picks the adapter by request
media type: `multipart/form-data` carries a PDF, `application/json` carries a URL. Both return the
same 202 body and both publish one `ContentSubmitted` carrying a `type` discriminator, matching what
the downstream events already did.

`ContentUploaded` and `VideoSubmitted` were removed rather than deprecated, because nothing consumed
them yet.

## Consequences

**What got better.** One resource, one ownership endpoint, one event contract, one status
lifecycle, one database, one set of tenant-scoping rules to get right. The `transcript_status`
column the video-service carried disappeared: transcription is the `PROCESSING` stage of a `VIDEO`,
not a parallel state machine.

**What got harder.** content-service now has two reasons to change, and PDF traffic and video
traffic scale together. Both are acceptable: the two ingestion paths are a few dozen lines each,
and neither is the bottleneck — the workers are, and they are already separate.

**What this commits us to.** A third source — a web article, a pasted block of text — is a new
permitted record in `ContentSource`, not a new service and not a new endpoint shape. The
`processing-worker`/`transcription-worker` split stays, because those two genuinely differ: one
parses bytes, the other calls a transcription API and produces timestamps.

## What would make this wrong

- If ingesting a video grew its own substantial domain — playlists, channel subscriptions, rights
  checking — so that the two paths stopped being adapters and became subjects in their own right.
- If PDF and video traffic diverged enough that they needed independent scaling or independent
  availability, and the shared service became the contended resource.

Neither is true today, and if either becomes true the sealed interface is the line the split would
follow.
