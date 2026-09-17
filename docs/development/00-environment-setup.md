# 00 — Environment setup

Everything below is verifiable: if a step does not produce the output shown, something is wrong and
the rest will not work.

## What you need

| Tool | Version | Needed for |
| --- | --- | --- |
| JDK | 21 or newer | the three Java services |
| Docker + Compose | any current version | Postgres, RabbitMQ, Qdrant, MinIO |
| Python | 3.11+ | `tools/contracts`, and the workers when they exist |
| Node.js | 22+ | Spectral (OpenAPI linting), and the frontend when it exists |

Maven is **not** required: each service ships its own wrapper (`./mvnw`, or `.\mvnw.cmd` on
Windows). There is no aggregator POM, so every Maven command runs from inside a service directory.

## 1. Configure the environment

`docker-compose.yml` declares no defaults. Without a `.env` file, `docker compose` fails with
`variable is not set`.

```bash
cp .env.example .env      # PowerShell: Copy-Item .env.example .env
```

The defaults in `.env.example` are development credentials and are meant to be committed as an
example. Do not reuse them anywhere that matters.

## 2. Start the infrastructure

```bash
docker compose up -d
docker compose ps
```

Six containers, all `healthy` except `studymind-minio-init`, which is a one-shot job that creates
the bucket and exits:

| Container | Port | What it is |
| --- | --- | --- |
| `studymind-postgres-auth` | 5433 | `auth_db`, owned by api-gateway-auth |
| `studymind-postgres-content` | 5434 | `content_db`, owned by content-service |
| `studymind-postgres-chat` | 5436 | `chat_db`, owned by chat-llm-service |
| `studymind-rabbitmq` | 5672, 15672 | the event bus; management UI on 15672 |
| `studymind-qdrant` | 6333 | the vector store |
| `studymind-minio` | 9000, 9001 | object storage; console on 9001 |
| `studymind-minio-init` | — | creates the bucket, then exits |

There is no Redis. See
[ADR-0003](../architecture/adr/0003-remove-redis-until-something-needs-it.md).

Check the bucket was created:

```bash
docker compose logs minio-init      # ends with: bucket studymind ready
```

## 3. Build and test a service

```bash
cd services/content-service
./mvnw -B -ntp verify
```

This compiles, runs the tests, and enforces the 90% coverage gate — all three, because the gate is
in the POM rather than only in CI
([ADR-0005](../architecture/adr/0005-coverage-gate-lives-in-the-build.md)). It needs **no Docker**:
content-service's tests use an in-memory blob store and read the contract schemas off disk.

Useful variants:

```bash
./mvnw -B -ntp test -Dtest=YouTubeLinksTest                       # one class
./mvnw -B -ntp test -Dtest=YouTubeLinksTest#rejectsUrlsWithNoVideoId
./mvnw spring-boot:run                                            # run it, needs compose up
```

The coverage report lands at `target/site/jacoco/index.html`.

### On Windows, without Maven or Java on PATH

The JDK that ships with IntelliJ works:

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\jbr'
cd services\content-service
.\mvnw.cmd -B -ntp verify
```

## 4. Set up the contracts tool

`tools/contracts` validates the event schemas and generates the Java wire types from them.

```bash
cd tools/contracts
python -m venv .venv
.venv/bin/pip install -e ".[dev]"      # Windows: .venv\Scripts\pip
```

Then, from anywhere in the repository:

```bash
studymind-contracts validate           # every schema, its $refs, and the README index
studymind-contracts codegen            # rewrite the generated Java wire types
studymind-contracts codegen --check    # fail instead of writing, which is what CI runs
pytest                                 # from tools/contracts
ruff check . && ruff format --check .
```

## 5. Lint the OpenAPI specs

```bash
npx --yes @stoplight/spectral-cli@6.15.0 lint "services/*/openapi/openapi.yaml" \
  --ruleset .spectral.yaml --fail-severity error
```

Expected output: `No results with a severity of 'error' found!`

## Running everything CI runs, before pushing

```bash
# backend
for s in services/*/; do (cd "$s" && ./mvnw -B -ntp verify) || break; done

# contracts
studymind-contracts validate && studymind-contracts codegen --check

# python projects
(cd tools/contracts && ruff check . && ruff format --check . && pytest)

# openapi
npx --yes @stoplight/spectral-cli@6.15.0 lint "services/*/openapi/openapi.yaml" \
  --ruleset .spectral.yaml --fail-severity error
```

If all four pass locally, CI will pass too — the workflow runs these same commands.

## Troubleshooting

**`variable is not set` from `docker compose`** — there is no `.env`. Step 1.

**`Connection to localhost:5434 refused` when running a service** — compose is not up, or Postgres
is still starting. `docker compose ps` and wait for `healthy`.

**`Failed to determine a suitable driver class` on startup** — the service has the Flyway starter
but no datasource configured. `chat-llm-service` is in this state deliberately; it has no database
yet.

**`NoSuchBucketException` on PDF upload** — `minio-init` did not run. `docker compose up -d minio-init`.

**Coverage check fails after adding a class** — the gate is 90% line *and* branch. `target/site/jacoco/index.html`
shows exactly which branches are missing.

**`studymind-contracts: command not found`** — the virtualenv is not active, or the package was not
installed with `-e`. You can always run it as `python -m studymind_contracts`.
