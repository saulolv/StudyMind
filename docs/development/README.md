# Development guides

Start with [00-environment-setup.md](00-environment-setup.md). After that, the build order below is
the dependency order: each step can be demonstrated end to end once the ones before it exist.

## Written

- **[00 — Environment setup](00-environment-setup.md)** — clone to green build, and the commands CI
  runs.
- **[02 — content-service](02-content-service.md)** — the one implemented service: its module
  shapes, why each one is the way it is, and how to extend it.

## Build order

| # | Guide | State |
| --- | --- | --- |
| 00 | Environment setup | written |
| 01 | api-gateway-auth — JWT, refresh cookie, `X-User-Id` forwarding | scaffold only |
| 02 | content-service — ingestion, catalog, events | **implemented** |
| 03 | processing-worker — PDF text extraction and chunking | not started |
| 04 | embedding-worker — embeddings and Qdrant upsert | not started |
| 05 | chat-llm-service — retrieval and answering | scaffold only |
| 06 | transcription-worker — Whisper, timestamps | not started |
| 07 | quiz and flashcard generation | not started |
| 08 | frontend — the Angular SPA | not started |
| 09 | observability and deployment | see [DEPLOYMENT.md](../DEPLOYMENT.md) |

A guide is written when the thing it describes exists. A numbered file here that documents an
unwritten service would be a design sketch pretending to be instructions, and the first person to
follow it would find out the hard way — so the rows above are a plan, not links.

## Before you write any of them

Read [RULES.md](../../RULES.md). It is short, and every rule in it is either enforced by CI or is
the reason something in this codebase looks the way it does.

The two that catch people first:

- **Contracts before code.** The OpenAPI spec and the event schemas are written first and changed in
  the same commit as the code that follows them.
- **Spring Boot 4 names differ from Boot 3.** `spring-boot-starter-webmvc`, per-starter test
  dependencies, Jackson 3 under `tools.jackson`. Copying a Boot 3 snippet produces imports that do
  not resolve.
