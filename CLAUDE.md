# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read `RULES.md` first.** It is the numbered rulebook this repository is held to, and it says which
CI job enforces each rule. This file is the orientation; `RULES.md` is the contract.

@RULES.md

## Project state

StudyMind is a RAG study platform (upload PDFs / YouTube links, chat against your own material). See `README.md` for product framing and the architecture diagram.

`content-service` is implemented. `api-gateway-auth` and `chat-llm-service` are still scaffolding — nothing but their `@SpringBootApplication` class, and no tests. The Python workers (`processing-worker`, `embedding-worker`, `transcription-worker`) and the Angular frontend shown in the README do not exist yet. `tools/contracts` is a Python package that validates the event schemas and generates the Java wire types from them.

`docs/` **is** in the repository: `docs/architecture/` (ARCHITECTURE, DATA-MODEL, EVENTS, and
`adr/`), `docs/development/` and `docs/DEPLOYMENT.md`. Every link in `README.md` points at a file
that exists, and it has to stay that way. A guide is written when the thing it describes exists, so
`docs/development/` lists the unwritten ones as plain rows rather than as dead links.

**Contracts are written before code here.** When implementing a service, read its `openapi/openapi.yaml` and the relevant schemas in `contracts/events/v1/` first and conform to them exactly (field names, status codes, snake_case JSON). If a contract needs to change, change the YAML/JSON schema in the same commit as the code. `content-service` enforces this: `ContentEventContractTest` validates every envelope it publishes against the real schema files.

## Build and test

Java 21. Each service is an **independent Maven project** with its own wrapper — there is no aggregator POM, so all commands run from inside a service directory.

