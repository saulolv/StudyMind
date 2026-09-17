# Deployment

**Nothing is deployed yet.** There is no production environment, no container registry, no
Kubernetes manifests and no release pipeline. This document states what the code already assumes
about being deployed, so that the first real deployment does not have to reverse-engineer it, and
what has to be decided before one is possible.

For local development, see [00-environment-setup.md](development/00-environment-setup.md).

## What the code already assumes

### Configuration is environment variables, with localhost defaults

Every service's `application.properties` reads from the environment and falls back to a development
default:

```properties
spring.datasource.url=jdbc:postgresql://${CONTENT_DB_HOST:localhost}:${CONTENT_DB_PORT:5434}/${CONTENT_DB_NAME:content_db}
```

Nothing has to be rebuilt to point a service at different infrastructure, and no profile file
carries environment-specific values. New configuration follows this pattern: an env var with a
localhost default, never a hardcoded host and never a `application-prod.properties`.

### Schema changes are Flyway migrations, and `ddl-auto` is `validate`

Each service owns its own database and migrates it on startup. `validate` means a mismatch between
the entity mapping and the actual schema fails the boot rather than silently altering a table. A
schema change is a new versioned file in `src/main/resources/db/migration`, never an edit to one
already applied.

### The internal services are not internet-facing

`content-service` and `chat-llm-service` trust the `X-User-Id` header. They never see a JWT and
perform no authentication of their own. **A deployment that exposes either of them directly hands
every user's material to anyone who can set a header.** Only `api-gateway-auth` (port 8080) is meant
to be reachable from outside.

This is a property of the network, not of the code, and it is the single most important thing to get
right in any deployment topology.

### Health and correlation

Every service exposes `/actuator/health` with `management.endpoints.web.exposure.include=health,info`
— enough for a readiness probe, and nothing else exposed. `content-service` accepts and echoes
`X-Correlation-Id`, generating one when absent, and stamps it on every event it publishes.

### Object storage is S3, with a different endpoint

MinIO in development and S3 in production are the same adapter configured differently:
`MINIO_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`, and the credentials. In AWS this should become
an instance role rather than static keys, which is the one code change deployment will require.

## Secrets

`.env.example` holds development credentials and is committed deliberately as an example. `.env` is
gitignored. Neither is a deployment mechanism: a real deployment injects secrets from whatever the
platform provides, and the values in `.env.example` must never be reused anywhere that matters.

Nothing in the repository currently holds a production secret, and nothing should.

## What has to be decided first

In rough order of what blocks what:

1. **Container images.** No service has a `Dockerfile`. Spring Boot's `bootBuildImage`/buildpacks
   support is the cheapest starting point and needs no Dockerfile at all; the alternative is a
   jlink-based multi-stage build for smaller images. The Python workers need their own.
2. **Where it runs.** Compose on a single host is enough for a demo and keeps the topology identical
   to development. Kubernetes buys independent scaling of the workers, which is the only component
   that will actually need it.
3. **How images get built and published.** CI builds and tests today; it does not produce an
   artefact. A tag-triggered job pushing to a registry is the missing piece.
4. **Database migration on deploy.** Flyway on startup is fine for one instance per service. With
   more than one, two instances migrating concurrently need Flyway's lock to be trusted or a
   migration job to run separately.
5. **Backups.** Three Postgres databases and one MinIO bucket hold the only data that cannot be
   recomputed. Qdrant can be rebuilt from them — expensively, since transcripts are not persisted
   ([ADR-0002](architecture/adr/0002-chunks-are-a-storage-artifact.md)).
6. **Observability.** Actuator health is present. There is no metrics scrape, no log aggregation and
   no tracing, although `correlation_id` already flows through every event and would make tracing
   mostly a matter of exporting it.

## Before this document describes anything real

The reliability gaps in
[EVENTS.md](architecture/EVENTS.md#reliability-and-what-is-missing) matter more in production than
in development, and at least the first two should be closed before real material is ingested:

- events are published inside the database transaction, so delivery is at-most-once;
- there are no failure events, so a content that fails processing stays `PROCESSING` forever;
- there is no dead-letter or retry convention for the workers to share.
