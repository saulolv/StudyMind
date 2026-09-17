# StudyMind documentation

## Start here

- **[development/00-environment-setup.md](development/00-environment-setup.md)** — clone to green
  build, and the commands CI runs.
- **[../RULES.md](../RULES.md)** — the rules this repository is held to, and which CI job enforces
  each one.

## Architecture

- **[architecture/ARCHITECTURE.md](architecture/ARCHITECTURE.md)** — the services, why the
  boundaries are where they are, the path a document takes, and where the design is weak today.
- **[architecture/DATA-MODEL.md](architecture/DATA-MODEL.md)** — the five stores, who owns each, and
  what is deliberately not in them.
- **[architecture/EVENTS.md](architecture/EVENTS.md)** — the envelope, the flow, the conventions CI
  enforces, and the reliability work that is still missing.
- **[architecture/adr/](architecture/adr/)** — the decisions, with what would make each one wrong.

## Development

- **[development/](development/)** — the build order, and the guides that exist.
- **[development/02-content-service.md](development/02-content-service.md)** — the one implemented
  service.

## Operations

- **[DEPLOYMENT.md](DEPLOYMENT.md)** — what the code already assumes about being deployed, and what
  has to be decided before it can be.

## Contracts

The contracts themselves are the documentation, and they are checked in CI:

- [`contracts/events/v1/`](../contracts/events/v1/) — JSON Schema for every event, with its own
  README as the index.
- `services/*/openapi/openapi.yaml` — one OpenAPI 3 specification per service.