Neither `java` nor `mvn` is on PATH in this environment. The JDK that IntelliJ ships works and compiles the project (it is JDK 25; `java.version=21` in the POM means javac targets 21 via `--release`):

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr'
cd services/content-service
.\mvnw.cmd -B -ntp verify                                 # build + tests
.\mvnw.cmd -B -ntp test -Dtest=YouTubeLinksTest           # single test class
.\mvnw.cmd -B -ntp test -Dtest=YouTubeLinksTest#rejectsUrlsWithNoVideoId
.\mvnw.cmd spring-boot:run                                # run the service
```

Use `./mvnw` instead of `.\mvnw.cmd` from the Bash tool. There is no Java linter or formatter
configured; Python is linted and formatted by `ruff`.

`verify` also runs the **JaCoCo gate: 90% line and branch**, configured in every service POM so the
local command and CI fail for the same reason. Only `**/*Application.class` and `**/events/wire/*`
are excluded. See `docs/architecture/adr/0005-coverage-gate-lives-in-the-build.md`.

### The rest of the toolchain

```bash
# contracts: validate the event schemas, and regenerate the Java wire types from them
cd tools/contracts && python -m venv .venv && .venv/Scripts/pip install -e ".[dev]"
studymind-contracts validate
studymind-contracts codegen           # --check to verify instead of write, which is what CI runs
cd tools/contracts && pytest && ruff check . && ruff format --check .

# openapi
npx --yes @stoplight/spectral-cli@6.15.0 lint "services/*/openapi/openapi.yaml" \n  --ruleset .spectral.yaml --fail-severity error
```

CI (`.github/workflows/ci.yml`) runs exactly these commands, across five jobs — `backend`,
`frontend`, `python`, `contracts`, `openapi` — plus a `ci` aggregator to require in branch
protection. The matrices are discovered (`services/*/pom.xml`, every `pyproject.toml`,
`frontend/package.json`), so adding a service or a worker does not mean editing the workflow.

The two scaffold services have **no tests**, deliberately: their Spring Initializr `contextLoads`
tests required a live Postgres and asserted nothing about this codebase. CI fails a service that has
production classes beyond its `Application` class and no test classes, because JaCoCo would
otherwise skip the coverage gate instead of enforcing it.

`content-service`'s test suite needs no Docker: ingestion is tested through its interface with an in-memory blob store, and the contract test reads the schema files off disk. Do not add a `@SpringBootTest` `contextLoads` test to it — that would make the suite require Postgres and RabbitMQ.

Infrastructure (Postgres x3, RabbitMQ, Qdrant, MinIO + bucket init — **no Redis**, see
`docs/architecture/adr/0003-remove-redis-until-something-needs-it.md`):

```powershell
Copy-Item .env.example .env    # compose declares no defaults; it fails without this
docker compose up -d
```

## Spring Boot 4 conventions (important)

The parent is `spring-boot-starter-parent:4.0.5`, whose names differ from Boot 3. Copying Boot 3 snippets produces artifacts and imports that do not resolve:

- `spring-boot-starter-webmvc` — **not** `spring-boot-starter-web`
- Test dependencies are **per-starter**: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-amqp-test`, `-flyway-test`, `-security-test`, `-actuator-test`. There is no single `spring-boot-starter-test`.
- Flyway comes via `spring-boot-starter-flyway` plus `flyway-database-postgresql`.
- **Jackson 3** is the primary mapper: `tools.jackson.databind.ObjectMapper`, not `com.fasterxml.jackson.databind`. Jackson 2 is still on the classpath transitively. Spring AMQP 4 ships both `JacksonJsonMessageConverter` (Jackson 3) and `Jackson2JsonMessageConverter`.
- Test annotations moved: `@WebMvcTest` is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, and mocks use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`), not `@MockBean`.
- JUnit is 6.x, Mockito 5.x, AssertJ 3.x.

Lombok is configured (with annotation-processor paths and a boot-plugin exclude) **only in `api-gateway-auth`**.

## Services

Each service owns its own Postgres database — never reach across service boundaries at the DB level.

| Service | Port | Package | DB (host port) | State |
| --- | --- | --- | --- | --- |
| `api-gateway-auth` | 8080 | `com.apigatewayauth` | `auth_db` @ 5433 | scaffold; Security + JPA + Lombok |
| `content-service` | 8081 | `com.contentservice` | `content_db` @ 5434 | implemented |
| `chat-llm-service` | 8083 | `com.chatllmservice` | @ 5436 | scaffold; no JPA/Postgres dependency yet |

Packages are flat per service (`com.<servicename>`), not `com.studymind.*`.

Follow `content-service`'s `application.properties` pattern when filling in the others: **env-var-driven config with localhost defaults, Flyway migrations, `ddl-auto=validate`, never `update`**.

### Why there is no document-service or video-service

They were merged into `content-service`. They carried the same resource (a user-owned content with a status), duplicated `/internal/{id}/ownership`, and emitted the same event shape under two names — while everything downstream (`ChunksCreated`, `ContentIndexed`) already discriminated on a single `type` field. Do not re-split them by file type. If a third source arrives (a web article, a raw text paste), it is a new variant of `ContentSource`, not a new service.

## content-service design

The module shapes here are deliberate; keep them when extending.

- **`ContentIngestion`** (`ingestion/`) — one method, `ingest(userId, source)`. `ContentSource` is a sealed interface with `PdfUpload` and `YouTubeLink`. This is the seam that replaced two services; a new ingestion source is a new permitted record, not a new endpoint shape. Its invariants (content is persisted, status `PENDING`, exactly one `ContentSubmitted` published, PDF bytes stored *before* the event) are documented on the interface and asserted in `DefaultContentIngestionTest`.
- **`EventPublisher`** (`events/`) — one method, `publish(payload)`, over a sealed `EventPayload`. Every envelope field is derived inside the adapter (`event_id`, `occurred_at` from an injected `Clock`, `correlation_id` from the MDC, `source_service` from `spring.application.name`). No caller builds an envelope. `RabbitEventPublisher` switches exhaustively over the sealed type, so a new event will not compile until it is given an `event_type` and routing key.
- The **envelope** JSON is assembled as an explicit `Map`, not by reflecting over Java field names, because Python workers consume it and the wire contract must not move when a record component is renamed. The **payload** is built through the generated records in `events/wire/`, which come from `contracts/events/v1/` — see below.
- **`ContentCatalog`** (`catalog/`) — a concrete class, not an interface. Nothing varies across it, so a port there would be indirection. Every method takes the owning `userId` first; there is no unscoped read path.
- **`BlobStore`** (`ingestion/`) — package-private. It exists so ingestion can be tested without MinIO, *not* to abstract "MinIO today, S3 tomorrow" — MinIO is S3-compatible, so those are one adapter with a different endpoint.
- Auth: this service never sees a JWT. `api-gateway-auth` validates it and forwards the subject as `X-User-Id`, resolved by `@CurrentUser`. It must not be reachable from outside the internal network.

Known gap: `DefaultContentIngestion` publishes inside the transaction, so a broker failure after commit loses the event (at-most-once). The fix is a transactional outbox; it is not implemented.

## Generated code

`services/content-service/src/main/java/com/contentservice/events/wire/` is **generated** from
`contracts/events/v1/` by `tools/contracts` and checked in. Do not hand-edit it: run
`studymind-contracts codegen`. Each generated record has a builder with one setter named after each
contract field, so renaming a field in a schema stops `RabbitEventPublisher` from compiling. CI
fails if the checked-in files are stale.

The domain records (`ContentSubmitted`, `ContentDeleted`) and the envelope are **not** generated, and
neither are the OpenAPI models — the reasoning is in
`docs/architecture/adr/0004-generate-wire-types-from-the-event-contracts.md`.

## Event contracts

All events share an envelope (`event_id`, `event_type`, `event_version`, `occurred_at`, `source_service`, `correlation_id`, `payload`); each concrete event `allOf`s `event-envelope.v1.schema.json` and pins `event_type`/`source_service` to a `const`. Payloads are `additionalProperties: false`, snake_case, UUIDs as strings, JSON Schema Draft 2020-12.

Flow: `ContentSubmitted` (content-service, `type` = PDF or VIDEO) → `ChunksCreated` (processing- or transcription-worker) → `ContentIndexed` (embedding-worker) → chat queries the Qdrant collection named in that event. `ContentDeleted` carries the cleanup that `DELETE /contents/{id}` accepts with a 202.

Every payload carries `user_id` alongside `content_id` — this is the tenant-isolation key and must be propagated into Qdrant filters and every query path. New events go in `contracts/events/v1/` as `<event-name>.v1.schema.json`; breaking changes get a new `v2` file rather than an edit.

`studymind-contracts validate` enforces all of the above in CI, plus: `$id` matches the file name,
`$ref`s resolve to files that exist, no two schemas claim one `event_type`, and the folder's README
lists exactly the events that have files. Adding a rule there means adding the test that makes it
fire.

Still missing and worth adding: failure events (`ProcessingFailed`, `TranscriptionFailed`,
`IndexingFailed`) and DLQ/retry conventions. No contract covers them yet, and
`docs/architecture/EVENTS.md` says so rather than the README claiming otherwise.
