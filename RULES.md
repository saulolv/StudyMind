# Rules

The rules this repository is held to. Every one of them is either **enforced** by a CI job, or is
the reason something in the codebase looks the way it does. A rule that is neither does not belong
here.

If a rule is wrong, change it here and change what enforces it, in the same commit. Do not work
around it quietly — a rule that is routinely ignored is worse than no rule, because it teaches
everyone that the rest are optional too.

| Area | Rules | Enforced by |
| --- | --- | --- |
| [Contracts](#1-contracts) | R1–R7 | `contracts`, `openapi` |
| [Testing](#2-testing) | R8–R12 | `backend`, `python`, `frontend` |
| [Services](#3-services) | R13–R18 | review |
| [Data and tenancy](#4-data-and-tenancy) | R19–R22 | review |
| [Spring Boot 4](#5-spring-boot-4) | R23–R25 | `backend` |
| [Repository](#6-repository) | R26–R31 | `ci`, review |

---

## 1. Contracts

**R1 — Contracts are written before the code.**
Before implementing an endpoint, write it in that service's `openapi/openapi.yaml`. Before
publishing an event, write its schema in `contracts/events/v1/`. The contract is the design step,
not the documentation step.

**R2 — A contract change and the code that follows it ship in the same commit.**
Never one without the other. `ContentEventContractTest` and the `contracts` CI job exist to make the
gap impossible to leave open.

**R3 — Every event schema follows the conventions in `contracts/events/v1/`.**
Draft 2020-12; `allOf` the envelope; `event_type` pinned to a `const`; payload
`additionalProperties: false`; snake_case field names; file named `<event-name>.v<n>.schema.json`
with a matching `$id`. *Enforced: `studymind-contracts validate`.*

**R4 — Every event payload carries a required `user_id`.**
It is the tenant isolation key and it has to reach the Qdrant filter. A payload without it produces
a chunk nobody can safely scope. *Enforced: `studymind-contracts validate`.*

**R5 — A breaking contract change gets a new version file, never an edit.**
`<event-name>.v2.schema.json`. The v1 file stays for as long as anything speaks v1. Adding an
optional field is not breaking; renaming, removing or tightening one is.

**R6 — Generated wire types are regenerated, not hand-edited.**
`studymind-contracts codegen` after any schema change. Files under `events/wire/` carry a
`// Generated` header and are overwritten. *Enforced: `codegen --check` in CI.*

**R7 — Every OpenAPI operation states who may call it, how it refuses, and what comes back.**
An `operationId`, a `summary`, a `security` block (`security: []` if genuinely public), at least one
failure response, and a schema for every body. Errors are `application/problem+json`, RFC 9457, in
every service. *Enforced: `.spectral.yaml`.*

## 2. Testing

**R8 — Backend coverage is at least 90%, line and branch.**
The gate is `jacoco:check` in each service's POM, so `./mvnw verify` fails locally for the same
reason CI does. Only two exclusions exist — the `*Application` bootstrap class and generated wire
types — and adding a third needs a written reason. See
[ADR-0005](docs/architecture/adr/0005-coverage-gate-lives-in-the-build.md). *Enforced: `backend`.*

**R9 — Production code ships with tests in the same commit.**
A service with production classes and no test classes fails CI explicitly, because JaCoCo would
otherwise skip the gate and report a pass. *Enforced: `backend`.*

**R10 — A unit test suite must not require Docker, a network or a live service.**
`./mvnw verify` has to pass on a clean machine with nothing running. Use the seam: content-service
tests ingestion through `ContentIngestion` with an in-memory `BlobStore`. Do not add a
`@SpringBootTest` `contextLoads` test — it asserts nothing about this codebase and drags Postgres
and RabbitMQ into the unit suite. *Enforced: `backend`.*

**R11 — Test the refusals, not only the happy path.**
The 90% *branch* threshold is the binding one, and it is deliberate: a malformed header, a file name
that sanitises to nothing, an oversized upload, a URL with no video id. Those paths are where the
security properties live.

**R12 — Python projects pass `ruff check`, `ruff format --check` and `pytest`.**
Every directory with a `pyproject.toml` is discovered and run. *Enforced: `python`.*

## 3. Services

**R13 — A service boundary needs two different reasons to change.**
"Different noun" is not one. document-service and video-service were merged because they were the
same resource twice; see
[ADR-0001](docs/architecture/adr/0001-merge-document-and-video-into-content-service.md).

**R14 — Do not re-split `content-service` by file type.**
A third source — a web article, a pasted block of text — is a new record permitted by
`ContentSource`, not a new service and not a new endpoint shape.

**R15 — An interface needs a second implementation that is not a mock, or a reason written down.**
`ContentCatalog` is a concrete class because nothing varies across it. `BlobStore` is an interface
because the in-memory test adapter is the second implementation — not because S3 might replace
MinIO, which is the same adapter with a different endpoint.

**R16 — Configuration is an environment variable with a localhost default.**
`${CONTENT_DB_HOST:localhost}`. No environment-specific property files, no hardcoded hosts.

**R17 — Schema changes are Flyway migrations. `ddl-auto` is `validate`, never `update`.**
A new versioned file each time; never edit one already applied.

**R18 — A new component is added when code needs it, not when the diagram has room for it.**
Redis was removed for exactly this reason; see
[ADR-0003](docs/architecture/adr/0003-remove-redis-until-something-needs-it.md).

## 4. Data and tenancy

**R19 — Never read another service's database.**
Each service owns its own Postgres database. Cross-service data moves as an event or as an explicit
HTTP call, never as a query.

**R20 — Every read path is scoped to a user.**
Every method on `ContentCatalog` takes the owning `userId` first. There is no unscoped read in
content-service and there must not be one anywhere else.

**R21 — Every vector search filters on `user_id` before similarity.**
An unfiltered search returns the *most convincing possible* leak. This is the worst bug the system
can have.

**R22 — Content the caller does not own is 404, not 403.**
The existence of another user's material is not theirs to learn.

## 5. Spring Boot 4

The parent is `spring-boot-starter-parent:4.0.5`. Boot 3 snippets produce artifacts and imports that
do not resolve, so check against these before copying anything in:

**R23 — Use the Boot 4 artifact names.**
`spring-boot-starter-webmvc`, not `-web`. Test dependencies are per-starter —
`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-amqp-test`, `-flyway-test`, `-security-test`,
`-actuator-test`. There is no `spring-boot-starter-test`. Flyway is
`spring-boot-starter-flyway` plus `flyway-database-postgresql`.

**R24 — Jackson 3 is the primary mapper.**
`tools.jackson.databind.ObjectMapper`, not `com.fasterxml.jackson.databind`. Jackson 2 is on the
classpath transitively; reaching for it should be deliberate and commented.

**R25 — Use the moved test annotations.**
`@WebMvcTest` is `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. Mocks are
`@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`), not
`@MockBean`. JUnit 6, Mockito 5, AssertJ 3.

## 6. Repository

**R26 — `main` is green.**
Every push and pull request runs the full workflow. Require the single `CI` job in branch
protection; it aggregates the rest, so adding a service or a worker never means editing the
protected-branch settings.

**R27 — Run CI's commands before pushing.**
The four blocks at the end of
[00-environment-setup.md](docs/development/00-environment-setup.md#running-everything-ci-runs-before-pushing).
CI runs the same commands; there is no reason to learn the result twenty minutes later.

**R28 — Every link in the README and in `docs/` points at a file in the repository.**
A documentation-first project whose documentation is not there is worse than one that never claimed
to be.

**R29 — Comments say why, not what.**
The code says what it does. A comment earns its place by recording the alternative that was
rejected, the constraint that is not visible locally, or the failure it prevents.

**R30 — Record a decision as an ADR when it was hard to make and would be expensive to reverse.**
Include what would make it wrong. A decision with no stated failure condition cannot be revisited
honestly. Never renumber and never delete: a superseded ADR gets a status and stays.

**R31 — Secrets never enter the repository.**
`.env.example` holds development credentials as an example and is committed on purpose. `.env` is
gitignored. Nothing else that looks like a credential belongs in a tracked file.
