# 02 — content-service

The one implemented service. Read this before extending it: the module shapes are deliberate, and
several of them exist to *not* be the obvious thing.

Port 8081, package `com.contentservice`, database `content_db` on host port 5434.

## What it is responsible for

Accepting a unit of study material from any source, making it durable and owned, and announcing it.
Then reading it back, and deleting it. Nothing else — it does not parse, transcribe, embed or
answer.

## The modules

```
com.contentservice
├── api/          controllers and response DTOs
├── catalog/      reads and deletion for content already ingested
├── domain/       the Content entity and its repository
├── events/       the publishing seam, and the generated wire types
├── ingestion/    the submission seam, and object storage
└── web/          correlation id, @CurrentUser, MVC wiring
```

### `ingestion` — one method, two adapters

```java
public interface ContentIngestion {
    Content ingest(UUID userId, ContentSource source);
}
```

`ContentSource` is a sealed interface permitting `PdfUpload` and `YouTubeLink`. This is the seam
that replaced two services
([ADR-0001](../architecture/adr/0001-merge-document-and-video-into-content-service.md)): **a new
ingestion source is a new permitted record, not a new endpoint shape and not a new service.**

The invariants are documented on the interface and asserted in `DefaultContentIngestionTest`:

- the returned `Content` is persisted and owned by `userId`;
- its status is `PENDING` — ingestion never blocks on processing;
- exactly one `ContentSubmitted` has been published for it;
- for a PDF, **the bytes are in object storage before the event is published**, so a worker
  consuming the event can always read `storage_path`.

A PDF is validated by its magic bytes, not by the client's `Content-Type`; an oversized or empty
upload is refused; and the file name is sanitised into the user's own storage prefix. Everything the
caller could have prevented throws `InvalidContentSourceException`, which the exception handler maps
to 400.

`BlobStore` is **package-private on purpose**. It is not part of `ContentIngestion`'s interface — it
exists so ingestion can be tested without MinIO. It is explicitly *not* there to abstract
"MinIO today, S3 tomorrow": MinIO is S3-compatible, so those are one adapter with a different
endpoint. The second implementation that justifies the seam is the in-memory one in the tests.

### `events` — one method, over a sealed type

```java
public interface EventPublisher {
    void publish(EventPayload payload);
}
```

Every envelope field is derived inside `RabbitEventPublisher`: `event_id` is a fresh UUID,
`occurred_at` comes from an injected `Clock`, `correlation_id` from the MDC, `source_service` from
`spring.application.name`. **No caller builds an envelope**, and no caller can get one wrong.

`EventPayload` is sealed, and the publisher switches over it exhaustively, so a new event does not
compile until it has been given a routing key.

The payload is built through the generated types in `events/wire/`, which come from
`contracts/events/v1/`. Renaming a field in a schema breaks this file
([ADR-0004](../architecture/adr/0004-generate-wire-types-from-the-event-contracts.md)). The envelope
itself is still an explicit `LinkedHashMap`, not a reflected object: Python workers read these keys
literally, and the wire must not move because someone renamed a Java record component.

### `catalog` — a class, not an interface

`ContentCatalog` is concrete. Nothing varies across it: its only swappable dependency is the
repository, and a Spring Data repository is already an interface with a local stand-in. A port there
would be indirection, not a seam.

**Every method takes the owning `userId` as its first argument.** There is no unscoped read path in
this service, and content belonging to someone else is reported as 404 rather than 403.

### `web` — the trust boundary

This service never sees a JWT. `api-gateway-auth` validates the token and forwards the subject as
`X-User-Id`, which `CurrentUserArgumentResolver` turns into a `UUID` for any `@CurrentUser`
parameter. A missing or malformed header is a 401.

**It must therefore not be reachable from outside the internal network.** Nothing in this service
enforces that; it is a deployment property.

`CorrelationIdFilter` puts the inbound `X-Correlation-Id` in the MDC and clears it afterwards, which
is what lets the publisher stamp every event with it without anyone passing it around.

## The API

Full spec: [`openapi/openapi.yaml`](../../services/content-service/openapi/openapi.yaml).

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/contents` | `multipart/form-data` → PDF, `application/json` → YouTube URL. 202 either way. |
| `GET` | `/contents` | the caller's contents, newest first, paged |
| `GET` | `/contents/{contentId}` | status and metadata |
| `DELETE` | `/contents/{contentId}` | 202; publishes `ContentDeleted` for asynchronous cleanup |
| `GET` | `/internal/contents?ids=…` | readiness for a batch belonging to the caller |

One endpoint selected by media type, rather than `/contents/pdf` and `/contents/video`, because
there is one resource with two adapters. Errors are RFC 9457 problem details throughout.

## Testing

```bash
cd services/content-service
./mvnw -B -ntp verify
```

**No Docker required, and it must stay that way.** Ingestion is tested through its interface with an
in-memory blob store, and the contract test reads the schema files off disk. Do not add a
`@SpringBootTest` `contextLoads` test here: it would make the suite require Postgres and RabbitMQ
for no assertion about this codebase.

`ContentEventContractTest` validates every envelope the publisher produces against the real files in
`contracts/events/v1/`. If a schema changes and the publisher does not, it fails.

The build enforces 90% line and branch coverage
([ADR-0005](../architecture/adr/0005-coverage-gate-lives-in-the-build.md)).

## Configuration

`application.properties` is env-var driven with localhost defaults, so the service runs against
`docker compose` with no configuration and against anything else with environment variables only.

| Variable | Default | |
| --- | --- | --- |
| `CONTENT_SERVICE_PORT` | 8081 | |
| `CONTENT_DB_HOST` / `_PORT` / `_NAME` / `_USER` / `_PASSWORD` | localhost:5434, `content_db` | |
| `RABBITMQ_HOST` / `_PORT` / `_USER` / `_PASS` | localhost:5672 | |
| `EVENTS_EXCHANGE` | `studymind.events` | |
| `MINIO_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_REGION` | localhost:9000, `studymind`, `us-east-1` | |
| `MAX_PDF_BYTES`, `MAX_UPLOAD_SIZE` | 50 MiB | both, or multipart rejects before ingestion does |

Schema changes go in `src/main/resources/db/migration` as a new Flyway migration.
`ddl-auto=validate`, never `update`.

## Known gap

`DefaultContentIngestion` publishes inside the transaction, so a broker failure after commit loses
the event: delivery is at-most-once. The fix is a transactional outbox, and it is not implemented.
See [EVENTS.md](../architecture/EVENTS.md#reliability-and-what-is-missing).

## Extending it

- **A new content source** (a web article, a pasted block of text): a new record permitted by
  `ContentSource`, a branch in `DefaultContentIngestion`, and a new `type` value in the migration's
  check constraint, the `ContentType` enum and `content-submitted.v1.schema.json`. Not a new
  service, and not a new endpoint.
- **A new event**: add the schema to `contracts/events/v1/`, list it in that folder's README, add a
  permitted record to `EventPayload`, run `studymind-contracts codegen`, and give it a routing key
  in `RabbitEventPublisher`. The compiler will not let you skip the last step.
- **A new read**: a method on `ContentCatalog` taking `userId` first.
